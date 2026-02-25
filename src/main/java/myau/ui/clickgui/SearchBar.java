package myau.ui.clickgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.lwjgl.opengl.GL11;

public class SearchBar {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private String  text      = "";
    private boolean focused   = false;
    private long    lastBlink = 0;
    private boolean showCaret = true;

    public void render(int x, int y, int mouseX, int mouseY) {
        int width  = 240;
        int height = 16;

        // No background box — just a thin bottom border
        drawRect(x, y + height - 1, x + width, y + height, focused ? 0xFF55AAFF : 0xFF333333);

        // Placeholder or typed text
        resetGL();
        mc.fontRendererObj.drawString(
                text.isEmpty() && !focused ? "Search..." : text,
                x + 2, y + 4,
                text.isEmpty() && !focused ? 0xFF555555 : 0xFFCCCCCC
        );

        // Blinking caret
        if (focused) {
            if (System.currentTimeMillis() - lastBlink > 500) {
                showCaret = !showCaret;
                lastBlink = System.currentTimeMillis();
            }
            if (showCaret) {
                int caretX = x + 2 + mc.fontRendererObj.getStringWidth(text);
                drawRect(caretX, y + 3, caretX + 1, y + height - 4, 0xFFCCCCCC);
            }
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        // Focused state handled by Rise6ClickGui passing real coordinates
        // We receive the correct x/y from render, so compare directly
        // The GUI calls this with the raw mouse pos; focused is toggled by Rise6ClickGui
    }

    // Called by Rise6ClickGui which knows the actual render position
    public void mouseClickedAt(int x, int y, int mouseX, int mouseY, int button) {
        int width = 240, height = 16;
        boolean inside = mouseX >= x && mouseX <= x + width &&
                         mouseY >= y && mouseY <= y + height;
        if (button == 0) focused = inside;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (!focused) return false;
        if (keyCode == 14) { // Backspace
            if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
            return true;
        }
        if (Character.isLetterOrDigit(typedChar) || typedChar == ' ') {
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
        float b = (color       & 0xFF) / 255f;
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
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private void resetGL() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
}