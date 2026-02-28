package myau.module.modules;

import myau.module.Module;
import net.minecraft.entity.EntityLivingBase;

/**
 * KillAura stub — keeps BackTrack, MixinEntityRenderer, and other
 * references compiling without any real logic.
 */
public class KillAura extends Module {

    public static int attackCooldownTicks = 0;

    public KillAura() { super("KillAura", false); }

    public EntityLivingBase getTarget()  { return null; }
    public boolean isAttackAllowed()     { return false; }
}
