package myau.ui.intel;

import myau.ui.clickgui.GuiColors;
import myau.ui.clickgui.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * HUD overlay that displays Intel player information during gameplay
 * Renders a compact list of players with their stats and threat levels
 */
public class IntelHudOverlay {

    private static final Minecraft mc = Minecraft.getMinecraft();
    
    // Layout constants
    private static final int LINE_HEIGHT = 18;
    private static final int HEAD_SIZE = 14;
    private static final int PADDING = 6;
    private static final int HEADER_HEIGHT = 12;
    private static final int BORDER_RADIUS = 4;
    
    // Color scheme - matching Spirit's aesthetic
    private int bgOpacity = 200; // 0-255, configurable
    private static final int BG_HOVER = 0xCC151535;
    private static final int ACCENT = GuiColors.ACCENT; // Pink
    private static final int TEXT_BRIGHT = 0xFFDDDDEE;
    private static final int TEXT_DIM = 0xFF888899;
    
    // Configuration (loaded from IntelGui settings panel)
    private boolean enabled = true;
    private int posX = 10;
    private int posY = 100;
    private float scale = 1.0f;
    private int maxPlayers = 10;
    private boolean showHeads = true;
    private boolean showFkdr = true;
    private boolean showWlr = false;
    private boolean showStreak = false;
    private boolean showThreat = true;
    private boolean showTeamColor = true;
    private String sortMode = "threat"; // "threat", "fkdr", "name"
    
    private List<IntelPlayer> players = new ArrayList<>();
    
    // Skin cache shared with IntelGui
    private final java.util.Map<String, ResourceLocation> skinCache = new java.util.HashMap<>();
    private final java.util.Map<String, Boolean> skinIsSheet = new java.util.HashMap<>();
    
    public IntelHudOverlay() {}
    
    // ── Configuration Setters ──────────────────────────────────────────────────
    
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setPosition(int x, int y) { this.posX = x; this.posY = y; }
    public void setScale(float scale) { this.scale = Math.max(0.5f, Math.min(2.0f, scale)); }
    public void setMaxPlayers(int max) { this.maxPlayers = Math.max(1, Math.min(20, max)); }
    public void setShowHeads(boolean show) { this.showHeads = show; }
    public void setShowFkdr(boolean show) { this.showFkdr = show; }
    public void setShowWlr(boolean show) { this.showWlr = show; }
    public void setShowStreak(boolean show) { this.showStreak = show; }
    public void setShowThreat(boolean show) { this.showThreat = show; }
    public void setShowTeamColor(boolean show) { this.showTeamColor = show; }
    public void setSortMode(String mode) { this.sortMode = mode; }
    public void setBgOpacity(int opacity) { this.bgOpacity = Math.max(0, Math.min(255, opacity)); }
    
    public boolean isEnabled() { return enabled; }
    public int getPosX() { return posX; }
    public int getPosY() { return posY; }
    public float getScale() { return scale; }
    public int getMaxPlayers() { return maxPlayers; }
    public boolean getShowHeads() { return showHeads; }
    public boolean getShowFkdr() { return showFkdr; }
    public boolean getShowWlr() { return showWlr; }
    public boolean getShowStreak() { return showStreak; }
    public boolean getShowThreat() { return showThreat; }
    public boolean getShowTeamColor() { return showTeamColor; }
    public String getSortMode() { return sortMode; }
    public int getBgOpacity() { return bgOpacity; }
    
    // ── Data Management ────────────────────────────────────────────────────────
    
    public void setPlayers(List<IntelPlayer> players) {
        this.players = new ArrayList<>(players);
        sortPlayers();
    }
    
    public void cacheSkin(String name, ResourceLocation skin, boolean isSheet) {
        skinCache.put(name, skin);
        skinIsSheet.put(name, isSheet);
    }
    
    private void sortPlayers() {
        switch (sortMode) {
            case "threat":
                players.sort((a, b) -> Double.compare(b.threatScore, a.threatScore));
                break;
            case "fkdr":
                players.sort((a, b) -> Double.compare(b.fkdr, a.fkdr));
                break;
            case "name":
                players.sort(Comparator.comparing(p -> p.name));
                break;
        }
    }
    
    // ── Rendering ──────────────────────────────────────────────────────────────
    
