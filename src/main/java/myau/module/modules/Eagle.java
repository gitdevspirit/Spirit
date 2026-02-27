package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.MoveUtil;
import myau.util.PlayerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class Eagle extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int sneakDelay = 0;

    public final SliderSetting  minDelay       = register(new SliderSetting("Min Delay",       2,  0, 10, 1));
    public final SliderSetting  maxDelay       = register(new SliderSetting("Max Delay",       3,  0, 10, 1));
    public final BooleanSetting directionCheck = register(new BooleanSetting("Direction Check", true));
    public final BooleanSetting pitchCheck     = register(new BooleanSetting("Pitch Check",    true));
    public final BooleanSetting blocksOnly     = register(new BooleanSetting("Blocks Only",    true));
    public final BooleanSetting sneakOnly      = register(new BooleanSetting("Sneak Only",     false));

    // Block counter HUD
    public final BooleanSetting showCount  = register(new BooleanSetting("Show Count",  true));
    public final SliderSetting  countOffX  = register(new SliderSetting("Count X",  0, -200, 200, 1));
    public final SliderSetting  countOffY  = register(new SliderSetting("Count Y", 20,  -50, 200, 1));

    public Eagle() {
        super("Eagle", false);
    }

    private boolean canMoveSafely() {
        double[] offset = MoveUtil.predictMovement();
        return PlayerUtil.canMove(mc.thePlayer.motionX + offset[0], mc.thePlayer.motionZ + offset[1]);
    }

    private boolean shouldSneak() {
        if (directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) return false;
        if (pitchCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) return false;
        if (sneakOnly.getValue() && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) return false;
        return (!blocksOnly.getValue() || ItemUtil.isHoldingBlock()) && mc.thePlayer.onGround;
    }

    /** Total block count across all hotbar slots */
    private int getTotalBlocks() {
        int total = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && ItemUtil.isBlock(stack)) {
                total += stack.stackSize;
            }
        }
        return total;
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (sneakDelay > 0) sneakDelay--;
        if (sneakDelay == 0 && canMoveSafely()) {
            int min = (int) minDelay.getValue();
            int max = (int) maxDelay.getValue();
            sneakDelay = RandomUtils.nextInt(Math.min(min, max), Math.max(min, max) + 1);
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled() || mc.currentScreen != null) return;
        if (sneakOnly.getValue() && Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()) && shouldSneak()) {
            mc.thePlayer.movementInput.sneak = false;
            mc.thePlayer.movementInput.moveForward /= 0.3F;
            mc.thePlayer.movementInput.moveStrafe  /= 0.3F;
        }
        if (!mc.thePlayer.movementInput.sneak) {
            if (shouldSneak() && (sneakDelay > 0 || canMoveSafely())) {
                mc.thePlayer.movementInput.sneak = true;
                mc.thePlayer.movementInput.moveStrafe  *= 0.3F;
                mc.thePlayer.movementInput.moveForward *= 0.3F;
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || !showCount.getValue()) return;
        if (mc.thePlayer == null || mc.currentScreen != null) return;
        if (!ItemUtil.isHoldingBlock()) return;

        int blocks = getTotalBlocks();
        String text = String.valueOf(blocks);

        ScaledResolution sr = new ScaledResolution(mc);
        int cx = sr.getScaledWidth()  / 2;
        int cy = sr.getScaledHeight() / 2;

        float x = cx + (float) countOffX.getValue() - mc.fontRendererObj.getStringWidth(text) / 2f;
        float y = cy + (float) countOffY.getValue();

        // Color: green → yellow → red based on count
        int color;
        if (blocks > 64)      color = 0xFF55FF55; // green
        else if (blocks > 16) color = 0xFFFFAA00; // yellow
        else                  color = 0xFFFF5555; // red

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        mc.fontRendererObj.drawStringWithShadow(text, x, y, color);
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public void onDisabled() { sneakDelay = 0; }

    @Override
    public String[] getSuffix() {
        int min = (int) minDelay.getValue();
        int max = (int) maxDelay.getValue();
        return min == max
                ? new String[]{ String.valueOf(min) }
                : new String[]{ String.format("%d-%d", min, max) };
    }
}
