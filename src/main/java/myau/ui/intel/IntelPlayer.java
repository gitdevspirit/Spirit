package myau.ui.intel;

/**
 * Data model for a single player's intelligence profile.
 */
public class IntelPlayer {
    public String  name;
    public String  team;
    /** Hypixel rank prefix (e.g. "§b[MVP§9+§b]"), extracted from the tab-list display name. */
    public String  rankPrefix = "";
    /** Whatever §-color is actually active for this player's name in the tab list — team color in a match, rank color in the lobby. 0 if not yet captured. */
    public int     tabNameColor = 0;
    public boolean loading    = true;
    /** Timestamp of the last stat-fetch attempt, used to throttle automatic retries. */
    public long lastStatAttemptMs = System.currentTimeMillis();

    // Hypixel BedWars stats
    public int    level       = 0;
    public int    star        = 0;
    public double fkdr        = 0;
    public double wlr         = 0;
    public int    winstreak   = 0;
    public int    finalKills  = 0;
    public int    finalDeaths = 0;
    public int    bedsBroken  = 0;
    public int    bedsLost    = 0;
    public int    kills       = 0;
    public int    deaths      = 0;
    public int    wins        = 0;
    public int    losses      = 0;
    public boolean isNicked   = false;

    // Coral / Urchin
    public boolean cheater      = false;
    public String  urchinTag    = null;
    public String  urchinType   = null;
    public String  urchinReason = null;

    // Ghost Intel
    public boolean ghostTagged = false;
    public String  ghostType   = null;
    public String  ghostReason = null;

    // Spirit Client Role
    public PlayerRole role = null;

    // Computed
    public double threatScore = 0;

    public IntelPlayer(String name, String team) {
        this.name = name;
        this.team = team;
        this.role = RoleManager.getInstance().getRole(name);
    }

    /**
     * Compact Coral tag label — single source of truth used by the HUD
     * overlay, LobbyIntel GUI, tab list, and .bw command so they never
     * diverge from each other again. Checks urchinType (which now folds in
     * icon + text + tooltip, since Cubelify's "icon" field alone is just a
     * Material Design icon id and never contains classification words) plus
     * urchinReason and urchinTag as fallbacks, in case the classification
     * word only shows up in one of them for a given tag source.
     *
     * Also folds in this client's own personal blacklist ("B") — checked live
     * against BlacklistManager rather than cached, so adding/removing someone
     * mid-lobby updates immediately without needing to re-scan.
     */
    public String getTagBadge() {
        String cheaterBadge = getCheaterBadge();
        if (isBlacklisted()) {
            return cheaterBadge.isEmpty() ? "B" : cheaterBadge + "/B";
        }
        return cheaterBadge;
    }

    private String getCheaterBadge() {
        if (!cheater) return "";

        String basis = (
                (urchinType   != null ? urchinType   : "") + " " +
                (urchinReason != null ? urchinReason : "") + " " +
                (urchinTag    != null ? urchinTag    : "")
        ).toLowerCase();

        if (basis.contains("blatant"))   return "BC";
        if (basis.contains("confirmed")) return "CC";
        if (basis.contains("closet"))    return "C";
        if (basis.contains("sniper"))    return "S";
        if (basis.contains("caution"))   return "!";
        if (basis.contains("replay"))    return "R";

        // Flagged by Coral but none of the known severity words matched —
        // still show a code rather than a static placeholder.
        return "C";
    }

    /** ARGB color matching {@link #getTagBadge()}'s classification. Blacklist-only players get blue. */
    public int getTagColor() {
        String cheaterBadge = getCheaterBadge();
        switch (cheaterBadge) {
            case "BC": return 0xFFFF3344; // blatant — red
            case "CC": return 0xFFDD44DD; // confirmed — magenta
            case "S":  return 0xFFFF1122; // sniper — bright red
            case "!":  return 0xFFFFCC44; // caution — amber
            case "R":  return 0xFF44DD66; // replays needed — green
            case "C":  return 0xFFFF8844; // closet / unclassified — orange
        }
        if (isBlacklisted()) return 0xFF4A9EFF; // blacklisted, not otherwise tagged — blue
        return 0xFFAAAAAA;
    }

    /** Live lookup against this client's personal blacklist — never stale. */
    public boolean isBlacklisted() {
        return myau.management.BlacklistManager.getInstance().isBlacklisted(name);
    }

    public String getBlacklistReason() {
        return myau.management.BlacklistManager.getInstance().getReason(name);
    }

