package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.TickEvent;
import myau.mixin.IAccessorEntityLivingBase;
import myau.module.Module;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;

public class NoJumpDelay extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting delay = new SliderSetting("Delay", 3, 0, 8, 1);

    public NoJumpDelay() {
        super("NoJumpDelay", false);
        register(delay);
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        ((IAccessorEntityLivingBase) mc.thePlayer)
                .setJumpTicks(Math.min(
                        ((IAccessorEntityLivingBase) mc.thePlayer).getJumpTicks(),
                        (int) delay.getValue() + 1));
    }

    @Override
    public String[] getSuffix() {
        return new String[]{ String.valueOf((int) delay.getValue()) };
    }
}