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
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.*;
import java.util.List;

public class Notifications extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting   duration  = register(new SliderSetting("Duration",  3.0, 1.0, 8.0, 0.5));
    public final DropdownSetting position  = register(new DropdownSetting("Position", 0,
            "Bottom Right", "Top Right", "Bottom Left", "Top Left"));
    public final BooleanSetting  slideAnim = register(new BooleanSetting("Slide Anim", true));
    public final SliderSetting   toastW    = register(new SliderSetting("Width", 155, 80, 280, 4));

    private static final int   BG_COLOR  = 0xEE0D0D12;
    private static final int   TOAST_H   = 24;
    private static final int   ACCENT_W  = 3;
    private static final int   PAD_H     = 7;
    private static final int   MARGIN    = 8;
    private static final int   GAP       = 4;
    private static final float RADIUS    = 4f;

    private static final float ANIM_IN   = 160f;
    private static final float ANIM_OUT  = 220f;

    public Notifications() { super("Notifications", true); }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.thePlayer == null || Myau.notificationManager == null) return;

        List<NotificationManager.NotificationEntry> active = Myau.notificationManager.getActive();
        if (active.isEmpty()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
        int W  = (int) toastW.getValue();
        int pos = position.getIndex();

        boolean fromRight  = pos == 0 || pos == 1;
        boolean fromBottom = pos == 0 || pos == 2;

        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        int accent = (hud != null ? hud.getColor(System.currentTimeMillis()) : new Color(0xE991B8)).getRGB();

        for (int i = 0; i < active.size(); i++) {
            NotificationManager.NotificationEntry n = active.get(i);

            String[] parts = n.message.split(" ", 2);
            String name    = parts[0];
            String body    = parts.length > 1 ? parts[1] : "";
            boolean isOn   = !body.toLowerCase().contains("disabled")
                          && !body.toLowerCase().contains("untoggled")
                          && !body.toLowerCase().contains("off");

            long age   = n.getAge();
            long total = n.durationMillis;

            float slide = computeSlide(age, total);
            float alpha = computeAlpha(age, total);
            float slideOff = slideAnim.getValue() ? (W + MARGIN + 10) * (1f - slide) : 0f;

            float x = fromRight  ? sw - MARGIN - W + slideOff : MARGIN - slideOff;
            float y = fromBottom ? sh - MARGIN - TOAST_H - i * (TOAST_H + GAP)
                                 : MARGIN + i * (TOAST_H + GAP);

            renderToast(x, y, W, name, body, isOn, accent, alpha);
        }
    }

    private void renderToast(float x, float y, int w,
                             String name, String body,
                             boolean isOn, int accent, float alpha) {

        int bgA = applyAlpha(BG_COLOR, alpha);

        // ── Background ───────────────────────────────────────────────────────
        RoundedUtils.drawRoundedRect(x, y, w, TOAST_H, RADIUS, bgA);

        // ── Left accent bar: rounded rect for the caps, plain rect to fill middle ─
        int barCol = isOn
                ? applyAlpha(accent | 0xFF000000, alpha)
                : applyAlpha((accent & 0x00FFFFFF) | 0x44000000, alpha);

        // Draw full-radius pill then paint background over the right overhang
        RoundedUtils.drawRoundedRect(x, y, ACCENT_W + RADIUS * 2, TOAST_H, RADIUS, barCol);
        // Fill the right half of that pill shape back with bg to get a flat right edge
        Gui.drawRect((int)(x + ACCENT_W), (int)y,
                     (int)(x + ACCENT_W + (int)(RADIUS * 2)), (int)(y + TOAST_H), bgA);
        // Now the accent bar is exactly ACCENT_W wide with left-rounded, right-flat edge

        // ── Subtle border ────────────────────────────────────────────────────
        int borderCol = applyAlpha((accent & 0x00FFFFFF) | 0x20000000, alpha);
        RoundedUtils.drawRoundedOutline(x, y, w, TOAST_H, RADIUS, 0.75f, borderCol);

        // ── Text ─────────────────────────────────────────────────────────────
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);

        int fontH  = mc.fontRendererObj.FONT_HEIGHT;
        float textY = y + (TOAST_H - fontH) / 2f;
        float textX = x + ACCENT_W + PAD_H;

        // Module name: accent when enabled, muted when disabled
        int nameCol = applyAlpha(isOn ? accent | 0xFF000000 : 0xFFAAAAAA, alpha);
        mc.fontRendererObj.drawString(name, textX, textY, nameCol, false);

        // Body text: softer, follows after a small gap
        int nameW   = mc.fontRendererObj.getStringWidth(name);
        int bodyCol = applyAlpha(isOn ? 0xFF888888 : 0xFF555555, alpha);
        mc.fontRendererObj.drawString(body, textX + nameW + 4, textY, bodyCol, false);

        GlStateManager.popMatrix();
    }

    private int applyAlpha(int color, float alpha) {
        int origA = (color >>> 24) & 0xFF;
        int newA  = (int)(origA * alpha);
        return (newA << 24) | (color & 0x00FFFFFF);
    }

    private float computeSlide(long age, long total) {
        if (age < ANIM_IN) {
            return easeOutQuart(age / ANIM_IN);
        } else if (total > 0 && total - age < ANIM_OUT) {
            return easeInQuart((total - age) / ANIM_OUT);
        }
        return 1f;
    }

    private float computeAlpha(long age, long total) {
        if (age < ANIM_IN) return Math.min(1f, age / ANIM_IN * 2f);
        if (total > 0 && total - age < ANIM_OUT) return Math.max(0f, (total - age) / ANIM_OUT);
        return 1f;
    }

    private float easeOutQuart(float t) { float r = 1f-t; return 1f - r*r*r*r; }
    private float easeInQuart(float t)  { return t*t*t*t; }
}
