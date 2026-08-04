package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.management.NotificationManager;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.List;

/**
 * Notification toasts. Rebuilt integrating the reference client's
 * Color Mode (Static/Gradient/Rainbow) + wave animation + split
 * module-name/state-text rendering, on top of Spirit's own card/accent
 * rendering (which already looked better than the reference's flat
 * background, so that part stayed).
 */
public class Notifications extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting   duration = register(new SliderSetting("Duration",  3.0, 1.0, 10.0, 0.5));
    public final DropdownSetting position = register(new DropdownSetting("Position", 0,
            "Bottom Right", "Top Right", "Bottom Left", "Top Left"));
    public final BooleanSetting  anim     = register(new BooleanSetting("Animation", true));

    // Color mode (ported from the reference client)
    public final DropdownSetting colorMode = register(new DropdownSetting("Color Mode", 0,
            "Static", "Gradient", "Rainbow"));
    public final SliderSetting colorRed    = register(new SliderSetting("Color Red",   233, 0, 255, 1));
    public final SliderSetting colorGreen  = register(new SliderSetting("Color Green", 145, 0, 255, 1));
    public final SliderSetting colorBlue   = register(new SliderSetting("Color Blue",  184, 0, 255, 1));
    public final SliderSetting color2Red   = register(new SliderSetting("Color 2 Red",    85, 0, 255, 1));
    public final SliderSetting color2Green = register(new SliderSetting("Color 2 Green",  85, 0, 255, 1));
    public final SliderSetting color2Blue  = register(new SliderSetting("Color 2 Blue",  255, 0, 255, 1));
    public final DropdownSetting waveAxis  = register(new DropdownSetting("Wave Axis", 0,
            "Vertical", "Horizontal"));
    public final SliderSetting waveSpeed   = register(new SliderSetting("Wave Speed", 1.0, 0.1, 5.0, 0.1));

    // Text options (ported from the reference client)
    public final BooleanSetting textShadow = register(new BooleanSetting("Text Shadow", true));
    public final BooleanSetting lowercase  = register(new BooleanSetting("Lowercase", false));
    public final BooleanSetting splitState = register(new BooleanSetting("Split State Text", true));

    private static final long RAINBOW_PERIOD_MS = 7500L;

    // Layout
    private static final int   H          = 28;
    private static final int   ACCENT_W   = 3;
    private static final int   PAD_LEFT   = 8;
    private static final int   PAD_RIGHT  = 12;
    private static final int   MARGIN     = 12;
    private static final int   GAP        = 5;
    private static final float CORNER_R   = 5f;
    private static final float ANIM_IN    = 220f;
    private static final float ANIM_OUT   = 280f;

    // Colors
    private static final int BG      = 0xEE0D0D0D;
    private static final int WHITE   = 0xFFEEEEFF;
    private static final int DIM     = 0xFF888899;

    public Notifications() { super("Notifications", true); }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.thePlayer == null || Myau.notificationManager == null) return;
        List<NotificationManager.NotificationEntry> active = Myau.notificationManager.getActive();
        if (active.isEmpty()) return;

        ScaledResolution sr  = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
        int pos         = position.getIndex();
        boolean right   = pos == 0 || pos == 1;
        boolean bottom  = pos == 0 || pos == 2;

        for (int i = 0; i < active.size(); i++) {
            NotificationManager.NotificationEntry n = active.get(i);

            long  age   = n.getAge();
            long  total = n.durationMillis;
            float alpha = computeAlpha(age, total);
            float slide = computeSlide(age, total);

            String moduleName = n.message;
            String stateText = "";

            if (splitState.getValue()) {
                if (n.message.endsWith(" toggled")) {
                    moduleName = n.message.substring(0, n.message.length() - " toggled".length());
                    stateText = "toggled";
                } else if (n.message.endsWith(" untoggled")) {
                    moduleName = n.message.substring(0, n.message.length() - " untoggled".length());
                    stateText = "untoggled";
                }
            }

            if (lowercase.getValue()) {
                moduleName = moduleName.toLowerCase();
                stateText = stateText.toLowerCase();
            }

            int nameW  = mc.fontRendererObj.getStringWidth(moduleName);
            int stateW = stateText.isEmpty() ? 0 : mc.fontRendererObj.getStringWidth(stateText);
            int stateGap = stateText.isEmpty() ? 0 : 4;
            int msgW = nameW + stateGap + stateW;

            int cardW  = ACCENT_W + PAD_LEFT + msgW + PAD_RIGHT;
            int minW   = 120;
            if (cardW < minW) cardW = minW;

            float slideOff = anim.getValue() ? (cardW + MARGIN + 20) * (1f - slide) : 0f;
            float x = right  ? sw - MARGIN - cardW + slideOff : MARGIN - slideOff;
            float y = bottom ? sh - MARGIN - H - i * (H + GAP)
                             : MARGIN + i * (H + GAP);

            int accentRaw = n.color != 0xFFFFFF
                    ? (0xFF000000 | n.color)
                    : resolveColor(i * 40f);
            int accent    = withAlpha(accentRaw, alpha);
            int bg        = withAlpha(BG, alpha);
            int stateCol  = withAlpha(DIM, alpha);

            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0);

            solidRect(-2, -2, cardW + 4, H + 4, withAlpha(0xFF000000, alpha * 0.3f));
            roundedRect(0, 0, cardW, H, CORNER_R, bg);
            roundedRect(0, 0, ACCENT_W + CORNER_R, H, CORNER_R, accent);
            solidRect(ACCENT_W, 0, CORNER_R, H, bg);

            float progress = total > 0 ? Math.max(0f, 1f - (float) age / total) : 1f;
            int barW = (int)((cardW - ACCENT_W) * progress);
            if (barW > 0) {
                solidRect(ACCENT_W, H - 2, barW, 2, withAlpha(accent, alpha * 0.5f));
            }

            solidRect(ACCENT_W, 0, cardW - ACCENT_W, 1, withAlpha(0xFFFFFFFF, alpha * 0.06f));

            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableDepth();

            int fontH = mc.fontRendererObj.FONT_HEIGHT;
            int ty    = (H - fontH) / 2;

            mc.fontRendererObj.drawString(
                    moduleName, ACCENT_W + PAD_LEFT, ty,
                    withAlpha(WHITE, alpha), textShadow.getValue()
            );

            if (!stateText.isEmpty()) {
                mc.fontRendererObj.drawString(
                        stateText, ACCENT_W + PAD_LEFT + nameW + stateGap, ty,
                        stateCol, textShadow.getValue()
                );
            }

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GL11.glColor4f(1, 1, 1, 1);

            GlStateManager.popMatrix();
        }
    }

    private int resolveColor(float waveOffset) {
        int mode = colorMode.getIndex();

        if (mode == 2) {
            return rainbowColor(waveOffset);
        }

        int r1 = (int) colorRed.getValue(), g1 = (int) colorGreen.getValue(), b1 = (int) colorBlue.getValue();

        if (mode == 1) {
            return gradientColor(waveOffset, r1, g1, b1);
        }

        return 0xFF000000 | (r1 << 16) | (g1 << 8) | b1;
    }

    private int gradientColor(float waveOffset, int r1, int g1, int b1) {
        int r2 = (int) color2Red.getValue(), g2 = (int) color2Green.getValue(), b2 = (int) color2Blue.getValue();

        double t = (Math.sin(getWaveAngle(waveOffset)) + 1.0) * 0.5;

        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private int rainbowColor(float waveOffset) {
        double hue = getWaveAngle(waveOffset) / (Math.PI * 2.0);
        hue -= Math.floor(hue);

        return 0xFF000000 | (Color.getHSBColor((float) hue, 1.0f, 1.0f).getRGB() & 0x00FFFFFF);
    }

    private double getWaveAngle(float waveOffset) {
        double speed = Math.max(0.1, waveSpeed.getValue());
        double base = System.currentTimeMillis() / (double) RAINBOW_PERIOD_MS * (Math.PI * 2.0) * speed;
        double offsetScale = waveAxis.getIndex() == 0 ? 0.12 : 0.0;
        return base + waveOffset * offsetScale;
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private void solidRect(float x, float y, float w, float h, int color) {
        float a = (color >> 24 & 0xFF) / 255f;
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >> 8  & 0xFF) / 255f;
        float b = (color       & 0xFF) / 255f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x,     y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x,     y + h);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void roundedRect(float x, float y, float w, float h, float r, int color) {
        float a = (color >> 24 & 0xFF) / 255f;
        float rf = (color >> 16 & 0xFF) / 255f;
        float gf = (color >> 8  & 0xFF) / 255f;
        float bf = (color       & 0xFF) / 255f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(rf, gf, bf, a);

        quad(x + r, y,     x + w - r, y + h);
        quad(x,     y + r, x + r,     y + h - r);
        quad(x + w - r, y + r, x + w, y + h - r);

        arc(x + r,     y + r,     r, 180, 270);
        arc(x + w - r, y + r,     r, 270, 360);
        arc(x + r,     y + h - r, r,  90, 180);
        arc(x + w - r, y + h - r, r,   0,  90);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void quad(float x1, float y1, float x2, float y2) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y1); GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x2, y2); GL11.glVertex2f(x1, y2);
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

    private int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, (int)(((color >> 24) & 0xFF) * alpha)));
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
        if (age < ANIM_IN)  return Math.min(1f, age / ANIM_IN * 1.5f);
        if (total > 0 && total - age < ANIM_OUT) return Math.max(0f, (total - age) / ANIM_OUT);
        return 1f;
    }
}
