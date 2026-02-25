package myau.module.modules;

import myau.event.EventTarget;
import myau.events.TickEvent;
import myau.mixin.IAccessorEntityLivingBase;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;

public class Sprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private boolean wasSprinting = false;
    public final DropdownSetting mode   = register(new DropdownSetting("Mode", 0, "RAGE", "LEGIT", "OMNI"));
    public final BooleanSetting foxFix  = register(new BooleanSetting("FOV Fix", true));

    public Sprint() {
        super("Sprint", true, true);
    }

    public boolean shouldApplyFovFix(IAttributeInstance attribute) {
        if (!this.foxFix.getValue()) {
            return false;
        } else {
            AttributeModifier attributeModifier = ((IAccessorEntityLivingBase) mc.thePlayer).getSprintingSpeedBoostModifier();
            return attribute.getModifier(attributeModifier.getID()) == null && this.wasSprinting;
        }
    }

    public boolean shouldKeepFov(boolean boolean2) {
        return this.foxFix.getValue() && !boolean2 && this.wasSprinting;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            switch (event.getType()) {
                case PRE:
                    boolean shouldSprint = mode.getIndex() == 0  // RAGE
                        || (mode.getIndex() == 1 && (mc.thePlayer.moveForward > 0 || mc.thePlayer.moveStrafing != 0))  // LEGIT
                        || mode.getIndex() == 2;  // OMNI
                    if (shouldSprint) KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
                    break;
                case POST:
                    this.wasSprinting = mc.thePlayer.isSprinting();
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void onDisabled() {
        this.wasSprinting = false;
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSprint.getKeyCode());
    }
}
