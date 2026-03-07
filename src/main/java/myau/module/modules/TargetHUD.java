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
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class TargetHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat hpFmt = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));

    // Pink accent to match GUI
    private static final Color ACCENT      = new Color(0xE991B8);
    private static final Color ACCENT_DARK = new Color(0x99, 0x55, 0x77, 0xFF);
    private static final Color BG_COLOR    = new Color(0x0D, 0x0D, 0x0D, 0xCC);
    private static final Color BG_DARK     = new Color(0x08, 0x08, 0x08, 0xEE);

    // Card dimensions
    private static final float CARD_W  = 160f;
    private static final float CARD_H  = 38f;
    private static final float HEAD_S  = 30f;  // head square size
    private static final float PAD     = 5f;

    private final TimerUtil lastAttackTimer = new TimerUtil();
    private final TimerUtil animTimer       = new TimerUtil();
    private EntityLivingBase lastTarget  = null;
    private EntityLivingBase target      = null;
    private ResourceLocation headTexture = null;
    private float oldHealth = 0, newHealth = 0, maxHealth = 1;

    // ── Settings ──────────────────────────────────────────────────────────────
    public final DropdownSetting posX       = register(new DropdownSetting("Position X", 1, "Left", "Middle", "Right"));
    public final DropdownSetting posY       = register(new DropdownSetting("Position Y", 2, "Top", "Middle", "Bottom"));
    public final SliderSetting   offX       = register(new SliderSetting("Offset X",   0, -500, 500, 1));
    public final SliderSetting   offY       = register(new SliderSetting("Offset Y", -50, -500, 500, 1));
    public final SliderSetting   scale      = register(new SliderSetting("Scale",    1.0,  0.5,  2.0, 0.05));
    public final BooleanSetting  showHead   = register(new BooleanSetting("Show Head",    true));
    public final BooleanSetting  showWL     = register(new BooleanSetting("Show W/L",     true));
    public final BooleanSetting  animations = register(new BooleanSetting("Animations",   true));
    public final BooleanSetting  shadow     = register(new BooleanSetting("Shadow",        true));
    public final BooleanSetting  kaOnly       = register(new BooleanSetting("KA Only",       true));
    public final BooleanSetting  trackTarget  = register(new BooleanSetting("Track Target",  false));

    public TargetHUD() { super("TargetHUD", false, true); }

    public EntityLivingBase getTarget() { return target; }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private EntityLivingBase resolveTarget() {
        if (!lastAttackTimer.hasTimeElapsed(1500L) && TeamUtil.isEntityLoaded(lastTarget))
            return lastTarget;
        return null;
    }

    private ResourceLocation getSkin(EntityLivingBase e) {
        if (e instanceof EntityPlayer) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(e.getName());
            if (info != null) return info.getLocationSkin();
        }
        return null;
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;
        EntityLivingBase prev = target;
        target = resolveTarget();
        if (target == null) return;

        // Health values (halved to match vanilla hearts display)
        float abs   = target.getAbsorptionAmount() / 2.0f;
        float hp    = target.getHealth() / 2.0f + abs;
        float maxHp = target.getMaxHealth() / 2.0f;
        float selfHp = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0f;

        // Reset on target change
        if (target != prev) {
            headTexture = null;
            animTimer.setTime();
            oldHealth = hp;
            newHealth = hp;
            maxHealth = Math.max(0.001f, maxHp);
        }

        // Animate health bar
        if (!animations.getValue() || animTimer.hasTimeElapsed(200L)) {
            oldHealth = newHealth;
            newHealth = hp;
            maxHealth = Math.max(0.001f, maxHp);
            if (oldHealth != newHealth) animTimer.reset();
        }

        ResourceLocation skin = getSkin(target);
        if (skin != null) headTexture = skin;

        float elapsed = (float) Math.min(Math.max(animTimer.getElapsedTime(), 0L), 200L);
        float animHp  = RenderUtil.lerpFloat(newHealth, oldHealth, elapsed / 200f);
        float hpRatio = Math.min(1f, Math.max(0f, animHp / maxHealth));

        // ── Layout ────────────────────────────────────────────────────────────
        float headW  = (showHead.getValue() && headTexture != null) ? HEAD_S + PAD : 0f;
        float totalW = CARD_W;
        float totalH = CARD_H;

        // Position
        ScaledResolution sr    = new ScaledResolution(mc);
        float scaleVal = (float) scale.getValue();
        float px = (float) offX.getValue();
        float py = (float) offY.getValue();

        switch (posX.getIndex()) {
            case 0: break; // left
            case 1: px += sr.getScaledWidth()  / 2f - (totalW * scaleVal) / 2f; break;
            case 2: px  = sr.getScaledWidth()  - (totalW * scaleVal) - px; break;
        }
        switch (posY.getIndex()) {
            case 0: break; // top
            case 1: py += sr.getScaledHeight() / 2f - (totalH * scaleVal) / 2f; break;
            case 2: py  = sr.getScaledHeight() - (totalH * scaleVal) - py; break;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(px, py, 0f);
        GlStateManager.scale(scaleVal, scaleVal, 1f);

        // ── Background card ───────────────────────────────────────────────────
        // Dark outer shadow
        RenderUtil.drawRect(-1, -1, totalW + 1, totalH + 1,
                new Color(0, 0, 0, 80).getRGB());
        // Main background
        RenderUtil.drawRect(0, 0, totalW, totalH, BG_COLOR.getRGB());
        // Slightly lighter inner top strip for depth
        RenderUtil.drawRect(0, 0, totalW, 1, new Color(255, 255, 255, 12).getRGB());

        // ── Left pink accent stripe ───────────────────────────────────────────
        RenderUtil.drawRect(0, 0, 2, totalH, ACCENT.getRGB());

        // ── Player head ───────────────────────────────────────────────────────
        float textX = 2 + PAD + headW;
        if (showHead.getValue() && headTexture != null) {
            // Head background
            RenderUtil.drawRect(PAD, PAD, PAD + HEAD_S, PAD + HEAD_S,
                    new Color(0, 0, 0, 60).getRGB());
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.getTextureManager().bindTexture(headTexture);
            Gui.drawScaledCustomSizeModalRect(
                    (int)(PAD), (int)(PAD),
                    8f, 8f, 8, 8,
                    (int)HEAD_S, (int)HEAD_S,
                    64f, 64f);
            // Hat layer
            Gui.drawScaledCustomSizeModalRect(
                    (int)(PAD), (int)(PAD),
                    40f, 8f, 8, 8,
                    (int)HEAD_S, (int)HEAD_S,
                    64f, 64f);
        }

        // ── Text ──────────────────────────────────────────────────────────────
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        String name  = target instanceof EntityPlayer
                ? ChatColors.formatColor("&r" + TeamUtil.stripName(target))
                : target.getName();
        String hpStr = hpFmt.format(animHp);

        // Name — white, top left of text area
        float nameY = PAD + 1;
        mc.fontRendererObj.drawString(name, textX, nameY,
                0xFFFFFFFF, shadow.getValue());

        // HP number — pink, right side, vertically centred on bar
        float barY  = totalH - 10f;
        float barX0 = textX;
        float barX1 = totalW - PAD;
        float hpNumW = mc.fontRendererObj.getStringWidth(hpStr);
        mc.fontRendererObj.drawString(hpStr,
                barX1 - hpNumW,
                nameY + 10,
                ACCENT.getRGB(), shadow.getValue());

        // W/L label
        if (showWL.getValue()) {
            String wl;
            int wlColor;
            if (selfHp > hp + 0.5f)      { wl = "Winning"; wlColor = 0xFF55FF55; }
            else if (selfHp < hp - 0.5f) { wl = "Losing";  wlColor = 0xFFFF5555; }
            else                          { wl = "Even";    wlColor = 0xFFAAAAAA; }
            mc.fontRendererObj.drawString(wl, textX, nameY + 10,
                    wlColor, shadow.getValue());
        }

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();

        // ── Health bar ────────────────────────────────────────────────────────
        RenderUtil.enableRenderState();

        // Track
        RenderUtil.drawRect(barX0, barY, barX1, barY + 4,
                new Color(0x1A, 0x1A, 0x1A, 0xFF).getRGB());
        // Fill — pink gradient effect (bright left → darker right)
        float fillX1 = barX0 + hpRatio * (barX1 - barX0);
        RenderUtil.drawRect(barX0, barY, fillX1, barY + 4, ACCENT.getRGB());
        // Bright leading edge on the fill
        if (hpRatio > 0.02f) {
            RenderUtil.drawRect(fillX1 - 1.5f, barY, fillX1, barY + 4,
                    new Color(255, 200, 230, 200).getRGB());
        }
        // Bottom border line under bar
        RenderUtil.drawRect(barX0, barY + 4, barX1, barY + 5,
                new Color(0, 0, 0, 100).getRGB());

        RenderUtil.disableRenderState();

        GlStateManager.popMatrix();
    }

    // ── Attack tracking ───────────────────────────────────────────────────────
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