    private static final Object[][] KEYWORD_SCORES = {
        { "blatant", false, 85 },
        { "blatant scaffold", false, 85 },
        { "fly", true, 80 },
        { "bhop", true, 75 },
        { "bunnyhop", false, 75 },
        { "full hop", false, 75 },
        { "speed", true, 75 },
        { "esp", true, 80 },
        { "xray", false, 80 },
        { "x-ray", false, 80 },
        { "wallhack", false, 80 },
        { "aimbot", false, 85 },
        { "killaura", false, 65 },
        { "kill aura", false, 65 },

        { "autoblock", false, 65 },
        { "reach", true, 58 },
        { "velocity", false, 55 },
        { "velo", true, 55 },
        { "jump reset", false, 55 },
        { "anti-kb", false, 55 },
        { "antikb", false, 55 },

        { "autoclicker", false, 35 },
        { "autoclick", false, 35 },
        { "legit scaffold", false, 30 },
        { "legitscaff", false, 30 },
        { "legitscaf", false, 30 },
        { "eagle", true, 25 },
        { "fastplace", false, 28 },
        { "safewalk", false, 25 },
        { "2q", false, 20 },
        { "3q", false, 22 },
        { "4q", false, 25 },
        { "boosting", false, 20 },
        { "queuing", false, 20 }
    };

    public void computeThreat() {
        // FKDR is weighted as the dominant factor in the stats-based score.
        double statsScore = 0;
        statsScore += Math.min(55, fkdr * 9.0);
        statsScore += Math.min(15, wlr * 4.0);
        statsScore += Math.min(15, winstreak * 0.6);
        statsScore += Math.min(8, level / 100.0 * 8);
        statsScore += Math.min(7, finalKills / 1200.0 * 7);
        statsScore = Math.min(100, statsScore);

        if (cheater) {
            String badge = getTagBadge();

            // Known tag types get a fixed baseline threat regardless of the
            // (often sparse) reason text — these are now hard floors (see
            // below), not just starting points that keyword-averaging could
            // drag down. Raised significantly across the board: a tag
            // classification itself is a strong signal and shouldn't end up
            // rated lower than an untagged high-stat player.
            double typeBase;
            switch (badge) {
                case "S":  typeBase = 92; break; // sniper
                case "BC": typeBase = 95; break; // blatant
                case "CC": typeBase = 85; break; // confirmed
                case "C":  typeBase = 72; break; // closet
                case "!":  typeBase = 40; break; // caution
                case "R":  typeBase = 30; break; // replay under review
                default:   typeBase = 55; break;
            }

            java.util.List<Double> found = new java.util.ArrayList<>();

            if (urchinReason != null) {
                String reason = urchinReason;

                for (Object[] keyword : KEYWORD_SCORES) {
                    String word = (String) keyword[0];
                    boolean boundary = (boolean) keyword[1];
                    double value = ((Number) keyword[2]).doubleValue();

                    boolean hit = boundary
                            ? hasWord(reason, word)
                            : reason.contains(word);

                    if (hit) found.add(value);
                }
            }

            double cheatScore;

            if (badge.equals("S")) {
                // Sniper is a fixed, deterministic threat level — don't let
                // keyword-average blending pull it down.
                cheatScore = typeBase;
            } else if (found.isEmpty()) {
                cheatScore = typeBase;
            } else {
                double average = 0;

                for (double score : found) {
                    average += score;
                }

                average /= found.size();

                // Blend for extra nuance from specific keyword matches, but
                // the tag's own baseline is always a floor — matching a
                // couple of low-severity keywords (e.g. "queue", "autoclick")
                // should never make a Confirmed/Blatant/Closet tag rate
                // lower than its baseline severity implies.
                double blended = average * 0.7 + typeBase * 0.3;
                cheatScore = Math.max(typeBase, blended);
            }

            cheatScore = Math.max(cheatScore, 20);
            threatScore = Math.max(cheatScore, statsScore);
        } else {
            threatScore = statsScore;
        }

        // Personal blacklist is a strong, deliberate signal — floor it high
        // regardless of stats/tags so a blacklisted player never blends into
        // the crowd on threat color alone.
        if (isBlacklisted()) {
            threatScore = Math.max(threatScore, 65);
        }

        loading = false;
    }

    private boolean hasWord(String text, String word) {
        int index = text.indexOf(word);

        while (index >= 0) {
            boolean beforeOk = index == 0
                    || !Character.isLetterOrDigit(text.charAt(index - 1));

            boolean afterOk = index + word.length() >= text.length()
                    || !Character.isLetterOrDigit(text.charAt(index + word.length()));

            if (beforeOk && afterOk) return true;

            index = text.indexOf(word, index + 1);
        }

        return false;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }

        return false;
    }
}
