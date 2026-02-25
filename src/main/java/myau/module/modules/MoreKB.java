package myau.module.modules;

import myau.event.EventTarget;
import myau.events.AttackEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

public class MoreKB extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final DropdownSetting mode        = new DropdownSetting("Mode", 0, "LEGIT", "LEGIT_FAST", "LESS_PACKET", "PACKET", "DOUBLE_PACKET");
    public final BooleanSetting  intelligent = new BooleanSetting("Intelligent", false);
    public final BooleanSetting  onlyGround  = new BooleanSetting("Only Ground",  true);

    private boolean          shouldSprintReset = false;
    private EntityLivingBase target            = null;

    public MoreKB() {
        super("MoreKB", false);
        register(mode); register(intelligent); register(onlyGround);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!isEnabled()) return;
        Entity t = event.getTarget();
        if (t instanceof EntityLivingBase) target = (EntityLivingBase) t;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled()) return;
        // LEGIT_FAST mode (1)
        if (mode.getIndex() == 1) {
            if (target != null && isMoving()) {
                if (!onlyGround.getValue() || mc.thePlayer.onGround)
                    mc.thePlayer.sprintingTicksLeft = 0;
                target = null;
            }
            return;
        }
        EntityLivingBase entity = null;
        if (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && mc.objectMouseOver.entityHit instanceof EntityLivingBase)
            entity = (EntityLivingBase) mc.objectMouseOver.entityHit;
        if (entity == null) return;

        double dx = mc.thePlayer.posX - entity.posX;
        double dz = mc.thePlayer.posZ - entity.posZ;
        float calcYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI - 90.0);
        float diffY   = Math.abs(MathHelper.wrapAngleTo180_float(calcYaw - entity.rotationYawHead));
        if (intelligent.getValue() && diffY > 120.0F) return;

        if (entity.hurtTime == 10) {
            switch (mode.getIndex()) {
                case 0: // LEGIT
                    shouldSprintReset = true;
                    if (mc.thePlayer.isSprinting()) { mc.thePlayer.setSprinting(false); mc.thePlayer.setSprinting(true); }
                    shouldSprintReset = false;
                    break;
                case 2: // LESS_PACKET
                    if (mc.thePlayer.isSprinting()) mc.thePlayer.setSprinting(false);
                    mc.getNetHandler().addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
                    mc.thePlayer.setSprinting(true);
                    break;
                case 3: // PACKET
                    mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
                    mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
                    mc.thePlayer.setSprinting(true);
                    break;
                case 4: // DOUBLE_PACKET
                    mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
                    mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
                    mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
                    mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
                    mc.thePlayer.setSprinting(true);
                    break;
            }
        }
    }

    private boolean isMoving() { return mc.thePlayer.moveForward != 0.0F || mc.thePlayer.moveStrafing != 0.0F; }

    @Override
    public String[] getSuffix() { return new String[]{ mode.getOptions()[mode.getIndex()] }; }
}