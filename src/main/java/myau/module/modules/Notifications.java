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

    public final SliderSetting   duration = register(new SliderSetting("Duration", 3.0, 1.0, 10.0, 0.5));
    public final DropdownSetting position = register(new DropdownSetting("Position", 0,
            "Bottom Right", "Top Right", "Bottom Left", "Top Left"));
    public final BooleanSetting  anim     = register(new BooleanSetting("Animation", true));

    // Label pill (left side — "Spirit")
    public final SliderSetting pillR = register(new SliderSetting(" Pill Red",   200,  0, 255, 1));
    public final SliderSetting pillG = register(new SliderSetting(" Pill Green",  80,  0, 255, 1));
    public final SliderSetting pillB = register(new SliderSetting(" Pill Blue",   30,  0, 255, 1));

    // Background of the whole toast
    public final SliderSetting bgR = register(new SliderSetting(" BG Red",    18,  0, 255, 1));
    public final SliderSetting bgG = register(new SliderSetting(" BG Green",  18,  0, 255, 1));
    public final SliderSetting bgB = register(new SliderSetting(" BG Blue",   18,  0, 255, 1));

    // Message text
    public final SliderSetting msgR = register(new SliderSetting(" Text Red",   220, 0, 255, 1));
    public final SliderSetting msgG = register(new SliderSetting(" Text Green", 220, 0, 255, 1));
    public final SliderSetting msgB = register(new SliderSetting(" Text Blue",  220, 0, 255, 1));

    private static final int   H         = 26;   // toast height
    private static final int   PILL_PAD  = 8;    // horizontal padding inside pill
    private static final int   MSG_PAD   = 10;   // padding between pill and message text
    private static final int   MARGIN    = 10;   // screen edge margin
    private static final int   GAP       = 5;    // between stacked toasts
    private static final float ANIM_IN   = 200f;
    private static final float ANIM_OUT  = 250f;

    // Fixed label
    private static final String LABEL = "Spirit";

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

        int fontH    = mc.fontRendererObj.FONT_HEIGHT;
        int labelW   = mc.fontRendererObj.getStringWidth(LABEL);
        int pillW    = labelW + PILL_PAD * 2;  // pill width based on text

        for (int i = 0; i < active.size(); i++) {
            NotificationManager.NotificationEntry n = active.get(i);
            String msg   = n.message; // full message e.g. "ESP enabled"
            int msgW     = mc.fontRendererObj.getStringWidth(msg);
            int toastW   = pillW + MSG_PAD + msgW + MSG_PAD; // total width

            long  age    = n.getAge();
            long  total  = n.durationMillis;
            float alpha  = computeAlpha(age, total);
            float slide  = computeSlide(age, total);
            float slideOff = anim.getValue() ? (toastW + MARGIN + 20) * (1f - slide) : 0f;

            float x = fromRight  ? sw - MARGIN - toastW + slideOff : MARGIN - slideOff;
            float y = fromBottom ? sh - MARGIN - H - i * (H + GAP)
                                 : MARGIN + i * (H + GAP);

            float r = H / 2f; // full pill radius

            // ── Full toast background (dark pill) ────────────────────────────
            int bg = rgba((int)bgR.getValue(), (int)bgG.getValue(), (int)bgB.getValue(), (int)(220 * alpha));
            GL11.glEnable(GL11.GL_BLEND);
            RoundedUtils.drawRoundedRect(x, y, toastW, H, r, bg);

            // ── Colored pill label on the left ───────────────────────────────
            int pill = rgba((int)pillR.getValue(), (int)pillG.getValue(), (int)pillB.getValue(), (int)(255 * alpha));
            GL11.glEnable(GL11.GL_BLEND);
            RoundedUtils.drawRoundedRect(x, y, pillW, H, r, pill);

            // ── Text ─────────────────────────────────────────────────────────
            GlStateManager.pushMatrix();
            GL11.glEnable(GL11.GL_BLEND);
            GlStateManager.blendFunc(770, 771);
            GlStateManager.enableTexture2D();
            GlStateManager.color(1f, 1f, 1f, 1f);

            float ty = y + (H - fontH) / 2f;

            // Label text (white, centered in pill)
            int labelCol = rgba(255, 255, 255, (int)(255 * alpha));
            mc.fontRendererObj.drawString(LABEL,
                    x + (pillW - labelW) / 2f, ty, labelCol, false);

            // Message text
            int msgCol = rgba((int)msgR.getValue(), (int)msgG.getValue(), (int)msgB.getValue(), (int)(255 * alpha));
            mc.fontRendererObj.drawString(msg,
                    x + pillW + MSG_PAD, ty, msgCol, false);

            GlStateManager.popMatrix();
        }
    }

    private int rgba(int r, int g, int b, int a) {
        a = Math.max(0, Math.min(255, a));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private float computeSlide(long age, long total) {
        if (age < ANIM_IN) {
            float t = age / ANIM_IN;
            return 1f - (1f - t) * (1f - t) * (1f - t);
        }
        if (total > 0 && total - age < ANIM_OUT) {
            float t = (total - age) / ANIM_OUT;
            return t * t * t;
        }
        return 1f;
    }

    private float computeAlpha(long age, long total) {
        if (age < ANIM_IN) return Math.min(1f, age / ANIM_IN * 2f);
        if (total > 0 && total - age < ANIM_OUT) return Math.max(0f, (total - age) / ANIM_OUT);
        return 1f;
    }
}
