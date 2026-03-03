package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.DiscordIPC;
import myau.util.ServerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.EnumChatFormatting;

public class DiscordRPC extends Module {

    // Replace with your Discord application client ID
    private static final long CLIENT_ID = 1478309687198875700L;

    private final Minecraft mc = Minecraft.getMinecraft();
    private DiscordIPC ipc;
    private int tick = 0;
    private long sessionStart;
    private String lastDetails = "";
    private String lastState   = "";

    public DiscordRPC() {
        super("Discord RPC", false);
    }

    @Override
    public void onEnabled() {
        sessionStart = System.currentTimeMillis() / 1000L;
        connectAsync();
    }

    @Override
    public void onDisabled() {
        if (ipc != null) {
            final DiscordIPC toClose = ipc;
            new Thread(toClose::close).start();
            ipc = null;
        }
        lastDetails = "";
        lastState   = "";
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        tick++;

        // Retry connection every ~15s if disconnected
        if (ipc == null || !ipc.isConnected()) {
            if (tick % 300 == 0) connectAsync();
            return;
        }

        // Update every 4s
        if (tick % 80 != 0) return;

        String details = buildDetails();
        String state   = buildState();
        if (details.equals(lastDetails) && state.equals(lastState)) return;
        lastDetails = details;
        lastState   = state;

        final String d = details, s = state;
        final long ts = sessionStart;
        new Thread(() -> ipc.setActivity(d, s, ts)).start();
    }

    private void connectAsync() {
        new Thread(() -> {
            ipc = new DiscordIPC(CLIENT_ID);
            ipc.connect();
        }).start();
    }

    private String buildDetails() {
        if (mc.thePlayer == null || mc.theWorld == null) return "In Menu";
        ServerData sd = mc.getCurrentServerData();
        if (sd == null) return "Singleplayer";
        String ip = sd.serverIP.toLowerCase();
        if (ip.contains("hypixel")) return "Hypixel - " + detectHypixelGame();
        return sd.serverIP;
    }

    private String detectHypixelGame() {
        if (!ServerUtil.isHypixel()) return "Lobby";
        for (String line : ServerUtil.getScoreboardLines()) {
            String c = EnumChatFormatting.getTextWithoutFormattingCodes(line).trim();
            if (c.contains("THE PIT"))        return "The Pit";
            if (c.contains("BED WARS"))       return "BedWars";
            if (c.contains("SKYWARS"))        return "SkyWars";
            if (c.contains("MURDER MYSTERY")) return "Murder Mystery";
            if (c.contains("DUELS"))          return "Duels";
            if (c.contains("BUILD BATTLE"))   return "Build Battle";
            if (c.contains("UHC"))            return "UHC";
            if (c.contains("SPEED UHC"))      return "Speed UHC";
            if (c.contains("BLITZ"))          return "Blitz SG";
            if (c.contains("ARCADE"))         return "Arcade";
            if (c.contains("HOUSING"))        return "Housing";
            if (c.contains("MEGA WALLS"))     return "Mega Walls";
            if (c.contains("WARLORDS"))       return "Warlords";
            if (c.contains("SMASH HEROES"))   return "Smash Heroes";
            if (c.contains("TNT GAMES"))      return "TNT Games";
            if (c.contains("VAMPIREZ"))       return "VampireZ";
            if (c.contains("QUAKE"))          return "Quakecraft";
        }
        return "Lobby";
    }

    private String buildState() {
        if (mc.thePlayer == null) return "";
        Pit pit = (Pit) myau.Myau.moduleManager.getModule(Pit.class);
        if (pit != null && pit.isEnabled() && pit.streak.getValue()) {
            return "Streak: " + pit.stKills + "K / " + pit.stAssists + "A";
        }
        return "Playing as " + mc.thePlayer.getName();
    }
}
