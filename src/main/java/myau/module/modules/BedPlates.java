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
import java.util.concurrent.*;

public class BedPlates extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Bed tracking state
    private final Map<String, Map<String, Object>> bedPositions  = new ConcurrentHashMap<>();
    private final Map<String, Boolean>             searchedBlocks = new ConcurrentHashMap<>();
    private final Set<Integer>                     yLevels        = ConcurrentHashMap.newKeySet();

    // GL matrices captured during Render3D so we can project in Render2D
    private final FloatBuffer modelview  = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer   viewport   = BufferUtils.createIntBuffer(4);
    private double renderPosX, renderPosY, renderPosZ;
    private boolean matricesCaptured = false;

    // Settings
    public final SliderSetting  renderDistance = new SliderSetting("Render Distance", 100, 10, 200, 5);
    public final SliderSetting  yOffset        = new SliderSetting("Y Offset",          1, -10, 10, 0.5);
    public final SliderSetting  scale          = new SliderSetting("Scale",           1.0, 0.4, 2.0, 0.1);
    public final BooleanSetting autoScale      = new BooleanSetting("Auto Scale",     true);
    public final BooleanSetting showCount      = new BooleanSetting("Show Count",     true);

    // Block names (registry paths) that don't count as real bed defense
    private static final Set<String> INVALID = new HashSet<>(Arrays.asList(
        "leaves", "water", "lava",
        "leaves2",
        "torch", "redstone_torch",
        "wooden_slab", "stone_slab", "stone_slab2", "double_wooden_slab", "double_stone_slab",
        "fire", "bed",
        "piston", "sticky_piston", "piston_extension",
        "log", "log2",
        "oak_stairs", "spruce_stairs", "birch_stairs", "jungle_stairs", "acacia_stairs",
        "dark_oak_stairs", "stone_stairs", "cobblestone_stairs", "brick_stairs",
        "stone_brick_stairs", "sandstone_stairs", "nether_brick_stairs", "quartz_stairs",
        "red_sandstone_stairs",
        "redstone_wire", "daylight_sensor",
        "wheat", "carrots", "potatoes", "beetroots",
        "farmland",
        "wooden_door", "spruce_door", "birch_door", "jungle_door", "acacia_door", "dark_oak_door",
        "rail", "activator_rail", "detector_rail", "golden_rail",
        "ladder", "furnace", "chest", "trapped_chest",
        "sign", "dispenser", "dropper", "hopper", "lever",
        "stone_pressure_plate", "light_weighted_pressure_plate", "heavy_weighted_pressure_plate",
        "wooden_button", "stone_button",
        "snow_layer", "cactus", "reeds",
        "jukebox", "pumpkin", "lit_pumpkin", "cake",
        "unpowered_repeater", "powered_repeater", "unpowered_comparator", "powered_comparator",
        "trapdoor", "skull", "quartz_block", "anvil",
        "brown_mushroom_block", "red_mushroom_block",
        "cobblestone_wall", "flower_pot", "monster_egg"
    ));

    public BedPlates() {
        super("BedPlates", false);
        register(renderDistance);
        register(yOffset);
        register(scale);
        register(autoScale);
        register(showCount);
    }

    @Override
    public void onDisabled() {
        bedPositions.clear();
        searchedBlocks.clear();
        yLevels.clear();
        matricesCaptured = false;
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        int ticks = mc.thePlayer.ticksExisted;
        if (ticks % 3   == 0) findYLevels();
        if (ticks % 20  == 0) searchForBeds();
        if (ticks % 300 == 0) searchedBlocks.clear();
        updateBeds();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled()) return;
        // Capture the GL transform while the world matrix is active so we can
        // project world positions to screen coords during Render2D
        modelview.clear();  projection.clear();  viewport.clear();
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

        // Back-to-front order so closer beds draw on top
        List<Map<String, Object>> sorted = new ArrayList<>(bedPositions.values());
        sorted.sort((a, b) -> Double.compare(
                (double) b.getOrDefault("distance", 0.0),
                (double) a.getOrDefault("distance", 0.0)));

        for (Map<String, Object> bed : sorted) {
            if (!(boolean) bed.getOrDefault("visible", false)) continue;

            double distance = (double) bed.getOrDefault("distance", 9999.0);
            if (distance > maxDist) continue;

            Map<String, Integer> layers = (Map<String, Integer>) bed.getOrDefault("layers", Collections.emptyMap());
            if (layers.isEmpty()) continue;

            BlockPos p1 = (BlockPos) bed.get("position1");
            BlockPos p2 = (BlockPos) bed.get("position2");
            if (p1 == null || p2 == null) continue;

            // Centre of the two bed blocks, offset upward by Y Offset setting
            double wx = (p1.getX() + p2.getX()) / 2.0 + 0.5;
            double wy =  p1.getY() + yOff;
            double wz = (p1.getZ() + p2.getZ()) / 2.0 + 0.5;

            float[] screen = worldToScreen(wx, wy, wz);
            if (screen == null) continue;

            // GL returns bottom-left origin; Minecraft uses top-left scaled coords
            float sx = screen[0] / sf;
            float sy = (mc.displayHeight - screen[1]) / sf;

            float currentScale = autoScale.getValue()
                ? (float) Math.max(0.2, sc * (1.0 - distance / maxDist))
                : sc;
            if (currentScale <= 0) continue;

            List<String> layerKeys = new ArrayList<>(layers.keySet());
            float itemSize = 16 * currentScale;
            float padding  =  2 * currentScale;
            float boxSize  = itemSize + padding;
            float totalW   = layerKeys.size() * boxSize;
            float startX   = sx - totalW / 2f;
            float startY   = sy - boxSize / 2f;

            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.disableDepth();

            // Dark translucent background
            drawRect(startX - padding, startY - padding,
                     startX + totalW + padding, startY + boxSize + padding,
                     0xCC1A1A1F);

            for (int i = 0; i < layerKeys.size(); i++) {
                String blockName = layerKeys.get(i).split(":")[0];
                float ix = startX + i * boxSize + padding / 2f;
                float iy = startY + padding / 2f;

                ItemStack stack = stackFromBlockName(blockName);
                if (stack != null) {
                    // Item icon
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(ix, iy, 150);
                    GlStateManager.scale(currentScale, currentScale, currentScale);
                    GlStateManager.enableDepth();
                    mc.getRenderItem().renderItemIntoGUI(stack, 0, 0);
                    GlStateManager.disableDepth();
                    GlStateManager.popMatrix();
                }

                // Block count label (bottom-right corner of the icon)
                if (showCount.getValue()) {
                    int count = layers.getOrDefault(layerKeys.get(i), 0);
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

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        bedPositions.clear();
        searchedBlocks.clear();
        yLevels.clear();
    }

    // ── Rendering helpers ─────────────────────────────────────────────────────

    /**
     * Projects a world position to 2D window pixel coordinates.
     * The GL matrices must have been captured during Render3D.
     * Returns null if the position is behind the camera.
     */
    private float[] worldToScreen(double wx, double wy, double wz) {
        // Minecraft's GL matrices are set up relative to renderPos, so subtract it
        float rx = (float)(wx - renderPosX);
        float ry = (float)(wy - renderPosY);
        float rz = (float)(wz - renderPosZ);

        FloatBuffer win = BufferUtils.createFloatBuffer(3);
        modelview.rewind();  projection.rewind();  viewport.rewind();
        boolean ok = GLU.gluProject(rx, ry, rz, modelview, projection, viewport, win);
        if (!ok) return null;

        float wz2 = win.get(2);
        if (wz2 < 0 || wz2 >= 1.0003684f) return null; // behind near/far plane

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
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private ItemStack stackFromBlockName(String name) {
        try {
            Block block = (Block) Block.blockRegistry.getObject(new ResourceLocation(name));
            if (block == null || block == Blocks.air) return null;
            return new ItemStack(block);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Block helpers ─────────────────────────────────────────────────────────

    private String getBlockName(Block block) {
        if (block == Blocks.air) return "air";
        ResourceLocation loc = (ResourceLocation) Block.blockRegistry.getNameForObject(block);
        return loc == null ? "air" : loc.getResourcePath();
    }

    private Block getBlockAt(int x, int y, int z) {
        return mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
    }

    // ── Async scanning ────────────────────────────────────────────────────────

    /**
     * Scans near all visible players for bed Y levels, so we know
     * which Y slices to search for beds.
     */
    private void findYLevels() {
        if (mc.theWorld == null) return;
        List<?> players = new ArrayList<>(mc.theWorld.playerEntities);
        executor.execute(() -> {
            try {
                for (Object o : players) {
                    if (!(o instanceof net.minecraft.entity.player.EntityPlayer)) continue;
                    net.minecraft.entity.player.EntityPlayer p = (net.minecraft.entity.player.EntityPlayer) o;
                    if (p == mc.thePlayer) continue;

                    int px = (int) p.posX, py = (int) p.posY, pz = (int) p.posZ;
                    for (int x = px - 4; x <= px + 4; x++) {
                        for (int y = py - 4; y <= py + 4; y++) {
                            for (int z = pz - 4; z <= pz + 4; z++) {
                                String key = "2" + x + "," + y + "," + z;
                                if (searchedBlocks.containsKey(key)) continue;
                                searchedBlocks.put(key, Boolean.TRUE);
                                if (getBlockAt(x, y, z) == Blocks.bed) yLevels.add(y);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    /**
     * Searches a 40×40 radius around each player on known Y levels for beds.
     */
    private void searchForBeds() {
        if (mc.theWorld == null || yLevels.isEmpty()) return;
        List<?> players = new ArrayList<>(mc.theWorld.playerEntities);
        double myX = mc.thePlayer.posX, myY = mc.thePlayer.posY, myZ = mc.thePlayer.posZ;

        executor.execute(() -> {
            try {
                for (Object o : players) {
                    if (!(o instanceof net.minecraft.entity.player.EntityPlayer)) continue;
                    net.minecraft.entity.player.EntityPlayer p = (net.minecraft.entity.player.EntityPlayer) o;
                    int px = (int) p.posX, pz = (int) p.posZ;

                    for (int yLevel : new HashSet<>(yLevels)) {
                        for (int x = px - 20; x <= px + 20; x++) {
                            for (int z = pz - 20; z <= pz + 20; z++) {
                                String key = "1" + x + "," + yLevel + "," + z;
                                if (searchedBlocks.containsKey(key)) continue;
                                searchedBlocks.put(key, Boolean.TRUE);

                                if (getBlockAt(x, yLevel, z) != Blocks.bed) continue;

                                // Only process the "foot" half of the bed to avoid double-counting
                                if (getBlockAt(x + 1, yLevel, z) == Blocks.bed) continue;
                                if (getBlockAt(x, yLevel, z + 1) == Blocks.bed) continue;

                                BlockPos pos1 = new BlockPos(x, yLevel, z);
                                BlockPos pos2 = pos1;
                                if (getBlockAt(x - 1, yLevel, z) == Blocks.bed)
                                    pos2 = new BlockPos(x - 1, yLevel, z);
                                else if (getBlockAt(x, yLevel, z - 1) == Blocks.bed)
                                    pos2 = new BlockPos(x, yLevel, z - 1);

                                double dist = Math.sqrt(
                                        (x - myX) * (x - myX) +
                                        (yLevel - myY) * (yLevel - myY) +
                                        (z - myZ) * (z - myZ));

                                Map<String, Object> bedData = new ConcurrentHashMap<>();
                                bedData.put("visible",   Boolean.TRUE);
                                bedData.put("distance",  dist);
                                bedData.put("position1", pos1);
                                bedData.put("position2", pos2);
                                bedData.put("layers",    getBedDefenseLayers(pos1, pos2));
                                bedData.put("lastcheck", System.currentTimeMillis());
                                bedPositions.put(key, bedData);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    /**
     * Called every tick — refreshes visibility, distance, and defense layers
     * for already-discovered beds.
     */
    private void updateBeds() {
        if (bedPositions.isEmpty() || mc.theWorld == null || mc.thePlayer == null) return;
        double myX = mc.thePlayer.posX, myY = mc.thePlayer.posY, myZ = mc.thePlayer.posZ;

        executor.execute(() -> {
            try {
                for (Map<String, Object> bed : bedPositions.values()) {
                    BlockPos p1 = (BlockPos) bed.get("position1");
                    if (p1 == null) continue;

                    double dist = Math.sqrt(
                            (p1.getX() - myX) * (p1.getX() - myX) +
                            (p1.getY() - myY) * (p1.getY() - myY) +
                            (p1.getZ() - myZ) * (p1.getZ() - myZ));
                    bed.put("distance", dist);

                    boolean visible = getBlockAt(p1.getX(), p1.getY(), p1.getZ()) == Blocks.bed;
                    bed.put("visible", visible);

                    if (visible) {
                        long lastCheck = (long) bed.getOrDefault("lastcheck", 0L);
                        if (System.currentTimeMillis() > lastCheck + getDelay(dist)) {
                            BlockPos p2 = (BlockPos) bed.get("position2");
                            bed.put("layers",    getBedDefenseLayers(p1, p2 != null ? p2 : p1));
                            bed.put("lastcheck", System.currentTimeMillis());
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private long getDelay(double distance) {
        if (distance > 100) return 4000;
        if (distance > 50)  return 3000;
        if (distance > 25)  return 2000;
        return 1000;
    }

    /**
     * Analyses up to 5 "layers" of blocks surrounding both halves of a bed.
     * Returns a map of blockName → total count across all layers.
     * Only blocks that make up ≥20% of their layer are included.
     */
    private Map<String, Integer> getBedDefenseLayers(BlockPos pos1, BlockPos pos2) {
        if (pos1 == null) return Collections.emptyMap();
        if (pos2 == null) pos2 = pos1;

        boolean facingZ = Math.abs(pos2.getZ() - pos1.getZ()) > Math.abs(pos2.getX() - pos1.getX());
        BlockPos[] beds = { pos1, pos2 };

        Map<String, Integer> finalCounts = new LinkedHashMap<>();
        int maxLayers = 5, airLayersCount = 0;

        for (int layer = 1; layer <= maxLayers; layer++) {
            Map<String, Integer> layerCounts = new HashMap<>();
            int total = 0, airBlocks = 0;

            for (int bedPart = 0; bedPart < beds.length; bedPart++) {
                BlockPos bed = beds[bedPart];
                int offset = bedPart == 0 ? layer : -layer;

                int startX = facingZ ? bed.getX()          : bed.getX() + offset;
                int startY = bed.getY();
                int startZ = facingZ ? bed.getZ() + offset : bed.getZ();

                for (int step1 = 0; step1 <= layer; step1++) {
                    int yOff = 0;
                    for (int step2 = step1; step2 >= 0; step2--) {
                        int x1, z1, x2, z2;
                        if (facingZ) {
                            int zShift = bedPart == 0 ? step1 : -step1;
                            x1 = startX - step2; z1 = startZ - zShift;
                            x2 = startX + step2; z2 = z1;
                        } else {
                            int xShift = bedPart == 0 ? step1 : -step1;
                            x1 = startX - xShift; z1 = startZ - step2;
                            x2 = x1;              z2 = startZ + step2;
                        }

                        String t1 = addBlock(x1, startY + yOff, z1, layerCounts);
                        if ("air".equals(t1)) airBlocks++;
                        total++;

                        if (x1 != x2 || z1 != z2) {
                            String t2 = addBlock(x2, startY + yOff, z2, layerCounts);
                            if ("air".equals(t2)) airBlocks++;
                            total++;
                        }

                        if (step2 > 0) yOff++;
                    }
                }
            }

            if (total == 0 || (float) airBlocks / total > 0.2f) {
                if (++airLayersCount >= 2) break;
                continue;
            }

            for (Map.Entry<String, Integer> e : layerCounts.entrySet()) {
                if (!"air".equals(e.getKey()) && (float) e.getValue() / total >= 0.2f)
                    finalCounts.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }

        return finalCounts;
    }

    /**
     * Gets a block at (x,y,z) and records it in the layer count map.
     * Invalid/decoration blocks are treated as air so they don't show in the HUD.
     */
    private String addBlock(int x, int y, int z, Map<String, Integer> counts) {
        Block block = getBlockAt(x, y, z);
        String name = getBlockName(block);
        if (INVALID.contains(name)) name = "air";
        counts.merge(name, 1, Integer::sum);
        return name;
    }
}
