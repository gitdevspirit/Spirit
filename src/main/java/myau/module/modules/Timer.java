package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.mixin.IAccessorMinecraft;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;

public class Timer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting   speed         = register(new SliderSetting("Speed",          1.2, 0.01, 10.0, 0.01));
    public final DropdownSetting mode          = register(new DropdownSetting("Mode", 0, "CONSTANT", "VARIABLE", "FREEZE"));
    public final SliderSetting   maxSpeed      = register(new SliderSetting("Max (Variable)", 2.0, 1.0, 5.0, 0.1));
    public final DropdownSetting side          = register(new DropdownSetting("Side", 0, "CLIENT", "SERVER", "BOTH"));
    public final BooleanSetting  pauseOnScroll = register(new BooleanSetting("Pause on Scroll", true));
    public final BooleanSetting  pauseOnRight  = register(new BooleanSetting("Pause on RClick", true));

    // Pause state
    private boolean paused   = false;
    private long    pauseEnd = -1;
    private static final long PAUSE_MS = 200;

    // Server-side: accumulate fractional extra packets
    private double packetAccum = 0.0;

    public Timer() { super("Timer", false); }

    @Override
    public void onDisabled() {
        // Instantly reset client timer
        net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
        if (timer != null) timer.timerSpeed = 1.0F;
        paused      = false;
        pauseEnd    = -1;
        packetAccum = 0.0;
    }

    private double getSpeed() {
        if (paused) return 1.0;
        switch (mode.getIndex()) {
            case 1: return speed.getValue() + (maxSpeed.getValue() - speed.getValue()) * Math.random();
            case 2: return 0.01;
            default: return speed.getValue();
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;

        // Update pause state
        if (paused && System.currentTimeMillis() >= pauseEnd) {
            paused   = false;
            pauseEnd = -1;
        }

        net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
        if (timer == null) return;

        int s = side.getIndex();

        // CLIENT or BOTH: modify game tick speed
        if (s == 0 || s == 2) {
            timer.timerSpeed = paused ? 1.0F : (float) getSpeed();
        } else {
            timer.timerSpeed = 1.0F;
        }
    }

    // SERVER or BOTH: intercept outgoing movement packets and duplicate them
    @EventTarget(Priority.HIGHEST)
    public void onPacketSend(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.SEND) return;
        int s = side.getIndex();
        if (s != 1 && s != 2) return; // only SERVER or BOTH
        if (paused) return;
        if (!(event.getPacket() instanceof C03PacketPlayer)) return;

        C03PacketPlayer original = (C03PacketPlayer) event.getPacket();
        if (!original.isMoving()) return;

        double spd = getSpeed();
        if (spd <= 1.0) return;

        // For each tick, send (spd - 1) extra packets so server sees more movement ticks
        packetAccum += spd - 1.0;
        while (packetAccum >= 1.0) {
            packetAccum -= 1.0;
            PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C06PacketPlayerPosLook(
                    original.getPositionX(),
                    original.getPositionY(),
                    original.getPositionZ(),
                    original.getYaw(),
                    original.getPitch(),
                    original.isOnGround()
            ));
        }
    }

    @EventTarget
    public void onPacketReceive(PacketEvent event) {
        if (!isEnabled() || mode.getIndex() != 2) return;
        if (event.getType() != EventType.RECEIVE) return;
        if (event.getPacket() instanceof S02PacketChat) {
            IChatComponent msg = ((S02PacketChat) event.getPacket()).getChatComponent();
            if (msg != null && mc.ingameGUI != null)
                mc.ingameGUI.getChatGUI().printChatMessage(msg);
        }
    }

    @EventTarget
    public void onSwapItem(SwapItemEvent event) {
        if (!isEnabled() || !pauseOnScroll.getValue()) return;
        paused   = true;
        pauseEnd = System.currentTimeMillis() + PAUSE_MS;
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (!isEnabled() || !pauseOnRight.getValue()) return;
        paused   = true;
        pauseEnd = System.currentTimeMillis() + PAUSE_MS;
    }

    @Override
    public String[] getSuffix() {
        String sideStr = new String[]{"C", "S", "C+S"}[side.getIndex()];
        if (paused) return new String[]{ sideStr + " paused" };
        return new String[]{ String.format("%.2fx %s", speed.getValue(), sideStr) };
    }
}
