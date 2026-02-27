package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
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
import java.util.stream.Collectors;

public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();

    // Persistent smoothed rotation — shared by both modes so state doesn't reset on switch
    private float smoothedYaw   = Float.NaN;
    private float smoothedPitch = Float.NaN;

    // Mode
    public final DropdownSetting mode      = new DropdownSetting("Mode", 0, "ASSIST", "SILENT");

    // Shared settings
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
        register(mode);
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
        smoothedYaw   = Float.NaN;
        smoothedPitch = Float.NaN;
    }

    // ── Target validation ─────────────────────────────────────────────────────

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

    // ── Shared smooth rotation ─────────────────────────────────────────────────

    /**
     * Returns [newYaw, newPitch] after advancing the smoothed rotation one tick
     * toward the target hitbox. Works for both ASSIST and SILENT — the caller
     * decides whether to push to rotationManager or to event.setRotation.
     *
     * Anti-flag measures:
     *  - Per-tick delta is capped by h/v speed sliders (no instant locks)
     *  - lerpFactor has small random variance each tick (±2%) so the speed
     *    isn't perfectly constant — real players don't move at exactly the
     *    same rate each tick
     *  - A tiny random jitter (±0.03°) is added on top so consecutive
     *    packets don't have a perfectly repeating delta pattern
     */
    private float[] computeSmoothedRotation(EntityPlayer player) {
        AxisAlignedBB bb     = player.getEntityBoundingBox();
        float         border = player.getCollisionBorderSize();

        float[] exactRots = RotationUtil.getRotationsToBox(
                bb.expand(border, border, border),
                mc.thePlayer.rotationYaw,
                mc.thePlayer.rotationPitch,
                180.0F,
                1.0f);

        if (Float.isNaN(smoothedYaw)) {
            smoothedYaw   = mc.thePlayer.rotationYaw;
            smoothedPitch = mc.thePlayer.rotationPitch;
        }

        // Lerp factor with small random variance to break up the repeating-delta pattern
        float baseLerp   = (float)(1.0 - smoothing.getValue() / 100.0 * 0.95);
        float lerpFactor = baseLerp + RandomUtil.nextFloat(-0.02f, 0.02f);
        lerpFactor = Math.max(0.04f, Math.min(1.0f, lerpFactor));

        float dyaw = exactRots[0] - smoothedYaw;
        while (dyaw >  180) dyaw -= 360;
        while (dyaw < -180) dyaw += 360;

        float targetYaw   = smoothedYaw   + dyaw                           * lerpFactor;
        float targetPitch = smoothedPitch + (exactRots[1] - smoothedPitch) * lerpFactor;

        // Cap per-tick movement by speed sliders
        float maxYaw   = (float) hSpeed.getValue();
        float maxPitch = (float) vSpeed.getValue();

        float moveYaw = targetYaw - mc.thePlayer.rotationYaw;
        while (moveYaw >  180) moveYaw -= 360;
        while (moveYaw < -180) moveYaw += 360;
        moveYaw = Math.max(-maxYaw, Math.min(maxYaw, moveYaw));

        float movePitch = targetPitch - mc.thePlayer.rotationPitch;
        if (maxPitch > 0)
            movePitch = Math.max(-maxPitch, Math.min(maxPitch, movePitch));
        else
            movePitch = 0;

        // Tiny jitter so consecutive packets don't share identical deltas
        float jitter = RandomUtil.nextFloat(-0.03f, 0.03f);

        smoothedYaw   = mc.thePlayer.rotationYaw   + moveYaw   + jitter;
        smoothedPitch = mc.thePlayer.rotationPitch + movePitch;
        smoothedPitch = MathHelper.clamp_float(smoothedPitch, -90f, 90f);

        return new float[]{ smoothedYaw, smoothedPitch };
    }

    // ── ASSIST mode ────────────────────────────────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || mode.getIndex() != 0) return;
        if (event.getType() != EventType.POST || mc.currentScreen != null) return;
        if (!weaponOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant()
                || (allowTools.getValue() && ItemUtil.isHoldingTool())) {

            boolean attacking = PlayerUtil.isAttacking();
            if (!attacking || !isLookingAtBlock()) {
                if (attacking || !timer.hasTimeElapsed(350L)) {
                    EntityPlayer player = getClosestTarget();
                    if (player == null) { smoothedYaw = Float.NaN; smoothedPitch = Float.NaN; return; }

                    float[] r = computeSmoothedRotation(player);
                    Myau.rotationManager.setRotation(r[0], r[1], 0, false);
                }
            }
        }
    }

    // ── SILENT mode ────────────────────────────────────────────────────────────

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || mode.getIndex() != 1) return;
        if (event.getType() != EventType.PRE || mc.currentScreen != null) return;
        if (!weaponOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant()
                || (allowTools.getValue() && ItemUtil.isHoldingTool())) {

            EntityPlayer player = getClosestTarget();
            if (player == null) { smoothedYaw = Float.NaN; smoothedPitch = Float.NaN; return; }

            float[] r = computeSmoothedRotation(player);

            // Silent: rotation goes into the outgoing packet, not the visible camera.
            // setPervRotation keeps prevYaw consistent so Grim doesn't flag a
            // discontinuity between rotationYaw and prevRotationYaw.
            event.setRotation(r[0], r[1], 1);
            event.setPervRotation(r[0], 1);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private EntityPlayer getClosestTarget() {
        List<EntityPlayer> inRange = mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityPlayer)
                .map(e -> (EntityPlayer) e)
                .filter(this::isValidTarget)
                .sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                .collect(Collectors.toList());

        if (inRange.isEmpty()) return null;
        if (inRange.stream().anyMatch(this::isInReach))
            inRange.removeIf(p -> !isInReach(p));
        EntityPlayer p = inRange.get(0);
        return RotationUtil.distanceToEntity(p) <= 0.0 ? null : p;
    }

    @EventTarget
    public void onPress(KeyEvent event) {
        if (event.getKey() == mc.gameSettings.keyBindAttack.getKeyCode()
                && !Myau.moduleManager.modules.get(AutoClicker.class).isEnabled()) {
            timer.reset();
        }
    }
}
