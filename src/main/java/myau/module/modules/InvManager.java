package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.events.WindowClickEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldSettings.GameType;
import org.apache.commons.lang3.RandomUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;

public class InvManager extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int actionDelay = 0;
    private int oDelay = 0;
    private boolean inventoryOpen = false;
    private final TimerUtil autoArmorTime = new TimerUtil();

    public final SliderSetting  minDelay          = register(new SliderSetting("Min Delay",          1,   0,   20, 1));
    public final SliderSetting  maxDelay          = register(new SliderSetting("Max Delay",          2,   0,   20, 1));
    public final SliderSetting  openDelay         = register(new SliderSetting("Open Delay",         1,   0,   20, 1));
    public final BooleanSetting autoArmor         = register(new BooleanSetting("Auto Armor",        true));
    public final SliderSetting  autoArmorInterval = register(new SliderSetting("Armor Interval",     0,   0,  100, 1));
    public final BooleanSetting dropTrash         = register(new BooleanSetting("Drop Trash",        false));
    public final BooleanSetting checkDurability   = register(new BooleanSetting("Check Durability",  true));
    public final SliderSetting  swordSlot         = register(new SliderSetting("Sword Slot",         1,   0,    9, 1));
    public final SliderSetting  pickaxeSlot       = register(new SliderSetting("Pickaxe Slot",       3,   0,    9, 1));
    public final SliderSetting  shovelSlot        = register(new SliderSetting("Shovel Slot",        4,   0,    9, 1));
    public final SliderSetting  axeSlot           = register(new SliderSetting("Axe Slot",           5,   0,    9, 1));
    public final SliderSetting  blocksSlot        = register(new SliderSetting("Blocks Slot",        2,   0,    9, 1));
    public final SliderSetting  blocks            = register(new SliderSetting("Max Blocks",       128,  64, 2304, 64));
    public final SliderSetting  projectileSlot    = register(new SliderSetting("Projectile Slot",    7,   0,    9, 1));
    public final SliderSetting  projectiles       = register(new SliderSetting("Max Projectiles",   64,  16, 2304, 16));
    public final SliderSetting  goldAppleSlot     = register(new SliderSetting("Gold Apple Slot",    9,   0,    9, 1));
    public final SliderSetting  arrow             = register(new SliderSetting("Max Arrows",        256,   0, 2304, 16));
    public final SliderSetting  bowSlot           = register(new SliderSetting("Bow Slot",           8,   0,    9, 1));

    public InvManager() {
        super("InvManager", false);
    }

    private boolean isValidGameMode() {
        GameType g = mc.playerController.getCurrentGameType();
        return g == GameType.SURVIVAL || g == GameType.ADVENTURE;
    }

    private int convertSlotIndex(int slot) {
        if (slot >= 36) return 8 - (slot - 36);
        return slot <= 8 ? slot + 36 : slot;
    }

    private void clickSlot(int windowId, int slotId, int btn, int mode) {
        mc.playerController.windowClick(windowId, slotId, btn, mode, mc.thePlayer);
    }

    private int getStackSize(int slot) {
        if (slot == -1) return 0;
        ItemStack s = mc.thePlayer.inventory.getStackInSlot(slot);
        return s != null ? s.stackSize : 0;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (actionDelay > 0) actionDelay--;
        if (oDelay > 0) oDelay--;

        if (!(mc.currentScreen instanceof GuiInventory)) {
            inventoryOpen = false;
        } else if (!(((GuiInventory) mc.currentScreen).inventorySlots instanceof ContainerPlayer)) {
            inventoryOpen = false;
        } else {
            if (!inventoryOpen) {
                inventoryOpen = true;
                oDelay = (int) openDelay.getValue() + 1;
                autoArmorTime.reset();
            }
            if (oDelay <= 0 && actionDelay <= 0 && isEnabled() && isValidGameMode()) {
                ArrayList<Integer> equippedArmor   = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
                ArrayList<Integer> inventoryArmor  = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
                for (int i = 0; i < 4; i++) {
                    equippedArmor.set(i,  ItemUtil.findArmorInventorySlot(i, true));
                    inventoryArmor.set(i, ItemUtil.findArmorInventorySlot(i, false));
                }
                int pSword  = (int) swordSlot.getValue() - 1;
                int iSword  = ItemUtil.findSwordInInventorySlot(pSword, checkDurability.getValue());
                if (iSword == -1) iSword = ItemUtil.findSwordInInventorySlot(pSword, false);

                int pPickaxe = (int) pickaxeSlot.getValue() - 1;
                int iPickaxe = ItemUtil.findInventorySlot("pickaxe", pPickaxe, checkDurability.getValue());
                if (iPickaxe == -1) iPickaxe = ItemUtil.findInventorySlot("pickaxe", pPickaxe, false);

                int pShovel = (int) shovelSlot.getValue() - 1;
                int iShovel = ItemUtil.findInventorySlot("shovel", pShovel, checkDurability.getValue());
                if (iShovel == -1) iShovel = ItemUtil.findInventorySlot("shovel", pShovel, false);

                int pAxe = (int) axeSlot.getValue() - 1;
                int iAxe = ItemUtil.findInventorySlot("axe", pAxe, checkDurability.getValue());
                if (iAxe == -1) iAxe = ItemUtil.findInventorySlot("axe", pAxe, false);

                int pBlocks     = (int) blocksSlot.getValue() - 1;
                int iBlocks     = ItemUtil.findInventorySlot(pBlocks, ItemUtil.ItemType.Block);
                int pProj       = (int) projectileSlot.getValue() - 1;
                int iProj       = ItemUtil.findInventorySlot(pProj, ItemUtil.ItemType.Projectile);
                if (iProj == -1) iProj = ItemUtil.findInventorySlot(pProj, ItemUtil.ItemType.FishRod);
                int pGolden     = (int) goldAppleSlot.getValue() - 1;
                int iGolden     = ItemUtil.findInventorySlot(pGolden, ItemUtil.ItemType.GoldApple);
                int pBow        = (int) bowSlot.getValue() - 1;
                int iBow        = ItemUtil.findBowInventorySlot(pBow, checkDurability.getValue());
                if (iBow == -1) iBow = ItemUtil.findBowInventorySlot(pBow, false);

                if (autoArmor.getValue() && autoArmorTime.hasTimeElapsed((long) autoArmorInterval.getValue() * 50L)) {
                    for (int i = 0; i < 4; i++) {
                        int eq = equippedArmor.get(i), inv = inventoryArmor.get(i);
                        if (eq != -1 || inv != -1) {
                            int armorSlot = 39 - i;
                            if (eq != armorSlot && inv != armorSlot) {
                                if (mc.thePlayer.inventory.getStackInSlot(armorSlot) != null) {
                                    if (mc.thePlayer.inventory.getFirstEmptyStack() != -1)
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(armorSlot), 0, 1);
                                    else
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(armorSlot), 1, 4);
                                } else {
                                    clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(eq != -1 ? eq : inv), 0, 1);
                                    autoArmorTime.reset();
                                }
                                return;
                            }
                        }
                    }
                }

                LinkedHashSet<Integer> used = new LinkedHashSet<>();
                if (pSword >= 0 && pSword <= 8 && iSword != -1) {
                    used.add(pSword);
                    if (iSword != pSword) { clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(iSword), pSword, 2); return; }
                }
                if (pPickaxe >= 0 && pPickaxe <= 8 && !used.contains(pPickaxe) && iPickaxe != -1) {
                    used.add(pPickaxe);
                    if (iPickaxe != pPickaxe) { clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(iPickaxe), pPickaxe, 2); return; }
                }
                if (pShovel >= 0 && pShovel <= 8 && !used.contains(pShovel) && iShovel != -1) {
                    used.add(pShovel);
                    if (iShovel != pShovel) { clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(iShovel), pShovel, 2); return; }
                }
                if (pAxe >= 0 && pAxe <= 8 && !used.contains(pAxe) && iAxe != -1) {
                    used.add(pAxe);
                    if (iAxe != pAxe) { clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(iAxe), pAxe, 2); return; }
                }
                if (pBlocks >= 0 && pBlocks <= 8 && !used.contains(pBlocks) && iBlocks != -1) {
                    used.add(pBlocks);
                    if (iBlocks != pBlocks) { clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(iBlocks), pBlocks, 2); return; }
                }
                if (pProj >= 0 && pProj <= 8 && !used.contains(pProj) && iProj != -1) {
                    used.add(pProj);
                    if (iProj != pProj) { clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(iProj), pProj, 2); return; }
                }
                if (pGolden >= 0 && pGolden <= 8 && !used.contains(pGolden) && iGolden != -1) {
                    used.add(pGolden);
                    if (iGolden != pGolden) { clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(iGolden), pGolden, 2); return; }
                }
                if (pBow >= 0 && pBow <= 8 && !used.contains(pBow) && iBow != -1) {
                    used.add(pBow);
                    if (iBow != pBow) { clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(iBow), pBow, 2); return; }
                }

                if (dropTrash.getValue()) {
                    int curBlocks = getStackSize(iBlocks), curProj = getStackSize(iProj);
                    for (int i = 0; i < 36; i++) {
                        if (!equippedArmor.contains(i) && !inventoryArmor.contains(i)
                                && iSword != i && iPickaxe != i && iShovel != i && iAxe != i
                                && iBlocks != i && iProj != i && iGolden != i && iBow != i) {
                            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
                            if (s != null) {
                                boolean isBlock = ItemUtil.isBlock(s);
                                boolean isProjectile = ItemUtil.isProjectile(s);
                                if (isBlock) curBlocks += s.stackSize;
                                if (isProjectile) curProj += s.stackSize;
                                if (ItemUtil.isNotSpecialItem(s)
                                        && (isBlock && curBlocks >= (int) blocks.getValue()
                                         || isProjectile && curProj >= (int) projectiles.getValue())) {
                                    clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(i), 1, 4);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onClick(WindowClickEvent event) {
        actionDelay = RandomUtils.nextInt((int) minDelay.getValue() + 1, (int) maxDelay.getValue() + 2);
    }
}