package myau.module.modules;

import myau.event.EventTarget;
import myau.events.AttackEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.RandomUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class BlockHit extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final DropdownSetting mode              = new DropdownSetting("Mode",            0, "VANILLA", "PACKET", "HEURISTIC", "SMART");
    public final BooleanSetting  raytrace          = new BooleanSetting("Raytrace",         true);
    public final SliderSetting   chance            = new SliderSetting("Chance",          100,   0, 100,   1);
    public final SliderSetting   blockTicks        = new SliderSetting("Block Ticks",       2,   1,  10,   1);
    public final SliderSetting   unblockTicks      = new SliderSetting("Unblock Ticks",     2,   1,  10,   1);
    public final BooleanSetting  autoBlock         = new BooleanSetting("Auto Block",       true);
    public final BooleanSetting  onlyWhileAttacking= new BooleanSetting("Only Attacking",   true);
    public final BooleanSetting  checkCooldown     = new BooleanSetting("Check Cooldown",   true);
    public final SliderSetting   reactionDelay     = new SliderSetting("Reaction Delay",  100.0, 0.0, 500.0, 1.0);
    public final SliderSetting   reactionVariance  = new SliderSetting("Reaction Variance", 50.0, 0.0, 200.0, 1.0);
    public final BooleanSetting  postHitDelay      = new BooleanSetting("Post-Hit Delay",   true);
    public final SliderSetting   minPostDelay      = new SliderSetting("Min Post Delay",    50,   0, 500,   1);
    public final SliderSetting   maxPostDelay      = new SliderSetting("Max Post Delay",   150,   0, 500,   1);
    public final BooleanSetting  smartPattern      = new BooleanSetting("Smart Pattern",    true);
    public final BooleanSetting  releaseOnMiss     = new BooleanSetting("Release On Miss",  true);

    private boolean          shouldBlock       = false;
    private int              ticksUntilAction  = 0;
    private boolean          isBlocking        = false;
    private int              hitCounter        = 0;
    private long             lastAttackTime    = 0;
    private long             lastBlockTime     = 0;
    private long             scheduledBlockTime= 0;
    private final TimerUtil  cooldownTimer     = new TimerUtil();
    private EntityLivingBase lastTarget        = null;
    private int              blockPattern      = 0;

    private final double[] reactionTimes = new double[10];
    private int reactionIndex = 0;

    public BlockHit() {
        super("BlockHit", false);
        register(mode); register(raytrace); register(chance);
        register(blockTicks); register(unblockTicks);
        register(autoBlock); register(onlyWhileAttacking); register(checkCooldown);
        register(reactionDelay); register(reactionVariance);
        register(postHitDelay); register(minPostDelay); register(maxPostDelay);
        register(smartPattern); register(releaseOnMiss);
        for (int i = 0; i < reactionTimes.length; i++)
            reactionTimes[i] = 150.0 + RandomUtil.nextDouble(-30.0, 30.0);
    }

    @Override
    public void onEnabled() {
        shouldBlock = false; ticksUntilAction = 0; isBlocking = false;
        hitCounter = 0; lastAttackTime = 0; lastBlockTime = 0;
        scheduledBlockTime = 0; lastTarget = null; blockPattern = 0;
        cooldownTimer.reset();
    }

    @Override
    public void onDisabled() { if (isBlocking && mc.thePlayer != null) unblock(); }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!isEnabled() || mc.thePlayer == null || !isHoldingSword()) return;
        if (raytrace.getValue() && (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null)) {
            if (releaseOnMiss.getValue() && isBlocking) unblock();
            return;
        }
        if (RandomUtil.nextInt(0, 100) > (int) chance.getValue()) return;
        if (checkCooldown.getValue() && !canAttack()) return;

        lastAttackTime = System.currentTimeMillis();
        hitCounter++;
        Entity target = event.getTarget();
        if (target instanceof EntityLivingBase) lastTarget = (EntityLivingBase) target;
        if (autoBlock.getValue()) scheduleBlock();
    }

    private void scheduleBlock() {
        long cur = System.currentTimeMillis();
        switch (mode.getIndex()) {
            case 0: shouldBlock = true; ticksUntilAction = 1; break;
            case 1:
                scheduledBlockTime = cur + (postHitDelay.getValue()
                        ? RandomUtil.nextInt((int) minPostDelay.getValue(), (int) maxPostDelay.getValue()) : 0);
                shouldBlock = true; break;
            case 2: scheduledBlockTime = cur + calculateHeuristicDelay(); shouldBlock = true; break;
            case 3: scheduledBlockTime = cur + calculateSmartDelay();     shouldBlock = true; break;
        }
    }

    private long calculateHeuristicDelay() {
        double base = reactionDelay.getValue(), variance = reactionVariance.getValue();
        double newReaction = base + RandomUtil.nextDouble(-variance, variance);
        reactionTimes[reactionIndex] = newReaction;
        reactionIndex = (reactionIndex + 1) % reactionTimes.length;
        double avg = 0;
        for (double t : reactionTimes) avg += t;
        avg /= reactionTimes.length;
        return (long)(newReaction * 0.8 + avg * 0.2);
    }

    private long calculateSmartDelay() {
        blockPattern++;
        long base = calculateHeuristicDelay();
        switch (blockPattern % 5) {
            case 0: return base;
            case 1: return base + 20;
            case 2: return base - 10;
            case 3: return base + 30;
            case 4: return base + 5;
        }
        return base;
    }

    private boolean canAttack() {
        return System.currentTimeMillis() - lastAttackTime >= 500;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != myau.event.types.EventType.SEND) return;
        if (event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity p = (C02PacketUseEntity) event.getPacket();
            if (p.getAction() == C02PacketUseEntity.Action.ATTACK) cooldownTimer.reset();
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;
        if (!isHoldingSword()) { if (isBlocking) unblock(); return; }
        if (onlyWhileAttacking.getValue() && hitCounter == 0) { if (isBlocking) unblock(); return; }

        long cur = System.currentTimeMillis();
        if (shouldBlock && scheduledBlockTime > 0 && cur >= scheduledBlockTime) scheduledBlockTime = 0;
        if (ticksUntilAction > 0) { ticksUntilAction--; return; }

        if (shouldBlock && !isBlocking && (scheduledBlockTime == 0 || cur >= scheduledBlockTime)) {
            block();
            ticksUntilAction = (int) blockTicks.getValue();
            shouldBlock = false;
        } else if (isBlocking) {
            if (cur - lastBlockTime >= (int) unblockTicks.getValue() * 50L) {
                unblock(); ticksUntilAction = 0;
            }
        }
        if (hitCounter > 0 && cur - lastAttackTime > 1000) hitCounter--;
        if (lastTarget != null && (lastTarget.isDead || lastTarget.getHealth() <= 0)) {
            lastTarget = null;
            if (isBlocking && releaseOnMiss.getValue()) unblock();
        }
    }

    private void block() {
        if (mc.thePlayer == null || !isHoldingSword()) return;
        int m = mode.getIndex();
        if (m == 1 || m == 2 || m == 3) blockPacket(); else blockVanilla();
        isBlocking = true; lastBlockTime = System.currentTimeMillis();
    }

    private void blockVanilla() {
        mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getCurrentItem()));
    }

    private void blockPacket() {
        mc.thePlayer.sendQueue.addToSendQueue(
                new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getCurrentItem()));
    }

    private void unblock() {
        if (mc.thePlayer == null) return;
        int m = mode.getIndex();
        if (m == 1 || m == 2 || m == 3)
            mc.thePlayer.sendQueue.addToSendQueue(
                    new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        else mc.playerController.onStoppedUsingItem(mc.thePlayer);
        isBlocking = false;
    }

    private boolean isHoldingSword() {
        return mc.thePlayer != null && mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{ mode.getOptions()[mode.getIndex()] };
    }
}