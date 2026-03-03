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

        int accentRGB  = hud.getColor(System.currentTimeMillis()).getRGB() | 0xFF000000;
        int nameRGB    = hud.getListColor().getRGB() | 0xFF000000;
        int lineHeight = mc.fontRendererObj.FONT_HEIGHT + 1;
        boolean shadow = hud.shadow.getValue();

        // ── Build rows ────────────────────────────────────────────────────────
        // Each row: [displayName, suffix, isPit, isSubmodule]
        List<String[]> rows = new ArrayList<>();

        // Pit block first — "pit" header + indented submodules
        Pit pit = (Pit) Myau.moduleManager.getModule(Pit.class);
        if (pit != null && pit.isEnabled()) {
            // Collect active submodules
            List<String> activeSubs = new ArrayList<>();
            for (myau.module.Setting s : pit.getSettings()) {
                if (s instanceof BooleanSetting && !s.getName().startsWith(" ")) {
                    BooleanSetting bs = (BooleanSetting) s;
                    if (bs.getValue()) activeSubs.add(s.getName().toLowerCase());
                }
            }
            if (!activeSubs.isEmpty()) {
                // "pit" header row
                rows.add(new String[]{ "pit", "", "module", "false" });
                // submodule rows indented with a space prefix for width calculation
                for (String sub : activeSubs) {
                    rows.add(new String[]{ sub, "", "pit", "true" });
                }
            } else {
                rows.add(new String[]{ "pit", "", "module", "false" });
            }
        }

        // All other enabled non-hidden modules (excluding Pit itself)
        List<Module> others = new ArrayList<>();
        for (Module m : Myau.moduleManager.modules.values()) {
            if (m.isEnabled() && !m.isHidden() && !(m instanceof Pit)) others.add(m);
        }
        // Sort by display width descending
        others.sort(Comparator.comparingInt((Module m) -> {
            String[] s = m.getSuffix();
            String suf = s.length > 0 ? " " + s[0].toLowerCase() : "";
            return mc.fontRendererObj.getStringWidth(m.getName().toLowerCase() + suf);
        }).reversed());

        for (Module m : others) {
            String[] suffArr = m.getSuffix();
            String suf = suffArr.length > 0 ? " " + suffArr[0].toLowerCase() : "";
            rows.add(new String[]{ m.getName().toLowerCase(), suf, "module", "false" });
        }

        // ── Render ────────────────────────────────────────────────────────────
        for (int i = 0; i < rows.size(); i++) {
            String name      = rows.get(i)[0];
            String suffix    = rows.get(i)[1];
            boolean isPit    = rows.get(i)[2].equals("pit");
            boolean isSub    = rows.get(i)[3].equals("true");

            // Submodules get a small indent visually
            String display = isSub ? "  " + name : name;
            int nameW  = mc.fontRendererObj.getStringWidth(display);
            int totalW = nameW + mc.fontRendererObj.getStringWidth(suffix);
            int textX  = sr.getScaledWidth() - totalW - 2;
            int textY  = 4 + i * lineHeight;

            int color = isPit ? accentRGB : nameRGB;
            mc.fontRendererObj.drawString(display, textX, textY, color, shadow);

            if (!suffix.isEmpty()) {
                mc.fontRendererObj.drawString(suffix, textX + nameW, textY, accentRGB, shadow);
            }
        }
    }
}
