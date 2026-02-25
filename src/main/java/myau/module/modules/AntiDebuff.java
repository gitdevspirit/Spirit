package myau.module.modules;

import myau.module.BooleanSetting;
import myau.module.Module;

public class AntiDebuff extends Module {
    public final BooleanSetting blindness = new BooleanSetting("Blindness", true);
    public final BooleanSetting nausea    = new BooleanSetting("Nausea", true);

    public AntiDebuff() {
        super("AntiDebuff", false);
        register(blindness);
        register(nausea);
    }
}
