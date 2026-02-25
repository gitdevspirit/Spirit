package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.potion.Potion;

public class Wtap extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();
    private boolean active       = false;
    private boolean stopForward  = false;
    private long    delayTicks   = 0L;
    private long    durationTicks = 0L;

    public final SliderSetting delay    = new SliderSetting("Delay",    5.5, 0.0, 10.0, 0.1);
    public final SliderSetting duration = new SliderSetting("Duration", 1.5, 1.0,  5.0, 0.1);

    public Wtap() {
        super("WTap", false);
        register(delay);
        register(duration);
    }

    private boolean canTrigger() {
        return !(mc.thePlayer.movementInput.moveForward < 0.8F)
                && !mc.thePlayer.isCollidedHorizontally
                && (!((float) mc.thePlayer.getFoodStats().getFoodLevel() <= 6.0F) || mc.thePlayer.capabilities.allowFlying)
                && (mc.thePlayer.isSprinting()
                    || !mc.thePlayer.isUsingItem()
                       && !mc.thePlayer.isPotionActive(Potion.blindness)
                       && mc.gameSettings.keyBindSprint.isKeyDown());
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!active) return;
        if (!stopForward && !canTrigger()) {
            active = false;
            delayTicks    = 0;
            durationTicks = 0;
        } else if (delayTicks > 0L) {
            delayTicks -= 50L;
        } else {
            if (durationTicks > 0L) {
                durationTicks -= 50L;
                stopForward = true;
                mc.thePlayer.movementInput.moveForward = 0.0F;
            }
            if (durationTicks <= 0L) {
                active = false;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.isCancelled() || event.getType() != EventType.SEND) return;
        if (!(event.getPacket() instanceof C02PacketUseEntity)) return;
        C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
        if (packet.getAction() == Action.ATTACK && !active
                && timer.hasTimeElapsed(500L) && mc.thePlayer.isSprinting()) {
            timer.reset();
            active       = true;
            stopForward  = false;
            delayTicks    += (long) (50.0 * delay.getValue());
            durationTicks += (long) (50.0 * duration.getValue());
        }
    }
}