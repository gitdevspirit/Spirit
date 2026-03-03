package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.AttackEvent;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BackTrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting  minLatency          = register(new SliderSetting("Min Latency",          50,  10, 1000, 10));
    public final SliderSetting  maxLatency          = register(new SliderSetting("Max Latency",         100,  10, 1000, 10));
    public final SliderSetting  minDistance         = register(new SliderSetting("Min Distance",        0.0, 0.0,  3.0, 0.1));
    public final SliderSetting  maxDistance         = register(new SliderSetting("Max Distance",        6.0, 0.0, 10.0, 0.1));
    public final SliderSetting  stopTargetHurtTime  = register(new SliderSetting("Stop Target HurtTime", -1,  -1,   10,   1));
    public final SliderSetting  stopSelfHurtTime    = register(new SliderSetting("Stop Self HurtTime",   -1,  -1,   10,   1));
    public final BooleanSetting drawRealPos         = register(new BooleanSetting("Draw Real Position", true));

    // Each entry: [packet, timestamp]
    private static final class TimedPacket {
        final Packet<?> packet;
        final long time;
        TimedPacket(Packet<?> p) { this.packet = p; this.time = System.currentTimeMillis(); }
        boolean expired(int latencyMs) { return System.currentTimeMillis() - time >= latencyMs; }
    }

    private final Queue<TimedPacket> packetQueue   = new ConcurrentLinkedQueue<>();
    private final List<Packet<?>>    skipPackets   = new ArrayList<>();

    private EntityPlayer target;
    private Vec3         trackedPos;   // backtrack position we hold target at
    private int          currentLatency = 0;

    public BackTrack() {
        super("BackTrack", false);
    }

    @Override
    public void onEnabled() {
        packetQueue.clear();
        skipPackets.clear();
        trackedPos = null;
        target = null;
        currentLatency = 0;
    }

    @Override
    public void onDisabled() {
        releaseAll();
        target = null;
        trackedPos = null;
        currentLatency = 0;
    }

    // ── Attack: lock onto target and set latency ──────────────────────────────

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;
        Entity e = event.getTarget();
        if (!(e instanceof EntityPlayer)) return;

        EntityPlayer attacked = (EntityPlayer) e;

        // Switching targets
        if (target == null || attacked != target) {
            trackedPos = new Vec3(attacked.posX, attacked.posY, attacked.posZ);
        }
        target = attacked;

        // Check distance gates
        if (trackedPos != null) {
            double dist = mc.thePlayer.getDistance(trackedPos.xCoord, trackedPos.yCoord, trackedPos.zCoord);
            if (dist > maxDistance.getValue() || dist < minDistance.getValue()) {
                currentLatency = 0;
                return;
            }
        }

        // Randomise latency in [min, max]
        currentLatency = (int)(Math.random() * (maxLatency.getValue() - minLatency.getValue()) + minLatency.getValue());
    }

    // ── Tick: flush expired packets ───────────────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;

        // Flush packets whose latency window has passed
        while (!packetQueue.isEmpty()) {
            TimedPacket tp = packetQueue.peek();
            if (tp == null) break;
            if (tp.expired(currentLatency)) {
                packetQueue.poll();
                skipPackets.add(tp.packet);
                PacketUtil.handlePacket((Packet) tp.packet);
            } else {
                break;
            }
        }

        // When queue is empty, update trackedPos to current real target position
        if (packetQueue.isEmpty() && target != null) {
            trackedPos = new Vec3(target.posX, target.posY, target.posZ);
        }

        // Distance gate check
        if (trackedPos != null && target != null && mc.thePlayer != null) {
            double dist = mc.thePlayer.getDistance(trackedPos.xCoord, trackedPos.yCoord, trackedPos.zCoord);
            if (dist > maxDistance.getValue() || dist < minDistance.getValue()) {
                currentLatency = 0;
            }
        }
    }

    // ── Packet: intercept + queue incoming movement packets ──────────────────

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (event.getType() != EventType.RECEIVE) return;

        Packet<?> p = event.getPacket();

        // Skip packets we already re-processed
        if (skipPackets.contains(p)) {
            skipPackets.remove(p);
            return;
        }

        // Stop conditions
        if (target != null && stopTargetHurtTime.getValue() != -1
                && target.hurtTime == (int) stopTargetHurtTime.getValue()) {
            releaseAll(); return;
        }
        if (stopSelfHurtTime.getValue() != -1
                && mc.thePlayer.hurtTime == (int) stopSelfHurtTime.getValue()) {
            releaseAll(); return;
        }

        // Safety checks
        if (mc.thePlayer.ticksExisted < 20) { packetQueue.clear(); return; }
        if (target == null)                 { releaseAll(); return; }
        if (event.isCancelled())             return;

        // Passthrough packets — never delay these
        if (p instanceof S19PacketEntityStatus
                || p instanceof S02PacketChat
                || p instanceof S0BPacketAnimation
                || p instanceof S06PacketUpdateHealth)
            return;

        // Force-release packets
        if (p instanceof S08PacketPlayerPosLook || p instanceof S40PacketDisconnect) {
            releaseAll();
            target = null;
            trackedPos = null;
            return;
        }

        // Target despawn
        if (p instanceof S13PacketDestroyEntities) {
            S13PacketDestroyEntities dp = (S13PacketDestroyEntities) p;
            for (int id : dp.getEntityIDs()) {
                if (id == target.getEntityId()) {
                    target = null;
                    trackedPos = null;
                    releaseAll();
                    return;
                }
            }
        }

        // Track real position via movement packets
        if (p instanceof S14PacketEntity) {
            S14PacketEntity mp = (S14PacketEntity) p;
            Entity ent = mp.getEntity(mc.theWorld);
            if (ent != null && ent == target && trackedPos != null) {
                trackedPos = trackedPos.addVector(
                        mp.func_149062_c() / 32.0,
                        mp.func_149061_d() / 32.0,
                        mp.func_149064_e() / 32.0);
            }
        } else if (p instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport tp = (S18PacketEntityTeleport) p;
            if (tp.getEntityId() == target.getEntityId()) {
                trackedPos = new Vec3(tp.getX() / 32.0, tp.getY() / 32.0, tp.getZ() / 32.0);
            }
        }

        // Queue + cancel movement packets for the target
        if (isTargetPacket(p)) {
            packetQueue.add(new TimedPacket(p));
            event.setCancelled(true);
        }
    }

    // ── Render: draw real position box ───────────────────────────────────────

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!drawRealPos.getValue() || target == null || trackedPos == null || target.isDead) return;

        Vec3 pos = currentLatency > 0 ? trackedPos : new Vec3(target.posX, target.posY, target.posZ);
        double x = pos.xCoord - mc.getRenderManager().viewerPosX;
        double y = pos.yCoord - mc.getRenderManager().viewerPosY;
        double z = pos.zCoord - mc.getRenderManager().viewerPosZ;

        AxisAlignedBB box = new AxisAlignedBB(
                x - target.width / 2f, y,                  z - target.width / 2f,
                x + target.width / 2f, y + target.height,  z + target.width / 2f);

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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isTargetPacket(Packet<?> p) {
        if (target == null) return false;
        if (p instanceof S14PacketEntity) {
            Entity e = ((S14PacketEntity) p).getEntity(mc.theWorld);
            return e != null && e == target;
        }
        if (p instanceof S18PacketEntityTeleport)
            return ((S18PacketEntityTeleport) p).getEntityId() == target.getEntityId();
        if (p instanceof S19PacketEntityHeadLook)
            return ((S19PacketEntityHeadLook) p).getEntity(mc.theWorld) == target;
        return false;
    }

    @SuppressWarnings("unchecked")
    private void releaseAll() {
        for (TimedPacket tp : packetQueue) {
            skipPackets.add(tp.packet);
            PacketUtil.handlePacket((Packet) tp.packet);
        }
        packetQueue.clear();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{ (currentLatency == 0 ? (int) maxLatency.getValue() : currentLatency) + "ms" };
    }
}
