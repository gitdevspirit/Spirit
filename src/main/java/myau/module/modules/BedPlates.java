package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.Render2DEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BedPlates extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // key = "x,y,z" of head block
    private final Map<String, BedEntry> beds = new ConcurrentHashMap<>();

    // GL matrices captured in Render3D, used in Render2D
    private final FloatBuffer modelview  = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer   viewport   = BufferUtils.createIntBuffer(4);
    private double renderPosX, renderPosY, renderPosZ;
    private boolean matricesCaptured = false;

    public final SliderSetting  renderDistance = register(new SliderSetting("Render Distance", 64, 10, 200, 5));
    public final SliderSetting  yOffset        = register(new SliderSetting("Y Offset",         1, -10, 10, 0.5));
    public final SliderSetting  scale          = register(new SliderSetting("Scale",           1.0, 0.4, 2.0, 0.1));
    public final BooleanSetting autoScale      = register(new BooleanSetting("Auto Scale",     true));
    public final BooleanSetting showCount      = register(new BooleanSetting("Show Count",     true));

    private static final Set<String> INVALID = new HashSet<>(Arrays.asList(
        "air","bed","water","flowing_water","lava","flowing_lava","fire",
        "leaves","leaves2","log","log2",
        "torch","redstone_torch","redstone_wire","daylight_sensor",
        "wooden_slab","stone_slab","stone_slab2","double_wooden_slab","double_stone_slab",
        "double_stone_slab2",
        "oak_stairs","spruce_stairs","birch_stairs","jungle_stairs","acacia_stairs",
        "dark_oak_stairs","stone_stairs","cobblestone_stairs","brick_stairs",
        "stone_brick_stairs","sandstone_stairs","nether_brick_stairs","quartz_stairs",
        "red_sandstone_stairs",
        "piston","sticky_piston","piston_extension","piston_head",
        "wheat","carrots","potatoes","beetroots","farmland","soul_sand",
        "wooden_door","iron_door","spruce_door","birch_door","jungle_door",
        "acacia_door","dark_oak_door",
        "rail","activator_rail","detector_rail","golden_rail",
        "ladder","furnace","lit_furnace","chest","trapped_chest","ender_chest",
        "sign","wall_sign","standing_sign","dispenser","dropper","hopper",
        "lever","stone_pressure_plate","light_weighted_pressure_plate",
        "heavy_weighted_pressure_plate","wooden_pressure_plate",
        "wooden_button","stone_button",
        "snow_layer","cactus","reeds","jukebox","pumpkin","lit_pumpkin","cake",
        "unpowered_repeater","powered_repeater","unpowered_comparator","powered_comparator",
        "trapdoor","iron_trapdoor","skull","anvil","flower_pot",
        "brown_mushroom_block","red_mushroom_block","cobblestone_wall",
        "monster_egg","barrier"
    ));

    private static class BedEntry {
        BlockPos head, foot;
        double distance;
        Map<String, Integer> layers = Collections.emptyMap();
        BedEntry(BlockPos head, BlockPos foot) { this.head = head; this.foot = foot; }
    }

    public BedPlates() { super("BedPlates", false); }

    @Override public void onEnabled()  { beds.clear(); matricesCaptured = false; }
    @Override public void onDisabled() { beds.clear(); matricesCaptured = false; }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        int ticks = mc.thePlayer.ticksExisted;
        if (ticks % 20 == 0) scanForBeds();
        if (ticks % 10 == 0) updateBeds();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled()) return;
        modelview.clear(); projection.clear(); viewport.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();
        renderPosX = rm.getRenderPosX();
        renderPosY = rm.getRenderPosY();
        renderPosZ = rm.getRenderPosZ();
        matricesCaptured = true;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || !matricesCaptured || beds.isEmpty()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int    sf           = sr.getScaleFactor();
        double maxDist      = renderDistance.getValue();
        double yOff         = yOffset.getValue();
        float  sc           = (float) scale.getValue();

        List<BedEntry> sorted = new ArrayList<>(beds.values());
        sorted.sort((a, b) -> Double.compare(b.distance, a.distance));

        for (BedEntry bed : sorted) {
            if (bed.distance > maxDist) continue;

            double wx = (bed.head.getX() + bed.foot.getX()) / 2.0 + 0.5;
            double wy =  bed.head.getY() + yOff;
            double wz = (bed.head.getZ() + bed.foot.getZ()) / 2.0 + 0.5;

            float[] screen = worldToScreen(wx, wy, wz);
            if (screen == null) continue;

            float sx = screen[0] / sf;
            float sy = (mc.displayHeight - screen[1]) / sf;

            float curScale = autoScale.getValue()
                ? (float) Math.max(0.2, sc * (1.0 - bed.distance / maxDist))
                : sc;
            if (curScale <= 0) continue;

            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.disableDepth();

            if (bed.layers.isEmpty()) {
                drawRect(sx - 4 * curScale, sy - 4 * curScale,
                         sx + 4 * curScale, sy + 4 * curScale, 0xCC44FF44);
            } else {
                List<String> keys = new ArrayList<>(bed.layers.keySet());
                float itemSize = 16 * curScale;
                float padding  =  2 * curScale;
                float boxSize  = itemSize + padding;
                float totalW   = keys.size() * boxSize;
                float startX   = sx - totalW / 2f;
                float startY   = sy - boxSize / 2f;

                drawRect(startX - padding, startY - padding,
                         startX + totalW + padding, startY + boxSize + padding, 0xCC1A1A1F);

                for (int i = 0; i < keys.size(); i++) {
                    String blockName = keys.get(i);
                    float ix = startX + i * boxSize + padding / 2f;
                    float iy = startY + padding / 2f;

                    ItemStack stack = stackFromName(blockName);
                    if (stack != null) {
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(ix, iy, 150);
                        GlStateManager.scale(curScale, curScale, curScale);
                        GlStateManager.enableDepth();
                        mc.getRenderItem().renderItemIntoGUI(stack, 0, 0);
                        GlStateManager.disableDepth();
                        GlStateManager.popMatrix();
                    }

                    if (showCount.getValue()) {
                        int count = bed.layers.getOrDefault(blockName, 0);
                        if (count > 1) {
                            String txt = String.valueOf(count);
                            float textScale = curScale * 0.55f;
                            float tw = mc.fontRendererObj.getStringWidth(txt) * textScale;
                            float tx = ix + itemSize - tw + padding * 0.25f;
                            float ty = iy + itemSize - (mc.fontRendererObj.FONT_HEIGHT * textScale) + padding * 0.25f;
                            GlStateManager.pushMatrix();
                            GlStateManager.translate(tx, ty, 0);
                            GlStateManager.scale(textScale, textScale, 1f);
                            mc.fontRendererObj.drawStringWithShadow(txt, 0, 0, 0xFFFFFF);
                            GlStateManager.popMatrix();
                        }
                    }
                }
            }

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        beds.clear();
        matricesCaptured = false;
    }

    // ── Scanning — main thread only ───────────────────────────────────────────

    private void scanForBeds() {
        if (mc.theWorld == null || mc.thePlayer == null) return;

        int chunkRadius = Math.min((int)(renderDistance.getValue() / 16) + 1, 8);
        int cx = ((int) mc.thePlayer.posX) >> 4;
        int cz = ((int) mc.thePlayer.posZ) >> 4;
        double myX = mc.thePlayer.posX, myY = mc.thePlayer.posY, myZ = mc.thePlayer.posZ;

        for (int chX = cx - chunkRadius; chX <= cx + chunkRadius; chX++) {
            for (int chZ = cz - chunkRadius; chZ <= cz + chunkRadius; chZ++) {
                if (!mc.theWorld.getChunkProvider().chunkExists(chX, chZ)) continue;

                int baseX = chX << 4;
                int baseZ = chZ << 4;

                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int wx = baseX + lx;
                        int wz = baseZ + lz;
                        for (int y = 0; y <= 128; y++) {
                            if (!isBed(wx, y, wz)) continue;

                            int meta = metaAt(wx, y, wz);
                            // Bit 3: 1 = head block, 0 = foot. Only process head to avoid duplicates.
                            if ((meta & 8) == 0) continue;

                            BlockPos headPos = new BlockPos(wx, y, wz);
                            BlockPos footPos = findFoot(wx, y, wz, meta);

                            String key = wx + "," + y + "," + wz;
                            BedEntry entry = beds.computeIfAbsent(key, k -> new BedEntry(headPos, footPos));
                            entry.head = headPos;
                            entry.foot = footPos;
                            entry.distance = Math.sqrt(
                                (wx - myX) * (wx - myX) +
                                (y  - myY) * (y  - myY) +
                                (wz - myZ) * (wz - myZ));
                            entry.layers = getDefenceLayers(headPos, footPos);
                        }
                    }
                }
            }
        }
    }

    private void updateBeds() {
        if (beds.isEmpty() || mc.theWorld == null || mc.thePlayer == null) return;
        double myX = mc.thePlayer.posX, myY = mc.thePlayer.posY, myZ = mc.thePlayer.posZ;

        Iterator<Map.Entry<String, BedEntry>> it = beds.entrySet().iterator();
        while (it.hasNext()) {
            BedEntry bed = it.next().getValue();
            if (!isBed(bed.head.getX(), bed.head.getY(), bed.head.getZ())) {
                it.remove();
                continue;
            }
            bed.distance = Math.sqrt(
                (bed.head.getX() - myX) * (bed.head.getX() - myX) +
                (bed.head.getY() - myY) * (bed.head.getY() - myY) +
                (bed.head.getZ() - myZ) * (bed.head.getZ() - myZ));
            bed.layers = getDefenceLayers(bed.head, bed.foot);
        }
    }

    // ── Foot finder ───────────────────────────────────────────────────────────

    /**
     * In 1.8, bed metadata bits 0-1 encode facing direction of the HEAD block.
     * The foot block is placed in that same direction from the head.
     * 0 = south (+Z), 1 = west (-X), 2 = north (-Z), 3 = east (+X)
     */
    private BlockPos findFoot(int hx, int hy, int hz, int meta) {
        int dir = meta & 3;
        int fx = hx, fz = hz;
        if      (dir == 0) fz = hz + 1;
        else if (dir == 1) fx = hx - 1;
        else if (dir == 2) fz = hz - 1;
        else               fx = hx + 1;
        return isBed(fx, hy, fz) ? new BlockPos(fx, hy, fz) : new BlockPos(hx, hy, hz);
    }

    // ── Defence layers ────────────────────────────────────────────────────────

    private Map<String, Integer> getDefenceLayers(BlockPos head, BlockPos foot) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int minX = Math.min(head.getX(), foot.getX()) - 2;
        int maxX = Math.max(head.getX(), foot.getX()) + 2;
        int minZ = Math.min(head.getZ(), foot.getZ()) - 2;
        int maxZ = Math.max(head.getZ(), foot.getZ()) + 2;
        int baseY = head.getY();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int dy = -1; dy <= 3; dy++) {
                    Block block = blockAt(x, baseY + dy, z);
                    if (block == Blocks.air) continue;
                    String name = blockName(block);
                    if (INVALID.contains(name)) continue;
                    counts.merge(name, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    // ── Block helpers ─────────────────────────────────────────────────────────

    private boolean isBed(int x, int y, int z) {
        try { return mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.bed; }
        catch (Exception e) { return false; }
    }

    private int metaAt(int x, int y, int z) {
        try {
            net.minecraft.block.state.IBlockState s = mc.theWorld.getBlockState(new BlockPos(x, y, z));
            return s.getBlock().getMetaFromState(s) & 0xF;
        } catch (Exception e) { return 0; }
    }

    private Block blockAt(int x, int y, int z) {
        try { return mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock(); }
        catch (Exception e) { return Blocks.air; }
    }

    private String blockName(Block block) {
        if (block == Blocks.air) return "air";
        ResourceLocation loc = (ResourceLocation) Block.blockRegistry.getNameForObject(block);
        return loc == null ? "air" : loc.getResourcePath();
    }

    private ItemStack stackFromName(String name) {
        try {
            Block block = (Block) Block.blockRegistry.getObject(new ResourceLocation(name));
            if (block == null || block == Blocks.air) return null;
            return new ItemStack(block);
        } catch (Exception e) { return null; }
    }

    // ── GL helpers ────────────────────────────────────────────────────────────

    private float[] worldToScreen(double wx, double wy, double wz) {
        float rx = (float)(wx - renderPosX);
        float ry = (float)(wy - renderPosY);
        float rz = (float)(wz - renderPosZ);
        FloatBuffer win = BufferUtils.createFloatBuffer(3);
        modelview.rewind(); projection.rewind(); viewport.rewind();
        boolean ok = GLU.gluProject(rx, ry, rz, modelview, projection, viewport, win);
        if (!ok) return null;
        float depth = win.get(2);
        if (depth < 0 || depth >= 1.0003684f) return null;
        return new float[]{ win.get(0), win.get(1) };
    }

    private void drawRect(float x1, float y1, float x2, float y2, int color) {
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
}
