package myau.module.modules;

import myau.event.EventTarget;
import myau.events.LoadWorldEvent;
import myau.events.Render2DEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.property.properties.TextProperty;
import myau.ui.intel.IntelGui;
import myau.ui.intel.IntelHudOverlay;
import myau.ui.intel.IntelManager;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LobbyIntel extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanSetting autoScan    = register(new BooleanSetting("Auto Scan on Join", true));
    public final BooleanSetting autoKey     = register(new BooleanSetting("Auto Detect API Key", true));

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
