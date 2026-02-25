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

    public final SliderSetting   triggerDepth = register(new SliderSetting("Trigger Depth", 4.0, 1.0, 10.0, 0.5));
    public final SliderSetting   speed        = register(new SliderSetting("Speed",         1.0, 0.5,  3.0, 0.05));
    public final SliderSetting   bridgePitch  = register(new SliderSetting("Bridge Pitch",  80,  60,   90,   1));
    public final DropdownSetting mode         = register(new DropdownSetting("Mode", 0, "CLUTCH", "SNEAK", "BRIDGE"));
    public final BooleanSetting  swing        = register(new BooleanSetting("Swing", true));

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

    // ── no solid block within triggerDepth below feet ─────────────────────────
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
     * Scans from current foot-Y downward (up to triggerDepth blocks) looking
     * for any surface we can place against right now.  This means the block
     * gets placed the instant a valid face enters reach — no waiting to land.
     *
     * Priority per Y level:
     *   1. TOP face of the block one below that Y  (most reliable, flat result)
     *   2. SIDE face of a horizontal neighbour at that Y
     */
    private boolean placeFalling() {
        if (blockSlot < 0) return false;

        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY;
        double pz = mc.thePlayer.posZ;
        double reach = mc.playerController.getBlockReachDistance();
        double depth = triggerDepth.getValue();

        // Walk down from feet, one block at a time
        int startY = MathHelper.floor_double(py) - 1;
        int endY   = MathHelper.floor_double(py - depth);

        for (int scanY = startY; scanY >= endY; scanY--) {
            int scanX = MathHelper.floor_double(px);
            int scanZ = MathHelper.floor_double(pz);
            BlockPos targetPos = new BlockPos(scanX, scanY, scanZ);

            // ── 1. TOP face of block directly beneath targetPos ───────────────
            BlockPos beneath = targetPos.down();
            if (!BlockUtil.isReplaceable(beneath)) {
                double dist = mc.thePlayer.getDistanceSq(
                        scanX + 0.5, scanY - 1 + 1.0, scanZ + 0.5);
                if (dist <= reach * reach) {
                    if (doPlace(beneath, EnumFacing.UP)) return true;
                }
            }

            // ── 2. SIDE faces of horizontal neighbours at this Y ──────────────
            for (EnumFacing face : new EnumFacing[]{
                    EnumFacing.NORTH, EnumFacing.SOUTH,
                    EnumFacing.EAST,  EnumFacing.WEST }) {
                BlockPos neighbour = targetPos.offset(face);
                if (!BlockUtil.isReplaceable(neighbour)) {
                    double dist = mc.thePlayer.getDistanceSq(
                            neighbour.getX() + 0.5,
                            neighbour.getY() + 0.5,
                            neighbour.getZ() + 0.5);
                    if (dist <= reach * reach) {
                        if (doPlace(neighbour, face.getOpposite())) return true;
                    }
                }
            }
        }
        return false;
    }

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

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (mc.thePlayer.onGround) {
            clutching = false;
            blockSlot = -1;
            return;
        }

        // Only activate when actually falling downward
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

        int modeIdx = mode.getIndex();

        // ── CLUTCH mode ───────────────────────────────────────────────────────
        // Silently rotate view downward so placement packets are valid,
        // then aggressively try to place at every reachable Y level below us.
        // Horizontal movement is preserved — this feels like a natural catch.
        if (modeIdx == 0) {
            // Silent rotation — look straight down so server accepts the placement
            Myau.rotationManager.setRotation(
                    mc.thePlayer.rotationYaw, 89.9f, 1, false);
            placeFalling();
        }

        // ── SNEAK mode ────────────────────────────────────────────────────────
        else if (modeIdx == 1) {
            Myau.rotationManager.setRotation(
                    mc.thePlayer.rotationYaw, 89.9f, 1, false);
            placeFalling();
            // edge-stop via SafeWalkEvent
        }

        // ── BRIDGE mode ───────────────────────────────────────────────────────
        else if (modeIdx == 2) {
            Myau.rotationManager.setRotation(
                    mc.thePlayer.rotationYaw,
                    (float) bridgePitch.getValue(),
                    1, false);
            double moveSpeed = MoveUtil.getBaseMoveSpeed() * speed.getValue();
            MoveUtil.setSpeed(moveSpeed, MoveUtil.getMoveYaw());
            placeFalling();
        }
    }

    @EventTarget
    public void onSafeWalk(SafeWalkEvent event) {
        if (!isEnabled() || !clutching) return;
        if (mode.getIndex() >= 1) event.setSafeWalk(true);
    }
}