    public void render() {
        if (!enabled || players.isEmpty()) return;
        
        ScaledResolution sr = new ScaledResolution(mc);
        
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0f);
        
        int scaledX = (int)(posX / scale);
        int scaledY = (int)(posY / scale);
        
        // Calculate dimensions
        int displayCount = Math.min(players.size(), maxPlayers);
        int width = calculateWidth();
        int contentHeight = (LINE_HEIGHT * displayCount) + (PADDING * 2);
        int totalHeight = HEADER_HEIGHT + contentHeight;
        
        // Draw background with configurable opacity
        int bgColor = (bgOpacity << 24) | 0x07070E;
        drawRoundedRect(scaledX, scaledY, scaledX + width, scaledY + totalHeight, BORDER_RADIUS, bgColor);
        
        // Draw header background (slightly darker)
        int headerBg = ((Math.min(255, bgOpacity + 40)) << 24) | 0x0A0A14;
        fillRect(scaledX, scaledY, width, HEADER_HEIGHT, headerBg);
        
        // Draw headers
        int headerY = scaledY + 3;
        int x = scaledX + PADDING;
        
        if (showHeads) x += HEAD_SIZE + 4;
        if (showTeamColor) x += 3;
        
        // Name header
        drawText("NAME", x, headerY, TEXT_DIM);
        x += 80;
        
        if (showFkdr) {
            drawText("FKDR", x, headerY, TEXT_DIM);
            x += 40;
        }
        if (showWlr) {
            drawText("WLR", x, headerY, TEXT_DIM);
            x += 35;
        }
        if (showStreak) {
            drawText("WS", x, headerY, TEXT_DIM);
            x += 30;
        }
        if (showThreat) {
            drawText("THREAT", x, headerY, TEXT_DIM);
        }
        
        // Draw players
        int y = scaledY + HEADER_HEIGHT + PADDING;
        for (int i = 0; i < displayCount; i++) {
            IntelPlayer p = players.get(i);
            drawPlayerLine(p, scaledX + PADDING, y, width - (PADDING * 2));
            y += LINE_HEIGHT;
        }
        
