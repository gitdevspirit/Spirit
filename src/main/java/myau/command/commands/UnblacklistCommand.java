package myau.command.commands;

import myau.command.Command;

public class UnblacklistCommand extends Command {

    public UnblacklistCommand() {
        super("unblacklist", "unbl");
        setDescription("Remove a player from your blacklist. Usage: .unblacklist <ign>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1) {
            reply("&cUsage: &f.unblacklist <ign>");
            return;
        }

        boolean removed = BlacklistManager.getInstance().remove(args[0]);

        if (removed) {
            reply("&a[Blacklist] &fRemoved &c" + args[0] + " &ffrom your blacklist.");
        } else {
            reply("&c[Blacklist] &c" + args[0] + " &fwas not on your blacklist.");
        }
    }
}
