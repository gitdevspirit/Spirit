package myau.module.modules;

import myau.Myau;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.management.RotationState;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class SilentAura extends Module {
    private static final Minecraft mc  = Minecraft.getMinecraft();
    private static final Random    rng = new Random();

    // ── Settings ──────────────────────────────────────────────────────────────
    public final SliderSetting   range       = register(new SliderSetting("Range",       4.0, 1.0, 8.0, 0.1));
    public final SliderSetting   extraSwing  = register(new SliderSetting("Extra Swing", 0.5, 0.0, 2.0, 0.1));
    public final SliderSetting   maxAngle    = register(new SliderSetting("Max Angle",   180, 1,   180, 1));
    public final DropdownSetting targetMode  = register(new DropdownSetting("Target Mode", 0, "Distance", "Yaw", "Health"));
    public final DropdownSetting targetArea  = register(new DropdownSetting("Target Area", 0, "Center", "Closest"));
    public final BooleanSetting  teamCheck   = register(new BooleanSetting("Team Check",   true));
    public final BooleanSetting  friendCheck = register(new BooleanSetting("Friend Check", true));
    public final BooleanSetting  botCheck    = register(new BooleanSetting("Bot Check",    true));
    public final SliderSetting   aimSpeed    = register(new SliderSetting("Aim Speed",    50, 1, 100, 1));
    public final SliderSetting   minCPS      = register(new SliderSetting("Min CPS",       8, 1,  20, 1));
    public final SliderSetting   maxCPS      = register(new SliderSetting("Max CPS",      12, 1,  20, 1));
    public final BooleanSetting  requireMouse   = register(new BooleanSetting("Require Mouse Down", false));
    public final BooleanSetting  breakBlocks    = register(new BooleanSetting("Break Blocks Pause", true));
    public final BooleanSetting  disableOnDeath = register(new BooleanSetting("Disable on Death",   true));
    public final BooleanSetting  showTarget  = register(new BooleanSetting("Show Target",  false));
    public final SliderSetting   targetR     = register(new SliderSetting("Target R", 255, 0, 255, 1));
    public final SliderSetting   targetG     = register(new SliderSetting("Target G",   0, 0, 255, 1));
    public final SliderSetting   targetB     = register(new SliderSetting("Target B", 255, 0, 255, 1));

    // ── State ─────────────────────────────────────────────────────────────────
    private EntityPlayer currentTarget     = null;
    private EntityPlayer attackingTarget   = null;
    private float        silentYaw         = 0;
    private float        silentPitch       = 0;
    private long         nextAttackMs      = 0;
    private long         breakPauseUntil   = 0;
    private boolean      positionSentThisTick = false;

    public static boolean attackingThisTick = false;

    public SilentAura() { super("SilentAura", false); }

    public EntityPlayer getTarget()          { return currentTarget; }
    public EntityPlayer getAttackingTarget() { return attackingTarget; }

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            silentYaw   = mc.thePlayer.rotationYaw;
            silentPitch = mc.thePlayer.rotationPitch;
        }
    }

    @Override
    public void onDisabled() {
        currentTarget       = null;
        attackingTarget     = null;
        attackingThisTick   = false;
        positionSentThisTick = false;
    }

    // ── Track whether vanilla sent a position packet this tick ────────────────
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.SEND) return;
        if (event.getPacket() instanceof C03PacketPlayer) {
            positionSentThisTick = true;
        }
    }

    // ── UpdateEvent PRE: compute target + inject silent rotation ──────────────
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (event.getType() == EventType.PRE) {
            // Reset per-tick state
            positionSentThisTick = false;
            attackingTarget      = null;
            attackingThisTick    = false;

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

            float[] rot = calcRotation(currentTarget);
            silentYaw   = rot[0];
            silentPitch = rot[1];

            if (getAngleDiff(silentYaw, silentPitch) > maxAngle.getValue()) {
                currentTarget = null;
                silentYaw   = mc.thePlayer.rotationYaw;
                silentPitch = mc.thePlayer.rotationPitch;
                return;
            }

            // Body rotation so other players see you looking at the target (cosmetic)
            RotationState.applyState(true, silentYaw, silentPitch, silentYaw, 10);

            // Inject rotation into position packet silently (camera stays on client yaw)
            event.setRotation(silentYaw, silentPitch, 10);

            // setPervRotation → RotationState.smoothYaw = silentYaw
            // MixinEntityLivingBase uses smoothYaw for moveFlying()
            // so actual velocity direction matches Grim's simulation — fixes Simulation flag
            event.setPervRotation(silentYaw, 10);

        } else if (event.getType() == EventType.POST) {
            // POST fires after onUpdateWalkingPlayer has sent the position packet.
            // Attack here so Grim sees: position → attack (correct vanilla order).
            if (currentTarget == null || currentTarget.isDead) return;
            double dist = RotationUtil.distanceToEntity(currentTarget);
            if (dist > range.getValue() + extraSwing.getValue()) return;
            if (System.currentTimeMillis() < nextAttackMs) return;

            // If vanilla didn't send a position packet this tick (standing still /
            // tiny rotation delta < 9°), force a look packet so Grim has a rotation
            // update before seeing our attack — this is what fixes PacketOrderB.
            if (!positionSentThisTick) {
                PacketUtil.sendPacket(new C03PacketPlayer.C05PacketPlayerLook(
                        silentYaw, silentPitch, mc.thePlayer.onGround));
            }

            attackingThisTick = true;
            EventManager.call(new AttackEvent(currentTarget));
            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
            PacketUtil.sendPacket(new C02PacketUseEntity(currentTarget, C02PacketUseEntity.Action.ATTACK));
            mc.thePlayer.swingItem();
            attackingTarget = currentTarget;
            scheduleNextAttack();
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────
    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || !showTarget.getValue() || currentTarget == null) return;
        if (!TeamUtil.isEntityLoaded(currentTarget)) return;
        boolean attacking = attackingTarget == currentTarget;
        int r = attacking ? 255 : (int) targetR.getValue();
        int g = attacking ? 85  : (int) targetG.getValue();
        int b = attacking ? 85  : (int) targetB.getValue();
        RenderUtil.enableRenderState();
        RenderUtil.drawEntityBox(currentTarget, r, g, b);
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
            case 1: targets.sort(Comparator.comparingDouble(p -> getAngleDiff(calcRotation(p)[0], calcRotation(p)[1]))); break;
            case 2: targets.sort(Comparator.comparingDouble(EntityPlayer::getHealth)); break;
            default: targets.sort(Comparator.comparingDouble(RotationUtil::distanceToEntity)); break;
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

    private float[] calcRotation(EntityPlayer target) {
        AxisAlignedBB bb    = target.getEntityBoundingBox();
        float         smooth = 1.0f - (float) aimSpeed.getValue() / 100.0f;
        if (targetArea.getIndex() == 1) {
            return RotationUtil.getRotationsToBox(bb, silentYaw, silentPitch, 180.0f, smooth);
        }
        double cx = (bb.minX + bb.maxX) / 2.0;
        double cy = (bb.minY + bb.maxY) / 2.0;
        double cz = (bb.minZ + bb.maxZ) / 2.0;
        net.minecraft.util.Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        return RotationUtil.getRotations(cx - eyes.xCoord, cy - eyes.yCoord, cz - eyes.zCoord,
                silentYaw, silentPitch, 180.0f, smooth);
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
