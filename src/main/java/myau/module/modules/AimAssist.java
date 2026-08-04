package com.spirit.modules.combat;

import com.spirit.SpiritClient;
import com.spirit.utils.Rotation;
import com.spirit.utils.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * AimAssist Module - Dynamic rotation-based aim assist
 * Continuously recalculates target position every tick
 * Features: Smoothing, FOV check, Range check, Human error simulation
 */
public class AimAssist {
    
    // ==================== CONFIGURATION ====================
    private static final float MAX_TURN_SPEED = 15.0f; // Degrees per tick
    private static final float FOV_LIMIT = 90.0f;      // Degrees
    private static final double MAX_RANGE = 6.0;       // Blocks
    private static final float SMOOTHING_FACTOR = 0.12f; // 0.0-1.0 (lower = smoother)
    private static final float HUMAN_ERROR_CHANCE = 0.08f; // 8% chance to "miss"
    
    // Dynamic aim point settings
    private static final boolean ENABLE_PREDICTION = true;
    private static final double PREDICTION_TICKS = 0.5;
    private static final double AIM_SPREAD = 0.015; // Small randomization for natural feel
    
    // ==================== STATE ====================
    private final Minecraft mc;
    private final Random random;
    
    private EntityLivingBase target;
    private Rotation currentRotation;
    private Rotation targetRotation;
    
    private Vec3d cachedAimPoint;
    private int lastAimPointTick = -1;
    private boolean isEnabled = false;
    
    // Anti-detection state
    private int ticksWithoutTarget = 0;
    private float[] previousAngles = new float[2];
    
    // ==================== CONSTRUCTOR ====================
    public AimAssist() {
        this.mc = Minecraft.getMinecraft();
        this.random = new Random();
        this.currentRotation = new Rotation(0, 0);
        this.targetRotation = new Rotation(0, 0);
        this.cachedAimPoint = Vec3d.ZERO;
    }
    
    // ==================== MAIN UPDATE LOOP ====================
    /**
     * Called every game tick (20 times per second)
     * This is where the dynamic recalculation happens
     */
    public void onTick() {
        if (!isEnabled || mc.player == null || mc.world == null) {
            return;
        }
        
        // 1. Update target selection
        updateTarget();
        
        // 2. If no target, smoothly reset
        if (target == null) {
            handleNoTarget();
            return;
        }
        
        // 3. Dynamic aim point calculation (happens every tick!)
        Vec3d aimPoint = getDynamicAimPoint(target);
        
        // 4. Calculate required rotation
        EntityPlayerSP player = mc.player;
        Vec3d eyePos = player.getEyePosition(1.0f);
        Rotation requiredRotation = RotationUtils.calculateRotation(eyePos, aimPoint);
        
        // 5. Apply FOV and range checks
        if (!isWithinFOV(requiredRotation) || !isWithinRange(target)) {
            return;
        }
        
        // 6. Smooth rotation (anti-snap)
        Rotation smoothed = applySmoothing(requiredRotation);
        
        // 7. Add human-like error
        Rotation finalRotation = applyHumanError(smoothed);
        
        // 8. Apply rotation to player
        applyRotation(finalRotation);
        
        // 9. Store state for next tick
        this.currentRotation = finalRotation;
        this.targetRotation = requiredRotation;
    }
    
    // ==================== TARGET SELECTION ====================
    private void updateTarget() {
        if (mc.world == null || mc.player == null) return;
        
        // Get all living entities (excluding self)
        List<EntityLivingBase> entities = mc.world.getEntities(
            EntityLivingBase.class,
            entity -> entity != mc.player && entity.isEntityAlive()
        );
        
        // Filter by range and filter
        entities = entities.stream()
            .filter(e -> mc.player.getDistance(e) <= MAX_RANGE)
            .sorted(Comparator.comparingDouble(e -> mc.player.getDistance(e)))
            .collect(Collectors.toList());
        
        if (entities.isEmpty()) {
            target = null;
            ticksWithoutTarget++;
            return;
        }
        
        // Select closest target
        EntityLivingBase newTarget = entities.get(0);
        
        // Check if we should switch targets (prevents wild flicking)
        if (target != newTarget) {
            // Don't switch if current target is still valid and in range
            if (target != null && 
                mc.player.getDistance(target) <= MAX_RANGE &&
                target.isEntityAlive()) {
                return;
            }
        }
        
        target = newTarget;
        ticksWithoutTarget = 0;
    }
    
    private void handleNoTarget() {
        ticksWithoutTarget++;
        
        // Slowly return to default aim if no target for > 3 ticks
        if (ticksWithoutTarget > 3) {
            // Could implement gradual reset here
        }
    }
    
    // ==================== DYNAMIC AIM POINT ====================
    /**
     * 🔥 The core dynamic calculation - recalculates every tick
     * This is the "moving block" analogy in practice
     */
    private Vec3d getDynamicAimPoint(Entity target) {
        // Cache recalculation every 2 ticks for performance
        int currentTick = mc.player.ticksExisted;
        if (lastAimPointTick == currentTick) {
            return cachedAimPoint;
        }
        
        lastAimPointTick = currentTick;
        
        // Get the target's bounding box
        AxisAlignedBB bb = target.getBoundingBox();
        Vec3d center = new Vec3d(
            (bb.minX + bb.maxX) / 2.0,
            (bb.minY + bb.maxY) / 2.0,
            (bb.minZ + bb.maxZ) / 2.0
        );
        
        // 1. PREDICTION: Lead moving targets
        if (ENABLE_PREDICTION && isMoving(target)) {
            Vec3d velocity = getEntityVelocity(target);
            center = center.add(
                velocity.x * PREDICTION_TICKS,
                velocity.y * PREDICTION_TICKS,
                velocity.z * PREDICTION_TICKS
            );
        }
        
        // 2. RANDOMIZATION: Natural aim point variation
        if (AIM_SPREAD > 0) {
            double spreadX = (random.nextDouble() - 0.5) * AIM_SPREAD;
            double spreadY = (random.nextDouble() - 0.5) * AIM_SPREAD;
            double spreadZ = (random.nextDouble() - 0.5) * AIM_SPREAD;
            center = center.add(spreadX, spreadY, spreadZ);
        }
        
        // 3. CACHE for performance
        cachedAimPoint = center;
        return center;
    }
    
