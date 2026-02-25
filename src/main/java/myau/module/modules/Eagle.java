package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.MoveUtil;
import myau.util.PlayerUtil;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Keyboard;

import java.util.Objects;

public class Eagle extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int sneakDelay = 0;

    public final SliderSetting  minDelay       = new SliderSetting("Min Delay",       2,  0, 10, 1);
    public final SliderSetting  maxDelay       = new SliderSetting("Max Delay",       3,  0, 10, 1);
    public final BooleanSetting directionCheck = new BooleanSetting("Direction Check", true);
    public final BooleanSetting pitchCheck     = new BooleanSetting("Pitch Check",    true);
    public final BooleanSetting blocksOnly     = new BooleanSetting("Blocks Only",    true);
    public final BooleanSetting sneakOnly      = new BooleanSetting("Sneak Only",     false);

    public Eagle() {
        super("Eagle", false);
        register(minDelay);
        register(maxDelay);
        register(directionCheck);
        register(pitchCheck);
        register(blocksOnly);
        register(sneakOnly);
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