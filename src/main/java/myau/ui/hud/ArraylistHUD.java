package myau.ui.hud;

import myau.Myau;
import myau.module.Module;
import myau.module.BooleanSetting;
import myau.font.CFontRenderer;
import myau.font.FontProcess;
import myau.module.modules.HUD;
import myau.module.modules.Pit;
import myau.ui.clickgui.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ArraylistHUD {
    private final Minecraft mc = Minecraft.getMinecraft();
    private HUD hud;

    private static final class Row {
        final String name;
        final String detail;
        final boolean isPitSub;

        Row(String name, String detail, boolean isPitSub) {
            this.name = name;
            this.detail = detail;
            this.isPitSub = isPitSub;
        }
    }

    public void render() {
        ScaledResolution sr = new ScaledResolution(mc);
        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
        this.hud = hud;

        if (hud == null || !hud.isEnabled()) return;

        int detailRGB = hud.getColor(System.currentTimeMillis()).getRGB() | 0xFF000000;

        boolean shadow = hud.shadow.getValue();
        int padX = (int) hud.alPadX.getValue();
        int padY = (int) hud.alPadY.getValue();
        float rounding = (float) hud.alRounding.getValue();
        boolean lowercase = hud.alLowercase.getValue();
        boolean showDetail = hud.alShowDetails.getValue();
        boolean boundOnly = hud.alBoundOnly.getValue();
        boolean drawBg = hud.alBackground.getValue();

        int fontH = fontHeight();
        int lineHeight = fontH + padY * 2;

        List<Row> rows = new ArrayList<>();

        Pit pit = (Pit) Myau.moduleManager.getModule(Pit.class);

        if (pit != null && pit.isEnabled() && (!boundOnly || pit.getKey() != 0)) {
            List<String> subs = new ArrayList<>();

            for (myau.module.Setting setting : pit.getSettings()) {
                if (setting instanceof BooleanSetting && !setting.getName().startsWith(" ")) {
                    if (((BooleanSetting) setting).getValue()) {
                        subs.add(fmt(setting.getName(), lowercase));
                    }
                }
            }

            rows.add(new Row(fmt("pit", lowercase), "", false));

            for (String sub : subs) {
                rows.add(new Row(sub, "", true));
            }
        }

        List<Module> others = new ArrayList<>();

        for (Module module : Myau.moduleManager.modules.values()) {
            if (!module.isEnabled() || module.isHidden() || module instanceof Pit) continue;
            if (boundOnly && module.getKey() == 0) continue;

            others.add(module);
        }

        if (hud.alAlphabeticalSort.getValue()) {
            others.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        } else {
            others.sort(Comparator.comparingInt((Module module) -> {
                String detail = showDetail && module.getSuffix().length > 0
                        ? " " + fmt(module.getSuffix()[0], lowercase)
                        : "";

                return fontWidth(
                        fmt(module.getName(), lowercase) + detail
                );
            }).reversed());
        }

        for (Module module : others) {
            String detail = showDetail && module.getSuffix().length > 0
                    ? " " + fmt(module.getSuffix()[0], lowercase)
                    : "";

            rows.add(new Row(fmt(module.getName(), lowercase), detail, false));
        }

        if (rows.isEmpty()) return;

        int maxW = 0;

        for (Row row : rows) {
            String display = (row.isPitSub ? "  " : "") + row.name;
            int width = fontWidth(display + row.detail);

            if (width > maxW) {
                maxW = width;
            }
        }

        int bgW = maxW + padX * 2;
        int offsetX = (int) hud.alPosX.getValue();
        int bgY = (int) hud.alPosY.getValue();
        boolean left = hud.alLeft.getValue();
        int bgX = left ? offsetX : sr.getScaledWidth() - bgW - offsetX;
        int rowGap = (int) hud.alSpacing.getValue();

        int bgColor = rgba(
                hud.alBgRed.getValue(),
                hud.alBgGreen.getValue(),
                hud.alBgBlue.getValue(),
                hud.alBgAlpha.getValue()
        );

        int gradientColor = rgba(
                hud.alGradRed.getValue(),
                hud.alGradGreen.getValue(),
                hud.alGradBlue.getValue(),
                hud.alBgAlpha.getValue()
        );

        int panelHeight = rows.size() * lineHeight + Math.max(0, rows.size() - 1) * rowGap;

        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();

        // ── Single unified panel, instead of a separate floating box per row ──
        if (drawBg) {
            if (rounding >= 1) {
                RoundedUtils.drawRoundedRect(bgX, bgY, bgW, panelHeight, rounding, bgColor);
            } else {
                net.minecraft.client.gui.Gui.drawRect(bgX, bgY, bgX + bgW, bgY + panelHeight, bgColor);
            }

            if (hud.alGradient.getValue()) {
                drawVerticalGradient(bgX, bgY, bgX + bgW, bgY + panelHeight, bgColor, gradientColor);
            }

            int outlineMode = hud.alOutline.getIndex();

            if (outlineMode == 1) {
                drawPanelOutline(bgX, bgY, bgW, panelHeight, rounding);
            } else if (outlineMode == 2) {
                int spineX = left ? bgX : bgX + bgW - 2;
                int spineInset = Math.min((int) Math.ceil(rounding), lineHeight / 2);

                net.minecraft.client.gui.Gui.drawRect(
                        spineX, bgY + spineInset, spineX + 2, bgY + panelHeight - spineInset,
                        hud.getListColor().getRGB() | 0xFF000000
                );
            }
        }

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);

            // Per-row wave phase, so Gradient/Rainbow color modes ripple down
            // the list instead of every row flashing the exact same color.
            int rowNameRGB = hud.getListColor(i * 30f).getRGB() | 0xFF000000;

            String display = (row.isPitSub ? "  " : "") + row.name;
            int nameW = fontWidth(display);
            int detailW = fontWidth(row.detail);
            int totalW = nameW + detailW;

            int rowY = bgY + i * (lineHeight + rowGap);

            // Subtle divider between rows within the same panel.
            if (drawBg && i > 0) {
                net.minecraft.client.gui.Gui.drawRect(
                        bgX + 4, rowY - Math.max(1, rowGap / 2),
                        bgX + bgW - 4, rowY - Math.max(1, rowGap / 2) + 1,
                        0x14FFFFFF
                );
            }

            int textX = left ? bgX + padX : bgX + bgW - totalW - padX;
            int textY = rowY + padY;

            int textColor = row.isPitSub ? detailRGB : rowNameRGB;

            drawFontString(display, textX, textY, textColor, shadow);

            if (!row.detail.isEmpty()) {
                drawFontString(row.detail, textX + nameW, textY, detailRGB, shadow);
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
    }

    private String fmt(String value, boolean lower) {
        return lower ? value.toLowerCase() : value;
    }

    // ── Font resolution (Vanilla or one of the bundled TTF fonts) ──────────────

    private CFontRenderer getCustomFont() {
        if (hud == null || hud.alFont.getIndex() == 0) return null; // 0 = Vanilla

        String key;
        switch (hud.alFont.getIndex()) {
            case 1: key = "client"; break;
            case 2: key = "bold";   break;
            case 3: key = "arial";  break;
            case 4: key = "apple";  break;
            case 5: key = "sans";   break;
            case 6: key = "noto";   break;
            case 7: key = "tahoma"; break;
            case 8: key = "sf-regular"; break;
            case 9: key = "sf-bold";    break;
            default: return null;
        }

        return FontProcess.getScaledFont(key, (float) hud.alFontScale.getValue());
    }

    private int fontWidth(String text) {
        CFontRenderer custom = getCustomFont();
        return custom != null ? custom.getStringWidth(text) : mc.fontRendererObj.getStringWidth(text);
    }

    private int fontHeight() {
        CFontRenderer custom = getCustomFont();
        return custom != null ? custom.FONT_HEIGHT : mc.fontRendererObj.FONT_HEIGHT;
    }

    private void drawFontString(String text, float x, float y, int color, boolean shadow) {
        CFontRenderer custom = getCustomFont();

        if (custom != null) {
            if (shadow) {
                custom.drawStringWithShadow(text, x, y, color);
            } else {
                custom.drawString(text, x, y, color);
            }
        } else {
            mc.fontRendererObj.drawString(text, x, y, color, shadow);
        }
    }

    private static int rgba(double r, double g, double b, double a) {
        return ((int) a & 255) << 24
                | ((int) r & 255) << 16
                | ((int) g & 255) << 8
                | ((int) b & 255);
    }

    private void drawVerticalGradient(int left, int top, int right, int bottom, int start, int end) {
        float startRed = ((start >> 16) & 255) / 255f;
        float startGreen = ((start >> 8) & 255) / 255f;
        float startBlue = (start & 255) / 255f;
        float startAlpha = ((start >>> 24) & 255) / 255f;

        float endRed = ((end >> 16) & 255) / 255f;
        float endGreen = ((end >> 8) & 255) / 255f;
        float endBlue = (end & 255) / 255f;
        float endAlpha = ((end >>> 24) & 255) / 255f;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();

        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );

        GL11.glBegin(GL11.GL_QUADS);

        GL11.glColor4f(startRed, startGreen, startBlue, startAlpha);
        GL11.glVertex2f(left, top);
        GL11.glVertex2f(right, top);

        GL11.glColor4f(endRed, endGreen, endBlue, endAlpha);
        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(left, bottom);

        GL11.glEnd();

        GlStateManager.enableTexture2D();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private void drawPanelOutline(int x, int y, int width, int height, float radius) {
        RoundedUtils.drawRoundedRect(x - 1, y - 1, width + 2, height + 2, radius + 1, 0x40FFFFFF);
    }
}
