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
    public final BooleanSetting swing        = register(new BooleanSetting("Swing", true));

    public Clutch() {
        super("Clutch", false);
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
        double mx = mc.thePlayer.motionX;
        double mz = mc.thePlayer.motionZ;
        for (int t = 1; t <= 3; t++) {
            for (double d = 0.25; d <= depth; d += 0.5) {
                if (!BlockUtil.isReplaceable(new BlockPos(px + mx * t, py - d, pz + mz * t)))
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

    /**
     * Scans from directly below feet outward. For every air block at foot
     * level (or below) that has a solid neighbour within reach, place against
     * that neighbour. No raycast verification — just place every valid surface
     * found every tick. The column builds itself tick by tick.
     */
    private void placeBelow(int slot) {
        double px    = mc.thePlayer.posX;
        double py    = mc.thePlayer.posY;
        double pz    = mc.thePlayer.posZ;
        double reach = mc.playerController.getBlockReachDistance();
        Vec3   eyes  = mc.thePlayer.getPositionEyes(1.0f);

        int baseX = MathHelper.floor_double(px);
        int baseY = MathHelper.floor_double(py) - 1; // directly under feet
        int baseZ = MathHelper.floor_double(pz);

        for (int radius = 0; radius <= 1; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    for (int dy = 0; dy >= -3; dy--) {
                        BlockPos target = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                        if (!BlockUtil.isReplaceable(target)) continue;

                        for (EnumFacing face : EnumFacing.VALUES) {
                            BlockPos against = target.offset(face);
                            if (BlockUtil.isReplaceable(against)) continue;

                            // reach check from eyes
                            double ex = against.getX() + 0.5 - eyes.xCoord;
                            double ey = against.getY() + 0.5 - eyes.yCoord;
                            double ez = against.getZ() + 0.5 - eyes.zCoord;
                            if (ex*ex + ey*ey + ez*ez > reach * reach) continue;

                            Vec3 hitVec = BlockUtil.getClickVec(against, face.getOpposite());

                            int prev = mc.thePlayer.inventory.currentItem;
                            mc.thePlayer.inventory.currentItem = slot;
                            ItemStack held = mc.thePlayer.inventory.getCurrentItem();
                            boolean ok = mc.playerController.onPlayerRightClick(
                                    mc.thePlayer, mc.theWorld, held, against, face.getOpposite(), hitVec);
                            mc.thePlayer.inventory.currentItem = prev;

                            if (ok) {
                                if (swing.getValue()) mc.thePlayer.swingItem();
                                else PacketUtil.sendPacket(new C0APacketAnimation());
                                return; // one block per tick
                            }
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.thePlayer.onGround) return;
        if (mc.thePlayer.motionY >= 0) return;
        if (!isOverVoid()) return;

        int slot = findBlockSlot();
        if (slot < 0) return;

        // Silent rotation straight down so placement packet is accepted
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 foot  = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY - 1.0, mc.thePlayer.posZ);
        double dx = foot.xCoord - eyes.xCoord;
        double dy = foot.yCoord - eyes.yCoord;
        double dz = foot.zCoord - eyes.zCoord;
        float h   = (float) Math.sqrt(dx*dx + dz*dz);
        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = MathHelper.clamp_float((float) -Math.toDegrees(Math.atan2(dy, h)), -90f, 90f);
        event.setRotation(yaw, pitch, 2);

        placeBelow(slot);
    }
}
