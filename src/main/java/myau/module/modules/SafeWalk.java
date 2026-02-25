package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.SafeWalkEvent;
import myau.events.UpdateEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.MoveUtil;
import myau.util.PlayerUtil;
import net.minecraft.client.Minecraft;

public class SafeWalk extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting  motion         = register(new SliderSetting("Motion",          1.0, 0.5, 1.0,  0.05));
    public final SliderSetting  speedMotion    = register(new SliderSetting("Speed Motion",    1.0, 0.5, 1.5,  0.05));
    public final BooleanSetting air            = register(new BooleanSetting("Air",            false));
    public final BooleanSetting directionCheck = register(new BooleanSetting("Direction Check",true));
    public final BooleanSetting pitCheck       = register(new BooleanSetting("Pitch Check",    true));
    public final BooleanSetting requirePress   = register(new BooleanSetting("Require Press",  false));
    public final BooleanSetting blocksOnly     = register(new BooleanSetting("Blocks Only",    true));

    public SafeWalk() {
        super("SafeWalk", false);
    }

    private boolean canSafeWalk() {
        Scaffold scaffold = (Scaffold) Myau.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled()) return false;
        if (directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) return false;
        if (pitCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) return false;
        if (blocksOnly.getValue() && !ItemUtil.isHoldingBlock()) return false;
        return (!requirePress.getValue() || mc.gameSettings.keyBindUseItem.isKeyDown())
                && (mc.thePlayer.onGround && PlayerUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -1.0)
                    || air.getValue() && PlayerUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -2.0));
    }

    @EventTarget
    public void onMove(SafeWalkEvent event) {
        if (isEnabled() && canSafeWalk()) event.setSafeWalk(true);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (isEnabled() && event.getType() == EventType.PRE) {
            if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && canSafeWalk()) {
                if (MoveUtil.getSpeedLevel() <= 0) {
                    if (motion.getValue() != 1.0) MoveUtil.setSpeed(MoveUtil.getSpeed() * motion.getValue());
                } else {
                    if (speedMotion.getValue() != 1.0) MoveUtil.setSpeed(MoveUtil.getSpeed() * speedMotion.getValue());
                }
            }
        }
    }
}