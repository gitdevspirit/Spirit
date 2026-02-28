package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class Autoblock extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // SLINKY removed — was index 11
    public final DropdownSetting mode = register(new DropdownSetting("Mode", 9,
            "NONE", "VANILLA", "SPOOF", "HYPIXEL", "BLINK",
            "INTERACT", "SWAP", "LEGIT", "FAKE", "LAGRANGE",
            "GRIM", "LEGITFULL"
    ));

    public final SliderSetting blockRange   = register(new SliderSetting("Block Range",   6.0,   3.0,   8.0,   0.1));
    public final SliderSetting minCPS       = register(new SliderSetting("Min APS",       6.0,   1.0,  20.0,   1.0));
    public final SliderSetting maxCPS       = register(new SliderSetting("Max APS",       9.0,   1.0,  20.0,   1.0));
    public final SliderSetting releaseDelay = register(new SliderSetting("Release Delay", 2.0,   1.0,   5.0,   0.5));

    public final BooleanSetting requirePress  = register(new BooleanSetting("Require Press",  false));
    public final BooleanSetting requireAttack = register(new BooleanSetting("Require Attack", true));
    public final BooleanSetting autoRelease   = register(new BooleanSetting("Auto Release",   true));

    // LEGITFULL settings — controls how long to hold and how long to wait before reblocking
    public final SliderSetting  legitfullHoldMin    = register(new SliderSetting("LF Hold Min",    3.0,  1.0, 10.0, 0.5));
    public final SliderSetting  legitfullHoldMax    = register(new SliderSetting("LF Hold Max",    6.0,  1.0, 10.0, 0.5));
    public final SliderSetting  legitfullDelayMin   = register(new SliderSetting("LF Delay Min",   2.0,  0.0,  8.0, 0.5));
    public final SliderSetting  legitfullDelayMax   = register(new SliderSetting("LF Delay Max",   4.0,  0.0,  8.0, 0.5));
    public final BooleanSetting legitfullBlockDelay = register(new BooleanSetting("Block Delay",   false, () -> mode.getIndex() == 11));

    // Internal state
    private boolean blockingState        = false;
    private boolean fakeBlockState       = false;
    private boolean isBlocking           = false;
    private boolean blinkReset           = false;
    private int     blockTick            = 0;
    private long    blockDelayMS         = 0L;
    private int     releaseTick          = 0;
    private int     releaseCooldownTicks = 0;

    // LEGITFULL rhythm state
    private int legitHoldTicks  = 0; // how many ticks to hold the block
    private int legitDelayTicks = 0; // how many ticks to wait before reblocking

    public Autoblock() {
        super("Autoblock", false);
    }

    private int   getMode()         { return mode.getIndex(); }
    private float getBlockRange()   { return (float) blockRange.getValue(); }
    private float getMinCPS()       { return (float) minCPS.getValue(); }
    private float getMaxCPS()       { return (float) maxCPS.getValue(); }
    private float getReleaseDelay() { return (float) releaseDelay.getValue(); }

    private long getBlockDelay() {
        return (long)(1000.0F / RandomUtil.nextLong((long) getMinCPS(), (long) getMaxCPS()));
    }

    /** Random int in [min, max] inclusive */
    private int randRange(double min, double max) {
        int lo = (int) Math.round(Math.min(min, max));
        int hi = (int) Math.round(Math.max(min, max));
        return lo == hi ? lo : lo + (int)(Math.random() * (hi - lo + 1));
    }

    private boolean canAutoblock() {
        if (!ItemUtil.isHoldingSword()) return false;
        if (requirePress.getValue() && !PlayerUtil.isUsingItem()) return false;
        // KillAura removed — requireAttack setting no longer has effect
        return true;
    }

    private boolean hasValidTarget() {
        return mc.theWorld.loadedEntityList.stream().anyMatch(
                entity -> entity instanceof net.minecraft.entity.EntityLivingBase
                        && RotationUtil.distanceToEntity((net.minecraft.entity.EntityLivingBase) entity)
                        <= getBlockRange()
        );
    }

    public void startBlock(ItemStack stack) {
        if (stack == null) return;
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(stack));
        mc.thePlayer.setItemInUse(stack, stack.getMaxItemUseDuration());
        this.blockingState = true;
        this.releaseTick = (int) getReleaseDelay();
    }

    public void stopBlock() {
        stopBlock(false);
    }

    private void stopBlock(boolean skipCooldown) {
        PacketUtil.sendPacket(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                EnumFacing.DOWN
        ));
        mc.thePlayer.stopUsingItem();
        this.blockingState = false;
        this.releaseTick   = 0;
        if (!skipCooldown) {
            this.releaseCooldownTicks = 5;
        }
    }

    private int findEmptySlot(int currentSlot) {
        for (int i = 0; i < 9; i++)
            if (i != currentSlot && mc.thePlayer.inventory.getStackInSlot(i) == null)
                return i;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (i != currentSlot && stack != null && !stack.hasDisplayName())
                return i;
        }
        return Math.floorMod(currentSlot - 1, 9);
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) {
            resetState();
            return;
        }

        if (event.getType() == EventType.POST && this.blinkReset) {
            this.blinkReset = false;
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            Myau.blinkManager.setBlinkState(true,  BlinkModules.AUTO_BLOCK);
        }

        if (event.getType() != EventType.PRE) return;

        if (this.blockDelayMS > 0L) this.blockDelayMS -= 50L;
        if (this.releaseCooldownTicks > 0) this.releaseCooldownTicks--;

        if (this.releaseCooldownTicks > 0) {
            if (this.blockingState) stopBlock();
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking     = false;
            this.fakeBlockState = false;
            this.blockTick      = 0;
            return;
        }

        if (autoRelease.getValue() && this.blockingState && this.releaseTick > 0) {
            this.releaseTick--;
            if (this.releaseTick <= 0) stopBlock();
        }

        boolean canBlock = this.canAutoblock() && this.hasValidTarget();
        if (!canBlock) {
            if (this.blockingState) stopBlock();
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking     = false;
            this.fakeBlockState = false;
            this.blockTick      = 0;
            return;
        }

        boolean swap = false;

        switch (getMode()) {

            case 0: // NONE
                this.isBlocking     = false;
                this.fakeBlockState = false;
                break;

            case 1: // VANILLA
                if (!this.blockingState) swap = true;
                this.isBlocking     = true;
                this.fakeBlockState = false;
                break;

            case 2: // SPOOF
                int item2 = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                int slot2 = this.findEmptySlot(item2);
                PacketUtil.sendPacket(new C09PacketHeldItemChange(slot2));
                PacketUtil.sendPacket(new C09PacketHeldItemChange(item2));
                swap = true;
                this.isBlocking     = true;
                this.fakeBlockState = false;
                break;

            case 3: // HYPIXEL
                switch (this.blockTick) {
                    case 0: swap = true; this.blockTick = 1; break;
                    case 1: if (this.blockDelayMS <= 50L) this.blockTick = 0; break;
                }
                this.isBlocking     = true;
                this.fakeBlockState = true;
                break;

            case 4: // BLINK
                switch (this.blockTick) {
                    case 0:
                        swap = true;
                        this.blinkReset = true;
                        this.blockTick  = 1;
                        break;
                    case 1:
                        if (this.blockDelayMS <= 50L) this.blockTick = 0;
                        break;
                }
                this.isBlocking     = true;
                this.fakeBlockState = true;
                break;

            case 5: // INTERACT
                int current5 = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                int empty5   = this.findEmptySlot(current5);
                PacketUtil.sendPacket(new C09PacketHeldItemChange(empty5));
                ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(empty5);
                swap = true;
                this.isBlocking     = true;
                this.fakeBlockState = true;
                break;

            case 6: // SWAP
                int cur6       = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                int emptySlot6 = this.findEmptySlot(cur6);
                PacketUtil.sendPacket(new C09PacketHeldItemChange(emptySlot6));
                PacketUtil.sendPacket(new C09PacketHeldItemChange(cur6));
                swap = true;
                this.isBlocking     = true;
                this.fakeBlockState = true;
                break;

            case 7: // LEGIT
                swap = true;
                this.isBlocking     = true;
                this.fakeBlockState = false;
                break;

            case 8: // FAKE
                this.isBlocking     = false;
                this.fakeBlockState = true;
                break;

            case 9: // LAGRANGE
                int ping      = PingUtil.getPing();
                int lagWindow = Math.min(100, Math.max(30, ping));
                switch (this.blockTick) {
                    case 0:
                        swap = true;
                        this.blockDelayMS = lagWindow;
                        this.blockTick    = 1;
                        break;
                    case 1:
                        if (this.blockDelayMS <= 0L) this.blockTick = 2;
                        break;
                    case 2:
                        this.blockTick = 0; // KillAura removed — always advance
                        break;
                }
                this.isBlocking     = true;
                this.fakeBlockState = true;
                break;

            case 10: // GRIM
                if (!this.blockingState) swap = true;
                this.isBlocking     = true;
                this.fakeBlockState = false;
                break;

            case 11: { // LEGITFULL — randomised hold→delay rhythm
                // Phase 0: waiting for optional pre-block delay
                // Phase 1: blocking (hold for legitHoldTicks)
                // Phase 2: released, waiting for legitDelayTicks before reblocking
                if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                    switch (this.blockTick) {
                        case 0: // Start — optionally wait one tick before blocking
                            if (legitfullBlockDelay.getValue()) {
                                this.blockTick = 1;
                                break;
                            }
                            // Fall through to case 1
                        case 1: // Block and pick how long to hold
                            if (!this.blockingState) swap = true;
                            this.legitHoldTicks = randRange(
                                    legitfullHoldMin.getValue(),
                                    legitfullHoldMax.getValue());
                            this.blockTick = 2;
                            break;

                        case 2: // Holding — count down
                            this.legitHoldTicks--;
                            if (this.legitHoldTicks <= 0) {
                                // Release and pick how long to wait
                                if (this.blockingState) stopBlock(true);
                                this.legitDelayTicks = randRange(
                                        legitfullDelayMin.getValue(),
                                        legitfullDelayMax.getValue());
                                this.blockTick = 3;
                            }
                            break;

                        case 3: // Delay between releases — count down then restart
                            this.legitDelayTicks--;
                            if (this.legitDelayTicks <= 0) {
                                this.blockTick = 0;
                            }
                            break;

                        default:
                            this.blockTick = 0;
                    }
                }
                this.isBlocking     = true;
                this.fakeBlockState = false;
                break;
            }
        }

        if (swap && this.blockDelayMS <= 0L) {
            this.blockDelayMS += this.getBlockDelay() + RandomUtil.nextInt(20, 50);
            this.startBlock(mc.thePlayer.getHeldItem());
        }
    }

    private void resetState() {
        if (this.blockingState) stopBlock();
        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        this.isBlocking          = false;
        this.fakeBlockState      = false;
        this.blockTick           = 0;
        this.blockDelayMS        = 0L;
        this.releaseTick         = 0;
        this.releaseCooldownTicks = 0;
        this.legitHoldTicks      = 0;
        this.legitDelayTicks     = 0;
    }

    public boolean isBlocking() {
        return this.fakeBlockState && ItemUtil.isHoldingSword();
    }

    public boolean isPlayerBlocking() {
        return (mc.thePlayer.isUsingItem() || this.blockingState) && ItemUtil.isHoldingSword();
    }

    public boolean isInLegitFullHoldPhase() {
        return getMode() == 11 && (this.blockTick == 2);
    }

    @Override
    public void onDisabled() {
        resetState();
    }

    @Override
    public String[] getSuffix() {
        return isBlocking ? new String[]{mode.getValue()} : new String[0];
    }
}
