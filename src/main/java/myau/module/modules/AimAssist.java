package myau.module.modules;

import myau.Myau;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.AttackEvent;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer      = new TimerUtil();
    private final TimerUtil attackTimer = new TimerUtil();

    // Smooth visual rotation state (persists across ticks)
    private float smoothedYaw   = Float.NaN;
    private float smoothedPitch = Float.NaN;

    // Silent/KA mode target + attack state
    private EntityLivingBase silentTarget = null;
    public static int attackCooldownTicks = 0; // read by Autoblock

    // ── Mode ──────────────────────────────────────────────────────────────────
    // 0 = ASSIST  — visual aim assist only (moves your crosshair)
    // 1 = SILENT  — silent rotations + auto-attacks (full KillAura replacement)
    public final DropdownSetting mode = register(new DropdownSetting("Mode", 0, "ASSIST", "SILENT"));

    // ── Shared settings ───────────────────────────────────────────────────────
    public final SliderSetting  hSpeed     = register(new SliderSetting("H-Speed",    2.0, 0.0, 10.0, 0.1));
    public final SliderSetting  vSpeed     = register(new SliderSetting("V-Speed",    0.0, 0.0, 10.0, 0.1));
    public final SliderSetting  smoothing  = register(new SliderSetting("Smoothing",  70,  0,   100,  1));
    public final SliderSetting  range      = register(new SliderSetting("Range",      4.5, 3.0, 8.0,  0.1));
    public final SliderSetting  fov        = register(new SliderSetting("FOV",        90,  30,  360,  1));
    public final BooleanSetting weaponOnly = register(new BooleanSetting("Weapons Only", true));
    public final BooleanSetting allowTools = register(new BooleanSetting("Allow Tools",  false));
    public final BooleanSetting botChecks  = register(new BooleanSetting("Bot Check",    true));
    public final BooleanSetting team       = register(new BooleanSetting("Teams",        true));

    // ── Silent/KA-only settings ───────────────────────────────────────────────
    public final SliderSetting  attackRange   = register(new SliderSetting("Attack Range", 3.0, 3.0, 6.0, 0.1));
    public final SliderSetting  swingRange    = register(new SliderSetting("Swing Range",  3.5, 3.0, 6.0, 0.1));
    public final SliderSetting  minCPS        = register(new SliderSetting("Min CPS",       8,   1,  20,  1));
    public final SliderSetting  maxCPS        = register(new SliderSetting("Max CPS",      12,   1,  20,  1));
    public final DropdownSetting sort         = register(new DropdownSetting("Sort",   0, "DISTANCE", "HEALTH", "HURT_TIME", "FOV"));
    public final DropdownSetting moveFix      = register(new DropdownSetting("Move Fix", 1, "NONE", "SILENT", "STRICT"));
    public final BooleanSetting  throughWalls = register(new BooleanSetting("Through Walls", true));
    public final BooleanSetting  requirePress = register(new BooleanSetting("Require Press",  false));
    public final BooleanSetting  allowMining  = register(new BooleanSetting("Allow Mining",   true));
    public final BooleanSetting  players      = register(new BooleanSetting("Players",    true));
    public final BooleanSetting  bosses       = register(new BooleanSetting("Bosses",     false));
    public final BooleanSetting  mobs         = register(new BooleanSetting("Mobs",       false));
    public final BooleanSetting  animals      = register(new BooleanSetting("Animals",    false));
    public final BooleanSetting  golems       = register(new BooleanSetting("Golems",     false));
    public final BooleanSetting  silverfish   = register(new BooleanSetting("Silverfish", false));

    public AimAssist() {
        super("AimAssist", false);
    }

    @Override
    public void onDisabled() {
        smoothedYaw       = Float.NaN;
        smoothedPitch     = Float.NaN;
        silentTarget      = null;
        attackCooldownTicks = 0;
    }

    // ── Target API (used by TargetHUD, TargetStrafe, etc via KillAura stub) ───

    public EntityLivingBase getTarget() { return silentTarget; }

    public boolean isAttackAllowed() {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        if (mode.getIndex() != 1) return false;
        try { if (Myau.moduleManager.modules.get(Scaffold.class).isEnabled()) return false; }
        catch (Exception ignored) {}
        if (!weaponOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant()
                || (allowTools.getValue() && ItemUtil.isHoldingTool())) {
            return !requirePress.getValue()
                    || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
        }
        return false;
    }

    // ── Assist-mode target validation (players only) ───────────────────────────

    private boolean isValidAssistTarget(EntityPlayer p) {
        if (p == mc.thePlayer || p == mc.thePlayer.ridingEntity) return false;
        if (p == mc.getRenderViewEntity() || p == mc.getRenderViewEntity().ridingEntity) return false;
        if (p.deathTime > 0) return false;
        if (RotationUtil.distanceToEntity(p) > range.getValue()) return false;
        if (RotationUtil.angleToEntity(p) > (float) fov.getValue()) return false;
        if (RotationUtil.rayTrace(p) != null) return false;
        if (TeamUtil.isFriend(p)) return false;
        return (!team.getValue() || !TeamUtil.isSameTeam(p))
                && (!botChecks.getValue() || !TeamUtil.isBot(p));
    }

    // ── Silent-mode target validation (all entity types) ──────────────────────

    private boolean isValidSilentTarget(EntityLivingBase entity) {
        if (entity == null) return false;
        if (!mc.theWorld.loadedEntityList.contains(entity)) return false;
        if (entity == mc.thePlayer || entity == mc.thePlayer.ridingEntity) return false;
        if (entity == mc.getRenderViewEntity() || entity == mc.getRenderViewEntity().ridingEntity) return false;
        if (entity.deathTime > 0 || entity.getHealth() <= 0) return false;
        if (mc.thePlayer.getDistanceToEntity(entity) > swingRange.getValue()) return false;
        if (fov.getValue() < 360 && RotationUtil.angleToEntity(entity) > (float) fov.getValue() / 2f) return false;
        if (!throughWalls.getValue() && !mc.thePlayer.canEntityBeSeen(entity)) return false;

        if (entity instanceof EntityOtherPlayerMP) {
            if (!players.getValue()) return false;
            if (TeamUtil.isFriend((EntityPlayer) entity)) return false;
            if (team.getValue() && TeamUtil.isSameTeam((EntityPlayer) entity)) return false;
            if (botChecks.getValue() && TeamUtil.isBot((EntityPlayer) entity)) return false;
            return true;
        }
        if (entity instanceof EntityDragon || entity instanceof EntityWither) return bosses.getValue();
        if (entity instanceof EntityMob || entity instanceof EntitySlime) {
            if (entity instanceof EntitySilverfish)
                return silverfish.getValue() && (!team.getValue() || !TeamUtil.hasTeamColor(entity));
            return mobs.getValue();
        }
        if (entity instanceof EntityAnimal || entity instanceof EntityBat
                || entity instanceof EntitySquid || entity instanceof EntityVillager)
            return animals.getValue();
        if (entity instanceof EntityIronGolem)
            return golems.getValue() && (!team.getValue() || !TeamUtil.hasTeamColor(entity));
        return false;
    }

    // ── Silent target finder ───────────────────────────────────────────────────

    private EntityLivingBase findSilentTarget() {
        if (mc.theWorld == null) return null;
        List<EntityLivingBase> list = new ArrayList<>();
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof EntityLivingBase)) continue;
            if (!isValidSilentTarget((EntityLivingBase) e)) continue;
            list.add((EntityLivingBase) e);
        }
        if (list.isEmpty()) return null;
        switch (sort.getIndex()) {
            case 0: list.sort(Comparator.comparingDouble(e -> mc.thePlayer.getDistanceToEntity(e))); break;
            case 1: list.sort(Comparator.comparingDouble(e -> e.getHealth())); break;
            case 2: list.sort((a, b) -> Integer.compare(b.hurtTime, a.hurtTime)); break;
            case 3: list.sort(Comparator.comparingDouble(e -> RotationUtil.angleToEntity(e))); break;
        }
        return list.get(0);
    }

    // ── Smooth rotation helper (shared by both modes) ─────────────────────────

    /**
     * Advances smoothedYaw/Pitch toward the target by lerpFactor per tick.
     * Because state persists, the rotation genuinely glides rather than
     * recalculating the full gap from scratch every tick.
     */
    private float[] advanceSmoothed(AxisAlignedBB box, float maxStep, boolean capBySpeed) {
        if (Float.isNaN(smoothedYaw))   smoothedYaw   = mc.thePlayer.rotationYaw;
        if (Float.isNaN(smoothedPitch)) smoothedPitch = mc.thePlayer.rotationPitch;

        float[] ideal = RotationUtil.getRotationsToBox(box, smoothedYaw, smoothedPitch, maxStep, 0f);

        float sm = (float) smoothing.getValue() / 100f;
        float lerpT = (1f - sm * 0.95f) + RandomUtil.nextFloat(-0.01f, 0.01f);
        lerpT = MathHelper.clamp_float(lerpT, 0.04f, 1.0f);

        float yawDiff   = MathHelper.wrapAngleTo180_float(ideal[0] - smoothedYaw);
        float pitchDiff = ideal[1] - smoothedPitch;

        if (capBySpeed) {
            yawDiff   = MathHelper.clamp_float(yawDiff,   -(float) hSpeed.getValue(), (float) hSpeed.getValue());
            pitchDiff = vSpeed.getValue() > 0
                    ? MathHelper.clamp_float(pitchDiff, -(float) vSpeed.getValue(), (float) vSpeed.getValue())
                    : 0f;
        }

        smoothedYaw   = RotationUtil.quantizeAngle(smoothedYaw   + yawDiff   * lerpT);
        smoothedPitch = RotationUtil.quantizeAngle(
                MathHelper.clamp_float(smoothedPitch + pitchDiff * lerpT, -90f, 90f));

        return new float[]{ smoothedYaw, smoothedPitch };
    }

    // ── ASSIST mode (TickEvent — same as before) ───────────────────────────────

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
        if (!isEnabled() || mode.getIndex() != 0) return; // ASSIST only
        if (event.getType() != EventType.POST || mc.currentScreen != null) return;
        if (!weaponOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant()
                || (allowTools.getValue() && ItemUtil.isHoldingTool())) {

            boolean attacking = PlayerUtil.isAttacking();
            if (!attacking || !isLookingAtBlock()) {
                if (attacking || !timer.hasTimeElapsed(350L)) {
                    List<EntityPlayer> inRange = mc.theWorld.loadedEntityList.stream()
                            .filter(e -> e instanceof EntityPlayer)
                            .map(e -> (EntityPlayer) e)
                            .filter(this::isValidAssistTarget)
                            .sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                            .collect(Collectors.toList());

                    if (inRange.isEmpty()) { smoothedYaw = Float.NaN; smoothedPitch = Float.NaN; return; }
                    if (inRange.stream().anyMatch(this::isInReach)) inRange.removeIf(p -> !isInReach(p));

                    EntityPlayer player = inRange.get(0);
                    if (RotationUtil.distanceToEntity(player) <= 0.0) return;

                    AxisAlignedBB bb     = player.getEntityBoundingBox();
                    float         border = player.getCollisionBorderSize();
                    float[] r = advanceSmoothed(bb.expand(border, border, border), 180f, true);
                    Myau.rotationManager.setRotation(r[0], r[1], 0, false);
                }
            }
        }
    }

    // ── SILENT mode (UpdateEvent — KillAura replacement) ──────────────────────

    private long getAttackDelay() {
        int mn = Math.min((int) minCPS.getValue(), (int) maxCPS.getValue());
        int mx = Math.max((int) minCPS.getValue(), (int) maxCPS.getValue());
        return 1000L / RandomUtil.nextLong(mn, mx);
    }

    private boolean canSilentAttack() {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        if (mc.currentScreen instanceof GuiContainer) return false;
        if (requirePress.getValue() && !mc.gameSettings.keyBindAttack.isKeyDown()) return false;
        if (weaponOnly.getValue() && !ItemUtil.hasRawUnbreakingEnchant()
                && !(allowTools.getValue() && ItemUtil.isHoldingTool())) return false;
        if (((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()
                && !allowMining.getValue()) return false;
        if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) return false;
        try {
            AutoHeal autoHeal = (AutoHeal) Myau.moduleManager.modules.get(AutoHeal.class);
            if (autoHeal != null && autoHeal.isEnabled() && autoHeal.isSwitching()) return false;
            BedNuker bedNuker = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
            if (bedNuker != null && bedNuker.isEnabled() && bedNuker.isReady()) return false;
            if (Myau.moduleManager.modules.get(Scaffold.class).isEnabled()) return false;
        } catch (Exception ignored) {}
        return true;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || mode.getIndex() != 1) return; // SILENT only
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (attackCooldownTicks > 0) attackCooldownTicks--;

        // Refresh target
        if (silentTarget == null || !isValidSilentTarget(silentTarget)
                || timer.hasTimeElapsed(200L)) {
            EntityLivingBase prev = silentTarget;
            silentTarget = findSilentTarget();
            timer.reset();
            if (silentTarget != prev) { smoothedYaw = Float.NaN; smoothedPitch = Float.NaN; }
        }

        if (silentTarget == null) return;

        AxisAlignedBB box = silentTarget.getEntityBoundingBox()
                .expand(silentTarget.getCollisionBorderSize(),
                        silentTarget.getCollisionBorderSize(),
                        silentTarget.getCollisionBorderSize());

        float[] r = advanceSmoothed(box, 180f, false);

        // Apply silent rotation (doesn't move the player's visible crosshair)
        event.setRotation(r[0], r[1], 1);
        if (moveFix.getIndex() != 0) event.setPervRotation(r[0], 1);

        if (!canSilentAttack()) return;
        if (Myau.playerStateManager.digging || Myau.playerStateManager.placing) return;
        if (!attackTimer.hasTimeElapsed(getAttackDelay())) return;

        // Coordinate with Autoblock — release block before attacking
        try {
            Autoblock ab = (Autoblock) Myau.moduleManager.modules.get(Autoblock.class);
            if (ab != null && ab.isEnabled() && ab.isPlayerBlocking()) {
                if (ab.isInLegitFullHoldPhase()) return;
                ab.stopBlock();
                // Autoblock will reblock next tick after attackCooldownTicks expires
                return;
            }
        } catch (Exception ignored) {}

        mc.thePlayer.swingItem();

        if (mc.thePlayer.getDistanceToEntity(silentTarget) <= attackRange.getValue()) {
            AttackEvent attackEvent = new AttackEvent(silentTarget);
            EventManager.call(attackEvent);
            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
            PacketUtil.sendPacket(new C02PacketUseEntity(silentTarget, C02PacketUseEntity.Action.ATTACK));
            if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR)
                PlayerUtil.attackEntity(silentTarget);
            attackCooldownTicks = 5;
        }
        attackTimer.reset();
    }

    // ── Key press (timer for assist mode) ─────────────────────────────────────

    @EventTarget
    public void onPress(KeyEvent event) {
        if (event.getKey() == mc.gameSettings.keyBindAttack.getKeyCode()
                && !Myau.moduleManager.modules.get(AutoClicker.class).isEnabled()) {
            timer.reset();
        }
    }
}
