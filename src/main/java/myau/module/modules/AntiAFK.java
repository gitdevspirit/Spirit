package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorKeyBinding;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

public class AntiAFK extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int lastInput;

    public final SliderSetting idleSeconds = register(new SliderSetting("Idle Time", 10, 5, 120, 1));
    public final DropdownSetting mode = register(new DropdownSetting("Mode", 0, "STRAFE", "JUMP", "BOTH", "RANDOM"));

    public AntiAFK() {
        super("AntiAFK", false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event){
        if(event.getType() == EventType.PRE && this.isEnabled()){
            GameSettings gameSettings = mc.gameSettings;
            if (gameSettings.keyBindJump.isPressed() || gameSettings.keyBindRight.isPressed() || gameSettings.keyBindForward.isPressed() || gameSettings.keyBindLeft.isPressed() || gameSettings.keyBindBack.isPressed()) {
                lastInput = 0;
            }
            lastInput++;
            if (lastInput < 20 * (int) idleSeconds.getValue()) return;
            if (mc.thePlayer.ticksExisted % 5 == 0) {
                ((IAccessorKeyBinding)mc.gameSettings.keyBindRight).setPressed(false);
                ((IAccessorKeyBinding)mc.gameSettings.keyBindLeft).setPressed(false);
                ((IAccessorKeyBinding)mc.gameSettings.keyBindJump).setPressed(false);
            }
            int m = mode.getIndex();
            boolean doStrafe = (m == 0 || m == 2 || (m == 3 && mc.thePlayer.ticksExisted % 2 == 0));
            boolean doJump  = (m == 1 || m == 2 || (m == 3 && mc.thePlayer.ticksExisted % 5 == 0));
            if (doStrafe && mc.thePlayer.ticksExisted % 20 == 0) {
                if (mc.thePlayer.ticksExisted % 40 == 0) {
                    ((IAccessorKeyBinding)mc.gameSettings.keyBindRight).setPressed(true);
                } else {
                    ((IAccessorKeyBinding)mc.gameSettings.keyBindLeft).setPressed(true);
                }
            }
            if (doJump && mc.thePlayer.ticksExisted % 100 == 0) {
                ((IAccessorKeyBinding)mc.gameSettings.keyBindJump).setPressed(true);
            }
        }
    }
}
