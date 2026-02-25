package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.Render3DEvent;
import myau.events.StrafeEvent;
import myau.events.UpdateEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;

import java.awt.*;
import java.util.ArrayList;

public class TargetStrafe extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private EntityLivingBase target    = null;
    private float            targetYaw = Float.NaN;
    private int              direction = 1;

    public final SliderSetting   radius       = new SliderSetting("Radius",         1.0, 0.0, 6.0, 0.1);
    public final SliderSetting   points       = new SliderSetting("Points",           6,   3,  24,   1);
    public final BooleanSetting  requirePress = new BooleanSetting("Require Press",  true);
    public final BooleanSetting  speedOnly    = new BooleanSetting("Speed Only",     true);
    public final DropdownSetting showTarget   = new DropdownSetting("Show Target",   1, "NONE", "DEFAULT", "HUD");

    public TargetStrafe() {
        super("TargetStrafe", false);
        register(radius);
        register(points);
        register(requirePress);
        register(speedOnly);
        register(showTarget);
    }

    private boolean canStrafe() {
        Autoblock autoblock = (Autoblock) Myau.moduleManager.modules.get(Autoblock.class);
        if (autoblock != null && autoblock.isEnabled()) return false;
        if (speedOnly.getValue()) {
            Speed    speed    = (Speed)    Myau.moduleManager.modules.get(Speed.class);
            Fly      fly      = (Fly)      Myau.moduleManager.modules.get(Fly.class);
            LongJump longJump = (LongJump) Myau.moduleManager.modules.get(LongJump.class);
            if (!speed.isEnabled() && !fly.isEnabled() && (!longJump.isEnabled() || !longJump.isJumping()))
                return false;
        }
        return !requirePress.getValue() || PlayerUtil.isJumping();
    }

    private EntityLivingBase getKillAuraTarget() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (killAura.isEnabled() && killAura.isAttackAllowed()) {
            EntityLivingBase t = killAura.getTarget();
            return TeamUtil.isEntityLoaded(t) ? t : null;
        }
        return null;
    }

    private Color getTargetColor(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            if (TeamUtil.isFriend((EntityPlayer) entity)) return Myau.friendManager.getColor();
            if (TeamUtil.isTarget((EntityPlayer) entity)) return Myau.targetManager.getColor();
        }
        switch (showTarget.getIndex()) {
            case 1:
                return (entity instanceof EntityPlayer)
                        ? TeamUtil.getTeamColor((EntityPlayer) entity, 1.0F) : Color.WHITE;
            case 2:
                return new Color(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()).getRGB());
            default:
                return new Color(-1);
        }
    }

    private boolean isInWater(double x, double z) {
        return PlayerUtil.checkInWater(new AxisAlignedBB(
                x - 0.015, mc.thePlayer.posY, z - 0.015,
                x + 0.015, mc.thePlayer.posY + mc.thePlayer.height, z + 0.015));
    }

    private int wrapIndex(int index, int size) {
        if (index < 0) return size - 1;
        return index >= size ? 0 : index;
    }

    public float getTargetYaw() { return targetYaw; }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;

        boolean left = PlayerUtil.isMovingLeft(), right = PlayerUtil.isMovingRight();
        if (left ^ right) direction = left ? 1 : -1;

        if (!canStrafe()) { target = null; targetYaw = Float.NaN; return; }

        target = getKillAuraTarget();
        if (target == null) { targetYaw = Float.NaN; return; }

        int pts = (int) points.getValue();
        double rad = radius.getValue();
        ArrayList<Vec2d> vpositions = new ArrayList<>();
        for (int i = 0; i < pts; i++) {
            vpositions.add(new Vec2d(
                    rad * Math.cos(i * (Math.PI * 2.0 / pts)),
                    rad * Math.sin(i * (Math.PI * 2.0 / pts))));
        }
        if (vpositions.isEmpty()) { target = null; targetYaw = Float.NaN; return; }

        double closestDistance = 0.0;
        int closestIndex = -1;
        for (int i = 0; i < vpositions.size(); i++) {
            double dist = mc.thePlayer.getDistance(
                    target.posX + vpositions.get(i).x, mc.thePlayer.posY,
                    target.posZ + vpositions.get(i).y);
            if (closestIndex == -1 || dist < closestDistance) { closestDistance = dist; closestIndex = i; }
        }

        if (mc.thePlayer.isCollidedHorizontally) direction *= -1;

        int nextIndex = wrapIndex(closestIndex + direction, vpositions.size());
        double nextX = target.posX + vpositions.get(nextIndex).x;
        double nextZ = target.posZ + vpositions.get(nextIndex).y;
        if (isInWater(nextX, nextZ)) {
            direction *= -1;
            nextIndex = wrapIndex(closestIndex + direction, vpositions.size());
            nextX = target.posX + vpositions.get(nextIndex).x;
            nextZ = target.posZ + vpositions.get(nextIndex).y;
        }

        targetYaw = RotationUtil.getRotationsTo(nextX - mc.thePlayer.posX, 0.0,
                nextZ - mc.thePlayer.posZ, event.getYaw(), event.getPitch())[0];
        event.setPervRotation(targetYaw, 10);
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!isEnabled() || Float.isNaN(targetYaw) || !MoveUtil.isForwardPressed()) return;
        event.setStrafe(0.0F);
        event.setForward(1.0F);
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!isEnabled() || !TeamUtil.isEntityLoaded(target) || showTarget.getIndex() == 0) return;
        Color color = getTargetColor(target);
        RenderUtil.enableRenderState();
        RenderUtil.drawEntityCircle(target, (float) radius.getValue(), (int) points.getValue(), ColorUtil.darker(color, 0.2F).getRGB());
        RenderUtil.drawEntityCircle(target, (float) radius.getValue(), (int) points.getValue(), color.getRGB());
        RenderUtil.disableRenderState();
    }

    @Override
    public void onDisabled() { target = null; targetYaw = Float.NaN; }

    public static class Vec2d {
        public final double x, y;
        public Vec2d(double x, double y) { this.x = x; this.y = y; }
    }
}