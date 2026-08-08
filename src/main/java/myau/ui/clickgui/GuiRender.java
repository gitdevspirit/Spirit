package myau.ui.clickgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

/**
 * Lightweight GL 2.1-compatible drawing utilities for the new ClickGUI.
 * Intentionally self-contained — no dependency on Spirit's broader RenderUtil
 * so it stays portable across GUI changes.
 */
public final class GuiRender {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // ── Primitives ────────────────────────────────────────────────────────────

    public static void rect(float x, float y, float w, float h, int color) {
        float a = (color >> 24 & 0xFF) / 255f;
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >>  8 & 0xFF) / 255f;
        float b = (color       & 0xFF) / 255f;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x,     y    );
        GL11.glVertex2f(x + w, y    );
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x,     y + h);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /** Vertical gradient — top to bottom. */
    public static void rectGradientV(float x, float y, float w, float h, int topColor, int bottomColor) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        setColor(topColor);    GL11.glVertex2f(x,     y    );
        setColor(topColor);    GL11.glVertex2f(x + w, y    );
        setColor(bottomColor); GL11.glVertex2f(x + w, y + h);
        setColor(bottomColor); GL11.glVertex2f(x,     y + h);
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static void roundedRect(float x, float y, float w, float h, float radius, int color) {
        float maxR = Math.min(w, h) / 2f;
        float rad  = Math.max(0f, Math.min(radius, maxR));
        float a = (color >> 24 & 0xFF) / 255f;
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >>  8 & 0xFF) / 255f;
        float b = (color       & 0xFF) / 255f;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(r, g, b, a);

        if (rad < 0.5f) {
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(x, y); GL11.glVertex2f(x + w, y);
            GL11.glVertex2f(x + w, y + h); GL11.glVertex2f(x, y + h);
            GL11.glEnd();
        } else {
            float cx = x + w / 2f, cy = y + h / 2f;
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2f(cx, cy);
            int segs = 8;
            arcVertices(x + rad,       y + rad,       rad, 180, 270, segs);
            arcVertices(x + w - rad,   y + rad,       rad, 270, 360, segs);
            arcVertices(x + w - rad,   y + h - rad,   rad,   0,  90, segs);
            arcVertices(x + rad,       y + h - rad,   rad,  90, 180, segs);
            GL11.glVertex2f(x + rad, y); // close back to start
            GL11.glEnd();
        }
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /** Rounded rect with only the top two corners rounded (sidebar sections). */
    public static void roundedRectTop(float x, float y, float w, float h, float radius, int color) {
        float maxR = Math.min(w, h / 2f);
        float rad  = Math.max(0f, Math.min(radius, maxR));
        float a = (color >> 24 & 0xFF) / 255f;
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >>  8 & 0xFF) / 255f;
        float b = (color       & 0xFF) / 255f;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(r, g, b, a);

        float cx = x + w / 2f, cy = y + h / 2f;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        arcVertices(x + rad,     y + rad, rad, 180, 270, 8);
        arcVertices(x + w - rad, y + rad, rad, 270, 360, 8);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x,     y + h);
        GL11.glVertex2f(x + rad, y);
        GL11.glEnd();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static void arcVertices(float cx, float cy, float r, float startDeg, float endDeg, int segments) {
        for (int i = 0; i <= segments; i++) {
            double a = Math.toRadians(startDeg + (endDeg - startDeg) * i / segments);
            GL11.glVertex2f(cx + (float)(Math.cos(a) * r), cy + (float)(Math.sin(a) * r));
        }
    }

    /** Thin 1px bottom border line. */
    public static void border(float x, float y, float w, float h, int color) {
        float a = (color >> 24 & 0xFF) / 255f;
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >>  8 & 0xFF) / 255f;
        float b = (color       & 0xFF) / 255f;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x, y); GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h); GL11.glVertex2f(x, y + h);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    // ── Slider rail + fill ────────────────────────────────────────────────────

    public static void sliderRail(float x, float y, float w, float h, float fill0to1, int railColor, int fillColor) {
        roundedRect(x, y, w,         h, h / 2f, railColor);
        roundedRect(x, y, w * fill0to1, h, h / 2f, fillColor);
    }

    // ── Toggle circle ─────────────────────────────────────────────────────────

    public static void toggle(float cx, float cy, float r, int trackColorOff, int trackColorOn, boolean on, float anim) {
        int trackColor = lerpColor(trackColorOff, trackColorOn, anim);
        // Track
        float tw = r * 2 + r;
        float tx = cx - tw / 2f;
        float ty = cy - r / 2f;
        roundedRect(tx, ty, tw, r, r / 2f, trackColor);
        // Thumb
        float thumbX = tx + r / 4f + (tw - r * 1.5f) * anim;
        fillCircle(thumbX + r / 2f, cy, r / 2f - 0.5f, 0xFFFFFFFF);
    }

    public static void fillCircle(float cx, float cy, float r, int color) {
        float a = (color >> 24 & 0xFF) / 255f;
        float rd = (color >> 16 & 0xFF) / 255f;
        float g  = (color >>  8 & 0xFF) / 255f;
        float b  = (color       & 0xFF) / 255f;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(rd, g, b, a);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        int segs = 16;
        for (int i = 0; i <= segs; i++) {
            double ang = Math.toRadians(360.0 * i / segs);
            GL11.glVertex2f(cx + (float)(Math.cos(ang) * r), cy + (float)(Math.sin(ang) * r));
        }
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    // ── Scissor / clip ────────────────────────────────────────────────────────

    public static void pushScissor(float x, float y, float w, float h) {
        int scale = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
        int scrH  = mc.displayHeight;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int)(x * scale), scrH - (int)((y + h) * scale), (int)(w * scale), (int)(h * scale));
    }

    public static void popScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ── Text ──────────────────────────────────────────────────────────────────

    public static void text(String s, float x, float y, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.enableTexture2D();
        mc.fontRendererObj.drawStringWithShadow(s, x, y, color);
        GlStateManager.popMatrix();
    }

    public static void textNoShadow(String s, float x, float y, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.enableTexture2D();
        mc.fontRendererObj.drawString(s, x, y, color, false);
        GlStateManager.popMatrix();
    }

    public static int textW(String s) {
        return mc.fontRendererObj.getStringWidth(s);
    }

    public static int textH() {
        return mc.fontRendererObj.FONT_HEIGHT;
    }

    // ── Color helpers ─────────────────────────────────────────────────────────

    private static void setColor(int c) {
        GL11.glColor4f((c >> 16 & 0xFF) / 255f, (c >> 8 & 0xFF) / 255f, (c & 0xFF) / 255f, (c >> 24 & 0xFF) / 255f);
    }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16 & 0xFF), ag = (a >> 8 & 0xFF), ab = (a & 0xFF), aa = (a >> 24 & 0xFF);
        int br = (b >> 16 & 0xFF), bg = (b >> 8 & 0xFF), bb = (b & 0xFF), ba = (b >> 24 & 0xFF);
        int r = (int)(ar + (br - ar) * t);
        int g = (int)(ag + (bg - ag) * t);
        int bv = (int)(ab + (bb - ab) * t);
        int av = (int)(aa + (ba - aa) * t);
        return (av << 24) | (r << 16) | (g << 8) | bv;
    }

    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private GuiRender() {}
}
