package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ServerUtil;
import myau.util.render.BlurShadowRenderer;
import myau.events.Render2DEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Session extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // Settings
    public final DropdownSetting posX        = register(new DropdownSetting("Position X",        2, "Left", "Center", "Right"));
    public final DropdownSetting posY        = register(new DropdownSetting("Position Y",        0, "Top", "Center", "Bottom"));
    public final SliderSetting   offsetX     = register(new SliderSetting("X Offset",            0, -400, 400, 1));
    public final SliderSetting   offsetY     = register(new SliderSetting("Y Offset",            0, -400, 400, 1));
    public final SliderSetting   scale       = register(new SliderSetting("Scale",             1.0,  0.5, 2.0, 0.05));
    public final SliderSetting   blurStr     = register(new SliderSetting("Blur Strength",       6,    1,  10,   1));
    public final SliderSetting   cornerRad   = register(new SliderSetting("Corner Radius",       6,    2,  20,   1));
    public final SliderSetting   bgAlpha     = register(new SliderSetting("Background Alpha",  160,    0, 255,   1));
    public final BooleanSetting  showGame    = register(new BooleanSetting("Game Stats",       true));
    public final BooleanSetting  showFinals  = register(new BooleanSetting("Final Kills",      true));
    public final BooleanSetting  showBeds    = register(new BooleanSetting("Beds",             true));
    public final BooleanSetting  showKills   = register(new BooleanSetting("Kills",            true));
    public final BooleanSetting  showWins    = register(new BooleanSetting("Wins",             true));
    public final BooleanSetting  showWs      = register(new BooleanSetting("Winstreak",        true));
    public final BooleanSetting  showGames   = register(new BooleanSetting("Session Games",    true));
    public final BooleanSetting  showGTime   = register(new BooleanSetting("Game Time",        true));
    public final BooleanSetting  showAvgTime = register(new BooleanSetting("Avg Game Time",    true));
    public final BooleanSetting  showSTime   = register(new BooleanSetting("Session Time",     true));

    // ── Session stats ─────────────────────────────────────────────────────────
    private int finalKills, finalDeaths, bedsBroken, bedsLost;
    private int kills, deaths, wins, losses, winstreak, sessionGames;
    private int gameFinals, gameBeds, gameKills;
    private long sessionStart, gameStart, gameEnd;
    private final List<Long> gameTimes = new ArrayList<>();
    private String myTeam = "", myName = "";
    private int status = -1;
    private int tickTimer = 0;

    private static final Set<String> KILL_KEYWORDS = new HashSet<>(Arrays.asList(
            "against", "by", "fighting", "for", "from", "meet", "of", "seeing", "to", "was", "with"));

    private static final String[] START_MESSAGES = {
        "Protect your bed and destroy the enemy beds.",
        "All generators are maxed! Your bed has three",
        "Collect Lucky Blocks from resource generators",
        "Select an ultimate in the store! They will",
        "Players swap teams at random intervals! Players",
        "Become a Soul Collector and trade in your"
    };

    private static final String[] COLOR_KEYS = {"c","9","a","e","b","f","d","8"};
    private static final String[] TEAM_NAMES = {"Red","Blue","Green","Yellow","Aqua","White","Pink","Gray"};

    public Session() { super("Session", false); }

    @Override
    public void onEnabled() {
        sessionStart = System.currentTimeMillis();
        gameStart = 0L;
        gameEnd   = 0L;
        resetSession();
    }

    @Override
    public void onDisabled() { }

    private void resetSession() {
        finalKills = finalDeaths = bedsBroken = bedsLost = 0;
        kills = deaths = wins = losses = winstreak = sessionGames = 0;
        gameFinals = gameBeds = gameKills = 0;
        gameTimes.clear();
    }

    // ── Tick: status + team refresh ───────────────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        status = getBedwarsStatus();

        if (++tickTimer % 20 == 0) {
            tickTimer = 0;
            if (status == 3) refreshTeam();
            else if (status == 1) checkWinstreakArmorStand();
        }

        if (mc.thePlayer != null) myName = mc.thePlayer.getName();
    }

    // ── World join: reset per-game stats ─────────────────────────────────────

    @EventTarget
    public void onWorldJoin(LoadWorldEvent event) {
        gameFinals = 0;
        gameBeds   = 0;
        gameKills  = 0;
        if (gameEnd == 0L) { gameStart = 0L; }
    }

    // ── Chat packet parsing ───────────────────────────────────────────────────

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.RECEIVE) return;
        if (!(event.getPacket() instanceof S02PacketChat)) return;
        if (status != 3 && status != 1) return;

        String raw = ((S02PacketChat) event.getPacket()).getChatComponent().getFormattedText();
        String msg = raw.replaceAll("§.", "").trim();

        // Game start
        for (String start : START_MESSAGES) {
            if (msg.contains(start)) {
                sessionGames++;
                gameStart = System.currentTimeMillis();
                gameEnd = 0L;
                return;
            }
        }

        if (status != 3) return;

        if (msg.startsWith("+") && msg.contains(" tokens!")) {
            if (msg.endsWith("(Win)")) {
                wins++; winstreak++;
            } else if (msg.endsWith("(Bed Destroyed)")) {
                bedsBroken++; gameBeds++;
            } else if (msg.endsWith("(Final Kill)")) {
                finalKills++; gameFinals++;
            }
        } else if (msg.startsWith("BED DESTRUCTION > Your Bed")) {
            bedsLost++;
        } else if (!msg.endsWith("FINAL KILL!") && !msg.startsWith("BED DESTRUCTION > ") && didPlayerGetKill(myName, msg)) {
            kills++; gameKills++;
        } else if (msg.equals("You have respawned!")) {
            deaths++;
        } else if (msg.equals("You have been eliminated!")) {
            finalDeaths++;
        } else if (!myTeam.isEmpty() && msg.startsWith("TEAM ELIMINATED > " + myTeam)) {
            losses++; winstreak = 0;
        } else if (msg.contains("1st Killer") && msg.startsWith(" ")) {
            if (gameStart != 0L) {
                gameEnd = System.currentTimeMillis();
                gameTimes.add(gameEnd - gameStart);
            }
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;
        if (mc.currentScreen != null) return;
        if (status < 1) return;

        List<String[]> rows = buildRows(); // [label, value]
        if (rows.isEmpty()) return;

        float sf  = (float) scale.getValue();
        int lineH = (int)(mc.fontRendererObj.FONT_HEIGHT * sf) + (int)(2 * sf);
        int pad   = (int)(6 * sf);

        // Measure
        int maxW = 0;
        for (String[] r : rows) {
            if (r == null) continue; // spacer
            int w = mc.fontRendererObj.getStringWidth(r[0] + " " + r[1]);
            if (w > maxW) maxW = w;
        }

        float panelW = maxW * sf + pad * 2;
        float panelH = rows.size() * lineH + pad * 2 - (int)(2 * sf);

        ScaledResolution sr = new ScaledResolution(mc);
        float baseX, baseY;
        switch (posX.getIndex()) {
            case 0:  baseX = 10f; break;
            case 1:  baseX = sr.getScaledWidth() / 2f - panelW / 2f; break;
            default: baseX = sr.getScaledWidth() - panelW - 10f; break;
        }
        switch (posY.getIndex()) {
            case 0:  baseY = 10f; break;
            case 1:  baseY = sr.getScaledHeight() / 2f - panelH / 2f; break;
            default: baseY = sr.getScaledHeight() - panelH - 10f; break;
        }
        baseX += (float) offsetX.getValue();
        baseY += (float) offsetY.getValue();

        GlStateManager.pushMatrix();

        BlurShadowRenderer.renderFrostedGlass(
                baseX, baseY, panelW, panelH,
                (float)(int) cornerRad.getValue(),
                (int) blurStr.getValue(),
                (int) bgAlpha.getValue());

        float y = baseY + pad;
        for (String[] row : rows) {
            if (row == null) { y += lineH; continue; } // spacer row

            boolean isHeader = row.length == 1;
            if (isHeader) {
                float tx = baseX + pad;
                mc.fontRendererObj.drawStringWithShadow(row[0], tx, y, 0xFFE991B8);
            } else {
                // label in grey, value right-aligned in white
                float tx = baseX + pad;
                mc.fontRendererObj.drawStringWithShadow(row[0], tx, y, 0xFFAAAAAA);
                float vx = baseX + panelW - pad - mc.fontRendererObj.getStringWidth(row[1]) * sf;
                mc.fontRendererObj.drawStringWithShadow(row[1], vx, y, 0xFFFFFFFF);
            }
            y += lineH;
        }

        GlStateManager.popMatrix();
    }

    // ── Row builder ───────────────────────────────────────────────────────────

    private List<String[]> buildRows() {
        List<String[]> rows = new ArrayList<>();
        boolean hasGameSection    = false;
        boolean hasSessionSection = false;
        boolean hasLowerSection   = false;

        int tFD = finalDeaths == 0 ? 1 : finalDeaths;
        int tBL = bedsLost    == 0 ? 1 : bedsLost;
        int tD  = deaths      == 0 ? 1 : deaths;
        int tL  = losses      == 0 ? 1 : losses;

        if (showGame.getValue()) {
            rows.add(new String[]{ "Game" });
            rows.add(new String[]{ "Finals", String.valueOf(gameFinals) });
            rows.add(new String[]{ "Beds",   String.valueOf(gameBeds) });
            rows.add(new String[]{ "Kills",  String.valueOf(gameKills) });
            hasGameSection = true;
        }

        if (showFinals.getValue() || showBeds.getValue() || showKills.getValue() || showWins.getValue()) {
            if (hasGameSection) rows.add(null); // spacer
            rows.add(new String[]{ "Session" });
            if (showFinals.getValue())
                rows.add(new String[]{ "Finals", fmt(finalKills) + "  FKDR " + fmtD((double)finalKills/tFD) });
            if (showBeds.getValue())
                rows.add(new String[]{ "Beds",   fmt(bedsBroken) + "  BBLR " + fmtD((double)bedsBroken/tBL) });
            if (showKills.getValue())
                rows.add(new String[]{ "Kills",  fmt(kills) + "  KDR " + fmtD((double)kills/tD) });
            if (showWins.getValue())
                rows.add(new String[]{ "Wins",   fmt(wins) + "  WLR " + fmtD((double)wins/tL) });
            hasSessionSection = true;
        }

        boolean needSpacer = hasGameSection || hasSessionSection;

        if (showWs.getValue()) {
            if (needSpacer) { rows.add(null); needSpacer = false; }
            rows.add(new String[]{ "Winstreak", String.valueOf(winstreak) });
            hasLowerSection = true;
        }
        if (showGames.getValue()) {
            if (needSpacer) { rows.add(null); needSpacer = false; }
            rows.add(new String[]{ "Session Games", String.valueOf(sessionGames) });
            hasLowerSection = true;
        }
        if (showGTime.getValue()) {
            if (needSpacer) { rows.add(null); needSpacer = false; }
            rows.add(new String[]{ "Game Time", gameTimeStr() });
            hasLowerSection = true;
        }
        if (showAvgTime.getValue()) {
            if (needSpacer) { rows.add(null); needSpacer = false; }
            rows.add(new String[]{ "Avg Time", avgTimeStr() });
            hasLowerSection = true;
        }
        if (showSTime.getValue()) {
            if (needSpacer) { rows.add(null); }
            rows.add(new String[]{ "Session Time", fmtTime(System.currentTimeMillis() - sessionStart) });
        }

        return rows;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean didPlayerGetKill(String name, String msg) {
        for (String kw : KILL_KEYWORDS) {
            if (msg.contains(kw + " " + name)) return true;
        }
        return false;
    }

    private void refreshTeam() {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.thePlayer.capabilities.allowFlying) return;
        try {
            Scoreboard sb = mc.theWorld.getScoreboard();
            ScorePlayerTeam team = sb.getPlayersTeam(mc.thePlayer.getName());
            if (team == null || team.getColorPrefix() == null) return;
            String prefix = team.getColorPrefix();
            if (prefix.length() < 2) return;
            String code = prefix.substring(1, 2);
            for (int i = 0; i < COLOR_KEYS.length; i++) {
                if (COLOR_KEYS[i].equals(code)) { myTeam = TEAM_NAMES[i]; return; }
            }
        } catch (Exception ignored) {}
    }

    private void checkWinstreakArmorStand() {
        if (mc.theWorld == null) return;
        for (Object e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof net.minecraft.entity.item.EntityArmorStand)) continue;
            String name = ((net.minecraft.entity.item.EntityArmorStand) e).getName().replaceAll("§.", "");
            if (!name.startsWith("Current Winstreak: ")) continue;
            try { winstreak = Integer.parseInt(name.split(": ")[1].replace(",", "")); } catch (Exception ignored) {}
            break;
        }
    }

    private int getBedwarsStatus() {
        ArrayList<String> sidebar = ServerUtil.getScoreboardLines();
        if (sidebar.isEmpty()) return -1;
        String top = sidebar.get(0).replaceAll("§.", "");
        if (!top.startsWith("BED WARS")) return -1;
        if (sidebar.size() < 7) return -1;
        String s6 = sidebar.get(6).replaceAll("§.", "");
        if (s6.equals("Waiting...") || s6.startsWith("Starting in")) return 2;
        String s5 = sidebar.get(5).replaceAll("§.", "");
        if (s5.startsWith("R Red:") && s6.startsWith("B Blue:")) return 3;
        if (sidebar.size() > 1) {
            String lobbyId = sidebar.get(1).replaceAll("§.", "").trim();
            if (lobbyId.startsWith("L")) return 1;
        }
        return 1;
    }

    private String gameTimeStr() {
        if (gameStart == 0L && gameEnd == 0L) return "00:00";
        long end = (gameStart != 0L && gameEnd == 0L) ? System.currentTimeMillis() : gameEnd;
        return fmtTime(end - gameStart);
    }

    private String avgTimeStr() {
        long total = 0L;
        int games = gameTimes.size();
        for (long t : gameTimes) total += t;
        if (gameStart != 0L && gameEnd == 0L) { total += System.currentTimeMillis() - gameStart; games++; }
        long avg = games == 0 ? 0L : total / games;
        return avg < 500 ? "00:00" : fmtTime(avg);
    }

    private String fmtTime(long ms) {
        long h = ms / 3600000L, m = (ms % 3600000L) / 60000L, s = (ms % 60000L) / 1000L;
        return h > 0 ? String.format("%02d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    private String fmt(int n) {
        if (Math.abs(n) < 1000) return String.valueOf(n);
        StringBuilder sb = new StringBuilder(String.valueOf(Math.abs(n)));
        for (int i = sb.length() - 3; i > 0; i -= 3) sb.insert(i, ',');
        return (n < 0 ? "-" : "") + sb;
    }

    private String fmtD(double v) {
        v = Math.round(v * 10.0) / 10.0;
        return v % 1 == 0 ? String.valueOf((int)v) : String.valueOf(v);
    }
}
