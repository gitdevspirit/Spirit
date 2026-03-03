package myau.module.modules;

import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.pipe.PipeStatus;
import myau.event.EventTarget;
import myau.events.TickEvent;
import myau.event.types.EventType;
import myau.module.Category;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.util.ServerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public class DiscordRPC extends Module {

    private static final long APP_ID = 1226739642587750420L; // Spirit client app id placeholder

    private final Minecraft mc = Minecraft.getMinecraft();
    private IPCClient client;
    private boolean connected = false;
    private int tickCount = 0;
    private long sessionStart = System.currentTimeMillis() / 1000L;

    // Detected game state
    private String lastState   = "";
    private String lastDetails = "";

    public DiscordRPC() {
        super("Discord RPC", "Shows Spirit client presence in Discord.", Category.MISC);
    }

    @Override
    public void onEnabled() {
        sessionStart = System.currentTimeMillis() / 1000L;
        connect();
    }

    @Override
    public void onDisabled() {
        disconnect();
    }

    private void connect() {
        try {
            client = new IPCClient(APP_ID);
            client.setListener(new IPCListener() {});
            client.connect();
            connected = true;
            updatePresence();
        } catch (Exception e) {
            connected = false;
        }
    }

    private void disconnect() {
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
            client = null;
        }
        connected = false;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;

        // Reconnect if pipe died
        if (!connected || client == null || client.getStatus() == PipeStatus.DISCONNECTED) {
            tickCount++;
            if (tickCount % 200 == 0) connect(); // retry every ~10s
            return;
        }

        // Update presence every 4 seconds (80 ticks)
        tickCount++;
        if (tickCount % 80 != 0) return;

        updatePresence();
    }

    private void updatePresence() {
        if (!connected || client == null) return;

        String details = buildDetails();
        String state   = buildState();

        // Only send update if something changed
        if (details.equals(lastDetails) && state.equals(lastState)) return;
        lastDetails = details;
        lastState   = state;

        try {
            RichPresence.Builder rp = new RichPresence.Builder();
            rp.setDetails(details);
            rp.setState(state);
            rp.setStartTimestamp(sessionStart);
            rp.setLargeImage("spirit_logo", "Spirit");
            client.sendRichPresence(rp.build());
        } catch (Exception ignored) {}
    }

    private String buildDetails() {
        if (mc.thePlayer == null || mc.theWorld == null) return "In Menu";

        // Detect server
        ServerData sd = mc.getCurrentServerData();
        if (sd == null) return "Singleplayer";

        String ip = sd.serverIP.toLowerCase();
        if (ip.contains("hypixel")) {
            return "Hypixel — " + detectHypixelGame();
        }
        return sd.serverIP;
    }

    private String detectHypixelGame() {
        if (!ServerUtil.isHypixel()) return "Lobby";

        java.util.ArrayList<String> lines = ServerUtil.getScoreboardLines();
        for (String line : lines) {
            String clean = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(line).trim();
            if (clean.contains("THE PIT"))          return "The Pit";
            if (clean.contains("BED WARS"))         return "BedWars";
            if (clean.contains("SKYWARS"))          return "SkyWars";
            if (clean.contains("MURDER MYSTERY"))   return "Murder Mystery";
            if (clean.contains("DUELS"))            return "Duels";
            if (clean.contains("BUILD BATTLE"))     return "Build Battle";
            if (clean.contains("HOUSING"))          return "Housing";
            if (clean.contains("ARCADE"))           return "Arcade";
            if (clean.contains("UHC"))              return "UHC";
            if (clean.contains("SPEED UHC"))        return "Speed UHC";
            if (clean.contains("BLITZ"))            return "Blitz SG";
            if (clean.contains("MEGA WALLS"))       return "Mega Walls";
            if (clean.contains("COPS AND CRIMS"))   return "Cops and Crims";
            if (clean.contains("WARLORDS"))         return "Warlords";
            if (clean.contains("SMASH HEROES"))     return "Smash Heroes";
            if (clean.contains("TNT GAMES"))        return "TNT Games";
            if (clean.contains("VAMPIREZ"))         return "VampireZ";
            if (clean.contains("PAINTBALL"))        return "Paintball";
            if (clean.contains("QUAKE"))            return "Quakecraft";
        }
        return "Lobby";
    }

    private String buildState() {
        if (mc.thePlayer == null) return "";

        // In Pit: show streak info
        Pit pit = (Pit) myau.Myau.moduleManager.getModule(Pit.class);
        if (pit != null && pit.isEnabled() && pit.streak.getValue()) {
            return "Streak: " + pit.stKills + "K / " + pit.stAssists + "A";
        }

        // Generic: show player name
        return "Playing as " + mc.thePlayer.getName();
    }
}
