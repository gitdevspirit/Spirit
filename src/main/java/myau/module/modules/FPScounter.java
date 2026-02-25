package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.render.BlurShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.*;

public class FPScounter extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanSetting  enabled         = new BooleanSetting("Enabled",          true);
    public final DropdownSetting posX            = new DropdownSetting("Position X",       1, "Left", "Center", "Right");
    public final DropdownSetting posY            = new DropdownSetting("Position Y",       1, "Top", "Center", "Bottom");
    public final SliderSetting   offsetX         = new SliderSetting("X Offset",           0, -200, 200, 1);
    public final SliderSetting   offsetY         = new SliderSetting("Y Offset",           0, -200, 200, 1);
    public final SliderSetting   scale           = new SliderSetting("Scale",            1.0,  0.6,  2.0, 0.05);
    public final SliderSetting   blurStrength    = new SliderSetting("Blur Strength",      6,    1,   10,    1);
    public final SliderSetting   cornerRadius    = new SliderSetting("Corner Radius",      8,    5,   20,    1);
    public final SliderSetting   backgroundAlpha = new SliderSetting("Background Alpha", 160,    0,  255,    1);
    // ColorProperty has no new-system equivalent; use R/G/B sliders
    public final SliderSetting   textColorR      = new SliderSetting("Text R",           255,    0,  255,    1);
    public final SliderSetting   textColorG      = new SliderSetting("Text G",           255,    0,  255,    1);
    public final SliderSetting   textColorB      = new SliderSetting("Text B",           255,    0,  255,    1);

    public FPScounter() {
        super("Fpscounter", false, false);
        register(enabled);
        register(posX);
        register(posY);
        register(offsetX);
        register(offsetY);
        register(scale);
        register(blurStrength);
        register(cornerRadius);
        register(backgroundAlpha);
        register(textColorR);
        register(textColorG);
        register(textColorB);
    }

    @EventTarget
    public void onRender2DEvent(Render2DEvent event) {
        if (!enabled.getValue() || !isEnabled()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        float scaleFactor = (float) scale.getValue();
        float baseX = 0.0f, baseY = 0.0f;

        switch (posX.getIndex()) {
            case 0: baseX = 10.0f; break;
            case 1: baseX = sr.getScaledWidth() / 2.0f; break;
            case 2: baseX = sr.getScaledWidth() - 10; break;
        }
        switch (posY.getIndex()) {
            case 0: baseY = 10.0f; break;
            case 1: baseY = sr.getScaledHeight() / 2.0f; break;
            case 2: baseY = sr.getScaledHeight() - 10; break;
        }
        baseX += (int) offsetX.getValue();
        baseY += (int) offsetY.getValue();

        int fps = Minecraft.getDebugFPS();
        String text = "FPS " + fps;
        int textWidth  = mc.fontRendererObj.getStringWidth(text);
        int textHeight = mc.fontRendererObj.FONT_HEIGHT;
        float w = textWidth + 12;
        float h = textHeight + 6;

        GlStateManager.pushMatrix();
        GlStateManager.scale(scaleFactor, scaleFactor, 1.0f);
        float drawX = baseX / scaleFactor;
        float drawY = baseY / scaleFactor;

        BlurShadowRenderer.renderFrostedGlass(
                drawX - w / 2.0f, drawY - h / 2.0f, w, h,
                (float)(int) cornerRadius.getValue(),
                (int) blurStrength.getValue(),
                (int) backgroundAlpha.getValue());

        int color = new Color((int) textColorR.getValue(), (int) textColorG.getValue(), (int) textColorB.getValue()).getRGB();
        mc.fontRendererObj.drawString(text, (int)(drawX - textWidth / 2.0f), (int)(drawY - textHeight / 2.0f), color);
        GlStateManager.popMatrix();
    }
}