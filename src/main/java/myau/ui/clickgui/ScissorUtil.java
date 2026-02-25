package myau.ui.clickgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

public class ScissorUtil {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void enable(int x, int y, int width, int height) {
        try {
            ScaledResolution sr = new ScaledResolution(mc);
            int scale   = sr.getScaleFactor();
            int screenH = mc.displayHeight;

            int sx = x * scale;
            int sy = screenH - (y + height) * scale;
            int sw = width * scale;
            int sh = height * scale;

            if (sw <= 0 || sh <= 0) return;

            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(
                    Math.max(0, sx),
                    Math.max(0, sy),
                    sw,
                    sh
            );
        } catch (Exception e) {
            // If scissor fails just disable it
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    public static void disable() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
