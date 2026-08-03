package myau.module.modules;

import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;

import java.awt.Color;

public class HUD extends Module {
    public final BooleanSetting toggleSound = register(new BooleanSetting("Toggle Sound", true));
    public final BooleanSetting toggleAlerts = register(new BooleanSetting("Toggle Alerts", true));
    public final SliderSetting scale = register(new SliderSetting("Scale", 1.0, 0.5, 3.0, 0.1));
    public final BooleanSetting shadow = register(new BooleanSetting("Shadow", true));

    public final SliderSetting listRed = register(new SliderSetting("List Red", 255, 0, 255, 1));
    public final SliderSetting listGreen = register(new SliderSetting("List Green", 136, 0, 255, 1));
    public final SliderSetting listBlue = register(new SliderSetting("List Blue", 85, 0, 255, 1));

    public final SliderSetting red = register(new SliderSetting("Red", 136, 0, 255, 1));
    public final SliderSetting green = register(new SliderSetting("Green", 136, 0, 255, 1));
    public final SliderSetting blue = register(new SliderSetting("Blue", 136, 0, 255, 1));

    public final SliderSetting alPadX = register(new SliderSetting("AL Pad X", 5, 0, 20, 1));
    public final SliderSetting alPadY = register(new SliderSetting("AL Pad Y", 3, 0, 20, 1));
    public final SliderSetting alRounding = register(new SliderSetting("AL Rounding", 5, 0, 16, 1));
    public final SliderSetting alSpacing = register(new SliderSetting("AL Spacing", 2, 0, 20, 1));
    public final SliderSetting alPosX = register(new SliderSetting("AL X", 2, 0, 1000, 1));
    public final SliderSetting alPosY = register(new SliderSetting("AL Y", 4, 0, 1000, 1));
    public final BooleanSetting alLeft = register(new BooleanSetting("AL Left Side", false));

    public final BooleanSetting alLowercase = register(new BooleanSetting("AL Lowercase", true));
    public final BooleanSetting alShowDetails = register(new BooleanSetting("AL Details", true));
    public final BooleanSetting alBoundOnly = register(new BooleanSetting("AL Bound Only", false));
    public final BooleanSetting alBackground = register(new BooleanSetting("AL Background", true));
    public final BooleanSetting alGradient = register(new BooleanSetting("AL Gradient", true));
    public final BooleanSetting alVanillaFont = register(new BooleanSetting("AL Vanilla Font", true));

    public final SliderSetting alBgRed = register(new SliderSetting("AL BG Red", 8, 0, 255, 1));
    public final SliderSetting alBgGreen = register(new SliderSetting("AL BG Green", 8, 0, 255, 1));
    public final SliderSetting alBgBlue = register(new SliderSetting("AL BG Blue", 14, 0, 255, 1));
    public final SliderSetting alBgAlpha = register(new SliderSetting("AL BG Alpha", 210, 0, 255, 1));

    public final SliderSetting alGradRed = register(new SliderSetting("AL Grad Red", 38, 0, 255, 1));
    public final SliderSetting alGradGreen = register(new SliderSetting("AL Grad Green", 15, 0, 255, 1));
    public final SliderSetting alGradBlue = register(new SliderSetting("AL Grad Blue", 55, 0, 255, 1));

    public HUD() {
        super("HUD", true);
    }

    public Color getColor(long time) {
        return new Color((int) red.getValue(), (int) green.getValue(), (int) blue.getValue());
    }

    public Color getColor(long time, float offset) {
        return getColor(time);
    }

    public Color getListColor() {
        return new Color((int) listRed.getValue(), (int) listGreen.getValue(), (int) listBlue.getValue());
    }
}
