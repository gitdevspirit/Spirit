package myau.ui.intel;

import myau.ui.clickgui.GuiColors;
import myau.ui.clickgui.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class IntelGui extends GuiScreen {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int CARD_H   = 36;
    private static final int CARD_GAP = 3;
    private static final int SCROLL_SPD = 16;

    // Background — semi-transparent dark, world still faintly visible
    private static final int BG_OUTER  = 0xBB05050A;
    private static final int BG_PANEL  = 0xCC0A0A12;
    private static final int BG_CARD   = 0xDD0D0D18;
    private static final int BG_HOV    = 0xDD131325;
    private static final int BG_SEL    = 0xDD16162E;
    private static final int DIVIDER   = 0x1AFFFFFF;

    private static final int TEXT_DIM    = 0xFF3D3D55;
    private static final int TEXT_MID    = 0xFF7777AA;
    private static final int TEXT_BRIGHT = 0xFFDDDDEE;
    private static final int ACCENT      = GuiColors.ACCENT;  // pink E991B8

    private static final String[] SORT_LABELS = {"Threat","FKDR","WLR","Streak","Name"};

    private int  sortMode  = 0;
    private int  scrollOff = 0;
    private int  maxScroll = 0;
    private IntelPlayer selected = null;
    private List<IntelPlayer> players = new ArrayList<>();

    // Search bar
    private String searchText  = "";
    private boolean searchFocus = false;
    private long searchBlink   = 0;

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

        // Semi-transparent dark overlay (keeps world faintly visible)
        fillRect(0, 0, sw, sh, 0xBB000008);

        int HDR = 40, FTR = 28, SRCH = 26;
        int detailW = selected != null ? sw / 3 : 0;
        int listW   = sw - detailW;

        drawHeader(sw, HDR, mx, my);
        drawSearchBar(0, HDR, listW, SRCH, mx, my);
        drawList(0, HDR + SRCH, listW, sh - HDR - SRCH - FTR, mx, my, sh);
        if (selected != null) drawDetail(listW, HDR, detailW, sh - HDR - FTR);
        drawFooter(sw, sh, FTR);

        super.drawScreen(mx, my, pt);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void drawHeader(int sw, int hdr, int mx, int my) {
        fillRect(0, 0, sw, hdr, 0xCC08080F);
        fillRect(0, hdr - 1, sw, 1, 0x33E991B8);

        gl(); mc.fontRendererObj.drawString("LOBBY INTEL", 14, 8, ACCENT, false);
        mc.fontRendererObj.drawString(players.size() + " players  •  " +
                (IntelManager.getInstance().isFetching() ? "fetching..." : "ready"),
                14, 20, TEXT_DIM, false);

        // Sort tabs
        int tabW = 52, tabH = 18, tabGap = 3;
        int totalW = SORT_LABELS.length * (tabW + tabGap) - tabGap;
        int bx = sw / 2 - totalW / 2;
        for (int i = 0; i < SORT_LABELS.length; i++) {
            int by = (hdr - tabH) / 2;
            boolean active = sortMode == i;
            boolean hov = mx >= bx && mx < bx + tabW && my >= by && my < by + tabH;
            // Unselected = white/dim, selected = pink with outline
            int bg = active ? 0xCC110D18 : hov ? 0x22FFFFFF : 0x11FFFFFF;
            RoundedUtils.drawRoundedRect(bx, by, tabW, tabH, tabH / 2f, bg);
            if (active) RoundedUtils.drawRoundedOutline(bx, by, tabW, tabH, tabH / 2f, 1.2f, ACCENT);
            gl();
            int tc = active ? ACCENT : hov ? TEXT_BRIGHT : 0xFFCCCCCC;
            int lw = mc.fontRendererObj.getStringWidth(SORT_LABELS[i]);
            mc.fontRendererObj.drawString(SORT_LABELS[i], bx + (tabW - lw) / 2f, by + (tabH - 8) / 2f, tc, false);
            bx += tabW + tabGap;
        }

        // Refresh
        String ref = "R  Refresh";
        int rw = mc.fontRendererObj.getStringWidth(ref) + 10;
        int rx = sw - rw - 10, ry = (hdr - 14) / 2;
        boolean rHov = mx >= rx && mx < rx + rw && my >= ry && my < ry + 14;
        RoundedUtils.drawRoundedRect(rx, ry, rw, 14, 3, rHov ? 0x33FFFFFF : 0x11FFFFFF);
        gl(); mc.fontRendererObj.drawString(ref, rx + 5f, ry + 3f, rHov ? TEXT_BRIGHT : TEXT_MID, false);
    }

    // ── Search bar ────────────────────────────────────────────────────────────

    private void drawSearchBar(int sx, int sy, int sw, int sh, int mx, int my) {
        fillRect(sx, sy, sw, sh, 0xAA06060C);
        fillRect(sx, sy + sh - 1, sw, 1, 0x22FFFFFF);

        int bx = sx + 8, by = sy + 5, bw = 200, bh = sh - 10;
        boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        int borderCol = searchFocus ? ACCENT : hov ? 0x55FFFFFF : 0x22FFFFFF;
        RoundedUtils.drawRoundedRect(bx, by, bw, bh, 3, 0x33000000);
        RoundedUtils.drawRoundedOutline(bx, by, bw, bh, 3, 1f, borderCol);

        gl();
        String display = searchText.isEmpty() && !searchFocus
                ? "Search / add player..."
                : searchText;
        int textCol = searchText.isEmpty() ? TEXT_DIM : TEXT_BRIGHT;

        // Cursor blink
        String drawn = display;
        if (searchFocus && (System.currentTimeMillis() - searchBlink) % 1000 < 500) {
            drawn = searchText + "|";
        }
        mc.fontRendererObj.drawString(drawn, bx + 5f, by + (bh - 8) / 2f, textCol, false);

        // Add button
        if (!searchText.isEmpty()) {
            int abx = bx + bw + 6, abw = mc.fontRendererObj.getStringWidth("+ Add") + 10;
            boolean ahov = mx >= abx && mx < abx + abw && my >= by && my < by + bh;
            RoundedUtils.drawRoundedRect(abx, by, abw, bh, 3, ahov ? 0x44E991B8 : 0x22E991B8);
            RoundedUtils.drawRoundedOutline(abx, by, abw, bh, 3, 1f, ahov ? ACCENT : 0x44E991B8);
            gl(); mc.fontRendererObj.drawString("+ Add", abx + 5f, by + (bh - 8) / 2f, ahov ? ACCENT : TEXT_MID, false);
        }
    }



    private void drawList(int lx, int ly, int lw, int lh, int mx, int my, int sh) {
        fillRect(lx, ly, lw, lh, 0xAA08080E);

        // Column headers
        int hY = ly + 5;
        gl();
        mc.fontRendererObj.drawString("PLAYER",  lx + 46,          hY, TEXT_DIM, false);
        mc.fontRendererObj.drawString("FKDR",    lx + lw * 36/100, hY, TEXT_DIM, false);
        mc.fontRendererObj.drawString("WLR",     lx + lw * 50/100, hY, TEXT_DIM, false);
        mc.fontRendererObj.drawString("STREAK",  lx + lw * 64/100, hY, TEXT_DIM, false);
        mc.fontRendererObj.drawString("THREAT",  lx + lw - 58,     hY, TEXT_DIM, false);
        fillRect(lx + 6, hY + 10, lw - 12, 1, 0x22FFFFFF);

        int cY  = ly + 20;
        int cH  = lh - 20;
        int tot = players.size() * (CARD_H + CARD_GAP);
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
            boolean hov = mx >= lx + 4 && mx < lx + lw - 4 && my >= cy && my < cy + CARD_H;
            boolean sel = p == selected;
            drawCard(p, lx + 4, cy, lw - 8, CARD_H, hov, sel, lw);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        if (maxScroll > 0) {
            int sbH = Math.max(16, cH * cH / tot);
            int sbY = cY + (int)((float) scrollOff / maxScroll * (cH - sbH));
            fillRect(lx + lw - 4, cY, 2, cH, 0x22FFFFFF);
            fillRect(lx + lw - 4, sbY, 2, sbH, 0x66E991B8);
        }
    }

    private void drawCard(IntelPlayer p, int cx, int cy, int cw, int ch,
                          boolean hov, boolean sel, int lw) {
        int bg = sel ? BG_SEL : hov ? BG_HOV : BG_CARD;
        RoundedUtils.drawRoundedRect(cx, cy, cw, ch, 4, bg);
        if (sel) RoundedUtils.drawRoundedOutline(cx, cy, cw, ch, 4, 1f, 0x88E991B8);

        int tc = threatCol((int) p.threatScore);

        // Threat strip on left edge
        RoundedUtils.drawRoundedRect(cx, cy, 2, ch, 1, tc);

        // Player head (8x8 px from skin, doubled = 16x16 display)
        int headSize = 16;
        int headX = cx + 6, headY = cy + (ch - headSize) / 2;
        drawPlayerHead(p.name, headX, headY, headSize);

        gl();
        // Name
        int nameCol = p.cheater ? 0xFFFF4466 : TEXT_BRIGHT;
        String prefix = p.cheater ? "⚑ " : "";
        mc.fontRendererObj.drawString(prefix + p.name, cx + 27f, cy + 6f, nameCol, false);

        // Level small below name
        mc.fontRendererObj.drawString("✦ " + p.level, cx + 27f, cy + 16f,
                p.loading ? TEXT_DIM : 0xFFFFCC44, false);

        // Urchin tag tiny pill
        if (p.urchinTag != null) {
            int tw = mc.fontRendererObj.getStringWidth(p.urchinTag) + 4;
            RoundedUtils.drawRoundedRect(cx + 27, cy + ch - 10, tw, 8, 2, 0x44FF3344);
            gl(); mc.fontRendererObj.drawString(p.urchinTag, cx + 29f, cy + ch - 9f, 0xFFFF5566, false);
        }

        // Team dot
        if (p.team != null)
            RoundedUtils.drawRoundedRect(cx + cw - 12, cy + 4, 7, 7, 2, teamCol(p.team));

        // Remove button for manually-added players
        if (IntelManager.getInstance().isManual(p)) {
            int xbx = cx + cw - 12, xby = cy + ch - 13;
            boolean xhov = false; // drawn only, click handled in mouseClicked
            gl(); mc.fontRendererObj.drawString("✕", xbx, xby, 0x88FF4466, false);
        }

        if (p.loading) {
            gl(); mc.fontRendererObj.drawString("—", cx + lw * 36 / 100 - cx, cy + ch / 2 - 4, TEXT_DIM, false);
            return;
        }

        // Stats
        int sy = cy + ch / 2 - 4;
        gl();
        mc.fontRendererObj.drawString(fmt(p.fkdr),   cx + lw*36/100-cx, sy, statCol(p.fkdr,  3, 6),  false);
        mc.fontRendererObj.drawString(fmt(p.wlr),    cx + lw*50/100-cx, sy, statCol(p.wlr,   1.5,4), false);
        mc.fontRendererObj.drawString(String.valueOf(p.winstreak), cx+lw*64/100-cx, sy, statCol(p.winstreak,10,30), false);

        // Threat bar + number
        int bx = cx + cw - 54, bw = 36, bh = 3, by = cy + ch / 2 - 1;
        fillRect(bx, by, bw, bh, 0x22FFFFFF);
        int fw = (int)(Math.min(1f, p.threatScore / 100f) * bw);
        if (fw > 0) fillRect(bx, by, fw, bh, tc);
        mc.fontRendererObj.drawString(String.valueOf((int) p.threatScore), bx + bw + 3f, by - 2f, tc, false);
    }

    // ── Detail panel ──────────────────────────────────────────────────────────

    private void drawDetail(int px, int py, int pw, int ph) {
        fillRect(px, py, pw, ph, 0xCC0A0A14);
        fillRect(px, py, 1, ph, 0x33E991B8);

        IntelPlayer p = selected;
        if (p == null) return;

        int x = px + 14, y = py + 14;
        int tc = threatCol((int) p.threatScore);

        // Head large
        drawPlayerHead(p.name, x, y, 32);

        // Name + level beside head
        gl();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 38, y + 2, 0);
        GlStateManager.scale(1.4f, 1.4f, 1f);
        mc.fontRendererObj.drawString(p.name, 0, 0, p.cheater ? 0xFFFF3344 : TEXT_BRIGHT, false);
        GlStateManager.popMatrix();

        mc.fontRendererObj.drawString("✦ " + p.level + (p.team != null ? "  [" + p.team + "]" : ""),
                x + 38f, y + 20f, 0xFFFFCC44, false);
        y += 42;

        if (p.urchinTag != null) {
            int tw = mc.fontRendererObj.getStringWidth("⚑ " + p.urchinTag) + 8;
            RoundedUtils.drawRoundedRect(x, y, tw, 13, 3, 0x44FF3344);
            RoundedUtils.drawRoundedOutline(x, y, tw, 13, 3, 1f, 0x77FF3344);
            gl(); mc.fontRendererObj.drawString("⚑ " + p.urchinTag, x + 4f, y + 3f, 0xFFFF5566, false);
            y += 18;
        }

        // Threat bar
        fillRect(x, y, pw - 28, 1, 0x22FFFFFF); y += 6;
        gl(); mc.fontRendererObj.drawString("THREAT", x, y, TEXT_DIM, false);
        mc.fontRendererObj.drawString((int) p.threatScore + "/100",
                x + pw - 28 - mc.fontRendererObj.getStringWidth((int)p.threatScore+"/100") - 14, y, tc, false);
        y += 10;
        fillRect(x, y, pw - 28, 4, 0x22FFFFFF);
        int fw = (int)(Math.min(1f, p.threatScore / 100f) * (pw - 28));
        if (fw > 0) fillRect(x, y, fw, 4, tc);
        y += 12;

        if (p.loading) { gl(); mc.fontRendererObj.drawString("Fetching stats...", x, y, TEXT_DIM, false); return; }

        fillRect(x, y, pw - 28, 1, 0x22FFFFFF); y += 8;
        y = dRow(x, y, pw, "Final K/D",    fmt(p.fkdr),                statCol(p.fkdr,  3,  6));
        y = dRow(x, y, pw, "Win/Loss",     fmt(p.wlr),                 statCol(p.wlr,   1.5,4));
        y = dRow(x, y, pw, "Streak",       String.valueOf(p.winstreak), statCol(p.winstreak,10,30));
        y = dRow(x, y, pw, "Final Kills",  String.valueOf(p.finalKills),  TEXT_BRIGHT);
        y = dRow(x, y, pw, "Beds Broken",  String.valueOf(p.bedsBroken),  TEXT_BRIGHT);
        y = dRow(x, y, pw, "Total Wins",   String.valueOf(p.wins),        TEXT_BRIGHT);

        y += 4; fillRect(x, y, pw - 28, 1, 0x22FFFFFF); y += 8;
        gl(); mc.fontRendererObj.drawString("INTEL", x, y, TEXT_DIM, false); y += 12;
        for (String line : wrap(recommend(p), pw - 28)) {
            gl(); mc.fontRendererObj.drawString(line, x, y, TEXT_MID, false); y += 11;
        }
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private void drawFooter(int sw, int sh, int ftr) {
        int fy = sh - ftr;
        fillRect(0, fy, sw, ftr, 0xCC08080F);
        fillRect(0, fy, sw, 1, 0x22E991B8);
        gl(); mc.fontRendererObj.drawString(
                "ESC  close    SCROLL  navigate    CLICK  inspect player    R  refresh",
                14, fy + (ftr - 8) / 2f, TEXT_DIM, false);
    }

    // ── Player head ───────────────────────────────────────────────────────────

    private void drawPlayerHead(String name, int x, int y, int size) {
        try {
            if (mc.getNetHandler() == null) return;
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfoMap().stream()
                    .filter(i -> i.getGameProfile().getName().equals(name))
                    .findFirst().orElse(null);
            if (info == null) return;
            ResourceLocation skin = info.getLocationSkin();
            if (skin == null) return;

            GlStateManager.enableBlend();
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.getTextureManager().bindTexture(skin);
            // Base layer (8,8 on 64x64 skin)
            Gui.drawScaledCustomSizeModalRect(x, y, 8f, 8f, 8, 8, size, size, 64f, 64f);
            // Hat layer (40,8)
            Gui.drawScaledCustomSizeModalRect(x, y, 40f, 8f, 8, 8, size, size, 64f, 64f);
            GlStateManager.color(1f, 1f, 1f, 1f);
        } catch (Exception ignored) {}
    }

    // ── Input ─────────────────────────────────────────────────────────────────

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
        int HDR = 40, FTR = 28, SRCH = 26;
        int detailW = selected != null ? sw / 3 : 0;
        int listW   = sw - detailW;

        // Sort tabs
        int tabW = 52, tabH = 18, tabGap = 3;
        int totalW = SORT_LABELS.length * (tabW + tabGap) - tabGap;
        int bx = sw / 2 - totalW / 2;
        for (int i = 0; i < SORT_LABELS.length; i++) {
            int by = (HDR - tabH) / 2;
            if (mx >= bx && mx < bx + tabW && my >= by && my < by + tabH) {
                sortMode = i; sortPlayers(); return;
            }
            bx += tabW + tabGap;
        }

        // Refresh
        String ref = "R  Refresh";
        int rw = mc.fontRendererObj.getStringWidth(ref) + 10;
        int rx = sw - rw - 10, ry = (HDR - 14) / 2;
        if (mx >= rx && mx < rx + rw && my >= ry && my < ry + 14) {
            IntelManager.getInstance().refresh(); return;
        }

        // Search bar focus
        int sbx = 8, sby = HDR + 5, sbw = 200, sbh = SRCH - 10;
        searchFocus = mx >= sbx && mx < sbx + sbw && my >= sby && my < sby + sbh;

        // Add button click
        if (!searchText.isEmpty()) {
            int abx = sbx + sbw + 6, abw = mc.fontRendererObj.getStringWidth("+ Add") + 10;
            if (mx >= abx && mx < abx + abw && my >= sby && my < sby + sbh) {
                IntelManager.getInstance().addManualPlayer(searchText.trim());
                searchText = "";
                return;
            }
        }

        // Card click + X remove
        int cY = HDR + SRCH + 20;
        for (int i = 0; i < players.size(); i++) {
            int cy = cY + i * (CARD_H + CARD_GAP) - scrollOff;
            if (my < cy || my >= cy + CARD_H || mx < 4 || mx >= listW - 4) continue;
            IntelPlayer p = players.get(i);
            // Check ✕ button (manual players only)
            if (IntelManager.getInstance().isManual(p)) {
                int xbx = 4 + (listW - 8) - 12;
                int xby = cy + CARD_H - 13;
                if (mx >= xbx && mx < xbx + 9 && my >= xby && my < xby + 9) {
                    IntelManager.getInstance().removeManualPlayer(p.name);
                    if (selected == p) selected = null;
                    return;
                }
            }
            selected = p; return;
        }

        super.mouseClicked(mx, my, button);
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (searchFocus) {
            if (key == 14) { // Backspace
                if (!searchText.isEmpty()) searchText = searchText.substring(0, searchText.length() - 1);
                return;
            }
            if (key == 28) { // Enter — add player
                if (!searchText.isEmpty()) {
                    IntelManager.getInstance().addManualPlayer(searchText.trim());
                    searchText = "";
                }
                return;
            }
            if (key == 1) { // ESC — unfocus first, then close on second press
                searchFocus = false; return;
            }
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                if (searchText.length() < 16) searchText += c;
            }
            return;
        }
        if (key == 19) { IntelManager.getInstance().refresh(); return; }
        if (key == 1)  { mc.displayGuiScreen(null); return; }
        super.keyTyped(c, key);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sortPlayers() {
        switch (sortMode) {
            case 0: players.sort((a,b)->Double.compare(b.threatScore,a.threatScore));  break;
            case 1: players.sort((a,b)->Double.compare(b.fkdr,a.fkdr));               break;
            case 2: players.sort((a,b)->Double.compare(b.wlr,a.wlr));                 break;
            case 3: players.sort((a,b)->Integer.compare(b.winstreak,a.winstreak));     break;
            case 4: players.sort(Comparator.comparing(p->p.name));                     break;
        }
    }

    private int dRow(int x, int y, int pw, String label, String val, int col) {
        gl();
        mc.fontRendererObj.drawString(label, x, y, TEXT_DIM, false);
        mc.fontRendererObj.drawString(val, x + pw - 28 - mc.fontRendererObj.getStringWidth(val) - 14, y, col, false);
        return y + 12;
    }

    private int threatCol(int s) {
        if (s >= 75) return 0xFFFF2244;
        if (s >= 50) return 0xFFFF7722;
        if (s >= 25) return 0xFFFFCC22;
        return 0xFF44DD66;
    }

    private int statCol(double v, double mid, double high) {
        if (v >= high)  return 0xFFFF3344;
        if (v >= mid)   return 0xFFFF9933;
        if (v >= mid/2) return 0xFFFFEE44;
        return 0xFF44CC66;
    }

    private int teamCol(String t) {
        switch (t) {
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
        return v == (long) v ? String.valueOf((long) v) : String.format("%.2f", v);
    }

    private String recommend(IntelPlayer p) {
        if (p.cheater && p.urchinTag != null) {
            String tag = p.urchinTag.toLowerCase();
            // Keyword-based intel
            if (containsAny(tag, "scaffold", "legitscaff", "bridg", "eagle")) {
                return "Flagged for scaffolding. Expect fast bridges and aerial angles. Destroy their bridge routes early.";
            }
            if (containsAny(tag, "autoblock", "auto block", "autoclick", "cps")) {
                return "Flagged for autoclicker or autoblock. Expect high CPS bursts. Keep distance and don't trade hits.";
            }
            if (containsAny(tag, "visual", "esp", "x-ray", "xray", "wallhack")) {
                return "Flagged for visuals/ESP. Assume they know your bed layout. Build covered defences.";
            }
            if (containsAny(tag, "killaura", "kill aura", "aimbot", "aim assist", "reach")) {
                return "Flagged for KillAura or aimbot. Don't fight directly. Bait with traps and rush bed instead.";
            }
            if (containsAny(tag, "velocity", "anti-kb", "antikb", "kb")) {
                return "Flagged for anti-knockback. Void traps won't work. Focus bed destruction over PvP.";
            }
            if (containsAny(tag, "fly", "speed", "movement", "bhop", "bunnyhop")) {
                return "Flagged for movement hacks. Expect rapid rushes. Fortify bed early and camp mid.";
            }
            if (containsAny(tag, "sniper", "crossbow")) {
                return "Flagged as sniper. Avoid open areas. Use tunnels and stay behind cover when exposed.";
            }
            // Generic cheater
            return "Flagged by Urchin. Expect unusual movement or combat. Avoid and focus bed defense.";
        }
        if (p.threatScore >= 75) return "High priority. Rush their bed before they scale. Don't engage alone.";
        if (p.threatScore >= 50) return "Solid player. Contest mid early. Engage with armor advantage.";
        if (p.threatScore >= 25) return "Average. Farm if convenient. Safe to deprioritise.";
        return "Low skill. Easy target. Rush early for free resources.";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String k : keywords) if (text.contains(k)) return true;
        return false;
    }

    private List<String> wrap(String text, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : text.split(" ")) {
            String test = cur.length() == 0 ? w : cur + " " + w;
            if (mc.fontRendererObj.getStringWidth(test) > maxW) {
                out.add(cur.toString()); cur = new StringBuilder(w);
            } else cur = new StringBuilder(test);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private void fillRect(int x, int y, int w, int h, int color) {
        Gui.drawRect(x, y, x + w, y + h, color);
    }

    private void gl() {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
