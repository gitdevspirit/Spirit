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

    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private volatile boolean fetching = false;

    // Hypixel rate limiter — strictly sequential, one request every 250ms
    // Hypixel allows 300 req/min with key = ~200ms between requests
    // We use 250ms to stay safely under the limit even under load
    private final java.util.concurrent.locks.ReentrantLock hypixelLock = new java.util.concurrent.locks.ReentrantLock(true); // fair queue
    private final AtomicLong lastHypixelRequest = new AtomicLong(0);
    private static final long HYPIXEL_INTERVAL_MS = 250L;

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
        manualPlayers.clear(); // clear stale data from previous game
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

        // Pre-fetch all UUIDs in parallel first (no rate limit needed for Mojang)
        // This way when the Hypixel queue gets to each player the UUID is already cached
        final List<IntelPlayer> playersRef = new ArrayList<>(players);
        pool.submit(() -> {
            // UUID fetches can run concurrently — Mojang allows it
            java.util.concurrent.ExecutorService uuidPool = Executors.newFixedThreadPool(4);
            for (IntelPlayer p : playersRef) {
                final IntelPlayer fp = p;
                uuidPool.submit(() -> fetchAndCacheUuid(fp.name));
            }
            uuidPool.shutdown();
            try { uuidPool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) {}
            dbg("[Intel] UUID pre-fetch complete for " + playersRef.size() + " players");
        });

        // Build fetch list — in focus mode, filter out obvious bots/obfuscated names first
        // so we spend API calls on real players only
        List<IntelPlayer> fetchList = buildFetchList(new ArrayList<>(players));

        // Fetch with staggered delay — 300ms between requests
        final List<IntelPlayer> staggerRef = fetchList;
        pool.submit(() -> {
            for (int i = 0; i < staggerRef.size(); i++) {
                final IntelPlayer fp = staggerRef.get(i);
                try { if (i > 0) Thread.sleep(300L); } catch (InterruptedException ignored) {}
                fetchHypixel(fp);
                fp.computeThreat();
                List<IntelPlayer> refreshed = new ArrayList<>(players);
                if (gui != null) gui.setPlayers(refreshed);
                if (hudOverlay != null) hudOverlay.setPlayers(refreshed);
            }
            // Mark any skipped players as done loading so they don't spin forever
            for (IntelPlayer p : players) {
                if (p.loading) { p.loading = false; }
            }
            List<IntelPlayer> final2 = new ArrayList<>(players);
            if (gui != null) gui.setPlayers(final2);
            if (hudOverlay != null) hudOverlay.setPlayers(final2);
        });

        fetching = false;
    }

    public void refresh() { scanLobby(); }

    /** Full reset — clears both lists. Call on new game start. */
    public void clearAll() {
        players.clear();
        manualPlayers.clear();
        if (gui != null) gui.setPlayers(new ArrayList<>());
        if (hudOverlay != null) hudOverlay.setPlayers(new ArrayList<>());
        dbg("[Intel] Cleared all players");
    }

    // ── Fetch list builder ───────────────────────────────────────────────────────

    /** 
     * Builds the list of players to actually fetch stats for.
     * In focus mode: filters out obfuscated names, limits to focusCount real players.
     * Always fetches everyone if focus mode is off.
     */
    private List<IntelPlayer> buildFetchList(List<IntelPlayer> all) {
        // Check focus mode setting from LobbyIntel
        int focusCount = 30; // default — fetch everyone up to 30
        boolean focus = false;
        try {
            myau.module.modules.LobbyIntel li = (myau.module.modules.LobbyIntel)
                myau.Myau.moduleManager.getModule(myau.module.modules.LobbyIntel.class);
            if (li != null) {
                focus = li.focusMode.getValue();
                focusCount = (int) li.focusCount.getValue();
            }
        } catch (Exception ignored) {}

        if (!focus) return all; // no filtering, fetch everyone

        // Filter: keep only players with normal-looking names (real players)
        // Obfuscated/bot names typically contain non-ASCII chars or are very short
        List<IntelPlayer> real = new ArrayList<>();
        for (IntelPlayer p : all) {
            if (isLikelyRealPlayer(p.name)) real.add(p);
        }

        // Cap to focusCount
        if (real.size() > focusCount) real = real.subList(0, focusCount);

        // Mark skipped players as not loading immediately
        for (IntelPlayer p : all) {
            boolean included = false;
            for (IntelPlayer r : real) { if (r == p) { included = true; break; } }
            if (!included) p.loading = false;
        }

        dbg("[Intel] Focus mode: fetching " + real.size() + "/" + all.size() + " players");
        return real;
    }

    /** Returns true if the name looks like a real Minecraft username */
    private boolean isLikelyRealPlayer(String name) {
        if (name == null || name.length() < 3 || name.length() > 16) return false;
        // Real usernames: only a-z, A-Z, 0-9, underscore
        for (char c : name.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') return false;
        }
        return true;
    }

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

        // Fallback if no key or API failed
        if (!got) {
            got = fetchSlothpixel(p);
        }

        // If we couldn't get any stats AND UUID lookup failed → likely nicked
        // A nicked player has no Mojang account matching their display name
        if (!got) {
            String uuid = getCachedUuid(p.name);
            if (uuid == null) {
                // Try to fetch UUID — if it fails, they're nicked
                uuid = fetchAndCacheUuid(p.name);
                if (uuid == null) {
                    p.isNicked = true;
                    dbg("[Intel] Nicked: " + p.name + " (no UUID)");
                }
            }
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
            hypixelLock.lock();
            try {
                long now  = System.currentTimeMillis();
                long wait = HYPIXEL_INTERVAL_MS - (now - lastHypixelRequest.get());
                if (wait > 0) Thread.sleep(wait);
                lastHypixelRequest.set(System.currentTimeMillis());
            } finally { hypixelLock.unlock(); }

            String uuid = fetchAndCacheUuid(p.name);
            if (uuid == null) return false;

            String[] hypixelResponse = getWithCode("https://api.hypixel.net/player?uuid=" + uuid, "API-Key", hypixelApiKey);
            int httpCode = hypixelResponse[0] == null ? 0 : Integer.parseInt(hypixelResponse[0]);
            String json = hypixelResponse[1];
            dbg("[HypixelAPI] http=" + httpCode + " len=" + (json == null ? "null" : json.length()) + " uuid=" + uuid);
            if (httpCode == 403 || httpCode == 401) { notifyInvalidKey(); return false; }
            if (json == null) return false;

            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            if (!root.has("success") || !root.get("success").getAsBoolean()) {
                String cause = root.has("cause") ? root.get("cause").getAsString() : "";
                dbg("[HypixelAPI] failed cause=" + cause);
                // Detect invalid/expired key and notify player
                if (cause.toLowerCase().contains("invalid") || cause.toLowerCase().contains("forbidden")
                        || cause.toLowerCase().contains("key") || cause.toLowerCase().contains("unauthorized")) {
                    notifyInvalidKey();
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

    // Tracks if we already notified this session to avoid spam
    private volatile boolean invalidKeyNotified = false;

    /** Call after setting a new key so the next request retries properly */
    public void resetInvalidKeyFlag() { invalidKeyNotified = false; }

    private void notifyInvalidKey() {
        if (invalidKeyNotified) return;
        invalidKeyNotified = true;
        // Clear the invalid key
        hypixelApiKey = "";
        // Remove from both property and key file
        try {
            myau.module.modules.LobbyIntel li = (myau.module.modules.LobbyIntel)
                myau.Myau.moduleManager.getModule(myau.module.modules.LobbyIntel.class);
            if (li != null) {
                li.savedApiKey.setValue("");
                // Delete key file so it doesn't reload on next launch
                new java.io.File("./config/Myau/intel-key.txt").delete();
            }
        } catch (Exception ignored) {}
        // Notify in chat
        net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
            myau.util.ChatUtil.sendFormatted(
                "&7[Intel] &cYour Hypixel API key is invalid or expired! " +
                "Run &e/api new &cin Hypixel to generate a new one.");
        });
        dbg("[HypixelAPI] Key invalidated - notified player");
    }

    /** Like get() but returns [httpCode, body] so callers can detect 403/401 */
    private String[] getWithCode(String urlStr, String headerKey, String headerVal) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setRequestProperty("User-Agent", "Spirit-Client/1.0");
            if (headerKey != null) con.setRequestProperty(headerKey, headerVal);
            int code = con.getResponseCode();
            if (code != 200) return new String[]{String.valueOf(code), null};
            return new String[]{String.valueOf(code), readStream(con.getInputStream())};
        } catch (Exception e) { return new String[]{null, null}; }
    }

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
     * Calculate BedWars star from Experience points using the correct Hypixel formula.
     * Each prestige (100 levels) = 487,000 XP total:
     *   Level 1 =   500 XP  (cumulative    500)
     *   Level 2 =  1000 XP  (cumulative  1,500)
     *   Level 3 =  2000 XP  (cumulative  3,500)
     *   Level 4 =  3500 XP  (cumulative  7,000)
     *   Levels 5-100 = 5000 XP each (cumulative 487,000 at end of prestige)
     */
    private static int getBedWarsLevelFromExp(int exp) {
        // XP required to reach the START of each easy level (cumulative)
        final int[] EASY_CUMULATIVE = { 0, 500, 1500, 3500, 7000 };
        final int XP_PER_PRESTIGE   = 487000;
        final int XP_PER_LEVEL      = 5000;

        int prestige  = exp / XP_PER_PRESTIGE;
        int remainder = exp % XP_PER_PRESTIGE;

        // Check which easy level (1-4) we're in
        for (int i = 1; i <= 4; i++) {
            if (remainder < EASY_CUMULATIVE[i]) {
                return prestige * 100 + (i - 1);
            }
        }

        // Past the easy levels — each level costs 5000 XP
        return prestige * 100 + 4 + (remainder - 7000) / XP_PER_LEVEL;
    }

    private String detectTeam(NetworkPlayerInfo info) {
        try {
            String dn = info.getDisplayName() != null ? info.getDisplayName().getFormattedText() : "";
            // Store the raw color code character so tab-based comparison works
            // BedWars + Castle + 40v40 all use these
            if (dn.contains("§c")) return "red";       // §c
            if (dn.contains("§9")) return "blue";      // §9
            if (dn.contains("§a")) return "green";     // §a
            if (dn.contains("§e")) return "yellow";    // §e
            if (dn.contains("§b")) return "aqua";      // §b
            if (dn.contains("§f")) return "white";     // §f
            if (dn.contains("§d")) return "pink";      // §d
            if (dn.contains("§8")) return "gray";      // §8
            if (dn.contains("§6")) return "orange";    // §6 gold/orange
            if (dn.contains("§5")) return "purple";    // §5 dark purple
            if (dn.contains("§3")) return "dark_aqua"; // §3 dark aqua
            if (dn.contains("§2")) return "dark_green";// §2 dark green
            if (dn.contains("§4")) return "dark_red";  // §4 dark red
            if (dn.contains("§1")) return "dark_blue"; // §1 dark blue
        } catch (Exception ignored) {}
        return null;
    }
}
