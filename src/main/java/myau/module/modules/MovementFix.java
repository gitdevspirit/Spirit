package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.MoveInputEvent;
import myau.events.TickEvent;
import myau.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import myau.management.RotationState;

/**
 * Fixes movement direction while a module is silently reporting a fake rotation.
 *
 * MovementFix converts the player's intended movement direction (based on their
 * real yaw) into the correct WASD input for the fake yaw being sent to the server.
 */
public class MovementFix extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /**
     * Set by modules like AimAssist Silent or Clutch when movement correction
     * should apply for the current tick.
     */
    public static boolean forceMovementFix = false;

    public MovementFix() {
        super("MovementFix", true);
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!forceMovementFix || !RotationState.isActived()) return;
        if (mc.thePlayer == null) return;

        MovementFix toggle = (MovementFix) myau.Myau.moduleManager.getModule(MovementFix.class);
        if (toggle != null && !toggle.isEnabled()) return;

        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;

        if (forward == 0.0f && strafe == 0.0f) return;

        float trueYaw = mc.thePlayer.rotationYaw;
        float fakeYaw = RotationState.getSmoothedYaw();

        double intendedAngle = MathHelper.wrapAngleTo180_double(
                Math.toDegrees(direction(trueYaw, forward, strafe))
        );

        float sneakMultiplier = mc.thePlayer.movementInput.sneak ? 0.3f : 1.0f;

        float bestForward = 0f;
        float bestStrafe = 0f;
        double bestDiff = Double.MAX_VALUE;

        for (float f = -1f; f <= 1f; f += 1f) {
            for (float s = -1f; s <= 1f; s += 1f) {

                if (f == 0f && s == 0f)
                    continue;

                float candidateForward = f * sneakMultiplier;
                float candidateStrafe = s * sneakMultiplier;

                double candidateAngle = MathHelper.wrapAngleTo180_double(
                        Math.toDegrees(direction(fakeYaw, candidateForward, candidateStrafe))
                );

                double diff = Math.abs(
                        MathHelper.wrapAngleTo180_double(
                                intendedAngle - candidateAngle
                        )
                );

                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestForward = candidateForward;
                    bestStrafe = candidateStrafe;
                }
            }
        }

        mc.thePlayer.movementInput.moveForward = bestForward;
        mc.thePlayer.movementInput.moveStrafe = bestStrafe;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE)
            return;

        forceMovementFix = false;
    }

    /**
     * Same formula vanilla uses to convert forward/strafe input into an angle.
     */
    private static double direction(float rotationYaw, double moveForward, double moveStrafing) {

        if (moveForward < 0.0)
            rotationYaw += 180.0f;

        float forwardSign = 1.0f;

        if (moveForward < 0.0)
            forwardSign = -0.5f;
        else if (moveForward > 0.0)
            forwardSign = 0.5f;

        if (moveStrafing > 0.0)
            rotationYaw -= 90.0f * forwardSign;

        if (moveStrafing < 0.0)
            rotationYaw += 90.0f * forwardSign;

        return Math.toRadians(rotationYaw);
    }
}
