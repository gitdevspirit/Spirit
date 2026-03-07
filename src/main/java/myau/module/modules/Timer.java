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
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;
import org.lwjgl.opengl.GL11;

public class Timer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting   speed         = register(new SliderSetting("Speed",          1.2, 0.01, 10.0, 0.01));
    public final DropdownSetting mode          = register(new DropdownSetting("Mode", 0, "CONSTANT", "VARIABLE", "FREEZE"));
    public final SliderSetting   maxSpeed      = register(new SliderSetting("Max (Variable)", 2.0, 1.0, 5.0, 0.1));
    public final DropdownSetting side          = register(new DropdownSetting("Side", 0, "CLIENT", "SERVER", "BOTH"));
    public final SliderSetting   countdownSecs = register(new SliderSetting("Countdown (s)", 3, 1, 10, 1));
    public final BooleanSetting  showCountdown = register(new BooleanSetting("Show Countdown", true));
    public final BooleanSetting  pauseOnScroll = register(new BooleanSetting("Pause on Scroll", true));
    public final BooleanSetting  pauseOnRight  = register(new BooleanSetting("Pause on RClick", true));

    // Pause state
    private boolean paused   = false;
    private long    pauseEnd = -1;
    private static final long PAUSE_MS = 200;

    // Server-side packet accumulator
    private double packetAccum = 0.0;

    // Countdown — timer still ON while this is running
    private boolean pendingOff   = false;
    private long    offAt        = -1; // absolute time when timer actually turns off
    private long    countdownTotal = 0;

    public Timer() { super("Timer", false); }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled && isEnabled()) {
            long ms = (long)(countdownSecs.getValue() * 1000.0);
            if (showCountdown.getValue() && ms > 0) {
                if (pendingOff) {
                    // Second press = instant off
                    pendingOff = false;
                    offAt      = -1;
                    super.setEnabled(false);
                } else {
                    pendingOff     = true;
                    offAt          = System.currentTimeMillis() + ms;
                    countdownTotal = ms;
                }
                return;
            }
        }
        super.setEnabled(enabled);
    }

    @Override
    public void onEnabled() {
        pendingOff    = false;
        offAt         = -1;
        packetAccum   = 0.0;
    }

    @Override
    public void onDisabled() {
        net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
        if (timer != null) timer.timerSpeed = 1.0F;
        pendingOff  = false;
        offAt       = -1;
        paused      = false;
        pauseEnd    = -1;
        packetAccum = 0.0;
    }

    private double getSpeed() {
        switch (mode.getIndex()) {
            case 1: return speed.getValue() + (maxSpeed.getValue() - speed.getValue()) * Math.random();
            case 2: return 0.01;
            default: return speed.getValue();
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;

        // Check if countdown expired — actually turn off now
        if (pendingOff && offAt > 0 && System.currentTimeMillis() >= offAt) {
            pendingOff = false;
            offAt      = -1;
            super.setEnabled(false);
            return;
        }

        if (paused && System.currentTimeMillis() >= pauseEnd) {
            paused = false; pauseEnd = -1;
        }

        net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
        if (timer == null) return;

        int s = side.getIndex();
        if (s == 0 || s == 2) {
            timer.timerSpeed = paused ? 1.0F : (float) getSpeed();
        } else {
            timer.timerSpeed = 1.0F;
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacketSend(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.SEND) return;
        int s = side.getIndex();
        if (s != 1 && s != 2) return;
        if (paused) return;
        if (!(event.getPacket() instanceof C03PacketPlayer)) return;

        C03PacketPlayer pkt = (C03PacketPlayer) event.getPacket();
        if (!pkt.isMoving()) return;
        double spd = getSpeed();
        if (spd <= 1.0) return;

        packetAccum += spd - 1.0;
        while (packetAccum >= 1.0) {
            packetAccum -= 1.0;
            PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C06PacketPlayerPosLook(
                    pkt.getPositionX(), pkt.getPositionY(), pkt.getPositionZ(),
                    pkt.getYaw(), pkt.getPitch(), pkt.isOnGround()));
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
        paused = true; pauseEnd = System.currentTimeMillis() + PAUSE_MS;
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (!isEnabled() || !pauseOnRight.getValue()) return;
        paused = true; pauseEnd = System.currentTimeMillis() + PAUSE_MS;
    }

    // ── HUD: shown while countdown is active (timer still ON) ─────────────────
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!pendingOff || offAt < 0) return;
        if (mc.thePlayer == null || mc.currentScreen != null) return;

        long remaining = offAt - System.currentTimeMillis();
        if (remaining <= 0) return;

        float progress = 1f - (float) remaining / countdownTotal; // 0→1
        int   secsLeft = (int) Math.ceil(remaining / 1000.0);

        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();

        // ── Layout ────────────────────────────────────────────────────────────
        String numStr   = String.valueOf(secsLeft);
        String labelStr = "turning off in";
        int fontH   = mc.fontRendererObj.FONT_HEIGHT;
        int labelW  = mc.fontRendererObj.getStringWidth(labelStr);
        int numW    = mc.fontRendererObj.getStringWidth(numStr) * 2;
        int cardW   = Math.max(labelW, numW) + 24;
        int barH    = 2;
        int cardH   = fontH + 4 + fontH * 2 + 6 + barH + 14;
        int cardX   = sw / 2 - cardW / 2;
        int cardY   = sh / 2 + 18;

        // ── GL ────────────────────────────────────────────────────────────────
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();

        // Dark card
        drawRect(cardX, cardY, cardW, cardH, 0xDD0C0C10);

        // Progress bar at bottom — shrinks as time runs out, green→red
        float hue = 0.33f * (1f - progress);
        int barCol = java.awt.Color.HSBtoRGB(hue, 0.85f, 1.0f) | 0xFF000000;
        int fillW  = (int)((1f - progress) * cardW);
        if (fillW > 0) drawRect(cardX, cardY + cardH - barH, fillW, barH, barCol);
        // bar track
        drawRect(cardX + fillW, cardY + cardH - barH, cardW - fillW, barH, 0x33FFFFFF);

        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);

        // "turning off in" — muted
        mc.fontRendererObj.drawString(labelStr,
                cardX + (cardW - labelW) / 2f,
                cardY + 8,
                0xFF888888, false);

        // Big number — 2x, colored same as bar
        GlStateManager.pushMatrix();
        GlStateManager.translate(cardX + (cardW - numW) / 2f, cardY + 8 + fontH + 4, 0);
        GlStateManager.scale(2f, 2f, 1f);
        mc.fontRendererObj.drawString(numStr, 0, 0, barCol, false);
        GlStateManager.popMatrix();

        GlStateManager.disableBlend();
    }

    private void drawRect(int x, int y, int w, int h, int color) {
        float[] c = unpack(color);
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x,   y,   0).color(c[0],c[1],c[2],c[3]).endVertex();
        wr.pos(x,   y+h, 0).color(c[0],c[1],c[2],c[3]).endVertex();
        wr.pos(x+w, y+h, 0).color(c[0],c[1],c[2],c[3]).endVertex();
        wr.pos(x+w, y,   0).color(c[0],c[1],c[2],c[3]).endVertex();
        t.draw();
    }

    private float[] unpack(int color) {
        return new float[]{
            ((color>>16)&0xFF)/255f, ((color>>8)&0xFF)/255f,
            (color&0xFF)/255f, ((color>>24)&0xFF)/255f
        };
    }

    @Override
    public String[] getSuffix() {
        if (pendingOff) {
            long rem = Math.max(0, offAt - System.currentTimeMillis());
            return new String[]{ "off in " + (rem < 1000 ? rem + "ms" : String.format("%.1fs", rem/1000.0)) };
        }
        if (paused) return new String[]{ "paused" };
        return new String[]{ String.format("%.2fx", speed.getValue()) };
    }
}
