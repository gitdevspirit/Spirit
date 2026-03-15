package myau.ui.intel;

import myau.ui.clickgui.GuiColors;
import myau.ui.clickgui.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/**
 * Centered popup settings GUI for the Intel HUD Overlay
 */
public class IntelHudSettingsGui extends GuiScreen {
    
    private final IntelHudOverlay hudOverlay;
    private final GuiScreen parent;
    
    // Layout
    private static final int POPUP_WIDTH = 280;
    private static final int POPUP_HEIGHT = 420;
    private static final int PADDING = 12;
    private static final int LINE_HEIGHT = 20;
    private static final int SLIDER_HEIGHT = 28;
    private static final int TOGGLE_HEIGHT = 24;
    
    // Colors
    private static final int BG_POPUP = 0xEE0A0A12;
    private static final int BG_HEADER = 0xFF15151D;
    private static final int ACCENT = GuiColors.ACCENT;
    private static final int TEXT_BRIGHT = 0xFFDDDDEE;
    private static final int TEXT_DIM = 0xFF888899;
    private static final int DIVIDER = 0x33FFFFFF;
    
    // State
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean draggingSlider = false;
    private int draggingSliderIndex = -1;
    
    public IntelHudSettingsGui(IntelHudOverlay hudOverlay, GuiScreen parent) {
        this.hudOverlay = hudOverlay;
        this.parent = parent;
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Draw dark overlay over entire screen
        drawRect(0, 0, width, height, 0xCC000000);
        
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();
        
        // Small centered popup
        int popupX = (sw - POPUP_WIDTH) / 2;
        int popupY = (sh - POPUP_HEIGHT) / 2;
        
        // Draw popup background with rounded corners
        RoundedUtils.drawRoundedRect(popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, 6, BG_POPUP);
        
        // Draw header
        RoundedUtils.drawRoundedRect(popupX, popupY, POPUP_WIDTH, 36, 6, BG_HEADER);
        drawRect(popupX, popupY + 30, popupX + POPUP_WIDTH, popupY + 36, BG_HEADER); // Square off bottom
        
        // Title
        GlStateManager.pushMatrix();
        GlStateManager.translate(popupX + PADDING, popupY + 12, 0);
        GlStateManager.scale(1.1f, 1.1f, 1f);
        mc.fontRendererObj.drawString("HUD Overlay", 0, 0, ACCENT, false);
        GlStateManager.popMatrix();
        
        // Close button (X)
        int closeX = popupX + POPUP_WIDTH - 28;
        int closeY = popupY + 10;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + 18 && mouseY >= closeY && mouseY < closeY + 18;
        mc.fontRendererObj.drawString("X", closeX + 5, closeY + 5, closeHover ? 0xFFFF4444 : TEXT_BRIGHT, false);
        
        // Enable scissor for scrollable content
        GlStateManager.pushMatrix();
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        int scale = sr.getScaleFactor();
        int contentY = popupY + 46;
        int contentH = POPUP_HEIGHT - 56;
        org.lwjgl.opengl.GL11.glScissor(popupX * scale, (sh - contentY - contentH) * scale, POPUP_WIDTH * scale, contentH * scale);
        
        int y = contentY - scrollOffset + PADDING;
        int innerX = popupX + PADDING;
        int innerW = POPUP_WIDTH - PADDING * 2;
        
        // === ENABLED TOGGLE ===
        y = drawToggle(innerX, y, innerW, "Enabled", hudOverlay.isEnabled(), mouseX, mouseY);
        y += 6;
        drawDivider(innerX, y, innerW);
        y += 12;
        
        // === POSITION SECTION ===
        drawLabel(innerX, y, "POSITION");
        y += 16;
        y = drawSlider(innerX, y, innerW, "X Position", hudOverlay.getPosX(), 0, 1920, mouseX, mouseY, 0);
        y = drawSlider(innerX, y, innerW, "Y Position", hudOverlay.getPosY(), 0, 1080, mouseX, mouseY, 1);
        y += 6;
        drawDivider(innerX, y, innerW);
        y += 12;
        
        // === DISPLAY SECTION ===
        drawLabel(innerX, y, "DISPLAY");
        y += 16;
        y = drawSlider(innerX, y, innerW, "Scale", (int)(hudOverlay.getScale() * 100), 50, 200, mouseX, mouseY, 2);
        y = drawSlider(innerX, y, innerW, "Max Players", hudOverlay.getMaxPlayers(), 1, 20, mouseX, mouseY, 3);
        y = drawSlider(innerX, y, innerW, "Background Opacity", hudOverlay.getBgOpacity(), 0, 255, mouseX, mouseY, 4);
        y = drawSlider(innerX, y, innerW, "Border Opacity", hudOverlay.getBorderOpacity(), 0, 255, mouseX, mouseY, 5);
        y += 6;
        drawDivider(innerX, y, innerW);
        y += 12;
        
        // === COLUMNS SECTION ===
        drawLabel(innerX, y, "COLUMNS");
        y += 20;
        y = drawToggle(innerX, y, innerW, "Player Heads", hudOverlay.getShowHeads(), mouseX, mouseY);
        y = drawToggle(innerX, y, innerW, "Star", hudOverlay.getShowStar(), mouseX, mouseY);
        y = drawToggle(innerX, y, innerW, "FKDR", hudOverlay.getShowFkdr(), mouseX, mouseY);
        y = drawToggle(innerX, y, innerW, "WLR", hudOverlay.getShowWlr(), mouseX, mouseY);
        y = drawToggle(innerX, y, innerW, "Winstreak", hudOverlay.getShowStreak(), mouseX, mouseY);
        y = drawToggle(innerX, y, innerW, "Urchin Icon", hudOverlay.getShowUrchin(), mouseX, mouseY);
        y = drawToggle(innerX, y, innerW, "Threat Score", hudOverlay.getShowThreat(), mouseX, mouseY);
        y = drawToggle(innerX, y, innerW, "Team Colors", hudOverlay.getShowTeamColor(), mouseX, mouseY);
        y += 8;
        drawDivider(innerX, y, innerW);
        y += 16;
        
        // === SORTING SECTION ===
        drawLabel(innerX, y, "SORTING");
        y += 20;
        y = drawDropdown(innerX, y, innerW, "Sort By", new String[]{"Threat", "FKDR", "Name"}, 
            hudOverlay.getSortMode().equals("threat") ? 0 : hudOverlay.getSortMode().equals("fkdr") ? 1 : 2, mouseX, mouseY);
        
        // Calculate max scroll
        int contentHeight = y - (contentY + PADDING) + PADDING;
        maxScroll = Math.max(0, contentHeight - contentH + PADDING * 2);
        
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        GlStateManager.popMatrix();
        
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    
    private void drawLabel(int x, int y, String text) {
        mc.fontRendererObj.drawString(text, x, y, TEXT_DIM, false);
    }
    
    private void drawDivider(int x, int y, int w) {
        drawRect(x, y, x + w, y + 1, DIVIDER);
    }
    
    private int drawToggle(int x, int y, int w, String label, boolean value, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + TOGGLE_HEIGHT;
        
        mc.fontRendererObj.drawString(label, x, y + 8, hover ? TEXT_BRIGHT : TEXT_DIM, false);
        
        // Toggle switch on right
        int toggleW = 40;
        int toggleH = 20;
        int toggleX = x + w - toggleW;
        int toggleY = y + 4;
        
        int bgColor = value ? (ACCENT & 0x00FFFFFF) | 0x44000000 : 0x33444455;
        RoundedUtils.drawRoundedRect(toggleX, toggleY, toggleW, toggleH, toggleH / 2, bgColor);
        
        // Knob
        int knobSize = 16;
        int knobX = value ? toggleX + toggleW - knobSize - 2 : toggleX + 2;
        int knobY = toggleY + 2;
        RoundedUtils.drawRoundedRect(knobX, knobY, knobSize, knobSize, knobSize / 2, value ? ACCENT : 0xFF666677);
        
        return y + TOGGLE_HEIGHT;
    }
    
    private int drawSlider(int x, int y, int w, String label, int value, int min, int max, int mx, int my, int sliderIndex) {
        // Draw label
        mc.fontRendererObj.drawString(label, x, y, TEXT_BRIGHT, false);
        y += 10;
        
        // Draw value on right
        String valStr = String.valueOf(value);
        mc.fontRendererObj.drawString(valStr, x + w - mc.fontRendererObj.getStringWidth(valStr), y, ACCENT, false);
        
        // Slider bar position
        int barX = x;
        int barY = y;
        int barW = w - 30; // Leave space for value
        int barH = 8;
        
        // Background track
        drawRect(barX, barY, barX + barW, barY + barH, 0x66444444);
        
        // Filled portion
        float pct = (value - min) / (float)(max - min);
        int fillW = Math.max(2, (int)(barW * pct));
        drawRect(barX, barY, barX + fillW, barY + barH, ACCENT & 0xFFFFFFFF);
        
        // Knob
        int knobSize = 12;
        int knobX = barX + fillW - knobSize / 2;
        int knobY = barY - 2;
        drawRect(knobX, knobY, knobX + knobSize, knobY + knobSize, 0xFFFFFFFF);
        drawRect(knobX + 2, knobY + 2, knobX + knobSize - 2, knobY + knobSize - 2, ACCENT & 0xFFFFFFFF);
        
        return y + 20;
    }
    
    private int drawDropdown(int x, int y, int w, String label, String[] options, int selected, int mx, int my) {
        mc.fontRendererObj.drawString(label, x, y, TEXT_DIM, false);
        y += 12;
        
        int btnH = 24;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + btnH;
        
        RoundedUtils.drawRoundedRect(x, y, w, btnH, 4, hover ? 0x44FFFFFF : 0x22FFFFFF);
        mc.fontRendererObj.drawString(options[selected], x + 12, y + 8, TEXT_BRIGHT, false);
        
        // Arrow
        mc.fontRendererObj.drawString("v", x + w - 20, y + 8, TEXT_DIM, false);
        
        return y + btnH + 8;
    }
    
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();
        int popupX = (sw - POPUP_WIDTH) / 2;
        int popupY = (sh - POPUP_HEIGHT) / 2;
        
        // Close button
        int closeX = popupX + POPUP_WIDTH - 28;
        int closeY = popupY + 10;
        if (mouseX >= closeX && mouseX < closeX + 18 && mouseY >= closeY && mouseY < closeY + 18) {
            saveAndClose();
            return;
        }
        
        // Click outside popup closes it
        if (mouseX < popupX || mouseX > popupX + POPUP_WIDTH || mouseY < popupY || mouseY > popupY + POPUP_HEIGHT) {
            saveAndClose();
            return;
        }
        
        int contentY = popupY + 46;
        int y = contentY - scrollOffset + PADDING;
        int innerX = popupX + PADDING;
        int innerW = POPUP_WIDTH - PADDING * 2;
        
        // Enabled toggle
        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + TOGGLE_HEIGHT) {
            hudOverlay.setEnabled(!hudOverlay.isEnabled());
            return;
        }
        y += TOGGLE_HEIGHT + 6 + 12 + 16;
        
