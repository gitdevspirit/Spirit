package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.SafeWalkEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.*;
import net.minecraft.util.MathHelper;

public class Clutch extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // ── Settings ──────────────────────────────────────────────────────────────

    public final SliderSetting   triggerDepth = register(new SliderSetting("Trigger Depth", 3.0, 1.0, 10.0, 0.5));
    public final SliderSetting   speed        = register(new SliderSetting("Speed",         1.0, 0.5,  3.0, 0.05));
    public final SliderSetting   bridgePitch  = register(new SliderSetting("Bridge Pitch",  80,  60,   90,   1));
    public final DropdownSetting mode         = register(new DropdownSetting("Mode", 0, "STOP", "SNEAK", "BRIDGE"));
    public final BooleanSetting  swing        = register(new BooleanSetting("Swing",        true));
    public final BooleanSetting  onlyFalling  = register(new BooleanSetting("Only Falling", false));

    // ── State ─────────────────────────────────────────────────────────────────

    private boolean clutching = false;
    private int     blockSlot = -1;

    public Clutch() {
        super("Clutch", false);
    }

    @Override
    public void onDisabled() {
        clutching = false;
        blockSlot = -1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** True when there are no solid blocks within triggerDepth below feet */
    private boolean isOverVoid() {
        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY;
        double pz = mc.thePlayer.posZ;
        double depth = triggerDepth.getValue();

        for (double d = 0.25; d <= depth; d += 0.25) {
            BlockPos check = new BlockPos(px, py - d, pz);
            if (!BlockUtil.isReplaceable(check)) return false;
        }
        return true;
    }

    /** Find first hotbar slot holding a solid placeable block */
    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            if (ItemUtil.isBlock(mc.thePlayer.inventory.getStackInSlot(i))) return i;
        }
        return -1;
    }

    /**
     * Places a block at exactly foot-level below the player.
     *
     * Strategy (in priority order):
     *   1. Place on top of the block that is at (footY - 1) directly below  → UP face
     *   2. Place against a horizontal neighbour at exactly footY - 1         → side face
     *
     * We NEVER go below footY - 1 so the result is always a flat floor.
     */
    private boolean placeBelow() {
        if (blockSlot < 0) return false;

        // The block position that should become our floor
        // MathHelper.floor_double gives us the correct integer Y regardless of sub-block offset
        int floorX = MathHelper.floor_double(mc.thePlayer.posX);
        int floorY = MathHelper.floor_double(mc.thePlayer.posY) - 1;
        int floorZ = MathHelper.floor_double(mc.thePlayer.posZ);
        BlockPos targetPos = new BlockPos(floorX, floorY, floorZ);

        // ── 1. Block directly below the floor position (place on its UP face) ─
        BlockPos beneath = targetPos.down(); // floorY - 1
        if (!BlockUtil.isReplaceable(beneath)) {
            if (doPlace(beneath, EnumFacing.UP)) return true;
        }

        // ── 2. Horizontal neighbours at exactly floorY (place on their side face) ─
        for (EnumFacing face : new EnumFacing[]{
                EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST }) {

            BlockPos neighbour = targetPos.offset(face);
            if (!BlockUtil.isReplaceable(neighbour)) {
                // Place against the face of the neighbour that points back toward targetPos
                if (doPlace(neighbour, face.getOpposite())) return true;
            }
        }

        return false;
    }

    /** Perform the actual block placement against the given block+face, switching slots silently */
    private boolean doPlace(BlockPos against, EnumFacing face) {
        Vec3 hitVec = BlockUtil.getClickVec(against, face);
        int prev = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = blockSlot;

        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        boolean placed = mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, held, against, face, hitVec);

        mc.thePlayer.inventory.currentItem = prev;

        if (placed) {
            if (swing.getValue()) mc.thePlayer.swingItem();
            else PacketUtil.sendPacket(new C0APacketAnimation());
        }
        return placed;
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // Landed — disengage
        if (mc.thePlayer.onGround) {
            clutching = false;
            blockSlot = -1;
            return;
        }

        // Only Falling gate
        if (onlyFalling.getValue() && mc.thePlayer.motionY >= 0) {
            clutching = false;
            return;
        }

        if (!isOverVoid()) {
            clutching = false;
            return;
        }

        clutching = true;
        blockSlot = findBlockSlot();
        if (blockSlot < 0) return;

        int modeIdx = mode.getIndex();

        // ── STOP ─────────────────────────────────────────────────────────────
        if (modeIdx == 0) {
            // Bleed off horizontal speed then place
            double spd = MoveUtil.getSpeed();
            if (spd > 0.01) MoveUtil.setSpeed(spd * (0.25 * speed.getValue()));
            placeBelow();
        }

        // ── SNEAK ─────────────────────────────────────────────────────────────
        else if (modeIdx == 1) {
            placeBelow();
            // edge-stop handled by SafeWalkEvent
        }

        // ── BRIDGE ────────────────────────────────────────────────────────────
        else if (modeIdx == 2) {
            // Look straight down enough to place
            Myau.rotationManager.setRotation(
                    mc.thePlayer.rotationYaw,
                    (float) bridgePitch.getValue(),
                    1, false);

            double moveSpeed = MoveUtil.getBaseMoveSpeed() * speed.getValue();
            MoveUtil.setSpeed(moveSpeed, MoveUtil.getMoveYaw());

            placeBelow();
        }
    }

    @EventTarget
    public void onSafeWalk(SafeWalkEvent event) {
        if (!isEnabled() || !clutching) return;
        if (mode.getIndex() >= 1) event.setSafeWalk(true);
    }
}
