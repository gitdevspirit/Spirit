package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.RenderUtil;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;

public class Trajectories extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting  opacity     = new SliderSetting("Opacity",    100,  0, 100, 1);
    public final BooleanSetting bow         = new BooleanSetting("Bow",       true);
    public final BooleanSetting projectiles = new BooleanSetting("Projectiles", false);
    public final BooleanSetting pearls      = new BooleanSetting("Pearls",    true);

    public Trajectories() {
        super("Trajectories", false, true);
        register(opacity);
        register(bow);
        register(projectiles);
        register(pearls);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || mc.thePlayer.getHeldItem() == null || mc.gameSettings.thirdPersonView != 0) return;

        Item item = mc.thePlayer.getHeldItem().getItem();
        RenderManager renderManager = mc.getRenderManager();
        boolean isBow = false;
        float velocityMultiplier = 1.5F;
        float drag = 0.99F;
        float gravity, hitboxExpand;

        if (item instanceof ItemBow && bow.getValue()) {
            if (!mc.thePlayer.isUsingItem()) return;
            isBow = true; gravity = 0.05F; hitboxExpand = 0.3F;
            float charge = (float) mc.thePlayer.getItemInUseDuration() / 20.0F;
            charge = (charge * charge + charge * 2.0F) / 3.0F;
            if (charge < 0.1F) return;
            if (charge > 1.0F) charge = 1.0F;
            velocityMultiplier = charge * 3.0F;
        } else if (item instanceof ItemFishingRod && projectiles.getValue()) {
            gravity = 0.04F; hitboxExpand = 0.25F; drag = 0.92F;
        } else if ((item instanceof ItemSnowball || item instanceof ItemEgg) && projectiles.getValue()) {
            gravity = 0.03F; hitboxExpand = 0.25F;
        } else if (item instanceof ItemEnderPearl && pearls.getValue()) {
            gravity = 0.03F; hitboxExpand = 0.25F;
        } else { return; }

        float yaw = mc.thePlayer.rotationYaw, pitch = mc.thePlayer.rotationPitch;
        IAccessorRenderManager rm = (IAccessorRenderManager) renderManager;
        double x = rm.getRenderPosX() - MathHelper.cos(yaw / 180.0F * (float)Math.PI) * 0.16;
        double y = rm.getRenderPosY() + mc.thePlayer.getEyeHeight() - 0.1F;
        double z = rm.getRenderPosZ() - MathHelper.sin(yaw / 180.0F * (float)Math.PI) * 0.16;
        double mult = isBow ? 1.0 : 0.4;
        double mx = -Math.sin(yaw/180.0F*(float)Math.PI) * Math.cos(pitch/180.0F*(float)Math.PI) * mult;
        double my = -Math.sin(pitch/180.0F*(float)Math.PI) * mult;
        double mz =  Math.cos(yaw/180.0F*(float)Math.PI) * Math.cos(pitch/180.0F*(float)Math.PI) * mult;
        float mag = MathHelper.sqrt_double(mx*mx + my*my + mz*mz);
        mx /= mag; my /= mag; mz /= mag;
        mx *= velocityMultiplier; my *= velocityMultiplier; mz *= velocityMultiplier;

        MovingObjectPosition mop = null;
        boolean hasHitBlock = false, hasHitEntity = false;
        WorldRenderer worldRenderer = Tessellator.getInstance().getWorldRenderer();
        ArrayList<Vec3> trajectoryPoints = new ArrayList<>();

        while (!hasHitBlock && y > 0.0) {
            Vec3 start = new Vec3(x, y, z), end = new Vec3(x+mx, y+my, z+mz);
            mop = mc.theWorld.rayTraceBlocks(start, end, false, true, false);
            if (mop != null) { hasHitBlock = true; end = new Vec3(mop.hitVec.xCoord, mop.hitVec.yCoord, mop.hitVec.zCoord); }

            AxisAlignedBB aabb = new AxisAlignedBB(x-hitboxExpand, y-hitboxExpand, z-hitboxExpand,
                    x+hitboxExpand, y+hitboxExpand, z+hitboxExpand).addCoord(mx,my,mz).expand(1,1,1);
            int minCX = MathHelper.floor_double((aabb.minX-2)/16.0), maxCX = MathHelper.floor_double((aabb.maxX+2)/16.0);
            int minCZ = MathHelper.floor_double((aabb.minZ-2)/16.0), maxCZ = MathHelper.floor_double((aabb.maxZ+2)/16.0);
            ArrayList<Entity> possible = new ArrayList<>();
            for (int cx = minCX; cx <= maxCX; cx++)
                for (int cz = minCZ; cz <= maxCZ; cz++)
                    mc.theWorld.getChunkFromChunkCoords(cx, cz).getEntitiesWithinAABBForEntity(mc.thePlayer, aabb, possible, null);
            for (Entity entity : possible) {
                if (entity.canBeCollidedWith() && entity != mc.thePlayer) {
                    AxisAlignedBB entityBox = entity.getEntityBoundingBox().expand(hitboxExpand, hitboxExpand, hitboxExpand);
                    MovingObjectPosition intercept = entityBox.calculateIntercept(new Vec3(x,y,z), new Vec3(x+mx,y+my,z+mz));
                    if (intercept != null) { hasHitEntity = true; hasHitBlock = true; mop = intercept; }
                }
            }
            x += mx; y += my; z += mz;
            if (mc.theWorld.getBlockState(new BlockPos(x,y,z)).getBlock().getMaterial() == Material.water) {
                mx *= 0.6; my *= 0.6; mz *= 0.6;
            } else { mx *= drag; my *= drag; mz *= drag; }
            my -= gravity;
            trajectoryPoints.add(new Vec3(x - rm.getRenderPosX(), y - rm.getRenderPosY(), z - rm.getRenderPosZ()));
        }

        if (trajectoryPoints.size() <= 1) return;

        int alpha = (int)((float) opacity.getValue() / 100.0F * 255.0F);
        int lineColor = new Color(hasHitEntity ? 85 : 255, 255, hasHitEntity ? 85 : 255, alpha).getRGB();

        RenderUtil.enableRenderState();
        RenderUtil.setColor(lineColor);
        GL11.glLineWidth(1.5F);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        worldRenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION);
        for (Vec3 v : trajectoryPoints) worldRenderer.pos(v.xCoord, v.yCoord, v.zCoord).endVertex();
        Tessellator.getInstance().draw();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x - rm.getRenderPosX(), y - rm.getRenderPosY(), z - rm.getRenderPosZ());
        if (mop != null) {
            switch (mop.sideHit.getAxis().ordinal()) {
                case 0: GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F); break;
                case 1: GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F); break;
            }
            RenderUtil.drawLine(-0.25F, -0.25F, 0.25F,  0.25F, 1.5F, lineColor);
            RenderUtil.drawLine(-0.25F,  0.25F, 0.25F, -0.25F, 1.5F, lineColor);
        }
        GlStateManager.popMatrix();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0F);
        GlStateManager.resetColor();
        RenderUtil.disableRenderState();
    }
}