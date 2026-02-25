package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LoadWorldEvent;
import myau.events.TickEvent;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;

public class Blink extends Module {

    public final DropdownSetting mode  = register(new DropdownSetting("Mode",  0, "DEFAULT", "PULSE"));
    public final SliderSetting   ticks = register(new SliderSetting("Ticks", 20, 0, 1200, 1));

    public Blink() {
        super("Blink", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.POST) return;
        if (!Myau.blinkManager.getBlinkingModule().equals(BlinkModules.BLINK)) {
            setEnabled(false);
            return;
        }
        if (ticks.getValue() > 0 && Myau.blinkManager.countMovement() > (long) ticks.getValue()) {
            switch (mode.getIndex()) {
                case 0:
                    setEnabled(false);
                    break;
                case 1:
                    Myau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
                    Myau.blinkManager.setBlinkState(true, BlinkModules.BLINK);
                    break;
            }
        }
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        setEnabled(false);
    }

    @Override
    public void onEnabled() {
        Myau.blinkManager.setBlinkState(false, Myau.blinkManager.getBlinkingModule());
        Myau.blinkManager.setBlinkState(true, BlinkModules.BLINK);
    }

    @Override
    public void onDisabled() {
        Myau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
    }
}