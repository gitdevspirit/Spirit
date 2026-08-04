package myau.ui.intel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.Myau;
import myau.module.modules.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.ScorePlayerTeam;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class IntelManager {

    public static final List<String> debugLog = new ArrayList<>();

    public static void dbg(String message) {
        synchronized (debugLog) {
            debugLog.add(message);

            if (debugLog.size() > 200) {
                debugLog.remove(0);
            }
        }
    }

    public static String hypixelApiKey = "";
    public static String urchinApiKey = "";
    public static String ghostApiKey = "";

    private static final IntelManager INSTANCE = new IntelManager();

    public static IntelManager getInstance() {
        return INSTANCE;
    }

    private static final String CORAL_CUBELIFY_URL =
            "https://api.urchin.gg/v3/cubelify";

    private static final String GHOST_URL =
            "https://ghost-intel-bot-production.up.railway.app/api/tags";

    private static final long HYPIXEL_INTERVAL_MS = 600L;

    private final ExecutorService pool = Executors.newFixedThreadPool(6);
    private final Semaphore hypixelSlots = new Semaphore(1);
    private final AtomicLong lastHypixelRequest = new AtomicLong(0);

    private final Map<String, String> uuidCache = new HashMap<>();
    private final List<IntelPlayer> players = new ArrayList<>();
    private final List<IntelPlayer> manualPlayers = new ArrayList<>();

    private volatile boolean fetching = false;

    private IntelGui gui;
    private IntelHudOverlay hudOverlay;

    private IntelManager() {
        loadUrchinKeyFromFile();
    }

    public void saveUrchinKeyToFile() {
        try {
            File dir = new File("./config/Myau/");
            dir.mkdirs();

            File keyFile = new File(dir, "coral-key.txt");

            if (urchinApiKey.isEmpty()) {
                keyFile.delete();
                return;
            }

            try (PrintWriter writer = new PrintWriter(new FileWriter(keyFile))) {
                writer.println(urchinApiKey);
            }
        } catch (Exception exception) {
            dbg("[Intel] failed to save Coral key: " + exception);
        }
    }

    private void loadUrchinKeyFromFile() {
        try {
            File keyFile = new File("./config/Myau/coral-key.txt");
            if (!keyFile.exists()) return;

            try (BufferedReader reader = new BufferedReader(new FileReader(keyFile))) {
                String key = reader.readLine();

                if (key != null && !key.trim().isEmpty()) {
                    urchinApiKey = key.trim();
                    dbg("[Intel] Loaded Coral key from file");
                }
            }
        } catch (Exception exception) {
            dbg("[Intel] failed to load Coral key: " + exception);
        }
    }

    public boolean isFetching() {
        return fetching;
    }

    public void setGui(IntelGui gui) {
        this.gui = gui;
    }

    public void setHudOverlay(IntelHudOverlay hud) {
        this.hudOverlay = hud;
    }

    public List<IntelPlayer> getPlayers() {
        return players;
    }

    public IntelPlayer getPlayer(String name) {
        if (name == null) return null;

        for (IntelPlayer player : new ArrayList<>(players)) {
            if (name.equalsIgnoreCase(player.name)) {
                return player;
            }
        }

        for (IntelPlayer player : new ArrayList<>(manualPlayers)) {
            if (name.equalsIgnoreCase(player.name)) {
                return player;
            }
        }

        return null;
    }

    /** Hypixel NPC profiles use a version-2 UUID; name/display checks cover
     * servers that expose an NPC without that UUID convention. */
    public static boolean isNpc(NetworkPlayerInfo info) {
        if (info == null || info.getGameProfile() == null) return true;
        String name = info.getGameProfile().getName();
        if (name == null || name.isEmpty() || name.equalsIgnoreCase("NPC") || name.startsWith("!")) return true;

        java.util.UUID uuid = info.getGameProfile().getId();
        if (uuid != null && uuid.version() == 2) return true;

        if (info.getDisplayName() != null) {
            String displayed = info.getDisplayName().getUnformattedText();
            if (displayed != null && displayed.toLowerCase(java.util.Locale.ROOT).contains("[npc]")) return true;
        }
        return false;
    }

    public void addManualPlayer(String name) {
        Minecraft minecraft = Minecraft.getMinecraft();
        NetworkPlayerInfo info = minecraft.getNetHandler() == null
                ? null : minecraft.getNetHandler().getPlayerInfo(name);
        if (info != null && isNpc(info)) return;

        for (IntelPlayer player : manualPlayers) {
            if (player.name.equalsIgnoreCase(name)) {
                return;
            }
        }

        IntelPlayer player = new IntelPlayer(name, null);
        manualPlayers.add(player);

        List<IntelPlayer> combined = combined();

        if (gui != null) {
            gui.setPlayers(combined);
        }

        if (hudOverlay != null) {
            hudOverlay.setPlayers(combined);
        }

        pool.submit(() -> {
            try {
                fetchAndCacheUuid(player.name);
            } catch (Exception exception) {
                dbg("[Intel] UUID fetch failed for " + player.name
                        + ": " + exception);
            } finally {
                List<IntelPlayer> refreshed = combined();

                if (gui != null) {
                    gui.setPlayers(refreshed);
                }

                if (hudOverlay != null) {
                    hudOverlay.setPlayers(refreshed);
                }
            }
        });

        pool.submit(() -> {
            try {
                fetchUrchinBatch(java.util.Collections.singletonList(player));
                fetchHypixel(player);
                player.computeThreat();
            } catch (Exception exception) {
                player.loading = false;
                dbg("[Intel] add-player fetch failed for " + player.name
                        + ": " + exception);
            } finally {
                List<IntelPlayer> refreshed = combined();

                if (gui != null) {
                    gui.setPlayers(refreshed);
                }

                if (hudOverlay != null) {
                    hudOverlay.setPlayers(refreshed);
                }
            }
        });
    }

    /**
     * Removes a player that was added by search or by the automatic /who scan.
     *
     * @return true when a matching manually-added player was removed
     */
    public boolean removeManualPlayer(String name) {
        boolean removed = manualPlayers.removeIf(
                player -> player.name.equalsIgnoreCase(name)
        );

        List<IntelPlayer> combined = combined();

        if (gui != null) {
            gui.setPlayers(combined);
        }

        if (hudOverlay != null) {
            hudOverlay.setPlayers(combined);
        }

        return removed;
    }

    public boolean isManual(IntelPlayer player) {
        return manualPlayers.contains(player);
    }

    private List<IntelPlayer> combined() {
        List<IntelPlayer> result = new ArrayList<>(players);

        for (IntelPlayer manual : manualPlayers) {
            boolean alreadyPresent = false;

            for (IntelPlayer player : players) {
                if (player.name.equalsIgnoreCase(manual.name)) {
                    alreadyPresent = true;
                    break;
                }
            }

            if (!alreadyPresent) {
                result.add(manual);
            }
        }

        return result;
    }

    public void scanLobby() {
        fetching = true;

        Minecraft minecraft = Minecraft.getMinecraft();

        if (minecraft.getNetHandler() == null) {
            fetching = false;
            return;
        }

        Map<String, IntelPlayer> existingByName = new HashMap<>();
        for (IntelPlayer existing : players) {
            existingByName.put(existing.name.toLowerCase(), existing);
        }

        List<IntelPlayer> newRoster = new ArrayList<>();
        List<IntelPlayer> needsFetch = new ArrayList<>();

        for (NetworkPlayerInfo info : minecraft.getNetHandler().getPlayerInfoMap()) {
            String name = info.getGameProfile().getName();

            if (isNpc(info)) {
                continue;
            }

            String team = detectTeam(info);
            IntelPlayer existing = existingByName.get(name.toLowerCase());

            IntelPlayer player;
            if (existing != null) {
                player = existing;
                player.team = team;
            } else {
                player = new IntelPlayer(name, team);
                needsFetch.add(player);
            }

            player.rankPrefix = extractRankPrefix(info, name);

            newRoster.add(player);

            java.util.UUID uuid = info.getGameProfile().getId();

            if (uuid != null) {
                synchronized (uuidCache) {
                    uuidCache.put(name, uuid.toString());
                }
            }
        }

        players.clear();
        players.addAll(new
