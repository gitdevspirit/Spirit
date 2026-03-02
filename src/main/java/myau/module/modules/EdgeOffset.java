package myau.module.modules;

/*
 * EdgeOffset — Legit scaffold sneak
 *
 * Uses a movement simulation (same approach as the reference Raven script):
 * predicts where the player will be BEFORE they actually move, then checks
 * all four bottom corners of the hitbox against the block below. Sneaks only
 * if the simulated position would overhang the edge by more than the configured
 * threshold. This is more accurate than Eagle/SafeWalk which check after movement.
 */

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.KeyBindUtil;
import myau.util.MoveUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

import java.util.Random;

public class EdgeOffset extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random rng   = new Random();

    // Half-width of player hitbox
    private static final double HW = 0.3;
    // The four bottom corners relative to player center
    private static final double[][] CORNERS = {
        {-HW, -HW}, {HW, -HW}, {-HW, HW}, {HW, HW}
    };

    public final SliderSetting  edgeOffset     = register(new SliderSetting("Edge Offset",    0.05, 0.0, 0.5,  0.01));
    public final SliderSetting  unsneakDelay   = register(new SliderSetting("Unsneak Delay",  50,   50,  300,  5));
    public final SliderSetting  sneakOnJump    = register(new SliderSetting("Sneak On Jump",  0,    0,   500,  5));
    public final BooleanSetting directionCheck = register(new BooleanSetting("Direction Check", true));
    public final BooleanSetting pitchCheck     = register(new BooleanSetting("Pitch Check",     true));
    public final BooleanSetting blocksOnly     = register(new BooleanSetting("Blocks Only",     true));

    // State
    private boolean sneakingFromModule = false;
    private int unsneakStartTick   = -1;
    private int unsneakDelayTicks  = -1;
    private int sneakJumpStartTick = -1;
    private int sneakJumpDelayTicks = -1;

    public EdgeOffset() {
        super("EdgeOffset", false);
    }

    @Override
    public void onDisabled() {
        sneakingFromModule = false;
        resetTimers();
    }

    // ── Conditions ────────────────────────────────────────────────────────────

    private boolean passesCriteria() {
        if (mc.currentScreen != null) return false;
        if (!mc.thePlayer.onGround) return false;
        if (directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) return false;
        if (pitchCheck.getValue() && mc.thePlayer.rotationPitch < 70.0f) return false;
        if (blocksOnly.getValue() && !ItemUtil.isHoldingBlock()) return false;
        return true;
    }

    // ── Main event ───────────────────────────────────────────────────────────

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled()) return;

        if (!passesCriteria()) {
            if (sneakingFromModule) releaseSneak();
            return;
        }

        boolean manualSneak = KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode());

        // If player is manually sneaking, don't interfere
        if (manualSneak) {
            sneakingFromModule = false;
            resetTimers();
            return;
        }

        // Handle sneak-on-jump
        if (mc.thePlayer.movementInput.jump && mc.thePlayer.onGround
                && (mc.thePlayer.movementInput.moveForward != 0 || mc.thePlayer.movementInput.moveStrafe != 0)
                && sneakOnJump.getValue() > 0) {
            sneakJumpStartTick = mc.thePlayer.ticksExisted;
            double raw = sneakOnJump.getValue() / 50.0;
            int base = (int) raw;
            sneakJumpDelayTicks = base + (rng.nextDouble() < (raw - base) ? 1 : 0);
            pressSneak(true);
            return;
        }

        // Simulate next position
        double[] delta = predictDelta();
        double simX = mc.thePlayer.posX + delta[0];
        double simZ = mc.thePlayer.posZ + delta[1];

        double edgeOff = computeEdgeOffset(simX, simZ, mc.thePlayer.posX, mc.thePlayer.posZ);

        if (Double.isNaN(edgeOff)) {
            // Over solid ground with no edge — release if we were sneaking
            if (sneakingFromModule) tryReleaseSneak();
            return;
        }

        boolean shouldSneak = edgeOff > edgeOffset.getValue();

        if (shouldSneak) {
            pressSneak(true);
        } else if (sneakingFromModule) {
            tryReleaseSneak();
        }
    }

    // ── Sneak control ────────────────────────────────────────────────────────

    private void pressSneak(boolean resetDelay) {
        mc.thePlayer.movementInput.sneak = true;
        // Scale movement like vanilla sneak does
        if (!sneakingFromModule) {
            mc.thePlayer.movementInput.moveForward *= 0.3f;
            mc.thePlayer.movementInput.moveStrafe  *= 0.3f;
        }
        sneakingFromModule = true;
        if (resetDelay) unsneakStartTick = -1;
    }

    private void tryReleaseSneak() {
        int now = mc.thePlayer.ticksExisted;

        // Still in sneak-on-jump window?
        if (sneakJumpStartTick != -1 && (now - sneakJumpStartTick) < sneakJumpDelayTicks) {
            pressSneak(false);
            return;
        }

        // Start unsneak delay timer
        if (unsneakStartTick == -1) {
            unsneakStartTick = now;
            double raw = (unsneakDelay.getValue() - 50.0) / 50.0;
            int base = (int) raw;
            unsneakDelayTicks = base + (rng.nextDouble() < (raw - base) ? 1 : 0);
        }

        // Still waiting?
        if ((now - unsneakStartTick) < unsneakDelayTicks) {
            pressSneak(false);
            return;
        }

        releaseSneak();
    }

    private void releaseSneak() {
        if (sneakingFromModule) {
            mc.thePlayer.movementInput.sneak = false;
            // Undo the 0.3 scale we applied
            if (mc.thePlayer.movementInput.moveForward != 0)
                mc.thePlayer.movementInput.moveForward /= 0.3f;
            if (mc.thePlayer.movementInput.moveStrafe != 0)
                mc.thePlayer.movementInput.moveStrafe /= 0.3f;
        }
        sneakingFromModule = false;
        resetTimers();
    }

    private void resetTimers() {
        unsneakStartTick = unsneakDelayTicks = -1;
        sneakJumpStartTick = sneakJumpDelayTicks = -1;
    }

    // ── Physics helpers ──────────────────────────────────────────────────────

    /**
     * Predicts the horizontal delta the player will move this tick,
     * accounting for current motion and input — mirrors Minecraft's
     * moveEntityWithHeading logic at a high level.
     */
    private double[] predictDelta() {
        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe  = mc.thePlayer.movementInput.moveStrafe;

        // If currently sneaking (but we're about to potentially change that),
        // undo the sneak slow so we predict actual movement
        if (sneakingFromModule) {
            if (forward != 0) forward /= 0.3f;
            if (strafe  != 0) strafe  /= 0.3f;
        }

        float speed = 0.98f * (sneakingFromModule ? 0.3f : 1.0f);
        float mag = forward * forward + strafe * strafe;
        if (mag < 1e-4f) return new double[]{0, 0};

        mag = MathHelper.sqrt_float(mag);
        if (mag < 1f) mag = 1f;
        mag = (float) MoveUtil.getAllowedHorizontalDistance() / mag;

        forward *= mag;
        strafe  *= mag;

        float yawRad = mc.thePlayer.rotationYaw * (float) Math.PI / 180f;
        float sin = MathHelper.sin(yawRad);
        float cos = MathHelper.cos(yawRad);

        double dx = strafe * cos - forward * sin;
        double dz = forward * cos + strafe * sin;

        // Add existing momentum (friction-damped)
        dx += mc.thePlayer.motionX * 0.91;
        dz += mc.thePlayer.motionZ * 0.91;

        return new double[]{dx, dz};
    }

    /**
     * Checks all four bottom corners of the player's hitbox at the simulated
     * position and returns the minimum overhang distance over an edge, or NaN
     * if all corners are safely over solid ground.
     *
     * @param simX  predicted X position
     * @param simZ  predicted Z position
     * @param curX  current X position (used for reference block lookup)
     * @param curZ  current Z position
     */
    private double computeEdgeOffset(double simX, double simZ, double curX, double curZ) {
        int floorY = (int) Math.floor(mc.thePlayer.posY - 0.01);
        double best = Double.NaN;

        for (double[] c : CORNERS) {
            int bx = (int) Math.floor(curX + c[0]);
            int bz = (int) Math.floor(curZ + c[1]);

            // Only care about corners that were over a solid block
            Block block = mc.theWorld.getBlockState(new BlockPos(bx, floorY, bz)).getBlock();
            if (block instanceof BlockAir) continue;

            // How far is the simulated position from the edge of this block?
            double offX = Math.abs(simX - (bx + (simX < bx + 0.5 ? 0 : 1)));
            double offZ = Math.abs(simZ - (bz + (simZ < bz + 0.5 ? 0 : 1)));
            boolean xDiff = (int) Math.floor(simX) != bx;
            boolean zDiff = (int) Math.floor(simZ) != bz;

            double cornerDist;
            if (xDiff) {
                cornerDist = zDiff ? Math.max(offX, offZ) : offX;
            } else {
                cornerDist = zDiff ? offZ : 0.0;
            }

            best = Double.isNaN(best) ? cornerDist : Math.min(best, cornerDist);
        }

        return best;
    }
}
