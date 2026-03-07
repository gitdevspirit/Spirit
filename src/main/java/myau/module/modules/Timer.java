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
    public final SliderSetting   countdownSecs = register(new SliderSetting("Countdown (s)", 5, 1, 10, 1));
    public final BooleanSetting  showCountdown = register(new BooleanSetting("Show Countdown", true));
    public final BooleanSetting  pauseOnScroll = register(new BooleanSetting("Pause on Scroll", true));
    public final BooleanSetting  pauseOnRight  = register(new BooleanSetting("Pause on RClick", true));

    // Pause state
    private boolean paused   = false;
    private long    pauseEnd = -1;
    private static final long PAUSE_MS = 200;

    // Server-side packet accumulator
    private double packetAccum = 0.0;

    // Post-off cosmetic countdown (purely visual, timer is already OFF)
    private boolean counting    = false;
    private long    countStart  = -1;
    private int     totalCount  = 5;

    public Timer() { super("Timer", false); }

    @Override
    public void onEnabled() {
        counting   = false;
        countStart = -1;
        packetAccum = 0.0;
    }

    @Override
    public void onDisabled() {
        // Instantly reset — zero delay
        net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
        if (timer != null) timer.timerSpeed = 1.0F;
        paused      = false;
        pauseEnd    = -1;
        packetAccum = 0.0;
        // Start cosmetic countdown
        if (showCountdown.getValue()) {
            counting   = true;
            countStart = System.currentTimeMillis();
            totalCount = (int) countdownSecs.getValue();
        }
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

    // ── Cosmetic countdown HUD (fires even when module is OFF) ────────────────
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!counting || countStart < 0) return;
        if (mc.thePlayer == null || mc.currentScreen != null) return;

        long elapsed  = System.currentTimeMillis() - countStart;
        long totalMs  = totalCount * 1000L;
        if (elapsed >= totalMs) { counting = false; countStart = -1; return; }

        // Which second we're on (5, 4, 3, 2, 1)
        int secsLeft  = (int) Math.ceil((totalMs - elapsed) / 1000.0);
        // Progress within the current second (0→1)
        float secProg = 1f - (float)((totalMs - elapsed) % 1000) / 1000f;
        // Overall fade out in last 500ms
        float fadeAlpha = elapsed > totalMs - 500 ? (float)(totalMs - elapsed) / 500f : 1f;

        ScaledResolution sr = new ScaledResolution(mc);
        int cx = sr.getScaledWidth()  / 2;
        int cy = sr.getScaledHeight() / 2;

        String numStr   = String.valueOf(secsLeft);
        String labelStr = "TIMER OFF IN";
        int fontH = mc.fontRendererObj.FONT_HEIGHT;

        // Number is rendered 3x scale
        int numScaledW  = mc.fontRendererObj.getStringWidth(numStr) * 3;
        int labelW      = mc.fontRendererObj.getStringWidth(labelStr);
        int cardW       = Math.max(numScaledW, labelW) + 32;
        int cardH       = fontH + 6 + fontH * 3 + 20; // label + gap + number(3x) + padding
        int cardX       = cx - cardW / 2;
        int cardY       = cy + 14;

        // Pulse: card slightly scales up/down each second
        float pulse = 1f + (float)Math.sin(secProg * Math.PI) * 0.015f;

        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy + 14 + cardH / 2f, 0);
        GlStateManager.scale(pulse, pulse, 1f);
        GlStateManager.translate(-(cx), -(cy + 14 + cardH / 2f), 0);

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();

        // Card bg
        drawRoundedRect(cardX, cardY, cardW, cardH, 5, applyAlpha(0xEE0D0D14, fadeAlpha));

        // Top accent bar — color shifts green→yellow→red as countdown progresses
        float hue = 0.33f - 0.33f * (1f - (float)(totalMs - elapsed) / totalMs);
        int accentColor = java.awt.Color.HSBtoRGB(hue, 0.9f, 1.0f) | 0xFF000000;
        drawRoundedRect(cardX + 4, cardY, cardW - 8, 2, 1, applyAlpha(accentColor, fadeAlpha));

        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);

        // "TIMER OFF IN" label
        mc.fontRendererObj.drawString(labelStr,
                cardX + (cardW - labelW) / 2f,
                cardY + 10,
                applyAlpha(0xFFAAAAAA, fadeAlpha), false);

        // Big number — 3x scale, centered
        GlStateManager.pushMatrix();
        float numX = cardX + (cardW - numScaledW) / 2f;
        float numY = cardY + 10 + fontH + 6;
        GlStateManager.translate(numX, numY, 0);
        GlStateManager.scale(3f, 3f, 1f);
        // Color: green when plenty of time, shifts to red
        int numColor = applyAlpha(accentColor, fadeAlpha);
        mc.fontRendererObj.drawString(numStr, 0, 0, numColor, false);
        GlStateManager.popMatrix();

        GlStateManager.popMatrix();
        GlStateManager.disableBlend();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int applyAlpha(int color, float a) {
        int orig = (color >>> 24) & 0xFF;
        return ((int)(orig * a) << 24) | (color & 0x00FFFFFF);
    }

    private void drawRoundedRect(int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        drawQuad(x+r, y,     x+w-r, y+h,   color);
        drawQuad(x,   y+r,   x+r,   y+h-r, color);
        drawQuad(x+w-r, y+r, x+w,   y+h-r, color);
        drawArc(x+r,   y+r,   r, 180, 270, color);
        drawArc(x+w-r, y+r,   r, 270, 360, color);
        drawArc(x+r,   y+h-r, r,  90, 180, color);
        drawArc(x+w-r, y+h-r, r,   0,  90, color);
    }

    private void drawQuad(int x1, int y1, int x2, int y2, int color) {
        float[] c = unpack(color);
        Tessellator t = Tessellator.getInstance(); WorldRenderer wr = t.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x1,y1,0).color(c[0],c[1],c[2],c[3]).endVertex();
        wr.pos(x1,y2,0).color(c[0],c[1],c[2],c[3]).endVertex();
        wr.pos(x2,y2,0).color(c[0],c[1],c[2],c[3]).endVertex();
        wr.pos(x2,y1,0).color(c[0],c[1],c[2],c[3]).endVertex();
        t.draw();
    }

    private void drawArc(int cx, int cy, int r, int s, int e, int color) {
        float[] c = unpack(color);
        Tessellator t = Tessellator.getInstance(); WorldRenderer wr = t.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(cx,cy,0).color(c[0],c[1],c[2],c[3]).endVertex();
        for (int deg = s; deg <= e; deg += 3) {
            double rad = Math.toRadians(deg);
            wr.pos(cx + Math.cos(rad)*r, cy + Math.sin(rad)*r, 0).color(c[0],c[1],c[2],c[3]).endVertex();
        }
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
        if (paused) return new String[]{ "paused" };
        return new String[]{ String.format("%.2fx", speed.getValue()) };
    }
}
