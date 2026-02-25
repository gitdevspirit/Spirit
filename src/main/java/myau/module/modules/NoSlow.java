package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.RightClickMouseEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.BlockUtil;
import myau.util.ItemUtil;
import myau.util.PlayerUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.BlockPos;

public class NoSlow extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int count;

    public final DropdownSetting swordMode    = register(new DropdownSetting("Sword Mode",   1, "NONE", "VANILLA", "GRIM"));
    public final SliderSetting   swordMotion  = register(new SliderSetting("Sword Motion %", 100, 0, 100, 1));
    public final BooleanSetting  swordSprint  = register(new BooleanSetting("Sword Sprint",  true));
    public final BooleanSetting  killauraonly = register(new BooleanSetting("KillAura Only",  false));
    public final DropdownSetting foodMode     = register(new DropdownSetting("Food Mode",    0, "NONE", "VANILLA", "GRIM"));
    public final SliderSetting   foodMotion   = register(new SliderSetting("Food Motion %",  100, 0, 100, 1));
    public final BooleanSetting  foodSprint   = register(new BooleanSetting("Food Sprint",   true));
    public final DropdownSetting bowMode      = register(new DropdownSetting("Bow Mode",     0, "NONE", "VANILLA", "GRIM"));
    public final SliderSetting   bowMotion    = register(new SliderSetting("Bow Motion %",   100, 0, 100, 1));
    public final BooleanSetting  bowSprint    = register(new BooleanSetting("Bow Sprint",    true));

    public NoSlow() {
        super("NoSlow", false);
    }

    public boolean isSwordActive() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (killauraonly.getValue()) {
            if (!killAura.isEnabled() || killAura.getTarget() == null) return false;
        }
        return swordMode.getIndex() != 0 && ItemUtil.isHoldingSword();
    }

    public boolean isFoodActive() { return foodMode.getIndex() != 0 && ItemUtil.isEating(); }
    public boolean isBowActive()  { return bowMode.getIndex() != 0 && ItemUtil.isUsingBow(); }

    public boolean isAnyActive() {
        return mc.thePlayer.isUsingItem() && (isSwordActive() || isFoodActive() || isBowActive());
    }

    public boolean canSprint() {
        return (isSwordActive() && swordSprint.getValue())
                || (isFoodActive() && foodSprint.getValue())
                || (isBowActive() && bowSprint.getValue());
    }

    public int getMotionMultiplier() {
        count++;
        if (ItemUtil.isHoldingSword()) {
            if (swordMode.getIndex() == 2) return count % 2 == 0 ? 100 : 20;
            return (int) swordMotion.getValue();
        } else if (ItemUtil.isEating()) {
            if (foodMode.getIndex() == 2) return count % 2 == 0 ? 100 : 20;
            return (int) foodMotion.getValue();
        } else if (ItemUtil.isUsingBow()) {
            if (bowMode.getIndex() == 2) return count % 2 == 0 ? 100 : 20;
            return (int) bowMotion.getValue();
        }
        return 100;
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (!isEnabled()) return;
        if (mc.objectMouseOver != null) {
            switch (mc.objectMouseOver.typeOfHit) {
                case BLOCK:
                    BlockPos bp = mc.objectMouseOver.getBlockPos();
                    if (BlockUtil.isInteractable(bp) && !PlayerUtil.isSneaking()) return;
                    break;
                case ENTITY:
                    Entity eh = mc.objectMouseOver.entityHit;
                    if (eh instanceof EntityVillager) return;
                    if (eh instanceof EntityLivingBase && TeamUtil.isShop((EntityLivingBase) eh)) return;
                    break;
            }
        }
    }
}