package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
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
 * AimAssist — reworked to match a simpler, unified keystrokesmod reference:
 * Normal and Silent share one target-point-picking + rotation routine instead
 * of Normal having its own separate predictive-smoothing math. Normal writes
 * the real camera on tick; Silent reports a fake rotation via UpdateEvent +
 * RotationState + MovementFix, same pipeline as Clutch.
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

    private long miningStartTime = -1L;

    public AimAssist() { super("AimAssist", false); }

    @Override
    public String[] getSuffix() {
        return new String[]{ mode.getValue() };
    }

    @Override
    public void onDisabled() {
        miningStartTime = -1L;
    }

    // ── Normal: real camera, once per tick ──────────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.POST) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mode.getIndex() != MODE_NORMAL || !conditionsMet()) return;

        EntityPlayer target = getEnemy(false);
        if (target == null) return;

        float baseYaw   = mc.thePlayer.rotationYaw;
        float basePitch = mc.thePlayer.rotationPitch;
        float[] rot = getRotationsToTarget(target, baseYaw, basePitch);
        if (rot == null) return;

        mc.thePlayer.rotationYaw     = rot[0];
        mc.thePlayer.rotationPitch   = rot[1];
        mc.thePlayer.rotationYawHead = rot[0];
    }

    // ── Silent: fake rotation only, via UpdateEvent + RotationState + MovementFix ──

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mode.getIndex() != MODE_SILENT || !conditionsMet()) return;

        EntityPlayer target = getEnemy(true);
        if (target == null) return;

        float baseYaw   = event.getYaw();
        float basePitch = event.getPitch();
        float[] rot = getRotationsToTarget(target, baseYaw, basePitch);
        if (rot == null) return;

        if (keepMoveDirection.getValue()) MovementFix.forceMovementFix = true;
        RotationState.applyState(true, rot[0], rot[1], rot[0], 10);
        event.setRotation(rot[0], rot[1], 10);
    }

    /** Shared by both modes: picks a point on the target and computes rotations toward it. */
    private float[] getRotationsToTarget(EntityPlayer target, float baseYaw, float basePitch) {
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

    /** Picks a point on the target to aim at, honoring the multipoint spread settings. */
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

        // Fall back to the exact head center — still worth attacking even if
        // the randomized multipoint spot happened to be obstructed.
        Vec3 fallback = new Vec3((bb.minX + bb.maxX) / 2.0, MathHelper.clamp_double(headY, bb.minY + 0.1, bb.maxY - 0.1), (bb.minZ + bb.maxZ) / 2.0);
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
