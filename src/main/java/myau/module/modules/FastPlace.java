package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.mixin.IAccessorMinecraft;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.BlockUtil;
import myau.util.RotationUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockObsidian;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class FastPlace extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat df = new DecimalFormat("0.0#", new DecimalFormatSymbols(Locale.US));
    private long delayMS = 0L;

    public final SliderSetting  delay             = new SliderSetting("Delay",              1.0, 1.0, 3.0, 0.1);
    public final BooleanSetting blocksOnly        = new BooleanSetting("Blocks Only",       true);
    public final BooleanSetting placeFix          = new BooleanSetting("Place Fix",         true);
    public final BooleanSetting skipObsidian      = new BooleanSetting("Skip Obsidian",     true);
    public final BooleanSetting skipInteractable  = new BooleanSetting("Skip Interactable", true);

    public FastPlace() {
        super("FastPlace", false);
        register(delay);
        register(blocksOnly);
        register(placeFix);
        register(skipObsidian);
        register(skipInteractable);
    }

    private boolean canPlace() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack != null) {
            Item item = stack.getItem();
            if (item instanceof ItemFishingRod) return false;
            if (item instanceof ItemBlock) {
                Block block = ((ItemBlock) item).getBlock();
                if (skipObsidian.getValue() && block instanceof BlockObsidian) return false;
                if (skipInteractable.getValue() && BlockUtil.isInteractable(block)) return false;
                if (!placeFix.getValue()) return true;
                MovingObjectPosition mop = RotationUtil.rayTrace(
                        mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                        mc.playerController.getBlockReachDistance(), 1.0F);
                return mop != null
                        && mop.typeOfHit == MovingObjectType.BLOCK
                        && ((ItemBlock) item).canPlaceBlockOnSide(mc.theWorld, mop.getBlockPos(), mop.sideHit, mc.thePlayer, stack);
            }
        }
        return !blocksOnly.getValue();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        int timer = ((IAccessorMinecraft) mc).getRightClickDelayTimer();
        if (timer == 4) delayMS += (long) (50.0 * delay.getValue());
        if (delayMS > 0L) delayMS -= 50;
        if (delayMS <= 0L && timer > 1 && canPlace())
            ((IAccessorMinecraft) mc).setRightClickDelayTimer(0);
    }

    @Override
    public void onDisabled() { delayMS = 0L; }

    @Override
    public String[] getSuffix() { return new String[]{ df.format(delay.getValue()) }; }
}