package myau.module.modules;

import myau.enums.ChatColors;
import myau.module.BooleanSetting;
import myau.module.Module;
import net.minecraft.client.Minecraft;

import java.util.regex.Matcher;

public class NickHider extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // TextProperty has no equivalent in the new system — store the name as a plain field.
    // If you add a TextSetting class later, swap this out.
    public String protectName = "You";

    public final BooleanSetting scoreboard = new BooleanSetting("Scoreboard", true);
    public final BooleanSetting level      = new BooleanSetting("Level",      true);

    public NickHider() {
        super("NickHider", false, true);
        register(scoreboard);
        register(level);
    }

    public String replaceNick(String input) {
        if (input == null || mc.thePlayer == null) return input;
        if (scoreboard.getValue() && input.matches("§7\\d{2}/\\d{2}/\\d{2}(?:\\d{2})?  ?§8.*")) {
            input = input.replaceAll("§8", "§8§k").replaceAll("[^\\x00-\\x7F§]", "?");
        }
        return input.replaceAll(mc.thePlayer.getName(), Matcher.quoteReplacement(ChatColors.formatColor(protectName)));
    }
}