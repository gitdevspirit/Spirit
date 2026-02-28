package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LivingUpdateEvent;
import myau.events.UpdateEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.KeyBindUtil;
import myau.util.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class JumpReset extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting   chance              = register(new SliderSetting("Chance",           100, 0, 100, 1));
    public final DropdownSetting minimizeMode        = register(new DropdownSetting("Minimize",       2, "Off", "Vertical", "Horizontal"));
    public final BooleanSetting  requireMouseDown    = register(new BooleanSetting("Require Mouse",   false));
    public final BooleanSetting  requireForward      = register(new BooleanSetting("Require Forward", true));
    public final BooleanSetting  requireAim          = register(new BooleanSetting("Require Aim",     true));

    private boolean setJump       = false;
    private boolean ignoreNext    = false;
    private boolean lastGround    = false;
    private int     lastHurtTime  = 0;
    private double  lastFallDist  = 0.0;

    public JumpReset() {
        super("JumpReset", false);
    }

    @Override
    public void onDisabled() {
        setJump      = false;
        ignoreNext   = false;
        lastGround   = false;
        lastHurtTime = 0;
        lastFallDist = 0.0;
        // Release jump key if we disabled mid-jump
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null) return;

        int     hurtTime = mc.thePlayer.hurtTime;
        boolean onGround = mc.thePlayer.onGround;

        // Ignore jumps that come from a big fall (not knockback)
        if (onGround && lastFallDist > 3.0 && !mc.thePlayer.capabilities.isCreativeMode) {
            ignoreNext = true;
        }
        // Reset ignore flag once we've been on the ground two ticks in a row
        if (lastGround && onGround) {
            ignoreNext = false;
        }

        if (hurtTime > lastHurtTime) {
            boolean mouseDown  = !requireMouseDown.getValue() || mc.gameSettings.keyBindAttack.isKeyDown();
            boolean forward    = !requireForward.getValue()   || mc.gameSettings.keyBindForward.isKeyDown();
            boolean aiming     = !requireAim.getValue()       || isAimingAtPlayer();
            boolean randomPass = chance.getValue() >= 100     || RandomUtil.nextFloat(0, 100) < chance.getValue();
            boolean minimizing = checkMinimize();
            boolean movingFOV  = isMovingInFOV();

            if (!ignoreNext && !mc.thePlayer.isInWater() && !mc.thePlayer.isInLava()
                    && onGround && aiming && forward && mouseDown
                    && randomPass && minimizing && !hasBadEffect() && movingFOV) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
                KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindJump.getKeyCode());
                setJump = true;
            }

            ignoreNext = false;
        }

        lastHurtTime = hurtTime;
        lastFallDist = mc.thePlayer.fallDistance;
        lastGround   = onGround;
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!isEnabled()) return;
        if (setJump && !mc.gameSettings.keyBindJump.isKeyDown()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            setJump = false;
        }
    }

    private boolean checkMinimize() {
        switch (minimizeMode.getIndex()) {
            case 1: // Vertical — only jump if moving upward (not already falling)
                return mc.thePlayer.motionY >= 0.42;
            case 2: // Horizontal — only jump if actually moving
                double xz = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX
                                    + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
                return xz > 0.2;
            default: // Off
                return true;
        }
    }

    /** Check if the player's movement direction is within a wide FOV (330°) — avoids jump resetting while strafing away */
    private boolean isMovingInFOV() {
        double mx = mc.thePlayer.motionX;
        double mz = mc.thePlayer.motionZ;
        if (mx == 0 && mz == 0) return true; // standing still — allow
        // Angle between movement vector and player facing
        double moveAngle = Math.toDegrees(Math.atan2(-mx, mz));
        double diff = Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(
                (float)(moveAngle - mc.thePlayer.rotationYaw)));
        return diff < 165.0; // 330° total FOV
    }

    private boolean isAimingAtPlayer() {
        MovingObjectPosition hit = mc.objectMouseOver;
        return hit != null && hit.typeOfHit == MovingObjectType.ENTITY
                && hit.entityHit instanceof EntityPlayer;
    }

    private boolean hasBadEffect() {
        PotionEffect jump   = mc.thePlayer.getActivePotionEffect(Potion.jump);
        PotionEffect poison = mc.thePlayer.getActivePotionEffect(Potion.poison);
        PotionEffect wither = mc.thePlayer.getActivePotionEffect(Potion.wither);
        return jump != null || poison != null || wither != null;
    }

    @Override
    public String[] getSuffix() {
        int c = (int) chance.getValue();
        return c < 100 ? new String[]{ c + "%" } : new String[0];
    }
}
