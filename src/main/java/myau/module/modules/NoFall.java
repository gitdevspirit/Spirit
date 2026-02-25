package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorC03PacketPlayer;
import myau.mixin.IAccessorMinecraft;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;

public class NoFall extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil packetDelayTimer     = new TimerUtil();
    private final TimerUtil scoreboardResetTimer = new TimerUtil();
    private boolean slowFalling  = false;
    private boolean lastOnGround = false;

    public final DropdownSetting mode     = new DropdownSetting("Mode",     0, "PACKET", "BLINK", "NO_GROUND", "SPOOF");
    public final SliderSetting   distance = new SliderSetting("Distance", 3.0, 0.0, 20.0, 0.5);
    public final SliderSetting   delay    = new SliderSetting("Delay",      0,   0, 10000, 10);

    public NoFall() {
        super("NoFall", false);
        register(mode);
        register(distance);
        register(delay);
    }

    private boolean canTrigger() {
        return scoreboardResetTimer.hasTimeElapsed(3000)
                && packetDelayTimer.hasTimeElapsed((long) delay.getValue());
    }

    @EventTarget(Priority.HIGH)
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof S08PacketPlayerPosLook) {
            onDisabled();
            return;
        }
        if (!isEnabled() || event.getType() != EventType.SEND || event.isCancelled()) return;
        if (!(event.getPacket() instanceof C03PacketPlayer)) return;

        C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
        switch (mode.getIndex()) {
            case 0: // PACKET
                if (slowFalling) {
                    slowFalling = false;
                    ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
                } else if (!packet.isOnGround()) {
                    AxisAlignedBB aabb = mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0);
                    if (PlayerUtil.canFly((float) distance.getValue())
                            && !PlayerUtil.checkInWater(aabb) && canTrigger()) {
                        packetDelayTimer.reset();
                        slowFalling = true;
                        ((IAccessorMinecraft) mc).getTimer().timerSpeed = 0.5F;
                    }
                }
                break;

            case 1: { // BLINK
                boolean allowed = !mc.thePlayer.isOnLadder()
                        && !mc.thePlayer.capabilities.allowFlying
                        && mc.thePlayer.hurtTime == 0;
                if (Myau.blinkManager.getBlinkingModule() != BlinkModules.NO_FALL) {
                    if (lastOnGround && !packet.isOnGround() && allowed
                            && PlayerUtil.canFly((int) distance.getValue())
                            && mc.thePlayer.motionY < 0.0) {
                        Myau.blinkManager.setBlinkState(false, Myau.blinkManager.getBlinkingModule());
                        Myau.blinkManager.setBlinkState(true, BlinkModules.NO_FALL);
                    }
                } else if (!allowed) {
                    Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                    ChatUtil.sendFormatted(String.format("%s%s: &cFailed player check!&r", Myau.clientName, getName()));
                } else if (PlayerUtil.checkInWater(mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0))) {
                    Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                    ChatUtil.sendFormatted(String.format("%s%s: &cFailed void check!&r", Myau.clientName, getName()));
                } else if (packet.isOnGround()) {
                    for (Packet<?> p : Myau.blinkManager.blinkedPackets)
                        if (p instanceof C03PacketPlayer)
                            ((IAccessorC03PacketPlayer) p).setOnGround(true);
                    Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                    packetDelayTimer.reset();
                }
                lastOnGround = packet.isOnGround() && allowed && canTrigger();
                break;
            }

            case 2: // NO_GROUND
                ((IAccessorC03PacketPlayer) packet).setOnGround(false);
                break;

            case 3: // SPOOF
                if (!packet.isOnGround()) {
                    AxisAlignedBB aabb = mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0);
                    if (PlayerUtil.canFly((float) distance.getValue())
                            && !PlayerUtil.checkInWater(aabb) && canTrigger()) {
                        packetDelayTimer.reset();
                        ((IAccessorC03PacketPlayer) packet).setOnGround(true);
                        mc.thePlayer.fallDistance = 0.0F;
                    }
                }
                break;
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (ServerUtil.hasPlayerCountInfo()) scoreboardResetTimer.reset();
        if (mode.getIndex() == 0 && slowFalling) {
            PacketUtil.sendPacketNoEvent(new C03PacketPlayer(true));
            mc.thePlayer.fallDistance = 0.0F;
        }
    }

    @Override
    public void onDisabled() {
        lastOnGround = false;
        Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
        if (slowFalling) {
            slowFalling = false;
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{ mode.getOptions()[mode.getIndex()] };
    }
}