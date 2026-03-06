package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.PacketUtil;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
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

    public final SliderSetting  minLatency         = register(new SliderSetting("Min Latency",         50,  10, 1000, 10));
    public final SliderSetting  maxLatency         = register(new SliderSetting("Max Latency",        150,  10, 1000, 10));
    public final SliderSetting  maxDistance        = register(new SliderSetting("Max Distance",        6.0, 0.0, 10.0, 0.1));
    public final SliderSetting  stopTargetHurtTime = register(new SliderSetting("Stop Target HurtTime", -1,  -1,   20,   1));
    public final SliderSetting  stopSelfHurtTime   = register(new SliderSetting("Stop Self HurtTime",   -1,  -1,   20,   1));
    public final BooleanSetting drawRealPos        = register(new BooleanSetting("Draw Real Position", true));

    private static final class TimedPacket {
        final Packet<?> packet;
        final long time;
        final int targetId;
        TimedPacket(Packet<?> p, int id) { this.packet = p; this.time = System.currentTimeMillis(); this.targetId = id; }
    }

    // Per-player queues: entityId → queue of delayed packets
    private final Map<Integer, Queue<TimedPacket>> queues    = new ConcurrentHashMap<>();
    // Per-player real positions (tracked ahead of the delay)
    private final Map<Integer, Vec3>               realPos   = new ConcurrentHashMap<>();
    // Packets we've already re-processed — skip them on the next receive
    private final Set<Packet<?>>                   skipSet   = Collections.newSetFromMap(new IdentityHashMap<>());
    // Per-player chosen latency (randomised once, refreshed each release cycle)
    private final Map<Integer, Integer>            latencies = new ConcurrentHashMap<>();

    public BackTrack() { super("BackTrack", false); }

    @Override public void onEnabled()  { clearAll(); }
    @Override public void onDisabled() { releaseAll(); clearAll(); }

    // ── Tick: flush expired packets ───────────────────────────────────────────
    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // Stop self hurt-time condition → release everything
        if (stopSelfHurtTime.getValue() != -1
                && mc.thePlayer.hurtTime == (int) stopSelfHurtTime.getValue()) {
            releaseAll();
            return;
        }

        for (Map.Entry<Integer, Queue<TimedPacket>> entry : queues.entrySet()) {
            int id = entry.getKey();
            Queue<TimedPacket> q = entry.getValue();
            int latency = latencies.getOrDefault(id, (int) maxLatency.getValue());

            // Stop target hurt-time condition → release this player's queue
            Entity ent = mc.theWorld.getEntityByID(id);
            if (ent instanceof EntityPlayer) {
                EntityPlayer ep = (EntityPlayer) ent;
                if (stopTargetHurtTime.getValue() != -1
                        && ep.hurtTime == (int) stopTargetHurtTime.getValue()) {
                    releaseQueue(id);
                    continue;
                }
            }

            // Flush expired packets
            while (!q.isEmpty()) {
                TimedPacket tp = q.peek();
                if (tp == null) break;
                if (System.currentTimeMillis() - tp.time >= latency) {
                    q.poll();
                    synchronized (skipSet) { skipSet.add(tp.packet); }
                    PacketUtil.handlePacket((Packet) tp.packet);
                } else break;
            }
        }

        // Clean up empty queues for despawned entities
        queues.entrySet().removeIf(e -> {
            if (e.getValue().isEmpty()) {
                Entity ent = mc.theWorld.getEntityByID(e.getKey());
                if (ent == null || ent.isDead) {
                    realPos.remove(e.getKey());
                    latencies.remove(e.getKey());
                    return true;
                }
            }
            return false;
        });
    }

    // ── Packet intercept ──────────────────────────────────────────────────────
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (event.getType() != EventType.RECEIVE) return;

        Packet<?> p = event.getPacket();

        // Don't intercept packets we just re-processed
        synchronized (skipSet) {
            if (skipSet.remove(p)) return;
        }

        // Force-release on teleport / disconnect
        if (p instanceof S08PacketPlayerPosLook || p instanceof S40PacketDisconnect) {
            releaseAll();
            clearAll();
            return;
        }

        // Player despawn → release their queue
        if (p instanceof S13PacketDestroyEntities) {
            for (int id : ((S13PacketDestroyEntities) p).getEntityIDs()) {
                releaseQueue(id);
                queues.remove(id);
                realPos.remove(id);
                latencies.remove(id);
            }
            return;
        }

        // Resolve target entity ID from packet
        int targetId = getTargetId(p);
        if (targetId == -1) return;

        // Only delay players within maxDistance
        Entity ent = mc.theWorld.getEntityByID(targetId);
        if (!(ent instanceof EntityPlayer)) return;
        if (ent == mc.thePlayer) return;
        double dist = mc.thePlayer.getDistanceToEntity(ent);
        if (dist > maxDistance.getValue()) {
            // Out of range — release any held packets for them
            releaseQueue(targetId);
            return;
        }

        // Assign latency for this player if not set
        latencies.computeIfAbsent(targetId, k -> {
            int min = (int) minLatency.getValue();
            int max = (int) maxLatency.getValue();
            return min + (int)(Math.random() * (max - min));
        });

        // Track real position from movement packets
        trackRealPos(p, targetId);

        // Queue and cancel
        queues.computeIfAbsent(targetId, k -> new ConcurrentLinkedQueue<>())
              .add(new TimedPacket(p, targetId));
        event.setCancelled(true);
    }

    // ── Render real position ──────────────────────────────────────────────────
    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!drawRealPos.getValue() || mc.theWorld == null) return;
        for (Map.Entry<Integer, Vec3> entry : realPos.entrySet()) {
            Entity ent = mc.theWorld.getEntityByID(entry.getKey());
            if (ent == null || ent.isDead) continue;
            Vec3 pos = entry.getValue();
            double rx = mc.getRenderManager().viewerPosX;
            double ry = mc.getRenderManager().viewerPosY;
            double rz = mc.getRenderManager().viewerPosZ;
            double x = pos.xCoord - rx;
            double y = pos.yCoord - ry;
            double z = pos.zCoord - rz;
            AxisAlignedBB box = new AxisAlignedBB(
                    x - ent.width / 2f, y,               z - ent.width / 2f,
                    x + ent.width / 2f, y + ent.height,  z + ent.width / 2f);
            GlStateManager.pushMatrix();
            GlStateManager.disableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            RenderGlobal.drawOutlinedBoundingBox(box, 72, 125, 227, 200);
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private int getTargetId(Packet<?> p) {
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

    private void trackRealPos(Packet<?> p, int id) {
        if (p instanceof S14PacketEntity) {
            S14PacketEntity mp = (S14PacketEntity) p;
            Entity ent = mc.theWorld.getEntityByID(id);
            if (ent == null) return;
            Vec3 cur = realPos.getOrDefault(id, new Vec3(ent.posX, ent.posY, ent.posZ));
            if (mp.func_149060_h()) { // hasRotations or combined pos+rot
                realPos.put(id, cur.addVector(
                        mp.func_149062_c() / 32.0,
                        mp.func_149061_d() / 32.0,
                        mp.func_149064_e() / 32.0));
            } else {
                realPos.put(id, cur.addVector(
                        mp.func_149062_c() / 32.0,
                        mp.func_149061_d() / 32.0,
                        mp.func_149064_e() / 32.0));
            }
        } else if (p instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport tp = (S18PacketEntityTeleport) p;
            realPos.put(id, new Vec3(tp.getX() / 32.0, tp.getY() / 32.0, tp.getZ() / 32.0));
        }
    }

    @SuppressWarnings("unchecked")
    private void releaseQueue(int id) {
        Queue<TimedPacket> q = queues.get(id);
        if (q == null) return;
        List<TimedPacket> copy = new ArrayList<>(q);
        q.clear();
        for (TimedPacket tp : copy) {
            synchronized (skipSet) { skipSet.add(tp.packet); }
            PacketUtil.handlePacket((Packet) tp.packet);
        }
        latencies.remove(id);
    }

    @SuppressWarnings("unchecked")
    private void releaseAll() {
        for (int id : new HashSet<>(queues.keySet())) releaseQueue(id);
    }

    private void clearAll() {
        queues.clear(); realPos.clear(); skipSet.clear(); latencies.clear();
    }

    @Override
    public String[] getSuffix() {
        int total = queues.values().stream().mapToInt(Queue::size).sum();
        return new String[]{ total > 0 ? total + " pkts" : (int)maxLatency.getValue() + "ms" };
    }
}
