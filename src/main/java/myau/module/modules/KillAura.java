package myau.module.modules;

import myau.Myau;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import myau.util.AttackData;
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
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;

public class KillAura extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat df = new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));

    private final TimerUtil timer       = new TimerUtil();
    private final TimerUtil attackTimer = new TimerUtil();
    private AttackData target = null;
    private boolean hitRegistered = false;

    // Grim-compatible deferred attack
    private boolean    deferredAttack = false;
    private AttackData deferredTarget = null;
    public static int  attackCooldownTicks = 0;

    // Separate visual rotation tracking for LOCK_VIEW smoothing.
    // The server always gets the real rotation; the player's camera eases toward it.
    private float visualYaw   = Float.NaN;
    private float visualPitch = Float.NaN;

    // ── Settings ──────────────────────────────────────────────────────────────

    public final DropdownSetting mode          = new DropdownSetting("Mode",        0, "SINGLE", "SWITCH");
    public final DropdownSetting sort          = new DropdownSetting("Sort",        0, "DISTANCE", "HEALTH", "HURT_TIME", "FOV");

    public final SliderSetting swingRange      = new SliderSetting("Swing Range",  3.5, 3.0, 6.0, 0.1);
    public final SliderSetting attackRange     = new SliderSetting("Attack Range", 3.0, 3.0, 6.0, 0.1);
    public final SliderSetting fov             = new SliderSetting("FOV",          360,  30, 360,   1);

    public final SliderSetting minCPS          = new SliderSetting("Min CPS",       8,   1,  20,   1);
    public final SliderSetting maxCPS          = new SliderSetting("Max CPS",      12,   1,  20,   1);
    public final SliderSetting switchDelay     = new SliderSetting("Switch Delay", 150,   0, 1000, 10);

    public final DropdownSetting rotations     = new DropdownSetting("Rotations",  2, "NONE", "LEGIT", "SILENT", "LOCK_VIEW");
    public final DropdownSetting moveFix       = new DropdownSetting("Move Fix",   1, "NONE", "SILENT", "STRICT");
    // Smoothing now controls how smooth the VISUAL camera movement looks.
    // 0 = instant snap visually, 100 = very gradual / human-looking camera arc.
    // The server packet always gets the correct angle regardless of this value.
    public final SliderSetting smoothing       = new SliderSetting("Smoothing",   70,   0, 100,   1);
    public final SliderSetting angleStep       = new SliderSetting("Angle Step", 180,  30, 180,   1);

    public final BooleanSetting throughWalls   = new BooleanSetting("Through Walls",  true);
    public final BooleanSetting requirePress   = new BooleanSetting("Require Press",  false);
    public final BooleanSetting allowMining    = new BooleanSetting("Allow Mining",   true);
    public final BooleanSetting weaponsOnly    = new BooleanSetting("Weapons Only",   false);
    public final BooleanSetting allowTools     = new BooleanSetting("Allow Tools",    true);
    public final BooleanSetting inventoryCheck = new BooleanSetting("Inv Check",      true);
    public final BooleanSetting botCheck       = new BooleanSetting("Bot Check",      true);

    public final BooleanSetting players        = new BooleanSetting("Players",    true);
    public final BooleanSetting bosses         = new BooleanSetting("Bosses",     false);
    public final BooleanSetting mobs           = new BooleanSetting("Mobs",       false);
    public final BooleanSetting animals        = new BooleanSetting("Animals",    false);
    public final BooleanSetting golems         = new BooleanSetting("Golems",     false);
    public final BooleanSetting silverfish     = new BooleanSetting("Silverfish", false);
    public final BooleanSetting teams          = new BooleanSetting("Teams",      true);

    public final DropdownSetting showTarget    = new DropdownSetting("Show Target", 0, "NONE", "DEFAULT", "HUD");
    public final DropdownSetting debugLog      = new DropdownSetting("Debug Log",   0, "NONE", "HEALTH");

    public KillAura() {
        super("KillAura", false);
        register(mode); register(sort);
        register(swingRange); register(attackRange); register(fov);
        register(minCPS); register(maxCPS); register(switchDelay);
        register(rotations); register(moveFix); register(smoothing); register(angleStep);
        register(throughWalls); register(requirePress); register(allowMining);
        register(weaponsOnly); register(allowTools); register(inventoryCheck); register(botCheck);
        register(players); register(bosses); register(mobs); register(animals);
        register(golems); register(silverfish); register(teams);
        register(showTarget); register(debugLog);
    }

    @Override
    public void onDisabled() {
        deferredAttack = false;
        deferredTarget = null;
        attackCooldownTicks = 0;
        visualYaw   = Float.NaN;
        visualPitch = Float.NaN;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long getAttackDelay() {
        int min = Math.min((int) minCPS.getValue(), (int) maxCPS.getValue());
        int max = Math.max((int) minCPS.getValue(), (int) maxCPS.getValue());
        return 1000L / RandomUtil.nextLong(min, max);
    }

    public EntityLivingBase getTarget() { return target != null ? target.getEntity() : null; }

    public boolean isAttackAllowed() {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        try {
            Scaffold scaffold = (Scaffold) Myau.moduleManager.modules.get(Scaffold.class);
            if (scaffold != null && scaffold.isEnabled()) return false;
        } catch (Exception ignored) {}
        if (!weaponsOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant()
                || (allowTools.getValue() && ItemUtil.isHoldingTool())) {
            return !requirePress.getValue()
                    || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
        }
        return false;
    }

    private boolean canAttack() {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        if (inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) return false;
        if (requirePress.getValue() && !mc.gameSettings.keyBindAttack.isKeyDown()) return false;
        if (weaponsOnly.getValue() && !ItemUtil.hasRawUnbreakingEnchant()
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

    private boolean isValidTarget(EntityLivingBase entity) {
        if (entity == null) return false;
        if (!mc.theWorld.loadedEntityList.contains(entity)) return false;
        if (entity == mc.thePlayer || entity == mc.thePlayer.ridingEntity) return false;
        if (entity == mc.getRenderViewEntity() || entity == mc.getRenderViewEntity().ridingEntity) return false;
        if (entity.deathTime > 0 || entity.getHealth() <= 0) return false;
        double distance = mc.thePlayer.getDistanceToEntity(entity);
        if (distance > swingRange.getValue()) return false;
        if (fov.getValue() < 360 && RotationUtil.angleToEntity(entity) > (float) fov.getValue() / 2.0f) return false;
        if (!throughWalls.getValue() && !mc.thePlayer.canEntityBeSeen(entity)) return false;
        if (entity instanceof EntityOtherPlayerMP) {
            if (!players.getValue()) return false;
            if (TeamUtil.isFriend((EntityPlayer) entity)) return false;
            if (teams.getValue() && TeamUtil.isSameTeam((EntityPlayer) entity)) return false;
            if (botCheck.getValue() && TeamUtil.isBot((EntityPlayer) entity)) return false;
            return true;
        }
        if (entity instanceof EntityDragon || entity instanceof EntityWither) return bosses.getValue();
        if (entity instanceof EntityMob || entity instanceof EntitySlime) {
            if (entity instanceof EntitySilverfish)
                return silverfish.getValue() && (!teams.getValue() || !TeamUtil.hasTeamColor(entity));
            return mobs.getValue();
        }
        if (entity instanceof EntityAnimal || entity instanceof EntityBat
                || entity instanceof EntitySquid || entity instanceof EntityVillager)
            return animals.getValue();
        if (entity instanceof EntityIronGolem)
            return golems.getValue() && (!teams.getValue() || !TeamUtil.hasTeamColor(entity));
        return false;
    }

    private boolean isBoxInSwingRange(AxisAlignedBB box)  { return RotationUtil.distanceToBox(box) <= swingRange.getValue(); }
    private boolean isBoxInAttackRange(AxisAlignedBB box) { return RotationUtil.distanceToBox(box) <= attackRange.getValue(); }

    // ── Rotation helpers ──────────────────────────────────────────────────────

    /**
     * Computes the server rotation — always snaps toward the hitbox as fast as
     * angleStep allows. Smoothing has NO effect here; it only affects the visual.
     */
    private float[] computeServerRotation(AxisAlignedBB box, float yaw, float pitch) {
        float step = (float) angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F);
        // Pass 1.0f as the lerp factor → reach the hitbox at full speed (bounded only by angleStep)
        return RotationUtil.getRotationsToBox(box, yaw, pitch, step, 1.0f);
    }

    /**
     * Eases the stored visual yaw/pitch toward the target rotation.
     * Higher smoothing = slower visual movement = looks more human.
     * This is purely cosmetic — it only affects what the LOCAL player's camera shows.
     *
     * lerpFactor range: smoothing=0 → 1.0 (instant), smoothing=100 → 0.05 (very gradual)
     */
    private void updateVisualRotation(float targetYaw, float targetPitch) {
        if (Float.isNaN(visualYaw)) {
            visualYaw   = mc.thePlayer.rotationYaw;
            visualPitch = mc.thePlayer.rotationPitch;
        }
        float lerpFactor = (float)(1.0 - smoothing.getValue() / 100.0 * 0.95);

        // Wrap yaw delta to [-180, 180] to avoid spinning the wrong way
        float dyaw = targetYaw - visualYaw;
        while (dyaw >  180) dyaw -= 360;
        while (dyaw < -180) dyaw += 360;

        visualYaw   += dyaw * lerpFactor;
        visualPitch += (targetPitch - visualPitch) * lerpFactor;
        visualPitch  = Math.max(-90, Math.min(90, visualPitch));
    }

    // ── Attack logic ──────────────────────────────────────────────────────────

    private boolean performAttack(float yaw, float pitch) {
        if (target == null) return false;
        if (Myau.playerStateManager.digging || Myau.playerStateManager.placing) return false;
        if (!attackTimer.hasTimeElapsed(getAttackDelay())) return false;

        try {
            Autoblock autoblock = (Autoblock) Myau.moduleManager.modules.get(Autoblock.class);
            if (autoblock != null && autoblock.isEnabled() && autoblock.isPlayerBlocking()) {
                if (autoblock.isInLegitFullHoldPhase()) return false;
                autoblock.stopBlock();
                deferredAttack = true;
                deferredTarget = target;
                return false;
            }
        } catch (Exception ignored) {}

        mc.thePlayer.swingItem();
        EntityLivingBase targetEntity = target.getEntity();
        if (mc.thePlayer.getDistanceToEntity(targetEntity) <= attackRange.getValue()) {
            AttackEvent event = new AttackEvent(targetEntity);
            EventManager.call(event);
            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
            PacketUtil.sendPacket(new C02PacketUseEntity(targetEntity, Action.ATTACK));
            if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR)
                PlayerUtil.attackEntity(targetEntity);
            hitRegistered = true;
            attackTimer.reset();
            attackCooldownTicks = 5;
            return true;
        }
        attackTimer.reset();
        return false;
    }

    private boolean performDeferredAttack(float yaw, float pitch) {
        if (deferredTarget == null || !isValidTarget(deferredTarget.getEntity())) {
            deferredAttack = false; deferredTarget = null; return false;
        }
        try {
            Autoblock autoblock = (Autoblock) Myau.moduleManager.modules.get(Autoblock.class);
            if (autoblock != null && autoblock.isEnabled() && autoblock.isPlayerBlocking()) return false;
        } catch (Exception ignored) {}
        target = deferredTarget;
        boolean result = performAttack(yaw, pitch);
        deferredAttack = false; deferredTarget = null;
        return result;
    }

    // ── Main update ───────────────────────────────────────────────────────────

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (attackCooldownTicks > 0) attackCooldownTicks--;

        boolean usesRotations = rotations.getIndex() == 2 || rotations.getIndex() == 3;
        boolean lockView      = rotations.getIndex() == 3;

        // ── Deferred attack ───────────────────────────────────────────────────
        if (deferredAttack && deferredTarget != null) {
            if (usesRotations) {
                float[] serverRots = computeServerRotation(deferredTarget.getBox(), event.getYaw(), event.getPitch());
                event.setRotation(serverRots[0], serverRots[1], 1);
                if (lockView) {
                    updateVisualRotation(serverRots[0], serverRots[1]);
                    Myau.rotationManager.setRotation(visualYaw, visualPitch, 1, true);
                }
                if (moveFix.getIndex() != 0 || lockView) event.setPervRotation(serverRots[0], 1);
                performDeferredAttack(serverRots[0], serverRots[1]);
            } else {
                performDeferredAttack(event.getYaw(), event.getPitch());
            }
            return;
        }

        // ── Target selection ──────────────────────────────────────────────────
        if (target == null || !isValidTarget(target.getEntity())
                || timer.hasTimeElapsed((long) switchDelay.getValue())) {
            target = findTarget();
            timer.reset();
            // Reset visual tracking when switching targets so there's no jarring snap
            visualYaw   = Float.NaN;
            visualPitch = Float.NaN;
        }

        if (target == null || !canAttack()) return;

        // ── Rotations & attack ────────────────────────────────────────────────
        if (usesRotations) {
            // Server rotation: always at full speed toward hitbox
            float[] serverRots = computeServerRotation(target.getBox(), event.getYaw(), event.getPitch());
            event.setRotation(serverRots[0], serverRots[1], 1);

            if (lockView) {
                // Visual rotation: smooth camera arc the player sees
                updateVisualRotation(serverRots[0], serverRots[1]);
                Myau.rotationManager.setRotation(visualYaw, visualPitch, 1, true);
            }

            if (moveFix.getIndex() != 0 || lockView) event.setPervRotation(serverRots[0], 1);
            performAttack(serverRots[0], serverRots[1]);
        } else {
            performAttack(event.getYaw(), event.getPitch());
        }
    }

    // ── Target finding ────────────────────────────────────────────────────────

    private AttackData findTarget() {
        if (mc.theWorld == null) return null;
        ArrayList<AttackData> targets = new ArrayList<>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) continue;
            EntityLivingBase living = (EntityLivingBase) entity;
            if (!isValidTarget(living)) continue;
            targets.add(new AttackData(living));
        }
        if (targets.isEmpty()) return null;
        switch (sort.getIndex()) {
            case 0: targets.sort((a, b) -> Double.compare(mc.thePlayer.getDistanceToEntity(a.getEntity()), mc.thePlayer.getDistanceToEntity(b.getEntity()))); break;
            case 1: targets.sort((a, b) -> Float.compare(a.getEntity().getHealth(), b.getEntity().getHealth())); break;
            case 2: targets.sort((a, b) -> Integer.compare(b.getEntity().hurtTime, a.getEntity().hurtTime)); break;
            case 3: targets.sort((a, b) -> Float.compare(RotationUtil.angleToEntity(a.getEntity()), RotationUtil.angleToEntity(b.getEntity()))); break;
        }
        return targets.get(0);
    }
}
