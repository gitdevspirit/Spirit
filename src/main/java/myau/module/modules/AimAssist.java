package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.ClientRotationEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.management.RotationState;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AimAssist — Ported from KeystrokesMod with Myau codebase style
 */
public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // ── Modes ──────────────────────────────────────────────────────────────────
    private static final int MODE_REGULAR = 0;
    private static final int MODE_SILENT = 1;
    private static final int MODE_LOCK_ON = 2;
    
    private static final int TARGET_SINGLE = 0;
    private static final int TARGET_SWITCH = 1;
    
    private static final int SORT_HEALTH = 0;
    private static final int SORT_ANGLE = 1;
    private static final int SORT_HURT_TIME = 2;
    private static final int SORT_DISTANCE = 3;

    // ── Settings ──────────────────────────────────────────────────────────────
    public final DropdownSetting mode = register(new DropdownSetting("Mode", 0, "Regular", "Silent", "Lock-on"));
    public final DropdownSetting targetMode = register(new DropdownSetting("Target Mode", 0, "Single", "Switch"));
    public final SliderSetting speed = register(new SliderSetting("Speed", 10, 1, 30, 1));
    public final SliderSetting multipointHorizontal = register(new SliderSetting("Multipoint Horizontal", 0, 0, 100, 1));
    public final SliderSetting multipointVertical = register(new SliderSetting("Multipoint Vertical", 0, 0, 100, 1));
    public final SliderSetting randomization = register(new SliderSetting("Randomization", 20, 0, 100, 1));
    public final SliderSetting fov = register(new SliderSetting("FOV", 90, 15, 360, 1));
    public final SliderSetting range = register(new SliderSetting("Range", 4.5, 0.0, 5.0, 0.1));
    public final DropdownSetting sortMode = register(new DropdownSetting("Sort", 1, "Health", "Angle", "Hurt Time", "Distance"));

    public final BooleanSetting ignoreBehindWalls = register(new BooleanSetting("Ignore Behind Walls", false));
    public final BooleanSetting ignoreBehindEntities = register(new BooleanSetting("Ignore Behind Entities", false));
    public final BooleanSetting aimInvis = register(new BooleanSetting("Aim Invisible", false));
    public final BooleanSetting clickAim = register(new BooleanSetting("Require Mouse", true));
    public final BooleanSetting ignoreTeammates = register(new BooleanSetting("Ignore Teammates", true));
    public final BooleanSetting stopWhenBreaking = register(new BooleanSetting("Stop When Breaking", false));
    public final BooleanSetting keepMoveDirection = register(new BooleanSetting("Keep Move Direction", true));
    public final SliderSetting hoverDelay = register(new SliderSetting("Hover Delay", 100, 0, 500, 10));
    public final BooleanSetting weaponOnly = register(new BooleanSetting("Weapon Only", false));
    public final BooleanSetting increasedFovWhileLocked = register(new BooleanSetting("Increased FOV While Locked", true));

    // ── State ─────────────────────────────────────────────────────────────────
    private long miningStartTime = -1L;
    private Entity lockedTarget = null;
    private Entity smoothedTargetEntity = null;
    private long lastSmoothNanoTime = -1L;
    private float lockedYaw = Float.NaN;
    private float lockedPitch = Float.NaN;
    private boolean regularAppliedThisRenderFrame = false;

    private static final float LOCK_ON_ERROR_LOCKED_DEGREES = 2.0F;

    public AimAssist() {
        super("AimAssist", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{ mode.getValue() };
    }

    @Override
    public void onDisabled() {
        miningStartTime = -1L;
        lockedTarget = null;
        resetLockOnSmooth();
        RotationState.applyState(false, 0, 0, 0, 0);
    }

    private void resetLockOnSmooth() {
        smoothedTargetEntity = null;
        lastSmoothNanoTime = -1L;
        lockedYaw = Float.NaN;
        lockedPitch = Float.NaN;
    }

    // ── Render Tick (for Regular and Lock-on) ────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            regularAppliedThisRenderFrame = false;
            return;
        }
        if (event.phase == TickEvent.Phase.END) {
            if (isLockOnMode()) {
                applyAim(false);
                return;
            }
            if (mode.getIndex() == MODE_REGULAR && !regularAppliedThisRenderFrame) {
                applyAim(false);
                regularAppliedThisRenderFrame = true;
            }
        }
    }

    // ── Update (for Lock-on and Regular fallback) ────────────────────────────

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        
        if (isLockOnMode() || mode.getIndex() == MODE_REGULAR) {
            // Handled in onTick
            return;
        }
        applyAim(false);
    }

    // ── Client Rotation Event (for Silent mode) ──────────────────────────────

    @EventTarget
    public void onClientRotation(ClientRotationEvent event) {
        if (mode.getIndex() != MODE_SILENT || !conditionsMet()) {
            return;
        }
        
        // Check if KillAura is active
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.hasTarget()) {
            return;
        }

        Entity target = getEnemy(true);
        if (target == null) {
            return;
        }

        float[] rot = getTargetRotations(target, true, event);
        if (rot == null) return;

        if (keepMoveDirection.getValue()) {
            // Movement fix would go here
        }
        
        event.setRotation(rot[0], rot[1], 1);
    }

    // ── Core: Apply Aim ───────────────────────────────────────────────────────

    private void applyAim(boolean silentMode) {
        if (!conditionsMet()) {
            if (isLockOnMode()) {
                resetLockOnSmooth();
            }
            return;
        }

        Entity target = getEnemy(silentMode);
        if (target == null) {
            if (isLockOnMode()) {
                resetLockOnSmooth();
            }
            return;
        }

        float[] rot = getTargetRotations(target, silentMode, null);
        if (rot == null) return;

        if (silentMode) {
            return;
        }

        mc.thePlayer.rotationYaw = rot[0];
        mc.thePlayer.rotationPitch = rot[1];
        mc.thePlayer.rotationYawHead = rot[0];
    }

    // ── Core: Get Target Rotations ────────────────────────────────────────────

    private float[] getTargetRotations(Entity target, boolean silentMode, ClientRotationEvent event) {
        int speedVal = (int) speed.getValue();
        double mpH = multipointHorizontal.getValue();
        double mpV = multipointVertical.getValue();
        float randPercent = isLockOnMode() ? 0.0F : (float) randomization.getValue();
        boolean useBackup = ignoreBehindWalls.getValue() || ignoreBehindEntities.getValue();

        if (isLockOnMode()) {
            return getLockOnRotations(target);
        }

        Vec3 predictedHead = getPredictedHeadCenter(target);
        if (predictedHead != null) {
            float baseYaw = silentMode && event != null ? event.getYaw() : mc.thePlayer.rotationYaw;
            float basePitch = silentMode && event != null ? event.getPitch() : mc.thePlayer.rotationPitch;
            float[] desiredRot = getRotationsToPointExact(
                predictedHead.xCoord, predictedHead.yCoord, predictedHead.zCoord, baseYaw, basePitch
            );
            
            if (desiredRot != null) {
                float errorYaw = MathHelper.wrapAngleTo180_float(desiredRot[0] - baseYaw);
                float errorPitch = desiredRot[1] - basePitch;
                float t = MathHelper.clamp_float(speedVal / 30.0F, 0.0F, 1.0F);
                float factor = 0.08F + t * 0.77F;
                float randScale = (randPercent / 100.0F) * 0.30F;
                float jitterYaw = (float) ((Math.random() - 0.5) * 2.0 * Math.abs(errorYaw) * randScale);
                float jitterPitch = (float) ((Math.random() - 0.5) * 2.0 * Math.abs(errorPitch) * randScale);
                float newYaw = baseYaw + errorYaw * factor + jitterYaw;
                float newPitch = clampPitch(basePitch + errorPitch * factor + jitterPitch);
                return new float[]{newYaw, newPitch};
            }
        }

        // Fallback using RotationUtil
        if (silentMode && event != null) {
            return getRotationsToEntity(target, event.getYaw(), event.getPitch(), mpH, mpV, randPercent);
        }
        return getRotationsToEntity(target, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, mpH, mpV, randPercent);
    }

    // ── Lock-on Rotations ─────────────────────────────────────────────────────

    private float[] getLockOnRotations(Entity target) {
        if (target == null || mc.thePlayer == null) {
            return null;
        }

        if (target != smoothedTargetEntity) {
            smoothedTargetEntity = target;
            lastSmoothNanoTime = -1L;
        }

        float deltaSeconds = getFrameDeltaSeconds();
        float speedScale = 0.7F + ((int) speed.getValue()) / 30.0F * 1.1F;

        Vec3 headCenter = getPredictedHeadCenter(target);
        if (headCenter == null) {
            return null;
        }

        float baseYaw = !Float.isNaN(lockedYaw) ? lockedYaw : mc.thePlayer.rotationYaw;
        float basePitch = !Float.isNaN(lockedPitch) ? lockedPitch : mc.thePlayer.rotationPitch;

        float[] desiredRot = getRotationsToPointExact(
            headCenter.xCoord, headCenter.yCoord, headCenter.zCoord, baseYaw, basePitch
        );
        if (desiredRot == null) {
            return null;
        }

        float errorYaw = MathHelper.wrapAngleTo180_float(desiredRot[0] - baseYaw);
        float errorPitch = desiredRot[1] - basePitch;
        float angularError = (float) MathHelper.sqrt_double(errorYaw * errorYaw + errorPitch * errorPitch);
        boolean lockedOn = angularError <= LOCK_ON_ERROR_LOCKED_DEGREES;

        float yaw, pitch;
        if (lockedOn) {
            yaw = desiredRot[0];
            pitch = desiredRot[1];
        } else {
            float viewRate = getAdaptiveLockOnRate(34.0F, 3.8F, angularError, speedScale);
            float viewFactor = getExpSmoothFactor(viewRate, deltaSeconds);
            yaw = baseYaw + errorYaw * viewFactor;
            pitch = basePitch + errorPitch * viewFactor;
        }
        lockedYaw = yaw;
        lockedPitch = clampPitch(pitch);
        return new float[]{lockedYaw, lockedPitch};
    }

    // ── Helper Methods ────────────────────────────────────────────────────────

    private float getFrameDeltaSeconds() {
        long now = System.nanoTime();
        if (lastSmoothNanoTime < 0L) {
            lastSmoothNanoTime = now;
            return 1.0F / 60.0F;
        }
        float delta = (now - lastSmoothNanoTime) / 1_000_000_000.0F;
        lastSmoothNanoTime = now;
        return MathHelper.clamp_float(delta, 0.001F, 0.05F);
    }

    private float getExpSmoothFactor(float rate, float deltaSeconds) {
        return 1.0F - (float) Math.exp(-rate * deltaSeconds);
    }

    private float getAdaptiveLockOnRate(float baseRate, float errorBoost, float angularError, float speedScale) {
        float rate = (baseRate + angularError * errorBoost) * speedScale;
        return MathHelper.clamp_float(rate, baseRate * speedScale, 58.0F);
    }

    private Vec3 getPredictedHeadCenter(Entity entity) {
        double x = entity.posX;
        double y = entity.posY;
        double z = entity.posZ;

        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB bb = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);

        double headCenterY;
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            headCenterY = y + living.getEyeHeight() + entity.height * 0.06D;
        } else {
            headCenterY = y + entity.height * 0.925D;
        }
        headCenterY = MathHelper.clamp_double(headCenterY, bb.minY + 0.05D, bb.maxY - 0.05D);

        // Prediction based on velocity
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
            float pitch = (float) (-(Math.atan2(deltaY, 0.0D) * 57.295780181884766D));
            return new float[]{baseYaw, clampPitch(pitch)};
        }

        float targetYaw = (float) (Math.atan2(deltaZ, deltaX) * 57.295780181884766D) - 90.0F;
        double horizDist = MathHelper.sqrt_double(horizDistSq);
        float targetPitch = (float) (-(Math.atan2(deltaY, horizDist) * 57.295780181884766D));
        return new float[]{
            baseYaw + MathHelper.wrapAngleTo180_float(targetYaw - baseYaw),
            clampPitch(targetPitch)
        };
    }

    private float[] getRotationsToEntity(Entity target, float baseYaw, float basePitch, double mpH, double mpV, float randPercent) {
        AxisAlignedBB bb = target.getEntityBoundingBox();
        double width = bb.maxX - bb.minX;
        double height = bb.maxY - bb.minY;
        double headY = target.posY + (target instanceof EntityLivingBase ? ((EntityLivingBase) target).getEyeHeight() : target.height * 0.85);

        double offsetX = (Math.random() - 0.5) * width * (mpH / 100.0);
        double offsetZ = (Math.random() - 0.5) * width * (mpH / 100.0);
        double offsetY = (Math.random() - 0.5) * height * (mpV / 100.0);

        double tx = (bb.minX + bb.maxX) / 2.0 + offsetX;
        double tz = (bb.minZ + bb.maxZ) / 2.0 + offsetZ;
        double ty = MathHelper.clamp_double(headY + offsetY, bb.minY + 0.1, bb.maxY - 0.1);

        return getRotationsToPointExact(tx, ty, tz, baseYaw, basePitch);
    }

    private float clampPitch(float pitch) {
        return MathHelper.clamp_float(pitch, -90.0F, 90.0F);
    }

    // ── Targeting ─────────────────────────────────────────────────────────────

    private Entity getEnemy(boolean silentMode) {
        if (isSingleTargetMode()) {
            boolean expandedFov = increasedFovWhileLocked.getValue() && lockedTarget != null;
            if (lockedTarget != null && isValidTarget(lockedTarget, silentMode, expandedFov)) {
                return lockedTarget;
            }
        }

        Entity best = findBestEnemy(silentMode);
        lockedTarget = best;
        return best;
    }

    private Entity findBestEnemy(boolean silentMode) {
        int fovVal = (int) fov.getValue();
        float viewYaw = mc.thePlayer.rotationYaw;
        if (silentMode) {
            // Silent mode would use server yaw
        }

        List<EntityPlayer> candidates = new ArrayList<>();
        for (Object obj : mc.theWorld.playerEntities) {
            EntityPlayer p = (EntityPlayer) obj;
            if (passesTargetFilters(p, silentMode, fovVal, viewYaw)) {
                candidates.add(p);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        Comparator<EntityPlayer> primary = getSortComparator();
        candidates.sort(primary.thenComparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p)));

        if (ignoreBehindWalls.getValue() || ignoreBehindEntities.getValue()) {
            double mpH = multipointHorizontal.getValue();
            double mpV = multipointVertical.getValue();
            for (EntityPlayer candidate : candidates) {
                if (hasValidAimPoint(candidate, mpH, mpV)) {
                    return candidate;
                }
            }
            return null;
        }

        return candidates.get(0);
    }

    private boolean isValidTarget(Entity target, boolean silentMode, boolean expandedFov) {
        if (!(target instanceof EntityPlayer)) {
            return false;
        }
        float viewYaw = mc.thePlayer.rotationYaw;
        int fovVal = expandedFov ? 360 : (int) fov.getValue();
        return passesTargetFilters((EntityPlayer) target, silentMode, fovVal, viewYaw);
    }

    private boolean passesTargetFilters(EntityPlayer target, boolean silentMode, int fovVal, float viewYaw) {
        if (target == mc.thePlayer || target.deathTime != 0) {
            return false;
        }
        if (TeamUtil.isFriend(target)) {
            return false;
        }
        if (ignoreTeammates.getValue() && TeamUtil.isSameTeam(target)) {
            return false;
        }
        if (!aimInvis.getValue() && target.isInvisible()) {
            return false;
        }
        if (TeamUtil.isBot(target)) {
            return false;
        }
        if (RotationUtil.distanceToEntity(target) > range.getValue()) {
            return false;
        }
        if (fovVal != 360 && RotationUtil.angleToEntity(target) > fovVal) {
            return false;
        }
        if (ignoreBehindWalls.getValue() || ignoreBehindEntities.getValue()) {
            double mpH = multipointHorizontal.getValue();
            double mpV = multipointVertical.getValue();
            if (!hasValidAimPoint(target, mpH, mpV)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasValidAimPoint(EntityPlayer target, double mpH, double mpV) {
        AxisAlignedBB bb = target.getEntityBoundingBox();
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        
        double width = bb.maxX - bb.minX;
        double height = bb.maxY - bb.minY;
        double headY = target.posY + target.getEyeHeight();

        double offsetX = (Math.random() - 0.5) * width * (mpH / 100.0);
        double offsetZ = (Math.random() - 0.5) * width * (mpH / 100.0);
        double offsetY = (Math.random() - 0.5) * height * (mpV / 100.0);

        Vec3 point = new Vec3(
            (bb.minX + bb.maxX) / 2.0 + offsetX,
            MathHelper.clamp_double(headY + offsetY, bb.minY + 0.1, bb.maxY - 0.1),
            (bb.minZ + bb.maxZ) / 2.0 + offsetZ
        );

        if (ignoreBehindWalls.getValue()) {
            if (mc.theWorld.rayTraceBlocks(eyes, point, false, true, false) != null) {
                return false;
            }
        }

        if (ignoreBehindEntities.getValue()) {
            for (Object obj : mc.theWorld.playerEntities) {
                EntityPlayer other = (EntityPlayer) obj;
                if (other == target || other == mc.thePlayer) continue;
                AxisAlignedBB expanded = other.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
                if (expanded.calculateIntercept(eyes, point) != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private Comparator<EntityPlayer> getSortComparator() {
        switch (sortMode.getIndex()) {
            case SORT_HEALTH:
                return Comparator.comparingDouble(p -> p.getHealth() + p.getAbsorptionAmount());
            case SORT_ANGLE:
                return Comparator.comparingDouble(p -> RotationUtil.angleToEntity(p));
            case SORT_HURT_TIME:
                return Comparator.comparingInt(p -> p.hurtTime);
            case SORT_DISTANCE:
            default:
                return Comparator.comparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p));
        }
    }

    // ── Conditions ────────────────────────────────────────────────────────────

    private boolean conditionsMet() {
        if (mc.currentScreen != null) {
            return false;
        }
        if (weaponOnly.getValue() && !ItemUtil.isHoldingSword()) {
            return false;
        }
        if (clickAim.getValue() && !Mouse.isButtonDown(0)) {
            return false;
        }
        if (stopWhenBreaking.getValue() && isBreakingBlock()) {
            if (miningStartTime == -1L) {
                miningStartTime = System.currentTimeMillis();
            }
            long elapsed = System.currentTimeMillis() - miningStartTime;
            if (elapsed >= (long) hoverDelay.getValue()) {
                return false;
            }
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

    // ── Getters ──────────────────────────────────────────────────────────────

    private boolean isLockOnMode() {
        return mode.getIndex() == MODE_LOCK_ON;
    }

    private boolean isSingleTargetMode() {
        return targetMode.getIndex() == TARGET_SINGLE;
    }

    public Entity getTarget() {
        return lockedTarget;
    }

    public boolean hasTarget() {
        return lockedTarget != null;
    }
}
