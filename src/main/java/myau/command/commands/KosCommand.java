package myau.command.commands;

import myau.command.Command;
import myau.module.modules.Pit;

public class KosCommand extends Command {

    public KosCommand() {
        super("kos");
        setDescription("Manage your KOS list. Usage: .kos add/remove/list/up/down <name>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            reply("&c[KOS] &fCommands: &c.kos add/remove/list/up/down <name>");
            return;
        }

        String action = args[0].toLowerCase();
        String target = args.length > 1 ? args[1] : "";

        switch (action) {
            case "add":
                if (target.isEmpty()) { reply("&c[KOS] &fUsage: .kos add <name>"); return; }
                if (Pit.kosNames.contains(target)) {
                    reply("&c[KOS] &c" + target + " &fis already on your KOS list.");
                } else {
                    Pit.kosNames.add(target);
                    reply("&c[KOS] &fYou have added &c" + target + " &fto your KOS list.");
                }
                break;

            case "remove":
                if (target.isEmpty()) { reply("&c[KOS] &fUsage: .kos remove <name>"); return; }
                boolean removed = Pit.kosNames.removeIf(n -> n.equalsIgnoreCase(target));
                if (removed) reply("&c[KOS] &fYou have removed &c" + target + " &ffrom your KOS list.");
                else         reply("&c[KOS] &c" + target + " &fwas not found on your KOS list.");
                break;

            case "list":
                if (Pit.kosNames.isEmpty()) {
                    reply("&c[KOS] &fYour KOS list is empty.");
                } else {
                    reply("&c[KOS] &fKOS List &7(" + Pit.kosNames.size() + " players)&f:");
                    int i = 1;
                    for (String name : Pit.kosNames) {
                        reply("  &7" + i++ + ". &c" + name);
                    }
                }
                break;

            case "up":
                if (target.isEmpty()) { reply("&c[KOS] &fUsage: .kos up <name>"); return; }
                moveKos(target, -1);
                break;

            case "down":
                if (target.isEmpty()) { reply("&c[KOS] &fUsage: .kos down <name>"); return; }
                moveKos(target, 1);
                break;

            default:
                reply("&c[KOS] &fCommands: &c.kos add/remove/list/up/down <name>");
                break;
        }
    }

    private void moveKos(String target, int dir) {
        java.util.LinkedList<String> ll = (java.util.LinkedList<String>) Pit.kosNames;
        int idx = -1;
        for (int i = 0; i < ll.size(); i++) {
            if (ll.get(i).equalsIgnoreCase(target)) { idx = i; break; }
        }
        if (idx == -1) { reply("&c[KOS] &c" + target + " &fnot found."); return; }
        int newIdx = idx + dir;
        if (newIdx < 0 || newIdx >= ll.size()) {
            reply("&c[KOS] &c" + target + " &fis already at the " + (dir < 0 ? "top" : "bottom") + ".");
            return;
        }
        String s = ll.remove(idx);
        ll.add(newIdx, s);
        reply("&c[KOS] &fMoved &c" + target + (dir < 0 ? " &fup." : " &fdown."));
    }
}
