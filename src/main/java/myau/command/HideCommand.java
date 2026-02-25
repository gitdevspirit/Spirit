package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.module.Module;

/**
 * .hide <module|all>  — hide a module from the arraylist
 * .show <module|all>  — show a module in the arraylist
 *
 * Register both separately:
 *   commandManager.register(new HideCommand(true));   // .hide
 *   commandManager.register(new HideCommand(false));  // .show
 */
public class HideCommand extends Command {

    private final boolean hiding;

    /** true = .hide command, false = .show command */
    public HideCommand(boolean hiding) {
        super(hiding ? "hide" : "show");
        this.hiding = hiding;
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            reply("&7Usage: &f." + (hiding ? "hide" : "show") + " <module|all>");
            return;
        }

        if (args[0].equalsIgnoreCase("all")) {
            for (Module m : Myau.moduleManager.modules.values()) {
                m.setHidden(hiding);
            }
            reply(hiding ? "&7All modules hidden from arraylist." : "&7All modules shown in arraylist.");
            saveConfig();
            return;
        }

        Module target = Myau.moduleManager.getModule(args[0]);
        if (target == null) {
            reply("&cModule &f" + args[0] + " &cnot found.");
            return;
        }

        target.setHidden(hiding);
        reply(hiding
            ? "&f" + target.getName() + " &7hidden from arraylist."
            : "&f" + target.getName() + " &7shown in arraylist.");
        saveConfig();
    }

    private void saveConfig() {
        new myau.config.Config("default", false).save();
    }
}
