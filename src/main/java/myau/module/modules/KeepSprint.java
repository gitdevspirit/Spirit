package myau.module.modules;

import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;

public class KeepSprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting  slowdown   = new SliderSetting("Slowdown",    0,  0, 100, 1);
    public final BooleanSetting groundOnly = new BooleanSetting("Ground Only", false);
    public final BooleanSetting reachOnly  = new BooleanSetting("Reach Only",  false);

    public KeepSprint() {
        super("KeepSprint", false);
        register(slowdown);
        register(groundOnly);
        register(reachOnly);
    }

    public boolean shouldKeepSprint() {
        if (groundOnly.getValue() && !mc.thePlayer.onGround) return false;
        return !reachOnly.getValue()
                || mc.objectMouseOver.hitVec.distanceTo(mc.getRenderViewEntity().getPositionEyes(1.0F)) > 3.0;
    }
}