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
    private static final DecimalFormat hpFmt = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));

    private final TimerUtil lastAttackTimer = new TimerUtil();
    private final TimerUtil animTimer       = new TimerUtil();
    private EntityLivingBase lastTarget  = null;
    private EntityLivingBase target      = null;
    private ResourceLocation headTexture = null;
    private float oldHealth = 0, newHealth = 0, maxHealth = 0;

    // ── Settings ──────────────────────────────────────────────────────────────
    public final DropdownSetting style       = register(new DropdownSetting("Style",      0, "Modern", "Minimal"));
    public final DropdownSetting color       = register(new DropdownSetting("Color",      0, "Default", "HUD"));
    public final DropdownSetting posX        = register(new DropdownSetting("Position X", 1, "Left", "Middle", "Right"));
    public final DropdownSetting posY        = register(new DropdownSetting("Position Y", 1, "Top", "Middle", "Bottom"));
    public final SliderSetting   scale       = register(new SliderSetting("Scale",    1.0, 0.5, 2.0, 0.05));
    public final SliderSetting   offX        = register(new SliderSetting("Offset X",   0, -300, 300, 1));
    public final SliderSetting   offY        = register(new SliderSetting("Offset Y",  40, -300, 300, 1));
    public final SliderSetting   bgAlpha     = register(new SliderSetting("BG Alpha",  85,    0, 100, 1));
    public final BooleanSetting  showHead    = register(new BooleanSetting("Show Head",   true));
    public final BooleanSetting  showArmor   = register(new BooleanSetting("Show Armor",  true));
    public final BooleanSetting  showPing    = register(new BooleanSetting("Show Ping",   true));
    public final BooleanSetting  showWL      = register(new BooleanSetting("Show W/L",    true));
    public final BooleanSetting  outline     = register(new BooleanSetting("Outline",     true));
    public final BooleanSetting  animations  = register(new BooleanSetting("Animations",  true));
    public final BooleanSetting  shadow      = register(new BooleanSetting("Shadow",      true));
    public final BooleanSetting  kaOnly      = register(new BooleanSetting("KA Only",     true));
    public final BooleanSetting  chatPreview = register(new BooleanSetting("Chat Preview",false));

    public TargetHUD() { super("TargetHUD", false, true); }

    public EntityLivingBase getTarget() { return target; }

    // ── Internal helpers ──────────────────────────────────────────────────────
    private EntityLivingBase resolveTarget() {
        if (!lastAttackTimer.hasTimeElapsed(1500L) && TeamUtil.isEntityLoaded(lastTarget))
            return lastTarget;
        return chatPreview.getValue() && mc.currentScreen instanceof GuiChat ? mc.thePlayer : null;
    }

    private ResourceLocation getSkin(EntityLivingBase e) {
        if (e instanceof EntityPlayer) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(e.getName());
            if (info != null) return info.getLocationSkin();
        }
        return null;
    }

    private int getPing(EntityLivingBase e) {
        if (e instanceof EntityPlayer) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(e.getName());
            if (info != null) return info.getResponseTime();
        }
        return -1;
    }

    private Color getAccentColor(EntityLivingBase e) {
        if (e instanceof EntityPlayer) {
            if (TeamUtil.isFriend((EntityPlayer) e)) return Myau.friendManager.getColor();
            if (TeamUtil.isTarget((EntityPlayer) e)) return Myau.targetManager.getColor();
        }
        if (color.getIndex() == 1)
            return new Color(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()).getRGB());
        if (e instanceof EntityPlayer)
            return TeamUtil.getTeamColor((EntityPlayer) e, 1.0f);
        return new Color(0xE991B8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;
        EntityLivingBase prev = target;
        target = resolveTarget();
        if (target == null) return;

        float abs   = target.getAbsorptionAmount() / 2.0f;
        float heal  = target.getHealth() / 2.0f + abs;
        float selfHp = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0f;

        if (target != prev) {
            headTexture = null; animTimer.setTime();
            oldHealth = heal; newHealth = heal;
        }
        if (!animations.getValue() || animTimer.hasTimeElapsed(150L)) {
            oldHealth = newHealth; newHealth = heal;
            maxHealth = target.getMaxHealth() / 2.0f;
            if (oldHealth != newHealth) animTimer.reset();
        }
        ResourceLocation skin = getSkin(target);
        if (skin != null) headTexture = skin;

        float elapsed     = (float) Math.min(Math.max(animTimer.getElapsedTime(), 0L), 150L);
        float healthRatio = Math.min(1, Math.max(0,
                RenderUtil.lerpFloat(newHealth, oldHealth, elapsed / 150f) / Math.max(0.001f, maxHealth)));
        Color accent    = getAccentColor(target);
        Color hpColor   = color.getIndex() == 0 ? ColorUtil.getHealthBlend(healthRatio) : accent;
        Color hpDark    = ColorUtil.darker(hpColor, 0.25f);

        boolean modern  = style.getIndex() == 0;
        float   headW   = showHead.getValue() && headTexture != null ? (modern ? 28f : 22f) : 0;
        float   armorH  = showArmor.getValue() && modern ? 12f : 0;

        // Calculate width from content
        String name    = ChatColors.formatColor("&r" + TeamUtil.stripName(target));
        String hpStr   = ChatColors.formatColor(String.format("&f%s%s❤", hpFmt.format(heal), abs > 0 ? "&6" : "&c"));
        int ping       = getPing(target);
        String pingStr = ping >= 0 ? ping + "ms" : "";
        String wlStr   = selfHp > heal ? "Winning" : selfHp < heal ? "Losing" : "Even";

        int nameW = mc.fontRendererObj.getStringWidth(name);
        int hpW   = mc.fontRendererObj.getStringWidth(hpStr);
        int pingW = showPing.getValue() && !pingStr.isEmpty() ? mc.fontRendererObj.getStringWidth(pingStr) + 4 : 0;
        int wlW   = showWL.getValue() ? mc.fontRendererObj.getStringWidth(wlStr) + 4 : 0;

        float rightExtra = Math.max(pingW, wlW);
        float contentW   = Math.max(nameW, hpW) + (rightExtra > 0 ? 4 + rightExtra : 0);
        float totalW     = headW + 6 + contentW + 6;
        if (modern) totalW = Math.max(totalW, headW + 80);
        float totalH     = modern ? 30 + armorH : 24;

        // Position
        ScaledResolution sr   = new ScaledResolution(mc);
        float scaleVal = (float) scale.getValue();
        float px = (float) offX.getValue() / scaleVal;
        float py = (float) offY.getValue() / scaleVal;
        switch (posX.getIndex()) {
            case 1: px += sr.getScaledWidth()  / scaleVal / 2f - totalW / 2f; break;
            case 2: px  = -px + sr.getScaledWidth() / scaleVal - totalW;      break;
        }
        switch (posY.getIndex()) {
            case 1: py += sr.getScaledHeight() / scaleVal / 2f - totalH / 2f; break;
            case 2: py  = -py + sr.getScaledHeight() / scaleVal - totalH;     break;
        }

        GlStateManager.pushMatrix();
        GlStateManager.scale(scaleVal, scaleVal, 1f);
        GlStateManager.translate(px, py, -450f);

        RenderUtil.enableRenderState();

        float bgA = (float) bgAlpha.getValue() / 100f;
        int   bg  = new Color(0.06f, 0.06f, 0.06f, bgA).getRGB();

        if (modern) {
            // ── Modern style ──────────────────────────────────────────────────
            // Main card
            RenderUtil.drawOutlineRect(0, 0, totalW, totalH, 1f,
                    bg, outline.getValue() ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 160).getRGB() : 0);

            // Left accent stripe
            RenderUtil.drawRect(0, 0, 2, totalH, accent.getRGB());

            // Health bar track + fill (bottom of card, above armor if shown)
            float barY0 = armorH > 0 ? totalH - armorH - 4 : totalH - 4;
            float barX0 = headW + 4, barX1 = totalW - 4;
            RenderUtil.drawRect(barX0, barY0, barX1, barY0 + 3, hpDark.getRGB());
            RenderUtil.drawRect(barX0, barY0, barX0 + healthRatio * (barX1 - barX0), barY0 + 3, hpColor.getRGB());

            // Armor row
            if (armorH > 0) {
                drawArmorRow(target, barX0, totalH - armorH + 1, barX1);
            }

        } else {
            // ── Minimal style ─────────────────────────────────────────────────
            RenderUtil.drawOutlineRect(0, 0, totalW, totalH, 1f,
                    bg, outline.getValue() ? accent.getRGB() : 0);
            float barY0 = totalH - 3;
            float barX0 = headW + 4, barX1 = totalW - 4;
            RenderUtil.drawRect(barX0, barY0, barX1, barY0 + 2, hpDark.getRGB());
            RenderUtil.drawRect(barX0, barY0, barX0 + healthRatio * (barX1 - barX0), barY0 + 2, hpColor.getRGB());
        }

        RenderUtil.disableRenderState();

        // ── Text ──────────────────────────────────────────────────────────────
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float textX = headW + 6, nameY = 4, hpY = nameY + 9;
        mc.fontRendererObj.drawString(name,  textX, nameY, -1, shadow.getValue());
        mc.fontRendererObj.drawString(hpStr, textX, hpY,   -1, shadow.getValue());

        if (showPing.getValue() && !pingStr.isEmpty()) {
            int pingColor = ping < 80 ? 0xFF55FF55 : ping < 150 ? 0xFFFFFF55 : 0xFFFF5555;
            mc.fontRendererObj.drawString(pingStr, totalW - 4 - mc.fontRendererObj.getStringWidth(pingStr), nameY,
                    pingColor, shadow.getValue());
        }
        if (showWL.getValue()) {
            int wlColor = selfHp > heal ? 0xFF55FF55 : selfHp < heal ? 0xFFFF5555 : 0xFFFFFFFF;
            mc.fontRendererObj.drawString(wlStr, totalW - 4 - mc.fontRendererObj.getStringWidth(wlStr), hpY,
                    wlColor, shadow.getValue());
        }

        // ── Head ──────────────────────────────────────────────────────────────
        if (showHead.getValue() && headTexture != null) {
            GlStateManager.color(1f, 1f, 1f);
            mc.getTextureManager().bindTexture(headTexture);
            float hs = headW - 4;
            Gui.drawScaledCustomSizeModalRect(2, 2, 8f,  8f, 8, 8, (int)hs, (int)hs, 64f, 64f);
            Gui.drawScaledCustomSizeModalRect(2, 2, 40f, 8f, 8, 8, (int)hs, (int)hs, 64f, 64f);
        }

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private void drawArmorRow(EntityLivingBase entity, float x, float y, float maxX) {
        // Slots: 3=helmet, 2=chest, 1=legs, 0=boots
        float slotSize = 10f;
        float spacing  = 1f;
        float startX   = x;
        for (int i = 3; i >= 0; i--) {
            ItemStack armor = entity.getCurrentArmor(i);
            if (armor == null) continue;
            if (startX + slotSize > maxX) break;
            GlStateManager.color(1f, 1f, 1f);
            mc.getRenderItem().renderItemAndEffectIntoGUI(armor, (int) startX, (int) y);
            startX += slotSize + spacing;
        }
        // Also render held item after armor
        ItemStack held = entity.getHeldItem();
        if (held != null && startX + slotSize <= maxX) {
            GlStateManager.color(1f, 1f, 1f);
            mc.getRenderItem().renderItemAndEffectIntoGUI(held, (int)(startX + 2), (int) y);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.SEND || !(event.getPacket() instanceof C02PacketUseEntity)) return;
        C02PacketUseEntity pkt = (C02PacketUseEntity) event.getPacket();
        if (pkt.getAction() != Action.ATTACK) return;
        Entity e = pkt.getEntityFromWorld(mc.theWorld);
        if (e instanceof EntityLivingBase && !(e instanceof EntityArmorStand)) {
            lastAttackTimer.reset();
            lastTarget = (EntityLivingBase) e;
        }
    }
}
