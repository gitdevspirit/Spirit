package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LeftClickMouseEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

public class AutoClicker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private boolean clickPending    = false;
    private long    clickDelay      = 0L;
    private boolean blockHitPending = false;
    private long    blockHitDelay   = 0L;

    public final SliderSetting  minCPS           = new SliderSetting("Min CPS",        8,   1, 20,  1);
    public final SliderSetting  maxCPS           = new SliderSetting("Max CPS",       12,   1, 20,  1);
    public final BooleanSetting blockHit         = new BooleanSetting("Block Hit",     false);
    public final SliderSetting  blockHitTicks    = new SliderSetting("BH Ticks",      1.5, 1.0, 20.0, 0.5);
    public final BooleanSetting weaponsOnly      = new BooleanSetting("Weapons Only",  true);
    public final BooleanSetting allowTools       = new BooleanSetting("Allow Tools",   false);
    public final BooleanSetting breakBlocks      = new BooleanSetting("Break Blocks",  true);
    public final SliderSetting  range            = new SliderSetting("Range",          3.0, 3.0, 8.0, 0.1);
    public final SliderSetting  hitBoxVertical   = new SliderSetting("HB Vertical",   0.1, 0.0, 1.0, 0.05);
    public final SliderSetting  hitBoxHorizontal = new SliderSetting("HB Horizontal", 0.2, 0.0, 1.0, 0.05);

    public AutoClicker() {
        super("AutoClicker", false);
        register(minCPS);
        register(maxCPS);
        register(blockHit);
        register(blockHitTicks);
        register(weaponsOnly);
        register(allowTools);
        register(breakBlocks);
        register(range);
        register(hitBoxVertical);
        register(hitBoxHorizontal);
    }

    private long getNextClickDelay() {
        return 1000L / RandomUtil.nextLong((long) minCPS.getValue(), (long) maxCPS.getValue());
    }

    private long getBlockHitDelay() {
        return (long)(50.0F * (float) blockHitTicks.getValue());
    }

    private boolean isBreakingBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    private boolean canClick() {
        if (!weaponsOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant()
                || allowTools.getValue() && ItemUtil.isHoldingTool()) {
            if (breakBlocks.getValue() && isBreakingBlock() && !hasValidTarget()) {
                GameType gt = mc.playerController.getCurrentGameType();
                return gt != GameType.SURVIVAL && gt != GameType.CREATIVE;
            }
            return true;
        }
        return false;
    }

    private boolean isValidTarget(EntityPlayer p) {
        if (p == mc.thePlayer || p == mc.thePlayer.ridingEntity) return false;
        if (p == mc.getRenderViewEntity() || p == mc.getRenderViewEntity().ridingEntity) return false;
        if (p.deathTime > 0) return false;
        float border = p.getCollisionBorderSize();
        return RotationUtil.rayTrace(
                p.getEntityBoundingBox().expand(
                        border + (float) hitBoxHorizontal.getValue(),
                        border + (float) hitBoxVertical.getValue(),
                        border + (float) hitBoxHorizontal.getValue()),
                mc.thePlayer.rotationYaw,
                mc.thePlayer.rotationPitch,
                (float) range.getValue()) != null;
    }

    private boolean hasValidTarget() {
        return mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityPlayer)
                .map(e -> (EntityPlayer) e)
                .anyMatch(this::isValidTarget);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            if (clickDelay > 0L)    clickDelay    -= 50L;
            if (blockHitDelay > 0L) blockHitDelay -= 50L;
            if (mc.currentScreen != null) {
                clickPending = false;
                blockHitPending = false;
            } else {
                if (clickPending) {
                    clickPending = false;
                    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindAttack.getKeyCode());
                }
                if (blockHitPending) {
                    blockHitPending = false;
                    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                }
                if (isEnabled() && canClick() && mc.gameSettings.keyBindAttack.isKeyDown()) {
                    if (!mc.thePlayer.isUsingItem()) {
                        while (clickDelay <= 0L) {
                            clickPending = true;
                            clickDelay  += getNextClickDelay();
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                            KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
                        }
                    }
                    if (blockHit.getValue() && blockHitDelay <= 0L
                            && mc.gameSettings.keyBindUseItem.isKeyDown()
                            && ItemUtil.isHoldingSword()) {
                        blockHitPending = true;
                        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                        if (!mc.thePlayer.isUsingItem()) {
                            blockHitDelay += getBlockHitDelay();
                            KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindUseItem.getKeyCode());
                        }
                    }
                }
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onCLick(LeftClickMouseEvent event) {
        if (isEnabled() && !event.isCancelled() && !clickPending)
            clickDelay += getNextClickDelay();
    }

    @Override
    public void onEnabled() {
        clickDelay    = 0L;
        blockHitDelay = 0L;
    }

    @Override
    public String[] getSuffix() {
        long mn = (long) minCPS.getValue(), mx = (long) maxCPS.getValue();
        return mn == mx
                ? new String[]{String.valueOf(mn)}
                : new String[]{String.format("%d-%d", mn, mx)};
    }
}
