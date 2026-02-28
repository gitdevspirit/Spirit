package myau.ui.clickgui;

import myau.module.Module;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Rise6ClickGui extends GuiScreen {

    private static final String[] TAB_NAMES = {"Combat", "Movement", "Player", "Render", "Misc"};

    private final List<List<Module>> tabModules = new ArrayList<>();
    private int selectedTab = 0;

    private final NewModulePanel modulePanel;
    private final NewSearchBar   searchBar;

    private static final int TAB_H       = 32;
    private static final int TAB_PAD     = 18;
    private static final int CONTENT_PAD = 14;

    public Rise6ClickGui(
            List<Module> combatModules,
            List<Module> movementModules,
            List<Module> playerModules,
            List<Module> renderModules,
            List<Module> miscModules
    ) {
        tabModules.add(combatModules);
        tabModules.add(movementModules);
        tabModules.add(playerModules);
        tabModules.add(renderModules);
        tabModules.add(miscModules);
        modulePanel = new NewModulePanel(tabModules.get(selectedTab));
        searchBar   = new NewSearchBar();
    }

    private int sw() { return new ScaledResolution(mc).getScaledWidth(); }
    private int sh() { return new ScaledResolution(mc).getScaledHeight(); }

    private int tabWidth(int i) {
        return mc.fontRendererObj.getStringWidth(TAB_NAMES[i]) + TAB_PAD * 2;
    }

    private int tabX(int i) {
        int totalW = 0;
        for (int j = 0; j < TAB_NAMES.length; j++) totalW += tabWidth(j) + 4;
        int x = (sw() - totalW) / 2;
        for (int j = 0; j < i; j++) x += tabWidth(j) + 4;
        return x;
    }

    private int barY()     { return 6; }
    private int contentY() { return barY() + TAB_H + 8; }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int W = sw(), H = sh();

        // Full-screen dark overlay
        drawRect(0, 0, W, H, 0xCC0A0A0A);
        drawGradientRect(0, 0, W, H / 3, 0x22000000, 0x00000000);
        drawGradientRect(0, H * 2 / 3, W, H, 0x00000000, 0x22000000);

        // Tab bar background
        int barW = 0;
        for (int i = 0; i < TAB_NAMES.length; i++) barW += tabWidth(i) + 4;
        barW -= 4;
        int barX = (W - barW) / 2;
        RoundedUtils.drawRoundedRect(barX - 8, barY(), barW + 16, TAB_H, 10, 0xEE141414);
        drawRect(barX - 8, barY(),              barX - 8 + barW + 16, barY() + 1,          0x18FFFFFF);
        drawRect(barX - 8, barY() + TAB_H - 1,  barX - 8 + barW + 16, barY() + TAB_H,     0x18FFFFFF);

        // Tab pills
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int tx = tabX(i);
            int ty = barY() + 5;
            int tw = tabWidth(i);
            int th = TAB_H - 10;
            boolean hov = mouseX >= tx && mouseX <= tx + tw && mouseY >= barY() && mouseY <= barY() + TAB_H;
            boolean sel = selectedTab == i;

            if (sel) {
                RoundedUtils.drawRoundedRect(tx, ty, tw, th, 6, 0xFFFFFFFF);
                GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1,1,1,1);
                mc.fontRendererObj.drawString(TAB_NAMES[i], tx + TAB_PAD, ty + (th - 8) / 2, 0xFF0D0D0D);
            } else {
                if (hov) RoundedUtils.drawRoundedRect(tx, ty, tw, th, 6, 0x22FFFFFF);
                GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1,1,1,1);
                mc.fontRendererObj.drawString(TAB_NAMES[i], tx + TAB_PAD, ty + (th - 8) / 2,
                        hov ? 0xFFEEEEEE : 0xFF666666);
            }
        }

        // Search bar
        int sbW = 200, sbX = W / 2 - sbW / 2;
        searchBar.render(sbX, contentY(), mouseX, mouseY, sbW);

        // Module panel
        int gridY = contentY() + 28;
        modulePanel.setVisibleArea(CONTENT_PAD, gridY, W - CONTENT_PAD * 2, H - gridY - CONTENT_PAD);
        modulePanel.render(mouseX, mouseY, searchBar.getText());

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void handleMouseInput() throws IOException {
        int delta = Mouse.getEventDWheel();
        if (delta != 0) modulePanel.handleScroll(delta);
        super.handleMouseInput();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int tx = tabX(i), tw = tabWidth(i);
            if (button == 0 && mouseX >= tx && mouseX <= tx + tw
                    && mouseY >= barY() && mouseY <= barY() + TAB_H) {
                if (selectedTab != i) { selectedTab = i; modulePanel.setModules(tabModules.get(i)); }
                return;
            }
        }
        int sbW = 200, sbX = sw() / 2 - sbW / 2;
        searchBar.mouseClicked(sbX, contentY(), mouseX, mouseY, button);
        modulePanel.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        modulePanel.mouseDrag(mouseX);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) { modulePanel.mouseReleased(); }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { mc.displayGuiScreen(null); return; }
        if (searchBar.keyTyped(typedChar, keyCode)) return;
        modulePanel.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
