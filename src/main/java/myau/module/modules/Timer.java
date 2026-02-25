package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorMinecraft;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;

public class Timer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting   speed  = register(new SliderSetting("Speed", 1.0, 0.1, 10.0, 0.1));
    public final DropdownSetting mode   = register(new DropdownSetting("Mode", 0, "CONSTANT", "VARIABLE"));
    public final SliderSetting   maxSpeed = register(new SliderSetting("Max (Variable)", 2.0, 1.0, 5.0, 0.1));

    public Timer() {
        super("Timer", false);
    }

    @Override
    public void onDisabled() {
        net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
        if (timer != null) {
            timer.timerSpeed = 1.0F;
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }

        net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
        if (timer != null) {
            double spd = mode.getIndex() == 1
                ? speed.getValue() + (maxSpeed.getValue() - speed.getValue()) * Math.random()
                : speed.getValue();
            timer.timerSpeed = (float) spd;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%.1fx", this.speed.getValue())};
    }
}