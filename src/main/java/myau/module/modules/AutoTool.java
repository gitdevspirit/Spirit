package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.KeyBindUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class AutoTool extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int currentToolSlot  = -1;
    private int previousSlot     = -1;
    private int tickDelayCounter = 0;

    public final SliderSetting  switchDelay = new SliderSetting("Delay", 0, 0, 5, 1);
    public final BooleanSetting switchBack  = new BooleanSetting("Switch Back", true);
    public final BooleanSetting sneakOnly   = new BooleanSetting("Sneak Only", true);

    public AutoTool() {
        super("AutoTool", false);
        register(switchDelay);
        register(switchBack);
        register(sneakOnly);
    }

    public boolean isKillAura() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (!killAura.isEnabled()) return false;
        return TeamUtil.isEntityLoaded(killAura.getTarget()) && killAura.isAttackAllowed();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.currentToolSlot != -1 && this.currentToolSlot != mc.thePlayer.inventory.currentItem) {
                this.currentToolSlot = -1;
                this.previousSlot    = -1;
            }
            if (mc.objectMouseOver != null
                    && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK
                    && mc.gameSettings.keyBindAttack.isKeyDown()
                    && !mc.thePlayer.isUsingItem()
                    && !isKillAura()) {
                if (this.tickDelayCounter >= (int) this.switchDelay.getValue()
                        && (!this.sneakOnly.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()))) {
                    int slot = ItemUtil.findInventorySlot(
                            mc.thePlayer.inventory.currentItem,
                            mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock());
                    if (mc.thePlayer.inventory.currentItem != slot) {
                        if (this.previousSlot == -1) this.previousSlot = mc.thePlayer.inventory.currentItem;
                        mc.thePlayer.inventory.currentItem = this.currentToolSlot = slot;
                    }
                }
                this.tickDelayCounter++;
            } else {
                if (this.switchBack.getValue() && this.previousSlot != -1)
                    mc.thePlayer.inventory.currentItem = this.previousSlot;
                this.currentToolSlot  = -1;
                this.previousSlot     = -1;
                this.tickDelayCounter = 0;
            }
        }
    }

    @Override
    public void onDisabled() {
        this.currentToolSlot  = -1;
        this.previousSlot     = -1;
        this.tickDelayCounter = 0;
    }
}
