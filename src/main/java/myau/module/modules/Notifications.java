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

    public final SliderSetting   duration   = register(new SliderSetting("Duration",      3.0,  1.0,  8.0, 0.5));
    public final SliderSetting   scale      = register(new SliderSetting("Scale",          1.0,  0.5,  2.0, 0.05));
    public final DropdownSetting anchorX    = register(new DropdownSetting("Anchor X",      2, "Left", "Center", "Right"));
    public final DropdownSetting anchorY    = register(new DropdownSetting("Anchor Y",      2, "Top", "Center", "Bottom"));
    public final SliderSetting   offsetX    = register(new SliderSetting("X Offset",        0, -500, 500, 1));
    public final SliderSetting   offsetY    = register(new SliderSetting("Y Offset",        0, -500, 500, 1));
    public final BooleanSetting  slideAnim  = register(new BooleanSetting("Slide Anim",  true));

    // Pill label color (defaults white)
    public final SliderSetting   pillR      = register(new SliderSetting("Pill Text R",  255, 0, 255, 1));
    public final SliderSetting   pillG      = register(new SliderSetting("Pill Text G",  255, 0, 255, 1));
    public final SliderSetting   pillB      = register(new SliderSetting("Pill Text B",  255, 0, 255, 1));

    // Message text color (defaults pink)
    public final SliderSetting   msgR       = register(new SliderSetting("Message R",    233, 0, 255, 1));
    public final SliderSetting   msgG       = register(new SliderSetting("Message G",    145, 0, 255, 1));
    public final SliderSetting   msgB       = register(new SliderSetting("Message B",    184, 0, 255, 1));

    private static final int PILL_ON  = 0xFFE991B8;
    private static final int PILL_OFF = 0xFF555555;
    private static final int BG       = 0xCC18181E;
    private static final int MSG_OFF  = 0xFF888888;

    private static final float ANIM_IN  = 180f;
    private static final float ANIM_OUT = 250f;
    // Base dimensions at scale=1 (native font pixels)
    private static final int BASE_PAD_V   = 4;  // vertical padding inside notif
    private static final int BASE_PAD_H   = 6;  // horizontal outer padding
    private static final int BASE_PILL_PAD = 5; // horizontal padding inside pill
    private static final int BASE_PILL_GAP = 7; // gap between pill and message
    private static final int BASE_PILL_INSET = 3; // pill inset from notif edge
    private static final int GAP = 4; // gap between stacked notifications (screen px)

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

        float sf    = (float) scale.getValue();
        int   fontH = mc.fontRendererObj.FONT_HEIGHT; // 9 native px

        // All dimensions in scaled screen pixels
        int notifH   = (int)((fontH + BASE_PAD_V * 2) * sf);
        int pillInset = (int)(BASE_PILL_INSET * sf);
        int pillPad   = (int)(BASE_PILL_PAD * sf);
        int pillGap   = (int)(BASE_PILL_GAP * sf);
        int outerPadH = (int)(BASE_PAD_H * sf);

        // Build colors from settings
        int pillTextColor = 0xFF000000
                | ((int) pillR.getValue() << 16)
                | ((int) pillG.getValue() << 8)
                | (int) pillB.getValue();
        int msgOnColor = 0xFF000000
                | ((int) msgR.getValue() << 16)
                | ((int) msgG.getValue() << 8)
                | (int) msgB.getValue();

        // Total stack height for centering
        int totalH = active.size() * notifH + Math.max(0, active.size() - 1) * GAP;

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

        boolean stackUp   = anchorY.getIndex() == 2;
        boolean fromRight = anchorX.getIndex() != 0;

        for (int i = 0; i < active.size(); i++) {
            NotificationManager.NotificationEntry n = active.get(i);
            boolean on = !n.message.contains("untoggled");

            String[] parts   = n.message.split(" ");
            String pillText  = parts.length > 0 ? parts[0].toUpperCase() : n.message.toUpperCase();
            String msgText   = on ? "enabled" : "disabled";

            // Measure text at native font size, then scale
            int pillTextNativeW = mc.fontRendererObj.getStringWidth(pillText);
            int msgTextNativeW  = mc.fontRendererObj.getStringWidth(msgText);

            int pillW  = (int)(pillTextNativeW * sf) + pillPad * 2;
            int notifW = pillInset + pillW + pillGap + (int)(msgTextNativeW * sf) + outerPadH;

            // Y position
            float ny = stackUp
                    ? baseY - notifH - i * (notifH + GAP)
                    : baseY + i * (notifH + GAP);

            // X position
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
                    float t = 1f - (float) Math.pow(1.0 - age / ANIM_IN, 2);
                    slideOff = (notifW + 20) * (1f - t);
                } else if (total > 0 && total - age < ANIM_OUT) {
                    float t = (float) Math.pow((total - age) / ANIM_OUT, 2);
                    slideOff = (notifW + 20) * (1f - t);
                }
                nx += fromRight ? slideOff : -slideOff;
            }

            // ── Background ───────────────────────────────────────────────────
            RoundedUtils.drawRoundedRect(nx, ny, notifW, notifH, notifH / 2f, BG);

            // ── Pill ─────────────────────────────────────────────────────────
            float pillX = nx + pillInset;
            float pillY = ny + pillInset;
            float pillH = notifH - pillInset * 2;
            RoundedUtils.drawRoundedRect(pillX, pillY, pillW, pillH, pillH / 2f,
                    on ? PILL_ON : PILL_OFF);

            // ── Text (scaled via matrix) ──────────────────────────────────────
            GlStateManager.pushMatrix();
            GlStateManager.scale(sf, sf, 1f);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            GlStateManager.enableTexture2D();
            GlStateManager.color(1f, 1f, 1f, 1f);

            // Text Y: center vertically — compute in native coords
            float textY = (ny + (notifH - fontH * sf) / 2f) / sf;

            // Pill label
            float pillLabelX = (pillX + pillPad) / sf;
            mc.fontRendererObj.drawString(pillText, pillLabelX, textY, pillTextColor, false);

            // Message
            float msgX = (pillX + pillW + pillGap) / sf;
            mc.fontRendererObj.drawString(msgText, msgX, textY, on ? msgOnColor : MSG_OFF, false);

            GlStateManager.popMatrix();
        }
    }
}
