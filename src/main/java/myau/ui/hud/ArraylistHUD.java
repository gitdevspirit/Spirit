package myau.ui.hud;

import myau.Myau;
import myau.module.Module;
import myau.module.BooleanSetting;
import myau.module.modules.HUD;
import myau.module.modules.Pit;
import myau.ui.clickgui.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ArraylistHUD {
    private final Minecraft mc = Minecraft.getMinecraft();

    private static final long HUD_RAINBOW_PERIOD_MS = 7500L;
    private static final int BACKGROUND_COLOR = new Color(0, 0, 0, 80).getRGB();

    private static final class RenderEntry {
        final Module module;
        final String displayName;
        final String infoText;
        final boolean isPitSub;

        RenderEntry(Module module, String displayName, String infoText, boolean isPitSub) {
            this.module = module;
            this.displayName = displayName;
            this.infoText = infoText;
            this.isPitSub = isPitSub;
        }

        public String getFullText() {
            if (infoText != null && !infoText.isEmpty()) {
                return displayName + " §7" + infoText;
            }
            return displayName;
        }
    }

    public void render() {
        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
        if (hud == null || !hud.isEnabled() || mc.gameSettings.showDebugInfo) return;

        ScaledResolution sr = new ScaledResolution(mc);

        boolean alignRight = !hud.alLeft.getValue();
        boolean lowercase = hud.alLowercase.getValue();
        boolean showInfo = hud.alShowDetails.getValue();
        boolean boundOnly = hud.alBoundOnly.getValue();
        boolean drawBg = hud.alBackground.getValue();
        boolean roundedBg = hud.alRounding.getValue() > 0;
        float backgroundRadius = (float) hud.alRounding.getValue();
        float animationSpeed = 0.1f;
        int colorMode = (int) hud.colorMode.getValue(); // 0: Static, 1: Gradient, 2: Rainbow
        boolean waveIsVertical = hud.waveAxis.getValue() == 0;

        int fontH = mc.fontRendererObj.FONT_HEIGHT;
        int horizontalPadding = (int) hud.alPadX.getValue();
        int verticalPadding = (int) hud.alPadY.getValue();
        int outlineThickness = 1;
        int rowHeight = fontH + verticalPadding * 2;

        float posX = hud.alLeft.getValue() 
                ? (float) hud.alPosX.getValue() 
                : sr.getScaledWidth() - (float) hud.alPosX.getValue();
        float posY = (float) hud.alPosY.getValue();

        List<RenderEntry> entries = getOrganizedEntries(lowercase, showInfo, boundOnly);
        if (entries.isEmpty()) return;

        // Render Background Pass
        if (drawBg) {
            List<double[]> rows = new ArrayList<>();
            float scanY = posY;
            double maxRight = Double.NEGATIVE_INFINITY;

            for (RenderEntry entry : entries) {
                Module module = entry.module;
                if (module != null) {
                    float targetAnim = module.isEnabled() ? 1.0f : 0.0f;
                    module.hudAnimation += (targetAnim - module.hudAnimation) * animationSpeed;
                    if (Math.abs(targetAnim - module.hudAnimation) < 0.01f) {
                        module.hudAnimation = targetAnim;
                    }
                    if (module.hudAnimation < 0.03f && !module.isEnabled()) continue;
                }

                String text = entry.getFullText();
                int textWidth = mc.fontRendererObj.getStringWidth(text);
                float animFactor = module != null ? Math.max(0.05f, module.hudAnimation) : 1.0f;
                int animatedWidth = (int) (textWidth * animFactor);

                float xp = posX;
                if (alignRight) {
                    xp -= animatedWidth;
                }

                double pad = Math.max(1, horizontalPadding);
                double left = Math.floor(xp - pad);
                double right = Math.ceil(xp + animatedWidth + pad);
                double top = Math.floor(scanY);
                double bottom = Math.floor(scanY + rowHeight);

                rows.add(new double[]{left, right, top, bottom});
                maxRight = Math.max(maxRight, right);
                scanY += rowHeight * (module != null ? module.hudAnimation : 1.0f);
            }

            if (!rows.isEmpty()) {
                drawSteppedScanlineBackground(rows, backgroundRadius, BACKGROUND_COLOR, roundedBg, alignRight);
            }
        }

        // Render Modules Pass (Text & Outlines)
        float currentY = posY;
        double verticalWaveAccum = 0.0;
        boolean firstVisibleRow = true;
        String previousText = "";
        double lastOutlineLeft = 0.0;
        double lastOutlineRight = 0.0;
        double lastBackgroundBottom = 0.0;

        try {
            for (RenderEntry entry : entries) {
                Module module = entry.module;
                if (module != null && !module.isEnabled() && module.hudAnimation <= 0.02f) {
                    continue;
                }

                String text = entry.getFullText();
                int originalWidth = mc.fontRendererObj.getStringWidth(text);
                float anim = module != null ? Math.max(0.05f, module.hudAnimation) : 1.0f;
                int moduleWidth = (int) (originalWidth * anim);

                float xPos = posX;
                float textY = currentY + verticalPadding;

                double bgLeft = xPos - horizontalPadding;
                double bgRight = xPos + moduleWidth + horizontalPadding;
                double bgTop = currentY;
                double bgBottom = currentY + rowHeight;
                double outlineLeft = bgLeft - outlineThickness;
                double outlineRight = bgRight + outlineThickness;
                double outlineTop = bgTop - outlineThickness;

                if (alignRight) {
                    xPos -= moduleWidth;
                    bgLeft = xPos - horizontalPadding;
                    bgRight = xPos + moduleWidth + horizontalPadding;
                    outlineLeft = bgLeft - outlineThickness;
                    outlineRight = bgRight + outlineThickness;
                }

                double rowCenterX = (bgLeft + bgRight) * 0.5;
                double wavePhase = hudWavePhase(hud, waveIsVertical, verticalWaveAccum, rowCenterX);
                int color = getHudColor(hud, wavePhase);

                // Top Outline for first row
                if (hud.outline.getValue() == 1.0 && firstVisibleRow) {
                    drawRect(outlineLeft, outlineTop, outlineRight, bgTop, color);
                }

                if (waveIsVertical) {
                    verticalWaveAccum += getVerticalWaveStep(hud);
                }

                firstVisibleRow = false;

                // Connection Outline between rows
                if (hud.outline.getValue() == 1.0 && !previousText.isEmpty()) {
                    double difference = mc.fontRendererObj.getStringWidth(previousText) - moduleWidth;
                    if (alignRight) {
                        double stepEdge = xPos - difference - horizontalPadding - outlineThickness;
                        drawRect(Math.min(stepEdge, bgLeft), outlineTop, Math.max(stepEdge, bgLeft), bgTop, color);
                    } else {
                        double stepEdge = xPos + difference + moduleWidth + horizontalPadding + outlineThickness;
                        drawRect(Math.min(bgRight, stepEdge), outlineTop, Math.max(bgRight, stepEdge), bgTop, color);
                    }
                }

                // Side Outlines
                if (hud.outline.getValue() > 0.0) {
                    if (alignRight) {
                        drawRect(bgRight, bgTop, outlineRight, bgBottom, color);
                    } else {
                        drawRect(outlineLeft, bgTop, bgLeft, bgBottom, color);
                    }
                }

                if (hud.outline.getValue() == 1.0) {
                    if (alignRight) {
                        drawRect(outlineLeft, bgTop, bgLeft, bgBottom, color);
                    } else {
                        drawRect(bgRight, bgTop, outlineRight, bgBottom, color);
                    }
                }

                // Text Rendering
                mc.fontRendererObj.drawString(text, xPos, textY, color, hud.shadow.getValue());

                previousText = text;
                lastOutlineLeft = outlineLeft;
                lastOutlineRight = outlineRight;
                lastBackgroundBottom = bgBottom;
                currentY += rowHeight * (module != null ? module.hudAnimation : 1.0f);
            }

            // Bottom Outline for last row
            if (hud.outline.getValue() == 1.0 && !previousText.isEmpty()) {
                double bottomCenterX = (lastOutlineLeft + lastOutlineRight) * 0.5;
                double bottomPhase = hudWavePhase(hud, waveIsVertical, verticalWaveAccum, bottomCenterX);
                drawRect(lastOutlineLeft, lastBackgroundBottom, lastOutlineRight, lastBackgroundBottom + outlineThickness, getHudColor(hud, bottomPhase));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<RenderEntry> getOrganizedEntries(boolean lowercase, boolean showInfo, boolean boundOnly) {
        List<RenderEntry> entries = new ArrayList<>();

        // Handle Pit module hierarchy
        Pit pit = (Pit) Myau.moduleManager.getModule(Pit.class);
        if (pit != null && pit.isEnabled() && (!boundOnly || pit.getKey() != 0)) {
            entries.add(new RenderEntry(pit, fmt("Pit", lowercase), "", false));
            for (myau.module.Setting setting : pit.getSettings()) {
                if (setting instanceof BooleanSetting && !setting.getName().startsWith(" ")) {
                    if (((BooleanSetting) setting).getValue()) {
                        entries.add(new RenderEntry(null, "  " + fmt(setting.getName(), lowercase), "", true));
                    }
                }
            }
        }

        // Collect other enabled modules
        List<Module> modules = new ArrayList<>();
        for (Module module : Myau.moduleManager.modules.values()) {
            if (!module.isEnabled() || module.isHidden() || module instanceof Pit) continue;
            if (boundOnly && module.getKey() == 0) continue;
            modules.add(module);
        }

        // Sort by text length descending
        modules.sort(Comparator.comparingInt((Module module) -> {
            String detail = showInfo && module.getSuffix().length > 0 ? " " + module.getSuffix()[0] : "";
            return mc.fontRendererObj.getStringWidth(fmt(module.getName(), lowercase) + detail);
        }).reversed());

        for (Module module : modules) {
            String info = showInfo && module.getSuffix().length > 0 ? module.getSuffix()[0] : "";
            entries.add(new RenderEntry(module, fmt(module.getName(), lowercase), fmt(info, lowercase), false));
        }

        return entries;
    }

    private static double hudWavePhase(HUD hud, boolean vertical, double verticalAccum, double rowCenterX) {
        if (vertical) return verticalAccum;
        double waveLengthMult = Math.max(0.5, hud.waveLength.getValue());
        double directionSign = hud.horizontalWaveDirection.getValue() != 0 ? 1 : -1;
        return rowCenterX * (0.35 / waveLengthMult) * directionSign;
    }

    private static double getVerticalWaveStep(HUD hud) {
        double waveLengthMult = Math.max(0.5, hud.waveLength.getValue());
        double directionSign = hud.verticalWaveDirection.getValue() != 0 ? 1 : -1;
        return (12.0 / waveLengthMult) * directionSign;
    }

    private static int getHudColor(HUD hud, double gradientOffset) {
        int mode = (int) hud.colorMode.getValue();
        if (mode == 2) {
            return getRainbowWaveColor(hud, gradientOffset);
        } else if (mode == 1) {
            Color c1 = hud.getListColor();
            Color c2 = hud.getColor(System.currentTimeMillis());
            return getGradientWaveColor(hud, c1, c2, gradientOffset);
        }
        return hud.getListColor().getRGB();
    }

    private static int getGradientWaveColor(HUD hud, Color c1, Color c2, double gradientOffset) {
        double animationProgress = (Math.sin(getAnimatedWaveAngle(hud, gradientOffset)) + 1.0) * 0.5;
        return convertColor(c1, c2, animationProgress).getRGB();
    }

    private static int getRainbowWaveColor(HUD hud, double gradientOffset) {
        double hue = getAnimatedWaveAngle(hud, gradientOffset) / (Math.PI * 2.0);
        hue -= Math.floor(hue);
        return Color.getHSBColor((float) hue, 1.0f, 1.0f).getRGB();
    }

    private static double getAnimatedWaveAngle(HUD hud, double gradientOffset) {
        double speed = Math.max(0.1, hud.waveSpeed.getValue());
        return (double) System.currentTimeMillis() / (double) HUD_RAINBOW_PERIOD_MS * (Math.PI * 2.0) * speed + gradientOffset * 0.12;
    }

    private static Color convertColor(Color c1, Color c2, double progress) {
        int red = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * progress);
        int green = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * progress);
        int blue = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * progress);
        return new Color(Math.max(0, Math.min(255, red)), Math.max(0, Math.min(255, green)), Math.max(0, Math.min(255, blue)));
    }

    private static void drawRect(double left, double top, double right, double bottom, int color) {
        net.minecraft.client.gui.Gui.drawRect((int) left, (int) top, (int) right, (int) bottom, color);
    }

    private String fmt(String value, boolean lower) {
        return lower ? value.toLowerCase() : value;
    }

    private static void drawSteppedScanlineBackground(List<double[]> rows, float radius, int color, boolean rounded, boolean alignRight) {
        if (rows == null || rows.isEmpty()) return;

        if (rounded) {
            float baseAlpha = (float) (color >> 24 & 255) / 255.0f;
            float red = (float) (color >> 16 & 255) / 255.0f;
            float green = (float) (color >> 8 & 255) / 255.0f;
            float blue = (float) (color & 255) / 255.0f;
            int scale = new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();

            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableTexture2D();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer renderer = tessellator.getWorldRenderer();
            renderer.begin(7, DefaultVertexFormats.POSITION_COLOR);

            for (int i = 0; i < rows.size(); ++i) {
                double[] row = rows.get(i);
                double x1 = row[0] * scale;
                double x2 = row[1] * scale;
                double y1 = row[2] * scale;
                double y2 = row[3] * scale;
                double r = Math.max(0.0, Math.min(radius * scale, Math.min(x2 - x1, y2 - y1) / 2.0));

                boolean isFirst = i == 0;
                boolean isLast = i == rows.size() - 1;
                boolean connectedToNext = i < rows.size() - 1 && Math.abs(row[0] - rows.get(i + 1)[0]) < 0.5;
                boolean connectedToPrev = i > 0 && Math.abs(row[0] - rows.get(i - 1)[0]) < 0.5;

                boolean roundTL = alignRight ? (isFirst && !connectedToPrev) : isFirst;
                boolean roundTR = alignRight ? isFirst : (!connectedToPrev && isFirst);
                boolean roundBL = alignRight ? !connectedToNext : isLast;
                boolean roundBR = alignRight ? isLast : !connectedToNext;

                double invS = 1.0 / scale;
                emitQuad(renderer, x1 * invS, y1 * invS, x2 * invS, y2 * invS, red, green, blue, baseAlpha);
            }

            tessellator.draw();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
        } else {
            for (double[] row : rows) {
                drawRect(row[0], row[2], row[1], row[3], color);
            }
        }
    }

    private static void emitQuad(WorldRenderer renderer, double x1, double y1, double x2, double y2, float red, float green, float blue, float alpha) {
        if (alpha <= 0.0f || x2 <= x1 || y2 <= y1) return;
        renderer.pos(x1, y2, 0.0D).color(red, green, blue, alpha).endVertex();
        renderer.pos(x2, y2, 0.0D).color(red, green, blue, alpha).endVertex();
        renderer.pos(x2, y1, 0.0D).color(red, green, blue, alpha).endVertex();
        renderer.pos(x1, y1, 0.0D).color(red, green, blue, alpha).endVertex();
    }
}
