package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.*;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.PacketUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.potion.Potion;

public class AutoHeal extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();
    private boolean shouldHeal = false;
    private int prevSlot = -1;
    private int hurtTick = 0;

    public final SliderSetting  health      = register(new SliderSetting("Health %",   35,    0,  100, 1));
    public final SliderSetting  delay       = register(new SliderSetting("Delay (ms)", 4000,  0, 5000, 50));
    public final BooleanSetting regenCheck  = register(new BooleanSetting("Regen Check",  false));
    public final BooleanSetting hurtCheck   = register(new BooleanSetting("Hurt Check",   false));
    public final SliderSetting  hurtTime    = register(new SliderSetting("Hurt Time",   20,    1,  100, 1));

    public AutoHeal() {
        super("AutoHeal", false);
    }

    public boolean isSwitching() {
        return prevSlot != -1;
    }

    private int findHealingItem() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.hasDisplayName()) {
                String name = stack.getDisplayName();
                if (stack.getItem() instanceof ItemSkull && name.contains("§6") && name.contains("Golden Head"))
                    return i;
            }
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.hasDisplayName()) {
                String name = stack.getDisplayName();
                if (stack.getItem() instanceof ItemSkull && name.matches("\\S+§c's Head"))
                    return i;
            }
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.hasDisplayName()) {
                String name = stack.getDisplayName();
                if (stack.getItem() instanceof ItemFood && name.contains("§6Cornucopia")) return i;
                if (stack.getItem() instanceof ItemSoup
                        && (name.contains("§a") && name.contains("Tasty Soup")
                         || name.contains("§a") && name.contains("Assist Soup"))) return i;
            }
        }
        return -1;
    }

    private boolean hasRegenEffect() {
        return regenCheck.getValue() && mc.thePlayer.isPotionActive(Potion.regeneration);
    }

    @EventTarget(Priority.HIGH)
    public void onTick(TickEvent event) {
        if (!isEnabled()) { prevSlot = -1; return; }

        if (hurtCheck.getValue()) {
            if (hurtTick > 0) hurtTick--;
            if (mc.thePlayer.hurtTime > 0) hurtTick = (int) hurtTime.getValue();
        } else {
            hurtTick = 1;
        }

        switch (event.getType()) {
            case PRE:
                boolean percent = (float) Math.ceil(mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount())
                        / mc.thePlayer.getMaxHealth() <= (float) health.getValue() / 100.0F;
                if (shouldHeal && percent && !hasRegenEffect()
                        && timer.hasTimeElapsed((long) delay.getValue()) && hurtTick > 0) {
                    int slot = findHealingItem();
                    if (slot != -1) {
                        prevSlot = mc.thePlayer.inventory.currentItem;
                        mc.thePlayer.inventory.currentItem = slot;
                        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                        timer.reset();
                    }
                }
                shouldHeal = percent;
                break;
            case POST:
                if (prevSlot != -1) {
                    mc.thePlayer.inventory.currentItem = prevSlot;
                    prevSlot = -1;
                }
                break;
        }
    }

    @EventTarget public void onLeftClick(LeftClickMouseEvent e)  { if (isEnabled() && isSwitching()) e.setCancelled(true); }
    @EventTarget public void onRightClick(RightClickMouseEvent e) { if (isEnabled() && isSwitching()) e.setCancelled(true); }
    @EventTarget public void onHitBlock(HitBlockEvent e)          { if (isEnabled() && isSwitching()) e.setCancelled(true); }
    @EventTarget public void onSwap(SwapItemEvent e)              { if (isEnabled() && isSwitching()) e.setCancelled(true); }
}