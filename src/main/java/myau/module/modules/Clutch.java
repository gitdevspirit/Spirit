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

public class Clutch extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting  triggerDepth = register(new SliderSetting("Trigger Depth", 5.0, 1.0, 20.0, 0.5));
    public final SliderSetting  speed        = register(new SliderSetting("Speed", 1.0, 0.5, 3.0, 0.05));
    public final BooleanSetting swing        = register(new BooleanSetting("Swing", true));

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

    private boolean isOverVoid() {
        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY;
        double pz = mc.thePlayer.posZ;
        double depth = triggerDepth.getValue();
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                for (double d = 0.25; d <= depth; d += 0.5) {
                    if (!BlockUtil.isReplaceable(new BlockPos(px + ox, py - d, pz + oz)))
                        return false;
                }
            }
        }
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

    private boolean placeFalling() {
        if (blockSlot < 0) return false;

        double px    = mc.thePlayer.posX;
        double py    = mc.thePlayer.posY;
        double pz    = mc.thePlayer.posZ;
        double reach = mc.playerController.getBlockReachDistance();

        int startY = MathHelper.floor_double(py) - 1;
        int endY   = MathHelper.floor_double(py - triggerDepth.getValue());

        for (int scanY = startY; scanY >= endY; scanY--) {
            int scanX = MathHelper.floor_double(px);
            int scanZ = MathHelper.floor_double(pz);

            // TOP face of block directly beneath this Y
            BlockPos beneath = new BlockPos(scanX, scanY - 1, scanZ);
            if (!BlockUtil.isReplaceable(beneath) && inReach(beneath, reach)) {
                if (doPlace(beneath, EnumFacing.UP)) return true;
            }

            // SIDE faces of horizontal neighbours at this Y
            for (EnumFacing face : new EnumFacing[]{
                    EnumFacing.NORTH, EnumFacing.SOUTH,
                    EnumFacing.EAST,  EnumFacing.WEST }) {
                BlockPos neighbour = new BlockPos(scanX, scanY, scanZ).offset(face);
                if (!BlockUtil.isReplaceable(neighbour) && inReach(neighbour, reach)) {
                    if (doPlace(neighbour, face.getOpposite())) return true;
                }
            }
        }
        return false;
    }

    private boolean inReach(BlockPos pos, double reach) {
        double dx = pos.getX() + 0.5 - mc.thePlayer.posX;
        double dy = pos.getY() + 0.5 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = pos.getZ() + 0.5 - mc.thePlayer.posZ;
        return dx*dx + dy*dy + dz*dz <= reach * reach;
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

    private float[] calcRotation(Vec3 eyes, Vec3 to) {
        double dx = to.xCoord - eyes.xCoord;
        double dy = to.yCoord - eyes.yCoord;
        double dz = to.zCoord - eyes.zCoord;
        float h   = (float) Math.sqrt(dx*dx + dz*dz);
        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = MathHelper.clamp_float((float) -Math.toDegrees(Math.atan2(dy, h)), -90f, 90f);
        return new float[]{ yaw, pitch };
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (mc.thePlayer.onGround) {
            clutching = false;
            blockSlot = -1;
            return;
        }

        if (mc.thePlayer.motionY >= 0 || !isOverVoid()) {
            clutching = false;
            return;
        }

        clutching = true;
        blockSlot = findBlockSlot();
        if (blockSlot < 0) return;

        // Silent rotation toward block below — server-side only
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 target = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY - 1.5, mc.thePlayer.posZ);
        float[] rots = calcRotation(eyes, target);
        event.setRotation(rots[0], rots[1], 2);

        placeFalling();

        // Speed — applies to bridging forward
        if (MoveUtil.isForwardPressed()) {
            MoveUtil.setSpeed(MoveUtil.getBaseMoveSpeed() * speed.getValue(), MoveUtil.getMoveYaw());
        }
    }
}
