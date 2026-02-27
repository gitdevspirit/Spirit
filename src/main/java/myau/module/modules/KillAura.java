package myau.module.modules;

import myau.Myau;
import myau.module.Module;
import net.minecraft.entity.EntityLivingBase;

/**
 * KillAura stub — all functionality has been merged into AimAssist (SILENT mode).
 * This class exists only to keep module references in AutoTool, BackTrack,
 * NoSlow, TargetHUD, TargetStrafe, Velocity, and Autoblock compiling.
 */
public class KillAura extends Module {

    public static int attackCooldownTicks = 0;

    public KillAura() {
        super("KillAura", false);
        // Hidden — not shown in the GUI, not a real module
    }

    private AimAssist aimAssist() {
        return (AimAssist) Myau.moduleManager.modules.get(AimAssist.class);
    }

    @Override
    public boolean isEnabled() {
        AimAssist aa = aimAssist();
        return aa != null && aa.isEnabled() && aa.mode.getIndex() == 1;
    }

    public EntityLivingBase getTarget() {
        AimAssist aa = aimAssist();
        return aa != null ? aa.getTarget() : null;
    }

    public boolean isAttackAllowed() {
        AimAssist aa = aimAssist();
        return aa != null && aa.isAttackAllowed();
    }

    // Sync static field from AimAssist so Autoblock can read it
    public static int getAttackCooldown() {
        return AimAssist.attackCooldownTicks;
    }
}
