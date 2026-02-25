package myau.module.modules;

import myau.event.EventTarget;
import myau.events.PacketEvent;
import myau.mixin.IAccessorC17PacketCustomPayload;
import myau.module.Module;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;

public class ClientSpoofer extends Module {
    // TextProperty has no new-system equivalent — plain field, editable via GUI text input
    public String brand = "vanilla";

    public ClientSpoofer() {
        super("ClientSpoofer", false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled()) return;
        if (event.getPacket() instanceof C17PacketCustomPayload) {
            C17PacketCustomPayload packet = (C17PacketCustomPayload) event.getPacket();
            if (packet.getChannelName().equals("MC|Brand")) {
                ((IAccessorC17PacketCustomPayload) packet)
                        .setData(new PacketBuffer(Unpooled.buffer()).writeString(brand));
            }
        }
    }
}