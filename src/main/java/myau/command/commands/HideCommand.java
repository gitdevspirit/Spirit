package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.module.Module;

public class HideCommand extends Command {

    private final boolean hiding;

    public HideCommand(boolean hiding) {
        super(hiding ? "hide" : "show");
        this.hiding = hiding;
        setDescription(hiding
            ? "Hide a module from the arraylist. Usage: .hide <module|all>"
            : "Show a module in the arraylist. Usage: .show <module|all>");
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
        new myau.config.Config(myau.config.Config.lastConfig != null ? myau.config.Config.lastConfig : "default", false).save();
    }
}
