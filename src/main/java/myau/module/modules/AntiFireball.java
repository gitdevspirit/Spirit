package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.management.RotationState;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.network.play.client.C0APacketAnimation;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AntiFireball extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final ArrayList<EntityFireball> farList  = new ArrayList<>();
    private final ArrayList<EntityFireball> nearList = new ArrayList<>();
    private EntityFireball target = null;

    public final SliderSetting   range      = new SliderSetting("Range",       5.0, 3.0, 8.0, 0.1);
    public final SliderSetting   fov        = new SliderSetting("FOV",         360,   1, 360,   1);
    public final BooleanSetting  rotations  = new BooleanSetting("Rotations",  true);
    public final BooleanSetting  swing      = new BooleanSetting("Swing",      true);
    public final DropdownSetting moveFix    = new DropdownSetting("Move Fix",  1, "NONE", "SILENT", "STRICT");
    public final DropdownSetting showTarget = new DropdownSetting("Show Target", 0, "NONE", "DEFAULT", "HUD");

    public AntiFireball() {
        super("AntiFireball", false);
        register(range);
        register(fov);
        register(rotations);
        register(swing);
        register(moveFix);
        register(showTarget);
    }

    private boolean isValidTarget(EntityFireball fb) {
        return !fb.getEntityBoundingBox().hasNaN()
                && RotationUtil.distanceToEntity(fb) <= range.getValue() + 3.0
                && RotationUtil.angleToEntity(fb) <= (float) fov.getValue();
    }

    private void doAttackAnimation() {
        if (swing.getValue()) mc.thePlayer.swingItem();
        else PacketUtil.sendPacket(new C0APacketAnimation());
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (isEnabled() && event.getType() == EventType.PRE) {
            List<EntityFireball> fireballs = mc.theWorld.loadedEntityList.stream()
                    .filter(e -> e instanceof EntityFireball)
                    .map(e -> (EntityFireball) e)
                    .collect(Collectors.toList());
            farList.removeIf(fb -> !fireballs.contains(fb));
            nearList.removeIf(fb -> !fireballs.contains(fb));
            for (EntityFireball fb : fireballs) {
                if (!farList.contains(fb) && !nearList.contains(fb)) {
                    if (RotationUtil.distanceToEntity(fb) > 3.0) farList.add(fb);
                    else nearList.add(fb);
                }
            }
            target = mc.thePlayer.capabilities.allowFlying ? null
                    : farList.stream().filter(this::isValidTarget)
                            .min(Comparator.comparingDouble(RotationUtil::distanceToEntity)).orElse(null);
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (isEnabled() && event.getType() == EventType.PRE && TeamUtil.isEntityLoaded(target)) {
            float[] rots = RotationUtil.getRotationsToBox(target.getEntityBoundingBox(), event.getYaw(), event.getPitch(), 180.0F, 0.0F);
            if (rotations.getValue() && !ItemUtil.isHoldingNonEmpty() && !ItemUtil.isUsingBow() && !ItemUtil.hasHoldItem()) {
                event.setRotation(rots[0], rots[1], 0);
                event.setPervRotation(moveFix.getIndex() != 0 ? rots[0] : mc.thePlayer.rotationYaw, 0);
            }
            if (!Myau.playerStateManager.attacking && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                doAttackAnimation();
                if (RotationUtil.distanceToEntity(target) <= range.getValue())
                    PacketUtil.sendPacket(new C02PacketUseEntity(target, Action.ATTACK));
                PlayerUtil.attackEntity(target);
            }
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (isEnabled() && moveFix.getIndex() == 1
                && RotationState.isActived()
                && RotationState.getPriority() == 0.0F
                && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (isEnabled() && showTarget.getIndex() != 0 && TeamUtil.isEntityLoaded(target)) {
            Color color;
            switch (showTarget.getIndex()) {
                case 1:
                    double dist = (target.posX - target.lastTickPosX) * (mc.thePlayer.posX - target.posX)
                            + (target.posY - target.lastTickPosY) * (mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - target.posY - target.height / 2.0)
                            + (target.posZ - target.lastTickPosZ) * (mc.thePlayer.posZ - target.posZ);
                    color = dist < 0.0 ? new Color(16733525) : new Color(5635925);
                    break;
                default:
                    color = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
            }
            RenderUtil.enableRenderState();
            RenderUtil.drawEntityBox(target, color.getRed(), color.getGreen(), color.getBlue());
            RenderUtil.disableRenderState();
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        farList.clear();
        nearList.clear();
    }
}
