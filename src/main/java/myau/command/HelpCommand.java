package myau.command.commands;

import myau.command.Command;
import myau.command.CommandManager;

import java.util.List;

/**
 * .c help — lists all registered commands with a short description
 */
public class HelpCommand extends Command {

    private final CommandManager commandManager;

    public HelpCommand(CommandManager commandManager) {
        super("c");
        this.commandManager = commandManager;
    }

    @Override
    public void execute(String[] args) {
        // Only respond to ".c help"
        if (args.length == 0 || !args[0].equalsIgnoreCase("help")) {
            reply("&7Did you mean &f.c help&7?");
            return;
        }

        reply("&7--- &fCommand List &7---");
        for (String line : getHelpLines()) {
            reply(line);
        }
        reply("&7Prefix: &f" + CommandManager.PREFIX);
    }

    private List<String> getHelpLines() {
        java.util.List<String> lines = new java.util.ArrayList<>();

        for (Command cmd : commandManager.getCommands()) {
            String aliases = String.join("&8/&f", cmd.getAliases());
            String desc    = cmd.getDescription();
            lines.add("  &f" + CommandManager.PREFIX + aliases + " &8— &7" + desc);
        }

        return lines;
    }
}
