package myau.command.commands;

import myau.command.Command;
import myau.ui.intel.IntelDebugGui;
import myau.ui.intel.IntelManager;
import net.minecraft.client.Minecraft;

public class IntelDebugCommand extends Command {

    public IntelDebugCommand() {
        super("inteldebug", "idebug");
        setDescription("Shows live stat-fetch debug log, or queries a specific player.");
    }

    @Override
    public void execute(String[] args) {
        if (args.length > 0 && args[0].equals("clear")) {
            synchronized (IntelManager.debugLog) { IntelManager.debugLog.clear(); }
            reply("&aDebug log cleared.");
            return;
        }

        // If a name is given, trigger a fresh fetch for that player so log fills up
        if (args.length > 0) {
            String name = args[0];
            reply("&7Triggering fetch for &f" + name + "&7 — run &f.idebug&7 again in 3s to see results.");
            IntelManager.getInstance().addManualPlayer(name);
        }

        // Show current log contents
        synchronized (IntelManager.debugLog) {
            String content = IntelManager.debugLog.isEmpty()
                    ? "No debug output yet.\nTry: .idebug <playername>"
                    : String.join("\n", IntelManager.debugLog);
            Minecraft.getMinecraft().addScheduledTask(() ->
                    Minecraft.getMinecraft().displayGuiScreen(
                            new IntelDebugGui("Intel Debug Log", content)));
        }
    }
}
