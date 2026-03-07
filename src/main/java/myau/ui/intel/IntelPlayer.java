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
        if (cheater) { threatScore = 100; return; }
        double score = 0;
        score += Math.min(40, fkdr  * 6.0);   // FKDR up to 40pts (maxes at ~6.7 FKDR)
        score += Math.min(20, wlr   * 5.0);   // WLR up to 20pts
        score += Math.min(20, winstreak * 0.8); // streak up to 20pts
        score += Math.min(10, level / 100.0 * 10); // prestige up to 10pts
        score += Math.min(10, finalKills / 1000.0 * 10); // kills up to 10pts
        threatScore = Math.min(100, score);
        loading = false;
    }
}
