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

    // Layout
    private static final int HDR       = 44;
    private static final int SRCH      = 28;
    private static final int FTR       = 24;
    private static final int COL_HDR   = 18;   // column header row height
    private static final int CARD_H    = 42;
    private static final int CARD_GAP  = 2;
    private static final int CARD_PAD  = 6;    // horizontal padding each side
    private static final int HEAD_SIZE = 20;
    private static final int SCROLL_SPD = 18;

    // Column centre X positions (used for both headers and card values)
    // These are absolute screen X - centred under the header labels
    // FKDR col centre=290, WLR=370, STREAK=460, THREAT right block at listW-70
    private static final int COL_FKDR_C   = 290;
    private static final int COL_WLR_C    = 370;
    private static final int COL_STREAK_C = 455;

    // Colours
    private static final int BG_FULL  = 0xBB000008;
    private static final int BG_HDR   = 0xEE07070E;
    private static final int BG_SRCH  = 0xEE050509;
    private static final int BG_LIST  = 0xAA070710;
    private static final int BG_CARD  = 0xCC0C0C1A;
    private static final int BG_HOV   = 0xCC121228;
    private static final int BG_SEL   = 0xCC151535;
    private static final int BG_DETAIL= 0xEE08080F;

    private static final int COL_DIVIDER  = 0x18FFFFFF;
    private static final int COL_DIM      = 0xFF404055;
    private static final int COL_MID      = 0xFF8888BB;
    private static final int COL_BRIGHT   = 0xFFDDDDEE;
    private static final int COL_ACCENT   = GuiColors.ACCENT; // E991B8 pink
    private static final int COL_GOLD     = 0xFFFFCC44;
    private static final int COL_RED      = 0xFFFF3355;
    private static final int COL_REDSUB   = 0xAAFF3355;

    private static final String[] SORT_LABELS = {"Threat","FKDR","WLR","Streak","Name"};

    private int  sortMode  = 0;
    private int  scrollOff = 0;
    private int  maxScroll = 0;
    private IntelPlayer selected = null;
    private List<IntelPlayer> players = new ArrayList<>();

    private String  searchText  = "";
    private boolean searchFocus = false;

    public void setPlayers(List<IntelPlayer> p) {
        players = new ArrayList<>(p);
        sortPlayers();
        if (selected == null && !players.isEmpty()) selected = players.get(0);
        else if (selected != null) {
            // keep selection pointer valid
            for (IntelPlayer np : players) if (np.name.equals(selected.name)) { selected = np; break; }
        }
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();

        fillRect(0, 0, sw, sh, BG_FULL);

        int detailW = selected != null ? Math.min(sw / 3, 320) : 0;
        int listW   = sw - detailW;

        drawHeader(sw, mx, my);
        drawSearchBar(listW, mx, my);
        drawList(listW, sh, mx, my, sr);
        if (selected != null) drawDetail(listW, detailW, sh);
        drawFooter(sw, sh);

        super.drawScreen(mx, my, pt);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void drawHeader(int sw, int mx, int my) {
        fillRect(0, 0, sw, HDR, BG_HDR);
        // Bottom accent line
        fillRect(0, HDR - 1, sw, 1, 0x44E991B8);

        // Title
        gl();
        GlStateManager.pushMatrix();
        GlStateManager.translate(14, 10, 0);
        GlStateManager.scale(1.1f, 1.1f, 1f);
        mc.fontRendererObj.drawString("LOBBY INTEL", 0, 0, COL_ACCENT, false);
        GlStateManager.popMatrix();

        mc.fontRendererObj.drawString(
            players.size() + " players  \u2022  " +
            (IntelManager.getInstance().isFetching() ? "fetching\u2026" : "ready"),
            14, 26, COL_DIM, false);

        // Sort tabs - centred
        int tabW = 54, tabH = 20, tabGap = 4;
        int totalW = SORT_LABELS.length * (tabW + tabGap) - tabGap;
        int bx = sw / 2 - totalW / 2;
        for (int i = 0; i < SORT_LABELS.length; i++) {
            int by = (HDR - tabH) / 2;
            boolean active = sortMode == i;
            boolean hov = mx >= bx && mx < bx + tabW && my >= by && my < by + tabH;
            int bg = active ? 0xDD100D1C : hov ? 0x22FFFFFF : 0x0DFFFFFF;
            RoundedUtils.drawRoundedRect(bx, by, tabW, tabH, tabH / 2f, bg);
            if (active) RoundedUtils.drawRoundedOutline(bx, by, tabW, tabH, tabH / 2f, 1.2f, COL_ACCENT);
            gl();
            int tc = active ? COL_ACCENT : hov ? COL_BRIGHT : COL_MID;
            int lw = mc.fontRendererObj.getStringWidth(SORT_LABELS[i]);
            mc.fontRendererObj.drawString(SORT_LABELS[i], bx + (tabW - lw) / 2f, by + (tabH - 8) / 2f, tc, false);
            bx += tabW + tabGap;
        }

        // Refresh button - top right
        String ref = "\u21BB  Refresh";
        int rw = mc.fontRendererObj.getStringWidth(ref) + 14;
        int rx = sw - rw - 10, ry = (HDR - 16) / 2;
        boolean rHov = mx >= rx && mx < rx + rw && my >= ry && my < ry + 16;
        RoundedUtils.drawRoundedRect(rx, ry, rw, 16, 4, rHov ? 0x33FFFFFF : 0x11FFFFFF);
        if (rHov) RoundedUtils.drawRoundedOutline(rx, ry, rw, 16, 4, 1f, 0x33FFFFFF);
        gl(); mc.fontRendererObj.drawString(ref, rx + 7f, ry + 4f, rHov ? COL_BRIGHT : COL_MID, false);
    }

    // ── Search bar ────────────────────────────────────────────────────────────

    private void drawSearchBar(int lw, int mx, int my) {
        int sy = HDR;
        fillRect(0, sy, lw, SRCH, BG_SRCH);
        fillRect(0, sy + SRCH - 1, lw, 1, COL_DIVIDER);

        // Search icon + input field
        int bx = 10, by = sy + 5, bw = 220, bh = SRCH - 10;
        boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        int border = searchFocus ? COL_ACCENT : hov ? 0x55FFFFFF : 0x22FFFFFF;
        RoundedUtils.drawRoundedRect(bx, by, bw, bh, 3, 0x22000000);
        RoundedUtils.drawRoundedOutline(bx, by, bw, bh, 3, 1f, border);

        gl();
        // Search glyph
        mc.fontRendererObj.drawString("\u2315", bx + 4f, by + (bh - 8) / 2f, COL_DIM, false);

        String placeholder = "Search / add player\u2026";
        String shown = searchText.isEmpty() && !searchFocus ? placeholder : searchText;
        if (searchFocus && (System.currentTimeMillis() % 1000) < 500) shown = searchText + "|";
        int tc = searchText.isEmpty() ? COL_DIM : COL_BRIGHT;
        mc.fontRendererObj.drawString(shown, bx + 14f, by + (bh - 8) / 2f, tc, false);

        // Add button
        if (!searchText.isEmpty()) {
            int abx = bx + bw + 6;
            int abw = mc.fontRendererObj.getStringWidth("+ Add") + 12;
            boolean ahov = mx >= abx && mx < abx + abw && my >= by && my < by + bh;
            RoundedUtils.drawRoundedRect(abx, by, abw, bh, 3, ahov ? 0x55E991B8 : 0x22E991B8);
            RoundedUtils.drawRoundedOutline(abx, by, abw, bh, 3, 1f, ahov ? COL_ACCENT : 0x33E991B8);
            gl(); mc.fontRendererObj.drawString("+ Add", abx + 6f, by + (bh - 8) / 2f, ahov ? COL_ACCENT : COL_MID, false);
        }
    }

    // ── List ──────────────────────────────────────────────────────────────────

    private void drawList(int lw, int sh, int mx, int my, ScaledResolution sr) {
        int ly = HDR + SRCH;
        int lh = sh - ly - FTR;
        fillRect(0, ly, lw, lh, BG_LIST);

        // Column headers row
        int hY = ly + 4;
        fillRect(0, hY, lw, COL_HDR, 0x0AFFFFFF);
        gl();
        int nameX = CARD_PAD + HEAD_SIZE + 10;
        mc.fontRendererObj.drawString("PLAYER", nameX, hY + 5, COL_DIM, false);
        drawCentred("FKDR",   COL_FKDR_C,   hY + 5, COL_DIM);
        drawCentred("WLR",    COL_WLR_C,    hY + 5, COL_DIM);
        drawCentred("STREAK", COL_STREAK_C, hY + 5, COL_DIM);
        drawCentred("THREAT", lw - 42,      hY + 5, COL_DIM);
        fillRect(0, hY + COL_HDR - 1, lw, 1, COL_DIVIDER);

        int cY = ly + COL_HDR + 4;
        int cH = lh - COL_HDR - 4;
        int tot = Math.max(1, players.size() * (CARD_H + CARD_GAP));
        maxScroll = Math.max(0, tot - cH);
        scrollOff = Math.max(0, Math.min(scrollOff, maxScroll));

        int scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, (sr.getScaledHeight() - cY - cH) * scale, lw * scale, cH * scale);

        for (int i = 0; i < players.size(); i++) {
            int cy = cY + i * (CARD_H + CARD_GAP) - scrollOff;
            if (cy + CARD_H < cY || cy > cY + cH) continue;
            IntelPlayer p = players.get(i);
            boolean hov = mx >= CARD_PAD && mx < lw - CARD_PAD && my >= cy && my < cy + CARD_H;
            boolean sel = p == selected;
            drawCard(p, CARD_PAD, cy, lw - CARD_PAD * 2, CARD_H, hov, sel);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Scrollbar
        if (maxScroll > 0) {
            int sbH = Math.max(20, cH * cH / tot);
            int sbY = cY + (int)((float) scrollOff / maxScroll * (cH - sbH));
            fillRect(lw - 3, cY, 2, cH, 0x11FFFFFF);
            fillRect(lw - 3, sbY, 2, sbH, 0x66E991B8);
        }
    }

    private void drawCard(IntelPlayer p, int cx, int cy, int cw, int ch, boolean hov, boolean sel) {
        int bg = sel ? BG_SEL : hov ? BG_HOV : BG_CARD;
        RoundedUtils.drawRoundedRect(cx, cy, cw, ch, 5, bg);
        if (sel) RoundedUtils.drawRoundedOutline(cx, cy, cw, ch, 5, 1f, 0x66E991B8);

        int tc = threatCol((int) p.threatScore);

        // Left threat stripe
        RoundedUtils.drawRoundedRect(cx, cy + 4, 3, ch - 8, 2, tc);

        // Player head
        int headX = cx + 10, headY = cy + (ch - HEAD_SIZE) / 2;
        drawPlayerHead(p.name, headX, headY, HEAD_SIZE);

        gl();
        int nameX = cx + 10 + HEAD_SIZE + 7;

        // Name line
        int nameCol = p.cheater ? COL_RED : COL_BRIGHT;
        String namePrefix = p.cheater ? "\u26D4 " : "";
        mc.fontRendererObj.drawString(namePrefix + p.name, nameX, cy + 8, nameCol, false);

        // Level below name
        String lvlStr = p.loading ? "loading\u2026" : "\u2605 " + p.level;
        mc.fontRendererObj.drawString(lvlStr, nameX, cy + 19, p.loading ? COL_DIM : COL_GOLD, false);

        // Urchin tag badge below level
        if (p.urchinTag != null) {
            // Truncate long tag for card display
            String tag = p.urchinTag;
            if (mc.fontRendererObj.getStringWidth(tag) > 130) {
                while (tag.length() > 3 && mc.fontRendererObj.getStringWidth(tag + "\u2026") > 130)
                    tag = tag.substring(0, tag.length() - 1);
                tag += "\u2026";
            }
            int tw = mc.fontRendererObj.getStringWidth(tag) + 6;
            RoundedUtils.drawRoundedRect(nameX, cy + 30, tw, 8, 2, 0x33FF2244);
            gl(); mc.fontRendererObj.drawString(tag, nameX + 3f, cy + 31f, COL_RED, false);
        }

        // Team colour dot (top right of card)
        if (p.team != null)
            RoundedUtils.drawRoundedRect(cx + cw - 10, cy + 5, 6, 6, 3, teamCol(p.team));

        // Remove ✕ for manual players (bottom right)
        if (IntelManager.getInstance().isManual(p)) {
            int xbx = cx + cw - 12, xby = cy + ch - 12;
            boolean xhov = hov; // brightens when card is hovered
            gl(); mc.fontRendererObj.drawString("\u00D7", xbx, xby, xhov ? COL_RED : COL_REDSUB, false);
        }

        if (p.loading) {
            gl();
            drawCentred("\u2014", COL_FKDR_C,   cy + ch/2 - 4, COL_DIM);
            drawCentred("\u2014", COL_WLR_C,    cy + ch/2 - 4, COL_DIM);
            drawCentred("\u2014", COL_STREAK_C, cy + ch/2 - 4, COL_DIM);
            return;
        }

        // Stats — vertically centred in card, centred under column headers
        int sy = cy + ch / 2 - 4;
        gl();
        drawCentred(fmt(p.fkdr),                COL_FKDR_C,   sy, statCol(p.fkdr, 3, 6));
        drawCentred(fmt(p.wlr),                 COL_WLR_C,    sy, statCol(p.wlr, 1.5, 4));
        drawCentred(String.valueOf(p.winstreak), COL_STREAK_C, sy, statCol(p.winstreak, 10, 30));

        // Threat bar + score (right side, centred under THREAT header)
        int barW = 40, barH = 3;
        int bx = cx + cw - barW - 22, by = sy + 3;
        fillRect(bx, by, barW, barH, 0x22FFFFFF);
        int fw = (int)(Math.min(1f, p.threatScore / 100f) * barW);
        if (fw > 0) fillRect(bx, by, fw, barH, tc);
        String scoreStr = String.valueOf((int) p.threatScore);
        drawCentred(scoreStr, cx + cw - barW / 2 - 22, sy, tc);
    }

    // ── Detail panel ──────────────────────────────────────────────────────────

    private void drawDetail(int px, int pw, int sh) {
        int py = HDR;
        int ph = sh - HDR - FTR;
        fillRect(px, py, pw, ph, BG_DETAIL);
        // Left border accent
        fillRect(px, py, 1, ph, 0x44E991B8);

        IntelPlayer p = selected;
        if (p == null) return;

        int x = px + 16, y = py + 16;
        int innerW = pw - 32;
        int tc = threatCol((int) p.threatScore);

        // Large head
        drawPlayerHead(p.name, x, y, 36);

        // Name scaled
        gl();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 44, y + 2, 0);
        GlStateManager.scale(1.3f, 1.3f, 1f);
        mc.fontRendererObj.drawString(p.name, 0, 0, p.cheater ? COL_RED : COL_BRIGHT, false);
        GlStateManager.popMatrix();

        // Level + team
        String sub = "\u2605 " + p.level + (p.team != null ? "   [" + p.team + "]" : "");
        mc.fontRendererObj.drawString(sub, x + 44f, y + 22f, COL_GOLD, false);
        y += 48;

        // Urchin tag — wrapped lines inside a background box
        if (p.urchinTag != null) {
            List<String> tagLines = wrap("\u26D4  " + p.urchinTag, innerW - 10);
            int boxH = tagLines.size() * 11 + 6;
            RoundedUtils.drawRoundedRect(x, y, innerW, boxH, 3, 0x33FF2244);
            RoundedUtils.drawRoundedOutline(x, y, innerW, boxH, 3, 1f, 0x55FF2244);
            gl();
            int ty = y + 4;
            for (String line : tagLines) {
                mc.fontRendererObj.drawString(line, x + 5f, ty, COL_RED, false);
                ty += 11;
            }
            y += boxH + 6;
        }

        // Divider
        fillRect(x, y, innerW, 1, COL_DIVIDER); y += 8;

        // Threat bar
        gl(); mc.fontRendererObj.drawString("THREAT", x, y, COL_DIM, false);
        String scoreStr = (int) p.threatScore + " / 100";
        mc.fontRendererObj.drawString(scoreStr,
            x + innerW - mc.fontRendererObj.getStringWidth(scoreStr), y, tc, false);
        y += 10;
        fillRect(x, y, innerW, 5, 0x22FFFFFF);
        int fw = (int)(Math.min(1f, p.threatScore / 100f) * innerW);
        if (fw > 0) fillRect(x, y, fw, 5, tc);
        y += 12;

        if (p.loading) {
            gl(); mc.fontRendererObj.drawString("Fetching stats\u2026", x, y + 4, COL_DIM, false);
            return;
        }

        fillRect(x, y, innerW, 1, COL_DIVIDER); y += 8;

        // Stats rows
        y = dRow(x, y, innerW, "Final K/D",   fmt(p.fkdr),                  statCol(p.fkdr, 3, 6));
        y = dRow(x, y, innerW, "Win/Loss",     fmt(p.wlr),                   statCol(p.wlr, 1.5, 4));
        y = dRow(x, y, innerW, "Win Streak",   String.valueOf(p.winstreak),   statCol(p.winstreak, 10, 30));
        y = dRow(x, y, innerW, "Final Kills",  String.valueOf(p.finalKills),  COL_BRIGHT);
        y = dRow(x, y, innerW, "Beds Broken",  String.valueOf(p.bedsBroken),  COL_BRIGHT);
        y = dRow(x, y, innerW, "Total Wins",   String.valueOf(p.wins),        COL_BRIGHT);

        y += 4; fillRect(x, y, innerW, 1, COL_DIVIDER); y += 8;

        // Intel recommendation
        gl(); mc.fontRendererObj.drawString("INTEL", x, y, COL_DIM, false); y += 12;
        for (String line : wrap(recommend(p), innerW)) {
            gl(); mc.fontRendererObj.drawString(line, x, y, COL_MID, false); y += 11;
        }
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private void drawFooter(int sw, int sh) {
        int fy = sh - FTR;
        fillRect(0, fy, sw, FTR, BG_HDR);
        fillRect(0, fy, sw, 1, COL_DIVIDER);
        gl();
        String hint = "ESC  close    SCROLL  navigate    CLICK  inspect    R  refresh    ENTER  add player";
        mc.fontRendererObj.drawString(hint, 12, fy + (FTR - 8) / 2f, COL_DIM, false);
    }

    // ── Player head ───────────────────────────────────────────────────────────

    private void drawPlayerHead(String name, int x, int y, int size) {
        try {
            ResourceLocation skin = null;

            // Try live network player first (fastest, always works for lobby players)
            if (mc.getNetHandler() != null) {
                NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(name);
                if (info != null) skin = info.getLocationSkin();
            }

            // For manually-added / non-lobby players: use cached UUID to load skin
            if (skin == null) {
                String uuid = IntelManager.getInstance().getCachedUuid(name);
                if (uuid != null) {
                    try {
                        com.mojang.authlib.GameProfile gp = new com.mojang.authlib.GameProfile(
                                java.util.UUID.fromString(uuid), name);
                        // Trigger async skin load and get whatever is cached so far
                        net.minecraft.client.resources.SkinManager sm =
                                mc.getSkinManager();
                        skin = sm.loadSkinFromCache(gp);
                    } catch (Exception ignored2) {}
                }
            }

            // Final fallback: Steve
            if (skin == null) skin = new ResourceLocation("textures/entity/steve.png");

            GlStateManager.enableBlend();
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.getTextureManager().bindTexture(skin);
            Gui.drawScaledCustomSizeModalRect(x, y, 8f, 8f, 8, 8, size, size, 64f, 64f);
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
        int detailW = selected != null ? Math.min(sw / 3, 320) : 0;
        int listW   = sw - detailW;

        // Sort tabs
        int tabW = 54, tabH = 20, tabGap = 4;
        int totalW = SORT_LABELS.length * (tabW + tabGap) - tabGap;
        int bx = sw / 2 - totalW / 2;
        for (int i = 0; i < SORT_LABELS.length; i++) {
            int by = (HDR - tabH) / 2;
            if (mx >= bx && mx < bx + tabW && my >= by && my < by + tabH) {
                sortMode = i; sortPlayers(); return;
            }
            bx += tabW + tabGap;
        }

        // Refresh button
        String ref = "\u21BB  Refresh";
        int rw = mc.fontRendererObj.getStringWidth(ref) + 14;
        int rx = sw - rw - 10, ry = (HDR - 16) / 2;
        if (mx >= rx && mx < rx + rw && my >= ry && my < ry + 16) {
            IntelManager.getInstance().refresh(); return;
        }

        // Search bar focus
        int sbx = 10, sby = HDR + 5, sbw = 220, sbh = SRCH - 10;
        searchFocus = mx >= sbx && mx < sbx + sbw && my >= sby && my < sby + sbh;

        // Add button
        if (!searchText.isEmpty()) {
            int abx = sbx + sbw + 6, abw = mc.fontRendererObj.getStringWidth("+ Add") + 12;
            if (mx >= abx && mx < abx + abw && my >= sby && my < sby + sbh) {
                IntelManager.getInstance().addManualPlayer(searchText.trim());
                searchText = ""; return;
            }
        }

        // Cards
        int cY = HDR + SRCH + COL_HDR + 4;
        for (int i = 0; i < players.size(); i++) {
            int cy = cY + i * (CARD_H + CARD_GAP) - scrollOff;
            if (my < cy || my >= cy + CARD_H || mx < CARD_PAD || mx >= listW - CARD_PAD) continue;
            IntelPlayer p = players.get(i);
            int cw = listW - CARD_PAD * 2;
            // X button (manual)
            if (IntelManager.getInstance().isManual(p)) {
                int xbx = CARD_PAD + cw - 12;
                int xby = cy + CARD_H - 12;
                if (mx >= xbx && mx < xbx + 10 && my >= xby && my < xby + 10) {
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
            if (key == 14) { if (!searchText.isEmpty()) searchText = searchText.substring(0, searchText.length()-1); return; }
            if (key == 28) { if (!searchText.isEmpty()) { IntelManager.getInstance().addManualPlayer(searchText.trim()); searchText = ""; } return; }
            if (key == 1)  { searchFocus = false; return; }
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') { if (searchText.length() < 16) searchText += c; }
            return;
        }
        if (key == 19) { IntelManager.getInstance().refresh(); return; }
        if (key == 1)  { mc.displayGuiScreen(null); return; }
        super.keyTyped(c, key);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sortPlayers() {
        switch (sortMode) {
            case 0: players.sort((a,b) -> Double.compare(b.threatScore, a.threatScore)); break;
            case 1: players.sort((a,b) -> Double.compare(b.fkdr, a.fkdr));              break;
            case 2: players.sort((a,b) -> Double.compare(b.wlr, a.wlr));                break;
            case 3: players.sort((a,b) -> Integer.compare(b.winstreak, a.winstreak));   break;
            case 4: players.sort(Comparator.comparing(p -> p.name));                     break;
        }
    }

    private int dRow(int x, int y, int innerW, String label, String val, int col) {
        gl();
        mc.fontRendererObj.drawString(label, x, y, COL_DIM, false);
        mc.fontRendererObj.drawString(val, x + innerW - mc.fontRendererObj.getStringWidth(val), y, col, false);
        return y + 13;
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
        if (v < 0) return "\u2014";
        return v == (long) v ? String.valueOf((long) v) : String.format("%.2f", v);
    }

    // Returns 0=low, 1=medium, 2=high threat based on cheat type
    private int urchinThreatLevel(String tag) {
        // Low threat — cosmetic / minor advantage cheats
        if (containsAny(tag, "legitscaff", "eagle", "autoclicker", "autoclick", "cps",
                        "info", "caution", "possible_sniper", "legit_sniper"))
            return 0;
        // Medium threat — moderate PvP advantage
        if (containsAny(tag, "aim assist", "aimassist", "killaura", "kill aura",
                        "reach", "velocity", "anti-kb", "antikb", "sniper", "confirmed"))
            return 1;
        // High threat — blatant / game-breaking cheats
        if (containsAny(tag, "blatant", "scaffold", "bridg", "autoblock", "auto block",
                        "fly", "speed", "bhop", "movement", "esp", "visual", "xray",
                        "x-ray", "wallhack", "aimbot"))
            return 2;
        return 1; // default medium
    }

    private String recommend(IntelPlayer p) {
        if (p.cheater && p.urchinTag != null) {
            String tag = p.urchinTag.toLowerCase();
            int tl = urchinThreatLevel(tag);

            // Scaffold / bridge cheats
            if (containsAny(tag, "scaffold", "bridg", "blatant scaffold")) {
                if (tl == 2)
                    return "HIGH THREAT | Blatant scaffolder. Expect near-instant bridges everywhere. Rush their bed immediately before they cross.";
                return "LOW THREAT | Uses legitscaff or eagle. Bridges slightly faster than normal. Contest mid early and cut off routes.";
            }
            // Autoclicker
            if (containsAny(tag, "autoclick", "autoclicker", "cps")) {
                return "LOW THREAT | Higher CPS but no aim advantage. Play defensively, use knockback and gap the fight.";
            }
            // Autoblock
            if (containsAny(tag, "autoblock", "auto block")) {
                return "HIGH THREAT | Autoblock gives near-perfect blocking. Avoid extended fights. Use projectiles and rush bed instead.";
            }
            // Eagle
            if (containsAny(tag, "eagle")) {
                return "LOW THREAT | Eagle bridges are faster but not instant. Play normal — cut their bridge and keep your bed defended.";
            }
            // KillAura / Aim
            if (containsAny(tag, "killaura", "kill aura", "aimbot")) {
                return "MEDIUM THREAT | KillAura or aimbot. Avoid 1v1s in open space. Use terrain, walls, and bed rush tactics instead.";
            }
            if (containsAny(tag, "aim assist", "aimassist")) {
                return "MEDIUM THREAT | Aim assist improves accuracy but isn't fully automated. You can still outmanoeuvre them.";
            }
            // Reach
            if (containsAny(tag, "reach")) {
                return "MEDIUM THREAT | Extended reach means they win trades at distance. Stay at melee range or use a bow.";
            }
            // Anti-KB
            if (containsAny(tag, "velocity", "anti-kb", "antikb")) {
                return "MEDIUM THREAT | Reduces knockback — void plays won't work. Focus on bed destruction rather than PvP.";
            }
            // Movement
            if (containsAny(tag, "fly", "bhop", "bunnyhop", "speed")) {
                return "HIGH THREAT | Movement hacks mean they can rush your base instantly. Fortify your bed and play defensive.";
            }
            // ESP / Visuals
            if (containsAny(tag, "esp", "visual", "xray", "x-ray", "wallhack")) {
                return "HIGH THREAT | Visuals / ESP — they can see through walls and track you. Cover your bed on all sides.";
            }
            // Sniper tags
            if (containsAny(tag, "sniper", "crossbow")) {
                return "MEDIUM THREAT | Known sniper — avoid exposed areas and open bridges. Use covered tunnels where possible.";
            }
            // Generic by threat level
            if (tl == 2) return "HIGH THREAT | Blatant cheater. Do not engage directly. Rush bed and escape.";
            if (tl == 0) return "LOW THREAT | Minor cheat advantage. Play normally with extra awareness.";
            return "MEDIUM THREAT | Cheating detected. Avoid prolonged fights. Focus bed destruction.";
        }
        if (p.threatScore >= 75) return "High priority target. Rush their bed early before they gear up. Avoid 1v1 without advantage.";
        if (p.threatScore >= 50) return "Solid player. Contest mid early and engage only with armor advantage.";
        if (p.threatScore >= 25) return "Average player. Farm resources first. Safe to deprioritise until mid-game.";
        return "Low threat. Easy target — rush early for free resources and bed elimination.";
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

    private void drawCentred(String text, int centreX, int y, int color) {
        int w = mc.fontRendererObj.getStringWidth(text);
        gl(); mc.fontRendererObj.drawString(text, centreX - w / 2f, y, color, false);
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
