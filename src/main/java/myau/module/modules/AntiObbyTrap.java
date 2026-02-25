package myau.module.modules;

import myau.module.BooleanSetting;
import myau.module.Module;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class AntiObbyTrap extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final BooleanSetting setAir = new BooleanSetting("Set Air", true);

    public AntiObbyTrap() {
        super("AntiObbyTrap", false);
        register(setAir);
    }

    public boolean isInsideBlock(World world, BlockPos blockPos) {
        IBlockState blockState = world.getBlockState(blockPos);
        Block block = blockState.getBlock();
        if (block.getMaterial().isSolid() && block.isFullBlock()) {
            Vec3 hitVec = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
            return block.getCollisionBoundingBox(mc.theWorld, blockPos, blockState).isVecInside(hitVec);
        }
        return false;
    }
}
