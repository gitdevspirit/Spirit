package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.management.RotationState;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.KeyBindUtil;
import myau.util.MoveUtil;
import myau.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;

import java.util.*;

public class AutoBedDefence extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final DropdownSetting defense     = register(new DropdownSetting("Defense",         0, "Compact", "Wide", "Wool Layer", "Obsidian", "Endstone", "Doubles"));
    public final BooleanSetting  onlyTopBeds = register(new BooleanSetting("Only Top Beds",    true));
    public final SliderSetting   delaySwap   = register(new SliderSetting("Delay After Swap",  0, 0, 10, 1));
    public final SliderSetting   delayAim    = register(new SliderSetting("Delay After Aim",   2, 0, 10, 1));
    public final SliderSetting   sneakTicks  = register(new SliderSetting("Sneak Hold Ticks",  5, 0, 20, 1));
    public final SliderSetting   fov         = register(new SliderSetting("FOV",             180, 0, 180, 1));
    public final DropdownSetting moveFix     = register(new DropdownSetting("Move Fix",        1, "NONE", "SILENT"));

    private static final Object[][][] DEFENSES = {
        // Compact
        {
            {"wool",  1, 0,  0}, {"wool", -1, 0,  0},
            {"wool",  0, 0,  1}, {"wool",  0, 0, -1},
            {"wool",  1, 0,  1}, {"wool", -1, 0,  1},
            {"wool",  1, 0, -1}, {"wool", -1, 0, -1},
            {"wool",  0, 1,  0}, {"wool",  1, 1,  0}, {"wool", -1, 1,  0},
            {"wool",  0, 1,  1}, {"wool",  0, 1, -1},
            {"wool",  1, 1,  1}, {"wool", -1, 1,  1},
            {"wool",  1, 1, -1}, {"wool", -1, 1, -1},
        },
        // Wide
        {
            {"wool",  1, 0,  0}, {"wool", -1, 0,  0},
            {"wool",  0, 0,  1}, {"wool",  0, 0, -1},
            {"wool",  1, 0,  1}, {"wool", -1, 0,  1},
            {"wool",  1, 0, -1}, {"wool", -1, 0, -1},
            {"wool",  2, 0,  0}, {"wool", -2, 0,  0},
            {"wool",  0, 0,  2}, {"wool",  0, 0, -2},
            {"wool",  2, 0,  1}, {"wool", -2, 0,  1},
            {"wool",  2, 0, -1}, {"wool", -2, 0, -1},
            {"wool",  1, 0,  2}, {"wool", -1, 0,  2},
            {"wool",  1, 0, -2}, {"wool", -1, 0, -2},
            {"wool",  2, 0,  2}, {"wool", -2, 0,  2},
            {"wool",  2, 0, -2}, {"wool", -2, 0, -2},
            {"wool",  0, 1,  0}, {"wool",  1, 1,  0}, {"wool", -1, 1,  0},
            {"wool",  0, 1,  1}, {"wool",  0, 1, -1},
            {"wool",  1, 1,  1}, {"wool", -1, 1,  1},
            {"wool",  1, 1, -1}, {"wool", -1, 1, -1},
        },
        // Wool Layer
        {
            {"wool",  0, 1,  0}, {"wool",  1, 1,  0}, {"wool", -1, 1,  0},
            {"wool",  0, 1,  1}, {"wool",  0, 1, -1},
            {"wool",  1, 1,  1}, {"wool", -1, 1,  1},
            {"wool",  1, 1, -1}, {"wool", -1, 1, -1},
        },
        // Obsidian
        {
            {"obsidian",  1, 0,  0}, {"obsidian", -1, 0,  0},
            {"obsidian",  0, 0,  1}, {"obsidian",  0, 0, -1},
            {"obsidian",  1, 0,  1}, {"obsidian", -1, 0,  1},
            {"obsidian",  1, 0, -1}, {"obsidian", -1, 0, -1},
            {"obsidian",  1, 1,  0}, {"obsidian", -1, 1,  0},
            {"obsidian",  0, 1,  1}, {"obsidian",  0, 1, -1},
            {"obsidian",  1, 1,  1}, {"obsidian", -1, 1,  1},
            {"obsidian",  1, 1, -1}, {"obsidian", -1, 1, -1},
            {"obsidian",  0, 2,  0}, {"obsidian",  1, 2,  0}, {"obsidian", -1, 2,  0},
            {"obsidian",  0, 2,  1}, {"obsidian",  0, 2, -1},
            {"obsidian",  1, 2,  1}, {"obsidian", -1, 2,  1},
            {"obsidian",  1, 2, -1}, {"obsidian", -1, 2, -1},
        },
        // Endstone: 12 block defence - back wall + side arms + cover over bed
        {
            // Back wall (3 wide, y=1, behind bed head)
            {"end_stone", -1, 1, -1}, {"end_stone",  0, 1, -1}, {"end_stone",  1, 1, -1},
            // Over bed head (y=1)
            {"end_stone", -1, 1,  0}, {"end_stone",  1, 1,  0},
            // Over bed foot (y=1)
            {"end_stone", -1, 1,  1}, {"end_stone",  1, 1,  1},
            // Side blocks at ground level (y=0)
            {"end_stone", -1, 0,  0}, {"end_stone",  1, 0,  0},
            {"end_stone", -1, 0,  1}, {"end_stone",  1, 0,  1},
            // Front block
            {"end_stone",  0, 1,  2},
        },
        // Doubles: two thick rows on sides + front/back caps
        {
            // Left column (3 wide)
            {"end_stone", -1, 0, -1}, {"end_stone", -1, 0,  0}, {"end_stone", -1, 0,  1},
            // Right column (3 wide)
            {"end_stone",  1, 0, -1}, {"end_stone",  1, 0,  0}, {"end_stone",  1, 0,  1},
            // Front/back extended arms
            {"end_stone",  0, 0, -1}, {"end_stone",  0, 0,  1},
            {"end_stone", -1, 0, -2}, {"end_stone",  0, 0, -2}, {"end_stone",  1, 0, -2},
            {"end_stone", -1, 0,  2}, {"end_stone",  0, 0,  2}, {"end_stone",  1, 0,  2},
        },
    };

    private int index;
    private boolean started, sneaked, pendingPlace;
    private String lockedDirection = "";
    private String hitFace;
    private BlockPos bedOrigin, hitBlock, renderTarget;
    private Vec3 placeVec;
    private float serverYaw, serverPitch;
    private int swapTicksLeft, aimTicksLeft, sneakTicksLeft;
    private int prevSlot = -1;
    private final Map<String, Integer> slotCache = new HashMap<>();

    private static final double INSET = 0.05, STEP = 0.2, JIT = 0.15;

    public AutoBedDefence() { super("AutoBedDefence", false); }

    @Override
    public void onEnabled() {
        if (mc.thePlayer == null) return;
        index = 0;
        started = sneaked = pendingPlace = false;
        lockedDirection = "";
        bedOrigin = renderTarget = null;
        serverYaw   = mc.thePlayer.rotationYaw;
        serverPitch = mc.thePlayer.rotationPitch;
        swapTicksLeft = 0;
        aimTicksLeft  = 0;
        slotCache.clear();
        prevSlot = mc.thePlayer.inventory.currentItem;
    }

    @Override
    public void onDisabled() {
        if (sneaked) KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        if (prevSlot != -1 && mc.thePlayer != null)
            mc.thePlayer.inventory.currentItem = prevSlot;
        prevSlot = -1;
        renderTarget = null;
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) return;

        // Track server rotation from outgoing packets
        serverYaw   = event.getYaw();
        serverPitch = event.getPitch();

        Object[][] def = DEFENSES[defense.getIndex()];

        // Find bed once
        if (!started) {
            BlockPos bed = findBed(8);
            if (bed == null) return;
            bedOrigin = bed;
            int meta = mc.theWorld.getBlockState(bed).getBlock()
                    .getMetaFromState(mc.theWorld.getBlockState(bed)) & 0xF;
            switch (meta) {
                case 10: case 0: lockedDirection = "north"; break;
                case 8:  case 2: lockedDirection = "south"; break;
                case 9:  case 3: lockedDirection = "west";  break;
                case 11: case 1: lockedDirection = "east";  break;
                default:         lockedDirection = "north"; break;
            }
            started = true;
        }

        // Skip already-filled positions
        while (index < def.length) {
            BlockPos tgt = getTargetPos(def[index]);
            Block b = mc.theWorld.getBlockState(tgt).getBlock();
            if (b == Blocks.air || b == Blocks.water || b == Blocks.flowing_water) break;
            index++;
        }
        if (index >= def.length) { setEnabled(false); return; }

        BlockPos target = getTargetPos(def[index]);
        renderTarget = target;

        // Swap to correct slot first — don't proceed until we have the right item
        String blockName = (String) def[index][0];
        int wantSlot = findSlot(blockName);
        if (wantSlot == -1) { setEnabled(false); return; }
        if (mc.thePlayer.inventory.currentItem != wantSlot) {
            if (swapTicksLeft > 0) { swapTicksLeft--; return; }
            mc.thePlayer.inventory.currentItem = wantSlot;
            swapTicksLeft = (int) delaySwap.getValue();
            return;
        }
        swapTicksLeft = 0;

        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) return;

        // Find rotation toward target
        float[] rot = findBestRotation(target);
        if (rot == null) return;

        float yaw = rot[0], pitch = rot[1];
        event.setRotation(yaw, pitch, 8);
        event.setPervRotation(moveFix.getIndex() != 0 ? yaw : mc.thePlayer.rotationYaw, 8);

        // Wait for aim delay
        if (aimTicksLeft > 0) { aimTicksLeft--; return; }

        // Verify raycast hits the right block face
        MovingObjectPosition mop = rayTrace(yaw, pitch, 4.5);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        BlockPos placed = mop.getBlockPos().offset(mop.sideHit);
        if (!placed.equals(target)) return;

        boolean onBed = mc.theWorld.getBlockState(mop.getBlockPos()).getBlock() == Blocks.bed;
        if (onlyTopBeds.getValue() && onBed && mop.sideHit != EnumFacing.UP) return;

        // Sneak if placing against a bed — only trigger once, wait for sneak to activate
        if (onBed && !sneaked && !mc.thePlayer.isSneaking()) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            sneaked = true;
            sneakTicksLeft = (int) sneakTicks.getValue();
            return;
        }
        // Wait for sneak to fully engage before placing
        if (onBed && sneakTicksLeft > 0) return;

        hitBlock     = mop.getBlockPos();
        hitFace      = mop.sideHit.getName().toUpperCase();
        placeVec     = mop.hitVec;
        pendingPlace = true;
        aimTicksLeft = (int) delayAim.getValue();
    }

    @EventTarget(Priority.HIGH)
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null) return;

        // Release sneak after placement
        if (sneaked) {
            if (sneakTicksLeft > 0) sneakTicksLeft--;
        }

        if (!pendingPlace) return;
        pendingPlace = false;

        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) return;

        EnumFacing facing = EnumFacing.byName(hitFace.toLowerCase());
        if (facing == null) return;

        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, held, hitBlock, facing, placeVec);
        mc.thePlayer.swingItem();

        // Release sneak now that we've placed
        if (sneaked) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            sneaked = false;
        }

        index++;
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (!isEnabled() || moveFix.getIndex() == 0) return;
        if (RotationState.isActived() && RotationState.getPriority() == 8 && MoveUtil.isForwardPressed())
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (isEnabled()) event.setCancelled(true);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || renderTarget == null) return;
        RenderUtil.drawBlockBoundingBox(renderTarget, 1.0, 0, 255, 0, 120, 1.5f);
    }

    // ── Rotation ─────────────────────────────────────────────────────────────

    private float[] findBestRotation(BlockPos target) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        float maxYaw   = (float) fov.getValue();
        float maxPitch = Math.min(maxYaw, 90f);
        float cliYaw   = normYaw(mc.thePlayer.rotationYaw);
        float cliPit   = mc.thePlayer.rotationPitch;
        float curYaw   = normYaw(serverYaw);
        float curPit   = serverPitch;

        int[] dx = {0, 0, 0, 0, 1, -1};
        int[] dy = {1,-1, 0, 0, 0,  0};
        int[] dz = {0, 0,-1, 1, 0,  0};
        EnumFacing[] faces = {EnumFacing.DOWN, EnumFacing.UP, EnumFacing.SOUTH, EnumFacing.NORTH, EnumFacing.WEST, EnumFacing.EAST};

        int GRID = (int) Math.round(1.0 / STEP);
        List<double[]> cands = new ArrayList<>();
        double insetTop = 1 - INSET - 1e-3, insetBot = INSET + 1e-3;

        for (int fi = 0; fi < 6; fi++) {
            BlockPos support = new BlockPos(
                    target.getX() + dx[fi],
                    target.getY() + dy[fi],
                    target.getZ() + dz[fi]);
            Block sb = mc.theWorld.getBlockState(support).getBlock();
            if (sb == Blocks.air) continue;
            if (onlyTopBeds.getValue() && sb == Blocks.bed && faces[fi] != EnumFacing.UP) continue;

            for (int rr = 0; rr <= GRID; rr++) {
                double v = clamp(rr * STEP + (Math.random() * STEP * JIT * 2 - STEP * JIT), 0, 1);
                for (int cc = 0; cc <= GRID; cc++) {
                    double u = clamp(cc * STEP + (Math.random() * STEP * JIT * 2 - STEP * JIT), 0, 1);
                    double px, py, pz;
                    int sx = support.getX(), sy = support.getY(), sz = support.getZ();
                    if (fi < 2) {
                        px = sx + u; pz = sz + v;
                        py = sy + (fi == 1 ? insetTop : insetBot);
                    } else if (fi < 4) {
                        px = sx + u; py = sy + v;
                        pz = sz + (fi == 2 ? insetTop : insetBot);
                    } else {
                        pz = sz + u; py = sy + v;
                        px = sx + (fi == 5 ? insetTop : insetBot);
                    }
                    float[] rw = getRotationsWrapped(eye, px, py, pz);
                    float yw = rw[0], pt = rw[1];
                    if (Math.abs(wrapYawDelta(cliYaw, yw)) > maxYaw) continue;
                    if (Math.abs(pt - cliPit) > maxPitch || Math.abs(pt) > 90f) continue;
                    double cost = Math.abs(wrapYawDelta(curYaw, yw)) + Math.abs(pt - curPit)
                            + (faces[fi] == EnumFacing.UP ? -0.5 : 0);
                    cands.add(new double[]{cost, yw, pt});
                }
            }
        }
        if (cands.isEmpty()) return null;
        cands.sort(Comparator.comparingDouble(a -> a[0]));

        for (double[] c : cands) {
            float yaw = unwrapYaw((float) c[1], serverYaw);
            float pit = (float) c[2];
            MovingObjectPosition mop = rayTrace(yaw, pit, 4.5);
            if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) continue;
            BlockPos pl = mop.getBlockPos().offset(mop.sideHit);
            if (!pl.equals(target)) continue;
            boolean onBed = mc.theWorld.getBlockState(mop.getBlockPos()).getBlock() == Blocks.bed;
            if (onlyTopBeds.getValue() && onBed && mop.sideHit != EnumFacing.UP) continue;
            return new float[]{yaw, pit};
        }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BlockPos getTargetPos(Object[] entry) {
        int ox = (int) entry[1], oy = (int) entry[2], oz = (int) entry[3];
        int[] r = rotateOffset(ox, oz, lockedDirection);
        return bedOrigin.add(r[0], oy, r[1]);
    }

    private int[] rotateOffset(int x, int z, String dir) {
        switch (dir) {
            case "north": return new int[]{x, z};
            case "south": return new int[]{-x, -z};
            case "east":  return new int[]{-z, x};
            case "west":  return new int[]{z, -x};
            default:      return new int[]{x, z};
        }
    }

    private BlockPos findBed(int range) {
        BlockPos p = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = p.getX() - range; x <= p.getX() + range; x++)
            for (int y = p.getY() - range; y <= p.getY() + range; y++)
                for (int z = p.getZ() - range; z <= p.getZ() + range; z++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    if (mc.theWorld.getBlockState(bp).getBlock() != Blocks.bed) continue;
                    double d = bp.distanceSq(p.getX(), p.getY(), p.getZ());
                    if (d < bestDist) { bestDist = d; best = bp; }
                }
        return best;
    }

    private int findSlot(String blockName) {
        Integer cached = slotCache.get(blockName);
        if (cached != null) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(cached);
            if (s != null && getBlockName(s).equalsIgnoreCase(blockName)) return cached;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (s == null || !(s.getItem() instanceof ItemBlock)) continue;
            if (getBlockName(s).equalsIgnoreCase(blockName)) { slotCache.put(blockName, i); return i; }
        }
        // Fallback: any block
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (s != null && s.getItem() instanceof ItemBlock) return i;
        }
        return -1;
    }

    private String getBlockName(ItemStack stack) {
        try {
            Block b = ((ItemBlock) stack.getItem()).getBlock();
            ResourceLocation loc = (ResourceLocation) Block.blockRegistry.getNameForObject(b);
            return loc == null ? "" : loc.getResourcePath();
        } catch (Exception e) { return ""; }
    }

    private MovingObjectPosition rayTrace(float yaw, float pitch, double dist) {
        float yr = (float) Math.toRadians(yaw), pr = (float) Math.toRadians(pitch);
        double dx = -Math.sin(yr) * Math.cos(pr), dy2 = -Math.sin(pr), dz = Math.cos(yr) * Math.cos(pr);
        Vec3 start = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 end = start.addVector(dx * dist, dy2 * dist, dz * dist);
        return mc.theWorld.rayTraceBlocks(start, end);
    }

    private float[] getRotationsWrapped(Vec3 eye, double tx, double ty, double tz) {
        double dx = tx - eye.xCoord, dy = ty - eye.yCoord, dz = tz - eye.zCoord;
        double hd = Math.sqrt(dx * dx + dz * dz);
        float yaw = normYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        float pit = (float) Math.toDegrees(-Math.atan2(dy, hd));
        return new float[]{yaw, pit};
    }

    private double clamp(double v, double lo, double hi) { return v < lo ? lo : v > hi ? hi : v; }
    private float normYaw(float y) { y = ((y % 360f) + 360f) % 360f; return y > 180f ? y - 360f : y; }
    private float wrapYawDelta(float base, float tgt) { float d = tgt - base; while (d <= -180f) d += 360f; while (d > 180f) d -= 360f; return d; }
    private float unwrapYaw(float yaw, float prev) { return prev + ((((yaw - prev + 180f) % 360f) + 360f) % 360f - 180f); }
}
