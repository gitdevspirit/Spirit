package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.mixin.*;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.MoveUtil;
import myau.util.PacketUtil;
import myau.util.RandomUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
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

    // ── Settings (visible in ClickGUI) ────────────────────────────────────────
    public final DropdownSetting mode = register(new DropdownSetting("Mode", 0,
            "Simple","AAC","AACPush","AACZero","AACv4","Reverse","SmoothReverse",
            "Jump","Glitch","Legit","Vulcan","MatrixReduce","MatrixReducePlus",
            "IntaveReduce","GrimC03","Hypixel","HypixelAir","BlockSMC",
            "GrimCombat","Polar","MatrixNoXZ","Intave13","SmartJumpReset",
            "Intave14","HypixelPrediction"));

    // Simple / Legit
    public final SliderSetting horizontal = register(new SliderSetting("Horizontal", 0, -2, 2, 0.01,
            () -> mode() == 0 || mode() == 9));
    public final SliderSetting vertical   = register(new SliderSetting("Vertical",   0, -2, 2, 0.01,
            () -> mode() == 0 || mode() == 9));

    // HypixelPrediction
    public final SliderSetting  predChance     = register(new SliderSetting("Pred Chance",      100, 0, 100, 1, () -> mode() == 24));
    public final SliderSetting  predHorizontal = register(new SliderSetting("Pred Horizontal",  0,   0, 1,  0.01, () -> mode() == 24));
    public final SliderSetting  predVertical   = register(new SliderSetting("Pred Vertical",    1,   0, 1,  0.01, () -> mode() == 24));
    public final BooleanSetting predFakeCheck  = register(new BooleanSetting("Pred Fake Check", false, () -> mode() == 24));
    public final BooleanSetting predDebug      = register(new BooleanSetting("Pred Debug",      false, () -> mode() == 24));

    // Reverse
    public final SliderSetting  reverseStr     = register(new SliderSetting("Reverse Strength", 1, 0.1, 1, 0.01, () -> mode() == 5));
    public final SliderSetting  smoothRevStr   = register(new SliderSetting("Smooth Rev Str",   0.05, 0.02, 0.1, 0.01, () -> mode() == 6));
    public final BooleanSetting onLook         = register(new BooleanSetting("On Look",         false, () -> mode() == 5 || mode() == 6));
    public final SliderSetting  maxAngle       = register(new SliderSetting("Max Angle",        45, 5, 90, 1,
            () -> (mode() == 5 || mode() == 6) && onLook.getValue()));

    // AACPush
    public final SliderSetting  aacPushXZ = register(new SliderSetting("AAC Push XZ", 2, 1, 3, 0.01, () -> mode() == 2));
    public final BooleanSetting aacPushY  = register(new BooleanSetting("AAC Push Y", true, () -> mode() == 2));

    // AACv4
    public final SliderSetting aacv4Reduce = register(new SliderSetting("AACv4 Reduce", 0.62, 0, 1, 0.01, () -> mode() == 4));

    // Jump / Legit
    public final SliderSetting chance        = register(new SliderSetting("Chance",     100, 0, 100, 1, () -> mode() == 7 || mode() == 9));
    public final SliderSetting ticksUntilJump= register(new SliderSetting("Jump Ticks", 4,   0, 20,  1, () -> mode() == 7));

    // IntaveReduce
    public final SliderSetting intaveReduceFactor = register(new SliderSetting("Reduce Factor", 0.6, 0, 1, 0.01, () -> mode() == 13));

    // SmartJumpReset
    public final BooleanSetting smartJumpSneak = register(new BooleanSetting("Sneak Reduce", false, () -> mode() == 22));
    public final BooleanSetting smartJumpBack  = register(new BooleanSetting("Backward",     false, () -> mode() == 22));

    // GrimCombat
    public final SliderSetting grimRange   = register(new SliderSetting("Grim Range",   3.5, 0, 6, 0.1, () -> mode() == 18));
    public final SliderSetting grimAttacks = register(new SliderSetting("Grim Attacks", 12,  1, 16, 1,  () -> mode() == 18));

    // Intave14
    public final SliderSetting intave14T1 = register(new SliderSetting("Intave14 T1", 0.3, 0.1, 2,  0.01, () -> mode() == 23));
    public final SliderSetting intave14T2 = register(new SliderSetting("Intave14 T2", 5.0, 1,   10, 0.01, () -> mode() == 23));

    // ── State ─────────────────────────────────────────────────────────────────
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

    public Velocity() { super("Velocity", false, false); }

    private int mode() { return mode.getIndex(); }

    @Override
    public void onDisabled() {
        ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0f;
        timerTicks = 0; limitUntilJump = 0;
        chanceCounter = 0; allowNext = true;
        shouldRotate = false; attackTimer = -1;
        lastHurtTime = 0; jumpFlag = false;
        reset();
    }

    private void reset() {
        hasReceivedVelocity = false; attacked = false;
        hypixelAbsorbed = false; matrixAbsorbed = false;
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
                        p.motionX *= horizontal.getValue();
                        p.motionZ *= horizontal.getValue();
                        hasReceivedVelocity = false;
                    }
                    break;
                case 2:
                    if (jump) { if (p.onGround) jump = false; }
                    else {
                        if (p.hurtTime > 0 && p.motionX != 0.0 && p.motionZ != 0.0) p.onGround = true;
                        if (p.ticksExisted > 0 && aacPushY.getValue()) p.motionY -= 0.014999993;
                    }
                    if (p.ticksExisted >= 19) {
                        p.motionX /= aacPushXZ.getValue();
                        p.motionZ /= aacPushXZ.getValue();
                    }
                    break;
                case 3:
                    if (p.hurtTime > 0) {
                        if (!hasReceivedVelocity || p.onGround || p.fallDistance > 2.0f) return;
                        p.motionY--; p.isAirBorne = true; p.onGround = true;
                    } else hasReceivedVelocity = false;
                    break;
                case 4:
                    if (p.hurtTime > 0 && !p.onGround) {
                        p.motionX *= aacv4Reduce.getValue();
                        p.motionZ *= aacv4Reduce.getValue();
                    }
                    break;
                case 5:
                    if (hasReceivedVelocity) {
                        if (!p.onGround) {
                            if (onLook.getValue()) {
                                KillAura aura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
                                Entity target = aura != null ? aura.getTarget() : null;
                                if (target != null && getRotDiff(p.rotationYaw, getTargetYaw(target)) > maxAngle.getValue())
                                    return;
                            }
                            MoveUtil.setSpeed(MoveUtil.getSpeed() * reverseStr.getValue());
                        } else if (velocityTimer.hasTimeElapsed(80L)) hasReceivedVelocity = false;
                    }
                    break;
                case 6:
                    if (hasReceivedVelocity) {
                        KillAura aura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
                        Entity target = aura != null ? aura.getTarget() : null;
                        if (target == null) {
                            // no smoothReverse without setSpeedInAir
                        } else if (onLook.getValue() && getRotDiff(p.rotationYaw, getTargetYaw(target)) > maxAngle.getValue()) {
                            hasReceivedVelocity = false;
                        } else if (!p.onGround) {
                            // smooth reduce via motionX/Z
                            p.motionX *= smoothRevStr.getValue() / 0.02;
                            p.motionZ *= smoothRevStr.getValue() / 0.02;
                        } else if (velocityTimer.hasTimeElapsed(80L)) {
                            hasReceivedVelocity = false;
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
                            if (((IAccessorEntityLivingBase) p).getJumpTicks() <= 0) p.jump();
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
                        if (p.onGround && ((IAccessorEntityLivingBase) p).getJumpTicks() <= 0) p.jump();
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
                        boolean fwd = org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode());
                        if (smartJumpBack.getValue()) {
                            if (p.hurtTime > 1) {
                                ((IAccessorKeyBinding) mc.gameSettings.keyBindForward).setPressed(false);
                                ((IAccessorKeyBinding) mc.gameSettings.keyBindBack).setPressed(true);
                                ((IAccessorKeyBinding) mc.gameSettings.keyBindSprint).setPressed(true);
                            } else if (mc.currentScreen == null) {
                                ((IAccessorKeyBinding) mc.gameSettings.keyBindForward).setPressed(org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()));
                                ((IAccessorKeyBinding) mc.gameSettings.keyBindBack).setPressed(org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()));
                                ((IAccessorKeyBinding) mc.gameSettings.keyBindSprint).setPressed(org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindSprint.getKeyCode()));
                            }
                        }
                        if (p.onGround && p.hurtTime >= 8 && fwd) {
                            p.jump();
                            p.motionX *= 0.9999999;
                            p.motionZ *= 0.9999999;
                        }
                        if (smartJumpSneak.getValue()) {
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
                    if      (p.hurtTime == 9)                          timer.timerSpeed = (float) intave14T1.getValue();
                    else if (p.hurtTime >= 3 && p.hurtTime <= 8)      timer.timerSpeed = (float) intave14T2.getValue();
                    else                                               timer.timerSpeed = 1.0f;
                    break;
                }
                case 24: {
                    int ht = p.hurtTime;
                    if (ht > lastHurtTime) {
                        KillAura aura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
                        EntityLivingBase target = aura != null && aura.isEnabled() ? aura.getTarget() : null;
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
                        EntityLivingBase target = aura != null && aura.isEnabled() ? aura.getTarget() : null;
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
            } else if (timer.timerSpeed <= 1.0f) timer.timerSpeed = 1.0f;
        }
    }

    // ── Packet ────────────────────────────────────────────────────────────────

    @EventTarget(0)
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
                    if (horizontal.getValue() == 0 && vertical.getValue() == 0) return;
                    if (horizontal.getValue() != 0) {
                        p.motionX = packet.getMotionX() / 8000.0 * horizontal.getValue();
                        p.motionZ = packet.getMotionZ() / 8000.0 * horizontal.getValue();
                    }
                    if (vertical.getValue() != 0)
                        p.motionY = packet.getMotionY() / 8000.0 * vertical.getValue();
                    break;
                case 1: case 3: case 5: case 6: case 9: case 13: case 19: case 21: case 22: case 23:
                    hasReceivedVelocity = true; break;
                case 2:
                    if (jump && p.onGround) jump = false; break;
                case 7: {
                    double mx = packet.getMotionX() / 8000.0;
                    double mz = packet.getMotionZ() / 8000.0;
                    if (Math.abs(Math.atan2(mx, mz) - Math.toRadians(p.rotationYaw)) < 2.0)
                        hasReceivedVelocity = true;
                    break;
                }
                case 8:
                    if (!p.onGround) return;
                    hasReceivedVelocity = true; event.setCancelled(true); break;
                case 10:
                    event.setCancelled(true); break;
                case 11:
                    acc.setMotionX((int)(packet.getMotionX() * 0.33));
                    acc.setMotionZ((int)(packet.getMotionZ() * 0.33));
                    if (p.onGround) { acc.setMotionX((int)(packet.getMotionX() * 0.86)); acc.setMotionZ((int)(packet.getMotionZ() * 0.86)); }
                    break;
                case 12:
                    acc.setMotionX((int)(packet.getMotionX() * -0.33));
                    acc.setMotionZ((int)(packet.getMotionZ() * -0.33));
                    if (p.onGround) { acc.setMotionX((int)(packet.getMotionX() * 0.86)); acc.setMotionZ((int)(packet.getMotionZ() * 0.86)); }
                    break;
                case 14:
                    if (p.onGround || p.fallDistance < 0.5f) { hasReceivedVelocity = true; event.setCancelled(true); }
                    break;
                case 15:
                    hasReceivedVelocity = true;
                    if (!p.onGround && !hypixelAbsorbed) { event.setCancelled(true); hypixelAbsorbed = true; return; }
                    acc.setMotionX((int)(p.motionX * 8000.0));
                    acc.setMotionZ((int)(p.motionZ * 8000.0));
                    break;
                case 16:
                    hasReceivedVelocity = true; event.setCancelled(true); break;
                case 17:
                    hasReceivedVelocity = true; event.setCancelled(true);
                    PacketUtil.sendPacket(new C0BPacketEntityAction(p, Action.START_SNEAKING));
                    PacketUtil.sendPacket(new C0BPacketEntityAction(p, Action.STOP_SNEAKING));
                    break;
                case 18: {
                    if (p.isDead || p.isPlayerSleeping() || p.isInWater() || p.isInLava()) return;
                    double hStr = Math.sqrt((double)(packet.getMotionX() * packet.getMotionX() + packet.getMotionZ() * packet.getMotionZ()));
                    if (hStr <= 1000.0) return;
                    Entity target = null;
                    if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof EntityLivingBase
                            && p.getDistanceToEntity(mc.objectMouseOver.entityHit) <= grimRange.getValue())
                        target = mc.objectMouseOver.entityHit;
                    if (target == null) {
                        KillAura aura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
                        if (aura != null && aura.getTarget() != null && p.getDistanceToEntity(aura.getTarget()) <= grimRange.getValue())
                            target = aura.getTarget();
                    }
                    if (target != null) {
                        boolean spr = p.isSprinting();
                        if (!spr) PacketUtil.sendPacket(new C0BPacketEntityAction(p, Action.START_SPRINTING));
                        for (int i = 0; i < (int) grimAttacks.getValue(); i++) {
                            PacketUtil.sendPacket(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
                            PacketUtil.sendPacket(new C0APacketAnimation());
                        }
                        if (!spr) PacketUtil.sendPacket(new C0BPacketEntityAction(p, Action.STOP_SPRINTING));
                        attacked = true; event.setCancelled(true);
                    }
                    break;
                }
                case 20:
                    hasReceivedVelocity = true;
                    if (!p.onGround && !matrixAbsorbed) { event.setCancelled(true); matrixAbsorbed = true; return; }
                    acc.setMotionX(0); acc.setMotionZ(0); break;
                case 24: {
                    double x = packet.getMotionX() / 8000.0;
                    double z = packet.getMotionZ() / 8000.0;
                    if (x != 0.0 || z != 0.0) { reduceYaw = (float)(Math.toDegrees(Math.atan2(-z, -x)) - 90.0); shouldRotate = true; }
                    if (predFakeCheck.getValue() && !allowNext) { allowNext = true; return; }
                    allowNext = true;
                    chanceCounter = chanceCounter % 100 + (int) predChance.getValue();
                    if (chanceCounter >= 100) {
                        jumpFlag = true;
                        if (predHorizontal.getValue() > 0) { p.motionX = x * predHorizontal.getValue(); p.motionZ = z * predHorizontal.getValue(); }
                        else { p.motionX = 0; p.motionZ = 0; }
                        p.motionY = predVertical.getValue() > 0 ? packet.getMotionY() / 8000.0 * predVertical.getValue() : 0;
                        if (predDebug.getValue())
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
                if (horizontal.getValue() == 0 && vertical.getValue() == 0) event.setCancelled(true);
                else {
                    acc.setField_149152_f(acc.getField_149152_f() * (float) horizontal.getValue());
                    acc.setField_149153_g(acc.getField_149153_g() * (float) vertical.getValue());
                    acc.setField_149159_h(acc.getField_149159_h() * (float) horizontal.getValue());
                }
            } else if (mode() == 7) {
                hasReceivedVelocity = true;
            } else if (mode() == 24) {
                if (predDebug.getValue())
                    p.addChatMessage(new ChatComponentText(String.format(
                            "Explosion (tick: %d, x: %.2f, y: %.2f, z: %.2f)",
                            p.ticksExisted, p.motionX + packet.func_149149_c(),
                            p.motionY + packet.func_149144_d(), p.motionZ + packet.func_149147_e())));
                if (predHorizontal.getValue() == 0 || predVertical.getValue() == 0) event.setCancelled(true);
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
                mc.thePlayer.motionX *= intaveReduceFactor.getValue();
                mc.thePlayer.motionZ *= intaveReduceFactor.getValue();
            }
            lastAttackTime = System.currentTimeMillis();
        }
    }

    // ── Strafe ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (mode() == 7 && hasReceivedVelocity) {
            if (((IAccessorEntityLivingBase) mc.thePlayer).getJumpTicks() <= 0
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
        return new String[]{ CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mode.getOptions()[mode.getIndex()]) };
    }

    private float getTargetYaw(Entity e) {
        double x = e.posX - mc.thePlayer.posX;
        double z = e.posZ - mc.thePlayer.posZ;
        return (float)(Math.atan2(z, x) * 180.0 / Math.PI) - 90.0f;
    }

    private float getRotDiff(float yawA, float yawB) {
        return Math.abs(MathHelper.wrapAngleTo180_float(yawA - yawB));
    }
}
