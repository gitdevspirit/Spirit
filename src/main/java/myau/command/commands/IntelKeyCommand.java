package myau.command.commands;

import myau.command.Command;
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
                reply("&7Get a key at &fhttps://developer.hypixel.net");
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
        IntelManager.hypixelApiKey = key;
        reply("&aHypixel API key set. &7Use &f.lobbyintel &7or toggle LobbyIntel to scan.");
    }
}
