package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PickEvent;
import myau.events.RaytraceEvent;
import myau.events.TickEvent;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Random;

public class Reach extends Module {
    private static final DecimalFormat df = new DecimalFormat("0.0#", new DecimalFormatSymbols(Locale.US));
    private final Random theRandom = new Random();
    private boolean expanding = true;
    public final SliderSetting   range  = register(new SliderSetting("Range", 3.1, 3.0, 6.0, 0.1));
    public final SliderSetting   chance = register(new SliderSetting("Chance %", 100, 1, 100, 1));
    public final DropdownSetting mode   = register(new DropdownSetting("Mode", 0, "CONSTANT", "RANDOM", "CHANCE_BASED"));

    public Reach() {
        super("Reach", false);
    }

    private boolean shouldExpand() {
        switch (mode.getIndex()) {
            case 0: return true;
            case 1: return theRandom.nextBoolean();
            case 2: return theRandom.nextDouble() <= chance.getValue() / 100.0;
            default: return true;
        }
    }

    @EventTarget
    public void onPick(PickEvent event) {
        if (this.isEnabled() && this.expanding) {
            event.setRange(this.range.getValue());
        }
    }

    @EventTarget
    public void onRaytrace(RaytraceEvent event) {
        if (this.isEnabled() && this.expanding) {
            event.setRange(Math.max(event.getRange(), this.range.getValue() + 0.5));
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            this.expanding = shouldExpand();
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{df.format(this.range.getValue())};
    }
}
