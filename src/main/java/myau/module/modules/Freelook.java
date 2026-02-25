package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class Freelook extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final DropdownSetting mode        = new DropdownSetting("Mode",         0, "Hold", "Toggle");
    public final DropdownSetting viewMode    = new DropdownSetting("View",         0, "First Person", "Third Person", "Second Person");
    public final SliderSetting   sensitivity = new SliderSetting("Sensitivity",  100, 1, 200, 1);
    public final BooleanSetting  invertPitch = new BooleanSetting("Invert Pitch", false);
    public final BooleanSetting  smoothReturn= new BooleanSetting("Smooth Return", true);
    public final SliderSetting   returnSpeed = new SliderSetting("Return Speed",  60, 1, 100, 1);

    private boolean isActive          = false;
    private boolean wasPressed         = false;
    private boolean wasActiveLastTick  = false;

    private float storedYaw;
    private float storedPitch;
    private float cameraYaw;
    private float cameraPitch;
    private int   savedThirdPerson    = 0;

    public Freelook() {
        super("Freelook", true);
        setKey(Keyboard.KEY_6);
        register(mode); register(viewMode); register(sensitivity);
        register(invertPitch); register(smoothReturn); register(returnSpeed);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;

        boolean keyDown = Keyboard.isKeyDown(getKey());

        // Activation logic
        if (mode.getIndex() == 0) { // Hold
            isActive = keyDown && mc.currentScreen == null;
        } else {                    // Toggle
            if (keyDown && !wasPressed) { isActive = !isActive; wasPressed = true; }
            if (!keyDown) wasPressed = false;
            if (mc.currentScreen != null) isActive = false;
        }

        if (isActive) {
            if (!wasActiveLastTick) {
                // First frame: capture angles, drain mouse, set perspective
                storedYaw   = mc.thePlayer.rotationYaw;
                storedPitch = mc.thePlayer.rotationPitch;
                cameraYaw   = storedYaw;
                cameraPitch = storedPitch;
                while (Mouse.next()) {}
                Mouse.getDX(); Mouse.getDY();
                savedThirdPerson = mc.gameSettings.thirdPersonView;
                mc.gameSettings.thirdPersonView = viewMode.getIndex();
            }

            // Consume mouse deltas -> update camera
            int dx = Mouse.getDX();
            int dy = Mouse.getDY();
            float sens = (float) sensitivity.getValue() / 100.0f * 0.15f;
            cameraYaw += dx * sens;
            float pitchDelta = dy * sens;
            if (invertPitch.getValue()) pitchDelta = -pitchDelta;
            cameraPitch -= pitchDelta;
            cameraPitch = Math.max(-90f, Math.min(90f, cameraPitch));

            // Lock player movement angles so server/physics are unaffected
            mc.thePlayer.rotationYaw        = storedYaw;
            mc.thePlayer.rotationPitch      = storedPitch;
            mc.thePlayer.prevRotationYaw    = storedYaw;
            mc.thePlayer.prevRotationPitch  = storedPitch;
            mc.thePlayer.renderYawOffset    = storedYaw;
            mc.thePlayer.prevRenderYawOffset= storedYaw;
        }

        if (!isActive && wasActiveLastTick) {
            // Just deactivated: restore perspective
            mc.gameSettings.thirdPersonView = savedThirdPerson;
            if (mc.thePlayer != null) {
                mc.thePlayer.rotationYaw   = storedYaw;
                mc.thePlayer.rotationPitch = storedPitch;
            }
        }

        wasActiveLastTick = isActive;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;

        if (isActive) {
            // Push camera angles to player rotation so MC renders from camera perspective
            mc.thePlayer.rotationYaw     = cameraYaw;
            mc.thePlayer.rotationPitch   = cameraPitch;
            mc.thePlayer.rotationYawHead = cameraYaw;
        } else if (smoothReturn.getValue()
                && (Math.abs(cameraYaw - storedYaw) > 0.25f || Math.abs(cameraPitch - storedPitch) > 0.25f)) {
            // Interpolate camera back to player look angles
            float speed = (float) returnSpeed.getValue() / 100.0f * 0.45f;
            cameraYaw   += (storedYaw   - cameraYaw)   * speed;
            cameraPitch += (storedPitch - cameraPitch) * speed;
            if (Math.abs(cameraYaw - storedYaw) < 0.5f && Math.abs(cameraPitch - storedPitch) < 0.5f) {
                cameraYaw   = storedYaw;
                cameraPitch = storedPitch;
            }
            mc.thePlayer.rotationYaw     = cameraYaw;
            mc.thePlayer.rotationPitch   = cameraPitch;
            mc.thePlayer.rotationYawHead = cameraYaw;
        } else {
            mc.thePlayer.rotationYawHead = mc.thePlayer.rotationYaw;
        }
    }

    @Override
    public void onDisabled() {
        isActive = false;
        wasPressed = false;
        wasActiveLastTick = false;
        if (mc.thePlayer != null) {
            mc.thePlayer.rotationYawHead     = mc.thePlayer.rotationYaw;
            mc.thePlayer.renderYawOffset     = mc.thePlayer.rotationYaw;
            mc.thePlayer.prevRenderYawOffset = mc.thePlayer.rotationYaw;
        }
        mc.gameSettings.thirdPersonView = savedThirdPerson;
    }

    @Override
    public String[] getSuffix() {
        if (!isActive) return new String[0];
        return new String[]{ mode.getIndex() == 0 ? "HOLD" : "ON" };
    }
}