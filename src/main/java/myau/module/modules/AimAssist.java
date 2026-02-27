package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();

    private float smoothedYaw   = Float.NaN;
    private float smoothedPitch = Float.NaN;

    // 0 = ASSIST  — moves your visible crosshair toward the target
    // 1 = SILENT  — rotates silently server-side, crosshair stays where you look
    public final DropdownSetting mode      = register(new DropdownSetting("Mode",      0, "ASSIST", "SILENT"));
    public final SliderSetting   hSpeed    = register(new SliderSetting("H-Speed",     2.0, 0.0, 10.0, 0.1));
    public final SliderSetting   vSpeed    = register(new SliderSetting("V-Speed",     0.0, 0.0, 10.0, 0.1));
    public final SliderSetting   smoothing = register(new SliderSetting("Smoothing",   70,  0,   100,  1));
    public final SliderSetting   range     = register(new SliderSetting("Range",       4.5, 3.0, 8.0,  0.1));
    public final SliderSetting   fov       = register(new SliderSetting("FOV",         90,  30,  360,  1));
    public final BooleanSetting  weaponOnly= register(new BooleanSetting("Weapons Only", true));
    public final BooleanSetting  allowTools= register(new BooleanSetting("Allow Tools",  false));
    public final BooleanSetting  botChecks = register(new BooleanSetting("Bot Check",    true));
    public final BooleanSetting  team      = register(new BooleanSetting("Teams",        true));

    public AimAssist() {
        super("AimAssist", false);
    }

    @Override
    public void onDisabled() {
        smoothedYaw   = Float.NaN;
        smoothedPitch = Float.NaN;
    }

    // ── Target validation ─────────────────────────────────────────────────────

    private boolean isValidTarget(EntityPlayer p) {
        if (p == mc.thePlayer || p == mc.thePlayer.ridingEntity) return false;
        if (p == mc.getRenderViewEntity() || p == mc.getRenderViewEntity().ridingEntity) return false;
        if (p.deathTime > 0) return false;
        if (RotationUtil.distanceToEntity(p) > range.getValue()) return false;
        if (RotationUtil.angleToEntity(p) > (float) fov.getValue()) return false;
        if (RotationUtil.rayTrace(p) != null) return false;
        if (TeamUtil.isFriend(p)) return false;
        return (!team.getValue() || !TeamUtil.isSameTeam(p))
                && (!botChecks.getValue() || !TeamUtil.isBot(p));
    }

    private boolean isInReach(EntityPlayer p) {
        Reach reach = (Reach) Myau.moduleManager.modules.get(Reach.class);
        double distance = reach.isEnabled() ? reach.range.getValue() : 3.0;
        return RotationUtil.distanceToEntity(p) <= distance;
    }

    private boolean isLookingAtBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    // ── Shared smooth rotation ─────────────────────────────────────────────────

    private float[] advanceSmoothed(AxisAlignedBB box) {
        if (Float.isNaN(smoothedYaw))   smoothedYaw   = mc.thePlayer.rotationYaw;
        if (Float.isNaN(smoothedPitch)) smoothedPitch = mc.thePlayer.rotationPitch;

        float[] ideal = RotationUtil.getRotationsToBox(box, smoothedYaw, smoothedPitch, 180f, 0f);

        float sm     = (float) smoothing.getValue() / 100f;
        float lerpT  = MathHelper.clamp_float(
                (1f - sm * 0.95f) + RandomUtil.nextFloat(-0.01f, 0.01f), 0.04f, 1.0f);

        float yawDiff   = MathHelper.wrapAngleTo180_float(ideal[0] - smoothedYaw);
        float pitchDiff = ideal[1] - smoothedPitch;

        // Cap per-tick delta by speed sliders
        float maxH = (float) hSpeed.getValue();
        float maxV = (float) vSpeed.getValue();
        yawDiff   = MathHelper.clamp_float(yawDiff * lerpT, -maxH, maxH);
        pitchDiff = maxV > 0 ? MathHelper.clamp_float(pitchDiff * lerpT, -maxV, maxV) : 0f;

        smoothedYaw   = RotationUtil.quantizeAngle(smoothedYaw   + yawDiff);
        smoothedPitch = RotationUtil.quantizeAngle(
                MathHelper.clamp_float(smoothedPitch + pitchDiff, -90f, 90f));

        return new float[]{ smoothedYaw, smoothedPitch };
    }

    // ── ASSIST mode ────────────────────────────────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || mode.getIndex() != 0) return;
        if (event.getType() != EventType.POST || mc.currentScreen != null) return;
        if (!weaponOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant()
                || (allowTools.getValue() && ItemUtil.isHoldingTool())) {

            boolean attacking = PlayerUtil.isAttacking();
            if (!attacking || !isLookingAtBlock()) {
                if (attacking || !timer.hasTimeElapsed(350L)) {
                    List<EntityPlayer> inRange = mc.theWorld.loadedEntityList.stream()
                            .filter(e -> e instanceof EntityPlayer).map(e -> (EntityPlayer) e)
                            .filter(this::isValidTarget)
                            .sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                            .collect(Collectors.toList());

                    if (inRange.isEmpty()) { smoothedYaw = Float.NaN; smoothedPitch = Float.NaN; return; }
                    if (inRange.stream().anyMatch(this::isInReach)) inRange.removeIf(p -> !isInReach(p));

                    EntityPlayer player = inRange.get(0);
                    if (RotationUtil.distanceToEntity(player) <= 0.0) return;

                    AxisAlignedBB bb = player.getEntityBoundingBox()
                            .expand(player.getCollisionBorderSize(), player.getCollisionBorderSize(), player.getCollisionBorderSize());
                    float[] r = advanceSmoothed(bb);
                    Myau.rotationManager.setRotation(r[0], r[1], 0, false);
                }
            }
        }
    }

    // ── SILENT mode ────────────────────────────────────────────────────────────

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || mode.getIndex() != 1) return;
        if (event.getType() != EventType.PRE || mc.currentScreen != null) return;
        if (!weaponOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant()
                || (allowTools.getValue() && ItemUtil.isHoldingTool())) {

            List<EntityPlayer> inRange = mc.theWorld.loadedEntityList.stream()
                    .filter(e -> e instanceof EntityPlayer).map(e -> (EntityPlayer) e)
                    .filter(this::isValidTarget)
                    .sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                    .collect(Collectors.toList());

            if (inRange.isEmpty()) { smoothedYaw = Float.NaN; smoothedPitch = Float.NaN; return; }

            EntityPlayer player = inRange.get(0);
            if (RotationUtil.distanceToEntity(player) <= 0.0) return;

            AxisAlignedBB bb = player.getEntityBoundingBox()
                    .expand(player.getCollisionBorderSize(), player.getCollisionBorderSize(), player.getCollisionBorderSize());
            float[] r = advanceSmoothed(bb);

            // Silent: update the server-sent rotation without touching the visible crosshair
            event.setRotation(r[0], r[1], 1);
        }
    }

    // ── Key press (resets assist timer) ───────────────────────────────────────

    @EventTarget
    public void onPress(KeyEvent event) {
        if (event.getKey() == mc.gameSettings.keyBindAttack.getKeyCode()
                && !Myau.moduleManager.modules.get(AutoClicker.class).isEnabled()) {
            timer.reset();
        }
    }
}
