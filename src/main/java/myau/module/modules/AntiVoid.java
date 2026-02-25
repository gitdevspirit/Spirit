package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.KeyEvent;
import myau.events.PlayerUpdateEvent;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.PlayerUtil;
import myau.util.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.util.AxisAlignedBB;

public class AntiVoid extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private boolean isInVoid  = false;
    private boolean wasInVoid = false;
    private double[] lastSafePosition = null;

    public final DropdownSetting mode     = new DropdownSetting("Mode",     0, "BLINK");
    public final SliderSetting   distance = new SliderSetting("Distance", 5.0, 0.0, 16.0, 0.5);

    public AntiVoid() {
        super("AntiVoid", false);
        register(mode);
        register(distance);
    }

    private void resetBlink() {
        Myau.blinkManager.setBlinkState(false, BlinkModules.ANTI_VOID);
        lastSafePosition = null;
    }

    private boolean canUseAntiVoid() {
        LongJump lj = (LongJump) Myau.moduleManager.modules.get(LongJump.class);
        return !lj.isJumping();
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(PlayerUpdateEvent event) {
        if (isEnabled()) {
            isInVoid = !mc.thePlayer.capabilities.allowFlying && PlayerUtil.isInWater();
            if (mode.getIndex() == 0) {
                if (!isInVoid) resetBlink();
                if (lastSafePosition != null) {
                    float sub = mc.thePlayer.width / 2.0F;
                    float h   = mc.thePlayer.height;
                    if (PlayerUtil.checkInWater(new AxisAlignedBB(
                            lastSafePosition[0] - sub, lastSafePosition[1], lastSafePosition[2] - sub,
                            lastSafePosition[0] + sub, lastSafePosition[1] + h, lastSafePosition[2] + sub)))
                        resetBlink();
                }
                if (!wasInVoid && isInVoid && canUseAntiVoid()) {
                    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    if (Myau.blinkManager.setBlinkState(true, BlinkModules.ANTI_VOID))
                        lastSafePosition = new double[]{mc.thePlayer.prevPosX, mc.thePlayer.prevPosY, mc.thePlayer.prevPosZ};
                }
                if (Myau.blinkManager.getBlinkingModule() == BlinkModules.ANTI_VOID
                        && lastSafePosition != null
                        && lastSafePosition[1] - distance.getValue() > mc.thePlayer.posY) {
                    Myau.blinkManager.blinkedPackets.offerFirst(
                            new C04PacketPlayerPosition(
                                    lastSafePosition[0],
                                    lastSafePosition[1] - RandomUtil.nextDouble(10.0, 20.0),
                                    lastSafePosition[2], false));
                    resetBlink();
                }
            }
            wasInVoid = isInVoid;
        }
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (event.getKey() == mc.gameSettings.keyBindUseItem.getKeyCode()) {
            ItemStack item = mc.thePlayer.inventory.getCurrentItem();
            if (item != null && item.getItem() instanceof ItemEnderPearl) resetBlink();
        }
    }

    @Override public void onEnabled()  { isInVoid = false; wasInVoid = false; resetBlink(); }
    @Override public void onDisabled() { Myau.blinkManager.setBlinkState(false, BlinkModules.ANTI_VOID); }
    @Override public String[] getSuffix() { return new String[]{mode.getValue()}; }
}
