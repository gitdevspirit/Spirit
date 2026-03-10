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
        // Text color - orange from the image (FF8855 area)
        int nameRGB = hud.getListColor().getRGB() | 0xFF000000;
        // Details color - gray from the image
        int detailRGB = hud.getColor(System.currentTimeMillis()).getRGB() | 0xFF000000;
        
        boolean shadow = hud.shadow.getValue();
        int    padX        = (int) hud.alPadX.getValue();
        int    padY        = (int) hud.alPadY.getValue();
        float  rounding    = (float) hud.alRounding.getValue();
        boolean lowercase  = hud.alLowercase.getValue();
        boolean showDetail = hud.alShowDetails.getValue();
        boolean boundOnly  = hud.alBoundOnly.getValue();
        boolean drawBg     = hud.alBackground.getValue();

        int fontH      = mc.fontRendererObj.FONT_HEIGHT;
        int lineHeight = fontH + padY * 2;

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

        // Sort by width (longest first)
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

        // ── Calculate max width ───────────────────────────────────────────────
        int maxW = 0;
        for (Row r : rows) {
            String display = (r.isPitSub ? "  " : "") + r.name;
            int w = mc.fontRendererObj.getStringWidth(display + r.detail);
            if (w > maxW) maxW = w;
        }

        // ── Draw background ───────────────────────────────────────────────────
        int bgW = maxW + padX * 2;
        int bgH = rows.size() * lineHeight;
        int bgX = sr.getScaledWidth() - bgW - 2;
        int bgY = 4;

        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();

        if (drawBg) {
            // More opaque background like in the image
            int bgColor = 0xAA000000; // Semi-transparent black
            if (rounding >= 1) {
                RoundedUtils.drawRoundedRect(bgX, bgY, bgW, bgH, rounding, bgColor);
            } else {
                net.minecraft.client.gui.Gui.drawRect(bgX, bgY, bgX + bgW, bgY + bgH, bgColor);
            }
        }

        // ── Render rows ───────────────────────────────────────────────────────
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            String display = (r.isPitSub ? "  " : "") + r.name;
            int nameW = mc.fontRendererObj.getStringWidth(display);
            int detailW = mc.fontRendererObj.getStringWidth(r.detail);
            int totalW = nameW + detailW;
            
            int rowY = bgY + i * lineHeight;
            
            // Right-align text within the background
            int textX = bgX + bgW - totalW - padX;
            int textY = rowY + padY;
            
            // Module name in orange (or custom list color)
            int color = r.isPitSub ? detailRGB : nameRGB;
            mc.fontRendererObj.drawString(display, textX, textY, color, shadow);
            
            // Details in gray (or custom accent color)
            if (!r.detail.isEmpty()) {
                mc.fontRendererObj.drawString(r.detail, textX + nameW, textY, detailRGB, shadow);
            }
            
            // Small colored bar on the right edge
            net.minecraft.client.gui.Gui.drawRect(
                sr.getScaledWidth() - 2, rowY,
                sr.getScaledWidth(), rowY + lineHeight,
                detailRGB
            );
        }

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String fmt(String s, boolean lower) {
        return lower ? s.toLowerCase() : s;
    }
}
