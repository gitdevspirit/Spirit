package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KnockbackEvent;
import myau.events.LivingUpdateEvent;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorEntity;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.Potion;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // CANCEL = 0, REDUCE = 1, JUMP_RESET = 2
    public final DropdownSetting mode       = register(new DropdownSetting("Mode", 0, "CANCEL", "REDUCE", "JUMP RESET"));
    public final SliderSetting   hReduction = register(new SliderSetting("H %",   0, 0, 100, 1));
    public final SliderSetting   vReduction = register(new SliderSetting("V %",   0, 0, 100, 1));
    public final BooleanSetting  fakeCheck  = register(new BooleanSetting("Fake Check", true));
    public final BooleanSetting  debugLog   = register(new BooleanSetting("Debug Log",  false));

    private boolean pendingExplosion = false;

    // Jump reset state
    private boolean jumpFlag     = false;
    private boolean shouldJump   = false;
    private int     jumpCooldown = 0;

    public Velocity() {
        super("Velocity", false);
    }

    @Override
    public void onDisabled() {
        pendingExplosion = false;
        jumpFlag         = false;
        shouldJump       = false;
        jumpCooldown     = 0;
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater()
                || mc.thePlayer.isInLava()
                || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    private boolean canDelay() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        return mc.thePlayer.onGround && !killAura.isEnabled();
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!isEnabled()) return;

        if (pendingExplosion) {
            event.setCancelled(true);
            pendingExplosion = false;
            return;
        }

        if (fakeCheck.getValue() && isInLiquidOrWeb()) {
            event.setCancelled(true);
            return;
        }

        if (canDelay()) {
            event.setCancelled(true);
            return;
        }

        int m = mode.getIndex();

        if (m == 0) { // CANCEL
            event.setCancelled(true);
        } else if (m == 1) { // REDUCE
            double h = (100.0 - hReduction.getValue()) / 100.0;
            double v = (100.0 - vReduction.getValue()) / 100.0;
            event.setX(event.getX() * h);
            event.setY(event.getY() * v);
            event.setZ(event.getZ() * h);
        } else if (m == 2) { // JUMP RESET
            // Let the knockback through fully, then jump on the next
            // LivingUpdate tick to reset vertical velocity
            if (event.getY() > 0.0) jumpFlag = true;
        }

        if (debugLog.getValue()) {
            System.out.println("[Velocity] Knockback event triggered, mode=" + m);
        }
    }

    // Jump reset: fires on next LivingUpdate after knockback received
    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!isEnabled() || mode.getIndex() != 2) return;

        if (jumpFlag) {
            jumpFlag = false;
            if (mc.thePlayer.onGround
                    && mc.thePlayer.isSprinting()
                    && !mc.thePlayer.isPotionActive(Potion.jump)
                    && !isInLiquidOrWeb()) {
                mc.thePlayer.movementInput.jump = true;
            }
        }
    }

    // LEGIT_TEST style: watch hurtTime and trigger a jump at peak knockback
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.POST) return;
        if (mode.getIndex() != 2) return;

        int hurtTime = mc.thePlayer.hurtTime;

        if (hurtTime >= 8) {
            if (jumpCooldown <= 0) {
                shouldJump   = true;
                jumpCooldown = 2;
            }
        } else if (hurtTime <= 1) {
            shouldJump   = false;
            jumpCooldown = 0;
        }

        if (shouldJump && mc.thePlayer.onGround && jumpCooldown <= 0) {
            mc.thePlayer.jump();
            shouldJump = false;
        }

        if (jumpCooldown > 0) jumpCooldown--;
    }
}
