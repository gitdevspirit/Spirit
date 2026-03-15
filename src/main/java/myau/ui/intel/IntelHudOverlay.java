package myau.ui.intel;

import myau.ui.clickgui.GuiColors;
import myau.ui.clickgui.RoundedUtils;
import myau.util.render.BlurShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class IntelHudOverlay {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int   ROW_H    = 20;
    private static final int   HEAD_S   = 14;
    private static final int   PAD_X    = 8;
    private static final int   PAD_Y    = 5;
    private static final int   HEADER_H = 18;
    private static final float CORNER_R = 6f;

    // Column widths
    private static final int COL_NAME   = 90;
    private static final int COL_STAR   = 38;
    private static final int COL_FKDR   = 38;
    private static final int COL_WLR    = 36;
    private static final int COL_WS     = 32;
    private static final int COL_URCHIN = 36;
    private static final int COL_THREAT = 44;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int ACCENT     = GuiColors.ACCENT;
    private static final int TEXT_WHITE = 0xFFEEEEFF;
    private static final int TEXT_DIM   = 0xFF667788;

    // ── Config ────────────────────────────────────────────────────────────────
    private boolean enabled       = true;
    private int     posX          = 10;
    private int     posY          = 100;
    private float   scale         = 1.0f;
    private int     maxPlayers    = 10;
    private int     bgOpacity     = 180;
    private int     borderOpacity = 100;
    private boolean showHeads     = true;
    private boolean showStar      = true;
    private boolean showFkdr      = true;
    private boolean showWlr       = true;
    private boolean showStreak    = true;
    private boolean showThreat    = true;
    private boolean showUrchin    = true;
    private boolean showTeamColor = true;
    private String  sortMode      = "threat";

    private List<IntelPlayer> players = new ArrayList<>();
    // Tracks last render positions of sort-clickable headers for click detection
    private int lastRenderX = 0, lastRenderY = 0;
    private int[] colHeaderX = new int[7]; // star,fkdr,wlr,ws,tags,threat positions
    private int colHeaderY = 0, colHeaderH = 0;
    private final java.util.Map<String, ResourceLocation> skinCache   = new java.util.HashMap<>();
    private final java.util.Map<String, Boolean>          skinIsSheet = new java.util.HashMap<>();

    public IntelHudOverlay() {}

    // ── Setters / Getters ─────────────────────────────────────────────────────
    public void setEnabled(boolean v)       { enabled = v; }
    public void setPosition(int x, int y)   { posX = x; posY = y; }
    public void setScale(float v)           { scale = Math.max(0.5f, Math.min(2f, v)); }
    public void setMaxPlayers(int v)        { maxPlayers = Math.max(1, Math.min(20, v)); }
    public void setBgOpacity(int v)         { bgOpacity     = Math.max(0, Math.min(255, v)); }
    public void setBorderOpacity(int v)     { borderOpacity = Math.max(0, Math.min(255, v)); }
    public void setShowHeads(boolean v)     { showHeads = v; }
    public void setShowStar(boolean v)      { showStar = v; }
    public void setShowFkdr(boolean v)      { showFkdr = v; }
    public void setShowWlr(boolean v)       { showWlr = v; }
    public void setShowStreak(boolean v)    { showStreak = v; }
    public void setShowThreat(boolean v)    { showThreat = v; }
    public void setShowUrchin(boolean v)    { showUrchin = v; }
    public void setShowTeamColor(boolean v) { showTeamColor = v; }
    public void setSortMode(String v)       { sortMode = v; }

    public boolean isEnabled()        { return enabled; }
    public int     getPosX()          { return posX; }
    public int     getPosY()          { return posY; }
    public float   getScale()         { return scale; }
    public int     getMaxPlayers()    { return maxPlayers; }
    public int     getBgOpacity()      { return bgOpacity; }
    public int     getBorderOpacity()  { return borderOpacity; }
    public boolean getShowHeads()     { return showHeads; }
    public boolean getShowStar()      { return showStar; }
    public boolean getShowFkdr()      { return showFkdr; }
    public boolean getShowWlr()       { return showWlr; }
    public boolean getShowStreak()    { return showStreak; }
    public boolean getShowThreat()    { return showThreat; }
    public boolean getShowUrchin()    { return showUrchin; }
    public boolean getShowTeamColor() { return showTeamColor; }
    public String  getSortMode()      { return sortMode; }

    public void setPlayers(List<IntelPlayer> p) {
        players = new ArrayList<>(p);
        sortPlayers();
    }

    public void cacheSkin(String name, ResourceLocation skin, boolean isSheet) {
        skinCache.put(name, skin);
        skinIsSheet.put(name, isSheet);
    }

    private void sortPlayers() {
        switch (sortMode) {
            case "fkdr":
                players.sort((a, b) -> {
                    int c = Double.compare(b.fkdr, a.fkdr);
                    return c != 0 ? c : Double.compare(b.threatScore, a.threatScore);
                });
                break;
            case "name":
                players.sort(Comparator.comparing(p -> p.name));
                break;
            default: // threat
                players.sort((a, b) -> {
                    // Loading players go to bottom, sorted players at top
                    if (a.loading && !b.loading) return 1;
                    if (!a.loading && b.loading) return -1;
                    return Double.compare(b.threatScore, a.threatScore);
                });
                break;
        }
    }

    private List<IntelPlayer> getDisplayPlayers() {
        try {
            myau.module.modules.LobbyIntel li =
                (myau.module.modules.LobbyIntel) myau.Myau.moduleManager.getModule(
                    myau.module.modules.LobbyIntel.class);
            if (li == null || !li.hideTeammates.getValue()) return new ArrayList<>(players);

            String myName = mc.thePlayer != null ? mc.thePlayer.getName() : null;
            String myTeam = null;
            if (myName != null)
                for (IntelPlayer p : players)
                    if (p.name.equals(myName)) { myTeam = p.team; break; }

            List<IntelPlayer> out = new ArrayList<>();
            for (IntelPlayer p : players) {
                if (myName != null && p.name.equals(myName)) continue;
                if (myTeam != null && myTeam.equals(p.team)) continue;
                out.add(p);
            }
            return out;
        } catch (Exception e) { return new ArrayList<>(players); }
    }

    private int totalWidth() {
        int w = PAD_X * 2;
        if (showTeamColor) w += 4;
        if (showHeads)     w += HEAD_S + 4;
        w += COL_NAME;
        if (showStar)   w += COL_STAR;
        if (showFkdr)   w += COL_FKDR;
        if (showWlr)    w += COL_WLR;
        if (showStreak) w += COL_WS;
        if (showUrchin) w += COL_URCHIN;
        if (showThreat) w += COL_THREAT;
        return w;
    }

    // ── Main render ───────────────────────────────────────────────────────────
    public void render() {
        if (!enabled || players.isEmpty()) return;
        List<IntelPlayer> display = getDisplayPlayers();
        if (display.isEmpty()) return;

        sortPlayers(); // re-sort each frame so newly loaded stats reorder immediately
        int count = Math.min(display.size(), maxPlayers);
        int W = totalWidth();
        int H = HEADER_H + PAD_Y + count * ROW_H + PAD_Y;

        GlStateManager.pushMatrix();
        GlStateManager.translate(posX, posY, 0);
        GlStateManager.scale(scale, scale, 1f);

        // ── Frosted glass blur (fixed strength, looks best at 8) ────────────────
        BlurShadowRenderer.renderFrostedGlass(0, 0, W, H, CORNER_R, 8, 160);

        // ── Dark overlay — bgOpacity controls how dark/opaque the panel is ─────
        solidRect(0, 0, W, H, (bgOpacity << 24) | 0x06060D);

        // ── Pink top shimmer line ──────────────────────────────────────────────
        gradRect(2, 0, W - 2, 1, 0xCCE991B8, 0x00E991B8);

        // ── Header section ────────────────────────────────────────────────────
        solidRect(0, 0, W, HEADER_H, 0x220D0D1A);
        // Header bottom divider
        int divAlpha = Math.min(255, borderOpacity + 30);
        gradRect(4, HEADER_H, W - 4, 1, (divAlpha << 24) | 0xE991B8, 0x00E991B8);

        // ── Column headers ────────────────────────────────────────────────────
        int hx = PAD_X;
        if (showTeamColor) hx += 4;
        if (showHeads)     hx += HEAD_S + 4;

        int headerTextY = (HEADER_H - mc.fontRendererObj.FONT_HEIGHT) / 2;
        drawSortHeader("NAME", hx, headerTextY, COL_NAME, "name"); hx += COL_NAME;
        // Clickable sort headers — highlighted when active, underlined
        if (showStar)   { colDiv(hx, HEADER_H); drawSortHeader("STAR", hx, headerTextY, COL_STAR, "star");   hx += COL_STAR; }
        if (showFkdr)   { colDiv(hx, HEADER_H); drawSortHeader("FKDR",   hx, headerTextY, COL_FKDR,   "fkdr");   hx += COL_FKDR; }
        if (showWlr)    { colDiv(hx, HEADER_H); drawSortHeader("WLR",    hx, headerTextY, COL_WLR,    "wlr");    hx += COL_WLR; }
        if (showStreak) { colDiv(hx, HEADER_H); drawSortHeader("WS",     hx, headerTextY, COL_WS,     "streak"); hx += COL_WS; }
        if (showUrchin) { colDiv(hx, HEADER_H); drawSortHeader("TAGS",   hx, headerTextY, COL_URCHIN, "urchin"); hx += COL_URCHIN; }
        if (showThreat) { colDiv(hx, HEADER_H); drawSortHeader("THREAT", hx, headerTextY, COL_THREAT, "threat"); }

        // ── Rows ──────────────────────────────────────────────────────────────
        int rowY = HEADER_H + PAD_Y;
        for (int i = 0; i < count; i++) {
            IntelPlayer p = display.get(i);

            // Subtle alternating tint
            if (i % 2 == 0) solidRect(1, rowY - 1, W - 2, ROW_H, 0x0AFFFFFF);

            // Threat left stripe
            int stripeColor = 0;
            if      (p.threatScore >= 75) stripeColor = 0xBBFF2244;
            else if (p.threatScore >= 50) stripeColor = 0xBBFF7722;
            else if (p.cheater)           stripeColor = 0xBBFFCC22;
            if (stripeColor != 0) solidRect(0, rowY, 3, ROW_H - 2, stripeColor);

            drawRow(p, rowY);
            rowY += ROW_H;
        }

        // ── Outer pink border ─────────────────────────────────────────────────
        int borderColor = (borderOpacity << 24) | 0xE991B8;
        RoundedUtils.drawRoundedOutline(0, 0, W, H, CORNER_R, 1f, borderColor);

        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
    }

    private void drawRow(IntelPlayer p, int rowY) {
        int cx = PAD_X;
        int fontH = mc.fontRendererObj.FONT_HEIGHT;
        int midY = rowY + (ROW_H - fontH) / 2;

        // Team stripe
        if (showTeamColor) {
            if (p.team != null) solidRect(cx, rowY + 2, 2, ROW_H - 4, teamColor(p.team));
            cx += 4;
        }

        // Head
        if (showHeads) {
            drawHead(p.name, cx, rowY + (ROW_H - HEAD_S) / 2);
            cx += HEAD_S + 4;
        }

        // Name
        enableText();
        int nameColor = p.cheater ? 0xFFFF5566 : 0xFFCCCCDD;
        mc.fontRendererObj.drawString(p.name, cx, midY, nameColor, true);
        cx += COL_NAME;

        // Star
        if (showStar) {
            drawStarCell(p.loading ? "-" : String.valueOf(p.star),
                     cx, midY, COL_STAR,
                     p.loading ? TEXT_DIM : prestigeColor(p.star));
            cx += COL_STAR;
        }
        // FKDR
        if (showFkdr) {
            drawCell(p.loading ? "-" : String.format("%.1f", p.fkdr),
                     cx, midY, COL_FKDR,
                     p.loading ? TEXT_DIM : statColor(p.fkdr, 3.0, 6.0));
            cx += COL_FKDR;
        }
        // WLR
        if (showWlr) {
            drawCell(p.loading ? "-" : String.format("%.1f", p.wlr),
                     cx, midY, COL_WLR,
                     p.loading ? TEXT_DIM : statColor(p.wlr, 2.0, 4.0));
            cx += COL_WLR;
        }
        // Winstreak
        if (showStreak) {
            int wsc = p.loading ? TEXT_DIM
                    : p.winstreak >= 10 ? 0xFFFFCC44
                    : p.winstreak >= 5  ? 0xFF44DD66
                    : TEXT_DIM;
            drawCell(p.loading ? "-" : String.valueOf(p.winstreak), cx, midY, COL_WS, wsc);
            cx += COL_WS;
        }
        // Urchin flag
        if (showUrchin) {
            if (p.cheater && p.urchinType != null) {
                String icon; int ic;
                if      (p.urchinType.contains("blatant"))   { icon = "BC"; ic = 0xFFFF3344; }
                else if (p.urchinType.contains("confirmed")) { icon = "CC"; ic = 0xFFDD44DD; }
                else if (p.urchinType.contains("sniper"))    { icon = "S";  ic = 0xFFFF1122; }
                else                                          { icon = "C";  ic = 0xFFFF8844; }
                drawCell(icon, cx, midY, COL_URCHIN, ic);
            }
            cx += COL_URCHIN;
        }
        // Threat — number aligned same as all other cells + mini bar at very bottom
        if (showThreat) {
            int threat = (int) p.threatScore;
            int tc = threatColor(threat);
            // Draw number at same midY as every other cell
            drawCell(p.loading ? "-" : String.valueOf(threat),
                     cx, midY, COL_THREAT,
                     p.loading ? TEXT_DIM : tc);

            if (!p.loading) {
                float ratio = Math.min(1f, threat / 100f);
                int bx = cx + 3;
                int by = rowY + ROW_H - 3;
                int bw = COL_THREAT - 6;
                solidRect(bx, by, bw, 2, 0xFF111111);
                if (ratio > 0) {
                    int fw = Math.max(2, (int)(ratio * bw));
                    gradRect(bx, by, fw, 2, tc, darken(tc));
                }
            }
        }
    }

    // ── Draw helpers ──────────────────────────────────────────────────────────
    private void enableText() {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();
    }

    private void drawSortHeader(String text, int x, int y, int colW, String mode) {
        boolean active = sortMode.equals(mode);
        int tw = mc.fontRendererObj.getStringWidth(text);
        int tx = x + (colW - tw) / 2;
        GlStateManager.enableTexture2D();
        mc.fontRendererObj.drawString(text, tx, y, active ? ACCENT : TEXT_WHITE, false);
        if (active) solidRect(x + 2, HEADER_H - 3, colW - 4, 1, ACCENT);
    }

    private void drawSortHeaderScaled(String text, int x, int y, int colW, String mode, float s) {
        boolean active = sortMode.equals(mode);
        int tw = (int)(mc.fontRendererObj.getStringWidth(text) * s);
        int tx = x + (colW - tw) / 2;
        GlStateManager.enableTexture2D();
        GlStateManager.pushMatrix();
        GlStateManager.translate(tx, y - (s - 1f) * mc.fontRendererObj.FONT_HEIGHT / 2f, 0);
        GlStateManager.scale(s, s, 1f);
        mc.fontRendererObj.drawString(text, 0, 0, active ? ACCENT : TEXT_WHITE, false);
        GlStateManager.popMatrix();
        if (active) solidRect(x + 2, HEADER_H - 3, colW - 4, 1, ACCENT);
    }

    /** Renders ☆ at 1.3x then the number at normal size, all centered in the column. */
    private void drawStarCell(String numText, int x, int y, int colW, int color) {
        float starScale = 1.1f;
        String star = "☆";
        int starW  = (int)(mc.fontRendererObj.getStringWidth(star) * starScale);
        int numW   = mc.fontRendererObj.getStringWidth(numText);
        int gap    = 1;
        int totalW = starW + gap + numW;
        int startX = x + (colW - totalW) / 2;

        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();

        // Draw star scaled - keep same baseline as number
        GlStateManager.pushMatrix();
        GlStateManager.translate(startX, y, 0);
        GlStateManager.scale(starScale, starScale, 1f);
        mc.fontRendererObj.drawString(star, 0, 0, color, true);
        GlStateManager.popMatrix();

        // Draw number at normal size
        mc.fontRendererObj.drawString(numText, startX + starW + gap, y, color, true);
    }

    /** Call from LobbyIntel mouse handler to cycle sort by clicking column headers. */
    public boolean handleClick(int mx, int my) {
        if (!enabled) return false;
        int lx = (int)((mx - posX) / scale);
        int ly = (int)((my - posY) / scale);
        if (lx < 0 || lx > totalWidth() || ly < 0 || ly >= HEADER_H) return false;

        int cx2 = PAD_X;
        if (showTeamColor) cx2 += 4;
        if (showHeads)     cx2 += HEAD_S + 4;
        cx2 += COL_NAME;

        // Check NAME column first
        if (lx >= PAD_X + (showTeamColor ? 4 : 0) + (showHeads ? HEAD_S + 4 : 0)
                && lx < PAD_X + (showTeamColor ? 4 : 0) + (showHeads ? HEAD_S + 4 : 0) + COL_NAME) {
            sortMode = "name"; sortPlayers(); return true;
        }
        String[] modes  = { "star",    "fkdr",   "wlr",    "streak",  "urchin",   "threat"  };
        int[]    widths = { COL_STAR,  COL_FKDR, COL_WLR,  COL_WS,   COL_URCHIN, COL_THREAT };
        boolean[] shown = { showStar,  showFkdr, showWlr,  showStreak,showUrchin, showThreat };

        for (int i = 0; i < modes.length; i++) {
            if (!shown[i]) continue;
            if (lx >= cx2 && lx < cx2 + widths[i]) {
                sortMode = modes[i];
                sortPlayers();
                return true;
            }
            cx2 += widths[i];
        }
        return false;
    }

    private void drawHeaderText(String text, int x, int y, int colW) {
        int tw = mc.fontRendererObj.getStringWidth(text);
        enableText();
        mc.fontRendererObj.drawString(text, x + (colW - tw) / 2, y, TEXT_DIM, false);
    }

    private void drawCell(String text, int x, int y, int colW, int color) {
        int tw = mc.fontRendererObj.getStringWidth(text);
        enableText();
        mc.fontRendererObj.drawString(text, x + (colW - tw) / 2, y, color, true);
    }

    private void colDiv(int x, int h) {
        solidRect(x, 3, 1, h - 6, 0x18FFFFFF);
    }

    private void solidRect(int x, int y, int w, int h, int color) {
        float a = (color >> 24 & 0xFF) / 255f;
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >> 8  & 0xFF) / 255f;
        float b = (color       & 0xFF) / 255f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x,     y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x,     y + h);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void gradRect(int x, int y, int w, int h, int cL, int cR) {
        float aL=(cL>>24&0xFF)/255f,rL=(cL>>16&0xFF)/255f,gL=(cL>>8&0xFF)/255f,bL=(cL&0xFF)/255f;
        float aR=(cR>>24&0xFF)/255f,rR=(cR>>16&0xFF)/255f,gR=(cR>>8&0xFF)/255f,bR=(cR&0xFF)/255f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(rL,gL,bL,aL); GL11.glVertex2f(x,   y);
        GL11.glColor4f(rR,gR,bR,aR); GL11.glVertex2f(x+w, y);
        GL11.glColor4f(rR,gR,bR,aR); GL11.glVertex2f(x+w, y+h);
        GL11.glColor4f(rL,gL,bL,aL); GL11.glVertex2f(x,   y+h);
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1,1,1,1);
    }

    private void drawHead(String name, int x, int y) {
        try {
            ResourceLocation skin = skinCache.get(name);
            if (skin == null && mc.getNetHandler() != null) {
                NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(name);
                if (info != null && info.getLocationSkin() != null) {
                    skin = info.getLocationSkin();
                    skinCache.put(name, skin);
                    skinIsSheet.put(name, true);
                }
            }
            if (skin == null) skin = new ResourceLocation("textures/entity/steve.png");

            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.getTextureManager().bindTexture(skin);
            boolean sheet = skinIsSheet.getOrDefault(name, true);
            if (sheet) {
                Gui.drawScaledCustomSizeModalRect(x, y, 8f, 8f, 8, 8, HEAD_S, HEAD_S, 64f, 64f);
                Gui.drawScaledCustomSizeModalRect(x, y, 40f, 8f, 8, 8, HEAD_S, HEAD_S, 64f, 64f);
            } else {
                Gui.drawScaledCustomSizeModalRect(x, y, 0f, 0f, 16, 16, HEAD_S, HEAD_S, 16f, 16f);
            }
            GlStateManager.color(1f, 1f, 1f, 1f);
        } catch (Exception ignored) {}
    }

    // ── Color helpers ─────────────────────────────────────────────────────────
    private int darken(int c) {
        return (c & 0xFF000000)
             | (Math.max(0,(c>>16&0xFF)-60) << 16)
             | (Math.max(0,(c>>8 &0xFF)-60) << 8)
             |  Math.max(0,(c    &0xFF)-60);
    }
    private int threatColor(int s) {
        if (s >= 75) return 0xFFFF2244;
        if (s >= 50) return 0xFFFF7722;
        if (s >= 25) return 0xFFFFCC22;
        return 0xFF44DD66;
    }
    private int statColor(double v, double mid, double high) {
        if (v < 0)      return TEXT_DIM;
        if (v >= high)  return 0xFFFF3344;
        if (v >= mid)   return 0xFFFF9933;
        if (v >= mid/2) return 0xFFFFEE44;
        return 0xFF44CC66;
    }
    private int teamColor(String t) {
        switch (t.toLowerCase()) {
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
    private int prestigeColor(int s) {
        if (s < 100)   return 0xFFAAAAAA; // 0-99:    Gray
        if (s < 200)   return 0xFFFFFFFF; // 100-199: White
        if (s < 300)   return 0xFFFFAA00; // 200-299: Gold
        if (s < 400)   return 0xFF55FFFF; // 300-399: Aqua
        if (s < 500)   return 0xFF55FF55; // 400-499: Green
        if (s < 600)   return 0xFF55FFFF; // 500-599: Aqua
        if (s < 700)   return 0xFFFF5555; // 600-699: Red
        if (s < 800)   return 0xFFFF55FF; // 700-799: Pink
        if (s < 900)   return 0xFF5555FF; // 800-899: Blue
        if (s < 1000)  return 0xFFAA00AA; // 900-999: Purple
        if (s < 1100)  return 0xFFFFAA00; // 1000-1099: Gold
        if (s < 2000)  return 0xFFAAAAAA; // 1100-1999: Gray (1000s prestige)
        if (s < 2100)  return 0xFFFFAA00; // 2000-2099: Gold
        if (s < 2200)  return 0xFFFFAA00; // 2100-2199: Gold
        if (s < 2300)  return 0xFFFF55FF; // 2200-2299: Pink
        if (s < 2400)  return 0xFF00AAAA; // 2300-2399: Teal
        if (s < 2500)  return 0xFFFFFFFF; // 2400-2499: White
        if (s < 2600)  return 0xFFFF5555; // 2500-2599: Red
        if (s < 2700)  return 0xFFFFFF55; // 2600-2699: Yellow
        if (s < 2800)  return 0xFF55FF55; // 2700-2799: Green
        if (s < 2900)  return 0xFF55FFFF; // 2800-2899: Aqua
        if (s < 3000)  return 0xFFFFAA00; // 2900-2999: Gold
        if (s < 3100)  return 0xFFFFAA00; // 3000-3099: Gold
        if (s < 3200)  return 0xFF5555FF; // 3100-3199: Blue
        if (s < 3300)  return 0xFFFF5555; // 3200-3299: Red
        if (s < 3400)  return 0xFFAA0000; // 3300-3399: Dark Red
        if (s < 3500)  return 0xFF55FFFF; // 3400-3499: Teal
        if (s < 3600)  return 0xFF55FFFF; // 3500-3599: Aqua
        if (s < 3700)  return 0xFF55FF55; // 3600-3699: Green
        if (s < 3800)  return 0xFFFF7700; // 3700-3799: Orange
        if (s < 3900)  return 0xFF0000AA; // 3800-3899: Dark Blue
        if (s < 4000)  return 0xFFFF5577; // 3900-3999: Pink Red
        if (s < 4100)  return 0xFF55FF55; // 4000-4099: Green
        if (s < 4200)  return 0xFFFFFF55; // 4100-4199: Yellow
        if (s < 4300)  return 0xFF0000AA; // 4200-4299: Dark Blue
        if (s < 4400)  return 0xFF333333; // 4300-4399: Black/Dark
        if (s < 4500)  return 0xFFFF55FF; // 4400-4499: Pink
        if (s < 4600)  return 0xFFFFFFFF; // 4500-4599: White
        if (s < 4700)  return 0xFF55FFFF; // 4600-4699: Aqua
        if (s < 4800)  return 0xFFAAFFFF; // 4700-4799: Light Aqua
        if (s < 4900)  return 0xFFAA00AA; // 4800-4899: Purple
        if (s < 5000)  return 0xFFFF5555; // 4900-4999: Red
        return           0xFF5555FF;       // 5000+:     Blue
    }
}
