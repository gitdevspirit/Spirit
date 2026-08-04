package myau.mixin;

import myau.ui.hud.ArraylistHUD;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngameForge.class)
public class MixinGuiIngameHUD {

    private final ArraylistHUD hud = new ArraylistHUD();

    @Inject(method = "func_175180_a", at = @At("TAIL"))
    private void renderHUD(float partialTicks, CallbackInfo ci) {
        System.out.println("HUD MIXIN FIRED (TAIL)");
        hud.render();
    }
}
