package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.mixin.*;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.MoveUtil;
import myau.util.PacketUtil;
import myau.util.RandomUtil;
import myau.util.TimerUtil;
import myau.util.rotation.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.client.C0BPacketEntityAction.Action;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.potion.Potion;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{
            "Simple","AAC","AACPush","AACZero","AACv4","Reverse","SmoothReverse",
            "Jump","Glitch","Legit","Vulcan","MatrixReduce","MatrixReducePlus",
            "IntaveReduce","GrimC03","Hypixel","HypixelAir","BlockSMC",
            "GrimCombat","Polar","MatrixNoXZ","Intave13","SmartJumpReset",
            "Intave14","HypixelPrediction"
    });

    public final FloatProperty horizontal         = new FloatProperty("Horizontal",       0.0f, -2.0f, 2.0f,  () -> mode() == 0 || mode() == 9);
    public final FloatProperty vertical           = new FloatProperty("Vertical",         0.0f, -2.0f, 2.0f,  () -> mode() == 0 || mode() == 9);
    public final IntProperty   predictionChance   = new IntProperty("PredChance",         100,  0,   100,      () -> mode() == 24);
    public final FloatProperty predictionHoriz    = new FloatProperty("PredHorizontal",   0.0f, 0.0f, 1.0f,   () -> mode() == 24);
    public final FloatProperty predictionVert     = new FloatProperty("PredVertical",     1.0f, 0.0f, 1.0f,   () -> mode() == 24);
    public final BooleanProperty predFakeCheck    = new BooleanProperty("PredFakeCheck",  false,               () -> mode() == 24);
    public final BooleanProperty predDebug        = new BooleanProperty("PredDebug",      false,               () -> mode() == 24);
    public final FloatProperty reverseStrength    = new FloatProperty("ReverseStrength",  1.0f, 0.1f, 1.0f,   () -> mode() == 5);
    public final FloatProperty smoothRevStrength  = new FloatProperty("SmoothRevStr",     0.05f,0.02f,0.1f,   () -> mode() == 6);
    public final BooleanProperty onLook           = new BooleanProperty("OnLook",         false,               () -> mode() == 5 || mode() == 6);
    public final FloatProperty maxAngle           = new FloatProperty("MaxAngle",         45.0f,5.0f, 90.0f,  () -> (mode() == 5 || mode() == 6) && (Boolean)onLook.getValue());
    public final FloatProperty aacPushXZ          = new FloatProperty("AACPushXZ",        2.0f, 1.0f, 3.0f,   () -> mode() == 2);
    public final BooleanProperty aacPushY         = new BooleanProperty("AACPushY",       true,                () -> mode() == 2);
    public final FloatProperty aacv4Reduce        = new FloatProperty("AACv4Reduce",      0.62f,0.0f, 1.0f,   () -> mode() == 4);
    public final IntProperty   chance             = new IntProperty("Chance",             100,  0,   100,      () -> mode() == 7 || mode() == 9);
    public final IntProperty   ticksUntilJump     = new IntProperty("JumpTicks",          4,    0,   20,       () -> mode() == 7);
    public final FloatProperty intaveReduceFactor = new FloatProperty("ReduceFactor",     0.6f, 0.0f, 1.0f,   () -> mode() == 13);
    public final BooleanProperty smartJumpSneak   = new BooleanProperty("SneakReduce",   false,               () -> mode() == 22);
    public final BooleanProperty smartJumpBack    = new BooleanProperty("Backward",      false,               () -> mode() == 22);
    public final FloatProperty grimRange          = new FloatProperty("GrimRange",        3.5f, 0.0f, 6.0f,   () -> mode() == 18);
    public final IntProperty   grimAttacks        = new IntProperty("GrimAttacks",        12,   1,   16,       () -> mode() == 18);
    public final FloatProperty intave14T1         = new FloatProperty("Intave14-T1",      0.3f, 0.1f, 2.0f,   () -> mode() == 23);
    public final FloatProperty intave14T2         = new FloatProperty("Intave14-T2",      5.0f, 1.0f, 10.0f,  () -> mode() == 23);

    private final TimerUtil velocityTimer = new TimerUtil();
    private boolean hasReceivedVelocity = false;
    private boolean jump = false;
    private int limitUntilJump = 0;
    private int intaveTick = 0;
    private int intaveDamageTick = 0;
    private long lastAttackTime = 0L;
    private boolean vulcanTrans = false;
    private boolean hypixelAbsorbed = false;
    private boolean matrixAbsorbed = false;
    private boolean attacked = false;
    private int timerTicks = 0;
    private int chanceCounter = 0;
    private boolean allowNext = true;
    private float reduceYaw = 0.0f;
    private boolean shouldRotate = false;
    private int attackTimer = -1;
    private int lastHurtTime = 0;
    private boolean jumpFlag = false;

    public Velocity() {
        super("Velocity", false, false);
    }

    private int mode() { return (Integer) mode.getValue(); }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null)
            ((IAccessorEntityPlayer) mc.thePlayer).setSpeedInAir(0.02f);
        ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0f;
        timerTicks = 0; limitUntilJump = 0;
        chanceCounter = 0; allowNext = true;
        shouldRotate = false; attackTimer = -1;
        lastHurtTime = 0; jumpFlag = false;
        reset();
    }

    private void reset() {
        hasReceivedVelocity = false;
        attacked = false;
        hypixelAbsorbed = false;
        matrixAbsorbed = false;
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava()
                || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            EntityPlayerSP p = mc.thePlayer;
            if (p == null || p.isInWater() || p.isInLava() || ((IAccessorEntity) p).getIsInWeb()) return;

            switch (mode()) {
                case 1:
                    if (hasReceivedVelocity && velocityTimer.hasTimeElapsed(80L)) {
                        p.motionX *= (double)(float) horizontal.getValue();
                        p.motionZ *= (double)(float) horizontal.getValue();
                        hasReceivedVelocity = false;
                    }
                    break;
                case 2:
                    if (jump) { if (p.onGround) jump = false; }
                    else {
                        if (p.hurtTime > 0 && p.motionX != 0.0 && p.motionZ != 0.0) p.onGround = true;
                        if (p.ticksExisted > 0 && (Boolean) aacPushY.getValue()) p.motionY -= 0.014999993;
                    }
                    if (p.ticksExisted >= 19) {
                        p.motionX /= (double)(float) aacPushXZ.getValue();
                        p.motionZ /= (double)(float) aacPushXZ.getValue();
                    }
                    break;
                case 3:
                    if (p.hurtTime > 0) {
                        if (!hasReceivedVelocity || p.onGround || p.fallDistance > 2.0f) return;
                        p.motionY--;
                        p.isAirBorne = true;
                        p.onGround = true;
                    } else hasReceivedVelocity = false;
                    break;
                case 4:
                    if (p.hurtTime > 0 && !p.onGround) {
                        p.motionX *= (double)(float) aacv4Reduce.getValue();
                        p.motionZ *= (double)(float) aacv4Reduce.getValue();
                    }
                    break;
                case 5:
                    if (hasReceivedVelocity) {
                        if (!p.onGround) {
                            if ((Boolean) onLook.getValue()) {
                                KillAura aura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
                                Entity target = aura != null && aura.target != null ? aura.target.getEntity() : null;
                                if (target != null && getRotDiff(new Rotation(p.rotationYaw, p.rotationPitch), getRotations(target)) > (float) maxAngle.getValue())
                                    return;
                            }
                            MoveUtil.setSpeed(MoveUtil.getSpeed() * (double)(float) reverseStrength.getValue());
                        } else if (velocityTimer.hasTimeElapsed(80L)) hasReceivedVelocity = false;
                    }
                    break;
                case 6:
                    if (hasReceivedVelocity) {
                        KillAura aura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
                        Entity target = aura != null && aura.target != null ? aura.target.getEntity() : null;
                        if (target == null) {
                            ((IAccessorEntityPlayer) p).setSpeedInAir(0.02f);
                        } else if ((Boolean) onLook.getValue() && getRotDiff(new Rotation(p.rotationYaw, p.rotationPitch), getRotations(target)) > (float) maxAngle.getValue()) {
                            hasReceivedVelocity = false;
                            ((IAccessorEntityPlayer) p).setSpeedInAir(0.02f);
                        } else if (!p.onGround) {
                            ((IAccessorEntityPlayer) p).setSpeedInAir((float) smoothRevStrength.getValue());
                        } else if (velocityTimer.hasTimeElapsed(80L)) {
                            hasReceivedVelocity = false;
                            ((IAccessorEntityPlayer) p).setSpeedInAir(0.02f);
                        }
                    }
                    break;
                case 8:
                    if (hasReceivedVelocity) {
                        p.isAirBorne = true;
                        if (p.hurtTime == 7) p.motionY = 0.4;
                        hasReceivedVelocity = false;
                    }
                    break;
                case 13:
                    if (!hasReceivedVelocity) return;
                    intaveTick++;
                    if (p.hurtTime == 2) {
                        intaveDamageTick++;
                        if (p.onGround && intaveTick % 2 == 0 && intaveDamageTick <= 10) {
                            if (!((IAccessorEntityLivingBase) p).isJumping()) p.jump();
                            intaveTick = 0;
                        }
                        hasReceivedVelocity = false;
                    }
                    break;
                case 15:
                    if (hasReceivedVelocity && p.onGround) hypixelAbsorbed = false;
                    break;
                case 16:
                    if (hasReceivedVelocity) {
                        if (p.onGround && !((IAccessorEntityLivingBase) p).isJumping()) p.jump();
                        hasReceivedVelocity = false;
                    }
                    break;
                case 18:
                    if (attacked && p.hurtTime == 0) attacked = false;
                    break;
                case 20:
                    if (hasReceivedVelocity && p.onGround) matrixAbsorbed = false;
                    break;
                case 22:
                    if (p.hurtTime > 0) {
                        boolean fwd = ((IAccessorKeyBinding) mc.gameSettings.keyBindForward).getPressed();
                        if ((Boolean) smartJumpBack.getValue()) {
                            if (p.hurtTime > 1) {
                                ((IAccessorKeyBinding) mc.gameSettings.keyBindForward).setPressed(false);
                                ((IAccessorKeyBinding) mc.gameSettings.keyBindBack).setPressed(true);
                                ((IAccessorKeyBinding) mc.gameSettings.keyBindSprint).setPressed(true);
                            } else if (mc.currentScreen == null) {
                                ((IAccessorKeyBinding) mc.gameSettings.keyBindForward).setPressed(GameSettings.isKeyDown(mc.gameSettings.keyBindForward));
                                ((IAccessorKeyBinding) mc.gameSettings.keyBindBack).setPressed(GameSettings.isKeyDown(mc.gameSettings.keyBindBack));
                                ((IAccessorKeyBinding) mc.gameSettings.keyBindSprint).setPressed(GameSettings.isKeyDown(mc.gameSettings.keyBindSprint));
                            }
                        }
                        if (p.onGround && p.hurtTime >= 8 && fwd) {
                            p.jump();
                            p.motionX *= 0.9999999;
                            p.motionZ *= 0.9999999;
                        }
                        if ((Boolean) smartJumpSneak.getValue()) {
                            if (p.hurtTime == 9) {
                                PacketUtil.sendPacket(new C0BPacketEntityAction(p, Action.START_SNEAKING));
                                PacketUtil.sendPacket(new C0BPacketEntityAction(p, Action.STOP_SNEAKING));
                            } else if (p.hurtTime == 8) {
                                p.motionX *= 0.9999999;
                                p.motionZ *= 0.9999999;
                            }
                        }
                    }
                    break;
                case 23: {
                    net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
                    if (p.hurtTime == 9)            timer.timerSpeed = (float) intave14T1.getValue();
                    else if (p.hurtTime >= 3 && p.hurtTime <= 8) timer.timerSpeed = (float) intave14T2.getValue();
                    else                            timer.timerSpeed = 1.0f;
                    break;
                }
                case 24: {
                    int ht = p.hurtTime;
                    if (ht > lastHurtTime) {
                        KillAura aura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
                        EntityLivingBase target = aura != null && aura.isEnabled() && aura.target != null ? aura.target.getEntity() : null;
                        if (target == null) {
                            if (shouldRotate) {
                                float diff = MathHelper.wrapAngleTo180_float(reduceYaw - p.rotationYaw);
                                p.rotationYaw += diff * 0.5f;
                                p.prevRotationYaw = p.rotationYaw;
                                if (p.onGround) p.jump();
                                shouldRotate = false;
                            }
                        } else {
                            if (p.onGround) p.jump();
                            if (p.getDistanceToEntity(target) <= 3.0f) attackTimer = 1;
                        }
                    }
                    if (attackTimer == 0) {
                        KillAura aura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
                        EntityLivingBase target = aura != null && aura.isEnabled() && aura.target != null ? aura.target.getEntity() : null;
                        if (target != null && p.getDistanceToEntity(target) <= 3.0f) {
                            p.swingItem();
                            mc.playerController.attackEntity(p, target);
                        }
                        attackTimer = -1;
                    }
                    if (attackTimer > 0) attackTimer--;
                    lastHurtTime = ht;
                    break;
                }
                default: break;
            }
        }

        if (event.getType() == EventType.POST && mode() == 24 && jumpFlag) {
            jumpFlag = false;
            EntityPlayerSP p = mc.thePlayer;
            if (p.onGround && p.isSprinting() && !p.isPotionActive(Potion.jump) && !isInLiquidOrWeb())
                p.jump();
        }
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (mode() == 14) {
            net.minecraft.util.Timer timer = ((IAccessorMinecraft) mc).getTimer();
            if (timerTicks > 0 && timer.timerSpeed <= 1.0f) {
                timer.timerSpeed = Math.min(0.8f + 0.2f * (float)(20 - timerTicks) / 20.0f, 1.0f);
                timerTicks--;
            } else if (timer.timerSpeed <= 1.0f) {
                timer.timerSpeed = 1.0f;
            }
        }
    }

    // ── Packet ────────────────────────────────────────────────────────────────

    @EventTarget(priority = 0)
    public void onPacket(PacketEvent event) {
        if (!isEnabled()) return;
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return;

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() != p.getEntityId()) return;

            velocityTimer.reset();
            IAccessorS12PacketEntityVelocity acc = (IAccessorS12PacketEntityVelocity) packet;

            switch (mode()) {
                case 0:
                    event.setCancelled(true);
                    if ((float) horizontal.getValue() == 0.0f && (float) vertical.getValue() == 0.0f) return;
                    if ((float) horizontal.getValue() != 0.0f) {
                        p.motionX = packet.getMotionX() / 8000.0 * (double)(float) horizontal.getValue();
                        p.motionZ = packet.getMotionZ() / 8000.0 * (double)(float) horizontal.getValue();
                    }
                    if ((float) vertical.getValue() != 0.0f)
                        p.motionY = packet.getMotionY() / 8000.0 * (double)(float) vertical.getValue();
                    break;
                case 1: case 3: case 5: case 6: case 9: case 13: case 19: case 21: case 22: case 23:
                    hasReceivedVelocity = true;
                    break;
                case 2:
                    if (jump && p.onGround) jump = false;
                    break;
                case 7: {
                    double mx = packet.getMotionX() / 8000.0;
                    double mz = packet.getMotionZ() / 8000.0;
                    if (Math.abs(Math.atan2(mx, mz) - Math.toRadians(p.rotationYaw)) < 2.0)
                        hasReceivedVelocity = true;
                    break;
                }
                case 8:
                    if (!p.onGround) return;
                    hasReceivedVelocity = true;
                    event.setCancelled(true);
                    break;
                case 10:
                    event.setCancelled(true);
                    break;
                case 11:
                    acc.setMotionX((int)(packet.getMotionX() * 0.33));
                    acc.setMotionZ((int)(packet.getMotionZ() * 0.33));
                    if (p.onGround) {
                        acc.setMotionX((int)(packet.getMotionX() * 0.86));
                        acc.setMotionZ((int)(packet.getMotionZ() * 0.86));
                    }
                    break;
                case 12:
                    acc.setMotionX((int)(packet.getMotionX() * -0.33));
                    acc.setMotionZ((int)(packet.getMotionZ() * -0.33));
                    if (p.onGround) {
                        acc.setMotionX((int)(packet.getMotionX() * 0.86));
                        acc.setMotionZ((int)(packet.getMotionZ() * 0.86));
                    }
                    break;
                case 14:
                    if (p.onGround || p.fallDistance < 0.5f) {
                        hasReceivedVelocity = true;
                        event.setCancelled(true);
                    }
                    break;
                case 15:
                    hasReceivedVelocity = true;
                    if (!p.onGround && !hypixelAbsorbed) {
                        event.setCancelled(true);
                        hypixelAbsorbed = true;
                        return;
                    }
                    acc.setMotionX((int)(p.motionX * 8000.0));
                    acc.setMotionZ((int)(p.motionZ * 8000.0));
                    break;
                case 16:
                    hasReceivedVelocity = true;
                    event.setCancelled(true);
                    break;
                case 17:
                    hasReceivedVelocity = true;
                    event.setCancelled(true);
                    PacketUtil.sendPacket(new C0BPacketEntityAction(p, Action.START_SNEAKING));
                    PacketUtil.sendPacket(new C0BPacketEntityAction(p, Action.STOP_SNEAKING));
                    break;
                case 18: {
                    if (p.isDead || p.isPlayerSleeping() || p.isInWater() || p.isInLava()) return;
                    double hStr = Math.sqrt((double)(packet.getMotionX() * packet.getMotionX()
                            + packet.getMotionZ() * packet.getMotionZ()));
                    if (hStr <= 1000.0) return;
                    Entity target = null;
                    if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof EntityLivingBase
                            && p.getDistanceToEntity(mc.objectMouseOver.entityHit) <= (float) grimRange.getValue())
                        target = mc.objectMouseOver.entityHit;
                    if (target == null) {
                        KillAura aura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
                        if (aura != null && aura.target != null && p.getDistanceToEntity(aura.target.getEntity()) <= (float) grimRange.getValue())
                            target = aura.target.getEntity();
                    }
                    if (target != null) {
                        boolean sprinting = p.isSprinting();
                        if (!sprinting) PacketUtil.sendPacket(new C0BPacketEntityAction(p, Action.START_SPRINTING));
                        for (int i = 0; i < (int) grimAttacks.getValue(); i++) {
                            PacketUtil.sendPacket(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
                            PacketUtil.sendPacket(new C0APacketAnimation());
                        }
                        if (!sprinting) PacketUtil.sendPacket(new C0BPacketEntityAction(p, Action.STOP_SPRINTING));
                        attacked = true;
                        event.setCancelled(true);
                    }
                    break;
                }
                case 20:
                    hasReceivedVelocity = true;
                    if (!p.onGround && !matrixAbsorbed) {
                        event.setCancelled(true);
                        matrixAbsorbed = true;
                        return;
                    }
                    acc.setMotionX(0);
                    acc.setMotionZ(0);
                    break;
                case 24: {
                    double x = packet.getMotionX() / 8000.0;
                    double z = packet.getMotionZ() / 8000.0;
                    if (x != 0.0 || z != 0.0) {
                        reduceYaw = (float)(Math.toDegrees(Math.atan2(-z, -x)) - 90.0);
                        shouldRotate = true;
                    }
                    if ((Boolean) predFakeCheck.getValue() && !allowNext) { allowNext = true; return; }
                    allowNext = true;
                    chanceCounter = chanceCounter % 100 + (int) predictionChance.getValue();
                    if (chanceCounter >= 100) {
                        jumpFlag = true;
                        if ((float) predictionHoriz.getValue() > 0.0f) {
                            p.motionX = x * (double)(float) predictionHoriz.getValue();
                            p.motionZ = z * (double)(float) predictionHoriz.getValue();
                        } else { p.motionX = 0.0; p.motionZ = 0.0; }
                        if ((float) predictionVert.getValue() > 0.0f)
                            p.motionY = packet.getMotionY() / 8000.0 * (double)(float) predictionVert.getValue();
                        else p.motionY = 0.0;
                        if ((Boolean) predDebug.getValue())
                            p.addChatMessage(new ChatComponentText(String.format(
                                    "Velocity (tick: %d, x: %.2f, y: %.2f, z: %.2f)",
                                    p.ticksExisted, x, packet.getMotionY() / 8000.0, z)));
                    } else event.setCancelled(true);
                    break;
                }
                default: break;
            }
        }

        if (event.getPacket() instanceof S27PacketExplosion) {
            S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
            IAccessorS27PacketExplosion acc = (IAccessorS27PacketExplosion) packet;
            if (mode() == 0) {
                if ((float) horizontal.getValue() == 0.0f && (float) vertical.getValue() == 0.0f)
                    event.setCancelled(true);
                else {
                    acc.setField_149152_f(acc.getField_149152_f() * (float) horizontal.getValue());
                    acc.setField_149153_g(acc.getField_149153_g() * (float) vertical.getValue());
                    acc.setField_149159_h(acc.getField_149159_h() * (float) horizontal.getValue());
                }
            } else if (mode() == 7) {
                hasReceivedVelocity = true;
            } else if (mode() == 24) {
                if ((Boolean) predDebug.getValue())
                    p.addChatMessage(new ChatComponentText(String.format(
                            "Explosion (tick: %d, x: %.2f, y: %.2f, z: %.2f)",
                            p.ticksExisted, p.motionX + packet.getExplosionStrengthX(),
                            p.motionY + packet.getExplosionStrengthY(),
                            p.motionZ + packet.getExplosionStrengthZ())));
                if ((float) predictionHoriz.getValue() == 0.0f || (float) predictionVert.getValue() == 0.0f)
                    event.setCancelled(true);
            }
        }

        if (mode() == 10 && event.getPacket() instanceof S32PacketConfirmTransaction) {
            event.setCancelled(true);
            S32PacketConfirmTransaction cp = (S32PacketConfirmTransaction) event.getPacket();
            PacketUtil.sendPacket(new C0FPacketConfirmTransaction(cp.getWindowId(), cp.getActionNumber(), vulcanTrans));
            vulcanTrans = !vulcanTrans;
        }
    }

    // ── Jump ──────────────────────────────────────────────────────────────────

    @EventTarget
    public void onJump(JumpEvent event) {
        if (mode() == 2) {
            jump = true;
            if (!mc.thePlayer.capabilities.allowFlying) event.setCancelled(true);
        } else if (mode() == 3 && mc.thePlayer.hurtTime > 0) {
            event.setCancelled(true);
        }
    }

    // ── Attack ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (mode() == 13) {
            if (mc.thePlayer.hurtTime == 9 && System.currentTimeMillis() - lastAttackTime <= 8000L) {
                mc.thePlayer.motionX *= (double)(float) intaveReduceFactor.getValue();
                mc.thePlayer.motionZ *= (double)(float) intaveReduceFactor.getValue();
            }
            lastAttackTime = System.currentTimeMillis();
        }
    }

    // ── Strafe ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (mode() == 7 && hasReceivedVelocity) {
            if (!((IAccessorEntityLivingBase) mc.thePlayer).isJumping()
                    && RandomUtil.nextInt(0, 100) < (int) chance.getValue()
                    && limitUntilJump >= (int) ticksUntilJump.getValue()
                    && mc.thePlayer.isSprinting()
                    && mc.thePlayer.onGround
                    && mc.thePlayer.hurtTime == 9) {
                mc.thePlayer.jump();
                limitUntilJump = 0;
            }
            hasReceivedVelocity = false;
        }
        if (mc.thePlayer.hurtTime == 9) limitUntilJump++;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Override
    public String[] getSuffix() {
        return new String[]{ CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mode.getModeString()) };
    }

    private Rotation getRotations(Entity e) {
        double x = e.posX - mc.thePlayer.posX;
        double z = e.posZ - mc.thePlayer.posZ;
        double y = e.posY + e.getEyeHeight() - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dist = MathHelper.sqrt_double(x * x + z * z);
        float yaw   = (float)(Math.atan2(z, x) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float)(-(Math.atan2(y, dist) * 180.0 / Math.PI));
        return new Rotation(yaw, pitch);
    }

    private float getRotDiff(Rotation a, Rotation b) {
        return Math.abs(MathHelper.wrapAngleTo180_float(a.yaw - b.yaw));
    }
}
