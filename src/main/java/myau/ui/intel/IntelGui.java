package myau.ui.intel;

import myau.ui.clickgui.GuiColors;
import myau.ui.clickgui.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class IntelGui extends GuiScreen {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int CARD_H     = 50;
    private static final int CARD_GAP   = 5;
    private static final int SCROLL_SPD = 14;

    private static final int BG          = 0xF2080810;
    private static final int PANEL_BG    = 0xFF0D0D18;
    private static final int CARD_BG     = 0xFF111120;
    private static final int CARD_HOV    = 0xFF171728;
    private static final int CARD_SEL    = 0xFF1A1A32;
    private static final int DIVIDER     = 0x22FFFFFF;
    private static final int TEXT_DIM    = 0xFF444455;
    private static final int TEXT_MID    = 0xFF888899;
    private static final int TEXT_BRIGHT = 0xFFDDDDEE;

    private static final String[] SORT_LABELS = {"Threat","FKDR","WLR","Streak","Name"};

    private int  sortMode    = 0;
    private int  scrollOff   = 0;
    private int  maxScroll   = 0;
    private IntelPlayer selected = null;
    private List<IntelPlayer> players = new ArrayList<>();

    public void setPlayers(List<IntelPlayer> p) {
        players = new ArrayList<>(p);
        sortPlayers();
        if (selected == null && !players.isEmpty()) selected = players.get(0);
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();

        // Full bg
        fill(0, 0, sw, sh, BG);

        int HDR = 42, FTR = 32;
        int detailW = selected != null ? sw / 3 : 0;
        int listW   = sw - detailW;

        drawHeader(sw, HDR, mx, my);
        drawList(0, HDR, listW, sh - HDR - FTR, mx, my);
        if (selected != null)
            drawDetail(listW, HDR, detailW, sh - HDR - FTR, mx, my);
        drawFooter(sw, sh, FTR);

        super.drawScreen(mx, my, pt);
    }

    // ─── Header ───────────────────────────────────────────────────────────────

    private void drawHeader(int sw, int hdr, int mx, int my) {
        fill(0, 0, sw, hdr, PANEL_BG);
        fillH(0, hdr - 1, sw, GuiColors.ACCENT_DIM);

        gl(); mc.fontRendererObj.drawString("LOBBY INTEL", 14, 8, GuiColors.ACCENT, false);
        mc.fontRendererObj.drawString(players.size() + " players  •  " +
                (IntelManager.getInstance().isFetching() ? "fetching..." : "ready"),
                14, 20, TEXT_DIM, false);

        // Sort tabs centered
        int tabW = 54, tabH = 20, tabGap = 4;
        int totalTabW = SORT_LABELS.length * (tabW + tabGap) - tabGap;
        int bx = sw/2 - totalTabW/2;
        for (int i = 0; i < SORT_LABELS.length; i++) {
            int by  = (hdr - tabH) / 2;
            boolean active = sortMode == i;
            boolean hov    = mx>=bx && mx<bx+tabW && my>=by && my<by+tabH;
            int bg  = active ? GuiColors.ACCENT : hov ? 0xFF1A1A2A : 0xFF111118;
            RoundedUtils.drawRoundedRect(bx, by, tabW, tabH, tabH/2f, bg);
            if (active) RoundedUtils.drawRoundedOutline(bx, by, tabW, tabH, tabH/2f, 1f, GuiColors.ACCENT);
            gl();
            int tc = active ? 0xFF0D0D18 : TEXT_MID;
            int lw = mc.fontRendererObj.getStringWidth(SORT_LABELS[i]);
            mc.fontRendererObj.drawString(SORT_LABELS[i], bx+(tabW-lw)/2f, by+(tabH-8)/2f, tc, false);
            bx += tabW + tabGap;
        }

        // Refresh button top right
        String ref = "R  Refresh";
        int rw = mc.fontRendererObj.getStringWidth(ref) + 12;
        int rx = sw - rw - 10, ry = (hdr-16)/2;
        boolean rHov = mx>=rx && mx<rx+rw && my>=ry && my<ry+16;
        RoundedUtils.drawRoundedRect(rx, ry, rw, 16, 4, rHov ? 0xFF222233 : 0xFF141420);
        gl(); mc.fontRendererObj.drawString(ref, rx+6f, ry+4f, TEXT_MID, false);
    }

    // ─── Player list ──────────────────────────────────────────────────────────

    private void drawList(int lx, int ly, int lw, int lh, int mx, int my) {
        fill(lx, ly, lw, lh, PANEL_BG);

        // Column header bar
        int colY = ly + 5;
        fill(lx, colY + 11, lw, 1, DIVIDER);
        gl();
        mc.fontRendererObj.drawString("PLAYER",  lx+50,        colY, TEXT_DIM, false);
        mc.fontRendererObj.drawString("FKDR",    lx+lw*36/100, colY, TEXT_DIM, false);
        mc.fontRendererObj.drawString("WLR",     lx+lw*50/100, colY, TEXT_DIM, false);
        mc.fontRendererObj.drawString("STREAK",  lx+lw*64/100, colY, TEXT_DIM, false);
        mc.fontRendererObj.drawString("THREAT",  lx+lw-60,     colY, TEXT_DIM, false);

        int cY   = ly + 18;
        int cH   = lh - 18;
        int tot  = players.size() * (CARD_H + CARD_GAP);
        maxScroll = Math.max(0, tot - cH);
        scrollOff = Math.max(0, Math.min(scrollOff, maxScroll));

        ScaledResolution sr = new ScaledResolution(mc);
        int scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(lx * scale, (sr.getScaledHeight() - cY - cH) * scale, lw * scale, cH * scale);

        for (int i = 0; i < players.size(); i++) {
            int cy = cY + i * (CARD_H + CARD_GAP) - scrollOff;
            if (cy + CARD_H < cY || cy > cY + cH) continue;
            IntelPlayer p = players.get(i);
            boolean hov = mx>=lx+4 && mx<lx+lw-4 && my>=cy && my<cy+CARD_H;
            boolean sel = p == selected;
            drawCard(p, lx+4, cy, lw-8, CARD_H, hov, sel, lw);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Scrollbar
        if (maxScroll > 0) {
            int sbH = Math.max(18, cH * cH / tot);
            int sbY = cY + (int)((float)scrollOff/maxScroll * (cH - sbH));
            fill(lx+lw-4, cY, 3, cH, 0x22FFFFFF);
            fill(lx+lw-4, sbY, 3, sbH, GuiColors.ACCENT_DIM);
        }
    }

    private void drawCard(IntelPlayer p, int cx, int cy, int cw, int ch,
                          boolean hov, boolean sel, int lw) {
        int bg = sel ? CARD_SEL : hov ? CARD_HOV : CARD_BG;
        RoundedUtils.drawRoundedRect(cx, cy, cw, ch, 5, bg);
        if (sel) RoundedUtils.drawRoundedOutline(cx, cy, cw, ch, 5, 1.2f, GuiColors.ACCENT);

        // Threat color strip
        int tc = threatCol((int)p.threatScore);
        RoundedUtils.drawRoundedRect(cx, cy, 3, ch, 1, tc);

        // Avatar letter
        RoundedUtils.drawRoundedRect(cx+7, cy+ch/2-10, 20, 20, 4,
                p.loading ? 0xFF222233 : tc & 0x44FFFFFF | 0x44000000);
        gl(); mc.fontRendererObj.drawString(String.valueOf(p.name.charAt(0)).toUpperCase(),
                cx+14f, cy+ch/2-4f, p.loading ? TEXT_DIM : tc, false);

        // Name
        int nameColor = p.cheater ? 0xFFFF3344 : TEXT_BRIGHT;
        String nameStr = (p.cheater ? "⚑ " : "") + p.name;
        gl(); mc.fontRendererObj.drawString(nameStr, cx+32f, cy+8f, nameColor, false);

        // Level
        String lvl = "✦" + p.level;
        mc.fontRendererObj.drawString(lvl, cx+32f, cy+22f, p.loading ? TEXT_DIM : 0xFFFFCC44, false);

        // Urchin pill
        if (p.urchinTag != null) {
            int tw = mc.fontRendererObj.getStringWidth(p.urchinTag) + 6;
            RoundedUtils.drawRoundedRect(cx+32, cy+ch-13, tw, 9, 2, 0x44FF3344);
            mc.fontRendererObj.drawString(p.urchinTag, cx+35f, cy+ch-12f, 0xFFFF5566, false);
        }

        // Team dot
        if (p.team != null) {
            RoundedUtils.drawRoundedRect(cx+cw-14, cy+5, 9, 9, 2, teamCol(p.team));
        }

        if (p.loading) {
            gl(); mc.fontRendererObj.drawString("—", cx+lw*36/100-cx, cy+ch/2-4, TEXT_DIM, false);
            return;
        }

        // Stats
        int sy = cy + ch/2 - 4;
        gl();
        mc.fontRendererObj.drawString(fmt(p.fkdr),            cx+lw*36/100-cx, sy, statCol(p.fkdr,   3,  6),  false);
        mc.fontRendererObj.drawString(fmt(p.wlr),             cx+lw*50/100-cx, sy, statCol(p.wlr,    1.5,4),  false);
        mc.fontRendererObj.drawString(String.valueOf(p.winstreak), cx+lw*64/100-cx, sy, statCol(p.winstreak,10,30), false);

        // Threat bar
        int bx = cx+cw-60, bw = 38, bh = 4, by = cy+ch/2-2;
        fill(bx, by, bw, bh, 0x33FFFFFF);
        int fw = (int)(Math.min(1f, p.threatScore/100f)*bw);
        if (fw>0) fill(bx, by, fw, bh, tc);
        mc.fontRendererObj.drawString(String.valueOf((int)p.threatScore), bx+bw+3f, by-2f, tc, false);
    }

    // ─── Detail panel ─────────────────────────────────────────────────────────

    private void drawDetail(int px, int py, int pw, int ph, int mx, int my) {
        fill(px, py, pw, ph, PANEL_BG);
        RoundedUtils.drawRoundedOutline(px, py, pw, ph, 0, 1f, GuiColors.ACCENT_DIM);

        IntelPlayer p = selected;
        if (p == null) return;

        int x = px+14, y = py+14;
        int tc = threatCol((int)p.threatScore);

        // Name big
        gl();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(1.6f, 1.6f, 1f);
        mc.fontRendererObj.drawString(p.name, 0, 0,
                p.cheater ? 0xFFFF3344 : TEXT_BRIGHT, false);
        GlStateManager.popMatrix();
        y += 22;

        mc.fontRendererObj.drawString("✦ " + p.level + (p.team!=null ? "  ["+p.team+"]" : ""),
                x, y, 0xFFFFCC44, false);
        y += 16;

        // Urchin tag if present
        if (p.urchinTag != null) {
            int tw = mc.fontRendererObj.getStringWidth("⚑ "+p.urchinTag)+10;
            RoundedUtils.drawRoundedRect(x, y, tw, 14, 3, 0x55FF3344);
            RoundedUtils.drawRoundedOutline(x, y, tw, 14, 3, 1f, 0x99FF3344);
            mc.fontRendererObj.drawString("⚑ "+p.urchinTag, x+5f, y+3f, 0xFFFF5566, false);
            y += 20;
        }

        // Threat bar
        fillH(x, y, pw-28, DIVIDER);
        y += 6;
        mc.fontRendererObj.drawString("THREAT SCORE", x, y, TEXT_DIM, false);
        mc.fontRendererObj.drawString(((int)p.threatScore)+"/100", pw+px-mc.fontRendererObj.getStringWidth(((int)p.threatScore)+"/100")-14, y, tc, false);
        y += 11;
        fill(x, y, pw-28, 5, 0x33FFFFFF);
        int fw = (int)(Math.min(1f,p.threatScore/100f)*(pw-28));
        if (fw>0) fill(x, y, fw, 5, tc);
        y += 14;

        if (p.loading) { mc.fontRendererObj.drawString("Fetching stats...", x, y, TEXT_DIM, false); return; }

        fillH(x, y, pw-28, DIVIDER); y += 8;

        // Stat rows
        y = detailRow(x, y, pw, "Final K/D Ratio",  fmt(p.fkdr),           statCol(p.fkdr,  3,   6));
        y = detailRow(x, y, pw, "Win/Loss Ratio",   fmt(p.wlr),            statCol(p.wlr,   1.5, 4));
        y = detailRow(x, y, pw, "Win Streak",       String.valueOf(p.winstreak), statCol(p.winstreak,10,30));
        y = detailRow(x, y, pw, "Final Kills",      String.valueOf(p.finalKills), TEXT_BRIGHT);
        y = detailRow(x, y, pw, "Beds Broken",      String.valueOf(p.bedsBroken), TEXT_BRIGHT);
        y = detailRow(x, y, pw, "Total Wins",       String.valueOf(p.wins), TEXT_BRIGHT);

        y += 4; fillH(x, y, pw-28, DIVIDER); y += 8;

        // Intel recommendation
        mc.fontRendererObj.drawString("INTEL", x, y, TEXT_DIM, false); y += 12;
        for (String line : wrap(recommend(p), pw-28)) {
            mc.fontRendererObj.drawString(line, x, y, TEXT_MID, false); y += 11;
        }
    }

    private int detailRow(int x, int y, int pw, String label, String val, int valCol) {
        gl();
        mc.fontRendererObj.drawString(label, x, y, TEXT_DIM, false);
        mc.fontRendererObj.drawString(val, x+pw-28-mc.fontRendererObj.getStringWidth(val)-14, y, valCol, false);
        return y + 13;
    }

    // ─── Footer ───────────────────────────────────────────────────────────────

    private void drawFooter(int sw, int sh, int ftr) {
        int fy = sh - ftr;
        fill(0, fy, sw, ftr, PANEL_BG);
        fillH(0, fy, sw, GuiColors.ACCENT_DIM);
        gl();
        mc.fontRendererObj.drawString("ESC  close    SCROLL  navigate    CLICK  inspect player    R  refresh",
                14, fy+(ftr-8)/2f, TEXT_DIM, false);
    }

    // ─── Input ────────────────────────────────────────────────────────────────

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) scrollOff -= dw > 0 ? SCROLL_SPD : -SCROLL_SPD;
        scrollOff = Math.max(0, Math.min(scrollOff, maxScroll));
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
        int HDR = 42, FTR = 32;
        int detailW = selected != null ? sw/3 : 0;
        int listW   = sw - detailW;

        // Sort tabs
        int tabW = 54, tabH = 20, tabGap = 4;
        int totalTabW = SORT_LABELS.length * (tabW + tabGap) - tabGap;
        int bx = sw/2 - totalTabW/2;
        for (int i = 0; i < SORT_LABELS.length; i++) {
            int by = (HDR-tabH)/2;
            if (mx>=bx && mx<bx+tabW && my>=by && my<by+tabH) {
                sortMode = i; sortPlayers(); return;
            }
            bx += tabW + tabGap;
        }

        // Refresh button
        String ref = "R  Refresh";
        int rw = mc.fontRendererObj.getStringWidth(ref)+12;
        int rx = sw-rw-10, ry = (HDR-16)/2;
        if (mx>=rx && mx<rx+rw && my>=ry && my<ry+16) {
            IntelManager.getInstance().refresh(); return;
        }

        // Card click
        int cY = HDR + 18, cH = sh - HDR - FTR - 18;
        for (int i = 0; i < players.size(); i++) {
            int cy = cY + i*(CARD_H+CARD_GAP) - scrollOff;
            if (my>=cy && my<cy+CARD_H && mx>=4 && mx<listW-4) {
                selected = players.get(i); return;
            }
        }

        super.mouseClicked(mx, my, button);
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == 19) { IntelManager.getInstance().refresh(); return; }
        if (key == 1)  { mc.displayGuiScreen(null); return; }
        super.keyTyped(c, key);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void sortPlayers() {
        switch (sortMode) {
            case 0: players.sort((a,b)->Double.compare(b.threatScore, a.threatScore)); break;
            case 1: players.sort((a,b)->Double.compare(b.fkdr,        a.fkdr));        break;
            case 2: players.sort((a,b)->Double.compare(b.wlr,         a.wlr));         break;
            case 3: players.sort((a,b)->Integer.compare(b.winstreak,  a.winstreak));   break;
            case 4: players.sort(Comparator.comparing(p->p.name));                     break;
        }
    }

    private int threatCol(int score) {
        if (score >= 75) return 0xFFFF2244;
        if (score >= 50) return 0xFFFF7722;
        if (score >= 25) return 0xFFFFCC22;
        return 0xFF44DD66;
    }

    private int statCol(double v, double mid, double high) {
        if (v >= high)   return 0xFFFF3344;
        if (v >= mid)    return 0xFFFF9933;
        if (v >= mid/2)  return 0xFFFFEE44;
        return 0xFF44CC66;
    }

    private int teamCol(String t) {
        switch(t) {
            case "red":    return 0xFFFF4444;
            case "blue":   return 0xFF4488FF;
            case "green":  return 0xFF44FF66;
            case "yellow": return 0xFFFFFF44;
            case "aqua":   return 0xFF44FFFF;
            case "white":  return 0xFFEEEEEE;
            case "pink":   return 0xFFFF88CC;
            default:       return 0xFF888888;
        }
    }

    private String fmt(double v) {
        if (v < 0) return "—";
        return v == (long)v ? String.valueOf((long)v) : String.format("%.2f", v);
    }

    private String recommend(IntelPlayer p) {
        if (p.cheater)       return "Flagged by Urchin. Expect unusual movement and combat. Avoid and focus bed defense.";
        if (p.threatScore >= 75) return "High priority threat. Rush their bed before they scale. Do not engage alone.";
        if (p.threatScore >= 50) return "Solid player. Contest mid generators early. Engage with full armor advantage.";
        if (p.threatScore >= 25) return "Average player. Farm if convenient. Safe to ignore until late game.";
        return "Low skill level. Easy resource target. Rush early for free resources.";
    }

    private List<String> wrap(String text, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : text.split(" ")) {
            String test = cur.length()==0 ? w : cur+" "+w;
            if (mc.fontRendererObj.getStringWidth(test) > maxW) {
                out.add(cur.toString()); cur = new StringBuilder(w);
            } else cur = new StringBuilder(test);
        }
        if (cur.length()>0) out.add(cur.toString());
        return out;
    }

    private void fill(int x, int y, int w, int h, int color) {
        RoundedUtils.drawRoundedRect(x, y, w, h, 0, color);
    }

    private void fillH(int x, int y, int w, int color) {
        fill(x, y, w, 1, color);
    }

    private void gl() {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1f,1f,1f,1f);
    }
}
