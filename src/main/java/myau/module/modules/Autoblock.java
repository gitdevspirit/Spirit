package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.CancelUseEvent;
import myau.events.PacketEvent;
import myau.events.RightClickMouseEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import org.lwjgl.input.Mouse;

/**
 * Autoblock — rewritten around the reference's design: instead of hand-rolling
 * the block/unblock packets, it presses/releases the real "use item" keybind
 * (vanilla sends the correct packets on its own from there), predicts
 * incoming hits via hurtTime the same tick they land, and folds in blinkManager
 * for a brief outbound-packet lag window so the block registers before the
 * hit does.
 */
public class Autoblock extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting  range            = register(new SliderSetting("Range", 4.0, 2.0, 6.0, 0.1));
    public final SliderSetting  maxHurtTime      = register(new SliderSetting("Max Hurt Time", 200, 50, 500, 10));
    public final SliderSetting  maxHoldTime      = register(new SliderSetting("Max Hold Time", 150, 50, 500, 10));
    public final SliderSetting  lagChance        = register(new SliderSetting("Lag Chance", 100, 0, 100, 5));
    public final SliderSetting  lagMaxDuration   = register(new SliderSetting("Lag Max Duration", 200, 50, 500, 10));
    public final BooleanSetting preventDelayAttacks = register(new BooleanSetting("Prevent Delaying Attacks", true));
    public final BooleanSetting blockAgainImmediately = register(new BooleanSetting("Block Again Immediately", true));
    public final BooleanSetting forceBlockAnimation = register(new BooleanSetting("Force Block Animation", true));
    public final BooleanSetting requireLmb       = register(new BooleanSetting("Require Left Mouse", false));
    public final BooleanSetting requireRmb       = register(new BooleanSetting("Require Right Mouse", false));
    public final BooleanSetting onlyWhenDamaged  = register(new BooleanSetting("Only When Damaged", false));
    public final BooleanSetting ignoreTeammates  = register(new BooleanSetting("Ignore Teammates", true));

    private boolean blocking;
    private boolean lagging;
    private long blockStartTick;
    private long lagStartTick;
    private long tickCounter;
    private EntityPlayer currentTarget;

    public Autoblock() { super("Autoblock", false); }

    @Override
    public void onEnabled() {
        tickCounter = 0L;
        blockStartTick = 0L;
        lagStartTick = 0L;
        currentTarget = null;
    }

    @Override
    public void onDisabled() {
        stopBlocking();
        releaseLag();
        currentTarget = null;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        tickCounter++;

        if (!conditionsMet()) {
            stopBlocking();
            releaseLag();
            return;
        }

        currentTarget = findTarget();
        boolean shouldBlock = currentTarget != null && (!onlyWhenDamaged.getValue() || shouldPredictiveBlock());

        if (!shouldBlock) {
            stopBlocking();
            releaseLag();
            return;
        }

        if (!blocking) {
            startBlocking();
        } else if (ticksToMs(tickCounter - blockStartTick) >= maxHoldTime.getValue()) {
            // Periodically let go and re-press — mirrors a human re-gripping their
            // shield rather than holding one continuous, suspiciously-long block.
            stopBlocking();
            if (blockAgainImmediately.getValue()) startBlocking();
        }

        maybeStartLag();
        if (lagging && ticksToMs(tickCounter - lagStartTick) >= lagMaxDuration.getValue()) {
            releaseLag();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.SEND) return;
        if (!lagging || !preventDelayAttacks.getValue()) return;
        if (!(event.getPacket() instanceof C02PacketUseEntity)) return;

        C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
        if (packet.getAction() == Action.ATTACK) {
            // Don't let a queued attack sit behind the lag window — release
            // immediately so the swing actually lands on time.
            releaseLag();
        }
    }

    @EventTarget
    public void onRightClickMouse(RightClickMouseEvent event) {
        if (shouldSuppressVanillaUse()) event.setCancelled(true);
    }

    @EventTarget
    public void onCancelUse(CancelUseEvent event) {
        if (shouldSuppressVanillaUse()) event.setCancelled(true);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.POST) return;
        if (!isEnabled() || mc.thePlayer == null) return;

        if ((blocking || lagging) && forceBlockAnimation.getValue()
                && ItemUtil.isHoldingSword() && !mc.thePlayer.isUsingItem()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held != null) {
                myau.mixin.IAccessorEntityPlayer accessor = (myau.mixin.IAccessorEntityPlayer) mc.thePlayer;
                accessor.setItemInUse(held);
                accessor.setItemInUseCount(held.getMaxItemUseDuration());
            }
        }
    }

    // ── Core actions ──────────────────────────────────────────────────────────

    private void startBlocking() {
        if (!ItemUtil.isHoldingSword()) return;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        blocking = true;
        blockStartTick = tickCounter;
    }

    private void stopBlocking() {
        if (!blocking) return;
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(key, false);
        if (mc.thePlayer != null && mc.thePlayer.isUsingItem()) {
            mc.thePlayer.stopUsingItem();
        }
        blocking = false;
    }

    private void maybeStartLag() {
        if (lagging) return;
        if (lagChance.getValue() <= 0) return;
        if (Math.random() * 100.0 > lagChance.getValue()) return;

        Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        lagging = true;
        lagStartTick = tickCounter;
    }

    private void releaseLag() {
        if (!lagging) return;
        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        lagging = false;
    }

    // ── Conditions / targeting ───────────────────────────────────────────────

    private boolean conditionsMet() {
        if (mc.currentScreen != null) return false;
        if (!ItemUtil.isHoldingSword()) return false;
        if (requireLmb.getValue() && !Mouse.isButtonDown(0)) return false;
        if (requireRmb.getValue() && !Mouse.isButtonDown(1)) return false;
        return true;
    }

    private boolean shouldPredictiveBlock() {
        // hurtTime counts down from ~10 the tick you're hit; catch it as close
        // to that first tick as the Max Hurt Time slider allows.
        int triggerTick = Math.max(1, Math.min(10, (int) (maxHurtTime.getValue() / 50.0)));
        return mc.thePlayer.hurtTime == triggerTick;
    }

    private EntityPlayer findTarget() {
        double rangeSq = range.getValue() * range.getValue();
        EntityPlayer nearest = null;
        double nearestDistSq = rangeSq;

        for (Object obj : mc.theWorld.playerEntities) {
            EntityPlayer p = (EntityPlayer) obj;
            if (p == mc.thePlayer || p.isDead || p.deathTime > 0) continue;
            if (TeamUtil.isFriend(p)) continue;
            if (ignoreTeammates.getValue() && TeamUtil.isSameTeam(p)) continue;

            double distSq = mc.thePlayer.getDistanceSqToEntity(p);
            if (distSq <= nearestDistSq) {
                nearestDistSq = distSq;
                nearest = p;
            }
        }
        return nearest;
    }

    private boolean shouldSuppressVanillaUse() {
        return isEnabled() && (blocking || lagging) && ItemUtil.isHoldingSword();
    }

    private double ticksToMs(long ticks) {
    return ticks * 50.0;
}

public boolean isBlocking() {
    return blocking;
}

@Override
public String[] getSuffix() {
    if (lagging) return new String[]{ "Lagging" };
    if (blocking) return new String[]{ "Blocking" };
    return new String[0];
}
