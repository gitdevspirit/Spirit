package myau.ui.intel;

/**
 * Data model for a single player's intelligence profile.
 */
public class IntelPlayer {
    public String  name;
    public String  team;
    public boolean loading    = true;

    // Hypixel BedWars stats
    public int    level       = 0;
    public int    star        = 0;  // BedWars star (accurate from Achievement Points)
    public double fkdr        = 0;
    public double wlr         = 0;
    public int    winstreak   = 0;
    public int    finalKills  = 0;
    public int    bedsBroken  = 0;
    public int    wins        = 0;

    // Urchin
    public boolean cheater     = false;
    public String  urchinTag   = null;    // display string: "Confirmed cheater — ac and legitscaff..."
    public String  urchinType  = null;    // raw type: "confirmed_cheater", "blatant_cheater" etc
    public String  urchinReason = null;   // raw reason text (lowercase): "ac and legitscaff when..."

    // Spirit Client Role
    public PlayerRole role     = null;    // OWNER, BETA, FRIEND, or USER

    // Computed
    public double  threatScore = 0;

    public IntelPlayer(String name, String team) {
        this.name = name;
        this.team = team;
        // Check role on creation
        this.role = RoleManager.getInstance().getRole(name);
    }

    /** Called after stats are loaded — compute weighted threat score 0-100 */
    // Each entry: { keyword, isWordBoundary, threatValue }
    // keyword matched against urchinReason; threatValue = per-cheat threat floor (0-100)
    private static final Object[][] KEYWORD_SCORES = {
        // Blatant / high-impact — 80+
        { "blatant",     false, 85 },
        { "scaffold",    true,  80 },
        { "bridg",       false, 78 },
        { "ab",          true,  78 },
        { "autoblock",   false, 78 },
        { "full hop",    false, 75 },
        { "hopping",     false, 75 },
        { "hop",         true,  72 },
        { "fly",         true,  80 },
        { "bhop",        true,  75 },
        { "bunnyhop",    false, 75 },
        { "speed",       true,  75 },
        { "esp",         true,  80 },
        { "visual",      false, 78 },
        { "xray",        false, 80 },
        { "x-ray",       false, 80 },
        { "wallhack",    false, 80 },
        { "aimbot",      false, 85 },
        // Medium — 50-65
        { "ka",          true,  65 },
        { "killaura",    false, 65 },
        { "kill aura",   false, 65 },
        { "aa",          true,  55 },
        { "aim assist",  false, 55 },
        { "aimassist",   false, 55 },
        { "reach",       true,  58 },
        { "velo",        true,  55 },
        { "velocity",    false, 55 },
        { "jr",          true,  55 },
        { "jrv",         true,  58 },
        { "jump reset",  false, 55 },
        { "anti-kb",     false, 55 },
        { "antikb",      false, 55 },
        { "sniper",      false, 50 },
        // Low-medium — 25-40
        { "ac",          true,  35 },
        { "autoclicker", false, 35 },
        { "autoclick",   false, 35 },
        { "cps",         true,  30 },
        { "legitscaff",  false, 30 },
        { "legitscaf",   false, 30 },
        { "fastplace",   false, 28 },
        { "eagle",       true,  25 },
        { "2q",          false, 20 },
        { "3q",          false, 22 },
        { "4q",          false, 25 },
        { "boosting",    false, 20 },
        { "queue",       false, 20 },
    };

    public void computeThreat() {
        // Stats-based score
        double statsScore = 0;
        statsScore += Math.min(40, fkdr       * 6.0);
        statsScore += Math.min(20, wlr        * 5.0);
        statsScore += Math.min(20, winstreak  * 0.8);
        statsScore += Math.min(10, level      / 100.0 * 10);
        statsScore += Math.min(10, finalKills / 1000.0 * 10);
        statsScore = Math.min(100, statsScore);

        if (cheater) {
            // Type-based baseline (before scanning reason keywords)
            double typeBase = 40;
            if (urchinType != null) {
                if      (urchinType.contains("blatant"))   typeBase = 80;
                else if (urchinType.contains("confirmed")) typeBase = 65;
                else if (urchinType.contains("closet"))    typeBase = 50;
                else if (urchinType.contains("sniper"))    typeBase = 45;
                else if (urchinType.contains("account"))   typeBase = 35;
                else if (urchinType.contains("caution"))   typeBase = 30;
                else if (urchinType.contains("info"))      typeBase = 20;
            }

            // Scan reason for every matching keyword, collect all their threat values
            java.util.List<Double> found = new java.util.ArrayList<>();
            if (urchinReason != null) {
                String r = urchinReason;
                for (Object[] kw : KEYWORD_SCORES) {
                    String word   = (String)  kw[0];
                    boolean bound = (boolean) kw[1];
                    double  val   = (double)  ((Number) kw[2]).doubleValue();
                    boolean hit   = bound ? hasWord(r, word) : r.contains(word);
                    if (hit) found.add(val);
                }
            }

            double cheatScore;
            if (found.isEmpty()) {
                // No specific keywords — use type base only
                cheatScore = typeBase;
            } else {
                // Average all matched keyword scores, then weight with type base
                double kwAvg = 0;
                for (double v : found) kwAvg += v;
                kwAvg /= found.size();
                // Blend: 70% keyword average + 30% type base
                cheatScore = kwAvg * 0.7 + typeBase * 0.3;
            }

            // Hard minimum: any cheater is at least 20
            cheatScore = Math.max(cheatScore, 20);
            // Take the higher of cheat score or real stats (a cheater who's also good is scarier)
            threatScore = Math.max(cheatScore, statsScore);
        } else {
            threatScore = statsScore;
        }
        loading = false;
    }

    /** Word-boundary safe check — "ac" won't match "place" or "black" */
    private boolean hasWord(String text, String word) {
        int idx = text.indexOf(word);
        while (idx >= 0) {
            boolean beforeOk = idx == 0 || !Character.isLetterOrDigit(text.charAt(idx - 1));
            boolean afterOk  = idx + word.length() >= text.length()
                    || !Character.isLetterOrDigit(text.charAt(idx + word.length()));
            if (beforeOk && afterOk) return true;
            idx = text.indexOf(word, idx + 1);
        }
        return false;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String k : keywords) if (text.contains(k)) return true;
        return false;
    }
}
