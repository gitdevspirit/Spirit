package myau.management;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.MoveInputEvent;
import myau.events.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

/**
 * Fixes movement direction while a module is silently reporting a fake rotation
 * (via {@link RotationState#applyState}) to the server.
 *
 * Why this exists: MixinEntityLivingBase#moveEntityWithHeading already swaps
 * rotationYaw to the fake yaw for the moveFlying() physics call, and MixinEntityLivingBase#jump
 * does the same for jump direction — so the player's ACTUAL position ends up
 * computed relative to the fake yaw. But movementInput.moveForward/moveStrafe
 * (raw WASD state) are still whatever the real keys say, unadjusted. The net
 * result: pressing W while silently aiming 90° away from where you're looking
 * sends you sideways instead of forward, because "forward" got rotated by the
 * fake yaw instead of your true one.
 *
 * This class asks: "what forward/strafe combo, when rotated by the FAKE yaw,
 * best reproduces the movement direction the player intended (raw input
 * rotated by their TRUE yaw)?" — then overwrites movementInput with that combo
 * before moveEntityWithHeading consumes it. This is what actually needs to be
 * true for "silent" rotation modes (AimAssist Silent, Clutch) to not desync
 * your own movement while active.
 *
 * Usage: a module sets {@link #forceMovementFix} = true during the same tick
 * it calls RotationState.applyState(true, ...). The flag auto-resets at the
 * start of every tick, so it must be re-set every tick it should apply.
 */
public class MovementFix {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Set by a module (e.g. AimAssist Silent, Clutch) for the current tick only. */
    public static boolean forceMovementFix = false;

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!forceMovementFix || !RotationState.isActived()) return;
        if (mc.thePlayer == null) return;

        myau.module.Module toggle = myau.Myau.moduleManager.getModule("MovementFix");
        if (toggle != null && !toggle.isEnabled()) return;

        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe  = mc.thePlayer.movementInput.moveStrafe;
        if (forward == 0.0f && strafe == 0.0f) return;

        float trueYaw = mc.thePlayer.rotationYaw;
        float fakeYaw = RotationState.getSmoothedYaw();

        // Angle the player actually intends to move in, world-relative, based on their true yaw.
        double intendedAngle = MathHelper.wrapAngleTo180_double(Math.toDegrees(direction(trueYaw, forward, strafe)));

        float sneakMultiplier = mc.thePlayer.movementInput.sneak ? 0.3f : 1.0f;

        float bestForward = 0f;
        float bestStrafe  = 0f;
        double bestDiff = Double.MAX_VALUE;

        // Search every {-1,0,1} combo (minus the no-op) for the one that, once
        // rotated by the FAKE yaw, best matches the TRUE intended direction.
        for (float f = -1f; f <= 1f; f += 1f) {
            for (float s = -1f; s <= 1f; s += 1f) {
                if (f == 0f && s == 0f) continue;

                float candidateForward = f * sneakMultiplier;
                float candidateStrafe  = s * sneakMultiplier;

                double candidateAngle = MathHelper.wrapAngleTo180_double(
                        Math.toDegrees(direction(fakeYaw, candidateForward, candidateStrafe)));
                double diff = Math.abs(MathHelper.wrapAngleTo180_double(intendedAngle - candidateAngle));

                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestForward = candidateForward;
                    bestStrafe  = candidateStrafe;
                }
            }
        }

        mc.thePlayer.movementInput.moveForward = bestForward;
        mc.thePlayer.movementInput.moveStrafe  = bestStrafe;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        forceMovementFix = false;
    }

    /** Same formula vanilla EntityPlayerSP uses to turn forward/strafe input into a world-relative angle. */
    private static double direction(float rotationYaw, double moveForward, double moveStrafing) {
        if (moveForward < 0.0) rotationYaw += 180.0f;

        float forwardSign = 1.0f;
        if (moveForward < 0.0) forwardSign = -0.5f;
        else if (moveForward > 0.0) forwardSign = 0.5f;

        if (moveStrafing > 0.0) rotationYaw -= 90.0f * forwardSign;
        if (moveStrafing < 0.0) rotationYaw += 90.0f * forwardSign;

        return Math.toRadians(rotationYaw);
    }
}
