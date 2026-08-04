package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render3DEvent;
import myau.events.UpdateEvent;
import myau.management.MovementFix;
import myau.management.RotationState;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AimAssist v2 — reworked entirely from the ground up on top of a
 * keystrokesmod-derived reference implementation.
 *
 * Three modes, each running on the hook that actually suits it:
 *  - Regular / Lock-on run on {@link Render3DEvent} (every render frame,
 *    decoupled from the 20 TPS tick — buttery smooth, matches the reference's
 *    RenderTickEvent-driven design) and move the real camera.
 *  - Silent runs on {@link UpdateEvent} PRE (the earliest hook before the
 *    packet-rotation swap) and never touches the camera — it reports a fake
 *    rotation to the server via RotationState + MovementFix, exactly like
 *    Clutch does.
 */
public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int MODE_REGULAR = 0;
    private static final int MODE_SILENT  = 1;
    private static final int MODE_LOCKON  = 2;
    private static final float LOCK_ON_LOCKED_DEGREES = 2.0f;

    public final DropdownSetting mode       = register(new DropdownSetting("Mode", 0, "Regular", "Silent", "Lock-on"));
    public final DropdownSetting targetMode = register(new DropdownSetting("Target Mode", 0, "Single", "Switch"));
    public final SliderSetting   speed      = register(new SliderSetting("Speed", 10, 1, 30, 1));
    public final SliderSetting   multipointHorizontal = register(new SliderSetting("Multipoint Horizontal", 0, 0, 100, 1));
    public final SliderSetting   multipointVertical   = register(new SliderSetting("Multipoint Vertical", 0, 0, 100, 1));
    public final SliderSetting   randomization = register(new SliderSetting("Randomization", 20, 0, 100, 1));
    public final SliderSetting   fov   = register(new SliderSetting("FOV", 90, 15, 360, 1));
    public final SliderSetting   range = register(new SliderSetting("Range", 4.5, 0.0, 5.0, 0.1));
    public final DropdownSetting sortMode = register(new DropdownSetting("Sort", 1, "Health", "Angle", "Hurt Time", "Distance"));

    public final BooleanSetting ignoreBehindWalls    = register(new BooleanSetting("Ignore Behind Walls", false));
    public final BooleanSetting ignoreBehindEntities = register(new BooleanSetting("Ignore Behind Entities", false));
    public final BooleanSetting aimInvis        = register(new BooleanSetting("Aim Invisible", false));
    public final BooleanSetting clickAim        = register(new BooleanSetting("Require Mouse", true));
    public final BooleanSetting ignoreTeammates = register(new BooleanSetting("Ignore Teammates", true));
    public final BooleanSetting stopWhenBreaking = register(new BooleanSetting("Stop When Breaking", false));
    public final BooleanSetting keepMoveDirection = register(new BooleanSetting(
            "Keep Move Direction", true, () -> mode.getIndex() == MODE_SILENT));
    public final SliderSetting  hoverDelay = register(new SliderSetting(
            "Hover Delay", 100, 0, 500, 1, () -> stopWhenBreaking.getValue()));
    public final BooleanSetting weaponOnly = register(new BooleanSetting("Weapon Only", false));
    public final BooleanSetting increasedFovWhileLocked = register(new BooleanSetting(
            "Increased FOV While Locked", true, () -> targetMode.getIndex() == 0));

    private long miningStartTime = -1L;
    private EntityPlayer lockedTarget;
    private EntityPlayer smoothedTargetEntity;
    private long lastSmoothNanoTime = -1L;
    private float lockedYaw   = Float.NaN;
    private float lockedPitch = Float.NaN;

    public AimAssist() { super("AimAssist", false); }

    @Override
    public String[] getSuffix() {
        return new String[]{ mode.getValue() };
    }

    @Override
    public void onDisabled() {
        miningStartTime = -1L;
        lockedTarget = null;
        resetLockOnSmooth();
    }

    private void resetLockOnSmooth() {
        smoothedTargetEntity = null;
        lastSmoothNanoTime = -1L;
        lockedYaw   = Float.NaN;
        lockedPitch = Float.NaN;
    }

    private boolean isLockOnMode() { return mode.getIndex() == MODE_LOCKON; }
    private boolean isSingleTargetMode() { return targetMode.getIndex() == 0; }

    // ── Regular / Lock-on: real camera, once per render frame ──────────────────

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (mode.getIndex() == MODE_SILENT) return;

        if (!conditionsMet()) {
            if (isLockOnMode()) resetLockOnSmooth();
            return;
        }

        EntityPlayer target = getEnemy(false);
        if (target == null) {
            if (isLockOnMode()) resetLockOnSmooth();
            return;
        }

        float partialTicks = event.getPartialTicks();
        float[] rot = isLockOnMode()
                ? getLockOnRotations(target, partialTicks)
                : getRegularRotations(target, partialTicks);
        if (rot == null) return;

        mc.thePlayer.rotationYaw     = rot[0];
        mc.thePlayer.rotationPitch   = rot[1];
        mc.thePlayer.rotationYawHead = rot[0];
    }

    private float[] getRegularRotations(EntityPlayer target, float partialTicks) {
        Vec3 predictedHead = getPredictedHeadCenter(target, partialTicks);
        if (predictedHead == null) return null;

        float baseYaw   = mc.thePlayer.rotationYaw;
        float basePitch = mc.thePlayer.rotationPitch;
        float[] desired = getRotationsToPointExact(predictedHead.xCoord, predictedHead.yCoord, predictedHead.zCoord, baseYaw, basePitch);
        if (desired == null) return null;

        float errorYaw   = MathHelper.wrapAngleTo180_float(desired[0] - baseYaw);
        float errorPitch = desired[1] - basePitch;
        float t      = MathHelper.clamp_float((float) speed.getValue() / 30.0f, 0.0f, 1.0f);
        float factor = 0.08f + t * 0.77f;

        float randScale   = ((float) randomization.getValue() / 100.0f) * 0.30f;
        float jitterYaw    = (float) ((Math.random() - 0.5) * 2.0 * Math.abs(errorYaw)   * randScale);
        float jitterPitch  = (float) ((Math.random() - 0.5) * 2.0 * Math.abs(errorPitch) * randScale);

        float newYaw   = baseYaw   + errorYaw   * factor + jitterYaw;
        float newPitch = clampPitch(basePitch + errorPitch * factor + jitterPitch);
        return new float[]{ newYaw, newPitch };
    }

    private float[] getLockOnRotations(EntityPlayer target, float partialTicks) {
        if (target != smoothedTargetEntity) {
            smoothedTargetEntity = target;
            lastSmoothNanoTime = -1L;
        }

        float deltaSeconds = getFrameDeltaSeconds();
        float speedScale   = 0.7f + ((float) speed.getValue()) / 30.0f * 1.1f;

        Vec3 headCenter = getPredictedHeadCenter(target, partialTicks);
        if (headCenter == null) return null;

        float baseYaw   = !Float.isNaN(lockedYaw)   ? lockedYaw   : mc.thePlayer.rotationYaw;
        float basePitch = !Float.isNaN(lockedPitch) ? lockedPitch : mc.thePlayer.rotationPitch;

        float[] desired = getRotationsToPointExact(headCenter.xCoord, headCenter.yCoord, headCenter.zCoord, baseYaw, basePitch);
        if (desired == null) return null;

        float errorYaw     = MathHelper.wrapAngleTo180_float(desired[0] - baseYaw);
        float errorPitch   = desired[1] - basePitch;
        float angularError = MathHelper.sqrt_float(errorYaw * errorYaw + errorPitch * errorPitch);
        boolean lockedOn    = angularError <= LOCK_ON_LOCKED_DEGREES;

        float yaw, pitch;
        if (lockedOn) {
            yaw = desired[0];
            pitch = desired[1];
        } else {
            float rate   = MathHelper.clamp_float((34.0f + angularError * 3.8f) * speedScale, 34.0f * speedScale, 58.0f);
            float factor = 1.0f - (float) Math.exp(-rate * deltaSeconds);
            yaw   = baseYaw   + errorYaw   * factor;
            pitch = basePitch + errorPitch * factor;
        }
        lockedYaw   = yaw;
        lockedPitch = clampPitch(pitch);
        return new float[]{ lockedYaw, lockedPitch };
    }

    private float getFrameDeltaSeconds() {
        long now = System.nanoTime();
        if (lastSmoothNanoTime < 0L) {
            lastSmoothNanoTime = now;
            return 1.0f / 60.0f;
        }
        float delta = (now - lastSmoothNanoTime) / 1_000_000_000.0f;
        lastSmoothNanoTime = now;
        return MathHelper.clamp_float(delta, 0.001f, 0.05f);
    }

    // ── Silent: fake rotation only, via UpdateEvent + RotationState + MovementFix ──

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mode.getIndex() != MODE_SILENT || !conditionsMet()) return;

        EntityPlayer target = getEnemy(true);
        if (target == null) return;

        Vec3 aimPoint = pickAimPoint(target);
        if (aimPoint == null) return;

        float baseYaw   = event.getYaw();
        float basePitch = event.getPitch();
        float[] rot = RotationUtil.getRotations(
                aimPoint.xCoord - mc.thePlayer.posX,
                aimPoint.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight()),
                aimPoint.zCoord - mc.thePlayer.posZ,
                baseYaw, basePitch, 180.0f, 1.0f - (float) randomization.getValue() / 200.0f);

        if (keepMoveDirection.getValue()) MovementFix.forceMovementFix = true;
        RotationState.applyState(true, rot[0], rot[1], rot[0], 10);
        event.setRotation(rot[0], rot[1], 10);
    }

    /** Picks a point on the target to aim at, honoring the multipoint spread settings. */
    private Vec3 pickAimPoint(EntityPlayer target) {
        AxisAlignedBB bb = target.getEntityBoundingBox();
        double mpH = multipointHorizontal.getValue() / 100.0;
        double mpV = multipointVertical.getValue() / 100.0;

        double width  = bb.maxX - bb.minX;
        double height = bb.maxY - bb.minY;
        double headY  = target.posY + target.getEyeHeight();

        double offsetX = (Math.random() - 0.5) * width  * mpH;
        double offsetZ = (Math.random() - 0.5) * width  * mpH;
        double offsetY = (Math.random() - 0.5) * height * mpV;

        double tx = (bb.minX + bb.maxX) / 2.0 + offsetX;
        double tz = (bb.minZ + bb.maxZ) / 2.0 + offsetZ;
        double ty = MathHelper.clamp_double(headY + offsetY, bb.minY + 0.1, bb.maxY - 0.1);

        Vec3 point = new Vec3(tx, ty, tz);
        if ((ignoreBehindWalls.getValue() || ignoreBehindEntities.getValue()) && !hasValidAimPoint(target, point)) {
            // Fall back to the exact head center — still worth attacking even
            // if the randomized multipoint spot happened to be obstructed.
            Vec3 fallback = new Vec3((bb.minX + bb.maxX) / 2.0, MathHelper.clamp_double(headY, bb.minY + 0.1, bb.maxY - 0.1), (bb.minZ + bb.maxZ) / 2.0);
            return hasValidAimPoint(target, fallback) ? fallback : null;
        }
        return point;
    }

    private boolean hasValidAimPoint(EntityPlayer target, Vec3 point) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);

        if (ignoreBehindWalls.getValue()) {
            MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(eyes, point, false, true, false);
            if (blockHit != null) return false;
        }

        if (ignoreBehindEntities.getValue()) {
            for (Object obj : mc.theWorld.playerEntities) {
                EntityPlayer other = (EntityPlayer) obj;
                if (other == target || other == mc.thePlayer) continue;
                AxisAlignedBB expanded = other.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
                MovingObjectPosition hit = expanded.calculateIntercept(eyes, point);
                if (hit != null) return false;
            }
        }
        return true;
    }

    // ── Shared geometry helpers ──────────────────────────────────────────────

    private Vec3 getPredictedHeadCenter(EntityPlayer entity, float partialTicks) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB bb = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);

        double headCenterY = y + entity.getEyeHeight() + entity.height * 0.06;
        headCenterY = MathHelper.clamp_double(headCenterY, bb.minY + 0.05, bb.maxY - 0.05);

        double velX = entity.posX - entity.lastTickPosX;
        double velY = entity.posY - entity.lastTickPosY;
        double velZ = entity.posZ - entity.lastTickPosZ;

        double distSq = mc.thePlayer.getDistanceSqToEntity(entity);
        float predTicks = (float) Math.min(0.8, Math.sqrt(distSq) * 0.07);

        return new Vec3(x + velX * predTicks, headCenterY + velY * predTicks, z + velZ * predTicks);
    }

    private float[] getRotationsToPointExact(double x, double y, double z, float baseYaw, float basePitch) {
        double deltaX = x - mc.thePlayer.posX;
        double deltaZ = z - mc.thePlayer.posZ;
        double deltaY = y - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double horizDistSq = deltaX * deltaX + deltaZ * deltaZ;

        if (horizDistSq < 1.0E-12) {
            float pitch = (float) (-(Math.atan2(deltaY, 0.0) * 57.29577951308232));
            return new float[]{ baseYaw, clampPitch(pitch) };
        }

        float targetYaw   = (float) (Math.atan2(deltaZ, deltaX) * 57.29577951308232) - 90.0f;
        double horizDist  = Math.sqrt(horizDistSq);
        float targetPitch = (float) (-(Math.atan2(deltaY, horizDist) * 57.29577951308232));
        return new float[]{ baseYaw + MathHelper.wrapAngleTo180_float(targetYaw - baseYaw), clampPitch(targetPitch) };
    }

    private float clampPitch(float pitch) {
        return MathHelper.clamp_float(pitch, -90.0f, 90.0f);
    }

    // ── Targeting ─────────────────────────────────────────────────────────────

    private EntityPlayer getEnemy(boolean silentMode) {
        if (isSingleTargetMode()) {
            boolean expandedFov = increasedFovWhileLocked.getValue() && lockedTarget != null;
            if (lockedTarget != null && isValidTarget(lockedTarget, silentMode, expandedFov)) {
                return lockedTarget;
            }
        }
        EntityPlayer best = findBestEnemy(silentMode);
        lockedTarget = best;
        return best;
    }

    private EntityPlayer findBestEnemy(boolean silentMode) {
        int fovVal = (int) fov.getValue();

        List<EntityPlayer> candidates = new ArrayList<>();
        for (Object obj : mc.theWorld.playerEntities) {
            EntityPlayer p = (EntityPlayer) obj;
            if (passesTargetFilters(p, fovVal)) candidates.add(p);
        }
        if (candidates.isEmpty()) return null;

        Comparator<EntityPlayer> primary = getSortComparator();
        candidates.sort(primary.thenComparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p)));

        if (!silentMode || !(ignoreBehindWalls.getValue() || ignoreBehindEntities.getValue())) {
            return candidates.get(0);
        }
        for (EntityPlayer candidate : candidates) {
            if (pickAimPoint(candidate) != null) return candidate;
        }
        return null;
    }

    private boolean isValidTarget(EntityPlayer target, boolean silentMode, boolean expandedFov) {
        int fovVal = expandedFov ? 360 : (int) fov.getValue();
        return passesTargetFilters(target, fovVal)
                && (!silentMode || !(ignoreBehindWalls.getValue() || ignoreBehindEntities.getValue()) || pickAimPoint(target) != null);
    }

    private boolean passesTargetFilters(EntityPlayer target, int fovVal) {
        if (target == mc.thePlayer || target.deathTime != 0) return false;
        if (TeamUtil.isFriend(target)) return false;
        if (ignoreTeammates.getValue() && TeamUtil.isSameTeam(target)) return false;
        if (!aimInvis.getValue() && target.isInvisible()) return false;
        if (TeamUtil.isBot(target)) return false;
        if (RotationUtil.distanceToBox(target.getEntityBoundingBox()) > range.getValue()) return false;
        if (fovVal != 360 && RotationUtil.angleToEntity(target) > fovVal) return false;
        return true;
    }

    private Comparator<EntityPlayer> getSortComparator() {
        switch (sortMode.getIndex()) {
            case 0: return Comparator.comparingDouble(p -> p.getHealth() + p.getAbsorptionAmount());
            case 2: return Comparator.comparingInt(p -> p.hurtTime);
            case 3: return Comparator.comparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p));
            default: return Comparator.comparingDouble(RotationUtil::angleToEntity); // Angle
        }
    }

    private boolean conditionsMet() {
        if (mc.currentScreen != null) return false;
        if (weaponOnly.getValue() && !ItemUtil.isHoldingSword()) return false;
        if (clickAim.getValue() && !Mouse.isButtonDown(0)) return false;

        if (stopWhenBreaking.getValue() && isBreakingBlock()) {
            if (miningStartTime == -1L) miningStartTime = System.currentTimeMillis();
            long elapsed = System.currentTimeMillis() - miningStartTime;
            if (elapsed >= (long) hoverDelay.getValue()) return false;
        } else {
            miningStartTime = -1L;
        }
        return true;
    }

    private boolean isBreakingBlock() {
        return mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && Mouse.isButtonDown(0);
    }
}
