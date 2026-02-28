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
import net.minecraft.world.chunk.Chunk;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.*;

public class BedPlates extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // key = "x,y,z" of the foot block
    private final Map<String, Map<String, Object>> bedPositions = new ConcurrentHashMap<>();

    // GL matrices captured in Render3D for projection in Render2D
    private final FloatBuffer modelview  = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer   viewport   = BufferUtils.createIntBuffer(4);
    private double renderPosX, renderPosY, renderPosZ;
    private boolean matricesCaptured = false;

    // Settings
    public final SliderSetting  renderDistance = register(new SliderSetting("Render Distance", 64, 10, 200, 5));
    public final SliderSetting  yOffset        = register(new SliderSetting("Y Offset",         1, -10, 10, 0.5));
    public final SliderSetting  scale          = register(new SliderSetting("Scale",           1.0, 0.4, 2.0, 0.1));
    public final BooleanSetting autoScale      = register(new BooleanSetting("Auto Scale",     true));
    public final BooleanSetting showCount      = register(new BooleanSetting("Show Count",     true));

    private static final Set<String> INVALID = new HashSet<>(Arrays.asList(
        "air","leaves","leaves2","water","lava","torch","redstone_torch",
        "wooden_slab","stone_slab","stone_slab2","double_wooden_slab","double_stone_slab",
        "fire","bed","piston","sticky_piston","piston_extension","log","log2",
        "oak_stairs","spruce_stairs","birch_stairs","jungle_stairs","acacia_stairs",
        "dark_oak_stairs","stone_stairs","cobblestone_stairs","brick_stairs",
        "stone_brick_stairs","sandstone_stairs","nether_brick_stairs","quartz_stairs",
        "red_sandstone_stairs","redstone_wire","daylight_sensor",
        "wheat","carrots","potatoes","beetroots","farmland",
        "wooden_door","spruce_door","birch_door","jungle_door","acacia_door","dark_oak_door",
        "rail","activator_rail","detector_rail","golden_rail",
        "ladder","furnace","chest","trapped_chest","sign","dispenser","dropper",
        "hopper","lever","stone_pressure_plate","light_weighted_pressure_plate",
        "heavy_weighted_pressure_plate","wooden_button","stone_button",
        "snow_layer","cactus","reeds","jukebox","pumpkin","lit_pumpkin","cake",
        "unpowered_repeater","powered_repeater","unpowered_comparator","powered_comparator",
        "trapdoor","skull","quartz_block","anvil",
        "brown_mushroom_block","red_mushroom_block","cobblestone_wall","flower_pot","monster_egg"
    ));

    public BedPlates() { super("BedPlates", false); }

    @Override
    public void onEnabled() {
        bedPositions.clear();
        matricesCaptured = false;
    }

    @Override
    public void onDisabled() {
        bedPositions.clear();
        matricesCaptured = false;
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        int ticks = mc.thePlayer.ticksExisted;

        // Full scan every 20 ticks (1 second) — no player-dependency
        if (ticks % 20 == 0) scanForBeds();

        // Update defence layers + visibility for known beds every 10 ticks
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

    @SuppressWarnings("unchecked")
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || !matricesCaptured || bedPositions.isEmpty()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int sf = sr.getScaleFactor();
        double maxDist = renderDistance.getValue();
        double yOff    = yOffset.getValue();
        float  sc      = (float) scale.getValue();

        List<Map<String, Object>> sorted = new ArrayList<>(bedPositions.values());
        sorted.sort((a, b) -> Double.compare(
                (double) b.getOrDefault("distance", 0.0),
                (double) a.getOrDefault("distance", 0.0)));

        for (Map<String, Object> bed : sorted) {
            if (!(boolean) bed.getOrDefault("visible", false)) continue;
            double distance = (double) bed.getOrDefault("distance", 9999.0);
            if (distance > maxDist) continue;

            Map<String, Integer> layers = (Map<String, Integer>) bed.getOrDefault("layers", Collections.emptyMap());
            BlockPos p1 = (BlockPos) bed.get("position1");
            BlockPos p2 = (BlockPos) bed.get("position2");
            if (p1 == null) continue;
            if (p2 == null) p2 = p1;

            double wx = (p1.getX() + p2.getX()) / 2.0 + 0.5;
            double wy =  p1.getY() + yOff;
            double wz = (p1.getZ() + p2.getZ()) / 2.0 + 0.5;

            float[] screen = worldToScreen(wx, wy, wz);
            if (screen == null) continue;

            float sxPos = screen[0] / sf;
            float syPos = (mc.displayHeight - screen[1]) / sf;

            float currentScale = autoScale.getValue()
                ? (float) Math.max(0.2, sc * (1.0 - distance / maxDist))
                : sc;
            if (currentScale <= 0) continue;

            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.disableDepth();

            if (layers.isEmpty()) {
                drawRect(sxPos - 4 * currentScale, syPos - 4 * currentScale,
                         sxPos + 4 * currentScale, syPos + 4 * currentScale, 0xCC44FF44);
            } else {
                List<String> layerKeys = new ArrayList<>(layers.keySet());
                float itemSize = 16 * currentScale;
                float padding  =  2 * currentScale;
                float boxSize  = itemSize + padding;
                float totalW   = layerKeys.size() * boxSize;
                float startX   = sxPos - totalW / 2f;
                float startY   = syPos - boxSize / 2f;

                drawRect(startX - padding, startY - padding,
                         startX + totalW + padding, startY + boxSize + padding, 0xCC1A1A1F);

                for (int i = 0; i < layerKeys.size(); i++) {
                    String blockName = layerKeys.get(i);
                    float ix = startX + i * boxSize + padding / 2f;
                    float iy = startY + padding / 2f;

                    ItemStack stack = stackFromBlockName(blockName);
                    if (stack != null) {
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(ix, iy, 150);
                        GlStateManager.scale(currentScale, currentScale, currentScale);
                        GlStateManager.enableDepth();
                        mc.getRenderItem().renderItemIntoGUI(stack, 0, 0);
                        GlStateManager.disableDepth();
                        GlStateManager.popMatrix();
                    }

                    if (showCount.getValue()) {
                        int count = layers.getOrDefault(blockName, 0);
                        if (count > 1) {
                            String txt = String.valueOf(count);
                            float textScale = currentScale * 0.55f;
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
        bedPositions.clear();
        matricesCaptured = false;
    }

    // ── Scanning ──────────────────────────────────────────────────────────────

    /**
     * Scans loaded chunks around the player for bed blocks.
     * Uses getChunkFromChunkCoords (never generates) and scans block storage sections
     * to skip empty 16x16x16 sections quickly.
     */
    private void scanForBeds() {
        if (mc.theWorld == null || mc.thePlayer == null) return;

        int chunkRadius = Math.min((int)(renderDistance.getValue() / 16) + 1, 6);
        int cx = ((int) mc.thePlayer.posX) >> 4;
        int cz = ((int) mc.thePlayer.posZ) >> 4;
        double myX = mc.thePlayer.posX, myY = mc.thePlayer.posY, myZ = mc.thePlayer.posZ;

        // Snapshot chunk coords on main thread — safe to read
        List<int[]> toScan = new ArrayList<>();
        for (int chX = cx - chunkRadius; chX <= cx + chunkRadius; chX++) {
            for (int chZ = cz - chunkRadius; chZ <= cz + chunkRadius; chZ++) {
                // chunkExists checks the loaded chunk map without generating anything
                if (mc.theWorld.getChunkProvider().chunkExists(chX, chZ)) {
                    toScan.add(new int[]{chX, chZ});
                }
            }
        }

        executor.execute(() -> {
            try {
                for (int[] coord : toScan) {
                    int chX = coord[0], chZ = coord[1];
                    // getChunkFromChunkCoords returns the already-loaded chunk, never generates
                    net.minecraft.world.chunk.Chunk chunk = mc.theWorld.getChunkFromChunkCoords(chX, chZ);
                    if (chunk == null) continue;

                    int baseX = chX << 4;
                    int baseZ = chZ << 4;

                    // Scan only Y levels 0-128 (beds are never above 128 in BWP)
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            int wx = baseX + lx;
                            int wz = baseZ + lz;
                            for (int y = 0; y <= 128; y++) {
                                if (getBlockAt(wx, y, wz) != Blocks.bed) continue;

                                // Only process head block (skip if neighbour in +X or +Z is also bed)
                                if (getBlockAt(wx + 1, y, wz) == Blocks.bed) continue;
                                if (getBlockAt(wx, y, wz + 1) == Blocks.bed) continue;

                                // Find second half
                                BlockPos pos1 = new BlockPos(wx, y, wz);
                                BlockPos pos2 = pos1;
                                if (getBlockAt(wx - 1, y, wz) == Blocks.bed)
                                    pos2 = new BlockPos(wx - 1, y, wz);
                                else if (getBlockAt(wx, y, wz - 1) == Blocks.bed)
                                    pos2 = new BlockPos(wx, y, wz - 1);

                                String key = wx + "," + y + "," + wz;
                                double dist = Math.sqrt(
                                    (wx - myX) * (wx - myX) +
                                    (y  - myY) * (y  - myY) +
                                    (wz - myZ) * (wz - myZ));

                                Map<String, Object> bedData = bedPositions.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
                                bedData.put("visible",   Boolean.TRUE);
                                bedData.put("distance",  dist);
                                bedData.put("position1", pos1);
                                bedData.put("position2", pos2);
                                bedData.put("layers",    getBedDefenseLayers(pos1, pos2));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    /**
     * Refreshes visibility and distance for already-discovered beds.
     * Removes beds that are no longer present.
     */
    private void updateBeds() {
        if (bedPositions.isEmpty() || mc.theWorld == null || mc.thePlayer == null) return;
        double myX = mc.thePlayer.posX, myY = mc.thePlayer.posY, myZ = mc.thePlayer.posZ;

        executor.execute(() -> {
            try {
                Iterator<Map.Entry<String, Map<String, Object>>> it = bedPositions.entrySet().iterator();
                while (it.hasNext()) {
                    Map<String, Object> bed = it.next().getValue();
                    BlockPos p1 = (BlockPos) bed.get("position1");
                    if (p1 == null) { it.remove(); continue; }

                    boolean visible = getBlockAt(p1.getX(), p1.getY(), p1.getZ()) == Blocks.bed;
                    if (!visible) { it.remove(); continue; } // Bed was destroyed — remove it

                    double dist = Math.sqrt(
                        (p1.getX() - myX) * (p1.getX() - myX) +
                        (p1.getY() - myY) * (p1.getY() - myY) +
                        (p1.getZ() - myZ) * (p1.getZ() - myZ));

                    bed.put("distance", dist);
                    bed.put("visible",  Boolean.TRUE);

                    // Refresh defence layers
                    BlockPos p2 = (BlockPos) bed.get("position2");
                    bed.put("layers", getBedDefenseLayers(p1, p2 != null ? p2 : p1));
                }
            } catch (Exception ignored) {}
        });
    }

    // ── Defence layer scanning ────────────────────────────────────────────────

    private Map<String, Integer> getBedDefenseLayers(BlockPos pos1, BlockPos pos2) {
        if (pos1 == null) return Collections.emptyMap();
        if (pos2 == null) pos2 = pos1;

        Map<String, Integer> counts = new LinkedHashMap<>();
        int minX = Math.min(pos1.getX(), pos2.getX()) - 2;
        int maxX = Math.max(pos1.getX(), pos2.getX()) + 2;
        int minZ = Math.min(pos1.getZ(), pos2.getZ()) - 2;
        int maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 2;
        int baseY = pos1.getY();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int dy = -1; dy <= 2; dy++) {
                    Block block = getBlockAt(x, baseY + dy, z);
                    if (block == Blocks.air) continue;
                    String name = getBlockName(block);
                    if (INVALID.contains(name)) continue;
                    counts.merge(name, 1, Integer::sum);
                }
            }
        }
        return counts;
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
        float wz2 = win.get(2);
        if (wz2 < 0 || wz2 >= 1.0003684f) return null;
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

    private ItemStack stackFromBlockName(String name) {
        try {
            Block block = (Block) Block.blockRegistry.getObject(new ResourceLocation(name));
            if (block == null || block == Blocks.air) return null;
            return new ItemStack(block);
        } catch (Exception e) { return null; }
    }

    private String getBlockName(Block block) {
        if (block == Blocks.air) return "air";
        ResourceLocation loc = (ResourceLocation) Block.blockRegistry.getNameForObject(block);
        return loc == null ? "air" : loc.getResourcePath();
    }

    private Block getBlockAt(int x, int y, int z) {
        try {
            return mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
        } catch (Exception e) { return Blocks.air; }
    }
}
