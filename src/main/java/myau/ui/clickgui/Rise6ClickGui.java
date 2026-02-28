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

    private static final int TAB_H   = 26;
    private static final int TAB_PAD = 14;

    public Rise6ClickGui(
            List<Module> combat, List<Module> movement, List<Module> player,
            List<Module> render, List<Module> misc) {
        tabModules.add(combat); tabModules.add(movement); tabModules.add(player);
        tabModules.add(render); tabModules.add(misc);
        modulePanel = new NewModulePanel(tabModules.get(selectedTab));
        searchBar   = new NewSearchBar();
    }

    private int sw() { return new ScaledResolution(mc).getScaledWidth(); }
    private int sh() { return new ScaledResolution(mc).getScaledHeight(); }

    private int tabW(int i) { return mc.fontRendererObj.getStringWidth(TAB_NAMES[i]) + TAB_PAD * 2; }
    private int tabX(int i) {
        int totalW = 0;
        for (int j = 0; j < TAB_NAMES.length; j++) totalW += tabW(j) + 5;
        totalW -= 5;
        int x = (sw() - totalW) / 2;
        for (int j = 0; j < i; j++) x += tabW(j) + 5;
        return x;
    }

    private int barY()     { return 8; }
    private int contentY() { return barY() + TAB_H + 10; }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int W = sw(), H = sh();

        // Very subtle dim — game still fully visible underneath
        drawRect(0, 0, W, H, 0x33000000);

        // Soft vignette for depth
        drawGradientRect(0, 0, W, 60, 0x18000000, 0x00000000);
        drawGradientRect(0, H - 40, W, H, 0x00000000, 0x18000000);

        // ── Tab row ───────────────────────────────────────────────────────────
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int tx = tabX(i), ty = barY(), tw = tabW(i), th = TAB_H;
            boolean sel = selectedTab == i;
            boolean hov = mouseX >= tx && mouseX <= tx + tw && mouseY >= ty && mouseY <= ty + th;

            if (sel) {
                // Filled pink pill
                RoundedUtils.drawRoundedRect(tx, ty, tw, th, th / 2, GuiColors.ACCENT);
                // Soft glow behind it
                RoundedUtils.drawRoundedRect(tx - 2, ty - 2, tw + 4, th + 4, th / 2 + 2, GuiColors.ACCENT_GLOW);
                gl(); mc.fontRendererObj.drawString(TAB_NAMES[i], tx + TAB_PAD, ty + (th - 8) / 2, 0xFF1A0D12);
            } else {
                // Ghost outline pill
                RoundedUtils.drawRoundedRect(tx, ty, tw, th, th / 2, hov ? 0x33E991B8 : 0x18FFFFFF);
                gl(); mc.fontRendererObj.drawString(TAB_NAMES[i], tx + TAB_PAD, ty + (th - 8) / 2,
                        hov ? GuiColors.ACCENT : 0xFF888888);
            }
        }

        // ── Search ────────────────────────────────────────────────────────────
        int sbW = 160, sbX = W / 2 - sbW / 2;
        searchBar.render(sbX, contentY(), mouseX, mouseY, sbW);

        // ── Modules ───────────────────────────────────────────────────────────
        int gridY = contentY() + 24;
        modulePanel.setVisibleArea(10, gridY, W - 20, H - gridY - 8);
        modulePanel.render(mouseX, mouseY, searchBar.getText());

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void handleMouseInput() throws IOException {
        int d = Mouse.getEventDWheel();
        if (d != 0) modulePanel.handleScroll(d);
        super.handleMouseInput();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int tx = tabX(i), tw = tabW(i);
            if (button == 0 && mouseX >= tx && mouseX <= tx + tw
                    && mouseY >= barY() && mouseY <= barY() + TAB_H) {
                if (selectedTab != i) { selectedTab = i; modulePanel.setModules(tabModules.get(i)); }
                return;
            }
        }
        int sbX = sw() / 2 - 80;
        searchBar.mouseClicked(sbX, contentY(), mouseX, mouseY, button);
        modulePanel.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int b, long t) { modulePanel.mouseDrag(mouseX); }
    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state)   { modulePanel.mouseReleased(); }

    @Override
    protected void keyTyped(char c, int keyCode) throws IOException {
        if (keyCode == 1) { mc.displayGuiScreen(null); return; }
        if (searchBar.keyTyped(c, keyCode)) return;
        modulePanel.keyTyped(c, keyCode);
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    private void gl() { GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1,1,1,1); }
}
