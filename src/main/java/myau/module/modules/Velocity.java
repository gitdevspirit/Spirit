package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.KnockbackEvent;
import myau.mixin.IAccessorEntity;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;

public class Velocity extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final DropdownSetting mode    = register(new DropdownSetting("Mode", 0, "CANCEL", "REDUCE"));
    public final SliderSetting   hReduction = register(new SliderSetting("H %", 0, 0, 100, 1));
    public final SliderSetting   vReduction = register(new SliderSetting("V %", 0, 0, 100, 1));
    public final BooleanSetting  fakeCheck = register(new BooleanSetting("Fake Check", true));
    public final BooleanSetting  debugLog  = register(new BooleanSetting("Debug Log", false));

    private boolean pendingExplosion = false;

    public Velocity() {
        super("Velocity", false);
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
        if (!this.isEnabled()) return;

        if (this.pendingExplosion) {
            event.setCancelled(true);
            this.pendingExplosion = false;
            return;
        }

        if (this.fakeCheck.getValue() && this.isInLiquidOrWeb()) {
            event.setCancelled(true);
            return;
        }

        if (this.canDelay()) {
            event.setCancelled(true);
            return;
        }

        if (mode.getIndex() == 1) {  // REDUCE
            double h = (100.0 - hReduction.getValue()) / 100.0;
            double v = (100.0 - vReduction.getValue()) / 100.0;
            event.setX(event.getX() * h);
            event.setY(event.getY() * v);
            event.setZ(event.getZ() * h);
            return;
        }

        if (mode.getIndex() == 0) event.setCancelled(true);  // CANCEL
        if (this.debugLog.getValue()) {
            System.out.println("[Velocity] Knockback event triggered.");
        }
    }
}
