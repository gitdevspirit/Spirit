package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();

    private float smoothedYaw   = Float.NaN;
    private float smoothedPitch = Float.NaN;

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

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.POST || mc.currentScreen != null) return;
        if (!weaponOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant()
                || (allowTools.getValue() && ItemUtil.isHoldingTool())) {

            boolean attacking = PlayerUtil.isAttacking();
            if (!attacking || !isLookingAtBlock()) {
                if (attacking || !timer.hasTimeElapsed(350L)) {

                    List<EntityPlayer> inRange = mc.theWorld.loadedEntityList.stream()
                            .filter(e -> e instanceof EntityPlayer)
                            .map(e -> (EntityPlayer) e)
                            .filter(this::isValidTarget)
                            .sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                            .collect(Collectors.toList());

                    if (inRange.isEmpty()) {
                        smoothedYaw   = Float.NaN;
                        smoothedPitch = Float.NaN;
                        return;
                    }

                    if (inRange.stream().anyMatch(this::isInReach))
                        inRange.removeIf(p -> !isInReach(p));

                    EntityPlayer player = inRange.get(0);
                    if (RotationUtil.distanceToEntity(player) <= 0.0) return;

                    AxisAlignedBB bb     = player.getEntityBoundingBox();
                    float         border = player.getCollisionBorderSize();
                    float[] exactRots = RotationUtil.getRotationsToBox(
                            bb.expand(border, border, border),
                            mc.thePlayer.rotationYaw,
                            mc.thePlayer.rotationPitch,
                            180.0F,
                            1.0f);

                    // hSpeed controls how fast we reach the target (degrees/tick).
                    // smoothing controls the easing curve — higher = more curved/gradual
                    // approach, but we always arrive within roughly the same time.
                    // At smoothing=0 movement is linear. At smoothing=100 the first few
                    // ticks move quickly then decelerate as we close in (ease-out).
                    float maxYaw   = (float) hSpeed.getValue();
                    float maxPitch = (float) vSpeed.getValue();

                    float rawDyaw = exactRots[0] - mc.thePlayer.rotationYaw;
                    while (rawDyaw >  180) rawDyaw -= 360;
                    while (rawDyaw < -180) rawDyaw += 360;
                    float rawDpitch = (maxPitch > 0) ? (exactRots[1] - mc.thePlayer.rotationPitch) : 0f;

                    // Clamp to max speed so we always reach target within predictable time
                    float clampedYaw   = Math.max(-maxYaw,   Math.min(maxYaw,   rawDyaw));
                    float clampedPitch = Math.max(-maxPitch, Math.min(maxPitch, rawDpitch));

                    // Apply ease-out based on smoothing: raise the clamped ratio to a power.
                    // power > 1 = ease-out (fast start, slow finish = smoother feel).
                    // smoothing=0 → power=1.0 (linear), smoothing=100 → power=2.5 (strong ease-out)
                    float sm    = (float) smoothing.getValue() / 100f;
                    float power = 1.0f + sm * 1.5f;

                    float signYaw   = rawDyaw   != 0 ? Math.signum(rawDyaw)   : 0f;
                    float signPitch = rawDpitch != 0 ? Math.signum(rawDpitch) : 0f;
                    float ratioYaw   = maxYaw   > 0 ? Math.min(Math.abs(rawDyaw)   / maxYaw,   1f) : 1f;
                    float ratioPitch = maxPitch > 0 ? Math.min(Math.abs(rawDpitch) / maxPitch, 1f) : 1f;

                    float moveYaw   = signYaw   * (float) Math.pow(ratioYaw,   power) * maxYaw;
                    float movePitch = signPitch * (float) Math.pow(ratioPitch, power) * maxPitch;

                    // Never overshoot — cap to the actual remaining delta
                    if (Math.abs(moveYaw)   > Math.abs(rawDyaw))   moveYaw   = rawDyaw;
                    if (Math.abs(movePitch) > Math.abs(rawDpitch)) movePitch = rawDpitch;

                    smoothedYaw   = mc.thePlayer.rotationYaw   + moveYaw;
                    smoothedPitch = mc.thePlayer.rotationPitch + movePitch;

                    Myau.rotationManager.setRotation(smoothedYaw, smoothedPitch, 0, false);
                }
            }
        }
    }

    @EventTarget
    public void onPress(KeyEvent event) {
        if (event.getKey() == mc.gameSettings.keyBindAttack.getKeyCode()
                && !Myau.moduleManager.modules.get(AutoClicker.class).isEnabled()) {
            timer.reset();
        }
    }
}
