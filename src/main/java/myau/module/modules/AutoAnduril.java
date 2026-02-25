package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MovingObjectPosition;

public class AutoAnduril extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int previousSlot = -1;
    private int currentSlot  = -1;
    private int intervalTick = -1;
    private int holdTick     = -1;

    public final SliderSetting  interval   = new SliderSetting("Interval",   40, 0, 100, 1);
    public final SliderSetting  hold       = new SliderSetting("Hold",        1, 0,  20, 1);
    public final BooleanSetting speedCheck = new BooleanSetting("Speed Check", false);
    public final SliderSetting  debug      = new SliderSetting("Debug",        0, 0,   9, 1);

    public AutoAnduril() {
        super("AutoAnduril", false);
        register(interval);
        register(hold);
        register(speedCheck);
        register(debug);
    }

    public boolean canSwap() {
        if (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mc.gameSettings.keyBindAttack.isKeyDown()) return false;
        ItemStack cur = mc.thePlayer.inventory.getStackInSlot(mc.thePlayer.inventory.currentItem);
        if (cur != null) {
            if (cur.getItem() instanceof ItemBlock && mc.gameSettings.keyBindUseItem.isKeyDown()) return false;
            if (!(cur.getItem() instanceof ItemSword) && mc.thePlayer.isUsingItem()) return false;
        }
        InvWalk invWalk = (InvWalk) Myau.moduleManager.modules.get(InvWalk.class);
        return mc.currentScreen == null
                || mc.currentScreen instanceof myau.ui.clickgui.Rise6ClickGui
                || invWalk.isEnabled() && invWalk.canInvWalk();
    }

    public boolean hasSpeed() {
        if (!speedCheck.getValue()) return false;
        PotionEffect pe = mc.thePlayer.getActivePotionEffect(Potion.moveSpeed);
        return pe != null && pe.getAmplifier() > 0;
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (isEnabled() && event.getType() == EventType.PRE) {
            if (currentSlot != -1 && currentSlot != mc.thePlayer.inventory.currentItem) {
                currentSlot = -1;
                previousSlot = -1;
                intervalTick = (int) interval.getValue();
                holdTick = -1;
            }
            if (intervalTick > 0) {
                intervalTick--;
            } else if (intervalTick == 0) {
                if (canSwap() && !hasSpeed()) {
                    int slot = ItemUtil.findAndurilHotbarSlot(mc.thePlayer.inventory.currentItem);
                    if ((int) debug.getValue() != 0 && slot == -1) slot = (int) debug.getValue() - 1;
                    if (slot != -1 && slot != mc.thePlayer.inventory.currentItem) {
                        previousSlot = mc.thePlayer.inventory.currentItem;
                        currentSlot  = mc.thePlayer.inventory.currentItem = slot;
                        intervalTick = -1;
                        holdTick     = (int) hold.getValue();
                        return;
                    } else {
                        intervalTick = (int) interval.getValue();
                        holdTick = -1;
                    }
                }
            }
            if (holdTick > 0) {
                holdTick--;
            } else if (holdTick == 0) {
                if (previousSlot != -1 && canSwap()) {
                    mc.thePlayer.inventory.currentItem = previousSlot;
                    previousSlot = -1;
                    holdTick = -1;
                    intervalTick = (int) interval.getValue();
                }
            }
        }
    }

    @Override
    public void onEnabled() {
        previousSlot = -1;
        currentSlot  = -1;
        intervalTick = (int) interval.getValue();
        holdTick     = -1;
    }

    @Override
    public void onDisabled() {
        previousSlot = -1;
        currentSlot  = -1;
        intervalTick = -1;
        holdTick     = -1;
    }
}
