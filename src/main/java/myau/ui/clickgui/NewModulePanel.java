package myau.ui.clickgui;

import myau.Myau;
import myau.module.*;
import myau.module.modules.HUD;
import myau.module.modules.Pit;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NewModulePanel {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int CARD_W   = 130;
    private static final int CARD_H   = 22;
    private static final int CARD_GAP = 6;
    private static final int SET_ROW  = 22;

    private int areaX, areaY, areaW, areaH;
    private List<Module> modules;
    private Module expandedModule = null;

    private final Map<Module, Float> toggleAnim = new HashMap<>();
    private int scrollOffset = 0;

    private final Set<Setting> collapsedHeaders = new HashSet<>();
    private SliderSetting  draggingSlider;
    private int            sliderBarX, sliderBarW;
    private KeybindSetting listeningKeybind;

    public boolean isListeningForKey() { return listeningKeybind != null; }

    public NewModulePanel(List<Module> modules) { this.modules = modules; collapseDefaults(); }

    public void setModules(List<Module> m) { modules = m; expandedModule = null; scrollOffset = 0; collapseDefaults(); }
    public void setVisibleArea(int x, int y, int w, int h) { areaX=x; areaY=y; areaW=w; areaH=h; }

    private int cols() { return Math.max(1, areaW / (CARD_W + CARD_GAP)); }

    // ── Default collapse: collapse all Pit submodule headers by default ─────
    private void collapseDefaults() {
        Pit pit = (Pit) Myau.moduleManager.getModule(Pit.class);
        if (pit == null) return;
        for (Setting s : pit.getSettings()) {
            if (s instanceof BooleanSetting && !s.getName().startsWith(" ")) {
                collapsedHeaders.add(s);
            }
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────
    public void render(int mx, int my, String search) {
        List<Module> list = filtered(search);
        int c = cols();
        int totalW = c * CARD_W + (c - 1) * CARD_GAP;
        int sx = areaX + (areaW - totalW) / 2;

        ScissorUtil.enable(areaX, areaY, areaW, areaH);

        // Render column by column so expansion only pushes down within its own column
        for (int col = 0; col < c; col++) {
            int x = sx + col * (CARD_W + CARD_GAP);
            int y = areaY - scrollOffset;

            for (int i = col; i < list.size(); i += c) {
                Module m = list.get(i);
                renderCard(m, x, y, mx, my);
                y += CARD_H + CARD_GAP;

                if (expandedModule == m) {
                    int sh = settingsH(m);
                    renderSettingsInline(m, x, y - CARD_GAP, mx, my);
                    y += sh + CARD_GAP;
                }
            }
        }

        ScissorUtil.disable();
    }

    private void renderCard(Module m, int x, int y, int mx, int my) {
        // ── NULL GUARD ────────────────────────────────────────────────────────
        if (m == null || m.getName() == null) {
            System.out.println("[Rise] Skipping null module or null name in renderCard: " + m);
            return;
        }

        boolean en  = m.isEnabled();
        boolean hov = mx >= x && mx <= x + CARD_W && my >= y && my <= y + CARD_H;

        // Animate toward current state
        float cur = toggleAnim.getOrDefault(m, en ? 1f : 0f);
        float target = en ? 1f : 0f;
        float ta = cur + (target - cur) * 0.2f;
        toggleAnim.put(m, ta);

        // Background — pink tint if on, ghost if off
        int bg = en ? blend(0x44E991B8, 0x77E991B8, ta) : (hov ? 0x22FFFFFF : 0x14FFFFFF);
        RoundedUtils.drawRoundedRect(x, y, CARD_W, CARD_H, CARD_H / 2, bg);

        // Pill border — pink when on, white when off
        int borderAlpha = en ? (int)(0x88 + 0x77 * ta) : (hov ? 0x88 : 0x55);
        int borderColor = en
            ? ((borderAlpha << 24) | (GuiColors.ACCENT & 0x00FFFFFF))
            : ((borderAlpha << 24) | 0xFFFFFF);
        RoundedUtils.drawRoundedOutline((float)x, (float)y, (float)CARD_W, (float)CARD_H, CARD_H / 2f, 1.2f, borderColor);

        // Status dot
        RoundedUtils.drawRoundedRect(x + 7, y + CARD_H / 2 - 3, 6, 6, 3,
                en ? GuiColors.ACCENT : 0xFF383838);

        // Name — pink=on, grey=off. Never white.
        gl();
        int nc = en ? GuiColors.ACCENT : (hov ? 0xFF888888 : 0xFF555555);
        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.fontRendererObj.drawString(m.getName(), x + 18, y + (CARD_H - 8) / 2, nc, false);

        // Expand arrow
        if (!m.getSettings().isEmpty()) {
            gl();
            boolean exp = expandedModule == m;
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.fontRendererObj.drawString(exp ? "v" : ">",
                    x + CARD_W - 10, y + (CARD_H - 8) / 2,
                    exp ? GuiColors.ACCENT : 0xFF484848, false);
        }
    }

    private void renderSettingsInline(Module m, int x, int y, int mx, int my) {
        int w  = CARD_W;
        int sh = settingsH(m);

        RoundedUtils.drawRoundedRect(x, y, w, sh, 6, 0xDD0C0C0C);
        // Pink left accent bar
        drawRect(x + 2, y + 4, x + 3, y + sh - 4, GuiColors.ACCENT_DIM);

        int oy = y + 5;
        boolean skipUntilNextHeader = false;
        for (Setting s : m.getSettings()) {
            if (s == null || s.getName() == null) continue;
            if (!s.isVisible()) continue;
            // A "header" is a BooleanSetting whose name does NOT start with spaces
            boolean isHeader = s instanceof BooleanSetting && !s.getName().startsWith(" ");
            if (isHeader) skipUntilNextHeader = collapsedHeaders.contains(s);
            if (!isHeader && skipUntilNextHeader) continue;
            gl();
            if (s instanceof SliderSetting) {
                SliderSetting sl = (SliderSetting) s;
                mc.fontRendererObj.drawString(s.getName(), x + 8, oy + 2, 0xFF777777);
                String val = fmtVal(sl.getValue());
                mc.fontRendererObj.drawString(val,
                        x + w - mc.fontRendererObj.getStringWidth(val) - 7, oy + 2, GuiColors.ACCENT);
                int bx = x + 8, by = oy + 13, bw = w - 16;
                drawRect(bx, by, bx + bw, by + 2, 0xFF222222);
                int fill = Math.max(2, (int)(bw * sl.getPercent()));
                drawRect(bx, by, bx + fill, by + 2, GuiColors.ACCENT);
                RoundedUtils.drawRoundedRect(bx + fill - 3, by - 2, 6, 6, 3, 0xFFEEEEEE);
                if (draggingSlider == sl) { sliderBarX = bx; sliderBarW = bw; }
                oy += SET_ROW;
            } else if (s instanceof DropdownSetting) {
                DropdownSetting dd = (DropdownSetting) s;
                mc.fontRendererObj.drawString(s.getName(), x + 8, oy + 2, 0xFF777777);
                String v = "< " + dd.getValue() + " >";
                mc.fontRendererObj.drawString(v,
                        x + w - mc.fontRendererObj.getStringWidth(v) - 7, oy + 2, GuiColors.ACCENT);
                oy += 16;
            } else if (s instanceof BooleanSetting) {
                BooleanSetting bs = (BooleanSetting) s;
                boolean hdr = !s.getName().startsWith(" ");
                boolean col = collapsedHeaders.contains(s);
                int nameColor = hdr ? (bs.getValue() ? GuiColors.ACCENT : 0xFFDDDDDD) : 0xFF666666;
                if (hdr) {
                    mc.fontRendererObj.drawStringWithShadow(s.getName(), x + 8, oy + 2, nameColor);
                } else {
                    mc.fontRendererObj.drawString(s.getName(), x + 8, oy + 2, nameColor);
                }
                if (hdr) {
                    // Show collapse arrow on right
                    String arrow = col ? ">" : "v";
                    mc.fontRendererObj.drawString(arrow, x + w - 10, oy + 2, 0xFF555555);
                } else {
                    // Checkbox box
                    int cbX = x + w - 14, cbY = oy + 2, cbS = 9;
                    drawRect(cbX, cbY, cbX + cbS, cbY + cbS, bs.getValue() ? GuiColors.ACCENT : 0xFF2A2A2A);
                    drawRect(cbX, cbY, cbX + cbS, cbY + 1, bs.getValue() ? 0xFFFFFFFF : 0xFF444444);
                    drawRect(cbX, cbY + cbS - 1, cbX + cbS, cbY + cbS, bs.getValue() ? 0xFFFFFFFF : 0xFF444444);
                    drawRect(cbX, cbY, cbX + 1, cbY + cbS, bs.getValue() ? 0xFFFFFFFF : 0xFF444444);
                    drawRect(cbX + cbS - 1, cbY, cbX + cbS, cbY + cbS, bs.getValue() ? 0xFFFFFFFF : 0xFF444444);
                    if (bs.getValue()) {
                        gl();
                        mc.fontRendererObj.drawString("\u2713", cbX + 1, cbY + 1, 0xFF1A0D12);
                    }
                }
                oy += 16;
            } else if (s instanceof KeybindSetting) {
                KeybindSetting kb = (KeybindSetting) s;
                mc.fontRendererObj.drawString(s.getName(), x + 8, oy + 2, 0xFF777777);
                boolean lstn = listeningKeybind == kb;
                String lbl = lstn ? "[ ... ]" : "[ " + kb.getDisplayName() + " ]";
                mc.fontRendererObj.drawString(lbl,
                        x + w - mc.fontRendererObj.getStringWidth(lbl) - 7, oy + 2,
                        lstn ? 0xFFEEEEEE : GuiColors.ACCENT);
                oy += 16;
            }
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────
    public void mouseClicked(int mx, int my, int button) {
        List<Module> list = filtered(null);
        int c = cols();
        int totalW = c * CARD_W + (c - 1) * CARD_GAP;
        int sx = areaX + (areaW - totalW) / 2;

        for (int col = 0; col < c; col++) {
            int x = sx + col * (CARD_W + CARD_GAP);
            int y = areaY - scrollOffset;

            for (int i = col; i < list.size(); i += c) {
                Module m = list.get(i);
                if (m == null) { y += CARD_H + CARD_GAP; continue; }

                // Card hit
                if (mx >= x && mx <= x + CARD_W && my >= y && my <= y + CARD_H) {
                    if (button == 0) m.toggle();
                    else if (button == 1 && !m.getSettings().isEmpty())
                        expandedModule = expandedModule == m ? null : m;
                    return;
                }
                y += CARD_H + CARD_GAP;

                // Settings hit
                if (expandedModule == m) {
                    int sh = settingsH(m);
                    int settingsY = y - CARD_GAP + 2;
                    if (mx >= x && mx <= x + CARD_W && my >= settingsY && my <= settingsY + sh) {
                        settingClick(m, x, settingsY, CARD_W, mx, my, button);
                        return;
                    }
                    y += sh + CARD_GAP;
                }
            }
        }
    }

    private void settingClick(Module m, int x, int y, int w, int mx, int my, int button) {
        int oy = y + 5;
        boolean skipClick = false;
        for (Setting s : m.getSettings()) {
            if (s == null || s.getName() == null) continue;
            if (!s.isVisible()) continue;
            boolean isHeaderClick = s instanceof BooleanSetting && !s.getName().startsWith(" ");
            if (isHeaderClick) skipClick = collapsedHeaders.contains(s);
            if (!isHeaderClick && skipClick) continue;
            if (s instanceof SliderSetting) {
                SliderSetting sl = (SliderSetting) s;
                int bx = x + 8, by = oy + 13, bw = w - 16;
                if (button == 0 && mx >= bx && mx <= bx + bw && my >= by - 3 && my <= by + 7) {
                    draggingSlider = sl; sliderBarX = bx; sliderBarW = bw; updateSlider(mx);
                }
                oy += SET_ROW;
            } else if (s instanceof DropdownSetting) {
                if (my >= oy && my <= oy + 16) {
                    if (button == 0) ((DropdownSetting)s).next();
                    else if (button == 1) ((DropdownSetting)s).prev();
                }
                oy += 16;
            } else if (s instanceof BooleanSetting) {
                boolean hdr = !s.getName().startsWith(" ");
                if (my >= oy && my <= oy + 16) {
                    if (button == 1 && hdr) {
                        if (collapsedHeaders.contains(s)) collapsedHeaders.remove(s);
                        else collapsedHeaders.add(s);
                        Myau.moduleManager.playSound();
                        return;
                    } else if (button == 0 && !hdr) {
                        ((BooleanSetting)s).toggle();
                    } else if (button == 0 && hdr) {
                        ((BooleanSetting)s).toggle();
                        Myau.moduleManager.playSound();
                        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
                        if (hud != null && hud.toggleAlerts.getValue()) {
                            String status = ((BooleanSetting)s).getValue() ? "&a&lON" : "&c&lOFF";
                            ChatUtil.sendFormatted(String.format("%s%s: %s&r", Myau.clientName, s.getName(), status));
                        }
                    }
                }
                oy += 16;
            } else if (s instanceof KeybindSetting) {
                KeybindSetting kb = (KeybindSetting) s;
                if (button == 0 && my >= oy && my <= oy + 16)
                    listeningKeybind = listeningKeybind == kb ? null : kb;
                oy += 16;
            }
        }
    }

    public void mouseDrag(int mx)   { if (draggingSlider != null) updateSlider(mx); }
    public void mouseReleased()     { draggingSlider = null; }
    public void handleScroll(int d) { scrollOffset = Math.max(0, scrollOffset + (d > 0 ? -16 : 16)); }
    public void keyTyped(char c, int kc) {
        if (listeningKeybind != null) {
            listeningKeybind.setKeyCode(kc == Keyboard.KEY_ESCAPE ? 0 : kc);
            listeningKeybind = null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private List<Module> filtered(String s) {
        if (s == null || s.isEmpty()) return modules;
        return modules.stream()
                .filter(m -> m != null && m.getName() != null && m.getName().toLowerCase().contains(s.toLowerCase()))
                .collect(Collectors.toList());
    }

    private int settingsH(Module m) {
        int h = 10;
        boolean skip = false;
        for (Setting s : m.getSettings()) {
            if (s == null || s.getName() == null) continue;
            if (!s.isVisible()) continue;
            boolean isHeader = s instanceof BooleanSetting && !s.getName().startsWith(" ");
            if (isHeader) skip = collapsedHeaders.contains(s);
            if (!isHeader && skip) continue;
            h += s instanceof SliderSetting ? SET_ROW : 16;
        }
        return h;
    }

    private void updateSlider(int mx) {
        if (draggingSlider == null) return;
        float pct = Math.max(0f, Math.min(1f, (float)(mx - sliderBarX) / sliderBarW));
        draggingSlider.setValue(
                draggingSlider.getMin() + pct * (draggingSlider.getMax() - draggingSlider.getMin()));
    }

    private String fmtVal(double v) {
        return v == Math.floor(v) ? String.valueOf((int)v) : String.format("%.2f", v);
    }

    private void drawRect(int x1, int y1, int x2, int y2, int color) {
        float a=(color>>24&0xFF)/255f, r=(color>>16&0xFF)/255f,
              g=(color>>8&0xFF)/255f,  b=(color&0xFF)/255f;
        GL11.glEnable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r,g,b,a); GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(x1,y2); GL11.glVertex2d(x2,y2);
        GL11.glVertex2d(x2,y1); GL11.glVertex2d(x1,y1);
        GL11.glEnd(); GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1,1,1,1);
    }

    private void gl() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private int blend(int c1, int c2, float t) {
        int a1=(c1>>24)&0xFF, r1=(c1>>16)&0xFF, g1=(c1>>8)&0xFF, b1=c1&0xFF;
        int a2=(c2>>24)&0xFF, r2=(c2>>16)&0xFF, g2=(c2>>8)&0xFF, b2=c2&0xFF;
        return ((int)(a1+(a2-a1)*t)<<24)|((int)(r1+(r2-r1)*t)<<16)|
               ((int)(g1+(g2-g1)*t)<<8)|(int)(b1+(b2-b1)*t);
    }
}
