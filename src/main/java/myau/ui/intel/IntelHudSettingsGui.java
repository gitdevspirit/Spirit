package myau.ui.intel;

import myau.Myau;
import myau.module.modules.LobbyIntel;
import myau.ui.clickgui.GuiColors;
import myau.ui.clickgui.RoundedUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

public class IntelHudSettingsGui extends GuiScreen {

    private final IntelHudOverlay hudOverlay;
    private final GuiScreen parent;

    private static final int POPUP_W  = 280;
    private static final int POPUP_H  = 520;
    private static final int PAD      = 12;
    private static final int SLIDER_H = 28;
    private static final int TOGGLE_H = 24;

    private static final int BG_POPUP  = 0xEE0A0A12;
    private static final int BG_HEADER = 0xFF15151D;
    private static final int ACCENT    = GuiColors.ACCENT;
    private static final int TEXT_ON   = 0xFFDDDDEE;
    private static final int TEXT_DIM  = 0xFF888899;
    private static final int DIVIDER   = 0x33FFFFFF;

    // Slider IDs
    private static final int S_POS_X  = 0;
    private static final int S_POS_Y  = 1;
    private static final int S_SCALE  = 2;
    private static final int S_MAX_P  = 3;
    private static final int S_BG_OP  = 4;
    private static final int S_BORDER = 5;

    private int  scrollOffset = 0;
    private int  maxScroll    = 0;
    private boolean dragging  = false;
    private int  dragId       = -1;
    private int  dragBarX     = 0;
    private int  dragBarW     = 0;

    public IntelHudSettingsGui(IntelHudOverlay hudOverlay, GuiScreen parent) {
        this.hudOverlay = hudOverlay;
        this.parent = parent;
    }

    // ── Slider math — always integer, 1 step per pixel when range fits ────────

    private int barW(int iw, int max) {
        // Reserve space for the value label on the right
        return iw - mc.fontRendererObj.getStringWidth(String.valueOf(max)) - 8;
    }

    private int pixelToVal(int mx, int bx, int bw, int min, int max) {
        float pct = Math.max(0f, Math.min(1f, (mx - bx) / (float) bw));
        return min + Math.round(pct * (max - min));
    }

