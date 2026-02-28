package myau.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Redirects camera yaw/pitch to Freelook.cameraYaw/cameraPitch while freelook is active.
 *
 * Two injection points:
 *  1. EntityRenderer#orientCamera — overrides the entity's rotationYaw/Pitch before
 *     the GL camera matrix is built, then restores them after.
 *  2. RenderManager#cacheActiveRenderInfo — overrides playerViewX/Y (name tags, mob heads)
 *     so they face the freelook direction too.
 */
@SideOnly(Side.CLIENT)
@Mixin(value = {EntityRenderer.class}, priority = 9990)
public abstract class MixinFreelook {

    // Saved real yaw/pitch so we can restore after the GL call
    private float _savedYaw;
    private float _savedPitch;
    private float _savedPrevYaw;
    private float _savedPrevPitch;

    @Inject(method = "orientCamera", at = @At("HEAD"))
    private void freelookPre(float partialTicks, CallbackInfo ci) {
        if (!Freelook.active) return;
        Entity cam = Minecraft.getMinecraft().getRenderViewEntity();
        if (cam == null) return;

        _savedYaw      = cam.rotationYaw;
        _savedPitch    = cam.rotationPitch;
        _savedPrevYaw  = cam.prevRotationYaw;
        _savedPrevPitch= cam.prevRotationPitch;

        cam.rotationYaw      = Freelook.cameraYaw;
        cam.rotationPitch    = Freelook.cameraPitch;
        cam.prevRotationYaw  = Freelook.cameraYaw;
        cam.prevRotationPitch= Freelook.cameraPitch;
    }

    @Inject(method = "orientCamera", at = @At("RETURN"))
    private void freelookPost(float partialTicks, CallbackInfo ci) {
        if (!Freelook.active) return;
        Entity cam = Minecraft.getMinecraft().getRenderViewEntity();
        if (cam == null) return;

        cam.rotationYaw      = _savedYaw;
        cam.rotationPitch    = _savedPitch;
        cam.prevRotationYaw  = _savedPrevYaw;
        cam.prevRotationPitch= _savedPrevPitch;
    }
}
