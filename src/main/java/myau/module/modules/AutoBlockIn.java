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
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Queue;

public class AutoBlockIn extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Map<String, Integer> BLOCK_SCORE = new HashMap<>();
    private long lastPlaceTime = 0;

    public final SliderSetting   range             = new SliderSetting("Range",           4.5, 3.0,  6.0, 0.1);
    public final SliderSetting   speed             = new SliderSetting("Speed",            20,   5,  100,   1);
    public final SliderSetting   placeDelay        = new SliderSetting("Place Delay",      50,   0,  200,   1);
    public final SliderSetting   rotationTolerance = new SliderSetting("Rot Tolerance",    25,   5,  100,   1);
    public final BooleanSetting  itemSpoof         = new BooleanSetting("Item Spoof",      true);
    public final BooleanSetting  showProgress      = new BooleanSetting("Show Progress",   true);
    public final DropdownSetting moveFix           = new DropdownSetting("Move Fix",       1, "NONE", "SILENT", "STRICT");

    private float serverYaw;
    private float serverPitch;
    private float progress;
    private float aimYaw;
    private float aimPitch;
    private BlockPos targetBlock;
    private EnumFacing targetFacing;
    private Vec3 targetHitVec;
    private int lastSlot = -1;

    private static final int[][] DIRS = {{1,0,0}, {0,0,1}, {-1,0,0}, {0,0,-1}};
    private static final double INSET = 0.05;
    private static final double STEP  = 0.2;
    private static final double JIT   = STEP * 0.1;

    public AutoBlockIn() {
        super("AutoBlockIn", false);
        register(range);
        register(speed);
        register(placeDelay);
        register(rotationTolerance);
        register(itemSpoof);
        register(showProgress);
        register(moveFix);

        BLOCK_SCORE.put("obsidian",             0);
        BLOCK_SCORE.put("end_stone",            1);
        BLOCK_SCORE.put("planks",               2);
        BLOCK_SCORE.put("log",                  2);
        BLOCK_SCORE.put("glass",                3);
        BLOCK_SCORE.put("stained_glass",        3);
        BLOCK_SCORE.put("hardened_clay",        4);
        BLOCK_SCORE.put("stained_hardened_clay",4);
        BLOCK_SCORE.put("cloth",                5);
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            serverYaw   = mc.thePlayer.rotationYaw;
            serverPitch = mc.thePlayer.rotationPitch;
            aimYaw      = serverYaw;
            aimPitch    = serverPitch;
            progress    = 0;
            lastSlot    = mc.thePlayer.inventory.currentItem;
            targetBlock  = null;
            targetFacing = null;
            targetHitVec = null;
            lastPlaceTime = 0;
        }
    }

    @Override
    public void onDisabled() {
        if (lastSlot != -1 && mc.thePlayer != null
                && mc.thePlayer.inventory.currentItem != lastSlot) {
            mc.thePlayer.inventory.currentItem = lastSlot;
        }
        progress     = 0;
        targetBlock  = null;
        targetFacing = null;
        targetHitVec = null;
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) return;

        serverYaw   = event.getYaw();
        serverPitch = event.getPitch();

        updateProgress();

        int blockSlot = findBestBlockSlot();
        if (blockSlot != -1 && mc.thePlayer.inventory.currentItem != blockSlot) {
            mc.thePlayer.inventory.currentItem = blockSlot;
        }

        ItemStack currentHeld = mc.thePlayer.inventory.getCurrentItem();
        boolean holdingBlock  = currentHeld != null && currentHeld.getItem() instanceof ItemBlock;
        if (!holdingBlock) {
            targetBlock  = null;
            targetFacing = null;
            targetHitVec = null;
            return;
        }

        findBestPlacement();

        if (targetBlock != null && targetFacing != null && targetHitVec != null) {
            Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
            double dx   = targetHitVec.xCoord - eyes.xCoord;
            double dy   = targetHitVec.yCoord - eyes.yCoord;
            double dz   = targetHitVec.zCoord - eyes.zCoord;
            double dist = Math.sqrt(dx * dx + dz * dz);

            float targetYaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
            targetYaw = MathHelper.wrapAngleTo180_float(targetYaw);

            float yawDiff   = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
            float pitchDiff = targetPitch - serverPitch;
            float maxTurn   = (float) speed.getValue();
            float yawStep   = MathHelper.clamp_float(yawDiff,   -maxTurn, maxTurn);
            float pitchStep = MathHelper.clamp_float(pitchDiff, -maxTurn, maxTurn);

            aimYaw   = serverYaw + yawStep;
            aimPitch = MathHelper.clamp_float(serverPitch + pitchStep, -90.0f, 90.0f);

            event.setRotation(aimYaw, aimPitch, 6);
            event.setPervRotation(moveFix.getIndex() != 0 ? aimYaw : mc.thePlayer.rotationYaw, 6);
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (isEnabled() && moveFix.getIndex() == 1
                && RotationState.isActived()
                && RotationState.getPriority() == 6
                && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    @EventTarget(Priority.HIGH)
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) return;

        if (targetBlock != null && targetFacing != null && targetHitVec != null) {
            if (!withinRotationTolerance(aimYaw, aimPitch)) return;

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastPlaceTime >= (long) placeDelay.getValue()) {
                lastPlaceTime = currentTime;

                MovingObjectPosition mop = rayTraceBlock(aimYaw, aimPitch, range.getValue());
                if (mop != null
                        && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                        && mop.getBlockPos().equals(targetBlock)
                        && mop.sideHit == targetFacing) {
                    ItemStack heldStack = mc.thePlayer.inventory.getCurrentItem();
                    if (heldStack != null && heldStack.getItem() instanceof ItemBlock) {
                        mc.playerController.onPlayerRightClick(
                                mc.thePlayer, mc.theWorld, heldStack,
                                targetBlock, targetFacing, mop.hitVec);
                        mc.thePlayer.swingItem();
                        targetBlock  = null;
                        targetFacing = null;
                        targetHitVec = null;
                    }
                }
            }
        }
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (isEnabled()) {
            lastSlot = event.setSlot(lastSlot);
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || mc.currentScreen != null) return;
        if (!showProgress.getValue()) return;
        if (mc.fontRendererObj == null) return;

        String text  = String.format("Blocking: %.0f%%", progress * 100.0F);
        float  scale = 1.0f;

        GL11.glPushMatrix();
        GL11.glScaled(scale, scale, 0.0);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        ScaledResolution sr    = new ScaledResolution(mc);
        int              width = mc.fontRendererObj.getStringWidth(text);
        Color            color = getProgressColor();

        mc.fontRendererObj.drawString(
                text,
                (float) sr.getScaledWidth()  / 2.0F / scale - (float) width / 2.0F,
                (float) sr.getScaledHeight() / 5.0F * 2.0F / scale,
                color.getRGB() & 16777215 | -1090519040,
                true);

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GL11.glPopMatrix();
    }

    // ----------------------------------------------------------------
    // INTERNALS
    // ----------------------------------------------------------------

    private int findBestBlockSlot() {
        int bestSlot  = -1;
        int bestScore = Integer.MAX_VALUE;
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null || stack.stackSize == 0) continue;
            if (stack.getItem() instanceof ItemBlock) {
                Block   block     = ((ItemBlock) stack.getItem()).getBlock();
                String  blockName = block.getUnlocalizedName().replace("tile.", "");
                Integer score     = BLOCK_SCORE.get(blockName);
                if (score != null && score < bestScore) {
                    bestScore = score;
                    bestSlot  = slot;
                    if (score == 0) break;
                }
            }
        }
        return bestSlot;
    }

    private void findBestPlacement() {
        Vec3     playerPos = mc.thePlayer.getPositionVector();
        BlockPos feetPos   = new BlockPos(playerPos.xCoord, playerPos.yCoord, playerPos.zCoord);
        Vec3     eye       = mc.thePlayer.getPositionEyes(1.0f);
        double   reach     = range.getValue();
        double   reachSq   = reach * reach;
        double   rp12      = (reach + 1) * (reach + 1);
        BlockPos roofTarget = feetPos.up(2);

        if (!isAir(roofTarget)) { sidesAim(eye, reach, feetPos); return; }

        List<BlockData> supports = new ArrayList<>();
        int minX = (int) Math.floor(eye.xCoord - reach), maxX = (int) Math.floor(eye.xCoord + reach);
        int minY = (int) Math.floor(eye.yCoord - 1),     maxY = (int) Math.floor(eye.yCoord + reach);
        int minZ = (int) Math.floor(eye.zCoord - reach), maxZ = (int) Math.floor(eye.zCoord + reach);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (isAir(p)) continue;
                    double dx = (x + 0.5) - eye.xCoord, dy = (y + 0.5) - eye.yCoord, dz = (z + 0.5) - eye.zCoord;
                    if (dx*dx + dy*dy + dz*dz > rp12) continue;
                    double d2 = dist2PointAABB(eye, x, y, z);
                    if (d2 > reachSq) continue;
                    Vec3 mid = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eye, mid, false, false, false);
                    if (mop == null || !mop.getBlockPos().equals(p)) continue;
                    supports.add(new BlockData(p, d2));
                }
            }
        }

        if (supports.isEmpty()) { sidesAim(eye, reach, feetPos); return; }
        supports.sort(Comparator.comparingDouble(a -> a.distance));

        for (BlockData bd : supports) {
            if (tryPlaceOnBlock(bd.pos, eye, reach, roofTarget)) return;
        }

        Queue<BlockPos>          q       = new LinkedList<>();
        Map<BlockPos, BlockPos>  parent  = new HashMap<>();
        Set<BlockPos>            visited = new HashSet<>();

        for (BlockData bd : supports) {
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos node = bd.pos.offset(f);
                if (!isAir(node) || visited.contains(node)) continue;
                visited.add(node);
                parent.put(node, null);
                q.add(node);
            }
        }

        BlockPos endNode  = null;
        int      nodesSeen = 0;
        while (!q.isEmpty() && nodesSeen < 8964) {
            BlockPos cur = q.poll(); nodesSeen++;
            if (cur.distanceSq(roofTarget) <= 1.5) { endNode = cur; break; }
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos nxt = cur.offset(f);
                if (visited.contains(nxt) || !isAir(nxt)) continue;
                visited.add(nxt); parent.put(nxt, cur); q.add(nxt);
            }
        }

        if (endNode == null) { sidesAim(eye, reach, feetPos); return; }

        List<BlockPos> path = new ArrayList<>();
        for (BlockPos cur = endNode; cur != null; cur = parent.get(cur)) path.add(cur);
        Collections.reverse(path);

        for (BlockPos place : path) {
            if (!isAir(place)) continue;
            for (BlockData bd : supports) {
                if (!isAdjacent(bd.pos, place)) continue;
                if (tryPlaceOnBlock(bd.pos, eye, reach, place)) return;
            }
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos sup = place.offset(f);
                if (isAir(sup)) continue;
                if (tryPlaceOnBlock(sup, eye, reach, place)) return;
            }
        }
        sidesAim(eye, reach, feetPos);
    }

    private boolean isAdjacent(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX()-b.getX()), dy = Math.abs(a.getY()-b.getY()), dz = Math.abs(a.getZ()-b.getZ());
        return (dx + dy + dz) == 1;
    }

    private boolean tryPlaceOnBlock(BlockPos supportBlock, Vec3 eye, double reach, BlockPos targetPos) {
        int n = (int) Math.round(1 / STEP);
        for (EnumFacing facing : EnumFacing.values()) {
            if (!supportBlock.offset(facing).equals(targetPos)) continue;
            for (int r = 0; r <= n; r++) {
                double v = Math.max(0, Math.min(1, r * STEP + (Math.random() * JIT * 2 - JIT)));
                for (int c = 0; c <= n; c++) {
                    double u   = Math.max(0, Math.min(1, c * STEP + (Math.random() * JIT * 2 - JIT)));
                    Vec3   hit = getHitPosOnFace(supportBlock, facing, u, v);
                    float[] rot = getRotationsWrapped(eye, hit.xCoord, hit.yCoord, hit.zCoord);
                    MovingObjectPosition mop = rayTraceBlock(rot[0], rot[1], reach);
                    if (mop != null
                            && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                            && mop.getBlockPos().equals(supportBlock)
                            && mop.sideHit == facing) {
                        targetBlock  = supportBlock;
                        targetFacing = facing;
                        targetHitVec = mop.hitVec;
                        aimYaw       = rot[0];
                        aimPitch     = rot[1];
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void sidesAim(Vec3 eye, double reach, BlockPos feetPos) {
        List<BlockPos> goals = new ArrayList<>();
        for (int[] d : DIRS) {
            BlockPos h = feetPos.add(d[0], 1, d[2]); if (isAir(h)) goals.add(h);
        }
        for (int[] d : DIRS) {
            BlockPos f = feetPos.add(d[0], 0, d[2]); if (isAir(f)) goals.add(f);
        }
        findBestForGoals(goals, eye, reach);
    }

    private void findBestForGoals(List<BlockPos> goals, Vec3 eye, double reach) {
        int n = (int) Math.round(1 / STEP);
        for (BlockPos goal : goals) {
            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos support = goal.offset(facing);
                if (isAir(support)) continue;
                Vec3 center = new Vec3(support.getX()+0.5, support.getY()+0.5, support.getZ()+0.5);
                if (eye.distanceTo(center) > reach) continue;
                for (int r = 0; r <= n; r++) {
                    double v = Math.max(0, Math.min(1, r * STEP + (Math.random() * JIT * 2 - JIT)));
                    for (int c = 0; c <= n; c++) {
                        double u   = Math.max(0, Math.min(1, c * STEP + (Math.random() * JIT * 2 - JIT)));
                        Vec3   hit = getHitPosOnFace(support, facing.getOpposite(), u, v);
                        float[] rot = getRotationsWrapped(eye, hit.xCoord, hit.yCoord, hit.zCoord);
                        MovingObjectPosition mop = rayTraceBlock(rot[0], rot[1], reach);
                        if (mop != null
                                && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                                && mop.getBlockPos().equals(support)
                                && mop.sideHit == facing.getOpposite()) {
                            targetBlock  = support;
                            targetFacing = facing.getOpposite();
                            targetHitVec = mop.hitVec;
                            aimYaw       = rot[0];
                            aimPitch     = rot[1];
                            return;
                        }
                    }
                }
            }
        }
    }

    private Vec3 getHitPosOnFace(BlockPos block, EnumFacing face, double u, double v) {
        double x = block.getX()+0.5, y = block.getY()+0.5, z = block.getZ()+0.5;
        switch (face) {
            case DOWN:  y = block.getY()+INSET;       x = block.getX()+u; z = block.getZ()+v; break;
            case UP:    y = block.getY()+1.0-INSET;   x = block.getX()+u; z = block.getZ()+v; break;
            case NORTH: z = block.getZ()+INSET;       x = block.getX()+u; y = block.getY()+v; break;
            case SOUTH: z = block.getZ()+1.0-INSET;   x = block.getX()+u; y = block.getY()+v; break;
            case WEST:  x = block.getX()+INSET;       z = block.getZ()+u; y = block.getY()+v; break;
            case EAST:  x = block.getX()+1.0-INSET;   z = block.getZ()+u; y = block.getY()+v; break;
        }
        return new Vec3(x, y, z);
    }

    private boolean isAir(BlockPos pos) {
        Block b = mc.theWorld.getBlockState(pos).getBlock();
        return b == Blocks.air || b == Blocks.water || b == Blocks.flowing_water
                || b == Blocks.lava || b == Blocks.flowing_lava || b == Blocks.fire;
    }

    private void updateProgress() {
        Vec3     playerPos = mc.thePlayer.getPositionVector();
        BlockPos feetPos   = new BlockPos(playerPos.xCoord, playerPos.yCoord, playerPos.zCoord);
        int filled = 0;
        if (!isAir(feetPos.up(2))) filled++;
        for (int[] d : DIRS) {
            if (!isAir(feetPos.add(d[0], 0, d[2]))) filled++;
            if (!isAir(feetPos.add(d[0], 1, d[2]))) filled++;
        }
        progress = (float) filled / 9.0f;
    }

    private Color getProgressColor() {
        if (progress <= 0.33f) return new Color(255, 85, 85);
        if (progress <= 0.66f) return new Color(255, 255, 85);
        return new Color(85, 255, 85);
    }

    private MovingObjectPosition rayTraceBlock(float yaw, float pitch, double range) {
        float  yr  = (float) Math.toRadians(yaw);
        float  pr  = (float) Math.toRadians(pitch);
        double x   = -Math.sin(yr) * Math.cos(pr);
        double y   = -Math.sin(pr);
        double z   =  Math.cos(yr) * Math.cos(pr);
        Vec3   start = mc.thePlayer.getPositionEyes(1.0f);
        Vec3   end   = start.addVector(x * range, y * range, z * range);
        return mc.theWorld.rayTraceBlocks(start, end);
    }

    private boolean withinRotationTolerance(float targetYaw, float targetPitch) {
        float dy = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw   - serverYaw));
        float dp = Math.abs(MathHelper.wrapAngleTo180_float(targetPitch - serverPitch));
        return dy <= (int) rotationTolerance.getValue() && dp <= (int) rotationTolerance.getValue();
    }

    private double dist2PointAABB(Vec3 p, int x, int y, int z) {
        double cx = clamp(p.xCoord, x, x+1), cy = clamp(p.yCoord, y, y+1), cz = clamp(p.zCoord, z, z+1);
        double dx = p.xCoord-cx, dy = p.yCoord-cy, dz = p.zCoord-cz;
        return dx*dx + dy*dy + dz*dz;
    }

    private double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private float[] getRotationsWrapped(Vec3 eye, double tx, double ty, double tz) {
        double dx = tx-eye.xCoord, dy = ty-eye.yCoord, dz = tz-eye.zCoord;
        double hd = Math.sqrt(dx*dx + dz*dz);
        float  yaw   = normYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f);
        float  pitch = (float) Math.toDegrees(-Math.atan2(dy, hd));
        return new float[]{yaw, pitch};
    }

    private float normYaw(float yaw) {
        yaw = ((yaw % 360f) + 360f) % 360f;
        return yaw > 180f ? yaw - 360f : yaw;
    }

    public int getSlot() { return lastSlot; }

    private static class BlockData {
        BlockPos pos; double distance;
        BlockData(BlockPos pos, double distance) { this.pos = pos; this.distance = distance; }
    }
}
