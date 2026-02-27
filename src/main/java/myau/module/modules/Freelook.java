package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Freelook — lets you move the camera independently of your player body.
 *
 * Ported from chromaticforge/freelook (Kotlin, 1.8.9 Forge).
 *
 * How it works:
 *  - While active, mouse deltas are consumed by cameraYaw/cameraPitch instead
 *    of rotationYaw/rotationPitch.
 *  - MixinFreelook (EntityRenderer + RenderManager) reads the static state here
 *    and redirects the GL camera to use cameraYaw/cameraPitch so the view rotates
 *    without the player turning.
 *  - On deactivation the camera snaps back to the player's real look direction
 *    (snap mode) or smoothly eases back (smooth mode).
 */
public class Freelook extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // ── Settings ───────────────────────────────────────────────────────────────

    public final DropdownSetting mode        = register(new DropdownSetting("Mode",        0, "Toggle", "Hold"));
    public final DropdownSetting perspective = register(new DropdownSetting("Perspective", 0, "Current", "First", "Third", "Reverse"));
    public final BooleanSetting  invertPitch = register(new BooleanSetting("Invert Pitch", false));
    public final BooleanSetting  invertYaw   = register(new BooleanSetting("Invert Yaw",   false));
    public final BooleanSetting  lockPitch   = register(new BooleanSetting("Lock Pitch",   false));
    public final BooleanSetting  smooth      = register(new BooleanSetting("Smooth",       true));
    public final SliderSetting   smoothSpeed = register(new SliderSetting("Smooth Speed",  8.0, 1.0, 20.0, 0.5));
    public final SliderSetting   sensitivity = register(new SliderSetting("Sensitivity",   1.0, 0.1,  3.0, 0.05));
    public final SliderSetting   keyBind     = register(new SliderSetting("Key (LWJGL)",   56, 1, 200, 1)); // 56 = LAlt

    // ── Static state (read by mixin) ───────────────────────────────────────────

    /** Camera yaw override — used by MixinFreelook to redirect rendering. */
    public static float cameraYaw   = 0f;
    /** Camera pitch override — used by MixinFreelook to redirect rendering. */
    public static float cameraPitch = 0f;
    /** True while freelook camera is active (rendering should use camera values). */
    public static boolean active    = false;

    // ── Internal state ─────────────────────────────────────────────────────────

    private int  savedPerspective    = 0;
    private boolean wasHeld          = false;

    // Smooth return state
    private boolean  returning       = false;
    private float    returnStartYaw  = 0f;
    private float    returnStartPitch= 0f;
    private long     returnStartMs   = 0L;
    private static final long RETURN_MS = 300L; // ms to ease back

    public Freelook() {
        super("Freelook", false);
    }

    // ── Activation / deactivation ──────────────────────────────────────────────

    @Override
    public void onEnabled() {
        if (mc.thePlayer == null) return;
        // Snap camera to player's current look direction on activation
        cameraYaw   = mc.thePlayer.rotationYaw;
        cameraPitch = mc.thePlayer.rotationPitch;
        returning   = false;
        active      = true;

        // Switch perspective if needed
        savedPerspective = mc.gameSettings.thirdPersonView;
        int p = perspective.getIndex();
        if (p == 1) mc.gameSettings.thirdPersonView = 0;
        else if (p == 2) mc.gameSettings.thirdPersonView = 1;
        else if (p == 3) mc.gameSettings.thirdPersonView = 2;
        // p == 0 (Current) → leave as-is
    }

    @Override
    public void onDisabled() {
        if (smooth.getValue()) {
            // Start smooth return to real look direction
            returning        = true;
            returnStartYaw   = cameraYaw;
            returnStartPitch = cameraPitch;
            returnStartMs    = System.currentTimeMillis();
            // Keep active=true until return finishes (handled in tick)
        } else {
            // Snap back immediately
            if (mc.thePlayer != null) {
                cameraYaw   = mc.thePlayer.rotationYaw;
                cameraPitch = mc.thePlayer.rotationPitch;
            }
            active = false;
        }

        // Restore perspective
        mc.gameSettings.thirdPersonView = savedPerspective;
        wasHeld = false;
    }

    // ── Tick: handle Hold mode + smooth return ─────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null) return;

        int key = (int) keyBind.getValue();

        // Hold mode: auto-disable when key released
        if (isEnabled() && mode.getIndex() == 1) {
            boolean held = Keyboard.isKeyDown(key);
            if (wasHeld && !held) {
                disable();
                return;
            }
            wasHeld = held;
        }

        // Smooth return animation after disable
        if (returning && mc.thePlayer != null) {
            long elapsed = System.currentTimeMillis() - returnStartMs;
            float t = Math.min(1f, (float) elapsed / RETURN_MS);
            // Ease-in-expo: 2^(10*(t-1))
            float ease = t == 0f ? 0f : (float) Math.pow(2.0, 10.0 * (t - 1.0));
            float targetYaw   = mc.thePlayer.rotationYaw;
            float targetPitch = mc.thePlayer.rotationPitch;
            cameraYaw   = lerp(returnStartYaw,   targetYaw,   ease);
            cameraPitch = lerp(returnStartPitch, targetPitch, ease);
            if (t >= 1f) {
                returning = false;
                active    = false;
            }
            return;
        }

        if (!isEnabled()) return;

        // Consume mouse input for camera
        if (mc.inGameHasFocus) {
            GameSettings gs = mc.gameSettings;
            float sens   = (float) sensitivity.getValue();
            float gSens  = gs.mouseSensitivity * 0.6f + 0.2f;
            float factor = gSens * gSens * gSens * 8f * sens;

            int dx = Mouse.getDX();
            int dy = Mouse.getDY();

            float dyaw   = dx * factor * (invertYaw.getValue()   ? -1f : 1f);
            float dpitch = -dy * factor * (invertPitch.getValue() ? -1f : 1f);

            cameraYaw   = (cameraYaw   + dyaw)   % 360f;
            if (!lockPitch.getValue()) {
                cameraPitch = clamp(cameraPitch + dpitch, -90f, 90f);
            }
        }
    }

    // ── Handle Toggle keybind outside the module toggle hotkey ────────────────

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        // Hold mode: enable on key press (toggle handled by the module's own hotkey)
        if (!isEnabled() && mode.getIndex() == 1) {
            int key = (int) keyBind.getValue();
            if (Keyboard.isKeyDown(key) && !wasHeld) {
                wasHeld = true;
                enable();
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private void enable() {
        if (!isEnabled()) toggle();
    }

    private void disable() {
        if (isEnabled()) toggle();
    }

    @Override
    public String[] getSuffix() {
        return active ? new String[]{ mode.getIndex() == 0 ? "Toggle" : "Hold" } : new String[0];
    }
}
