package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.events.WindowClickEvent;
import myau.mixin.IAccessorItemSword;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ChatUtil;
import myau.util.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.*;
import net.minecraft.world.WorldSettings.GameType;
import org.apache.commons.lang3.RandomUtils;

public class ChestStealer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int     clickDelay = 0;
    private int     oDelay     = 0;
    private boolean inChest    = false;
    private boolean warnedFull = false;

    public final SliderSetting  minDelay   = new SliderSetting("Min Delay",   1, 0, 20, 1);
    public final SliderSetting  maxDelay   = new SliderSetting("Max Delay",   2, 0, 20, 1);
    public final SliderSetting  openDelay  = new SliderSetting("Open Delay",  1, 0, 20, 1);
    public final BooleanSetting autoClose  = new BooleanSetting("Auto Close",  false);
    public final BooleanSetting nameCheck  = new BooleanSetting("Name Check",  true);
    public final BooleanSetting skipTrash  = new BooleanSetting("Skip Trash",  true);
    public final BooleanSetting moreArmor  = new BooleanSetting("More Armor",  false);
    public final BooleanSetting moreSword  = new BooleanSetting("More Sword",  false);

    public ChestStealer() {
        super("ChestStealer", false);
        register(minDelay); register(maxDelay); register(openDelay);
        register(autoClose); register(nameCheck); register(skipTrash);
        register(moreArmor); register(moreSword);
    }

    private boolean isValidGameMode() {
        GameType gt = mc.playerController.getCurrentGameType();
        return gt == GameType.SURVIVAL || gt == GameType.ADVENTURE;
    }

    private boolean isMoreArmor(ItemStack stack) {
        if (stack == null || !moreArmor.getValue() || !(stack.getItem() instanceof ItemArmor)) return false;
        ItemArmor.ArmorMaterial m = ((ItemArmor) stack.getItem()).getArmorMaterial();
        if (m == ItemArmor.ArmorMaterial.DIAMOND) return true;
        return m == ItemArmor.ArmorMaterial.IRON && stack.isItemEnchanted();
    }

    private boolean isMoreSword(ItemStack stack) {
        if (stack == null || !moreSword.getValue() || !(stack.getItem() instanceof ItemSword)) return false;
        Item.ToolMaterial m = ((IAccessorItemSword) stack.getItem()).getMaterial();
        if (m == Item.ToolMaterial.EMERALD) return true;
        if (EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, stack) != 0) return true;
        return m == Item.ToolMaterial.IRON && stack.isItemEnchanted();
    }

    private boolean isInvManagerRequire(ItemStack stack) {
        if (stack == null) return false;
        InvManager invManager = (InvManager) Myau.moduleManager.modules.get(InvManager.class);
        if (ItemUtil.ItemType.Block.contains(stack))
            return !invManager.isEnabled() || ItemUtil.findInventorySlot(ItemUtil.ItemType.Block) < invManager.blocks.getValue();
        if (ItemUtil.ItemType.Projectile.contains(stack))
            return !invManager.isEnabled() || ItemUtil.findInventorySlot(ItemUtil.ItemType.Projectile) < invManager.projectiles.getValue();
        if (ItemUtil.ItemType.FishRod.contains(stack))
            return ItemUtil.findInventorySlot(ItemUtil.ItemType.Projectile) == 0;
        if (ItemUtil.ItemType.Arrow.contains(stack))
            return !invManager.isEnabled() || ItemUtil.findInventorySlot(ItemUtil.ItemType.Arrow) < invManager.arrow.getValue();
        return false;
    }

    private void shiftClick(int windowId, int slotId) {
        mc.playerController.windowClick(windowId, slotId, 0, 1, mc.thePlayer);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (clickDelay > 0) clickDelay--;
        if (oDelay > 0) oDelay--;
        if (!(mc.currentScreen instanceof GuiChest)) { inChest = false; return; }
        Container container = ((GuiChest) mc.currentScreen).inventorySlots;
        if (!(container instanceof ContainerChest)) { inChest = false; return; }
        if (!inChest) {
            inChest = true; warnedFull = false;
            oDelay = (int) openDelay.getValue() + 1;
        }
        if (oDelay > 0 || clickDelay > 0 || !isEnabled() || !isValidGameMode()) return;
        IInventory inventory = ((ContainerChest) container).getLowerChestInventory();
        if (nameCheck.getValue()) {
            String name = inventory.getName();
            if (!name.equals(I18n.format("container.chest")) && !name.equals(I18n.format("container.chestDouble"))) return;
        }
        if (mc.thePlayer.inventory.getFirstEmptyStack() == -1) {
            if (!warnedFull) {
                ChatUtil.sendFormatted(String.format("%s%s: &cYour inventory is full!&r", Myau.clientName, getName()));
                warnedFull = true;
            }
            if (autoClose.getValue()) mc.thePlayer.closeScreen();
            return;
        }
        if (skipTrash.getValue()) {
            int bestSword = -1; double bestDamage = 0;
            int[] bestArmorSlots = {-1,-1,-1,-1}; double[] bestArmorProt = {0,0,0,0};
            int bestPickaxe = -1; float bestPickEff = 1;
            int bestShovel  = -1; float bestShvEff  = 1;
            int bestAxe     = -1; float bestAxeEff  = 1;
            int bestBow     = -1; double bestBowDmg = 0;
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                if (!container.getSlot(i).getHasStack()) continue;
                ItemStack stack = container.getSlot(i).getStack();
                Item item = stack.getItem();
                if (item instanceof ItemSword)   { double d = ItemUtil.getAttackBonus(stack);     if (bestSword   == -1 || d > bestDamage)             { bestSword   = i; bestDamage = d; } }
                else if (item instanceof ItemArmor)   { int t = ((ItemArmor)item).armorType; double p = ItemUtil.getArmorProtection(stack); if (bestArmorSlots[t] == -1 || p > bestArmorProt[t]) { bestArmorSlots[t] = i; bestArmorProt[t] = p; } }
                else if (item instanceof ItemPickaxe) { float e = ItemUtil.getToolEfficiency(stack); if (bestPickaxe == -1 || e > bestPickEff) { bestPickaxe = i; bestPickEff = e; } }
                else if (item instanceof ItemSpade)   { float e = ItemUtil.getToolEfficiency(stack); if (bestShovel  == -1 || e > bestShvEff)  { bestShovel  = i; bestShvEff  = e; } }
                else if (item instanceof ItemAxe)     { float e = ItemUtil.getToolEfficiency(stack); if (bestAxe     == -1 || e > bestAxeEff)  { bestAxe     = i; bestAxeEff  = e; } }
                else if (item instanceof ItemBow)     { double d = ItemUtil.getBowAttackBonus(stack); if (bestBow    == -1 || d > bestBowDmg)  { bestBow     = i; bestBowDmg  = d; } }
            }
            int ss = ItemUtil.findSwordInInventorySlot(0, true);
            double curDmg = ss != -1 ? ItemUtil.getAttackBonus(mc.thePlayer.inventory.getStackInSlot(ss)) : 0;
            if (bestDamage > curDmg) { shiftClick(container.windowId, bestSword); return; }
            for (int i = 0; i < 4; i++) {
                int slot = ItemUtil.findArmorInventorySlot(i, true);
                double curProt = slot != -1 ? ItemUtil.getArmorProtection(mc.thePlayer.inventory.getStackInSlot(slot)) : 0;
                if (bestArmorProt[i] > curProt) { shiftClick(container.windowId, bestArmorSlots[i]); return; }
            }
            int ps = ItemUtil.findInventorySlot("pickaxe", 0, true);
            float curPE = ps != -1 ? ItemUtil.getToolEfficiency(mc.thePlayer.inventory.getStackInSlot(ps)) : 1;
            if (bestPickEff > curPE) { shiftClick(container.windowId, bestPickaxe); return; }
            int shs = ItemUtil.findInventorySlot("shovel", 0, true);
            float curSE = shs != -1 ? ItemUtil.getToolEfficiency(mc.thePlayer.inventory.getStackInSlot(shs)) : 1;
            if (bestShvEff > curSE) { shiftClick(container.windowId, bestShovel); return; }
            int as = ItemUtil.findInventorySlot("axe", 0, true);
            float curAE = as != -1 ? ItemUtil.getToolEfficiency(mc.thePlayer.inventory.getStackInSlot(as)) : 1;
            if (bestAxeEff > curAE) { shiftClick(container.windowId, bestAxe); return; }
            int bs = ItemUtil.findBowInventorySlot(0, true);
            double curBD = bs != -1 ? ItemUtil.getBowAttackBonus(mc.thePlayer.inventory.getStackInSlot(bs)) : 0;
            if (bestBowDmg > curBD) { shiftClick(container.windowId, bestBow); return; }
        }
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            if (!container.getSlot(i).getHasStack()) continue;
            ItemStack stack = container.getSlot(i).getStack();
            if (!skipTrash.getValue() || !ItemUtil.isNotSpecialItem(stack) || isMoreArmor(stack) || isMoreSword(stack) || isInvManagerRequire(stack)) {
                shiftClick(container.windowId, i); return;
            }
        }
        if (autoClose.getValue()) mc.thePlayer.closeScreen();
    }

    @EventTarget
    public void onWindowClick(WindowClickEvent event) {
        clickDelay = RandomUtils.nextInt((int) minDelay.getValue() + 1, (int) maxDelay.getValue() + 2);
    }

    @Override
    public void verifyValue(String settingName) {
        if (settingName.equals("Min Delay") && minDelay.getValue() > maxDelay.getValue())
            maxDelay.setValue(minDelay.getValue());
        if (settingName.equals("Max Delay") && minDelay.getValue() > maxDelay.getValue())
            minDelay.setValue(maxDelay.getValue());
    }
}