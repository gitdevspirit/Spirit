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
import java.util.concurrent.atomic.AtomicInteger;
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

    // Hypixel rate limiter: Use 200ms delay (was working before)
    // This gives us 5 req/sec = 300 req/min which Hypixel tolerates with bursts
    private final Semaphore hypixelSlots = new Semaphore(1);
    private final AtomicLong lastHypixelRequest = new AtomicLong(0);
    private static final long HYPIXEL_INTERVAL_MS = 200L; // 200ms like before - was working!

    // UUID cache for skin lookups of non-lobby players
    private final Map<String, String> uuidCache = new HashMap<>();

    private final List<IntelPlayer> players = new ArrayList<>();
    private final List<IntelPlayer> manualPlayers = new ArrayList<>();
    private IntelGui gui;
    private IntelHudOverlay hudOverlay;

    private IntelManager() {}

    public boolean isFetching() { return fetching; }
    public void setGui(IntelGui gui) { this.gui = gui; }
    public void setHudOverlay(IntelHudOverlay hud) { this.hudOverlay = hud; }
    public List<IntelPlayer> getPlayers() { return players; }

    /** Add a player by name manually (search bar). Fetches stats async. */
    public void addManualPlayer(String name) {
        for (IntelPlayer p : manualPlayers) {
            if (p.name.equalsIgnoreCase(name)) return; // already added
        }
        IntelPlayer p = new IntelPlayer(name, null);
        manualPlayers.add(p);
        List<IntelPlayer> combined = combined();
        if (gui != null) gui.setPlayers(combined);
        if (hudOverlay != null) hudOverlay.setPlayers(combined);
        
        // Fetch UUID immediately so skin can start downloading before Hypixel finishes
        pool.submit(() -> {
            fetchAndCacheUuid(p.name); // caches UUID → triggers face download on next GUI render
            List<IntelPlayer> refreshed = combined();
            if (gui != null) gui.setPlayers(refreshed); // refresh so drawPlayerHead sees the UUID
            if (hudOverlay != null) hudOverlay.setPlayers(refreshed);
        });
        // Fetch full stats in parallel
        pool.submit(() -> {
            fetchUrchinBatch(java.util.Collections.singletonList(p));
            fetchHypixel(p); // reuses cached UUID
            p.computeThreat();
            List<IntelPlayer> refreshed = combined();
            if (gui != null) gui.setPlayers(refreshed);
            if (hudOverlay != null) hudOverlay.setPlayers(refreshed);
        });
    }

    public void removeManualPlayer(String name) {
        manualPlayers.removeIf(p -> p.name.equalsIgnoreCase(name));
        List<IntelPlayer> combined = combined();
        if (gui != null) gui.setPlayers(combined);
        if (hudOverlay != null) hudOverlay.setPlayers(combined);
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
                    if (hudOverlay != null) hudOverlay.cacheSkin(n, loc, true);
                }
            }
            gui.setPlayers(new ArrayList<>(players));
        }
        if (hudOverlay != null) hudOverlay.setPlayers(new ArrayList<>(players));

        // Fetch Urchin for all players in one batch request
        final List<IntelPlayer> batchRef = new ArrayList<>(players);
        pool.submit(() -> {
            fetchUrchinBatch(batchRef);
            // Notify any cheaters found
            for (IntelPlayer p : batchRef) {
                if (p.cheater) notifyCheater(p);
            }
            List<IntelPlayer> refreshed = new ArrayList<>(players);
            if (gui != null) gui.setPlayers(refreshed);
            if (hudOverlay != null) hudOverlay.setPlayers(refreshed);
        });

        // Fetch Hypixel per player
        for (IntelPlayer p : players) {
            final IntelPlayer fp = p;
            pool.submit(() -> {
                fetchHypixel(fp);
                fp.computeThreat();
                List<IntelPlayer> refreshed = new ArrayList<>(players);
                if (gui != null) gui.setPlayers(refreshed);
                if (hudOverlay != null) hudOverlay.setPlayers(refreshed);
            });
        }

        fetching = false;
    }

    public void refresh() { scanLobby(); }

    // ── Hypixel ───────────────────────────────────────────────────────────────

    /** Fetches UUID from Mojang (with playerdb.co fallback) and caches it. */
    private String fetchAndCacheUuid(String name) {
        synchronized (uuidCache) { String cached = uuidCache.get(name); if (cached != null) return cached; }
        // Try Mojang first
        try {
            String json = get("https://api.mojang.com/users/profiles/minecraft/" + name, null, null);
            if (json != null) {
                JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
                if (obj.has("id")) {
                    String raw  = obj.get("id").getAsString();
                    String uuid = raw.replaceAll("^(.{8})(.{4})(.{4})(.{4})(.{12})$", "$1-$2-$3-$4-$5");
                    synchronized (uuidCache) { uuidCache.put(name, uuid); }
                    dbg("[UUID] Mojang OK: " + uuid);
                    return uuid;
                }
            }
        } catch (Exception e) { dbg("[UUID] Mojang ex: " + e.getMessage()); }
        // Fallback: playerdb.co
        try {
            String json = get("https://playerdb.co/api/player/minecraft/" + name, null, null);
            if (json != null) {
                JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
                if (obj.has("data")) {
                    JsonObject data = obj.getAsJsonObject("data");
                    if (data.has("player")) {
                        JsonObject player = data.getAsJsonObject("player");
                        if (player.has("id")) {
                            String uuid = player.get("id").getAsString(); // already has dashes
                            synchronized (uuidCache) { uuidCache.put(name, uuid); }
                            dbg("[UUID] playerdb OK: " + uuid);
                            return uuid;
                        }
                    }
                }
            }
        } catch (Exception e) { dbg("[UUID] playerdb ex: " + e.getMessage()); }
        dbg("[UUID] failed for: " + name);
        return null;
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
        boolean got = false;
        
        // If we have Hypixel API key, use it first (more accurate, has stars)
        if (!hypixelApiKey.isEmpty()) {
            got = fetchHypixelApi(p);
        }
        
        // Fallback to Slothpixel if Hypixel API failed or no key
        if (!got) {
            got = fetchSlothpixel(p);
        }
        
        p.loading = false;
    }

    /** Ashcon API (minetools) — free public Hypixel wrapper, no key needed, actively maintained */
    private boolean fetchSlothpixel(IntelPlayer p) {
        // Try Ashcon/Mowojang API first (fast, reliable)
        try {
            String uuid = fetchAndCacheUuid(p.name);
            if (uuid == null) {
                dbg("[Ashcon] no UUID for " + p.name);
                return false;
            }
            // Use api.hypixel.net public stats endpoint via minetools proxy
            // Actually use api.slothpixel.me replacement: api.hypixel.net public endpoint
            // Best free fallback: use plancke.io scrape is unreliable, use api2.hypixel.net
            // Real working free API: use the Hypixel public API without key (returns limited data)
            String json = get("https://api.hypixel.net/player?uuid=" + uuid, null, null);
            dbg("[FreeHypixel] len=" + (json == null ? "null" : json.length()));
            if (json == null) return false;

            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            // No key = still returns success:true but with rate limit
            if (!root.has("success") || !root.get("success").getAsBoolean()) {
                dbg("[FreeHypixel] success=false");
                return false;
            }
            if (!root.has("player") || root.get("player").isJsonNull()) {
                dbg("[FreeHypixel] no player");
                return false;
            }

            JsonObject player = root.getAsJsonObject("player");
            JsonObject stats  = player.has("stats") ? player.getAsJsonObject("stats") : null;
            JsonObject bw     = stats != null && stats.has("Bedwars") ? stats.getAsJsonObject("Bedwars") : null;

            if (bw != null && bw.has("Experience")) {
                try { p.star = getBedWarsLevelFromExp(bw.get("Experience").getAsInt()); }
                catch (Exception e) { p.star = 0; }
            }

            if (bw == null) { dbg("[FreeHypixel] no bw stats"); return false; }

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
            dbg("[FreeHypixel] OK fk=" + fk + " fkdr=" + p.fkdr);
            return true;
        } catch (Exception e) {
            dbg("[FreeHypixel] ex: " + e.getMessage());
            return false;
        }
    }

    /** Official Hypixel API v2 — used when API key is configured */
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

            String json = get("https://api.hypixel.net/player?uuid=" + uuid, "API-Key", hypixelApiKey);
            dbg("[HypixelAPI] response len=" + (json == null ? "null" : json.length()) + " uuid=" + uuid);
            if (json == null) return false;

            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            if (!root.has("success") || !root.get("success").getAsBoolean()) {
                if (root.has("cause")) {
                    System.err.println("[HypixelAPI] Error: " + root.get("cause").getAsString());
                }
                return false;
            }
            if (!root.has("player") || root.get("player").isJsonNull()) return false;

            JsonObject player = root.getAsJsonObject("player");
            if (player.has("networkExp")) {
                double exp = player.get("networkExp").getAsDouble();
                p.level = (int)((Math.sqrt(exp + 15312.5) - 88.38) / 35.35);
            }

            JsonObject stats = player.has("stats") ? player.getAsJsonObject("stats") : null;
            JsonObject bw    = stats != null && stats.has("Bedwars") ? stats.getAsJsonObject("Bedwars") : null;
            
            // Calculate accurate BedWars star from Achievement Points
            // Hypixel stores BedWars level as "Experience" in the Bedwars stats
            if (bw != null && bw.has("Experience")) {
                try {
                    int experience = bw.get("Experience").getAsInt();
                    p.star = getBedWarsLevelFromExp(experience);
                } catch (Exception e) {
                    p.star = 0;
                }
            } else {
                // Fallback: use achievements if available
                try {
                    JsonObject achievements = player.has("achievements") ? player.getAsJsonObject("achievements") : null;
                    if (achievements != null && achievements.has("bedwars_level")) {
                        p.star = achievements.get("bedwars_level").getAsInt();
                    } else {
                        p.star = 0; // Default to 0 if no data
                    }
                } catch (Exception e) {
                    p.star = 0;
                }
            }
            
            // If no BedWars stats, return false but set star to 0 first
            if (bw == null) {
                p.star = 0;
                return false;
            }

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
            
            return true; // Success - stats loaded
        } catch (Exception e) { 
            return false; 
        }
    }

    private int bwStat(JsonObject bw, String... keys) {
        for (String k : keys) if (bw.has(k)) { try { return bw.get(k).getAsInt(); } catch (Exception ignored) {} }
        return 0;
    }

    private int bwInt(JsonObject bw, String key) {
        return bw.has(key) ? bw.get(key).getAsInt() : 0;
    }

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
            // Check if notifications are enabled in LobbyIntel module
            myau.module.modules.LobbyIntel lobbyIntel = (myau.module.modules.LobbyIntel) 
                myau.Myau.moduleManager.getModule("LobbyIntel");
            if (lobbyIntel == null || !lobbyIntel.notifyCheaters.getValue()) return;
            
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
    
    /**
     * Calculate BedWars level (star) from experience points
     * Hypixel BedWars uses a tiered progression system
     */
    private static int getBedWarsLevelFromExp(int exp) {
        int level = 0;
        
        // Levels 0-3: 500 XP per level
        if (exp < 2000) {
            return exp / 500;
        }
        level = 4;
        exp -= 2000;
        
        // Levels 4-99: 1000 XP per level  
        if (exp < 96000) {
            return level + (exp / 1000);
        }
        level = 100;
        exp -= 96000;
        
        // Levels 100-199: 2000 XP per level
        if (exp < 200000) {
            return level + (exp / 2000);
        }
        level = 200;
        exp -= 200000;
        
        // Levels 200-299: 3000 XP per level
        if (exp < 300000) {
            return level + (exp / 3000);
        }
        level = 300;
        exp -= 300000;
        
        // Levels 300-399: 4000 XP per level
        if (exp < 400000) {
            return level + (exp / 4000);
        }
        level = 400;
        exp -= 400000;
        
        // Levels 400+: 5000 XP per level
        return level + (exp / 5000);
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
