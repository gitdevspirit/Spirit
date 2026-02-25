package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();

    // Smoothed rotation state — reset when we lose a target
    private float smoothYaw   = Float.NaN;
    private float smoothPitch = Float.NaN;

    public final SliderSetting  hSpeed     = new SliderSetting("H-Speed",    2.0, 0.0, 10.0, 0.1);
    public final SliderSetting  vSpeed     = new SliderSetting("V-Speed",    0.0, 0.0, 10.0, 0.1);
    public final SliderSetting  smoothing  = new SliderSetting("Smoothing",  70,  0,   100,   1);
    public final SliderSetting  range      = new SliderSetting("Range",      4.5, 3.0, 8.0,  0.1);
    public final SliderSetting  fov        = new SliderSetting("FOV",        90,  30,  360,   1);
    public final BooleanSetting weaponOnly = new BooleanSetting("Weapons Only", true);
    public final BooleanSetting allowTools = new BooleanSetting("Allow Tools",  false);
    public final BooleanSetting botChecks  = new BooleanSetting("Bot Check",    true);
    public final BooleanSetting team       = new BooleanSetting("Teams",        true);

    public AimAssist() {
        super("AimAssist", false);
        register(hSpeed);
        register(vSpeed);
        register(smoothing);
        register(range);
        register(fov);
        register(weaponOnly);
        register(allowTools);
        register(botChecks);
        register(team);
    }

    @Override
    public void onDisabled() {
        smoothYaw   = Float.NaN;
        smoothPitch = Float.NaN;
    }

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

    /**
     * Computes the exact yaw/pitch to the nearest point on the target's hitbox.
     * Using the nearest point (rather than centre) means the camera never has to
     * overshoot or snap back when the player is already close to the hitbox edge.
     */
    private float[] getExactRotations(EntityPlayer target) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);

        float border = target.getCollisionBorderSize();
        AxisAlignedBB bb = target.getEntityBoundingBox().expand(border, border, border);

        // Clamp the eye position to the box to get the closest point on the hitbox
        double tx = MathHelper.clamp_double(eyes.xCoord, bb.minX, bb.maxX);
        // Aim for upper-body (75% height) when we're above, lower-body (25%) when below,
        // otherwise straight through — gives a stable centre-mass lock with no jitter
        double ty;
        double upperY = bb.minY + 0.75 * (bb.maxY - bb.minY);
        double lowerY = bb.minY + 0.25 * (bb.maxY - bb.minY);
        if (eyes.yCoord >= upperY)      ty = upperY;
        else if (eyes.yCoord <= lowerY) ty = lowerY;
        else                            ty = eyes.yCoord; // already inside → no pitch needed

        double tz = MathHelper.clamp_double(eyes.zCoord, bb.minZ, bb.maxZ);

        double dx = tx - eyes.xCoord;
        double dy = ty - eyes.yCoord;
        double dz = tz - eyes.zCoord;

        double horizDist = Math.sqrt(dx * dx + dz * dz);
        float exactYaw   = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float exactPitch = (float)(-Math.atan2(dy, horizDist) * 180.0 / Math.PI);

        return new float[]{ exactYaw, exactPitch };
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.POST || mc.currentScreen != null) return;
        if (!weaponOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant()
                || (allowTools.getValue() && ItemUtil.isHoldingTool())) {

            boolean attacking = PlayerUtil.isAttacking();
            if (!attacking || !isLookingAtBlock()) {
                if (attacking || !timer.hasTimeElapsed(350L)) {

                    List<EntityPlayer> inRange = mc.theWorld.loadedEntityList.stream()
                            .filter(e -> e instanceof EntityPlayer)
                            .map(e -> (EntityPlayer) e)
                            .filter(this::isValidTarget)
                            .sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                            .collect(Collectors.toList());

                    if (inRange.isEmpty()) {
                        smoothYaw   = Float.NaN;
                        smoothPitch = Float.NaN;
                        return;
                    }

                    if (inRange.stream().anyMatch(this::isInReach))
                        inRange.removeIf(p -> !isInReach(p));

                    EntityPlayer target = inRange.get(0);
                    if (RotationUtil.distanceToEntity(target) <= 0.0) return;

                    // ── Exact rotation to the nearest hitbox point ────────────────
                    float[] exact = getExactRotations(target);
                    float exactYaw   = exact[0];
                    float exactPitch = MathHelper.clamp_float(exact[1], -90f, 90f);

                    // ── Seed smooth state on first tick with this target ──────────
                    if (Float.isNaN(smoothYaw)) {
                        smoothYaw   = mc.thePlayer.rotationYaw;
                        smoothPitch = mc.thePlayer.rotationPitch;
                    }

                    // ── Single clean lerp — no double-smoothing ───────────────────
                    // smoothing=0   → lerpFactor=1.0  (instant lock, no delay)
                    // smoothing=100 → lerpFactor=0.08 (very gradual)
                    // The curve is tuned so low values still feel responsive while
                    // high values give a human arc without getting stuck.
                    double s = smoothing.getValue() / 100.0;
                    float lerpFactor = (float)(1.0 - s * s * 0.92); // quadratic so low end stays snappy

                    // Shortest-path yaw delta (handles 180° wrap)
                    float dyaw = MathHelper.wrapAngleTo180_float(exactYaw - smoothYaw);
                    smoothYaw   = smoothYaw   + dyaw              * lerpFactor;
                    smoothPitch = smoothPitch + (exactPitch - smoothPitch) * lerpFactor;

                    // ── Clamp per-tick movement to h/v speed sliders ──────────────
                    float maxH = (float) hSpeed.getValue();
                    float maxV = (float) vSpeed.getValue();

                    float dY = MathHelper.wrapAngleTo180_float(smoothYaw - mc.thePlayer.rotationYaw);
                    float dP = smoothPitch - mc.thePlayer.rotationPitch;

                    dY = MathHelper.clamp_float(dY, -maxH, maxH);
                    if (maxV > 0f) dP = MathHelper.clamp_float(dP, -maxV, maxV);
                    else           dP = 0f;

                    float finalYaw   = mc.thePlayer.rotationYaw   + dY;
                    float finalPitch = MathHelper.clamp_float(mc.thePlayer.rotationPitch + dP, -90f, 90f);

                    Myau.rotationManager.setRotation(finalYaw, finalPitch, 0, false);
                }
            }
        }
    }

    @EventTarget
    public void onPress(KeyEvent event) {
        if (event.getKey() == mc.gameSettings.keyBindAttack.getKeyCode()
                && !Myau.moduleManager.modules.get(AutoClicker.class).isEnabled()) {
            timer.reset();
        }
    }
}
