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
    private boolean showStar = true;
    private boolean showFkdr = true;
    private boolean showWlr = false;
    private boolean showStreak = false;
    private boolean showThreat = true;
    private boolean showUrchin = true;
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
    public void setShowStar(boolean show) { this.showStar = show; }
    public void setShowFkdr(boolean show) { this.showFkdr = show; }
    public void setShowWlr(boolean show) { this.showWlr = show; }
    public void setShowStreak(boolean show) { this.showStreak = show; }
    public void setShowThreat(boolean show) { this.showThreat = show; }
    public void setShowUrchin(boolean show) { this.showUrchin = show; }
    public void setShowTeamColor(boolean show) { this.showTeamColor = show; }
    public void setSortMode(String mode) { this.sortMode = mode; }
    public void setBgOpacity(int opacity) { this.bgOpacity = Math.max(0, Math.min(255, opacity)); }
    
    public boolean isEnabled() { return enabled; }
    public int getPosX() { return posX; }
    public int getPosY() { return posY; }
    public float getScale() { return scale; }
    public int getMaxPlayers() { return maxPlayers; }
    public boolean getShowHeads() { return showHeads; }
    public boolean getShowStar() { return showStar; }
    public boolean getShowFkdr() { return showFkdr; }
    public boolean getShowWlr() { return showWlr; }
    public boolean getShowStreak() { return showStreak; }
    public boolean getShowThreat() { return showThreat; }
    public boolean getShowUrchin() { return showUrchin; }
    public boolean getShowTeamColor() { return showTeamColor; }
    public String getSortMode() { return sortMode; }
    public int getBgOpacity() { return bgOpacity; }
    public int getBorderOpacity() { return bgOpacity; } // Border uses same as bg for now
    
    public void setBorderOpacity(int opacity) { this.bgOpacity = opacity; } // Border uses same as bg
    
    public void handleClick(int mx, int my) {
        // Mouse click handling - currently unused but required by LobbyIntel
    }
    
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
    
    /**
     * Get players to display, filtering out self and teammates if enabled
     */
    private List<IntelPlayer> getDisplayPlayers() {
        // Get hide teammates setting
        myau.module.modules.LobbyIntel lobbyIntel = 
            (myau.module.modules.LobbyIntel) myau.Myau.moduleManager.getModule(myau.module.modules.LobbyIntel.class);
        boolean hideTeammates = lobbyIntel != null && lobbyIntel.hideTeammates.getValue();
        
        // If not hiding teammates, show everyone
        if (!hideTeammates) {
            return new ArrayList<>(players);
        }
        
        // Get my team for filtering
        String myTeam = null;
        String myName = mc.thePlayer != null ? mc.thePlayer.getName() : null;
        
        if (myName != null) {
            for (IntelPlayer p : players) {
                if (p.name.equals(myName)) {
                    myTeam = p.team;
                    break;
                }
            }
        }
        
        // Filter players - skip self and teammates
        List<IntelPlayer> filtered = new ArrayList<>();
        for (IntelPlayer p : players) {
            // Skip self
            if (myName != null && p.name.equals(myName)) continue;
            
            // Skip teammates (same team as me)
            if (myTeam != null && p.team != null && p.team.equals(myTeam)) {
                continue;
            }
            
            filtered.add(p);
        }
        
        return filtered;
    }
    
    // ── Rendering ──────────────────────────────────────────────────────────────
    
    public void render() {
        if (!enabled || players.isEmpty()) return;
        
        // Filter out teammates if hideTeammates is enabled
        List<IntelPlayer> displayPlayers = getDisplayPlayers();
        if (displayPlayers.isEmpty()) return;
        
        ScaledResolution sr = new ScaledResolution(mc);
        
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0f);
        
        int scaledX = (int)(posX / scale);
        int scaledY = (int)(posY / scale);
        
        // Calculate dimensions
        int displayCount = Math.min(displayPlayers.size(), maxPlayers);
        int width = calculateWidth();
        int contentHeight = (LINE_HEIGHT * displayCount) + (PADDING * 2);
        int totalHeight = HEADER_HEIGHT + contentHeight;
        
        // Draw single unified background with rounded corners
        int bgColor = (bgOpacity << 24) | 0x07070E;
        drawRoundedRect(scaledX, scaledY, scaledX + width, scaledY + totalHeight, BORDER_RADIUS, bgColor);
        
        // Draw header divider line instead of separate background
        int dividerY = scaledY + HEADER_HEIGHT;
        fillRect(scaledX + 2, dividerY, width - 4, 1, 0x33FFFFFF);
        
        // Draw headers
        int headerY = scaledY + 3;
        int x = scaledX + PADDING;
        
        if (showHeads) x += HEAD_SIZE + 4;
        if (showTeamColor) x += 3;
        
        // Name header
        drawText("NAME", x, headerY, TEXT_DIM);
        x += 120; // Increased from 80 to 120 for longer names
        
        // Star header - centered
        if (showStar) {
            int starHeaderW = mc.fontRendererObj.getStringWidth("✫");
            drawText("✫", x + (35 - starHeaderW) / 2, headerY, TEXT_DIM);
            x += 35;
        }
        
        // FKDR header - centered
        if (showFkdr) {
            int fkdrHeaderW = mc.fontRendererObj.getStringWidth("FKDR");
            drawText("FKDR", x + (40 - fkdrHeaderW) / 2, headerY, TEXT_DIM);
            x += 40;
        }
        
        // WLR header - centered
        if (showWlr) {
            int wlrHeaderW = mc.fontRendererObj.getStringWidth("WLR");
            drawText("WLR", x + (35 - wlrHeaderW) / 2, headerY, TEXT_DIM);
            x += 35;
        }
        
        // WS header - centered
        if (showStreak) {
            int wsHeaderW = mc.fontRendererObj.getStringWidth("WS");
            drawText("WS", x + (30 - wsHeaderW) / 2, headerY, TEXT_DIM);
            x += 30;
        }
        
        // U header - centered
        if (showUrchin) {
            int uHeaderW = mc.fontRendererObj.getStringWidth("U");
            drawText("U", x + (25 - uHeaderW) / 2, headerY, TEXT_DIM);
            x += 25;
        }
        
        // THREAT header - centered
        if (showThreat) {
            int threatHeaderW = mc.fontRendererObj.getStringWidth("THREAT");
            drawText("THREAT", x + (45 - threatHeaderW) / 2, headerY, TEXT_DIM);
        }
        
        // Draw players
        int y = scaledY + HEADER_HEIGHT + PADDING;
        for (int i = 0; i < displayCount; i++) {
            IntelPlayer p = displayPlayers.get(i);
            drawPlayerLine(p, scaledX + PADDING, y, width - (PADDING * 2));
            y += LINE_HEIGHT;
        }
        
        GlStateManager.popMatrix();
    }
    
    private int calculateWidth() {
        int width = PADDING * 2; // Base padding
        
        if (showHeads) width += HEAD_SIZE + 4;
        if (showTeamColor) width += 3; // Team indicator
        width += 120; // Name column (increased from 80 to 120)
        if (showStar) width += 35; // Star column
        if (showFkdr) width += 40;
        if (showWlr) width += 35;
        if (showStreak) width += 30;
        if (showUrchin) width += 25; // Urchin icon column
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
        
        // Draw role badge if player has a role
        if (p.role != null) {
            int badgeX = currentX + mc.fontRendererObj.getStringWidth(p.name) + 3;
            int badgeY = y + 3;
            
            // Draw small rounded badge
            String roleText = p.role.getIcon();
            int badgeW = mc.fontRendererObj.getStringWidth(roleText) + 4;
            fillRect(badgeX, badgeY, badgeW, 10, p.role.getColor() & 0x66FFFFFF);
            drawText(roleText, badgeX + 2, badgeY + 1, p.role.getColor());
        }
        
        currentX += 120; // Increased from 80 to 120 for longer names
        
        // Draw Star (centered with ✫ symbol)
        if (showStar) {
            if (p.loading) {
                int dashWidth = mc.fontRendererObj.getStringWidth("-");
                drawText("-", currentX + (35 - dashWidth) / 2, y + 4, TEXT_DIM);
            } else {
                String starText = "✫" + p.star;
                int starColor = getPrestigeColor(p.star);
                int starWidth = mc.fontRendererObj.getStringWidth(starText);
                drawText(starText, currentX + (35 - starWidth) / 2, y + 4, starColor);
            }
            currentX += 35;
        }
        
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
        
        // Draw Urchin/Ghost Intel icons (centered) - shows both if both exist
        if (showUrchin) {
            String displayText = "";
            int displayColor = TEXT_DIM;
            
            // Check Urchin first
            if (p.cheater && p.urchinType != null) {
                if (p.urchinType.contains("closet")) {
                    displayText = "C";   // C for closet
                    displayColor = 0xFFFF8844; // orange
                } else if (p.urchinType.contains("confirmed")) {
                    displayText = "CC";  // CC for confirmed
                    displayColor = 0xFFDD44DD; // purple
                } else if (p.urchinType.contains("blatant")) {
                    displayText = "BC";  // BC for blatant
                    displayColor = 0xFFFF3344; // red
                } else if (p.urchinType.contains("sniper")) {
                    displayText = "S";   // S for sniper
                    displayColor = 0xFFFF1122; // bright red
                } else {
                    displayText = "C";   // default
                    displayColor = 0xFFFFAA44;
                }
            }
            
            // Check Ghost Intel - add if exists
            if (p.ghostTagged && p.ghostType != null) {
                String ghostIcon = "A"; // default Account
                int ghostColor = 0xFFFF69B4; // pink
                
                // Map Ghost Intel types to icons and colors (from your Discord bot)
                String type = p.ghostType.toLowerCase();
                if (type.contains("account")) {
                    ghostIcon = "A";   // Account
                    ghostColor = 0xFFFF69B4; // pink
                } else if (type.contains("caution")) {
                    ghostIcon = "C";   // Caution
                    ghostColor = 0xFFFFAA00; // yellow/orange
                } else if (type.contains("closet")) {
                    ghostIcon = "CC";  // Closet Cheater
                    ghostColor = 0xFFFF8800; // orange
                } else if (type.contains("blatant")) {
                    ghostIcon = "BC";  // Blatant Cheater
                    ghostColor = 0xFFCCAA00; // dark yellow
                } else if (type.contains("sniper")) {
                    ghostIcon = "S";   // Sniper
                    ghostColor = 0xFFFF0000; // red
                } else if (type.contains("verified")) {
                    ghostIcon = "VC";  // Verified Cheater
                    ghostColor = 0xFFFF00AA; // magenta
                } else {
                    ghostIcon = "G";   // Generic Ghost tag
                    ghostColor = 0xFF00FFFF; // cyan
                }
                
                // If both Urchin and Ghost exist, show both
                if (!displayText.isEmpty()) {
                    displayText = displayText + "/" + ghostIcon;
                    // Use Ghost color since it's from your bot
                    displayColor = ghostColor;
                } else {
                    displayText = ghostIcon;
                    displayColor = ghostColor;
                }
            }
            
            // Draw the icon(s) if any
            if (!displayText.isEmpty()) {
                int iconWidth = mc.fontRendererObj.getStringWidth(displayText);
                drawText(displayText, currentX + (25 - iconWidth) / 2, y + 4, displayColor);
            }
            
            currentX += 25;
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
    
    /**
     * Get color based on Hypixel BedWars prestige brackets
     * Based on official Hypixel color scheme
     */
    private int getPrestigeColor(int star) {
        // 5000+ Prestiges
        if (star >= 5000) return 0xFF00FFFF; // Aqua+ (5000+)
        if (star >= 4900) return 0xFFFF0000; // Red+ (4900-4999)
        if (star >= 4800) return 0xFFFF00FF; // Light Purple+ (4800-4899)
        if (star >= 4700) return 0xFF00AA00; // Dark Green+ (4700-4799)
        if (star >= 4600) return 0xFF00AAAA; // Dark Aqua+ (4600-4699)
        if (star >= 4500) return 0xFF0000AA; // Dark Blue+ (4500-4599)
        if (star >= 4400) return 0xFFAA0000; // Dark Red+ (4400-4499)
        if (star >= 4300) return 0xFF555555; // Dark Gray+ (4300-4399)
        if (star >= 4200) return 0xFFAAAAAA; // Gray+ (4200-4299)
        if (star >= 4100) return 0xFFFFFFFF; // White+ (4100-4199)
        if (star >= 4000) return 0xFF55FF55; // Green+ (4000-4099)
        
        // 3000+ Prestiges
        if (star >= 3900) return 0xFF00FFFF; // Aqua✦ (3900-3999)
        if (star >= 3800) return 0xFFFF0000; // Red✦ (3800-3899)
        if (star >= 3700) return 0xFFFF00FF; // Light Purple✦ (3700-3799)
        if (star >= 3600) return 0xFF00AA00; // Dark Green✦ (3600-3699)
        if (star >= 3500) return 0xFF00AAAA; // Dark Aqua✦ (3500-3599)
        if (star >= 3400) return 0xFF0000AA; // Dark Blue✦ (3400-3499)
        if (star >= 3300) return 0xFFAA0000; // Dark Red✦ (3300-3399)
        if (star >= 3200) return 0xFF555555; // Dark Gray✦ (3200-3299)
        if (star >= 3100) return 0xFFAAAAAA; // Gray✦ (3100-3199)
        if (star >= 3000) return 0xFFFFFFFF; // White✦ (3000-3099)
        
        // 2000+ Prestiges
        if (star >= 2900) return 0xFF00FFFF; // Aqua☯ (2900-2999)
        if (star >= 2800) return 0xFFFF0000; // Red☯ (2800-2899)
        if (star >= 2700) return 0xFFFF00FF; // Light Purple☯ (2700-2799)
        if (star >= 2600) return 0xFF00AA00; // Dark Green☯ (2600-2699)
        if (star >= 2500) return 0xFF00AAAA; // Dark Aqua☯ (2500-2599)
        if (star >= 2400) return 0xFF0000AA; // Dark Blue☯ (2400-2499)
        if (star >= 2300) return 0xFFAA0000; // Dark Red☯ (2300-2399)
        if (star >= 2200) return 0xFF555555; // Dark Gray☯ (2200-2299)
        if (star >= 2100) return 0xFFAAAAAA; // Gray☯ (2100-2199)
        if (star >= 2000) return 0xFFFFFFFF; // White☯ (2000-2099)
        
        // 1000+ Prestiges
        if (star >= 1900) return 0xFF00FFFF; // Aqua⚝ (1900-1999)
        if (star >= 1800) return 0xFFFF0000; // Red⚝ (1800-1899)
        if (star >= 1700) return 0xFFFF00FF; // Light Purple⚝ (1700-1799)
        if (star >= 1600) return 0xFF00AA00; // Dark Green⚝ (1600-1699)
        if (star >= 1500) return 0xFF00AAAA; // Dark Aqua⚝ (1500-1599)
        if (star >= 1400) return 0xFF0000AA; // Dark Blue⚝ (1400-1499)
        if (star >= 1300) return 0xFFAA0000; // Dark Red⚝ (1300-1399)
        if (star >= 1200) return 0xFF555555; // Dark Gray⚝ (1200-1299)
        if (star >= 1100) return 0xFFAAAAAA; // Gray⚝ (1100-1199)
        if (star >= 1000) return 0xFFFFFFFF; // White⚝ (1000-1099)
        
        // Below 1000
        if (star >= 900) return 0xFFFF00FF;  // Light Purple☆ (900-999)
        if (star >= 800) return 0xFF5555FF;  // Blue☆ (800-899)
        if (star >= 700) return 0xFF55FFFF;  // Aqua☆ (700-799)
        if (star >= 600) return 0xFF55FF55;  // Green☆ (600-699)
        if (star >= 500) return 0xFFFF5555;  // Red☆ (500-599)
        if (star >= 400) return 0xFF0000AA;  // Dark Blue★ (400-499)
        if (star >= 300) return 0xFF00AAAA;  // Dark Aqua★ (300-399)
        if (star >= 200) return 0xFFFFAA00;  // Gold★ (200-299)
        if (star >= 100) return 0xFFFFFFFF;  // White★ (100-199)
        
        return 0xFFAAAAAA; // Gray (0-99)
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
