package myau.module.modules;

import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class Jesus extends Module {
    private static final DecimalFormat df = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.US));

    public final SliderSetting  speed       = new SliderSetting("Speed",       2.5, 0.0, 3.0, 0.05);
    public final BooleanSetting noPush      = new BooleanSetting("No Push",    true);
    public final BooleanSetting groundOnly  = new BooleanSetting("Ground Only", true);

    public Jesus() {
        super("Jesus", false);
        register(speed);
        register(noPush);
        register(groundOnly);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{ df.format(speed.getValue()) };
    }
}