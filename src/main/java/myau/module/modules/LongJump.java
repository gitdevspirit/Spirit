package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.management.RotationState;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemFireball;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

public class LongJump extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil fireballTimer = new TimerUtil();
    private final TimerUtil jumpTimer     = new TimerUtil();
    private boolean isJumping        = false;
    private int     tickCounter      = 0;
    private int     jumpModeStage    = 0;
    private boolean readyToUseFireball = false;
    private boolean fireballLaunched   = false;
    private int     savedHotbarSlot    = -1;

    public final DropdownSetting mode        = new DropdownSetting("Mode", 0, "FIREBALL", "FIREBALL_MANUAL", "FIREBALL_HIGH", "FIREBALL_FLAT");
    public final SliderSetting   motion      = new SliderSetting("Motion",       1.0, 1.0, 20.0, 0.1);
    public final SliderSetting   speedMotion = new SliderSetting("Speed Motion", 1.0, 1.0, 20.0, 0.1);
    public final SliderSetting   strafe      = new SliderSetting("Strafe",         0,   0,   100, 1);
    public final BooleanSetting  onyaw       = new BooleanSetting("Yaw",          false);
    public final BooleanSetting  autolag     = new BooleanSetting("Auto Lag",     false);

    public LongJump() {
        super("LongJump", false);
        register(mode); register(motion); register(speedMotion);
        register(strafe); register(onyaw); register(autolag);
    }

    private int findFireballInHotbar() {
        if (mc.thePlayer == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemFireball) return i;
        }
        return -1;
    }

    private double getMotionFactor() {
        return MoveUtil.getSpeedLevel() > 0 ? speedMotion.getValue() : motion.getValue();
    }

    public boolean isAutoMode()     { int m = mode.getIndex(); return m == 0 || m == 2 || m == 3; }
    public boolean isManualMode()   { return mode.getIndex() == 1; }
    public boolean isLongJumpMode() { return isAutoMode() || isManualMode(); }
    public boolean canStartJump()   { return !fireballTimer.hasTimeElapsed(1000L) && !isJumping; }
    public boolean isJumping()      { return isJumping; }

    @EventTarget(Priority.HIGHEST)
    public void onKnockback(KnockbackEvent event) {
        if (isEnabled() && !event.isCancelled() && (isManualMode() || isAutoMode()) && canStartJump()) {
            event.setCancelled(true);
            isJumping = true;
            tickCounter = 0;
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (!isEnabled()) return;
        switch (event.getType()) {
            case PRE:
                if (isAutoMode() && !fireballLaunched && readyToUseFireball) {
                    int slot = findFireballInHotbar();
                    if (slot != -1) {
                        savedHotbarSlot = mc.thePlayer.inventory.currentItem;
                        mc.thePlayer.inventory.currentItem = slot;
                        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                        fireballTimer.reset();
                        fireballLaunched = true;
                    }
                }
                break;
            case POST:
                if (savedHotbarSlot != -1) {
                    mc.thePlayer.inventory.currentItem = savedHotbarSlot;
                    savedHotbarSlot = -1;
                }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (isLongJumpMode() && isJumping) {
            tickCounter++;
            if (tickCounter == 1) {
                switch (mode.getIndex()) {
                    case 0: case 1: jumpModeStage = 0; break;
                    case 2: jumpModeStage = 1; break;
                    case 3: jumpModeStage = MoveUtil.isForwardPressed() ? 2 : 1; break;
                }
            }
            if (tickCounter == 2 && MoveUtil.isForwardPressed())
                MoveUtil.setSpeed(MoveUtil.getSpeed() * getMotionFactor());
            if (tickCounter >= 1 && tickCounter <= 30) {
                switch (jumpModeStage) {
                    case 1:
                        if (tickCounter == 1) { mc.thePlayer.motionY *= 0.75; }
                        else { double m = mc.thePlayer.motionY / 0.98F + 0.055; if (m > 0.0) mc.thePlayer.motionY = m; }
                        break;
                    case 2:
                        if (tickCounter == 1) { mc.thePlayer.motionY *= 0.75; }
                        else { mc.thePlayer.motionY = 0.01 + tickCounter * 0.003; }
                        break;
                }
            }
            if (tickCounter >= 30) {
                isJumping = false; tickCounter = 0; jumpModeStage = 0;
                if (isAutoMode()) setEnabled(false);
                return;
            }
        }
        if (isAutoMode() && !isJumping) {
            if (jumpTimer.hasTimeElapsed(1500L)) { setEnabled(false); return; }
            readyToUseFireball = true;
            float yaw = !onyaw.getValue()
                    ? mc.thePlayer.rotationYaw
                    : RotationUtil.quantizeAngle(mc.thePlayer.rotationYaw - 180.0F - RandomUtil.nextFloat(0.0F, 1.0F));
            float pitch = RotationUtil.quantizeAngle(89.0F + RandomUtil.nextFloat(-0.25F, 0.25F));
            event.setRotation(yaw, pitch, 4);
            event.setPervRotation(yaw, 4);
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (isEnabled() && RotationState.isActived()
                && RotationState.getPriority() == 4.0F && MoveUtil.isForwardPressed())
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!isEnabled() || !isLongJumpMode() || !isJumping) return;
        if (tickCounter < 5 || tickCounter > 30 || strafe.getValue() <= 0) return;
        double speed = MoveUtil.getSpeed();
        MoveUtil.setSpeed(speed * ((100 - strafe.getValue()) / 100.0), MoveUtil.getDirectionYaw());
        MoveUtil.addSpeed(speed * (strafe.getValue() / 100.0), MoveUtil.getMoveYaw());
        MoveUtil.setSpeed(speed);
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (event.getKey() == mc.gameSettings.keyBindUseItem.getKeyCode()) {
            ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
            if (stack != null && stack.getItem() instanceof ItemFireball) fireballTimer.reset();
        }
    }

    @EventTarget(Priority.HIGH)
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE && !event.isCancelled()
                && event.getPacket() instanceof S08PacketPlayerPosLook) {
            isJumping = false; tickCounter = 0; jumpModeStage = 0;
            if (isAutoMode()) setEnabled(false);
        }
    }

    @Override
    public void onEnabled() {
        jumpTimer.reset();
        if (isAutoMode() && findFireballInHotbar() == -1) {
            setEnabled(false);
            ChatUtil.sendFormatted(String.format("%s%s: &cNo fireball found in your hotbar!&r", Myau.clientName, getName()));
        } else if (autolag.getValue()) {
            Myau.moduleManager.modules.get(ServerLag.class).setEnabled(true);
        }
    }

    @Override
    public void onDisabled() {
        isJumping = false; tickCounter = 0; jumpModeStage = 0;
        readyToUseFireball = false; fireballLaunched = false;
    }

    @Override
    public String[] getSuffix() {
        String m = mode.getOptions()[mode.getIndex()];
        return m.contains("FIREBALL") ? new String[]{"Fireball"}
                : new String[]{ CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, m) };
    }
}