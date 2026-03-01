package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.events.LoadWorldEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ChatUtil;
import myau.util.ServerUtil;
import myau.util.SoundUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemBow;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;

import java.util.*;

public class ItemAlerts extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // Settings
    public final BooleanSetting chatAlerts    = register(new BooleanSetting("Chat Alerts",     true));
    public final BooleanSetting soundAlert    = register(new BooleanSetting("Sound Alert",     true));
    public final BooleanSetting showDistance  = register(new BooleanSetting("Show Distance",   true));
    public final BooleanSetting ignoreTeam    = register(new BooleanSetting("Ignore Team",     true));
    public final BooleanSetting swords        = register(new BooleanSetting("Swords",          true));
    public final BooleanSetting armor         = register(new BooleanSetting("Armor",           true));
    public final BooleanSetting sharpness     = register(new BooleanSetting("Sharpness",       true));
    public final BooleanSetting protection    = register(new BooleanSetting("Protection",      true));
    public final BooleanSetting enderPearls   = register(new BooleanSetting("Ender Pearls",    true));
    public final BooleanSetting bows          = register(new BooleanSetting("Bows",            true));
    public final BooleanSetting pickaxes      = register(new BooleanSetting("Pickaxes",        false));
    public final BooleanSetting potions       = register(new BooleanSetting("Potions",         true));
    public final BooleanSetting specials      = register(new BooleanSetting("Specials",        true));
    public final SliderSetting  alertDelay    = register(new SliderSetting("Delay (s)",        15, 0, 60, 1));

    // Per-player state: uuid → { lastitem, armorpiece, itemKey → lastAlertTime, teamUpgrades }
    private final Map<String, Map<String, Object>> playerData   = new HashMap<>();
    private final Set<String>                       teamUpgrades = new HashSet<>();

    // Item name lookup maps — populated on enable and each tick
    // raw MC item name → display label
    private static final Map<String, String> NAME_LABELS = new LinkedHashMap<>();
    // display name fragment → display label (for custom-named Hypixel items)
    private static final Map<String, String> DISPLAY_LABELS = new LinkedHashMap<>();

    static {
        // Raw names (item.name / getUnlocalizedName)
        NAME_LABELS.put("iron_sword",          "§fIron Sword");
        NAME_LABELS.put("diamond_sword",       "§bDiamond Sword");
        NAME_LABELS.put("diamond_pickaxe",     "§bDiamond Pickaxe");
        NAME_LABELS.put("ender_pearl",         "§3Ender Pearl");
        NAME_LABELS.put("egg",                 "§eBridge Egg");
        NAME_LABELS.put("fire_charge",         "§6Fireball");
        NAME_LABELS.put("tnt",                 "§cTNT");
        NAME_LABELS.put("prismarine_shard",    "§3Block Zapper");
        NAME_LABELS.put("obsidian",            "§5Obsidian");

        // Hypixel display names (substring match)
        DISPLAY_LABELS.put("Dream Defender",                "§fIron Golem");
        DISPLAY_LABELS.put("Machine Gun Bow",               "§4Machine Gun Bow");
        DISPLAY_LABELS.put("Charlie the Unicorn",           "§dCharlie the Unicorn");
        DISPLAY_LABELS.put("Ice Bridge",                    "§bIce Bridge");
        DISPLAY_LABELS.put("Sleeping Dust",                 "§cSleeping Dust");
        DISPLAY_LABELS.put("Unstable Teleportation Device", "§eUnstable Teleportation Device");
        DISPLAY_LABELS.put("Devastator Bow",                "§2Devastator Bow");
        DISPLAY_LABELS.put("Miracle of the Stars",          "§eMiracle of the Stars");
        DISPLAY_LABELS.put("Mystic Mirror",                 "§dMystic Mirror");
        DISPLAY_LABELS.put("Speed",                         "§bSpeed Potion");
        DISPLAY_LABELS.put("Jump",                          "§aJump Boost Potion");
        DISPLAY_LABELS.put("Invisibility",                  "§fInvisibility Potion");
    }

    // Armor slot → armor type label
    private static final Map<String, String> ARMOR_LABELS = new LinkedHashMap<>();
    static {
        ARMOR_LABELS.put("chainmail_leggings", "§fChainmail Armor");
        ARMOR_LABELS.put("iron_leggings",      "§fIron Armor");
        ARMOR_LABELS.put("diamond_leggings",   "§bDiamond Armor");
        ARMOR_LABELS.put("diamond_chestplate", "§bDiamond Armor");
        ARMOR_LABELS.put("iron_chestplate",    "§fIron Armor");
    }

    public ItemAlerts() { super("ItemAlerts", false); }

    @Override
    public void onDisabled() {
        playerData.clear();
        teamUpgrades.clear();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent e) {
        playerData.clear();
        teamUpgrades.clear();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.RECEIVE) return;
        if (!(event.getPacket() instanceof S04PacketEntityEquipment)) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        S04PacketEntityEquipment pkt = (S04PacketEntityEquipment) event.getPacket();
        int    entityId = pkt.getEntityID();
        int    slot     = pkt.getEquipmentSlot();
        ItemStack item  = pkt.getItemStack();

        Entity entity = mc.theWorld.getEntityByID(entityId);
        if (!(entity instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) entity;
        if (player == mc.thePlayer) return;
        if (player.isDead) return;

        // Ignore teammates if setting enabled
        if (ignoreTeam.getValue() && TeamUtil.isSameTeam(player)) return;

        handleEquipment(player, item, slot);
    }

    private void handleEquipment(EntityPlayer player, ItemStack item, int slot) {
        String uuid = player.getUniqueID().toString();
        Map<String, Object> data = playerData.computeIfAbsent(uuid, k -> new HashMap<>());
        long now = System.currentTimeMillis();
        long delayMs = (long)(alertDelay.getValue() * 1000);

        String itemRawName    = item != null ? getItemName(item) : "";
        String itemDisplayName = item != null && item.hasDisplayName() ? item.getDisplayName() : "";

        // ── Slot 0: Held item ────────────────────────────────────────────────
        if (slot == 0 && item != null) {
            String lastItem = (String) data.getOrDefault("lastItem", "");

            // Sword check
            if (swords.getValue() && item.getItem() instanceof ItemSword) {
                String label = NAME_LABELS.get(itemRawName);
                if (label != null) fireItemAlert(player, label, itemRawName, data, now, delayMs, lastItem);
            }

            // Pickaxe check
            if (pickaxes.getValue() && item.getItem() instanceof ItemPickaxe) {
                String label = NAME_LABELS.get(itemRawName);
                if (label != null) fireItemAlert(player, label, itemRawName, data, now, delayMs, lastItem);
            }

            // Bow check
            if (bows.getValue() && item.getItem() instanceof ItemBow) {
                String label = matchDisplayLabel(itemDisplayName);
                if (label == null) label = "§2Bow";
                fireItemAlert(player, label, itemRawName.isEmpty() ? "bow" : itemRawName, data, now, delayMs, lastItem);
            }

            // Ender pearl
            if (enderPearls.getValue() && itemRawName.equals("ender_pearl")) {
                fireItemAlert(player, "§3Ender Pearl", "ender_pearl", data, now, delayMs, lastItem);
            }

            // Other raw name items (fireball, tnt, egg, prismarine_shard, obsidian)
            if (specials.getValue()) {
                String label = NAME_LABELS.get(itemRawName);
                if (label != null && !itemRawName.endsWith("sword") && !itemRawName.endsWith("pickaxe")
                        && !itemRawName.equals("ender_pearl")) {
                    fireItemAlert(player, label, itemRawName, data, now, delayMs, lastItem);
                }
            }

            // Potion / special display-name items
            if (potions.getValue() || specials.getValue()) {
                String label = matchDisplayLabel(itemDisplayName);
                if (label != null) {
                    String key = "display_" + label;
                    fireItemAlert(player, label, key, data, now, delayMs, lastItem);
                }
            }

            // Sharpness on sword
            if (sharpness.getValue() && item.getItem() instanceof ItemSword && item.isItemEnchanted()) {
                String teamColor = getTeamColorCode(player);
                String upgradeKey = "sharpness_" + teamColor;
                if (!teamUpgrades.contains(upgradeKey)) {
                    teamUpgrades.add(upgradeKey);
                    String team = getTeamLabel(teamColor);
                    sendAlert(player, team + " §7purchased §bSharpened Swords", null);
                }
            }

            data.put("lastItem", itemRawName);
        }

        // ── Slot 2: Legs (armor upgrades) ───────────────────────────────────
        if (slot == 2 && item != null && armor.getValue()) {
            String armorLabel = ARMOR_LABELS.get(itemRawName);
            if (armorLabel != null) {
                String existingArmor = (String) data.getOrDefault("armor", "");
                if (!existingArmor.equals(itemRawName)) {
                    sendAlert(player, player.getName() + " §7equipped §r" + armorLabel, player);
                    data.put("armor", itemRawName);
                }
            }
        }

        // ── Slot 3: Chest (protection enchant) ──────────────────────────────
        if (slot == 3 && item != null && protection.getValue() && item.isItemEnchanted()) {
            String teamColor = getTeamColorCode(player);
            String upgradeKey = "protection_" + teamColor;
            if (!teamUpgrades.contains(upgradeKey)) {
                teamUpgrades.add(upgradeKey);
                String team = getTeamLabel(teamColor);
                sendAlert(player, team + " §7purchased §bReinforced Armor", null);
            }
        }

        playerData.put(uuid, data);
    }

    private void fireItemAlert(EntityPlayer player, String label, String key,
                               Map<String, Object> data, long now, long delayMs, String lastItem) {
        long lastAlert = (long) data.getOrDefault("alert_" + key, 0L);
        if (now < lastAlert) return;
        if (key.equals(lastItem)) return; // same item, no spam

        sendAlert(player, player.getName() + " §7is holding §r" + label, player);
        data.put("alert_" + key, now + delayMs);
    }

    private void sendAlert(EntityPlayer player, String message, EntityPlayer distSource) {
        String full = "§8[§dItemAlerts§8] §r" + message;
        if (distSource != null && showDistance.getValue()) {
            int dist = (int) mc.thePlayer.getDistanceToEntity(distSource);
            full += " §8(§d" + dist + "m§8)";
        }
        if (chatAlerts.getValue()) ChatUtil.sendRaw(full);
        if (soundAlert.getValue()) SoundUtil.playSound("note.pling");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getItemName(ItemStack stack) {
        String name = Item.itemRegistry.getNameForObject(stack.getItem()).toString();
        // Strip "minecraft:" prefix
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    /** Returns first matching display label for the given display name string */
    private String matchDisplayLabel(String displayName) {
        if (displayName == null || displayName.isEmpty()) return null;
        String stripped = displayName.replaceAll("§.", "");
        for (Map.Entry<String, String> e : DISPLAY_LABELS.entrySet()) {
            if (stripped.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    private String getTeamColorCode(EntityPlayer player) {
        try {
            Scoreboard sb = mc.theWorld.getScoreboard();
            ScorePlayerTeam team = sb.getPlayersTeam(player.getName());
            if (team != null && team.getColorPrefix() != null && team.getColorPrefix().length() >= 2) {
                return team.getColorPrefix().substring(1, 2);
            }
        } catch (Exception ignored) {}
        return "f";
    }

    private String getTeamLabel(String colorCode) {
        switch (colorCode) {
            case "c": return "§cRed Team";
            case "9": return "§9Blue Team";
            case "a": return "§aGreen Team";
            case "e": return "§eYellow Team";
            case "b": return "§bAqua Team";
            case "f": return "§fWhite Team";
            case "d": return "§dPink Team";
            case "8": return "§8Gray Team";
            default:  return "§7Unknown Team";
        }
    }
}
