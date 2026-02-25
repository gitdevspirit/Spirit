package myau.module.modules;

import myau.module.Module;
import myau.ui.clickgui.ModuleRegistry;
import myau.ui.clickgui.Rise6ClickGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class GuiModule extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private Rise6ClickGui clickGui;

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
    }

    @Override
    public void onEnabled() {
        // GUI modules disable themselves immediately
        setEnabled(false);

        // Initialize module lists (replaces old ClickGui constructor)
        ModuleRegistry.init();

        // Create Rise6ClickGui instance using the new registry
        clickGui = new Rise6ClickGui(
                ModuleRegistry.combatModules,
                ModuleRegistry.movementModules,
                ModuleRegistry.playerModules,
                ModuleRegistry.renderModules,
                ModuleRegistry.miscModules
        );

        // Open the GUI
        mc.displayGuiScreen(clickGui);
    }
}
