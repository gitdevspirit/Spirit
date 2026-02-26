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

    public final SliderSetting  triggerDepth = register(new SliderSetting("Trigger Depth", 5.0,  1.0, 20.0, 0.5));
    public final SliderSetting  speed        = register(new SliderSetting("Speed",         1.0,  0.5,  3.0, 0.05));
    public final BooleanSetting bridge       = register(new BooleanSetting("Bridge",       false));
    public final BooleanSetting swing        = register(new BooleanSetting("Swing",        true));

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

    // ── No solid block within triggerDepth below feet ─────────────────────────
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
     * Finds the best block+face to place against.
     *
     * Searches from the block directly below feet, expanding outward and
     * downward.  Crucially, after each successful placement the newly placed
     * block becomes the surface for the NEXT tick — so each tick we place one
     * block and the column grows until the player lands.
     *
     * Returns null if nothing is in reach.
     */
    private PlaceData findPlacement() {
        double px    = mc.thePlayer.posX;
        double py    = mc.thePlayer.posY;
        double pz    = mc.thePlayer.posZ;
        double reach = mc.playerController.getBlockReachDistance();

        int baseX = MathHelper.floor_double(px);
        int baseY = MathHelper.floor_double(py) - 1; // block directly below feet
        int baseZ = MathHelper.floor_double(pz);

        // Scan outward in rings, then downward — closest wins
        for (int radius = 0; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    for (int dy = 0; dy >= -4; dy--) {
                        BlockPos target = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                        if (!BlockUtil.isReplaceable(target)) continue;

                        // Check every face of this air block for a solid neighbour
                        for (EnumFacing face : EnumFacing.VALUES) {
                            BlockPos neighbour = target.offset(face);
                            if (BlockUtil.isReplaceable(neighbour)) continue;
                            if (!inReach(neighbour, reach)) continue;
                            // place against neighbour's face that points back at target
                            return new PlaceData(neighbour, face.getOpposite());
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean inReach(BlockPos pos, double reach) {
        double dx = pos.getX() + 0.5 - mc.thePlayer.posX;
        double dy = pos.getY() + 0.5 - (mc.thePlayer.posY - mc.thePlayer.getEyeHeight());
        double dz = pos.getZ() + 0.5 - mc.thePlayer.posZ;
        return dx*dx + dy*dy + dz*dz <= reach * reach;
    }

    /** Yaw + pitch aimed straight at the placement face — for the server packet */
    private float[] getPlacementRotation(PlaceData pd) {
        Vec3 eyes   = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 target = BlockUtil.getClickVec(pd.block, pd.face);
        double dx   = target.xCoord - eyes.xCoord;
        double dy   = target.yCoord - eyes.yCoord;
        double dz   = target.zCoord - eyes.zCoord;
        float yaw   = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float)(-Math.atan2(dy, Math.sqrt(dx*dx + dz*dz)) * 180.0 / Math.PI);
        return new float[]{ yaw, MathHelper.clamp_float(pitch, -90f, 90f) };
    }

    private boolean doPlace(PlaceData pd) {
        Vec3 hitVec = BlockUtil.getClickVec(pd.block, pd.face);
        int prev = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = blockSlot;
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        boolean placed = mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, held, pd.block, pd.face, hitVec);
        mc.thePlayer.inventory.currentItem = prev;
        if (placed) {
            if (swing.getValue()) mc.thePlayer.swingItem();
            else PacketUtil.sendPacket(new C0APacketAnimation());
        }
        return placed;
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (mc.thePlayer.onGround) {
            clutching = false;
            blockSlot = -1;
            return;
        }

        // Must be falling downward
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

        PlaceData pd = findPlacement();
        if (pd == null) return;

        // ── Silent rotation: server receives the correct look direction,
        //    player's visual camera is completely unchanged ──────────────────
        float[] rots = getPlacementRotation(pd);
        event.setRotation(rots[0], rots[1], 2);

        // Place the block this tick — next tick the placed block becomes the
        // new surface and we place the next one on top of it, building a column
        doPlace(pd);

        // Bridge: push forward each tick so we keep moving while placing
        if (bridge.getValue()) {
            MoveUtil.setSpeed(
                MoveUtil.getBaseMoveSpeed() * speed.getValue(),
                MoveUtil.getMoveYaw());
        }
    }

    /** Stay on edge of placed blocks — don't slide off */
    @EventTarget
    public void onSafeWalk(SafeWalkEvent event) {
        if (isEnabled() && clutching) event.setSafeWalk(true);
    }

    // ── Helper record ─────────────────────────────────────────────────────────

    private static class PlaceData {
        final BlockPos   block;
        final EnumFacing face;
        PlaceData(BlockPos block, EnumFacing face) {
            this.block = block;
            this.face  = face;
        }
    }
}
