package myau.module.modules;

import myau.Myau;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.Render2DEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.stream.Collectors;

public class Radar extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final DropdownSetting colorMode   = new DropdownSetting("Color",        0, "DEFAULT", "TEAMS", "HUD");
    public final SliderSetting   position    = new SliderSetting("Position",        0, 0, 4, 1);
    public final SliderSetting   offsetX     = new SliderSetting("Offset X",       60, 0, 1000, 1);
    public final SliderSetting   offsetY     = new SliderSetting("Offset Y",       60, 0, 1000, 1);
    public final SliderSetting   radarRadius = new SliderSetting("Radar Radius",   55, 10, 200, 1);
    public final SliderSetting   dotRadius   = new SliderSetting("Dot Radius",    1.5, 0.1, 5.0, 0.1);
    public final BooleanSetting  showPlayers = new BooleanSetting("Players",       true);
    public final BooleanSetting  showFriends = new BooleanSetting("Friends",       true);
    public final BooleanSetting  showEnemies = new BooleanSetting("Enemies",       true);
    public final BooleanSetting  showBots    = new BooleanSetting("Bots",          false);
    public final BooleanSetting  showPVP     = new BooleanSetting("Show PVP",      false);
    // ColorProperty workaround: R/G/B sliders per color
    public final SliderSetting   fillR       = new SliderSetting("Fill R",        128, 0, 255, 1);
    public final SliderSetting   fillG       = new SliderSetting("Fill G",        128, 0, 255, 1);
    public final SliderSetting   fillB       = new SliderSetting("Fill B",        128, 0, 255, 1);
    public final SliderSetting   outlineR    = new SliderSetting("Outline R",      64, 0, 255, 1);
    public final SliderSetting   outlineG    = new SliderSetting("Outline G",      64, 0, 255, 1);
    public final SliderSetting   outlineB    = new SliderSetting("Outline B",      64, 0, 255, 1);
    public final SliderSetting   crossR      = new SliderSetting("Cross R",       192, 0, 255, 1);
    public final SliderSetting   crossG      = new SliderSetting("Cross G",       192, 0, 255, 1);
    public final SliderSetting   crossB      = new SliderSetting("Cross B",       192, 0, 255, 1);

    public Radar() {
        super("Radar", false);
        register(colorMode); register(position); register(offsetX); register(offsetY);
        register(radarRadius); register(dotRadius);
        register(showPlayers); register(showFriends); register(showEnemies);
        register(showBots); register(showPVP);
        register(fillR); register(fillG); register(fillB);
        register(outlineR); register(outlineG); register(outlineB);
        register(crossR); register(crossG); register(crossB);
    }

    private boolean shouldRender(EntityPlayer p) {
        if (p.deathTime > 0) return false;
        if (mc.getRenderViewEntity().getDistanceToEntity(p) > 512.0F) return false;
        if (p == mc.thePlayer || p == mc.getRenderViewEntity()) return false;
        if (TeamUtil.isBot(p))    return showBots.getValue();
        if (TeamUtil.isFriend(p)) return showFriends.getValue();
        return TeamUtil.isTarget(p) ? showEnemies.getValue() : showPlayers.getValue();
    }

    private Color getEntityColor(EntityPlayer p) {
        if (TeamUtil.isFriend(p)) {
            Color c = Myau.friendManager.getColor();
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
        }
        if (TeamUtil.isTarget(p)) {
            Color c = Myau.targetManager.getColor();
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
        }
        switch (colorMode.getIndex()) {
            case 0: return TeamUtil.getTeamColor(p, 1.0F);
            case 1: return new Color(TeamUtil.isSameTeam(p) ? ChatColors.BLUE.toAwtColor() : ChatColors.RED.toAwtColor() | 0xFF000000, true);
            case 2: return new Color(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()).getRGB() | 0xFF000000, true);
            default: return Color.WHITE;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onRender(Render2DEvent event) {
        if (!isEnabled()) return;
        ScaledResolution sr = new ScaledResolution(mc);
        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        int pos = (int) position.getValue();
        double centerX, centerY;
        if (pos == 4) {
            centerX = sr.getScaledWidth() / 2.0;
            centerY = sr.getScaledHeight() / 2.0;
        } else {
            centerX = (pos & 0x1) == 0x1 ? Math.max(sr.getScaledWidth() - offsetX.getValue(), 0) : Math.min(offsetX.getValue(), sr.getScaledWidth());
            centerY = (pos & 0x2) == 0x2 ? Math.max(sr.getScaledHeight() - offsetY.getValue(), 0) : Math.min(offsetY.getValue(), sr.getScaledHeight());
        }
        GlStateManager.pushMatrix();
        GlStateManager.scale(hud.scale.getValue(), hud.scale.getValue(), 1.0f);
        GlStateManager.translate(centerX, centerY, 0.0f);
        RenderUtil.enableRenderState();

        float yaw = (float) Math.toRadians(mc.thePlayer.rotationYaw);
        if (mc.gameSettings.thirdPersonView != 2) yaw += (float) Math.toRadians(180.0F);
        double cos = Math.cos(yaw), sin = Math.sin(yaw);

        int fColor = new Color((int)fillR.getValue(), (int)fillG.getValue(), (int)fillB.getValue(), 100).getRGB();
        int oColor = new Color((int)outlineR.getValue(), (int)outlineG.getValue(), (int)outlineB.getValue(), 255).getRGB();
        int cColor = new Color((int)crossR.getValue(), (int)crossG.getValue(), (int)crossB.getValue(), 255).getRGB();
        drawRadarCircle(0, 0, yaw, radarRadius.getValue(), 64, fColor, oColor, cColor);

        for (EntityPlayer player : TeamUtil.getLoadedEntitiesSorted().stream()
                .filter(e -> e instanceof EntityPlayer && shouldRender((EntityPlayer) e))
                .map(EntityPlayer.class::cast).collect(Collectors.toList())) {
            double dx   = (player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks()) - mc.thePlayer.posX;
            double dz   = (player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks()) - mc.thePlayer.posZ;
            double relX = dx * cos + dz * sin;
            double relY = dz * cos - dx * sin;
            double dist  = Math.sqrt(relX * relX + relY * relY);
            double scale = dist < radarRadius.getValue() ? 1.0 : radarRadius.getValue() / dist;
            RenderUtil.fillCircle(relX * scale, relY * scale, dotRadius.getValue(), 12, getEntityColor(player).getRGB());
        }

        if (showPVP.getValue()) {
            double dx   = -mc.thePlayer.posX, dz = -mc.thePlayer.posZ;
            double relX = dx * cos + dz * sin, relY = dz * cos - dx * sin;
            double dist  = Math.sqrt(relX * relX + relY * relY);
            double scale = dist < radarRadius.getValue() * 2 ? 1.0 : radarRadius.getValue() * 2 / dist;
            double px = relX * scale, py = relY * scale;
            GlStateManager.pushMatrix();
            GlStateManager.disableDepth(); GlStateManager.enableBlend(); GlStateManager.enableTexture2D();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.scale(hud.scale.getValue() / 2, hud.scale.getValue() / 2, 1.0f);
            mc.fontRendererObj.drawString("PVP",
                    (float)(px - mc.fontRendererObj.getStringWidth("PVP") / 2.0),
                    (float)(py - mc.fontRendererObj.FONT_HEIGHT / 2.0),
                    Color.WHITE.getRGB(), hud.shadow.getValue());
            GlStateManager.popMatrix();
        }

        RenderUtil.disableRenderState();
        GlStateManager.popMatrix();
    }

    private void drawRadarCircle(double x, double y, double angle, double radius,
                                 int segments, int fillColor, int outlineColor, int crossColor) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        if ((fillColor >>> 24) != 0) {
            RenderUtil.setColor(fillColor);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2d(x, y);
            for (int i = 0; i <= segments; i++) {
                double a = i * (Math.PI * 2 / segments);
                GL11.glVertex2d(x + Math.cos(a) * radius, y + Math.sin(a) * radius);
            }
            GL11.glEnd();
        }
        if ((outlineColor >>> 24) != 0) {
            RenderUtil.setColor(outlineColor);
            GL11.glLineWidth(2f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i <= segments; i++) {
                double a = i * (Math.PI * 2 / segments);
                GL11.glVertex2d(x + Math.cos(a) * radius, y + Math.sin(a) * radius);
            }
            GL11.glEnd();
        }
        if ((crossColor >>> 24) != 0) {
            RenderUtil.setColor(crossColor);
            GL11.glLineWidth(1.5f);
            GL11.glBegin(GL11.GL_LINES);
            double dx1 = Math.sin(angle), dy1 = Math.cos(angle);
            double dx2 = Math.sin(angle + Math.PI / 2), dy2 = Math.cos(angle + Math.PI / 2);
            GL11.glVertex2d(x - dx1 * radius, y - dy1 * radius); GL11.glVertex2d(x + dx1 * radius, y + dy1 * radius);
            GL11.glVertex2d(x - dx2 * radius, y - dy2 * radius); GL11.glVertex2d(x + dx2 * radius, y + dy2 * radius);
            GL11.glEnd();

            GlStateManager.disableDepth(); GlStateManager.enableBlend(); GlStateManager.enableTexture2D();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
            int color = hud.getColor(System.currentTimeMillis()).getRGB();
            mc.fontRendererObj.drawString("N", (float)(x - dx1*(radius+5)) - mc.fontRendererObj.getStringWidth("N")/2.0F, (float)(y - dy1*(radius+5)) - mc.fontRendererObj.FONT_HEIGHT/2.0F, color, hud.shadow.getValue());
            mc.fontRendererObj.drawString("E", (float)(x + dx2*(radius+5)) - mc.fontRendererObj.getStringWidth("E")/2.0F, (float)(y + dy2*(radius+5)) - mc.fontRendererObj.FONT_HEIGHT/2.0F, color, hud.shadow.getValue());
            mc.fontRendererObj.drawString("S", (float)(x + dx1*(radius+5)) - mc.fontRendererObj.getStringWidth("S")/2.0F, (float)(y + dy1*(radius+5)) - mc.fontRendererObj.FONT_HEIGHT/2.0F, color, hud.shadow.getValue());
            mc.fontRendererObj.drawString("W", (float)(x - dx2*(radius+5)) - mc.fontRendererObj.getStringWidth("W")/2.0F, (float)(y - dy2*(radius+5)) - mc.fontRendererObj.FONT_HEIGHT/2.0F, color, hud.shadow.getValue());
            GlStateManager.disableTexture2D(); GlStateManager.disableBlend(); GlStateManager.enableDepth();
        }
        GlStateManager.enableTexture2D(); GlStateManager.disableBlend(); GlStateManager.resetColor();
    }
}