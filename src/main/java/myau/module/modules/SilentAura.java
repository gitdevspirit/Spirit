package myau.module.modules;

import myau.Myau;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.management.RotationState;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class SilentAura extends Module {
    private static final Minecraft mc  = Minecraft.getMinecraft();
    private static final Random    rng = new Random();

    // ── Target ────────────────────────────────────────────────────────────────
    public final SliderSetting   range          = register(new SliderSetting("Range",        4.0, 1.0, 8.0, 0.1));
    public final SliderSetting   extraSwing     = register(new SliderSetting("Extra Swing",  0.5, 0.0, 2.0, 0.1));
    public final SliderSetting   maxAngle       = register(new SliderSetting("Max Angle",    180, 1,   180, 1));
    public final DropdownSetting targetMode     = register(new DropdownSetting("Target Mode", 0, "Distance", "Yaw", "Health"));
    public final DropdownSetting targetArea     = register(new DropdownSetting("Target Area", 0, "Center", "Closest"));
    public final BooleanSetting  teamCheck      = register(new BooleanSetting("Team Check",   true));
    public final BooleanSetting  friendCheck    = register(new BooleanSetting("Friend Check", true));
    public final BooleanSetting  botCheck       = register(new BooleanSetting("Bot Check",    true));

    // ── Rotation ──────────────────────────────────────────────────────────────
    public final SliderSetting   aimSpeed       = register(new SliderSetting("Aim Speed",          50, 1, 100, 1));
    public final BooleanSetting  keepMoveDir    = register(new BooleanSetting("Keep Move Direction", false));
    public final BooleanSetting  ignoreManualAim= register(new BooleanSetting("Ignore Manual Aim",   false));

    // ── Attack ────────────────────────────────────────────────────────────────
    public final SliderSetting   minCPS         = register(new SliderSetting("Min CPS",           8, 1, 20, 1));
    public final SliderSetting   maxCPS         = register(new SliderSetting("Max CPS",          12, 1, 20, 1));
    public final BooleanSetting  requireMouse   = register(new BooleanSetting("Require Mouse Down", false));
    public final BooleanSetting  breakBlocks    = register(new BooleanSetting("Break Blocks Pause", true));
    public final BooleanSetting  disableOnDeath = register(new BooleanSetting("Disable on Death",   true));

    // ── Render ────────────────────────────────────────────────────────────────
    public final BooleanSetting  showTarget     = register(new BooleanSetting("Show Target", false));
    public final SliderSetting   targetR        = register(new SliderSetting("Target R", 255, 0, 255, 1));
    public final SliderSetting   targetG        = register(new SliderSetting("Target G",   0, 0, 255, 1));
    public final SliderSetting   targetB        = register(new SliderSetting("Target B", 255, 0, 255, 1));

    // ── State ─────────────────────────────────────────────────────────────────
    private EntityPlayer currentTarget    = null;
    private EntityPlayer attackingTarget  = null;
    private float        silentYaw        = 0;
    private float        silentPitch      = 0;
    private long         nextAttackMs     = 0;
    private long         breakPauseUntil  = 0;
    private boolean      positionSentTick = false;

    public static boolean attackingThisTick = false;

    public SilentAura() { super("SilentAura", false); }

    public EntityPlayer getTarget()          { return currentTarget; }
    public EntityPlayer getAttackingTarget() { return attackingTarget; }

    @Override
    public void onEnabled() {
        currentTarget = null; attackingTarget = null; attackingThisTick = false;
        if (mc.thePlayer != null) { silentYaw = mc.thePlayer.rotationYaw; silentPitch = mc.thePlayer.rotationPitch; }
    }

    @Override
    public void onDisabled() {
        currentTarget = null; attackingTarget = null; attackingThisTick = false;
    }

    // ── Track whether vanilla sent a position packet this tick ───────────────
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.SEND) return;
        if (event.getPacket() instanceof C03PacketPlayer) positionSentTick = true;
    }

    // ── UpdateEvent PRE: silent rotation + attack (same as KillAura SILENT) ──
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // ── POST: attack after position packet is sent (fixes PacketOrderB) ──────
        if (event.getType() == EventType.POST) {
            if (currentTarget == null || currentTarget.isDead) return;
            if (System.currentTimeMillis() < nextAttackMs) return;
            double dist = RotationUtil.distanceToEntity(currentTarget);
            if (dist > range.getValue() + extraSwing.getValue()) return;
            Autoblock autoblock = (Autoblock) Myau.moduleManager.modules.get(Autoblock.class);
            if (autoblock != null && autoblock.isEnabled() && autoblock.isPlayerBlocking()) return;
            // Ensure a look/position packet precedes attack in this tick (fixes PacketOrderB)
            if (!positionSentTick) {
                PacketUtil.sendPacket(new C03PacketPlayer.C05PacketPlayerLook(
                        silentYaw, silentPitch, mc.thePlayer.onGround));
            }
            attackingThisTick = true;
            // Use full vanilla attackEntity path — same as AimAssist silent mode
            // attackEntity: syncCurrentPlayItem → AttackEvent (via mixin) → C02 ATTACK
            mc.playerController.attackEntity(mc.thePlayer, currentTarget);
            mc.thePlayer.swingItem();
            attackingTarget = currentTarget;
            scheduleNextAttack();
            return;
        }

        if (event.getType() != EventType.PRE) return;

        attackingTarget   = null;
        attackingThisTick = false;
        positionSentTick  = false;

        if (disableOnDeath.getValue() && mc.thePlayer.getHealth() <= 0) { setEnabled(false); return; }
        if (requireMouse.getValue() && !org.lwjgl.input.Mouse.isButtonDown(0)) { currentTarget = null; return; }

        if (breakBlocks.getValue()) {
            if (mc.objectMouseOver != null
                    && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK
                    && mc.thePlayer.isUsingItem()) {
                breakPauseUntil = System.currentTimeMillis() + 200;
            }
            if (System.currentTimeMillis() < breakPauseUntil) { currentTarget = null; return; }
        }

        currentTarget = findTarget();
        if (currentTarget == null) {
            silentYaw   = mc.thePlayer.rotationYaw;
            silentPitch = mc.thePlayer.rotationPitch;
            return;
        }

        // Smooth from last REPORTED yaw toward target — same as Raven AimAssist Silent.
        // event.getYaw() = lastReportedYaw = what the server last confirmed.
        // This is the authoritative base; camera is irrelevant for silent mode.
        float[] rot = calcRotation(currentTarget, event.getYaw(), event.getPitch());
        if (getAngleDiff(rot[0], rot[1]) > maxAngle.getValue()) {
            currentTarget = null;
            return;
        }

        silentYaw   = rot[0];
        silentPitch = rot[1];

        // Mixin will call RotationState.applyState after this returns using event values
        event.setRotation(silentYaw, silentPitch, 10);


    }

    // ── fixStrafe so movement direction matches silentYaw for Grim sim ────────
    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (!isEnabled() || currentTarget == null) return;
        if (!keepMoveDir.getValue()) return;
        if (!RotationState.isActived()) return;
        if (!MoveUtil.isForwardPressed()) return;
        MoveUtil.fixStrafe(silentYaw);
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || !showTarget.getValue() || currentTarget == null) return;
        if (!TeamUtil.isEntityLoaded(currentTarget)) return;
        boolean atk = attackingTarget == currentTarget;
        RenderUtil.enableRenderState();
        RenderUtil.drawEntityBox(currentTarget,
                atk ? 255 : (int) targetR.getValue(),
                atk ? 85  : (int) targetG.getValue(),
                atk ? 85  : (int) targetB.getValue());
        RenderUtil.disableRenderState();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private EntityPlayer findTarget() {
        List<EntityPlayer> targets = mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityPlayer).map(e -> (EntityPlayer) e)
                .filter(this::isValidTarget)
                .collect(Collectors.toList());
        if (targets.isEmpty()) return null;
        switch (targetMode.getIndex()) {
            case 1: targets.sort(Comparator.comparingDouble(p -> {
                float[] r = calcRotation(p, silentYaw, silentPitch);
                return getAngleDiff(r[0], r[1]);
            })); break;
            case 2: targets.sort(Comparator.comparingDouble(EntityPlayer::getHealth)); break;
            default: targets.sort(Comparator.comparingDouble(RotationUtil::distanceToEntity));
        }
        return targets.get(0);
    }

    private boolean isValidTarget(EntityPlayer p) {
        if (p == mc.thePlayer || p == mc.thePlayer.ridingEntity) return false;
        if (p == mc.getRenderViewEntity() || p == mc.getRenderViewEntity().ridingEntity) return false;
        if (p.deathTime > 0 || p.isDead) return false;
        if (RotationUtil.distanceToEntity(p) > range.getValue() + extraSwing.getValue()) return false;
        if (friendCheck.getValue() && TeamUtil.isFriend(p)) return false;
        if (teamCheck.getValue() && TeamUtil.isSameTeam(p)) return false;
        if (botCheck.getValue() && TeamUtil.isBot(p)) return false;
        return true;
    }

    private float[] calcRotation(EntityPlayer target, float fromYaw, float fromPitch) {
        AxisAlignedBB bb     = target.getEntityBoundingBox();
        float         smooth = 1.0f - (float) aimSpeed.getValue() / 100.0f;
        if (targetArea.getIndex() == 1)
            return RotationUtil.getRotationsToBox(bb, fromYaw, fromPitch, 180.0f, smooth);
        double cx = (bb.minX + bb.maxX) / 2.0;
        double cy = (bb.minY + bb.maxY) / 2.0;
        double cz = (bb.minZ + bb.maxZ) / 2.0;
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        return RotationUtil.getRotations(cx - eyes.xCoord, cy - eyes.yCoord, cz - eyes.zCoord,
                fromYaw, fromPitch, 180.0f, smooth);
    }

    private float getAngleDiff(float yaw1, float pitch1, float yaw2, float pitch2) {
        float dy = Math.abs(MathHelper.wrapAngleTo180_float(yaw1 - yaw2));
        float dp = Math.abs(pitch1 - pitch2);
        return Math.max(dy, dp);
    }

    private float getAngleDiff(float yaw, float pitch) {
        float dy = Math.abs(MathHelper.wrapAngleTo180_float(yaw   - mc.thePlayer.rotationYaw));
        float dp = Math.abs(MathHelper.wrapAngleTo180_float(pitch - mc.thePlayer.rotationPitch));
        return Math.max(dy, dp);
    }

    private void scheduleNextAttack() {
        double minMs = 1000.0 / maxCPS.getValue();
        double maxMs = 1000.0 / minCPS.getValue();
        nextAttackMs = System.currentTimeMillis() + (long)(minMs + rng.nextDouble() * (maxMs - minMs));
    }
}
