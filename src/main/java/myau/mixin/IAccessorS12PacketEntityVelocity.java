package myau.mixin;

import net.minecraft.network.play.server.S12PacketEntityVelocity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(S12PacketEntityVelocity.class)
public interface IAccessorS12PacketEntityVelocity {
    @Accessor("motionX") int getMotionX();
    @Accessor("motionY") int getMotionY();
    @Accessor("motionZ") int getMotionZ();
    @Mutable @Accessor("motionX") void setMotionX(int motionX);
    @Mutable @Accessor("motionY") void setMotionY(int motionY);
    @Mutable @Accessor("motionZ") void setMotionZ(int motionZ);
}
