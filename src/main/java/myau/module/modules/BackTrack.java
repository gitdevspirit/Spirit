package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.UpdateEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.MSTimer;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.util.*;

public class BackTrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanSetting  legit         = register(new BooleanSetting("Legit",          false));
    public final BooleanSetting  releaseOnHit  = register(new BooleanSetting("Release On Hit", true));
    public final SliderSetting   delay         = register(new SliderSetting("Delay (ms)", 400,  0, 1000, 10));
    public final SliderSetting   hitRange      = register(new SliderSetting("Range",      3.0,  3.0, 10.0, 0.1));
    public final BooleanSetting  onlyIfNeeded  = register(new BooleanSetting("Only If Needed", true));
    public final BooleanSetting  esp           = register(new BooleanSetting("ESP",             true));
    public final DropdownSetting espMode       = register(new DropdownSetting("ESP Mode",   0, "Hitbox", "None"));

    private final Queue<Packet> incomingPackets = new LinkedList<>();
    private final Queue<Packet> outgoingPackets = new LinkedList<>();
    private final Map<Integer, Vec3> realPositions = new HashMap<>();
    private final MSTimer timer = new MSTimer();

    private KillAura killAura;
    private EntityLivingBase target;
    private Vec3 lastRealPos;

    public BackTrack() {
        super("BackTrack", false);
    }

    @Override
    public void onEnabled() {
        Module m = Myau.moduleManager.getModule(KillAura.class);
        if (m instanceof KillAura) killAura = (KillAura) m;
        incomingPackets.clear(); outgoingPackets.clear();
        realPositions.clear(); lastRealPos = null; timer.reset();
    }

    @Override
    public void onDisabled() {
        releaseAll();
        incomingPackets.clear(); outgoingPackets.clear();
        realPositions.clear(); lastRealPos = null;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null || killAura == null) return;
        Module scaffold = Myau.moduleManager.getModule(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) {
            releaseAll(); incomingPackets.clear(); outgoingPackets.clear(); return;
        }
        target = killAura.getTarget();
        if (event.getType() == EventType.RECEIVE) handleIncoming(event);
        else if (event.getType() == EventType.SEND) handleOutgoing(event);
    }

    private void handleIncoming(PacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof S14PacketEntity) {
            S14PacketEntity p = (S14PacketEntity) packet;
            Entity e = p.getEntity(mc.theWorld);
            if (e != null) {
                int id = e.getEntityId();
                Vec3 pos = realPositions.getOrDefault(id, new Vec3(0, 0, 0));
                realPositions.put(id, pos.addVector(p.func_149062_c() / 32.0, p.func_149061_d() / 32.0, p.func_149064_e() / 32.0));
            }
        }
        if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport p = (S18PacketEntityTeleport) packet;
            realPositions.put(p.getEntityId(), new Vec3(p.getX() / 32.0, p.getY() / 32.0, p.getZ() / 32.0));
        }
        if (shouldQueue()) {
            if (blockIncoming(packet)) { incomingPackets.add(packet); event.setCancelled(true); }
        } else releaseIncoming();
    }

    private void handleOutgoing(PacketEvent event) {
        if (!legit.getValue()) return;
        if (shouldQueue()) {
            if (blockOutgoing(event.getPacket())) { outgoingPackets.add(event.getPacket()); event.setCancelled(true); }
        } else releaseOutgoing();
    }

    @EventTarget
    public void onUpdate(UpdateEvent e) {
        if (!isEnabled() || mc.thePlayer == null) return;
        if (target != killAura.getTarget()) { releaseAll(); lastRealPos = null; }
        if (target == null) return;
        Vec3 real = realPositions.get(target.getEntityId());
        if (real == null) return;
        if (mc.thePlayer.maxHurtTime > 0 && mc.thePlayer.hurtTime == mc.thePlayer.maxHurtTime) releaseAll();
        if (mc.thePlayer.getDistance(real.xCoord, real.yCoord, real.zCoord) > hitRange.getValue()
                || timer.hasTimePassed((long) delay.getValue())) releaseAll();
        if (onlyIfNeeded.getValue()) {
            if (mc.thePlayer.getDistanceToEntity(target) <= mc.thePlayer.getDistance(real.xCoord, real.yCoord, real.zCoord))
                releaseAll();
            if (lastRealPos != null) {
                double lastDist = mc.thePlayer.getDistance(lastRealPos.xCoord, lastRealPos.yCoord, lastRealPos.zCoord);
                if (mc.thePlayer.getDistance(real.xCoord, real.yCoord, real.zCoord) < lastDist) releaseAll();
            }
        }
        if (legit.getValue() && releaseOnHit.getValue() && target.hurtTime == 1) releaseAll();
        lastRealPos = real;
    }

    @EventTarget
    public void onRender3D(Render3DEvent e) {
        if (!esp.getValue() || espMode.getIndex() != 0 || target == null) return;
        Vec3 real = realPositions.get(target.getEntityId());
        if (real == null) return;
        double x = real.xCoord - mc.getRenderManager().viewerPosX;
        double y = real.yCoord - mc.getRenderManager().viewerPosY;
        double z = real.zCoord - mc.getRenderManager().viewerPosZ;
        AxisAlignedBB box = new AxisAlignedBB(
                x - target.width / 2, y, z - target.width / 2,
                x + target.width / 2, y + target.height, z + target.width / 2);
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D(); GlStateManager.disableDepth(); GlStateManager.depthMask(false);
        GlStateManager.color(1F, 0F, 0F, 0.4F);
        RenderGlobal.drawOutlinedBoundingBox(box, 255, 0, 0, 153);
        GlStateManager.depthMask(true); GlStateManager.enableDepth(); GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private boolean shouldQueue() {
        if (target == null) return false;
        Vec3 real = realPositions.get(target.getEntityId());
        if (real == null) return false;
        double distReal = mc.thePlayer.getDistance(real.xCoord, real.yCoord, real.zCoord);
        double distCurrent = mc.thePlayer.getDistanceToEntity(target);
        if (!onlyIfNeeded.getValue())
            return distReal + 0.15 < distCurrent && !timer.hasTimePassed((long) delay.getValue());
        return distReal < distCurrent;
    }

    private void releaseIncoming() {
        if (mc.getNetHandler() == null) return;
        while (!incomingPackets.isEmpty()) incomingPackets.poll().processPacket(mc.getNetHandler());
        timer.reset();
    }
    private void releaseOutgoing() {
        while (!outgoingPackets.isEmpty()) PacketUtil.sendPacketNoEvent(outgoingPackets.poll());
        timer.reset();
    }
    private void releaseAll() { releaseIncoming(); releaseOutgoing(); }

    private boolean blockIncoming(Packet<?> p) {
        if (!onlyIfNeeded.getValue()) {
            if (p instanceof S12PacketEntityVelocity || p instanceof S27PacketExplosion) return false;
            return p instanceof S14PacketEntity || p instanceof S18PacketEntityTeleport
                    || p instanceof S19PacketEntityHeadLook || p instanceof S0FPacketSpawnMob;
        }
        return p instanceof S12PacketEntityVelocity || p instanceof S27PacketExplosion
                || p instanceof S14PacketEntity || p instanceof S18PacketEntityTeleport
                || p instanceof S19PacketEntityHeadLook || p instanceof S0FPacketSpawnMob;
    }

    private boolean blockOutgoing(Packet<?> p) {
        return p instanceof C03PacketPlayer || p instanceof C02PacketUseEntity
                || p instanceof C0APacketAnimation || p instanceof C0BPacketEntityAction
                || p instanceof C08PacketPlayerBlockPlacement || p instanceof C07PacketPlayerDigging
                || p instanceof C09PacketHeldItemChange || p instanceof C00PacketKeepAlive
                || p instanceof C01PacketPing;
    }
}