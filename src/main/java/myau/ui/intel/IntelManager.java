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
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class IntelManager {

    private static final IntelManager INSTANCE = new IntelManager();
    public static IntelManager getInstance() { return INSTANCE; }

    public static String hypixelApiKey = "";
    public static String urchinApiKey  = "68DE_lQ0UprVJX8q5k7TIeeZV938J2EfDAF08Q_07s0";

    private static final String URCHIN_URL  = "https://urchin.ws/player";

    private final ExecutorService pool = Executors.newFixedThreadPool(6);
    private volatile boolean fetching = false;

    // Hypixel rate limiter: max 1 request per 200ms (5/sec, well under 120/min limit)
    private final Semaphore hypixelSlots = new Semaphore(1);
    private final AtomicLong lastHypixelRequest = new AtomicLong(0);
    private static final long HYPIXEL_INTERVAL_MS = 200L;

    // UUID cache for skin lookups of non-lobby players
    private final Map<String, String> uuidCache = new HashMap<>();

    private final List<IntelPlayer> players = new ArrayList<>();
    private final List<IntelPlayer> manualPlayers = new ArrayList<>();
    private IntelGui gui;

    private IntelManager() {}

    public boolean isFetching() { return fetching; }
    public void setGui(IntelGui gui) { this.gui = gui; }
    public List<IntelPlayer> getPlayers() { return players; }

    /** Add a player by name manually (search bar). Fetches stats async. */
    public void addManualPlayer(String name) {
        for (IntelPlayer p : manualPlayers) {
            if (p.name.equalsIgnoreCase(name)) return; // already added
        }
        IntelPlayer p = new IntelPlayer(name, null);
        manualPlayers.add(p);
        if (gui != null) {
            List<IntelPlayer> combined = combined();
            gui.setPlayers(combined);
        }
        // Fetch UUID immediately so skin can start downloading before Hypixel finishes
        pool.submit(() -> {
            fetchAndCacheUuid(p.name); // caches UUID → triggers face download on next GUI render
            if (gui != null) gui.setPlayers(combined()); // refresh so drawPlayerHead sees the UUID
        });
        // Fetch full stats in parallel
        pool.submit(() -> {
            fetchUrchinBatch(java.util.Collections.singletonList(p));
            fetchHypixel(p); // reuses cached UUID
            p.computeThreat();
            if (gui != null) gui.setPlayers(combined());
        });
    }

    public void removeManualPlayer(String name) {
        manualPlayers.removeIf(p -> p.name.equalsIgnoreCase(name));
        if (gui != null) gui.setPlayers(combined());
    }

    public boolean isManual(IntelPlayer p) { return manualPlayers.contains(p); }

    private List<IntelPlayer> combined() {
        List<IntelPlayer> all = new ArrayList<>(players);
        for (IntelPlayer m : manualPlayers) {
            boolean already = false;
            for (IntelPlayer p : players) { if (p.name.equalsIgnoreCase(m.name)) { already = true; break; } }
            if (!already) all.add(m);
        }
        return all;
    }

    public void scanLobby() {
        players.clear();
        fetching = true;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) { fetching = false; return; }

        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            String name = info.getGameProfile().getName();
            if (name == null || name.isEmpty() || name.startsWith("!")) continue;
            players.add(new IntelPlayer(name, detectTeam(info)));
            // Pre-cache UUID from tab list — avoids a Mojang API call per player later
            java.util.UUID uid = info.getGameProfile().getId();
            if (uid != null) {
                synchronized (uuidCache) { uuidCache.put(name, uid.toString()); }
            }
        }

        // Pre-populate skin ResourceLocations from tab list so they show instantly
        if (gui != null) {
            for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
                String n = info.getGameProfile().getName();
                net.minecraft.util.ResourceLocation loc = info.getLocationSkin();
                if (n != null && loc != null) {
                    gui.cacheLobbyPlayerSkin(n, loc);
                }
            }
            gui.setPlayers(new ArrayList<>(players));
        }

        // Fetch Urchin for all players in one batch request
        final List<IntelPlayer> batchRef = new ArrayList<>(players);
        pool.submit(() -> {
            fetchUrchinBatch(batchRef);
            // Notify any cheaters found
            for (IntelPlayer p : batchRef) {
                if (p.cheater) notifyCheater(p);
            }
            if (gui != null) gui.setPlayers(new ArrayList<>(players));
        });

        // Fetch Hypixel per player
        for (IntelPlayer p : players) {
            final IntelPlayer fp = p;
            pool.submit(() -> {
                fetchHypixel(fp);
                fp.computeThreat();
                if (gui != null) gui.setPlayers(new ArrayList<>(players));
            });
        }

        fetching = false;
    }

    public void refresh() { scanLobby(); }

    // ── Hypixel ───────────────────────────────────────────────────────────────

    /** Fetches UUID from Mojang and caches it. Returns the UUID string or null. */
    private String fetchAndCacheUuid(String name) {
        synchronized (uuidCache) { String cached = uuidCache.get(name); if (cached != null) return cached; }
        try {
            String mojang = get("https://api.mojang.com/users/profiles/minecraft/" + name, null, null);
            if (mojang == null) return null;
            JsonObject mObj = new JsonParser().parse(mojang).getAsJsonObject();
            if (!mObj.has("id")) return null;
            String raw  = mObj.get("id").getAsString();
            String uuid = raw.replaceAll("^(.{8})(.{4})(.{4})(.{4})(.{12})$", "$1-$2-$3-$4-$5");
            synchronized (uuidCache) { uuidCache.put(name, uuid); }
            return uuid;
        } catch (Exception e) { return null; }
    }

    // ── Debug log ─────────────────────────────────────────────────────────────
    public static final java.util.List<String> debugLog = new java.util.ArrayList<>();
    public static void dbg(String msg) {
        synchronized (debugLog) {
            debugLog.add(msg);
            if (debugLog.size() > 200) debugLog.remove(0);
        }
    }

    private void fetchHypixel(IntelPlayer p) {
        dbg("[Stats] fetching " + p.name);
        boolean got = fetchSlothpixel(p);
        dbg("[Stats] slothpixel=" + got + " fkdr=" + p.fkdr);
        if (!got) {
            got = fetchPixelic(p);
            dbg("[Stats] pixelic=" + got + " fkdr=" + p.fkdr);
        }
        if (!got && !hypixelApiKey.isEmpty()) {
            got = fetchHypixelApi(p);
            dbg("[Stats] hypixelApi=" + got + " fkdr=" + p.fkdr);
        }
        p.loading = false;
    }

    /** Tries multiple free stat sources in order until one works */
    private boolean fetchSlothpixel(IntelPlayer p) {
        // 1. Slothpixel
        try {
            String json = get("https://api.slothpixel.me/api/players/" + p.name, null, null);
            dbg("[Sloth] len=" + (json == null ? "null" : json.length()));
            if (json != null) {
                JsonObject root = new JsonParser().parse(json).getAsJsonObject();
                if (!root.has("error")) {
                    if (root.has("level")) p.level = (int) root.get("level").getAsDouble();
                    JsonObject bw = null;
                    if (root.has("stats")) {
                        JsonObject st = root.getAsJsonObject("stats");
                        if (st.has("Bedwars")) bw = st.getAsJsonObject("Bedwars");
                    }
                    if (bw != null) {
                        int fk = bwInt(bw, "final_kills_bedwars");
                        int fd = bwInt(bw, "final_deaths_bedwars"); if (fd==0) fd=1;
                        int w  = bwInt(bw, "wins_bedwars");
                        int l  = bwInt(bw, "losses_bedwars");       if (l ==0) l =1;
                        p.finalKills = fk; p.bedsBroken = bwInt(bw,"beds_broken_bedwars");
                        p.wins=w; p.winstreak=bwInt(bw,"winstreak");
                        p.fkdr=(double)fk/fd; p.wlr=(double)w/l;
                        if (fk > 0 || w > 0) { dbg("[Sloth] OK fk="+fk+" w="+w); return true; }
                    }
                    dbg("[Sloth] no bw data");
                } else { dbg("[Sloth] error: " + root.get("error")); }
            }
        } catch (Exception e) { dbg("[Sloth] ex: " + e); }

        // 2. Lilith (api.hystatik.com) — another free Hypixel proxy
        try {
            String uuid = fetchAndCacheUuid(p.name);
            if (uuid != null) {
                String json = get("https://api.hystatik.com/v1/hypixel/player/" + uuid.replace("-",""),
                        "User-Agent", "Spirit-Client/1.0");
                dbg("[Hystatik] len=" + (json==null?"null":json.length()));
                if (json != null) {
                    JsonObject root = new JsonParser().parse(json).getAsJsonObject();
                    JsonObject player = root.has("player") ? root.getAsJsonObject("player") : root;
                    JsonObject stats  = player.has("stats") ? player.getAsJsonObject("stats") : null;
                    JsonObject bw     = stats != null && stats.has("Bedwars") ? stats.getAsJsonObject("Bedwars") : null;
                    if (bw != null) {
                        int fk = bwInt(bw,"final_kills_bedwars");
                        int fd = bwInt(bw,"final_deaths_bedwars"); if(fd==0)fd=1;
                        int w  = bwInt(bw,"wins_bedwars");
                        int l  = bwInt(bw,"losses_bedwars");       if(l==0)l=1;
                        p.finalKills=fk; p.bedsBroken=bwInt(bw,"beds_broken_bedwars");
                        p.wins=w; p.winstreak=bwInt(bw,"winstreak");
                        p.fkdr=(double)fk/fd; p.wlr=(double)w/l;
                        if(fk>0||w>0){dbg("[Hystatik] OK");return true;}
                    }
                }
            }
        } catch (Exception e) { dbg("[Hystatik] ex: " + e); }

        return false;
    }

    /** Pixelic — free tier, no key for BedWars stats */
    private boolean fetchPixelic(IntelPlayer p) {
        try {
            String uuid = fetchAndCacheUuid(p.name);
            if (uuid == null) return false;
            String uuidRaw = uuid.replace("-", "");
            String json = get("https://api.pixelic.de/v3/player/" + uuidRaw + "/bedwars",
                    "X-API-Key", ""); // free tier doesn't need a key
            dbg("[Pixelic] response null=" + (json == null) + (json != null ? " len=" + json.length() : ""));
            if (json == null) return false;

            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            JsonObject data = root.has("data") ? root.getAsJsonObject("data") : root;

            int fk = bwStat(data, "finalKills",  "final_kills");
            int fd = bwStat(data, "finalDeaths", "final_deaths"); if (fd == 0) fd = 1;
            int w  = bwStat(data, "wins");
            int l  = bwStat(data, "losses");                      if (l  == 0) l  = 1;
            if (fk == 0 && w == 0) return false;
            p.finalKills = fk;
            p.bedsBroken = bwStat(data, "bedsBroken", "beds_broken");
            p.wins       = w;
            p.winstreak  = bwStat(data, "winstreak", "currentWinstreak");
            p.fkdr       = (double) fk / fd;
            p.wlr        = (double) w  / l;
            return true;
        } catch (Exception e) { dbg("[Pixelic] exception: " + e.getMessage()); return false; }
    }

        /** Official Hypixel API v2 — used as fallback when API key is set */
    private boolean fetchHypixelApi(IntelPlayer p) {
        try {
            hypixelSlots.acquire();
            try {
                long now  = System.currentTimeMillis();
                long wait = HYPIXEL_INTERVAL_MS - (now - lastHypixelRequest.get());
                if (wait > 0) Thread.sleep(wait);
                lastHypixelRequest.set(System.currentTimeMillis());
            } finally { hypixelSlots.release(); }

            String uuid = fetchAndCacheUuid(p.name);
            if (uuid == null) return false;

            String json = get("https://api.hypixel.net/v2/player?uuid=" + uuid, "API-Key", hypixelApiKey);
            if (json == null) return false;

            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            if (!root.has("success") || !root.get("success").getAsBoolean()) return false;
            if (!root.has("player") || root.get("player").isJsonNull()) return false;

            JsonObject player = root.getAsJsonObject("player");
            if (player.has("networkExp")) {
                double exp = player.get("networkExp").getAsDouble();
                p.level = (int)((Math.sqrt(exp + 15312.5) - 88.38) / 35.35);
            }

            JsonObject stats = player.has("stats") ? player.getAsJsonObject("stats") : null;
            JsonObject bw    = stats != null && stats.has("Bedwars") ? stats.getAsJsonObject("Bedwars") : null;
            if (bw != null) {
                int fk = bwInt(bw, "final_kills_bedwars");
                int fd = bwInt(bw, "final_deaths_bedwars"); if (fd == 0) fd = 1;
                int w  = bwInt(bw, "wins_bedwars");
                int l  = bwInt(bw, "losses_bedwars");       if (l  == 0) l  = 1;
                p.finalKills = fk;
                p.bedsBroken = bwInt(bw, "beds_broken_bedwars");
                p.wins       = w;
                p.winstreak  = bwInt(bw, "winstreak");
                p.fkdr       = (double) fk / fd;
                p.wlr        = (double) w  / l;
                return true;
            }
            return false;
        } catch (Exception e) { return false; }
    }

    private int bwStat(JsonObject bw, String... keys) {
        for (String k : keys) if (bw.has(k)) { try { return bw.get(k).getAsInt(); } catch (Exception ignored) {} }
        return 0;
    }

    private int bwInt(JsonObject bw, String key) {
        return bw.has(key) ? bw.get(key).getAsInt() : 0;
    }

        // ── Urchin batch POST ─────────────────────────────────────────────────────
    // POST https://urchin.ws/player?key=<key>&sources=MANUAL
    // Body: {"usernames":["name1","name2",...]}
    // Response: {"players":{"Name":[{"type":"...","reason":"...","added_on":"..."}]}}

    private void fetchUrchinBatch(List<IntelPlayer> batch) {
        if (batch.isEmpty()) return;
        try {
            StringBuilder sb = new StringBuilder("{\"usernames\":[");
            for (int i = 0; i < batch.size(); i++) {
                sb.append("\"").append(batch.get(i).name).append("\"");
                if (i < batch.size() - 1) sb.append(",");
            }
            sb.append("]}");
            String body = sb.toString();

            String url = URCHIN_URL + "?sources=MANUAL"
                    + (urchinApiKey.isEmpty() ? "" : "&key=" + urchinApiKey);

            String response = post(url, body);
            if (response == null || response.equals("Invalid Key")) return;

            JsonObject root = new JsonParser().parse(response).getAsJsonObject();
            if (!root.has("players")) return;

            JsonObject players = root.getAsJsonObject("players");
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : players.entrySet()) {
                String name = entry.getKey();
                com.google.gson.JsonArray tags = entry.getValue().getAsJsonArray();
                if (tags.size() == 0) continue;
                for (IntelPlayer p : batch) {
                    if (p.name.equalsIgnoreCase(name)) {
                        p.cheater = true;
                        JsonObject tag = tags.get(0).getAsJsonObject();
                        String type   = tag.has("type") ? tag.get("type").getAsString() : "flagged";
                        String reason = tag.has("reason") && !tag.get("reason").isJsonNull()
                                ? tag.get("reason").getAsString() : "";
                        String typeFmt = type.replace("_", " ");
                        typeFmt = Character.toUpperCase(typeFmt.charAt(0)) + typeFmt.substring(1);
                        p.urchinTag    = typeFmt + (reason.isEmpty() ? "" : " \u2014 " + reason);
                        p.urchinType   = type.toLowerCase();   // e.g. "confirmed_cheater"
                        p.urchinReason = reason.toLowerCase(); // e.g. "ac and legitscaff"
                        p.loading      = false; // show data now; Hypixel will add real stats
                        p.computeThreat(); // apply cheat floor immediately; recomputed again after Hypixel
                        p.computeThreat(); // apply cheat floor immediately; Hypixel will recompute with real stats
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    // Single-player wrapper (used by debug command)
    private void fetchUrchin(IntelPlayer p) {
        fetchUrchinBatch(java.util.Collections.singletonList(p));
    }

    /** Returns cached UUID for a player name, or null if not yet known */
    public String getCachedUuid(String name) {
        synchronized (uuidCache) { return uuidCache.get(name); }
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

    private String post(String urlStr, String jsonBody) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
            con.setRequestMethod("POST");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("User-Agent", "Spirit-Client/1.0");
            try (OutputStream os = con.getOutputStream()) {
                os.write(jsonBody.getBytes("UTF-8"));
            }
            int code = con.getResponseCode();
            if (code != 200) return null;
            return readStream(con.getInputStream());
        } catch (Exception e) { return null; }
    }

    private String readStream(InputStream is) throws IOException {
        return readStreamStatic(is);
    }

    public static String readStreamStatic(InputStream is) throws IOException {
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
