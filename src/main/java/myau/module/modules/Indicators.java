package myau.module.modules;

import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.awt.*;
import java.util.stream.Collectors;

public class Indicators extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting  scale          = new SliderSetting("Scale",           1.0, 0.5, 1.5, 0.05);
    public final SliderSetting  offset         = new SliderSetting("Offset",          50.0,  0, 255, 1.0);
    public final BooleanSetting directionCheck = new BooleanSetting("Direction Check", true);
    public final BooleanSetting fireballs      = new BooleanSetting("Fireballs",       true);
    public final BooleanSetting pearls         = new BooleanSetting("Pearls",          true);
    public final BooleanSetting arrows         = new BooleanSetting("Arrows",          true);
    public final BooleanSetting egg            = new BooleanSetting("Egg",             true);
    public final BooleanSetting snowball       = new BooleanSetting("Snowball",        true);

    public Indicators() {
        super("Indicators", false, true);
        register(scale);
        register(offset);
        register(directionCheck);
        register(fireballs);
        register(pearls);
        register(arrows);
        register(egg);
        register(snowball);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean shouldRender(Entity entity) {
        double d = (entity.posX - entity.lastTickPosX) * (mc.thePlayer.posX - entity.posX)
                 + (entity.posY - entity.lastTickPosY) * (mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - entity.posY - entity.height / 2.0)
                 + (entity.posZ - entity.lastTickPosZ) * (mc.thePlayer.posZ - entity.posZ);
        if (d == 0.0) return false;
        if (d < 0.0 && directionCheck.getValue()) return false;
        if (fireballs.getValue() && entity instanceof EntityFireball)  return true;
        if (pearls.getValue()    && entity instanceof EntityEnderPearl) return true;
        if (arrows.getValue()    && entity instanceof EntityArrow)      return true;
        if (egg.getValue()       && entity instanceof EntityEgg)        return true;
        if (snowball.getValue()  && entity instanceof EntitySnowball)   return true;
        return false;
    }

    private Item getIndicatorItem(Entity entity) {
        if (entity instanceof EntityFireball)  return Items.fire_charge;
        if (entity instanceof EntityEnderPearl) return Items.ender_pearl;
        if (entity instanceof EntityArrow)     return Items.arrow;
        if (entity instanceof EntityEgg)       return Items.egg;
        if (entity instanceof EntitySnowball)  return Items.snowball;
        return new Item();
    }

    private Color getIndicatorColor(Entity entity) {
        if (entity instanceof EntityFireball)  return new Color(12676363);
        if (entity instanceof EntityEnderPearl) return new Color(2458740);
        if (entity instanceof EntityArrow)     return new Color(0x969696);
        return new Color(-1);
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!isEnabled()) return;

        float scaleVal  = (float) scale.getValue();
        float offsetVal = 10.0f + (float) offset.getValue();
        ScaledResolution sr = new ScaledResolution(mc);

        for (Entity entity : TeamUtil.getLoadedEntitiesSorted().stream()
                .filter(this::shouldRender)
                .collect(Collectors.toList())) {

            float yawBetween = RotationUtil.getYawBetween(
                    RenderUtil.lerpDouble(mc.thePlayer.posX, mc.thePlayer.prevPosX, event.getPartialTicks()),
                    RenderUtil.lerpDouble(mc.thePlayer.posZ, mc.thePlayer.prevPosZ, event.getPartialTicks()),
                    RenderUtil.lerpDouble(entity.posX, entity.prevPosX, event.getPartialTicks()),
                    RenderUtil.lerpDouble(entity.posZ, entity.prevPosZ, event.getPartialTicks()));
            if (mc.gameSettings.thirdPersonView == 2) yawBetween += 180.0f;

            float x = (float)  Math.sin(Math.toRadians(yawBetween));
            float z = (float) (Math.cos(Math.toRadians(yawBetween)) * -1.0f);

            GlStateManager.pushMatrix();
            GlStateManager.disableDepth();
            GlStateManager.scale(scaleVal, scaleVal, 0.0f);
            GlStateManager.translate(
                    (float) sr.getScaledWidth()  / 2.0f / scaleVal,
                    (float) sr.getScaledHeight() / 2.0f / scaleVal, 0.0f);

            // Item icon
            GlStateManager.pushMatrix();
            GlStateManager.translate((offsetVal) * x - 8.0f, (offsetVal) * z - 8.0f, -300.0f);
            mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(getIndicatorItem(entity)), 0, 0);
            GlStateManager.popMatrix();

            // Distance label
            String dist = String.format("%dm", (int) mc.thePlayer.getDistanceToEntity(entity));
            GlStateManager.pushMatrix();
            GlStateManager.translate(
                    offsetVal * x - (float) mc.fontRendererObj.getStringWidth(dist) / 2.0f + 1.0f,
                    offsetVal * z + 1.0f, -100.0f);
            mc.fontRendererObj.drawStringWithShadow(dist, 0.0f, 0.0f,
                    ChatColors.GRAY.toAwtColor() & 0xFFFFFF | 0xBF000000);
            GlStateManager.popMatrix();

            // Arrow
            GlStateManager.pushMatrix();
            GlStateManager.translate((offsetVal + 15.0f) * x + 1.0f, (offsetVal + 15.0f) * z + 1.0f, -100.0f);
            RenderUtil.enableRenderState();
            RenderUtil.drawArrow(0.0f, 0.0f, (float)(Math.atan2(z, x) + Math.PI),
                    7.5f, 1.5f, getIndicatorColor(entity).getRGB());
            RenderUtil.disableRenderState();
            GlStateManager.popMatrix();

            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    }
}