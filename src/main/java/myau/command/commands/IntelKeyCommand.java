package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.module.modules.LobbyIntel;
import myau.ui.intel.IntelManager;

/**
 * .intelkey <api-key>   — set Hypixel API key for LobbyIntel
 * .intelkey             — show whether a key is set
 */
public class IntelKeyCommand extends Command {

    public IntelKeyCommand() {
        super("intelkey", "ikey");
        setDescription("Set your Hypixel API key for LobbyIntel. Usage: .intelkey <key>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            if (IntelManager.hypixelApiKey.isEmpty()) {
                reply("&cNo API key set. Use &f.intelkey <key> &cto set one.");
                reply("&7Tip: run &e/api new &7on Hypixel then rejoin — it auto-detects too.");
            } else {
                // Show only first/last 4 chars for security
                String key = IntelManager.hypixelApiKey;
                String masked = key.substring(0, Math.min(4, key.length()))
                        + "****"
                        + key.substring(Math.max(0, key.length() - 4));
                reply("&7API key set: &f" + masked);
            }
            return;
        }

        String key = args[0].trim();
        // Basic UUID format validation
        if (!key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            reply("&cThat doesn't look like a valid Hypixel API key.");
            reply("&7Keys look like: &fxxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx");
            reply("&7Get one by running &e/api new &7on Hypixel.");
            return;
        }

        IntelManager.hypixelApiKey = key;
        // Reset invalid-key flag so it retries with the new key
        IntelManager.getInstance().resetInvalidKeyFlag();

        // Persist to config via LobbyIntel property
        try {
            LobbyIntel li = (LobbyIntel) Myau.moduleManager.getModule(LobbyIntel.class);
            if (li != null) {
                li.savedApiKey.setValue(key);
            }
        } catch (Exception ignored) {}

        reply("&aHypixel API key set and saved. &7Use &f.lobbyintel &7or toggle LobbyIntel to scan.");
    }
}
