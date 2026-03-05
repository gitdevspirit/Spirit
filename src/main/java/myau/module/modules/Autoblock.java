package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.event.types.EventType;
import myau.events.*;
import myau.events.PlayerUpdateEvent;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/**
 * Autoblock — predicts incoming damage using hurtResistantTime and blocks
 * before the hit lands, releases after. Unblocks during attacks for full damage.
 */
public class Autoblock extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // ── Settings ──────────────────────────────────────────────────────────────
    public final SliderSetting  range           = register(new SliderSetting("Range",              3.5,  1.0,  8.0,  0.1));
    public final SliderSetting  maxHurtTime     = register(new SliderSetting("Max Hurt Time (ms)", 200,  50,   500,  10));
    public final SliderSetting  maxHoldDuration = register(new SliderSetting("Max Hold (ms)",      150,  50,   500,  10));

    public final SliderSetting  lagChance       = register(new SliderSetting("Lag Chance (%)",     0,    0,    100,  1));
    public final SliderSetting  lagMaxDuration  = register(new SliderSetting("Lag Max (ms)",       200,  50,   1000, 10));
    public final BooleanSetting preventDelay    = register(new BooleanSetting("Prevent Delay",     true));
    public final BooleanSetting blockAgain      = register(new BooleanSetting("Block Again",       true));

    public final BooleanSetting forceAnimation  = register(new BooleanSetting("Force Animation",   false));
    public final BooleanSetting animOnlyInRange = register(new BooleanSetting("Anim Only In Range", true));

    public final BooleanSetting requireLMB      = register(new BooleanSetting("Require LMB",       false));
    public final BooleanSetting requireRMB      = register(new BooleanSetting("Require RMB",       false));
    public final BooleanSetting requireDamaged  = register(new BooleanSetting("Only When Damaged",  false));

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean blockingState  = false;
    private boolean fakeBlockState = false;
    private boolean isBlocking     = false;
    private long    blockStartMs   = 0L;
    private boolean pendingBlock    = false;
    private boolean attackedThisTick  = false; // set by AttackEvent, suppresses C07/C08
    private boolean lagging        = false;
    private long    lagStartMs     = 0L;
    private long    lastDamagedMs  = 0L;
    private int     lastHurtTime   = 0;

    public Autoblock() {
        super("Autoblock", false);
    }

    // ── Public API ────────────────────────────────────────────────────────────
    public boolean isBlocking() {
        if (forceAnimation.getValue()) {
            if (animOnlyInRange.getValue()) return fakeBlockState && ItemUtil.isHoldingSword();
            return isEnabled() && ItemUtil.isHoldingSword();
        }
        return fakeBlockState && ItemUtil.isHoldingSword();
    }

    public boolean isPlayerBlocking() {
        return blockingState && ItemUtil.isHoldingSword();
    }

    public boolean isInLegitFullHoldPhase() { return false; }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) lastHurtTime = mc.thePlayer.hurtResistantTime;
    }

    @Override
    public void onDisabled() { cleanup(); }

    private void cleanup() {
        if (blockingState) stopBlock();
        if (lagging) { Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK); lagging = false; }
        blockingState = false; fakeBlockState = false; isBlocking = false; blockStartMs = 0L; pendingBlock = false;
    }

    // ── Events ────────────────────────────────────────────────────────────────
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        attackedThisTick = false; // reset each tick
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!ItemUtil.isHoldingSword()) { cleanup(); return; }

        // Track damage for requireDamaged condition
        int currentHurtTime = mc.thePlayer.hurtResistantTime;
        if (currentHurtTime > lastHurtTime) lastDamagedMs = System.currentTimeMillis();
        lastHurtTime = currentHurtTime;

        if (!checkConditions()) {
            if (blockingState) stopBlock();
            fakeBlockState = false; isBlocking = false; return;
        }

        EntityLivingBase nearestTarget = getNearestTarget();
        boolean targetInRange = nearestTarget != null;
        fakeBlockState = targetInRange;

        if (!targetInRange) {
            if (blockingState && !attackedThisTick) stopBlock();
            isBlocking = false; return;
        }

        // Lag management
        if (lagging) {
            long lagElapsed = System.currentTimeMillis() - lagStartMs;
            if (lagElapsed >= lagMaxDuration.getValue()) {
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                lagging = false;
                if (blockAgain.getValue()) pendingBlock = true;
                else { if (blockingState) stopBlock(); isBlocking = false; }
            }
            return;
        }

        // Block when hurtResistantTime is low enough (player can soon take damage)
        long hurtTimeMs = (long)(mc.thePlayer.hurtResistantTime * 50L);
        boolean shouldBlock = hurtTimeMs <= maxHurtTime.getValue();

        if (shouldBlock && !blockingState && !pendingBlock && !attackedThisTick) {
            pendingBlock = true;
        } else if (blockingState) {
            long holdElapsed = System.currentTimeMillis() - blockStartMs;
            if (holdElapsed >= maxHoldDuration.getValue() && !attackedThisTick) {
                stopBlock();
                isBlocking = false;
                if (lagChance.getValue() > 0 && Math.random() * 100 < lagChance.getValue()) {
                    Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                    lagging = true; lagStartMs = System.currentTimeMillis();
                }
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        // Keep-alive not needed — we don't use setItemInUse
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!isEnabled()) return;
        attackedThisTick = true;
        // Suppress pending block this tick — sending C08 same tick as C02 = PacketOrderI
        pendingBlock = false;
        if (blockingState) {
            // Unblock so full damage is dealt (blocking halves damage in 1.8)
            stopBlock();
        }
        if (lagging && preventDelay.getValue()) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            lagging = false;
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
                if (lagging && preventDelay.getValue()) {
                    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    lagging = false; pendingBlock = true;
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

    // ── Helpers ───────────────────────────────────────────────────────────────
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
            EntityLivingBase entity = (EntityLivingBase) obj;
            if (entity == mc.thePlayer || entity.isDead || entity.deathTime > 0) continue;
            double dist = RotationUtil.distanceToEntity(entity);
            if (dist <= nearestDist) { nearestDist = dist; nearest = entity; }
        }
        return nearest;
    }


    // ── Send block packet just before position packet (matches Grim's expected order) ─
    @EventTarget(Priority.LOW)
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!isEnabled() || !pendingBlock || attackedThisTick) return;
        pendingBlock = false;
        startBlock();
    }

    private void startBlock() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemSword)) return;
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        // Only send C08 to server — do NOT call setItemInUse on client
        // setItemInUse triggers Minecraft's 0.2x blocking speed penalty which
        // Grim doesn't predict (it uses full speed), causing Simulation flags
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(held));
        blockingState = true; isBlocking = true; blockStartMs = System.currentTimeMillis();
    }

    private void stopBlock() {
        PacketUtil.sendPacket(new C07PacketPlayerDigging(
            C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        // Don't call stopUsingItem — we never called setItemInUse
        blockingState = false;
    }

    @Override
    public String[] getSuffix() {
        return blockingState ? new String[]{"Blocking"} : new String[0];
    }
}
