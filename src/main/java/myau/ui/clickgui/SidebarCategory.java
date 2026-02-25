package myau.ui.clickgui;

import myau.module.Module;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

public class SidebarCategory {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private final String name;
    private final List<Module> modules;

    private final int width = 100;
    private final int height = 22;

    public SidebarCategory(String name, List<Module> modules) {
        this.name = name;
        this.modules = modules;
    }

    public void render(int x, int y, int mouseX, int mouseY, boolean selected) {

        Gui.drawRect(x, y, x + width, y + height, 0xFF1A1A1A);

        if (mouseX >= x && mouseX <= x + width &&
            mouseY >= y && mouseY <= y + height) {
            Gui.drawRect(x, y, x + width, y + height, 0x22FFFFFF);
        }

        if (selected) {
            Gui.drawRect(x, y, x + 3, y + height, 0xFF55AAFF);
        }

        mc.fontRendererObj.drawString(
                name,
                x + 12,
                y + 7,
                selected ? 0xFF55AAFF : 0xFFFFFFFF
        );
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return button == 0;
    }

    public String getName() {
        return name;
    }

    public List<Module> getModules() {
        return modules;
    }
}
