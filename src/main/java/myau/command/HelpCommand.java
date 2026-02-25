package myau.command.commands;

import myau.command.Command;
import myau.command.CommandManager;

import java.util.ArrayList;
import java.util.List;

public class HelpCommand extends Command {

    private final CommandManager commandManager;

    public HelpCommand(CommandManager commandManager) {
        super("c");
        this.commandManager = commandManager;
        setDescription("Show all commands. Usage: .c help");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("help")) {
            reply("&7Did you mean &f.c help&7?");
            return;
        }

        reply("&7--- &fCommand List &7(" + CommandManager.PREFIX + ") ---");
        for (Command cmd : commandManager.getCommands()) {
            String aliases = String.join("&8/&f" + CommandManager.PREFIX, cmd.getAliases());
            reply("  &f" + CommandManager.PREFIX + aliases + " &8— &7" + cmd.getDescription());
        }
    }
}
