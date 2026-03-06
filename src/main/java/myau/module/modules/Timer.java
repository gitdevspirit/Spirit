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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public class Timer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting   speed       = register(new SliderSetting("Speed",          1.0, 0.01, 10.0, 0.01));
    public final DropdownSetting mode        = register(new DropdownSetting("Mode", 0, "CONSTANT", "VARIABLE", "FREEZE"));
    public final SliderSetting   maxSpeed    = register(new SliderSetting("Max (Variable)", 2.0,  1.0,  5.0,  0.1));
    public final SliderSetting   offDelay    = register(new SliderSetting("Off Delay (ms)", 0,    0,    5000, 100));
    public final BooleanSetting  showCountdown = register(new BooleanSetting("Show Countdown", true));

    // Countdown state
    private long  countdownEnd   = -1;   // -1 = not counting down
    private boolean pendingDisable = false;

    public Timer() { super("Timer", false); }

    // ── Override setEnabled to intercept the disable with a countdown ────────
    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled && isEnabled() && (long) offDelay.getValue() > 0) {
            // Don't actually disable yet — start the countdown
            countdownEnd   = System.currentTimeMillis() + (long) offDelay.getValue();
            pendingDisable = true;
            return; // skip super.setEnabled(false)
        }
        super.setEnabled(enabled);
    }

    @Override
    public void onDisabled() {
        // Reset real timer to 1x when actually disabled
        net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
        if (timer != null) timer.timerSpeed = 1.0F;
        countdownEnd   = -1;
        pendingDisable = false;
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;

        // Check if countdown expired
        if (pendingDisable && countdownEnd > 0
                && System.currentTimeMillis() >= countdownEnd) {
            pendingDisable = false;
            countdownEnd   = -1;
            setEnabled(false);
            return;
        }

        net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
        if (timer == null) return;

        double spd;
        switch (mode.getIndex()) {
            case 1: // VARIABLE
                spd = speed.getValue() + (maxSpeed.getValue() - speed.getValue()) * Math.random();
                break;
            case 2: // FREEZE
                spd = 0.01;
                break;
            default: // CONSTANT
                spd = speed.getValue();
                break;
        }
        timer.timerSpeed = (float) spd;
    }

    // FREEZE mode: push chat directly to GUI so it bypasses the frozen tick queue
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

    // ── Countdown HUD ─────────────────────────────────────────────────────────
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || !pendingDisable || !showCountdown.getValue()) return;
        if (mc.thePlayer == null || mc.currentScreen != null) return;

        long remaining = countdownEnd - System.currentTimeMillis();
        if (remaining <= 0) return;

        double seconds  = remaining / 1000.0;
        long   total    = (long) offDelay.getValue();
        float  progress = 1.0f - (float) remaining / total; // 0 → 1 as countdown progresses

        ScaledResolution sr = new ScaledResolution(mc);
        int cx = sr.getScaledWidth()  / 2;
        int cy = sr.getScaledHeight() / 2 + 30; // just below crosshair

        String label = String.format("%.1fs", seconds);

        // Background bar
        int barW = 80;
        int barH = 6;
        int bx   = cx - barW / 2;
        int by   = cy + 10;

        // Draw using vanilla font renderer + GL primitives
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.enableBlend();

        // Background
        drawRect(bx - 1, by - 1, bx + barW + 1, by + barH + 1, 0x88000000);
        // Empty bar
        drawRect(bx, by, bx + barW, by + barH, 0xFF333333);
        // Filled portion — fades from pink to red as time runs out
        int fillW = (int)(barW * progress);
        float hue = 0.93f - progress * 0.13f; // pink → red
        int barColor = java.awt.Color.HSBtoRGB(hue, 0.85f, 1.0f) | 0xFF000000;
        if (fillW > 0) drawRect(bx, by, bx + fillW, by + barH, barColor);

        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.popMatrix();

        // Text
        mc.fontRendererObj.drawStringWithShadow(
                label,
                cx - mc.fontRendererObj.getStringWidth(label) / 2f,
                cy,
                0xFFFFFFFF);
        mc.fontRendererObj.drawStringWithShadow(
                "Timer off in",
                cx - mc.fontRendererObj.getStringWidth("Timer off in") / 2f,
                cy - 10,
                0xAAFFFFFF);
    }

    private static void drawRect(int x1, int y1, int x2, int y2, int color) {
        int a = (color >> 24 & 0xFF);
        int r = (color >> 16 & 0xFF);
        int g = (color >> 8  & 0xFF);
        int b = (color       & 0xFF);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer wr  = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x1, y2, 0).color(r, g, b, a).endVertex();
        wr.pos(x2, y2, 0).color(r, g, b, a).endVertex();
        wr.pos(x2, y1, 0).color(r, g, b, a).endVertex();
        wr.pos(x1, y1, 0).color(r, g, b, a).endVertex();
        tess.draw();
    }

    @Override
    public String[] getSuffix() {
        if (pendingDisable) {
            long rem = Math.max(0, countdownEnd - System.currentTimeMillis());
            return new String[]{ String.format("off in %.1fs", rem / 1000.0) };
        }
        return new String[]{ String.format("%.2fx", speed.getValue()) };
    }
}
