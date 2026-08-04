package myau.ui.hud;

import myau.module.Module;
import myau.module.ModuleManager;
import myau.module.modules.HUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ArraylistHUD {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void render(HUD hud) {
        if (mc.thePlayer == null || mc.theWorld == null || mc.gameSettings.showDebugInfo) {
            return;
        }

        FontRenderer fontRenderer = mc.fontRendererObj;
        ScaledResolution sr = new ScaledResolution(mc);

        List<Module> modules = new ArrayList<>(ModuleManager.getModules());

        // Sort modules by rendered width descending
        if (hud.alphabeticalSort.getValue()) {
            modules.sort(Comparator.comparing(Module::getNameInHud));
        } else {
            modules.sort((m1, m2) -> {
                String t1 = getRenderText(hud, m1);
                String t2 = getRenderText(hud, m2);
                return Integer.compare(fontRenderer.getStringWidth(t2), fontRenderer.getStringWidth(t1));
            });
        }

        int rowHeight = fontRenderer.FONT_HEIGHT + 4;
        float animationSpeed = hud.animationSpeed.getValue();

        int colorMode = hud.colorMode.getValue(); // 0: Static, 1: Gradient, 2: Rainbow
        boolean waveIsVertical = hud.waveAxis.getValue() == 0;

        float posX = HUD.posX;
        float posY = HUD.posY;
        boolean alignRight = hud.alignRight.getValue();

        // 1. Draw Backgrounds
        if (hud.drawBackground.getValue()) {
            float scanY = posY;
            for (Module module : modules) {
                if (shouldSkip(module)) continue;

                // Animate module inclusion
                float targetAnim = module.isEnabled() ? 1.0f : 0.0f;
                module.hudAnimation += (targetAnim - module.hudAnimation) * animationSpeed;
                if (Math.abs(targetAnim - module.hudAnimation) < 0.01f) {
                    module.hudAnimation = targetAnim;
                }
                if (module.hudAnimation < 0.03f && !module.isEnabled()) continue;

                String text = getRenderText(hud, module);
                int textWidth = fontRenderer.getStringWidth(text);
                float animFactor = Math.max(0.05f, module.hudAnimation);
                int renderWidth = (int) (textWidth * animFactor);

                float renderX = alignRight ? (posX - renderWidth - 4) : posX;
                float renderY = scanY;

                Gui.drawRect((int) renderX, (int) renderY, (int) (renderX + renderWidth + 4), (int) (renderY + rowHeight), new Color(0, 0, 0, 120).getRGB());

                scanY += rowHeight * module.hudAnimation;
            }
        }

        // 2. Render Text and Outlines
        float currentY = posY;
        double verticalWaveAccum = 0.0;
        String previousText = "";
        boolean firstVisibleRow = true;

        for (Module module : modules) {
            if (shouldSkip(module)) continue;

            float targetAnim = module.isEnabled() ? 1.0f : 0.0f;
            if (!hud.drawBackground.getValue()) {
                module.hudAnimation += (targetAnim - module.hudAnimation) * animationSpeed;
                if (Math.abs(targetAnim - module.hudAnimation) < 0.01f) {
                    module.hudAnimation = targetAnim;
                }
            }

            if (module.hudAnimation <= 0.02f && !module.isEnabled()) continue;

            String text = getRenderText(hud, module);
            int textWidth = fontRenderer.getStringWidth(text);
            float anim = Math.max(0.05f, module.hudAnimation);
            int renderWidth = (int) (textWidth * anim);

            float currentX = alignRight ? (posX - renderWidth - 4) : posX;

            double rowCenterX = currentX + renderWidth / 2.0;
            double wavePhase = getWavePhase(hud, verticalWaveAccum, rowCenterX);
            int color = getHudColor(hud, wavePhase);

            if (waveIsVertical) {
                verticalWaveAccum += getVerticalWaveStep(hud);
            }

            // Draw Top Outline Bar
            if (hud.outline.getValue() == 1.0 && firstVisibleRow) {
                Gui.drawRect((int) currentX - 1, (int) currentY - 1, (int) (currentX + renderWidth + 5), (int) currentY, color);
            }

            // Side outline bar
            if (hud.outline.getValue() > 0.0) {
                if (alignRight) {
                    Gui.drawRect((int) (currentX + renderWidth + 4), (int) currentY, (int) (currentX + renderWidth + 5), (int) (currentY + rowHeight), color);
                } else {
                    Gui.drawRect((int) currentX - 1, (int) currentY, (int) currentX, (int) (currentY + rowHeight), color);
                }
            }

            // Full outline side bar
            if (hud.outline.getValue() == 1.0) {
                if (alignRight) {
                    Gui.drawRect((int) currentX - 1, (int) currentY, (int) currentX, (int) (currentY + rowHeight), color);
                } else {
                    Gui.drawRect((int) (currentX + renderWidth + 4), (int) currentY, (int) (currentX + renderWidth + 5), (int) (currentY + rowHeight), color);
                }
            }

            // Render Text
            fontRenderer.drawString(text, currentX + 2, currentY + 2, color, hud.textShadow.getValue());

            previousText = text;
            firstVisibleRow = false;

            currentY += rowHeight * module.hudAnimation;
        }

        // Bottom outline bar
        if (hud.outline.getValue() == 1.0 && !previousText.isEmpty()) {
            int lastWidth = fontRenderer.getStringWidth(previousText);
            float lastX = alignRight ? (posX - lastWidth - 4) : posX;
            Gui.drawRect((int) lastX - 1, (int) currentY, (int) (lastX + lastWidth + 5), (int) (currentY + 1), getHudColor(hud, verticalWaveAccum));
        }
    }

    private static boolean shouldSkip(Module module) {
        return module.isHidden() || (!module.isEnabled() && module.hudAnimation <= 0.01f);
    }

    private static String getRenderText(HUD hud, Module module) {
        String name = module.getNameInHud();
        if (hud.lowercase.getValue()) {
            name = name.toLowerCase();
        }
        if (hud.showInfo.getValue() && !module.getInfo().isEmpty()) {
            name += " \u00a77" + module.getInfo();
        }
        return name;
    }

    private static double getWavePhase(HUD hud, double verticalAccum, double rowCenterX) {
        if (hud.waveAxis.getValue() == 0) {
            return verticalAccum;
        }
        double waveLengthMult = Math.max(0.5, hud.waveLength.getValue());
        double directionSign = hud.horizontalWaveDirection.getValue() != 0 ? 1 : -1;
        return rowCenterX * (0.35 / waveLengthMult) * directionSign;
    }

    private static double getVerticalWaveStep(HUD hud) {
        double waveLengthMult = Math.max(0.5, hud.waveLength.getValue());
        double directionSign = hud.verticalWaveDirection.getValue() != 0 ? 1 : -1;
        return (12.0 / waveLengthMult) * directionSign;
    }

    public static int getHudColor(HUD hud, double gradientOffset) {
        int mode = hud.colorMode.getValue();
        if (mode == 2) {
            return getRainbowColor(hud, gradientOffset);
        } else if (mode == 1) {
            return getGradientColor(hud, hud.hudColor.getValue(), hud.hudColor2.getValue(), gradientOffset);
        } else {
            return hud.hudColor.getValue().getRGB();
        }
    }

    private static int getGradientColor(HUD hud, Color c1, Color c2, double gradientOffset) {
        double angle = getAnimatedWaveAngle(hud, gradientOffset);
        double animProgress = (Math.sin(angle) + 1.0) * 0.5;
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * animProgress);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * animProgress);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * animProgress);
        return new Color(r, g, b).getRGB();
    }

    private static int getRainbowColor(HUD hud, double gradientOffset) {
        double hue = getAnimatedWaveAngle(hud, gradientOffset) / (Math.PI * 2.0);
        hue -= Math.floor(hue);
        return Color.getHSBColor((float) hue, 1.0f, 1.0f).getRGB();
    }

    private static double getAnimatedWaveAngle(HUD hud, double gradientOffset) {
        double speed = Math.max(0.1, hud.waveSpeed.getValue());
        return (System.currentTimeMillis() / 7500.0) * (Math.PI * 2.0) * speed + gradientOffset * 0.12;
    }
}
