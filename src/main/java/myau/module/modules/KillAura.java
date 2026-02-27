package myau.module.modules;

import myau.module.Module;
import net.minecraft.entity.EntityLivingBase;

/**
 * KillAura has been removed. This stub keeps AutoTool, BackTrack, NoSlow,
 * TargetHUD, TargetStrafe, Velocity, and Autoblock compiling without changes.
 */
public class KillAura extends Module {

    public static int attackCooldownTicks = 0;

    public KillAura() { super("KillAura", false); }

    public EntityLivingBase getTarget()    { return null; }
    public boolean isAttackAllowed()       { return false; }
}
