package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.SafeWalkEvent;
import myau.events.UpdateEvent;
import myau.module.BooleanSetting;
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

    // How many air blocks below feet before clutch activates
    public final SliderSetting  triggerDepth = register(new SliderSetting("Trigger Depth", 4.0, 1.0, 10.0, 0.5));
    // Bridge forward speed multiplier (1.0 = normal walk speed)
    public final SliderSetting  speed        = register(new SliderSetting("Speed",         1.0, 0.5,  3.0, 0.05));
    // Whether to actively bridge forward while clutching
    public final BooleanSetting bridge       = register(new BooleanSetting("Bridge",       false));
    // Swing arm on placement
    public final BooleanSetting swing        = register(new BooleanSetting("Swing",        true));

    private boolean clutching = false;
    private int     blockSlot = -1;

    // Stored placement target for this tick — set in UpdateEvent, used in placeBelow
    private BlockPos   pendingBlock = null;
    private EnumFacing pendingFace  = null;

    public Clutch() {
        super("Clutch", false);
    }

    @Override
    public void onDisabled() {
        clutching = false;
        blockSlot = -1;
        pendingBlock = null;
        pendingFace  = null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isOverVoid() {
        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY;
        double pz = mc.thePlayer.posZ;
        for (double d = 0.25; d <= triggerDepth.getValue(); d += 0.25) {
            if (!BlockUtil.isReplaceable(new BlockPos(px, py - d, pz))) return false;
        }
        return true;
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            if (ItemUtil.isBlock(mc.thePlayer.inventory.getStackInSlot(i))) return i;
        }
        return -1;
    }

    /**
     * Finds the best block+face to place against right now.
     * Scans from current foot-Y downward so it triggers the instant
     * any surface enters reach — no waiting to land.
     *
     * Priority:
     *   1. TOP of block directly beneath each scan Y  (always flat)
     *   2. SIDE of a horizontal neighbour at each scan Y
     */
    private boolean findPlacement() {
        double px   = mc.thePlayer.posX;
        double py   = mc.thePlayer.posY;
        double pz   = mc.thePlayer.posZ;
        double reach = mc.playerController.getBlockReachDistance();
        double depth = triggerDepth.getValue();

        int startY = MathHelper.floor_double(py) - 1;
        int endY   = MathHelper.floor_double(py - depth);

        for (int scanY = startY; scanY >= endY; scanY--) {
            int scanX = MathHelper.floor_double(px);
            int scanZ = MathHelper.floor_double(pz);

            // 1. TOP face of block one below this Y
            BlockPos beneath = new BlockPos(scanX, scanY - 1, scanZ);
            if (!BlockUtil.isReplaceable(beneath)) {
                if (inReach(beneath, reach)) {
                    pendingBlock = beneath;
                    pendingFace  = EnumFacing.UP;
                    return true;
                }
            }

            // 2. Horizontal neighbours at this exact Y
            for (EnumFacing face : new EnumFacing[]{
                    EnumFacing.NORTH, EnumFacing.SOUTH,
                    EnumFacing.EAST,  EnumFacing.WEST }) {
                BlockPos neighbour = new BlockPos(scanX, scanY, scanZ).offset(face);
                if (!BlockUtil.isReplaceable(neighbour)) {
                    if (inReach(neighbour, reach)) {
                        pendingBlock = neighbour;
                        pendingFace  = face.getOpposite();
                        return true;
                    }
                }
            }
        }

        pendingBlock = null;
        pendingFace  = null;
        return false;
    }

    private boolean inReach(BlockPos pos, double reach) {
        return mc.thePlayer.getDistanceSq(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= reach * reach;
    }

    /** Computes the yaw/pitch required to be looking at pendingBlock's pendingFace */
    private float[] getPlacementRotation() {
        Vec3 eyes   = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 target = BlockUtil.getClickVec(pendingBlock, pendingFace);
        double dx   = target.xCoord - eyes.xCoord;
        double dy   = target.yCoord - eyes.yCoord;
        double dz   = target.zCoord - eyes.zCoord;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float)(-Math.atan2(dy, horiz) * 180.0 / Math.PI);
        return new float[]{ yaw, MathHelper.clamp_float(pitch, -90f, 90f) };
    }

    private void doPlace() {
        if (pendingBlock == null || blockSlot < 0) return;
        Vec3 hitVec = BlockUtil.getClickVec(pendingBlock, pendingFace);
        int prev = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = blockSlot;
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        boolean placed = mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, held, pendingBlock, pendingFace, hitVec);
        mc.thePlayer.inventory.currentItem = prev;
        if (placed) {
            if (swing.getValue()) mc.thePlayer.swingItem();
            else PacketUtil.sendPacket(new C0APacketAnimation());
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    /**
     * UpdateEvent fires during onUpdate() — this is where we:
     * 1. Decide if we should be clutching
     * 2. Find a placement target
     * 3. Set a SILENT server-side rotation toward the placement face
     *    using event.setRotation() — this does NOT move the camera visually
     * 4. Place the block (server will accept it because the sent rotation matches)
     * 5. If Bridge is on, push movement forward
     */
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // Reset when landed
        if (mc.thePlayer.onGround) {
            clutching    = false;
            blockSlot    = -1;
            pendingBlock = null;
            pendingFace  = null;
            return;
        }

        // Only activate while falling
        if (mc.thePlayer.motionY >= 0) {
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

        // Find what to place against
        if (!findPlacement()) return;

        // ── Silent rotation — server-side only, camera stays where the player left it ──
        float[] rots = getPlacementRotation();
        event.setRotation(rots[0], rots[1], 2);

        // Place the block
        doPlace();

        // ── Bridge: push forward at walking speed in the direction the player faces ──
        if (bridge.getValue()) {
            double moveSpeed = MoveUtil.getBaseMoveSpeed() * speed.getValue();
            MoveUtil.setSpeed(moveSpeed, MoveUtil.getMoveYaw());
        }
    }

    /** Edge-stop so you don't slide off the placed block */
    @EventTarget
    public void onSafeWalk(SafeWalkEvent event) {
        if (isEnabled() && clutching) event.setSafeWalk(true);
    }
}
