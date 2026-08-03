package myau;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.ksyz.accountmanager.AccountManager;
import myau.command.CommandManager;
import myau.render.RenderEventBridge;
import net.minecraftforge.common.MinecraftForge;
import myau.command.commands.BindCommand;
import myau.command.commands.HideCommand;
import myau.command.commands.KosCommand;
import myau.command.commands.NameProtectCommand;
import myau.command.commands.SpammerCommand;
import myau.command.commands.ConfigCommand;
import myau.command.commands.IntelKeyCommand;
import myau.config.Config;
import myau.event.EventManager;
import myau.management.*;
import myau.module.Module;
import myau.module.ModuleManager;
import myau.module.modules.*;
import myau.property.Property;
import myau.property.PropertyManager;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

public class Myau {
    public static String clientName = "&7[&bSpirit&7]&r ";
    public static String version;
    public static RotationManager rotationManager;
    public static FloatManager floatManager;
    public static BlinkManager blinkManager;
    public static DelayManager delayManager;
    public static LagManager lagManager;
    public static PlayerStateManager playerStateManager;
    public static FriendManager friendManager;
    public static TargetManager targetManager;
    public static PropertyManager propertyManager;
    public static ModuleManager moduleManager;
    public static NotificationManager notificationManager;
    public static CommandManager commandManager;

    public Myau() {
        this.init();
    }

