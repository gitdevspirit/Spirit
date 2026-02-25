package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.mixin.IAccessorMinecraft;
import myau.mixin.IAccessorRenderManager;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.stream.Collectors;

public class ChestESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // ColorProperty workaround: R/G/B sliders per chest type
    public final SliderSetting  chestR        = new SliderSetting("Chest R",        255, 0, 255, 1);
    public final SliderSetting  chestG        = new SliderSetting("Chest G",        170, 0, 255, 1);
    public final SliderSetting  chestB        = new SliderSetting("Chest B",          0, 0, 255, 1);
    public final SliderSetting  trappedR      = new SliderSetting("Trapped R",      255, 0, 255, 1);
    public final SliderSetting  trappedG      = new SliderSetting("Trapped G",       43, 0, 255, 1);
    public final SliderSetting  trappedB      = new SliderSetting("Trapped B",        0, 0, 255, 1);
    public final SliderSetting  enderR        = new SliderSetting("Ender R",         26, 0, 255, 1);
    public final SliderSetting  enderG        = new SliderSetting("Ender G",         17, 0, 255, 1);
    public final SliderSetting  enderB        = new SliderSetting("Ender B",          0, 0, 255, 1);
    public final BooleanSetting tracers       = new BooleanSetting("Tracers",       false);

    public ChestESP() {
        super("ChestESP", false);
        register(chestR); register(chestG); register(chestB);
        register(trappedR); register(trappedG); register(trappedB);
        register(enderR); register(enderG); register(enderB);
        register(tracers);
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!isEnabled()) return;
        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();
        RenderUtil.enableRenderState();

        for (TileEntity chest : mc.theWorld.loadedTileEntityList.stream()
                .filter(te -> te instanceof TileEntityChest || te instanceof TileEntityEnderChest)
                .collect(Collectors.toList())) {

            Block block = mc.theWorld.getBlockState(chest.getPos()).getBlock();
            double minX = 0.0625, minZ = 0.0625, maxX = 0.9375, maxZ = 0.9375;
            int r, g, b;

            if (block instanceof BlockChest) {
                if (block.canProvidePower()) {
                    r = (int) trappedR.getValue(); g = (int) trappedG.getValue(); b = (int) trappedB.getValue();
                } else {
                    r = (int) chestR.getValue(); g = (int) chestG.getValue(); b = (int) chestB.getValue();
                }
                EnumFacing facing = mc.theWorld.getBlockState(chest.getPos()).getValue(BlockChest.FACING);
                switch (facing) {
                    case NORTH:
                        if (mc.theWorld.getBlockState(chest.getPos().east()).getBlock() == block) continue;
                        else if (mc.theWorld.getBlockState(chest.getPos().west()).getBlock() == block) minX -= 1;
                        break;
                    case SOUTH:
                        if (mc.theWorld.getBlockState(chest.getPos().west()).getBlock() == block) continue;
                        else if (mc.theWorld.getBlockState(chest.getPos().east()).getBlock() == block) maxX += 1;
                        break;
                    case WEST:
                        if (mc.theWorld.getBlockState(chest.getPos().north()).getBlock() == block) continue;
                        else if (mc.theWorld.getBlockState(chest.getPos().south()).getBlock() == block) maxZ += 1;
                        break;
                    case EAST:
                        if (mc.theWorld.getBlockState(chest.getPos().south()).getBlock() == block) continue;
                        else if (mc.theWorld.getBlockState(chest.getPos().north()).getBlock() == block) minZ -= 1;
                        break;
                    default: continue;
                }
            } else {
                r = (int) enderR.getValue(); g = (int) enderG.getValue(); b = (int) enderB.getValue();
            }

            AxisAlignedBB aabb = new AxisAlignedBB(
                    chest.getPos().getX() + minX, chest.getPos().getY() + 0.0, chest.getPos().getZ() + minZ,
                    chest.getPos().getX() + maxX, chest.getPos().getY() + 0.875, chest.getPos().getZ() + maxZ)
                    .offset(-rm.getRenderPosX(), -rm.getRenderPosY(), -rm.getRenderPosZ());

            RenderUtil.drawBoundingBox(aabb, r, g, b, 255, 1.5F);

            if (tracers.getValue()) {
                Vec3 vec;
                if (mc.gameSettings.thirdPersonView == 0) {
                    vec = new Vec3(0.0, 0.0, 1.0)
                            .rotatePitch((float) -Math.toRadians(RenderUtil.lerpFloat(mc.getRenderViewEntity().rotationPitch, mc.getRenderViewEntity().prevRotationPitch, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)))
                            .rotateYaw((float) -Math.toRadians(RenderUtil.lerpFloat(mc.getRenderViewEntity().rotationYaw, mc.getRenderViewEntity().prevRotationYaw, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)));
                } else {
                    vec = new Vec3(0.0, 0.0, 0.0)
                            .rotatePitch((float) -Math.toRadians(RenderUtil.lerpFloat(mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)))
                            .rotateYaw((float) -Math.toRadians(RenderUtil.lerpFloat(mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)));
                }
                vec = new Vec3(vec.xCoord, vec.yCoord + mc.getRenderViewEntity().getEyeHeight(), vec.zCoord);
                float opacity = (float) ((Tracers) Myau.moduleManager.modules.get(Tracers.class)).opacity.getValue() / 100.0F;
                RenderUtil.drawLine3D(vec,
                        chest.getPos().getX() + 0.5, chest.getPos().getY() + 0.5, chest.getPos().getZ() + 0.5,
                        r / 255.0F, g / 255.0F, b / 255.0F, opacity, 1.5F);
            }
        }
        RenderUtil.disableRenderState();
    }
}