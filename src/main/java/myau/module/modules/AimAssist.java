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

    public final SliderSetting  smoothing  = new SliderSetting("Smoothing",  50,  0,   100,   1);
    public final SliderSetting  range      = new SliderSetting("Range",      4.5, 3.0, 8.0,  0.1);
    public final SliderSetting  fov        = new SliderSetting("FOV",        90,  30,  360,   1);
    public final BooleanSetting weaponOnly = new BooleanSetting("Weapons Only", true);
    public final BooleanSetting allowTools = new BooleanSetting("Allow Tools",  false);
    public final BooleanSetting botChecks  = new BooleanSetting("Bot Check",    true);
    public final BooleanSetting team       = new BooleanSetting("Teams",        true);

    public AimAssist() {
        super("AimAssist", false);
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
                    float[] target = RotationUtil.getRotationsToBox(
                            bb.expand(border, border, border),
                            mc.thePlayer.rotationYaw,
                            mc.thePlayer.rotationPitch,
                            180.0F,
                            1.0f);

                    float dyaw = target[0] - mc.thePlayer.rotationYaw;
                    while (dyaw >  180) dyaw -= 360;
                    while (dyaw < -180) dyaw += 360;
                    float dpitch = target[1] - mc.thePlayer.rotationPitch;

                    // smoothing=0  → lerpT=1.0 (snap instantly to target)
                    // smoothing=100→ lerpT=0.05 (ease in slowly)
                    // lerpT applied to the delta each tick — large gaps close fast,
                    // small gaps ease in, so it always feels smooth on arrival
                    float sm    = (float) smoothing.getValue() / 100f;
                    // smoothing=0 → lerpT=1.0 (instant snap)
                    // smoothing=100 → lerpT=0.6 (still fast, just slightly eased)
                    float lerpT = 1.0f - sm * 0.4f;

                    float moveYaw   = dyaw   * lerpT;
                    float movePitch = dpitch * lerpT;

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
