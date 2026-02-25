package myau.command.commands;

import myau.command.Command;
import myau.command.CommandManager;
import myau.config.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * .c help      — list all commands
 * .c l         — list saved configs
 * .c l <name>  — load config (creates it if it doesn't exist)
 * .c s         — save current config
 * .c s <name>  — save to a named config
 */
public class ConfigCommand extends Command {

    private static final File CONFIG_DIR = new File("./config/Myau/");
    private final CommandManager commandManager;

    public ConfigCommand(CommandManager commandManager) {
        super("c");
        this.commandManager = commandManager;
        setDescription("Config + help. Usage: .c <help|l|s> [name]");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            showUsage();
            return;
        }

        switch (args[0].toLowerCase()) {

            // ── Help ──────────────────────────────────────────────────────────
            case "help": {
                reply("&7--- &fCommand List &7(" + CommandManager.PREFIX + ") ---");
                for (Command cmd : commandManager.getCommands()) {
                    String name = CommandManager.PREFIX + cmd.getName();
                    reply("  &f" + name + " &8— &7" + cmd.getDescription());
                }
                break;
            }

            // ── Load ──────────────────────────────────────────────────────────
            case "l": {
                if (args.length == 1) {
                    listConfigs();
                    return;
                }
                String name = args[1];
                Config config = new Config(name, true);
                if (!config.file.exists()) {
                    reply("&7Config &f" + name + "&7 not found — creating it.");
                    config.save();
                } else {
                    config.load();
                }
                break;
            }

            // ── Save ──────────────────────────────────────────────────────────
            case "s": {
                String name = args.length >= 2 ? args[1] : Config.lastConfig;
                if (name == null || name.isEmpty()) name = "default";
                new Config(name, true).save();
                break;
            }

            default:
                showUsage();
                break;
        }
    }

    private void listConfigs() {
        CONFIG_DIR.mkdirs();
        File[] files = CONFIG_DIR.listFiles((dir, n) -> n.endsWith(".json"));
        if (files == null || files.length == 0) {
            reply("&7No configs found.");
            return;
        }

        List<String> names = new ArrayList<>();
        for (File f : files) {
            String n = f.getName().replace(".json", "");
            names.add(n.equals(Config.lastConfig) ? "&a" + n + " &7(current)" : "&f" + n);
        }

        reply("&7--- &fSaved Configs &7---");
        for (String n : names) reply("  " + n);
        reply("&7Use &f.c l <name> &7to load.");
    }

    private void showUsage() {
        reply("&7Usage:");
        reply("  &f.c help       &8— &7show all commands");
        reply("  &f.c l          &8— &7list saved configs");
        reply("  &f.c l <name>   &8— &7load or create a config");
        reply("  &f.c s          &8— &7save current config");
        reply("  &f.c s <name>   &8— &7save to a named config");
    }
}
