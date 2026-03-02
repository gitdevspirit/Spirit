package myau.module.modules;

import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.BooleanSetting;
import myau.module.SliderSetting;
import myau.module.Module;
import myau.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class ItemESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanSetting outline   = new BooleanSetting("Outline",    true);
    public final SliderSetting  bgOpacity = new SliderSetting("BG Opacity", 67, 0, 100, 1);
    public final BooleanSetting emeralds  = new BooleanSetting("Emeralds",  true);
    public final BooleanSetting diamonds = new BooleanSetting("Diamonds", true);
    public final BooleanSetting gold     = new BooleanSetting("Gold",     true);
    public final BooleanSetting iron     = new BooleanSetting("Iron",     true);

    public ItemESP() {
        super("ItemESP", false);
        register(outline); register(bgOpacity);
        register(emeralds); register(diamonds);
        register(gold);     register(iron);
    }

    // ── Item type helpers ─────────────────────────────────────────────────────

    private boolean shouldShow(int id) {
        return (emeralds.getValue() && isEmerald(id))
            || (diamonds.getValue() && isDiamond(id))
            || (gold.getValue()     && isGold(id))
            || (iron.getValue()     && isIron(id));
    }

    private boolean isEmerald(int id) {
        Item item = Item.getItemById(id);
        Block b = Block.getBlockFromItem(item);
        return item == Items.emerald || b == Blocks.emerald_block || b == Blocks.emerald_ore;
    }

    private boolean isDiamond(int id) {
        Item item = Item.getItemById(id);
        Block b = Block.getBlockFromItem(item);
        return item == Items.diamond
            || item == Items.diamond_sword      || item == Items.diamond_pickaxe
            || item == Items.diamond_shovel     || item == Items.diamond_axe
            || item == Items.diamond_hoe        || item == Items.diamond_helmet
            || item == Items.diamond_chestplate || item == Items.diamond_leggings
            || item == Items.diamond_boots      || b == Blocks.diamond_block
            || b == Blocks.diamond_ore;
    }

    private boolean isGold(int id) {
        Item item = Item.getItemById(id);
        Block b = Block.getBlockFromItem(item);
        return item == Items.gold_ingot   || item == Items.gold_nugget
            || item == Items.golden_apple || b == Blocks.gold_block
            || b == Blocks.gold_ore;
    }

    private boolean isIron(int id) {
        Item item = Item.getItemById(id);
        Block b = Block.getBlockFromItem(item);
        return item == Items.iron_ingot || b == Blocks.iron_block || b == Blocks.iron_ore;
    }

    private Color getColor(int id) {
        if (isEmerald(id)) return new Color(ChatColors.GREEN.toAwtColor());
        if (isDiamond(id)) return new Color(ChatColors.AQUA.toAwtColor());
        if (isGold(id))    return new Color(ChatColors.YELLOW.toAwtColor());
        if (isIron(id))    return new Color(ChatColors.WHITE.toAwtColor());
        return new Color(ChatColors.GRAY.toAwtColor());
    }

    private int getPriority(int id) {
        if (isEmerald(id)) return 4;
        if (isDiamond(id)) return 3;
        if (isGold(id))    return 2;
        if (isIron(id))    return 1;
        return 0;
    }

    /** Short display name for the nametag label */
    private String getLabel(int id) {
        Item item = Item.getItemById(id);
        if (item == Items.emerald)           return "Emerald";
        if (item == Items.diamond)           return "Diamond";
        if (item == Items.golden_apple)      return "Gapple";
        if (item == Items.gold_ingot)        return "Gold";
        if (item == Items.gold_nugget)       return "Gold Nugget";
        if (item == Items.iron_ingot)        return "Iron";
        Block b = Block.getBlockFromItem(item);
        if (b == Blocks.emerald_block)       return "Emerald Block";
        if (b == Blocks.emerald_ore)         return "Emerald Ore";
        if (b == Blocks.diamond_block)       return "Diamond Block";
        if (b == Blocks.diamond_ore)         return "Diamond Ore";
        if (b == Blocks.gold_block)          return "Gold Block";
        if (b == Blocks.gold_ore)            return "Gold Ore";
        if (b == Blocks.iron_block)          return "Iron Block";
        if (b == Blocks.iron_ore)            return "Iron Ore";
        // Diamond gear
        if (item == Items.diamond_sword)      return "Diamond Sword";
        if (item == Items.diamond_pickaxe)    return "Diamond Pick";
        if (item == Items.diamond_axe)        return "Diamond Axe";
        if (item == Items.diamond_shovel)     return "Diamond Shovel";
        if (item == Items.diamond_hoe)        return "Diamond Hoe";
        if (item == Items.diamond_helmet)     return "Diamond Helmet";
        if (item == Items.diamond_chestplate) return "Diamond Chest";
        if (item == Items.diamond_leggings)   return "Diamond Legs";
        if (item == Items.diamond_boots)      return "Diamond Boots";
        return "Item";
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!isEnabled()) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        // Collect + merge items at same block position
        LinkedHashMap<ItemData, Integer> itemMap = new LinkedHashMap<>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity.ticksExisted < 3) continue;
            if (!(entity instanceof EntityItem)) continue;

            EntityItem ei = (EntityItem) entity;
            ItemStack stack = ei.getEntityItem();
            if (stack == null || stack.stackSize <= 0) continue;

            int id = Item.getIdFromItem(stack.getItem());
            if (!shouldShow(id)) continue;

            double x = RenderUtil.lerpDouble(ei.posX, ei.lastTickPosX, event.getPartialTicks());
            double y = RenderUtil.lerpDouble(ei.posY, ei.lastTickPosY, event.getPartialTicks());
            double z = RenderUtil.lerpDouble(ei.posZ, ei.lastTickPosZ, event.getPartialTicks());

            ItemData data = new ItemData(id, x, y, z);
            itemMap.merge(data, stack.stackSize, Integer::sum);
        }

        if (itemMap.isEmpty()) return;

        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();

        // Sort by priority (emerald first)
        List<Map.Entry<ItemData, Integer>> entries = itemMap.entrySet().stream()
            .sorted((a, b) -> Integer.compare(getPriority(b.getKey().itemId), getPriority(a.getKey().itemId)))
            .collect(Collectors.toList());

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        GlStateManager.disableLighting();

        for (Map.Entry<ItemData, Integer> entry : entries) {
            int id    = entry.getKey().itemId;
            int count = entry.getValue();
            Color col = getColor(id);
            int cr = col.getRed(), cg = col.getGreen(), cb = col.getBlue();

            double rx = entry.getKey().x - rm.getRenderPosX();
            double ry = entry.getKey().y - rm.getRenderPosY();
            double rz = entry.getKey().z - rm.getRenderPosZ();

            double dist = mc.getRenderViewEntity().getDistance(
                entry.getKey().x, entry.getKey().y, entry.getKey().z);
            double boxSize = 0.5 + 0.375 * ((Math.max(6.0, dist) - 6.0) / 28.0);

            // Optional outline box
            if (outline.getValue()) {
                AxisAlignedBB bb = new AxisAlignedBB(
                    rx - boxSize * 0.5, ry,           rz - boxSize * 0.5,
                    rx + boxSize * 0.5, ry + boxSize, rz + boxSize * 0.5);
                RenderUtil.enableRenderState();
                RenderUtil.drawBoundingBox(bb, cr, cg, cb, 255, 1.5f);
                RenderUtil.disableRenderState();
            }

            // Nametag: "Diamond 5x"
            String label = getLabel(id) + " " + count + "x";

            GlStateManager.pushMatrix();
            // Position tag above the item
            GlStateManager.translate(rx, ry + boxSize + 0.15, rz);
            // Billboard — face the camera
            GlStateManager.rotate(mc.getRenderManager().playerViewY * -1.0f, 0f, 1f, 0f);
            float flip = mc.gameSettings.thirdPersonView == 2 ? -1f : 1f;
            GlStateManager.rotate(mc.getRenderManager().playerViewX, flip, 0f, 0f);

            // Scale: small at close range, larger further away
            double fs = -(0.025 + 0.02 * ((Math.max(6.0, dist) - 6.0) / 28.0));
            GlStateManager.scale(fs, fs, 1.0);

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GlStateManager.enableBlend();

            float tw = mc.fontRendererObj.getStringWidth(label) / 2f;
            float th = mc.fontRendererObj.FONT_HEIGHT / 2f;

            // Dark background pill
            int bgAlpha = (int)(bgOpacity.getValue() / 100.0 * 255.0);
            int bgColor = (bgAlpha << 24) | 0x000000;
            float pad = 2f;
            drawFlatRect(-tw - pad, -th - pad, tw + pad, th + pad, bgColor);

            // Colored text with shadow
            int textColor = (255 << 24) | (cr << 16) | (cg << 8) | cb;
            mc.fontRendererObj.drawStringWithShadow(label, -tw, -th, textColor);

            GlStateManager.disableBlend();
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GlStateManager.popMatrix();
        }

        GlStateManager.enableLighting();
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    /** Draws a flat (non-3D) rect in the current GL matrix space */
    private void drawFlatRect(float x1, float y1, float x2, float y2, int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >>  8) & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y2); GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1); GL11.glVertex2f(x1, y1);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    // ── ItemData ──────────────────────────────────────────────────────────────

    public static class ItemData {
        private final int hashCode;
        public final int itemId;
        public final double x, y, z;

        public ItemData(int id, double x, double y, double z) {
            this.itemId = id;
            this.x = x; this.y = y; this.z = z;
            this.hashCode = Objects.hash(id, (int) x, (int) y, (int) z);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemData)) return false;
            ItemData d = (ItemData) o;
            return itemId == d.itemId && (int) x == (int) d.x
                && (int) y == (int) d.y && (int) z == (int) d.z;
        }

        @Override public int hashCode() { return hashCode; }
    }
}
