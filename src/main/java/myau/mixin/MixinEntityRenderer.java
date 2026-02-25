package myau.mixin;

import myau.Myau;
import myau.module.modules.Autoblock;
import myau.module.modules.KillAura;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    @Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true)
    private void onHurtCam(float ticks, CallbackInfo ci) {

        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        Autoblock autoblock = (Autoblock) Myau.moduleManager.modules.get(Autoblock.class);

        // If KillAura is enabled AND Autoblock is currently blocking, cancel the hurtcam effect
        if (killAura.isEnabled() && autoblock.isBlocking()) {
            ci.cancel();
        }
    }
}
