package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.module.Module;
import myau.util.KeyBindUtil;
import org.lwjgl.input.Keyboard;

/**
 * .b <module> <key>   — bind a module to a key
 * .b <module> none    — clear the bind
 * .b <module>         — show current bind
 * .b                  — list all binds
 */
public class BindCommand extends Command {

    public BindCommand() {
        super("b", "bind");
        setDescription("Bind a module to a key. Usage: .b <module> <key|none>");
    }

    @Override
    public void execute(String[] args) {

        // .b → list all active binds
        if (args.length == 0) {
            reply("&7--- &fBinds &7---");
            boolean any = false;
            for (Module m : Myau.moduleManager.modules.values()) {
                if (m.getKey() != 0) {
                    reply("  &f" + m.getName() + " &8→ &7" + KeyBindUtil.getKeyName(m.getKey()));
                    any = true;
                }
            }
            if (!any) reply("  &7No modules are currently bound.");
            return;
        }

        Module target = Myau.moduleManager.getModule(args[0]);
        if (target == null) {
            reply("&cModule &f" + args[0] + " &cnot found.");
            return;
        }

        // .b <module> → show current bind
        if (args.length == 1) {
            int k = target.getKey();
            if (k == 0) reply("&f" + target.getName() + " &7has no bind.");
            else        reply("&f" + target.getName() + " &7is bound to &f" + KeyBindUtil.getKeyName(k) + "&7.");
            return;
        }

        // .b <module> none/clear/0 → remove bind
        String keyArg = args[1];
        if (keyArg.equalsIgnoreCase("none") || keyArg.equalsIgnoreCase("clear") || keyArg.equals("0")) {
            target.setKey(0);
            reply("&7Cleared bind for &f" + target.getName() + "&7.");
            saveConfig();
            return;
        }

        // .b <module> <key> → set bind
        int keyCode = Keyboard.getKeyIndex(keyArg.toUpperCase());
        if (keyCode == Keyboard.KEY_NONE) {
            reply("&cUnknown key: &f" + keyArg + "&c. Use names like &fR&c, &fF5&c, &fHOME&c, etc.");
            return;
        }

        // Warn if another module already has this key
        for (Module m : Myau.moduleManager.modules.values()) {
            if (m != target && m.getKey() == keyCode) {
                reply("&eWarning: &f" + m.getName() + " &ealready uses that key — overwriting.");
                m.setKey(0);
            }
        }

        target.setKey(keyCode);
        reply("&f" + target.getName() + " &7bound to &f" + KeyBindUtil.getKeyName(keyCode) + "&7.");
        saveConfig();
    }

    private void saveConfig() {
        new myau.config.Config("default", false).save();
    }
}
