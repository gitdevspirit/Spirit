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
    public double fkdr        = 0;
    public double wlr         = 0;
    public int    winstreak   = 0;
    public int    finalKills  = 0;
    public int    bedsBroken  = 0;
    public int    wins        = 0;

    // Urchin
    public boolean cheater    = false;
    public String  urchinTag  = null; // e.g. "Cheating", "Suspicious", "Blacklisted"

    // Computed
    public double  threatScore = 0;

    public IntelPlayer(String name, String team) {
        this.name = name;
        this.team = team;
    }

    /** Called after stats are loaded — compute weighted threat score 0-100 */
    public void computeThreat() {
        double score = 0;
        score += Math.min(40, fkdr        * 6.0);
        score += Math.min(20, wlr         * 5.0);
        score += Math.min(20, winstreak   * 0.8);
        score += Math.min(10, level       / 100.0 * 10);
        score += Math.min(10, finalKills  / 1000.0 * 10);
        score = Math.min(100, score);

        if (cheater && urchinTag != null) {
            String tag = urchinTag.toLowerCase();
            double cheatBoost;
            // High threat floor — blatant cheats
            if (containsAny(tag,
                    "blatant", "scaffold", "bridg",
                    "ab", "autoblock", "auto block", "auto_block", "hop", "hopping", "full hop",
                    "fly", "speed", "bhop", "bunnyhop", "movement",
                    "esp", "visual", "xray", "x-ray", "wallhack", "aimbot"))
                cheatBoost = 80;
            // Medium threat floor
            else if (containsAny(tag,
                    "aa", "aim assist", "aimassist", "aim_assist",
                    "ka", "killaura", "kill aura", "kill_aura",
                    "reach",
                    "jr", "jrv", "jump reset", "jr velo", "velo", "velocity",
                    "anti-kb", "antikb", "anti kb",
                    "sniper", "confirmed"))
                cheatBoost = 55;
            // Low threat floor — minor advantage
            else
                cheatBoost = 30; // ac, legitscaff, eagle, queue tags, info/caution
            threatScore = Math.max(cheatBoost, score);
        } else {
            threatScore = score;
        }
        loading = false;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String k : keywords) if (text.contains(k)) return true;
        return false;
    }
}
