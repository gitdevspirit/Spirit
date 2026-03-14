package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
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
    public final SliderSetting  hudKeybind  = register(new SliderSetting("HUD Toggle Key", Keyboard.KEY_H, 0, 255, 1));

    // HUD Overlay properties (saved to config)
    public final BooleanProperty hudEnabled      = new BooleanProperty("hud-enabled", true);
    public final IntProperty     hudPosX         = new IntProperty("hud-x", 10, 0, 3840);
    public final IntProperty     hudPosY         = new IntProperty("hud-y", 100, 0, 2160);
    public final FloatProperty   hudScale        = new FloatProperty("hud-scale", 1.0f, 0.5f, 2.0f);
    public final IntProperty     hudMaxPlayers   = new IntProperty("hud-max-players", 10, 1, 20);
    public final IntProperty     hudBgOpacity    = new IntProperty("hud-bg-opacity", 200, 0, 255);
    public final BooleanProperty hudShowHeads    = new BooleanProperty("hud-show-heads", true);
    public final BooleanProperty hudShowFkdr     = new BooleanProperty("hud-show-fkdr", true);
    public final BooleanProperty hudShowWlr      = new BooleanProperty("hud-show-wlr", false);
    public final BooleanProperty hudShowStreak   = new BooleanProperty("hud-show-streak", false);
    public final BooleanProperty hudShowThreat   = new BooleanProperty("hud-show-threat", true);
    public final BooleanProperty hudShowUrchin   = new BooleanProperty("hud-show-urchin", true);
    public final BooleanProperty hudShowTeamColor= new BooleanProperty("hud-show-team-color", true);
    public final TextProperty    hudSortMode     = new TextProperty("hud-sort-mode", "threat");

    // Saved via config, set via .intelpath command
    public final TextProperty logPath = new TextProperty("log-path",
            System.getProperty("user.home") + detectDefaultLogPath());

    private final IntelGui gui = new IntelGui();
    private final IntelHudOverlay hudOverlay = new IntelHudOverlay();
    private boolean scannedThisSession = false;

    public LobbyIntel() {
        super("LobbyIntel", false);
        IntelManager.getInstance().setGui(gui);
        IntelManager.getInstance().setHudOverlay(hudOverlay);
        // Try to auto-detect key on startup
        tryAutoDetectKey();
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
        hudOverlay.setShowHeads(hudShowHeads.getValue());
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
        hudShowHeads.setValue(hudOverlay.getShowHeads());
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
        mc.addScheduledTask(() -> mc.displayGuiScreen(gui));
        if (IntelManager.getInstance().getPlayers().isEmpty()) {
            IntelManager.getInstance().scanLobby();
        } else {
            gui.setPlayers(IntelManager.getInstance().getPlayers());
        }
        setEnabled(false);
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (event.getKey() == (int) hudKeybind.getValue()) {
            boolean newState = !hudOverlay.isEnabled();
            hudOverlay.setEnabled(newState);
            String status = newState ? "&a&lON" : "&c&lOFF";
            ChatUtil.sendFormatted("&7[Intel] HUD Overlay: " + status);
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        // Render HUD overlay during gameplay
        if (mc.currentScreen == null && hudOverlay.isEnabled()) {
            hudOverlay.render();
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        if (!autoScan.getValue()) return;
        // Re-scan on every world load (catches new BW game joins)
        scannedThisSession = false;
        mc.addScheduledTask(() -> {
            if (!scannedThisSession) {
                scannedThisSession = true;
                if (autoKey.getValue()) tryAutoDetectKey();
                IntelManager.getInstance().scanLobby();
            }
        });
    }

    public IntelHudOverlay getHudOverlay() {
        return hudOverlay;
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
        if (!isEnabled() || event.getType() != EventType.RECEIVE) return;
        if (!(event.getPacket() instanceof S02PacketChat)) return;
        
        S02PacketChat packet = (S02PacketChat) event.getPacket();
        IChatComponent component = packet.getChatComponent();
        if (component == null) return;
        
        String message = component.getUnformattedText();
        
        // Detect final kills and remove player from overlay
        // Hypixel format: "FINAL KILL! PlayerName was killed by OtherPlayer"
        // or "FINAL KILL! PlayerName fell into the void"
        if (message.contains("FINAL KILL!")) {
            // Extract the killed player name
            // Patterns: "FINAL KILL! Name was..." or "FINAL KILL! Name fell..."
            Pattern killPattern = Pattern.compile("FINAL KILL! (\\w+) (?:was|fell|died)");
            Matcher matcher = killPattern.matcher(message);
            
            if (matcher.find()) {
                String killedPlayer = matcher.group(1);
                removePlayerFromOverlay(killedPlayer);
            }
        }
        
        // Detect /who command response and add players
        // Hypixel format: "ONLINE: Player1, Player2, Player3, ..."
        if (message.startsWith("ONLINE:")) {
            String playerList = message.substring(7).trim(); // Remove "ONLINE:"
            String[] players = playerList.split(",\\s*");
            
            for (String playerName : players) {
                // Clean up any extra formatting
                playerName = playerName.replaceAll("[^a-zA-Z0-9_]", "").trim();
                if (!playerName.isEmpty() && !playerName.equals(mc.thePlayer.getName())) {
                    addPlayerToOverlay(playerName);
                }
            }
            
            ChatUtil.sendFormatted("&7[Intel] &aAdded " + players.length + " players from /who command");
        }
    }
    
    /**
     * Remove a player from the overlay by name
     */
    private void removePlayerFromOverlay(String playerName) {
        IntelManager manager = IntelManager.getInstance();
        boolean removed = false;
        
        // Remove from main players list
        for (int i = manager.getPlayers().size() - 1; i >= 0; i--) {
            IntelPlayer p = manager.getPlayers().get(i);
            if (p.name.equalsIgnoreCase(playerName)) {
                manager.getPlayers().remove(i);
                removed = true;
                break;
            }
        }
        
        if (removed) {
            ChatUtil.sendFormatted("&7[Intel] &cRemoved " + playerName + " (Final Kill)");
        }
    }
    
    /**
     * Add a player to the overlay by name
     */
    private void addPlayerToOverlay(String playerName) {
        IntelManager manager = IntelManager.getInstance();
        
        // Check if player already exists
        for (IntelPlayer p : manager.getPlayers()) {
            if (p.name.equalsIgnoreCase(playerName)) {
                return; // Already in overlay
            }
        }
        
        // Add player manually
        manager.addManualPlayer(playerName);
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
