package myau.ui.clickgui;

import myau.config.Config;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConfigPanel {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final File CONFIG_DIR = new File("./config/Myau/");

    private List<String> configs = new ArrayList<>();
    private String activeConfig = Config.lastConfig;

    private String contextConfig = null;
    private int contextX = 0;
    private int contextY = 0;
    private static final String[] CONTEXT_OPTIONS = {"Load", "Save", "Delete", "Open Folder"};

    public ConfigPanel() {
        refresh();
    }

    public void refresh() {
        configs.clear();
        if (CONFIG_DIR.exists() && CONFIG_DIR.listFiles() != null) {
            for (File f : CONFIG_DIR.listFiles()) {
                if (f.getName().endsWith(".json") && !f.getName().equals("gui.json")) {
                    configs.add(f.getName().replace(".json", ""));
                }
            }
        }
    }

    public void render(int x, int y, int mouseX, int mouseY) {
        if (configs.isEmpty()) {
            resetGL();
            mc.fontRendererObj.drawString("No configs found", x + 8, y + 6, 0xFF666666);
            return;
        }

        int offsetY = y;
        for (String config : configs) {
            boolean selected = config.equals(activeConfig);
            boolean hovered  = mouseX >= x && mouseX <= x + 106 &&
                               mouseY >= offsetY && mouseY <= offsetY + 18;

            // Row background
            int bg = selected ? 0xFF1A3A5C : (hovered ? 0xFF2A2A2A : 0xFF181818);
            drawRect(x, offsetY, x + 106, offsetY + 18, bg);

            // Blue accent bar if selected
            if (selected) {
                drawRect(x, offsetY, x + 3, offsetY + 18, 0xFF55AAFF);
            }

            // Config name — white if selected, grey otherwise
            int textColor = selected ? 0xFF55AAFF : (hovered ? 0xFFDDDDDD : 0xFF888888);
            resetGL();
            mc.fontRendererObj.drawString(config, x + 8, offsetY + 5, textColor);

            offsetY += 20;
        }

        // Context menu — drawn on top
        if (contextConfig != null) {
            int menuW = 90;
            int menuH = CONTEXT_OPTIONS.length * 16 + 4;

            // Background
            drawRect(contextX, contextY, contextX + menuW, contextY + menuH, 0xFF1A1A1A);
            // Border
            drawRect(contextX, contextY, contextX + menuW, contextY + 1, 0xFF333333);
            drawRect(contextX, contextY + menuH - 1, contextX + menuW, contextY + menuH, 0xFF333333);
            drawRect(contextX, contextY, contextX + 1, contextY + menuH, 0xFF333333);
            drawRect(contextX + menuW - 1, contextY, contextX + menuW, contextY + menuH, 0xFF333333);

            for (int i = 0; i < CONTEXT_OPTIONS.length; i++) {
                String option = CONTEXT_OPTIONS[i];
                int optY = contextY + 2 + i * 16;
                boolean optHovered = mouseX >= contextX && mouseX <= contextX + menuW &&
                                     mouseY >= optY && mouseY <= optY + 16;

                if (optHovered) {
                    drawRect(contextX + 2, optY, contextX + menuW - 2, optY + 16, 0xFF2A2A2A);
                }

                int optColor;
                if (option.equals("Delete"))       optColor = 0xFFFF5555;
                else if (option.equals("Save"))    optColor = 0xFF55FF55;
                else if (option.equals("Load"))    optColor = optHovered ? 0xFF55AAFF : 0xFF888888;
                else                               optColor = optHovered ? 0xFFFFFFFF : 0xFF888888;

                resetGL();
                mc.fontRendererObj.drawString(option, contextX + 8, optY + 4, optColor);
            }
        }
    }

    public void mouseClicked(int x, int y, int mouseX, int mouseY, int button) {

        // Context menu open — handle first
        if (contextConfig != null) {
            int menuW = 90;
            int menuH = CONTEXT_OPTIONS.length * 16 + 4;

            // Check if clicked inside menu
            if (mouseX >= contextX && mouseX <= contextX + menuW &&
                mouseY >= contextY && mouseY <= contextY + menuH) {
                for (int i = 0; i < CONTEXT_OPTIONS.length; i++) {
                    int optY = contextY + 2 + i * 16;
                    if (mouseY >= optY && mouseY <= optY + 16) {
                        handleContextOption(CONTEXT_OPTIONS[i], contextConfig);
                        contextConfig = null;
                        return;
                    }
                }
            }
            // Clicked outside — just close
            contextConfig = null;
            return;
        }

        int offsetY = y;
        for (String config : configs) {
            boolean inRow = mouseX >= x && mouseX <= x + 106 &&
                            mouseY >= offsetY && mouseY <= offsetY + 18;

            if (inRow) {
                if (button == 0) {
                    activeConfig = config;
                    new Config(config, false).load();
                } else if (button == 1) {
                    contextConfig = config;
                    // Position menu to the right of the sidebar so it doesn't overlap
                    contextX = x;
                    contextY = mouseY + 4;
                }
                return;
            }
            offsetY += 20;
        }
    }

    private void handleContextOption(String option, String config) {
        switch (option) {
            case "Load":
                activeConfig = config;
                new Config(config, false).load();
                break;

            case "Save":
                new Config(config, false).save();
                break;

            case "Delete":
                File f = new File(CONFIG_DIR, config + ".json");
                if (f.exists()) f.delete();
                if (config.equals(activeConfig)) activeConfig = null;
                refresh();
                break;

            case "Open Folder":
                try {
                    Desktop.getDesktop().open(CONFIG_DIR);
                } catch (Exception e) {
                    System.err.println("[ConfigPanel] Could not open folder: " + e.getMessage());
                }
                break;
        }
    }

    public boolean isContextMenuOpen() {
        return contextConfig != null;
    }

    public void closeContextMenu() {
        contextConfig = null;
    }

    public int getContentHeight() {
        if (configs.isEmpty()) return 20;
        return configs.size() * 20 + 4;
    }

    private void drawRect(int x1, int y1, int x2, int y2, int color) {
        float a = (color >> 24 & 0xFF) / 255f;
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >> 8  & 0xFF) / 255f;
        float b = (color       & 0xFF) / 255f;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(x1, y2);
        GL11.glVertex2d(x2, y2);
        GL11.glVertex2d(x2, y1);
        GL11.glVertex2d(x1, y1);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private void resetGL() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
}
