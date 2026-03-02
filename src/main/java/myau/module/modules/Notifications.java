package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.management.NotificationManager;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.ui.clickgui.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import java.util.List;

public class Notifications extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting duration = register(new SliderSetting("Duration",    3.0, 1.0, 8.0, 0.5));
    public final SliderSetting width    = register(new SliderSetting("Width",       160,  80, 300,   5));
    public final SliderSetting corner   = register(new SliderSetting("Corner",        5,   2,  12,   1));

    private static final int ACCENT     = 0xFFE991B8;
    private static final int BG         = 0xDD1A1A1F;
    private static final int BAR_ON     = 0xFFE991B8;
    private static final int BAR_OFF    = 0xFF666666;
    private static final int PAD        = 8;
    private static final int NOTIF_H    = 36;
    private static final int GAP        = 6;
    private static final float ANIM_IN  = 200f;  // ms to slide in
    private static final float ANIM_OUT = 300f;  // ms to slide out before expiry

    public Notifications() { super("Notifications", true); }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.thePlayer == null) return;
        if (Myau.notificationManager == null) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();

        int notifW = (int) width.getValue();
        float durationMs = (float)(duration.getValue() * 1000.0);

        List<NotificationManager.NotificationEntry> active = Myau.notificationManager.getActive();

        // Draw from bottom up
        int baseY = sh - 10;

        for (int i = active.size() - 1; i >= 0; i--) {
            NotificationManager.NotificationEntry n = active.get(i);

            long age   = n.getAge();
            long total = n.durationMillis > 0 ? n.durationMillis : (long) durationMs;

            // Slide-in / slide-out X offset
            float slideX;
            if (age < ANIM_IN) {
                // sliding in from right
                float t = age / ANIM_IN;
                t = 1f - (1f - t) * (1f - t); // ease out
                slideX = notifW * (1f - t);
            } else if (total - age < ANIM_OUT) {
                // sliding out to right
                float t = (total - age) / ANIM_OUT;
                t = t * t; // ease in
                slideX = notifW * (1f - t);
            } else {
                slideX = 0f;
            }

            int x = (int)(sw - notifW - 10 + slideX);
            int y = baseY - NOTIF_H;

            float cr = (float)(int) corner.getValue();

            // Background
            RoundedUtils.drawRoundedRect(x, y, notifW, NOTIF_H, cr, BG);

            // Left accent bar (pink if enabled message, grey if disabled)
            boolean wasEnabled = !n.message.contains("untoggled");
            int barColor = wasEnabled ? BAR_ON : BAR_OFF;
            RoundedUtils.drawRoundedRect(x, y, 3, NOTIF_H, cr, barColor);

            // Progress bar at bottom
            float progress = Math.max(0f, 1f - (float) age / total);
            int barW = (int)((notifW - 6) * progress);
            if (barW > 0) {
                RoundedUtils.drawRoundedRect(x + 3, y + NOTIF_H - 3, barW, 3, 1, barColor);
            }

            // Title: module name
            String[] parts = n.message.split(" ");
            String moduleName = parts.length > 0 ? parts[0] : n.message;
            String statusText = wasEnabled ? "enabled" : "disabled";

            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableTexture2D();

            // Module name in accent/grey
            int nameColor = wasEnabled ? ACCENT : 0xFF888888;
            mc.fontRendererObj.drawString(moduleName, x + PAD, y + 8, nameColor, true);

            // Status line in lighter grey
            mc.fontRendererObj.drawString(statusText, x + PAD, y + 20, 0xFF555555, true);

            baseY -= NOTIF_H + GAP;
        }
    }
}
