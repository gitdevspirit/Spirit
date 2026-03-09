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

    // ── Settings ──────────────────────────────────────────────────────────────
    public final DropdownSetting posX        = register(new DropdownSetting("Position X",  1, "Left", "Middle", "Right"));
    public final DropdownSetting posY        = register(new DropdownSetting("Position Y",  2, "Top",  "Middle", "Bottom"));
    public final SliderSetting   offX        = register(new SliderSetting("Offset X",      0, -500, 500, 1));
    public final SliderSetting   offY        = register(new SliderSetting("Offset Y",    -60, -500, 500, 1));
    public final SliderSetting   scale       = register(new SliderSetting("Scale",        1.0,  0.5, 2.0, 0.05));
    public final SliderSetting   opacity     = register(new SliderSetting("Opacity",      200,    0, 255,    1));
    public final BooleanSetting  showWL      = register(new BooleanSetting("Show W/L",    true));
    public final BooleanSetting  animations  = register(new BooleanSetting("Animations",  true));
    public final BooleanSetting  shadowText  = register(new BooleanSetting("Shadow",       true));
    public final BooleanSetting  kaOnly      = register(new BooleanSetting("KA Only",      true));
    public final BooleanSetting  trackTarget = register(new BooleanSetting("Track Target", false));

    // ── State ─────────────────────────────────────────────────────────────────
    private final TimerUtil lastAttackTimer = new TimerUtil();
    private final TimerUtil animTimer       = new TimerUtil();
    private EntityLivingBase lastTarget = null;
    private EntityLivingBase target     = null;
    private float oldHp = 0, newHp = 0, maxHp = 1;

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
                if (info != null && info.getLocationSkin() != null) return info.getLocationSkin();
            } catch (Exception ignored) {}
        }
        return STEVE;
    }

    // Draw a solid rect using raw GL (no texture interference)
    private void rect(float x1, float y1, float x2, float y2, int color) {
        float a = (color >> 24 & 0xFF) / 255f;
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >> 8  & 0xFF) / 255f;
        float b = (color       & 0xFF) / 255f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y1); GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x2, y2); GL11.glVertex2f(x1, y2);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
    }

    // Horizontal gradient rect
    private void gradRect(float x1, float y1, float x2, float y2, int colL, int colR) {
        float aL=(colL>>24&0xFF)/255f, rL=(colL>>16&0xFF)/255f, gL=(colL>>8&0xFF)/255f, bL=(colL&0xFF)/255f;
        float aR=(colR>>24&0xFF)/255f, rR=(colR>>16&0xFF)/255f, gR=(colR>>8&0xFF)/255f, bR=(colR&0xFF)/255f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(rL,gL,bL,aL); GL11.glVertex2f(x1,y1);
        GL11.glColor4f(rR,gR,bR,aR); GL11.glVertex2f(x2,y1);
        GL11.glColor4f(rR,gR,bR,aR); GL11.glVertex2f(x2,y2);
        GL11.glColor4f(rL,gL,bL,aL); GL11.glVertex2f(x1,y2);
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1,1,1,1);
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
        if (!animations.getValue() || animTimer.hasTimeElapsed(300L)) {
            oldHp = newHp; newHp = hp; maxHp = Math.max(0.001f, mxHp);
            if (oldHp != newHp) animTimer.reset();
        }
        float elapsed = (float) Math.min(animTimer.getElapsedTime(), 300L);
        float animHp  = RenderUtil.lerpFloat(newHp, oldHp, elapsed / 300f);
        float hpRatio = Math.min(1f, Math.max(0f, animHp / maxHp));

        // ── Layout constants ──────────────────────────────────────────────────
        final float HEAD  = 38f;   // head square size
        final float PAD   = 8f;    // general padding
        final float BAR_H = 6f;    // health bar height
        final int   FONT  = mc.fontRendererObj.FONT_HEIGHT;

        String nameStr = target instanceof EntityPlayer
                ? ChatColors.formatColor("&r" + TeamUtil.stripName(target))
                : target.getName();
        String hpStr   = hpFmt.format(animHp) + " / " + hpFmt.format(maxHp);

        // W/L text + color
        String wlStr  = "Even"; int wlCol = 0xFFAAAAAA;
        if (showWL.getValue()) {
            if      (selfHp > hp + 0.5f) { wlStr = "Winning"; wlCol = 0xFF55FF55; }
            else if (selfHp < hp - 0.5f) { wlStr = "Losing";  wlCol = 0xFFFF5555; }
        }

        // Card dimensions
        float nameW   = mc.fontRendererObj.getStringWidth(nameStr);
        float hpW     = mc.fontRendererObj.getStringWidth(hpStr);
        float wlW     = showWL.getValue() ? mc.fontRendererObj.getStringWidth(wlStr) : 0;
        float contentW = Math.max(Math.max(nameW, hpW), wlW > 0 ? wlW : 0) + PAD;
        float cardW   = PAD + HEAD + PAD + contentW + PAD;
        // rows: name, hp, wl (if shown), then bar at bottom
        float textH   = FONT + 3 + FONT + (showWL.getValue() ? 3 + FONT : 0);
        float cardH   = PAD + Math.max(HEAD, textH) + PAD + BAR_H + 3;

        // ── Position ──────────────────────────────────────────────────────────
        ScaledResolution sr = new ScaledResolution(mc);
        float sv = (float) scale.getValue();
        float px = (float) offX.getValue();
        float py = (float) offY.getValue();
        switch (posX.getIndex()) {
            case 1: px += sr.getScaledWidth()  / 2f - (cardW * sv) / 2f; break;
            case 2: px  = sr.getScaledWidth()  - cardW * sv - px;         break;
        }
        switch (posY.getIndex()) {
            case 1: py += sr.getScaledHeight() / 2f - (cardH * sv) / 2f; break;
            case 2: py  = sr.getScaledHeight() - cardH * sv - py;         break;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(px, py, 0f);
        GlStateManager.scale(sv, sv, 1f);

        int bgAlpha = (int) opacity.getValue();
        int bgColor = (bgAlpha << 24) | 0x0D0D0D;

        // ── 1. Drop shadow ────────────────────────────────────────────────────
        rect(-2, -2, cardW + 2, cardH + 2, 0x33000000);

        // ── 2. Main background ────────────────────────────────────────────────
        rect(0, 0, cardW, cardH, bgColor);

        // ── 3. Subtle top accent line (pink) ──────────────────────────────────
        gradRect(0, 0, cardW, 1, 0xAAE991B8, 0x00E991B8);

        // ── 4. Head section — slightly lighter bg ─────────────────────────────
        rect(0, 0, PAD + HEAD + PAD / 2f, cardH, 0x18FFFFFF);

        // ── 5. Vertical separator after head ─────────────────────────────────
        rect(PAD + HEAD + PAD / 2f, 2, PAD + HEAD + PAD / 2f + 1, cardH - 2, 0x33E991B8);

        // ── 6. Health bar track + fill ────────────────────────────────────────
        float barY  = cardH - BAR_H - 1;
        float barX0 = 0;
        float barX1 = cardW;
        rect(barX0, barY, barX1, barY + BAR_H, 0xFF111111);
        if (hpRatio > 0f) {
            float fillW = hpRatio * (barX1 - barX0);
            // Gradient: bright pink → deep purple
            gradRect(barX0, barY, barX0 + fillW, barY + BAR_H, 0xFFE991B8, 0xFF6B2F5C);
            // Shimmer on leading edge
            if (fillW > 6) {
                gradRect(barX0 + fillW - 6, barY, barX0 + fillW, barY + BAR_H, 0x00FFFFFF, 0x44FFFFFF);
            }
        }

        // ── 7. Head texture ───────────────────────────────────────────────────
        ResourceLocation skin = getSkin(target);
        float headX = PAD;
        float headY = (barY - HEAD) / 2f;
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        mc.getTextureManager().bindTexture(skin);
        Gui.drawScaledCustomSizeModalRect((int)headX, (int)headY, 8, 8, 8, 8, (int)HEAD, (int)HEAD, 64, 64);
        Gui.drawScaledCustomSizeModalRect((int)headX, (int)headY, 40, 8, 8, 8, (int)HEAD, (int)HEAD, 64, 64);
        GlStateManager.disableBlend();

        // ── 8. Text ───────────────────────────────────────────────────────────
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        boolean shad = shadowText.getValue();

        float tx   = PAD + HEAD + PAD;
        float ty   = (barY - textH) / 2f;

        // Name — white
        mc.fontRendererObj.drawString(nameStr, (int)tx, (int)ty, 0xFFFFFFFF, shad);

        // HP — pink
        float hpY = ty + FONT + 3;
        mc.fontRendererObj.drawString(hpStr, (int)tx, (int)hpY, 0xFFE991B8, shad);

        // W/L — colored
        if (showWL.getValue()) {
            float wlY = hpY + FONT + 3;
            mc.fontRendererObj.drawString(wlStr, (int)tx, (int)wlY, wlCol, shad);
        }

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
