package myau.ui.clickgui;

import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.KeybindSetting;
import myau.module.Module;
import myau.module.Setting;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spirit ClickGUI — visually inspired by Vape's sidebar + module-card design,
 * rebuilt from scratch on GL 2.1 (no shaders) to actually work on this
 * codebase and the user's hardware (OpenGL 2.1 Metal on Apple M4).
 *
 * Layout:
 *   Left sidebar  — category icons/labels, search bar at bottom
 *   Right panel   — scrollable list of module cards for the active category
 *                   each card has a toggle, name, suffix, and expands to show
 *                   settings (booleans, sliders, dropdowns, keybinds)
 */
public class VapeClickGui extends GuiScreen {

    // ── Colors (Vape-inspired dark theme) ────────────────────────────────────
    private static final int BG            = 0xF0141414;  // main background
    private static final int SIDEBAR_BG    = 0xF01A1A1A;  // sidebar fill
    private static final int PANEL_BG      = 0xF0111111;  // module panel fill
    private static final int CARD_BG       = 0xFF1E1E1E;  // module card idle
    private static final int CARD_HOVER    = 0xFF252525;  // module card hovered
    private static final int CARD_ENABLED  = 0xFF202A20;  // module card when on
    private static final int ACCENT        = 0xFF5B6AF0;  // purple-blue accent
    private static final int ACCENT_DIM    = 0xFF3D4AA8;  // dimmed accent
    private static final int TEXT_PRIMARY  = 0xFFEEEEEE;
    private static final int TEXT_SECONDARY= 0xFFAAAAAA;
    private static final int TEXT_DIM      = 0xFF666666;
    private static final int DIVIDER       = 0xFF2A2A2A;
    private static final int SETTING_BG    = 0xFF191919;
    private static final int TOGGLE_OFF    = 0xFF404040;
    private static final int SEARCH_BG     = 0xFF222222;
    private static final int SEARCH_BORDER = 0xFF333333;

    // ── Layout constants ─────────────────────────────────────────────────────
    private static final int   SIDEBAR_W     = 110;
    private static final int   CAT_H         = 32;        // category row height
    private static final int   CARD_H        = 30;        // module card height
    private static final int   CARD_PAD      = 5;         // vertical gap between cards
    private static final int   CARD_INDENT   = 8;         // card left margin in panel
    private static final int   SETTING_H     = 20;        // each setting row height
    private static final int   PANEL_RADIUS  = 6;
    private static final int   CARD_RADIUS   = 5;
    private static final float SEARCH_H      = 26f;

    // ── State ─────────────────────────────────────────────────────────────────
    private final List<String>            catNames;
    private final List<List<Module>>      catModules;
    private int                           selectedCat = 0;

    private float   scrollOffset   = 0f;
    private float   targetScroll   = 0f;

    // Per-module expansion & animation
    private final Map<Module, Boolean>    expanded   = new HashMap<>();
    private final Map<Module, Float>      expandAnim = new HashMap<>();
    // Per-module enabled animation (0 = off, 1 = on)
    private final Map<Module, Float>      toggleAnim = new HashMap<>();
    // Per-category hover animation
    private final float[]                 catHover;

    // Search
    private String  searchQuery  = "";
    private boolean searchActive = false;

    // Slider drag state
    private SliderSetting draggingSlider = null;
    private float         sliderStartX   = 0;
    private double        sliderStartVal = 0;

    // Dropdown open state
    private DropdownSetting openDropdown = null;
    private float           dropdownX, dropdownY, dropdownW;

    // Keybind capture
    private KeybindSetting capturingKeybind = null;

    // Hover tracking
    private int   hoveredCat   = -1;
    private Module hoveredMod  = null;
    private Setting hoveredSet = null;

    private long lastRenderMs;

    public VapeClickGui(List<String> categoryNames, List<List<Module>> categoryModules) {
        this.catNames   = categoryNames;
        this.catModules = categoryModules;
        this.catHover   = new float[categoryNames.size()];
        this.lastRenderMs = System.currentTimeMillis();
    }

