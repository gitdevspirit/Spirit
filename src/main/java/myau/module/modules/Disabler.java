package myau.module.modules;

import java.util.ArrayDeque;
import java.util.concurrent.LinkedBlockingQueue;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.AttackEvent;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorC03PacketPlayer;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.property.properties.TextProperty;
import myau.util.LatePacket;
import myau.util.MoveUtil;
import myau.util.PacketUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.client.C0BPacketEntityAction.Action;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

public class Disabler extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanSetting  lifeboat                   = new BooleanSetting("Lifeboat",                   false);
    public final BooleanSetting  startSprint                = new BooleanSetting("Start Sprint",               true);
    public final BooleanSetting  grimPlace                  = new BooleanSetting("Grim Place",                 false);
    public final BooleanSetting  vulcanScaffold             = new BooleanSetting("Vulcan Scaffold",            false);
    public final SliderSetting   vulcanPacketTick           = new SliderSetting("Vulcan Packet Tick",          15, 1, 20, 1);
    public final BooleanSetting  verusFly                   = new BooleanSetting("Verus Fly",                  false);
    public final BooleanSetting  verusCombat                = new BooleanSetting("Verus Combat",               false);
    public final BooleanSetting  onlyCombat                 = new BooleanSetting("Only Combat",               true);
    public final BooleanSetting  intaveFly                  = new BooleanSetting("Intave Fly",                 false);
    public final BooleanSetting  noRotationDisabler         = new BooleanSetting("No Rotation Disabler",      false);
    public final DropdownSetting modifyMode                 = new DropdownSetting("Modify Mode",               0, "ConvertNull", "Spoof", "Zero", "SpoofZero", "Negative", "OffsetYaw");
    public final SliderSetting   offsetAmount               = new SliderSetting("Offset Amount",             6.0, -180.0, 180.0, 0.5);
    public final BooleanSetting  basicDisabler              = new BooleanSetting("Basic Disabler",            false);
    public final BooleanSetting  cancelC00                  = new BooleanSetting("Cancel C00",                true);
    public final BooleanSetting  cancelC0F                  = new BooleanSetting("Cancel C0F",                true);
    public final BooleanSetting  cancelC0A                  = new BooleanSetting("Cancel C0A",                true);
    public final BooleanSetting  cancelC0B                  = new BooleanSetting("Cancel C0B",                true);
    public final BooleanSetting  cancelC07                  = new BooleanSetting("Cancel C07",                true);
    public final BooleanSetting  cancelC13                  = new BooleanSetting("Cancel C13",                true);
    public final BooleanSetting  cancelC03                  = new BooleanSetting("Cancel C03",                true);
    public final BooleanSetting  c03NoMove                  = new BooleanSetting("C03 No Move",              true);
    public final BooleanSetting  watchdogMotion             = new BooleanSetting("Watchdog Motion",           false);
    public final BooleanSetting  watchdogInventory          = new BooleanSetting("Watchdog Inventory",        false);
    public final BooleanSetting  spigotSpam                 = new BooleanSetting("Spigot Spam",              false);
    public final TextProperty    message                    = new TextProperty("message", "/skill");
    public final BooleanSetting  chatDebug                  = new BooleanSetting("Chat Debug",               false);
    public final BooleanSetting  betaVerus                  = new BooleanSetting("Verus Beta",               false);
    public final SliderSetting   betaVerusBufferSize        = new SliderSetting("Buffer Size",               300, 0, 1000, 1);
    public final SliderSetting   betaVerusRepeatTimes       = new SliderSetting("Repeat Times",                1, 1, 5, 1);
    public final SliderSetting   betaVerusRepeatTimesFighting = new SliderSetting("Repeat Times Fighting",    1, 1, 5, 1);
    public final SliderSetting   betaVerusFlagDelay         = new SliderSetting("Flag Delay",                 40, 35, 60, 1);
    public final BooleanSetting  matrixDisabler             = new BooleanSetting("Matrix Disabler",          false);
    public final BooleanSetting  matrixTA                   = new BooleanSetting("Matrix TA",                true);
    public final BooleanSetting  matrixTA188                = new BooleanSetting("Matrix TA 188",            false);
    public final SliderSetting   matrixTAPacket             = new SliderSetting("Matrix TA Packet",          1.0, 1.0, 5.0, 0.1);
    public final DropdownSetting matrixAB                   = new DropdownSetting("Matrix AB",               0, "Off", "BlockHit", "Shield");
    public final DropdownSetting matrixT                    = new DropdownSetting("Matrix T",                0, "Off", "Pingspoof", "FunnyValue", "OldCancel");
    public final BooleanSetting  matrixReach                = new BooleanSetting("Matrix Reach",             false);
    public final BooleanSetting  matrixAllDir               = new BooleanSetting("Matrix AllDir",            false);
    public final BooleanSetting  taPacketCounter            = new BooleanSetting("TA Packet Counter",        false);
    public final BooleanSetting  verusExperimental          = new BooleanSetting("Verus Experimental",       false);
    public final BooleanSetting  verusExpVoidTP             = new BooleanSetting("Exp Void TP",              false);
    public final SliderSetting   verusExpVoidTPDelay        = new SliderSetting("Exp Void TP Delay",      1000, 0, 30000, 10);

    private boolean shouldDelay         = false;
    private final LinkedBlockingQueue<Packet<INetHandlerPlayClient>> packets = new LinkedBlockingQueue<>();
    private int     flags               = 0;
    private boolean execute             = false;
    private boolean jump                = false;
    private boolean c16                 = false;
    private boolean c0d                 = false;
    private boolean transaction         = false;
    public  boolean isOnCombat          = false;
    private boolean betaVerus2Stat      = false;
    private boolean betaVerusModified   = false;
    private final ArrayDeque<Packet<INetHandlerPlayClient>> betaVerusPacketBuffer = new ArrayDeque<>();
    private final TimerUtil betaVerusLagTimer  = new TimerUtil();
    private final TimerUtil lastC00timer       = new TimerUtil();
    private final TimerUtil lastSAPtimer       = new TimerUtil();
    private final TimerUtil lastC03timer       = new TimerUtil();
    private final TimerUtil lastPacketTimer    = new TimerUtil();
    private final TimerUtil lastFlagtimer      = new TimerUtil();
    private boolean wasBlockHit         = false;
    private int     matrixIndex1        = 0;
    private double  lastSpeed2d         = 0.0;
    private int     flagSkip            = 0;
    private boolean predictNextC0F      = false;
    private boolean shouldDelayMatrix   = false;
    private int     lastPongId          = 0;
    private int     c0fCount            = 0;
    private long    randomLong          = 0L;
    public  int     savedAbusePacket    = 0;
    private final ArrayDeque<Packet<?>>     c00s = new ArrayDeque<>();
    private final ArrayDeque<LatePacket>    c0fs = new ArrayDeque<>();
    private long    lastVoidTP          = 0L;
    private int     cancelNext          = 0;

    public Disabler() {
        super("Disabler", false);
        register(lifeboat); register(startSprint); register(grimPlace);
        register(vulcanScaffold); register(vulcanPacketTick);
        register(verusFly); register(verusCombat); register(onlyCombat);
        register(intaveFly); register(noRotationDisabler); register(modifyMode);
        register(offsetAmount); register(basicDisabler);
        register(cancelC00); register(cancelC0F); register(cancelC0A);
        register(cancelC0B); register(cancelC07); register(cancelC13);
        register(cancelC03); register(c03NoMove); register(watchdogMotion);
        register(watchdogInventory); register(spigotSpam); register(chatDebug);
        register(betaVerus); register(betaVerusBufferSize);
        register(betaVerusRepeatTimes); register(betaVerusRepeatTimesFighting);
        register(betaVerusFlagDelay); register(matrixDisabler); register(matrixTA);
        register(matrixTA188); register(matrixTAPacket); register(matrixAB);
        register(matrixT); register(matrixReach); register(matrixAllDir);
        register(taPacketCounter); register(verusExperimental);
        register(verusExpVoidTP); register(verusExpVoidTPDelay);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;
        Packet<?> packet = event.getPacket();

        if (matrixDisabler.getValue()) handleMatrixPacket(event, packet);

        if (lifeboat.getValue() && packet instanceof C0FPacketConfirmTransaction) {
            event.setCancelled(true); debugMessage("Cancelled Lifeboat Transaction");
        }

        if (basicDisabler.getValue()) {
            if      (packet instanceof C00PacketKeepAlive      && cancelC00.getValue()) { event.setCancelled(true); debugMessage("Cancelled C00-KeepAlive"); }
            else if (packet instanceof C0FPacketConfirmTransaction && cancelC0F.getValue()) { event.setCancelled(true); debugMessage("Cancelled C0F-Transaction"); }
            else if (packet instanceof C0APacketAnimation     && cancelC0A.getValue()) { event.setCancelled(true); debugMessage("Cancelled C0A-Swing"); }
            else if (packet instanceof C0BPacketEntityAction  && cancelC0B.getValue()) { event.setCancelled(true); debugMessage("Cancelled C0B-Action"); }
            else if (packet instanceof C07PacketPlayerDigging && cancelC07.getValue()) { event.setCancelled(true); debugMessage("Cancelled C07-Digging"); }
            else if (packet instanceof C13PacketPlayerAbilities && cancelC13.getValue()) { event.setCancelled(true); debugMessage("Cancelled C13-Abilities"); }
            else if (packet instanceof C03PacketPlayer && cancelC03.getValue()) {
                C03PacketPlayer c03 = (C03PacketPlayer) packet;
                if (!(c03 instanceof C03PacketPlayer.C04PacketPlayerPosition)
                        && !(c03 instanceof C03PacketPlayer.C05PacketPlayerLook)
                        && !(c03 instanceof C03PacketPlayer.C06PacketPlayerPosLook)) {
                    if (c03NoMove.getValue() && isMoving()) return;
                    event.setCancelled(true); debugMessage("Cancelled C03-Flying");
                }
            }
        }

        if (noRotationDisabler.getValue() && packet instanceof C03PacketPlayer) {
            C03PacketPlayer c03 = (C03PacketPlayer) packet;
            switch (modifyMode.getIndex()) {
                case 0:
                    if (c03 instanceof C03PacketPlayer.C04PacketPlayerPosition || c03 instanceof C03PacketPlayer.C06PacketPlayerPosLook)
                        PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, c03.isOnGround()));
                    else
                        PacketUtil.sendPacket(new C03PacketPlayer(c03.isOnGround()));
                    event.setCancelled(true); break;
                case 3:
                    PacketUtil.sendPacket(new C03PacketPlayer.C06PacketPlayerPosLook(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, 0.0F, 0.0F, c03.isOnGround()));
                    event.setCancelled(true); break;
            }
        }

        if (watchdogMotion.getValue()) {
            if (packet instanceof S07PacketRespawn) { flags = 0; execute = false; jump = true; }
            else if (packet instanceof S08PacketPlayerPosLook && ++flags >= 20) { execute = false; flags = 0; }
        }

        if (watchdogInventory.getValue()) {
            if (packet instanceof C16PacketClientStatus)   { if (c16) event.setCancelled(true); c16 = true; }
            if (packet instanceof C0DPacketCloseWindow)    { if (c0d) event.setCancelled(true); c0d = true; }
        }

        if (grimPlace.getValue() && packet instanceof C08PacketPlayerBlockPlacement) {
            event.setCancelled(true);
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            debugMessage("§cModified §aPlace §cPacket§7.");
        }

        if (intaveFly.getValue()) {
            if (packet instanceof S08PacketPlayerPosLook && mc.thePlayer.capabilities.isFlying) {
                shouldDelay = true; debugMessage("§cStarted Canceling IntaveFly");
            }
            if (packet instanceof S32PacketConfirmTransaction && shouldDelay) {
                event.setCancelled(true);
                packets.add((Packet<INetHandlerPlayClient>) packet);
            }
        }

        if (verusCombat.getValue()) {
            if (mc.thePlayer.ticksExisted <= 20) { isOnCombat = false; return; }
            if (onlyCombat.getValue() && !isOnCombat) return;
            if (packet instanceof S32PacketConfirmTransaction) {
                event.setCancelled(true);
                PacketUtil.sendPacket(new C0FPacketConfirmTransaction(transaction ? 1 : -1, (short)(transaction ? -1 : 1), transaction));
                transaction = !transaction;
            }
            isOnCombat = false;
        }

        if (betaVerus.getValue()) {
            if (!(packet instanceof C0FPacketConfirmTransaction)) {
                if (packet instanceof C03PacketPlayer) {
                    if (mc.thePlayer.ticksExisted % (int) betaVerusFlagDelay.getValue() == 0
                            && mc.thePlayer.ticksExisted > (int) betaVerusFlagDelay.getValue() + 1
                            && !betaVerusModified) {
                        betaVerusModified = true; event.setCancelled(true);
                        debugMessage("Packet C03 -> BetaVerus Y offset");
                    }
                } else if (packet instanceof S08PacketPlayerPosLook) {
                    S08PacketPlayerPosLook s08 = (S08PacketPlayerPosLook) packet;
                    double diff = Math.sqrt(Math.pow(s08.getX()-mc.thePlayer.posX,2)+Math.pow(s08.getY()-mc.thePlayer.posY,2)+Math.pow(s08.getZ()-mc.thePlayer.posZ,2));
                    if (diff <= 8.0) {
                        event.setCancelled(true); debugMessage("Silent Flag");
                        PacketUtil.sendPacket(new C03PacketPlayer.C06PacketPlayerPosLook(s08.getX(),s08.getY(),s08.getZ(),s08.getYaw(),s08.getPitch(),true));
                    }
                }
            } else {
                betaVerusPacketBuffer.add((Packet<INetHandlerPlayClient>) packet);
                event.setCancelled(true);
                if (betaVerusPacketBuffer.size() > (int) betaVerusBufferSize.getValue()) {
                    if (!betaVerus2Stat) betaVerus2Stat = true;
                    Packet<INetHandlerPlayClient> p = betaVerusPacketBuffer.poll();
                    int rpt = isOnCombat ? (int) betaVerusRepeatTimesFighting.getValue() : (int) betaVerusRepeatTimes.getValue();
                    for (int i = 0; i < rpt; i++) PacketUtil.sendPacketNoEvent(p);
                }
                debugMessage("Packet C0F IN BufferSize=" + betaVerusPacketBuffer.size());
            }
            if (mc.thePlayer.ticksExisted <= 7) { betaVerusLagTimer.reset(); betaVerusPacketBuffer.clear(); }
        }

        if (verusExperimental.getValue()) {
            if (verusExpVoidTP.getValue() && packet instanceof C03PacketPlayer) {
                if (mc.thePlayer.ticksExisted > 20 && mc.thePlayer.posY > -64.0
                        && lastVoidTP + (long) verusExpVoidTPDelay.getValue() < System.currentTimeMillis()) {
                    lastVoidTP = System.currentTimeMillis();
                    PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX, -48.0, mc.thePlayer.posZ, true));
                    PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, false));
                    PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.onGround));
                    cancelNext = 2; event.setCancelled(true);
                    debugMessage("VerusExp VoidTP attempt");
                }
            } else if (verusExpVoidTP.getValue() && packet instanceof S08PacketPlayerPosLook && cancelNext > 0) {
                --cancelNext; event.setCancelled(true);
                debugMessage("VerusExp cancelled server position look");
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;

        if (watchdogMotion.getValue() && jump) mc.thePlayer.jump();
        if (watchdogInventory.getValue()) { c16 = false; c0d = false; }

        if (verusFly.getValue()) {
            if (!isOnCombat && !mc.thePlayer.isDead) {
                BlockPos pos = mc.thePlayer.getPosition().add(0, mc.thePlayer.posY > 0.0 ? -255 : 255, 0);
                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(pos, 256, new ItemStack(Items.apple), 0.0F, (float)(0.5 + Math.random() * 0.44), 0.0F));
            } else { isOnCombat = false; }
        }

        if (vulcanScaffold.getValue() && !mc.thePlayer.isInWater() && !mc.thePlayer.isSneaking()
                && !mc.thePlayer.isDead && !mc.thePlayer.isRiding() && isMoving()
                && mc.thePlayer.ticksExisted % (int) vulcanPacketTick.getValue() == 0) {
            PacketUtil.sendPacket(new C0BPacketEntityAction(mc.thePlayer, Action.START_SNEAKING));
            PacketUtil.sendPacket(new C0BPacketEntityAction(mc.thePlayer, Action.STOP_SNEAKING));
        }

        if (betaVerus.getValue()) {
            betaVerusModified = false;
            if (betaVerusLagTimer.hasTimeElapsed(490L)) {
                betaVerusLagTimer.reset();
                if (!betaVerusPacketBuffer.isEmpty()) {
                    Packet<INetHandlerPlayClient> p = betaVerusPacketBuffer.poll();
                    int rpt = isOnCombat ? (int) betaVerusRepeatTimesFighting.getValue() : (int) betaVerusRepeatTimes.getValue();
                    for (int i = 0; i < rpt; i++) PacketUtil.sendPacketNoEvent(p);
                    debugMessage("Packet Buffer Dump");
                } else { debugMessage("Empty Packet Buffer"); }
            }
        }

        if (matrixDisabler.getValue() && matrixTA.getValue()) {
            double speed = MoveUtil.getSpeed();
            if (speed > 0.001) {
                double diff = Math.abs(speed - lastSpeed2d);
                if (!mc.thePlayer.onGround && diff < 1.0E-4)
                    MoveUtil.setSpeed(speed * (0.99999999999 - Math.random() * 1.0E-7), MoveUtil.getMoveYaw());
            }
            lastSpeed2d = speed;
            if (lastSAPtimer.hasTimeElapsed(50L) && savedAbusePacket <= 0) { ++savedAbusePacket; lastSAPtimer.reset(); }
            if (lastC03timer.hasTimeElapsed(4000L) && MoveUtil.getSpeed() < 0.001) {
                lastC03timer.reset();
                shouldDelayMatrix = (double) randomLong * 1.3 < (double) c0fCount;
                randomLong = 0L; c0fCount = 0;
                if (!c00s.isEmpty()) { lastC00timer.reset(); while (!c00s.isEmpty()) PacketUtil.sendPacketNoEvent(c00s.pollFirst()); }
            }
            if (lastFlagtimer.hasTimeElapsed(400L)) { flagSkip = 0; lastFlagtimer.reset(); }
            if (savedAbusePacket < -2) savedAbusePacket = -2;
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) { if (isEnabled()) isOnCombat = true; }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        if (!isEnabled()) return;
        isOnCombat = false;
        if (betaVerus.getValue()) { betaVerus2Stat = false; betaVerusPacketBuffer.clear(); betaVerusLagTimer.reset(); }
    }

    private boolean isMoving() { return mc.thePlayer.moveForward != 0.0F || mc.thePlayer.moveStrafing != 0.0F; }

    private void handleMatrixPacket(PacketEvent event, Packet<?> packet) {
        if (packet instanceof C0FPacketConfirmTransaction && matrixT.getIndex() == 1) {
            lastPongId = 0;
            if (!event.isCancelled()) {
                long firstTime = !c0fs.isEmpty() ? c0fs.getLast().getRequiredMs() : 100L;
                c0fs.add(new LatePacket(packet, System.currentTimeMillis() + 35000L));
                event.setCancelled(true);
                long secTime = !c0fs.isEmpty() ? c0fs.getLast().getRequiredMs() : 200L;
                if (secTime - firstTime >= 20L) c0fs.pollLast();
            }
            while (!c0fs.isEmpty() && c0fs.peekFirst().getRequiredMs() <= System.currentTimeMillis())
                PacketUtil.sendPacketNoEvent(c0fs.pollFirst().getPacket());
            ++c0fCount;
        }

        if (packet instanceof C00PacketKeepAlive && matrixT.getIndex() == 1) {
            if (c00s.isEmpty()) lastC00timer.reset();
            c00s.add(packet); event.setCancelled(true);
        }

        if (packet instanceof C0BPacketEntityAction && matrixAllDir.getValue()) event.setCancelled(true);

        if (packet instanceof C03PacketPlayer && matrixT.getIndex() == 1) {
            --savedAbusePacket;
            lastC03timer.reset();
            if (savedAbusePacket < 5 && !c0fs.isEmpty()) {
                long first = c0fs.getFirst().getRequiredMs();
                PacketUtil.sendPacketNoEvent(c0fs.pollFirst().getPacket());
                if (!c0fs.isEmpty() && c0fs.getFirst().getRequiredMs() - first < 20L)
                    PacketUtil.sendPacketNoEvent(c0fs.pollFirst().getPacket());
            }
            if (lastC00timer.hasTimeElapsed(10000L)) {
                shouldDelayMatrix = (double) randomLong * 1.3 < (double) c0fCount;
                randomLong = 0L; c0fCount = 0;
                if (!c00s.isEmpty()) { lastC00timer.reset(); while (!c00s.isEmpty()) PacketUtil.sendPacketNoEvent(c00s.pollFirst()); }
            }
            ++randomLong;
        }

        if (packet instanceof S08PacketPlayerPosLook && event.getType() == EventType.RECEIVE) {
            if (flagSkip > 0) { --flagSkip; event.setCancelled(true); debugMessage("Skipped flag packet"); }
            lastFlagtimer.reset();
        }

        if (packet instanceof S07PacketRespawn && event.getType() == EventType.RECEIVE) {
            savedAbusePacket = 0; flagSkip = 0; lastSAPtimer.reset();
        }
    }

    private void debugMessage(String msg) {
        if (chatDebug.getValue())
            mc.thePlayer.addChatMessage(new ChatComponentText("§7[§bDisabler§7] §f" + msg));
    }

    @Override
    public void onDisabled() {
        flags = 0; execute = false; jump = false; transaction = false;
        isOnCombat = false; c16 = false; c0d = false; shouldDelay = false;
        betaVerus2Stat = false; betaVerusModified = false; cancelNext = 0;
        lastVoidTP = 0L; packets.clear(); betaVerusPacketBuffer.clear();
        c00s.clear(); c0fs.clear(); wasBlockHit = false; matrixIndex1 = 0;
        lastSpeed2d = 0.0; flagSkip = 0; predictNextC0F = false;
        shouldDelayMatrix = false; lastPongId = 0; c0fCount = 0;
        randomLong = 0L; savedAbusePacket = 0;
    }

    @Override
    public String[] getSuffix() {
        int active = 0;
        if (basicDisabler.getValue())  active++;
        if (verusCombat.getValue())    active++;
        if (watchdogMotion.getValue()) active++;
        if (betaVerus.getValue())      active++;
        return new String[]{ String.valueOf(active) };
    }
}