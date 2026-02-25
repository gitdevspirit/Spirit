package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorC0DPacketCloseWindow;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.KeyBindUtil;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InvWalk extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Queue<C0EPacketClickWindow> clickQueue = new ConcurrentLinkedQueue<>();
    private boolean keysPressed = false;
    private C16PacketClientStatus pendingStatus = null;
    private int delayTicks = 0;
    private int openDelayTicks = -1;
    private int closeDelayTicks = -1;
    private final Map<KeyBinding, Boolean> movementKeys = new HashMap<KeyBinding, Boolean>(8) {{
        put(mc.gameSettings.keyBindForward, false);
        put(mc.gameSettings.keyBindBack,    false);
        put(mc.gameSettings.keyBindLeft,    false);
        put(mc.gameSettings.keyBindRight,   false);
        put(mc.gameSettings.keyBindJump,    false);
        put(mc.gameSettings.keyBindSneak,   false);
        put(mc.gameSettings.keyBindSprint,  false);
    }};

    public final DropdownSetting mode        = register(new DropdownSetting("Mode",         1, "VANILLA", "LEGIT", "HYPIXEL", "LEGIT+"));
    public final BooleanSetting  guiEnabled  = register(new BooleanSetting("Click GUI",     true));
    public final SliderSetting   openDelay   = register(new SliderSetting("Open Delay",     0, 0, 20, 1));
    public final SliderSetting   closeDelay  = register(new SliderSetting("Close Delay",    4, 0, 20, 1));
    public final BooleanSetting  lockMoveKey = register(new BooleanSetting("Lock Move Key", false));

    public InvWalk() {
        super("InvWalk", false);
    }

    public void pressMovementKeys(boolean skipSneak) {
        movementKeys.keySet().stream()
                .filter(k -> !skipSneak || k != mc.gameSettings.keyBindSneak)
                .forEach(k -> KeyBindUtil.updateKeyState(k.getKeyCode()));
        if (Myau.moduleManager.modules.get(Sprint.class).isEnabled())
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        keysPressed = true;
    }

    public void resetMovementKeys()   { movementKeys.replaceAll((k, v) -> false); }
    public boolean isSetMovementKeys() { return movementKeys.values().stream().anyMatch(Boolean::booleanValue); }

    public void storeMovementKeys() {
        movementKeys.replaceAll((k, v) -> KeyBindUtil.isKeyDown(k.getKeyCode()));
    }

    public void restoreMovementKeys() {
        for (Map.Entry<KeyBinding, Boolean> e : movementKeys.entrySet())
            KeyBindUtil.setKeyBindState(e.getKey().getKeyCode(), e.getValue());
        if (Myau.moduleManager.modules.get(Sprint.class).isEnabled())
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        keysPressed = true;
    }

    public boolean canInvWalk() {
        if (!(mc.currentScreen instanceof GuiContainer)) return false;
        if (mc.currentScreen instanceof GuiContainerCreative) return false;
        switch (mode.getIndex()) {
            case 0: return true;
            case 1: return mc.currentScreen instanceof GuiInventory && pendingStatus != null && clickQueue.isEmpty();
            case 2: return delayTicks == 0 && clickQueue.isEmpty();
            case 3: return mc.currentScreen instanceof GuiInventory && closeDelayTicks == -1 && clickQueue.isEmpty();
            default: return false;
        }
    }

    public boolean temporaryStackIsEmpty() {
        if (mc.thePlayer.inventory.getItemStack() != null) return false;
        if (mc.thePlayer.inventoryContainer instanceof ContainerPlayer) {
            ContainerPlayer cp = (ContainerPlayer) mc.thePlayer.inventoryContainer;
            for (int i = 0; i < cp.craftMatrix.getSizeInventory(); i++)
                if (cp.craftMatrix.getStackInSlot(i) != null) return false;
        }
        return true;
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (openDelayTicks >= 0) { openDelayTicks--; return; }
        while (!clickQueue.isEmpty()) PacketUtil.sendPacketNoEvent(clickQueue.poll());
        if (closeDelayTicks > 0) {
            if (temporaryStackIsEmpty()) closeDelayTicks--;
        } else if (closeDelayTicks == 0) {
            if (mc.currentScreen instanceof GuiInventory) PacketUtil.sendPacketNoEvent(new C0DPacketCloseWindow(0));
            closeDelayTicks = -1;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.currentScreen instanceof myau.ui.clickgui.Rise6ClickGui && guiEnabled.getValue()) {
            pressMovementKeys(true); return;
        }
        if (canInvWalk()) {
            if (isSetMovementKeys() && lockMoveKey.getValue()) restoreMovementKeys();
            else pressMovementKeys(true);
        } else {
            if (keysPressed) {
                if (mc.currentScreen != null) KeyBinding.unPressAllKeys();
                else if (isSetMovementKeys()) { resetMovementKeys(); pressMovementKeys(false); }
                keysPressed = false;
            }
            if (pendingStatus != null) { PacketUtil.sendPacketNoEvent(pendingStatus); pendingStatus = null; }
            if (delayTicks > 0) delayTicks--;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.SEND) return;
        if (event.getPacket() instanceof C16PacketClientStatus) {
            storeMovementKeys();
            if (mode.getIndex() == 1 || mode.getIndex() == 3) {
                C16PacketClientStatus p = (C16PacketClientStatus) event.getPacket();
                if (p.getStatus() == EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
                    event.setCancelled(true);
                    if (mode.getIndex() == 1) pendingStatus = p;
                }
            }
        } else if (event.getPacket() instanceof C0DPacketCloseWindow) {
            C0DPacketCloseWindow p = (C0DPacketCloseWindow) event.getPacket();
            if (((IAccessorC0DPacketCloseWindow) p).getWindowId() == 0) {
                if (mode.getIndex() == 3) {
                    clickQueue.clear();
                    if (openDelayTicks >= 0) openDelayTicks = -1;
                    if (closeDelayTicks >= 0) closeDelayTicks = -1;
                    else event.setCancelled(true);
                } else if (pendingStatus != null) {
                    pendingStatus = null; event.setCancelled(true);
                }
            } else {
                clickQueue.clear();
                if (openDelayTicks >= 0) openDelayTicks = -1;
                if (closeDelayTicks >= 0) closeDelayTicks = -1;
            }
        } else if (event.getPacket() instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow p = (C0EPacketClickWindow) event.getPacket();
            switch (mode.getIndex()) {
                case 1:
                    if (p.getWindowId() == 0) {
                        if ((p.getMode() == 3 || p.getMode() == 4) && p.getSlotId() == -999) { event.setCancelled(true); return; }
                        if (pendingStatus != null) { KeyBinding.unPressAllKeys(); event.setCancelled(true); clickQueue.offer(p); }
                    }
                    break;
                case 2:
                    if ((p.getMode() == 3 || p.getMode() == 4) && p.getSlotId() == -999) { event.setCancelled(true); }
                    else { KeyBinding.unPressAllKeys(); event.setCancelled(true); clickQueue.offer(p); delayTicks = 8; }
                    break;
                case 3:
                    if (p.getWindowId() == 0) {
                        if ((p.getMode() == 3 || p.getMode() == 4) && p.getSlotId() == -999) { event.setCancelled(true); return; }
                        KeyBinding.unPressAllKeys(); event.setCancelled(true); clickQueue.offer(p);
                        if (closeDelayTicks < 0 && openDelayTicks < 0) {
                            pendingStatus = new C16PacketClientStatus(EnumState.OPEN_INVENTORY_ACHIEVEMENT);
                            openDelayTicks = (int) openDelay.getValue();
                        }
                        closeDelayTicks = (int) closeDelay.getValue();
                    }
                    break;
            }
            if (pendingStatus != null) { PacketUtil.sendPacketNoEvent(pendingStatus); pendingStatus = null; }
        }
    }

    @Override
    public void onDisabled() {
        if (keysPressed) { if (mc.currentScreen != null) KeyBinding.unPressAllKeys(); keysPressed = false; }
        if (pendingStatus != null) { PacketUtil.sendPacketNoEvent(pendingStatus); pendingStatus = null; }
        delayTicks = 0;
    }

    @Override
    public String[] getSuffix() {
        String[] opts = {"VANILLA", "LEGIT", "HYPIXEL", "LEGIT+"};
        return new String[]{opts[mode.getIndex()]};
    }
}