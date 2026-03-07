package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.management.NotificationManager;
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
import org.lwjgl.opengl.GL11;

import java.util.List;

public class Notifications extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting   duration = register(new SliderSetting("Duration", 3.0, 1.0, 10.0, 0.5));
    public final DropdownSetting position = register(new DropdownSetting("Position", 0,
            "Bottom Right", "Top Right", "Bottom Left", "Top Left"));
    public final BooleanSetting  anim     = register(new BooleanSetting("Animation", true));

    public final SliderSetting pillR = register(new SliderSetting(" Pill Red",   220,  0, 255, 1));
    public final SliderSetting pillG = register(new SliderSetting(" Pill Green",  80,  0, 255, 1));
    public final SliderSetting pillB = register(new SliderSetting(" Pill Blue",   30,  0, 255, 1));

    public final SliderSetting bgR = register(new SliderSetting(" BG Red",    18,  0, 255, 1));
    public final SliderSetting bgG = register(new SliderSetting(" BG Green",  18,  0, 255, 1));
    public final SliderSetting bgB = register(new SliderSetting(" BG Blue",   18,  0, 255, 1));
    public final SliderSetting bgA = register(new SliderSetting(" BG Alpha", 210,  0, 255, 1));

    public final SliderSetting msgR = register(new SliderSetting(" Text Red",   210, 0, 255, 1));
    public final SliderSetting msgG = register(new SliderSetting(" Text Green", 210, 0, 255, 1));
    public final SliderSetting msgB = register(new SliderSetting(" Text Blue",  210, 0, 255, 1));

    private static final String LABEL    = "Spirit";
    private static final int    H        = 26;
    private static final int    PILL_PAD = 7;
    private static final int    MSG_PAD  = 9;
    private static final int    MARGIN   = 10;
    private static final int    GAP      = 5;
    private static final int    R        = 4; // corner radius

    private static final float ANIM_IN  = 200f;
    private static final float ANIM_OUT = 250f;

    public Notifications() { super("Notifications", true); }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.thePlayer == null || Myau.notificationManager == null) return;
        List<NotificationManager.NotificationEntry> active = Myau.notificationManager.getActive();
        if (active.isEmpty()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
        int pos = position.getIndex();
        boolean fromRight  = pos == 0 || pos == 1;
        boolean fromBottom = pos == 0 || pos == 2;

        int labelW = mc.fontRendererObj.getStringWidth(LABEL);
        int pillW  = labelW + PILL_PAD * 2;

        for (int i = 0; i < active.size(); i++) {
            NotificationManager.NotificationEntry n = active.get(i);
            String msg  = n.message;
            int msgW    = mc.fontRendererObj.getStringWidth(msg);
            int toastW  = pillW + MSG_PAD + msgW + MSG_PAD;

            long  age   = n.getAge();
            long  total = n.durationMillis;
            float alpha = computeAlpha(age, total);
            float slide = computeSlide(age, total);
            float off   = anim.getValue() ? (toastW + MARGIN + 20) * (1f - slide) : 0f;

            float x = fromRight ? sw - MARGIN - toastW + off : MARGIN - off;
            float y = fromBottom ? sh - MARGIN - H - i * (H + GAP)
                                 : MARGIN + i * (H + GAP);

            int bgColor   = argb((int)bgA.getValue(), (int)bgR.getValue(), (int)bgG.getValue(), (int)bgB.getValue(), alpha);
            int pillColor = argb(255, (int)pillR.getValue(), (int)pillG.getValue(), (int)pillB.getValue(), alpha);
            int textWhite = argb(255, 255, 255, 255, alpha);
            int msgColor  = argb(255, (int)msgR.getValue(), (int)msgG.getValue(), (int)msgB.getValue(), alpha);

            // ── Setup GL ─────────────────────────────────────────────────────
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.disableAlpha();

            // ── Full toast background: only round the outer corners ───────────
            // Left corners rounded, right corners rounded, all done with arcs
            drawRoundedRect(x, y, toastW, H, R, bgColor);

            // ── Pill: left corners rounded, right edge FLAT (square) ──────────
            drawRectLeftRounded(x, y, pillW, H, R, pillColor);

            // ── Restore GL for text ───────────────────────────────────────────
            GlStateManager.enableTexture2D();
            GlStateManager.enableDepth();
            GlStateManager.enableAlpha();
            GlStateManager.color(1f, 1f, 1f, 1f);

            int fontH = mc.fontRendererObj.FONT_HEIGHT;
            float ty  = y + (H - fontH) / 2f;

            mc.fontRendererObj.drawString(LABEL, x + (pillW - labelW) / 2f, ty, textWhite, false);
            mc.fontRendererObj.drawString(msg,   x + pillW + MSG_PAD,        ty, msgColor,  false);

            GlStateManager.disableBlend();
        }
    }

    /** Full rounded rect — all 4 corners arc'd */
    private void drawRoundedRect(float x, float y, float w, float h, int r, int color) {
        float[] c = colorF(color);
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        GL11.glColor4f(c[0], c[1], c[2], c[3]);

        // Center fill
        drawQuad(wr, t, x+r, y, x+w-r, y+h, color);
        drawQuad(wr, t, x,   y+r, x+r, y+h-r, color);
        drawQuad(wr, t, x+w-r, y+r, x+w, y+h-r, color);

        // Corners
        drawArc(wr, t, x+r,   y+r,   r, 180, 270, color); // top-left
        drawArc(wr, t, x+w-r, y+r,   r, 270, 360, color); // top-right
        drawArc(wr, t, x+r,   y+h-r, r,  90, 180, color); // bottom-left
        drawArc(wr, t, x+w-r, y+h-r, r,   0,  90, color); // bottom-right
    }

    /** Rect with only LEFT side rounded, right side flat */
    private void drawRectLeftRounded(float x, float y, float w, float h, int r, int color) {
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();

        // Center fill
        drawQuad(wr, t, x+r, y, x+w, y+h, color);
        drawQuad(wr, t, x,   y+r, x+r, y+h-r, color);

        // Only left corners
        drawArc(wr, t, x+r, y+r,   r, 180, 270, color); // top-left
        drawArc(wr, t, x+r, y+h-r, r,  90, 180, color); // bottom-left
    }

    private void drawQuad(WorldRenderer wr, Tessellator t, float x1, float y1, float x2, float y2, int color) {
        float[] c = colorF(color);
        GlStateManager.disableTexture2D();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x1, y1, 0).color(c[0], c[1], c[2], c[3]).endVertex();
        wr.pos(x1, y2, 0).color(c[0], c[1], c[2], c[3]).endVertex();
        wr.pos(x2, y2, 0).color(c[0], c[1], c[2], c[3]).endVertex();
        wr.pos(x2, y1, 0).color(c[0], c[1], c[2], c[3]).endVertex();
        t.draw();
    }

    private void drawArc(WorldRenderer wr, Tessellator t, float cx, float cy,
                         int r, int startDeg, int endDeg, int color) {
        float[] c = colorF(color);
        GlStateManager.disableTexture2D();
        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(cx, cy, 0).color(c[0], c[1], c[2], c[3]).endVertex();
        for (int deg = startDeg; deg <= endDeg; deg += 3) {
            double rad = Math.toRadians(deg);
            wr.pos(cx + Math.cos(rad) * r, cy + Math.sin(rad) * r, 0)
              .color(c[0], c[1], c[2], c[3]).endVertex();
        }
        t.draw();
    }

    private float[] colorF(int color) {
        return new float[]{
            ((color >> 16) & 0xFF) / 255f,
            ((color >>  8) & 0xFF) / 255f,
            ( color        & 0xFF) / 255f,
            ((color >> 24) & 0xFF) / 255f
        };
    }

    private int argb(int a, int r, int g, int b, float alpha) {
        return (Math.max(0, Math.min(255, (int)(a * alpha))) << 24)
             | (r << 16) | (g << 8) | b;
    }

    private float computeSlide(long age, long total) {
        if (age < ANIM_IN) { float t = age / ANIM_IN; return 1f-(1f-t)*(1f-t)*(1f-t); }
        if (total > 0 && total - age < ANIM_OUT) { float t = (total-age)/ANIM_OUT; return t*t*t; }
        return 1f;
    }

    private float computeAlpha(long age, long total) {
        if (age < ANIM_IN) return Math.min(1f, age / ANIM_IN * 2f);
        if (total > 0 && total - age < ANIM_OUT) return Math.max(0f, (total-age) / ANIM_OUT);
        return 1f;
    }
}
