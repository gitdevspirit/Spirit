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

    // ── Design constants ──────────────────────────────────────────────────────
    private static final int   ACCENT      = 0xFFE991B8;
    private static final int   ACCENT_DIM  = 0x66E991B8;
    private static final int   BG          = 0xDD0D0D0D;
    private static final float CARD_W      = 170f;
    private static final float CARD_H      = 44f;
    private static final float CORNER_R    = 8f;
    private static final float HEAD_S      = 32f;
    private static final float PAD         = 6f;
    private static final float BAR_H       = 5f;
    private static final float BAR_CORNER  = 2.5f;

    // ── State ─────────────────────────────────────────────────────────────────
    private final TimerUtil lastAttackTimer = new TimerUtil();
    private final TimerUtil animTimer       = new TimerUtil();
    private EntityLivingBase lastTarget  = null;
    private EntityLivingBase target      = null;
    private ResourceLocation headTexture = null;
    private float oldHealth = 0, newHealth = 0, maxHealth = 1;

    // ── Settings ──────────────────────────────────────────────────────────────
    public final DropdownSetting posX        = register(new DropdownSetting("Position X", 1, "Left", "Middle", "Right"));
    public final DropdownSetting posY        = register(new DropdownSetting("Position Y", 2, "Top", "Middle", "Bottom"));
    public final SliderSetting   offX        = register(new SliderSetting("Offset X",    0, -500, 500, 1));
    public final SliderSetting   offY        = register(new SliderSetting("Offset Y",  -55, -500, 500, 1));
    public final SliderSetting   scale       = register(new SliderSetting("Scale",     1.0,  0.5,   2.0, 0.05));
    public final BooleanSetting  showHead    = register(new BooleanSetting("Show Head",   true));
    public final BooleanSetting  showWL      = register(new BooleanSetting("Show W/L",    true));
    public final BooleanSetting  animations  = register(new BooleanSetting("Animations",  true));
    public final BooleanSetting  shadow      = register(new BooleanSetting("Shadow",       true));
    public final BooleanSetting  kaOnly      = register(new BooleanSetting("KA Only",      true));
    public final BooleanSetting  trackTarget = register(new BooleanSetting("Track Target", false));

    public TargetHUD() { super("TargetHUD", false, true); }

    public EntityLivingBase getTarget() { return target; }

    private EntityLivingBase resolveTarget() {
        if (!lastAttackTimer.hasTimeElapsed(1500L) && TeamUtil.isEntityLoaded(lastTarget))
            return lastTarget;
        return null;
    }

    private ResourceLocation getSkin(EntityLivingBase e) {
        if (e instanceof EntityPlayer) {
            try {
                NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(e.getName());
                if (info != null) return info.getLocationSkin();
            } catch (Exception ignored) {}
        }
        return null;
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;
        EntityLivingBase prev = target;
        target = resolveTarget();
        if (target == null) return;

        float abs    = target.getAbsorptionAmount() / 2.0f;
        float hp     = target.getHealth() / 2.0f + abs;
        float maxHp  = target.getMaxHealth() / 2.0f;
        float selfHp = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0f;

        if (target != prev) {
            headTexture = null;
            animTimer.setTime();
            oldHealth = hp; newHealth = hp;
            maxHealth = Math.max(0.001f, maxHp);
        }
        if (!animations.getValue() || animTimer.hasTimeElapsed(250L)) {
            oldHealth = newHealth; newHealth = hp;
            maxHealth = Math.max(0.001f, maxHp);
            if (oldHealth != newHealth) animTimer.reset();
        }

        ResourceLocation skin = getSkin(target);
        if (skin != null) headTexture = skin;

        float elapsed = (float) Math.min(Math.max(animTimer.getElapsedTime(), 0L), 250L);
        float animHp  = RenderUtil.lerpFloat(newHealth, oldHealth, elapsed / 250f);
        float hpRatio = Math.min(1f, Math.max(0f, animHp / maxHealth));

        // ── Position ──────────────────────────────────────────────────────────
        ScaledResolution sr = new ScaledResolution(mc);
        float scaleVal = (float) scale.getValue();
        float px = (float) offX.getValue();
        float py = (float) offY.getValue();
        switch (posX.getIndex()) {
            case 1: px += sr.getScaledWidth()  / 2f - (CARD_W * scaleVal) / 2f; break;
            case 2: px  = sr.getScaledWidth()  - (CARD_W * scaleVal) - px; break;
        }
        switch (posY.getIndex()) {
            case 1: py += sr.getScaledHeight() / 2f - (CARD_H * scaleVal) / 2f; break;
            case 2: py  = sr.getScaledHeight() - (CARD_H * scaleVal) - py; break;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(px, py, 0f);
        GlStateManager.scale(scaleVal, scaleVal, 1f);

        // ── Draw everything with proper GL state ──────────────────────────────
        // All geometry uses RoundedUtils which correctly manages disableTexture2D internally

        // Drop shadow layers
        for (int i = 4; i >= 1; i--) {
            int a = (int)(40f / i);
            RoundedUtils.drawRoundedRect(-i, -i, CARD_W + i * 2f, CARD_H + i * 2f, CORNER_R + i, a << 24);
        }

        // Main card
        RoundedUtils.drawRoundedRect(0, 0, CARD_W, CARD_H, CORNER_R, BG);

        // Pink outline
        RoundedUtils.drawRoundedOutline(0, 0, CARD_W, CARD_H, CORNER_R, 1.0f, ACCENT_DIM);

        // Left accent stripe — draw as a narrow rounded rect
        RoundedUtils.drawRoundedRect(0, 0, 3, CARD_H, CORNER_R, ACCENT);

        // ── Health bar track and fill ─────────────────────────────────────────
        float headW  = (showHead.getValue() && headTexture != null) ? HEAD_S + PAD : 0f;
        float textX  = 3 + PAD + headW;
        float barY   = CARD_H - PAD - BAR_H;
        float barX0  = textX;
        float barX1  = CARD_W - PAD;
        float barW   = barX1 - barX0;

        // Bar track
        RoundedUtils.drawRoundedRect(barX0, barY, barW, BAR_H, BAR_CORNER, 0xFF1A1A1A);

        // Bar fill
        float fillW = hpRatio * barW;
        if (fillW >= BAR_CORNER * 2) {
            RoundedUtils.drawRoundedRect(barX0, barY, fillW, BAR_H, BAR_CORNER, ACCENT);
            // Shimmer on leading edge
            RoundedUtils.drawRoundedRect(barX0 + fillW - 5, barY, 5, BAR_H, BAR_CORNER, 0x88FFD6EC);
        } else if (fillW > 0) {
            RoundedUtils.drawRoundedRect(barX0, barY, fillW, BAR_H, 1, ACCENT);
        }

        // ── Player head ───────────────────────────────────────────────────────
        if (showHead.getValue() && headTexture != null) {
            float headX = 3 + PAD;
            // Head shadow
            RoundedUtils.drawRoundedRect(headX - 1, PAD - 1, HEAD_S + 2, HEAD_S + 2, 3, 0x44000000);

            // Now bind texture and draw head
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableTexture2D();
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.getTextureManager().bindTexture(headTexture);
            Gui.drawScaledCustomSizeModalRect(
                    (int) headX, (int) PAD,
                    8f, 8f, 8, 8,
                    (int) HEAD_S, (int) HEAD_S,
                    64f, 64f);
            Gui.drawScaledCustomSizeModalRect(
                    (int) headX, (int) PAD,
                    40f, 8f, 8, 8,
                    (int) HEAD_S, (int) HEAD_S,
                    64f, 64f);
            GlStateManager.disableTexture2D();
            GlStateManager.disableBlend();
        }

        // ── Text ──────────────────────────────────────────────────────────────
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();

        String name  = target instanceof EntityPlayer
                ? ChatColors.formatColor("&r" + TeamUtil.stripName(target))
                : target.getName();
        String hpStr = hpFmt.format(animHp) + " \u2764";

        float nameY  = PAD + 1;
        float hpNumY = nameY + 10;

        // Name
        mc.fontRendererObj.drawString(name, textX, nameY, 0xFFFFFFFF, shadow.getValue());

        // HP — pink, right-aligned
        mc.fontRendererObj.drawString(hpStr,
                barX1 - mc.fontRendererObj.getStringWidth(hpStr),
                hpNumY, ACCENT, shadow.getValue());

        // W/L — left, same row
        if (showWL.getValue()) {
            String wl;
            int wlColor;
            if (selfHp > hp + 0.5f)      { wl = "Winning"; wlColor = 0xFF55FF55; }
            else if (selfHp < hp - 0.5f) { wl = "Losing";  wlColor = 0xFFFF5555; }
            else                          { wl = "Even";    wlColor = 0xFFAAAAAA; }
            mc.fontRendererObj.drawString(wl, textX, hpNumY, wlColor, shadow.getValue());
        }

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GL11.glColor4f(1f, 1f, 1f, 1f);

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