    public void init() {
        rotationManager = new RotationManager();
        floatManager = new FloatManager();
        blinkManager = new BlinkManager();
        delayManager = new DelayManager();
        lagManager = new LagManager();
        playerStateManager = new PlayerStateManager();
        friendManager = new FriendManager();
        targetManager = new TargetManager();
        propertyManager = new PropertyManager();
        moduleManager = new ModuleManager();
        notificationManager = new NotificationManager();

        commandManager = new CommandManager();
        commandManager.register(new BindCommand());
        commandManager.register(new HideCommand(true));
        commandManager.register(new HideCommand(false));
        commandManager.register(new KosCommand());
        commandManager.register(new NameProtectCommand());
        commandManager.register(new SpammerCommand());
        commandManager.register(new IntelKeyCommand());
        commandManager.register(new myau.command.commands.UrchinKeyCommand());
        commandManager.register(new myau.command.commands.RemoveIntelPlayerCommand());
        commandManager.register(new myau.command.commands.IntelDebugCommand());
        commandManager.register(new myau.command.commands.IntelPathCommand());
        commandManager.register(new myau.command.commands.RoleCommand());
        commandManager.register(new ConfigCommand(commandManager));
        EventManager.register(commandManager);

        EventManager.register(rotationManager);
        EventManager.register(floatManager);
        EventManager.register(blinkManager);
        EventManager.register(delayManager);
        EventManager.register(lagManager);
        EventManager.register(moduleManager);

        moduleManager.modules.put(AimAssist.class, new AimAssist());
        moduleManager.modules.put(Autoblock.class, new Autoblock());
        moduleManager.modules.put(
                myau.module.modules.KillAura.class,
                new myau.module.modules.KillAura()
        );
        moduleManager.modules.put(AntiAFK.class, new AntiAFK());
        moduleManager.modules.put(AntiDebuff.class, new AntiDebuff());
        moduleManager.modules.put(AntiFireball.class, new AntiFireball());
        moduleManager.modules.put(AntiObbyTrap.class, new AntiObbyTrap());
        moduleManager.modules.put(AntiObfuscate.class, new AntiObfuscate());
        moduleManager.modules.put(AntiVoid.class, new AntiVoid());
        moduleManager.modules.put(AutoClicker.class, new AutoClicker());
        moduleManager.modules.put(AutoAnduril.class, new AutoAnduril());
        moduleManager.modules.put(AutoHeal.class, new AutoHeal());
        moduleManager.modules.put(AutoTool.class, new AutoTool());
        moduleManager.modules.put(AutoSwap.class, new AutoSwap());
        moduleManager.modules.put(BedNuker.class, new BedNuker());
        moduleManager.modules.put(BedESP.class, new BedESP());
        moduleManager.modules.put(BedTracker.class, new BedTracker());
        moduleManager.modules.put(Blink.class, new Blink());
        moduleManager.modules.put(BackTrack.class, new BackTrack());
        moduleManager.modules.put(FPScounter.class, new FPScounter());
        moduleManager.modules.put(Chams.class, new Chams());
        moduleManager.modules.put(ChestESP.class, new ChestESP());
        moduleManager.modules.put(ChestStealer.class, new ChestStealer());
        moduleManager.modules.put(Eagle.class, new Eagle());
        moduleManager.modules.put(EdgeOffset.class, new EdgeOffset());
        moduleManager.modules.put(ESP.class, new ESP());
        moduleManager.modules.put(FastPlace.class, new FastPlace());
        moduleManager.modules.put(ServerLag.class, new ServerLag());
        moduleManager.modules.put(Fly.class, new Fly());
        moduleManager.modules.put(FakeLag.class, new FakeLag());
        moduleManager.modules.put(FullBright.class, new FullBright());
        moduleManager.modules.put(GhostHand.class, new GhostHand());
        moduleManager.modules.put(GuiModule.class, new GuiModule());
        moduleManager.modules.put(HitSelect.class, new HitSelect());
        moduleManager.modules.put(AutoHypixel.class, new AutoHypixel());
        moduleManager.modules.put(HUD.class, new HUD());
        moduleManager.modules.put(MoreKB.class, new MoreKB());
        moduleManager.modules.put(Indicators.class, new Indicators());
        moduleManager.modules.put(InventoryClicker.class, new InventoryClicker());
        moduleManager.modules.put(InvManager.class, new InvManager());
        moduleManager.modules.put(InvWalk.class, new InvWalk());
        moduleManager.modules.put(Criticals.class, new Criticals());
        moduleManager.modules.put(JumpReset.class, new JumpReset());
        moduleManager.modules.put(FastBow.class, new FastBow());
        moduleManager.modules.put(BlockHit.class, new BlockHit());
        moduleManager.modules.put(ClientSpoofer.class, new ClientSpoofer());
        moduleManager.modules.put(ItemESP.class, new ItemESP());
        moduleManager.modules.put(Jesus.class, new Jesus());
        moduleManager.modules.put(Disabler.class, new Disabler());
        moduleManager.modules.put(KeepSprint.class, new KeepSprint());
        moduleManager.modules.put(FlagDetector.class, new FlagDetector());
        moduleManager.modules.put(AC.class, new AC());
        moduleManager.modules.put(AutoBedDefence.class, new AutoBedDefence());
        moduleManager.modules.put(Notifications.class, new Notifications());
        moduleManager.modules.put(
                myau.module.modules.LobbyIntel.class,
                new myau.module.modules.LobbyIntel()
        );
        moduleManager.modules.put(Session.class, new Session());
        moduleManager.modules.put(ItemAlerts.class, new ItemAlerts());
        moduleManager.modules.put(InfoHUD.class, new InfoHUD());
        moduleManager.modules.put(HitBox.class, new HitBox());
        moduleManager.modules.put(LagRange.class, new LagRange());
        moduleManager.modules.put(LightningTracker.class, new LightningTracker());
        moduleManager.modules.put(LongJump.class, new LongJump());
        moduleManager.modules.put(MCF.class, new MCF());
        moduleManager.modules.put(NameTags.class, new NameTags());
        moduleManager.modules.put(BedwarsTag.class, new BedwarsTag());
        moduleManager.modules.put(NickHider.class, new NickHider());
        moduleManager.modules.put(NoFall.class, new NoFall());
        moduleManager.modules.put(NoHitDelay.class, new NoHitDelay());
        moduleManager.modules.put(NoHurtCam.class, new NoHurtCam());
        moduleManager.modules.put(NoJumpDelay.class, new NoJumpDelay());
        moduleManager.modules.put(NoRotate.class, new NoRotate());
        moduleManager.modules.put(NoSlow.class, new NoSlow());
        moduleManager.modules.put(ClickAssits.class, new ClickAssits());
        moduleManager.modules.put(Timer.class, new Timer());
        moduleManager.modules.put(Radar.class, new Radar());
        moduleManager.modules.put(Reach.class, new Reach());
        moduleManager.modules.put(Refill.class, new Refill());
        moduleManager.modules.put(SafeWalk.class, new SafeWalk());
        moduleManager.modules.put(Scaffold.class, new Scaffold());
        moduleManager.modules.put(AutoBlockIn.class, new AutoBlockIn());
        moduleManager.modules.put(Spammer.class, new Spammer());
        moduleManager.modules.put(Pit.class, new Pit());
        moduleManager.modules.put(Speed.class, new Speed());
        moduleManager.modules.put(SpeedMine.class, new SpeedMine());
        moduleManager.modules.put(Sprint.class, new Sprint());
        moduleManager.modules.put(TargetHUD.class, new TargetHUD());
        moduleManager.modules.put(TargetStrafe.class, new TargetStrafe());
        moduleManager.modules.put(Tracers.class, new Tracers());
        moduleManager.modules.put(Trajectories.class, new Trajectories());
        moduleManager.modules.put(Velocity.class, new Velocity());
        moduleManager.modules.put(ViewClip.class, new ViewClip());
        moduleManager.modules.put(Wtap.class, new Wtap());
        moduleManager.modules.put(Xray.class, new Xray());
        moduleManager.modules.put(QualityOfLife.class, new QualityOfLife());

        for (Module module : moduleManager.modules.values()) {
            ArrayList<Property<?>> properties = new ArrayList<>();

            for (Field field : module.getClass().getDeclaredFields()) {
                field.setAccessible(true);

                final Object value;

                try {
                    value = field.get(module);
                } catch (IllegalAccessException exception) {
                    throw new RuntimeException(exception);
                }

                if (value instanceof Property<?>) {
                    ((Property<?>) value).setOwner(module);
                    properties.add((Property<?>) value);
                }
            }

            propertyManager.properties.put(module.getClass(), properties);

            if (module.getName().equals("LobbyIntel")) {
                System.out.println(
                        "[PropertyManager] Found "
                                + properties.size()
                                + " properties for LobbyIntel:"
                );

                for (Property<?> property : properties) {
                    System.out.println(
                            "  - " + property.getName() + " = " + property.getValue()
                    );
                }
            }

            EventManager.register(module);
        }

        Config config = new Config("default", true);

        if (config.file.exists()) {
            config.load();
        }

        if (friendManager.file.exists()) {
            friendManager.load();
        }

        if (targetManager.file.exists()) {
            targetManager.load();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                myau.module.modules.LobbyIntel lobbyIntel =
                        (myau.module.modules.LobbyIntel)
                                moduleManager.getModule("LobbyIntel");

                if (lobbyIntel != null) {
                    lobbyIntel.saveHudSettings();
                }
            } catch (Exception ignored) {
            }

            config.save();
        }));

        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(
                        Myau.class.getResourceAsStream("/version.json")
                ),
                StandardCharsets.UTF_8
        )) {
            JsonObject modInfo = new JsonParser().parse(reader).getAsJsonObject();
            version = modInfo.get("version").getAsString();
        } catch (Exception exception) {
            version = "dev";
        }

        AccountManager.init();
        MinecraftForge.EVENT_BUS.register(new RenderEventBridge());
    }
}