    private void applySlider(int id, int mx, int bx, int bw) {
        switch (id) {
            case S_POS_X:  hudOverlay.setPosition(pixelToVal(mx,bx,bw,0,1920), hudOverlay.getPosY()); break;
            case S_POS_Y:  hudOverlay.setPosition(hudOverlay.getPosX(), pixelToVal(mx,bx,bw,0,1080)); break;
            case S_SCALE:  hudOverlay.setScale(pixelToVal(mx,bx,bw,50,200) / 100f); break;
            case S_MAX_P:  hudOverlay.setMaxPlayers(pixelToVal(mx,bx,bw,1,20)); break;
            case S_BG_OP:  hudOverlay.setBgOpacity(pixelToVal(mx,bx,bw,0,255)); break;
            case S_BORDER: hudOverlay.setBorderOpacity(pixelToVal(mx,bx,bw,0,255)); break;
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawRect(0, 0, width, height, 0xCC000000);

        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
        int px = (sw - POPUP_W) / 2, py = (sh - POPUP_H) / 2;

        RoundedUtils.drawRoundedRect(px, py, POPUP_W, POPUP_H, 6, BG_POPUP);
        RoundedUtils.drawRoundedRect(px, py, POPUP_W, 36, 6, BG_HEADER);
        drawRect(px, py + 30, px + POPUP_W, py + 36, BG_HEADER);

        GlStateManager.pushMatrix();
        GlStateManager.translate(px + PAD, py + 12, 0);
        GlStateManager.scale(1.1f, 1.1f, 1f);
        mc.fontRendererObj.drawString("HUD Overlay Settings", 0, 0, ACCENT, false);
        GlStateManager.popMatrix();

        int cx = px + POPUP_W - 28, cy = py + 10;
        boolean ch = mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18;
        mc.fontRendererObj.drawString("X", cx + 5, cy + 5, ch ? 0xFFFF4444 : TEXT_ON, false);

        // Scrollable region
        GlStateManager.pushMatrix();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        int sf = sr.getScaleFactor();
        int contentY = py + 46, contentH = POPUP_H - 56;
        GL11.glScissor(px * sf, (sh - contentY - contentH) * sf, POPUP_W * sf, contentH * sf);

        int y  = contentY - scrollOffset + PAD;
        int ix = px + PAD;
        int iw = POPUP_W - PAD * 2;

        y = drawToggle(ix, y, iw, "Enabled", hudOverlay.isEnabled(), mx, my);
        y += 6; div(ix, y, iw); y += 12;

        label(ix, y, "POSITION"); y += 16;
        y = slider(ix, y, iw, "X Position",         hudOverlay.getPosX(),              0,   1920, S_POS_X,  mx, my);
        y = slider(ix, y, iw, "Y Position",         hudOverlay.getPosY(),              0,   1080, S_POS_Y,  mx, my);
        y += 6; div(ix, y, iw); y += 12;

        label(ix, y, "DISPLAY"); y += 16;
        y = slider(ix, y, iw, "Scale %",            (int)(hudOverlay.getScale()*100),  50,  200,  S_SCALE,  mx, my);
        y = slider(ix, y, iw, "Max Players",        hudOverlay.getMaxPlayers(),         1,   20,   S_MAX_P,  mx, my);
        y = slider(ix, y, iw, "Background Opacity", hudOverlay.getBgOpacity(),          0,   255,  S_BG_OP,  mx, my);
        y = slider(ix, y, iw, "Border Opacity",     hudOverlay.getBorderOpacity(),      0,   255,  S_BORDER, mx, my);
        y += 6; div(ix, y, iw); y += 12;

        label(ix, y, "COLUMNS"); y += 20;
        y = drawToggle(ix, y, iw, "Player Heads",  hudOverlay.getShowHeads(),     mx, my);
        y = drawToggle(ix, y, iw, "Star",          hudOverlay.getShowStar(),      mx, my);
        y = drawToggle(ix, y, iw, "FKDR",          hudOverlay.getShowFkdr(),      mx, my);
        y = drawToggle(ix, y, iw, "WLR",           hudOverlay.getShowWlr(),       mx, my);
        y = drawToggle(ix, y, iw, "Winstreak",     hudOverlay.getShowStreak(),    mx, my);
        y = drawToggle(ix, y, iw, "Tags",          hudOverlay.getShowUrchin(),    mx, my);
        y = drawToggle(ix, y, iw, "Threat Score",  hudOverlay.getShowThreat(),    mx, my);
        y = drawToggle(ix, y, iw, "Team Colors",   hudOverlay.getShowTeamColor(), mx, my);
        y += 8; div(ix, y, iw); y += 16;

        label(ix, y, "SORTING"); y += 20;
        y = dropdown(ix, y, iw, "Sort By",
                new String[]{"Threat", "FKDR", "Name"},
                hudOverlay.getSortMode().equals("threat") ? 0 : hudOverlay.getSortMode().equals("fkdr") ? 1 : 2,
                mx, my);

        // Add extra bottom padding so the last item is fully scrollable into view
        int totalContentH = y - contentY + scrollOffset + PAD * 4;
        maxScroll = Math.max(0, totalContentH - contentH);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.popMatrix();

        super.drawScreen(mx, my, pt);
    }

    // ── Widget helpers ────────────────────────────────────────────────────────

    private void label(int x, int y, String t) {
        mc.fontRendererObj.drawString(t, x, y, TEXT_DIM, false);
    }

    private void div(int x, int y, int w) {
        drawRect(x, y, x + w, y + 1, DIVIDER);
    }

    private int slider(int x, int y, int w, String lbl, int val, int min, int max,
                       int id, int mx, int my) {
        mc.fontRendererObj.drawString(lbl, x, y, TEXT_ON, false);
        y += 10;

        String vs = String.valueOf(val);
        mc.fontRendererObj.drawString(vs, x + w - mc.fontRendererObj.getStringWidth(vs), y, ACCENT, false);

        int bw = barW(w, max);
        int bh = 8;
        drawRect(x, y, x + bw, y + bh, 0x66444444);
        float pct = (val - min) / (float)(max - min);
        int fw = Math.max(2, (int)(bw * pct));
        drawRect(x, y, x + fw, y + bh, ACCENT);
        // Knob
        int kx = x + fw - 5, ky = y - 2;
        drawRect(kx, ky, kx + 10, ky + 12, 0xFFFFFFFF);
        drawRect(kx + 2, ky + 2, kx + 8, ky + 10, ACCENT);

        return y + 20;
    }

    private int drawToggle(int x, int y, int w, String lbl, boolean val, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + TOGGLE_H;
        mc.fontRendererObj.drawString(lbl, x, y + 8, hov ? TEXT_ON : TEXT_DIM, false);
        int tw = 40, th = 20, tx = x + w - tw, ty = y + 4;
        RoundedUtils.drawRoundedRect(tx, ty, tw, th, th / 2, val ? (ACCENT & 0x00FFFFFF) | 0x44000000 : 0x33444455);
        int ks = 16, kx = val ? tx + tw - ks - 2 : tx + 2;
        RoundedUtils.drawRoundedRect(kx, ty + 2, ks, ks, ks / 2, val ? ACCENT : 0xFF666677);
        return y + TOGGLE_H;
    }

    private int dropdown(int x, int y, int w, String lbl, String[] opts, int sel, int mx, int my) {
        mc.fontRendererObj.drawString(lbl, x, y, TEXT_DIM, false);
        y += 12;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 24;
        RoundedUtils.drawRoundedRect(x, y, w, 24, 4, hov ? 0x44FFFFFF : 0x22FFFFFF);
        mc.fontRendererObj.drawString(opts[sel], x + 12, y + 8, TEXT_ON, false);
        mc.fontRendererObj.drawString("v", x + w - 20, y + 8, TEXT_DIM, false);
        return y + 32;
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
        int px = (sw - POPUP_W) / 2, py = (sh - POPUP_H) / 2;

        int cx = px + POPUP_W - 28, cy = py + 10;
        if (mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18) { saveAndClose(); return; }
        if (mx < px || mx > px + POPUP_W || my < py || my > py + POPUP_H) { saveAndClose(); return; }

        int y  = py + 46 - scrollOffset + PAD;
        int ix = px + PAD;
        int iw = POPUP_W - PAD * 2;

        // Enabled toggle
        if (hit(mx, my, ix, y, iw, TOGGLE_H)) { hudOverlay.setEnabled(!hudOverlay.isEnabled()); return; }
        y += TOGGLE_H + 6 + 12 + 16;

        // Position sliders
        if (tryDrag(mx, my, ix, iw, y, S_POS_X, 1920)) return; y += SLIDER_H;
        if (tryDrag(mx, my, ix, iw, y, S_POS_Y, 1080)) return; y += SLIDER_H;
        y += 6 + 12 + 16;

        // Display sliders
        if (tryDrag(mx, my, ix, iw, y, S_SCALE,  200)) return; y += SLIDER_H;
        if (tryDrag(mx, my, ix, iw, y, S_MAX_P,   20)) return; y += SLIDER_H;
        if (tryDrag(mx, my, ix, iw, y, S_BG_OP,  255)) return; y += SLIDER_H;
        if (tryDrag(mx, my, ix, iw, y, S_BORDER, 255)) return; y += SLIDER_H;
        y += 6 + 12 + 16 + 20;

        // Column toggles
        if (hit(mx,my,ix,y,iw,TOGGLE_H)){ hudOverlay.setShowHeads(!hudOverlay.getShowHeads());         return; } y+=TOGGLE_H;
        if (hit(mx,my,ix,y,iw,TOGGLE_H)){ hudOverlay.setShowStar(!hudOverlay.getShowStar());           return; } y+=TOGGLE_H;
        if (hit(mx,my,ix,y,iw,TOGGLE_H)){ hudOverlay.setShowFkdr(!hudOverlay.getShowFkdr());           return; } y+=TOGGLE_H;
        if (hit(mx,my,ix,y,iw,TOGGLE_H)){ hudOverlay.setShowWlr(!hudOverlay.getShowWlr());             return; } y+=TOGGLE_H;
        if (hit(mx,my,ix,y,iw,TOGGLE_H)){ hudOverlay.setShowStreak(!hudOverlay.getShowStreak());       return; } y+=TOGGLE_H;
        if (hit(mx,my,ix,y,iw,TOGGLE_H)){ hudOverlay.setShowUrchin(!hudOverlay.getShowUrchin());       return; } y+=TOGGLE_H;
        if (hit(mx,my,ix,y,iw,TOGGLE_H)){ hudOverlay.setShowThreat(!hudOverlay.getShowThreat());       return; } y+=TOGGLE_H;
        if (hit(mx,my,ix,y,iw,TOGGLE_H)){ hudOverlay.setShowTeamColor(!hudOverlay.getShowTeamColor()); return; } y+=TOGGLE_H+8+16+20;

        // Sort dropdown
        if (hit(mx, my, ix, y + 12, iw, 24)) {
            String m = hudOverlay.getSortMode();
            hudOverlay.setSortMode(m.equals("threat") ? "fkdr" : m.equals("fkdr") ? "name" : "threat");
        }

        super.mouseClicked(mx, my, btn);
    }

    /** Returns true and starts dragging if the mouse is on this slider's bar. */
    private boolean tryDrag(int mx, int my, int ix, int iw, int y, int id, int max) {
        int bx = ix, bw = barW(iw, max);
        // bar is at y+10, height 8
        if (mx >= bx && mx <= bx + bw && my >= y + 10 && my < y + 18) {
            applySlider(id, mx, bx, bw);
            dragging  = true;
            dragId    = id;
            dragBarX  = bx;
            dragBarW  = bw;
            return true;
        }
        return false;
    }

    private boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        dragging = false; dragId = -1;
        super.mouseReleased(mx, my, state);
    }

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long t) {
        if (dragging && dragId >= 0) applySlider(dragId, mx, dragBarX, dragBarW);
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
    public boolean doesGuiPauseGame() { return false; }

    private void saveAndClose() {
        LobbyIntel li = (LobbyIntel) Myau.moduleManager.getModule("LobbyIntel");
        if (li != null) li.saveHudSettings();
        mc.displayGuiScreen(parent);
    }
}
