package myau.ui.hud;

import myau.Myau;
import myau.module.Module;
import myau.module.modules.HUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ArraylistHUD {
    private final Minecraft mc = Minecraft.getMinecraft();

    public void render() {
        ScaledResolution sr = new ScaledResolution(mc);
        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
        if (hud == null || !hud.isEnabled()) return;

        Color accentColor = hud.getColor(System.currentTimeMillis());
        Color nameColor   = hud.getListColor();

        // Build sorted list of enabled non-hidden modules
        List<Module> enabled = new ArrayList<>();
        for (Module m : Myau.moduleManager.modules.values()) {
            if (m.isEnabled() && !m.isHidden()) enabled.add(m);
        }

        // Sort by display width descending so longest is at top
        enabled.sort(Comparator.comparingInt((Module m) -> {
            String[] suffix = m.getSuffix();
            String suffixStr = suffix.length > 0 ? " " + suffix[0] : "";
            return mc.fontRendererObj.getStringWidth(
                    m.getName().toLowerCase() + suffixStr.toLowerCase());
        }).reversed());

        // 1px gap between lines — as tight as possible while staying readable
        int lineHeight = mc.fontRendererObj.FONT_HEIGHT + 1;
        boolean shadow = hud.shadow.getValue();

        for (int i = 0; i < enabled.size(); i++) {
            Module module = enabled.get(i);

            String name      = module.getName().toLowerCase();
            String[] suffArr = module.getSuffix();
            String suffix    = suffArr.length > 0 ? " " + suffArr[0].toLowerCase() : "";

            int nameW  = mc.fontRendererObj.getStringWidth(name);
            int totalW = nameW + mc.fontRendererObj.getStringWidth(suffix);

            // Right-align with a 2px margin from the screen edge
            int textX = sr.getScaledWidth() - totalW - 2;
            int textY = 4 + i * lineHeight;

            // Module name in the configurable list color
            mc.fontRendererObj.drawString(name, textX, textY, nameColor.getRGB() | 0xFF000000, shadow);

            // Suffix in HUD accent color
            if (!suffix.isEmpty()) {
                mc.fontRendererObj.drawString(suffix, textX + nameW, textY,
                        accentColor.getRGB() | 0xFF000000, shadow);
            }
        }
    }
}