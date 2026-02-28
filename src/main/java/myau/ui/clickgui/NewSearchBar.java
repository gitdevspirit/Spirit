package myau.ui.clickgui;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

public class NewSearchBar {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private String  text      = "";
    private boolean focused   = false;
    private long    lastBlink = 0;
    private boolean showCaret = true;

    public void render(int x, int y, int mouseX, int mouseY, int w) {
        int h = 18;

        // Pill background
        RoundedUtils.drawRoundedRect(x, y, w, h, 9, focused ? 0xFF1E1E1E : 0xFF161616);
        // Border — slightly brighter when focused
        drawRect(x, y, x + w, y + 1, focused ? 0x60FFFFFF : 0x25FFFFFF);
        drawRect(x, y + h - 1, x + w, y + h, focused ? 0x60FFFFFF : 0x25FFFFFF);
        drawRect(x, y, x + 1, y + h, focused ? 0x60FFFFFF : 0x25FFFFFF);
        drawRect(x + w - 1, y, x + w, y + h, focused ? 0x60FFFFFF : 0x25FFFFFF);

        // Search icon (magnifying glass approximation)
        GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1,1,1,1);
        mc.fontRendererObj.drawString("\u2315", x + 5, y + 5, focused ? 0xFF888888 : 0xFF444444);

        // Text / placeholder
        GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1,1,1,1);
        String display = (text.isEmpty() && !focused) ? "Search modules..." : text;
        int textColor  = (text.isEmpty() && !focused) ? 0xFF3A3A3A : 0xFFCCCCCC;
        mc.fontRendererObj.drawString(display, x + 16, y + 5, textColor);

        // Caret
        if (focused) {
            if (System.currentTimeMillis() - lastBlink > 530) {
                showCaret = !showCaret;
                lastBlink = System.currentTimeMillis();
            }
            if (showCaret) {
                int cx = x + 16 + mc.fontRendererObj.getStringWidth(text);
                drawRect(cx, y + 4, cx + 1, y + h - 4, 0xFFCCCCCC);
            }
        }
    }

    public void mouseClicked(int x, int y, int mouseX, int mouseY, int button) {
        focused = button == 0 && mouseX >= x && mouseX <= x + 200
                && mouseY >= y && mouseY <= y + 18;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (!focused) return false;
        if (keyCode == 14) {
            if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
            return true;
        }
        if (Character.isDefined(typedChar) && typedChar >= 32) {
            text += typedChar;
            return true;
        }
        return false;
    }

    public String getText() { return text; }

    private void drawRect(int x1, int y1, int x2, int y2, int color) {
        float a = (color >> 24 & 0xFF) / 255f;
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >> 8  & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(x1, y2); GL11.glVertex2d(x2, y2);
        GL11.glVertex2d(x2, y1); GL11.glVertex2d(x1, y1);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }
}