        // Position sliders (clickable)
        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + SLIDER_HEIGHT) {
            int val = (int)((mouseX - innerX) / (float)innerW * 1920);
            hudOverlay.setPosition(Math.max(0, Math.min(1920, val)), hudOverlay.getPosY());
            draggingSlider = true;
            draggingSliderIndex = 0;
            return;
        }
        y += SLIDER_HEIGHT;
        
        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + SLIDER_HEIGHT) {
            int val = (int)((mouseX - innerX) / (float)innerW * 1080);
            hudOverlay.setPosition(hudOverlay.getPosX(), Math.max(0, Math.min(1080, val)));
            draggingSlider = true;
            draggingSliderIndex = 1;
            return;
        }
        y += SLIDER_HEIGHT + 6 + 12 + 16;
        
        // Display sliders
        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + SLIDER_HEIGHT) {
            int val = 50 + (int)((mouseX - innerX) / (float)innerW * 150);
            hudOverlay.setScale(Math.max(0.5f, Math.min(2.0f, val / 100f)));
            draggingSlider = true;
            draggingSliderIndex = 2;
            return;
        }
        y += SLIDER_HEIGHT;
        
        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + SLIDER_HEIGHT) {
            int val = 1 + (int)((mouseX - innerX) / (float)innerW * 19);
            hudOverlay.setMaxPlayers(Math.max(1, Math.min(20, val)));
            draggingSlider = true;
            draggingSliderIndex = 3;
            return;
        }
        y += SLIDER_HEIGHT;
        
        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + SLIDER_HEIGHT) {
            int val = (int)((mouseX - innerX) / (float)innerW * 255);
            hudOverlay.setBgOpacity(Math.max(0, Math.min(255, val)));
            draggingSlider = true;
            draggingSliderIndex = 4;
            return;
        }
        y += SLIDER_HEIGHT;
        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + SLIDER_HEIGHT) {
            int val = (int)((mouseX - innerX) / (float)innerW * 255);
            hudOverlay.setBorderOpacity(Math.max(0, Math.min(255, val)));
            draggingSlider = true;
            draggingSliderIndex = 5;
            return;
        }
        y += SLIDER_HEIGHT + 6 + 12 + 16;
        
        // Column toggles
        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + TOGGLE_HEIGHT) {
            hudOverlay.setShowHeads(!hudOverlay.getShowHeads()); return;
        }
        y += TOGGLE_HEIGHT;
        
        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + TOGGLE_HEIGHT) {
            hudOverlay.setShowStar(!hudOverlay.getShowStar()); return;
        }
        y += TOGGLE_HEIGHT;
        
        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + TOGGLE_HEIGHT) {
            hudOverlay.setShowFkdr(!hudOverlay.getShowFkdr()); return;
        }
        y += TOGGLE_HEIGHT;
        
        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + TOGGLE_HEIGHT) {
            hudOverlay.setShowWlr(!hudOverlay.getShowWlr()); return;
        }
        y += TOGGLE_HEIGHT;
        
        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + TOGGLE_HEIGHT) {
            hudOverlay.setShowStreak(!hudOverlay.getShowStreak()); return;
        }
        y += TOGGLE_HEIGHT;
        
        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + TOGGLE_HEIGHT) {
            hudOverlay.setShowUrchin(!hudOverlay.getShowUrchin()); return;
        }
        y += TOGGLE_HEIGHT;
        
        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + TOGGLE_HEIGHT) {
            hudOverlay.setShowThreat(!hudOverlay.getShowThreat()); return;
        }
        y += TOGGLE_HEIGHT;
        
        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + TOGGLE_HEIGHT) {
            hudOverlay.setShowTeamColor(!hudOverlay.getShowTeamColor()); return;
        }
        y += TOGGLE_HEIGHT + 8 + 16 + 20;
        
        // Sort dropdown
        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y + 12 && mouseY < y + 12 + 24) {
            String mode = hudOverlay.getSortMode();
            String newMode = mode.equals("threat") ? "fkdr" : mode.equals("fkdr") ? "name" : "threat";
            hudOverlay.setSortMode(newMode);
            return;
        }
        
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
    
    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingSlider = false;
        draggingSliderIndex = -1;
        super.mouseReleased(mouseX, mouseY, state);
    }
    
    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (!draggingSlider || draggingSliderIndex < 0) return;
        
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth();
        int popupX = (sw - POPUP_WIDTH) / 2;
        int innerX = popupX + PADDING;
        int innerW = POPUP_WIDTH - PADDING * 2 - 30; // Account for value text space
        
        switch (draggingSliderIndex) {
            case 0: // X Position
                int valX = (int)((mouseX - innerX) / (float)innerW * 1920);
                hudOverlay.setPosition(Math.max(0, Math.min(1920, valX)), hudOverlay.getPosY());
                break;
            case 1: // Y Position
                int valY = (int)((mouseX - innerX) / (float)innerW * 1080);
                hudOverlay.setPosition(hudOverlay.getPosX(), Math.max(0, Math.min(1080, valY)));
                break;
            case 2: // Scale
                int valScale = 50 + (int)((mouseX - innerX) / (float)innerW * 150);
                hudOverlay.setScale(Math.max(0.5f, Math.min(2.0f, valScale / 100f)));
                break;
            case 3: // Max Players
                int valMax = 1 + (int)((mouseX - innerX) / (float)innerW * 19);
                hudOverlay.setMaxPlayers(Math.max(1, Math.min(20, valMax)));
                break;
            case 4: // Background Opacity
                int valOpacity = (int)((mouseX - innerX) / (float)innerW * 255);
                hudOverlay.setBgOpacity(Math.max(0, Math.min(255, valOpacity)));
                break;
            case 5: // Border Opacity
                int valBorder = (int)((mouseX - innerX) / (float)innerW * 255);
                hudOverlay.setBorderOpacity(Math.max(0, Math.min(255, valBorder)));
                break;
        }
    }
    
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) {
            scrollOffset -= dw > 0 ? 20 : -20;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        }
    }
    
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
    
    private void saveAndClose() {
        // Save HUD settings to config
        myau.module.modules.LobbyIntel lobbyIntel = (myau.module.modules.LobbyIntel) 
            myau.Myau.moduleManager.getModule("LobbyIntel");
        if (lobbyIntel != null) {
            lobbyIntel.saveHudSettings();
        }
        mc.displayGuiScreen(parent);
    }
}
