package myau.ui.utils;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class GuiUtils {

    // === Rounded Rectangle ===
    public static void drawRoundedRect(int x, int y, int w, int h, int radius, Color color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float a = color.getAlpha() / 255f;

        GL11.glColor4f(r, g, b, a);

        drawRoundedRectInternal(x, y, w, h, radius);

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static void drawRoundedRectInternal(float x, float y, float w, float h, float r) {
        // Middle
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x + r, y);
        GL11.glVertex2f(x + w - r, y);
        GL11.glVertex2f(x + w - r, y + h);
        GL11.glVertex2f(x + r, y + h);
        GL11.glEnd();

        // Left
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y + r);
        GL11.glVertex2f(x + r, y + r);
        GL11.glVertex2f(x + r, y + h - r);
        GL11.glVertex2f(x, y + h - r);
        GL11.glEnd();

        // Right
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x + w - r, y + r);
        GL11.glVertex2f(x + w, y + r);
        GL11.glVertex2f(x + w, y + h - r);
        GL11.glVertex2f(x + w - r, y + h - r);
        GL11.glEnd();

        // Corners
        drawCircle(x + r, y + r, r, 180, 270);
        drawCircle(x + w - r, y + r, r, 270, 360);
        drawCircle(x + w - r, y + h - r, r, 0, 90);
        drawCircle(x + r, y + h - r, r, 90, 180);
    }

    private static void drawCircle(float cx, float cy, float r, int start, int end) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = start; i <= end; i++) {
            double rad = Math.toRadians(i);
            GL11.glVertex2f((float) (cx + Math.cos(rad) * r), (float) (cy + Math.sin(rad) * r));
        }
        GL11.glEnd();
    }

    // === Soft Shadow ===
    public static void drawShadow(int x, int y, int w, int h, int size) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();

        for (int i = 0; i < size; i++) {
            float alpha = (float) (40f / (i + 8));
            GL11.glColor4f(0, 0, 0, alpha / 255f);

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(x - i, y - i);
            GL11.glVertex2f(x + w + i, y - i);
            GL11.glVertex2f(x + w + i, y + h + i);
            GL11.glVertex2f(x - i, y + h + i);
            GL11.glEnd();
        }

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }
}
// force build
