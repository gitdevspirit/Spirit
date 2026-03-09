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
import org.lwjgl.opengl.GL11;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class TargetHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat hpFmt = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
    private static final ResourceLocation STEVE = new ResourceLocation("textures/entity/steve.png");

    // ── Colors ────────────────────────────────────────────────────────────────
    // Pink accent
    private static final float[] PINK   = { 0xE9/255f, 0x91/255f, 0xB8/255f };
    // Bar gradient: left = bright pink, right = dim pink/purple
    private static final int BAR_LEFT   = 0xFFE991B8;
    private static final int BAR_RIGHT  = 0xFF7B3F6E;
    private static final int BAR_TRACK  = 0xFF1A1A1A;
    private static final int BG_COLOR   = 0xE0111111;
    private static final int HIT_FLASH  = 0x55FF4466; // red-pink overlay on hit

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final float HEAD_S  = 32f;
    private static final float PAD     = 7f;
    private static final float BAR_H   = 5f;
    private static final float CARD_R  = 7f;

    // ── State ─────────────────────────────────────────────────────────────────
    private final TimerUtil lastAttackTimer = new TimerUtil();
    private final TimerUtil animTimer       = new TimerUtil(); // hp lerp
    private final TimerUtil hitTimer        = new TimerUtil(); // hit flash
    private EntityLivingBase lastTarget = null;
    private EntityLivingBase target     = null;
    private float oldHp = 0, newHp = 0, maxHp = 1;
    private float lastKnownHp = -1; // to detect damage

    // ── Settings ──────────────────────────────────────────────────────────────
    public final DropdownSetting posX        = register(new DropdownSetting("Position X", 1, "Left", "Middle", "Right"));
    public final DropdownSetting posY        = register(new DropdownSetting("Position Y", 2, "Top", "Middle", "Bottom"));
    public final SliderSetting   offX        = register(new SliderSetting("Offset X",   0, -500, 500, 1));
    public final SliderSetting   offY        = register(new SliderSetting("Offset Y", -55, -500, 500, 1));
    public final SliderSetting   scale       = register(new SliderSetting("Scale",    1.0,  0.5, 2.0, 0.05));
    public final BooleanSetting  showHead    = register(new BooleanSetting("Show Head",    true));
    public final BooleanSetting  showWL      = register(new BooleanSetting("Show W/L",     true));
    public final BooleanSetting  hitAnim     = register(new BooleanSetting("Hit Flash",     true));
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

    private ResourceLocation getSkin(EntityLivingBase e) {
        if (e instanceof EntityPlayer) {
            try {
                NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(e.getName());
                if (info != null && info.getLocationSkin() != null) return info.getLocationSkin();
            } catch (Exception ignored) {}
        }
        return STEVE;
    }

    // Draw a horizontal gradient rect using raw GL
    private void drawGradientRect(float x1, float y1, float x2, float y2, int colorLeft, int colorRight) {
        float aL = (colorLeft  >> 24 & 0xFF) / 255f, rL = (colorLeft  >> 16 & 0xFF) / 255f,
              gL = (colorLeft  >> 8  & 0xFF) / 255f, bL = (colorLeft         & 0xFF) / 255f;
        float aR = (colorRight >> 24 & 0xFF) / 255f, rR = (colorRight >> 16 & 0xFF) / 255f,
              gR = (colorRight >> 8  & 0xFF) / 255f, bR = (colorRight        & 0xFF) / 255f;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(rL, gL, bL, aL); GL11.glVertex2f(x1, y1);
        GL11.glColor4f(rR, gR, bR, aR); GL11.glVertex2f(x2, y1);
        GL11.glColor4f(rR, gR, bR, aR); GL11.glVertex2f(x2, y2);
        GL11.glColor4f(rL, gL, bL, aL); GL11.glVertex2f(x1, y2);
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;
        EntityLivingBase prev = target;
        target = resolveTarget();
        if (target == null) { lastKnownHp = -1; return; }

        float abs    = target.getAbsorptionAmount() / 2f;
        float hp     = target.getHealth() / 2f + abs;
        float mxHp   = target.getMaxHealth() / 2f;
        float selfHp = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2f;

        // Detect hit — hp dropped
        if (lastKnownHp >= 0 && hp < lastKnownHp - 0.1f && hitAnim.getValue()) {
            hitTimer.reset();
        }
        lastKnownHp = hp;

        if (target != prev) {
            animTimer.setTime();
            oldHp = hp; newHp = hp; maxHp = Math.max(0.001f, mxHp);
            lastKnownHp = hp;
        }
        if (!animations.getValue() || animTimer.hasTimeElapsed(300L)) {
            oldHp = newHp; newHp = hp; maxHp = Math.max(0.001f, mxHp);
            if (oldHp != newHp) animTimer.reset();
        }

        float elapsed = (float) Math.min(animTimer.getElapsedTime(), 300L);
        float animHp  = RenderUtil.lerpFloat(newHp, oldHp, elapsed / 300f);
        float hpRatio = Math.min(1f, Math.max(0f, animHp / maxHp));

        // Hit flash alpha: fades out over 350ms
        float hitAlpha = 0f;
        if (hitAnim.getValue() && !hitTimer.hasTimeElapsed(350L)) {
            hitAlpha = 1f - (float) hitTimer.getElapsedTime() / 350f;
        }

        String nameStr = target instanceof EntityPlayer
                ? ChatColors.formatColor("&r" + TeamUtil.stripName(target))
                : target.getName();
        String hpStr   = hpFmt.format(animHp) + " \u2764";

        boolean hasHead  = showHead.getValue();
        float   headBlockW = hasHead ? HEAD_S + PAD : 0f;
        float   hpNumW   = mc.fontRendererObj.getStringWidth(hpStr);
        float   nameW    = mc.fontRendererObj.getStringWidth(nameStr);

        // W/L string
        String wlStr = ""; int wlCol = 0xFFAAAAAA;
        if (showWL.getValue()) {
            if (selfHp > hp + 0.5f)      { wlStr = "Winning"; wlCol = 0xFF55FF55; }
            else if (selfHp < hp - 0.5f) { wlStr = "Losing";  wlCol = 0xFFFF5555; }
            else                          { wlStr = "Even";    wlCol = 0xFFAAAAAA; }
        }

        int fontH    = mc.fontRendererObj.FONT_HEIGHT;
        // Two text rows: name row, then wl row if enabled
        float textRows = showWL.getValue() ? fontH * 2 + 3 : fontH;
        float cardH    = PAD + Math.max(HEAD_S, textRows + 3 + BAR_H) + PAD;
        float minBarW  = 90f;
        float contentW = Math.max(nameW + 10, minBarW + 6 + hpNumW);
        float cardW    = PAD + headBlockW + contentW + PAD;

        // content start x
        float cx   = PAD + headBlockW;
        // vertical centre for head
        float headY = (cardH - HEAD_S) / 2f;
        // name sits at top of content area
        float nameY = PAD + 1;
        float wlY   = nameY + fontH + 2;
        float barY  = cardH - PAD - BAR_H;
        float barX0 = cx;
        float barX1 = cardW - PAD - hpNumW - 4;
        float barW  = barX1 - barX0;

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

        // ── 1. Shadow + Background ────────────────────────────────────────────
        RoundedUtils.drawRoundedRect(-3, -3, cardW + 6, cardH + 6, CARD_R + 3, 0x44000000);
        RoundedUtils.drawRoundedRect(-1, -1, cardW + 2, cardH + 2, CARD_R + 1, 0x22000000);
        RoundedUtils.drawRoundedRect(0, 0, cardW, cardH, CARD_R, BG_COLOR);

        // ── 2. Hit flash overlay ──────────────────────────────────────────────
        if (hitAlpha > 0f) {
            int flashColor = ((int)(hitAlpha * 0x55) << 24) | 0xFF4466;
            RoundedUtils.drawRoundedRect(0, 0, cardW, cardH, CARD_R, flashColor);
        }

        // ── 3. Bar track ──────────────────────────────────────────────────────
        Gui.drawRect((int) barX0, (int) barY, (int)(barX0 + barW), (int)(barY + BAR_H), BAR_TRACK);

        // ── 4. Bar fill — gradient from pink to dim purple, clipped to fill ──
        if (hpRatio > 0.001f) {
            float fillW = Math.max(1f, hpRatio * barW);
            // Interpolate right-side color based on fill width so gradient always looks full
            // at full HP the bar goes pink→purple, at low HP it's mostly pink
            drawGradientRect(barX0, barY, barX0 + fillW, barY + BAR_H, BAR_LEFT, BAR_RIGHT);
            // Bright shimmer on leading edge
            float shimW = Math.min(8f, fillW);
            drawGradientRect(barX0 + fillW - shimW, barY, barX0 + fillW, barY + BAR_H,
                    0x00FFD6EC, 0x66FFD6EC);
        }

        // ── 5. Thin pink line above bar ───────────────────────────────────────
        Gui.drawRect((int) barX0, (int)(barY - 1), (int)(barX0 + barW), (int) barY, 0x33E991B8);

        // ── 6. Head ───────────────────────────────────────────────────────────
        if (hasHead) {
            ResourceLocation skin = getSkin(target);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            mc.getTextureManager().bindTexture(skin);
            Gui.drawScaledCustomSizeModalRect(
                    (int) PAD, (int) headY, 8, 8, 8, 8,
                    (int) HEAD_S, (int) HEAD_S, 64, 64);
            Gui.drawScaledCustomSizeModalRect(
                    (int) PAD, (int) headY, 40, 8, 8, 8,
                    (int) HEAD_S, (int) HEAD_S, 64, 64);
            GlStateManager.disableBlend();
        }

        // ── 7. Text ───────────────────────────────────────────────────────────
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        boolean shad = shadowText.getValue();

        // Name — white
        mc.fontRendererObj.drawString(nameStr, (int) cx, (int) nameY, 0xFFFFFFFF, shad);

        // W/L below name
        if (showWL.getValue()) {
            mc.fontRendererObj.drawString(wlStr, (int) cx, (int) wlY, wlCol, shad);
        }

        // HP — pink, right aligned, vertically centred on bar
        mc.fontRendererObj.drawString(hpStr,
                (int)(cardW - PAD - hpNumW),
                (int)(barY + (BAR_H - fontH) / 2f - 1),
                0xFFE991B8, shad);

        GlStateManager.enableDepth();
        GL11.glColor4f(1, 1, 1, 1);
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
