package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.List;

public class AutoSwap extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private Item lastItem;
    private int  lastSlot = -1;

    public final BooleanSetting blocks      = new BooleanSetting("Blocks",      true);
    public final BooleanSetting projectiles = new BooleanSetting("Projectiles", true);
    public final BooleanSetting pearls      = new BooleanSetting("Pearls",      true);
    public final BooleanSetting swords      = new BooleanSetting("Swords",      true);
    public final BooleanSetting tools       = new BooleanSetting("Tools",       true);
    public final BooleanSetting resources   = new BooleanSetting("Resources",   true);

    private final List<String> ALLOWED_BLOCKS = Arrays.asList("stone","grass","dirt","planks","wool","wood","glass","leaves","clay","cloth","cobblestone","sand","gravel","netherrack");
    private final List<String> PROJECTILES    = Arrays.asList("egg","snowball","ender_pearl","fireball");
    private final List<String> PEARLS         = Arrays.asList("pearl","ender_pearl");
    private final List<String> SWORDS         = Arrays.asList("sword","axe");
    private final List<String> TOOLS          = Arrays.asList("rod","pickaxe","axe","shovel","hoe","flint_and_steel");
    private final List<String> RESOURCES      = Arrays.asList("265","266","388","264","diamond","gold","iron","emerald");

    public AutoSwap() {
        super("AutoSwap", false);
        register(blocks);
        register(projectiles);
        register(pearls);
        register(swords);
        register(tools);
        register(resources);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return;

        int slot = mc.thePlayer.inventory.currentItem;
        ItemStack held = mc.thePlayer.inventory.getStackInSlot(slot);

        if (lastItem != null && slot == lastSlot && (held == null || held.stackSize < 1))
            swapItem(lastItem);

        lastItem = held != null ? held.getItem() : null;
        lastSlot = slot;
    }

    private void swapItem(Item lastItem) {
        if (lastItem == null) return;
        String lastId = lastItem.getUnlocalizedName().toLowerCase();
        boolean isBlock = lastItem instanceof ItemBlock;
        int current = mc.thePlayer.inventory.currentItem;
        List<String> category = null;

        if (!isBlock) {
            if (projectiles.getValue() && containsAny(lastId, PROJECTILES) && !lastId.contains("leggings")) category = PROJECTILES;
            else if (pearls.getValue()     && containsAny(lastId, PEARLS))     category = PEARLS;
            else if (swords.getValue()     && containsAny(lastId, SWORDS))     category = SWORDS;
            else if (tools.getValue()      && containsAny(lastId, TOOLS))      category = TOOLS;
            else if (resources.getValue()  && containsAny(lastId, RESOURCES))  category = RESOURCES;
        }

        for (int offset = 1; offset <= 9; offset++) {
            int i = (current + offset) % 9;
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.stackSize >= 1) {
                Item item = stack.getItem();
                String id = item.getUnlocalizedName().toLowerCase();
                if (item == lastItem) { mc.thePlayer.inventory.currentItem = i; return; }
                if (isBlock && blocks.getValue() && isValidBlock(stack)) { mc.thePlayer.inventory.currentItem = i; return; }
                if (category != null && containsAny(id, category) && !id.contains("leggings")) { mc.thePlayer.inventory.currentItem = i; return; }
            }
        }
    }

    private boolean isValidBlock(ItemStack stack) {
        if (!blocks.getValue() || !(stack.getItem() instanceof ItemBlock)) return false;
        return containsAny(stack.getItem().getUnlocalizedName().toLowerCase(), ALLOWED_BLOCKS);
    }

    private boolean containsAny(String str, List<String> items) {
        for (String item : items) if (str.contains(item)) return true;
        return false;
    }
}
