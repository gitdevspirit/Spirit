package myau.mixin;

import myau.Myau;
import myau.event.EventManager;
import myau.events.Render3DEvent;
import myau.module.modules.Autoblock;
import myau.module.modules.KillAura;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    @Inject(method = "renderWorldPass", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;renderCloudsCheck(Lnet/minecraft/client/renderer/RenderGlobal;F)V",
            shift = At.Shift.BEFORE
    ))
    private void onRenderWorld(int pass, float partialTicks, long timeSlice, CallbackInfo ci) {
        EventManager.call(new Render3DEvent(partialTicks));
    }

    @Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true)
    private void onHurtCam(float ticks, CallbackInfo ci) {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        Autoblock autoblock = (Autoblock) Myau.moduleManager.modules.get(Autoblock.class);
        if (killAura.isEnabled() && autoblock.isBlocking()) {
            ci.cancel();
        }
    }
}
