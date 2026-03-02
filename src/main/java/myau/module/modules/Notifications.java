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

import java.util.List;

public class Notifications extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting   duration  = register(new SliderSetting("Duration",       3.0,  1.0,  8.0, 0.5));
    public final SliderSetting   scale     = register(new SliderSetting("Scale",           1.0,  0.5,  2.0, 0.05));
    public final DropdownSetting anchorX   = register(new DropdownSetting("Anchor X",       2,  "Left", "Center", "Right"));
    public final DropdownSetting anchorY   = register(new DropdownSetting("Anchor Y",       2,  "Top", "Center", "Bottom"));
    public final SliderSetting   offsetX   = register(new SliderSetting("X Offset",         0, -500,  500,  1));
    public final SliderSetting   offsetY   = register(new SliderSetting("Y Offset",         0, -500,  500,  1));
    public final BooleanSetting  slideAnim = register(new BooleanSetting("Slide Anim",   true));

    private static final int PILL_ON   = 0xFFE991B8;
    private static final int PILL_OFF  = 0xFF555555;
    private static final int PILL_TEXT = 0xFF1A0D12;
    private static final int BG        = 0xCC18181E;
    private static final int MSG_ON    = 0xFFE991B8;
    private static final int MSG_OFF   = 0xFF888888;

    private static final float ANIM_IN  = 180f;
    private static final float ANIM_OUT = 250f;
    private static final int   GAP      = 5;

    public Notifications() { super("Notifications", true); }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.thePlayer == null) return;
        if (Myau.notificationManager == null) return;

        List<NotificationManager.NotificationEntry> active = Myau.notificationManager.getActive();
        if (active.isEmpty()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();

        float sf     = (float) scale.getValue();
        int   fontH  = mc.fontRendererObj.FONT_HEIGHT;
        int   padV   = (int)(3 * sf);
        int   padH   = (int)(6 * sf);
        int   notifH = (int)((fontH + padV * 2) * sf);
        int   pill2pad = (int)(5 * sf);

        // Measure total stack height for centering
        int totalH = active.size() * notifH + Math.max(0, active.size() - 1) * GAP;

        // Base anchor
        float baseX, baseY;
        switch (anchorX.getIndex()) {
            case 0:  baseX = 10f; break;
            case 1:  baseX = sw / 2f; break;
            default: baseX = sw - 10f; break;
        }
        switch (anchorY.getIndex()) {
            case 0:  baseY = 10f; break;
            case 1:  baseY = sh / 2f - totalH / 2f; break;
            default: baseY = sh - 10f; break;
        }
        baseX += (float) offsetX.getValue();
        baseY += (float) offsetY.getValue();

        boolean stackUp = anchorY.getIndex() == 2;
        boolean fromRight = anchorX.getIndex() == 2;

        for (int i = 0; i < active.size(); i++) {
            NotificationManager.NotificationEntry n = active.get(i);
            boolean on = !n.message.contains("untoggled");

            String[] parts = n.message.split(" ");
            String pillText = parts.length > 0 ? parts[0].toUpperCase() : n.message.toUpperCase();
            String msgText  = on ? "enabled" : "disabled";

            // Widths
            int pillTextW = (int)(mc.fontRendererObj.getStringWidth(pillText) * sf);
            int pillW     = pillTextW + pill2pad * 2;
            int msgW      = (int)(mc.fontRendererObj.getStringWidth(msgText) * sf);
            int notifW    = (int)(4 * sf) + pillW + (int)(8 * sf) + msgW + padH;

            // Y per notification
            float ny;
            if (stackUp) {
                ny = baseY - notifH - i * (notifH + GAP);
            } else {
                ny = baseY + i * (notifH + GAP);
            }

            // X (right-anchored by default)
            float nx;
            switch (anchorX.getIndex()) {
                case 0:  nx = baseX; break;
                case 1:  nx = baseX - notifW / 2f; break;
                default: nx = baseX - notifW; break;
            }

            // Slide animation
            if (slideAnim.getValue()) {
                long age   = n.getAge();
                long total = n.durationMillis;
                float slideOff = 0f;
                if (age < ANIM_IN) {
                    float t = 1f - (float)Math.pow(1f - age / ANIM_IN, 2);
                    slideOff = (notifW + 20) * (1f - t);
                } else if (total > 0 && total - age < ANIM_OUT) {
                    float t = (float)Math.pow((total - age) / ANIM_OUT, 2);
                    slideOff = (notifW + 20) * (1f - t);
                }
                nx += fromRight ? slideOff : -slideOff;
            }

            float textY = ny + (notifH - fontH * sf) / 2f;

            // Background
            RoundedUtils.drawRoundedRect(nx, ny, notifW, notifH, notifH / 2f, BG);

            // Pill
            float pillX = nx + (int)(4 * sf);
            float pillY = ny + (int)(3 * sf);
            float pillH = notifH - (int)(6 * sf);
            RoundedUtils.drawRoundedRect(pillX, pillY, pillW, pillH, pillH / 2f,
                    on ? PILL_ON : PILL_OFF);

            // Text
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            GlStateManager.enableTexture2D();
            GlStateManager.color(1f, 1f, 1f, 1f);

            // Pill label
            mc.fontRendererObj.drawString(pillText,
                    pillX + pill2pad, textY, PILL_TEXT, false);

            // Message
            mc.fontRendererObj.drawString(msgText,
                    pillX + pillW + (int)(8 * sf), textY,
                    on ? MSG_ON : MSG_OFF, false);
        }
    }
}
