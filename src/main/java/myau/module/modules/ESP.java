package myau.module.modules;

import myau.Myau;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.Render2DEvent;
import myau.events.Render3DEvent;
import myau.events.ResizeEvent;
import myau.mixin.IAccessorEntityRenderer;
import myau.mixin.IAccessorRenderManager;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import myau.util.shader.GlowShader;
import myau.util.shader.OutlineShader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;

import javax.vecmath.Vector4d;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class ESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final OutlineShader outlineRenderer = new OutlineShader();
    private final GlowShader    glowShader      = new GlowShader();
    private Framebuffer framebuffer = null;
    private boolean outline = true;
    private boolean glow    = true;

    public final DropdownSetting mode      = new DropdownSetting("Mode",       2, "NONE", "2D", "3D", "OUTLINE", "FAKECORNER", "FAKE2D");
    public final DropdownSetting colorMode = new DropdownSetting("Color",      0, "DEFAULT", "TEAMS", "HUD");
    public final DropdownSetting healthBar = new DropdownSetting("Health Bar", 0, "NONE", "2D", "RAVEN");
    public final BooleanSetting  players   = new BooleanSetting("Players",  true);
    public final BooleanSetting  friends   = new BooleanSetting("Friends",  true);
    public final BooleanSetting  enemies   = new BooleanSetting("Enemies",  true);
    public final BooleanSetting  self      = new BooleanSetting("Self",     false);
    public final BooleanSetting  bots      = new BooleanSetting("Bots",     false);

    public ESP() {
        super("ESP", false);
        register(mode); register(colorMode); register(healthBar);
        register(players); register(friends); register(enemies);
        register(self); register(bots);
    }

    public boolean isOutlineEnabled() { return outline; }
    public boolean isGlowEnabled()    { return glow; }

    private boolean shouldRenderPlayer(EntityPlayer p) {
        if (p.deathTime > 0) return false;
        if (mc.getRenderViewEntity().getDistanceToEntity(p) > 512.0F) return false;
        if (p != mc.thePlayer && p != mc.getRenderViewEntity()) {
            if (TeamUtil.isBot(p))    return bots.getValue();
            if (TeamUtil.isFriend(p)) return friends.getValue();
            return TeamUtil.isTarget(p) ? enemies.getValue() : players.getValue();
        }
        return self.getValue() && mc.gameSettings.thirdPersonView != 0;
    }

    private Color getEntityColor(EntityPlayer p) {
        if (TeamUtil.isFriend(p)) return Myau.friendManager.getColor();
        if (TeamUtil.isTarget(p)) return Myau.targetManager.getColor();
        switch (colorMode.getIndex()) {
            case 0: return TeamUtil.getTeamColor(p, 1.0F);
            case 1: return new Color(TeamUtil.isSameTeam(p) ? ChatColors.BLUE.toAwtColor() : ChatColors.RED.toAwtColor());
            case 2: return new Color(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()).getRGB());
            default: return Color.WHITE;
        }
    }

    private List<EntityPlayer> getRendered() {
        return TeamUtil.getLoadedEntitiesSorted().stream()
                .filter(e -> e instanceof EntityPlayer && shouldRenderPlayer((EntityPlayer) e))
                .map(EntityPlayer.class::cast)
                .collect(Collectors.toList());
    }

    @EventTarget
    public void onResize(ResizeEvent event) {
        if (framebuffer != null) framebuffer.deleteFramebuffer();
        framebuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
    }

    @EventTarget(Priority.HIGH)
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled()) return;
        int modeIdx      = mode.getIndex();
        int healthBarIdx = healthBar.getIndex();
        if (modeIdx != 1 && modeIdx != 3 && healthBarIdx != 1) return;

        List<EntityPlayer> rendered = getRendered();
        if (rendered.isEmpty()) return;

        // ── OUTLINE mode ──────────────────────────────────────────────────────
        if (modeIdx == 3) {
            GlStateManager.pushMatrix();
            GlStateManager.pushAttrib();
            if (framebuffer == null)
                framebuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
            // Clear at the START so previous frame content doesn't bleed through
            framebuffer.framebufferClear();
            framebuffer.bindFramebuffer(false);
            ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(event.getPartialTicks(), 0);
            boolean shadow = mc.gameSettings.entityShadows;
            mc.gameSettings.entityShadows = false;
            outline = false; glow = false;
            glowShader.use();
            for (EntityPlayer player : rendered) {
                glowShader.W(getEntityColor(player));
                boolean invisible = player.isInvisible();
                player.setInvisible(false);
                mc.getRenderManager().renderEntityStatic(player, event.getPartialTicks(), true);
                player.setInvisible(invisible);
            }
            glowShader.stop();
            glow = true; outline = true;
            mc.gameSettings.entityShadows = shadow;
            mc.entityRenderer.disableLightmap();
            mc.entityRenderer.setupOverlayRendering();
            mc.getFramebuffer().bindFramebuffer(false);
            outlineRenderer.use();
            RenderUtil.drawFramebuffer(framebuffer);
            outlineRenderer.stop();
            mc.getFramebuffer().bindFramebuffer(false);
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
        }

        // ── 2D box / health bar ───────────────────────────────────────────────
        if (modeIdx == 1 || healthBarIdx == 1) {
            double scaleFactor = new ScaledResolution(mc).getScaleFactor();
            double scale = scaleFactor / Math.pow(scaleFactor, 2.0);
            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, scale);
            for (EntityPlayer player : rendered) {
                ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(event.getPartialTicks(), 0);
                Vector4d screenPos = RenderUtil.projectToScreen(player, scaleFactor);
                mc.entityRenderer.setupOverlayRendering();
                if (screenPos == null) continue;
                float x = (float) screenPos.x, y = (float) screenPos.y;
                float z = (float) screenPos.z, w = (float) screenPos.w;
                RenderUtil.enableRenderState();
                if (modeIdx == 1) {
                    int color = getEntityColor(player).getRGB();
                    RenderUtil.drawOutlineRect(x, y, z, w, 3.0F, 0, (color & 0x00FFFFFF) >> 2 | color & 0xFF000000);
                    RenderUtil.drawOutlineRect(x, y, z, w, 1.5F, 0, color);
                }
                if (healthBarIdx == 1) {
                    float heal    = player.getHealth() + player.getAbsorptionAmount();
                    float percent = Math.min(Math.max(heal / player.getMaxHealth(), 0.0F), 1.0F);
                    float box     = (z - x) * 0.08F;
                    Color hc = ColorUtil.getHealthBlend(percent);
                    RenderUtil.drawLine(x - box, y, x - box, w, 3.0F, ColorUtil.darker(hc, 0.2F).getRGB());
                    RenderUtil.drawLine(x - box, w, x - box, w + (y - w) * percent, 1.5F, hc.getRGB());
                }
                RenderUtil.disableRenderState();
            }
            GlStateManager.popMatrix();
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled()) return;
        int modeIdx      = mode.getIndex();
        int healthBarIdx = healthBar.getIndex();
        if (modeIdx != 2 && modeIdx != 4 && modeIdx != 5 && healthBarIdx != 2) return;

        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();

        for (EntityPlayer player : getRendered()) {
            double ix = RenderUtil.lerpDouble(player.posX, player.lastTickPosX, event.getPartialTicks()) - rm.getRenderPosX();
            double iy = RenderUtil.lerpDouble(player.posY, player.lastTickPosY, event.getPartialTicks()) - rm.getRenderPosY();
            double iz = RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, event.getPartialTicks()) - rm.getRenderPosZ();

            Color color = getEntityColor(player);
            int r = color.getRed(), g = color.getGreen(), b = color.getBlue();

            // ── 3D bounding box ───────────────────────────────────────────────
            if (modeIdx == 2) {
                AxisAlignedBB bb = player.getEntityBoundingBox();
                AxisAlignedBB offsetBB = new AxisAlignedBB(
                        bb.minX - player.posX + ix,
                        bb.minY - player.posY + iy,
                        bb.minZ - player.posZ + iz,
                        bb.maxX - player.posX + ix,
                        bb.maxY - player.posY + iy,
                        bb.maxZ - player.posZ + iz);
                // Isolated state per player — no bleed between iterations
                RenderUtil.enableRenderState();
                RenderUtil.drawBoundingBox(offsetBB, r, g, b, 255, 1.5F);
                RenderUtil.disableRenderState();
            }

            // ── FAKECORNER ────────────────────────────────────────────────────
            if (modeIdx == 4) {
                RenderUtil.enableRenderState();
                RenderUtil.drawCornerESP(player, r / 255.0F, g / 255.0F, b / 255.0F);
                RenderUtil.disableRenderState();
            }

            // ── FAKE2D ────────────────────────────────────────────────────────
            if (modeIdx == 5) {
                RenderUtil.enableRenderState();
                RenderUtil.drawFake2DESP(player, r / 255.0F, g / 255.0F, b / 255.0F);
                RenderUtil.disableRenderState();
            }

            // ── RAVEN health bar ──────────────────────────────────────────────
            if (healthBarIdx == 2) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(ix, iy - 0.1, iz);
                GlStateManager.rotate(mc.getRenderManager().playerViewY * -1.0F, 0.0F, 1.0F, 0.0F);
                float heal    = player.getHealth() + player.getAbsorptionAmount();
                float percent = Math.min(Math.max(heal / player.getMaxHealth(), 0.0F), 1.0F);
                Color hc      = ColorUtil.getHealthBlend(percent);
                float height  = player.height + 0.2F;
                RenderUtil.enableRenderState();
                RenderUtil.drawRect3D(0.57250005F, -0.027500002F, 0.7275F,      height + 0.027500002F, Color.black.getRGB());
                RenderUtil.drawRect3D(0.6F,         0.0F,          0.70000005F, height,                Color.darkGray.getRGB());
                RenderUtil.drawRect3D(0.6F,         0.0F,          0.70000005F, height * percent,      hc.getRGB());
                RenderUtil.disableRenderState();
                GlStateManager.popMatrix();
            }
        }
    }
}