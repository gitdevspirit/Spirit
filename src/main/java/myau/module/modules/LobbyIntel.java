package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.KeybindSetting;
import myau.property.properties.*;
import myau.ui.intel.IntelGui;
import myau.ui.intel.IntelHudOverlay;
import myau.ui.intel.IntelManager;
import myau.ui.intel.IntelPlayer;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;
import org.lwjgl.input.Keyboard;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LobbyIntel extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanSetting autoScan    = register(new BooleanSetting("Auto Scan on Join", true));
    public final BooleanSetting autoKey     = register(new BooleanSetting("Auto Detect API Key", true));
    public final BooleanSetting notifyCheaters = register(new BooleanSetting("Notify Cheaters", false));
    public final BooleanSetting hideTeammates  = register(new BooleanSetting("Hide Teammates", false));
    public final KeybindSetting hudKeybind  = register(new KeybindSetting("HUD Toggle Key", Keyboard.KEY_H));

    // HUD Overlay properties (saved to config)
    public final BooleanProperty hudEnabled      = new BooleanProperty("hud-enabled", true);
    public final IntProperty     hudPosX         = new IntProperty("hud-x", 10, 0, 3840);
    public final IntProperty     hudPosY         = new IntProperty("hud-y", 100, 0, 2160);
    public final FloatProperty   hudScale        = new FloatProperty("hud-scale", 1.0f, 0.5f, 2.0f);
    public final IntProperty     hudMaxPlayers   = new IntProperty("hud-max-players", 10, 1, 20);
    public final IntProperty     hudBgOpacity     = new IntProperty("hud-bg-opacity",     180, 0, 255);
    public final IntProperty     hudBorderOpacity = new IntProperty("hud-border-opacity", 100, 0, 255);
    public final BooleanProperty hudShowHeads    = new BooleanProperty("hud-show-heads", true);
    public final BooleanProperty hudShowStar     = new BooleanProperty("hud-show-star", true);
    public final BooleanProperty hudShowFkdr     = new BooleanProperty("hud-show-fkdr", true);
    public final BooleanProperty hudShowWlr      = new BooleanProperty("hud-show-wlr", false);
    public final BooleanProperty hudShowStreak   = new BooleanProperty("hud-show-streak", false);
    public final BooleanProperty hudShowThreat   = new BooleanProperty("hud-show-threat", true);
    public final BooleanProperty hudShowUrchin   = new BooleanProperty("hud-show-urchin", true);
    public final BooleanProperty hudShowTeamColor= new BooleanProperty("hud-show-team-color", true);
    public final TextProperty    hudSortMode     = new TextProperty("hud-sort-mode", "threat");
    public final TextProperty    hudColumnOrder  = new TextProperty("hud-column-order", "name,star,fkdr,urchin,threat");

    // Saved via config, set via .intelpath command
    public final TextProperty logPath = new TextProperty("log-path",
            System.getProperty("user.home") + detectDefaultLogPath());

    // API key — persisted across sessions
    public final TextProperty savedApiKey = new TextProperty("hypixel-api-key", "");

    private final IntelGui gui = new IntelGui();
    private final IntelHudOverlay hudOverlay = new IntelHudOverlay();
    private boolean scannedThisSession = false;

    public LobbyIntel() {
        super("LobbyIntel", true); // Start enabled so packet events work
        IntelManager.getInstance().setGui(gui);
        IntelManager.getInstance().setHudOverlay(hudOverlay);
        // Try to auto-detect key on startup
        tryAutoDetectKey();
        // Load saved API key from config (overrides auto-detect if present)
        if (!savedApiKey.getValue().isEmpty()) {
            IntelManager.hypixelApiKey = savedApiKey.getValue();
        }
        // Load HUD settings from properties
        loadHudSettings();
    }
    
    public void loadHudSettings() {
        // Debug logging
        System.out.println("[LobbyIntel] Loading HUD settings: pos=(" + hudPosX.getValue() + "," + hudPosY.getValue() + 
                          ") scale=" + hudScale.getValue() + " enabled=" + hudEnabled.getValue());
        
        hudOverlay.setEnabled(hudEnabled.getValue());
        hudOverlay.setPosition(hudPosX.getValue(), hudPosY.getValue());
        hudOverlay.setScale(hudScale.getValue());
        hudOverlay.setMaxPlayers(hudMaxPlayers.getValue());
        hudOverlay.setBgOpacity(hudBgOpacity.getValue());
        hudOverlay.setBorderOpacity(hudBorderOpacity.getValue());
        hudOverlay.setShowHeads(hudShowHeads.getValue());
        hudOverlay.setShowStar(hudShowStar.getValue());
        hudOverlay.setShowFkdr(hudShowFkdr.getValue());
        hudOverlay.setShowWlr(hudShowWlr.getValue());
        hudOverlay.setShowStreak(hudShowStreak.getValue());
        hudOverlay.setShowThreat(hudShowThreat.getValue());
        hudOverlay.setShowUrchin(hudShowUrchin.getValue());
        hudOverlay.setShowTeamColor(hudShowTeamColor.getValue());
        hudOverlay.setSortMode(hudSortMode.getValue());
        
        System.out.println("[LobbyIntel] HUD settings applied to overlay");
    }
    
    public void saveHudSettings() {
        hudEnabled.setValue(hudOverlay.isEnabled());
        hudPosX.setValue(hudOverlay.getPosX());
        hudPosY.setValue(hudOverlay.getPosY());
        hudScale.setValue(hudOverlay.getScale());
        hudMaxPlayers.setValue(hudOverlay.getMaxPlayers());
        hudBgOpacity.setValue(hudOverlay.getBgOpacity());
        hudBorderOpacity.setValue(hudOverlay.getBorderOpacity());
        hudShowHeads.setValue(hudOverlay.getShowHeads());
        hudShowStar.setValue(hudOverlay.getShowStar());
        hudShowFkdr.setValue(hudOverlay.getShowFkdr());
        hudShowWlr.setValue(hudOverlay.getShowWlr());
        hudShowStreak.setValue(hudOverlay.getShowStreak());
        hudShowThreat.setValue(hudOverlay.getShowThreat());
        hudShowUrchin.setValue(hudOverlay.getShowUrchin());
        hudShowTeamColor.setValue(hudOverlay.getShowTeamColor());
        hudSortMode.setValue(hudOverlay.getSortMode());
        
        // Debug logging
        System.out.println("[LobbyIntel] Saved HUD settings: pos=(" + hudPosX.getValue() + "," + hudPosY.getValue() + 
                          ") scale=" + hudScale.getValue() + " enabled=" + hudEnabled.getValue());
    }

    @Override
    public void onEnabled() {
        // Open the GUI
        mc.addScheduledTask(() -> mc.displayGuiScreen(gui));
        if (IntelManager.getInstance().getPlayers().isEmpty()) {
            IntelManager.getInstance().scanLobby();
        } else {
            gui.setPlayers(IntelManager.getInstance().getPlayers());
        }
        
        // Keep module enabled after opening GUI so events work
    }
    
    @Override
    public void onDisabled() {
        // Re-enable immediately so packet events keep working
        setEnabled(true);
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (event.getKey() == hudKeybind.getKeyCode()) {
            boolean newState = !hudOverlay.isEnabled();
            hudOverlay.setEnabled(newState);
            String status = newState ? "&a&lON" : "&c&lOFF";
            ChatUtil.sendFormatted("&7[Intel] HUD Overlay: " + status);
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.currentScreen == null && hudOverlay.isEnabled()) {
            hudOverlay.render();
        }
    }

    private boolean wasMouseDown = false;

    @EventTarget
    public void onRender2DClick(Render2DEvent event) {
        // Detect left click on overlay headers to change sort
        if (mc.currentScreen != null) return;
        boolean down = org.lwjgl.input.Mouse.isButtonDown(0);
        if (down && !wasMouseDown) {
            net.minecraft.client.gui.ScaledResolution sr =
                new net.minecraft.client.gui.ScaledResolution(mc);
            int mx = org.lwjgl.input.Mouse.getX() * sr.getScaledWidth()  / mc.displayWidth;
            int my = sr.getScaledHeight() - org.lwjgl.input.Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;
            hudOverlay.handleClick(mx, my);
        }
        wasMouseDown = down;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        // Reset scan flag on every world change so we re-scan the next game
        scannedThisSession = false;
        if (autoKey.getValue()) tryAutoDetectKey();
    }

    public IntelHudOverlay getHudOverlay() {
        return hudOverlay;
    }

    public IntelGui getGui() {
        return gui;
    }

    /** Scan the log file for a Hypixel API key */
    public void tryAutoDetectKey() {
        if (!autoKey.getValue()) return;
        new Thread(() -> {
            try {
                String path = logPath.getValue();
                File log = new File(path);
                if (!log.exists()) return;

                // Pattern: "Your new API key is XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX"
                // Also catches "API key set to XXXX" format
                Pattern pattern = Pattern.compile(
                    "(?:Your new API key is|API key set to|api key is)\\s+([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})",
                    Pattern.CASE_INSENSITIVE
                );

                String lastKey = null;
                // Read from end of file to get most recent key
                try (RandomAccessFile raf = new RandomAccessFile(log, "r")) {
                    long fileLen = raf.length();
                    // Read last 500KB max
                    long start = Math.max(0, fileLen - 512 * 1024);
                    raf.seek(start);
                    byte[] buf = new byte[(int)(fileLen - start)];
                    raf.readFully(buf);
                    String content = new String(buf, "UTF-8");

                    Matcher m = pattern.matcher(content);
                    while (m.find()) {
                        lastKey = m.group(1); // keep last match = most recent
                    }
                }

                if (lastKey != null && !lastKey.equals(IntelManager.hypixelApiKey)) {
                    IntelManager.hypixelApiKey = lastKey;
                    savedApiKey.setValue(lastKey);
                    // Notify in chat
                    net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                        myau.util.ChatUtil.sendFormatted("&7[Intel] &aAuto-detected Hypixel API key from log.");
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }
    
    /**
     * Handle chat packets for final kills and /who command
     */
    @EventTarget
    public void onPacket(PacketEvent event) {
        // Don't check isEnabled() - we want this to work even when GUI is closed
        if (event.getType() != EventType.RECEIVE) return;
        if (!(event.getPacket() instanceof S02PacketChat)) return;
        
        S02PacketChat packet = (S02PacketChat) event.getPacket();
        IChatComponent component = packet.getChatComponent();
        if (component == null) return;
        
        String message = component.getUnformattedText();
        
        // Detect BedWars game start — "The game starts in 10 seconds!"
        // This is the most reliable signal that we're actually in a BedWars game
        if (autoScan.getValue() && !scannedThisSession
                && message.contains("The game starts in 10 seconds")) {
            scannedThisSession = true;
            IntelManager.dbg("[Intel] BedWars game start detected — scanning lobby");
            mc.addScheduledTask(() -> {
                if (autoKey.getValue()) tryAutoDetectKey();
                IntelManager.getInstance().scanLobby();
            });
        }

        // Detect final kills — remove player from overlay immediately
        // Hypixel formats (unformatted):
        //   "PlayerName was killed by OtherPlayer. FINAL KILL!"
        //   "PlayerName fell into the void. FINAL KILL!"
        //   "PlayerName was blown up by OtherPlayer. FINAL KILL!"
        //   "PlayerName drowned. FINAL KILL!" etc.
        // The killed player is always the first word(s) before the verb
        if (message.contains("FINAL KILL!")) {
            // Match: word(s) at start, followed by " was "," fell "," drowned"," died"," hit"," got"
            Pattern killPattern = Pattern.compile("^([A-Za-z0-9_]+) (?:was |fell |drowned|died|hit |got )");
            Matcher matcher = killPattern.matcher(message.trim());
            if (matcher.find()) {
                String killedPlayer = matcher.group(1);
                removePlayerFromOverlay(killedPlayer);
                IntelManager.dbg("[Intel] Final kill: " + killedPlayer);
            }
        }
        
        // Detect /who response — "ONLINE: Player1, Player2, ..."
        // When this arrives: REPLACE the entire overlay with only these real player names,
        // discarding any obfuscated/bot entries that came from the tab list scan.
        if (message.startsWith("ONLINE:")) {
            String playerList = message.substring(7).trim();
            String[] parts = playerList.split(",\\s*");

            java.util.List<String> realNames = new java.util.ArrayList<>();
            for (String raw : parts) {
                String name = raw.replaceAll("[^a-zA-Z0-9_]", "").trim();
                if (!name.isEmpty()) realNames.add(name);
            }

            if (!realNames.isEmpty()) {
                IntelManager manager = IntelManager.getInstance();

                // Clear ALL current players (removes obfuscated tab-list entries)
                manager.getPlayers().clear();

                // Re-add only the real names from /who, preserving any already-fetched stats
                for (String name : realNames) {
                    if (name.equalsIgnoreCase(mc.thePlayer != null ? mc.thePlayer.getName() : "")) continue;
                    manager.addManualPlayer(name);
                }

                ChatUtil.sendFormatted("&7[Intel] &aReplaced player list with " + realNames.size() + " real players from /who");
                IntelManager.dbg("[Intel] /who replaced list: " + realNames);
            }
        }
    }
    
    /**
     * Remove a player from the overlay by name (final kill / eliminated)
     */
    private void removePlayerFromOverlay(String playerName) {
        IntelManager manager = IntelManager.getInstance();
        boolean removed = false;

        for (int i = manager.getPlayers().size() - 1; i >= 0; i--) {
            if (manager.getPlayers().get(i).name.equalsIgnoreCase(playerName)) {
                manager.getPlayers().remove(i);
                removed = true;
                break;
            }
        }
        // Also remove from manual players if added via /who or search
        manager.removeManualPlayer(playerName);

        if (removed) {
            // Refresh both GUI and HUD overlay
            java.util.List<IntelPlayer> refreshed = new java.util.ArrayList<>(manager.getPlayers());
            if (getGui() != null) getGui().setPlayers(refreshed);
            if (getHudOverlay() != null) getHudOverlay().setPlayers(refreshed);
        }
    }
    
    /**
     * Add a player to the overlay by name
     * @return true if player was added, false if already exists
     */
    private boolean addPlayerToOverlay(String playerName) {
        IntelManager manager = IntelManager.getInstance();
        
        // Check if player already exists
        for (IntelPlayer p : manager.getPlayers()) {
            if (p.name.equalsIgnoreCase(playerName)) {
                return false; // Already in overlay
            }
        }
        
        // Add player manually
        manager.addManualPlayer(playerName);
        return true;
    }

    /** Try to guess the default log path based on OS */
    private static String detectDefaultLogPath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return "/Library/Application Support/PrismLauncher/instances/Forge/minecraft/logs/latest.log";
        } else if (os.contains("win")) {
            return "\\AppData\\Roaming\\PrismLauncher\\instances\\Forge\\minecraft\\logs\\latest.log";
        } else {
            return "/.local/share/PrismLauncher/instances/Forge/minecraft/logs/latest.log";
        }
    }
}
