package myau.module.modules;

import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;

import java.awt.Color;

public class HUD extends Module {
    public final BooleanSetting toggleSound    = register(new BooleanSetting("Toggle Sound",   true));
    public final BooleanSetting toggleAlerts   = register(new BooleanSetting("Toggle Alerts",  true));
    public final SliderSetting  scale          = register(new SliderSetting("Scale",   1.0, 0.5, 3.0, 0.1));
    public final BooleanSetting shadow         = register(new BooleanSetting("Shadow",  true));
    
    // ── Arraylist Settings ────────────────────────────────────────────────────
    
    // Text color (module names) - Default: Orange (FF8855)
    public final SliderSetting  listRed        = register(new SliderSetting("List Red",   255, 0, 255, 1));
    public final SliderSetting  listGreen      = register(new SliderSetting("List Green", 136, 0, 255, 1));
    public final SliderSetting  listBlue       = register(new SliderSetting("List Blue",   85, 0, 255, 1));
    
    // Details color (suffixes/details) - Default: Gray (888888)
    public final SliderSetting  red            = register(new SliderSetting("Red",   136,  0, 255, 1));
    public final SliderSetting  green          = register(new SliderSetting("Green", 136,  0, 255, 1));
    public final SliderSetting  blue           = register(new SliderSetting("Blue",  136,  0, 255, 1));
    
    // Layout settings
    public final SliderSetting  alPadX        = register(new SliderSetting("AL Pad X",      4, 0, 10, 1));
    public final SliderSetting  alPadY        = register(new SliderSetting("AL Pad Y",      2, 0, 10, 1));
    public final SliderSetting  alRounding    = register(new SliderSetting("AL Rounding",   4, 0, 8,  1));
    
    // Display options
    public final BooleanSetting alLowercase   = register(new BooleanSetting("AL Lowercase",  true));
    public final BooleanSetting alShowDetails = register(new BooleanSetting("AL Details",    true));
    public final BooleanSetting alBoundOnly   = register(new BooleanSetting("AL Bound Only", false));
    public final BooleanSetting alBackground  = register(new BooleanSetting("AL Background", true));

    public HUD() {
        super("HUD", true);
    }

    // Details color (gray for suffixes)
    public Color getColor(long time) {
        return new Color((int) red.getValue(), (int) green.getValue(), (int) blue.getValue());
    }

    public Color getColor(long time, float offset) {
        return getColor(time);
    }

    // Text color (orange for module names)
    public Color getListColor() {
        return new Color((int) listRed.getValue(), (int) listGreen.getValue(), (int) listBlue.getValue());
    }
}
