package myau.module.modules;

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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class TargetHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat hpFmt = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
    private static final ResourceLocation STEVE = new ResourceLocation("textures/entity/steve.png");

    // colors as ARGB
    private static final int ACCENT   = 0xFFE991B8;
    private static final int BG       = 0xCC111111;
    private static final int BAR_BG   = 0xFF222222;
    private static final float HEAD_S = 32f;
    private static final float PAD    = 6f;
    private static final float BAR_H  = 4f;

    private final TimerUtil lastAttackTimer = new TimerUtil();
    private final TimerUtil animTimer       = new TimerUtil();
    private EntityLivingBase lastTarget = null;
    private EntityLivingBase target     = null;
    private float oldHp = 0, newHp = 0, maxHp = 1;

    public final DropdownSetting posX        = register(new DropdownSetting("Position X", 1, "Left", "Middle", "Right"));
    public final DropdownSetting posY        = register(new DropdownSetting("Position Y", 2, "Top", "Middle", "Bottom"));
    public final SliderSetting   offX        = register(new SliderSetting("Offset X",   0, -500, 500, 1));
    public final SliderSetting   offY        = register(new SliderSetting("Offset Y", -55, -500, 500, 1));
    public final SliderSetting   scale       = register(new SliderSetting("Scale",    1.0,  0.5, 2.0, 0.05));
    public final BooleanSetting  showHead    = register(new BooleanSetting("Show Head",    true));
    public final BooleanSetting  showWL      = register(new BooleanSetting("Show W/L",     true));
    public final BooleanSetting  animations  = register(new BooleanSetting("Animations",   true));
    public final BooleanSetting  shadowText  = register(new BooleanSetting("Shadow",        true));
    public final BooleanSetting  kaOnly      = register(new BooleanSetting("KA Only",       true));
    public final BooleanSetting  trackTarget = register(new BooleanSetting("Track Target",  false));

    public TargetHUD() { super("TargetHUD", false, true); }
    public EntityLivingBase getTarget() { return target; }

    private EntityLivingBase resolveTarget() {
        if (!lastAttackTimer.hasTimeElapsed(1500L) && TeamUtil.isEntityLoaded(lastTarget)) return lastTarget;
        return null;
    }

    // Always returns a texture — Steve as fallback so head shows immediately
    private ResourceLocation getSkin(EntityLivingBase e) {
        if (e instanceof EntityPlayer) {
            try {
                NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(e.getName());
                if (info != null && info.getLocationSkin() != null) return info.getLocationSkin();
            } catch (Exception ignored) {}
        }
        return STEVE;
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
            animTimer.setTime();
            oldHp = hp; newHp = hp; maxHp = Math.max(0.001f, mxHp);
        }
        if (!animations.getValue() || animTimer.hasTimeElapsed(250L)) {
            oldHp = newHp; newHp = hp; maxHp = Math.max(0.001f, mxHp);
            if (oldHp != newHp) animTimer.reset();
        }

        float elapsed = (float) Math.min(animTimer.getElapsedTime(), 250L);
        float animHp  = RenderUtil.lerpFloat(newHp, oldHp, elapsed / 250f);
        float hpRatio = Math.min(1f, Math.max(0f, animHp / maxHp));

        String nameStr = target instanceof EntityPlayer
                ? ChatColors.formatColor("&r" + TeamUtil.stripName(target))
                : target.getName();
        String hpStr   = hpFmt.format(animHp);
        int    fontH   = mc.fontRendererObj.FONT_HEIGHT;

        boolean hasHead = showHead.getValue();
        float headBlockW = hasHead ? HEAD_S + PAD : 0f;
        float hpNumW     = mc.fontRendererObj.getStringWidth(hpStr);
        float nameW      = mc.fontRendererObj.getStringWidth(nameStr);
        float minContent = Math.max(nameW + 40, 80 + 4 + hpNumW + 20);
        float cardW      = PAD + headBlockW + minContent + PAD;
        float cardH      = PAD + fontH + 3 + BAR_H + PAD;

        // layout coords
        float contentX = PAD + headBlockW;
        float nameY    = PAD;
        float barY     = nameY + fontH + 3;
        float barX0    = contentX;
        float barX1    = cardW - PAD - hpNumW - 4;
        float barW     = barX1 - barX0;

        // ── Position ──────────────────────────────────────────────────────────
        ScaledResolution sr = new ScaledResolution(mc);
        float sv = (float) scale.getValue();
        float px = (float) offX.getValue();
        float py = (float) offY.getValue();
        switch (posX.getIndex()) {
            case 1: px += sr.getScaledWidth()  / 2f - (cardW * sv) / 2f; break;
            case 2: px  = sr.getScaledWidth()  - (cardW * sv) - px; break;
        }
        switch (posY.getIndex()) {
            case 1: py += sr.getScaledHeight() / 2f - (cardH * sv) / 2f; break;
            case 2: py  = sr.getScaledHeight() - (cardH * sv) - py; break;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(px, py, 0f);
        GlStateManager.scale(sv, sv, 1f);

        // ── 1. Background — rounded corners ─────────────────────────────────────
        RoundedUtils.drawRoundedRect(-2, -2, cardW + 4, cardH + 4, 8, 0x33000000); // shadow
        RoundedUtils.drawRoundedRect(0, 0, cardW, cardH, 6, BG);

        // ── 2. Bar track and fill — Gui.drawRect, zero GL fuss ────────────────
        Gui.drawRect((int) barX0, (int) barY,
                     (int)(barX0 + barW), (int)(barY + BAR_H), BAR_BG);
        if (hpRatio > 0.001f) {
            float fillW = Math.max(1f, hpRatio * barW);
            Gui.drawRect((int) barX0, (int) barY,
                         (int)(barX0 + fillW), (int)(barY + BAR_H), ACCENT);
        }

        // ── 3. Head ───────────────────────────────────────────────────────────
        if (hasHead) {
            ResourceLocation skin = getSkin(target);
            float hx = PAD;
            float hy = (cardH - HEAD_S) / 2f;
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            mc.getTextureManager().bindTexture(skin);
            // base face layer
            Gui.drawScaledCustomSizeModalRect(
                    (int) hx, (int) hy, 8, 8, 8, 8,
                    (int) HEAD_S, (int) HEAD_S, 64, 64);
            // hat layer
            Gui.drawScaledCustomSizeModalRect(
                    (int) hx, (int) hy, 40, 8, 8, 8,
                    (int) HEAD_S, (int) HEAD_S, 64, 64);
            GlStateManager.disableBlend();
        }

        // ── 4. Text ───────────────────────────────────────────────────────────
        GlStateManager.enableTexture2D();
        boolean shad = shadowText.getValue();
        mc.fontRendererObj.drawString(nameStr, (int) contentX, (int) nameY, 0xFFFFFFFF, shad);

        if (showWL.getValue()) {
            String wl; int wlCol;
            if (selfHp > hp + 0.5f)      { wl = "W"; wlCol = 0xFF55FF55; }
            else if (selfHp < hp - 0.5f) { wl = "L"; wlCol = 0xFFFF5555; }
            else                          { wl = "="; wlCol = 0xFF888888; }
            mc.fontRendererObj.drawString(wl, (int)(contentX + nameW + 4), (int) nameY, wlCol, shad);
        }

        mc.fontRendererObj.drawString(hpStr,
                (int)(cardW - PAD - hpNumW),
                (int)(barY + (BAR_H - fontH) / 2f - 1),
                ACCENT, shad);

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
