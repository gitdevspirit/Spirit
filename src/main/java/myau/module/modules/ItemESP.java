package myau.module.modules;

import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
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
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

public class ItemESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting  opacity   = new SliderSetting("Opacity",     50,  0, 100, 1);
    public final BooleanSetting outline   = new BooleanSetting("Outline",    true);
    public final BooleanSetting itemCount = new BooleanSetting("Item Count", true);
    public final BooleanSetting autoScale = new BooleanSetting("Auto Scale", true);
    public final BooleanSetting emeralds  = new BooleanSetting("Emeralds",   true);
    public final BooleanSetting diamonds  = new BooleanSetting("Diamonds",   true);
    public final BooleanSetting gold      = new BooleanSetting("Gold",       true);
    public final BooleanSetting iron      = new BooleanSetting("Iron",       true);

    public ItemESP() {
        super("ItemESP", false);
        register(opacity); register(outline); register(itemCount);
        register(autoScale); register(emeralds); register(diamonds);
        register(gold); register(iron);
    }

    // ── Item type checks ─────────────────────────────────────────────────────

    private boolean shouldHighlightItem(int itemId) {
        return (emeralds.getValue() && isEmeraldItem(itemId))
            || (diamonds.getValue() && isDiamondItem(itemId))
            || (gold.getValue()     && isGoldItem(itemId))
            || (iron.getValue()     && isIronItem(itemId));
    }

    private boolean isEmeraldItem(int itemId) {
        Item item = Item.getItemById(itemId);
        Block block = Block.getBlockFromItem(item);
        return item == Items.emerald || block == Blocks.emerald_block || block == Blocks.emerald_ore;
    }

    private boolean isDiamondItem(int itemId) {
        Item item = Item.getItemById(itemId);
        Block block = Block.getBlockFromItem(item);
        return item == Items.diamond
            || item == Items.diamond_sword      || item == Items.diamond_pickaxe
            || item == Items.diamond_shovel     || item == Items.diamond_axe
            || item == Items.diamond_hoe        || item == Items.diamond_helmet
            || item == Items.diamond_chestplate || item == Items.diamond_leggings
            || item == Items.diamond_boots      || block == Blocks.diamond_block
            || block == Blocks.diamond_ore;
    }

    private boolean isGoldItem(int itemId) {
        Item item = Item.getItemById(itemId);
        Block block = Block.getBlockFromItem(item);
        return item == Items.gold_ingot || item == Items.gold_nugget
            || item == Items.golden_apple || block == Blocks.gold_block
            || block == Blocks.gold_ore;
    }

    private boolean isIronItem(int itemId) {
        Item item = Item.getItemById(itemId);
        Block block = Block.getBlockFromItem(item);
        return item == Items.iron_ingot || block == Blocks.iron_block || block == Blocks.iron_ore;
    }

    private Color getItemColor(int itemId) {
        if (isEmeraldItem(itemId)) return new Color(ChatColors.GREEN.toAwtColor());
        if (isDiamondItem(itemId)) return new Color(ChatColors.AQUA.toAwtColor());
        if (isGoldItem(itemId))    return new Color(ChatColors.YELLOW.toAwtColor());
        if (isIronItem(itemId))    return new Color(ChatColors.WHITE.toAwtColor());
        return new Color(ChatColors.GRAY.toAwtColor());
    }

    private int getItemPriority(int itemId) {
        if (isEmeraldItem(itemId)) return 4;
        if (isDiamondItem(itemId)) return 3;
        if (isGoldItem(itemId))    return 2;
        if (isIronItem(itemId))    return 1;
        return 0;
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!isEnabled()) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        LinkedHashMap<ItemData, Integer> itemMap = new LinkedHashMap<>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity.ticksExisted < 3) continue;
            if (!(entity instanceof EntityItem)) continue;

            EntityItem entityItem = (EntityItem) entity;
            ItemStack stack = entityItem.getEntityItem();
            if (stack == null || stack.stackSize <= 0) continue;

            int itemId = Item.getIdFromItem(stack.getItem());
            if (!shouldHighlightItem(itemId)) continue;

            double x = RenderUtil.lerpDouble(entityItem.posX, entityItem.lastTickPosX, event.getPartialTicks());
            double y = RenderUtil.lerpDouble(entityItem.posY, entityItem.lastTickPosY, event.getPartialTicks());
            double z = RenderUtil.lerpDouble(entityItem.posZ, entityItem.lastTickPosZ, event.getPartialTicks());
            ItemData data = new ItemData(itemId, x, y, z);
            Integer existing = itemMap.get(data);
            itemMap.put(data, stack.stackSize + (existing == null ? 0 : existing));
        }

        if (itemMap.isEmpty()) return;

        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();
        int alphaFill = (int)(opacity.getValue() / 100.0 * 255.0);

        // Push GL state once for all items
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        GlStateManager.disableLighting();

        for (Entry<ItemData, Integer> itemEntry : itemMap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(
                        getItemPriority(a.getKey().itemId),
                        getItemPriority(b.getKey().itemId)))
                .collect(Collectors.toList())) {

            Color itemColor = getItemColor(itemEntry.getKey().itemId);
            int cr = itemColor.getRed(), cg = itemColor.getGreen(), cb = itemColor.getBlue();
            double x = itemEntry.getKey().x - rm.getRenderPosX();
            double y = itemEntry.getKey().y - rm.getRenderPosY();
            double z = itemEntry.getKey().z - rm.getRenderPosZ();
            double distance = mc.getRenderViewEntity().getDistance(
                    itemEntry.getKey().x, itemEntry.getKey().y, itemEntry.getKey().z);
            double scale = autoScale.getValue()
                    ? 0.5 + 0.375 * ((Math.max(6.0, distance) - 6.0) / 28.0)
                    : 0.5;

            AxisAlignedBB bb = new AxisAlignedBB(
                    x - scale * 0.5, y,         z - scale * 0.5,
                    x + scale * 0.5, y + scale, z + scale * 0.5);

            // Filled box with opacity from slider
            if (alphaFill > 0) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                // Pass alpha directly into drawFilledBox via color override
                RenderUtil.drawFilledBox(bb, cr, cg, cb, alphaFill);
                GL11.glDepthMask(true);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_BLEND);
            }

            // Outline
            if (outline.getValue()) {
                RenderUtil.enableRenderState();
                RenderUtil.drawBoundingBox(bb, cr, cg, cb, 255, 1.5F);
                RenderUtil.disableRenderState();
            }

            // Item count label
            if (itemCount.getValue()) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(x, y + scale * 0.5, z);
                GlStateManager.rotate(mc.getRenderManager().playerViewY * -1.0F, 0.0F, 1.0F, 0.0F);
                float flip = mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F;
                GlStateManager.rotate(mc.getRenderManager().playerViewX, flip, 0.0F, 0.0F);
                double fontScale = autoScale.getValue()
                        ? -0.04375 - 0.0328125 * ((Math.max(6.0, distance) - 6.0) / 28.0)
                        : -0.04375;
                GlStateManager.scale(fontScale, fontScale, 1.0);
                GlStateManager.disableDepth();
                String countText = String.valueOf(itemEntry.getValue());
                RenderUtil.drawOutlinedString(
                        countText,
                        ((float) mc.fontRendererObj.getStringWidth(countText) / 2.0F - 0.5F) * -1.0F,
                        ((float)(mc.fontRendererObj.FONT_HEIGHT / 2) - 0.5F) * -1.0F);
                GlStateManager.enableDepth();
                GlStateManager.popMatrix();
            }
        }

        GlStateManager.enableLighting();
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    // ── Inner class ───────────────────────────────────────────────────────────

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
        public boolean equals(Object object) {
            if (this == object) return true;
            if (object == null || getClass() != object.getClass()) return false;
            ItemData o = (ItemData) object;
            return itemId == o.itemId && (int) x == (int) o.x
                && (int) y == (int) o.y && (int) z == (int) o.z;
        }

        @Override public int hashCode() { return hashCode; }
    }
}
