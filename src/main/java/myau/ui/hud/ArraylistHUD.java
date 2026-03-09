package myau.ui.hud;

import myau.Myau;
import myau.module.Module;
import myau.module.BooleanSetting;
import myau.module.SliderSetting;
import myau.module.modules.HUD;
import myau.module.modules.Pit;
import myau.ui.clickgui.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ArraylistHUD {
    private final Minecraft mc = Minecraft.getMinecraft();

    // ── Settings (registered on HUD module) ───────────────────────────────────
    // These are declared here as references but must be registered in HUD.java:
    //   public final SliderSetting  alPadX        = register(new SliderSetting("AL Pad X",      2, 0, 10, 1));
    //   public final SliderSetting  alPadY        = register(new SliderSetting("AL Pad Y",      1, 0, 10, 1));
    //   public final SliderSetting  alRounding    = register(new SliderSetting("AL Rounding",   3, 0, 8,  1));
    //   public final BooleanSetting alLowercase   = register(new BooleanSetting("AL Lowercase",  true));
    //   public final BooleanSetting alShowDetails = register(new BooleanSetting("AL Details",    true));
    //   public final BooleanSetting alBoundOnly   = register(new BooleanSetting("AL Bound Only", false));
    //   public final BooleanSetting alBackground  = register(new BooleanSetting("AL Background", true));
    //   (text color and details color come from existing hud.getListColor() and hud.getColor())

    private static final class Row {
        final String name;
        final String detail;
        final boolean isPitSub;
        Row(String name, String detail, boolean isPitSub) {
            this.name = name; this.detail = detail; this.isPitSub = isPitSub;
        }
    }

    public void render() {
        ScaledResolution sr = new ScaledResolution(mc);
        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
        if (hud == null || !hud.isEnabled()) return;

        // ── Read settings from HUD ────────────────────────────────────────────
        int accentRGB  = hud.getColor(System.currentTimeMillis()).getRGB() | 0xFF000000;
        int nameRGB    = hud.getListColor().getRGB() | 0xFF000000;
        boolean shadow = hud.shadow.getValue();

        // New settings — read with safe fallback if not yet added to HUD.java
        int    padX        = getSlider(hud, "AL Pad X",      2);
        int    padY        = getSlider(hud, "AL Pad Y",      1);
        float  rounding    = getSlider(hud, "AL Rounding",   3);
        boolean lowercase  = getBool(hud,   "AL Lowercase",  true);
        boolean showDetail = getBool(hud,   "AL Details",    true);
        boolean boundOnly  = getBool(hud,   "AL Bound Only", false);
        boolean drawBg     = getBool(hud,   "AL Background", true);

        int fontH      = mc.fontRendererObj.FONT_HEIGHT;
        int lineHeight = fontH + padY * 2 + 1;

        // ── Build rows ────────────────────────────────────────────────────────
        List<Row> rows = new ArrayList<>();

        // Pit block
        Pit pit = (Pit) Myau.moduleManager.getModule(Pit.class);
        if (pit != null && pit.isEnabled() && (!boundOnly || pit.getKey() != 0)) {
            List<String> subs = new ArrayList<>();
            for (myau.module.Setting s : pit.getSettings()) {
                if (s instanceof BooleanSetting && !s.getName().startsWith(" ")) {
                    if (((BooleanSetting) s).getValue()) subs.add(fmt(s.getName(), lowercase));
                }
            }
            rows.add(new Row(fmt("pit", lowercase), "", false));
            for (String sub : subs) rows.add(new Row(sub, "", true));
        }

        // Other enabled modules
        List<Module> others = new ArrayList<>();
        for (Module m : Myau.moduleManager.modules.values()) {
            if (!m.isEnabled() || m.isHidden() || m instanceof Pit) continue;
            if (boundOnly && m.getKey() == 0) continue;
            others.add(m);
        }

        others.sort(Comparator.comparingInt((Module m) -> {
            String det = showDetail && m.getSuffix().length > 0
                    ? " " + fmt(m.getSuffix()[0], lowercase) : "";
            return mc.fontRendererObj.getStringWidth(fmt(m.getName(), lowercase) + det);
        }).reversed());

        for (Module m : others) {
            String det = showDetail && m.getSuffix().length > 0
                    ? " " + fmt(m.getSuffix()[0], lowercase) : "";
            rows.add(new Row(fmt(m.getName(), lowercase), det, false));
        }

        if (rows.isEmpty()) return;

        // ── Measure max width ─────────────────────────────────────────────────
        int maxW = 0;
        for (Row r : rows) {
            int w = mc.fontRendererObj.getStringWidth(
                    (r.isPitSub ? "  " : "") + r.name + r.detail);
            if (w > maxW) maxW = w;
        }

        int totalH = rows.size() * lineHeight;
        int bgW    = maxW + padX * 2;
        int bgX    = sr.getScaledWidth() - bgW - 1;
        int bgY    = 4;

        // ── Draw background ───────────────────────────────────────────────────
        if (drawBg) {
            if (rounding >= 1) {
                // Shadow
                RoundedUtils.drawRoundedRect(bgX - 2, bgY - 2, bgW + 4, totalH + 4,
                        rounding + 2, 0x33000000);
                // BG per-row with rounding
                RoundedUtils.drawRoundedRect(bgX, bgY, bgW, totalH, rounding, 0xBB0A0A0A);
            } else {
                // Plain rect fallback (no import needed)
                net.minecraft.client.gui.Gui.drawRect(bgX, bgY, bgX + bgW, bgY + totalH, 0xBB0A0A0A);
            }
        }

        // ── Render rows ───────────────────────────────────────────────────────
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();

        for (int i = 0; i < rows.size(); i++) {
            Row r     = rows.get(i);
            String display = (r.isPitSub ? "  " : "") + r.name;
            int nameW = mc.fontRendererObj.getStringWidth(display);
            int rowY  = bgY + i * lineHeight + padY;

            // Right-align: text starts so that (display + detail) ends at right edge
            int totalRowW = nameW + mc.fontRendererObj.getStringWidth(r.detail);
            int textX     = sr.getScaledWidth() - totalRowW - 1 - padX;

            // Submodule names in accent, normal names in nameRGB
            int color = r.isPitSub ? accentRGB : nameRGB;
            mc.fontRendererObj.drawString(display, textX, rowY, color, shadow);

            if (!r.detail.isEmpty()) {
                mc.fontRendererObj.drawString(r.detail, textX + nameW, rowY, accentRGB, shadow);
            }

            // Accent bar on right edge of each row
            net.minecraft.client.gui.Gui.drawRect(
                    sr.getScaledWidth() - 2, bgY + i * lineHeight,
                    sr.getScaledWidth() - 1, bgY + i * lineHeight + lineHeight - 1,
                    accentRGB);
        }

        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String fmt(String s, boolean lower) {
        return lower ? s.toLowerCase() : s;
    }

    private int getSlider(HUD hud, String name, int def) {
        for (myau.module.Setting s : hud.getSettings()) {
            if (s instanceof SliderSetting && s.getName().equals(name))
                return (int)((SliderSetting) s).getValue();
        }
        return def;
    }

    private boolean getBool(HUD hud, String name, boolean def) {
        for (myau.module.Setting s : hud.getSettings()) {
            if (s instanceof BooleanSetting && s.getName().equals(name))
                return ((BooleanSetting) s).getValue();
        }
        return def;
    }
}
