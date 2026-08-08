package myau.module.modules;

import myau.module.Module;
import myau.ui.clickgui.ModuleRegistry;
import myau.ui.clickgui.VapeClickGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

public class GuiModule extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        ModuleRegistry.init();

        VapeClickGui gui = new VapeClickGui(
            Arrays.asList("Combat", "Movement", "Player", "Render", "Misc", "Blatant"),
            Arrays.asList(
                ModuleRegistry.combatModules,
                ModuleRegistry.movementModules,
                ModuleRegistry.playerModules,
                ModuleRegistry.renderModules,
                ModuleRegistry.miscModules,
                ModuleRegistry.blatantModules
            )
        );

        mc.displayGuiScreen(gui);
    }
}
