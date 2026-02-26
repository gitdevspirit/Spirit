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

import java.util.ArrayList;
import java.util.Comparator;

public class Clutch extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting  triggerDepth = register(new SliderSetting("Trigger Depth", 5.0, 1.0, 20.0, 0.5));
    public final BooleanSetting swing        = register(new BooleanSetting("Swing", true));

    public Clutch() {
        super("Clutch", false);
    }

    private boolean isOverVoid() {
        double px = mc.thePlayer.posX, py = mc.thePlayer.posY, pz = mc.thePlayer.posZ;
        double depth = triggerDepth.getValue();
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                for (double d = 0.25; d <= depth; d += 0.5) {
                    if (!BlockUtil.isReplaceable(new BlockPos(px + ox, py - d, pz + oz))) return false;
                }
            }
        }
        double mx = mc.thePlayer.motionX, mz = mc.thePlayer.motionZ;
        for (int t = 1; t <= 3; t++) {
            for (double d = 0.25; d <= depth; d += 0.5) {
                if (!BlockUtil.isReplaceable(new BlockPos(px + mx * t, py - d, pz + mz * t))) return false;
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
     * Scaffold-style placement: find all solid blocks near targetPos (foot-1),
     * sort by closeness to target, pick the best face to place against.
     * This works while bridging because targetPos follows the player's position.
     */
    private boolean place(int slot) {
        // Target = block position directly under feet (where floor should be)
        BlockPos targetPos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ));

        // If the floor already exists, nothing to do
        if (!BlockUtil.isReplaceable(targetPos)) return false;

        double reach = mc.playerController.getBlockReachDistance();

        // Collect all solid blocks in a 4x4 area that are within reach and
        // have at least one replaceable neighbour (so we can place against them)
        ArrayList<BlockPos> candidates = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 0; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos pos = targetPos.add(x, y, z);
                    if (BlockUtil.isReplaceable(pos)) continue;
                    if (BlockUtil.isInteractable(pos)) continue;
                    if (mc.thePlayer.getDistance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > reach) continue;

                    // Must have at least one replaceable neighbour face (excluding DOWN)
                    for (EnumFacing f : EnumFacing.VALUES) {
                        if (f == EnumFacing.DOWN) continue;
                        if (BlockUtil.isReplaceable(pos.offset(f))) {
                            candidates.add(pos);
                            break;
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) return false;

        // Sort by distance to targetPos — closest wins
        candidates.sort(Comparator.comparingDouble(
                p -> p.distanceSqToCenter(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5)));

        BlockPos best = candidates.get(0);

        // Find the best facing on that block toward targetPos
        EnumFacing bestFace = getBestFacing(best, targetPos);
        if (bestFace == null) return false;

        // Compute hit vec and rotation
        Vec3 hitVec = BlockUtil.getClickVec(best, bestFace);
        Vec3 eyes   = mc.thePlayer.getPositionEyes(1.0f);
        double dx = hitVec.xCoord - eyes.xCoord;
        double dy = hitVec.yCoord - eyes.yCoord;
        double dz = hitVec.zCoord - eyes.zCoord;
        float h   = (float) Math.sqrt(dx*dx + dz*dz);
        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = MathHelper.clamp_float((float) -Math.toDegrees(Math.atan2(dy, h)), -90f, 90f);

        // The UpdateEvent rotation must be set BEFORE playerController.onPlayerRightClick
        // We can't set it here since we're already inside onUpdate — so we store and apply
        storedYaw   = yaw;
        storedPitch = pitch;
        hasRotation = true;

        int prev = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        boolean ok = mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, held, best, bestFace, hitVec);
        mc.thePlayer.inventory.currentItem = prev;

        if (ok) {
            if (swing.getValue()) mc.thePlayer.swingItem();
            else PacketUtil.sendPacket(new C0APacketAnimation());
        }
        return ok;
    }

    // Best face from a solid block toward the target air position
    private EnumFacing getBestFacing(BlockPos solid, BlockPos target) {
        EnumFacing best = null;
        double bestDist = Double.MAX_VALUE;
        for (EnumFacing f : EnumFacing.VALUES) {
            if (f == EnumFacing.DOWN) continue;
            BlockPos neighbour = solid.offset(f);
            if (!BlockUtil.isReplaceable(neighbour)) continue;
            if (neighbour.getY() > target.getY()) continue;
            double dist = neighbour.distanceSqToCenter(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            if (dist < bestDist || (dist == bestDist && f == EnumFacing.UP)) {
                bestDist = dist;
                best = f;
            }
        }
        return best;
    }

    // Rotation computed in place(), applied in onUpdate before placement
    private float   storedYaw   = 0;
    private float   storedPitch = 0;
    private boolean hasRotation = false;

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.thePlayer.onGround) return;
        if (mc.thePlayer.motionY >= 0) return;
        if (!isOverVoid()) return;

        int slot = findBlockSlot();
        if (slot < 0) return;

        hasRotation = false;

        // Pre-aim downward so the placement packet is accepted — will be
        // overwritten with precise rotation inside place() if a block is found
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 foot  = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY - 1.0, mc.thePlayer.posZ);
        double dx = foot.xCoord - eyes.xCoord;
        double dy = foot.yCoord - eyes.yCoord;
        double dz = foot.zCoord - eyes.zCoord;
        float h   = (float) Math.sqrt(dx*dx + dz*dz);
        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = MathHelper.clamp_float((float) -Math.toDegrees(Math.atan2(dy, h)), -90f, 90f);
        event.setRotation(yaw, pitch, 2);

        place(slot);

        // If place() found a better rotation, apply it
        if (hasRotation) {
            event.setRotation(storedYaw, storedPitch, 2);
        }
    }
}
