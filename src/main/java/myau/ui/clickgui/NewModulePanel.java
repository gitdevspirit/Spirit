package myau.ui.clickgui;

import myau.module.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NewModulePanel {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // Layout
    private static final int CARD_W       = 150;
    private static final int CARD_H       = 36;
    private static final int CARD_GAP     = 8;
    private static final int SETTINGS_ROW = 24;

    // Area set by parent each frame
    private int areaX, areaY, areaW, areaH;

    private List<Module> modules;
    private Module expandedModule = null;

    private final Map<Module, Float> toggleAnim    = new HashMap<>();
    private final Map<Module, Float> expandAnim    = new HashMap<>();

    private int scrollOffset = 0;

    // Slider drag state
    private SliderSetting  draggingSlider = null;
    private int            sliderBarX, sliderBarW;

    // Keybind
    private KeybindSetting listeningKeybind = null;

    public NewModulePanel(List<Module> modules) {
        this.modules = modules;
    }

    public void setModules(List<Module> modules) {
        this.modules = modules;
        expandedModule = null;
        scrollOffset   = 0;
    }

    public void setVisibleArea(int x, int y, int w, int h) {
        areaX = x; areaY = y; areaW = w; areaH = h;
    }

    // ── How many columns fit ──────────────────────────────────────────────────
    private int cols() {
        return Math.max(1, areaW / (CARD_W + CARD_GAP));
    }

    // ── Total content height ──────────────────────────────────────────────────
    public int contentHeight(List<Module> filtered) {
        int c = cols();
        int rows = 0;
        int col  = 0;
        int rowH = CARD_H + CARD_GAP;
        for (Module m : filtered) {
            if (col == 0) rows++;
            // If this module is expanded it pushes down the next row
            if (expandedModule == m && col == 0) {
                rowH = Math.max(rowH, CARD_H + settingsHeight(m) + CARD_GAP);
            }
            col = (col + 1) % c;
        }
        // Rough total: multiply rows * rowH
        return rows * (CARD_H + CARD_GAP) + (expandedModule != null ? settingsHeight(expandedModule) : 0);
    }

    // ── Render ────────────────────────────────────────────────────────────────
    public void render(int mouseX, int mouseY, String search) {
        List<Module> filtered = filtered(search);

        // Clamp scroll
        int maxScroll = Math.max(0, contentHeight(filtered) - areaH + 20);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        ScissorUtil.enable(areaX, areaY, areaW, areaH);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);

        int c    = cols();
        int totalW = c * CARD_W + (c - 1) * CARD_GAP;
        int startX = areaX + (areaW - totalW) / 2;

        int col = 0, x = startX, y = areaY - scrollOffset;
        int extraY = 0; // extra height from expanded settings

        for (Module m : filtered) {
            if (col == 0) { x = startX; y += extraY; extraY = 0; }
            renderCard(m, x, y, mouseX, mouseY);
            int cH = CARD_H;
            if (expandedModule == m) cH += settingsHeight(m);
            extraY = Math.max(extraY, cH + CARD_GAP);
            x += CARD_W + CARD_GAP;
            col = (col + 1) % c;
            if (col == 0) { y += CARD_H + CARD_GAP; extraY = 0; }
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        ScissorUtil.disable();
    }

    private void renderCard(Module m, int x, int y, int mouseX, int mouseY) {
        boolean enabled = m.isEnabled();
        boolean hovered = mouseX >= x && mouseX <= x + CARD_W && mouseY >= y && mouseY <= y + CARD_H;
        boolean expanded = expandedModule == m;

        // Advance toggle animation
        toggleAnim.putIfAbsent(m, enabled ? 1f : 0f);
        float ta = toggleAnim.get(m);
        ta += ((enabled ? 1f : 0f) - ta) * 0.25f;
        toggleAnim.put(m, ta);

        // Card background
        int bgColor = enabled
                ? blend(0xFF1A1A1A, 0xFF252525, ta)
                : (hovered ? 0xFF181818 : 0xFF131313);
        RoundedUtils.drawRoundedRect(x, y, CARD_W, CARD_H, 8, bgColor);

        // Left accent bar when enabled
        if (enabled) {
            RoundedUtils.drawRoundedRect(x, y + 6, 2, CARD_H - 12, 1, blend(0xFF333333, 0xFFFFFFFF, ta));
        }

        // Subtle border
        int borderAlpha = enabled ? 0x40 : (hovered ? 0x25 : 0x15);
        drawBorder(x, y, CARD_W, CARD_H, 8, (borderAlpha << 24) | 0xFFFFFF);

        // Module name
        GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1,1,1,1);
        int nameColor = enabled ? 0xFFFFFFFF : (hovered ? 0xFFBBBBBB : 0xFF777777);
        mc.fontRendererObj.drawString(m.getName(), x + 10, y + 8, nameColor);

        // Suffix (e.g. mode name)
        String[] suffix = m.getSuffix();
        if (suffix != null && suffix.length > 0) {
            GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1,1,1,1);
            mc.fontRendererObj.drawString(suffix[0], x + 10, y + 19, 0xFF555555);
        }

        // Toggle switch (right side)
        int tgX = x + CARD_W - 26, tgY = y + CARD_H / 2 - 4;
        drawSwitch(tgX, tgY, ta);

        // Expand arrow if settings exist
        if (!m.getSettings().isEmpty()) {
            GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1,1,1,1);
            mc.fontRendererObj.drawString(expanded ? "v" : ">", x + CARD_W - 38, y + 8, 0xFF444444);
        }

        // Settings panel below card if expanded
        if (expanded) {
            renderSettings(m, x, y + CARD_H, CARD_W, mouseX, mouseY);
        }
    }

    private void renderSettings(Module m, int x, int y, int w, int mouseX, int mouseY) {
        // Settings background — slightly different shade
        int sh = settingsHeight(m);
        RoundedUtils.drawRoundedRect(x, y, w, sh, 8, 0xFF0F0F0F);
        drawBorder(x, y, w, sh, 8, 0x20FFFFFF);

        int oy = y + 6;
        for (Setting s : m.getSettings()) {
            if (!s.isVisible()) continue;

            GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1,1,1,1);

            if (s instanceof SliderSetting) {
                SliderSetting sl = (SliderSetting) s;
                mc.fontRendererObj.drawString(s.getName(), x + 10, oy + 2, 0xFF888888);
                String valStr = formatVal(sl.getValue());
                mc.fontRendererObj.drawString(valStr,
                        x + w - mc.fontRendererObj.getStringWidth(valStr) - 10, oy + 2, 0xFFCCCCCC);

                int bx = x + 10, by = oy + 14, bw = w - 20;
                drawRect(bx, by, bx + bw, by + 3, 0xFF252525);
                int fill = Math.max(3, (int)(bw * sl.getPercent()));
                drawRect(bx, by, bx + fill, by + 3, 0xFFCCCCCC);
                // Handle dot
                drawRoundRect(bx + fill - 3, by - 2, 6, 7, 3, 0xFFFFFFFF);

                if (draggingSlider == sl) { sliderBarX = bx; sliderBarW = bw; }
                oy += SETTINGS_ROW;

            } else if (s instanceof DropdownSetting) {
                DropdownSetting dd = (DropdownSetting) s;
                mc.fontRendererObj.drawString(s.getName(), x + 10, oy + 2, 0xFF888888);
                String val = "< " + dd.getValue() + " >";
                mc.fontRendererObj.drawString(val,
                        x + w - mc.fontRendererObj.getStringWidth(val) - 10, oy + 2, 0xFFCCCCCC);
                oy += 18;

            } else if (s instanceof BooleanSetting) {
                BooleanSetting bs = (BooleanSetting) s;
                mc.fontRendererObj.drawString(s.getName(), x + 10, oy + 2, 0xFF888888);
                float ba = bs.getValue() ? 1f : 0f;
                drawSwitch(x + w - 26, oy, ba);
                oy += 18;

            } else if (s instanceof KeybindSetting) {
                KeybindSetting kb = (KeybindSetting) s;
                mc.fontRendererObj.drawString(s.getName(), x + 10, oy + 2, 0xFF888888);
                boolean listening = listeningKeybind == kb;
                String label = listening ? "[ ... ]" : "[ " + kb.getDisplayName() + " ]";
                int lc = listening ? 0xFFFFFFFF : 0xFFAAAAAA;
                mc.fontRendererObj.drawString(label,
                        x + w - mc.fontRendererObj.getStringWidth(label) - 10, oy + 2, lc);
                oy += 18;
            }
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────
    public void mouseClicked(int mouseX, int mouseY, int button) {
        List<Module> filtered = filtered(null); // search doesn't affect click detection
        int c = cols();
        int totalW = c * CARD_W + (c - 1) * CARD_GAP;
        int startX = areaX + (areaW - totalW) / 2;

        int col = 0, x = startX, y = areaY - scrollOffset, extraY = 0;

        for (Module m : filtered) {
            if (col == 0) { x = startX; y += extraY; extraY = 0; }

            // Card hit
            if (mouseX >= x && mouseX <= x + CARD_W && mouseY >= y && mouseY <= y + CARD_H) {
                if (button == 0) m.toggle();
                else if (button == 1 && !m.getSettings().isEmpty())
                    expandedModule = (expandedModule == m) ? null : m;
                return;
            }

            // Settings hit (if expanded)
            if (expandedModule == m) {
                int sh = settingsHeight(m);
                if (mouseX >= x && mouseX <= x + CARD_W && mouseY >= y + CARD_H && mouseY <= y + CARD_H + sh) {
                    settingClicked(m, x, y + CARD_H, CARD_W, mouseX, mouseY, button);
                    return;
                }
                extraY = Math.max(extraY, CARD_H + sh + CARD_GAP);
            } else {
                extraY = Math.max(extraY, CARD_H + CARD_GAP);
            }

            x += CARD_W + CARD_GAP;
            col = (col + 1) % c;
            if (col == 0) { y += CARD_H + CARD_GAP; extraY = 0; }
        }
    }

    private void settingClicked(Module m, int x, int y, int w, int mouseX, int mouseY, int button) {
        int oy = y + 6;
        for (Setting s : m.getSettings()) {
            if (!s.isVisible()) continue;
            if (s instanceof SliderSetting) {
                SliderSetting sl = (SliderSetting) s;
                int bx = x + 10, by = oy + 14, bw = w - 20;
                if (button == 0 && mouseX >= bx && mouseX <= bx + bw
                        && mouseY >= by - 3 && mouseY <= by + 8) {
                    draggingSlider = sl; sliderBarX = bx; sliderBarW = bw;
                    updateSlider(mouseX);
                }
                oy += SETTINGS_ROW;
            } else if (s instanceof DropdownSetting) {
                DropdownSetting dd = (DropdownSetting) s;
                if (mouseY >= oy && mouseY <= oy + 18) {
                    if (button == 0) dd.next();
                    else if (button == 1) dd.prev();
                }
                oy += 18;
            } else if (s instanceof BooleanSetting) {
                BooleanSetting bs = (BooleanSetting) s;
                if (button == 0 && mouseY >= oy && mouseY <= oy + 18) bs.toggle();
                oy += 18;
            } else if (s instanceof KeybindSetting) {
                KeybindSetting kb = (KeybindSetting) s;
                if (button == 0 && mouseY >= oy && mouseY <= oy + 18)
                    listeningKeybind = (listeningKeybind == kb) ? null : kb;
                oy += 18;
            }
        }
    }

    public void mouseDrag(int mouseX) { if (draggingSlider != null) updateSlider(mouseX); }
    public void mouseReleased()       { draggingSlider = null; }

    public void handleScroll(int delta) {
        scrollOffset = Math.max(0, scrollOffset + (delta > 0 ? -16 : 16));
    }

    public void keyTyped(char c, int keyCode) {
        if (listeningKeybind != null) {
            listeningKeybind.setKeyCode(keyCode == Keyboard.KEY_ESCAPE ? 0 : keyCode);
            listeningKeybind = null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private List<Module> filtered(String search) {
        if (search == null || search.isEmpty()) return modules;
        String q = search.toLowerCase();
        return modules.stream().filter(m -> m.getName().toLowerCase().contains(q)).collect(Collectors.toList());
    }

    private int settingsHeight(Module m) {
        int h = 6;
        for (Setting s : m.getSettings()) {
            if (!s.isVisible()) continue;
            h += (s instanceof SliderSetting) ? SETTINGS_ROW : 18;
        }
        return h + 6;
    }

    private void updateSlider(int mouseX) {
        if (draggingSlider == null) return;
        float pct = Math.max(0f, Math.min(1f, (float)(mouseX - sliderBarX) / sliderBarW));
        draggingSlider.setValue(draggingSlider.getMin() + pct * (draggingSlider.getMax() - draggingSlider.getMin()));
    }

    private String formatVal(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.2f", v);
    }

    // ── Draw primitives ───────────────────────────────────────────────────────
    private void drawSwitch(int x, int y, float t) {
        int trackColor = blend(0xFF2A2A2A, 0xFF555555, t);
        drawRect(x, y, x + 18, y + 8, trackColor);
        // Round ends
        RoundedUtils.drawRoundedRect(x, y, 18, 8, 4, trackColor);
        // Knob
        int kx = x + 2 + (int)(t * 8);
        RoundedUtils.drawRoundedRect(kx, y + 1, 6, 6, 3, 0xFFFFFFFF);
    }

    private void drawBorder(int x, int y, int w, int h, int r, int color) {
        // Single-pixel border sim using thin rects
        drawRect(x, y, x + w, y + 1, color);
        drawRect(x, y + h - 1, x + w, y + h, color);
        drawRect(x, y, x + 1, y + h, color);
        drawRect(x + w - 1, y, x + w, y + h, color);
    }

    private void drawRoundRect(int x, int y, int w, int h, int r, int color) {
        RoundedUtils.drawRoundedRect(x, y, w, h, r, color);
    }

    private void drawRect(int x1, int y1, int x2, int y2, int color) {
        float a = (color >> 24 & 0xFF) / 255f;
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >> 8  & 0xFF) / 255f;
        float b = (color       & 0xFF) / 255f;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(x1, y2); GL11.glVertex2d(x2, y2);
        GL11.glVertex2d(x2, y1); GL11.glVertex2d(x1, y1);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private int blend(int c1, int c2, float t) {
        int a1=(c1>>24)&0xFF, r1=(c1>>16)&0xFF, g1=(c1>>8)&0xFF, b1=c1&0xFF;
        int a2=(c2>>24)&0xFF, r2=(c2>>16)&0xFF, g2=(c2>>8)&0xFF, b2=c2&0xFF;
        return ((int)(a1+(a2-a1)*t)<<24)|((int)(r1+(r2-r1)*t)<<16)|((int)(g1+(g2-g1)*t)<<8)|(int)(b1+(b2-b1)*t);
    }
}
