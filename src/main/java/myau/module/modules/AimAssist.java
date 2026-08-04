package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.module.modules.MovementFix;
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
 * AimAssist — Dynamic silent rotations for both Normal and Silent modes.
 * Silent mode now recalculates target position every tick and applies smooth
 * rotations server-side while keeping your camera visually unchanged.
 */
public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int MODE_NORMAL = 0;
    private static final int MODE_SILENT = 1;

    public final DropdownSetting mode = register(new DropdownSetting("Mode", 0, "Normal", "Silent"));
    public final SliderSetting   speed = register(new SliderSetting("Speed", 10, 1, 30, 1));
    public final SliderSetting   multipointHorizontal = register(new SliderSetting("Multipoint Horizontal", 0, 0, 100, 1));
    public final SliderSetting   multipointVertical   = register(new SliderSetting("Multipoint Vertical", 0, 0, 100, 1));
    public final SliderSetting   randomization = register(new SliderSetting("Randomization", 50, 0, 100, 1));
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
            "Hover Delay", 100, 0, 500, 10, () -> stopWhenBreaking.getValue()));
    public final BooleanSetting weaponOnly = register(new BooleanSetting("Weapon Only", false));
    
    // ==================== DYNAMIC ROTATION SETTINGS ====================
    public final SliderSetting   prediction = register(new SliderSetting("Prediction", 0, 0, 10, 1));
    public final SliderSetting   smoothness = register(new SliderSetting("Smoothness", 8, 1, 20, 1));
    public final BooleanSetting  dynamicAimPoint = register(new BooleanSetting("Dynamic Aim Point", true));

    private long miningStartTime = -1L;
    
    // ==================== DYNAMIC ROTATION STATE ====================
    private EntityPlayer currentTarget;
    private float previousYaw;
    private float previousPitch;
    private int targetSwitchCooldown = 0;
    private Vec3 cachedAimPoint;
    private int lastAimPointTick = -1;

    public AimAssist() { 
        super("AimAssist", false);
        this.cachedAimPoint = new Vec3(0, 0, 0);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{ mode.getValue() };
    }

    @Override
    public void onDisabled() {
        miningStartTime = -1L;
        currentTarget = null;
        targetSwitchCooldown = 0;
        RotationState.applyState(false, 0, 0, 0, 0);
    }

    // ── Normal: real camera, once per tick ──────────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.POST) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mode.getIndex() != MODE_NORMAL || !conditionsMet()) return;

        EntityPlayer target = getEnemy(false);
        if (target == null) {
            currentTarget = null;
            return;
        }

        // Store target for dynamic tracking
        currentTarget = target;
        
        float baseYaw   = mc.thePlayer.rotationYaw;
        float basePitch = mc.thePlayer.rotationPitch;
        float[] rot = getDynamicRotationsToTarget(target, baseYaw, basePitch);
        if (rot == null) return;

        mc.thePlayer.rotationYaw     = rot[0];
        mc.thePlayer.rotationPitch   = rot[1];
        mc.thePlayer.rotationYawHead = rot[0];
    }

    // ── Silent: DYNAMIC rotations via UpdateEvent + RotationState ──────────────

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mode.getIndex() != MODE_SILENT || !conditionsMet()) return;

        EntityPlayer target = getEnemy(true);
        if (target == null) {
            currentTarget = null;
            // Deactivate silent aim
            RotationState.applyState(false, 0, 0, 0, 0);
            return;
        }

        // Store target for dynamic tracking
        currentTarget = target;
        
        float baseYaw   = event.getYaw();
        float basePitch = event.getPitch();
        float[] rot = getDynamicRotationsToTarget(target, baseYaw, basePitch);
        if (rot == null) {
            RotationState.applyState(false, 0, 0, 0, 0);
            return;
        }

        // Apply dynamic silent rotation
        if (keepMoveDirection.getValue()) MovementFix.forceMovementFix = true;
        
        // Calculate rotation priority based on smoothness setting
        int priority = (int) smoothness.getValue();
        RotationState.applyState(true, rot[0], rot[1], rot[0], priority);
        event.setRotation(rot[0], rot[1], priority);
        
        // Store for debugging / visualization
        previousYaw = rot[0];
        previousPitch = rot[1];
    }

    // ==================== CORE: DYNAMIC ROTATION CALCULATION ====================

    /**
     * 🔥 DYNAMIC ROTATION — Recalculates aim point every tick
     * This is the "moving block" analogy: target moves, we recalculate
     */
    private float[] getDynamicRotationsToTarget(EntityPlayer target, float baseYaw, float basePitch) {
        if (!dynamicAimPoint.getValue()) {
            // Legacy mode: single aim point
            return getStaticRotationsToTarget(target, baseYaw, basePitch);
        }

        // 🔥 DYNAMIC AIM POINT - Recalculated every tick!
        Vec3 aimPoint = getDynamicAimPoint(target);
        if (aimPoint == null) return null;

        // Apply prediction (lead the target)
        if (prediction.getValue() > 0) {
            aimPoint = applyPrediction(target, aimPoint);
        }

        // Apply randomization (human-like spread)
        if (randomization.getValue() > 0) {
            aimPoint = applyRandomization(aimPoint);
        }

        // Calculate rotations with smoothing
        float smoothFactor = getDynamicSmoothFactor();
        
        return RotationUtil.getRotations(
                aimPoint.xCoord - mc.thePlayer.posX,
                aimPoint.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight()),
                aimPoint.zCoord - mc.thePlayer.posZ,
                baseYaw, basePitch, 180.0f, smoothFactor);
    }

    /**
     * 🔥 RECALCULATES AIM POINT EVERY TICK
     * This is the key difference from static block placement
     */
    private Vec3 getDynamicAimPoint(EntityPlayer target) {
        int currentTick = mc.thePlayer.ticksExisted;
        
        // Cache within same tick for performance
        if (lastAimPointTick == currentTick && cachedAimPoint != null) {
            return cachedAimPoint;
        }
        lastAimPointTick = currentTick;

        AxisAlignedBB bb = target.getEntityBoundingBox();
        double mpH = multipointHorizontal.getValue() / 100.0;
        double mpV = multipointVertical.getValue() / 100.0;

        // Calculate target center
        double centerX = (bb.minX + bb.maxX) / 2.0;
        double centerY = (bb.minY + bb.maxY) / 2.0;
        double centerZ = (bb.minZ + bb.maxZ) / 2.0;
        
        // Dynamic offset based on target movement
        double width = bb.maxX - bb.minX;
        double height = bb.maxY - bb.minY;
        double headY = target.posY + target.getEyeHeight();

        // Multipoint spread (randomized but consistent)
        double offsetX = (Math.random() - 0.5) * width * mpH;
        double offsetZ = (Math.random() - 0.5) * width * mpH;
        double offsetY = (Math.random() - 0.5) * height * mpV;

        // Apply offsets
        double tx = centerX + offsetX;
        double tz = centerZ + offsetZ;
        double ty = MathHelper.clamp_double(headY + offsetY, bb.minY + 0.1, bb.maxY - 0.1);

        // If target is moving, adjust aim point toward direction of movement
        if (isMoving(target)) {
            double speed = Math.sqrt(target.motionX * target.motionX + target.motionZ * target.motionZ);
            if (speed > 0.05) {
                // Shift aim point slightly in movement direction
                double shift = Math.min(speed * 0.5, 0.3);
                tx += target.motionX * shift;
                tz += target.motionZ * shift;
                ty += target.motionY * shift * 0.5;
            }
        }

        Vec3 point = new Vec3(tx, ty, tz);

        // Validate aim point (check walls/entities)
        boolean useBackup = ignoreBehindWalls.getValue() || ignoreBehindEntities.getValue();
        if (useBackup && !hasValidAimPoint(target, point)) {
            // Fallback to center
            Vec3 fallback = new Vec3(centerX, MathHelper.clamp_double(headY, bb.minY + 0.1, bb.maxY - 0.1), centerZ);
            if (hasValidAimPoint(target, fallback)) {
                point = fallback;
            } else {
                cachedAimPoint = null;
                return null;
            }
        }

        cachedAimPoint = point;
        return point;
    }

    /**
     * Prediction - lead the target based on velocity
     */
    private Vec3 applyPrediction(EntityPlayer target, Vec3 aimPoint) {
        double predTicks = prediction.getValue() * 0.1; // 0 to 1.0 ticks
        
        if (predTicks <= 0) return aimPoint;
        if (!isMoving(target)) return aimPoint;

        // Calculate predicted position
        double predX = aimPoint.xCoord + target.motionX * predTicks;
        double predY = aimPoint.yCoord + target.motionY * predTicks * 0.5; // Less vertical prediction
        double predZ = aimPoint.zCoord + target.motionZ * predTicks;

        // Don't predict outside hitbox
        AxisAlignedBB bb = target.getEntityBoundingBox();
        predX = MathHelper.clamp_double(predX, bb.minX - 0.1, bb.maxX + 0.1);
        predY = MathHelper.clamp_double(predY, bb.minY - 0.1, bb.maxY + 0.1);
        predZ = MathHelper.clamp_double(predZ, bb.minZ - 0.1, bb.maxZ + 0.1);

        return new Vec3(predX, predY, predZ);
    }

    /**
     * Randomization - natural human-like spread
     */
    private Vec3 applyRandomization(Vec3 aimPoint) {
        double randAmount = randomization.getValue() / 100.0 * 0.03; // Max 0.03 block spread
        
        if (randAmount <= 0) return aimPoint;
        
        return aimPoint.addVector(
            (Math.random() - 0.5) * randAmount,
            (Math.random() - 0.5) * randAmount * 0.5,
            (Math.random() - 0.5) * randAmount
        );
    }

    /**
     * Dynamic smooth factor based on distance to target
     */
    private float getDynamicSmoothFactor() {
        if (currentTarget == null) {
            return 1.0f - (float) speed.getValue() / 30.0f;
        }
        
        float distance = mc.thePlayer.getDistanceToEntity(currentTarget);
        float baseSpeed = (float) speed.getValue() / 30.0f;
        
        // Aim faster when target is far, slower when close (more precise)
        float distanceFactor = 1.0f;
        if (distance > 3.0f) {
            distanceFactor = 1.0f + (distance - 3.0f) * 0.05f;
        } else if (distance < 1.5f) {
            distanceFactor = 0.7f + (distance / 1.5f) * 0.3f;
        }
        
        return 1.0f - Math.min(baseSpeed * distanceFactor, 0.95f);
    }

    private boolean isMoving(EntityPlayer entity) {
        return Math.abs(entity.motionX) > 0.01 || 
               Math.abs(entity.motionZ) > 0.01 ||
               Math.abs(entity.motionY) > 0.01;
    }

    // ── Legacy static rotation (fallback) ──────────────────────────────

    private float[] getStaticRotationsToTarget(EntityPlayer target, float baseYaw, float basePitch) {
        boolean useBackup = ignoreBehindWalls.getValue() || ignoreBehindEntities.getValue();
        Vec3 aimPoint = pickAimPoint(target, useBackup);
        if (aimPoint == null) return null;

        float smoothFactor = 1.0f - (float) speed.getValue() / 30.0f;
        return RotationUtil.getRotations(
                aimPoint.xCoord - mc.thePlayer.posX,
                aimPoint.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight()),
                aimPoint.zCoord - mc.thePlayer.posZ,
                baseYaw, basePitch, 180.0f, smoothFactor);
    }

    /** Legacy aim point picker (kept for compatibility) */
    private Vec3 pickAimPoint(EntityPlayer target, boolean useBackup) {
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
        if (!useBackup) return point;

        if (hasValidAimPoint(target, point)) return point;

        Vec3 fallback = new Vec3((bb.minX + bb.maxX) / 2.0, 
                MathHelper.clamp_double(headY, bb.minY + 0.1, bb.maxY - 0.1), 
                (bb.minZ + bb.maxZ) / 2.0);
        return hasValidAimPoint(target, fallback) ? fallback : null;
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

    // ── Targeting ─────────────────────────────────────────────────────────────

    private EntityPlayer getEnemy(boolean silentMode) {
        int fovVal = (int) fov.getValue();

        List<EntityPlayer> candidates = new ArrayList<>();
        for (Object obj : mc.theWorld.playerEntities) {
            EntityPlayer p = (EntityPlayer) obj;
            if (passesTargetFilters(p, fovVal)) candidates.add(p);
        }
        if (candidates.isEmpty()) return null;

        Comparator<EntityPlayer> primary = getSortComparator();
        candidates.sort(primary.thenComparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p)));

        boolean useBackup = ignoreBehindWalls.getValue() || ignoreBehindEntities.getValue();
        if (!useBackup) return candidates.get(0);

        for (EntityPlayer candidate : candidates) {
            if (pickAimPoint(candidate, true) != null) return candidate;
        }
        return null;
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
            default: return Comparator.comparingDouble(RotationUtil::angleToEntity);
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
