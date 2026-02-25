package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.StrafeEvent;
import myau.events.UpdateEvent;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.KeyBindUtil;
import myau.util.MoveUtil;
import net.minecraft.client.Minecraft;

public class Fly extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private double verticalMotion = 0.0;
    public final DropdownSetting mode  = register(new DropdownSetting("Mode", 0, "VANILLA", "CREATIVE", "GLIDE"));
    public final SliderSetting   hSpeed = register(new SliderSetting("H Speed", 1.0, 0.1, 10.0, 0.1));
    public final SliderSetting   vSpeed = register(new SliderSetting("V Speed", 1.0, 0.1, 10.0, 0.1));
    public final SliderSetting   glideSpeed = register(new SliderSetting("Glide Fall", 0.1, 0.01, 1.0, 0.01));

    public Fly() {
        super("Fly", false);
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled()) {
            if (mode.getIndex() == 2) {  // GLIDE
                mc.thePlayer.motionY = -glideSpeed.getValue() * 0.42;
            } else if (mc.thePlayer.posY % 1.0 != 0.0) {
                mc.thePlayer.motionY = this.verticalMotion;
            }
            MoveUtil.setSpeed(0.0);
            event.setFriction((float) MoveUtil.getBaseMoveSpeed() * (float) this.hSpeed.getValue());
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            this.verticalMotion = 0.0;
            if (mode.getIndex() == 1) mc.thePlayer.capabilities.isFlying = true;  // CREATIVE
            if (mc.currentScreen == null && mode.getIndex() != 2) {
                if (KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
                    this.verticalMotion = this.verticalMotion + this.vSpeed.getValue() * 0.42F;
                }
                if (KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
                    this.verticalMotion = this.verticalMotion - this.vSpeed.getValue() * 0.42F;
                }
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            }
        }
    }

    @Override
    public void onDisabled() {
        mc.thePlayer.capabilities.isFlying = false;
        mc.thePlayer.motionY = 0.0;
        MoveUtil.setSpeed(0.0);
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
    }
}
