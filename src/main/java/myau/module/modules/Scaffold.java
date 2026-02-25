package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.management.RotationState;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;

public class Scaffold extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double[] placeOffsets = new double[]{
            0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375,
            0.40625, 0.46875, 0.53125, 0.59375, 0.65625, 0.71875,
            0.78125, 0.84375, 0.90625, 0.96875
    };

    private int    rotationTick  = 0;
    private int    lastSlot      = -1;
    private int    blockCount    = -1;
    private float  yaw           = -180.0F;
    private float  pitch         = 0.0F;
    private boolean canRotate    = false;
    private int    towerTick     = 0;
    private int    towerDelay    = 0;
    private int    stage         = 0;
    private int    startY        = 256;
    private boolean shouldKeepY  = false;
    private boolean towering     = false;
    private EnumFacing targetFacing = null;

    // Rotations
    public final DropdownSetting rotationMode = new DropdownSetting("Rotations", 2,
            "NONE", "DEFAULT", "BACKWARDS", "SIDEWAYS", "GODBRIDGE", "SMOOTH");

    // Telly rotation speeds (only relevant when keepY == TELLY)
    public final SliderSetting tellystartrotationminspeed  = new SliderSetting("Telly Start Min Speed",  90.0, 1.0, 180.0, 0.5);
    public final SliderSetting tellystartrotationmaxspeed  = new SliderSetting("Telly Start Max Speed",  95.0, 1.0, 180.0, 0.5);
    public final SliderSetting tellynormalrotationminspeed = new SliderSetting("Telly Normal Min Speed", 30.0, 1.0, 180.0, 0.5);
    public final SliderSetting tellynormalrotationmaxspeed = new SliderSetting("Telly Normal Max Speed", 35.0, 1.0, 180.0, 0.5);

    // Movement
    public final DropdownSetting moveFix      = new DropdownSetting("Move Fix",   1, "NONE", "SILENT");
    public final DropdownSetting sprintMode   = new DropdownSetting("Sprint",     0, "NONE", "VANILLA");
    public final SliderSetting   groundMotion = new SliderSetting("Ground Motion", 100, 0, 200, 1);
    public final SliderSetting   airMotion    = new SliderSetting("Air Motion",    100, 0, 200, 1);
    public final SliderSetting   speedMotion  = new SliderSetting("Speed Motion",  100, 0, 200, 1);

    // Tower / Keep Y
    public final DropdownSetting tower       = new DropdownSetting("Tower",      0, "NONE", "VANILLA", "EXTRA", "TELLY");
    public final BooleanSetting  hypixeltower = new BooleanSetting("Hypixel Tower", false);
    public final DropdownSetting keepY       = new DropdownSetting("Keep Y",     0, "NONE", "VANILLA", "EXTRA", "TELLY");
    public final BooleanSetting  keepYonPress            = new BooleanSetting("Keep Y On Press",          false);
    public final BooleanSetting  disableWhileJumpActive  = new BooleanSetting("No Keep Y On Jump Potion", false);

    // Behaviour
    public final BooleanSetting  multiplace    = new BooleanSetting("Multi Place",    true);
    public final BooleanSetting  safeWalk      = new BooleanSetting("Safe Walk",      true);
    public final BooleanSetting  swing         = new BooleanSetting("Swing",          true);
    public final BooleanSetting  itemSpoof     = new BooleanSetting("Item Spoof",     false);
    public final BooleanSetting  blockCounter  = new BooleanSetting("Block Counter",  true);

    public Scaffold() {
        super("Scaffold", false);
        register(rotationMode);
        register(tellystartrotationminspeed);
        register(tellystartrotationmaxspeed);
        register(tellynormalrotationminspeed);
        register(tellynormalrotationmaxspeed);
        register(moveFix);
        register(sprintMode);
        register(groundMotion);
        register(airMotion);
        register(speedMotion);
        register(tower);
        register(hypixeltower);
        register(keepY);
        register(keepYonPress);
        register(disableWhileJumpActive);
        register(multiplace);
        register(safeWalk);
        register(swing);
        register(itemSpoof);
        register(blockCounter);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean shouldStopSprint() {
        if (isTowering()) return false;
        boolean stage = keepY.getIndex() == 1 || keepY.getIndex() == 2;
        return (!stage || this.stage <= 0) && sprintMode.getIndex() == 0;
    }

    private boolean canPlace() {
        BedNuker bedNuker = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
        if (bedNuker.isEnabled() && bedNuker.isReady()) return false;
        LongJump longJump = (LongJump) Myau.moduleManager.modules.get(LongJump.class);
        return !longJump.isEnabled() || !longJump.isAutoMode() || longJump.isJumping();
    }

    private EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
        double offset = 0.0;
        EnumFacing enumFacing = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing != EnumFacing.DOWN) {
                BlockPos pos = blockPos1.offset(facing);
                if (pos.getY() <= blockPos3.getY()) {
                    double distance = pos.distanceSqToCenter(
                            (double) blockPos3.getX() + 0.5,
                            (double) blockPos3.getY() + 0.5,
                            (double) blockPos3.getZ() + 0.5);
                    if (enumFacing == null || distance < offset || (distance == offset && facing == EnumFacing.UP)) {
                        offset = distance;
                        enumFacing = facing;
                    }
                }
            }
        }
        return enumFacing;
    }

    private BlockData getBlockData() {
        int startY = MathHelper.floor_double(mc.thePlayer.posY);
        BlockPos targetPos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                (this.stage != 0 && !this.shouldKeepY ? Math.min(startY, this.startY) : startY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ));
        if (!BlockUtil.isReplaceable(targetPos)) return null;

        ArrayList<BlockPos> positions = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 0; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = targetPos.add(x, y, z);
                    if (!BlockUtil.isReplaceable(pos)
                            && !BlockUtil.isInteractable(pos)
                            && !(mc.thePlayer.getDistance(
                                    (double) pos.getX() + 0.5,
                                    (double) pos.getY() + 0.5,
                                    (double) pos.getZ() + 0.5) > (double) mc.playerController.getBlockReachDistance())
                            && (this.stage == 0 || this.shouldKeepY || pos.getY() < this.startY)) {
                        for (EnumFacing facing : EnumFacing.VALUES) {
                            if (facing != EnumFacing.DOWN) {
                                BlockPos blockPos = pos.offset(facing);
                                if (BlockUtil.isReplaceable(blockPos)) {
                                    positions.add(pos);
                                }
                            }
                        }
                    }
                }
            }
        }
        if (positions.isEmpty()) return null;

        positions.sort(Comparator.comparingDouble(
                o -> o.distanceSqToCenter(
                        (double) targetPos.getX() + 0.5,
                        (double) targetPos.getY() + 0.5,
                        (double) targetPos.getZ() + 0.5)));

        BlockPos blockPos = positions.get(0);
        EnumFacing facing = getBestFacing(blockPos, targetPos);
        return facing == null ? null : new BlockData(blockPos, facing);
    }

    private void place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
        if (ItemUtil.isHoldingBlock() && blockCount > 0) {
            if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                    mc.thePlayer.inventory.getCurrentItem(), blockPos, enumFacing, vec3)) {
                if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) blockCount--;
                if (swing.getValue()) mc.thePlayer.swingItem();
                else PacketUtil.sendPacket(new C0APacketAnimation());
            }
        }
    }

    private EnumFacing yawToFacing(float yaw) {
        if (yaw < -135.0F || yaw > 135.0F) return EnumFacing.NORTH;
        else if (yaw < -45.0F) return EnumFacing.EAST;
        else return yaw < 45.0F ? EnumFacing.SOUTH : EnumFacing.WEST;
    }

    private double distanceToEdge(EnumFacing enumFacing) {
        switch (enumFacing) {
            case NORTH: return mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
            case EAST:  return Math.ceil(mc.thePlayer.posX) - mc.thePlayer.posX;
            case SOUTH: return Math.ceil(mc.thePlayer.posZ) - mc.thePlayer.posZ;
            default:    return mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
        }
    }

    private float getSpeed() {
        if (!mc.thePlayer.onGround) {
            return (float) airMotion.getValue() / 100.0F;
        }
        return MoveUtil.getSpeedLevel() > 0
                ? (float) speedMotion.getValue() / 100.0F
                : (float) groundMotion.getValue() / 100.0F;
    }

    private double getRandomOffset() {
        return 0.2155 - RandomUtil.nextDouble(1.0E-4, 9.0E-4);
    }

    private float getCurrentYaw() {
        return MoveUtil.adjustYaw(mc.thePlayer.rotationYaw,
                (float) MoveUtil.getForwardValue(), (float) MoveUtil.getLeftValue());
    }

    private boolean isDiagonal(float yaw) {
        float absYaw = Math.abs(yaw % 90.0F);
        return absYaw > 20.0F && absYaw < 70.0F;
    }

    private boolean isTowering() {
        if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !PlayerUtil.isAirAbove()) {
            boolean isKeepYTelly = keepY.getIndex() == 3;
            boolean isTowerTelly = tower.getIndex() == 3;
            return (isKeepYTelly && stage > 0) || (isTowerTelly && mc.gameSettings.keyBindJump.isKeyDown());
        }
        return false;
    }

    public int getSlot() { return lastSlot; }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;

        if (rotationTick > 0) rotationTick--;

        if (hypixeltower.getValue()
                && mc.thePlayer.motionY <= 0.0
                && Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ) <= 0.02D
                && mc.thePlayer.motionY >= -0.09
                && !(Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())
                        || Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode())
                        || Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode())
                        || Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode()))
                && Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
            mc.thePlayer.motionY = -0.38;
        }

        if (mc.thePlayer.onGround) {
            if (stage > 0) stage--;
            if (stage < 0) stage++;
            if (stage == 0
                    && keepY.getIndex() != 0
                    && (!keepYonPress.getValue() || PlayerUtil.isUsingItem())
                    && (!disableWhileJumpActive.getValue() || !mc.thePlayer.isPotionActive(Potion.jump))
                    && !mc.gameSettings.keyBindJump.isKeyDown()) {
                stage = 1;
            }
            startY = shouldKeepY ? startY : MathHelper.floor_double(mc.thePlayer.posY);
            shouldKeepY = false;
            towering = false;
        }

        if (canPlace()) {
            ItemStack stack = mc.thePlayer.getHeldItem();
            int count = ItemUtil.isBlock(stack) ? stack.stackSize : 0;
            blockCount = Math.min(blockCount, count);
            if (blockCount <= 0) {
                int slot = mc.thePlayer.inventory.currentItem;
                if (blockCount == 0) slot--;
                for (int i = slot; i > slot - 9; i--) {
                    int hotbarSlot = (i % 9 + 9) % 9;
                    ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
                    if (ItemUtil.isBlock(candidate)) {
                        mc.thePlayer.inventory.currentItem = hotbarSlot;
                        blockCount = candidate.stackSize;
                        break;
                    }
                }
            }

            float currentYaw = getCurrentYaw();
            float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
            float diagonalYaw  = isDiagonal(currentYaw)
                    ? yawDiffTo180
                    : RotationUtil.wrapAngleDiff(
                            currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F),
                            event.getYaw());

            if (!canRotate) {
                switch (rotationMode.getIndex()) {
                    case 1: // DEFAULT
                        if (yaw == -180.0F && pitch == 0.0F) {
                            yaw   = RotationUtil.quantizeAngle(diagonalYaw);
                            pitch = RotationUtil.quantizeAngle(85.0F);
                        } else {
                            yaw = RotationUtil.quantizeAngle(diagonalYaw);
                        }
                        break;
                    case 2: // BACKWARDS
                        if (yaw == -180.0F && pitch == 0.0F) {
                            yaw   = RotationUtil.quantizeAngle(yawDiffTo180);
                            pitch = RotationUtil.quantizeAngle(85.0F);
                        } else {
                            yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                        }
                        break;
                    case 3: // SIDEWAYS
                        if (yaw == -180.0F && pitch == 0.0F) {
                            yaw   = RotationUtil.quantizeAngle(diagonalYaw);
                            pitch = RotationUtil.quantizeAngle(85.0F);
                        } else {
                            yaw = RotationUtil.quantizeAngle(diagonalYaw);
                        }
                        break;
                    case 4: // GODBRIDGE
                        float roundedYaw = Math.round(currentYaw / 45.0f) * 45.0f;
                        yaw = RotationUtil.quantizeAngle(roundedYaw);
                        if (pitch == 0.0F || !canRotate) {
                            pitch = RotationUtil.quantizeAngle(79.3f);
                        }
                        break;
                    case 5: // SMOOTH
                        if (yaw == -180.0F && pitch == 0.0F) {
                            yaw   = RotationUtil.quantizeAngle(diagonalYaw);
                            pitch = RotationUtil.quantizeAngle(85.0F);
                        } else {
                            float targetYaw = isDiagonal(currentYaw) ? diagonalYaw : yawDiffTo180;
                            float yawDiff   = RotationUtil.wrapAngleDiff(targetYaw - yaw, event.getYaw());
                            float tolerance = rotationTick >= 2
                                    ? RandomUtil.nextFloat((float) tellystartrotationminspeed.getValue(), (float) tellystartrotationmaxspeed.getValue())
                                    : RandomUtil.nextFloat((float) tellynormalrotationminspeed.getValue(), (float) tellynormalrotationmaxspeed.getValue());
                            if (Math.abs(yawDiff) > tolerance) {
                                yaw = RotationUtil.quantizeAngle(yaw + RotationUtil.clampAngle(yawDiff, tolerance));
                            }
                        }
                        break;
                }
            }

            BlockData blockData = getBlockData();
            Vec3 hitVec = null;

            if (blockData != null) {
                double[] x = placeOffsets, y = placeOffsets, z = placeOffsets;
                switch (blockData.facing()) {
                    case NORTH: z = new double[]{0.0}; break;
                    case EAST:  x = new double[]{1.0}; break;
                    case SOUTH: z = new double[]{1.0}; break;
                    case WEST:  x = new double[]{0.0}; break;
                    case DOWN:  y = new double[]{0.0}; break;
                    case UP:    y = new double[]{1.0}; break;
                }
                float bestYaw = -180.0F, bestPitch = 0.0F, bestDiff = 0.0F;
                for (double dx : x) {
                    for (double dy : y) {
                        for (double dz : z) {
                            double relX = (double) blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                            double relY = (double) blockData.blockPos().getY() + dy - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                            double relZ = (double) blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                            float baseYaw = RotationUtil.wrapAngleDiff(yaw, event.getYaw());
                            float[] rotations = RotationUtil.getRotationsTo(relX, relY, relZ, baseYaw, pitch);
                            MovingObjectPosition mop = RotationUtil.rayTrace(rotations[0], rotations[1],
                                    mc.playerController.getBlockReachDistance(), 1.0F);
                            if (mop != null
                                    && mop.typeOfHit == MovingObjectType.BLOCK
                                    && mop.getBlockPos().equals(blockData.blockPos())
                                    && mop.sideHit == blockData.facing()) {
                                float totalDiff = Math.abs(rotations[0] - baseYaw) + Math.abs(rotations[1] - pitch);
                                if ((bestYaw == -180.0F && bestPitch == 0.0F) || totalDiff < bestDiff) {
                                    bestYaw = rotations[0];
                                    bestPitch = rotations[1];
                                    bestDiff = totalDiff;
                                    hitVec = mop.hitVec;
                                }
                            }
                        }
                    }
                }
                if (bestYaw != -180.0F || bestPitch != 0.0F) {
                    yaw = bestYaw;
                    pitch = bestPitch;
                    canRotate = true;
                }
            }

            if (canRotate && MoveUtil.isForwardPressed()
                    && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - yaw)) < 90.0F) {
                switch (rotationMode.getIndex()) {
                    case 2: yaw = RotationUtil.quantizeAngle(yawDiffTo180); break;
                    case 3: yaw = RotationUtil.quantizeAngle(diagonalYaw);  break;
                }
            }

            if (rotationMode.getIndex() != 0) {
                float targetYaw = yaw, targetPitch = pitch;
                if (towering && (mc.thePlayer.motionY > 0.0 || mc.thePlayer.posY > (double)(startY + 1))) {
                    float yawDiff  = MathHelper.wrapAngleTo180_float(yaw - event.getYaw());
                    float tolerance = rotationTick >= 2
                            ? RandomUtil.nextFloat((float) tellystartrotationminspeed.getValue(), (float) tellystartrotationmaxspeed.getValue())
                            : RandomUtil.nextFloat((float) tellynormalrotationminspeed.getValue(), (float) tellynormalrotationmaxspeed.getValue());
                    if (Math.abs(yawDiff) > tolerance) {
                        float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
                        targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
                        rotationTick = Math.max(rotationTick, 1);
                    }
                }
                if (isTowering()) {
                    float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
                    targetYaw   = RotationUtil.quantizeAngle(event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
                    targetPitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
                    rotationTick = 3;
                    towering = true;
                }
                event.setRotation(targetYaw, targetPitch, 3);
                if (moveFix.getIndex() == 1) event.setPervRotation(targetYaw, 3);
            }

            if (blockData != null && hitVec != null && rotationTick <= 0) {
                place(blockData.blockPos(), blockData.facing(), hitVec);
                if (multiplace.getValue()) {
                    for (int i = 0; i < 3; i++) {
                        blockData = getBlockData();
                        if (blockData == null) break;
                        MovingObjectPosition mop = RotationUtil.rayTrace(yaw, pitch,
                                mc.playerController.getBlockReachDistance(), 1.0F);
                        if (mop != null
                                && mop.typeOfHit == MovingObjectType.BLOCK
                                && mop.getBlockPos().equals(blockData.blockPos())
                                && mop.sideHit == blockData.facing()) {
                            place(blockData.blockPos(), blockData.facing(), mop.hitVec);
                        } else {
                            hitVec = BlockUtil.getClickVec(blockData.blockPos(), blockData.facing());
                            double dx = hitVec.xCoord - mc.thePlayer.posX;
                            double dy = hitVec.yCoord - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                            double dz = hitVec.zCoord - mc.thePlayer.posZ;
                            float[] rotations = RotationUtil.getRotationsTo(dx, dy, dz, event.getYaw(), event.getPitch());
                            if (!(Math.abs(rotations[0] - yaw) < 120.0F) || !(Math.abs(rotations[1] - pitch) < 60.0F)) break;
                            mop = RotationUtil.rayTrace(rotations[0], rotations[1],
                                    mc.playerController.getBlockReachDistance(), 1.0F);
                            if (mop == null || mop.typeOfHit != MovingObjectType.BLOCK
                                    || !mop.getBlockPos().equals(blockData.blockPos())
                                    || mop.sideHit != blockData.facing()) break;
                            place(blockData.blockPos(), blockData.facing(), mop.hitVec);
                        }
                    }
                }
            }

            if (targetFacing != null) {
                if (rotationTick <= 0) {
                    int pX = MathHelper.floor_double(mc.thePlayer.posX);
                    int pY = MathHelper.floor_double(mc.thePlayer.posY);
                    int pZ = MathHelper.floor_double(mc.thePlayer.posZ);
                    BlockPos below = new BlockPos(pX, pY - 1, pZ);
                    hitVec = BlockUtil.getHitVec(below, targetFacing, yaw, pitch);
                    place(below, targetFacing, hitVec);
                }
                targetFacing = null;
            } else if (keepY.getIndex() == 2 && stage > 0 && !mc.thePlayer.onGround) {
                int nextBlockY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
                if (nextBlockY <= startY && mc.thePlayer.posY > (double)(startY + 1)) {
                    shouldKeepY = true;
                    blockData = getBlockData();
                    if (blockData != null && rotationTick <= 0) {
                        hitVec = BlockUtil.getHitVec(blockData.blockPos(), blockData.facing(), yaw, pitch);
                        place(blockData.blockPos(), blockData.facing(), hitVec);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!isEnabled()) return;
        if (!mc.thePlayer.isCollidedHorizontally
                && mc.thePlayer.hurtTime <= 5
                && !mc.thePlayer.isPotionActive(Potion.jump)
                && mc.gameSettings.keyBindJump.isKeyDown()
                && ItemUtil.isHoldingBlock()) {
            int yState = (int)(mc.thePlayer.posY % 1.0 * 100.0);
            switch (tower.getIndex()) {
                case 1: // VANILLA
                    switch (towerTick) {
                        case 0:
                            if (mc.thePlayer.onGround) { towerTick = 1; mc.thePlayer.motionY = -0.0784000015258789; }
                            return;
                        case 1:
                            if (yState == 0 && PlayerUtil.isAirBelow()) {
                                startY = MathHelper.floor_double(mc.thePlayer.posY);
                                towerTick = 2;
                                mc.thePlayer.motionY = 0.42F;
                                if (MoveUtil.isForwardPressed()) MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                                else { MoveUtil.setSpeed(0.0); event.setForward(0.0F); event.setStrafe(0.0F); }
                            } else { towerTick = 0; }
                            return;
                        case 2:
                            towerTick = 3;
                            mc.thePlayer.motionY = 0.75 - mc.thePlayer.posY % 1.0;
                            return;
                        case 3:
                            towerTick = 1;
                            mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                            return;
                        default: towerTick = 0; return;
                    }
                case 2: // EXTRA
                    switch (towerTick) {
                        case 0:
                            if (mc.thePlayer.onGround) { towerTick = 1; mc.thePlayer.motionY = -0.0784000015258789; }
                            return;
                        case 1:
                            if (yState == 0 && PlayerUtil.isAirBelow()) {
                                startY = MathHelper.floor_double(mc.thePlayer.posY);
                                if (!MoveUtil.isForwardPressed()) {
                                    towerDelay = 2;
                                    MoveUtil.setSpeed(0.0);
                                    event.setForward(0.0F);
                                    event.setStrafe(0.0F);
                                    EnumFacing facing = yawToFacing(MathHelper.wrapAngleTo180_float(yaw - 180.0F));
                                    double distance = distanceToEdge(facing);
                                    if (distance > 0.1) {
                                        if (mc.thePlayer.onGround) {
                                            Vec3i directionVec = facing.getDirectionVec();
                                            double offset = Math.min(getRandomOffset(), distance - 0.05);
                                            double jitter = RandomUtil.nextDouble(0.02, 0.03);
                                            AxisAlignedBB nextBox = mc.thePlayer.getEntityBoundingBox()
                                                    .offset((double) directionVec.getX() * (offset - jitter), 0.0,
                                                            (double) directionVec.getZ() * (offset - jitter));
                                            if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, nextBox).isEmpty()) {
                                                mc.thePlayer.motionY = -0.0784000015258789;
                                                mc.thePlayer.setPosition(
                                                        nextBox.minX + (nextBox.maxX - nextBox.minX) / 2.0,
                                                        nextBox.minY,
                                                        nextBox.minZ + (nextBox.maxZ - nextBox.minZ) / 2.0);
                                            }
                                        }
                                    } else {
                                        towerTick = 2;
                                        targetFacing = facing;
                                        mc.thePlayer.motionY = 0.42F;
                                    }
                                } else {
                                    towerTick = 2;
                                    towerDelay++;
                                    mc.thePlayer.motionY = 0.42F;
                                    MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                                }
                            } else { towerTick = 0; towerDelay = 0; }
                            return;
                        case 2:
                            towerTick = 3;
                            mc.thePlayer.motionY -= RandomUtil.nextDouble(0.00101, 0.00109);
                            return;
                        case 3:
                            if (towerDelay >= 4) { towerTick = 4; towerDelay = 0; }
                            else { towerTick = 1; mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0; }
                            return;
                        case 4: towerTick = 5; return;
                        case 5:
                            if (!PlayerUtil.isAirBelow()) { towerTick = 0; }
                            else {
                                towerTick = 1;
                                mc.thePlayer.motionY -= 0.08;
                                mc.thePlayer.motionY *= 0.98F;
                                mc.thePlayer.motionY -= 0.08;
                                mc.thePlayer.motionY *= 0.98F;
                            }
                            return;
                        default: towerTick = 0; towerDelay = 0; return;
                    }
                default:
                    towerTick = 0;
                    towerDelay = 0;
            }
        } else {
            towerTick = 0;
            towerDelay = 0;
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled()) return;
        if (moveFix.getIndex() == 1
                && RotationState.isActived()
                && RotationState.getPriority() == 3.0F
                && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
        if (mc.thePlayer.onGround && stage > 0 && MoveUtil.isForwardPressed()) {
            mc.thePlayer.movementInput.jump = true;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!isEnabled()) return;
        float speed = getSpeed();
        if (speed != 1.0F) {
            if (mc.thePlayer.movementInput.moveForward != 0.0F && mc.thePlayer.movementInput.moveStrafe != 0.0F) {
                mc.thePlayer.movementInput.moveForward *= (1.0F / (float) Math.sqrt(2.0));
                mc.thePlayer.movementInput.moveStrafe  *= (1.0F / (float) Math.sqrt(2.0));
            }
            mc.thePlayer.movementInput.moveForward *= speed;
            mc.thePlayer.movementInput.moveStrafe  *= speed;
        }
        if (shouldStopSprint()) mc.thePlayer.setSprinting(false);
    }

    @EventTarget
    public void onSafeWalk(SafeWalkEvent event) {
        if (!isEnabled() || !safeWalk.getValue()) return;
        if (mc.thePlayer.onGround && mc.thePlayer.motionY <= 0.0
                && PlayerUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -1.0)) {
            event.setSafeWalk(true);
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!isEnabled() || !blockCounter.getValue()) return;

        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.stackSize > 0) {
                Item item = stack.getItem();
                if (item instanceof ItemBlock) {
                    Block block = ((ItemBlock) item).getBlock();
                    if (!BlockUtil.isInteractable(block) && BlockUtil.isSolid(block)) {
                        count += stack.stackSize;
                    }
                }
            }
        }

        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        float scale = (float) hud.scale.getValue();   // ← fixed: was .floatValue()

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 0.0F);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        mc.fontRendererObj.drawString(
                String.format("%d block%s left", count, count != 1 ? "s" : ""),
                ((float) new ScaledResolution(mc).getScaledWidth() / 2.0F
                        + (float) mc.fontRendererObj.FONT_HEIGHT * 1.5F) / scale,
                (float) new ScaledResolution(mc).getScaledHeight() / 2.0F / scale
                        - (float) mc.fontRendererObj.FONT_HEIGHT / 2.0F + 1.0F,
                (count > 0 ? Color.WHITE.getRGB() : new Color(255, 85, 85).getRGB()) | -1090519040,
                hud.shadow.getValue());
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (isEnabled()) event.setCancelled(true);
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (isEnabled()) event.setCancelled(true);
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (isEnabled()) event.setCancelled(true);
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (isEnabled()) {
            lastSlot = event.setSlot(lastSlot);
            event.setCancelled(true);
        }
    }

    @Override
    public void onEnabled() {
        lastSlot     = mc.thePlayer != null ? mc.thePlayer.inventory.currentItem : -1;
        blockCount   = -1;
        rotationTick = 3;
        yaw          = -180.0F;
        pitch        = 0.0F;
        canRotate    = false;
        towerTick    = 0;
        towerDelay   = 0;
        towering     = false;
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null && lastSlot != -1) {
            mc.thePlayer.inventory.currentItem = lastSlot;
        }
    }

    public int getBlockCount() { return blockCount; }

    // ── Inner class ───────────────────────────────────────────────────────────

    public static class BlockData {
        private final BlockPos    blockPos;
        private final EnumFacing  facing;

        public BlockData(BlockPos blockPos, EnumFacing enumFacing) {
            this.blockPos = blockPos;
            this.facing   = enumFacing;
        }

        public BlockPos   blockPos() { return blockPos; }
        public EnumFacing facing()   { return facing; }
    }
}