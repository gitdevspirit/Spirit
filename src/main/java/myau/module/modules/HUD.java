package myau.module.modules;

import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;

import java.awt.Color;

public class HUD extends Module {
    public static final String[] COLOR_MODES = new String[]{"Static", "Gradient", "Rainbow"};
    public static final String[] WAVE_AXES = new String[]{"Vertical", "Horizontal"};
    public static final String[] VERTICAL_WAVE_DIRECTIONS = new String[]{"Down", "Up"};
    public static final String[] HORIZONTAL_WAVE_DIRECTIONS = new String[]{"Left", "Right"};

    public static float posX = 2.0f;
    public static float posY = 2.0f;

    public final ModeProperty colorMode = new ModeProperty("Color mode", 0, COLOR_MODES);
    public final ColorProperty hudColor = new ColorProperty("Color 1", new Color(255, 255, 255));
    public final ColorProperty hudColor2 = new ColorProperty("Color 2", new Color(85, 85, 255));
    public final ModeProperty waveAxis = new ModeProperty("Wave axis", 0, WAVE_AXES);
    public final ModeProperty verticalWaveDirection = new ModeProperty("Wave direction ", 0, VERTICAL_WAVE_DIRECTIONS);
    public final ModeProperty horizontalWaveDirection = new ModeProperty("Wave direction", 0, HORIZONTAL_WAVE_DIRECTIONS);
    public final FloatProperty waveSpeed = new FloatProperty("Wave speed", 1.0f, 0.1f, 5.0f);
    public final FloatProperty waveLength = new FloatProperty("Wave length", 1.0f, 0.5f, 5.0f);
    public final FloatProperty animationSpeed = new FloatProperty("Animation speed", 0.1f, 0.01f, 1.0f);

    public final BooleanProperty alphabeticalSort = new BooleanProperty("Alphabetical", false);
    public final BooleanProperty alignRight = new BooleanProperty("Align right", false);
    public final BooleanProperty drawBackground = new BooleanProperty("Background", true);
    public final FloatProperty outline = new FloatProperty("Outline", 0.0f, 0.0f, 1.0f);
    public final BooleanProperty textShadow = new BooleanProperty("Text shadow", true);
    public final BooleanProperty lowercase = new BooleanProperty("Lowercase", false);
    public final BooleanProperty showInfo = new BooleanProperty("Show info", true);

    public HUD() {
        super("HUD", Category.RENDER);
        this.registerProperties(
                colorMode, hudColor, hudColor2, waveAxis, verticalWaveDirection, horizontalWaveDirection,
                waveSpeed, waveLength, animationSpeed, alphabeticalSort, alignRight,
                drawBackground, outline, textShadow, lowercase, showInfo
        );
    }
}
