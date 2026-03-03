package myau.ui.hud;

import myau.Myau;
import myau.module.Module;
import myau.module.BooleanSetting;
import myau.module.modules.HUD;
import myau.module.modules.Pit;
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

        // Collect active Pit submodules as fake entries
        List<String[]> pitEntries = new ArrayList<>(); // [name, suffix]
        Pit pit = (Pit) Myau.moduleManager.getModule(Pit.class);
        if (pit != null && pit.isEnabled()) {
            for (myau.module.Setting s : pit.getSettings()) {
                if (s instanceof BooleanSetting && !s.getName().startsWith(" ")) {
                    BooleanSetting bs = (BooleanSetting) s;
                    if (bs.getValue()) {
                        pitEntries.add(new String[]{ s.getName().toLowerCase(), "" });
                    }
                }
            }
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

        // Combine: module entries + pit submodule entries, all sorted by width
        List<String[]> allEntries = new ArrayList<>();
        for (Module m : enabled) {
            String[] suffArr = m.getSuffix();
            String suffix = suffArr.length > 0 ? " " + suffArr[0].toLowerCase() : "";
            allEntries.add(new String[]{ m.getName().toLowerCase(), suffix, "module" });
        }
        for (String[] pe : pitEntries) {
            allEntries.add(new String[]{ pe[0], pe[1], "pit" });
        }

        // Sort by total display width descending
        allEntries.sort(java.util.Comparator.comparingInt(
            (String[] e) -> mc.fontRendererObj.getStringWidth(e[0] + e[1])
        ).reversed());

        for (int i = 0; i < allEntries.size(); i++) {
            String[] entry  = allEntries.get(i);
            String name     = entry[0];
            String suffix   = entry[1];
            boolean isPit   = entry[2].equals("pit");

            int nameW  = mc.fontRendererObj.getStringWidth(name);
            int totalW = nameW + mc.fontRendererObj.getStringWidth(suffix);

            int textX = sr.getScaledWidth() - totalW - 2;
            int textY = 4 + i * lineHeight;

            // Pit submodules use accent color, normal modules use list color
            int nColor = isPit ? (accentColor.getRGB() | 0xFF000000) : (nameColor.getRGB() | 0xFF000000);
            mc.fontRendererObj.drawString(name, textX, textY, nColor, shadow);

            if (!suffix.isEmpty()) {
                mc.fontRendererObj.drawString(suffix, textX + nameW, textY,
                        accentColor.getRGB() | 0xFF000000, shadow);
            }
        }
    }
}
