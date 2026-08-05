package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import java.util.Random;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class Autoblock extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting  range           = register(new SliderSetting("Range",              3.5, 1.0,  8.0,  0.1));
    public final SliderSetting  blockDuration   = register(new SliderSetting("Block Duration (ms)", 100, 50,  500,  10));
    public final SliderSetting  unblockDuration = register(new SliderSetting("Unblock Duration (ms)", 80, 10, 200, 10));
    public final SliderSetting  postHitDelay    = register(new SliderSetting("Post-Hit Delay (ms)", 50, 0, 200, 10));
    public final SliderSetting  lagChance       = register(new SliderSetting("Lag Chance (%)",     0,   0,    100,  1));
    public final SliderSetting  lagMaxDuration  = register(new SliderSetting("Lag Max (ms)",       200, 50,   1000, 10));
    public final BooleanSetting preventDelay    = register(new BooleanSetting("Prevent Delay",     true));
    public final BooleanSetting blockAgain      = register(new BooleanSetting("Block Again",       true));
    public final BooleanSetting forceAnimation  = register(new BooleanSetting("Force Animation",   false));
    public final BooleanSetting animInRange     = register(new BooleanSetting("Anim Only In Range", true));
    public final BooleanSetting requireLMB      = register(new BooleanSetting("Require LMB",       false));
    public final BooleanSetting requireRMB      = register(new BooleanSetting("Require RMB",       false));
    public final BooleanSetting requireDamaged  = register(new BooleanSetting("Only When Damaged",  false));
    private static final Random rng = new Random();
    private boolean blockingState  = false;
    private boolean fakeBlockState = false;
    private long    blockStartMs   = 0L;
    private long    unblockStartMs = 0L;
    private long    lastAttackMs   = 0L;
    private boolean lagging        = false;
    private long    lagStartMs     = 0L;
    private long    lastDamagedMs  = 0L;
    private int     lastHurtTime   = 0;
    
    private enum BlockState {
        IDLE,      // Not blocking
        BLOCKING,  // Currently blocking
        UNBLOCKED  // Released block, waiting to block again
    }
    
    private BlockState state = BlockState.IDLE;

    public Autoblock() { super("Autoblock", false); }

    public boolean isBlocking() {
        if (forceAnimation.getValue()) {
            if (animInRange.getValue()) return fakeBlockState && ItemUtil.isHoldingSword();
            return isEnabled() && ItemUtil.isHoldingSword();
        }
        return fakeBlockState && ItemUtil.isHoldingSword();
    }

    public boolean isPlayerBlocking() {
        return (mc.thePlayer.isUsingItem() || blockingState) && ItemUtil.isHoldingSword();
    }

    public boolean isInLegitFullHoldPhase() { return false; }

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) lastHurtTime = mc.thePlayer.hurtResistantTime;
        state = BlockState.IDLE;
    }

    @Override
    public void onDisabled() {
        if (blockingState) stopBlock();
        if (lagging) { Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK); lagging = false; }
        blockingState = false; fakeBlockState = false; state = BlockState.IDLE;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!ItemUtil.isHoldingSword()) { cleanup(); return; }

        int currentHurtTime = mc.thePlayer.hurtResistantTime;
        if (currentHurtTime > lastHurtTime) lastDamagedMs = System.currentTimeMillis();
        lastHurtTime = currentHurtTime;

        EntityLivingBase nearestTarget = getNearestTarget();
        boolean hasTarget = nearestTarget != null;
        fakeBlockState = hasTarget;

        if (!checkConditions() || !hasTarget) {
            if (blockingState) stopBlock();
            fakeBlockState = false;
            state = BlockState.IDLE;
            return;
        }

        if (lagging) {
            long elapsed = System.currentTimeMillis() - lagStartMs;
            if (elapsed >= lagMaxDuration.getValue()) {
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                lagging = false;
                if (!blockAgain.getValue() && blockingState) stopBlock();
            }
            return;
        }

        long now = System.currentTimeMillis();
        long timeSinceAttack = now - lastAttackMs;

        switch (state) {
            case IDLE:
                // Start blocking
                if (!isPlayerBlocking()) startBlock();
                blockStartMs = now;
                state = BlockState.BLOCKING;
                break;
                
            case BLOCKING:
                // Stay blocked for the configured duration
                long blockElapsed = now - blockStartMs;
                // Add post-hit delay: don't unblock until enough time has passed since last attack
                boolean canUnblock = timeSinceAttack >= postHitDelay.getValue();
                
                if (blockElapsed >= blockDuration.getValue() && canUnblock) {
                    // Time to unblock
                    if (isPlayerBlocking()) {
                        stopBlock();
                        if (lagChance.getValue() > 0 && Math.random() * 100 < lagChance.getValue()) {
                            Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                            lagging = true;
                            lagStartMs = now;
                        }
                    }
                    unblockStartMs = now;
                    state = BlockState.UNBLOCKED;
                }
                break;
                
            case UNBLOCKED:
                // Stay unblocked briefly, then re-block
                long unblockElapsed = now - unblockStartMs;
                if (unblockElapsed >= unblockDuration.getValue()) {
                    state = BlockState.IDLE; // Will re-block on next tick
                }
                break;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.POST || mc.thePlayer == null) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (blockingState && held != null && !mc.thePlayer.isBlocking()) {
            mc.thePlayer.setItemInUse(held, held.getMaxItemUseDuration());
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!isEnabled()) return;
        lastAttackMs = System.currentTimeMillis();
        // Keep blocking during and after attack
        // Just reset the block timer to maintain blocking
        if (state == BlockState.BLOCKING) {
            blockStartMs = System.currentTimeMillis(); // Reset timer to stay blocked
        } else if (state != BlockState.BLOCKING) {
            // If not blocking, start blocking immediately after attack
            state = BlockState.IDLE; // Will start blocking on next update
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.RECEIVE || mc.thePlayer == null) return;
        if (event.getPacket() instanceof net.minecraft.network.play.server.S06PacketUpdateHealth) {
            net.minecraft.network.play.server.S06PacketUpdateHealth pkt =
                (net.minecraft.network.play.server.S06PacketUpdateHealth) event.getPacket();
            if (pkt.getHealth() < mc.thePlayer.getHealth()) {
                lastDamagedMs = System.currentTimeMillis();
                // When taking damage, ensure we're blocking
                if (state != BlockState.BLOCKING && !lagging) {
                    state = BlockState.IDLE; // Will start blocking on next tick
                }
                if (lagging && preventDelay.getValue()) {
                    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    lagging = false;
                }
            }
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (isEnabled() && blockingState) mc.thePlayer.movementInput.jump = false;
    }

    @EventTarget
    public void onCancelUse(CancelUseEvent event) {
        if (isEnabled() && blockingState) event.setCancelled(true);
    }

    private void cleanup() {
        if (blockingState) stopBlock();
        blockingState = false; fakeBlockState = false; state = BlockState.IDLE;
    }

    private boolean checkConditions() {
        if (requireLMB.getValue() && !org.lwjgl.input.Mouse.isButtonDown(0)) return false;
        if (requireRMB.getValue() && !org.lwjgl.input.Mouse.isButtonDown(1)) return false;
        if (requireDamaged.getValue() && System.currentTimeMillis() - lastDamagedMs > 3000L) return false;
        return true;
    }

    private EntityLivingBase getNearestTarget() {
        EntityLivingBase nearest = null;
        double nearestDist = range.getValue();
        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof EntityLivingBase)) continue;
            EntityLivingBase e = (EntityLivingBase) obj;
            if (e == mc.thePlayer || e.isDead || e.deathTime > 0) continue;
            double dist = RotationUtil.distanceToEntity(e);
            if (dist <= nearestDist) { nearestDist = dist; nearest = e; }
        }
        return nearest;
    }

    private void startBlock() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemSword)) return;
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(held));
        mc.thePlayer.setItemInUse(held, held.getMaxItemUseDuration());
        blockingState = true;
    }

    private void stopBlock() {
        PacketUtil.sendPacket(new C07PacketPlayerDigging(
            C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        mc.thePlayer.stopUsingItem();
        blockingState = false;
    }

}

