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
import myau.ui.clickgui.RoundedUtils;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class TargetHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat hpFmt = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));

    // ── Design ────────────────────────────────────────────────────────────────
    //   Card:  [HEAD] Name
    //                 [====bar====] 10.0
    private static final int   ACCENT     = 0xFFE991B8;  // pink
    private static final int   BAR_TRACK  = 0xFF1E1E1E;
    private static final int   BG         = 0xCC0A0A0A;
    private static final float CARD_R     = 6f;
    private static final float HEAD_S     = 34f;
    private static final float PAD        = 7f;
    private static final float BAR_H      = 5f;
    private static final float BAR_R      = 2.5f;
    // Card width/height computed from content

    // ── State ─────────────────────────────────────────────────────────────────
    private final TimerUtil lastAttackTimer = new TimerUtil();
    private final TimerUtil animTimer       = new TimerUtil();
    private EntityLivingBase lastTarget = null;
    private EntityLivingBase target     = null;
    private ResourceLocation headTex    = null;
    private float oldHp = 0, newHp = 0, maxHp = 1;

    // ── Settings ──────────────────────────────────────────────────────────────
    public final DropdownSetting posX       = register(new DropdownSetting("Position X", 1, "Left", "Middle", "Right"));
    public final DropdownSetting posY       = register(new DropdownSetting("Position Y", 2, "Top", "Middle", "Bottom"));
    public final SliderSetting   offX       = register(new SliderSetting("Offset X",   0, -500, 500, 1));
    public final SliderSetting   offY       = register(new SliderSetting("Offset Y", -55, -500, 500, 1));
    public final SliderSetting   scale      = register(new SliderSetting("Scale",    1.0,  0.5, 2.0, 0.05));
    public final BooleanSetting  showHead   = register(new BooleanSetting("Show Head",   true));
    public final BooleanSetting  showWL     = register(new BooleanSetting("Show W/L",    true));
    public final BooleanSetting  animations = register(new BooleanSetting("Animations",  true));
    public final BooleanSetting  shadow     = register(new BooleanSetting("Shadow",      true));
    public final BooleanSetting  kaOnly     = register(new BooleanSetting("KA Only",     true));
    public final BooleanSetting  trackTarget= register(new BooleanSetting("Track Target",false));

    public TargetHUD() { super("TargetHUD", false, true); }
    public EntityLivingBase getTarget() { return target; }

    private EntityLivingBase resolveTarget() {
        if (!lastAttackTimer.hasTimeElapsed(1500L) && TeamUtil.isEntityLoaded(lastTarget)) return lastTarget;
        return null;
    }

    private ResourceLocation getSkin(EntityLivingBase e) {
        if (!(e instanceof EntityPlayer)) return null;
        try {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(e.getName());
            return info != null ? info.getLocationSkin() : null;
        } catch (Exception ignored) { return null; }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;
        EntityLivingBase prev = target;
        target = resolveTarget();
        if (target == null) return;

        float abs    = target.getAbsorptionAmount() / 2f;
        float hp     = target.getHealth() / 2f + abs;
        float mxHp   = target.getMaxHealth() / 2f;
        float selfHp = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2f;

        if (target != prev) {
            headTex = null; animTimer.setTime();
            oldHp = hp; newHp = hp; maxHp = Math.max(0.001f, mxHp);
        }
        if (!animations.getValue() || animTimer.hasTimeElapsed(250L)) {
            oldHp = newHp; newHp = hp; maxHp = Math.max(0.001f, mxHp);
            if (oldHp != newHp) animTimer.reset();
        }

        ResourceLocation skin = getSkin(target);
        if (skin != null) headTex = skin;

        float elapsed  = (float) Math.min(animTimer.getElapsedTime(), 250L);
        float animHp   = RenderUtil.lerpFloat(newHp, oldHp, elapsed / 250f);
        float hpRatio  = Math.min(1f, Math.max(0f, animHp / maxHp));

        // ── Layout ────────────────────────────────────────────────────────────
        boolean hasHead = showHead.getValue() && headTex != null;
        float headW     = hasHead ? HEAD_S + PAD : 0f;

        String nameStr = target instanceof EntityPlayer
                ? ChatColors.formatColor("&r" + TeamUtil.stripName(target))
                : target.getName();
        String hpStr   = hpFmt.format(animHp);

        float nameW    = mc.fontRendererObj.getStringWidth(nameStr);
        float hpNumW   = mc.fontRendererObj.getStringWidth(hpStr);

        // Minimum bar width so card doesn't get too narrow
        float minBarW  = 80f;
        float contentW = Math.max(nameW, minBarW + PAD + hpNumW);
        float cardW    = PAD + headW + contentW + PAD;
        float cardH    = PAD + mc.fontRendererObj.FONT_HEIGHT + 4 + BAR_H + PAD;

        // ── Position ──────────────────────────────────────────────────────────
        ScaledResolution sr = new ScaledResolution(mc);
        float sv = (float) scale.getValue();
        float px = (float) offX.getValue();
        float py = (float) offY.getValue();
        switch (posX.getIndex()) {
            case 1: px += sr.getScaledWidth()  / 2f - (cardW * sv) / 2f; break;
            case 2: px  = sr.getScaledWidth()  - (cardW * sv) - px;       break;
        }
        switch (posY.getIndex()) {
            case 1: py += sr.getScaledHeight() / 2f - (cardH * sv) / 2f; break;
            case 2: py  = sr.getScaledHeight() - (cardH * sv) - py;       break;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(px, py, 0f);
        GlStateManager.scale(sv, sv, 1f);

        // ── Background ────────────────────────────────────────────────────────
        // Soft shadow
        RoundedUtils.drawRoundedRect(-3, -3, cardW + 6, cardH + 6, CARD_R + 3, 0x33000000);
        RoundedUtils.drawRoundedRect(-1, -1, cardW + 2, cardH + 2, CARD_R + 1, 0x22000000);
        // Main bg
        RoundedUtils.drawRoundedRect(0, 0, cardW, cardH, CARD_R, BG);

        // ── Head ──────────────────────────────────────────────────────────────
        float contentX = PAD + headW;
        if (hasHead) {
            float hx = PAD;
            float hy = (cardH - HEAD_S) / 2f;
            // Subtle head bg
            RoundedUtils.drawRoundedRect(hx - 1, hy - 1, HEAD_S + 2, HEAD_S + 2, 4, 0x33000000);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableTexture2D();
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.getTextureManager().bindTexture(headTex);
            Gui.drawScaledCustomSizeModalRect((int)hx, (int)hy, 8, 8, 8, 8, (int)HEAD_S, (int)HEAD_S, 64, 64);
            Gui.drawScaledCustomSizeModalRect((int)hx, (int)hy, 40, 8, 8, 8, (int)HEAD_S, (int)HEAD_S, 64, 64);
            GlStateManager.disableTexture2D();
            GlStateManager.disableBlend();
        }

        // ── Name ──────────────────────────────────────────────────────────────
        float nameY = PAD;
        float barY  = nameY + mc.fontRendererObj.FONT_HEIGHT + 4;
        float barX0 = contentX;
        float barX1 = cardW - PAD - hpNumW - 4;
        float barW  = barX1 - barX0;

        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();

        mc.fontRendererObj.drawString(nameStr, contentX, nameY, 0xFFFFFFFF, shadow.getValue());

        // W/L tag — small, muted, right after name
        if (showWL.getValue()) {
            String wl;
            int wlColor;
            if (selfHp > hp + 0.5f)      { wl = "W";  wlColor = 0xFF55FF55; }
            else if (selfHp < hp - 0.5f) { wl = "L";  wlColor = 0xFFFF5555; }
            else                          { wl = "=";  wlColor = 0xFF888888; }
            mc.fontRendererObj.drawString(wl,
                    contentX + nameW + 4, nameY, wlColor, shadow.getValue());
        }

        // HP number — pink, right-aligned
        mc.fontRendererObj.drawString(hpStr,
                cardW - PAD - hpNumW, barY + (BAR_H - mc.fontRendererObj.FONT_HEIGHT) / 2f - 1,
                ACCENT, shadow.getValue());

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();

        // ── Health bar ────────────────────────────────────────────────────────
        // Track
        RoundedUtils.drawRoundedRect(barX0, barY, barW, BAR_H, BAR_R, BAR_TRACK);
        // Fill
        float fillW = hpRatio * barW;
        if (fillW >= BAR_R * 2) {
            RoundedUtils.drawRoundedRect(barX0, barY, fillW, BAR_H, BAR_R, ACCENT);
            // Shimmer
            RoundedUtils.drawRoundedRect(barX0 + fillW - 5, barY, 5, BAR_H, BAR_R, 0x66FFD6EC);
        } else if (fillW > 0) {
            RoundedUtils.drawRoundedRect(barX0, barY, Math.max(fillW, 2), BAR_H, 1, ACCENT);
        }

        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
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