    // ── Screen lifecycle ──────────────────────────────────────────────────────

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        scrollOffset = 0;
        targetScroll = 0;
        openDropdown = null;
        capturingKeybind = null;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        capturingKeybind = null;
        openDropdown = null;
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastRenderMs) / 1000f, 0.1f);
        lastRenderMs = now;

        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();

        // Panel geometry
        int panelH = sh - 40;
        int panelY = 20;
        int sideX  = (sw - SIDEBAR_W - 320) / 2;
        int sideY  = panelY;
        int modX   = sideX + SIDEBAR_W;
        int modW   = 320;

        // Dim whole screen
        GuiRender.rect(0, 0, sw, sh, 0xAA000000);

        // Sidebar background
        GuiRender.roundedRect(sideX, sideY, SIDEBAR_W, panelH, PANEL_RADIUS, SIDEBAR_BG);

        // Module panel background
        GuiRender.roundedRect(modX, sideY, modW, panelH, PANEL_RADIUS, PANEL_BG);

        // Thin divider between sidebar and panel
        GuiRender.rect(modX, sideY + 8, 1, panelH - 16, DIVIDER);

        // Animate sidebar hover
        hoveredCat = -1;
        for (int i = 0; i < catNames.size(); i++) {
            float catY = sideY + i * CAT_H + 6;
            boolean hover = mouseX >= sideX + 4 && mouseX <= sideX + SIDEBAR_W - 4
                         && mouseY >= catY && mouseY <= catY + CAT_H - 2;
            if (hover) hoveredCat = i;
            float target = (hover || i == selectedCat) ? 1f : 0f;
            catHover[i] = lerp(catHover[i], target, dt * 12f);
            drawCategoryRow(i, sideX + 4, catY, SIDEBAR_W - 8, catHover[i], i == selectedCat);
        }

        // Search bar at bottom of sidebar
        float searchY = sideY + panelH - SEARCH_H - 6;
        drawSearchBar(sideX + 6, searchY, SIDEBAR_W - 12);

        // Modules panel — scrollable
        List<Module> modules = getVisibleModules();
        float contentH = computeContentHeight(modules);
        float maxScroll = Math.max(0, contentH - (panelH - 16));
        targetScroll = MathHelper.clamp_float(targetScroll, 0, maxScroll);
        scrollOffset = lerp(scrollOffset, targetScroll, dt * 18f);

        GuiRender.pushScissor(modX + 1, sideY + 4, modW - 2, panelH - 8);
        float curY = sideY + 6 - scrollOffset;
        hoveredMod = null;
        hoveredSet = null;
        for (Module mod : modules) {
            curY = drawModuleCard(mod, modX + CARD_INDENT, curY, modW - CARD_INDENT * 2, mouseX, mouseY, dt);
        }
        GuiRender.popScissor();

        // Dropdown overlay (rendered above scroll clip)
        if (openDropdown != null) {
            drawDropdownOverlay(mouseX, mouseY);
        }

        // Scrollbar if needed
        if (maxScroll > 0) {
            float barH = Math.max(20, (panelH - 16) / (contentH) * (panelH - 16));
            float barY = sideY + 8 + (scrollOffset / maxScroll) * (panelH - 16 - barH);
            GuiRender.roundedRect(modX + modW - 5, barY, 3, barH, 1.5f, 0xFF444444);
        }
    }

    // ── Category row ──────────────────────────────────────────────────────────

    private void drawCategoryRow(int idx, float x, float y, float w, float anim, boolean selected) {
        int bg = selected ? GuiRender.lerpColor(0xFF1F2033, ACCENT, 0.15f) : SIDEBAR_BG;
        if (anim > 0.01f) bg = GuiRender.lerpColor(bg, 0xFF222232, anim);
        if (selected) {
            // Left accent bar
            GuiRender.roundedRect(x, y, 3, CAT_H - 4, 1.5f, ACCENT);
        }
        GuiRender.roundedRect(x + 3, y, w - 3, CAT_H - 2, CARD_RADIUS, bg);
        String name = catNames.get(idx);
        int textColor = selected ? TEXT_PRIMARY : GuiRender.lerpColor(TEXT_DIM, TEXT_SECONDARY, anim);
        GuiRender.textNoShadow(name, x + 14, y + (CAT_H - 2) / 2f - GuiRender.textH() / 2f, textColor);
    }

    // ── Module card ───────────────────────────────────────────────────────────

    private float drawModuleCard(Module mod, float x, float y, float w, int mx, int my, float dt) {
        boolean enabled = mod.isEnabled();

        // Toggle animation
        float ta = toggleAnim.getOrDefault(mod, enabled ? 1f : 0f);
        ta = lerp(ta, enabled ? 1f : 0f, dt * 14f);
        toggleAnim.put(mod, ta);

        boolean exp = expanded.getOrDefault(mod, false);
        float ea = expandAnim.getOrDefault(mod, 0f);
        ea = lerp(ea, exp ? 1f : 0f, dt * 14f);
        expandAnim.put(mod, ea);

        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + CARD_H;
        if (hover) hoveredMod = mod;

        // Card background
        int bg = enabled ? GuiRender.lerpColor(CARD_BG, CARD_ENABLED, ta)
                         : hover ? CARD_HOVER : CARD_BG;
        GuiRender.roundedRect(x, y, w, CARD_H, CARD_RADIUS, bg);

        // Accent top edge when enabled
        if (ta > 0.01f) {
            GuiRender.rect(x + CARD_RADIUS, y, w - CARD_RADIUS * 2, 1, GuiRender.withAlpha(ACCENT, (int)(ta * 180)));
        }

        // Module name
        String name = mod.getName();
        String[] suffix = mod.getSuffix();
        String suffixStr = (suffix != null && suffix.length > 0) ? " §8[§7" + String.join(", ", suffix) + "§8]" : "";
        GuiRender.text(name + suffixStr, x + 9, y + CARD_H / 2f - GuiRender.textH() / 2f, enabled ? TEXT_PRIMARY : TEXT_SECONDARY);

        // Toggle
        float toggleCX = x + w - 18;
        float toggleCY = y + CARD_H / 2f;
        GuiRender.toggle(toggleCX, toggleCY, 7f, TOGGLE_OFF, ACCENT, enabled, ta);

        // Expand chevron (only if has visible settings)
        List<Setting> vis = getVisibleSettings(mod);
        if (!vis.isEmpty()) {
            String chevron = exp ? "▴" : "▾";
            GuiRender.textNoShadow(chevron, toggleCX - 14, toggleCY - 4, TEXT_DIM);
        }

        float nextY = y + CARD_H + CARD_PAD;

        // Expanded settings
        if (ea > 0.01f && !vis.isEmpty()) {
            float settingsTotalH = vis.size() * SETTING_H;
            float visibleH = settingsTotalH * ea;
            GuiRender.pushScissor(x, nextY - CARD_PAD, w, visibleH + CARD_PAD);
            GuiRender.roundedRect(x, nextY - CARD_PAD, w, visibleH + CARD_PAD, CARD_RADIUS, SETTING_BG);
            float sy = nextY;
            for (Setting s : vis) {
                sy = drawSetting(s, x + 4, sy, w - 8, mx, my);
            }
            GuiRender.popScissor();
            nextY += visibleH;
        }

        return nextY;
    }

    // ── Setting row ───────────────────────────────────────────────────────────

    private float drawSetting(Setting s, float x, float y, float w, int mx, int my) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + SETTING_H;
        if (hover) hoveredSet = s;

        if (s instanceof BooleanSetting) {
            BooleanSetting bs = (BooleanSetting) s;
            GuiRender.textNoShadow(bs.getName(), x + 4, y + SETTING_H / 2f - GuiRender.textH() / 2f, hover ? TEXT_PRIMARY : TEXT_SECONDARY);
            // Mini toggle on the right
            float tcx = x + w - 14;
            float tcy = y + SETTING_H / 2f;
            boolean on = bs.getValue();
            // Use hover state for simple color tint
            int trackOn  = bs.getValue() ? ACCENT : TOGGLE_OFF;
            float tw = 10f;
            GuiRender.roundedRect(tcx - tw / 2, tcy - 3.5f, tw, 7, 3.5f, on ? ACCENT : TOGGLE_OFF);
            GuiRender.fillCircle(on ? tcx + tw / 2 - 3.5f : tcx - tw / 2 + 3.5f, tcy, 3f, TEXT_PRIMARY);

        } else if (s instanceof SliderSetting) {
            SliderSetting ss = (SliderSetting) s;
            double val = ss.getValue(), min = ss.getMin(), max = ss.getMax();
            float pct = (float)((val - min) / (max - min));
            // Show as integer if the value is a whole number and range looks integer-stepped
            String valStr = (val == Math.floor(val) && val < 1e9) ? String.valueOf((int) val) : String.format("%.2f", val);
            GuiRender.textNoShadow(ss.getName(), x + 4, y + 3, hover ? TEXT_PRIMARY : TEXT_SECONDARY);
            GuiRender.textNoShadow(valStr, x + w - GuiRender.textW(valStr) - 2, y + 3, TEXT_DIM);
            float railY = y + SETTING_H - 7;
            GuiRender.sliderRail(x + 4, railY, w - 8, 4, pct, 0xFF333333, ACCENT);
            // Handle thumb
            GuiRender.fillCircle(x + 4 + (w - 8) * pct, railY + 2, 4, draggingSlider == ss ? 0xFFFFFFFF : 0xFFCCCCCC);

            if (draggingSlider == ss) {
                // Update value during drag
                double newPct = MathHelper.clamp_float((mx - (x + 4)) / (w - 8), 0f, 1f);
                double newVal = min + (max - min) * newPct;
                ss.setValue(newVal);
            }

        } else if (s instanceof DropdownSetting) {
            DropdownSetting ds = (DropdownSetting) s;
            GuiRender.textNoShadow(ds.getName(), x + 4, y + SETTING_H / 2f - GuiRender.textH() / 2f, hover ? TEXT_PRIMARY : TEXT_SECONDARY);
            String val = ds.getValue();
            int valW = GuiRender.textW(val);
            int boxW = valW + 16;
            float boxX = x + w - boxW;
            GuiRender.roundedRect(boxX, y + 3, boxW, SETTING_H - 6, 3, openDropdown == ds ? ACCENT_DIM : 0xFF2A2A2A);
            GuiRender.textNoShadow(val, boxX + 4, y + SETTING_H / 2f - GuiRender.textH() / 2f, TEXT_PRIMARY);
            GuiRender.textNoShadow("▾", boxX + boxW - 10, y + SETTING_H / 2f - GuiRender.textH() / 2f, TEXT_DIM);

            if (hover && openDropdown != ds) {
                dropdownX = boxX; dropdownY = y + SETTING_H; dropdownW = boxW;
            }

        } else if (s instanceof KeybindSetting) {
            KeybindSetting ks = (KeybindSetting) s;
            boolean capturing = capturingKeybind == ks;
            String keyStr = capturing ? "..." : (ks.getKeyCode() == 0 ? "NONE" : Keyboard.getKeyName(ks.getKeyCode()));
            GuiRender.textNoShadow(ks.getName(), x + 4, y + SETTING_H / 2f - GuiRender.textH() / 2f, hover ? TEXT_PRIMARY : TEXT_SECONDARY);
            int kW = GuiRender.textW(keyStr) + 10;
            float kX = x + w - kW;
            GuiRender.roundedRect(kX, y + 3, kW, SETTING_H - 6, 3, capturing ? ACCENT_DIM : 0xFF2A2A2A);
            GuiRender.textNoShadow(keyStr, kX + 5, y + SETTING_H / 2f - GuiRender.textH() / 2f, capturing ? 0xFFFFCC44 : TEXT_PRIMARY);
        }

        GuiRender.rect(x + 4, y + SETTING_H - 1, w - 8, 1, 0xFF1F1F1F);
        return y + SETTING_H;
    }

    // ── Dropdown overlay ──────────────────────────────────────────────────────

    private void drawDropdownOverlay(int mx, int my) {
        String[] opts = openDropdown.getOptions();
        int cur = openDropdown.getIndex();
        float ovH = opts.length * SETTING_H + 4;
        GuiRender.roundedRect(dropdownX, dropdownY, dropdownW, ovH, 4, 0xFF1A1A2E);
        GuiRender.border(dropdownX, dropdownY, dropdownW, ovH, 0xFF444466);
        for (int i = 0; i < opts.length; i++) {
            float oy = dropdownY + 2 + i * SETTING_H;
            boolean hov = mx >= dropdownX && mx <= dropdownX + dropdownW && my >= oy && my <= oy + SETTING_H;
            if (hov) GuiRender.roundedRect(dropdownX + 2, oy, dropdownW - 4, SETTING_H, 3, 0xFF252540);
            int col = (i == cur) ? ACCENT : (hov ? TEXT_PRIMARY : TEXT_SECONDARY);
            GuiRender.textNoShadow(opts[i], dropdownX + 6, oy + SETTING_H / 2f - GuiRender.textH() / 2f, col);
        }
    }

    // ── Search bar ────────────────────────────────────────────────────────────

    private void drawSearchBar(float x, float y, float w) {
        int borderCol = searchActive ? ACCENT : SEARCH_BORDER;
        GuiRender.roundedRect(x, y, w, SEARCH_H, SEARCH_H / 2f, SEARCH_BG);
        GuiRender.border(x, y, w, SEARCH_H, borderCol);
        String display = searchQuery.isEmpty() && !searchActive ? "Search..." : searchQuery + (searchActive ? "|" : "");
        int col = searchQuery.isEmpty() && !searchActive ? TEXT_DIM : TEXT_PRIMARY;
        GuiRender.textNoShadow(display, x + 8, y + SEARCH_H / 2f - GuiRender.textH() / 2f, col);
    }

    // ── Content helpers ───────────────────────────────────────────────────────

    private List<Module> getVisibleModules() {
        List<Module> all = catModules.get(selectedCat);
        if (searchQuery.isEmpty()) return all;
        List<Module> result = new ArrayList<>();
        String q = searchQuery.toLowerCase();
        for (Module m : all) {
            if (m.getName().toLowerCase().contains(q)) result.add(m);
        }
        return result;
    }

    private List<Setting> getVisibleSettings(Module m) {
        List<Setting> result = new ArrayList<>();
        for (Setting s : m.getSettings()) {
            if (s.isVisible()) result.add(s);
        }
        return result;
    }

    private float computeContentHeight(List<Module> modules) {
        float h = 0;
        for (Module m : modules) {
            h += CARD_H + CARD_PAD;
            if (expanded.getOrDefault(m, false)) {
                h += getVisibleSettings(m).size() * SETTING_H;
            }
        }
        return h;
    }

    private float getModuleCardY(Module target, List<Module> modules, float startY) {
        float y = startY;
        for (Module m : modules) {
            if (m == target) return y;
            y += CARD_H + CARD_PAD;
            if (expanded.getOrDefault(m, false)) {
                y += getVisibleSettings(m).size() * SETTING_H;
            }
        }
        return y;
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public void mouseClicked(int mx, int my, int button) throws IOException {
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
        int panelH = sh - 40, panelY = 20;
        int sideX  = (sw - SIDEBAR_W - 320) / 2;
        int modX   = sideX + SIDEBAR_W;
        int modW   = 320;

        // Close dropdown on outside click
        if (openDropdown != null) {
            String[] opts = openDropdown.getOptions();
            float ovH = opts.length * SETTING_H + 4;
            if (mx >= dropdownX && mx <= dropdownX + dropdownW && my >= dropdownY && my <= dropdownY + ovH) {
                int i = (int)((my - dropdownY - 2) / SETTING_H);
                if (i >= 0 && i < opts.length) openDropdown.setIndex(i);
            }
            openDropdown = null;
            return;
        }

        // Cancel keybind capture on right-click
        if (button == 1 && capturingKeybind != null) {
            capturingKeybind.setKeyCode(0);
            capturingKeybind = null;
            return;
        }

        // Search bar click
        float searchY = panelY + panelH - SEARCH_H - 6;
        if (mx >= sideX + 6 && mx <= sideX + SIDEBAR_W - 6 && my >= searchY && my <= searchY + SEARCH_H) {
            searchActive = !searchActive;
            return;
        }

        // Category click
        for (int i = 0; i < catNames.size(); i++) {
            float catY = panelY + i * CAT_H + 6;
            if (mx >= sideX + 4 && mx <= sideX + SIDEBAR_W - 4 && my >= catY && my <= catY + CAT_H - 2) {
                selectedCat = i;
                scrollOffset = 0;
                targetScroll = 0;
                return;
            }
        }

        // Module / setting click (inside the module panel)
        if (mx < modX || mx > modX + modW) return;
        List<Module> modules = getVisibleModules();
        float curY = panelY + 6 - scrollOffset;
        for (Module mod : modules) {
            float cardY = curY;
            // Toggle click (right side of card)
            float toggleCX = modX + CARD_INDENT + (modW - CARD_INDENT * 2) - 18;
            if (my >= cardY && my <= cardY + CARD_H) {
                if (button == 0 && mx >= toggleCX - 10 && mx <= toggleCX + 10) {
                    mod.toggle();
                    return;
                }
                List<Setting> vis = getVisibleSettings(mod);
                if (!vis.isEmpty() && button == 0) {
                    boolean exp = expanded.getOrDefault(mod, false);
                    expanded.put(mod, !exp);
                    return;
                }
                if (button == 0) {
                    mod.toggle();
                    return;
                }
            }

            curY += CARD_H + CARD_PAD;

            if (expanded.getOrDefault(mod, false)) {
                List<Setting> vis = getVisibleSettings(mod);
                float sx = modX + CARD_INDENT + 4;
                float sw2 = modW - CARD_INDENT * 2 - 8;
                for (Setting s : vis) {
                    float sy = curY;
                    if (my >= sy && my <= sy + SETTING_H && mx >= sx && mx <= sx + sw2) {
                        handleSettingClick(s, mx, sy, sx, sw2, button);
                        return;
                    }
                    curY += SETTING_H;
                }
            }
        }
    }

    private void handleSettingClick(Setting s, int mx, float sy, float sx, float sw, int button) {
        if (s instanceof BooleanSetting) {
            ((BooleanSetting) s).setValue(!((BooleanSetting) s).getValue());

        } else if (s instanceof SliderSetting) {
            if (button == 0) {
                draggingSlider = (SliderSetting) s;
                sliderStartX   = mx;
                sliderStartVal = ((SliderSetting) s).getValue();
            }

        } else if (s instanceof DropdownSetting) {
            openDropdown = openDropdown == s ? null : (DropdownSetting) s;
            dropdownX = sx + sw - (GuiRender.textW(((DropdownSetting)s).getValue()) + 16);
            dropdownW = GuiRender.textW(((DropdownSetting)s).getValue()) + 16;
            dropdownY = sy + SETTING_H;

        } else if (s instanceof KeybindSetting) {
            capturingKeybind = (capturingKeybind == s) ? null : (KeybindSetting) s;
        }
    }

    @Override
    public void mouseReleased(int mx, int my, int button) {
        if (button == 0) draggingSlider = null;
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long timeSinceLastClick) {
        if (draggingSlider != null) {
            // Value update happens in drawSetting using current mouse position
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            if (openDropdown == null) {
                targetScroll -= scroll > 0 ? 20 : -20;
                targetScroll = MathHelper.clamp_float(targetScroll, 0, 9999);
            }
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) throws IOException {
        if (capturingKeybind != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                capturingKeybind.setKeyCode(0);
            } else {
                capturingKeybind.setKeyCode(keyCode);
            }
            capturingKeybind = null;
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (searchActive && !searchQuery.isEmpty()) {
                searchQuery = "";
                return;
            }
            if (searchActive) { searchActive = false; return; }
            mc.displayGuiScreen(null);
            return;
        }

        if (searchActive) {
            if (keyCode == Keyboard.KEY_BACK && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            } else if (typedChar >= ' ') {
                searchQuery += typedChar;
            }
            // Re-trigger search
            selectedCat = findCategoryWithResults();
        }
    }

    private int findCategoryWithResults() {
        if (searchQuery.isEmpty()) return selectedCat;
        String q = searchQuery.toLowerCase();
        for (int i = 0; i < catModules.size(); i++) {
            for (Module m : catModules.get(i)) {
                if (m.getName().toLowerCase().contains(q)) return i;
            }
        }
        return selectedCat;
    }

    // ── Util ─────────────────────────────────────────────────────────────────

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * MathHelper.clamp_float(t, 0f, 1f);
    }
}
