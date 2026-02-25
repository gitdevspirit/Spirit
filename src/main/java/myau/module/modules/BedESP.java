package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.RenderUtil;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.block.BlockObsidian;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArraySet;

public class BedESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final CopyOnWriteArraySet<BlockPos> beds = new CopyOnWriteArraySet<>();

    public final DropdownSetting mode        = new DropdownSetting("Mode",    0, "DEFAULT", "FULL");
    public final DropdownSetting colorMode   = new DropdownSetting("Color",   0, "CUSTOM", "HUD");
    // Custom color as R/G/B sliders (replaces ColorProperty)
    public final SliderSetting   colorR      = new SliderSetting("Color R",  64,  0, 255, 1);
    public final SliderSetting   colorG      = new SliderSetting("Color G",   0,  0, 255, 1);
    public final SliderSetting   colorB      = new SliderSetting("Color B", 255,  0, 255, 1);
    public final SliderSetting   opacity     = new SliderSetting("Opacity",  25,  0, 100, 1);
    public final BooleanSetting  outline     = new BooleanSetting("Outline",  false);
    public final BooleanSetting  obsidian    = new BooleanSetting("Obsidian", true);

    public BedESP() {
        super("BedESP", false);
        register(mode);
        register(colorMode);
        register(colorR);
        register(colorG);
        register(colorB);
        register(opacity);
        register(outline);
        register(obsidian);
    }

    public double getHeight() {
        return mode.getIndex() == 1 ? 1.0 : 0.5625;
    }

    private Color getColor() {
        switch (colorMode.getIndex()) {
            case 0:
                return new Color((int) colorR.getValue(), (int) colorG.getValue(), (int) colorB.getValue());
            case 1:
                return ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
            default:
                return new Color(-1);
        }
    }

    private void drawObsidianBox(AxisAlignedBB bb) {
        if (outline.getValue()) RenderUtil.drawBoundingBox(bb, 170, 0, 170, 255, 1.5F);
        RenderUtil.drawFilledBox(bb, 170, 0, 170);
    }

    private void drawObsidian(BlockPos blockPos) {
        if (outline.getValue()) RenderUtil.drawBlockBoundingBox(blockPos, 1.0, 170, 0, 170, 255, 1.5F);
        RenderUtil.drawBlockBox(blockPos, 1.0, 170, 0, 170);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled()) return;
        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();
        RenderUtil.enableRenderState();

        for (BlockPos blockPos : beds) {
            IBlockState state = mc.theWorld.getBlockState(blockPos);
            if (!(state.getBlock() instanceof BlockBed) || state.getValue(BlockBed.PART) != EnumPartType.HEAD) {
                beds.remove(blockPos);
                continue;
            }

            BlockPos opposite = blockPos.offset(state.getValue(BlockBed.FACING).getOpposite());
            IBlockState oppositeState = mc.theWorld.getBlockState(opposite);
            if (!(oppositeState.getBlock() instanceof BlockBed) || oppositeState.getValue(BlockBed.PART) != EnumPartType.FOOT) continue;

            if (obsidian.getValue()) {
                for (EnumFacing facing : Arrays.asList(EnumFacing.UP, EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST)) {
                    BlockPos offsetX = blockPos.offset(facing);
                    BlockPos offsetZ = opposite.offset(facing);
                    boolean xObs = mc.theWorld.getBlockState(offsetX).getBlock() instanceof BlockObsidian;
                    boolean zObs = mc.theWorld.getBlockState(offsetZ).getBlock() instanceof BlockObsidian;
                    if (xObs && zObs) {
                        drawObsidianBox(new AxisAlignedBB(
                                Math.min(offsetX.getX(), offsetZ.getX()),  offsetX.getY(), Math.min(offsetX.getZ(), offsetZ.getZ()),
                                Math.max((double)offsetX.getX()+1,(double)offsetZ.getX()+1), (double)offsetX.getY()+1,
                                Math.max((double)offsetX.getZ()+1,(double)offsetZ.getZ()+1))
                                .offset(-rm.getRenderPosX(), -rm.getRenderPosY(), -rm.getRenderPosZ()));
                    } else if (xObs) { drawObsidian(offsetX); }
                    else if (zObs)   { drawObsidian(offsetZ); }
                }
            }

            AxisAlignedBB aabb = new AxisAlignedBB(
                    Math.min(blockPos.getX(), opposite.getX()), blockPos.getY(), Math.min(blockPos.getZ(), opposite.getZ()),
                    Math.max((double)blockPos.getX()+1,(double)opposite.getX()+1), (double)blockPos.getY() + getHeight(),
                    Math.max((double)blockPos.getZ()+1,(double)opposite.getZ()+1))
                    .offset(-rm.getRenderPosX(), -rm.getRenderPosY(), -rm.getRenderPosZ());

            Color color = getColor();
            if (outline.getValue()) RenderUtil.drawBoundingBox(aabb, color.getRed(), color.getGreen(), color.getBlue(), 255, 1.5F);
            RenderUtil.drawFilledBox(aabb, color.getRed(), color.getGreen(), color.getBlue());
        }

        RenderUtil.disableRenderState();
    }

    @Override
    public void onEnabled() {
        if (mc.renderGlobal != null) mc.renderGlobal.loadRenderers();
    }
}