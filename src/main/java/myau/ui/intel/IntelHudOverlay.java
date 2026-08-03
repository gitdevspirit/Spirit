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

public class IntelHudOverlay {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int LINE_HEIGHT = 18;
    private static final int HEAD_SIZE = 14;
    private static final int PADDING = 6;
    private static final int HEADER_HEIGHT = 12;
    private static final int BORDER_RADIUS = 4;

    private int bgOpacity = 200;
    private static final int ACCENT = GuiColors.ACCENT;
    private static final int TEXT_BRIGHT = 0xFFDDDDEE;
    private static final int TEXT_DIM = 0xFF888899;

    private boolean enabled = true;
    private int posX = 10;
    private int posY = 100;
    private float scale = 1.0f;
    private int maxPlayers = 10;

    private boolean showHeads = true;
    private boolean showStar = true;
    private boolean showLevel = false;
    private boolean showFkdr = true;
    private boolean showWlr = false;
    private boolean showStreak = false;
    private boolean showThreat = true;
    private boolean showUrchin = true;
    private boolean showTeamColor = true;
    private String sortMode = "threat";

    private List<IntelPlayer> players = new ArrayList<>();

    private final java.util.Map<String, ResourceLocation> skinCache =
            new java.util.HashMap<>();

    private final java.util.Map<String, Boolean> skinIsSheet =
            new java.util.HashMap<>();

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPosition(int x, int y) {
        this.posX = x;
        this.posY = y;
    }

    public void setScale(float scale) {
        this.scale = Math.max(0.5f, Math.min(2.0f, scale));
    }

    public void setMaxPlayers(int max) {
        this.maxPlayers = Math.max(1, Math.min(20, max));
    }

    public void setShowHeads(boolean show) {
        this.showHeads = show;
    }

    public void setShowStar(boolean show) {
        this.showStar = show;
    }

    public void setShowLevel(boolean show) {
        this.showLevel = show;
    }

    public void setShowFkdr(boolean show) {
        this.showFkdr = show;
    }

    public void setShowWlr(boolean show) {
        this.showWlr = show;
    }

    public void setShowStreak(boolean show) {
        this.showStreak = show;
    }

    public void setShowThreat(boolean show) {
        this.showThreat = show;
    }

    public void setShowUrchin(boolean show) {
        this.showUrchin = show;
    }

    public void setShowTeamColor(boolean show) {
        this.showTeamColor = show;
    }

    public void setSortMode(String mode) {
        this.sortMode = mode;
    }

    public void setBgOpacity(int opacity) {
        this.bgOpacity = Math.max(0, Math.min(255, opacity));
    }

