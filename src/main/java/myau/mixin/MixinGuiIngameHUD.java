package myau.mixin;

import myau.module.ModuleManager;
import myau.module.modules.HUD;
import myau.ui.hud.ArraylistHUD;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public class MixinGuiIngameHUD {

    @Inject(method = "renderGameOverlay", at = @At("TAIL"))
    private void renderHUD(float partialTicks, CallbackInfo ci) {
        HUD hud = ModuleManager.INSTANCE.getModule(HUD.class);
        if (hud != null && hud.isEnabled()) {
            ArraylistHUD.render(hud);
        }
    }
}
