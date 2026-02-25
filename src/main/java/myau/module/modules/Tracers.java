package myau.module.modules;

import myau.Myau;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.events.Render3DEvent;
import myau.mixin.IAccessorMinecraft;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Tracers extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // Color / visibility
    public final DropdownSetting colorMode         = new DropdownSetting("Color Mode",       0, "DEFAULT", "TEAMS", "HUD", "BEDWARS");
    public final BooleanSetting  drawLines         = new BooleanSetting("Draw Lines",        true);
    public final BooleanSetting  drawArrows        = new BooleanSetting("Draw Arrows",       true);
    public final SliderSetting   opacity           = new SliderSetting("Opacity",            85,   0, 100, 1);
    public final SliderSetting   arrowRadius       = new SliderSetting("Arrow Radius",       45,   0, 100, 1);
    public final SliderSetting   arrowSize         = new SliderSetting("Arrow Size",        100,  10, 200, 1);

    // Filters
    public final BooleanSetting  showPlayers       = new BooleanSetting("Show Players",      true);
    public final BooleanSetting  showFriends       = new BooleanSetting("Show Friends",      true);
    public final BooleanSetting  showEnemies       = new BooleanSetting("Show Enemies",      true);
    public final BooleanSetting  showBots          = new BooleanSetting("Show Bots",         false);
    public final BooleanSetting  hideTeammates     = new BooleanSetting("Hide Teammates",    true);
    public final BooleanSetting  enemiesOnly       = new BooleanSetting("Enemies Only",      false);
    public final BooleanSetting  renderOnlyOffScreen = new BooleanSetting("Only Offscreen",  false);
    public final BooleanSetting  renderInGUIs      = new BooleanSetting("In GUIs",           false);
    public final BooleanSetting  showDistance      = new BooleanSetting("Show Distance",     true);

    // Arrow style
    public final DropdownSetting arrowMode         = new DropdownSetting("Arrow Style",      3, "Caret", "Greater Than", "Triangle", "Slinky");

    // BedWars team colors
    private static final Map<String, Color> BEDWARS_TEAM_COLORS = new HashMap<>();
    static {
        BEDWARS_TEAM_COLORS.put("Red",     new Color(255, 50,  50));
        BEDWARS_TEAM_COLORS.put("Blue",    new Color(50,  80,  255));
        BEDWARS_TEAM_COLORS.put("Green",   new Color(50,  200, 50));
        BEDWARS_TEAM_COLORS.put("Yellow",  new Color(255, 220, 30));
        BEDWARS_TEAM_COLORS.put("Aqua",    new Color(50,  220, 220));
        BEDWARS_TEAM_COLORS.put("White",   new Color(230, 230, 230));
        BEDWARS_TEAM_COLORS.put("Pink",    new Color(255, 100, 180));
        BEDWARS_TEAM_COLORS.put("Gray",    new Color(130, 130, 130));
        BEDWARS_TEAM_COLORS.put("Orange",  new Color(255, 140, 20));
        BEDWARS_TEAM_COLORS.put("Purple",  new Color(160, 50,  220));
        BEDWARS_TEAM_COLORS.put("Maroon",  new Color(180, 30,  30));
        BEDWARS_TEAM_COLORS.put("Teal",    new Color(30,  180, 150));
        BEDWARS_TEAM_COLORS.put("Lime",    new Color(120, 255, 30));
        BEDWARS_TEAM_COLORS.put("Brown",   new Color(140, 80,  30));
        BEDWARS_TEAM_COLORS.put("Silver",  new Color(180, 180, 180));
        BEDWARS_TEAM_COLORS.put("Crimson", new Color(200, 20,  60));
    }

    public Tracers() {
        super("Tracers", false);
        register(colorMode);
        register(drawLines);
        register(drawArrows);
        register(opacity);
        register(arrowRadius);
        register(arrowSize);
        register(showPlayers);
        register(showFriends);
        register(showEnemies);
        register(showBots);
        register(hideTeammates);
        register(enemiesOnly);
        register(renderOnlyOffScreen);
        register(renderInGUIs);
        register(showDistance);
        register(arrowMode);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Color getBedWarsTeamColor(EntityPlayer player, float alpha) {
        if (mc.theWorld == null) return null;
        Scoreboard sb = mc.theWorld.getScoreboard();
        ScorePlayerTeam team = sb.getPlayersTeam(player.getName());
        if (team == null) return null;
        String teamName = team.getTeamName();
        for (Map.Entry<String, Color> entry : BEDWARS_TEAM_COLORS.entrySet()) {
            if (teamName.toLowerCase().contains(entry.getKey().toLowerCase())) {
                Color c = entry.getValue();
                return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int)(alpha * 255.0F));
            }
        }
        String prefix = team.getColorPrefix();
        if (prefix != null && !prefix.isEmpty()) {
            return TeamUtil.getTeamColor(player, alpha);
        }
        return null;
    }

    private boolean shouldRender(EntityPlayer entityPlayer) {
        if (entityPlayer.deathTime > 0) return false;
        if (mc.getRenderViewEntity().getDistanceToEntity(entityPlayer) > 512.0F) return false;
        if (entityPlayer == mc.thePlayer || entityPlayer == mc.getRenderViewEntity()) return false;
        if (TeamUtil.isBot(entityPlayer) && !showBots.getValue()) return false;
        if (TeamUtil.isSameTeam(entityPlayer) && hideTeammates.getValue()) return false;
        if (TeamUtil.isFriend(entityPlayer) && !showFriends.getValue()) return false;
        if (TeamUtil.isTarget(entityPlayer) && !showEnemies.getValue()) return false;
        if (!TeamUtil.isTarget(entityPlayer) && !showPlayers.getValue()) return false;
        if (enemiesOnly.getValue() && !TeamUtil.isTarget(entityPlayer)) return false;
        return true;
    }

    private Color getEntityColor(EntityPlayer entityPlayer, float alpha) {
        if (TeamUtil.isFriend(entityPlayer)) {
            Color color = Myau.friendManager.getColor();
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(alpha * 255));
        } else if (TeamUtil.isTarget(entityPlayer)) {
            Color color = Myau.targetManager.getColor();
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(alpha * 255));
        } else {
            switch (colorMode.getIndex()) {
                case 0: // DEFAULT
                    return TeamUtil.getTeamColor(entityPlayer, alpha);
                case 1: { // TEAMS
                    int teamColor = TeamUtil.isSameTeam(entityPlayer)
                            ? ChatColors.BLUE.toAwtColor()
                            : ChatColors.RED.toAwtColor();
                    return new Color(teamColor & Color.WHITE.getRGB() | (int)(alpha * 255.0F) << 24, true);
                }
                case 2: { // HUD
                    int color = ((HUD) Myau.moduleManager.modules.get(HUD.class))
                            .getColor(System.currentTimeMillis()).getRGB();
                    return new Color(color & Color.WHITE.getRGB() | (int)(alpha * 255.0F) << 24, true);
                }
                case 3: { // BEDWARS
                    Color bwColor = getBedWarsTeamColor(entityPlayer, alpha);
                    if (bwColor != null) return bwColor;
                    return TeamUtil.getTeamColor(entityPlayer, alpha);
                }
                default:
                    return new Color(1.0F, 1.0F, 1.0F, alpha);
            }
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || !drawLines.getValue()) return;
        RenderUtil.enableRenderState();

        Vec3 position;
        if (mc.gameSettings.thirdPersonView == 0) {
            position = new Vec3(0.0, 0.0, 1.0)
                    .rotatePitch((float)(-Math.toRadians(RenderUtil.lerpFloat(
                            mc.getRenderViewEntity().rotationPitch,
                            mc.getRenderViewEntity().prevRotationPitch,
                            ((IAccessorMinecraft) mc).getTimer().renderPartialTicks))))
                    .rotateYaw((float)(-Math.toRadians(RenderUtil.lerpFloat(
                            mc.getRenderViewEntity().rotationYaw,
                            mc.getRenderViewEntity().prevRotationYaw,
                            ((IAccessorMinecraft) mc).getTimer().renderPartialTicks))));
        } else {
            position = new Vec3(0.0, 0.0, 0.0)
                    .rotatePitch((float)(-Math.toRadians(RenderUtil.lerpFloat(
                            mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch,
                            ((IAccessorMinecraft) mc).getTimer().renderPartialTicks))))
                    .rotateYaw((float)(-Math.toRadians(RenderUtil.lerpFloat(
                            mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw,
                            ((IAccessorMinecraft) mc).getTimer().renderPartialTicks))));
        }
        position = new Vec3(position.xCoord,
                position.yCoord + mc.getRenderViewEntity().getEyeHeight(),
                position.zCoord);

        float alpha = (float) opacity.getValue() / 100.0F;

        for (EntityPlayer player : TeamUtil.getLoadedEntitiesSorted().stream()
                .filter(e -> e instanceof EntityPlayer && shouldRender((EntityPlayer) e))
                .map(EntityPlayer.class::cast)
                .collect(Collectors.toList())) {
            Color color = getEntityColor(player, alpha);
            double x = RenderUtil.lerpDouble(player.posX, player.lastTickPosX, event.getPartialTicks());
            double y = RenderUtil.lerpDouble(player.posY, player.lastTickPosY, event.getPartialTicks())
                    - (player.isSneaking() ? 0.125 : 0.0);
            double z = RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, event.getPartialTicks());
            RenderUtil.drawLine3D(position,
                    x, y + player.getEyeHeight(), z,
                    color.getRed()   / 255.0F,
                    color.getGreen() / 255.0F,
                    color.getBlue()  / 255.0F,
                    color.getAlpha() / 255.0F,
                    1.5F);
        }

        RenderUtil.disableRenderState();
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!isEnabled() || !drawArrows.getValue()) return;
        if (mc.currentScreen != null && !renderInGUIs.getValue()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        float hudScale = (float) hud.scale.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.scale(hudScale, hudScale, 1.0F);
        GlStateManager.translate(sr.getScaledWidth() / 2.0F / hudScale,
                                 sr.getScaledHeight() / 2.0F / hudScale, 0.0F);

        float opacityVal = (float) opacity.getValue() / 100.0F;
        float sizeScale  = (float) arrowSize.getValue() / 100.0F;
        float percent    = (float) arrowRadius.getValue();
        float r          = 30.0f + (percent / 100.0f) * 170.0f;

        for (EntityPlayer player : TeamUtil.getLoadedEntitiesSorted().stream()
                .filter(e -> e instanceof EntityPlayer && shouldRender((EntityPlayer) e))
                .map(EntityPlayer.class::cast)
                .collect(Collectors.toList())) {

            float yawBetween = RotationUtil.getYawBetween(
                    RenderUtil.lerpDouble(mc.thePlayer.posX, mc.thePlayer.prevPosX, event.getPartialTicks()),
                    RenderUtil.lerpDouble(mc.thePlayer.posZ, mc.thePlayer.prevPosZ, event.getPartialTicks()),
                    RenderUtil.lerpDouble(player.posX, player.prevPosX, event.getPartialTicks()),
                    RenderUtil.lerpDouble(player.posZ, player.prevPosZ, event.getPartialTicks()));
            if (mc.gameSettings.thirdPersonView == 2) yawBetween += 180.0F;

            float arrowDirX = (float)  Math.sin(Math.toRadians(yawBetween));
            float arrowDirY = (float) (Math.cos(Math.toRadians(yawBetween)) * -1.0F);

            float thisOpacity = opacityVal;
            float absYaw = Math.abs(MathHelper.wrapAngleTo180_float(yawBetween - mc.thePlayer.rotationYawHead));

            if (absYaw < 30.0F) {
                thisOpacity = 0.0F;
            } else if (absYaw < 60.0F) {
                thisOpacity *= (absYaw - 30.0F) / 30.0F;
            }

            if (thisOpacity <= 0.01F) continue;
            if (renderOnlyOffScreen.getValue() && absYaw < 90.0F) continue;

            Color fillColor = getEntityColor(player, 1.0F);
            float red   = fillColor.getRed()   / 255.0f;
            float green = fillColor.getGreen() / 255.0f;
            float blue  = fillColor.getBlue()  / 255.0f;
            float alpha = thisOpacity;

            float rotation = (float)(Math.atan2(arrowDirY, arrowDirX) * (180.0 / Math.PI) + 90.0);

            GlStateManager.pushMatrix();
            GlStateManager.translate(r * arrowDirX, r * arrowDirY, 0.0F);
            GlStateManager.rotate(rotation, 0.0F, 0.0F, 1.0F);
            GlStateManager.scale(sizeScale, sizeScale, 1.0F);

            if (arrowMode.getIndex() == 3) { // Slinky
                final float halfWidth = 16.0F;
                final float height    = 22.0F;
                final float midOffset = 6.0F;

                GL11.glEnable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                GL11.glColor4f(red, green, blue, alpha);
                GL11.glBegin(GL11.GL_TRIANGLES);
                GL11.glVertex2f(0.0F, -height);
                GL11.glVertex2f(-halfWidth, 0.0F);
                GL11.glVertex2f(halfWidth, 0.0F);
                GL11.glEnd();

                GL11.glColor4f(Math.min(1.0f, red + 0.15f), Math.min(1.0f, green + 0.15f), Math.min(1.0f, blue + 0.15f), alpha * 0.6f);
                GL11.glBegin(GL11.GL_TRIANGLES);
                GL11.glVertex2f(0.0F, -height + 4.0F);
                GL11.glVertex2f(-halfWidth + midOffset, midOffset);
                GL11.glVertex2f(halfWidth - midOffset, midOffset);
                GL11.glEnd();

                float darken = 0.40f;
                GL11.glColor4f(red * darken, green * darken, blue * darken, alpha);
                GL11.glLineWidth(1.2F);
                GL11.glBegin(GL11.GL_LINE_LOOP);
                GL11.glVertex2f(0.0F, -height);
                GL11.glVertex2f(-halfWidth, 0.0F);
                GL11.glVertex2f(halfWidth, 0.0F);
                GL11.glEnd();

                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_BLEND);
            }

            GlStateManager.popMatrix();

            if (showDistance.getValue()) {
                String dist = (int) mc.thePlayer.getDistanceToEntity(player) + "m";
                GlStateManager.pushMatrix();
                GlStateManager.translate(r * arrowDirX, r * arrowDirY - 18.0F * sizeScale, 0.0F);
                GlStateManager.scale(0.75F * sizeScale, 0.75F * sizeScale, 1.0F);
                mc.fontRendererObj.drawString(dist,
                        -mc.fontRendererObj.getStringWidth(dist) / 2.0F,
                        -4.0F, fillColor.getRGB(), true);
                GlStateManager.popMatrix();
            }
        }

        GlStateManager.popMatrix();
    }
}