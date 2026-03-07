package myau.ui.intel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches Hypixel + Urchin data for all lobby players async.
 * Singleton — call getInstance() to access.
 */
public class IntelManager {

    private static final IntelManager INSTANCE = new IntelManager();
    public static IntelManager getInstance() { return INSTANCE; }

    // Set your Hypixel API key here or via the module setting
    public static String hypixelApiKey = "";

    private static final String HYPIXEL_URL = "https://api.hypixel.net/player?key=%s&name=%s";
    private static final String URCHIN_URL  = "https://api.urchin.so/user/%s"; // adjust if endpoint differs

    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private volatile boolean fetching = false;

    private final List<IntelPlayer> players = new ArrayList<>();
    private IntelGui gui;

    private IntelManager() {}

    public boolean isFetching() { return fetching; }

    public void setGui(IntelGui gui) { this.gui = gui; }

    /** Call when loading into a game — scans the tab list */
    public void scanLobby() {
        players.clear();
        fetching = true;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) { fetching = false; return; }

        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            String name = info.getGameProfile().getName();
            if (name == null || name.isEmpty()) continue;
            // Detect team from display name color (BW uses colored names)
            String team = detectTeam(info);
            IntelPlayer p = new IntelPlayer(name, team);
            players.add(p);
        }

        if (gui != null) gui.setPlayers(new ArrayList<>(players));

        // Fetch each player async
        for (IntelPlayer p : players) {
            final IntelPlayer fp = p;
            pool.submit(() -> {
                fetchHypixel(fp);
                fetchUrchin(fp);
                fp.computeThreat();
                if (gui != null) gui.setPlayers(new ArrayList<>(players));
            });
        }

        fetching = false;
    }

    public void refresh() {
        scanLobby();
    }

    private void fetchHypixel(IntelPlayer p) {
        if (hypixelApiKey.isEmpty()) return;
        try {
            String url = String.format(HYPIXEL_URL, hypixelApiKey, p.name);
            String json = get(url);
            if (json == null) return;

            JsonObject root   = new JsonParser().parse(json).getAsJsonObject();
            if (!root.get("success").getAsBoolean()) return;
            if (root.get("player").isJsonNull()) return;

            JsonObject player = root.getAsJsonObject("player");
            JsonObject stats  = player.has("stats") ? player.getAsJsonObject("stats") : null;
            JsonObject bw     = stats != null && stats.has("Bedwars") ? stats.getAsJsonObject("Bedwars") : null;

            // Level from networkExp
            if (player.has("networkExp")) {
                double exp = player.get("networkExp").getAsDouble();
                p.level = (int)((Math.sqrt(exp + 15312.5) - 88.38) / 35.35);
            }

            if (bw != null) {
                int fk   = bw.has("final_kills_bedwars")        ? bw.get("final_kills_bedwars").getAsInt()        : 0;
                int fd   = bw.has("final_deaths_bedwars")       ? bw.get("final_deaths_bedwars").getAsInt()       : 1;
                int w    = bw.has("wins_bedwars")               ? bw.get("wins_bedwars").getAsInt()               : 0;
                int l    = bw.has("losses_bedwars")             ? bw.get("losses_bedwars").getAsInt()             : 1;
                int bb   = bw.has("beds_broken_bedwars")        ? bw.get("beds_broken_bedwars").getAsInt()        : 0;
                int ws   = bw.has("winstreak")                  ? bw.get("winstreak").getAsInt()                  : 0;

                p.finalKills  = fk;
                p.bedsBroken  = bb;
                p.wins        = w;
                p.winstreak   = ws;
                p.fkdr        = fd > 0 ? (double)fk / fd : fk;
                p.wlr         = l  > 0 ? (double)w  / l  : w;
            }
        } catch (Exception e) {
            // silently fail — player just stays as "loading"
        }
    }

    private void fetchUrchin(IntelPlayer p) {
        try {
            String url  = String.format(URCHIN_URL, p.name);
            String json = get(url);
            if (json == null) return;

            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            if (root.has("cheating") && root.get("cheating").getAsBoolean()) {
                p.cheater   = true;
                p.urchinTag = root.has("tag") ? root.get("tag").getAsString() : "Flagged";
            }
        } catch (Exception ignored) {}
    }

    private String get(String urlStr) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(4000);
            con.setReadTimeout(4000);
            con.setRequestProperty("User-Agent", "Spirit-Client/1.0");
            if (con.getResponseCode() != 200) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private String detectTeam(NetworkPlayerInfo info) {
        try {
            String displayName = info.getDisplayName() != null
                    ? info.getDisplayName().getFormattedText() : "";
            if (displayName.contains("§c")) return "red";
            if (displayName.contains("§9")) return "blue";
            if (displayName.contains("§a")) return "green";
            if (displayName.contains("§e")) return "yellow";
            if (displayName.contains("§b")) return "aqua";
            if (displayName.contains("§f")) return "white";
            if (displayName.contains("§d")) return "pink";
            if (displayName.contains("§8")) return "gray";
        } catch (Exception ignored) {}
        return null;
    }

    public List<IntelPlayer> getPlayers() { return players; }
}
