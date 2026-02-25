package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.event.types.EventType;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.mixin.IAccessorRenderManager;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class LagRange extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private int     tickIndex    = -1;
    private long    delayCounter = 0L;
    private boolean hasTarget    = false;
    private Vec3    lastPosition    = null;
    private Vec3    currentPosition = null;

    public final SliderSetting   delay       = new SliderSetting("Delay",        150, 0, 1000, 10);
    public final SliderSetting   range       = new SliderSetting("Range",         10.0, 3.0, 100.0, 0.5);
    public final BooleanSetting  weaponsOnly = new BooleanSetting("Weapons Only", true);
    public final BooleanSetting  allowTools  = new BooleanSetting("Allow Tools",  false);
    public final BooleanSetting  botCheck    = new BooleanSetting("Bot Check",    true);
    public final BooleanSetting  teams       = new BooleanSetting("Teams",        true);
    public final DropdownSetting showPosition= new DropdownSetting("Show Position", 0, "NONE", "DEFAULT", "HUD");

    public LagRange() {
        super("LagRange", false);
        register(delay); register(range); register(weaponsOnly);
        register(allowTools); register(botCheck); register(teams);
        register(showPosition);
    }

    private boolean isValidTarget(EntityPlayer p) {
        if (p == mc.thePlayer || p == mc.thePlayer.ridingEntity) return false;
        if (p == mc.getRenderViewEntity() || p == mc.getRenderViewEntity().ridingEntity) return false;
        if (p.deathTime > 0) return false;
        if (TeamUtil.isFriend(p)) return false;
        if (teams.getValue() && TeamUtil.isSameTeam(p)) return false;
        if (botCheck.getValue() && TeamUtil.isBot(p)) return false;
        return true;
    }

    private boolean shouldResetOnPacket(Packet<?> packet) {
        if (packet instanceof C02PacketUseEntity) return true;
        if (packet instanceof C07PacketPlayerDigging)
            return ((C07PacketPlayerDigging) packet).getStatus() != Action.RELEASE_USE_ITEM;
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            ItemStack item = ((C08PacketPlayerBlockPlacement) packet).getStack();
            return item == null || !(item.getItem() instanceof ItemSword);
        }
        return false;
    }

    @EventTarget(Priority.LOW)
    public void onTick(TickEvent event) {
        if (!isEnabled()) return;
        switch (event.getType()) {
            case PRE:
                Myau.lagManager.setDelay(0);
                hasTarget = false;
                BedNuker bedNuker = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
                boolean bedBusy = bedNuker.isEnabled() && bedNuker.isReady();
                boolean hittingBlock = ((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock();
                boolean usingItem = mc.thePlayer.isUsingItem() && !mc.thePlayer.isBlocking();
                boolean hasWeapon = !weaponsOnly.getValue()
                        || ItemUtil.hasRawUnbreakingEnchant()
                        || (allowTools.getValue() && ItemUtil.isHoldingTool());

                if (!bedBusy && !hittingBlock && !usingItem && hasWeapon) {
                    List<EntityPlayer> players = mc.theWorld.loadedEntityList.stream()
                            .filter(e -> e instanceof EntityPlayer)
                            .map(e -> (EntityPlayer) e)
                            .filter(this::isValidTarget)
                            .collect(Collectors.toList());

                    if (players.isEmpty()) {
                        tickIndex = -1;
                    } else {
                        double eyeHeight = mc.thePlayer.getEyeHeight();
                        Vec3 lagEyePos    = Myau.lagManager.getLastPosition().addVector(0.0, eyeHeight, 0.0);
                        Vec3 lastTickEye  = new Vec3(mc.thePlayer.lastTickPosX, mc.thePlayer.lastTickPosY + eyeHeight, mc.thePlayer.lastTickPosZ);
                        Vec3 currentEye   = new Vec3(mc.thePlayer.posX,         mc.thePlayer.posY + eyeHeight,         mc.thePlayer.posZ);

                        for (EntityPlayer player : players) {
                            double dist     = RotationUtil.distanceToBox(player, currentEye);
                            if (dist > range.getValue()) continue;

                            double lagDist  = RotationUtil.distanceToBox(player, lagEyePos);
                            double tickDist = RotationUtil.distanceToBox(player, lastTickEye);

                            if (dist < tickDist || dist < lagDist) {
                                if (tickIndex < 0) {
                                    tickIndex = 0;
                                    for (delayCounter += (long) delay.getValue(); delayCounter > 0L; delayCounter -= 50)
                                        tickIndex++;
                                }
                                Myau.lagManager.setDelay(tickIndex);
                                hasTarget = true;
                                return;
                            }
                        }
                    }
                } else {
                    tickIndex = -1;
                }
                break;

            case POST:
                Vec3 savedPos = Myau.lagManager.getLastPosition();
                lastPosition    = currentPosition == null ? savedPos : currentPosition;
                currentPosition = savedPos;
                break;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled()) return;
        if (shouldResetOnPacket(event.getPacket())) {
            Myau.lagManager.setDelay(0);
            tickIndex = -1;
        }
    }

    @EventTarget(Priority.HIGH)
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled()) return;
        if (showPosition.getIndex() == 0) return;
        if (mc.gameSettings.thirdPersonView == 0) return;
        if (!hasTarget || lastPosition == null || currentPosition == null) return;

        Color color;
        switch (showPosition.getIndex()) {
            case 1:  color = TeamUtil.getTeamColor(mc.thePlayer, 1.0F); break;
            case 2:  color = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()); break;
            default: color = Color.WHITE;
        }

        double x = RenderUtil.lerpDouble(currentPosition.xCoord, lastPosition.xCoord, event.getPartialTicks());
        double y = RenderUtil.lerpDouble(currentPosition.yCoord, lastPosition.yCoord, event.getPartialTicks());
        double z = RenderUtil.lerpDouble(currentPosition.zCoord, lastPosition.zCoord, event.getPartialTicks());
        float  s = mc.thePlayer.getCollisionBorderSize();
        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();

        AxisAlignedBB aabb = new AxisAlignedBB(
                x - mc.thePlayer.width / 2.0, y,                   z - mc.thePlayer.width / 2.0,
                x + mc.thePlayer.width / 2.0, y + mc.thePlayer.height, z + mc.thePlayer.width / 2.0)
                .expand(s, s, s)
                .offset(-rm.getRenderPosX(), -rm.getRenderPosY(), -rm.getRenderPosZ());

        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox(aabb, color.getRed(), color.getGreen(), color.getBlue());
        RenderUtil.disableRenderState();
    }

    @Override
    public void onDisabled() {
        Myau.lagManager.setDelay(0);
        tickIndex    = -1;
        delayCounter = 0L;
        hasTarget    = false;
        lastPosition    = null;
        currentPosition = null;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{ String.format("%dms", (int) delay.getValue()) };
    }
}