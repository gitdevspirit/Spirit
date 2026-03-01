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
import myau.util.MoveUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;

import java.util.*;

public class Clutch extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting   range        = register(new SliderSetting("Range",           4.5, 0.5, 6.0, 0.1));
    public final SliderSetting   speed        = register(new SliderSetting("Speed",             8,  0, 100,   1));
    public final SliderSetting   snapback     = register(new SliderSetting("Snapback Speed",   12,  0, 100,   1));
    public final SliderSetting   maxBlocks    = register(new SliderSetting("Max Blocks",       10,  0,  20,   1));
    public final SliderSetting   rotTolerance = register(new SliderSetting("Rot Tolerance",    25, 5, 100,   1));
    public final BooleanSetting  simFuture    = register(new BooleanSetting("Simulate Future", true));
    public final DropdownSetting moveFix      = register(new DropdownSetting("Move Fix",       1, "NONE", "SILENT", "STRICT"));

    // Rotation tracking
    private float serverYaw, serverPitch;
    private float aimYaw, aimPitch;

    // Placement state
    private BlockPos targetBlock;
    private EnumFacing targetFacing;
    private Vec3 targetHitVec;

    // Resetting / snapback state
    private boolean hasAim;
    private boolean resetting;
    private boolean placing;
    private int prevSlot = -1;
    private int plannedSlot = -1;
    private int clutchBlocksPlaced = 0;
    private boolean placeQueued;
    private BlockPos lastPlacedPos;

    private static final double HW = 0.3;
    private static final double[][] CORNERS = {{-HW,-HW},{HW,-HW},{-HW,HW},{HW,HW}};
    private static final double INSET = 0.05, STEP = 0.2, JIT = STEP * 0.1;

    private static final Map<String, Integer> BLOCK_SCORE = new HashMap<>();
    static {
        BLOCK_SCORE.put("obsidian",             0);
        BLOCK_SCORE.put("end_stone",            1);
        BLOCK_SCORE.put("planks",               2);
        BLOCK_SCORE.put("log",                  2);
        BLOCK_SCORE.put("log2",                 2);
        BLOCK_SCORE.put("glass",                3);
        BLOCK_SCORE.put("stained_glass",        3);
        BLOCK_SCORE.put("hardened_clay",        4);
        BLOCK_SCORE.put("stained_hardened_clay",4);
        BLOCK_SCORE.put("stone",                5);
        BLOCK_SCORE.put("wool",                 5);
    }

    public Clutch() { super("Clutch", false); }

    @Override
    public void onEnabled() {
        if (mc.thePlayer == null) return;
        serverYaw   = mc.thePlayer.rotationYaw;
        serverPitch = mc.thePlayer.rotationPitch;
        hasAim      = false;
        resetting   = false;
        placing     = false;
        clutchBlocksPlaced = 0;
        targetBlock  = null;
        targetFacing = null;
        targetHitVec = null;
    }

    @Override
    public void onDisabled() {
        disablePlacing();
        hasAim   = false;
        resetting = false;
    }

    // ── Rotation capture ───────────────────────────────────────────────────────

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        serverYaw   = event.getYaw();
        serverPitch = event.getPitch();

        if (mc.thePlayer.onGround) clutchBlocksPlaced = 0;

        // Check if we need a clutch: no solid block below feet
        boolean needsClutch = isAirBelow();

        if (!needsClutch || mc.currentScreen != null) {
            clearAim();
            disablePlacing();
            return;
        }

        plannedSlot = pickBlockSlot();
        if (plannedSlot == -1) {
            disablePlacing();
            return;
        }

        // Find aim
        Object[] tgt = clutchAim();
        if (tgt != null) {
            targetBlock  = (BlockPos)    tgt[0];
            targetFacing = (EnumFacing)  tgt[1];
            targetHitVec = (Vec3)        tgt[2];
            aimYaw       = (float)       tgt[3];
            aimPitch     = (float)       tgt[4];
            hasAim       = true;
            resetting    = false;
        }

        if (hasAim && !placing) enablePlacing();

        if (!hasAim && placing) {
            disablePlacing();
            return;
        }

        if (placing) {
            // Smoothed rotation toward aim
            float[] sm = smoothRotation(aimYaw, aimPitch, false);
            event.setRotation(sm[0], sm[1], 7);
            event.setPervRotation(moveFix.getIndex() != 0 ? sm[0] : mc.thePlayer.rotationYaw, 7);

            // Check if we're on target and within tolerance — queue the place
            float dy = Math.abs(MathHelper.wrapAngleTo180_float(sm[0] - serverYaw));
            float dp = Math.abs(sm[1] - serverPitch);
            if (dy <= (float) rotTolerance.getValue() && dp <= (float) rotTolerance.getValue()) {
                MovingObjectPosition mop = rayTrace(sm[0], sm[1], range.getValue());
                if (mop != null
                        && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                        && mop.getBlockPos().equals(targetBlock)
                        && mop.sideHit == targetFacing) {
                    int max = (int) maxBlocks.getValue();
                    if (max == 0 || clutchBlocksPlaced < max) {
                        placeQueued = true;
                    }
                }
            }

            equipPlannedSlot();
        }

        // Snapback when we have no aim but were aiming
        if (resetting) {
            float[] sm = smoothRotation(serverYaw, serverPitch, true);
            event.setRotation(sm[0], sm[1], 7);
            event.setPervRotation(moveFix.getIndex() != 0 ? sm[0] : mc.thePlayer.rotationYaw, 7);
            if (Math.abs(sm[0] - serverYaw) < 0.5f && Math.abs(sm[1] - serverPitch) < 0.5f) {
                resetting = false;
            }
        }
    }

    @EventTarget(Priority.HIGH)
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!placeQueued) return;
        placeQueued = false;

        ItemStack heldStack = mc.thePlayer.inventory.getCurrentItem();
        if (heldStack == null || !(heldStack.getItem() instanceof ItemBlock)) return;

        MovingObjectPosition mop = rayTrace(aimYaw, aimPitch, range.getValue());
        if (mop != null
                && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mop.getBlockPos().equals(targetBlock)
                && mop.sideHit == targetFacing) {
            mc.playerController.onPlayerRightClick(
                    mc.thePlayer, mc.theWorld, heldStack,
                    targetBlock, targetFacing, mop.hitVec);
            mc.thePlayer.swingItem();
            if (targetFacing != EnumFacing.UP) clutchBlocksPlaced++;
            lastPlacedPos = targetBlock;
            targetBlock  = null;
            targetFacing = null;
            targetHitVec = null;
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (!isEnabled() || !placing) return;
        if (moveFix.getIndex() == 1
                && RotationState.isActived()
                && RotationState.getPriority() == 7
                && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (isEnabled() && placing) {
            event.setCancelled(true);
        }
    }

    // ── Clutch logic ──────────────────────────────────────────────────────────

    /**
     * Returns true if there is no solid block directly below the player's feet
     * (checks all four foot corners).
     */
    private boolean isAirBelow() {
        double x = mc.thePlayer.posX;
        double y = mc.thePlayer.posY;
        double z = mc.thePlayer.posZ;
        for (double[] c : CORNERS) {
            BlockPos bp = new BlockPos(x + c[0], y - 1, z + c[1]);
            Block b = mc.theWorld.getBlockState(bp).getBlock();
            if (b != Blocks.air && b != Blocks.water && b != Blocks.flowing_water
                    && b != Blocks.lava && b != Blocks.flowing_lava && b != Blocks.fire) {
                return false; // at least one corner has solid ground
            }
        }
        return true;
    }

    /**
     * Scans nearby blocks and returns the best placement target:
     * [BlockPos, EnumFacing, Vec3 hitVec, float yaw, float pitch]
     */
    private Object[] clutchAim() {
        Vec3 pos = mc.thePlayer.getPositionVector();
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        double reach = range.getValue();

        // Simulate future position if enabled (simple fall prediction)
        Vec3 futurePos = pos;
        if (simFuture.getValue()) {
            double vy = mc.thePlayer.motionY;
            double fy = pos.yCoord;
            for (int t = 0; t < 20; t++) {
                vy = (vy - 0.08) * 0.98;
                fy += vy;
                if (fy < pos.yCoord - 2) break;
            }
            futurePos = new Vec3(pos.xCoord, fy, pos.zCoord);
        }

        int fx = (int) Math.floor(pos.xCoord), fy2 = (int) Math.floor(pos.yCoord), fz = (int) Math.floor(pos.zCoord);
        int minY = fy2 - 4, maxY = fy2 - 1;
        int rng = 5;

        // Gather candidate support blocks, scored by distance
        List<Object[]> cands = new ArrayList<>();
        for (int y = maxY; y >= minY; y--) {
            for (int x = fx - rng; x <= fx + rng; x++) {
                for (int z = fz - rng; z <= fz + rng; z++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    Block blk = mc.theWorld.getBlockState(bp).getBlock();
                    if (blk == Blocks.air || blk == Blocks.water || blk == Blocks.flowing_water
                            || blk == Blocks.lava || blk == Blocks.flowing_lava || blk == Blocks.fire) continue;

                    double cx = x + 0.5, cy = y + 0.5, cz2 = z + 0.5;
                    double curDist  = dist2PointAABB(pos,       x, y, z);
                    double futDist  = dist2PointAABB(futurePos, x, y, z);
                    double score    = simFuture.getValue() ? curDist * 0.3 + futDist * 0.7 : curDist;
                    if (lastPlacedPos != null && lastPlacedPos.getX() == x && lastPlacedPos.getY() == y && lastPlacedPos.getZ() == z)
                        score *= 0.95;
                    cands.add(new Object[]{ score, bp });
                }
            }
        }
        cands.sort(Comparator.comparingDouble(a -> (double) a[0]));

        ItemStack held = plannedSlot >= 0 ? mc.thePlayer.inventory.getStackInSlot(plannedSlot) : null;

        for (Object[] cand : cands) {
            BlockPos bp = (BlockPos) cand[1];
            boolean underPlayer = isBlockUnderPlayer(bp, pos);
            Object[] res = getBestRotationsToBlock(held, bp, eye, reach, underPlayer);
            if (res != null) return res;
        }
        return null;
    }

    private boolean isBlockUnderPlayer(BlockPos bp, Vec3 pos) {
        if (bp.getY() >= (int) Math.floor(pos.yCoord)) return false;
        for (double[] c : CORNERS) {
            if (bp.getX() == (int) Math.floor(pos.xCoord + c[0])
             && bp.getZ() == (int) Math.floor(pos.zCoord + c[1])) return true;
        }
        return false;
    }

    /**
     * Returns [BlockPos, EnumFacing, Vec3 hitVec, float yaw, float pitch]
     * or null if no valid rotation found.
     */
    private Object[] getBestRotationsToBlock(ItemStack held, BlockPos bp, Vec3 eye, double reach, boolean underPlayer) {
        boolean faceSouth = Math.abs(eye.zCoord - (bp.getZ() + 1)) < Math.abs(eye.zCoord - bp.getZ());
        boolean faceEast  = Math.abs(eye.xCoord - (bp.getX() + 1)) < Math.abs(eye.xCoord - bp.getX());
        float baseYaw = normYaw(serverYaw);
        float basePit = serverPitch;
        int n = (int) Math.round(1 / STEP);

        List<double[]> rots = new ArrayList<>(); // [cost, yaw, pitch]
        rots.add(new double[]{ 0, baseYaw, basePit });

        for (int r = 0; r <= n; r++) {
            double v = clamp(r * STEP + (Math.random() * JIT * 2 - JIT), 0, 1);
            for (int c = 0; c <= n; c++) {
                double u = clamp(c * STEP + (Math.random() * JIT * 2 - JIT), 0, 1);

                if (underPlayer) {
                    float[] rot = getRotationsWrapped(eye, bp.getX() + u, bp.getY() + 1 - INSET, bp.getZ() + v);
                    double cost = Math.abs(wrapYawDelta(baseYaw, rot[0])) + Math.abs(rot[1] - basePit);
                    rots.add(new double[]{ cost, rot[0], rot[1] });
                }
                float[] rZ = getRotationsWrapped(eye, bp.getX() + u, bp.getY() + v, faceSouth ? bp.getZ() + 1 - INSET : bp.getZ() + INSET);
                rots.add(new double[]{ Math.abs(wrapYawDelta(baseYaw, rZ[0])) + Math.abs(rZ[1] - basePit), rZ[0], rZ[1] });

                float[] rX = getRotationsWrapped(eye, faceEast ? bp.getX() + 1 - INSET : bp.getX() + INSET, bp.getY() + v, bp.getZ() + u);
                rots.add(new double[]{ Math.abs(wrapYawDelta(baseYaw, rX[0])) + Math.abs(rX[1] - basePit), rX[0], rX[1] });
            }
        }
        rots.sort(Comparator.comparingDouble(a -> a[0]));

        for (double[] rot : rots) {
            float yaw = unwrapYaw((float) rot[1], serverYaw);
            float pit = (float) rot[2];
            MovingObjectPosition mop = rayTrace(yaw, pit, reach);
            if (mop == null) continue;
            if (mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) continue;
            if (!mop.getBlockPos().equals(bp)) continue;
            EnumFacing face = mop.sideHit;
            if (face == EnumFacing.DOWN) continue;
            if (face == EnumFacing.UP && !underPlayer) continue;
            // Check the block we'd place into is air
            BlockPos placeDest = bp.offset(face);
            Block destBlock = mc.theWorld.getBlockState(placeDest).getBlock();
            if (destBlock != Blocks.air && destBlock != Blocks.water && destBlock != Blocks.flowing_water) continue;

            return new Object[]{ bp, face, mop.hitVec, yaw, pit };
        }
        return null;
    }

    // ── Slot management ────────────────────────────────────────────────────────

    private int pickBlockSlot() {
        int best = -1, bestScore = Integer.MAX_VALUE;
        for (int slot = 8; slot >= 0; slot--) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(slot);
            if (s == null || s.stackSize == 0 || !(s.getItem() instanceof ItemBlock)) continue;
            String name = getBlockName(s);
            Integer score = BLOCK_SCORE.get(name);
            if (score != null && score < bestScore) { bestScore = score; best = slot; }
        }
        return best;
    }

    private String getBlockName(ItemStack stack) {
        try {
            Block b = ((ItemBlock) stack.getItem()).getBlock();
            net.minecraft.util.ResourceLocation loc = (net.minecraft.util.ResourceLocation) Block.blockRegistry.getNameForObject(b);
            return loc == null ? "" : loc.getResourcePath();
        } catch (Exception e) { return ""; }
    }

    private void equipPlannedSlot() {
        if (plannedSlot != -1 && mc.thePlayer.inventory.currentItem != plannedSlot) {
            mc.thePlayer.inventory.currentItem = plannedSlot;
        }
    }

    private void enablePlacing() {
        if (placing) return;
        placing = true;
        prevSlot = mc.thePlayer.inventory.currentItem;
    }

    private void disablePlacing() {
        if (!placing) return;
        if (prevSlot != -1 && mc.thePlayer != null) mc.thePlayer.inventory.currentItem = prevSlot;
        placing = false;
        prevSlot = -1;
        plannedSlot = -1;
    }

    private void clearAim() {
        targetBlock  = null;
        targetFacing = null;
        targetHitVec = null;
        if (hasAim) resetting = true;
        hasAim = false;
        clutchBlocksPlaced = 0;
        lastPlacedPos = null;
    }

    // ── Math helpers ───────────────────────────────────────────────────────────

    private float[] smoothRotation(float targetYaw, float targetPitch, boolean isSnapback) {
        float dYaw = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
        float dPit = targetPitch - serverPitch;
        float curYaw = serverYaw, curPit = serverPitch;

        if (Math.abs(dYaw) < 0.1f) curYaw = targetYaw;
        if (Math.abs(dPit) < 0.1f) curPit = targetPitch;
        if (curYaw == targetYaw && curPit == targetPitch) return new float[]{ curYaw, curPit };

        float maxStep = (float)(isSnapback ? snapback.getValue() : speed.getValue());
        maxStep *= (1f - (float)(Math.random() * 0.2));
        float total = Math.abs(dYaw) + Math.abs(dPit);
        if (total <= maxStep) {
            curYaw = targetYaw; curPit = targetPitch;
        } else {
            float scale = maxStep / total;
            curYaw += dYaw * scale;
            curPit += dPit * scale;
        }
        return new float[]{ curYaw, MathHelper.clamp_float(curPit, -90, 90) };
    }

    private MovingObjectPosition rayTrace(float yaw, float pitch, double dist) {
        float yr = (float) Math.toRadians(yaw), pr = (float) Math.toRadians(pitch);
        double x = -Math.sin(yr) * Math.cos(pr), y = -Math.sin(pr), z = Math.cos(yr) * Math.cos(pr);
        Vec3 start = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 end   = start.addVector(x * dist, y * dist, z * dist);
        return mc.theWorld.rayTraceBlocks(start, end);
    }

    private float[] getRotationsWrapped(Vec3 eye, double tx, double ty, double tz) {
        double dx = tx - eye.xCoord, dy = ty - eye.yCoord, dz = tz - eye.zCoord;
        double hd = Math.sqrt(dx * dx + dz * dz);
        float yaw = normYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        float pit = (float) Math.toDegrees(-Math.atan2(dy, hd));
        return new float[]{ yaw, pit };
    }

    private double dist2PointAABB(Vec3 p, int x, int y, int z) {
        double cx = clamp(p.xCoord, x, x+1), cy = clamp(p.yCoord, y, y+1), cz = clamp(p.zCoord, z, z+1);
        return Math.pow(p.xCoord-cx,2) + Math.pow(p.yCoord-cy,2) + Math.pow(p.zCoord-cz,2);
    }

    private double clamp(double v, double lo, double hi) { return v < lo ? lo : v > hi ? hi : v; }
    private float normYaw(float y) { y = ((y % 360f) + 360f) % 360f; return y > 180f ? y - 360f : y; }
    private float wrapYawDelta(float base, float tgt) { float d = tgt - base; while (d <= -180f) d += 360f; while (d > 180f) d -= 360f; return d; }
    private float unwrapYaw(float yaw, float prev) { return prev + ((((yaw - prev + 180f) % 360f) + 360f) % 360f - 180f); }
}
