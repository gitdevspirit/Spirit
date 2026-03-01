package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.render.BlurShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class InfoHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final DropdownSetting posX           = register(new DropdownSetting("Position X",     0, "Left", "Center", "Right"));
    public final DropdownSetting posY           = register(new DropdownSetting("Position Y",     2, "Top",  "Center", "Bottom"));
    public final SliderSetting   offsetX        = register(new SliderSetting("X Offset",         0, -200, 200, 1));
    public final SliderSetting   offsetY        = register(new SliderSetting("Y Offset",         0, -200, 200, 1));
    public final SliderSetting   scale          = register(new SliderSetting("Scale",           1.0,  0.6, 2.0, 0.05));
    public final SliderSetting   blurStrength   = register(new SliderSetting("Blur Strength",     6,    1,  10,    1));
    public final SliderSetting   cornerRadius   = register(new SliderSetting("Corner Radius",     6,    2,  20,    1));
    public final SliderSetting   bgAlpha        = register(new SliderSetting("Background Alpha", 160,   0, 255,    1));
    public final BooleanSetting  showHealth     = register(new BooleanSetting("Show Health",   true));
    public final DropdownSetting healthMode     = register(new DropdownSetting("Health Mode",   0, "HEARTS", "TAB"));
    public final BooleanSetting  showBlocks     = register(new BooleanSetting("Show Blocks",   true));
    public final BooleanSetting  showHealthIcon = register(new BooleanSetting("Health Icon",   true));
    public final BooleanSetting  showBlockIcon  = register(new BooleanSetting("Block Icon",    true));

    public InfoHUD() { super("InfoHUD", false); }

    // ── Data helpers ──────────────────────────────────────────────────────────

    private String getHealthText() {
        if (healthMode.getIndex() == 1) {
            try {
                Scoreboard sb = mc.theWorld.getScoreboard();
                if (sb != null) {
                    ScoreObjective obj = sb.getObjectiveInDisplaySlot(2);
                    if (obj != null) {
                        Score score = sb.getValueFromObjective(mc.thePlayer.getName(), obj);
                        if (score != null) return String.valueOf(score.getScorePoints());
                    }
                }
            } catch (Exception ignored) {}
        }
        float hp = mc.thePlayer.getHealth();
        return hp == (int) hp ? String.valueOf((int)(hp / 2f)) : String.format("%.1f", hp / 2f);
    }

    private int getHealthColor() {
        float ratio = mc.thePlayer.getHealth() / mc.thePlayer.getMaxHealth();
        if (ratio > 0.6f) return 0xFFFF5555;
        if (ratio > 0.3f) return 0xFFFFAA00;
        return 0xFFFF2222;
    }

    private int getTotalBlocks() {
        int total = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && ItemUtil.isBlock(stack)) total += stack.stackSize;
        }
        return total;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.currentScreen != null) return;

        // Build rows: label → value string + value color
        List<String[]> rows = new ArrayList<>(); // [label, value, colorHex]

        if (showHealth.getValue()) {
            String icon = showHealthIcon.getValue() ? "\u2764 " : "";
            // [icon, value, valueColor, iconColor]
            rows.add(new String[]{ icon, getHealthText(), String.valueOf(getHealthColor()), String.valueOf(getHealthColor()) });
        }

        if (showBlocks.getValue() && ItemUtil.isHoldingBlock()) {
            int blocks = getTotalBlocks();
            int col = blocks > 64 ? 0xFF55FF55 : blocks > 16 ? 0xFFFFAA00 : 0xFFFF5555;
            String icon = showBlockIcon.getValue() ? "\u25A0 " : "";
            rows.add(new String[]{ icon, String.valueOf(blocks), String.valueOf(col), String.valueOf(col) });
        }

        if (rows.isEmpty()) return;

        int lineH      = mc.fontRendererObj.FONT_HEIGHT + 3;
        int padding    = 6;

        // Measure panel
        int maxW = 0;
        for (String[] row : rows) {
            int w = mc.fontRendererObj.getStringWidth(row[0] + row[1]);
            if (w > maxW) maxW = w;
        }
        float panelW = maxW + padding * 2;
        float panelH = rows.size() * lineH + padding * 2 - 3;

        ScaledResolution sr  = new ScaledResolution(mc);
        float scaleFactor    = (float) scale.getValue();
        float baseX, baseY;

        switch (posX.getIndex()) {
            case 0:  baseX = 10.0f; break;
            case 1:  baseX = sr.getScaledWidth()  / 2.0f - panelW * scaleFactor / 2.0f; break;
            default: baseX = sr.getScaledWidth()  - panelW * scaleFactor - 10.0f; break;
        }
        switch (posY.getIndex()) {
            case 0:  baseY = 10.0f; break;
            case 1:  baseY = sr.getScaledHeight() / 2.0f - panelH * scaleFactor / 2.0f; break;
            default: baseY = sr.getScaledHeight() - panelH * scaleFactor - 10.0f; break;
        }
        baseX += (float) offsetX.getValue();
        baseY += (float) offsetY.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.scale(scaleFactor, scaleFactor, 1.0f);
        float dx = baseX / scaleFactor;
        float dy = baseY / scaleFactor;

        // Frosted glass background
        BlurShadowRenderer.renderFrostedGlass(
                dx, dy, panelW, panelH,
                (float)(int) cornerRadius.getValue(),
                (int) blurStrength.getValue(),
                (int) bgAlpha.getValue());

        // Draw rows
        for (int i = 0; i < rows.size(); i++) {
            String[] row      = rows.get(i);
            float    ry       = dy + padding + i * lineH;
            float    rx       = dx + padding;
            int      valColor  = Integer.parseInt(row[2]);
            int      iconColor = Integer.parseInt(row[3]);

            // Icon in its own color (heart = red, block = block count color)
            mc.fontRendererObj.drawStringWithShadow(row[0], rx, ry, iconColor);
            // Value right-aligned in value color
            float valX = dx + panelW - padding - mc.fontRendererObj.getStringWidth(row[1]);
            mc.fontRendererObj.drawStringWithShadow(row[1], valX, ry, valColor);
        }

        GlStateManager.popMatrix();
    }
}
