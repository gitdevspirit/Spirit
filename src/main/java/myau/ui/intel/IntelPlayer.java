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
    public boolean cheater     = false;
    public String  urchinTag   = null;    // display string: "Confirmed cheater — ac and legitscaff..."
    public String  urchinType  = null;    // raw type: "confirmed_cheater", "blatant_cheater" etc
    public String  urchinReason = null;   // raw reason text (lowercase): "ac and legitscaff when..."

    // Computed
    public double  threatScore = 0;

    public IntelPlayer(String name, String team) {
        this.name = name;
        this.team = team;
    }

    /** Called after stats are loaded — compute weighted threat score 0-100 */
    public void computeThreat() {
        double score = 0;
        score += Math.min(40, fkdr       * 6.0);
        score += Math.min(20, wlr        * 5.0);
        score += Math.min(20, winstreak  * 0.8);
        score += Math.min(10, level      / 100.0 * 10);
        score += Math.min(10, finalKills / 1000.0 * 10);
        score = Math.min(100, score);

        if (cheater) {
            // Type-based floor first
            double typeFloor = 40; // default for any urchin flag
            if (urchinType != null) {
                if (urchinType.contains("blatant"))   typeFloor = 80;
                else if (urchinType.contains("confirmed")) typeFloor = 65;
                else if (urchinType.contains("closet"))    typeFloor = 50;
                else if (urchinType.contains("caution"))   typeFloor = 30;
                else if (urchinType.contains("info"))      typeFloor = 20;
                else if (urchinType.contains("sniper"))    typeFloor = 45;
                else if (urchinType.contains("account"))   typeFloor = 35;
            }
            // Reason keyword override — more specific than type alone
            if (urchinReason != null) {
                String r = urchinReason;
                // HIGH floor — blatant movement/visual/autoblock
                if (hasWord(r, "scaffold") || hasWord(r, "blatant") || hasWord(r, "bridg")
                        || hasWord(r, "ab") || hasWord(r, "autoblock") || hasWord(r, "hop")
                        || hasWord(r, "hopping") || hasWord(r, "fly") || hasWord(r, "speed")
                        || hasWord(r, "esp") || hasWord(r, "visual") || hasWord(r, "xray")
                        || hasWord(r, "aimbot") || hasWord(r, "bhop"))
                    typeFloor = Math.max(typeFloor, 75);
                // MEDIUM floor
                else if (hasWord(r, "ka") || hasWord(r, "killaura") || hasWord(r, "kill aura")
                        || hasWord(r, "aa") || hasWord(r, "aimassist") || hasWord(r, "aim assist")
                        || hasWord(r, "reach") || hasWord(r, "velo") || hasWord(r, "velocity")
                        || hasWord(r, "jr") || hasWord(r, "jrv") || hasWord(r, "jump reset")
                        || hasWord(r, "anti-kb") || hasWord(r, "antikb") || hasWord(r, "sniper"))
                    typeFloor = Math.max(typeFloor, 55);
                // LOW-MEDIUM floor — still cheating, still an advantage
                else if (hasWord(r, "ac") || hasWord(r, "legitscaff") || hasWord(r, "legitscaf")
                        || hasWord(r, "eagle") || hasWord(r, "queue") || hasWord(r, "boosting")
                        || r.contains("2q") || r.contains("3q") || r.contains("4q"))
                    typeFloor = Math.max(typeFloor, 30);
            }
            threatScore = Math.max(typeFloor, score);
        } else {
            threatScore = score;
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
