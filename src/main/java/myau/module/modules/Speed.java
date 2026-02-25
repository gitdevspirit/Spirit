package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.LivingUpdateEvent;
import myau.events.StrafeEvent;
import myau.mixin.IAccessorEntity;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.MoveUtil;
import net.minecraft.client.Minecraft;

public class Speed extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting multiplier = new SliderSetting("Multiplier", 1.0, 0.0, 10.0, 0.1);
    public final SliderSetting friction   = new SliderSetting("Friction",   1.0, 0.0, 10.0, 0.1);
    public final SliderSetting strafe     = new SliderSetting("Strafe",       0,   0,  100,   1);

    public Speed() {
        super("Speed", false);
        register(multiplier);
        register(friction);
        register(strafe);
    }

    private boolean canBoost() {
        Scaffold scaffold = (Scaffold) Myau.moduleManager.modules.get(Scaffold.class);
        return !scaffold.isEnabled()
                && MoveUtil.isForwardPressed()
                && mc.thePlayer.getFoodStats().getFoodLevel() > 6
                && !mc.thePlayer.isSneaking()
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isInLava()
                && !((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    @EventTarget(Priority.LOW)
    public void onStrafe(StrafeEvent event) {
        if (!isEnabled() || !canBoost()) return;
        if (mc.thePlayer.onGround) {
            mc.thePlayer.motionY = 0.42F;
            MoveUtil.setSpeed(MoveUtil.getJumpMotion() * multiplier.getValue(), MoveUtil.getMoveYaw());
        } else {
            if (friction.getValue() != 1.0) {
                event.setFriction((float)(event.getFriction() * friction.getValue()));
            }
            int strafeVal = (int) strafe.getValue();
            if (strafeVal > 0) {
                double speed = MoveUtil.getSpeed();
                MoveUtil.setSpeed(speed * ((100 - strafeVal) / 100.0), MoveUtil.getDirectionYaw());
                MoveUtil.addSpeed(speed * (strafeVal / 100.0), MoveUtil.getMoveYaw());
                MoveUtil.setSpeed(speed);
            }
        }
    }

    @EventTarget(Priority.LOW)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (isEnabled() && canBoost()) mc.thePlayer.movementInput.jump = false;
    }
}