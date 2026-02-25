package myau.module.modules;

import myau.Myau;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class NameTags extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat healthFormatter = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));

    // Display
    public final SliderSetting   scale            = new SliderSetting("Scale",             1.0, 0.5, 2.0, 0.05);
    public final BooleanSetting  autoScale        = new BooleanSetting("Auto Scale",       true);
    public final SliderSetting   backgroundOpacity= new SliderSetting("Background",        25,  0, 100, 1);
    public final BooleanSetting  shadow           = new BooleanSetting("Shadow",           true);

    // Modes
    public final DropdownSetting distanceMode     = new DropdownSetting("Distance",        0, "NONE", "DEFAULT", "VAPE");
    public final DropdownSetting healthMode       = new DropdownSetting("Health",          2, "NONE", "HP", "HEARTS", "TAB");

    // Info layers
    public final BooleanSetting  armor            = new BooleanSetting("Armor",            true);
    public final BooleanSetting  effects          = new BooleanSetting("Effects",          true);

    // Target filters
    public final BooleanSetting  players          = new BooleanSetting("Players",          true);
    public final BooleanSetting  friends          = new BooleanSetting("Friends",          true);
    public final BooleanSetting  enemies          = new BooleanSetting("Enemies",          true);
    public final BooleanSetting  bosses           = new BooleanSetting("Bosses",           false);
    public final BooleanSetting  mobs             = new BooleanSetting("Mobs",             false);
    public final BooleanSetting  creepers         = new BooleanSetting("Creepers",         false);
    public final BooleanSetting  endermans        = new BooleanSetting("Endermen",         false);
    public final BooleanSetting  blazes           = new BooleanSetting("Blazes",           false);
    public final BooleanSetting  animals          = new BooleanSetting("Animals",          false);
    public final BooleanSetting  self             = new BooleanSetting("Self",             false);
    public final BooleanSetting  bots             = new BooleanSetting("Bots",             false);

    public NameTags() {
        super("NameTags", false);
        register(scale);
        register(autoScale);
        register(backgroundOpacity);
        register(shadow);
        register(distanceMode);
        register(healthMode);
        register(armor);
        register(effects);
        register(players);
        register(friends);
        register(enemies);
        register(bosses);
        register(mobs);
        register(creepers);
        register(endermans);
        register(blazes);
        register(animals);
        register(self);
        register(bots);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean shouldRenderTags(EntityLivingBase entity) {
        if (entity.deathTime > 0) return false;
        if (mc.getRenderViewEntity().getDistanceToEntity(entity) > 512.0F) return false;

        if (entity instanceof EntityPlayer) {
            if (entity != mc.thePlayer && entity != mc.getRenderViewEntity()) {
                if (TeamUtil.isBot((EntityPlayer) entity))    return bots.getValue();
                if (TeamUtil.isFriend((EntityPlayer) entity)) return friends.getValue();
                return TeamUtil.isTarget((EntityPlayer) entity) ? enemies.getValue() : players.getValue();
            } else {
                return self.getValue() && mc.gameSettings.thirdPersonView != 0;
            }
        }

        if (entity instanceof EntityDragon || entity instanceof EntityWither)
            return !entity.isInvisible() && bosses.getValue();

        if (!(entity instanceof EntityMob) && !(entity instanceof EntitySlime))
            return (entity instanceof EntityAnimal
                    || entity instanceof EntityBat
                    || entity instanceof EntitySquid
                    || entity instanceof EntityVillager) && animals.getValue();

        if (entity instanceof EntityCreeper)  return creepers.getValue();
        if (entity instanceof EntityEnderman) return endermans.getValue();
        if (entity instanceof EntityBlaze)    return blazes.getValue();
        return mobs.getValue();
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!isEnabled()) return;

        for (Entity entity : TeamUtil.getLoadedEntitiesSorted()) {
            if (!(entity instanceof EntityLivingBase)) continue;
            if (!shouldRenderTags((EntityLivingBase) entity)) continue;
            if (!entity.ignoreFrustumCheck && !RenderUtil.isInViewFrustum(entity.getEntityBoundingBox(), 10.0)) continue;

            String teamName = TeamUtil.stripName(entity);
            if (StringUtils.isBlank(EnumChatFormatting.getTextWithoutFormattingCodes(teamName))) continue;

            IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();
            double x = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, event.getPartialTicks()) - rm.getRenderPosX();
            double y = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, event.getPartialTicks()) - rm.getRenderPosY() + (double) entity.getEyeHeight();
            double z = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, event.getPartialTicks()) - rm.getRenderPosZ();
            double distance = mc.getRenderViewEntity().getDistanceToEntity(entity);

            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y + (entity.isSneaking() ? 0.225 : 0.4), z);
            GlStateManager.rotate(mc.getRenderManager().playerViewY * -1.0F, 0.0F, 1.0F, 0.0F);
            float view = mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F;
            GlStateManager.rotate(mc.getRenderManager().playerViewX, view, 0.0F, 0.0F);

            double tagScale = Math.pow(Math.min(Math.max(autoScale.getValue() ? distance : 0.0, 6.0), 128.0), 0.75)
                    * 0.0075 * scale.getValue();
            GlStateManager.scale(-tagScale, -tagScale, 1.0);

            // Distance text
            String distanceText = "";
            switch (distanceMode.getIndex()) {
                case 1: distanceText = String.format("&7%dm&r ", (int) distance); break;
                case 2: distanceText = String.format("&a[&f%d&a]&r ", (int) distance); break;
            }

            // Health text
            float health    = ((EntityLivingBase) entity).getHealth();
            float absorption= ((EntityLivingBase) entity).getAbsorptionAmount();
            float max       = ((EntityLivingBase) entity).getMaxHealth();
            float percent   = Math.min(Math.max((health + absorption) / max, 0.0F), 1.0F);
            String healText = "";
            switch (healthMode.getIndex()) {
                case 1:
                    healText = String.format(" %d%s", (int) health,
                            absorption > 0.0F ? String.format(" &6%d&r", (int) absorption) : "&r");
                    break;
                case 2:
                    healText = String.format(" %s%s",
                            healthFormatter.format((double) health / 2.0),
                            absorption > 0.0F ? String.format(" &6%s&r", healthFormatter.format((double) absorption / 2.0)) : "&r");
                    break;
                case 3:
                    if (entity instanceof EntityPlayer) {
                        Scoreboard sb = mc.theWorld.getScoreboard();
                        if (sb != null) {
                            ScoreObjective obj = sb.getObjectiveInDisplaySlot(2);
                            if (obj != null) {
                                Score score = sb.getValueFromObjective(entity.getName(), obj);
                                if (score != null) healText = String.format(" &e%d&r", score.getScorePoints());
                            }
                        }
                    }
                    break;
            }

            String color = ChatColors.formatColor(String.format("%s&f%s&r%s", distanceText, teamName, healText));
            int width = mc.fontRendererObj.getStringWidth(color);

            // Background
            if (backgroundOpacity.getValue() > 0) {
                Color bgColor = !entity.isSneaking() && !entity.isInvisible()
                        ? new Color(0.0F, 0.0F, 0.0F, (float) backgroundOpacity.getValue() / 100.0F)
                        : new Color(0.33F, 0.0F, 0.33F, (float) backgroundOpacity.getValue() / 100.0F);
                RenderUtil.enableRenderState();
                RenderUtil.drawRect(
                        (float)(-width) / 2.0F - 1.0F,
                        (float)(-mc.fontRendererObj.FONT_HEIGHT) - 1.0F,
                        (float) width / 2.0F + (shadow.getValue() ? 1.0F : 0.0F),
                        shadow.getValue() ? 0.0F : -1.0F,
                        bgColor.getRGB());
                RenderUtil.disableRenderState();
            }

            // Name text
            GlStateManager.disableDepth();
            mc.fontRendererObj.drawString(
                    color,
                    (float)(-width) / 2.0F,
                    (float)(-mc.fontRendererObj.FONT_HEIGHT),
                    ColorUtil.getHealthBlend(percent).getRGB(),
                    shadow.getValue());
            GlStateManager.enableDepth();

            // Player-specific: armor, effects, friend/enemy outline
            if (entity instanceof EntityPlayer) {
                int height = mc.fontRendererObj.FONT_HEIGHT + 2;

                if (armor.getValue()) {
                    ArrayList<ItemStack> renderingItems = new ArrayList<>();
                    for (int i = 4; i >= 0; i--) {
                        ItemStack itemStack = (i == 0)
                                ? ((EntityPlayer) entity).getHeldItem()
                                : ((EntityPlayer) entity).inventory.armorInventory[i - 1];
                        if (itemStack != null) renderingItems.add(itemStack);
                    }
                    if (!renderingItems.isEmpty()) {
                        int offset = renderingItems.size() * -8;
                        for (int i = 0; i < renderingItems.size(); i++) {
                            RenderUtil.renderItemInGUI(renderingItems.get(i), offset + i * 16, -height - 16);
                        }
                        height += 16;
                    }
                }

                if (effects.getValue()) {
                    List<PotionEffect> activeEffects = ((EntityPlayer) entity)
                            .getActivePotionEffects()
                            .stream()
                            .filter(e -> Potion.potionTypes[e.getPotionID()].hasStatusIcon())
                            .collect(Collectors.toList());
                    if (!activeEffects.isEmpty()) {
                        GlStateManager.pushMatrix();
                        GlStateManager.scale(0.5F, 0.5F, 1.0F);
                        int offset = activeEffects.size() * -9;
                        for (int i = 0; i < activeEffects.size(); i++) {
                            RenderUtil.renderPotionEffect(activeEffects.get(i), offset + i * 18, -(height * 2) - 18);
                        }
                        GlStateManager.popMatrix();
                    }
                }

                if (TeamUtil.isFriend((EntityPlayer) entity)) {
                    RenderUtil.enableRenderState();
                    float x1     = (float)(-width) / 2.0F - 1.0F;
                    float y1     = (float)(-mc.fontRendererObj.FONT_HEIGHT) - 1.0F;
                    float x2     = (float) width / 2.0F + 1.0F;
                    float y2     = shadow.getValue() ? 0.0F : -1.0F;
                    RenderUtil.drawOutlineRect(x1, y1, x2, y2, 1.5F, 0, Myau.friendManager.getColor().getRGB());
                    RenderUtil.disableRenderState();
                } else if (TeamUtil.isTarget((EntityPlayer) entity)) {
                    RenderUtil.enableRenderState();
                    float x1     = (float)(-width) / 2.0F - 1.0F;
                    float y1     = (float)(-mc.fontRendererObj.FONT_HEIGHT) - 1.0F;
                    float x2     = (float) width / 2.0F + 1.0F;
                    float y2     = shadow.getValue() ? 0.0F : -1.0F;
                    RenderUtil.drawOutlineRect(x1, y1, x2, y2, 1.5F, 0, Myau.targetManager.getColor().getRGB());
                    RenderUtil.disableRenderState();
                }
            }

            GlStateManager.popMatrix();
        }
    }
}