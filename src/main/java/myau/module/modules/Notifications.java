package myau.module.modules;

import com.google.gson.JsonObject;
import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.management.NotificationManager;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.module.TextSetting;
import myau.module.ColorSetting;
import myau.utility.Theme;
import myau.utility.Utils;
import myau.utility.font.FontManager;
import myau.utility.font.RavenFontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Notifications extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final String[] COLOR_MODES = new String[]{"Static", "Gradient", "Rainbow"};
    private static final String[] WAVE_AXES = new String[]{"Vertical", "Horizontal"};
    private static final String[] VERTICAL_WAVE_DIRECTIONS = new String[]{"Down", "Up"};
    private static final String[] HORIZONTAL_WAVE_DIRECTIONS = new String[]{"Left", "Right"};
    private static final String[] FONT_OPTIONS = FontManager.getHudFontOptions();
    private static final long RAINBOW_PERIOD_MS = 7500L;
    private static final double WAVE_ANGLE_SCALE = 0.12;

    // ── Module Settings ───────────────────────────────────────────────────────
    public final SliderSetting duration = register(new SliderSetting("Duration", 3.0, 1.0, 10.0, 0.5));
    public final DropdownSetting position = register(new DropdownSetting("Position", 0, "Bottom Right", "Top Right", "Bottom Left", "Top Left"));
    public final BooleanSetting anim = register(new BooleanSetting("Animation", true));

    public final SliderSetting colorMode = register(new SliderSetting("Color mode", 0, COLOR_MODES));
    public final ColorSetting notifColor = register(new ColorSetting("Color", 255, 255, 255));
    public final ColorSetting notifColor2 = register(new ColorSetting("Color 2", 85, 85, 255));
    public final BooleanSetting useColorCodes = register(new BooleanSetting("Use color codes", false));
    public final TextSetting notifColorCode;
    public final TextSetting notifColorCode2;
    public final SliderSetting waveAxis = register(new SliderSetting("Wave axis", 0, WAVE_AXES));
    public final SliderSetting verticalWaveDirection = register(new SliderSetting("Wave direction", 0, VERTICAL_WAVE_DIRECTIONS));
    public final SliderSetting horizontalWaveDirection = register(new SliderSetting("Wave direction", 0, HORIZONTAL_WAVE_DIRECTIONS));
    public final SliderSetting waveSpeed = register(new SliderSetting("Wave speed", 1.0, 0.1, 5.0, 0.1));
    public final SliderSetting waveLength = register(new SliderSetting("Wave length", 1.0, 0.5, 5.0, 0.1));
    public final SliderSetting font = register(new SliderSetting("Font", 0, FONT_OPTIONS));
    public final SliderSetting fontSize = register(new SliderSetting("Scale", 1.0, 0.5, 2.0, 0.1));
    public final BooleanSetting textShadow = register(new BooleanSetting("Text shadow", true));
    public final BooleanSetting lowercase = register(new BooleanSetting("Lowercase", false));
    public final ColorSetting stateColor = register(new ColorSetting("State color", 170, 170, 170));
    public final BooleanSetting drawBackground = register(new BooleanSetting("Draw background", true));
    public final BooleanSetting roundedBackground = register(new BooleanSetting("Rounded background", true));
    public final SliderSetting backgroundRadius = register(new SliderSetting("Background radius", 5.0, 0.0, 30.0, 0.5));

    // ── Layout Constants ──────────────────────────────────────────────────────
    private static final int H = 28;
    private static final int ACCENT_W = 3;
    private static final int PAD_LEFT = 8;
    private static final int PAD_RIGHT = 12;
    private static final int MARGIN = 12;
    private static final int GAP = 5;
    private static final float ANIM_IN = 220f;
    private static final float ANIM_OUT = 280f;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int BG = 0xEE0D0D0D;
    private static final int PINK = 0xFFE991B8;
    private static final int WHITE = 0xFFEEEEFF;
    private static final int DIM = 0xFF888899;

    // ── Batch Management ──────────────────────────────────────────────────────
    private static final List<String> pendingBatch = new ArrayList<>();
    private static long batchStartMs = -1L;
    private static final long BATCH_WINDOW_MS = 100L;
    private static final int BATCH_THRESHOLD = 3;

    public Notifications() {
        super("Notifications", true);
        this.notifColorCode = register(createColorCodeSetting("Color code", "f"));
        this.notifColorCode2 = register(createColorCodeSetting("Color code 2", "9"));
    }

    @Override
    public void onEnable() {
        this.guiUpdate();
    }

    @Override
    public void guiUpdate() {
        int mode = colorMode == null ? 0 : (int) colorMode.getValue();
        boolean colorCodeInput = useColorCodes != null && useColorCodes.isToggled();

        if (notifColor != null) notifColor.setVisible((mode == 0 || mode == 1) && !colorCodeInput, this);
        if (notifColor2 != null) notifColor2.setVisible(mode == 1 && !colorCodeInput, this);
        if (useColorCodes != null) useColorCodes.setVisible(mode == 0 || mode == 1, this);
        if (notifColorCode != null) notifColorCode.setVisible((mode == 0 || mode == 1) && colorCodeInput, this);
        if (notifColorCode2 != null) notifColorCode2.setVisible(mode == 1 && colorCodeInput, this);

        boolean showWaveSettings = mode == 1 || mode == 2;
        boolean verticalAxis = waveIsVertical();
        if (waveAxis != null) waveAxis.setVisible(showWaveSettings, this);
        if (verticalWaveDirection != null) verticalWaveDirection.setVisible(showWaveSettings && verticalAxis, this);
        if (horizontalWaveDirection != null) horizontalWaveDirection.setVisible(showWaveSettings && !verticalAxis, this);
        if (waveSpeed != null) waveSpeed.setVisible(showWaveSettings, this);
        if (waveLength != null) waveLength.setVisible(showWaveSettings, this);
        if (backgroundRadius != null) backgroundRadius.setVisible(roundedBackground != null && roundedBackground.isToggled(), this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (batchStartMs >= 0 && System.currentTimeMillis() - batchStartMs >= BATCH_WINDOW_MS) {
            flushBatch();
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.thePlayer == null || Myau.notificationManager == null || !this.isEnabled() || mc.gameSettings.showDebugInfo) {
            return;
        }

        List<NotificationManager.NotificationEntry> active = Myau.notificationManager.getActive();
        if (active.isEmpty()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
        int pos = position.getIndex();
        boolean right = pos == 0 || pos == 1;
        boolean bottom = pos == 0 || pos == 2;

        RavenFontRenderer notifFont = getFontRenderer();

        for (int i = 0; i < active.size(); i++) {
            NotificationManager.NotificationEntry n = active.get(i);

            long age = n.getAge();
            long total = n.durationMillis;
            float alpha = computeAlpha(age, total);
            float slide = computeSlide(age, total);

            String message = n.message;
            if (shouldUseLowercase()) {
                message = message.toLowerCase();
            }

            int msgW = notifFont.getStringWidth(message);
            int cardW = ACCENT_W + PAD_LEFT + msgW + PAD_RIGHT;
            int minW = 120;
            if (cardW < minW) cardW = minW;

            float slideOff = anim.getValue() ? (cardW + MARGIN + 20) * (1f - slide) : 0f;
            float x = right ? sw - MARGIN - cardW + slideOff : MARGIN - slideOff;
            float y = bottom ? sh - MARGIN - H - i * (H + GAP)
                             : MARGIN + i * (H + GAP);

            int calculatedColor = getNotificationColor(i * 0.5);
            int accentRaw = n.color == 0xFFFFFF ? calculatedColor : (0xFF000000 | n.color);

            int accent = withAlpha(accentRaw, alpha);
            int bg = withAlpha(BG, alpha);
            int textCol = withAlpha(WHITE, alpha);

            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0);

            // ── Shadow ────────────────────────────────────────────────────────
            solidRect(-2, -2, cardW + 4, H + 4, withAlpha(0xFF000000, alpha * 0.3f));

            // ── Card background ───────────────────────────────────────────────
            if (drawBackground.getValue()) {
                float cornerRadius = roundedBackground.getValue() ? (float) backgroundRadius.getValue() : 0f;
                if (cornerRadius > 0f) {
                    roundedRect(0, 0, cardW, H, cornerRadius, bg);
                    roundedRect(0, 0, ACCENT_W + cornerRadius, H, cornerRadius, accent);
                    solidRect(ACCENT_W, 0, cornerRadius, H, bg);
                } else {
                    solidRect(0, 0, cardW, H, bg);
                    solidRect(0, 0, ACCENT_W, H, accent);
                }
            }

            // ── Progress bar at bottom ────────────────────────────────────────
            float progress = total > 0 ? Math.max(0f, 1f - (float) age / total) : 1f;
            int barW = (int) ((cardW - ACCENT_W) * progress);
            if (barW > 0) {
                solidRect(ACCENT_W, H - 2, barW, 2, withAlpha(accent, alpha * 0.5f));
            }

            // ── Thin top highlight line ───────────────────────────────────────
            solidRect(ACCENT_W, 0, cardW - ACCENT_W, 1, withAlpha(0xFFFFFFFF, alpha * 0.06f));

            // ── Text Rendering ────────────────────────────────────────────────
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableDepth();

            int fontH = notifFont.getFontHeight();
            float ty = (H - fontH) / 2.0f;

            notifFont.drawString(message, ACCENT_W + PAD_LEFT, ty, textCol, shouldDrawTextShadow());

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GL11.glColor4f(1, 1, 1, 1);

            GlStateManager.popMatrix();
        }
    }

    // ── GL Helpers ────────────────────────────────────────────────────────────
    private void solidRect(float x, float y, float w, float h, int color) {
        float a = (color >> 24 & 0xFF) / 255f;
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >> 8 & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void roundedRect(float x, float y, float w, float h, float r, int color) {
        float a = (color >> 24 & 0xFF) / 255f;
        float rf = (color >> 16 & 0xFF) / 255f;
        float gf = (color >> 8 & 0xFF) / 255f;
        float bf = (color & 0xFF) / 255f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(rf, gf, bf, a);

        quad(x + r, y, x + w - r, y + h);
        quad(x, y + r, x + r, y + h - r);
        quad(x + w - r, y + r, x + w, y + h - r);

        arc(x + r, y + r, r, 180, 270);
        arc(x + w - r, y + r, r, 270, 360);
        arc(x + r, y + h - r, r, 90, 180);
        arc(x + w - r, y + h - r, r, 0, 90);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void quad(float x1, float y1, float x2, float y2) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x1, y2);
        GL11.glEnd();
    }

    private void arc(float cx, float cy, float r, int start, int end) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int d = start; d <= end; d += 4) {
            double rad = Math.toRadians(d);
            GL11.glVertex2f(cx + (float) Math.cos(rad) * r, cy + (float) Math.sin(rad) * r);
        }
        GL11.glEnd();
    }

    // ── Static Notification / Batch Logic ────────────────────────────────────
    public static void addNotification(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        pendingBatch.add(text);
        if (batchStartMs < 0) {
            batchStartMs = System.currentTimeMillis();
        }
    }

    private static void flushBatch() {
        if (pendingBatch.isEmpty()) {
            batchStartMs = -1L;
            return;
        }

        List<String> enabledList = new ArrayList<>();
        List<String> disabledList = new ArrayList<>();
        List<String> otherList = new ArrayList<>();

        for (String t : pendingBatch) {
            if (t.endsWith(" enabled")) enabledList.add(t);
            else if (t.endsWith(" disabled")) disabledList.add(t);
            else otherList.add(t);
        }
        pendingBatch.clear();
        batchStartMs = -1L;

        if (Myau.notificationManager != null) {
            if (enabledList.size() >= BATCH_THRESHOLD) {
                Myau.notificationManager.add(enabledList.size() + " modules enabled");
            } else {
                for (String t : enabledList) Myau.notificationManager.add(t);
            }

            if (disabledList.size() >= BATCH_THRESHOLD) {
                Myau.notificationManager.add(disabledList.size() + " modules disabled");
            } else {
                for (String t : disabledList) Myau.notificationManager.add(t);
            }

            for (String t : otherList) Myau.notificationManager.add(t);
        }
    }

    // ── Utility & Customization Methods ───────────────────────────────────────
    private RavenFontRenderer getFontRenderer() {
        return FontManager.getHudRenderer(getSelectedFontName(), getSelectedFontScale());
    }

    private String getSelectedFontName() {
        if (font == null) {
            return FONT_OPTIONS[0];
        }
        int index = (int) Math.max(0, Math.min(font.getOptions().length - 1, font.getValue()));
        return font.getOptions()[index];
    }

    private float getSelectedFontScale() {
        return fontSize == null ? 1.0f : (float) fontSize.getValue();
    }

    private boolean shouldDrawTextShadow() {
        return textShadow == null || textShadow.getValue();
    }

    private boolean shouldUseLowercase() {
        return lowercase != null && lowercase.getValue();
    }

    private int getNotificationColor(double gradientOffset) {
        if (colorMode == null || notifColor == null) {
            return PINK;
        }
        int mode = (int) colorMode.getValue();
        if (mode == 2) {
            return getRainbowWaveColor(gradientOffset);
        }
        if (mode == 1 && notifColor2 != null) {
            Color c1 = getPrimaryColor();
            Color c2 = getSecondaryColor();
            return getGradientWaveColor(c1, c2, gradientOffset);
        }
        return getPrimaryColor().getRGB();
    }

    private Color getPrimaryColor() {
        if (useColorCodes != null && useColorCodes.getValue()) {
            return new Color(getColorCodeRgb(notifColorCode, notifColor));
        }
        return new Color(notifColor.getRed(), notifColor.getGreen(), notifColor.getBlue());
    }

    private Color getSecondaryColor() {
        if (useColorCodes != null && useColorCodes.getValue()) {
            return new Color(getColorCodeRgb(notifColorCode2, notifColor2));
        }
        return notifColor2 == null ? getPrimaryColor() : new Color(notifColor2.getRed(), notifColor2.getGreen(), notifColor2.getBlue());
    }

    private int getColorCodeRgb(TextSetting colorCodeSetting, ColorSetting fallback) {
        char code = parseColorCodeChar(colorCodeSetting == null ? null : colorCodeSetting.getText());
        return code == 0 && fallback != null ? fallback.getRGB() : getMinecraftColorRgb(code == 0 ? 'f' : code);
    }

    private static char parseColorCodeChar(String input) {
        if (input == null) return '\0';
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return '\0';
        if (trimmed.length() >= 2 && (trimmed.charAt(0) == '&' || trimmed.charAt(0) == '\u00a7')) {
            char code = trimmed.charAt(1);
            return "0123456789abcdef".indexOf(Character.toLowerCase(code)) >= 0 ? Character.toLowerCase(code) : '\0';
        }
        char code = trimmed.charAt(0);
        return "0123456789abcdef".indexOf(Character.toLowerCase(code)) >= 0 ? Character.toLowerCase(code) : '\0';
    }

    private static int getMinecraftColorRgb(char code) {
        int index = "0123456789abcdef".indexOf(Character.toLowerCase(code));
        if (index < 0) return 0xFFFFFF;
        int offset = (index >> 3 & 1) * 85;
        int red = (index >> 2 & 1) * 170 + offset;
        int green = (index >> 1 & 1) * 170 + offset;
        int blue = (index & 1) * 170 + offset;
        if (index == 6) red += 85;
        return red << 16 | green << 8 | blue;
    }

    private TextSetting createColorCodeSetting(String name, String defaultCode) {
        return new TextSetting(name, defaultCode) {
            public void loadProfile(JsonObject data) {
                if (data != null && data.has(this.getName()) && data.get(this.getName()).isJsonPrimitive()) {
                    String value = data.getAsJsonPrimitive(this.getName()).getAsString();
                    if (Notifications.parseColorCodeChar(value) != 0) {
                        this.setText(value);
                    }
                }
            }
        };
    }

    private int getGradientWaveColor(Color c1, Color c2, double gradientOffset) {
        double animationProgress = (Math.sin(getAnimatedWaveAngle(gradientOffset)) + 1.0) * 0.5;
        return Theme.convert(c1, c2, animationProgress).getRGB();
    }

    private int getRainbowWaveColor(double gradientOffset) {
        double hue = getAnimatedWaveAngle(gradientOffset) / (Math.PI * 2.0);
        hue -= Math.floor(hue);
        return Color.getHSBColor((float) hue, 1.0F, 1.0F).getRGB();
    }

    private double getAnimatedWaveAngle(double gradientOffset) {
        return System.currentTimeMillis() / (double) RAINBOW_PERIOD_MS * (Math.PI * 2.0) * getWaveSpeedMultiplier()
                + gradientOffset * WAVE_ANGLE_SCALE;
    }

    private double getWaveSpeedMultiplier() {
        return waveSpeed == null ? 1.0 : Math.max(0.1, waveSpeed.getValue());
    }

    private boolean waveIsVertical() {
        return waveAxis == null || (int) waveAxis.getValue() == 0;
    }

    private int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((color >> 24) & 0xFF) * alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private float computeSlide(long age, long total) {
        if (age < ANIM_IN) {
            float t = age / ANIM_IN;
            return 1f - (1f - t) * (1f - t) * (1f - t);
        }
        if (total > 0 && total - age < ANIM_OUT) {
            float t = (total - age) / ANIM_OUT;
            return t * t * t;
        }
        return 1f;
    }

    private float computeAlpha(long age, long total) {
        if (age < ANIM_IN) return Math.min(1f, age / ANIM_IN * 1.5f);
        if (total > 0 && total - age < ANIM_OUT) return Math.max(0f, (total - age) / ANIM_OUT);
        return 1f;
    }
}
