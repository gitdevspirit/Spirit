package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LeftClickMouseEvent;
import myau.events.RightClickMouseEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.input.Mouse;

import java.awt.AWTException;
import java.awt.Robot;
import java.util.LinkedList;

public class ClickAssits extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Robot bot;
    private boolean ignoreNextLeft  = false;
    private boolean ignoreNextRight = false;

    private final LinkedList<Long> leftClicks  = new LinkedList<>();
    private final LinkedList<Long> rightClicks = new LinkedList<>();

    public final BooleanSetting leftClick         = new BooleanSetting("Left Click",           true);
    public final SliderSetting  chanceLeft        = new SliderSetting("Chance Left",          80.0, 0.0, 100.0, 1.0);
    public final BooleanSetting weaponOnly        = new BooleanSetting("Weapon Only",          true);
    public final BooleanSetting onlyWhileTargeting= new BooleanSetting("Only While Targeting", false);
    public final BooleanSetting aboveCPSLeft      = new BooleanSetting("Above 5 CPS Left",    false);

    public final BooleanSetting rightClick        = new BooleanSetting("Right Click",          false);
    public final SliderSetting  chanceRight       = new SliderSetting("Chance Right",         80.0, 0.0, 100.0, 1.0);
    public final BooleanSetting blocksOnly        = new BooleanSetting("Blocks Only",          true);
    public final BooleanSetting aboveCPSRight     = new BooleanSetting("Above 5 CPS Right",   false);

    public final BooleanSetting disableInCreative = new BooleanSetting("Disable In Creative",  true);

    public ClickAssits() {
        super("ClickAssits", false);
        register(leftClick); register(chanceLeft); register(weaponOnly);
        register(onlyWhileTargeting); register(aboveCPSLeft);
        register(rightClick); register(chanceRight); register(blocksOnly);
        register(aboveCPSRight); register(disableInCreative);
    }

    private int getLeftCPS() {
        long now = System.currentTimeMillis();
        leftClicks.removeIf(t -> now - t > 1000L);
        return leftClicks.size();
    }

    private int getRightCPS() {
        long now = System.currentTimeMillis();
        rightClicks.removeIf(t -> now - t > 1000L);
        return rightClicks.size();
    }

    private boolean shouldDoubleClickLeft() {
        if (!leftClick.getValue()) return false;
        if (chanceLeft.getValue() == 0.0) return false;
        if (aboveCPSLeft.getValue() && getLeftCPS() <= 5) return false;
        if (weaponOnly.getValue() && !ItemUtil.isHoldingSword()) return false;
        if (onlyWhileTargeting.getValue()) {
            if (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null) return false;
        }
        if (chanceLeft.getValue() < 100.0 && Math.random() >= chanceLeft.getValue() / 100.0) return false;
        return true;
    }

    private boolean shouldDoubleClickRight() {
        if (!rightClick.getValue()) return false;
        if (chanceRight.getValue() == 0.0) return false;
        if (aboveCPSRight.getValue() && getRightCPS() <= 5) return false;
        if (blocksOnly.getValue()) {
            ItemStack item = mc.thePlayer.getHeldItem();
            if (item == null || !(item.getItem() instanceof ItemBlock)) return false;
        }
        if (chanceRight.getValue() < 100.0 && Math.random() >= chanceRight.getValue() / 100.0) return false;
        return true;
    }

    private void fixLeftButton() {
        if (ignoreNextLeft && !Mouse.isButtonDown(0)) {
            bot.mouseRelease(16);
            ignoreNextLeft = false;
        }
    }

    private void fixRightButton() {
        if (ignoreNextRight && !Mouse.isButtonDown(1)) {
            bot.mouseRelease(4);
            ignoreNextRight = false;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            fixLeftButton();
            fixRightButton();
        }
    }

    @EventTarget(Priority.HIGH)
    public void onLeftClick(LeftClickMouseEvent event) {
        if (disableInCreative.getValue() && mc.playerController.getCurrentGameType() == GameType.CREATIVE) return;
        if (mc.currentScreen != null) { fixLeftButton(); fixRightButton(); return; }
        if (aboveCPSLeft.getValue()) leftClicks.add(System.currentTimeMillis());
        if (!isEnabled() || event.isCancelled()) return;
        if (ignoreNextLeft) { ignoreNextLeft = false; return; }
        if (shouldDoubleClickLeft()) {
            bot.mouseRelease(16);
            bot.mousePress(16);
            ignoreNextLeft = true;
        }
    }

    @EventTarget(Priority.HIGH)
    public void onRightClick(RightClickMouseEvent event) {
        if (disableInCreative.getValue() && mc.playerController.getCurrentGameType() == GameType.CREATIVE) return;
        if (mc.currentScreen != null) { fixLeftButton(); fixRightButton(); return; }
        if (aboveCPSRight.getValue()) rightClicks.add(System.currentTimeMillis());
        if (!isEnabled() || event.isCancelled()) return;
        if (ignoreNextRight) { ignoreNextRight = false; return; }
        if (shouldDoubleClickRight()) {
            bot.mouseRelease(4);
            bot.mousePress(4);
            ignoreNextRight = true;
        }
    }

    @Override
    public void onEnabled() {
        try { bot = new Robot(); } catch (AWTException e) { setEnabled(false); }
        ignoreNextLeft = false;
        ignoreNextRight = false;
        leftClicks.clear();
        rightClicks.clear();
    }

    @Override
    public void onDisabled() {
        ignoreNextLeft = false;
        ignoreNextRight = false;
        leftClicks.clear();
        rightClicks.clear();
        bot = null;
    }
}