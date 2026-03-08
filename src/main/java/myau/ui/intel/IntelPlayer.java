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
            // Boost threat based on cheat severity, but factor in real stats too
            double cheatBoost;
            if (containsAny(tag, "blatant", "scaffold", "bridg", "autoblock",
                    "fly", "speed", "bhop", "esp", "visual", "xray", "aimbot"))
                cheatBoost = 80; // high threat floor
            else if (containsAny(tag, "killaura", "kill aura", "reach", "velocity",
                    "anti-kb", "antikb", "confirmed", "sniper"))
                cheatBoost = 55; // medium threat floor
            else
                cheatBoost = 30; // low threat: legitscaff, eagle, autoclicker, info tags
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
