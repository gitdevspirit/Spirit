package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.modules.SilentAura;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

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
    private boolean pendingStop     = false;
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
        if (blockingState) { mc.thePlayer.stopUsingItem(); blockingState = false; }
        if (lagging) { Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK); lagging = false; }
        blockingState = false; fakeBlockState = false; isBlocking = false; blockStartMs = 0L; pendingBlock = false; pendingStop = false;
    }

    // ── Events ────────────────────────────────────────────────────────────────
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (event.getType() == EventType.PRE) {
            if (!ItemUtil.isHoldingSword()) { cleanup(); return; }

            // Track damage for requireDamaged condition
            int currentHurtTime = mc.thePlayer.hurtResistantTime;
            if (currentHurtTime > lastHurtTime) lastDamagedMs = System.currentTimeMillis();
            lastHurtTime = currentHurtTime;

            if (!checkConditions()) {
                fakeBlockState = false; isBlocking = false;
                pendingBlock = false; pendingStop = false; return;
            }

            EntityLivingBase nearestTarget = getNearestTarget();
            boolean targetInRange = nearestTarget != null;
            fakeBlockState = targetInRange;

            if (!targetInRange) {
                if (blockingState) pendingStop = true;
                isBlocking = false; return;
            }

            // Lag management
            if (lagging) {
                long lagElapsed = System.currentTimeMillis() - lagStartMs;
                if (lagElapsed >= lagMaxDuration.getValue()) {
                    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    lagging = false;
                    if (blockAgain.getValue()) pendingBlock = true;
                    else { pendingStop = true; isBlocking = false; }
                }
                return;
            }

            // Decide whether to block or release — actual packets sent in POST
            long hurtTimeMs = (long)(mc.thePlayer.hurtResistantTime * 50L);
            boolean shouldBlock = hurtTimeMs <= maxHurtTime.getValue();

            if (shouldBlock && !blockingState && !pendingBlock) {
                pendingBlock = true;
            } else if (blockingState) {
                long holdElapsed = System.currentTimeMillis() - blockStartMs;
                if (holdElapsed >= maxHoldDuration.getValue()) {
                    pendingStop = true;
                    isBlocking = false;
                    if (lagChance.getValue() > 0 && Math.random() * 100 < lagChance.getValue()) {
                        Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                        lagging = true; lagStartMs = System.currentTimeMillis();
                        pendingStop = false; // lag handles release
                    }
                }
            }

        } else if (event.getType() == EventType.POST) {
            // POST = after position packet sent — same timing as vanilla rightClickMouse
            // Skip if SilentAura attacked this tick to avoid MultiActionsE
            if (SilentAura.attackingThisTick) {
                pendingBlock = false;
                pendingStop = false;
                return;
            }
            if (pendingStop && blockingState) {
                stopBlock();
                pendingStop = false;
            }
            if (pendingBlock && !blockingState) {
                startBlock();
                pendingBlock = false;
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
        // Stop using item properly — this sends C07 via vanilla pipeline
        // Attack fires from playerController which handles packet ordering correctly
        if (blockingState) stopBlock();
        pendingBlock = false;
        pendingStop = false;
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


    // Block/release now sent in UpdateEvent POST (shared tick with SilentAura's attack check)

    private void startBlock() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof net.minecraft.item.ItemSword)) return;
        // Use vanilla sendUseItem — sends C08 through the normal pipeline
        // NoSlow's isUsingItem() mixin intercept handles speed bypass automatically
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
        blockingState = true; isBlocking = true; blockStartMs = System.currentTimeMillis();
    }

    private void stopBlock() {
        // Use vanilla stopUsingItem — sends C07 through normal pipeline
        mc.thePlayer.stopUsingItem();
        blockingState = false;
    }

    @Override
    public String[] getSuffix() {
        return blockingState ? new String[]{"Blocking"} : new String[0];
    }
}