    public void setBorderOpacity(int opacity) {
        this.bgOpacity = opacity;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPosX() {
        return posX;
    }

    public int getPosY() {
        return posY;
    }

    public float getScale() {
        return scale;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public boolean getShowHeads() {
        return showHeads;
    }

    public boolean getShowStar() {
        return showStar;
    }

    public boolean getShowLevel() {
        return showLevel;
    }

    public boolean getShowFkdr() {
        return showFkdr;
    }

    public boolean getShowWlr() {
        return showWlr;
    }

    public boolean getShowStreak() {
        return showStreak;
    }

    public boolean getShowThreat() {
        return showThreat;
    }

    public boolean getShowUrchin() {
        return showUrchin;
    }

    public boolean getShowTeamColor() {
        return showTeamColor;
    }

    public String getSortMode() {
        return sortMode;
    }

    public int getBgOpacity() {
        return bgOpacity;
    }

    public int getBorderOpacity() {
        return bgOpacity;
    }

    public void handleClick(int mx, int my) {
    }

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

    private List<IntelPlayer> getDisplayPlayers() {
        myau.module.modules.LobbyIntel lobbyIntel =
                (myau.module.modules.LobbyIntel) myau.Myau.moduleManager.getModule(
                        myau.module.modules.LobbyIntel.class
                );

        boolean hideTeammates =
                lobbyIntel != null && lobbyIntel.hideTeammates.getValue();

        if (!hideTeammates) {
            return new ArrayList<>(players);
        }

        String myTeam = null;
        String myName = mc.thePlayer != null ? mc.thePlayer.getName() : null;

        if (myName != null) {
            for (IntelPlayer player : players) {
                if (player.name.equals(myName)) {
                    myTeam = player.team;
                    break;
                }
            }
        }

        List<IntelPlayer> filtered = new ArrayList<>();

        for (IntelPlayer player : players) {
            if (myName != null && player.name.equals(myName)) continue;

            if (myTeam != null
                    && player.team != null
                    && player.team.equals(myTeam)) {
                continue;
            }

            filtered.add(player);
        }

        return filtered;
    }

    public void render() {
        if (!enabled || players.isEmpty()) return;

        List<IntelPlayer> displayPlayers = getDisplayPlayers();

        if (displayPlayers.isEmpty()) return;

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0f);

        int scaledX = (int) (posX / scale);
        int scaledY = (int) (posY / scale);

        int displayCount = Math.min(displayPlayers.size(), maxPlayers);
        int width = calculateWidth();
        int contentHeight = (LINE_HEIGHT * displayCount) + (PADDING * 2);
        int totalHeight = HEADER_HEIGHT + contentHeight;

        int bgColor = (bgOpacity << 24) | 0x07070E;
        drawRoundedRect(
                scaledX,
                scaledY,
                scaledX + width,
                scaledY + totalHeight,
                BORDER_RADIUS,
                bgColor
        );

        int dividerY = scaledY + HEADER_HEIGHT;
        fillRect(scaledX + 2, dividerY, width - 4, 1, 0x33FFFFFF);

        int headerY = scaledY + 3;
        int x = scaledX + PADDING;

        if (showHeads) x += HEAD_SIZE + 4;
        if (showTeamColor) x += 3;

        drawText("NAME", x, headerY, 0xFFFFFFFF);
        x += 120;

        if (showStar) {
            int headerWidth = mc.fontRendererObj.getStringWidth("✫");
            drawText("✫", x + (35 - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += 35;
        }

        if (showLevel) {
            int headerWidth = mc.fontRendererObj.getStringWidth("LVL");
            drawText("LVL", x + (35 - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += 35;
        }

        if (showFkdr) {
            int headerWidth = mc.fontRendererObj.getStringWidth("FKDR");
            drawText("FKDR", x + (40 - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += 40;
        }

        if (showWlr) {
            int headerWidth = mc.fontRendererObj.getStringWidth("WLR");
            drawText("WLR", x + (35 - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += 35;
        }

        if (showStreak) {
            int headerWidth = mc.fontRendererObj.getStringWidth("WS");
            drawText("WS", x + (30 - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += 30;
        }

        if (showUrchin) {
            int headerWidth = mc.fontRendererObj.getStringWidth("TAGS");
            drawText("TAGS", x + (35 - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += 35;
        }

        if (showThreat) {
            int headerWidth = mc.fontRendererObj.getStringWidth("THREAT");
            drawText("THREAT", x + (45 - headerWidth) / 2, headerY, 0xFFFFFFFF);
        }

        int y = scaledY + HEADER_HEIGHT + PADDING;

        for (int i = 0; i < displayCount; i++) {
            drawPlayerLine(
                    displayPlayers.get(i),
                    scaledX + PADDING,
                    y
            );

            y += LINE_HEIGHT;
        }

        GlStateManager.popMatrix();
    }

    private int calculateWidth() {
        int width = PADDING * 2;

        if (showHeads) width += HEAD_SIZE + 4;
        if (showTeamColor) width += 3;

        width += 120;

        if (showStar) width += 35;
        if (showLevel) width += 35;
        if (showFkdr) width += 40;
        if (showWlr) width += 35;
        if (showStreak) width += 30;
        if (showUrchin) width += 35;
        if (showThreat) width += 45;

        return width;
    }

    private void drawPlayerLine(IntelPlayer player, int x, int y) {
        int currentX = x;

        if (showTeamColor && player.team != null && !player.team.isEmpty()) {
            fillRect(currentX, y + 2, 2, HEAD_SIZE, getTeamColor(player.team));
            currentX += 3;
        }

        if (showHeads) {
            drawPlayerHead(player.name, currentX, y + 2, HEAD_SIZE);
            currentX += HEAD_SIZE + 4;
        }

        int nameColor = player.cheater ? 0xFFFF4444 : TEXT_BRIGHT;

        if (player.threatScore >= 75) {
            nameColor = ACCENT;
        }

        drawText(player.name, currentX, y + 4, nameColor);
        currentX += 120;

        if (showStar) {
            String text = player.loading ? "-" : "✫" + player.star;
            int color = player.loading ? TEXT_DIM : getPrestigeColor(player.star);
            int textWidth = mc.fontRendererObj.getStringWidth(text);

            drawText(text, currentX + (35 - textWidth) / 2, y + 4, color);
            currentX += 35;
        }

        if (showLevel) {
            String text = player.loading ? "-" : String.valueOf(player.level);
            int textWidth = mc.fontRendererObj.getStringWidth(text);

            drawText(
                    text,
                    currentX + (35 - textWidth) / 2,
                    y + 4,
                    player.loading ? TEXT_DIM : 0xFFFFFFFF
            );

            currentX += 35;
        }

        if (showFkdr) {
            String text = player.loading || player.fkdr < 0
                    ? "-"
                    : String.format("%.1f", player.fkdr);

            int color = player.loading
                    ? TEXT_DIM
                    : getStatColor(player.fkdr, 3.0, 6.0);

            int textWidth = mc.fontRendererObj.getStringWidth(text);
            drawText(text, currentX + (40 - textWidth) / 2, y + 4, color);
            currentX += 40;
        }

        if (showWlr) {
            String text = player.loading || player.wlr < 0
                    ? "-"
                    : String.format("%.1f", player.wlr);

            int color = player.loading
                    ? TEXT_DIM
                    : getStatColor(player.wlr, 2.0, 4.0);

            int textWidth = mc.fontRendererObj.getStringWidth(text);
            drawText(text, currentX + (35 - textWidth) / 2, y + 4, color);
            currentX += 35;
        }

        if (showStreak) {
            String text = player.loading || player.winstreak < 0
                    ? "-"
                    : String.valueOf(player.winstreak);

            int color = player.loading
                    ? TEXT_DIM
                    : player.winstreak >= 10
                            ? 0xFFFFCC44
                            : player.winstreak >= 5
                                    ? 0xFF44DD66
                                    : TEXT_DIM;

            int textWidth = mc.fontRendererObj.getStringWidth(text);
            drawText(text, currentX + (30 - textWidth) / 2, y + 4, color);
            currentX += 30;
        }

        if (showUrchin) {
            String displayText = "";
            int displayColor = TEXT_DIM;

            if (player.cheater && player.urchinType != null) {
                String type = player.urchinType.toLowerCase();

                if (type.contains("closet")) {
                    displayText = "C";
                    displayColor = 0xFFFF8844;
                } else if (type.contains("confirmed")) {
                    displayText = "CC";
                    displayColor = 0xFFDD44DD;
                } else if (type.contains("blatant")) {
                    displayText = "BC";
                    displayColor = 0xFFFF3344;
                } else if (type.contains("sniper")) {
                    displayText = "S";
                    displayColor = 0xFFFF1122;
                } else {
                    displayText = "C";
                    displayColor = 0xFFFFAA44;
                }
            }

            if (player.ghostTagged && player.ghostType != null) {
                String ghostIcon = "A";
                int ghostColor = 0xFFFF69B4;
                String type = player.ghostType.toLowerCase();

                if (type.contains("account")) {
                    ghostIcon = "A";
                    ghostColor = 0xFFFF69B4;
                } else if (type.contains("caution")) {
                    ghostIcon = "C";
                    ghostColor = 0xFFFFAA00;
                } else if (type.contains("closet")) {
                    ghostIcon = "CC";
                    ghostColor = 0xFFFF8800;
                } else if (type.contains("blatant")) {
                    ghostIcon = "BC";
                    ghostColor = 0xFFCCAA00;
                } else if (type.contains("sniper")) {
                    ghostIcon = "S";
                    ghostColor = 0xFFFF0000;
                } else if (type.contains("verified")) {
                    ghostIcon = "VC";
                    ghostColor = 0xFFFF00AA;
                } else {
                    ghostIcon = "G";
                    ghostColor = 0xFF00FFFF;
                }

                displayText = displayText.isEmpty()
                        ? ghostIcon
                        : displayText + "/" + ghostIcon;

                displayColor = ghostColor;
            }

            if (!displayText.isEmpty()) {
                int textWidth = mc.fontRendererObj.getStringWidth(displayText);
                drawText(
                        displayText,
                        currentX + (35 - textWidth) / 2,
                        y + 4,
                        displayColor
                );
            }

            currentX += 35;
        }

        if (showThreat) {
            String text = player.loading
                    ? "-"
                    : String.valueOf((int) player.threatScore);

            int textWidth = mc.fontRendererObj.getStringWidth(text);

            drawText(
                    text,
                    currentX + (45 - textWidth) / 2,
                    y + 4,
                    player.loading ? TEXT_DIM : getThreatColor((int) player.threatScore)
            );
        }
    }

    private void drawPlayerHead(String name, int x, int y, int size) {
        try {
            ResourceLocation skin = skinCache.get(name);

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

            if (skin == null) {
                skin = new ResourceLocation("textures/entity/steve.png");
                skinIsSheet.put(name, true);
            }

            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE,
                    GL11.GL_ZERO
            );
            GlStateManager.enableAlpha();
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.getTextureManager().bindTexture(skin);

            boolean isSheet = skinIsSheet.getOrDefault(name, true);

            if (isSheet) {
                Gui.drawScaledCustomSizeModalRect(
                        x, y, 8f, 8f, 8, 8, size, size, 64f, 64f
                );

                Gui.drawScaledCustomSizeModalRect(
                        x, y, 40f, 8f, 8, 8, size, size, 64f, 64f
                );
            } else {
                Gui.drawScaledCustomSizeModalRect(
                        x, y, 0f, 0f, 16, 16, size, size, 16f, 16f
                );
            }

            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
        } catch (Exception ignored) {
        }
    }

    private int getTeamColor(String team) {
        switch (team.toLowerCase()) {
            case "red":
                return 0xFFFF4444;
            case "blue":
                return 0xFF4488FF;
            case "green":
                return 0xFF44FF66;
            case "yellow":
                return 0xFFFFFF44;
            case "aqua":
                return 0xFF44FFFF;
            case "white":
                return 0xFFEEEEEE;
            case "pink":
                return 0xFFFF88CC;
            case "gray":
            default:
                return 0xFF888888;
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

    private int getPrestigeColor(int star) {
        if (star >= 5000) return 0xFF00FFFF;
        if (star >= 4900) return 0xFFFF0000;
        if (star >= 4800) return 0xFFFF00FF;
        if (star >= 4700) return 0xFF00AA00;
        if (star >= 4600) return 0xFF00AAAA;
        if (star >= 4500) return 0xFF0000AA;
        if (star >= 4400) return 0xFFAA0000;
        if (star >= 4300) return 0xFF555555;
        if (star >= 4200) return 0xFFAAAAAA;
        if (star >= 4100) return 0xFFFFFFFF;
        if (star >= 4000) return 0xFF55FF55;

        if (star >= 3900) return 0xFF00FFFF;
        if (star >= 3800) return 0xFFFF0000;
        if (star >= 3700) return 0xFFFF00FF;
        if (star >= 3600) return 0xFF00AA00;
        if (star >= 3500) return 0xFF00AAAA;
        if (star >= 3400) return 0xFF0000AA;
        if (star >= 3300) return 0xFFAA0000;
        if (star >= 3200) return 0xFF555555;
        if (star >= 3100) return 0xFFAAAAAA;
        if (star >= 3000) return 0xFFFFFFFF;

        if (star >= 2900) return 0xFF00FFFF;
        if (star >= 2800) return 0xFFFF0000;
        if (star >= 2700) return 0xFFFF00FF;
        if (star >= 2600) return 0xFF00AA00;
        if (star >= 2500) return 0xFF00AAAA;
        if (star >= 2400) return 0xFF0000AA;
        if (star >= 2300) return 0xFFAA0000;
        if (star >= 2200) return 0xFF555555;
        if (star >= 2100) return 0xFFAAAAAA;
        if (star >= 2000) return 0xFFFFFFFF;

        if (star >= 1900) return 0xFF00FFFF;
        if (star >= 1800) return 0xFFFF0000;
        if (star >= 1700) return 0xFFFF00FF;
        if (star >= 1600) return 0xFF00AA00;
        if (star >= 1500) return 0xFF00AAAA;
        if (star >= 1400) return 0xFF0000AA;
        if (star >= 1300) return 0xFFAA0000;
        if (star >= 1200) return 0xFF555555;
        if (star >= 1100) return 0xFFAAAAAA;
        if (star >= 1000) return 0xFFFFFFFF;

        if (star >= 900) return 0xFFFF00FF;
        if (star >= 800) return 0xFF5555FF;
        if (star >= 700) return 0xFF55FFFF;
        if (star >= 600) return 0xFF55FF55;
        if (star >= 500) return 0xFFFF5555;
        if (star >= 400) return 0xFF0000AA;
        if (star >= 300) return 0xFF00AAAA;
        if (star >= 200) return 0xFFFFAA00;
        if (star >= 100) return 0xFFFFFFFF;

        return 0xFFAAAAAA;
    }

    private void drawText(String text, int x, int y, int color) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        mc.fontRendererObj.drawString(text, x, y, color, true);
    }

    private void fillRect(int x, int y, int width, int height, int color) {
        Gui.drawRect(x, y, x + width, y + height, color);
    }

    private void drawRoundedRect(
            int x,
            int y,
            int x2,
            int y2,
            int radius,
            int color
    ) {
        RoundedUtils.drawRoundedRect(x, y, x2 - x, y2 - y, radius, color);
    }
}
