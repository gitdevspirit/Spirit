package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.enums.ChatColors;
import myau.enums.DelayModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.management.RotationState;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BedNuker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final TimerUtil timer = new TimerUtil();
    private final ArrayList<BlockPos> bedWhitelist = new ArrayList<>();
    private final Color colorRed    = new Color(ChatColors.RED.toAwtColor());
    private final Color colorYellow = new Color(ChatColors.YELLOW.toAwtColor());
    private final Color colorGreen  = new Color(ChatColors.GREEN.toAwtColor());

    private BlockPos targetBed     = null;
    private int      breakStage    = 0;
    private int      tickCounter   = 0;
    private float    breakProgress = 0.0F;
    private boolean  isBed         = false;
    private int      savedSlot     = -1;
    private boolean  readyToBreak  = false;
    private boolean  breaking      = false;
    private boolean  waitingForStart = false;

    // Settings
    public final DropdownSetting mode           = new DropdownSetting("Mode",            0, "NORMAL", "SWAP");
    public final SliderSetting   range          = new SliderSetting("Range",             4.5, 3.0, 6.0, 0.1);
    public final SliderSetting   speed          = new SliderSetting("Speed",               0,   0, 100,   1);
    public final BooleanSetting  groundSpeed    = new BooleanSetting("Ground Spoof",     false);
    public final DropdownSetting ignoreVelocity = new DropdownSetting("Ignore Velocity", 0, "NONE", "CANCEL", "DELAY");
    public final BooleanSetting  surroundings   = new BooleanSetting("Surroundings",     true);
    public final BooleanSetting  toolCheck      = new BooleanSetting("Tool Check",       true);
    public final BooleanSetting  whiteList      = new BooleanSetting("Whitelist",        true);
    public final BooleanSetting  swing          = new BooleanSetting("Swing",            true);
    public final DropdownSetting moveFix        = new DropdownSetting("Move Fix",        1, "NONE", "SILENT", "STRICT");
    public final DropdownSetting showTarget     = new DropdownSetting("Show Target",     1, "NONE", "DEFAULT", "HUD");
    public final DropdownSetting showProgress   = new DropdownSetting("Show Progress",   1, "NONE", "DEFAULT", "HUD");

    public BedNuker() {
        super("BedNuker", false);
        register(mode);
        register(range);
        register(speed);
        register(groundSpeed);
        register(ignoreVelocity);
        register(surroundings);
        register(toolCheck);
        register(whiteList);
        register(swing);
        register(moveFix);
        register(showTarget);
        register(showProgress);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void resetBreaking() {
        if (targetBed != null) mc.theWorld.sendBlockBreakProgress(mc.thePlayer.getEntityId(), targetBed, -1);
        targetBed     = null;
        breakStage    = 0;
        tickCounter   = 0;
        breakProgress = 0.0F;
        isBed         = false;
        readyToBreak  = false;
        breaking      = false;
    }

    private float calcProgress() {
        if (targetBed == null) return 0.0F;
        float progress = breakProgress;
        if (groundSpeed.getValue()) {
            int slot = ItemUtil.findInventorySlot(mc.thePlayer.inventory.currentItem, mc.theWorld.getBlockState(targetBed).getBlock());
            progress = (float) tickCounter * getBreakDelta(mc.theWorld.getBlockState(targetBed), targetBed, slot, true);
        }
        return Math.min(1.0F, progress / (1.0F - 0.3F * ((float)(int) speed.getValue() / 100.0F)));
    }

    private void restoreSlot() {
        if (savedSlot != -1) {
            mc.thePlayer.inventory.currentItem = savedSlot;
            syncHeldItem();
            savedSlot = -1;
        }
    }

    private void syncHeldItem() {
        int currentPlayerItem = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
        if (mc.thePlayer.inventory.currentItem != currentPlayerItem) mc.thePlayer.stopUsingItem();
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
    }

    private boolean hasProperTool(Block block) {
        Material material = block.getMaterial();
        if (material != Material.iron && material != Material.anvil && material != Material.rock) return true;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemPickaxe) return true;
        }
        return false;
    }

    private EnumFacing getHitFacing(BlockPos blockPos) {
        double x = (double) blockPos.getX() + 0.5 - mc.thePlayer.posX;
        double y = (double) blockPos.getY() + 0.25 - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
        double z = (double) blockPos.getZ() + 0.5 - mc.thePlayer.posZ;
        float[] rotations = RotationUtil.getRotationsTo(x, y, z, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        MovingObjectPosition mop = RotationUtil.rayTrace(rotations[0], rotations[1], 8.0, 1.0F);
        return mop == null ? EnumFacing.UP : mop.sideHit;
    }

    private float getDigSpeed(IBlockState iBlockState, int slot, boolean onGround) {
        ItemStack item = mc.thePlayer.inventory.getStackInSlot(slot);
        float digSpeed = item == null ? 1.0F : item.getItem().getDigSpeed(item, iBlockState);
        if (digSpeed > 1.0F) {
            int lvl = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, item);
            if (lvl > 0) digSpeed += (float)(lvl * lvl + 1);
        }
        if (mc.thePlayer.isPotionActive(Potion.digSpeed))
            digSpeed *= 1.0F + (float)(mc.thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1) * 0.2F;
        if (mc.thePlayer.isPotionActive(Potion.digSlowdown)) {
            switch (mc.thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) {
                case 0: digSpeed *= 0.3F;    break;
                case 1: digSpeed *= 0.09F;   break;
                case 2: digSpeed *= 0.0027F; break;
                default: digSpeed *= 8.1E-4F;
            }
        }
        if (mc.thePlayer.isInsideOfMaterial(Material.water) && !EnchantmentHelper.getAquaAffinityModifier(mc.thePlayer))
            digSpeed /= 5.0F;
        if (!onGround) digSpeed /= 5.0F;
        return digSpeed;
    }

    boolean canHarvest(Block block, int slot) {
        if (block.getMaterial().isToolNotRequired()) return true;
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
        return stack != null && stack.canHarvestBlock(block);
    }

    private float getBreakDelta(IBlockState iBlockState, BlockPos blockPos, int slot, boolean onGround) {
        Block block = iBlockState.getBlock();
        float hardness = block.getBlockHardness(mc.theWorld, blockPos);
        float boost = canHarvest(block, slot) ? 30.0F : 100.0F;
        return hardness < 0.0F ? 0.0F : getDigSpeed(iBlockState, slot, onGround) / hardness / boost;
    }

    private float calcBlockStrength(BlockPos blockPos) {
        IBlockState blockState = mc.theWorld.getBlockState(blockPos);
        int slot = ItemUtil.findInventorySlot(mc.thePlayer.inventory.currentItem, blockState.getBlock());
        return getBreakDelta(blockState, blockPos, slot, mc.thePlayer.onGround);
    }

    private BlockPos validateBedPlacement(BlockPos bedPosition) {
        IBlockState blockState = mc.theWorld.getBlockState(bedPosition);
        if (!(blockState.getBlock() instanceof BlockBed)) return null;
        ArrayList<BlockPos> pos = new ArrayList<>();
        EnumPartType partType = blockState.getValue(BlockBed.PART);
        EnumFacing facing     = blockState.getValue(BlockBed.FACING);
        for (BlockPos blockPos : Arrays.asList(bedPosition, bedPosition.offset(partType == EnumPartType.HEAD ? facing.getOpposite() : facing))) {
            for (EnumFacing enumFacing : Arrays.asList(EnumFacing.UP, EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST)) {
                Block block = mc.theWorld.getBlockState(blockPos.offset(enumFacing)).getBlock();
                if (BlockUtil.isReplaceable(block)) return null;
                if (!(block instanceof BlockBed)) pos.add(blockPos.offset(enumFacing));
            }
        }
        if (pos.isEmpty()) return null;
        pos.sort((a, b) -> {
            int o = Float.compare(calcBlockStrength(b), calcBlockStrength(a));
            return o != 0 ? o : Double.compare(
                    a.distanceSqToCenter(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ),
                    b.distanceSqToCenter(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ));
        });
        return pos.get(0);
    }

    private BlockPos findNearestBed() {
        return findTargetBed(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
    }

    private BlockPos findTargetBed(double x, double y, double z) {
        ArrayList<BlockPos> targets = new ArrayList<>();
        int sX = MathHelper.floor_double(x), sY = MathHelper.floor_double(y), sZ = MathHelper.floor_double(z);
        for (int i = sX - 6; i <= sX + 6; i++) {
            for (int j = sY - 6; j <= sY + 6; j++) {
                for (int k = sZ - 6; k <= sZ + 6; k++) {
                    BlockPos newPos = new BlockPos(i, j, k);
                    if (whiteList.getValue() && bedWhitelist.contains(newPos)) continue;
                    Block block = mc.theWorld.getBlockState(newPos).getBlock();
                    if (block instanceof BlockBed && PlayerUtil.isBlockWithinReach(newPos, x, y, z, range.getValue()))
                        targets.add(newPos);
                }
            }
        }
        if (targets.isEmpty()) return null;
        targets.sort(Comparator.comparingDouble(bp ->
                bp.distanceSqToCenter(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ)));
        for (BlockPos blockPos : targets) {
            if (surroundings.getValue()) {
                BlockPos pos = validateBedPlacement(blockPos);
                if (pos != null) {
                    Block block = mc.theWorld.getBlockState(pos).getBlock();
                    if (toolCheck.getValue() && !hasProperTool(block)) continue;
                    return pos;
                }
            } else {
                return blockPos;
            }
        }
        return null;
    }

    private void doSwing() {
        if (swing.getValue()) mc.thePlayer.swingItem();
        else PacketUtil.sendPacket(new C0APacketAnimation());
    }

    private Color getProgressColor(int modeIndex) {
        switch (modeIndex) {
            case 1:
                float p = calcProgress();
                if (p <= 0.5F) return ColorUtil.interpolate(p / 0.5F, colorRed, colorYellow);
                return ColorUtil.interpolate((p - 0.5F) / 0.5F, colorYellow, colorGreen);
            case 2:
                return ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
            default:
                return new Color(-1);
        }
    }

    public boolean isReady()    { return targetBed != null && readyToBreak; }
    public boolean isBreaking() { return targetBed != null && breaking; }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventTarget(Priority.HIGH)
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        AutoBlockIn autoBlockIn = (AutoBlockIn) Myau.moduleManager.modules.get(AutoBlockIn.class);
        if (autoBlockIn.isEnabled()) return;

        if (targetBed != null) {
            if (mc.theWorld.isAirBlock(targetBed) || !PlayerUtil.canReach(targetBed, range.getValue())) {
                restoreSlot(); resetBreaking();
            } else if (!isBed) {
                BlockPos nearestBed = findNearestBed();
                if (nearestBed != null && mc.theWorld.getBlockState(nearestBed).getBlock() instanceof BlockBed)
                    resetBreaking();
            }
        }

        if (targetBed != null) {
            int slot = ItemUtil.findInventorySlot(mc.thePlayer.inventory.currentItem, mc.theWorld.getBlockState(targetBed).getBlock());
            if (mode.getIndex() == 0 && savedSlot == -1) {
                savedSlot = mc.thePlayer.inventory.currentItem;
                mc.thePlayer.inventory.currentItem = slot;
                syncHeldItem();
            }
            float speedFactor = 1.0F - 0.3F * ((float)(int) speed.getValue() / 100.0F);
            switch (breakStage) {
                case 0:
                    if (!mc.thePlayer.isUsingItem()) {
                        doSwing();
                        PacketUtil.sendPacket(new C07PacketPlayerDigging(Action.START_DESTROY_BLOCK, targetBed, getHitFacing(targetBed)));
                        doSwing();
                        mc.effectRenderer.addBlockHitEffects(targetBed, getHitFacing(targetBed));
                        breakStage = 1;
                    }
                    break;
                case 1:
                    if (mode.getIndex() == 1) readyToBreak = false;
                    breaking = true;
                    tickCounter++;
                    breakProgress += getBreakDelta(mc.theWorld.getBlockState(targetBed), targetBed, slot, mc.thePlayer.onGround);
                    float tick  = (float) tickCounter;
                    boolean canBreak = mc.thePlayer.onGround && groundSpeed.getValue();
                    float delta = tick * getBreakDelta(mc.theWorld.getBlockState(targetBed), targetBed, slot, canBreak);
                    mc.effectRenderer.addBlockHitEffects(targetBed, getHitFacing(targetBed));
                    if (breakProgress >= speedFactor || delta >= speedFactor) {
                        if (mode.getIndex() == 1) {
                            readyToBreak = true;
                            savedSlot = mc.thePlayer.inventory.currentItem;
                            mc.thePlayer.inventory.currentItem = slot;
                            syncHeldItem();
                            if (mc.thePlayer.isUsingItem()) {
                                savedSlot = mc.thePlayer.inventory.currentItem;
                                mc.thePlayer.inventory.currentItem = (mc.thePlayer.inventory.currentItem + 1) % 9;
                                syncHeldItem();
                            }
                        }
                        breaking = false;
                        PacketUtil.sendPacket(new C07PacketPlayerDigging(Action.STOP_DESTROY_BLOCK, targetBed, getHitFacing(targetBed)));
                        doSwing();
                        IBlockState blockState_ = mc.theWorld.getBlockState(targetBed);
                        Block block = blockState_.getBlock();
                        if (block.getMaterial() != Material.air) {
                            mc.theWorld.playAuxSFX(2001, targetBed, Block.getStateId(blockState_));
                            mc.theWorld.setBlockToAir(targetBed);
                        }
                        if (block instanceof BlockBed) timer.reset();
                        breakStage = 2;
                    }
                    break;
                case 2:
                    restoreSlot(); resetBreaking(); break;
            }
            if (targetBed != null) return;
        }

        if (mc.thePlayer.capabilities.allowEdit && timer.hasTimeElapsed(500)) {
            targetBed     = findNearestBed();
            breakStage    = 0;
            tickCounter   = 0;
            breakProgress = 0.0F;
            isBed         = targetBed != null && mc.theWorld.getBlockState(targetBed).getBlock() instanceof BlockBed;
            restoreSlot();
            if (targetBed != null) readyToBreak = true;
        }
        if (targetBed == null) Myau.delayManager.setDelayState(false, DelayModules.BED_NUKER);
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        AutoBlockIn autoBlockIn = (AutoBlockIn) Myau.moduleManager.modules.get(AutoBlockIn.class);
        if (autoBlockIn.isEnabled()) return;
        if (isReady()) {
            double x = (double) targetBed.getX() + 0.5 - mc.thePlayer.posX;
            double y = (double) targetBed.getY() + 0.5 - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
            double z = (double) targetBed.getZ() + 0.5 - mc.thePlayer.posZ;
            float[] rotations = RotationUtil.getRotationsTo(x, y, z, event.getYaw(), event.getPitch());
            event.setRotation(rotations[0], rotations[1], 5);
            event.setPervRotation(moveFix.getIndex() != 0 ? rotations[0] : mc.thePlayer.rotationYaw, 5);
        }
    }

    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!isEnabled()) return;
        if (isBreaking()
                && !Myau.playerStateManager.attacking
                && !Myau.playerStateManager.digging
                && !Myau.playerStateManager.placing
                && !Myau.playerStateManager.swinging) {
            doSwing();
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled()) return;
        if (moveFix.getIndex() == 1 && RotationState.isActived()
                && RotationState.getPriority() == 5.0F && MoveUtil.isForwardPressed())
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
    }

    @EventTarget(Priority.HIGH)
    public void onKnockback(KnockbackEvent event) {
        if (!isEnabled() || event.isCancelled() || event.getY() <= 0.0) return;
        if (ignoreVelocity.getIndex() == 1 && targetBed != null) {
            event.setCancelled(true);
            event.setX(mc.thePlayer.motionX);
            event.setY(mc.thePlayer.motionY);
            event.setZ(mc.thePlayer.motionZ);
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || targetBed == null || (isBed && surroundings.getValue())) return;
        if (showProgress.getIndex() == 0) return;

        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        float scale = (float) hud.scale.getValue();
        String text = String.format("%d%%", (int)(calcProgress() * 100.0F));
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 0.0F);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        int width = mc.fontRendererObj.getStringWidth(text);
        mc.fontRendererObj.drawString(text,
                (float) new ScaledResolution(mc).getScaledWidth()  / 2.0F / scale - (float) width / 2.0F,
                (float) new ScaledResolution(mc).getScaledHeight() / 5.0F * 2.0F / scale,
                getProgressColor(showProgress.getIndex()).getRGB() & 16777215 | -1090519040,
                hud.shadow.getValue());
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    @EventTarget(Priority.LOW)
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || targetBed == null || mc.theWorld.isAirBlock(targetBed)) return;
        mc.theWorld.sendBlockBreakProgress(mc.thePlayer.getEntityId(), targetBed, (int)(calcProgress() * 10.0F) - 1);
        if (showTarget.getIndex() == 0) return;
        BedESP bedESP = (BedESP) Myau.moduleManager.modules.get(BedESP.class);
        Color color = getProgressColor(showTarget.getIndex());
        RenderUtil.enableRenderState();
        double newHeight = isBed ? bedESP.getHeight() : 1.0;
        RenderUtil.drawBlockBox(targetBed, newHeight, color.getRed(), color.getBlue(), color.getGreen());
        RenderUtil.disableRenderState();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) { waitingForStart = false; }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.isCancelled()) return;
        if (event.getPacket() instanceof S02PacketChat) {
            String text = ((S02PacketChat) event.getPacket()).getChatComponent().getFormattedText();
            if (text.contains("§e§lProtect your bed") || text.contains("§e§lDestroy the enemy bed"))
                waitingForStart = true;
        }
        if (event.getPacket() instanceof S08PacketPlayerPosLook && waitingForStart) {
            waitingForStart = false;
            bedWhitelist.clear();
            scheduler.schedule(() -> {
                int sX = MathHelper.floor_double(mc.thePlayer.posX);
                int sY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
                int sZ = MathHelper.floor_double(mc.thePlayer.posZ);
                for (int i = sX - 25; i <= sX + 25; i++)
                    for (int j = sY - 25; j <= sY + 25; j++)
                        for (int k = sZ - 25; k <= sZ + 25; k++) {
                            BlockPos blockPos = new BlockPos(i, j, k);
                            if (mc.theWorld.getBlockState(blockPos).getBlock() instanceof BlockBed)
                                bedWhitelist.add(blockPos);
                        }
            }, 1L, TimeUnit.SECONDS);
        }
        if (isEnabled() && targetBed != null && ignoreVelocity.getIndex() == 2
                && Myau.delayManager.getDelayModule() != DelayModules.BED_NUKER) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
                if (packet.getEntityID() == mc.thePlayer.getEntityId() && packet.getMotionY() > 0) {
                    Myau.delayManager.delay(DelayModules.BED_NUKER);
                    Myau.delayManager.delayedPacket.offer(packet);
                    event.setCancelled(true);
                }
            }
            if (event.getPacket() instanceof S27PacketExplosion) {
                S27PacketExplosion explosion = (S27PacketExplosion) event.getPacket();
                if (explosion.func_149149_c() != 0.0F || explosion.func_149144_d() != 0.0F || explosion.func_149147_e() != 0.0F) {
                    Myau.delayManager.delay(DelayModules.BED_NUKER);
                    Myau.delayManager.delayedPacket.offer(explosion);
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (!isEnabled()) return;
        if (isReady() || (targetBed != null && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK))
            event.setCancelled(true);
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (isEnabled() && isReady()) event.setCancelled(true);
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (!isEnabled()) return;
        if (isReady() || (targetBed != null && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK))
            event.setCancelled(true);
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (isEnabled() && savedSlot != -1) event.setCancelled(true);
    }

    @Override
    public void onDisabled() {
        resetBreaking();
        savedSlot = -1;
        Myau.delayManager.setDelayState(false, DelayModules.BED_NUKER);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{ mode.getOptions()[mode.getIndex()] };
    }
}