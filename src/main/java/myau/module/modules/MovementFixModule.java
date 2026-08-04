package myau.module.modules;

import myau.module.Module;

/**
 * Master on/off switch for {@link myau.management.MovementFix}. Modules that
 * silently report a fake rotation (AimAssist Silent, Clutch) still request the
 * correction per-tick via {@code myau.management.MovementFix.forceMovementFix},
 * but the actual correction only ever applies while this module is enabled —
 * disabling it lets movement go fully server-relative even if something else
 * asks for the fix, which is useful to isolate whether a movement issue is
 * caused by the fix itself.
 */
public class MovementFix extends Module {
    public MovementFix() { super("MovementFix", true); }
}
