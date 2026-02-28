package myau.ui.clickgui;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

public class NewSearchBar {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private String  text    = "";
    private boolean focused = false;
    private long    blink   = 0;
    private boolean caret   = true;

    public void render(int x, int y, int mx, int my, int w) {
        int h = 18;
        // Ghost pill
        RoundedUtils.drawRoundedRect(x, y, w, h, h / 2,
                focused ? 0x33E991B8 : 0x18FFFFFF);
        // Pink bottom line when focused
        if (focused) drawRect(x + 8, y + h - 1, x + w - 8, y + h, Rise6ClickGui.ACCENT_DIM);

        gl();
        String disp = text.isEmpty() && !focused ? "Search..." : text;
        int tc = text.isEmpty() && !focused ? 0xFF444444 : 0xFFCCCCCC;
        mc.fontRendererObj.drawString(disp, x + 10, y + (h - 8) / 2, tc);

        if (focused) {
            if (System.currentTimeMillis() - blink > 530) { caret = !caret; blink = System.currentTimeMillis(); }
            if (caret) drawRect(x + 10 + mc.fontRendererObj.getStringWidth(text), y + 3, x + 11 + mc.fontRendererObj.getStringWidth(text), y + h - 3, Rise6ClickGui.ACCENT);
        }
    }

    public void mouseClicked(int x, int y, int mx, int my, int button) {
        focused = button == 0 && mx >= x && mx <= x + 160 && my >= y && my <= y + 18;
    }

    public boolean keyTyped(char c, int kc) {
        if (!focused) return false;
        if (kc == 14) { if (!text.isEmpty()) text = text.substring(0, text.length() - 1); return true; }
        if (Character.isDefined(c) && c >= 32) { text += c; return true; }
        return false;
    }

    public String getText() { return text; }

    private void drawRect(int x1, int y1, int x2, int y2, int color) {
        float a=(color>>24&0xFF)/255f, r=(color>>16&0xFF)/255f, g=(color>>8&0xFF)/255f, b=(color&0xFF)/255f;
        GL11.glEnable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r,g,b,a); GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(x1,y2); GL11.glVertex2d(x2,y2); GL11.glVertex2d(x2,y1); GL11.glVertex2d(x1,y1);
        GL11.glEnd(); GL11.glDisable(GL11.GL_BLEND); GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1,1,1,1);
    }

    private void gl() { GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1,1,1,1); }
}
