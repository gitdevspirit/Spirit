package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.property.properties.TextProperty;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;

public class Spammer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();
    private int charOffset = 19968;

    // TextProperty has no SliderSetting equivalent — keep as-is
    public final TextProperty  text   = new TextProperty("text", "meow");
    public final SliderSetting delay  = register(new SliderSetting("Delay (s)",   3.5, 0.0, 3600.0, 0.5));
    public final SliderSetting random = register(new SliderSetting("Random Chars", 0,   0,   10,     1));

    public Spammer() {
        super("Spammer", false);
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!isEnabled()) return;
        if (timer.hasTimeElapsed((long) (delay.getValue() * 1000.0))) {
            timer.reset();
            String msg = text.getValue();
            if ((int) random.getValue() > 0) {
                msg = msg + " ";
                for (int i = 0; i < (int) random.getValue(); i++) {
                    msg = msg + (char) charOffset;
                    charOffset++;
                    if (charOffset > 40959) charOffset = 19968;
                }
            }
            mc.thePlayer.sendChatMessage(msg);
        }
    }
}