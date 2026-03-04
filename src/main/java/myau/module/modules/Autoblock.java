package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import java.util.Random;

public class AutoBlock extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // ── Settings ──────────────────────────────────────────────────────────────
    public final DropdownSetting mode = register(new DropdownSetting("Mode", 2,
            "None", "Vanilla", "Spoof", "Hypixel", "Blink", "Interact", "Swap", "Legit", "Fake"));

    public final BooleanSetting requirePress = register(new BooleanSetting("Require Press", false));
    public final SliderSetting  minCPS       = register(new SliderSetting("Min APS", 8.0, 1.0, 20.0, 0.5));
    public final SliderSetting  maxCPS       = register(new SliderSetting("Max APS", 10.0, 1.0, 20.0, 0.5));
    public final SliderSetting  blockRange   = register(new SliderSetting("Block Range", 6.0, 3.0, 8.0, 0.1));

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean blockingState = false;
    private boolean isBlocking    = false;
    private boolean fakeBlockState = false;
    private boolean blinkReset    = false;
    private long    attackDelayMS = 0L;
    private int     blockTick     = 0;

    public AutoBlock() {
        super("AutoBlock", false);
    }

    @Override
    public void onDisabled() {
        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        blockingState  = false;
        isBlocking     = false;
        fakeBlockState = false;
        blockTick      = 0;
        attackDelayMS  = 0L;
    }

    // ── Public API (used by other modules) ────────────────────────────────────
    public boolean isBlocking() {
        return fakeBlockState && ItemUtil.isHoldingSword();
    }

    public boolean isPlayerBlocking() {
        return (mc.thePlayer.isUsingItem() || blockingState) && ItemUtil.isHoldingSword();
    }

    public boolean shouldAutoBlock() {
        if (isPlayerBlocking() && isBlocking) {
            int m = mode.getIndex();
            return !mc.thePlayer.isInWater() && !mc.thePlayer.isInLava()
                    && (m == 3 || m == 4 || m == 5 || m == 6 || m == 7);
        }
        return false;
    }

    // ── Main logic ────────────────────────────────────────────────────────────
    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled()) return;

        // Blink reset on POST
        if (event.getType() == EventType.POST && blinkReset) {
            blinkReset = false;
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        }

        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (attackDelayMS > 0L) attackDelayMS -= 50L;

        if (!canAutoBlock()) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            isBlocking     = false;
            fakeBlockState = false;
            blockTick      = 0;
            return;
        }

        if (!hasValidTarget()) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            isBlocking     = false;
            fakeBlockState = false;
            return;
        }

        boolean swap    = false;
        boolean blocked = false;
        int     m       = mode.getIndex();

        switch (m) {
            case 0: // None
                if (PlayerUtil.isUsingItem()) {
                    isBlocking = true;
                    if (!isPlayerBlocking() && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing)
                        swap = true;
                } else {
                    isBlocking = false;
                    if (isPlayerBlocking() && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing)
                        stopBlock();
                }
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                fakeBlockState = false;
                break;

            case 1: // Vanilla
                if (!isPlayerBlocking() && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing)
                    swap = true;
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                isBlocking     = true;
                fakeBlockState = false;
                break;

            case 2: // Spoof
                int spoofItem = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing
                        && mc.thePlayer.inventory.currentItem == spoofItem
                        && !(isPlayerBlocking() && blockTick != 0)
                        && !(attackDelayMS > 0L && attackDelayMS <= 50L)) {
                    int slot = findEmptySlot(spoofItem);
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(spoofItem));
                    swap = true;
                    blockTick = 1;
                } else {
                    blockTick = 0;
                }
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                isBlocking     = true;
                fakeBlockState = false;
                break;

            case 3: // Hypixel
                if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                    switch (blockTick) {
                        case 0:
                            if (!isPlayerBlocking()) swap = true;
                            blocked   = true;
                            blockTick = 1;
                            break;
                        case 1:
                            if (isPlayerBlocking()) {
                                if (Myau.moduleManager.modules.get(NoSlow.class).isEnabled()) {
                                    int rand = new Random().nextInt(9);
                                    while (rand == mc.thePlayer.inventory.currentItem) rand = new Random().nextInt(9);
                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(rand));
                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                }
                                stopBlock();
                            }
                            if (attackDelayMS <= 50L) blockTick = 0;
                            break;
                        default:
                            blockTick = 0;
                    }
                }
                isBlocking     = true;
                fakeBlockState = true;
                break;

            case 4: // Blink
                if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                    switch (blockTick) {
                        case 0:
                            if (!isPlayerBlocking()) swap = true;
                            blinkReset = true;
                            blockTick  = 1;
                            break;
                        case 1:
                            if (isPlayerBlocking()) stopBlock();
                            if (attackDelayMS <= 50L) blockTick = 0;
                            break;
                        default:
                            blockTick = 0;
                    }
                }
                isBlocking     = true;
                fakeBlockState = true;
                break;

            case 5: // Interact
                int interactItem = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                if (mc.thePlayer.inventory.currentItem == interactItem
                        && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                    switch (blockTick) {
                        case 0:
                            if (!isPlayerBlocking()) swap = true;
                            blinkReset = true;
                            blockTick  = 1;
                            break;
                        case 1:
                            if (isPlayerBlocking()) {
                                int slot = findEmptySlot(interactItem);
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
                                ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(slot);
                            }
                            if (attackDelayMS <= 50L) blockTick = 0;
                            break;
                        default:
                            blockTick = 0;
                    }
                }
                isBlocking     = true;
                fakeBlockState = true;
                break;

            case 6: // Swap
                int swapItem = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                if (mc.thePlayer.inventory.currentItem == swapItem
                        && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                    switch (blockTick) {
                        case 0:
                            int slot0 = findSwordSlot(swapItem);
                            if (slot0 != -1) {
                                if (!isPlayerBlocking()) swap = true;
                                blockTick = 1;
                            }
                            break;
                        case 1:
                            int swordSlot = findSwordSlot(swapItem);
                            if (swordSlot == -1) {
                                blockTick = 0;
                            } else if (!isPlayerBlocking()) {
                                swap = true;
                            } else if (attackDelayMS <= 50L) {
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(swordSlot));
                                ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(swordSlot);
                                startBlock(mc.thePlayer.inventory.getStackInSlot(swordSlot));
                                blockTick = 0;
                            }
                            break;
                        default:
                            blockTick = 0;
                    }
                    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    isBlocking     = true;
                    fakeBlockState = true;
                    break;
                }
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                isBlocking     = false;
                fakeBlockState = false;
                break;

            case 7: // Legit
                if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                    switch (blockTick) {
                        case 0:
                            if (!isPlayerBlocking()) swap = true;
                            blockTick = 1;
                            break;
                        case 1:
                            if (isPlayerBlocking()) stopBlock();
                            if (attackDelayMS <= 50L) blockTick = 0;
                            break;
                        default:
                            blockTick = 0;
                    }
                }
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                isBlocking     = true;
                fakeBlockState = false;
                break;

            case 8: // Fake
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                isBlocking     = false;
                fakeBlockState = true;
                if (PlayerUtil.isUsingItem() && !isPlayerBlocking()
                        && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing)
                    swap = true;
                break;
        }

        if (swap) sendUseItem();

        if (blocked) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.POST) return;
        if (mc.thePlayer == null) return;
        // Keep blocking animation alive
        if (isPlayerBlocking() && !mc.thePlayer.isBlocking()) {
            mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;
        if (event.getPacket() instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging pkt = (C07PacketPlayerDigging) event.getPacket();
            if (pkt.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM)
                blockingState = false;
        }
        if (event.getPacket() instanceof C09PacketHeldItemChange) {
            blockingState = false;
            if (isBlocking) mc.thePlayer.stopUsingItem();
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (!isEnabled()) return;
        if (shouldAutoBlock()) mc.thePlayer.movementInput.jump = false;
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (!isEnabled()) return;
        if (isBlocking) event.setCancelled(true);
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (!isEnabled()) return;
        if (isBlocking) event.setCancelled(true);
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (!isEnabled()) return;
        if (isBlocking) event.setCancelled(true);
    }

    @EventTarget
    public void onCancelUse(CancelUseEvent event) {
        if (!isEnabled()) return;
        if (isBlocking) event.setCancelled(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private boolean canAutoBlock() {
        if (!ItemUtil.isHoldingSword()) return false;
        if (requirePress.getValue() && !PlayerUtil.isUsingItem()) return false;
        return true;
    }

    private boolean hasValidTarget() {
        if (mc.theWorld == null) return false;
        return mc.theWorld.loadedEntityList.stream().anyMatch(e -> {
            if (!(e instanceof EntityLivingBase)) return false;
            EntityLivingBase living = (EntityLivingBase) e;
            if (living == mc.thePlayer || living.deathTime > 0) return false;
            if (living instanceof EntityPlayer && TeamUtil.isFriend((EntityPlayer) living)) return false;
            return RotationUtil.distanceToEntity(living) <= blockRange.getValue();
        });
    }

    private void sendUseItem() {
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        startBlock(mc.thePlayer.getHeldItem());
    }

    private void startBlock(ItemStack stack) {
        if (stack == null) return;
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(stack));
        mc.thePlayer.setItemInUse(stack, stack.getMaxItemUseDuration());
        blockingState = true;
    }

    private void stopBlock() {
        PacketUtil.sendPacket(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        mc.thePlayer.stopUsingItem();
        blockingState = false;
    }

    private int findEmptySlot(int currentSlot) {
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot && mc.thePlayer.inventory.getStackInSlot(i) == null) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot) {
                ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
                if (s != null && !s.hasDisplayName()) return i;
            }
        }
        return Math.floorMod(currentSlot - 1, 9);
    }

    private int findSwordSlot(int currentSlot) {
        for (int i = 0; i < 9; i++) {
            if (i == currentSlot) continue;
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (s != null && s.getItem() instanceof ItemSword) return i;
        }
        return -1;
    }

    public void notifyAttack(long delayMs) {
        this.attackDelayMS += delayMs;
    }
}
