package myau.module.modules;

import myau.event.EventTarget;
import myau.events.TickEvent;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.module.BooleanSetting;
import myau.ui.intel.IntelGui;
import myau.ui.intel.IntelManager;
import net.minecraft.client.Minecraft;

public class LobbyIntel extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting apiKeyNotice = register(new SliderSetting("(Set API key in code)", 0, 0, 0, 0));
    public final BooleanSetting autoScan    = register(new BooleanSetting("Auto Scan on Join", true));

    private final IntelGui gui = new IntelGui();

    public LobbyIntel() {
        super("LobbyIntel", false);
        IntelManager.getInstance().setGui(gui);
    }

    @Override
    public void onEnabled() {
        // Open the GUI
        mc.addScheduledTask(() -> mc.displayGuiScreen(gui));
        // Scan if we have players and haven't yet
        if (IntelManager.getInstance().getPlayers().isEmpty()) {
            IntelManager.getInstance().scanLobby();
        } else {
            gui.setPlayers(IntelManager.getInstance().getPlayers());
        }
        // Toggle back off — this is a keybind-to-open, not a persistent toggle
        setEnabled(false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        // Auto scan when joining a new game (detect via tab list changes)
        // For now a manual trigger via the module toggle
    }
}