        GlStateManager.popMatrix();
    }
    
    private int calculateWidth() {
        int width = PADDING * 2; // Base padding
        
        if (showHeads) width += HEAD_SIZE + 4;
        if (showTeamColor) width += 3; // Team indicator
        width += 80; // Name column (minimum)
        if (showFkdr) width += 40;
        if (showWlr) width += 35;
        if (showStreak) width += 30;
        if (showThreat) width += 45;
        
        return width;
    }
    
    private void drawPlayerLine(IntelPlayer p, int x, int y, int width) {
        int currentX = x;
        
        // Draw team color indicator
        if (showTeamColor && p.team != null && !p.team.isEmpty()) {
            int teamColor = getTeamColor(p.team);
            fillRect(currentX, y + 2, 2, HEAD_SIZE, teamColor);
            currentX += 3;
        }
        
        // Draw player head
        if (showHeads) {
            drawPlayerHead(p.name, currentX, y + 2, HEAD_SIZE);
            currentX += HEAD_SIZE + 4;
        }
        
        // Draw full name (no truncation)
        int nameColor = p.cheater ? 0xFFFF4444 : TEXT_BRIGHT;
        if (p.threatScore >= 75) nameColor = ACCENT; // Pink for high threat
        
        drawText(p.name, currentX, y + 4, nameColor);
        currentX += 80;
        
        // Draw FKDR (centered)
        if (showFkdr) {
            String fkdrText = p.loading ? "-" : p.fkdr < 0 ? "-" : String.format("%.1f", p.fkdr);
            int fkdrColor = p.loading ? TEXT_DIM : getStatColor(p.fkdr, 3.0, 6.0);
            int fkdrWidth = mc.fontRendererObj.getStringWidth(fkdrText);
            drawText(fkdrText, currentX + (40 - fkdrWidth) / 2, y + 4, fkdrColor);
            currentX += 40;
        }
        
        // Draw WLR (centered)
        if (showWlr) {
            String wlrText = p.loading ? "-" : p.wlr < 0 ? "-" : String.format("%.1f", p.wlr);
            int wlrColor = p.loading ? TEXT_DIM : getStatColor(p.wlr, 2.0, 4.0);
            int wlrWidth = mc.fontRendererObj.getStringWidth(wlrText);
            drawText(wlrText, currentX + (35 - wlrWidth) / 2, y + 4, wlrColor);
            currentX += 35;
        }
        
        // Draw Winstreak (centered)
        if (showStreak) {
            String wsText = p.loading ? "-" : p.winstreak < 0 ? "-" : String.valueOf(p.winstreak);
            int wsColor = p.loading ? TEXT_DIM : p.winstreak >= 10 ? 0xFFFFCC44 : p.winstreak >= 5 ? 0xFF44DD66 : TEXT_DIM;
            int wsWidth = mc.fontRendererObj.getStringWidth(wsText);
            drawText(wsText, currentX + (30 - wsWidth) / 2, y + 4, wsColor);
            currentX += 30;
        }
        
        // Draw threat score (centered, no bar)
        if (showThreat) {
            int threat = (int) p.threatScore;
            String threatText = p.loading ? "-" : String.valueOf(threat);
            int threatColor = getThreatColor(threat);
            int threatWidth = mc.fontRendererObj.getStringWidth(threatText);
            drawText(threatText, currentX + (45 - threatWidth) / 2, y + 4, threatColor);
        }
    }
    
    private void drawPlayerHead(String name, int x, int y, int size) {
        try {
            ResourceLocation skin = skinCache.get(name);
            
            // Fallback to network lookup
            if (skin == null && mc.getNetHandler() != null) {
                NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(name);
                if (info != null) {
                    skin = info.getLocationSkin();
                    if (skin != null) {
                        skinCache.put(name, skin);
                        skinIsSheet.put(name, true);
                    }
                }
            }
            
            // Fallback to Steve
            if (skin == null) {
                skin = new ResourceLocation("textures/entity/steve.png");
                skinIsSheet.put(name, true);
            }
            
            // Proper GL state for rendering
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.enableAlpha();
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.getTextureManager().bindTexture(skin);
            
            boolean isSheet = skinIsSheet.getOrDefault(name, true);
            if (isSheet) {
                // Full 64x64 skin - draw face (8,8) and hat (40,8)
                Gui.drawScaledCustomSizeModalRect(x, y, 8f, 8f, 8, 8, size, size, 64f, 64f);
                Gui.drawScaledCustomSizeModalRect(x, y, 40f, 8f, 8, 8, size, size, 64f, 64f);
            } else {
                // Pre-cropped 16x16 face
                Gui.drawScaledCustomSizeModalRect(x, y, 0f, 0f, 16, 16, size, size, 16f, 16f);
            }
            
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
        } catch (Exception ignored) {}
    }
    
    private int getTeamColor(String team) {
        switch (team.toLowerCase()) {
            case "red":    return 0xFFFF4444;
            case "blue":   return 0xFF4488FF;
            case "green":  return 0xFF44FF66;
            case "yellow": return 0xFFFFFF44;
            case "aqua":   return 0xFF44FFFF;
            case "white":  return 0xFFEEEEEE;
            case "pink":   return 0xFFFF88CC;
            case "gray":   return 0xFF888888;
            default:       return 0xFF888888;
        }
    }
    
    private int getThreatColor(int score) {
        if (score >= 75) return 0xFFFF2244;
        if (score >= 50) return 0xFFFF7722;
        if (score >= 25) return 0xFFFFCC22;
        return 0xFF44DD66;
    }
    
    private int getStatColor(double value, double mid, double high) {
        if (value < 0) return TEXT_DIM;
        if (value >= high) return 0xFFFF3344;
        if (value >= mid) return 0xFFFF9933;
        if (value >= mid / 2) return 0xFFFFEE44;
        return 0xFF44CC66;
    }
    
    // ── Utility Methods ────────────────────────────────────────────────────────
    
    private void drawText(String text, int x, int y, int color) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        mc.fontRendererObj.drawString(text, x, y, color, true);
    }
    
    private void fillRect(int x, int y, int w, int h, int color) {
        Gui.drawRect(x, y, x + w, y + h, color);
    }
    
    private void drawRoundedRect(int x, int y, int x2, int y2, int radius, int color) {
        // Use RoundedUtils for proper rounded corners
        int width = x2 - x;
        int height = y2 - y;
        RoundedUtils.drawRoundedRect(x, y, width, height, radius, color);
    }
}
