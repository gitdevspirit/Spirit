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
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class Clutch extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting  triggerDepth = register(new SliderSetting("Trigger Depth", 5.0, 1.0, 20.0, 0.5));
    public final BooleanSetting bridge       = register(new BooleanSetting("Bridge",       false));
    public final SliderSetting  speed        = register(new SliderSetting("Speed",         1.0, 0.5,  3.0, 0.05));
    public final BooleanSetting swing        = register(new BooleanSetting("Swing",        true));

    private boolean clutching = false;

    public Clutch() {
        super("Clutch", false);
    }

    @Override
    public void onDisabled() {
        clutching = false;
    }

    // ── Only true when falling over real void (not a normal jump) ─────────────
    private boolean isOverVoid() {
        double px    = mc.thePlayer.posX;
        double py    = mc.thePlayer.posY;
        double pz    = mc.thePlayer.posZ;
        double depth = triggerDepth.getValue();

        // 3x3 footprint straight down
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                for (double d = 0.25; d <= depth; d += 0.5) {
                    if (!BlockUtil.isReplaceable(new BlockPos(px + ox, py - d, pz + oz)))
                        return false;
                }
            }
        }

        // Predictive — check along motion vector for next 3 ticks
        double motX = mc.thePlayer.motionX;
        double motZ = mc.thePlayer.motionZ;
        for (int tick = 1; tick <= 3; tick++) {
            for (double d = 0.25; d <= depth; d += 0.5) {
                if (!BlockUtil.isReplaceable(new BlockPos(px + motX * tick, py - d, pz + motZ * tick)))
                    return false;
            }
        }

        return true;
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            if (ItemUtil.isBlock(mc.thePlayer.inventory.getStackInSlot(i))) return i;
        }
        return -1;
    }

    // ── Find a block+face to place against, return placement data or null ──────
    private PlaceData findPlacement() {
        double px    = mc.thePlayer.posX;
        double py    = mc.thePlayer.posY;
        double pz    = mc.thePlayer.posZ;
        double reach = mc.playerController.getBlockReachDistance();

        int baseX = MathHelper.floor_double(px);
        int baseY = MathHelper.floor_double(py) - 1;
        int baseZ = MathHelper.floor_double(pz);

        for (int radius = 0; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    for (int dy = 0; dy >= -4; dy--) {
                        BlockPos target = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                        if (!BlockUtil.isReplaceable(target)) continue;
                        for (EnumFacing face : EnumFacing.VALUES) {
                            BlockPos neighbour = target.offset(face);
                            if (BlockUtil.isReplaceable(neighbour)) continue;
                            // Distance check from eyes to centre of neighbour block
                            double nx = neighbour.getX() + 0.5 - px;
                            double ny = neighbour.getY() + 0.5 - (py + mc.thePlayer.getEyeHeight());
                            double nz = neighbour.getZ() + 0.5 - pz;
                            if (nx*nx + ny*ny + nz*nz > reach * reach) continue;
                            return new PlaceData(neighbour, face.getOpposite());
                        }
                    }
                }
            }
        }
        return null;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (mc.thePlayer.onGround) {
            clutching = false;
            return;
        }

        if (mc.thePlayer.motionY >= 0 || !isOverVoid()) {
            clutching = false;
            return;
        }

        int blockSlot = findBlockSlot();
        if (blockSlot < 0) {
            clutching = false;
            return;
        }

        PlaceData pd = findPlacement();
        if (pd == null) {
            clutching = false;
            return;
        }

        clutching = true;

        // ── Compute exact rotation to the hit face ────────────────────────────
        Vec3 hitVec = BlockUtil.getClickVec(pd.block, pd.face);
        double relX = hitVec.xCoord - mc.thePlayer.posX;
        double relY = hitVec.yCoord - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
        double relZ = hitVec.zCoord - mc.thePlayer.posZ;

        // getRotationsTo returns [yaw, pitch] needed to look at this point
        float[] rots = RotationUtil.getRotationsTo(relX, relY, relZ,
                event.getYaw(), event.getPitch());

        // ── Raycast to confirm the rotation actually hits the target ──────────
        MovingObjectPosition mop = RotationUtil.rayTrace(rots[0], rots[1],
                mc.playerController.getBlockReachDistance(), 1.0f);

        // If the raycast doesn't land on our target block+face, find the best
        // hit vec offset that does
        if (mop == null || mop.typeOfHit != MovingObjectType.BLOCK
                || !mop.getBlockPos().equals(pd.block)
                || mop.sideHit != pd.face) {

            // Try centre of face as fallback
            hitVec = BlockUtil.getClickVec(pd.block, pd.face);
            relX = hitVec.xCoord - mc.thePlayer.posX;
            relY = hitVec.yCoord - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
            relZ = hitVec.zCoord - mc.thePlayer.posZ;
            rots = RotationUtil.getRotationsTo(relX, relY, relZ,
                    event.getYaw(), event.getPitch());

            mop = RotationUtil.rayTrace(rots[0], rots[1],
                    mc.playerController.getBlockReachDistance(), 1.0f);

            // If still can't hit it, skip this tick — don't place blind
            if (mop == null || mop.typeOfHit != MovingObjectType.BLOCK
                    || !mop.getBlockPos().equals(pd.block)) {
                return;
            }
            hitVec = mop.hitVec;
        } else {
            hitVec = mop.hitVec;
        }

        // ── Apply silent rotation — server-side only, camera doesn't move ─────
        event.setRotation(rots[0], rots[1], 2);

        // ── Place the block ───────────────────────────────────────────────────
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

        // Bridge: only nudge speed when player is pressing a movement key —
        // don't set speed from nothing as that causes Simulation flags
        if (bridge.getValue() && MoveUtil.isForwardPressed()) {
            double current = MoveUtil.getSpeed();
            double target  = MoveUtil.getBaseMoveSpeed() * speed.getValue();
            // Gently ease toward target speed rather than snapping
            if (current < target) {
                MoveUtil.setSpeed(current + Math.min(0.02, target - current), MoveUtil.getMoveYaw());
            }
        }
    }

    // SafeWalk only while actively clutching so we don't slide off placed blocks
    @EventTarget
    public void onSafeWalk(SafeWalkEvent event) {
        if (isEnabled() && clutching) event.setSafeWalk(true);
    }

    private static class PlaceData {
        final BlockPos   block;
        final EnumFacing face;
        PlaceData(BlockPos block, EnumFacing face) {
            this.block = block;
            this.face  = face;
        }
    }
}
