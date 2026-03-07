package myau.ui.intel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.Myau;
import myau.module.modules.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IntelManager {

    private static final IntelManager INSTANCE = new IntelManager();
    public static IntelManager getInstance() { return INSTANCE; }

    public static String hypixelApiKey = "";
    public static String urchinApiKey  = "68DE_lQ0UprVJX8q5k7TIeeZV938J2EfDAF08Q_07s0";

    private static final String HYPIXEL_URL = "https://api.hypixel.net/player?key=%s&name=%s";
    private static final String URCHIN_URL  = "https://urchin.ws/cubelify";

    private final ExecutorService pool = Executors.newFixedThreadPool(6);
    private volatile boolean fetching = false;

    private final List<IntelPlayer> players = new ArrayList<>();
    private IntelGui gui;

    private IntelManager() {}

    public boolean isFetching() { return fetching; }
    public void setGui(IntelGui gui) { this.gui = gui; }
    public List<IntelPlayer> getPlayers() { return players; }

    public void scanLobby() {
        players.clear();
        fetching = true;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) { fetching = false; return; }

        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            String name = info.getGameProfile().getName();
            if (name == null || name.isEmpty() || name.startsWith("!")) continue;
            players.add(new IntelPlayer(name, detectTeam(info)));
        }

        if (gui != null) gui.setPlayers(new ArrayList<>(players));

        // Fetch Hypixel + Urchin per player
        for (IntelPlayer p : players) {
            final IntelPlayer fp = p;
            pool.submit(() -> {
                fetchHypixel(fp);
                fetchUrchin(fp);
                fp.computeThreat();
                if (fp.cheater) notifyCheater(fp);
                if (gui != null) gui.setPlayers(new ArrayList<>(players));
            });
        }

        fetching = false;
    }

    public void refresh() { scanLobby(); }

    // ── Hypixel ───────────────────────────────────────────────────────────────

    private void fetchHypixel(IntelPlayer p) {
        if (hypixelApiKey.isEmpty()) { p.loading = false; return; }
        try {
            String json = get(String.format(HYPIXEL_URL, hypixelApiKey, p.name), null, null);
            if (json == null) { p.loading = false; return; }

            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            if (!root.get("success").getAsBoolean()) { p.loading = false; return; }
            if (root.get("player").isJsonNull())      { p.loading = false; return; }

            JsonObject player = root.getAsJsonObject("player");

            if (player.has("networkExp")) {
                double exp = player.get("networkExp").getAsDouble();
                p.level = (int)((Math.sqrt(exp + 15312.5) - 88.38) / 35.35);
            }

            JsonObject stats = player.has("stats") ? player.getAsJsonObject("stats") : null;
            JsonObject bw    = stats != null && stats.has("Bedwars") ? stats.getAsJsonObject("Bedwars") : null;

            if (bw != null) {
                int fk = bwInt(bw, "final_kills_bedwars");
                int fd = bwInt(bw, "final_deaths_bedwars");  if (fd == 0) fd = 1;
                int w  = bwInt(bw, "wins_bedwars");
                int l  = bwInt(bw, "losses_bedwars");        if (l  == 0) l  = 1;
                p.finalKills = fk;
                p.bedsBroken = bwInt(bw, "beds_broken_bedwars");
                p.wins       = w;
                p.winstreak  = bwInt(bw, "winstreak");
                p.fkdr       = (double) fk / fd;
                p.wlr        = (double) w  / l;
            }
        } catch (Exception ignored) {}
        p.loading = false;
    }

    private int bwInt(JsonObject bw, String key) {
        return bw.has(key) ? bw.get(key).getAsInt() : 0;
    }

    // ── Urchin per-player GET ─────────────────────────────────────────────────

    private void fetchUrchin(IntelPlayer p) {
        if (urchinApiKey.isEmpty()) return;
        try {
            // GET https://urchin.ws/cubelify?id=<uuid>&name=<name>&sources=&key=<key>
            // id can be empty if we don't have UUID — name alone is enough
            // Build URL manually - don't encode the key as it contains safe chars
            String url = URCHIN_URL
                    + "?id="
                    + "&name=" + java.net.URLEncoder.encode(p.name, "UTF-8")
                    + "&sources="
                    + "&key=" + urchinApiKey;

            String json = get(url, null, null);
            if (json == null) return;

            JsonObject root = new JsonParser().parse(json).getAsJsonObject();

            // Response: { "blacklisted": true/false, "tags": [...], "reason": "..." }
            if (root.has("blacklisted") && root.get("blacklisted").getAsBoolean()) {
                p.cheater   = true;
                String tag    = root.has("tags") && root.getAsJsonArray("tags").size() > 0
                        ? root.getAsJsonArray("tags").get(0).getAsString() : "Blacklisted";
                String reason = root.has("reason") && !root.get("reason").isJsonNull()
                        ? root.get("reason").getAsString() : "";
                p.urchinTag = tag + (reason.isEmpty() ? "" : " — " + reason);
            }
        } catch (Exception ignored) {}
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private void notifyCheater(IntelPlayer p) {
        try {
            if (Myau.notificationManager == null) return;
            Notifications notifModule = (Notifications) Myau.moduleManager.modules.get(Notifications.class);
            if (notifModule == null || !notifModule.isEnabled()) return;
            long dur = (long)(notifModule.duration.getValue() * 1000.0);
            Myau.notificationManager.add("⚑ " + p.name + " flagged by Urchin", dur, 0xFFFF3344);
        } catch (Exception ignored) {}
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private String get(String urlStr, String headerKey, String headerVal) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setRequestProperty("User-Agent", "Spirit-Client/1.0");
            if (headerKey != null) con.setRequestProperty(headerKey, headerVal);
            if (con.getResponseCode() != 200) return null;
            return readStream(con.getInputStream());
        } catch (Exception e) { return null; }
    }

    private String post(String urlStr, String jsonBody, String apiKey) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
            con.setRequestMethod("POST");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("User-Agent", "Spirit-Client/1.0");
            con.setRequestProperty("Authorization", apiKey);
            try (OutputStream os = con.getOutputStream()) {
                os.write(jsonBody.getBytes("UTF-8"));
            }
            if (con.getResponseCode() != 200) return null;
            return readStream(con.getInputStream());
        } catch (Exception e) { return null; }
    }

    private String readStream(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private String detectTeam(NetworkPlayerInfo info) {
        try {
            String dn = info.getDisplayName() != null ? info.getDisplayName().getFormattedText() : "";
            if (dn.contains("§c")) return "red";
            if (dn.contains("§9")) return "blue";
            if (dn.contains("§a")) return "green";
            if (dn.contains("§e")) return "yellow";
            if (dn.contains("§b")) return "aqua";
            if (dn.contains("§f")) return "white";
            if (dn.contains("§d")) return "pink";
            if (dn.contains("§8")) return "gray";
        } catch (Exception ignored) {}
        return null;
    }
}
