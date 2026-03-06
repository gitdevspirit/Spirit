package myau.module.modules;

import myau.event.EventTarget;
import myau.events.PacketEvent;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorMinecraft;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;

public class Timer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting   speed    = register(new SliderSetting("Speed", 1.0, 0.01, 10.0, 0.01));
    public final DropdownSetting mode     = register(new DropdownSetting("Mode", 0, "CONSTANT", "VARIABLE", "FREEZE"));
    public final SliderSetting   maxSpeed = register(new SliderSetting("Max (Variable)", 2.0, 1.0, 5.0, 0.1));

    public Timer() {
        super("Timer", false);
    }

    @Override
    public void onDisabled() {
        net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
        if (timer != null) {
            timer.timerSpeed = 1.0F;
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }

        net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
        if (timer != null) {
            double spd;
            switch (mode.getIndex()) {
                case 1: // VARIABLE
                    spd = speed.getValue() + (maxSpeed.getValue() - speed.getValue()) * Math.random();
                    break;
                case 2: // FREEZE — near-zero timer, but chat/notifications pass through via PacketEvent
                    spd = 0.01;
                    break;
                default: // CONSTANT
                    spd = speed.getValue();
                    break;
            }
            timer.timerSpeed = (float) spd;
        }
    }

    // In FREEZE mode, push chat messages directly to the GUI so they appear
    // instantly without waiting for the near-frozen tick queue to process them.
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || mode.getIndex() != 2) return;
        if (event.getType() != EventType.RECEIVE) return;
        if (event.getPacket() instanceof S02PacketChat) {
            S02PacketChat pkt = (S02PacketChat) event.getPacket();
            IChatComponent msg = pkt.getChatComponent();
            if (msg != null && mc.ingameGUI != null) {
                mc.ingameGUI.getChatGUI().printChatMessage(msg);
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%.1fx", this.speed.getValue())};
    }
}
