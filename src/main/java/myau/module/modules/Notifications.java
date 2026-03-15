package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.management.NotificationManager;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class Notifications extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting   duration = register(new SliderSetting("Duration",  3.0, 1.0, 10.0, 0.5));
    public final DropdownSetting position = register(new DropdownSetting("Position", 0,
            "Bottom Right", "Top Right", "Bottom Left", "Top Left"));
    public final BooleanSetting  anim     = register(new BooleanSetting("Animation", true));

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int   H          = 28;   // card height
    private static final int   ACCENT_W   = 3;    // left color stripe width
    private static final int   PAD_LEFT   = 8;    // padding after accent stripe
    private static final int   PAD_RIGHT  = 12;   // right padding
    private static final int   MARGIN     = 12;   // screen edge margin
    private static final int   GAP        = 5;    // gap between notifications
    private static final float CORNER_R   = 5f;
    private static final float ANIM_IN    = 220f;
    private static final float ANIM_OUT   = 280f;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int BG      = 0xEE0D0D0D; // near-black background
    private static final int PINK    = 0xFFE991B8; // default accent (Spirit pink)
    private static final int WHITE   = 0xFFEEEEFF;
    private static final int DIM     = 0xFF888899;

    public Notifications() { super("Notifications", true); }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.thePlayer == null || Myau.notificationManager == null) return;
        List<NotificationManager.NotificationEntry> active = Myau.notificationManager.getActive();
        if (active.isEmpty()) return;

        ScaledResolution sr  = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
        int pos         = position.getIndex();
        boolean right   = pos == 0 || pos == 1;
        boolean bottom  = pos == 0 || pos == 2;

        for (int i = 0; i < active.size(); i++) {
            NotificationManager.NotificationEntry n = active.get(i);

            long  age   = n.getAge();
            long  total = n.durationMillis;
            float alpha = computeAlpha(age, total);
            float slide = computeSlide(age, total);

            // Card width based on message
            int msgW   = mc.fontRendererObj.getStringWidth(n.message);
            int cardW  = ACCENT_W + PAD_LEFT + msgW + PAD_RIGHT;
            int minW   = 120;
            if (cardW < minW) cardW = minW;

            float slideOff = anim.getValue() ? (cardW + MARGIN + 20) * (1f - slide) : 0f;
            float x = right  ? sw - MARGIN - cardW + slideOff : MARGIN - slideOff;
            float y = bottom ? sh - MARGIN - H - i * (H + GAP)
                             : MARGIN + i * (H + GAP);

            // Accent color — use notification's own color if set, else Spirit pink
            int accentRaw = n.color == 0xFFFFFF ? PINK : (0xFF000000 | n.color);
            int accent    = withAlpha(accentRaw, alpha);
            int bg        = withAlpha(BG, alpha);
            int textCol   = withAlpha(WHITE, alpha);
            int dimCol    = withAlpha(DIM, alpha);

            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0);

            // ── Shadow ────────────────────────────────────────────────────────
            solidRect(-2, -2, cardW + 4, H + 4, withAlpha(0xFF000000, alpha * 0.3f));

            // ── Card background ───────────────────────────────────────────────
            roundedRect(0, 0, cardW, H, CORNER_R, bg);

            // ── Accent left stripe ─────────────────────────────────────────────
            // Draw as a rounded rect on the left, clipped to card
            roundedRect(0, 0, ACCENT_W + CORNER_R, H, CORNER_R, accent); // fills corners
            solidRect(ACCENT_W, 0, CORNER_R, H, bg);                      // square off right side

            // ── Progress bar at bottom — shrinks as notification expires ──────
            float progress = total > 0 ? Math.max(0f, 1f - (float) age / total) : 1f;
            int barW = (int)((cardW - ACCENT_W) * progress);
            if (barW > 0) {
                solidRect(ACCENT_W, H - 2, barW, 2, withAlpha(accent, alpha * 0.5f));
            }

            // ── Thin top highlight line ───────────────────────────────────────
            solidRect(ACCENT_W, 0, cardW - ACCENT_W, 1, withAlpha(0xFFFFFFFF, alpha * 0.06f));

            // ── Text ──────────────────────────────────────────────────────────
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableDepth();

            int fontH = mc.fontRendererObj.FONT_HEIGHT;
            int ty    = (H - fontH) / 2;

            mc.fontRendererObj.drawString(n.message, ACCENT_W + PAD_LEFT, ty, textCol, true);

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GL11.glColor4f(1, 1, 1, 1);

            GlStateManager.popMatrix();
        }
    }

    // ── GL helpers ────────────────────────────────────────────────────────────

    private void solidRect(float x, float y, float w, float h, int color) {
        float a = (color >> 24 & 0xFF) / 255f;
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >> 8  & 0xFF) / 255f;
        float b = (color       & 0xFF) / 255f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x,     y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x,     y + h);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void roundedRect(float x, float y, float w, float h, float r, int color) {
        float a = (color >> 24 & 0xFF) / 255f;
        float rf = (color >> 16 & 0xFF) / 255f;
        float gf = (color >> 8  & 0xFF) / 255f;
        float bf = (color       & 0xFF) / 255f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(rf, gf, bf, a);

        // Center strips
        quad(x + r, y,     x + w - r, y + h);
        quad(x,     y + r, x + r,     y + h - r);
        quad(x + w - r, y + r, x + w, y + h - r);

        // Corners
        arc(x + r,     y + r,     r, 180, 270);
        arc(x + w - r, y + r,     r, 270, 360);
        arc(x + r,     y + h - r, r,  90, 180);
        arc(x + w - r, y + h - r, r,   0,  90);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void quad(float x1, float y1, float x2, float y2) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y1); GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x2, y2); GL11.glVertex2f(x1, y2);
        GL11.glEnd();
    }

    private void arc(float cx, float cy, float r, int start, int end) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int d = start; d <= end; d += 4) {
            double rad = Math.toRadians(d);
            GL11.glVertex2f(cx + (float) Math.cos(rad) * r, cy + (float) Math.sin(rad) * r);
        }
        GL11.glEnd();
    }

    // ── Color helpers ─────────────────────────────────────────────────────────

    private int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, (int)(((color >> 24) & 0xFF) * alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    // ── Animation ─────────────────────────────────────────────────────────────

    private float computeSlide(long age, long total) {
        if (age < ANIM_IN) {
            float t = age / ANIM_IN;
            return 1f - (1f - t) * (1f - t) * (1f - t); // ease-out cubic
        }
        if (total > 0 && total - age < ANIM_OUT) {
            float t = (total - age) / ANIM_OUT;
            return t * t * t; // ease-in cubic
        }
        return 1f;
    }

    private float computeAlpha(long age, long total) {
        if (age < ANIM_IN)  return Math.min(1f, age / ANIM_IN * 1.5f);
        if (total > 0 && total - age < ANIM_OUT) return Math.max(0f, (total - age) / ANIM_OUT);
        return 1f;
    }
}
