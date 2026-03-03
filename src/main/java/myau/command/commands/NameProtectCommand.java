package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.module.modules.NickHider;

public class NameProtectCommand extends Command {

    public NameProtectCommand() {
        super("nick", "nameprotect", "np");
        setDescription("Set a fake name for NickHider. Usage: .nick <name|reset>");
    }

    @Override
    public void execute(String[] args) {
        NickHider nh = (NickHider) Myau.moduleManager.getModule(NickHider.class);
        if (nh == null) { reply("&cNickHider module not found."); return; }

        if (args.length == 0) {
            reply("&7Current fake name: &f" + nh.protectName);
            reply("&7Usage: &f.nick <name|reset>");
            return;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            nh.protectName = "You";
            reply("&7Fake name reset to &fYou&7.");
        } else {
            nh.protectName = args[0];
            reply("&7Fake name set to &f" + args[0] + "&7.");
            if (!nh.isEnabled()) reply("&7Tip: enable &fNickHider &7in the GUI to activate.");
        }
    }
}
