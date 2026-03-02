package myau.module.modules;

import myau.Myau;
import myau.mixin.IAccessorKeyBinding;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.text.NumberFormat;
import java.util.Stack;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Pit extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // ── Submodule toggles ────────────────────────────────────────────────────

    public final BooleanSetting aimAssist = register(new BooleanSetting("Aim Assist", false));

    // ── Aim Assist settings — only visible when aimAssist is on ──────────────

    public final SliderSetting  aaHSpeed    = register(new SliderSetting("  H-Speed",    3.0, 0.0, 10.0, 0.1,  () -> aimAssist.getValue()));
    public final SliderSetting  aaVSpeed    = register(new SliderSetting("  V-Speed",    0.0, 0.0, 10.0, 0.1,  () -> aimAssist.getValue()));
    public final SliderSetting  aaSmoothing = register(new SliderSetting("  Smoothing",  50,  0,   100,   1,   () -> aimAssist.getValue()));
    public final SliderSetting  aaRange     = register(new SliderSetting("  Range",      4.5, 3.0, 8.0,  0.1,  () -> aimAssist.getValue()));
    public final SliderSetting  aaFov       = register(new SliderSetting("  FOV",        90,  30,  360,   1,   () -> aimAssist.getValue()));
    public final BooleanSetting aaWeapon    = register(new BooleanSetting("  Weapons Only",  true,  () -> aimAssist.getValue()));
    public final BooleanSetting aaTools     = register(new BooleanSetting("  Allow Tools",   false, () -> aimAssist.getValue()));
    public final BooleanSetting aaBotCheck  = register(new BooleanSetting("  Bot Check",     true,  () -> aimAssist.getValue()));
    public final BooleanSetting aaTeam      = register(new BooleanSetting("  Teams",          true,  () -> aimAssist.getValue()));

    // Requirements — filter who gets targeted
    public final BooleanSetting aaReqArmor     = register(new BooleanSetting("  Req: Armor",        false, () -> aimAssist.getValue()));
    public final DropdownSetting aaArmorTier   = register(new DropdownSetting("  Armor Tier",   0, () -> aimAssist.getValue() && aaReqArmor.getValue(), "Any", "Leather", "Chain", "Iron", "Gold", "Diamond"));
    public final BooleanSetting aaReqHealth    = register(new BooleanSetting("  Req: Health",       false, () -> aimAssist.getValue()));
    public final SliderSetting  aaMaxHealth    = register(new SliderSetting("  Max Health",   10,  1, 20, 1,   () -> aimAssist.getValue() && aaReqHealth.getValue()));

    private final TimerUtil aaTimer = new TimerUtil();

    // ── Gold Requirement submodule ────────────────────────────────────────────

    public final BooleanSetting goldReq = register(new BooleanSetting("Gold Req", false));
    public final SliderSetting  grX     = register(new SliderSetting("  HUD X", 10, 0, 500, 1, () -> goldReq.getValue()));
    public final SliderSetting  grY     = register(new SliderSetting("  HUD Y", 10, 0, 300, 1, () -> goldReq.getValue()));

    private double grGained = 0, grNeeded = 0;
    private int    grTick   = 0;

    // ── Streak submodule ──────────────────────────────────────────────────────

    public final BooleanSetting streak   = register(new BooleanSetting("Streak", false));
    public final SliderSetting  stX      = register(new SliderSetting("  Streak X",       5,  0, 500, 1, () -> streak.getValue()));
    public final SliderSetting  stY      = register(new SliderSetting("  Streak Y",      50,  0, 300, 1, () -> streak.getValue()));
    public final SliderSetting  stOpacity = register(new SliderSetting("  Box Opacity",  33,  0, 100, 1, () -> streak.getValue()));

    private int   stKills   = 0;
    private int   stAssists = 0;
    private float stXP      = 0f;
    private float stGold    = 0f;
    private boolean stPaused = true;
    private final TimerUtil stTimer = new TimerUtil();

    // ── AutoMath submodule ────────────────────────────────────────────────────

    public final BooleanSetting autoMath       = register(new BooleanSetting("AutoMath", false));
    public final SliderSetting  amDelay        = register(new SliderSetting("  Delay", 1000, 0, 5000, 100, () -> autoMath.getValue()));
    public final BooleanSetting amAutoSubmit   = register(new BooleanSetting("  Auto Submit", true, () -> autoMath.getValue()));

    private String amPendingSolution = null;
    private static final ScheduledExecutorService amScheduler = Executors.newScheduledThreadPool(1);

    // ── AutoGrinder submodule ─────────────────────────────────────────────────

    public final BooleanSetting autoGrinder  = register(new BooleanSetting("AutoGrinder", false));
    public final DropdownSetting agSorting   = register(new DropdownSetting("  Sorting",    0, () -> autoGrinder.getValue(), "Distance", "Health"));
    public final DropdownSetting agMode      = register(new DropdownSetting("  Mode",       0, () -> autoGrinder.getValue(), "Legit", "Blatant"));
    public final SliderSetting   agRange     = register(new SliderSetting("  Attack Range", 4.0, 3.0, 6.0, 0.1, () -> autoGrinder.getValue()));
    public final SliderSetting   agFov       = register(new SliderSetting("  FOV",         30,  1,  360,   1,   () -> autoGrinder.getValue()));

    private int agTicks = 0;

    public Pit() {
        super("Pit", false);
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null) agSetKeys(false);
    }

    // ── Aim Assist logic ──────────────────────────────────────────────────────

    private boolean passesRequirements(EntityPlayer p) {
        // Health requirement
        if (aaReqHealth.getValue() && p.getHealth() > aaMaxHealth.getValue()) return false;

        // Armor tier requirement
        if (aaReqArmor.getValue()) {
            String tier = aaArmorTier.getValue();
            if (!tier.equals("Any") && !playerHasArmorTier(p, tier)) return false;
        }

        return true;
    }

    private int countArmorPieces(EntityPlayer p, String tier) {
        int count = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack armor = p.getCurrentArmor(i);
            if (armor == null || !(armor.getItem() instanceof ItemArmor)) continue;
            ItemArmor.ArmorMaterial mat = ((ItemArmor) armor.getItem()).getArmorMaterial();
            String matName = mat.name().toLowerCase();
            boolean matches =
                (tier.equalsIgnoreCase("Leather") && matName.equals("cloth"))   ||
                (tier.equalsIgnoreCase("Chain")   && matName.equals("chain"))   ||
                (tier.equalsIgnoreCase("Iron")    && matName.equals("iron"))    ||
                (tier.equalsIgnoreCase("Gold")    && matName.equals("gold"))    ||
                (tier.equalsIgnoreCase("Diamond") && matName.equals("diamond"));
            if (matches) count++;
        }
        return count;
    }

    private boolean playerHasArmorTier(EntityPlayer p, String tier) {
        if (tier.equals("Any")) return true;
        // Target must have at least 2 pieces of the specified tier
        if (countArmorPieces(p, tier) < 2) return false;
        // And must NOT have 2 or more diamond pieces (to avoid locking onto diamond players)
        if (!tier.equalsIgnoreCase("Diamond") && countArmorPieces(p, "Diamond") >= 2) return false;
        return true;
    }

    private boolean isValidAaTarget(EntityPlayer p) {
        if (p == mc.thePlayer || p == mc.thePlayer.ridingEntity) return false;
        if (p == mc.getRenderViewEntity() || p == mc.getRenderViewEntity().ridingEntity) return false;
        if (p.deathTime > 0) return false;
        if (RotationUtil.distanceToEntity(p) > aaRange.getValue()) return false;
        if (RotationUtil.angleToEntity(p) > (float) aaFov.getValue()) return false;
        if (RotationUtil.rayTrace(p) != null) return false;
        if (TeamUtil.isFriend(p)) return false;
        if (aaBotCheck.getValue() && TeamUtil.isBot(p)) return false;
        if (aaTeam.getValue() && TeamUtil.isSameTeam(p)) return false;
        if (!passesRequirements(p)) return false;
        return true;
    }

    private boolean isInReach(EntityPlayer p) {
        Reach reach = (Reach) Myau.moduleManager.modules.get(Reach.class);
        double distance = reach.isEnabled() ? reach.range.getValue() : 3.0;
        return RotationUtil.distanceToEntity(p) <= distance;
    }

    private boolean isLookingAtBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.POST || mc.currentScreen != null) return;

        // ── Gold Requirement ──────────────────────────────────────────────────
        if (goldReq.getValue() && mc.thePlayer != null && mc.theWorld != null) {
            grTick++;
            if (grTick % 100 == 0) {
                PacketUtil.sendPacket(new C01PacketChatMessage("/goldreq " + mc.thePlayer.getName()));
            }
        }

        // ── Aim Assist ────────────────────────────────────────────────────────
        if (aimAssist.getValue()) {
            if (!aaWeapon.getValue() || ItemUtil.hasRawUnbreakingEnchant()
                    || (aaTools.getValue() && ItemUtil.isHoldingTool())) {

                boolean attacking = PlayerUtil.isAttacking();
                if (!attacking || !isLookingAtBlock()) {
                    if (attacking || !aaTimer.hasTimeElapsed(350L)) {

                        List<EntityPlayer> inRange = mc.theWorld.loadedEntityList.stream()
                                .filter(e -> e instanceof EntityPlayer)
                                .map(e -> (EntityPlayer) e)
                                .filter(this::isValidAaTarget)
                                .sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                                .collect(Collectors.toList());

                        if (inRange.isEmpty()) return;

                        if (inRange.stream().anyMatch(this::isInReach))
                            inRange.removeIf(p -> !isInReach(p));

                        EntityPlayer target = inRange.get(0);
                        if (RotationUtil.distanceToEntity(target) <= 0.0) return;

                        AxisAlignedBB bb     = target.getEntityBoundingBox();
                        double        border = target.getCollisionBorderSize();
                        float[] rotation = RotationUtil.getRotationsToBox(
                                bb.expand(border, border, border),
                                mc.thePlayer.rotationYaw,
                                mc.thePlayer.rotationPitch,
                                180.0F,
                                (float) aaSmoothing.getValue() / 100.0F
                        );

                        float yaw   = (float) Math.min(Math.abs(aaHSpeed.getValue()), 10.0);
                        float pitch = (float) Math.min(Math.abs(aaVSpeed.getValue()), 10.0);

                        Myau.rotationManager.setRotation(
                                mc.thePlayer.rotationYaw   + (rotation[0] - mc.thePlayer.rotationYaw)   * 0.1F * yaw,
                                mc.thePlayer.rotationPitch + (rotation[1] - mc.thePlayer.rotationPitch) * 0.1F * pitch,
                                0,
                                false
                        );
                    }
                }
            }
        }

        // ── AutoGrinder ───────────────────────────────────────────────────────
        if (autoGrinder.getValue()) {
            agTicks++;
            EntityLivingBase agTarget = agGetTarget();
            if (agTarget == null) {
                agSetKeys(false);
            } else {
                // Rotations
                double[] yawPitch = agGetYawPitch(agTarget);
                float targetYaw   = (float) yawPitch[0];
                float targetPitch = (float) yawPitch[1];
                if (agMode.getValue().equals("Legit")) {
                    float smoothYaw   = mc.thePlayer.rotationYaw   + 0.1f * (targetYaw   - mc.thePlayer.rotationYaw);
                    float smoothPitch = mc.thePlayer.rotationPitch + 0.1f * (targetPitch - mc.thePlayer.rotationPitch);
                    Myau.rotationManager.setRotation(smoothYaw, smoothPitch, 1, false);
                } else {
                    Myau.rotationManager.setRotation(targetYaw, targetPitch, 1, true);
                }
                agSetKeys(true);
                // Attack every 2 ticks within range
                if (agTicks % 2 == 0 && mc.thePlayer.getDistanceToEntity(agTarget) <= agRange.getValue()) {
                    double yawDiff   = Math.abs(mc.thePlayer.rotationYaw   - targetYaw);
                    double pitchDiff = Math.abs(mc.thePlayer.rotationPitch - targetPitch);
                    if (agMode.getValue().equals("Blatant") || (yawDiff <= agFov.getValue() && pitchDiff <= agFov.getValue())) {
                        PlayerUtil.attackEntity(agTarget);
                        mc.thePlayer.swingItem();
                    }
                }
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || !goldReq.getValue()) return;
        if (event.getType() != EventType.PRE) return;
        if (!(event.getPacket() instanceof S02PacketChat)) return;

        String msg = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();
        String name = mc.thePlayer != null ? mc.thePlayer.getName() : "";
        if (!msg.contains(name + ":")) return;

        try {
            String part = msg.split(name + ":")[1].trim();
            String[] parts = part.split("/");
            if (parts.length == 2) {
                grGained = Double.parseDouble(parts[0].replaceAll("[^0-9.]", ""));
                grNeeded = Double.parseDouble(parts[1].replaceAll("[^0-9.]", ""));
                event.setCancelled(true); // hide from chat
            }
        } catch (Exception ignored) {}

        // ── Streak chat parsing ───────────────────────────────────────────────
        if (streak.getValue()) {
            String raw = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();
            if (raw.contains("DEATH!")) {
                stPaused = true;
                stKills = 0; stAssists = 0; stXP = 0f; stGold = 0f;
                stTimer.reset();
            } else if (raw.contains("KILL!")) {
                stKills++;
                stPaused = false;
            } else if (raw.contains("ASSIST!")) {
                stAssists++;
            } else {
                try {
                    if (raw.contains("XP") && raw.contains("+")) {
                        String xpPart = raw.split("\\+")[1].split("XP")[0].trim();
                        stXP += Float.parseFloat(xpPart);
                    }
                    if (raw.contains("g") && raw.split("\\+").length > 2) {
                        String goldPart = raw.split("\\+")[2].split("g")[0].trim();
                        stGold += Float.parseFloat(goldPart);
                    }
                } catch (Exception ignored2) {}
            }
        }

        // ── AutoMath chat parsing ─────────────────────────────────────────────
        if (autoMath.getValue()) {
            String raw = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();
            String prefix = "QUICK MATHS! Solve: ";
            if (raw.startsWith(prefix)) {
                String equation = raw.substring(prefix.length()).replace("x", "*");
                try {
                    double result = amEvaluate(equation.replaceAll("\\s+", ""));
                    long rounded = Math.round(result);
                    amPendingSolution = Long.toString(rounded);
                    mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.LIGHT_PURPLE + "" + EnumChatFormatting.BOLD + "QUICK MATHS! "
                        + EnumChatFormatting.GRAY + "Result: " + EnumChatFormatting.YELLOW + rounded));
                    if (amAutoSubmit.getValue()) {
                        amScheduler.schedule(() -> {
                            if (amPendingSolution != null && mc.thePlayer != null) {
                                mc.thePlayer.sendChatMessage("/ac " + amPendingSolution);
                                amPendingSolution = null;
                            }
                        }, (long) amDelay.getValue(), TimeUnit.MILLISECONDS);
                    }
                } catch (Exception e) {
                    mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.RED + "AutoMath error: " + e.getMessage()));
                }
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || !goldReq.getValue()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        NumberFormat nf = NumberFormat.getNumberInstance();
        String text = "§aGold Req: §6" + nf.format(grGained) + "§7/§6" + nf.format(grNeeded) + "g";
        mc.fontRendererObj.drawStringWithShadow(text, (float) grX.getValue(), (float) grY.getValue(), 0xFFFFFFFF);

        // ── Streak HUD ────────────────────────────────────────────────────────
        if (streak.getValue()) {
            int sx = (int) stX.getValue();
            int sy = (int) stY.getValue();
            int alpha = (int)(stOpacity.getValue() / 100.0 * 255.0);
            // Background box
            net.minecraft.client.gui.Gui.drawRect(sx, sy, sx + 90, sy + 98, (alpha << 24));

            String statusCol = stPaused ? "§c" : "§a";
            String status    = stPaused ? "Last" : "Active";
            long   elapsed   = stTimer.getElapsedTime() / 1000L;

            mc.fontRendererObj.drawStringWithShadow("§cS§at§dr§9e§6a§e§3k §7[" + statusCol + status + "§7]", sx + 5, sy + 5,  0xFFFFFFFF);
            mc.fontRendererObj.drawStringWithShadow("§aKills§f: §a"  + stKills,                              sx + 5, sy + 20, 0xFFFFFFFF);
            mc.fontRendererObj.drawStringWithShadow("§cAssists§f: §c" + stAssists,                           sx + 5, sy + 35, 0xFFFFFFFF);
            mc.fontRendererObj.drawStringWithShadow("§bXP§f: §f"     + String.format("%.1f", stXP),          sx + 5, sy + 50, 0xFFFFFFFF);
            mc.fontRendererObj.drawStringWithShadow("§6Gold§f: §6"   + String.format("%.1f", stGold),        sx + 5, sy + 65, 0xFFFFFFFF);
            mc.fontRendererObj.drawStringWithShadow("Time: "         + formatStreakTime(elapsed),             sx + 5, sy + 80, 0xFFFFFFFF);
        }
    }

    @EventTarget
    public void onPress(KeyEvent event) {
        if (!isEnabled()) return;
        if (aimAssist.getValue()
                && event.getKey() == mc.gameSettings.keyBindAttack.getKeyCode()
                && !Myau.moduleManager.modules.get(AutoClicker.class).isEnabled()) {
            aaTimer.reset();
        }
    }

    // ── AutoGrinder helpers ───────────────────────────────────────────────────

    private EntityLivingBase agGetTarget() {
        if (mc.theWorld == null || mc.thePlayer == null) return null;
        List<EntityLivingBase> targets = mc.theWorld.loadedEntityList.stream()
            .filter(e -> e instanceof EntityLivingBase && e != mc.thePlayer)
            .map(e -> (EntityLivingBase) e)
            .filter(e -> !e.isDead && e.getHealth() > 0)
            .collect(Collectors.toList());

        if (agSorting.getValue().equals("Health")) {
            return targets.stream()
                .min(Comparator.comparing(EntityLivingBase::getHealth))
                .orElse(null);
        } else {
            return targets.stream()
                .min(Comparator.comparingDouble(e -> mc.thePlayer.getDistanceToEntity(e)))
                .orElse(null);
        }
    }

    private double[] agGetYawPitch(EntityLivingBase target) {
        double dx = target.posX - mc.thePlayer.posX;
        double dy = target.posY + target.getEyeHeight() - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = target.posZ - mc.thePlayer.posZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double pitch = -Math.asin(dy / dist) * (180.0 / Math.PI);
        double yaw   =  Math.atan2(dz, dx)   * (180.0 / Math.PI) - 90.0;
        return new double[]{yaw, pitch};
    }

    private void agSetKeys(boolean on) {
        ((IAccessorKeyBinding) mc.gameSettings.keyBindForward).setPressed(on);
        ((IAccessorKeyBinding) mc.gameSettings.keyBindAttack).setPressed(on);
        ((IAccessorKeyBinding) mc.gameSettings.keyBindJump).setPressed(on);
        if (!on) {
            ((IAccessorKeyBinding) mc.gameSettings.keyBindBack).setPressed(false);
            ((IAccessorKeyBinding) mc.gameSettings.keyBindLeft).setPressed(false);
            ((IAccessorKeyBinding) mc.gameSettings.keyBindRight).setPressed(false);
        }
    }

    // ── Math solver ───────────────────────────────────────────────────────────

    private double amEvaluate(String eq) throws Exception {
        Stack<Double> vals = new Stack<>();
        Stack<Character> ops = new Stack<>();
        for (int i = 0; i < eq.length(); i++) {
            char ch = eq.charAt(i);
            if (Character.isDigit(ch) || ch == '.') {
                StringBuilder sb = new StringBuilder();
                while (i < eq.length() && (Character.isDigit(eq.charAt(i)) || eq.charAt(i) == '.'))
                    sb.append(eq.charAt(i++));
                vals.push(Double.parseDouble(sb.toString()));
                i--;
            } else if (ch == '(') {
                ops.push(ch);
            } else if (ch == ')') {
                while (ops.peek() != '(')
                    vals.push(amApply(ops.pop(), vals.pop(), vals.pop()));
                ops.pop();
            } else if (amIsOp(ch)) {
                while (!ops.isEmpty() && amPrec(ch) <= amPrec(ops.peek()))
                    vals.push(amApply(ops.pop(), vals.pop(), vals.pop()));
                ops.push(ch);
            }
        }
        while (!ops.isEmpty()) vals.push(amApply(ops.pop(), vals.pop(), vals.pop()));
        return vals.pop();
    }

    private boolean amIsOp(char c) { return c == '+' || c == '-' || c == '*' || c == '/'; }

    private int amPrec(char c) {
        if (c == '*' || c == '/') return 2;
        if (c == '+' || c == '-') return 1;
        return -1;
    }

    private double amApply(char op, double b, double a) throws Exception {
        switch (op) {
            case '+': return a + b;
            case '-': return a - b;
            case '*': return a * b;
            case '/':
                if (b == 0) throw new Exception("Division by zero");
                return a / b;
            default: throw new Exception("Unknown operator: " + op);
        }
    }

    private String formatStreakTime(long seconds) {
        long hrs  = seconds / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (hrs  > 0) sb.append(hrs).append("hr ");
        if (mins > 0) sb.append(mins).append("m ");
        sb.append(secs).append("s");
        return sb.toString().trim();
    }
}
