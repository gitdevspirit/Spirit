package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LeftClickMouseEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.module.DropdownSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * Advanced Auto Clicker with Jitter/Butterfly patterns, exhaust simulation, and inventory clicking
 */
public class AutoClicker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();
    
    // Click state
    private boolean clickPending    = false;
    private long    clickDelay      = 0L;
    private boolean blockHitPending = false;
    private long    blockHitDelay   = 0L;
    
    // Exhaust simulation
    private long exhaustCooldown = 0L;
    private boolean isExhausted = false;
    private int clicksSinceExhaust = 0;
    
    // ═══════════════════════════════════════════════════════════════════
    // CORE SETTINGS
    // ═══════════════════════════════════════════════════════════════════
    public final DropdownSetting clickPattern   = register(new DropdownSetting("Click Pattern", 0, "JITTER", "BUTTERFLY"));
    public final SliderSetting   targetCPS      = register(new SliderSetting("Target CPS", 12, 1, 25, 1));
    public final BooleanSetting  randomize      = register(new BooleanSetting("Randomize", true));
    public final SliderSetting   randomVariance = register(new SliderSetting("  Variance %", 15, 5, 50, 1));
    
    // ═══════════════════════════════════════════════════════════════════
    // EXHAUST SIMULATION
    // ═══════════════════════════════════════════════════════════════════
    public final BooleanSetting simulateExhaust = register(new BooleanSetting("Simulate Exhaust", true));
    public final SliderSetting  exhaustInterval = register(new SliderSetting("  Exhaust Every (s)", 8, 3, 20, 1));
    public final SliderSetting  exhaustDuration = register(new SliderSetting("  Exhaust Duration (s)", 2, 1, 5, 0.5));
    public final SliderSetting  exhaustSlowdown = register(new SliderSetting("  Slowdown %", 40, 20, 70, 5));
    
    // ═══════════════════════════════════════════════════════════════════
    // CONDITIONS
    // ═══════════════════════════════════════════════════════════════════
    public final BooleanSetting allowBreakingBlocks = register(new BooleanSetting("Allow Breaking Blocks", false));
    public final BooleanSetting holdingWeapon       = register(new BooleanSetting("Holding Weapon", true));
    public final BooleanSetting notUsingItem        = register(new BooleanSetting("Not Using Item", true));
    
    // ═══════════════════════════════════════════════════════════════════
    // INVENTORY CLICKING
    // ═══════════════════════════════════════════════════════════════════
    public final BooleanSetting inventoryClick    = register(new BooleanSetting("Inventory Click", true));
    public final BooleanSetting inventoryShiftOnly = register(new BooleanSetting("  Shift Only", true));
    public final BooleanSetting inventoryRandomize = register(new BooleanSetting("  Randomize Speed", true));
    public final SliderSetting  inventoryCPS       = register(new SliderSetting("  Max Inventory CPS", 20, 10, 40, 1));
    
    // ═══════════════════════════════════════════════════════════════════
    // LEGACY SETTINGS (kept for compatibility)
    // ═══════════════════════════════════════════════════════════════════
    public final BooleanSetting blockHit         = register(new BooleanSetting("Block Hit", false));
    public final SliderSetting  blockHitTicks    = register(new SliderSetting("  BH Ticks", 1.5, 1.0, 20.0, 0.5));
    public final BooleanSetting allowTools       = register(new BooleanSetting("Allow Tools", false));
    public final SliderSetting  range            = register(new SliderSetting("Range", 3.0, 3.0, 8.0, 0.1));
    public final SliderSetting  hitBoxVertical   = register(new SliderSetting("HB Vertical", 0.1, 0.0, 1.0, 0.05));
    public final SliderSetting  hitBoxHorizontal = register(new SliderSetting("HB Horizontal", 0.2, 0.0, 1.0, 0.05));

    public AutoClicker() {
        super("AutoClicker", false);
    }

    /**
     * Get next click delay based on pattern and settings
     */
    private long getNextClickDelay() {
        double cps = targetCPS.getValue();
        
        // Apply exhaust slowdown
        if (simulateExhaust.getValue() && isExhausted) {
            double slowdown = exhaustSlowdown.getValue() / 100.0;
            cps *= (1.0 - slowdown);
        }
        
        // Apply randomization
        if (randomize.getValue()) {
            double variance = randomVariance.getValue() / 100.0;
            double min = cps * (1.0 - variance);
            double max = cps * (1.0 + variance);
            cps = min + (max - min) * random.nextDouble();
        }
        
        // Butterfly pattern generates double-clicks
        if (clickPattern.getIndex() == 1) { // BUTTERFLY
            // Butterfly clicking produces pairs of clicks
            // Alternate between fast and normal delays
            boolean isSecondClick = (clickDelay % 2 == 0);
            if (isSecondClick) {
                // Second click in pair comes very quickly
                return (long) (1000.0 / (cps * 3.0));
            }
        }
        
        return (long) (1000.0 / cps);
    }

    private long getBlockHitDelay() {
        return (long)(50.0F * (float) blockHitTicks.getValue());
    }

    private boolean isBreakingBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }
    
    private boolean isInInventory() {
        return mc.currentScreen instanceof GuiContainer;
    }

    /**
     * Check if all conditions are met for clicking
     */
    private boolean canClick() {
        // In inventory
        if (isInInventory()) {
            if (!inventoryClick.getValue()) return false;
            if (inventoryShiftOnly.getValue() && !Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) return false;
            return true;
        }
        
        // Not using item condition
        if (notUsingItem.getValue() && mc.thePlayer.isUsingItem()) {
            return false;
        }
        
        // Breaking blocks condition
        if (!allowBreakingBlocks.getValue() && isBreakingBlock() && !hasValidTarget()) {
            return false;
        }
        
        // Holding weapon condition
        if (holdingWeapon.getValue()) {
            if (!ItemUtil.hasRawUnbreakingEnchant() && !ItemUtil.isHoldingSword()) {
                if (!allowTools.getValue() || !ItemUtil.isHoldingTool()) {
                    return false;
                }
            }
        }
        
        return true;
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
    
    /**
     * Update exhaust state
     */
    private void updateExhaust() {
        if (!simulateExhaust.getValue()) {
            isExhausted = false;
            return;
        }
        
        long now = System.currentTimeMillis();
        
        // Check if it's time to trigger exhaust
        if (!isExhausted && exhaustCooldown <= now) {
            int clicksBeforeExhaust = (int)(exhaustInterval.getValue() * targetCPS.getValue());
            if (clicksSinceExhaust >= clicksBeforeExhaust) {
                isExhausted = true;
                clicksSinceExhaust = 0;
                exhaustCooldown = now + (long)(exhaustDuration.getValue() * 1000);
            }
        }
        
        // Check if exhaust period is over
        if (isExhausted && exhaustCooldown <= now) {
            isExhausted = false;
            exhaustCooldown = now + (long)(exhaustInterval.getValue() * 1000);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            if (clickDelay > 0L)    clickDelay    -= 50L;
            if (blockHitDelay > 0L) blockHitDelay -= 50L;
            
            updateExhaust();
            
            if (mc.currentScreen != null && !isInInventory()) {
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
                
                // Handle inventory clicking
                if (isInInventory() && inventoryClick.getValue()) {
                    if (canClick() && mc.gameSettings.keyBindAttack.isKeyDown()) {
                        // Limit inventory CPS if randomize is enabled
                        double maxInvCPS = inventoryCPS.getValue();
                        if (inventoryRandomize.getValue()) {
                            maxInvCPS = Math.min(maxInvCPS, targetCPS.getValue() * 1.5);
                        }
                        
                        long invDelay = (long)(1000.0 / maxInvCPS);
                        while (clickDelay <= 0L) {
                            clickPending = true;
                            clickDelay += invDelay;
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                            KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
                        }
                    }
                } 
                // Handle normal clicking
                else if (isEnabled() && canClick() && mc.gameSettings.keyBindAttack.isKeyDown()) {
                    while (clickDelay <= 0L) {
                        clickPending = true;
                        clickDelay  += getNextClickDelay();
                        clicksSinceExhaust++;
                        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                        KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
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
        exhaustCooldown = System.currentTimeMillis() + (long)(exhaustInterval.getValue() * 1000);
        clicksSinceExhaust = 0;
        isExhausted = false;
    }

    @Override
    public String[] getSuffix() {
        String pattern = clickPattern.getIndex() == 0 ? "J" : "B";
        String exhaust = isExhausted ? "!" : "";
        return new String[]{String.format("%s%d%s", pattern, (int)targetCPS.getValue(), exhaust)};
    }
}
