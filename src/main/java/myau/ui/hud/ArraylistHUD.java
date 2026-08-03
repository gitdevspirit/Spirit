package myau.ui.hud;

import myau.Myau;
import myau.module.BooleanSetting;
import myau.module.Module;
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

        if (hud == null || !hud.isEnabled()) return;

        int nameRGB = hud.getListColor().getRGB() | 0xFF000000;
        int detailRGB = hud.getColor(System.currentTimeMillis()).getRGB() | 0xFF000000;

        boolean shadow = hud.shadow.getValue();
        int padX = (int) hud.alPadX.getValue();
        int padY = (int) hud.alPadY.getValue();
        float rounding = (float) hud.alRounding.getValue();
        boolean lowercase = hud.alLowercase.getValue();
        boolean showDetail = hud.alShowDetails.getValue();
        boolean boundOnly = hud.alBoundOnly.getValue();
        boolean drawBg = hud.alBackground.getValue();

        int fontH = mc.fontRendererObj.FONT_HEIGHT;
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

        others.sort(Comparator.comparingInt((Module module) -> {
            String detail = showDetail && module.getSuffix().length > 0
                    ? " " + fmt(module.getSuffix()[0], lowercase)
                    : "";

            return mc.fontRendererObj.getStringWidth(fmt(module.getName(), lowercase) + detail);
        }).reversed());

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
            int width = mc.fontRendererObj.getStringWidth(display + row.detail);
            if (width > maxW) maxW = width;
        }

        int bgW = maxW + padX * 2;
        int offsetX = (int) hud.alPosX.getValue();
        int bgY = (int) hud.alPosY.getValue();
        boolean left = hud.alLeft.getValue();
        int bgX = left ? offsetX : sr.getScaledWidth() - bgW - offsetX;
        int spacing = (int) hud.alSpacing.getValue();

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

        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);

            String display = (row.isPitSub ? "  " : "") + row.name;
            int nameW = mc.fontRendererObj.getStringWidth(display);
            int detailW = mc.fontRendererObj.getStringWidth(row.detail);
            int totalW = nameW + detailW;

            int rowY = bgY + i * (lineHeight + spacing);

            if (drawBg) {
                drawRowBackground(
                        bgX,
                        rowY,
                        bgW,
                        lineHeight,
                        rounding,
                        bgColor,
                        gradientColor,
                        hud.alGradient.getValue()
                );
            }

            int textX = left ? bgX + padX : bgX + bgW - totalW - padX;
            int textY = rowY + padY;

            int textColor = row.isPitSub ? detailRGB : nameRGB;

            mc.fontRendererObj.drawString(display, textX, textY, textColor, shadow);

            if (!row.detail.isEmpty()) {
                mc.fontRendererObj.drawString(row.detail, textX + nameW, textY, detailRGB, shadow);
            }

            if (left) {
                net.minecraft.client.gui.Gui.drawRect(bgX, rowY, bgX + 2, rowY + lineHeight, nameRGB);
            } else {
                net.minecraft.client.gui.Gui.drawRect(
                        bgX + bgW - 2,
                        rowY,
                        bgX + bgW,
                        rowY + lineHeight,
                        nameRGB
                );
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
    }

    private String fmt(String value, boolean lower) {
        return lower ? value.toLowerCase() : value;
    }

    private static int rgba(double r, double g, double b, double a) {
        return ((int) a & 255) << 24
                | ((int) r & 255) << 16
                | ((int) g & 255) << 8
                | ((int) b & 255);
    }

    private void drawRowBackground(
            int x,
            int y,
            int width,
            int height,
            float radius,
            int base,
            int gradient,
            boolean useGradient
    ) {
        if (radius >= 1) {
            RoundedUtils.drawRoundedRect(x, y, width, height, radius, base);
        } else {
            net.minecraft.client.gui.Gui.drawRect(x, y, x + width, y + height, base);
        }

        if (useGradient) {
            int inset = Math.max(1, (int) radius);

            drawHorizontalGradient(x + inset, y, x + width - inset, y + height, base, gradient);
            drawHorizontalGradient(x, y + inset, x + width, y + height - inset, base, gradient);
        }
    }

    private void drawHorizontalGradient(
            int left,
            int top,
            int right,
            int bottom,
            int start,
            int end
    ) {
        float sr = ((start >> 16) & 255) / 255f;
        float sg = ((start >> 8) & 255) / 255f;
        float sb = (start & 255) / 255f;
        float sa = ((start >>> 24) & 255) / 255f;

        float er = ((end >> 16) & 255) / 255f;
        float eg = ((end >> 8) & 255) / 255f;
        float eb = (end & 255) / 255f;
        float ea = ((end >>> 24) & 255) / 255f;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(sr, sg, sb, sa);
        GL11.glVertex2f(left, top);
        GL11.glVertex2f(left, bottom);

        GL11.glColor4f(er, eg, eb, ea);
        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(right, top);
        GL11.glEnd();

        GlStateManager.enableTexture2D();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
}
