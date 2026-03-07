package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.management.NotificationManager;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.ui.clickgui.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class Notifications extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // Timing
    public final SliderSetting duration  = register(new SliderSetting("Duration",  3.0, 1.0, 10.0, 0.5));

    // Layout
    public final DropdownSetting position = register(new DropdownSetting("Position", 0,
            "Bottom Right", "Top Right", "Bottom Left", "Top Left"));
    public final SliderSetting   width    = register(new SliderSetting("Width",    160, 80, 300, 2));
    public final BooleanSetting  anim     = register(new BooleanSetting("Animation", true));

    // Background
    public final SliderSetting bgR     = register(new SliderSetting(" BG Red",    20,  0, 255, 1));
    public final SliderSetting bgG     = register(new SliderSetting(" BG Green",  20,  0, 255, 1));
    public final SliderSetting bgB     = register(new SliderSetting(" BG Blue",   20,  0, 255, 1));
    public final SliderSetting bgAlpha = register(new SliderSetting(" BG Alpha", 220,  0, 255, 1));
    public final SliderSetting radius  = register(new SliderSetting(" Radius",     6,  0,  20, 1));

    // Accent bar
    public final BooleanSetting accentBar   = register(new BooleanSetting(" Accent Bar", true));
    public final SliderSetting  accentR     = register(new SliderSetting(" Accent Red",   233, 0, 255, 1));
    public final SliderSetting  accentG     = register(new SliderSetting(" Accent Green", 145, 0, 255, 1));
    public final SliderSetting  accentB     = register(new SliderSetting(" Accent Blue",  184, 0, 255, 1));

    // Title text
    public final SliderSetting titleR = register(new SliderSetting(" Title Red",   255, 0, 255, 1));
    public final SliderSetting titleG = register(new SliderSetting(" Title Green", 255, 0, 255, 1));
    public final SliderSetting titleB = register(new SliderSetting(" Title Blue",  255, 0, 255, 1));

    // Body text
    public final SliderSetting bodyR = register(new SliderSetting(" Body Red",   160, 0, 255, 1));
    public final SliderSetting bodyG = register(new SliderSetting(" Body Green", 160, 0, 255, 1));
    public final SliderSetting bodyB = register(new SliderSetting(" Body Blue",  160, 0, 255, 1));

    private static final int   H        = 28;
    private static final int   MARGIN   = 10;
    private static final int   GAP      = 5;
    private static final int   PAD_H    = 8;
    private static final float ANIM_IN  = 180f;
    private static final float ANIM_OUT = 240f;

    public Notifications() { super("Notifications", true); }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.thePlayer == null || Myau.notificationManager == null) return;
        List<NotificationManager.NotificationEntry> active = Myau.notificationManager.getActive();
        if (active.isEmpty()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
        int W = (int) width.getValue();
        int pos = position.getIndex();
        boolean fromRight  = pos == 0 || pos == 1;
        boolean fromBottom = pos == 0 || pos == 2;

        int bgColor     = rgba(bgR, bgG, bgB, bgAlpha);
        int accentColor = rgb(accentR, accentG, accentB);
        int titleColor  = rgb(titleR, titleG, titleB);
        int bodyColor   = rgb(bodyR, bodyG, bodyB);
        float r         = (float) radius.getValue();
        boolean bar     = accentBar.getValue();

        for (int i = 0; i < active.size(); i++) {
            NotificationManager.NotificationEntry n = active.get(i);

            String[] parts = n.message.split(" ", 2);
            String title   = parts[0];
            String body    = parts.length > 1 ? parts[1] : "";

            long  age   = n.getAge();
            long  total = n.durationMillis;
            float alpha = computeAlpha(age, total);
            float slide = computeSlide(age, total);
            float slideOff = anim.getValue() ? (W + MARGIN + 20) * (1f - slide) : 0f;

            float x = fromRight  ? sw - MARGIN - W + slideOff : MARGIN - slideOff;
            float y = fromBottom ? sh - MARGIN - H - i * (H + GAP)
                                 : MARGIN + i * (H + GAP);

            // ── Background ───────────────────────────────────────────────────
            GL11.glEnable(GL11.GL_BLEND);
            RoundedUtils.drawRoundedRect(x, y, W, H, r, applyAlpha(bgColor, alpha));

            // ── Accent bar ───────────────────────────────────────────────────
            float textX = x + PAD_H;
            if (bar) {
                int barCol = applyAlpha(0xFF000000 | accentColor, alpha);
                GL11.glEnable(GL11.GL_BLEND);
                RoundedUtils.drawRoundedRect(x, y, 3, H, Math.min(r, 3f), barCol);
                drawRect(x + 2, y, x + 3, y + H, barCol);
                textX = x + 3 + PAD_H;
            }

            // ── Text ─────────────────────────────────────────────────────────
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            GlStateManager.enableTexture2D();
            GlStateManager.color(1f, 1f, 1f, 1f);

            int fontH = mc.fontRendererObj.FONT_HEIGHT;
            float ty  = y + (H - fontH) / 2f;

            mc.fontRendererObj.drawString(title, textX, ty,
                    applyAlpha(0xFF000000 | titleColor, alpha), false);

            int nw = mc.fontRendererObj.getStringWidth(title);
            mc.fontRendererObj.drawString(body, textX + nw + 5, ty,
                    applyAlpha(0xFF000000 | bodyColor, alpha), false);

            GlStateManager.popMatrix();
        }
    }

    private void drawRect(float x1, float y1, float x2, float y2, int color) {
        net.minecraft.client.renderer.Tessellator t = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer w = t.getWorldRenderer();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        int a = (color >> 24) & 0xFF, r = (color >> 16) & 0xFF,
            g = (color >>  8) & 0xFF, b = color & 0xFF;
        w.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
        w.pos(x1, y2, 0).color(r,g,b,a).endVertex();
        w.pos(x2, y2, 0).color(r,g,b,a).endVertex();
        w.pos(x2, y1, 0).color(r,g,b,a).endVertex();
        w.pos(x1, y1, 0).color(r,g,b,a).endVertex();
        t.draw();
        GlStateManager.enableTexture2D();
    }

    private int applyAlpha(int color, float a) {
        int orig = (color >>> 24) & 0xFF;
        return ((int)(orig * a) << 24) | (color & 0x00FFFFFF);
    }

    private int rgba(SliderSetting r, SliderSetting g, SliderSetting b, SliderSetting a) {
        return ((int)a.getValue() << 24) | ((int)r.getValue() << 16) | ((int)g.getValue() << 8) | (int)b.getValue();
    }

    private int rgb(SliderSetting r, SliderSetting g, SliderSetting b) {
        return ((int)r.getValue() << 16) | ((int)g.getValue() << 8) | (int)b.getValue();
    }

    private float computeSlide(long age, long total) {
        if (age < ANIM_IN) { float t = age / ANIM_IN; return 1f - (1f-t)*(1f-t)*(1f-t); }
        if (total > 0 && total - age < ANIM_OUT) { float t = (total - age) / ANIM_OUT; return t*t*t; }
        return 1f;
    }

    private float computeAlpha(long age, long total) {
        if (age < ANIM_IN) return Math.min(1f, age / ANIM_IN * 2f);
        if (total > 0 && total - age < ANIM_OUT) return Math.max(0f, (total - age) / ANIM_OUT);
        return 1f;
    }
}
