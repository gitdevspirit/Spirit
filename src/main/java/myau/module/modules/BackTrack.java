package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BackTrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // ── Mode ──────────────────────────────────────────────────────────────────
    public final DropdownSetting mode = register(new DropdownSetting("Mode", 0, "Manual", "Lag Based"));

    // ── Manual settings ───────────────────────────────────────────────────────
    public final SliderSetting  ticks         = register(new SliderSetting("Ticks",              10,  1,  40,  1));
    public final BooleanSetting renderPrevTicks = register(new BooleanSetting("Render Prev Ticks", true));

    // ── Lag Based settings ────────────────────────────────────────────────────
    public final SliderSetting  latency       = register(new SliderSetting("Latency (ms)",       150, 10, 1000, 10));
    public final SliderSetting  maxDistance   = register(new SliderSetting("Max Distance",        6.0, 0.0, 10.0, 0.1));
    public final SliderSetting  stopHurtTime  = register(new SliderSetting("Stop Target HurtTime", -1, -1, 20, 1));
    public final SliderSetting  stopSelfHurt  = register(new SliderSetting("Stop Self HurtTime",   -1, -1, 20, 1));
    public final BooleanSetting renderServerPos = register(new BooleanSetting("Render Server Pos", true));
    public final SliderSetting  shadowR       = register(new SliderSetting("Color R", 72,  0, 255, 1));
    public final SliderSetting  shadowG       = register(new SliderSetting("Color G", 125, 0, 255, 1));
    public final SliderSetting  shadowB       = register(new SliderSetting("Color B", 227, 0, 255, 1));

    // ── Manual mode state ─────────────────────────────────────────────────────
    // Per-player ring buffer of past positions: entityId → list of Vec3 (oldest first)
    private final Map<Integer, ArrayDeque<double[]>> posHistory = new ConcurrentHashMap<>();

    // ── Lag Based state ───────────────────────────────────────────────────────
    private static final class TimedPacket {
        final Packet<?> packet;
        final long time;
        TimedPacket(Packet<?> p) { this.packet = p; this.time = System.currentTimeMillis(); }
    }
    private final Map<Integer, Queue<TimedPacket>> queues  = new ConcurrentHashMap<>();
    private final Map<Integer, double[]>           realPos = new ConcurrentHashMap<>();
    private final Set<Packet<?>> skipSet = Collections.newSetFromMap(new IdentityHashMap<>());

    public BackTrack() { super("BackTrack", false); }

    @Override public void onEnabled()  { clearAll(); }
    @Override public void onDisabled() { if (mode.getIndex() == 1) releaseAll(); clearAll(); }

    // ── Tick ──────────────────────────────────────────────────────────────────
    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        if (mode.getIndex() == 0) {
            // Manual: record current position of all nearby players each tick
            for (Object obj : mc.theWorld.playerEntities) {
                if (!(obj instanceof EntityPlayer)) continue;
                EntityPlayer p = (EntityPlayer) obj;
                if (p == mc.thePlayer) continue;
                int id = p.getEntityId();
                ArrayDeque<double[]> hist = posHistory.computeIfAbsent(id, k -> new ArrayDeque<>());
                hist.addLast(new double[]{p.posX, p.posY, p.posZ});
                int max = (int) ticks.getValue();
                while (hist.size() > max + 1) hist.pollFirst();
            }
            // Remove history for players who left
            posHistory.keySet().removeIf(id -> mc.theWorld.getEntityByID(id) == null);

        } else {
            // Lag Based: flush expired packets
            if (event.getType() != EventType.PRE) return;

            if (stopSelfHurt.getValue() != -1
                    && mc.thePlayer.hurtTime == (int) stopSelfHurt.getValue()) {
                releaseAll(); return;
            }

            for (Map.Entry<Integer, Queue<TimedPacket>> entry : new HashMap<>(queues).entrySet()) {
                int id = entry.getKey();
                Queue<TimedPacket> q = entry.getValue();
                Entity ent = mc.theWorld.getEntityByID(id);

                if (stopHurtTime.getValue() != -1 && ent instanceof EntityPlayer
                        && ((EntityPlayer) ent).hurtTime == (int) stopHurtTime.getValue()) {
                    releaseQueue(id); continue;
                }

                long lat = (long) latency.getValue();
                while (!q.isEmpty() && System.currentTimeMillis() - q.peek().time >= lat) {
                    TimedPacket tp = q.poll();
                    synchronized (skipSet) { skipSet.add(tp.packet); }
                    PacketUtil.handlePacket((Packet) tp.packet);
                }
            }
            queues.entrySet().removeIf(e -> e.getValue().isEmpty()
                    && mc.theWorld.getEntityByID(e.getKey()) == null);
        }
    }

    // ── Packet intercept (Lag Based only) ────────────────────────────────────
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || mode.getIndex() != 1) return;
        if (event.getType() != EventType.RECEIVE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        Packet<?> p = event.getPacket();
        synchronized (skipSet) { if (skipSet.remove(p)) return; }

        if (p instanceof S08PacketPlayerPosLook || p instanceof S40PacketDisconnect) {
            releaseAll(); clearAll(); return;
        }
        if (p instanceof S13PacketDestroyEntities) {
            for (int id : ((S13PacketDestroyEntities) p).getEntityIDs()) releaseQueue(id);
            return;
        }

        int targetId = resolveId(p);
        if (targetId == -1) return;
        Entity ent = mc.theWorld.getEntityByID(targetId);
        if (!(ent instanceof EntityPlayer) || ent == mc.thePlayer) return;
        if (mc.thePlayer.getDistanceToEntity(ent) > maxDistance.getValue()) {
            releaseQueue(targetId); return;
        }

        trackRealPos(p, targetId, ent);
        queues.computeIfAbsent(targetId, k -> new ConcurrentLinkedQueue<>()).add(new TimedPacket(p));
        event.setCancelled(true);
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || mc.theWorld == null) return;

        if (mode.getIndex() == 0 && renderPrevTicks.getValue()) {
            // Manual: render ghost at each historical position
            for (Map.Entry<Integer, ArrayDeque<double[]>> entry : posHistory.entrySet()) {
                Entity ent = mc.theWorld.getEntityByID(entry.getKey());
                if (!(ent instanceof AbstractClientPlayer) || ent.isDead) continue;
                AbstractClientPlayer player = (AbstractClientPlayer) ent;
                ArrayDeque<double[]> hist = entry.getValue();
                double[] positions = hist.toArray(new double[0][]);
                int count = positions.length;
                for (int i = 0; i < count; i++) {
                    double[] pos = (double[]) positions[i];
                    float alpha = 0.15f + 0.6f * ((float) i / count);
                    renderGhost(player, pos[0], pos[1], pos[2], alpha);
                }
            }
        } else if (mode.getIndex() == 1 && renderServerPos.getValue()) {
            // Lag Based: render real server position
            for (Map.Entry<Integer, double[]> entry : realPos.entrySet()) {
                Entity ent = mc.theWorld.getEntityByID(entry.getKey());
                if (ent == null || ent.isDead) continue;
                double[] pos = entry.getValue();
                double rx = mc.getRenderManager().viewerPosX;
                double ry = mc.getRenderManager().viewerPosY;
                double rz = mc.getRenderManager().viewerPosZ;
                double x = pos[0] - rx, y = pos[1] - ry, z = pos[2] - rz;
                AxisAlignedBB box = new AxisAlignedBB(
                        x - ent.width/2f, y, z - ent.width/2f,
                        x + ent.width/2f, y + ent.height, z + ent.width/2f);
                GlStateManager.pushMatrix();
                GlStateManager.disableTexture2D();
                GlStateManager.disableDepth();
                GlStateManager.depthMask(false);
                RenderGlobal.drawOutlinedBoundingBox(box,
                        (int) shadowR.getValue(),
                        (int) shadowG.getValue(),
                        (int) shadowB.getValue(), 200);
                GlStateManager.depthMask(true);
                GlStateManager.enableDepth();
                GlStateManager.enableTexture2D();
                GlStateManager.popMatrix();
            }
        }
    }

    private void renderGhost(AbstractClientPlayer player, double x, double y, double z, float alpha) {
        RenderManager rm = mc.getRenderManager();
        double rx = rm.viewerPosX, ry = rm.viewerPosY, rz = rm.viewerPosZ;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x - rx, y - ry, z - rz);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.color(
                (int) shadowR.getValue() / 255f,
                (int) shadowG.getValue() / 255f,
                (int) shadowB.getValue() / 255f,
                alpha);
        try {
            mc.getRenderManager().renderEntityWithPosYaw(
                    player, 0, 0, 0, player.rotationYaw, mc.timer.renderPartialTicks);
        } catch (Exception ignored) {}
        GlStateManager.enableDepth();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private int resolveId(Packet<?> p) {
        if (p instanceof S14PacketEntity) {
            Entity e = ((S14PacketEntity) p).getEntity(mc.theWorld);
            return e != null ? e.getEntityId() : -1;
        }
        if (p instanceof S18PacketEntityTeleport) return ((S18PacketEntityTeleport) p).getEntityId();
        if (p instanceof S19PacketEntityHeadLook) {
            Entity e = ((S19PacketEntityHeadLook) p).getEntity(mc.theWorld);
            return e != null ? e.getEntityId() : -1;
        }
        return -1;
    }

    private void trackRealPos(Packet<?> p, int id, Entity ent) {
        if (p instanceof S14PacketEntity) {
            S14PacketEntity mp = (S14PacketEntity) p;
            double[] cur = realPos.getOrDefault(id, new double[]{ent.posX, ent.posY, ent.posZ});
            realPos.put(id, new double[]{
                cur[0] + mp.func_149062_c() / 32.0,
                cur[1] + mp.func_149061_d() / 32.0,
                cur[2] + mp.func_149064_e() / 32.0});
        } else if (p instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport tp = (S18PacketEntityTeleport) p;
            realPos.put(id, new double[]{tp.getX()/32.0, tp.getY()/32.0, tp.getZ()/32.0});
        }
    }

    @SuppressWarnings("unchecked")
    private void releaseQueue(int id) {
        Queue<TimedPacket> q = queues.remove(id);
        if (q == null) return;
        for (TimedPacket tp : q) {
            synchronized (skipSet) { skipSet.add(tp.packet); }
            PacketUtil.handlePacket((Packet) tp.packet);
        }
        realPos.remove(id);
    }

    @SuppressWarnings("unchecked")
    private void releaseAll() {
        for (int id : new HashSet<>(queues.keySet())) releaseQueue(id);
    }

    private void clearAll() {
        queues.clear(); realPos.clear(); posHistory.clear();
        synchronized (skipSet) { skipSet.clear(); }
    }

    @Override
    public String[] getSuffix() {
        if (mode.getIndex() == 1) {
            int held = queues.values().stream().mapToInt(Queue::size).sum();
            return new String[]{ held > 0 ? held + " pkts" : (int)latency.getValue() + "ms" };
        }
        return new String[]{ (int)ticks.getValue() + "t" };
    }
}
