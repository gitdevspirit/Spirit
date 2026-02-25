package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;

import java.util.concurrent.ConcurrentLinkedQueue;

public class FakeLag extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting delay = new SliderSetting("Delay MS", 200, 50, 5000, 10);

    private final ConcurrentLinkedQueue<PacketData> packetQueue = new ConcurrentLinkedQueue<>();
    private boolean isDispatching = false;

    public FakeLag() {
        super("FakeLag", false);
        register(delay);
    }

    @Override
    public void onEnabled() {
        packetQueue.clear();
        isDispatching = false;
    }

    @Override
    public void onDisabled() {
        isDispatching = true;
        while (!packetQueue.isEmpty())
            PacketUtil.sendPacket(packetQueue.poll().packet);
        isDispatching = false;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.SEND) return;
        if (isDispatching || mc.thePlayer == null || mc.theWorld == null) return;
        event.setCancelled(true);
        packetQueue.add(new PacketData(event.getPacket(), System.currentTimeMillis()));
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE || mc.thePlayer == null) return;
        long now = System.currentTimeMillis();
        long delayMs = (long) delay.getValue();
        while (!packetQueue.isEmpty()) {
            PacketData data = packetQueue.peek();
            if (now - data.timestamp >= delayMs) {
                packetQueue.poll();
                isDispatching = true;
                PacketUtil.sendPacket(data.packet);
                isDispatching = false;
            } else break;
        }
    }

    private static class PacketData {
        final Packet<?> packet;
        final long      timestamp;
        PacketData(Packet<?> packet, long timestamp) { this.packet = packet; this.timestamp = timestamp; }
    }
}