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

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int    ROW_H       = 20;
    private static final int    HEAD_S      = 14;
    private static final int    PAD_X       = 8;
    private static final int    PAD_Y       = 6;
    private static final int    HEADER_H    = 18;
    private static final float  CORNER_R    = 6f;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int ACCENT      = GuiColors.ACCENT;           // pink
    private static final int ACCENT_DIM  = 0x44E991B8;
    private static final int TEXT_WHITE  = 0xFFEEEEFF;
    private static final int TEXT_DIM    = 0xFF777788;
    private static final int COL_SEP     = 0x18FFFFFF;                 // very subtle column divider

    // ── Config ────────────────────────────────────────────────────────────────
    private boolean enabled       = true;
    private int     posX          = 10;
    private int     posY          = 100;
    private float   scale         = 1.0f;
    private int     maxPlayers    = 10;
    private int     bgOpacity     = 200;
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
    private final java.util.Map<String, ResourceLocation> skinCache   = new java.util.HashMap<>();
    private final java.util.Map<String, Boolean>          skinIsSheet = new java.util.HashMap<>();

    public IntelHudOverlay() {}

    // ── Setters ───────────────────────────────────────────────────────────────
    public void setEnabled(boolean v)       { enabled = v; }
    public void setPosition(int x, int y)   { posX = x; posY = y; }
    public void setScale(float v)           { scale = Math.max(0.5f, Math.min(2f, v)); }
    public void setMaxPlayers(int v)        { maxPlayers = Math.max(1, Math.min(20, v)); }
    public void setBgOpacity(int v)         { bgOpacity = Math.max(0, Math.min(255, v)); }
    public void setShowHeads(boolean v)     { showHeads = v; }
    public void setShowStar(boolean v)      { showStar = v; }
    public void setShowFkdr(boolean v)      { showFkdr = v; }
    public void setShowWlr(boolean v)       { showWlr = v; }
    public void setShowStreak(boolean v)    { showStreak = v; }
    public void setShowThreat(boolean v)    { showThreat = v; }
    public void setShowUrchin(boolean v)    { showUrchin = v; }
    public void setShowTeamColor(boolean v) { showTeamColor = v; }
    public void setSortMode(String v)       { sortMode = v; }

    // ── Getters ───────────────────────────────────────────────────────────────
    public boolean isEnabled()     { return enabled; }
    public int     getPosX()       { return posX; }
    public int     getPosY()       { return posY; }
    public float   getScale()      { return scale; }
    public int     getMaxPlayers() { return maxPlayers; }
    public int     getBgOpacity()  { return bgOpacity; }
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
            case "fkdr": players.sort((a,b) -> Double.compare(b.fkdr, a.fkdr)); break;
            case "name": players.sort(Comparator.comparing(p -> p.name)); break;
            default:     players.sort((a,b) -> Double.compare(b.threatScore, a.threatScore)); break;
        }
    }

    private List<IntelPlayer> getDisplayPlayers() {
        try {
            myau.module.modules.LobbyIntel li =
                (myau.module.modules.LobbyIntel) myau.Myau.moduleManager.getModule(
                    myau.module.modules.LobbyIntel.class);
            boolean hideTeam = li != null && li.hideTeammates.getValue();
            if (!hideTeam) return new ArrayList<>(players);

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

    // ── Column widths ─────────────────────────────────────────────────────────
    private static final int COL_NAME   = 100;
    private static final int COL_STAR   = 38;
    private static final int COL_FKDR   = 38;
    private static final int COL_WLR    = 34;
    private static final int COL_WS     = 28;
    private static final int COL_URCHIN = 24;
    private static final int COL_THREAT = 40;

    private int totalWidth() {
        int w = PAD_X * 2;
        if (showHeads)     w += HEAD_S + 4;
        if (showTeamColor) w += 4;
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

        int count = Math.min(display.size(), maxPlayers);
        int W = totalWidth();
        int H = HEADER_H + PAD_Y + count * ROW_H + PAD_Y;

        GlStateManager.pushMatrix();
        GlStateManager.translate(posX, posY, 0);
        GlStateManager.scale(scale, scale, 1f);

        // ── Outer shadow ──────────────────────────────────────────────────────
        RoundedUtils.drawRoundedRect(-4, -4, W + 8, H + 8, CORNER_R + 4, 0x28000000);
        RoundedUtils.drawRoundedRect(-2, -2, W + 4, H + 4, CORNER_R + 2, 0x33000000);

        // ── Main panel background ─────────────────────────────────────────────
        int bgColor = (bgOpacity << 24) | 0x07070E;
        RoundedUtils.drawRoundedRect(0, 0, W, H, CORNER_R, bgColor);

        // ── Pink gradient header bar ──────────────────────────────────────────
        RoundedUtils.drawRoundedRect(0, 0, W, HEADER_H, CORNER_R, 0xCC0D0D1A);
        // Pink shimmer line along top
        gradRect(0, 0, W, 1, 0xAAE991B8, 0x22E991B8);
        // Subtle pink glow under header
        gradRect(0, HEADER_H - 2, W, 2, 0x00E991B8, 0x22E991B8);
        // Header bottom divider
        Gui.drawRect(0, HEADER_H, W, HEADER_H + 1, 0x33E991B8);

        // ── Draw column headers ───────────────────────────────────────────────
        int hx = PAD_X;
        if (showTeamColor) hx += 4;
        if (showHeads)     hx += HEAD_S + 4;

        drawHeader("NAME",   hx,           COL_NAME);   hx += COL_NAME;
        if (showStar)   { drawColDivider(hx); drawHeader("✫",    hx, COL_STAR);   hx += COL_STAR; }
        if (showFkdr)   { drawColDivider(hx); drawHeader("FKDR", hx, COL_FKDR);   hx += COL_FKDR; }
        if (showWlr)    { drawColDivider(hx); drawHeader("WLR",  hx, COL_WLR);    hx += COL_WLR; }
        if (showStreak) { drawColDivider(hx); drawHeader("WS",   hx, COL_WS);     hx += COL_WS; }
        if (showUrchin) { drawColDivider(hx); drawHeader("FLAG", hx, COL_URCHIN); hx += COL_URCHIN; }
        if (showThreat) { drawColDivider(hx); drawHeader("THREAT", hx, COL_THREAT); }

        // ── Draw rows ─────────────────────────────────────────────────────────
        int rowY = HEADER_H + PAD_Y;
        for (int i = 0; i < count; i++) {
            IntelPlayer p = display.get(i);

            // Alternate row tint
            if (i % 2 == 0) {
                RoundedUtils.drawRoundedRect(1, rowY - 1, W - 2, ROW_H, 2, 0x08FFFFFF);
            }

            // Threat-based left glow stripe
            if (p.threatScore >= 75) {
                RoundedUtils.drawRoundedRect(0, rowY - 1, 3, ROW_H, 2, 0xAAFF2244);
            } else if (p.threatScore >= 50) {
                RoundedUtils.drawRoundedRect(0, rowY - 1, 3, ROW_H, 2, 0xAAFF7722);
            } else if (p.cheater) {
                RoundedUtils.drawRoundedRect(0, rowY - 1, 3, ROW_H, 2, 0xAAFFCC22);
            }

            drawRow(p, rowY);
            rowY += ROW_H;
        }

        // ── Outer border ──────────────────────────────────────────────────────
        RoundedUtils.drawRoundedOutline(0, 0, W, H, CORNER_R, 1f, 0x33E991B8);

        GlStateManager.popMatrix();
    }

    private void drawHeader(String text, int x, int colW) {
        int tw = mc.fontRendererObj.getStringWidth(text);
        int tx = x + (colW - tw) / 2;
        int ty = (HEADER_H - mc.fontRendererObj.FONT_HEIGHT) / 2;
        GlStateManager.enableTexture2D();
        mc.fontRendererObj.drawString(text, tx, ty, TEXT_DIM, false);
    }

    private void drawColDivider(int x) {
        Gui.drawRect(x, 2, x + 1, HEADER_H - 2, COL_SEP);
    }

    private void drawRow(IntelPlayer p, int rowY) {
        int cx = PAD_X;
        int midY = rowY + (ROW_H - mc.fontRendererObj.FONT_HEIGHT) / 2;

        // Team color stripe
        if (showTeamColor && p.team != null) {
            Gui.drawRect(cx, rowY + 2, cx + 2, rowY + ROW_H - 2, teamColor(p.team));
            cx += 4;
        } else if (showTeamColor) {
            cx += 4;
        }

        // Head
        if (showHeads) {
            drawHead(p.name, cx, rowY + (ROW_H - HEAD_S) / 2);
            cx += HEAD_S + 4;
        }

        // Name
        int nameColor = p.cheater ? 0xFFFF5566 : TEXT_WHITE;
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        mc.fontRendererObj.drawString(p.name, cx, midY, nameColor, true);
        cx += COL_NAME;

        // Star
        if (showStar) {
            String s = p.loading ? "-" : "✫" + p.star;
            drawCentered(s, cx, midY, COL_STAR, p.loading ? TEXT_DIM : prestigeColor(p.star));
            cx += COL_STAR;
        }
        // FKDR
        if (showFkdr) {
            String s = p.loading ? "-" : String.format("%.1f", p.fkdr);
            drawCentered(s, cx, midY, COL_FKDR, p.loading ? TEXT_DIM : statColor(p.fkdr, 3.0, 6.0));
            cx += COL_FKDR;
        }
        // WLR
        if (showWlr) {
            String s = p.loading ? "-" : String.format("%.1f", p.wlr);
            drawCentered(s, cx, midY, COL_WLR, p.loading ? TEXT_DIM : statColor(p.wlr, 2.0, 4.0));
            cx += COL_WLR;
        }
        // Winstreak
        if (showStreak) {
            String s = p.loading ? "-" : String.valueOf(p.winstreak);
            int wsc = p.loading ? TEXT_DIM : p.winstreak >= 10 ? 0xFFFFCC44 : p.winstreak >= 5 ? 0xFF44DD66 : TEXT_DIM;
            drawCentered(s, cx, midY, COL_WS, wsc);
            cx += COL_WS;
        }
        // Urchin
        if (showUrchin) {
            if (p.cheater && p.urchinType != null) {
                String icon; int ic;
                if      (p.urchinType.contains("blatant"))   { icon = "BC"; ic = 0xFFFF3344; }
                else if (p.urchinType.contains("confirmed")) { icon = "CC"; ic = 0xFFDD44DD; }
                else if (p.urchinType.contains("sniper"))    { icon = "S";  ic = 0xFFFF1122; }
                else                                          { icon = "C";  ic = 0xFFFF8844; }
                drawCentered(icon, cx, midY, COL_URCHIN, ic);
            }
            cx += COL_URCHIN;
        }
        // Threat — shown as colored number + thin bar underneath
        if (showThreat) {
            int threat = (int) p.threatScore;
            String ts = p.loading ? "-" : String.valueOf(threat);
            int tc = threatColor(threat);
            drawCentered(ts, cx, midY - 2, COL_THREAT, p.loading ? TEXT_DIM : tc);

            // Mini threat bar
            if (!p.loading) {
                float ratio = Math.min(1f, threat / 100f);
                int barY = rowY + ROW_H - 4;
                int barX = cx + 2;
                int barW = COL_THREAT - 4;
                Gui.drawRect(barX, barY, barX + barW, barY + 2, 0xFF111111);
                if (ratio > 0) {
                    int fillW = Math.max(2, (int)(ratio * barW));
                    // Gradient: green → red based on threat
                    gradRect(barX, barY, barX + fillW, barY + 2, tc, darken(tc));
                }
            }
        }

        GlStateManager.enableDepth();
    }

    private void drawCentered(String text, int x, int y, int colW, int color) {
        int tw = mc.fontRendererObj.getStringWidth(text);
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        mc.fontRendererObj.drawString(text, x + (colW - tw) / 2, y, color, true);
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
            if (skin == null) {
                skin = new ResourceLocation("textures/entity/steve.png");
                skinIsSheet.put(name, true);
            }
            GlStateManager.pushMatrix();
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
            GlStateManager.popMatrix();
        } catch (Exception ignored) {}
    }

    // ── GL helpers ────────────────────────────────────────────────────────────
    private void gradRect(int x1, int y1, int x2, int y2, int colL, int colR) {
        float aL=(colL>>24&0xFF)/255f, rL=(colL>>16&0xFF)/255f, gL=(colL>>8&0xFF)/255f, bL=(colL&0xFF)/255f;
        float aR=(colR>>24&0xFF)/255f, rR=(colR>>16&0xFF)/255f, gR=(colR>>8&0xFF)/255f, bR=(colR&0xFF)/255f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(rL,gL,bL,aL); GL11.glVertex2f(x1,y1);
        GL11.glColor4f(rR,gR,bR,aR); GL11.glVertex2f(x2,y1);
        GL11.glColor4f(rR,gR,bR,aR); GL11.glVertex2f(x2,y2);
        GL11.glColor4f(rL,gL,bL,aL); GL11.glVertex2f(x1,y2);
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1,1,1,1);
    }

    // ── Color helpers ─────────────────────────────────────────────────────────
    private int darken(int color) {
        int r = Math.max(0, (color>>16&0xFF) - 60);
        int g = Math.max(0, (color>>8 &0xFF) - 60);
        int b = Math.max(0, (color     &0xFF) - 60);
        return (color & 0xFF000000) | (r<<16) | (g<<8) | b;
    }

    private int threatColor(int score) {
        if (score >= 75) return 0xFFFF2244;
        if (score >= 50) return 0xFFFF7722;
        if (score >= 25) return 0xFFFFCC22;
        return 0xFF44DD66;
    }

    private int statColor(double v, double mid, double high) {
        if (v < 0) return TEXT_DIM;
        if (v >= high) return 0xFFFF3344;
        if (v >= mid)  return 0xFFFF9933;
        if (v >= mid/2)return 0xFFFFEE44;
        return 0xFF44CC66;
    }

    private int teamColor(String team) {
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

    private int prestigeColor(int star) {
        if (star >= 1000) return 0xFFFFFFFF;
        if (star >= 900)  return 0xFFFF00FF;
        if (star >= 800)  return 0xFF5555FF;
        if (star >= 700)  return 0xFF55FFFF;
        if (star >= 600)  return 0xFF55FF55;
        if (star >= 500)  return 0xFFFF5555;
        if (star >= 400)  return 0xFF0000AA;
        if (star >= 300)  return 0xFF00AAAA;
        if (star >= 200)  return 0xFFFFAA00;
        if (star >= 100)  return 0xFFFFFFFF;
        return 0xFFAAAAAA;
    }
}
