package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.UpdateEvent;
import myau.module.DropdownSetting;
import myau.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.Vec3;

public class HitSelect extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final DropdownSetting mode = register(new DropdownSetting("Mode", 0, "SECOND", "CRITICALS", "W_TAP"));

    private boolean sprintState = false;
    private boolean set = false;
    private double savedSlowdown = 0.0;
    private int blockedHits = 0;
    private int allowedHits = 0;

    public HitSelect() {
        super("HitSelect", false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled()) return;
        if (event.getType() == EventType.POST) resetMotion();
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.SEND || event.isCancelled()) return;
        if (event.getPacket() instanceof C0BPacketEntityAction) {
            C0BPacketEntityAction packet = (C0BPacketEntityAction) event.getPacket();
            switch (packet.getAction()) {
                case START_SPRINTING: sprintState = true;  break;
                case STOP_SPRINTING:  sprintState = false; break;
            }
            return;
        }
        if (!(event.getPacket() instanceof C02PacketUseEntity)) return;
        C02PacketUseEntity use = (C02PacketUseEntity) event.getPacket();
        if (use.getAction() != C02PacketUseEntity.Action.ATTACK) return;
        Entity target = use.getEntityFromWorld(mc.theWorld);
        if (target == null || target instanceof EntityLargeFireball || !(target instanceof EntityLivingBase)) return;
        EntityLivingBase living = (EntityLivingBase) target;
        boolean allow;
        switch (mode.getIndex()) {
            case 0:  allow = prioritizeSecondHit(mc.thePlayer, living); break;
            case 1:  allow = prioritizeCriticalHits(mc.thePlayer);      break;
            case 2:  allow = prioritizeWTapHits(mc.thePlayer, sprintState); break;
            default: allow = true;
        }
        if (!allow) { event.setCancelled(true); blockedHits++; }
        else allowedHits++;
    }

    private boolean prioritizeSecondHit(EntityLivingBase player, EntityLivingBase target) {
        if (target.hurtTime != 0) return true;
        if (player.hurtTime <= player.maxHurtTime - 1) return true;
        if (player.getDistanceToEntity(target) < 2.5) return true;
        if (!isMovingTowards(target, player, 60.0) || !isMovingTowards(player, target, 60.0)) return true;
        fixMotion(); return false;
    }

    private boolean prioritizeCriticalHits(EntityLivingBase player) {
        if (player.onGround || player.hurtTime != 0 || player.fallDistance > 0.0f) return true;
        fixMotion(); return false;
    }

    private boolean prioritizeWTapHits(EntityLivingBase player, boolean sprinting) {
        if (player.isCollidedHorizontally || !mc.gameSettings.keyBindForward.isKeyDown() || sprinting) return true;
        fixMotion(); return false;
    }

    private void fixMotion() {
        if (set) return;
        KeepSprint ks = (KeepSprint) Myau.moduleManager.modules.get(KeepSprint.class);
        if (ks == null) return;
        try {
            savedSlowdown = ks.slowdown.getValue();
            if (!ks.isEnabled()) ks.toggle();  
            ks.slowdown.setValue(0);
            set = true;
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void resetMotion() {
        if (!set) return;
        KeepSprint ks = (KeepSprint) Myau.moduleManager.modules.get(KeepSprint.class);
        if (ks == null) return;
        try {
            ks.slowdown.setValue((int) savedSlowdown);
            if (ks.isEnabled()) ks.toggle();
        } catch (Exception e) { e.printStackTrace(); }
        set = false; savedSlowdown = 0.0;
    }

    private boolean isMovingTowards(EntityLivingBase source, EntityLivingBase target, double maxAngle) {
        Vec3 cur = source.getPositionVector();
        Vec3 last = new Vec3(source.lastTickPosX, source.lastTickPosY, source.lastTickPosZ);
        double mx = cur.xCoord - last.xCoord, mz = cur.zCoord - last.zCoord;
        double ml = Math.sqrt(mx*mx + mz*mz);
        if (ml == 0.0) return false;
        mx /= ml; mz /= ml;
        double tx = target.posX - cur.xCoord, tz = target.posZ - cur.zCoord;
        double tl = Math.sqrt(tx*tx + tz*tz);
        if (tl == 0.0) return false;
        tx /= tl; tz /= tl;
        return mx*tx + mz*tz >= Math.cos(Math.toRadians(maxAngle));
    }

    @Override
    public void onDisabled() {
        resetMotion(); sprintState = false; set = false;
        savedSlowdown = 0.0; blockedHits = 0; allowedHits = 0;
    }

    @Override
    public String[] getSuffix() {
        String[] opts = {"SECOND", "CRITICALS", "W_TAP"};
        return new String[]{opts[mode.getIndex()]};
    }
}