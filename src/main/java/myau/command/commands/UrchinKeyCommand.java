package myau.command.commands;

import myau.command.Command;
import myau.ui.intel.IntelManager;

/**
 * .urchinkey <api-key>  — set Urchin API key
 * .urchinkey            — show whether a key is set
 */
public class UrchinKeyCommand extends Command {

    public UrchinKeyCommand() {
        super("urchinkey", "ukey");
        setDescription("Set your Urchin API key for cheater detection. Usage: .urchinkey <key>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            if (IntelManager.urchinApiKey.isEmpty()) {
                reply("&cNo Urchin key set. Use &f.urchinkey <key> &cto set one.");
            } else {
                String key = IntelManager.urchinApiKey;
                String masked = key.substring(0, Math.min(4, key.length()))
                        + "****"
                        + key.substring(Math.max(0, key.length() - 4));
                reply("&7Urchin key set: &f" + masked);
            }
            return;
        }
        IntelManager.urchinApiKey = args[0].trim();
        reply("&aUrchin API key set.");
    }
}
