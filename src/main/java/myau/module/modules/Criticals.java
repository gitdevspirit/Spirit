package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.AttackEvent;
import myau.events.PacketEvent;
import myau.mixin.IAccessorC03PacketPlayer;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.PacketUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C03PacketPlayer;

public class Criticals extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final DropdownSetting mode          = new DropdownSetting("Mode", 0,
            "Packet", "NCPPacket", "OldBlocksMC", "OldBlocksMC2", "NoGround",
            "Hop", "TPHop", "Jump", "LowJump", "CustomMotion", "Visual");
    public final SliderSetting   delay         = new SliderSetting("Delay",       0,    0, 500, 1);
    public final SliderSetting   hurtTime      = new SliderSetting("Hurt Time",  10,    0,  10, 1);
    public final SliderSetting   customMotionY = new SliderSetting("Custom Y",  0.2, 0.01, 0.42, 0.01);

    private final TimerUtil timer = new TimerUtil();

    public Criticals() {
        super("Criticals", false);
        register(mode); register(delay); register(hurtTime); register(customMotionY);
    }

    @Override
    public void onEnabled() {
        if (mode.getIndex() == 4 && mc.thePlayer != null) mc.thePlayer.jump();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (!(event.getTarget() instanceof EntityLivingBase)) return;
        EntityLivingBase target = (EntityLivingBase) event.getTarget();
        if (!mc.thePlayer.onGround || mc.thePlayer.isUsingItem()) return;
        if (mc.thePlayer.isInWater() || mc.thePlayer.isInLava()) return;
        if (mc.thePlayer.ridingEntity != null) return;
        if (target.hurtTime > (int) hurtTime.getValue()) return;
        Fly fly = (Fly) Myau.moduleManager.modules.get(Fly.class);
        if (fly != null && fly.isEnabled()) return;
        if (!timer.hasTimeElapsed((long) delay.getValue())) return;

        double x = mc.thePlayer.posX, y = mc.thePlayer.posY, z = mc.thePlayer.posZ;
        switch (mode.getIndex()) {
            case 0:
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.0625, z, true));
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
                mc.thePlayer.attackTargetEntityWithCurrentItem(target);
                break;
            case 1:
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.11, z, false));
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.1100013579, z, false));
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 1.3579E-6, z, false));
                mc.thePlayer.attackTargetEntityWithCurrentItem(target);
                break;
            case 2:
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.001091981, z, true));
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
                break;
            case 3:
                if (mc.thePlayer.ticksExisted % 4 == 0) {
                    PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.0011, z, true));
                    PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
                }
                break;
            case 4: default: break;
            case 5:
                mc.thePlayer.motionY = 0.1;
                mc.thePlayer.fallDistance = 0.1F;
                mc.thePlayer.onGround = false;
                break;
            case 6:
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.02, z, false));
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.01, z, false));
                mc.thePlayer.setPosition(x, y + 0.01, z);
                break;
            case 7: mc.thePlayer.motionY = 0.42; break;
            case 8: mc.thePlayer.motionY = 0.3425; break;
            case 9: mc.thePlayer.motionY = customMotionY.getValue(); break;
            case 10: mc.thePlayer.attackTargetEntityWithCurrentItem(target); break;
        }
        timer.reset();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.SEND) return;
        if (mode.getIndex() == 4 && event.getPacket() instanceof C03PacketPlayer)
            ((IAccessorC03PacketPlayer) event.getPacket()).setOnGround(false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{ mode.getOptions()[mode.getIndex()] };
    }
}