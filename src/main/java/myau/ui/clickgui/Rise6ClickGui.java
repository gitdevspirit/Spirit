package myau.ui.clickgui;

import myau.config.GuiConfig;
import myau.module.Module;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Rise6ClickGui extends GuiScreen {

    private final List<SidebarCategory> categories = new ArrayList<>();
    private SidebarCategory selectedCategory;

    private SearchBar   searchBar;
    private ModulePanel modulePanel;
    private ConfigPanel configPanel;

    private boolean showConfigs = false;

    private boolean dragging    = false;
    private int dragOffsetX     = 0;
    private int dragOffsetY     = 0;

    private int posX;
    private int posY;

    private static final int SIDEBAR_WIDTH   = 120;
    private static final int PANEL_WIDTH     = 260;
    private static final int DRAG_BAR_HEIGHT = 16;
    private static final int TOTAL_WIDTH     = SIDEBAR_WIDTH + PANEL_WIDTH;

    public Rise6ClickGui(
            List<Module> combatModules,
            List<Module> movementModules,
            List<Module> playerModules,
            List<Module> renderModules,
            List<Module> miscModules
    ) {
        categories.add(new SidebarCategory("Combat",   combatModules));
        categories.add(new SidebarCategory("Movement", movementModules));
        categories.add(new SidebarCategory("Player",   playerModules));
        categories.add(new SidebarCategory("Render",   renderModules));
        categories.add(new SidebarCategory("Misc",     miscModules));

        selectedCategory = categories.get(0);
        searchBar   = new SearchBar();
        modulePanel = new ModulePanel(selectedCategory);
        configPanel = new ConfigPanel();

        GuiConfig.load();
        posX = GuiConfig.guiX;
        posY = GuiConfig.guiY;
    }

    private int getCategoryHeight() { return categories.size() * 24 + 28; }
    private int getConfigBtnY()     { return posY + getCategoryHeight() + 8; }
    private int getConfigPanelH()   { return showConfigs ? configPanel.getContentHeight() : 0; }
    private int getSidebarHeight()  { return getCategoryHeight() + 8 + 16 + getConfigPanelH() + 8; }
    private int getPanelHeight() {
        ScaledResolution sr = new ScaledResolution(mc);
        int maxH = sr.getScaledHeight() - posY - 4; // 4px bottom margin
        int ideal = Math.max(modulePanel.getContentHeight() + 50, getSidebarHeight());
        return Math.min(ideal, Math.max(getSidebarHeight(), maxH));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int panelHeight = getPanelHeight();
        // visibleHeight = space available for the module list (below search bar + separator)
        // This is independent of content height so scroll kicks in when content overflows
        int moduleAreaTop = 36; // search bar (10) + bar height (18) + separator (1) + gap (7)
        modulePanel.setVisibleHeight(panelHeight - moduleAreaTop - 8); // 8px bottom padding

        // Single clean panel — no outer shadow box
        RoundedUtils.drawRoundedRect(posX, posY, TOTAL_WIDTH, panelHeight, 10, 0xF0101010);

        // Sidebar slightly lighter
        RoundedUtils.drawRoundedRect(posX, posY, SIDEBAR_WIDTH, panelHeight, 10, 0xF0181818);

        // Title
        GL11.glColor4f(1f, 1f, 1f, 1f);
        mc.fontRendererObj.drawString("§b§lSpirit", posX + 10, posY + 8, 0xFFFFFFFF);

        // Categories
        int yOffset = posY + 28;
        for (SidebarCategory cat : categories) {
            boolean selected = selectedCategory == cat;
            boolean hovered  = mouseX >= posX + 6 && mouseX <= posX + SIDEBAR_WIDTH - 6 &&
                               mouseY >= yOffset - 2 && mouseY <= yOffset + 18;

            if (selected) {
                RoundedUtils.drawRoundedRect(posX + 6, yOffset - 2, SIDEBAR_WIDTH - 12, 20, 4, 0xFF1A3A5C);
                drawRectGui(posX + 4, yOffset, posX + 7, yOffset + 14, 0xFF55AAFF);
            } else if (hovered) {
                RoundedUtils.drawRoundedRect(posX + 6, yOffset - 2, SIDEBAR_WIDTH - 12, 20, 4, 0xFF1E1E1E);
            }

            int textColor = selected ? 0xFF55AAFF : (hovered ? 0xFFCCCCCC : 0xFF888888);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            mc.fontRendererObj.drawString(cat.getName(), posX + 14, yOffset + 3, textColor);
            yOffset += 24;
        }

        // Configs button
        int configBtnY = getConfigBtnY();
        boolean configHovered = mouseX >= posX + 6 && mouseX <= posX + SIDEBAR_WIDTH - 6 &&
                                mouseY >= configBtnY && mouseY <= configBtnY + 16;
        RoundedUtils.drawRoundedRect(posX + 6, configBtnY, SIDEBAR_WIDTH - 12, 16, 4,
                showConfigs ? 0xFF1A3A5C : (configHovered ? 0xFF1E1E1E : 0xFF161616));
        if (showConfigs)
            drawRectGui(posX + 4, configBtnY + 2, posX + 7, configBtnY + 14, 0xFF55AAFF);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        mc.fontRendererObj.drawString(
                showConfigs ? "§bConfigs" : "§7Configs",
                posX + 14, configBtnY + 4,
                showConfigs ? 0xFF55AAFF : (configHovered ? 0xFFCCCCCC : 0xFF888888));
        if (showConfigs)
            configPanel.render(posX + 6, configBtnY + 20, mouseX, mouseY);

        // Main panel — search bar (no background box) + thin separator + modules
        int panelX = posX + SIDEBAR_WIDTH + 8;
        searchBar.render(panelX, posY + 10, mouseX, mouseY);
        // Thin separator line
        drawRectGui(panelX, posY + 28, posX + TOTAL_WIDTH - 8, posY + 29, 0xFF252525);
        modulePanel.render(panelX, posY + 36, mouseX, mouseY, searchBar.getText());

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        GuiConfig.guiX = posX;
        GuiConfig.guiY = posY;
        GuiConfig.save();
    }

    @Override
    public void handleMouseInput() throws IOException {
        int delta = Mouse.getEventDWheel();
        if (delta != 0) modulePanel.handleScroll(delta);
        super.handleMouseInput();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (configPanel.isContextMenuOpen()) {
            configPanel.mouseClicked(posX + 6, getConfigBtnY() + 20, mouseX, mouseY, button);
            return;
        }

        // Drag
        if (button == 0 &&
            mouseX >= posX && mouseX <= posX + TOTAL_WIDTH &&
            mouseY >= posY && mouseY <= posY + DRAG_BAR_HEIGHT) {
            dragging = true;
            dragOffsetX = mouseX - posX;
            dragOffsetY = mouseY - posY;
            return;
        }

        // Categories
        int yOffset = posY + 28;
        for (SidebarCategory cat : categories) {
            if (mouseX >= posX + 6 && mouseX <= posX + SIDEBAR_WIDTH - 6 &&
                mouseY >= yOffset - 2 && mouseY <= yOffset + 18) {
                selectedCategory = cat;
                modulePanel.setCategory(cat);
                return;
            }
            yOffset += 24;
        }

        // Configs toggle
        int configBtnY = getConfigBtnY();
        if (button == 0 &&
            mouseX >= posX + 6 && mouseX <= posX + SIDEBAR_WIDTH - 6 &&
            mouseY >= configBtnY && mouseY <= configBtnY + 16) {
            showConfigs = !showConfigs;
            if (showConfigs) configPanel.refresh();
            return;
        }
        if (showConfigs)
            configPanel.mouseClicked(posX + 6, configBtnY + 20, mouseX, mouseY, button);

        int panelX = posX + SIDEBAR_WIDTH + 8;
        // Wire up searchBar focus with real coordinates
        searchBar.mouseClickedAt(panelX, posY + 10, mouseX, mouseY, button);
        modulePanel.mouseClicked(panelX, posY + 36, mouseX, mouseY, button);
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (dragging) {
            ScaledResolution sr = new ScaledResolution(mc);
            posX = Math.max(0, Math.min(sr.getScaledWidth()  - TOTAL_WIDTH, mouseX - dragOffsetX));
            posY = Math.max(0, Math.min(sr.getScaledHeight() - getSidebarHeight() - 20, mouseY - dragOffsetY));
        } else {
            modulePanel.mouseClickMove(mouseX);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        modulePanel.mouseReleased();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { mc.displayGuiScreen(null); return; }
        if (searchBar.keyTyped(typedChar, keyCode)) return;
        modulePanel.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private void drawRectGui(int x1, int y1, int x2, int y2, int color) {
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
}
