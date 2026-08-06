package myau.command.commands;

import myau.command.Command;

import java.util.Arrays;
import java.util.Map;

public class BlacklistCommand extends Command {

    public BlacklistCommand() {
        super("blacklist", "bl");
        setDescription("Blacklist a player with a reason, or list your blacklist. "
                + "Usage: .blacklist <ign> <reason> | .blacklist list");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            reply("&cUsage: &f.blacklist <ign> <reason>  &7or&f  .blacklist list");
            return;
        }

        if (args[0].equalsIgnoreCase("list")) {
            Map<String, String> all = BlacklistManager.getInstance().getAllReasons();

            if (all.isEmpty()) {
                reply("&7[Blacklist] &fYour blacklist is empty.");
                return;
            }

            reply("&c[Blacklist] &7(" + all.size() + " players)&f:");

            for (Map.Entry<String, String> entry : all.entrySet()) {
                reply("  &c" + entry.getKey() + " &7— &f" + entry.getValue());
            }

            return;
        }

        if (!args[0].matches("[A-Za-z0-9_]{1,16}")) {
            reply("&cUsage: &f.blacklist <ign> <reason>");
            return;
        }

        String ign = args[0];
        String reason = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "";

        boolean isNew = BlacklistManager.getInstance().add(ign, reason);
        String finalReason = BlacklistManager.getInstance().getReason(ign);

        if (isNew) {
            reply("&c[Blacklist] &fAdded &c" + ign + " &7(" + finalReason + ")&f.");
        } else {
            reply("&c[Blacklist] &fUpdated reason for &c" + ign + " &7(" + finalReason + ")&f.");
        }
    }
}
