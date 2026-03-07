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
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class Timer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting   speed         = register(new SliderSetting("Speed",          1.2, 0.01, 10.0, 0.01));
    public final DropdownSetting mode          = register(new DropdownSetting("Mode", 0, "CONSTANT", "VARIABLE", "FREEZE"));
    public final SliderSetting   maxSpeed      = register(new SliderSetting("Max (Variable)", 2.0,  1.0,  5.0,  0.1));
    public final SliderSetting   offDelay      = register(new SliderSetting("Off Delay (ms)", 200,  0,    2000, 50));
    public final BooleanSetting  showCountdown = register(new BooleanSetting("Show Countdown", true));
    public final BooleanSetting  pauseOnScroll = register(new BooleanSetting("Pause on Scroll", true));
    public final BooleanSetting  pauseOnRight  = register(new BooleanSetting("Pause on RClick", true));

    // Countdown state
    private long    countdownEnd   = -1;
    private boolean pendingDisable = false;
    private long    totalDelay     = 0;

    // Pause state
    private boolean paused  = false;
    private long    pauseEnd = -1;
    private static final long PAUSE_MS = 200;

    public Timer() { super("Timer", false); }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled && isEnabled()) {
            long delay = (long) offDelay.getValue();
            if (delay > 0) {
                if (pendingDisable) {
                    // Second press = instant off
                    pendingDisable = false;
                    countdownEnd   = -1;
                    totalDelay     = 0;
                    super.setEnabled(false);
                    return;
                }
                countdownEnd   = System.currentTimeMillis() + delay;
                totalDelay     = delay;
                pendingDisable = true;
                return;
            }
        }
        super.setEnabled(enabled);
    }

    @Override
    public void onDisabled() {
        net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
        if (timer != null) timer.timerSpeed = 1.0F;
        countdownEnd   = -1;
        pendingDisable = false;
        totalDelay     = 0;
        paused         = false;
        pauseEnd       = -1;
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;

        if (pendingDisable && countdownEnd > 0 && System.currentTimeMillis() >= countdownEnd) {
            pendingDisable = false;
            countdownEnd   = -1;
            totalDelay     = 0;
            setEnabled(false);
            return;
        }

        net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
        if (timer == null) return;

        if (paused) {
            if (System.currentTimeMillis() >= pauseEnd) {
                paused   = false;
                pauseEnd = -1;
            } else {
                timer.timerSpeed = 1.0F;
                return;
            }
        }

        double spd;
        switch (mode.getIndex()) {
            case 1: spd = speed.getValue() + (maxSpeed.getValue() - speed.getValue()) * Math.random(); break;
            case 2: spd = 0.01; break;
            default: spd = speed.getValue(); break;
        }
        timer.timerSpeed = (float) spd;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
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

    // ── Countdown HUD ─────────────────────────────────────────────────────────
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        // Show countdown whenever offDelay > 0 AND module is enabled
        // (pendingDisable = actively counting down, OR just enabled with delay set = preview)
        if (!isEnabled() || !showCountdown.getValue()) return;
        if (mc.thePlayer == null || mc.currentScreen != null) return;
        if (!pendingDisable) return;

        long remaining = countdownEnd - System.currentTimeMillis();
        if (remaining <= 0) return;

        // Progress 0→1 over the countdown
        float progress = totalDelay > 0 ? 1f - (float) remaining / totalDelay : 0f;

        ScaledResolution sr = new ScaledResolution(mc);
        int cx = sr.getScaledWidth()  / 2;
        int cy = sr.getScaledHeight() / 2;

        // Format: show ms if < 1s, else seconds
        String timeStr = remaining < 1000
                ? remaining + "ms"
                : String.format("%.1fs", remaining / 1000.0);
        String label = "Timer off in " + timeStr;

        int labelW = mc.fontRendererObj.getStringWidth(label);
        int fontH  = mc.fontRendererObj.FONT_HEIGHT;

        // Card dimensions
        int padH = 10, padV = 6;
        int cardW = labelW + padH * 2 + 6; // +6 for bar end clearance
        int cardH = fontH + padV * 2 + 8;  // 8 for bar below text
        int cardX = cx - cardW / 2;
        int cardY = cy + 20;

        // ── GL setup ─────────────────────────────────────────────────────────
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();

        // ── Background card ───────────────────────────────────────────────────
        drawRoundedRect(cardX, cardY, cardW, cardH, 4, 0xDD0D0D12);

        // ── Progress bar (inside card, below text) ────────────────────────────
        int barX  = cardX + padH;
        int barY  = cardY + padV + fontH + 3;
        int barW  = cardW - padH * 2;
        int barH  = 3;

        // Track
        drawRoundedRect(barX, barY, barW, barH, 1, 0x55FFFFFF);

        // Fill — shrinks from full to 0 as time runs out (progress = how much has elapsed)
        int fillW = (int)((1f - progress) * barW);
        if (fillW > 2) {
            // Color shifts pink → orange → red as time runs out
            float hue = 0.93f - progress * 0.2f;
            int fillCol = Color.HSBtoRGB(hue, 0.9f, 1.0f) | 0xFF000000;
            drawRoundedRect(barX, barY, fillW, barH, 1, fillCol);
        }

        // ── Restore GL for text ───────────────────────────────────────────────
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);

        // Label: "Timer off in Xs"
        mc.fontRendererObj.drawString(
                label,
                cardX + padH,
                cardY + padV,
                0xFFFFFFFF,
                false);

        GlStateManager.disableBlend();
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private void drawRoundedRect(int x, int y, int w, int h, int r, int color) {
        // Clamp radius
        r = Math.min(r, Math.min(w, h) / 2);
        drawQuad(x + r, y,     x + w - r, y + h,     color);
        drawQuad(x,     y + r, x + r,     y + h - r, color);
        drawQuad(x+w-r, y + r, x + w,     y + h - r, color);
        drawArc(x + r,     y + r,     r, 180, 270, color);
        drawArc(x + w - r, y + r,     r, 270, 360, color);
        drawArc(x + r,     y + h - r, r,  90, 180, color);
        drawArc(x + w - r, y + h - r, r,   0,  90, color);
    }

    private void drawQuad(int x1, int y1, int x2, int y2, int color) {
        float[] c = unpack(color);
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x1,y1,0).color(c[0],c[1],c[2],c[3]).endVertex();
        wr.pos(x1,y2,0).color(c[0],c[1],c[2],c[3]).endVertex();
        wr.pos(x2,y2,0).color(c[0],c[1],c[2],c[3]).endVertex();
        wr.pos(x2,y1,0).color(c[0],c[1],c[2],c[3]).endVertex();
        t.draw();
    }

    private void drawArc(int cx, int cy, int r, int s, int e, int color) {
        float[] c = unpack(color);
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
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
            ((color>>16)&0xFF)/255f,
            ((color>> 8)&0xFF)/255f,
            ( color     &0xFF)/255f,
            ((color>>24)&0xFF)/255f
        };
    }

    @Override
    public String[] getSuffix() {
        if (pendingDisable) {
            long rem = Math.max(0, countdownEnd - System.currentTimeMillis());
            return new String[]{ rem < 1000 ? rem + "ms" : String.format("%.1fs", rem/1000.0) };
        }
        if (paused) return new String[]{ "paused" };
        return new String[]{ String.format("%.2fx", speed.getValue()) };
    }
}
