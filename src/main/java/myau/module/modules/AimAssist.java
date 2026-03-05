package myau.module.modules;

import myau.Myau;
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
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class AimAssist extends Module {
    private static final Minecraft mc  = Minecraft.getMinecraft();
    private static final Random    rng = new Random();

    // ── Mode ──────────────────────────────────────────────────────────────────
    public final DropdownSetting mode = register(new DropdownSetting("Mode", 0,
            "Regular", "Linear", "Lock-on", "Silent"));

    // ── Aim speed ─────────────────────────────────────────────────────────────
    public final SliderSetting hSpeed    = register(new SliderSetting("H-Speed",   3.0, 0.0, 10.0, 0.1));
    public final SliderSetting vSpeed    = register(new SliderSetting("V-Speed",   1.0, 0.0, 10.0, 0.1));
    public final SliderSetting smoothing = register(new SliderSetting("Smoothing", 50,  0,   100,  1));

    // ── Range / FOV ───────────────────────────────────────────────────────────
    public final SliderSetting  range = register(new SliderSetting("Range", 4.5, 1.0, 8.0, 0.1));
    public final SliderSetting  fov   = register(new SliderSetting("FOV",   180, 10,  360, 1));

    // ── Silent mode: autoclicker ───────────────────────────────────────────────
    public final SliderSetting  minCPS     = register(new SliderSetting("Min CPS",    8,  1, 20, 1));
    public final SliderSetting  maxCPS     = register(new SliderSetting("Max CPS",   12,  1, 20, 1));
    public final SliderSetting  extraSwing = register(new SliderSetting("Extra Swing", 0.5, 0.0, 2.0, 0.1));
    public final SliderSetting  maxAngle   = register(new SliderSetting("Max Angle", 180, 1, 180, 1));

    // ── Target settings ───────────────────────────────────────────────────────
    public final DropdownSetting targetMode = register(new DropdownSetting("Target Mode", 0,
            "Distance", "Yaw", "Health"));
    public final BooleanSetting weaponOnly  = register(new BooleanSetting("Weapons Only", true));
    public final BooleanSetting allowTools  = register(new BooleanSetting("Allow Tools",  false));
    public final BooleanSetting botCheck    = register(new BooleanSetting("Bot Check",    true));
    public final BooleanSetting teamCheck   = register(new BooleanSetting("Teams",        true));
    public final BooleanSetting friendCheck = register(new BooleanSetting("Friends",      true));
    public final BooleanSetting requireMouse = register(new BooleanSetting("Require Mouse Down", false));
    public final BooleanSetting breakPause  = register(new BooleanSetting("Break Blocks Pause", true));
    public final BooleanSetting disableOnDeath = register(new BooleanSetting("Disable on Death", false));

    // ── State ─────────────────────────────────────────────────────────────────
    private EntityPlayer currentTarget   = null;
    private EntityPlayer attackingTarget = null;
    private float        silentYaw       = 0;
    private float        silentPitch     = 0;
    private long         nextAttackMs    = 0;
    private long         breakPauseUntil = 0;
    private final TimerUtil timer        = new TimerUtil();

    // Read by Autoblock to suppress C07/C08 on attack ticks
    public static boolean attackingThisTick = false;

    public AimAssist() { super("AimAssist", false); }

    public EntityPlayer getTarget()          { return currentTarget; }
    public EntityPlayer getAttackingTarget() { return attackingTarget; }

    @Override public void onEnabled() {
        if (mc.thePlayer != null) {
            silentYaw   = mc.thePlayer.rotationYaw;
            silentPitch = mc.thePlayer.rotationPitch;
        }
    }

    @Override public void onDisabled() {
        currentTarget     = null;
        attackingTarget   = null;
        attackingThisTick = false;
    }

    // ── Regular / Linear / Lock-on: runs in TickEvent POST (moves actual camera) ──
    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.POST) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return;
        if (mode.getIndex() == 3) return; // Silent handled in UpdateEvent

        if (disableOnDeath.getValue() && mc.thePlayer.getHealth() <= 0) { setEnabled(false); return; }
        if (requireMouse.getValue() && !org.lwjgl.input.Mouse.isButtonDown(0)) return;
        if (!isWeaponConditionMet()) return;

        EntityPlayer target = findTarget(false);
        if (target == null) return;

        AxisAlignedBB bb     = target.getEntityBoundingBox();
        double        border = target.getCollisionBorderSize();
        float[] dest = RotationUtil.getRotationsToBox(
                bb.expand(border, border, border),
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                180.0f, (float) smoothing.getValue() / 100.0f);

        float yawStep, pitchStep;
        int m = mode.getIndex();

        if (m == 2) { // Lock-on: instant
            yawStep   = dest[0] - mc.thePlayer.rotationYaw;
            pitchStep = dest[1] - mc.thePlayer.rotationPitch;
        } else if (m == 1) { // Linear: fixed step per tick
            float h = (float) hSpeed.getValue() * 0.5f;
            float v = (float) vSpeed.getValue() * 0.5f;
            float dy = MathHelper.wrapAngleTo180_float(dest[0] - mc.thePlayer.rotationYaw);
            float dp = dest[1] - mc.thePlayer.rotationPitch;
            yawStep   = Math.abs(dy) < h ? dy : Math.signum(dy) * h;
            pitchStep = Math.abs(dp) < v ? dp : Math.signum(dp) * v;
        } else { // Regular: proportional (further = faster)
            yawStep   = (dest[0] - mc.thePlayer.rotationYaw)   * 0.1f * (float) hSpeed.getValue();
            pitchStep = (dest[1] - mc.thePlayer.rotationPitch) * 0.1f * (float) vSpeed.getValue();
        }

        Myau.rotationManager.setRotation(
                mc.thePlayer.rotationYaw   + yawStep,
                mc.thePlayer.rotationPitch + pitchStep,
                0, false);
    }

    // ── Silent mode: rotation via UpdateEvent, attack via playerController ────
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || mode.getIndex() != 3) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (event.getType() == EventType.PRE) {
            attackingTarget   = null;
            attackingThisTick = false;

            if (disableOnDeath.getValue() && mc.thePlayer.getHealth() <= 0) { setEnabled(false); return; }
            if (requireMouse.getValue() && !org.lwjgl.input.Mouse.isButtonDown(0)) { currentTarget = null; return; }

            if (breakPause.getValue()) {
                if (mc.objectMouseOver != null
                        && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK
                        && mc.thePlayer.isUsingItem()) {
                    breakPauseUntil = System.currentTimeMillis() + 200;
                }
                if (System.currentTimeMillis() < breakPauseUntil) { currentTarget = null; return; }
            }

            currentTarget = findTarget(true);
            if (currentTarget == null) {
                silentYaw   = mc.thePlayer.rotationYaw;
                silentPitch = mc.thePlayer.rotationPitch;
                return;
            }

            float[] rot = calcSilentRotation(currentTarget);
            silentYaw   = rot[0];
            silentPitch = rot[1];

            float diff = getAngleDiff(silentYaw, silentPitch);
            if (diff > maxAngle.getValue()) {
                currentTarget = null;
                silentYaw   = mc.thePlayer.rotationYaw;
                silentPitch = mc.thePlayer.rotationPitch;
                return;
            }

            // Body rotation for rendering
            RotationState.applyState(true, silentYaw, silentPitch, silentYaw, 10);

            // Inject silent rotation into position packet
            event.setRotation(silentYaw, silentPitch, 10);

        } else if (event.getType() == EventType.POST) {
            // Attack after position packet — vanilla order
            if (currentTarget == null || currentTarget.isDead) return;
            double dist = RotationUtil.distanceToEntity(currentTarget);
            if (dist > range.getValue() + extraSwing.getValue()) return;
            if (System.currentTimeMillis() < nextAttackMs) return;

            attackingThisTick = true;
            mc.playerController.attackEntity(mc.thePlayer, currentTarget);
            mc.thePlayer.swingItem();
            attackingTarget = currentTarget;
            scheduleNextAttack();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private EntityPlayer findTarget(boolean silent) {
        float currentYaw   = silent ? silentYaw : mc.thePlayer.rotationYaw;
        float currentPitch = silent ? silentPitch : mc.thePlayer.rotationPitch;

        List<EntityPlayer> targets = mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityPlayer).map(e -> (EntityPlayer) e)
                .filter(p -> isValidTarget(p, silent))
                .collect(Collectors.toList());
        if (targets.isEmpty()) return null;

        switch (targetMode.getIndex()) {
            case 1: targets.sort(Comparator.comparingDouble(p -> {
                float[] r = silent ? calcSilentRotation(p) : new float[]{0,0};
                return getAngleDiff(r[0], r[1]);
            })); break;
            case 2: targets.sort(Comparator.comparingDouble(EntityPlayer::getHealth)); break;
            default: targets.sort(Comparator.comparingDouble(RotationUtil::distanceToEntity)); break;
        }
        return targets.get(0);
    }

    private boolean isValidTarget(EntityPlayer p, boolean silent) {
        if (p == mc.thePlayer || p == mc.thePlayer.ridingEntity) return false;
        if (p == mc.getRenderViewEntity() || p == mc.getRenderViewEntity().ridingEntity) return false;
        if (p.deathTime > 0 || p.isDead) return false;
        double maxRange = silent ? range.getValue() + extraSwing.getValue() : range.getValue();
        if (RotationUtil.distanceToEntity(p) > maxRange) return false;
        if (!silent && RotationUtil.angleToEntity(p) > fov.getValue()) return false;
        if (friendCheck.getValue() && TeamUtil.isFriend(p)) return false;
        if (teamCheck.getValue() && TeamUtil.isSameTeam(p)) return false;
        if (botCheck.getValue() && TeamUtil.isBot(p)) return false;
        return true;
    }

    private float[] calcSilentRotation(EntityPlayer target) {
        AxisAlignedBB bb  = target.getEntityBoundingBox();
        float smooth = 1.0f - (float) smoothing.getValue() / 100.0f;
        double cx = (bb.minX + bb.maxX) / 2.0;
        double cy = (bb.minY + bb.maxY) / 2.0;
        double cz = (bb.minZ + bb.maxZ) / 2.0;
        net.minecraft.util.Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        return RotationUtil.getRotations(
                cx - eyes.xCoord, cy - eyes.yCoord, cz - eyes.zCoord,
                silentYaw, silentPitch, 180.0f, smooth);
    }

    private float getAngleDiff(float yaw, float pitch) {
        float dy = Math.abs(MathHelper.wrapAngleTo180_float(yaw   - mc.thePlayer.rotationYaw));
        float dp = Math.abs(MathHelper.wrapAngleTo180_float(pitch - mc.thePlayer.rotationPitch));
        return Math.max(dy, dp);
    }

    private boolean isWeaponConditionMet() {
        if (!weaponOnly.getValue()) return true;
        if (allowTools.getValue() && ItemUtil.isHoldingTool()) return true;
        return ItemUtil.isHoldingSword();
    }

    private void scheduleNextAttack() {
        double minMs = 1000.0 / maxCPS.getValue();
        double maxMs = 1000.0 / minCPS.getValue();
        nextAttackMs = System.currentTimeMillis() + (long)(minMs + rng.nextDouble() * (maxMs - minMs));
    }
}
