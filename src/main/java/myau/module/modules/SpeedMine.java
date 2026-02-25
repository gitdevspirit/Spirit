package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.Module;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class SpeedMine extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting speed = register(new SliderSetting("Speed %", 15, 0, 100, 1));
    public final SliderSetting delay = register(new SliderSetting("Delay",   0,  0, 4,   1));

    public SpeedMine() {
        super("SpeedMine", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.playerController.isInCreativeMode()) return;
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectType.BLOCK) return;

        IAccessorPlayerControllerMP ctrl = (IAccessorPlayerControllerMP) mc.playerController;
        ctrl.setBlockHitDelay(Math.min(ctrl.getBlockHitDelay(), (int) delay.getValue() + 1));
        if (ctrl.getIsHittingBlock()) {
            float damage = 0.3F * ((float) speed.getValue() / 100.0F);
            if (ctrl.getCurBlockDamageMP() < damage) {
                ctrl.setCurBlockDamageMP(damage);
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%d%%", (int) speed.getValue())};
    }
}