    private boolean isMoving(Entity entity) {
        Vec3d vel = getEntityVelocity(entity);
        return Math.abs(vel.x) > 0.01 || Math.abs(vel.z) > 0.01;
    }
    
    private Vec3d getEntityVelocity(Entity entity) {
        // Handle different entity types
        if (entity instanceof EntityLivingBase) {
            return new Vec3d(entity.motionX, entity.motionY, entity.motionZ);
        }
        return Vec3d.ZERO;
    }
    
    // ==================== ROTATION CALCULATIONS ====================
    private Rotation applySmoothing(Rotation targetRot) {
        float yawDiff = MathHelper.wrapDegrees(targetRot.yaw - currentRotation.yaw);
        float pitchDiff = MathHelper.wrapDegrees(targetRot.pitch - currentRotation.pitch);
        
        // Clamp turn speed
        float maxSpeed = MAX_TURN_SPEED * getDynamicSpeedMultiplier();
        yawDiff = MathHelper.clamp(yawDiff, -maxSpeed, maxSpeed);
        pitchDiff = MathHelper.clamp(pitchDiff, -maxSpeed * 0.7f, maxSpeed * 0.7f);
        
        // Apply smoothing with dynamic factor
        float smoothFactor = SMOOTHING_FACTOR * getDistanceBasedFactor();
        
        return new Rotation(
            currentRotation.yaw + yawDiff * smoothFactor,
            currentRotation.pitch + pitchDiff * smoothFactor
        );
    }
    
    private float getDynamicSpeedMultiplier() {
        if (target == null) return 1.0f;
        
        // Aim faster when target is far, slower when close
        float distance = mc.player.getDistance(target);
        if (distance < 1.0f) return 0.5f;
        if (distance > 4.0f) return 1.5f;
        return 1.0f;
    }
    
    private float getDistanceBasedFactor() {
        if (target == null) return 1.0f;
        
        // More aggressive smoothing when close (prevents jitter)
        float distance = mc.player.getDistance(target);
        if (distance < 1.5f) return 0.6f;
        if (distance > 4.0f) return 1.2f;
        return 1.0f;
    }
    
    private Rotation applyHumanError(Rotation rotation) {
        if (random.nextFloat() < HUMAN_ERROR_CHANCE) {
            // Add slight mis-aim
            float errorYaw = (random.nextFloat() - 0.5f) * 0.5f;
            float errorPitch = (random.nextFloat() - 0.5f) * 0.3f;
            return new Rotation(
                rotation.yaw + errorYaw,
                rotation.pitch + errorPitch
            );
        }
        return rotation;
    }
    
    private void applyRotation(Rotation rotation) {
        if (mc.player == null) return;
        
        // Normalize angles
        rotation.yaw = MathHelper.wrapDegrees(rotation.yaw);
        rotation.pitch = MathHelper.clamp(rotation.pitch, -90.0f, 90.0f);
        
        mc.player.rotationYaw = rotation.yaw;
        mc.player.rotationPitch = rotation.pitch;
        
        // Store for next tick
        previousAngles[0] = rotation.yaw;
        previousAngles[1] = rotation.pitch;
    }
    
    // ==================== VALIDATION ====================
    private boolean isWithinFOV(Rotation rotation) {
        if (mc.player == null) return true;
        
        float currentYaw = mc.player.rotationYaw;
        float diff = MathHelper.wrapDegrees(rotation.yaw - currentYaw);
        
        return Math.abs(diff) <= FOV_LIMIT;
    }
    
    private boolean isWithinRange(Entity target) {
        return mc.player != null && mc.player.getDistance(target) <= MAX_RANGE;
    }
    
    // ==================== MODULE CONTROLS ====================
    public void enable() {
        this.isEnabled = true;
        SpiritClient.logger.info("AimAssist enabled");
    }
    
    public void disable() {
        this.isEnabled = false;
        this.target = null;
        SpiritClient.logger.info("AimAssist disabled");
    }
    
    public void toggle() {
        if (isEnabled) {
            disable();
        } else {
            enable();
        }
    }
    
    public boolean isEnabled() {
        return isEnabled;
    }
    
    // ==================== GETTERS/SETTERS ====================
    public EntityLivingBase getTarget() {
        return target;
    }
    
    public Rotation getCurrentRotation() {
        return currentRotation;
    }
    
    public Rotation getTargetRotation() {
        return targetRotation;
    }
    
    // ==================== NESTED CLASSES ====================
    public static class Rotation {
        public float yaw;
        public float pitch;
        
        public Rotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
        
        @Override
        public String toString() {
            return String.format("Rotation{yaw=%.2f, pitch=%.2f}", yaw, pitch);
        }
    }
}
