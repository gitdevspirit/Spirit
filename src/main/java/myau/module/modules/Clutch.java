package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.management.RotationState;
import myau.module.BooleanSetting;
import myau.module.KeybindSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.RotationUtil;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clutch — silently aims at and places a block beneath/around you when you're
 * about to fall (off the map, into void, etc.), without moving your camera.
 * Ported from Raven's Clutch, rebuilt on top of Myau's own silent-rotation
 * pipeline (UpdateEvent + RotationState + MovementFix) instead of Raven's
 * ClientRotationEvent/RotationHelper.
 */
public class Clutch extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Map<String, Integer> BLOCK_SCORE = new HashMap<>();
    private static final double HALF_WIDTH = 0.3;
    private static final double[][] CORNERS = {
            {-HALF_WIDTH, -HALF_WIDTH}, {HALF_WIDTH, -HALF_WIDTH},
            {-HALF_WIDTH, HALF_WIDTH}, {HALF_WIDTH, HALF_WIDTH}
    };

    static {
        BLOCK_SCORE.put("obsidian", 0);
        BLOCK_SCORE.put("end_stone", 1);
        BLOCK_SCORE.put("planks", 2);
        BLOCK_SCORE.put("log", 2);
        BLOCK_SCORE.put("log2", 2);
        BLOCK_SCORE.put("glass", 3);
        BLOCK_SCORE.put("stained_glass", 3);
        BLOCK_SCORE.put("hardened_clay", 4);
        BLOCK_SCORE.put("stained_hardened_clay", 4);
        BLOCK_SCORE.put("stone", 5);
        BLOCK_SCORE.put("wool", 5);
    }

    public final SliderSetting  reach                  = register(new SliderSetting("Reach", 4.5, 0.5, 4.5, 0.1));
    public final SliderSetting  speed                  = register(new SliderSetting("Speed", 8, 0, 100, 1));
    public final SliderSetting  snapbackSpeed          = register(new SliderSetting("Snapback Speed", 12, 0, 100, 1));
    public final SliderSetting  maxDistance            = register(new SliderSetting("Max Distance", 10, 0, 20, 1));
    public final SliderSetting  rotationTolerance      = register(new SliderSetting("Rotation Tolerance", 25, 20, 100, 1));
    public final BooleanSetting simulateFuturePosition = register(new BooleanSetting("Simulate Future Position", true));
    public final BooleanSetting autoClutch             = register(new BooleanSetting("Auto Clutch", false));
    public final SliderSetting  minimumFallDistance    = register(new SliderSetting("Minimum Fall Distance", 10, 3, 20, 1));
    public final KeybindSetting selectKeybind          = register(new KeybindSetting("Select Keybind", 0));

    private BlockPos placeAtBlock;
    private EnumFacing hitSide;
    private Vec3 hitVec;
    private boolean placeQueued;
    private boolean placing;
    private boolean slotWasSwapped;
    private boolean autoClickerWasOn;
    private int prevSlot = -1;
    private int plannedSlot = -1;
    private float aimYaw;
    private float aimPitch;
    private BlockPos targetHitPos;
    private EnumFacing targetSide;
    private boolean hasAim;
    private boolean resetting;
    private BlockPos lastPlaced;
    private int clutchBlocksPlaced;
    private boolean autoClutchActive;
    private boolean autoClutchChecking;
    private int autoClutchCheckCounter;
    private boolean autoClutchLandedGuard;
    private int autoClutchLandedTick;
    private int prevHurtTime = -1;

    public Clutch() { super("Clutch", false); }

    @Override
    public void onEnabled() {
        hasAim = false;
        resetting = false;
        clutchBlocksPlaced = 0;
        autoClutchActive = false;
        autoClutchChecking = false;
        autoClutchCheckCounter = 0;
        autoClutchLandedGuard = false;
        prevHurtTime = -1;
    }

    @Override
    public void onDisabled() {
        clearAim(false);
        disablePlacing(true);
        placeQueued = false;
        autoClutchActive = false;
        autoClutchChecking = false;
        autoClutchLandedGuard = false;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        if (event.getType() == EventType.PRE) {
            runPrePlayerInteract();

            float baseYaw   = event.getYaw();
            float basePitch = event.getPitch();

            if (resetting) {
                aimYaw   = mc.thePlayer.rotationYaw;
                aimPitch = mc.thePlayer.rotationPitch;
                float[] smoothed = getRotationsSmoothed(baseYaw, basePitch, aimYaw, aimPitch, true);
                if (Math.abs(MathHelper.wrapAngleTo180_float(smoothed[0] - aimYaw)) < 0.5f
                        && Math.abs(smoothed[1] - aimPitch) < 0.5f) {
                    resetting = false;
                    restoreInputsAndAutoClicker();
                    return;
                }
                RotationState.applyState(true, smoothed[0], smoothed[1], smoothed[0], 15);
                myau.management.MovementFix.forceMovementFix = true;
                event.setRotation(smoothed[0], smoothed[1], 15);
                return;
            }

            if (!hasAim) return;

            float[] smoothed = getRotationsSmoothed(baseYaw, basePitch, aimYaw, aimPitch, false);

            if (placing && targetHitPos != null) {
                MovingObjectPosition mop = RotationUtil.rayTrace(smoothed[0], smoothed[1], reach.getValue(), 1.0f);
                if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                        && targetHitPos.equals(mop.getBlockPos()) && targetSide == mop.sideHit) {
                    int maxBlocks = (int) maxDistance.getValue();
                    if (maxBlocks == 0 || clutchBlocksPlaced < maxBlocks) {
                        double tolerance = rotationTolerance.getValue();
                        if (Math.abs(MathHelper.wrapAngleTo180_float(smoothed[0] - baseYaw)) <= tolerance
                                && Math.abs(smoothed[1] - basePitch) <= tolerance) {
                            placeAtBlock = mop.getBlockPos();
                            hitSide = mop.sideHit;
                            hitVec = mop.hitVec;
                            placeQueued = true;
                        }
                    }
                }
            }

            RotationState.applyState(true, smoothed[0], smoothed[1], smoothed[0], 15);
            myau.management.MovementFix.forceMovementFix = true;
            event.setRotation(smoothed[0], smoothed[1], 15);

        } else if (event.getType() == EventType.POST) {
            if (!placeQueued) return;
            placeQueued = false;

            if (placeAtBlock != null && hitSide != null && hitVec != null
                    && mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                            mc.thePlayer.inventory.getCurrentItem(), placeAtBlock, hitSide, hitVec)) {
                if (hitSide != EnumFacing.DOWN) clutchBlocksPlaced++;
                lastPlaced = placeAtBlock;
                mc.thePlayer.swingItem();
            }
        }
    }

    // ── Selection / block-finding ────────────────────────────────────────────

    private void runPrePlayerInteract() {
        if (mc.thePlayer.onGround) clutchBlocksPlaced = 0;
        int ticksExisted = mc.thePlayer.ticksExisted;

        updateAutoClutch(ticksExisted);

        boolean active = isSelectKeyDown() || autoClutchActive;
        if (mc.currentScreen != null || !active) {
            clearAim(true);
            disablePlacing(false);
            return;
        }

        BlockPos below = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ));
        if (!canPlaceThrough(below)) {
            disablePlacing(false);
            return;
        }

        int weakSlot = pickBlockSlot();
        if (weakSlot == -1) {
            disablePlacing(false);
            return;
        }

        plannedSlot = weakSlot;
        AimResult target = clutchAim();
        if (target != null) {
            targetHitPos = target.ray.getBlockPos();
            targetSide   = target.ray.sideHit;
            placeAtBlock = targetHitPos;
            hitSide      = targetSide;
            hitVec       = target.ray.hitVec;
            aimYaw       = target.yaw;
            aimPitch     = target.pitch;
            hasAim       = true;
            resetting    = false;
        }

        if (hasAim && !placing) enablePlacing();

        if (placing || resetting || hasAim) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            equipPlannedSlot();
        }
    }

    private boolean isSelectKeyDown() {
        int key = selectKeybind.getKeyCode();
        return key != 0 && Keyboard.isKeyDown(key);
    }

    private void updateAutoClutch(int ticksExisted) {
        if (autoClutch.getValue()) {
            int curHurtTime = mc.thePlayer.hurtTime;
            if (curHurtTime > prevHurtTime) {
                autoClutchChecking = true;
                autoClutchCheckCounter = 0;
                autoClutchLandedGuard = false;
            }
            prevHurtTime = curHurtTime;

            if (autoClutchChecking && !autoClutchActive && !autoClutchLandedGuard) {
                if (autoClutchCheckCounter == 0 || autoClutchCheckCounter % 3 == 0) {
                    if (willFallFar(minimumFallDistance.getValue())) {
                        autoClutchActive = true;
                    }
                }
                autoClutchCheckCounter++;
            }

            if (autoClutchLandedGuard) {
                boolean expired    = ticksExisted - autoClutchLandedTick >= 10;
                boolean jumped     = mc.gameSettings.keyBindJump.isKeyDown();
                boolean airborneUp = !mc.thePlayer.onGround && mc.thePlayer.motionY > 0;
                if (expired || jumped || airborneUp) {
                    autoClutchActive = false;
                    autoClutchChecking = false;
                    autoClutchLandedGuard = false;
                }
            }

            if (autoClutchActive && mc.thePlayer.onGround && mc.thePlayer.hurtTime < mc.thePlayer.maxHurtTime - 2) {
                if (!autoClutchLandedGuard) {
                    autoClutchLandedGuard = true;
                    autoClutchLandedTick = ticksExisted;
                    if (!willFallSoon()) {
                        autoClutchActive = false;
                        autoClutchChecking = false;
                        autoClutchLandedGuard = false;
                    }
                }
            }

            if (!autoClutchActive && !autoClutchLandedGuard && mc.thePlayer.onGround && mc.thePlayer.hurtTime == 0) {
                autoClutchChecking = false;
                autoClutchCheckCounter = 0;
            }
        } else {
            autoClutchActive = false;
            autoClutchChecking = false;
            autoClutchLandedGuard = false;
            prevHurtTime = mc.thePlayer.hurtTime;
        }
    }

    private void enablePlacing() {
        if (placing) return;
        placing = true;
        if (!slotWasSwapped) prevSlot = mc.thePlayer.inventory.currentItem;
        AutoClicker autoClicker = (AutoClicker) Myau.moduleManager.modules.get(AutoClicker.class);
        if (autoClicker != null) {
            autoClickerWasOn = autoClickerWasOn || autoClicker.isEnabled();
            if (autoClickerWasOn) autoClicker.setEnabled(false);
        }
    }

    private void disablePlacing(boolean forceRestore) {
        if (!placing && !forceRestore) return;

        placing = false;
        plannedSlot = -1;

        if ((forceRestore || !hasAim) && slotWasSwapped && prevSlot != -1 && prevSlot != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.inventory.currentItem = prevSlot;
            slotWasSwapped = false;
        }
        if (forceRestore) {
            prevSlot = -1;
            restoreInputsAndAutoClicker();
        }
    }

    private void clearAim(boolean allowSnapback) {
        if (slotWasSwapped && prevSlot != -1 && prevSlot != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.inventory.currentItem = prevSlot;
            slotWasSwapped = false;
        }
        targetHitPos = null;
        targetSide = null;
        lastPlaced = null;
        clutchBlocksPlaced = 0;
        if (allowSnapback && hasAim) resetting = true;
        hasAim = false;
        prevSlot = -1;
    }

    private void restoreInputsAndAutoClicker() {
        if (mc.currentScreen == null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), Mouse.isButtonDown(0));
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), Mouse.isButtonDown(1));
        }
        AutoClicker autoClicker = (AutoClicker) Myau.moduleManager.modules.get(AutoClicker.class);
        if (autoClickerWasOn && autoClicker != null) {
            autoClicker.setEnabled(true);
            autoClickerWasOn = false;
        }
    }

    // ── Fall prediction (simplified: Y-axis only, ignores XZ drift) ─────────

    private boolean willFallFar(double minFall) {
        double startY  = mc.thePlayer.posY;
        double y       = startY;
        double motionY = mc.thePlayer.motionY;
        int x = MathHelper.floor_double(mc.thePlayer.posX);
        int z = MathHelper.floor_double(mc.thePlayer.posZ);
        for (int t = 0; t < 60; t++) {
            motionY -= 0.08;
            y += motionY;
            motionY *= 0.98;
            if (isSolidGroundAt(x, y, z)) return false;
            if (startY - y > minFall) return true;
        }
        return false;
    }

    private boolean willFallSoon() {
        double y       = mc.thePlayer.posY;
        double motionY = mc.thePlayer.motionY;
        int x = MathHelper.floor_double(mc.thePlayer.posX);
        int z = MathHelper.floor_double(mc.thePlayer.posZ);
        for (int t = 0; t < 10; t++) {
            motionY -= 0.08;
            y += motionY;
            motionY *= 0.98;
            if (!isSolidGroundAt(x, y, z) && motionY < 0) return true;
        }
        return false;
    }

    private Vec3 simulateFallPosition(Vec3 start) {
        double x = start.xCoord, y = start.yCoord, z = start.zCoord;
        double motionY = mc.thePlayer.motionY;
        int bx = MathHelper.floor_double(x);
        int bz = MathHelper.floor_double(z);
        for (int t = 0; t < 20; t++) {
            motionY -= 0.08;
            y += motionY;
            motionY *= 0.98;
            if (isSolidGroundAt(bx, y, bz) || y < start.yCoord - 2) break;
        }
        return new Vec3(x, y, z);
    }

    private boolean isSolidGroundAt(int x, double y, int z) {
        BlockPos pos = new BlockPos(x, MathHelper.floor_double(y) - 1, z);
        return !canPlaceThrough(pos);
    }

    // ── Aiming ────────────────────────────────────────────────────────────────

    private AimResult clutchAim() {
        Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);

        Vec3 futurePos = simulateFuturePosition.getValue() ? simulateFallPosition(playerPos) : playerPos;

        int feetX = MathHelper.floor_double(playerPos.xCoord);
        int feetZ = MathHelper.floor_double(playerPos.zCoord);
        int feetY = MathHelper.floor_double(playerPos.yCoord);
        int minX = feetX - 5, maxX = feetX + 4;
        int minZ = feetZ - 5, maxZ = feetZ + 4;
        int maxY = feetY - 1, minY = feetY - 4;

        List<BlockCandidate> candidates = new ArrayList<>();
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (canPlaceThrough(pos)) continue;

                    double currentDist = distanceToAABB(playerPos, pos);
                    double futureDist  = distanceToAABB(futurePos, pos);
                    double score = simulateFuturePosition.getValue() ? (currentDist * 0.3 + futureDist * 0.7) : currentDist;
                    if (pos.equals(lastPlaced)) score *= 0.95;
                    candidates.add(new BlockCandidate(score, pos));
                }
            }
        }

        candidates.sort((a, b) -> Double.compare(a.score, b.score));

        for (BlockCandidate candidate : candidates) {
            boolean underPlayer = isBlockUnderPlayer(candidate.pos, playerPos);
            AimResult result = getBestRotationsToBlock(candidate.pos, eye, underPlayer);
            if (result != null) return result;
        }
        return null;
    }

    private boolean isBlockUnderPlayer(BlockPos blockPos, Vec3 pos) {
        if (blockPos.getY() >= MathHelper.floor_double(pos.yCoord)) return false;
        for (double[] corner : CORNERS) {
            int cx = MathHelper.floor_double(pos.xCoord + corner[0]);
            int cz = MathHelper.floor_double(pos.zCoord + corner[1]);
            if (blockPos.getX() == cx && blockPos.getZ() == cz) return true;
        }
        return false;
    }

    private AimResult getBestRotationsToBlock(BlockPos targetCell, Vec3 eye, boolean underPlayer) {
        double inset = 0.05;
        double[] points = {0.2, 0.5, 0.8};
        float baseYaw   = mc.thePlayer.rotationYaw;
        float basePitch = mc.thePlayer.rotationPitch;

        boolean faceSouth = Math.abs(eye.zCoord - (targetCell.getZ() + 1)) < Math.abs(eye.zCoord - targetCell.getZ());
        boolean faceEast  = Math.abs(eye.xCoord - (targetCell.getX() + 1)) < Math.abs(eye.xCoord - targetCell.getX());

        List<RotationCandidate> candidates = new ArrayList<>();
        for (double v : points) {
            for (double u : points) {
                if (underPlayer) {
                    addCandidate(candidates, eye, targetCell.getX() + u, targetCell.getY() + 1 - inset, targetCell.getZ() + v, baseYaw, basePitch);
                }
                double zFace = faceSouth ? targetCell.getZ() + 1 - inset : targetCell.getZ() + inset;
                addCandidate(candidates, eye, targetCell.getX() + u, targetCell.getY() + v, zFace, baseYaw, basePitch);

                double xFace = faceEast ? targetCell.getX() + 1 - inset : targetCell.getX() + inset;
                addCandidate(candidates, eye, xFace, targetCell.getY() + v, targetCell.getZ() + u, baseYaw, basePitch);
            }
        }

        candidates.sort((a, b) -> Double.compare(a.cost, b.cost));

        for (RotationCandidate candidate : candidates) {
            MovingObjectPosition ray = RotationUtil.rayTrace(candidate.yaw, candidate.pitch, reach.getValue(), 1.0f);
            if (ray == null || ray.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) continue;

            EnumFacing face = ray.sideHit;
            if (face == EnumFacing.DOWN) continue;
            if (face == EnumFacing.UP && !underPlayer) continue;
            if (!targetCell.equals(ray.getBlockPos())) continue;

            return new AimResult(ray, candidate.yaw, candidate.pitch);
        }
        return null;
    }

    private void addCandidate(List<RotationCandidate> list, Vec3 eye, double tx, double ty, double tz, float baseYaw, float basePitch) {
        float[] r = RotationUtil.getRotations(tx - eye.xCoord, ty - eye.yCoord, tz - eye.zCoord, baseYaw, basePitch, 180.0f, 0.0f);
        float yawDiff   = Math.abs(MathHelper.wrapAngleTo180_float(r[0] - baseYaw));
        float pitchDiff = Math.abs(r[1] - basePitch);
        list.add(new RotationCandidate(yawDiff + pitchDiff, r[0], r[1]));
    }

    private double distanceToAABB(Vec3 point, BlockPos pos) {
        AxisAlignedBB box = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        Vec3 clamped = RotationUtil.clampVecToBox(point, box);
        double dx = clamped.xCoord - point.xCoord;
        double dy = clamped.yCoord - point.yCoord;
        double dz = clamped.zCoord - point.zCoord;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ── Slot selection ────────────────────────────────────────────────────────

    private int pickBlockSlot() {
        int current = mc.thePlayer.inventory.currentItem;
        int best = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int slot = 8; slot >= 0; --slot) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
            if (stack == null || stack.stackSize == 0 || !(stack.getItem() instanceof ItemBlock)) continue;

            Block block = ((ItemBlock) stack.getItem()).getBlock();
            net.minecraft.util.ResourceLocation id = Block.blockRegistry.getNameForObject(block);
            if (id == null) continue;

            Integer score = BLOCK_SCORE.get(id.getResourcePath());
            if (score == null) continue;

            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }

        if (best != -1) return best;
        return isBlockSlot(current) ? current : -1;
    }

    private boolean isBlockSlot(int slot) {
        if (slot < 0 || slot > 8) return false;
        ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
        return stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock;
    }

    private void equipPlannedSlot() {
        int current = mc.thePlayer.inventory.currentItem;
        if (plannedSlot != -1 && plannedSlot != current) {
            mc.thePlayer.inventory.currentItem = plannedSlot;
            slotWasSwapped = true;
        }
    }

    private boolean canPlaceThrough(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        Material material = block.getMaterial();
        return material == Material.air || material == Material.water || material == Material.lava || block == Blocks.fire;
    }

    // ── Rotation smoothing ────────────────────────────────────────────────────

    private float[] getRotationsSmoothed(float currentYaw, float currentPitch, float targetYaw, float targetPitch, boolean snapback) {
        float curYaw   = currentYaw;
        float curPitch = currentPitch;
        float deltaYaw   = MathHelper.wrapAngleTo180_float(targetYaw - curYaw);
        float deltaPitch = targetPitch - curPitch;

        if (Math.abs(deltaYaw) < 0.1f) curYaw = targetYaw;
        if (Math.abs(deltaPitch) < 0.1f) curPitch = targetPitch;
        if (curYaw == targetYaw && curPitch == targetPitch) {
            return new float[]{ curYaw, MathHelper.clamp_float(curPitch, -90.0f, 90.0f) };
        }

        float maxStep = (float) (snapback ? snapbackSpeed.getValue() : speed.getValue());
        float factor = 1f - (float) (Math.random() * 0.2);
        maxStep *= factor;

        float totalDelta = Math.abs(deltaYaw) + Math.abs(deltaPitch);
        if (totalDelta <= maxStep) {
            curYaw = targetYaw;
            curPitch = targetPitch;
        } else if (maxStep > 0) {
            float scale = maxStep / totalDelta;
            curYaw   += deltaYaw * scale;
            curPitch += deltaPitch * scale;
        }

        return new float[]{ curYaw, MathHelper.clamp_float(curPitch, -90.0f, 90.0f) };
    }

    private static class BlockCandidate {
        final double score;
        final BlockPos pos;
        BlockCandidate(double score, BlockPos pos) { this.score = score; this.pos = pos; }
    }

    private static class RotationCandidate {
        final double cost;
        final float yaw;
        final float pitch;
        RotationCandidate(double cost, float yaw, float pitch) { this.cost = cost; this.yaw = yaw; this.pitch = pitch; }
    }

    private static class AimResult {
        final MovingObjectPosition ray;
        final float yaw;
        final float pitch;
        AimResult(MovingObjectPosition ray, float yaw, float pitch) { this.ray = ray; this.yaw = yaw; this.pitch = pitch; }
    }
}
