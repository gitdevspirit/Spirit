package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.ui.intel.IntelManager;
import myau.ui.intel.IntelPlayer;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Renders a BedWars star tag above players' heads using stats
 * already cached in IntelManager — no extra API calls needed.
 */
public class BedWarsTag extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanSetting showStar   = register(new BooleanSetting("Show Star",    true));
    public final BooleanSetting showFkdr   = register(new BooleanSetting("Show FKDR",    false));
    public final BooleanSetting showThreat = register(new BooleanSetting("Show Threat",  false));
    public final BooleanSetting selfTag    = register(new BooleanSetting("Show Self",     false));
    public final BooleanSetting autoScale  = register(new BooleanSetting("Auto Scale",    true));
    public final SliderSetting  scale      = register(new SliderSetting("Scale",          1.0, 0.5, 2.0, 0.05));
    public final BooleanSetting background = register(new BooleanSetting("Background",    true));
    public final BooleanSetting onlyIntel  = register(new BooleanSetting("Intel Only",    false));

    public BedWarsTag() { super("BedWarsTag", false); }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || mc.theWorld == null || mc.thePlayer == null) return;

        List<IntelPlayer> intelPlayers = IntelManager.getInstance().getPlayers();
        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityPlayer)) continue;
            EntityPlayer player = (EntityPlayer) entity;

            if (!selfTag.getValue() && player == mc.thePlayer) continue;
            if (player.deathTime > 0) continue;
            if (mc.getRenderViewEntity().getDistanceToEntity(player) > 64f) continue;

            // Look up intel data
            IntelPlayer intel = null;
            for (IntelPlayer p : intelPlayers) {
                if (p.name.equalsIgnoreCase(player.getName())) { intel = p; break; }
            }

            // If intel-only mode and no data yet, skip
            if (onlyIntel.getValue() && (intel == null || intel.loading)) continue;

            // Build tag string
            String tag = buildTag(intel);
            if (tag == null || tag.isEmpty()) continue;

            // ── 3D billboard setup (same as NameTags) ─────────────────────────
            double px = RenderUtil.lerpDouble(player.posX, player.lastTickPosX, event.getPartialTicks()) - rm.getRenderPosX();
            double py = RenderUtil.lerpDouble(player.posY, player.lastTickPosY, event.getPartialTicks()) - rm.getRenderPosY();
            double pz = RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, event.getPartialTicks()) - rm.getRenderPosZ();
            double dist = mc.getRenderViewEntity().getDistanceToEntity(player);

            // Position above head — offset above vanilla nametag
            double nametagY = py + player.getEyeHeight() + (player.isSneaking() ? 0.225 : 0.4);
            double tagOffset = 0.28; // how far above the nametag

            GlStateManager.pushMatrix();
            GlStateManager.translate(px, nametagY + tagOffset, pz);

            // Billboard — face camera
            GlStateManager.rotate(mc.getRenderManager().playerViewY * -1f, 0f, 1f, 0f);
            float view = mc.gameSettings.thirdPersonView == 2 ? -1f : 1f;
            GlStateManager.rotate(mc.getRenderManager().playerViewX, view, 0f, 0f);

            // Scale — auto-scale with distance like NameTags does
            double tagScale = Math.pow(Math.min(Math.max(
                    autoScale.getValue() ? dist : 6.0, 6.0), 128.0), 0.75)
                    * 0.0065 * scale.getValue();
            GlStateManager.scale(-tagScale, -tagScale, 1.0);

            int tagColor = getTagColor(intel);
            int w = mc.fontRendererObj.getStringWidth(tag);
            float tx = -w / 2f;
            float ty = -(float) mc.fontRendererObj.FONT_HEIGHT;

            // Background
            if (background.getValue()) {
                RenderUtil.enableRenderState();
                RenderUtil.drawRect(tx - 1, ty - 1, tx + w + 1, 0, 0x55000000);
                RenderUtil.disableRenderState();
            }

            GlStateManager.disableDepth();
            mc.fontRendererObj.drawString(tag, tx, ty, tagColor, true);
            GlStateManager.enableDepth();

            GlStateManager.popMatrix();
        }
    }

    private String buildTag(IntelPlayer intel) {
        if (intel == null || intel.loading) {
            // Show placeholder if not intel-only
            return showStar.getValue() ? "☆?" : null;
        }

        StringBuilder sb = new StringBuilder();

        if (showStar.getValue()) {
            sb.append("☆").append(intel.star);
        }

        if (showFkdr.getValue()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(String.format("%.1f", intel.fkdr));
        }

        if (showThreat.getValue()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append((int) intel.threatScore);
        }

        return sb.toString();
    }

    private int getTagColor(IntelPlayer intel) {
        if (intel == null || intel.loading) return 0xFFAAAAAA;

        // Use prestige color for the star
        if (showStar.getValue()) return prestigeColor(intel.star);

        // Threat color if showing threat
        if (showThreat.getValue()) return threatColor((int) intel.threatScore);

        return 0xFFFFFFFF;
    }

    private int prestigeColor(int s) {
        if (s < 100)  return 0xFFAAAAAA;
        if (s < 200)  return 0xFFFFFFFF;
        if (s < 300)  return 0xFFFFAA00;
        if (s < 400)  return 0xFF55FFFF;
        if (s < 500)  return 0xFF55FF55;
        if (s < 600)  return 0xFF55FFFF;
        if (s < 700)  return 0xFFFF5555;
        if (s < 800)  return 0xFFFF55FF;
        if (s < 900)  return 0xFF5555FF;
        if (s < 1000) return 0xFFAA00AA;
        if (s < 1100) return 0xFFFFAA00;
        if (s < 2000) return 0xFFAAAAAA;
        if (s < 2100) return 0xFFFFAA00;
        if (s < 2200) return 0xFFFFAA00;
        if (s < 2300) return 0xFFFF55FF;
        if (s < 2400) return 0xFF00AAAA;
        if (s < 2500) return 0xFFFFFFFF;
        if (s < 2600) return 0xFFFF5555;
        if (s < 2700) return 0xFFFFFF55;
        if (s < 2800) return 0xFF55FF55;
        if (s < 2900) return 0xFF55FFFF;
        if (s < 3000) return 0xFFFFAA00;
        if (s < 3100) return 0xFFFFAA00;
        if (s < 3200) return 0xFF5555FF;
        if (s < 3300) return 0xFFFF5555;
        if (s < 3400) return 0xFFAA0000;
        if (s < 3500) return 0xFF55FFFF;
        if (s < 3600) return 0xFF55FFFF;
        if (s < 3700) return 0xFF55FF55;
        if (s < 3800) return 0xFFFF7700;
        if (s < 3900) return 0xFF0000AA;
        if (s < 4000) return 0xFFFF5577;
        if (s < 4100) return 0xFF55FF55;
        if (s < 4200) return 0xFFFFFF55;
        if (s < 4300) return 0xFF0000AA;
        if (s < 4400) return 0xFF333333;
        if (s < 4500) return 0xFFFF55FF;
        if (s < 4600) return 0xFFFFFFFF;
        if (s < 4700) return 0xFF55FFFF;
        if (s < 4800) return 0xFFAAFFFF;
        if (s < 4900) return 0xFFAA00AA;
        if (s < 5000) return 0xFFFF5555;
        return 0xFF5555FF;
    }

    private int threatColor(int score) {
        if (score >= 75) return 0xFFFF2244;
        if (score >= 50) return 0xFFFF7722;
        if (score >= 25) return 0xFFFFCC22;
        return 0xFF44DD66;
    }
}
