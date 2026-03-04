package myau.module.modules;

import myau.Myau;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;
import myau.util.ChatUtil;
import myau.mixin.IAccessorKeyBinding;
import myau.ui.clickgui.Rise6ClickGui;
import myau.mixin.IAccessorRenderManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.events.Render3DEvent;
import myau.events.LoadWorldEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.module.BooleanSetting;
import myau.module.KeybindSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.opengl.GL11;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Stack;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Pit extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // ── Submodule toggles ────────────────────────────────────────────────────

    public final BooleanSetting hudPreview  = register(new BooleanSetting("HUD Preview", false));

    public final BooleanSetting aimAssist = register(new BooleanSetting("Aim Assist", false));
    public final KeybindSetting kb_aimAssist = register(new KeybindSetting("  Aim Assist Key", 0));

    // ── Aim Assist settings — only visible when aimAssist is on ──────────────

    public final SliderSetting  aaHSpeed    = register(new SliderSetting("  H-Speed",    3.0, 0.0, 10.0, 0.1,  () -> aimAssist.getValue()));
    public final SliderSetting  aaVSpeed    = register(new SliderSetting("  V-Speed",    0.0, 0.0, 10.0, 0.1,  () -> aimAssist.getValue()));
    public final SliderSetting  aaSmoothing = register(new SliderSetting("  Smoothing",  50,  0,   100,   1,   () -> aimAssist.getValue()));
    public final SliderSetting  aaRange     = register(new SliderSetting("  Range",      4.5, 3.0, 8.0,  0.1,  () -> aimAssist.getValue()));
    public final SliderSetting  aaFov       = register(new SliderSetting("  FOV",        90,  30,  360,   1,   () -> aimAssist.getValue()));
    public final BooleanSetting aaWeapon    = register(new BooleanSetting("  Weapons Only",  true,  () -> aimAssist.getValue()));
    public final BooleanSetting aaTools     = register(new BooleanSetting("  Allow Tools",   false, () -> aimAssist.getValue()));
    public final BooleanSetting aaBotCheck  = register(new BooleanSetting("  Bot Check",     true,  () -> aimAssist.getValue()));
    public final BooleanSetting aaTeam      = register(new BooleanSetting("  Teams",          true,  () -> aimAssist.getValue()));

    // Requirements — filter who gets targeted
    public final BooleanSetting aaReqArmor     = register(new BooleanSetting("  Req: Armor",        false, () -> aimAssist.getValue()));
    public final DropdownSetting aaArmorTier   = register(new DropdownSetting("  Armor Tier",   0, () -> aimAssist.getValue() && aaReqArmor.getValue(), "Any", "Leather", "Chain", "Iron", "Gold", "Diamond"));
    public final BooleanSetting aaReqHealth    = register(new BooleanSetting("  Req: Health",       false, () -> aimAssist.getValue()));
    public final SliderSetting  aaMaxHealth    = register(new SliderSetting("  Max Health",   10,  1, 20, 1,   () -> aimAssist.getValue() && aaReqHealth.getValue()));

    private final TimerUtil aaTimer = new TimerUtil();

    // ── Gold Requirement submodule ────────────────────────────────────────────

    public final BooleanSetting goldReq = register(new BooleanSetting("Gold Req", false));
    public final KeybindSetting kb_goldReq = register(new KeybindSetting("  Gold Req Key", 0));
    public final SliderSetting  grX     = register(new SliderSetting("  HUD X", 10, 0, 500, 1, () -> goldReq.getValue()));
    public final SliderSetting  grY     = register(new SliderSetting("  HUD Y", 10, 0, 300, 1, () -> goldReq.getValue()));

    private double grGained = 0, grNeeded = 0;
    private int    grTick   = 0;

    // ── Streak submodule ──────────────────────────────────────────────────────

    public final BooleanSetting streak   = register(new BooleanSetting("Streak", false));
    public final KeybindSetting kb_streak = register(new KeybindSetting("  Streak Key", 0));
    public final SliderSetting  stX      = register(new SliderSetting("  Streak X",       5,  0, 500, 1, () -> streak.getValue()));
    public final SliderSetting  stY      = register(new SliderSetting("  Streak Y",      50,  0, 300, 1, () -> streak.getValue()));
    public final SliderSetting  stOpacity = register(new SliderSetting("  Box Opacity",  33,  0, 100, 1, () -> streak.getValue()));
    public final SliderSetting  stSpacing  = register(new SliderSetting("  Line Spacing",  15,  8,  20, 1, () -> streak.getValue()));
    public final BooleanSetting stShowKills   = register(new BooleanSetting("  Show Kills",    true,  () -> streak.getValue()));
    public final BooleanSetting stShowAssists = register(new BooleanSetting("  Show Assists",  true,  () -> streak.getValue()));
    public final BooleanSetting stShowXP      = register(new BooleanSetting("  Show XP",       true,  () -> streak.getValue()));
    public final BooleanSetting stShowGold    = register(new BooleanSetting("  Show Gold",     true,  () -> streak.getValue()));
    public final BooleanSetting stShowTime    = register(new BooleanSetting("  Show Time",     true,  () -> streak.getValue()));
    public final BooleanSetting stShowKD      = register(new BooleanSetting("  Show K/D",      true,  () -> streak.getValue()));
    public final BooleanSetting stShowGPM     = register(new BooleanSetting("  Show GPM",      true,  () -> streak.getValue()));
    public final SliderSetting  stOrdKills   = register(new SliderSetting("  Kills Order",   1, 1, 7, 1, () -> streak.getValue()));
    public final SliderSetting  stOrdAssists = register(new SliderSetting("  Assists Order", 2, 1, 7, 1, () -> streak.getValue()));
    public final SliderSetting  stOrdXP      = register(new SliderSetting("  XP Order",      3, 1, 7, 1, () -> streak.getValue()));
    public final SliderSetting  stOrdGold    = register(new SliderSetting("  Gold Order",    4, 1, 7, 1, () -> streak.getValue()));
    public final SliderSetting  stOrdTime    = register(new SliderSetting("  Time Order",    5, 1, 7, 1, () -> streak.getValue()));
    public final SliderSetting  stOrdKD      = register(new SliderSetting("  K/D Order",     6, 1, 7, 1, () -> streak.getValue()));
    public final SliderSetting  stOrdGPM     = register(new SliderSetting("  GPM Order",     7, 1, 7, 1, () -> streak.getValue()));

    // ── Events submodule ──────────────────────────────────────────────────────

    public final BooleanSetting pitEvents    = register(new BooleanSetting("Events", false));
    public final KeybindSetting kb_pitEvents = register(new KeybindSetting("  Events Key", 0));
    public final DropdownSetting evSound     = register(new DropdownSetting("  Event Sound", 0, () -> pitEvents.getValue(), "Note Pling", "Note Bass", "Note Harp", "Orb Pickup", "Level Up", "Chest Open", "Anvil Land"));
    public final SliderSetting  evX          = register(new SliderSetting("  Events X",    5,   0, 500, 1, () -> pitEvents.getValue()));
    public final SliderSetting  evY          = register(new SliderSetting("  Events Y",   50,   0, 300, 1, () -> pitEvents.getValue()));
    public final SliderSetting  evCount      = register(new SliderSetting("  Event Count", 5,   1,  10, 1, () -> pitEvents.getValue()));

    private final List<String> evList       = new ArrayList<>();
    private String             evResponse   = null;
    private long               evLastFetch  = 0L;
    private int                evPassIndex  = 0;
    private int                evTick       = 0;

    public  int   stKills   = 0;
    public  int   stDeaths  = 0;
    private long  stStartMs = 0; // session start time for GPM
    private float stTotalGold = 0f; // total gold earned this session for GPM
    public  int   stAssists = 0;
    private float stXP      = 0f;
    private float stGold    = 0f;
    private boolean stPaused = true;
    private final TimerUtil stTimer = new TimerUtil();

    // ── AutoMath submodule ────────────────────────────────────────────────────

    public final BooleanSetting autoMath       = register(new BooleanSetting("AutoMath", false));
    public final KeybindSetting kb_autoMath = register(new KeybindSetting("  AutoMath Key", 0));
    public final SliderSetting  amDelay        = register(new SliderSetting("  Delay", 1000, 0, 5000, 100, () -> autoMath.getValue()));
    public final BooleanSetting amAutoSubmit   = register(new BooleanSetting("  Auto Submit", true, () -> autoMath.getValue()));

    private String amPendingSolution = null;
    private static final ScheduledExecutorService amScheduler = Executors.newScheduledThreadPool(1);

    // ── Bounty Tracker submodule ─────────────────────────────────────────────

    public final BooleanSetting bountyTracker = register(new BooleanSetting("Bounty Tracker", false));
    public final KeybindSetting kb_bountyTracker = register(new KeybindSetting("  Bounty Key", 0));
    public final SliderSetting  btX           = register(new SliderSetting("  BT X",   5,  0, 500, 1, () -> bountyTracker.getValue()));
    public final SliderSetting  btY           = register(new SliderSetting("  BT Y",  50,  0, 300, 1, () -> bountyTracker.getValue()));

    // name -> bounty amount string
    private final LinkedHashMap<String, String> btTargets = new LinkedHashMap<>();

    // ── AutoGrinder submodule ─────────────────────────────────────────────────

    public final BooleanSetting autoGrinder  = register(new BooleanSetting("AutoGrinder", false));
    public final KeybindSetting kb_autoGrinder = register(new KeybindSetting("  AutoGrinder Key", 0));
    public final DropdownSetting agSorting   = register(new DropdownSetting("  Sorting",    0, () -> autoGrinder.getValue(), "Distance", "Health"));
    public final DropdownSetting agMode      = register(new DropdownSetting("  Mode",       0, () -> autoGrinder.getValue(), "Legit", "Blatant"));
    public final SliderSetting   agRange     = register(new SliderSetting("  Attack Range", 4.0, 3.0, 6.0, 0.1, () -> autoGrinder.getValue()));
    public final SliderSetting   agFov       = register(new SliderSetting("  FOV",         30,  1,  360,   1,   () -> autoGrinder.getValue()));

    private int agTicks = 0;

    // ── Gamble submodule ──────────────────────────────────────────────────────

    public final BooleanSetting gamble          = register(new BooleanSetting("Gamble", false));
    public final KeybindSetting kb_gamble = register(new KeybindSetting("  Gamble Key", 0));
    public final BooleanSetting gambleGiantStop = register(new BooleanSetting("  Giant Ticket Stop", true, () -> gamble.getValue()));

    private final List<Vec3> gambleWaypoints = new ArrayList<>();
    private boolean gambleTracking = false;

    // ── Killstreak Announcer submodule ───────────────────────────────────────
    public final BooleanSetting ksAnnouncer  = register(new BooleanSetting("KS Announcer", false));
    public final KeybindSetting kb_ksAnnouncer = register(new KeybindSetting("  KS Key", 0));
    // Milestone kill counts (comma-separated idea: use sliders for 3 milestones)
    public final SliderSetting  ksMilestone1 = register(new SliderSetting("  Milestone 1",  5, 1, 100, 1, () -> ksAnnouncer.getValue()));
    public final SliderSetting  ksMilestone2 = register(new SliderSetting("  Milestone 2", 10, 1, 100, 1, () -> ksAnnouncer.getValue()));
    public final SliderSetting  ksMilestone3 = register(new SliderSetting("  Milestone 3", 25, 1, 100, 1, () -> ksAnnouncer.getValue()));

    // ── KOS submodule ─────────────────────────────────────────────────────────

    public final BooleanSetting kosList     = register(new BooleanSetting("KOS List", false));
    public final SliderSetting  kosX        = register(new SliderSetting("  KOS X",  5,  0, 500, 1, () -> kosList.getValue()));
    public final SliderSetting  kosY        = register(new SliderSetting("  KOS Y", 50,  0, 300, 1, () -> kosList.getValue()));
    public final KeybindSetting kb_kosList  = register(new KeybindSetting("  KOS Key", 0));
    public final DropdownSetting kosSound    = register(new DropdownSetting("  KOS Alert Sound", 3, () -> kosList.getValue(), "Note Pling", "Note Bass", "Note Harp", "Orb Pickup", "Level Up", "Chest Open", "Anvil Land"));

    // Static so NameTags can read it
    public static final List<String> kosNames = new LinkedList<>();
    private static final java.io.File KOS_FILE = new java.io.File("./config/Myau/kos_list.txt");

    // ── HUD Layout persistence ────────────────────────────────────────────────
    private static final java.io.File HUD_FILE = new java.io.File("./config/Myau/pit_hud_layout.json");

    public void saveHudLayout() {
        try {
            if (!HUD_FILE.getParentFile().exists()) HUD_FILE.getParentFile().mkdirs();
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            obj.addProperty("stX",  stX.getValue());  obj.addProperty("stY",  stY.getValue());
            obj.addProperty("grX",  grX.getValue());  obj.addProperty("grY",  grY.getValue());
            obj.addProperty("btX",  btX.getValue());  obj.addProperty("btY",  btY.getValue());
            obj.addProperty("evX",  evX.getValue());  obj.addProperty("evY",  evY.getValue());
            obj.addProperty("kosX", kosX.getValue()); obj.addProperty("kosY", kosY.getValue());
            obj.addProperty("ctX",  ctX.getValue());  obj.addProperty("ctY",  ctY.getValue());
            obj.addProperty("plX",  plX.getValue());  obj.addProperty("plY",  plY.getValue());
            java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(HUD_FILE));
            pw.println(new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(obj));
            pw.close();
        } catch (Exception ignored) {}
    }

    public void loadHudLayout() {
        try {
            if (!HUD_FILE.exists()) return;
            String raw = new String(java.nio.file.Files.readAllBytes(HUD_FILE.toPath()));
            com.google.gson.JsonObject obj = new com.google.gson.JsonParser().parse(raw).getAsJsonObject();
            if (obj.has("stX"))  stX.setValue(obj.get("stX").getAsDouble());
            if (obj.has("stY"))  stY.setValue(obj.get("stY").getAsDouble());
            if (obj.has("grX"))  grX.setValue(obj.get("grX").getAsDouble());
            if (obj.has("grY"))  grY.setValue(obj.get("grY").getAsDouble());
            if (obj.has("btX"))  btX.setValue(obj.get("btX").getAsDouble());
            if (obj.has("btY"))  btY.setValue(obj.get("btY").getAsDouble());
            if (obj.has("evX"))  evX.setValue(obj.get("evX").getAsDouble());
            if (obj.has("evY"))  evY.setValue(obj.get("evY").getAsDouble());
            if (obj.has("kosX")) kosX.setValue(obj.get("kosX").getAsDouble());
            if (obj.has("kosY")) kosY.setValue(obj.get("kosY").getAsDouble());
            if (obj.has("ctX"))  ctX.setValue(obj.get("ctX").getAsDouble());
            if (obj.has("ctY"))  ctY.setValue(obj.get("ctY").getAsDouble());
            if (obj.has("plX"))  plX.setValue(obj.get("plX").getAsDouble());
            if (obj.has("plY"))  plY.setValue(obj.get("plY").getAsDouble());
        } catch (Exception ignored) {}
    }

    public static void saveKosList() {
        try {
            if (!KOS_FILE.getParentFile().exists()) KOS_FILE.getParentFile().mkdirs();
            java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(KOS_FILE));
            for (String name : kosNames) pw.println(name);
            pw.close();
        } catch (Exception ignored) {}
    }

    public static void loadKosList() {
        kosNames.clear();
        try {
            if (!KOS_FILE.exists()) return;
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(KOS_FILE));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) kosNames.add(line);
            }
            br.close();
        } catch (Exception ignored) {}
    }

    // ── Auto Spawn submodule ─────────────────────────────────────────────────
    public final BooleanSetting autoSpawn     = register(new BooleanSetting("Auto Spawn", false));
    public final KeybindSetting kb_autoSpawn  = register(new KeybindSetting("  Auto Spawn Key", 0));
    public final SliderSetting  asHealth      = register(new SliderSetting("  Health Threshold", 6, 1, 20, 1, () -> autoSpawn.getValue()));
    public final SliderSetting  asInterval    = register(new SliderSetting("  Retry Interval (s)", 3, 1, 10, 1, () -> autoSpawn.getValue()));

    private long asLastSent = 0;

    // ── Contract Tracker submodule ───────────────────────────────────────────────
    public final BooleanSetting contractTracker = register(new BooleanSetting("Contract", false));
    public final KeybindSetting kb_contract     = register(new KeybindSetting("  Contract Key", 0));
    public final DropdownSetting ctSound     = register(new DropdownSetting("  Complete Sound", 1, () -> contractTracker.getValue(), "Note Pling", "Note Bass", "Note Harp", "Orb Pickup", "Level Up", "Chest Open", "Anvil Land"));
    public final SliderSetting  ctX             = register(new SliderSetting("  Contract X", 5,   0, 500, 1, () -> contractTracker.getValue()));
    public final SliderSetting  ctY             = register(new SliderSetting("  Contract Y", 200, 0, 300, 1, () -> contractTracker.getValue()));

    // Contract state
    private String ctName     = "";
    private int    ctProgress = 0;
    private int    ctGoal     = 0;
    private String ctReward   = "";

    // ── Death Recap submodule ─────────────────────────────────────────────────
    public final BooleanSetting deathRecap   = register(new BooleanSetting("Death Recap", false));
    public final KeybindSetting kb_deathRecap = register(new KeybindSetting("  Death Recap Key", 0));

    // Death Recap state
    private String   drKillerName   = "";
    private ItemStack drKillerWeapon = null;
    private float    drMyHP         = 0f;

    // ── Prestige List submodule ───────────────────────────────────────────────

    public final BooleanSetting prestigeList   = register(new BooleanSetting("Prestige List",     false));
    public final KeybindSetting kb_prestigeList = register(new KeybindSetting("  Prestige Key", 0));
    public final SliderSetting  plX            = register(new SliderSetting("  PL X",    5,   0, 500, 1,  () -> prestigeList.getValue()));
    public final SliderSetting  plY            = register(new SliderSetting("  PL Y",   50,   0, 300, 1,  () -> prestigeList.getValue()));
    public final BooleanSetting plP0           = register(new BooleanSetting("  Prestige 0",     true,  () -> prestigeList.getValue()));
    public final BooleanSetting plP1_4         = register(new BooleanSetting("  Prestige 1-4",   true,  () -> prestigeList.getValue()));
    public final BooleanSetting plP5_9         = register(new BooleanSetting("  Prestige 5-9",   true,  () -> prestigeList.getValue()));
    public final BooleanSetting plP10_14       = register(new BooleanSetting("  Prestige 10-14", true,  () -> prestigeList.getValue()));
    public final BooleanSetting plP15_19       = register(new BooleanSetting("  Prestige 15-19", true,  () -> prestigeList.getValue()));
    public final BooleanSetting plP20_24       = register(new BooleanSetting("  Prestige 20-24", true,  () -> prestigeList.getValue()));
    public final BooleanSetting plP25_29       = register(new BooleanSetting("  Prestige 25-29", true,  () -> prestigeList.getValue()));
    public final BooleanSetting plP30_34       = register(new BooleanSetting("  Prestige 30-34", true,  () -> prestigeList.getValue()));
    public final BooleanSetting plP35_39       = register(new BooleanSetting("  Prestige 35-39", true,  () -> prestigeList.getValue()));

    public Pit() {
        super("Pit", false);
    }

    @Override
    public void onDisabled() {
        saveHudLayout();
        saveKosList();
        if (mc.thePlayer != null) agSetKeys(false);
    }

    // ── Aim Assist logic ──────────────────────────────────────────────────────

    private boolean passesRequirements(EntityPlayer p) {
        // Health requirement
        if (aaReqHealth.getValue() && p.getHealth() > aaMaxHealth.getValue()) return false;

        // Armor tier requirement
        if (aaReqArmor.getValue()) {
            String tier = aaArmorTier.getValue();
            if (!tier.equals("Any") && !playerHasArmorTier(p, tier)) return false;
        }

        return true;
    }

    private int countArmorPieces(EntityPlayer p, String tier) {
        int count = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack armor = p.getCurrentArmor(i);
            if (armor == null || !(armor.getItem() instanceof ItemArmor)) continue;
            ItemArmor.ArmorMaterial mat = ((ItemArmor) armor.getItem()).getArmorMaterial();
            String matName = mat.name().toLowerCase();
            boolean matches =
                (tier.equalsIgnoreCase("Leather") && matName.equals("cloth"))   ||
                (tier.equalsIgnoreCase("Chain")   && matName.equals("chain"))   ||
                (tier.equalsIgnoreCase("Iron")    && matName.equals("iron"))    ||
                (tier.equalsIgnoreCase("Gold")    && matName.equals("gold"))    ||
                (tier.equalsIgnoreCase("Diamond") && matName.equals("diamond"));
            if (matches) count++;
        }
        return count;
    }

    private boolean playerHasArmorTier(EntityPlayer p, String tier) {
        if (tier.equals("Any")) return true;
        // Target must have at least 2 pieces of the specified tier
        if (countArmorPieces(p, tier) < 2) return false;
        // And must NOT have 2 or more diamond pieces (to avoid locking onto diamond players)
        if (!tier.equalsIgnoreCase("Diamond") && countArmorPieces(p, "Diamond") >= 2) return false;
        return true;
    }

    private boolean isValidAaTarget(EntityPlayer p) {
        if (p == mc.thePlayer || p == mc.thePlayer.ridingEntity) return false;
        if (p == mc.getRenderViewEntity() || p == mc.getRenderViewEntity().ridingEntity) return false;
        if (p.deathTime > 0) return false;
        if (RotationUtil.distanceToEntity(p) > aaRange.getValue()) return false;
        if (RotationUtil.angleToEntity(p) > (float) aaFov.getValue()) return false;
        if (RotationUtil.rayTrace(p) != null) return false;
        if (TeamUtil.isFriend(p)) return false;
        if (aaBotCheck.getValue() && TeamUtil.isBot(p)) return false;
        if (aaTeam.getValue() && TeamUtil.isSameTeam(p)) return false;
        if (!passesRequirements(p)) return false;
        return true;
    }

    private boolean isInReach(EntityPlayer p) {
        Reach reach = (Reach) Myau.moduleManager.modules.get(Reach.class);
        double distance = reach.isEnabled() ? reach.range.getValue() : 3.0;
        return RotationUtil.distanceToEntity(p) <= distance;
    }

    private boolean isLookingAtBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.POST || mc.currentScreen != null) return;

        // ── Events fetch ─────────────────────────────────────────────────────
        if (pitEvents.getValue()) {
            evTick++;
            // Fetch every ~20 ticks (1s) off main thread
            if (evTick % 20 == 0) {
                long now = System.currentTimeMillis();
                if (now - evLastFetch > 1000L) {
                    evLastFetch = now;
                    new Thread(() -> {
                        try {
                            StringBuilder sb = new StringBuilder();
                            URL url = new URL("https://raw.githubusercontent.com/BrookeAFK/brookeafk-api/main/events.js");
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestProperty("accept", "application/json");
                            InputStream is = conn.getInputStream();
                            BufferedReader br = new BufferedReader(new InputStreamReader(is));
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line);
                            br.close();
                            evResponse = sb.toString();
                        } catch (Exception ignored) {}
                    }).start();
                }
            }
            // Parse response on tick
            if (evResponse != null) {
                try {
                    JsonArray arr = new JsonParser().parse(evResponse).getAsJsonArray();
                    evList.clear();
                    int count = (int) evCount.getValue();
                    for (int i = evPassIndex; i < evPassIndex + count; i++) {
                        try {
                            String name = arr.get(i).getAsJsonObject().get("event").getAsString();
                            long ts     = arr.get(i).getAsJsonObject().get("timestamp").getAsLong();
                            long ms     = ts - Instant.now().toEpochMilli();
                            if (ms < 0) { evPassIndex++; continue; }
                            long mins = TimeUnit.MILLISECONDS.toMinutes(ms);
                            long secs = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
                            evList.add(name + " [" + String.format("%02d:%02d", mins, secs) + "]");
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
                evResponse = null;
            }
        }

        // ── Gold Requirement ──────────────────────────────────────────────────
        if (goldReq.getValue() && mc.thePlayer != null && mc.theWorld != null) {
            grTick++;
            if (grTick % 100 == 0) {
                PacketUtil.sendPacket(new C01PacketChatMessage("/goldreq " + mc.thePlayer.getName()));
            }
        }

        // ── Aim Assist ────────────────────────────────────────────────────────
        if (aimAssist.getValue()) {
            if (!aaWeapon.getValue() || ItemUtil.hasRawUnbreakingEnchant()
                    || (aaTools.getValue() && ItemUtil.isHoldingTool())) {

                boolean attacking = PlayerUtil.isAttacking();
                if (!attacking || !isLookingAtBlock()) {
                    if (attacking || !aaTimer.hasTimeElapsed(350L)) {

                        List<EntityPlayer> inRange = mc.theWorld.loadedEntityList.stream()
                                .filter(e -> e instanceof EntityPlayer)
                                .map(e -> (EntityPlayer) e)
                                .filter(this::isValidAaTarget)
                                .sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                                .collect(Collectors.toList());

                        if (inRange.isEmpty()) return;

                        if (inRange.stream().anyMatch(this::isInReach))
                            inRange.removeIf(p -> !isInReach(p));

                        EntityPlayer target = inRange.get(0);
                        if (RotationUtil.distanceToEntity(target) <= 0.0) return;

                        AxisAlignedBB bb     = target.getEntityBoundingBox();
                        double        border = target.getCollisionBorderSize();
                        float[] rotation = RotationUtil.getRotationsToBox(
                                bb.expand(border, border, border),
                                mc.thePlayer.rotationYaw,
                                mc.thePlayer.rotationPitch,
                                180.0F,
                                (float) aaSmoothing.getValue() / 100.0F
                        );

                        float yaw   = (float) Math.min(Math.abs(aaHSpeed.getValue()), 10.0);
                        float pitch = (float) Math.min(Math.abs(aaVSpeed.getValue()), 10.0);

                        Myau.rotationManager.setRotation(
                                mc.thePlayer.rotationYaw   + (rotation[0] - mc.thePlayer.rotationYaw)   * 0.1F * yaw,
                                mc.thePlayer.rotationPitch + (rotation[1] - mc.thePlayer.rotationPitch) * 0.1F * pitch,
                                0,
                                false
                        );
                    }
                }
            }
        }

        // ── AutoGrinder ───────────────────────────────────────────────────────
        if (autoGrinder.getValue()) {
            agTicks++;
            EntityLivingBase agTarget = agGetTarget();
            if (agTarget == null) {
                agSetKeys(false);
            } else {
                // Rotations
                double[] yawPitch = agGetYawPitch(agTarget);
                float targetYaw   = (float) yawPitch[0];
                float targetPitch = (float) yawPitch[1];
                if (agMode.getValue().equals("Legit")) {
                    float smoothYaw   = mc.thePlayer.rotationYaw   + 0.1f * (targetYaw   - mc.thePlayer.rotationYaw);
                    float smoothPitch = mc.thePlayer.rotationPitch + 0.1f * (targetPitch - mc.thePlayer.rotationPitch);
                    Myau.rotationManager.setRotation(smoothYaw, smoothPitch, 1, false);
                } else {
                    Myau.rotationManager.setRotation(targetYaw, targetPitch, 1, true);
                }
                agSetKeys(true);
                // Attack every 2 ticks within range
                if (agTicks % 2 == 0 && mc.thePlayer.getDistanceToEntity(agTarget) <= agRange.getValue()) {
                    double yawDiff   = Math.abs(mc.thePlayer.rotationYaw   - targetYaw);
                    double pitchDiff = Math.abs(mc.thePlayer.rotationPitch - targetPitch);
                    if (agMode.getValue().equals("Blatant") || (yawDiff <= agFov.getValue() && pitchDiff <= agFov.getValue())) {
                        PlayerUtil.attackEntity(agTarget);
                        mc.thePlayer.swingItem();
                    }
                }
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || !goldReq.getValue()) return;
        if (event.getType() != EventType.PRE) return;
        if (!(event.getPacket() instanceof S02PacketChat)) return;

        String msg = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();
        String name = mc.thePlayer != null ? mc.thePlayer.getName() : "";
        if (!msg.contains(name + ":")) return;

        try {
            String part = msg.split(name + ":")[1].trim();
            String[] parts = part.split("/");
            if (parts.length == 2) {
                grGained = Double.parseDouble(parts[0].replaceAll("[^0-9.]", ""));
                grNeeded = Double.parseDouble(parts[1].replaceAll("[^0-9.]", ""));
                event.setCancelled(true); // hide from chat
            }
        } catch (Exception ignored) {}

        // ── Streak chat parsing ───────────────────────────────────────────────
        if (streak.getValue()) {
            String raw = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();
            if (raw.contains("DEATH!")) {
                stPaused = true;
                stDeaths++;
                stKills = 0; stAssists = 0; stXP = 0f; stGold = 0f;
                stTimer.reset();
                if (deathRecap.getValue()) sendDeathRecap(raw);
            } else if (raw.contains("KILL!")) {
                stKills++;
                stPaused = false;
                // Killstreak Announcer
                if (ksAnnouncer.getValue()) {
                    int k = stKills;
                    int m1 = (int)ksMilestone1.getValue(), m2 = (int)ksMilestone2.getValue(), m3 = (int)ksMilestone3.getValue();
                    String ksMsg = null;
                    if (k == m1) ksMsg = k + " kill streak!";
                    else if (k == m2) ksMsg = k + " kill streak! gg";
                    else if (k == m3) ksMsg = k + " kill streak! gg ez";
                    if (ksMsg != null) {
                        final String toSend = ksMsg;
                        new Thread(() -> {
                            try { Thread.sleep(500); } catch (Exception ignored) {}
                            myau.util.PacketUtil.sendPacket(new net.minecraft.network.play.client.C01PacketChatMessage(toSend));
                        }).start();
                    }
                }
            } else if (raw.contains("ASSIST!")) {
                stAssists++;
            } else {
                try {
                    if (raw.contains("XP") && raw.contains("+")) {
                        String xpPart = raw.split("\\+")[1].split("XP")[0].trim();
                        stXP += Float.parseFloat(xpPart);
                    }
                    if (raw.contains("g") && raw.split("\\+").length > 2) {
                        String goldPart = raw.split("\\+")[2].split("g")[0].trim();
                        stGold += Float.parseFloat(goldPart);
                    }
                } catch (Exception ignored2) {}
            }
        }

        // ── AutoMath chat parsing ─────────────────────────────────────────────
        if (autoMath.getValue()) {
            String raw = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();
            String prefix = "QUICK MATHS! Solve: ";
            if (raw.startsWith(prefix)) {
                String equation = raw.substring(prefix.length()).replace("x", "*");
                try {
                    double result = amEvaluate(equation.replaceAll("\\s+", ""));
                    long rounded = Math.round(result);
                    amPendingSolution = Long.toString(rounded);
                    mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.LIGHT_PURPLE + "" + EnumChatFormatting.BOLD + "QUICK MATHS! "
                        + EnumChatFormatting.GRAY + "Result: " + EnumChatFormatting.YELLOW + rounded));
                    if (amAutoSubmit.getValue()) {
                        amScheduler.schedule(() -> {
                            if (amPendingSolution != null && mc.thePlayer != null) {
                                mc.thePlayer.sendChatMessage("/ac " + amPendingSolution);
                                amPendingSolution = null;
                            }
                        }, (long) amDelay.getValue(), TimeUnit.MILLISECONDS);
                    }
                } catch (Exception e) {
                    mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.RED + "AutoMath error: " + e.getMessage()));
                }
            }
        }

        // ── KOS lobby join alert ──────────────────────────────────────────────
        if (kosList.getValue() && event.getPacket() instanceof S02PacketChat) {
            String raw = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();
            // Hypixel lobby join: "NAME joined the lobby!"
            java.util.regex.Matcher mJoin = java.util.regex.Pattern
                .compile("^(\\S+) joined the lobby!")
                .matcher(raw);
            if (mJoin.find()) {
                String joined = mJoin.group(1);
                for (String kos : kosNames) {
                    if (kos.equalsIgnoreCase(joined)) {
                        ChatUtil.sendFormatted("&c[KOS] ☠ &c&l" + joined + " &r&chas joined your lobby!");
                        playCustomSound(kosSound);
                        break;
                    }
                }
            }
        }

        // ── Contract Tracker chat parsing ─────────────────────────────────────
        if (contractTracker.getValue()) {
            String ctRaw = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();
            // Contract accepted: "Contract accepted: Kill 15 players for 500g"
            java.util.regex.Matcher ctAccept = java.util.regex.Pattern
                .compile("Contract accepted: (.+?) (\\d+) .+ for (\\S+)")
                .matcher(ctRaw);
            if (ctAccept.find()) {
                ctName = ctAccept.group(1) + " " + ctAccept.group(2);
                ctGoal = Integer.parseInt(ctAccept.group(2));
                ctProgress = 0;
                ctReward = ctAccept.group(3);
            }
            // Progress update: "Contract: 7/15"
            java.util.regex.Matcher ctProg = java.util.regex.Pattern
                .compile("Contract: (\\d+)/(\\d+)")
                .matcher(ctRaw);
            if (ctProg.find()) {
                ctProgress = Integer.parseInt(ctProg.group(1));
                ctGoal     = Integer.parseInt(ctProg.group(2));
            }
            // Complete
            if (ctRaw.contains("Contract complete") || ctRaw.contains("contract complete")) {
                ctProgress = ctGoal;
                playCustomSound(ctSound);
                ChatUtil.sendFormatted("&a[Contract] &fComplete! Earned &6" + ctReward);
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    public void run() { ctName = ""; ctProgress = 0; ctGoal = 0; ctReward = ""; }
                }, 3000);
            }
        }

        // ── Bounty Tracker chat parsing ───────────────────────────────────────
        if (bountyTracker.getValue()) {
            String raw = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();

            // New/updated bounty: "☆ PlayerName now has a bounty of 1,234g!"
            java.util.regex.Matcher mSet = java.util.regex.Pattern
                .compile("^[\u2606\u2605]?\\s*(\\S+) now has a bounty of ([\\d,]+)g")
                .matcher(raw);
            if (mSet.find()) {
                btTargets.put(mSet.group(1), mSet.group(2) + "g");
            }

            // Bounty claimed: "☆ PlayerName's bounty of 1,234g was claimed by KillerName!"
            java.util.regex.Matcher mClaim = java.util.regex.Pattern
                .compile("^[\u2606\u2605]?\\s*(\\S+)'s bounty of [\\d,]+g was claimed by (\\S+)")
                .matcher(raw);
            if (mClaim.find()) {
                btTargets.remove(mClaim.group(1));
            }

            // Bounty reset: "☆ PlayerName's bounty has been reset"
            java.util.regex.Matcher mReset = java.util.regex.Pattern
                .compile("^[\u2606\u2605]?\\s*(\\S+)'s bounty has been reset")
                .matcher(raw);
            if (mReset.find()) {
                btTargets.remove(mReset.group(1));
            }
        }

        // ── Gamble chat parsing ───────────────────────────────────────────────
        if (gamble.getValue()) {
            String raw = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();
            if (raw.contains("MAJOR EVENT! GAMBLE starting now")) {
                gambleTracking = true;
            } else if (raw.contains("PIT EVENT ENDED: GAMBLE!")) {
                gambleTracking = false;
                gambleWaypoints.clear();
            } else if (gambleTracking) {
                Matcher m = Pattern.compile("-?\\d+\\s-?\\d+\\s-?\\d+").matcher(raw);
                while (m.find()) {
                    String[] c = m.group().split(" ");
                    try {
                        gambleWaypoints.add(new Vec3(
                            Integer.parseInt(c[0]),
                            Integer.parseInt(c[1]),
                            Integer.parseInt(c[2])));
                    } catch (Exception ignored) {}
                }
            }
            if (gambleGiantStop.getValue() && raw.contains("GIANT TICKET! Claimed By")) {
                gambleTracking = false;
                gambleWaypoints.clear();
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled()) return;
        if (mc.currentScreen instanceof Rise6ClickGui && !hudPreview.getValue()) return;

        // ── Bounty Tracker HUD (renders even in singleplayer) ─────────────────
        if (bountyTracker.getValue()) {
            float bx = (float) btX.getValue();
            float by = (float) btY.getValue();
            mc.fontRendererObj.drawStringWithShadow("\u00a76\u00a7l\u2756 BOUNTY TRACKER", bx, by, 0xFFFFAA00);
            by += mc.fontRendererObj.FONT_HEIGHT + 3;
            if (btTargets.isEmpty()) {
                mc.fontRendererObj.drawStringWithShadow("\u00a77No active bounties", bx, by, 0xFF777777);
            } else {
                for (java.util.Map.Entry<String, String> entry : btTargets.entrySet()) {
                    String line = "\u00a7e" + entry.getKey() + " \u00a76" + entry.getValue();
                    mc.fontRendererObj.drawStringWithShadow(line, bx, by, 0xFFFFFFFF);
                    by += mc.fontRendererObj.FONT_HEIGHT + 1;
                }
            }
        }


        // ── Gold Req HUD ──────────────────────────────────────────────────────
        if (goldReq.getValue()) {
            float gx = (float) grX.getValue();
            float gy = (float) grY.getValue();
            mc.fontRendererObj.drawStringWithShadow("\u00a76\u00a7lGOLD REQ", gx, gy, 0xFFFFAA00);
            gy += mc.fontRendererObj.FONT_HEIGHT + 3;
            if (grGained == 0 && grNeeded == 0) {
                mc.fontRendererObj.drawStringWithShadow("\u00a77Waiting...", gx, gy, 0xFF777777);
            } else {
                NumberFormat nf = NumberFormat.getNumberInstance();
                String text = "\u00a7a" + nf.format(grGained) + "\u00a77/\u00a76" + nf.format(grNeeded) + "g";
                mc.fontRendererObj.drawStringWithShadow(text, gx, gy, 0xFFFFFFFF);
            }
        }

        // ── Streak HUD ────────────────────────────────────────────────────────
        if (streak.getValue()) {
            int sx = (int) stX.getValue();
            int sy = (int) stY.getValue();
            int sp = (int) stSpacing.getValue();
            int alpha = (int)(stOpacity.getValue() / 100.0 * 255.0);
            String statusCol = stPaused ? "§c" : "§a";
            String status    = stPaused ? "Last" : "Active";
            long   elapsed   = stTimer.getElapsedTime() / 1000L;

            // Count visible rows for dynamic box height
            int rows = 1; // header always shown
            if (stShowKills.getValue())   rows++;
            if (stShowAssists.getValue()) rows++;
            if (stShowXP.getValue())      rows++;
            if (stShowGold.getValue())    rows++;
            if (stShowTime.getValue())    rows++;
            if (stShowKD.getValue())      rows++;
            int boxH = 8 + rows * sp;
            net.minecraft.client.gui.Gui.drawRect(sx, sy, sx + 90, sy + boxH, (alpha << 24));

            // Build ordered row list
            java.util.Map<Integer, String[]> stRows = new java.util.TreeMap<>();
            String kdStr = stDeaths == 0
                ? (stKills == 0 ? "0.00" : stKills + ".00")
                : String.format("%.2f", (float) stKills / (float) stDeaths);
            if (stShowKills.getValue())   stRows.put((int)stOrdKills.getValue(),   new String[]{"§aKills§f: §a"   + stKills});
            if (stShowAssists.getValue()) stRows.put((int)stOrdAssists.getValue(), new String[]{"§cAssists§f: §c" + stAssists});
            if (stShowXP.getValue())      stRows.put((int)stOrdXP.getValue(),      new String[]{"§bXP§f: §f"      + String.format("%.1f", stXP)});
            if (stShowGold.getValue())    stRows.put((int)stOrdGold.getValue(),    new String[]{"§6Gold§f: §6"    + String.format("%.1f", stGold)});
            if (stShowTime.getValue())    stRows.put((int)stOrdTime.getValue(),    new String[]{"§fTime§f: §f"    + formatStreakTime(elapsed)});
            if (stShowKD.getValue())      stRows.put((int)stOrdKD.getValue(),      new String[]{"§eK/D§f: §e"     + kdStr});
            if (stShowGPM.getValue()) {
                long minElapsed = Math.max(1, elapsed / 60);
                String gpmStr = String.format("%.1f", stTotalGold / Math.max(1f, elapsed / 60f));
                stRows.put((int)stOrdGPM.getValue(), new String[]{"§6GPM§f: §6" + gpmStr});
            }

            int ly = sy + 4;
            mc.fontRendererObj.drawStringWithShadow("§cS§at§dr§9e§6a§e§3k §7[" + statusCol + status + "§7]", sx + 5, ly, 0xFFFFFFFF); ly += sp;
            for (String[] row : stRows.values()) {
                mc.fontRendererObj.drawStringWithShadow(row[0], sx + 5, ly, 0xFFFFFFFF);
                ly += sp;
            }
        }


        // ── Events HUD ────────────────────────────────────────────────────────
        if (pitEvents.getValue()) {
            float ex = (float) evX.getValue();
            float ey = (float) evY.getValue();
            mc.fontRendererObj.drawStringWithShadow("\u00a7e\u00a7lPIT EVENTS", ex, ey, 0xFFFFFF55);
            ey += mc.fontRendererObj.FONT_HEIGHT + 3;
            if (evList.isEmpty()) {
                mc.fontRendererObj.drawStringWithShadow("\u00a77Fetching...", ex, ey, 0xFF777777);
            } else {
                int maxW = 0;
                for (String info : evList)
                    maxW = Math.max(maxW, mc.fontRendererObj.getStringWidth(info.split(" \\[")[0]));
                for (String info : evList) {
                    String[] parts = info.split(" \\[");
                    String evName  = parts[0];
                    String evTime  = parts[1].replace("]", "");
                    int color = evGetColor(evName);
                    mc.fontRendererObj.drawStringWithShadow(evName, ex, ey, color);
                    float tx = ex + maxW + 2;
                    mc.fontRendererObj.drawStringWithShadow("[", tx, ey, 0xFFAAAAAA);
                    tx += mc.fontRendererObj.getStringWidth("[");
                    mc.fontRendererObj.drawStringWithShadow(evTime, tx, ey, 0xFF00FF00);
                    tx += mc.fontRendererObj.getStringWidth(evTime);
                    mc.fontRendererObj.drawStringWithShadow("]", tx, ey, 0xFFAAAAAA);
                    ey += mc.fontRendererObj.FONT_HEIGHT + 2;
                }
            }
        }

        if (mc.thePlayer == null || mc.theWorld == null) return;
        // ── Prestige List HUD ─────────────────────────────────────────────────
        if (prestigeList.getValue() && mc.theWorld != null) {
            float px = (float) plX.getValue();
            float py = (float) plY.getValue();
            int oy = 0;
            for (EntityPlayer player : mc.theWorld.playerEntities) {
                if (player == mc.thePlayer) continue;
                if (!plIsMatch(player)) continue;
                String name     = player.getDisplayName().getUnformattedText();
                String armor    = plGetArmor(player);
                String location = player.posY >= 85.0 ? "§aIn Spawn" : "§cDown";
                int color = armor.equals("Chain") ? 0xFFAAAAAA : armor.equals("Diamond") ? 0xFF5555FF : 0xFFFFFFFF;
                String line = name + " - " + armor + " " + location;
                mc.fontRendererObj.drawStringWithShadow(line, px, py + oy, color);
                oy += mc.fontRendererObj.FONT_HEIGHT + 1;
            }
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || !gamble.getValue() || !gambleTracking || gambleWaypoints.isEmpty()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();

        for (Vec3 wp : gambleWaypoints) {
            double rx = wp.xCoord - rm.getRenderPosX();
            double ry = wp.yCoord - rm.getRenderPosY();
            double rz = wp.zCoord - rm.getRenderPosZ();

            GL11.glPushMatrix();
            GL11.glTranslated(rx, ry + 0.5, rz);
            GL11.glRotatef(-mc.getRenderManager().playerViewY, 0f, 1f, 0f);
            GL11.glRotatef(mc.getRenderManager().playerViewX, 1f, 0f, 0f);
            float scale = 0.02666667f;
            GL11.glScalef(-scale, -scale, scale);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            String label = "Gamble " + (int)wp.xCoord + " " + (int)wp.yCoord + " " + (int)wp.zCoord;
            int w = mc.fontRendererObj.getStringWidth(label) / 2;
            mc.fontRendererObj.drawStringWithShadow(label, -w, 0, 0xFFFFFF);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glPopMatrix();
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        btTargets.clear();
        // Also reset streak, gold req, events on lobby swap
        stKills = 0; stAssists = 0; stXP = 0; stGold = 0; stDeaths = 0; stTotalGold = 0; stStartMs = System.currentTimeMillis();
        stPaused = true;
        grGained = 0; grNeeded = 0;
        evList.clear(); evResponse = null; evPassIndex = 0;
    }

    @EventTarget
    public void onUpdate(myau.events.UpdateEvent event) {
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // ── Auto Spawn ────────────────────────────────────────────────────────
        if (autoSpawn.getValue() && event.getType() == EventType.PRE) {
            float hp = mc.thePlayer.getHealth();
            long now = System.currentTimeMillis();
            long intervalMs = (long)(asInterval.getValue() * 1000);
            if (hp > 0 && hp <= asHealth.getValue() && (now - asLastSent) >= intervalMs) {
                mc.thePlayer.sendChatMessage("/spawn");
                asLastSent = now;
            }
        }

        if (!deathRecap.getValue()) return;
        // Track closest recently-attacking player as likely killer
        float myHP = mc.thePlayer.getHealth();
        for (Object obj : mc.theWorld.playerEntities) {
            if (!(obj instanceof EntityPlayer)) continue;
            EntityPlayer p = (EntityPlayer) obj;
            if (p == mc.thePlayer) continue;
            if (p.deathTime > 0) continue;
            // If player is within 8 blocks and swinging, record as potential killer
            if (mc.thePlayer.getDistanceToEntity(p) < 8.0f && p.isSwingInProgress) {
                drKillerName   = p.getName();
                drKillerWeapon = p.getHeldItem();
                drMyHP         = myHP;
            }
        }
    }

    @EventTarget
    public void onPress(KeyEvent event) {
        if (!isEnabled()) return;
        int k = event.getKey();
        if (k == 0) return;

        // Submodule keybinds
        if (kb_aimAssist.getKeyCode()     != 0 && k == kb_aimAssist.getKeyCode())     { aimAssist.toggle();     Myau.moduleManager.playSound(); notifySubmodule("Aim Assist",     aimAssist.getValue()); }
        if (kb_goldReq.getKeyCode()       != 0 && k == kb_goldReq.getKeyCode())       { goldReq.toggle();       Myau.moduleManager.playSound(); notifySubmodule("Gold Req",        goldReq.getValue()); }
        if (kb_streak.getKeyCode()        != 0 && k == kb_streak.getKeyCode())        { streak.toggle();        Myau.moduleManager.playSound(); notifySubmodule("Streak",          streak.getValue()); }
        if (kb_pitEvents.getKeyCode()     != 0 && k == kb_pitEvents.getKeyCode())     { pitEvents.toggle();     Myau.moduleManager.playSound(); notifySubmodule("Events",          pitEvents.getValue()); }
        if (kb_autoMath.getKeyCode()      != 0 && k == kb_autoMath.getKeyCode())      { autoMath.toggle();      Myau.moduleManager.playSound(); notifySubmodule("AutoMath",        autoMath.getValue()); }
        if (kb_autoGrinder.getKeyCode()   != 0 && k == kb_autoGrinder.getKeyCode())   { autoGrinder.toggle();   Myau.moduleManager.playSound(); notifySubmodule("AutoGrinder",     autoGrinder.getValue()); }
        if (kb_gamble.getKeyCode()        != 0 && k == kb_gamble.getKeyCode())        { gamble.toggle();        Myau.moduleManager.playSound(); notifySubmodule("Gamble",          gamble.getValue()); }
        if (kb_bountyTracker.getKeyCode() != 0 && k == kb_bountyTracker.getKeyCode()) { bountyTracker.toggle(); Myau.moduleManager.playSound(); notifySubmodule("Bounty Tracker",  bountyTracker.getValue()); }
        if (kb_prestigeList.getKeyCode()  != 0 && k == kb_prestigeList.getKeyCode())  { prestigeList.toggle();  Myau.moduleManager.playSound(); notifySubmodule("Prestige List",   prestigeList.getValue()); }
        if (kb_kosList.getKeyCode()        != 0 && k == kb_kosList.getKeyCode())        { kosList.toggle();        Myau.moduleManager.playSound(); notifySubmodule("KOS List",        kosList.getValue()); }
        if (kb_deathRecap.getKeyCode()     != 0 && k == kb_deathRecap.getKeyCode())     { deathRecap.toggle();     Myau.moduleManager.playSound(); notifySubmodule("Death Recap",     deathRecap.getValue()); }
        if (kb_ksAnnouncer.getKeyCode()    != 0 && k == kb_ksAnnouncer.getKeyCode())    { ksAnnouncer.toggle();    Myau.moduleManager.playSound(); notifySubmodule("KS Announcer",    ksAnnouncer.getValue()); }
        if (kb_contract.getKeyCode()       != 0 && k == kb_contract.getKeyCode())       { contractTracker.toggle(); Myau.moduleManager.playSound(); notifySubmodule("Contract",        contractTracker.getValue()); }
        if (kb_autoSpawn.getKeyCode()      != 0 && k == kb_autoSpawn.getKeyCode())      { autoSpawn.toggle();      Myau.moduleManager.playSound(); notifySubmodule("Auto Spawn",      autoSpawn.getValue()); }

        // AimAssist attack timer
        if (aimAssist.getValue()
                && k == mc.gameSettings.keyBindAttack.getKeyCode()
                && !Myau.moduleManager.modules.get(AutoClicker.class).isEnabled()) {
            aaTimer.reset();
        }
    }

    private void sendDeathRecap(String deathMsg) {
        // Parse killer name from message if possible
        // Hypixel Pit death: "☠ You were killed by KillerName ..."
        String killer = drKillerName;
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("killed by (\\S+)").matcher(deathMsg);
        if (m.find()) killer = m.group(1);
        if (killer.isEmpty()) killer = "Unknown";

        float hp = drMyHP;
        String hpStr = String.format("%.1f", hp);

        // Build base message
        IChatComponent msg = new ChatComponentText("");

        // Header
        msg.appendSibling(new ChatComponentText("§c§l☠ Killed by ")
            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED).setBold(true)));
        msg.appendSibling(new ChatComponentText("§f" + killer));
        msg.appendSibling(new ChatComponentText("  §7HP: §c" + hpStr + "§7/§c20"));

        ChatUtil.send(msg);

        // Weapon line with hover
        if (drKillerWeapon != null) {
            String weaponName = drKillerWeapon.getDisplayName();

            // Build enchant list for hover
            StringBuilder enchants = new StringBuilder(weaponName + "\n");
            try {
                NBTTagList enchList = drKillerWeapon.getEnchantmentTagList();
                if (enchList != null) {
                    for (int i = 0; i < enchList.tagCount(); i++) {
                        NBTTagCompound tag = enchList.getCompoundTagAt(i);
                        int id  = tag.getShort("id");
                        int lvl = tag.getShort("lvl");
                        Enchantment enc = Enchantment.getEnchantmentById(id);
                        if (enc != null) {
                            enchants.append("§7").append(enc.getTranslatedName(lvl)).append("\n");
                        }
                    }
                }
            } catch (Exception ignored) {}

            // Hover item component
            IChatComponent weaponComp = new ChatComponentText("§7  Weapon: §f" + weaponName + " §8(hover)");
            ChatStyle style = new ChatStyle();
            style.setChatHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_ITEM,
                new ChatComponentText(drKillerWeapon.writeToNBT(new NBTTagCompound()).toString())
            ));
            weaponComp.setChatStyle(style);
            ChatUtil.send(weaponComp);
        }

        // Reset
        drKillerName   = "";
        drKillerWeapon = null;
        drMyHP         = 0f;
    }

    private static final String[] SOUND_MAP = {"note.pling","note.bass","note.harp","random.orb","random.levelup","random.chestopen","random.anvil_land"};
    private void playCustomSound(DropdownSetting setting) {
        String snd = SOUND_MAP[Math.min(setting.getIndex(), SOUND_MAP.length - 1)];
        myau.util.SoundUtil.playSound(snd);
    }

    private String buildProgressBar(float pct, int len) {
        int filled = (int)(pct * len);
        StringBuilder bar = new StringBuilder("\u00a7a[");
        for (int i = 0; i < len; i++) bar.append(i < filled ? "\u00a7a|" : "\u00a78|");
        bar.append("\u00a7a]");
        return bar.toString();
    }

    private void notifySubmodule(String name, boolean on) {
        myau.module.modules.HUD hud = (myau.module.modules.HUD) Myau.moduleManager.getModule(myau.module.modules.HUD.class);
        if (hud == null || !hud.toggleAlerts.getValue()) return;
        String status = on ? "&a&lON" : "&c&lOFF";
        ChatUtil.sendFormatted(String.format("%s%s: %s&r", Myau.clientName, name, status));
    }

    // ── Events helpers ────────────────────────────────────────────────────────

    private int evGetColor(String name) {
        if (name.contains("Blockhead"))        return 0xFFFFAA00;
        if (name.contains("Pizza"))            return 0xFFFF6655;
        if (name.contains("Beast"))            return 0xFF558855;
        if (name.contains("Robbery"))          return 0xFFFFAA00;
        if (name.contains("Spire"))            return 0xFFAA55AA;
        if (name.contains("Squads"))           return 0xFF5555FF;
        if (name.contains("Team Deathmatch"))  return 0xFFAA55AA;
        if (name.contains("Raffle"))           return 0xFFFFAA00;
        if (name.contains("Rage Pit"))         return 0xFFFF6655;
        if (name.contains("2x Rewards"))       return 0xFF00AA00;
        if (name.contains("Giant Cake"))       return 0xFFFFAAFF;
        if (name.contains("KOTL"))             return 0xFF558855;
        if (name.contains("Dragon Egg"))       return 0xFFAA55AA;
        if (name.contains("Auction"))          return 0xFFFFFF55;
        if (name.contains("Quick Maths"))      return 0xFFAA55AA;
        if (name.contains("KOTH"))             return 0xFF5555FF;
        if (name.contains("Care Package"))     return 0xFFFFAA00;
        if (name.contains("All bounty"))       return 0xFFFFAA00;
        if (name.contains("Gamble"))           return 0xFFFFAA00;
        return 0xFFFFFFFF;
    }

    // ── Prestige List helpers ─────────────────────────────────────────────────

    private boolean plIsMatch(EntityPlayer player) {
        String name = player.getDisplayName().getFormattedText();
        return (plP0.getValue()     && name.contains("§7["))
            || (plP1_4.getValue()   && name.contains("§9["))
            || (plP5_9.getValue()   && name.contains("§e["))
            || (plP10_14.getValue() && name.contains("§6["))
            || (plP15_19.getValue() && name.contains("§c["))
            || (plP20_24.getValue() && name.contains("§5["))
            || (plP25_29.getValue() && name.contains("§d["))
            || (plP30_34.getValue() && name.contains("§f["))
            || (plP35_39.getValue() && name.contains("§b["));
    }

    private String plGetArmor(EntityPlayer player) {
        for (int i = 0; i < 4; i++) {
            ItemStack s = player.getCurrentArmor(i);
            if (s == null || !(s.getItem() instanceof ItemArmor)) continue;
            ItemArmor.ArmorMaterial mat = ((ItemArmor) s.getItem()).getArmorMaterial();
            if (mat == ItemArmor.ArmorMaterial.CHAIN)   return "Chain";
            if (mat == ItemArmor.ArmorMaterial.DIAMOND) return "Diamond";
        }
        return "None";
    }

    // ── AutoGrinder helpers ───────────────────────────────────────────────────

    private EntityLivingBase agGetTarget() {
        if (mc.theWorld == null || mc.thePlayer == null) return null;
        List<EntityLivingBase> targets = mc.theWorld.loadedEntityList.stream()
            .filter(e -> e instanceof EntityLivingBase && e != mc.thePlayer)
            .map(e -> (EntityLivingBase) e)
            .filter(e -> !e.isDead && e.getHealth() > 0)
            .collect(Collectors.toList());

        if (agSorting.getValue().equals("Health")) {
            return targets.stream()
                .min(Comparator.comparing(EntityLivingBase::getHealth))
                .orElse(null);
        } else {
            return targets.stream()
                .min(Comparator.comparingDouble(e -> mc.thePlayer.getDistanceToEntity(e)))
                .orElse(null);
        }
    }

    private double[] agGetYawPitch(EntityLivingBase target) {
        double dx = target.posX - mc.thePlayer.posX;
        double dy = target.posY + target.getEyeHeight() - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = target.posZ - mc.thePlayer.posZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double pitch = -Math.asin(dy / dist) * (180.0 / Math.PI);
        double yaw   =  Math.atan2(dz, dx)   * (180.0 / Math.PI) - 90.0;
        return new double[]{yaw, pitch};
    }

    private void agSetKeys(boolean on) {
        ((IAccessorKeyBinding) mc.gameSettings.keyBindForward).setPressed(on);
        ((IAccessorKeyBinding) mc.gameSettings.keyBindAttack).setPressed(on);
        ((IAccessorKeyBinding) mc.gameSettings.keyBindJump).setPressed(on);
        if (!on) {
            ((IAccessorKeyBinding) mc.gameSettings.keyBindBack).setPressed(false);
            ((IAccessorKeyBinding) mc.gameSettings.keyBindLeft).setPressed(false);
            ((IAccessorKeyBinding) mc.gameSettings.keyBindRight).setPressed(false);
        }
    }

    // ── Math solver ───────────────────────────────────────────────────────────

    private double amEvaluate(String eq) throws Exception {
        Stack<Double> vals = new Stack<>();
        Stack<Character> ops = new Stack<>();
        for (int i = 0; i < eq.length(); i++) {
            char ch = eq.charAt(i);
            if (Character.isDigit(ch) || ch == '.') {
                StringBuilder sb = new StringBuilder();
                while (i < eq.length() && (Character.isDigit(eq.charAt(i)) || eq.charAt(i) == '.'))
                    sb.append(eq.charAt(i++));
                vals.push(Double.parseDouble(sb.toString()));
                i--;
            } else if (ch == '(') {
                ops.push(ch);
            } else if (ch == ')') {
                while (ops.peek() != '(')
                    vals.push(amApply(ops.pop(), vals.pop(), vals.pop()));
                ops.pop();
            } else if (amIsOp(ch)) {
                while (!ops.isEmpty() && amPrec(ch) <= amPrec(ops.peek()))
                    vals.push(amApply(ops.pop(), vals.pop(), vals.pop()));
                ops.push(ch);
            }
        }
        while (!ops.isEmpty()) vals.push(amApply(ops.pop(), vals.pop(), vals.pop()));
        return vals.pop();
    }

    private boolean amIsOp(char c) { return c == '+' || c == '-' || c == '*' || c == '/'; }

    private int amPrec(char c) {
        if (c == '*' || c == '/') return 2;
        if (c == '+' || c == '-') return 1;
        return -1;
    }

    private double amApply(char op, double b, double a) throws Exception {
        switch (op) {
            case '+': return a + b;
            case '-': return a - b;
            case '*': return a * b;
            case '/':
                if (b == 0) throw new Exception("Division by zero");
                return a / b;
            default: throw new Exception("Unknown operator: " + op);
        }
    }

    private String formatStreakTime(long seconds) {
        long hrs  = seconds / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (hrs  > 0) sb.append(hrs).append("hr ");
        if (mins > 0) sb.append(mins).append("m ");
        sb.append(secs).append("s");
        return sb.toString().trim();
    }
}
