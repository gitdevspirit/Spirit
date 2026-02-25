package myau.module.modules;

import myau.Myau;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.property.properties.TextProperty;
import myau.util.ChatUtil;
import myau.util.ColorUtil;
import myau.util.SoundUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BedTracker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final ScheduledExecutorService executor      = Executors.newScheduledThreadPool(1);
    private final LinkedHashMap<String, Long> alertCooldowns = new LinkedHashMap<>();
    private final LinkedHashSet<EntityEnderPearl> trackedPearls  = new LinkedHashSet<>();
    private final LinkedHashSet<String> whitelistedPlayers = new LinkedHashSet<>();
    private final Color wBed = new Color(ChatColors.WHITE.toAwtColor());
    private final Color rBed = new Color(ChatColors.RED.toAwtColor());
    private final Color yBed = new Color(ChatColors.YELLOW.toAwtColor());
    private final Color gBed = new Color(ChatColors.GREEN.toAwtColor());
    private BlockPos bedPos      = null;
    private long     lastMarcoTime = -1L;
    private boolean  waiting       = false;

    public final BooleanSetting  alerts         = new BooleanSetting("Alerts",             true);
    public final SliderSetting   alertRange     = new SliderSetting("Alert Range",          48, 8, 128, 1);
    public final BooleanSetting  alertOnPearl   = new BooleanSetting("Alert On Pearl",      true);
    public final DropdownSetting alertSound     = new DropdownSetting("Alert Sound",        1, "NONE", "MEOW", "ANVIL");
    public final SliderSetting   alertFrequency = new SliderSetting("Alert Frequency",      5, 1, 30, 1);
    public final BooleanSetting  marco          = new BooleanSetting("Macro",              false);
    public final SliderSetting   marcoRange     = new SliderSetting("Macro Range",         24, 8, 128, 1);
    public final BooleanSetting  marcoOnPearl   = new BooleanSetting("Macro On Pearl",     false);
    // TextProperty kept as-is — no new-system equivalent
    public final TextProperty    marcoText      = new TextProperty("macro-text", "/lobby");
    public final SliderSetting   marcoDelay     = new SliderSetting("Macro Delay",          1, 1, 10, 1);
    public final BooleanSetting  hud            = new BooleanSetting("HUD",                true);
    public final DropdownSetting hudPosX        = new DropdownSetting("HUD Position X",    0, "LEFT", "MIDDLE", "RIGHT");
    public final DropdownSetting hudPosY        = new DropdownSetting("HUD Position Y",    0, "TOP", "MIDDLE", "BOTTOM");
    public final SliderSetting   hudOffX        = new SliderSetting("HUD Offset X",        2, 0, 255, 1);
    public final SliderSetting   hudOffY        = new SliderSetting("HUD Offset Y",        2, 0, 255, 1);
    public final SliderSetting   hudScale       = new SliderSetting("HUD Scale",         1.0, 0.5, 1.5, 0.05);
    public final BooleanSetting  hudShadow      = new BooleanSetting("HUD Shadow",         true);

    public BedTracker() {
        super("BedTracker", false, true);
        register(alerts); register(alertRange); register(alertOnPearl);
        register(alertSound); register(alertFrequency);
        register(marco); register(marcoRange); register(marcoOnPearl);
        register(marcoDelay);
        register(hud); register(hudPosX); register(hudPosY);
        register(hudOffX); register(hudOffY); register(hudScale); register(hudShadow);
    }

    private void playAlertSound() {
        switch (alertSound.getIndex()) {
            case 1: SoundUtil.playSound("mob.cat.meow"); break;
            case 2: SoundUtil.playSound("random.anvil_land"); break;
        }
    }

    private Color getHudColor(int distance) {
        if (distance < 0)         return wBed;
        if (distance <= 100)      return gBed;
        if (distance <= 114)      return ColorUtil.interpolate((float)(114 - distance) / 14.0F, yBed, gBed);
        if (distance <= 128)      return ColorUtil.interpolate((float)(128 - distance) / 14.0F, rBed, yBed);
        return rBed;
    }

    private boolean isBed(BlockPos pos) {
        return pos != null && mc.theWorld.getBlockState(pos).getBlock() == Blocks.bed;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.POST || !isBed(bedPos)) return;

        long millis = System.currentTimeMillis();
        boolean pearl = false, doMarco = false;

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityEnderPearl) {
                EntityEnderPearl ep = (EntityEnderPearl) entity;
                if (!trackedPearls.contains(ep)) {
                    trackedPearls.add(ep);
                    if (alertOnPearl.getValue()) {
                        ChatUtil.sendFormatted(String.format("%s%s: &fDetected &5Ender Pearl&r &e&l⚠&r", Myau.clientName, getName()));
                        pearl = true;
                    }
                    if (marcoOnPearl.getValue() && lastMarcoTime + (long) marcoDelay.getValue() * 1000L <= millis) {
                        lastMarcoTime = millis; doMarco = true;
                    }
                }
            }
        }

        for (EntityPlayer player : mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityPlayer)
                .map(e -> (EntityPlayer) e)
                .filter(p -> !TeamUtil.isBot(p) && !whitelistedPlayers.contains(p.getName()))
                .collect(Collectors.toList())) {

            if (TeamUtil.isSameTeam(player)) { whitelistedPlayers.add(player.getName()); continue; }

            double distance = player.getDistance(bedPos.getX() + 0.5, bedPos.getY() + 0.5, bedPos.getZ() + 0.5);
            String name = player.getName();
            String text = player.getDisplayName().getFormattedText();
            ItemStack item = player.getHeldItem();
            boolean isPearl = item != null && item.getItem() instanceof ItemEnderPearl;

            if (alerts.getValue() && distance < alertRange.getValue()) {
                Long cd = alertCooldowns.get(name);
                if (cd == null || cd + (long) alertFrequency.getValue() * 1000L <= millis) {
                    alertCooldowns.put(name, millis);
                    ChatUtil.sendFormatted(String.format("%s%s: %s&r &fis %d blocks away from your bed &e&l⚠&r",
                            Myau.clientName, getName(), text, (int) distance + 1));
                    pearl = true;
                }
            }
            if (alertOnPearl.getValue() && isPearl) {
                Long cd = alertCooldowns.get(name);
                if (cd == null || cd + (long) alertFrequency.getValue() * 1000L <= millis) {
                    alertCooldowns.put(name, millis);
                    ChatUtil.sendFormatted(String.format("%s%s: %s&r &fhas &5Ender Pearl&r &e&l⚠&r",
                            Myau.clientName, getName(), text));
                    pearl = true;
                }
            }
            if ((marco.getValue() && distance < marcoRange.getValue() || marcoOnPearl.getValue() && isPearl)
                    && lastMarcoTime + (long) marcoDelay.getValue() * 1000L <= millis) {
                lastMarcoTime = millis; doMarco = true;
            }
        }

        if (pearl)   playAlertSound();
        if (doMarco) {
            ChatUtil.sendRaw(String.format(ChatColors.formatColor("%s%s: &fRunning &6%s&r"),
                    ChatColors.formatColor(Myau.clientName), getName(), marcoText.getValue()));
            ChatUtil.sendMessage(marcoText.getValue());
        }
    }

    @EventTarget(Priority.LOW)
    public void onRender(Render2DEvent event) {
        if (!isEnabled() || !hud.getValue()) return;
        if (mc.theWorld == null || mc.thePlayer == null || mc.gameSettings.showDebugInfo) return;
        GuiScreen screen = mc.currentScreen;
        if (screen != null && !(screen instanceof GuiChat)) return;

        int distanceSq = 0;
        boolean hasBed = isBed(bedPos);
        if (hasBed) {
            double xd = mc.thePlayer.posX - bedPos.getX();
            double zd = mc.thePlayer.posZ - bedPos.getZ();
            distanceSq = (int) Math.sqrt(xd * xd + zd * zd) + 1;
        }
        String text = ChatColors.formatColor(String.format("&fBed: %s%s",
                !hasBed ? "&cfalse&r" : "&atrue&r",
                !hasBed ? "" : String.format(" &7| &fDistance: &r%d%s", distanceSq, distanceSq >= 128 ? " &c&l⚠&r" : "")));

        ScaledResolution sr = new ScaledResolution(mc);
        float width  = mc.fontRendererObj.getStringWidth(text);
        float height = mc.fontRendererObj.FONT_HEIGHT - 1.0F;
        float scaleF = (float) hudScale.getValue();
        float offX   = (float) hudOffX.getValue() / scaleF;
        float offY   = (float) hudOffY.getValue() / scaleF;

        switch (hudPosX.getIndex()) {
            case 0: offX++; break;
            case 1: offX += sr.getScaledWidth() / scaleF / 2.0F - width / 2.0F; break;
            case 2: offX = -(offX + 1.0F) + sr.getScaledWidth() / scaleF - width; break;
        }
        switch (hudPosY.getIndex()) {
            case 0: offY++; break;
            case 1: offY += sr.getScaledHeight() / scaleF / 2.0F - height / 2.0F; break;
            case 2: offY = -(offY + 1.0F) + sr.getScaledHeight() / scaleF - height; break;
        }

        GlStateManager.pushMatrix();
        GlStateManager.scale(scaleF, scaleF, 1.0F);
        GlStateManager.translate(offX, offY, 0.0F);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        mc.fontRendererObj.drawString(text, 0.0F, 0.0F, getHudColor(distanceSq).getRGB(), hudShadow.getValue());
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) { waiting = false; }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled()) return;
        if (event.getPacket() instanceof S02PacketChat) {
            String msg = ((S02PacketChat) event.getPacket()).getChatComponent().getFormattedText();
            if (msg.contains("§e§lProtect your bed") || msg.contains("§e§lDestroy the enemy bed")) {
                alertCooldowns.clear(); trackedPearls.clear(); whitelistedPlayers.clear();
                bedPos = null; waiting = true;
            }
        }
        if (event.getPacket() instanceof S08PacketPlayerPosLook && waiting) {
            waiting = false;
            executor.schedule(() -> {
                int x = MathHelper.floor_double(mc.thePlayer.posX);
                int y = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
                int z = MathHelper.floor_double(mc.thePlayer.posZ);
                for (int i = x - 25; i <= x + 25; i++)
                    for (int j = y - 25; j <= y + 25; j++)
                        for (int k = z - 25; k <= z + 25; k++) {
                            BlockPos bp = new BlockPos(i, j, k);
                            if (isBed(bp)) {
                                bedPos = bp;
                                ChatUtil.sendFormatted(String.format(
                                        "%s%s: &fWhitelisted your bed at (%d, %d, %d) &a&l✔&r",
                                        Myau.clientName, getName(), bp.getX(), bp.getY(), bp.getZ()));
                                SoundUtil.playSound("note.pling");
                                return;
                            }
                        }
            }, 3000L, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void onDisabled() {
        alertCooldowns.clear(); trackedPearls.clear(); whitelistedPlayers.clear();
        bedPos = null;
    }
}