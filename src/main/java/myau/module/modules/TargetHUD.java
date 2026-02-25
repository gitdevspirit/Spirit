package myau.module.modules;

import myau.Myau;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class TargetHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat healthFormat = new DecimalFormat("0.0",    new DecimalFormatSymbols(Locale.US));
    private static final DecimalFormat diffFormat   = new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));

    private final TimerUtil lastAttackTimer = new TimerUtil();
    private final TimerUtil animTimer       = new TimerUtil();
    private EntityLivingBase lastTarget  = null;
    private EntityLivingBase target      = null;
    private ResourceLocation headTexture = null;
    private float oldHealth = 0.0F, newHealth = 0.0F, maxHealth = 0.0F;

    public final DropdownSetting color        = new DropdownSetting("Color",        0, "DEFAULT", "HUD");
    public final DropdownSetting posX         = new DropdownSetting("Position X",   1, "LEFT", "MIDDLE", "RIGHT");
    public final DropdownSetting posY         = new DropdownSetting("Position Y",   1, "TOP", "MIDDLE", "BOTTOM");
    public final SliderSetting   scale        = new SliderSetting("Scale",        1.0,  0.5, 1.5, 0.05);
    public final SliderSetting   offX         = new SliderSetting("Offset X",       0, -255, 255, 1);
    public final SliderSetting   offY         = new SliderSetting("Offset Y",      40, -255, 255, 1);
    public final SliderSetting   background   = new SliderSetting("Background",    25,    0, 100, 1);
    public final BooleanSetting  head         = new BooleanSetting("Head",          true);
    public final BooleanSetting  indicator    = new BooleanSetting("Indicator",     true);
    public final BooleanSetting  outline      = new BooleanSetting("Outline",       false);
    public final BooleanSetting  animations   = new BooleanSetting("Animations",    true);
    public final BooleanSetting  shadow       = new BooleanSetting("Shadow",        true);
    public final BooleanSetting  kaOnly       = new BooleanSetting("KA Only",       true);
    public final BooleanSetting  chatPreview  = new BooleanSetting("Chat Preview",  false);
    public final BooleanSetting  trackTarget  = new BooleanSetting("Track Target",  false);
    public final DropdownSetting trackingMode = new DropdownSetting("Tracking Mode", 0, "TOP", "MIDDLE", "LEFT", "RIGHT");
    public final BooleanSetting  distanceScale= new BooleanSetting("Distance Scale", true);

    public TargetHUD() {
        super("TargetHUD", false, true);
        register(color); register(posX); register(posY);
        register(scale); register(offX); register(offY); register(background);
        register(head); register(indicator); register(outline);
        register(animations); register(shadow); register(kaOnly);
        register(chatPreview); register(trackTarget); register(trackingMode); register(distanceScale);
    }

    public EntityLivingBase getTarget() { return target; }

    private EntityLivingBase resolveTarget() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (killAura.isEnabled() && killAura.isAttackAllowed() && TeamUtil.isEntityLoaded(killAura.getTarget()))
            return killAura.getTarget();
        if (!kaOnly.getValue() && !lastAttackTimer.hasTimeElapsed(1500L) && TeamUtil.isEntityLoaded(lastTarget))
            return lastTarget;
        return chatPreview.getValue() && mc.currentScreen instanceof GuiChat ? mc.thePlayer : null;
    }

    private ResourceLocation getSkin(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(entity.getName());
            if (info != null) return info.getLocationSkin();
        }
        return null;
    }

    private Color getTargetColor(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            if (TeamUtil.isFriend((EntityPlayer) entity)) return Myau.friendManager.getColor();
            if (TeamUtil.isTarget((EntityPlayer) entity)) return Myau.targetManager.getColor();
        }
        switch (color.getIndex()) {
            case 0: return (entity instanceof EntityPlayer)
                    ? TeamUtil.getTeamColor((EntityPlayer) entity, 1.0F) : new Color(-1);
            case 1: return new Color(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()).getRGB());
            default: return new Color(-1);
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;
        EntityLivingBase prev = target;
        target = resolveTarget();
        if (target == null) return;

        float abs  = target.getAbsorptionAmount() / 2.0F;
        float heal = target.getHealth() / 2.0F + abs;
        float health = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F;

        if (target != prev) {
            headTexture = null;
            animTimer.setTime();
            oldHealth = heal; newHealth = heal;
        }
        if (!animations.getValue() || animTimer.hasTimeElapsed(150L)) {
            oldHealth = newHealth; newHealth = heal;
            maxHealth = target.getMaxHealth() / 2.0F;
            if (oldHealth != newHealth) animTimer.reset();
        }
        ResourceLocation skin = getSkin(target);
        if (skin != null) headTexture = skin;

        float elapsed     = (float) Math.min(Math.max(animTimer.getElapsedTime(), 0L), 150L);
        float healthRatio = Math.min(Math.max(
                RenderUtil.lerpFloat(newHealth, oldHealth, elapsed / 150.0F) / maxHealth, 0.0F), 1.0F);
        Color targetColor      = getTargetColor(target);
        Color healthBarColor   = color.getIndex() == 0 ? ColorUtil.getHealthBlend(healthRatio) : targetColor;
        float hdRatio          = Math.min(Math.max((health - heal + 1.0F) / 2.0F, 0.0F), 1.0F);
        Color healthDeltaColor = ColorUtil.getHealthBlend(hdRatio);

        ScaledResolution sr = new ScaledResolution(mc);
        String targetNameText = ChatColors.formatColor(String.format("&r%s&r", TeamUtil.stripName(target)));
        String healthText     = ChatColors.formatColor(String.format("&r&f%s%s❤&r",
                healthFormat.format(heal), abs > 0.0F ? "&6" : "&c"));
        String statusText     = ChatColors.formatColor(String.format("&r&l%s&r",
                heal == health ? "D" : (heal < health ? "W" : "L")));
        String healthDiffText = ChatColors.formatColor(String.format("&r%s&r",
                heal == health ? "0.0" : diffFormat.format(health - heal)));

        int targetNameWidth = mc.fontRendererObj.getStringWidth(targetNameText);
        int healthTextWidth = mc.fontRendererObj.getStringWidth(healthText);
        int statusTextWidth = mc.fontRendererObj.getStringWidth(statusText);
        int healthDiffWidth = mc.fontRendererObj.getStringWidth(healthDiffText);

        float barContentWidth = Math.max(
                targetNameWidth  + (indicator.getValue() ? 2.0F + statusTextWidth  + 2.0F : 0.0F),
                healthTextWidth  + (indicator.getValue() ? 2.0F + healthDiffWidth   + 2.0F : 0.0F));
        float headIconOffset = head.getValue() && headTexture != null ? 25.0F : 0.0F;
        float barTotalWidth  = Math.max(headIconOffset + 70.0F, headIconOffset + 2.0F + barContentWidth + 2.0F);

        float scaleVal = (float) scale.getValue();
        float px = (float) offX.getValue() / scaleVal;
        switch (posX.getIndex()) {
            case 1: px += sr.getScaledWidth()  / scaleVal / 2.0F - barTotalWidth / 2.0F; break;
            case 2: px = -px + sr.getScaledWidth() / scaleVal - barTotalWidth;            break;
        }
        float py = (float) offY.getValue() / scaleVal;
        switch (posY.getIndex()) {
            case 1: py += sr.getScaledHeight() / scaleVal / 2.0F - 13.5F; break;
            case 2: py = -py + sr.getScaledHeight() / scaleVal - 27.0F;    break;
        }

        GlStateManager.pushMatrix();
        GlStateManager.scale(scaleVal, scaleVal, 0.0F);
        GlStateManager.translate(px, py, -450.0F);
        RenderUtil.enableRenderState();
        int bgColor      = new Color(0.0F, 0.0F, 0.0F, (float) background.getValue() / 100.0F).getRGB();
        int outlineColor = outline.getValue() ? targetColor.getRGB() : new Color(0, 0, 0, 0).getRGB();
        RenderUtil.drawOutlineRect(0.0F, 0.0F, barTotalWidth, 27.0F, 1.5F, bgColor, outlineColor);
        RenderUtil.drawRect(headIconOffset + 2.0F, 22.0F, barTotalWidth - 2.0F, 25.0F,
                ColorUtil.darker(healthBarColor, 0.2F).getRGB());
        RenderUtil.drawRect(headIconOffset + 2.0F, 22.0F,
                headIconOffset + 2.0F + healthRatio * (barTotalWidth - 2.0F - headIconOffset - 2.0F), 25.0F,
                healthBarColor.getRGB());
        RenderUtil.disableRenderState();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        mc.fontRendererObj.drawString(targetNameText, headIconOffset + 2.0F, 2.0F,  -1, shadow.getValue());
        mc.fontRendererObj.drawString(healthText,     headIconOffset + 2.0F, 12.0F, -1, shadow.getValue());
        if (indicator.getValue()) {
            mc.fontRendererObj.drawString(statusText,    barTotalWidth - 2.0F - statusTextWidth, 2.0F,
                    healthDeltaColor.getRGB(), shadow.getValue());
            mc.fontRendererObj.drawString(healthDiffText, barTotalWidth - 2.0F - healthDiffWidth, 12.0F,
                    ColorUtil.darker(healthDeltaColor, 0.8F).getRGB(), shadow.getValue());
        }
        if (head.getValue() && headTexture != null) {
            GlStateManager.color(1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(headTexture);
            Gui.drawScaledCustomSizeModalRect(2, 2, 8.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect(2, 2, 40.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F);
        }
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private float damage(ItemStack stack, Entity attacker, Entity target) {
        float base = 1.0f;
        if (stack != null) {
            Item item = stack.getItem();
            int sharpness = EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack);
            if (item instanceof ItemSword)     base = 4.0f;
            else if (item instanceof ItemTool) base = 3.0f;
            base += sharpness * 1.25f;
        }
        if (attacker instanceof EntityLivingBase) {
            EntityLivingBase la = (EntityLivingBase) attacker;
            int strength = la.getActivePotionEffect(Potion.damageBoost) != null
                    ? la.getActivePotionEffect(Potion.damageBoost).getAmplifier() + 1 : 0;
            base += strength * 3.0f;
        }
        float total = base;
        if (target instanceof EntityLivingBase) {
            EntityLivingBase lt = (EntityLivingBase) target;
            int prot = 0;
            for (int i = 0; i < 4; i++)
                prot += EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, lt.getCurrentArmor(i));
            total *= 1 - lt.getTotalArmorValue() * 0.04f;
            total *= 1 - prot * 0.04f;
            int resistance = lt.getActivePotionEffect(Potion.resistance) != null
                    ? lt.getActivePotionEffect(Potion.resistance).getAmplifier() + 1 : 0;
            total *= 1 - resistance * 0.2f;
        }
        return total;
    }

    private float calculateWinning() {
        if (target == null || mc.thePlayer == null) return 0f;
        float playerHP = mc.thePlayer.getHealth(), targetHP = target.getHealth();
        while (playerHP > 0 && targetHP > 0) {
            targetHP -= damage(mc.thePlayer.getHeldItem(), mc.thePlayer, target);
            playerHP -= damage(target.getHeldItem(), target, mc.thePlayer);
        }
        return playerHP - targetHP;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.SEND || !(event.getPacket() instanceof C02PacketUseEntity)) return;
        C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
        if (packet.getAction() != Action.ATTACK) return;
        Entity entity = packet.getEntityFromWorld(mc.theWorld);
        if (entity instanceof EntityLivingBase && !(entity instanceof EntityArmorStand)) {
            lastAttackTimer.reset();
            lastTarget = (EntityLivingBase) entity;
        }
    }
}