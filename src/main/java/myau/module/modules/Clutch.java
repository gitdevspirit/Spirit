package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
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
    public final BooleanSetting swing        = register(new BooleanSetting("Swing",        true));

    private int placeCooldown = 0;

    public Clutch() {
        super("Clutch", false);
    }

    @Override
    public void onDisabled() {
        placeCooldown = 0;
    }

    private boolean isOverVoid() {
        double px    = mc.thePlayer.posX;
        double py    = mc.thePlayer.posY;
        double pz    = mc.thePlayer.posZ;
        double depth = triggerDepth.getValue();

        // 3x3 straight down
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                for (double d = 0.25; d <= depth; d += 0.5) {
                    if (!BlockUtil.isReplaceable(new BlockPos(px + ox, py - d, pz + oz)))
                        return false;
                }
            }
        }
        // Predictive along motion
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
                            BlockPos nb = target.offset(face);
                            if (BlockUtil.isReplaceable(nb)) continue;
                            double nx = nb.getX() + 0.5 - px;
                            double ny = nb.getY() + 0.5 - (py + mc.thePlayer.getEyeHeight());
                            double nz = nb.getZ() + 0.5 - pz;
                            if (nx*nx + ny*ny + nz*nz > reach * reach) continue;
                            return new PlaceData(nb, face.getOpposite());
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Computes yaw/pitch directly — bypasses RotationUtil.getRotationsTo
     * which has a ≤1° dead zone and random noise that causes raycasts to miss.
     */
    private float[] calcRotation(Vec3 from, Vec3 to) {
        double dx   = to.xCoord - from.xCoord;
        double dy   = to.yCoord - from.yCoord;
        double dz   = to.zCoord - from.zCoord;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float)(Math.toDegrees(Math.atan2(dz, dx))) - 90f;
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, horiz)));
        return new float[]{ yaw, MathHelper.clamp_float(pitch, -90f, 90f) };
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (mc.thePlayer.onGround) {
            placeCooldown = 0;
            return;
        }

        if (mc.thePlayer.motionY >= 0 || !isOverVoid()) return;

        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }

        int blockSlot = findBlockSlot();
        if (blockSlot < 0) return;

        PlaceData pd = findPlacement();
        if (pd == null) return;

        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);

        // Use centre of the placement face as the hit target
        Vec3 hitVec = BlockUtil.getClickVec(pd.block, pd.face);

        // Compute exact rotation directly — no dead zones, no noise
        float[] rots = calcRotation(eyes, hitVec);

        // Verify raycast hits our block — if not, skip this tick
        MovingObjectPosition mop = RotationUtil.rayTrace(rots[0], rots[1],
                mc.playerController.getBlockReachDistance(), 1.0f);

        if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK
                && mop.getBlockPos().equals(pd.block)) {
            // Use the actual raycast hit vec for maximum accuracy
            hitVec = mop.hitVec;
        } else {
            // Raycast missed — don't place blind, skip tick
            return;
        }

        // Silent server-side rotation — camera does NOT move
        event.setRotation(rots[0], rots[1], 2);

        // Switch slot, place, restore slot
        int prev = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = blockSlot;
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        boolean placed = mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, held, pd.block, pd.face, hitVec);
        mc.thePlayer.inventory.currentItem = prev;

        if (placed) {
            if (swing.getValue()) mc.thePlayer.swingItem();
            else PacketUtil.sendPacket(new C0APacketAnimation());
            placeCooldown = 2;
        }

        // Bridge: steer existing momentum toward look direction — never add speed
        if (bridge.getValue() && MoveUtil.isForwardPressed()) {
            double spd = MoveUtil.getSpeed();
            if (spd > 0.001) {
                MoveUtil.setSpeed(spd, MoveUtil.getMoveYaw());
            }
        }
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
