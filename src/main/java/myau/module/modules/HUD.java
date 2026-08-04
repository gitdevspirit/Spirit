package myau.module.modules;

import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;

import java.awt.Color;

public class HUD extends Module {
    public final BooleanSetting toggleSound =
            register(new BooleanSetting("Toggle Sound", true));

    public final BooleanSetting toggleAlerts =
            register(new BooleanSetting("Toggle Alerts", true));

    public final SliderSetting scale =
            register(new SliderSetting("Scale", 1.0, 0.5, 3.0, 0.1));

    public final BooleanSetting shadow =
            register(new BooleanSetting("Shadow", true));

    public final SliderSetting listRed =
            register(new SliderSetting("List Red", 255, 0, 255, 1));

    public final SliderSetting listGreen =
            register(new SliderSetting("List Green", 136, 0, 255, 1));

    public final SliderSetting listBlue =
            register(new SliderSetting("List Blue", 85, 0, 255, 1));

    public final SliderSetting red =
            register(new SliderSetting("Red", 136, 0, 255, 1));

    public final SliderSetting green =
            register(new SliderSetting("Green", 136, 0, 255, 1));

    public final SliderSetting blue =
            register(new SliderSetting("Blue", 136, 0, 255, 1));

    public final SliderSetting alPadX =
            register(new SliderSetting("AL Pad X", 5, 0, 20, 1));

    public final SliderSetting alPadY =
            register(new SliderSetting("AL Pad Y", 3, 0, 20, 1));

    public final SliderSetting alRounding =
            register(new SliderSetting("AL Rounding", 5, 0, 16, 1));

    public final SliderSetting alSpacing =
            register(new SliderSetting("AL Spacing", 2, 0, 20, 1));

    public final SliderSetting alPosX =
            register(new SliderSetting("AL X", 2, 0, 1000, 1));

    public final SliderSetting alPosY =
            register(new SliderSetting("AL Y", 4, 0, 1000, 1));

    public final BooleanSetting alLeft =
            register(new BooleanSetting("AL Left Side", false));

    public final BooleanSetting alLowercase =
            register(new BooleanSetting("AL Lowercase", true));

    public final BooleanSetting alShowDetails =
            register(new BooleanSetting("AL Details", true));

    public final BooleanSetting alBoundOnly =
            register(new BooleanSetting("AL Bound Only", false));

    public final BooleanSetting alBackground =
            register(new BooleanSetting("AL Background", true));

    public final BooleanSetting alGradient =
            register(new BooleanSetting("AL Gradient", true));

    public final BooleanSetting alVanillaFont =
            register(new BooleanSetting("AL Vanilla Font", true));

    public final SliderSetting alBgRed =
            register(new SliderSetting("AL BG Red", 8, 0, 255, 1));

    public final SliderSetting alBgGreen =
            register(new SliderSetting("AL BG Green", 8, 0, 255, 1));

    public final SliderSetting alBgBlue =
            register(new SliderSetting("AL BG Blue", 14, 0, 255, 1));

    public final SliderSetting alBgAlpha =
            register(new SliderSetting("AL BG Alpha", 210, 0, 255, 1));

    public final SliderSetting alGradRed =
            register(new SliderSetting("AL Grad Red", 38, 0, 255, 1));

    public final SliderSetting alGradGreen =
            register(new SliderSetting("AL Grad Green", 15, 0, 255, 1));

    public final SliderSetting alGradBlue =
            register(new SliderSetting("AL Grad Blue", 55, 0, 255, 1));

    // ── Animated text color (applies to both the header and list colors) ──
    public final DropdownSetting colorMode =
            register(new DropdownSetting("Color Mode", 0, "Static", "Gradient", "Rainbow"));

    public final SliderSetting waveGradRed =
            register(new SliderSetting("Wave Gradient Red", 85, 0, 255, 1));

    public final SliderSetting waveGradGreen =
            register(new SliderSetting("Wave Gradient Green", 85, 0, 255, 1));

    public final SliderSetting waveGradBlue =
            register(new SliderSetting("Wave Gradient Blue", 255, 0, 255, 1));

    public final SliderSetting waveSpeed =
            register(new SliderSetting("Wave Speed", 1.0, 0.1, 5.0, 0.1));

    private static final long RAINBOW_PERIOD_MS = 4000L;

    public HUD() {
        super("HUD", true);
    }

    public Color getColor(long time) {
        return getColor(time, 0f);
    }

    public Color getColor(long time, float offset) {
        return resolveColor(
                time, offset,
                (int) red.getValue(), (int) green.getValue(), (int) blue.getValue()
        );
    }

    public Color getListColor() {
        return resolveColor(
                System.currentTimeMillis(), 0f,
                (int) listRed.getValue(), (int) listGreen.getValue(), (int) listBlue.getValue()
        );
    }

    private Color resolveColor(long time, float offset, int baseR, int baseG, int baseB) {
        int mode = colorMode.getIndex();

        if (mode == 2) {
            return rainbowColor(time, offset);
        }

        if (mode == 1) {
            return gradientColor(time, offset, baseR, baseG, baseB);
        }

        return new Color(baseR, baseG, baseB);
    }

    private Color gradientColor(long time, float offset, int baseR, int baseG, int baseB) {
        double t = (Math.sin(
                time / 1000.0 * waveSpeed.getValue() + offset * 0.12
        ) + 1.0) / 2.0;

        int r = (int) (baseR + ((int) waveGradRed.getValue() - baseR) * t);
        int g = (int) (baseG + ((int) waveGradGreen.getValue() - baseG) * t);
        int b = (int) (baseB + ((int) waveGradBlue.getValue() - baseB) * t);

        return new Color(clamp(r), clamp(g), clamp(b));
    }

    private Color rainbowColor(long time, float offset) {
        double hue = (time / (double) RAINBOW_PERIOD_MS
                * waveSpeed.getValue() + offset * 0.002) % 1.0;

        if (hue < 0) hue += 1.0;

        return Color.getHSBColor((float) hue, 0.8f, 1f);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
