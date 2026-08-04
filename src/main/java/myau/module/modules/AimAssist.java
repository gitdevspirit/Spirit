package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.MoveInputEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.management.RotationState;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.MoveUtil;
import myau.util.PacketUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * AimAssist — Silent logic ported from KillAura with proper movement fix
 */
public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random random = new Random();

    private static final int MODE_NORMAL = 0;
    private static final int MODE_SILENT = 1;

    // ── Mode ──────────────────────────────────────────────────────────────────
    public final DropdownSetting mode = register(new DropdownSetting("Mode", 0, "Normal", "Silent"));
    
    // ── Rotation (from KillAura) ─────────────────────────────────────────────
    public final DropdownSetting rotations = register(new DropdownSetting("Rotations", 2, "None", "Legit", "Silent", "Lock View"));
    public final DropdownSetting moveFix = register(new DropdownSetting("Move Fix", 1, "None", "Silent", "Strict"));
    public final SliderSetting smoothing = register(new SliderSetting("Smoothing", 0, 0, 100, 1));
    public final SliderSetting angleStep = register(new SliderSetting("Angle Step", 90, 30, 180, 1));
    
    // ── Targeting ────────────────────────────────────────────────────────────
    public final SliderSetting attackRange = register(new SliderSetting("Range", 4.5, 3.0, 6.0, 0.1));
    public final SliderSetting fov = register(new SliderSetting("FOV", 90, 30, 360, 1));
    public final DropdownSetting sort = register(new DropdownSetting("Sort", 0, "Distance", "Health", "Hurt Time", "FOV"));
    
    // ── Filters ──────────────────────────────────────────────────────────────
    public final BooleanSetting throughWalls = register(new BooleanSetting("Through Walls", true));
    public final BooleanSetting requirePress = register(new BooleanSetting("Require Press", false));
    public final BooleanSetting weaponsOnly = register(new BooleanSetting("Weapons Only", true));
    public final BooleanSetting ignoreTeammates = register(new BooleanSetting("Ignore Teammates", true));
    public final BooleanSetting aimInvis = register(new BooleanSetting("Aim Invisible", false));
    public final BooleanSetting botCheck = register(new BooleanSetting("Bot Check", true));

    private EntityPlayer currentTarget = null;
    private long lastAttackTime = 0L;
    private static final long ATTACK_COOLDOWN = 250L;
    private float lastYaw = 0;
    private float lastPitch = 0;

    public AimAssist() {
        super("AimAssist", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{ mode.getValue() };
    }

    @Override
    public void onDisabled() {
        currentTarget = null;
        RotationState.applyState(false, 0, 0, 0, 0);
        lastYaw = 0;
        lastPitch = 0;
    }

    // ── Normal: real camera ──────────────────────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.POST) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mode.getIndex() != MODE_NORMAL || !conditionsMet()) return;

        EntityPlayer target = getTarget();
        if (target == null) {
            currentTarget = null;
            return;
        }

        currentTarget = target;
        
        float baseYaw = mc.thePlayer.rotationYaw;
        float basePitch = mc.thePlayer.rotationPitch;
        float[] rot = getRotations(target, baseYaw, basePitch);
        if (rot == null) return;

        // Apply rotation
        mc.thePlayer.rotationYaw = rot[0];
        mc.thePlayer.rotationPitch = rot[1];
        mc.thePlayer.rotationYawHead = rot[0];
        
        // Store for movement fix
        lastYaw = rot[0];
        lastPitch = rot[1];
        
        // Actually attack
        performAttack(target, rot[0], rot[1]);
    }

    // ── Silent: EXACT KillAura silent logic WITH ATTACKING ─────────────────

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mode.getIndex() != MODE_SILENT || !conditionsMet()) return;

        EntityPlayer target = getTarget();
        if (target == null) {
            currentTarget = null;
            RotationState.applyState(false, 0, 0, 0, 0);
            lastYaw = 0;
            lastPitch = 0;
            return;
        }

        currentTarget = target;
        
        // ── EXACT KillAura rotation logic ────────────────────────────────────
        if (rotations.getIndex() == 2 || rotations.getIndex() == 3) {
            AxisAlignedBB box = target.getEntityBoundingBox();
            
            float[] rots = RotationUtil.getRotationsToBox(
                box,
                event.getYaw(),
                event.getPitch(),
                (float) angleStep.getValue() + (random.nextFloat() - 0.5f) * 10.0f,
                (float) smoothing.getValue() / 100.0f
            );
            
            // Store for movement fix
            lastYaw = rots[0];
            lastPitch = rots[1];
            
            // Apply rotation to event (same as KillAura)
            event.setRotation(rots[0], rots[1], 1);
            
            // Lock View (same as KillAura)
            if (rotations.getIndex() == 3) {
                Myau.rotationManager.setRotation(rots[0], rots[1], 1, true);
            }
            
            // Move fix (same as KillAura) - THIS FIXES THE WALKING ISSUE
            if (moveFix.getIndex() != 0 || rotations.getIndex() == 3) {
                event.setPervRotation(rots[0], 1);
            }
            
            // 🔥 ACTUALLY ATTACK using the calculated rotation
            performAttack(target, rots[0], rots[1]);
        }
        // ── End KillAura rotation logic ──────────────────────────────────────
    }

    // ── Movement Fix (EXACT from KillAura) ──────────────────────────────────

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (!isEnabled()) return;
        
        // EXACT KillAura movement fix logic
        if (moveFix.getIndex() == 1 && 
            rotations.getIndex() != 3 && 
            RotationState.isActived() && 
            RotationState.getPriority() == 1.0F && 
            MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
        
        // Also fix for Lock View mode
        if (rotations.getIndex() == 3 && currentTarget != null) {
            // When in Lock View, fix movement based on target direction
            if (MoveUtil.isForwardPressed()) {
                // Use the stored yaw to fix strafe
                MoveUtil.fixStrafe(lastYaw);
            }
        }
    }

    // ── Attack Method (from KillAura) ───────────────────────────────────────

    private void performAttack(EntityPlayer target, float yaw, float pitch) {
        // Check cooldown
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAttackTime < ATTACK_COOLDOWN) {
            return;
        }
        
        // Check range
        if (mc.thePlayer.getDistanceToEntity(target) > attackRange.getValue()) {
            return;
        }
        
        // Check if target is still valid
        if (target.deathTime != 0 || !target.isEntityAlive()) {
            return;
        }
        
        // Check if we can hit (raytrace)
        if (!throughWalls.getValue()) {
            Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
            Vec3 targetVec = new Vec3(target.posX, target.posY + target.getEyeHeight(), target.posZ);
            MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eyes, targetVec, false, true, false);
            if (hit != null) {
                return; // Block in the way
            }
        }
        
        // Swing arm
        mc.thePlayer.swingItem();
        
        // Send attack packet (same as KillAura)
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        PacketUtil.sendPacket(new C02PacketUseEntity(target, Action.ATTACK));
        
        // Actually attack if not in spectator
        if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
            mc.playerController.attackEntity(mc.thePlayer, target);
        }
        
        lastAttackTime = currentTime;
    }

    // ── Core: Get Rotations (fallback for Normal mode) ──────────────────────

    private float[] getRotations(EntityPlayer target, float baseYaw, float basePitch) {
        AxisAlignedBB box = target.getEntityBoundingBox();
        
        // Use KillAura's rotation system for Normal mode too
        if (rotations.getIndex() == 2 || rotations.getIndex() == 3) {
            float angleStepVal = (float) angleStep.getValue() + (random.nextFloat() - 0.5f) * 10.0f;
            float smoothVal = (float) smoothing.getValue() / 100.0f;
            
            return RotationUtil.getRotationsToBox(
                box,
                baseYaw,
                basePitch,
                angleStepVal,
                smoothVal
            );
        }
        
        // Fallback: simple rotations to center of box
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 targetPos = new Vec3(
            (box.minX + box.maxX) / 2.0,
            (box.minY + box.maxY) / 2.0,
            (box.minZ + box.maxZ) / 2.0
        );
        
        return RotationUtil.getRotations(
            targetPos.xCoord - eyePos.xCoord,
            targetPos.yCoord - eyePos.yCoord,
            targetPos.zCoord - eyePos.zCoord,
            baseYaw, basePitch, 180.0f, 1.0f
        );
    }

    // ── Targeting (from KillAura) ────────────────────────────────────────────

    private EntityPlayer getTarget() {
        List<EntityPlayer> targets = new ArrayList<>();
        int fovVal = (int) fov.getValue();

        for (Object obj : mc.theWorld.playerEntities) {
            EntityPlayer p = (EntityPlayer) obj;
            if (isValidTarget(p, fovVal)) {
                targets.add(p);
            }
        }

        if (targets.isEmpty()) return null;

        // Sort targets (from KillAura logic)
        targets.sort((a, b) -> {
            int s = 0;
            switch (sort.getIndex()) {
                case 1: s = Float.compare(TeamUtil.getHealthScore(a), TeamUtil.getHealthScore(b)); break;
                case 2: s = Integer.compare(a.hurtResistantTime, b.hurtResistantTime); break;
                case 3: s = Float.compare(RotationUtil.angleToEntity(a), RotationUtil.angleToEntity(b)); break;
                default: // Distance
                    return Double.compare(RotationUtil.distanceToEntity(a), RotationUtil.distanceToEntity(b));
            }
            return s != 0 ? s : Double.compare(RotationUtil.distanceToEntity(a), RotationUtil.distanceToEntity(b));
        });

        return targets.get(0);
    }

    private boolean isValidTarget(EntityPlayer target, int fovVal) {
        if (target == mc.thePlayer || target.deathTime != 0) return false;
        if (TeamUtil.isFriend(target)) return false;
        if (ignoreTeammates.getValue() && TeamUtil.isSameTeam(target)) return false;
        if (!aimInvis.getValue() && target.isInvisible()) return false;
        if (botCheck.getValue() && TeamUtil.isBot(target)) return false;
        if (mc.thePlayer.getDistanceToEntity(target) > attackRange.getValue()) return false;
        if (fovVal != 360 && RotationUtil.angleToEntity(target) > fovVal) return false;
        if (!throughWalls.getValue() && RotationUtil.rayTrace(target) != null) return false;
        return true;
    }

    // ── Conditions ────────────────────────────────────────────────────────────

    private boolean conditionsMet() {
        if (mc.currentScreen != null) return false;
        if (weaponsOnly.getValue() && !ItemUtil.isHoldingSword()) return false;
        if (requirePress.getValue() && !Mouse.isButtonDown(0)) return false;
        return true;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public EntityPlayer getCurrentTarget() {
        return currentTarget;
    }

    public boolean hasTarget() {
        return currentTarget != null;
    }
}
