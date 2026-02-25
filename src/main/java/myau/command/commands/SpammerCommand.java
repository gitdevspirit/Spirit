package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.module.modules.Spammer;

public class SpammerCommand extends Command {

    public SpammerCommand() {
        super("spammer");
        setDescription("Set the spammer message. Usage: .spammer text <message>");
    }

    @Override
    public void execute(String[] args) {
        Spammer spammer = (Spammer) Myau.moduleManager.getModule(Spammer.class);
        if (spammer == null) {
            reply("&cCould not find Spammer module.");
            return;
        }

        if (args.length == 0) {
            reply("&7Usage: &f.spammer text <message>");
            reply("&7Current text: &f" + spammer.text.getValue());
            return;
        }

        if (args[0].equalsIgnoreCase("text")) {
            if (args.length == 1) {
                reply("&7Current spammer text: &f" + spammer.text.getValue());
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) sb.append(" ");
                sb.append(args[i]);
            }
            String newText = sb.toString();
            spammer.text.setValue(newText);
            reply("&7Spammer text set to: &f" + newText);
            new myau.config.Config("default", false).save();
            return;
        }

        reply("&7Usage: &f.spammer text <message>");
    }
}
