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
import net.minecraft.util.MathHelper;
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

    // --- Smooth rotation state ---
    // These persist between ticks so the angle glides smoothly rather than
    // recalculating the full gap-to-target every tick (which is what made
    // smoothing feel instant regardless of the setting).
    private float smoothYaw   = Float.NaN;
    private float smoothPitch = Float.NaN;

    // Grim-compatible deferred attack after releasing block
    private boolean    deferredAttack = false;
    private AttackData deferredTarget = null;
    public static int  attackCooldownTicks = 0;

    // ── Settings ───────────────────────────────────────────────────────────────

    public final DropdownSetting mode      = new DropdownSetting("Mode", 0, "SINGLE", "SWITCH");
    public final DropdownSetting sort      = new DropdownSetting("Sort", 0, "DISTANCE", "HEALTH", "HURT_TIME", "FOV");

    public final SliderSetting swingRange  = new SliderSetting("Swing Range",  3.5, 3.0, 6.0, 0.1);
    public final SliderSetting attackRange = new SliderSetting("Attack Range", 3.0, 3.0, 6.0, 0.1);
    public final SliderSetting fov         = new SliderSetting("FOV",          360,  30, 360,   1);

    public final SliderSetting minCPS      = new SliderSetting("Min CPS",       8,   1,  20,   1);
    public final SliderSetting maxCPS      = new SliderSetting("Max CPS",      12,   1,  20,   1);
    public final SliderSetting switchDelay = new SliderSetting("Switch Delay", 150,   0, 1000, 10);

    public final DropdownSetting rotations = new DropdownSetting("Rotations", 2, "NONE", "LEGIT", "SILENT", "LOCK_VIEW");
    public final DropdownSetting moveFix   = new DropdownSetting("Move Fix",  1, "NONE", "SILENT", "STRICT");
    // Smoothing 0 = instant snap, 100 = very slow glide (~5% of gap per tick)
    public final SliderSetting smoothing   = new SliderSetting("Smoothing",   60,   0, 100,   1);
    public final SliderSetting angleStep   = new SliderSetting("Angle Step", 180,  30, 180,   1);

    public final BooleanSetting throughWalls   = new BooleanSetting("Through Walls",  true);
    public final BooleanSetting requirePress   = new BooleanSetting("Require Press",  false);
    public final BooleanSetting allowMining    = new BooleanSetting("Allow Mining",   true);
    public final BooleanSetting weaponsOnly    = new BooleanSetting("Weapons Only",   false);
    public final BooleanSetting allowTools     = new BooleanSetting("Allow Tools",    true);
    public final BooleanSetting inventoryCheck = new BooleanSetting("Inv Check",      true);
    public final BooleanSetting botCheck       = new BooleanSetting("Bot Check",      true);

    public final BooleanSetting players    = new BooleanSetting("Players",    true);
    public final BooleanSetting bosses     = new BooleanSetting("Bosses",     false);
    public final BooleanSetting mobs       = new BooleanSetting("Mobs",       false);
    public final BooleanSetting animals    = new BooleanSetting("Animals",    false);
    public final BooleanSetting golems     = new BooleanSetting("Golems",     false);
    public final BooleanSetting silverfish = new BooleanSetting("Silverfish", false);
    public final BooleanSetting teams      = new BooleanSetting("Teams",      true);

    public final DropdownSetting showTarget = new DropdownSetting("Show Target", 0, "NONE", "DEFAULT", "HUD");
    public final DropdownSetting debugLog   = new DropdownSetting("Debug Log",   0, "NONE", "HEALTH");

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
        deferredAttack      = false;
        deferredTarget      = null;
        attackCooldownTicks = 0;
        target              = null;
        smoothYaw           = Float.NaN;
        smoothPitch         = Float.NaN;
    }

    // ── Smoothed rotation ──────────────────────────────────────────────────────

    /**
     * Each call advances smoothYaw/smoothPitch toward the ideal angle by a
     * fraction of the remaining gap. Because the state is stored on the class,
     * consecutive ticks accumulate progress — the rotation genuinely glides.
     *
     * lerpT: smoothing=0  → lerpT=1.00 (instant snap)
     *        smoothing=50 → lerpT=0.30 (reaches target in ~4-5 ticks)
     *        smoothing=100→ lerpT=0.05 (very slow, ~20+ ticks to arrive)
     */
    private float[] advanceSmoothedRotation(AxisAlignedBB box) {
        // Seed on first call for this target
        if (Float.isNaN(smoothYaw))   smoothYaw   = mc.thePlayer.rotationYaw;
        if (Float.isNaN(smoothPitch)) smoothPitch = mc.thePlayer.rotationPitch;

        // Compute ideal rotation to target from the current smoothed position
        float[] ideal = RotationUtil.getRotationsToBox(
                box,
                smoothYaw,
                smoothPitch,
                (float) angleStep.getValue() + RandomUtil.nextFloat(-3f, 3f),
                0f); // raw target — we handle lerp ourselves

        float sm = (float) smoothing.getValue() / 100f;  // 0..1
        // lerpT = 1 - sm*0.95 → sm=0 gives 1.0, sm=1 gives 0.05
        float lerpT = (1f - sm * 0.95f) + RandomUtil.nextFloat(-0.01f, 0.01f);
        lerpT = MathHelper.clamp_float(lerpT, 0.04f, 1.0f);

        // Lerp on the shortest angular path
        float yawDiff   = MathHelper.wrapAngleTo180_float(ideal[0] - smoothYaw);
        float pitchDiff = ideal[1] - smoothPitch;

        smoothYaw   = RotationUtil.quantizeAngle(smoothYaw   + yawDiff   * lerpT);
        smoothPitch = RotationUtil.quantizeAngle(
                MathHelper.clamp_float(smoothPitch + pitchDiff * lerpT, -90f, 90f));

        return new float[]{ smoothYaw, smoothPitch };
    }

    // ── Attack ─────────────────────────────────────────────────────────────────

    private long getAttackDelay() {
        int min = Math.min((int) minCPS.getValue(), (int) maxCPS.getValue());
        int max = Math.max((int) minCPS.getValue(), (int) maxCPS.getValue());
        return 1000L / RandomUtil.nextLong(min, max);
    }

    public EntityLivingBase getTarget() { return target != null ? target.getEntity() : null; }

    /** Used by Autoblock to know when KillAura is ready to attack. */
    public boolean isAttackAllowed() {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        try {
            if (Myau.moduleManager.modules.get(Scaffold.class).isEnabled()) return false;
        } catch (Exception ignored) {}
        if (!weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
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
        if (mc.thePlayer.getDistanceToEntity(entity) > swingRange.getValue()) return false;
        if (fov.getValue() < 360 && RotationUtil.angleToEntity(entity) > (float) fov.getValue() / 2f) return false;
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

    /**
     * Core attack. KillAura never calls rightClickMouse or manages blocking itself —
     * that is entirely Autoblock's job. Here we only coordinate the timing so
     * we don't attack while the block is up (Grim PacketOrderI).
     */
    private boolean performAttack(float yaw, float pitch) {
        if (target == null) return false;
        if (Myau.playerStateManager.digging || Myau.playerStateManager.placing) return false;
        if (!attackTimer.hasTimeElapsed(getAttackDelay())) return false;

        // Ask Autoblock to release before we hit
        try {
            Autoblock ab = (Autoblock) Myau.moduleManager.modules.get(Autoblock.class);
            if (ab != null && ab.isEnabled() && ab.isPlayerBlocking()) {
                if (ab.isInLegitFullHoldPhase()) return false; // wait for LEGITFULL hold to finish
                ab.stopBlock();
                deferredAttack = true;
                deferredTarget = target;
                return false; // attack next tick once block is released
            }
        } catch (Exception ignored) {}

        mc.thePlayer.swingItem();

        EntityLivingBase entity = target.getEntity();
        if (mc.thePlayer.getDistanceToEntity(entity) <= attackRange.getValue()) {
            AttackEvent event = new AttackEvent(entity);
            EventManager.call(event);
            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
            PacketUtil.sendPacket(new C02PacketUseEntity(entity, Action.ATTACK));
            if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR)
                PlayerUtil.attackEntity(entity);
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
            Autoblock ab = (Autoblock) Myau.moduleManager.modules.get(Autoblock.class);
            if (ab != null && ab.isEnabled() && ab.isPlayerBlocking()) return false; // still blocking
        } catch (Exception ignored) {}
        target = deferredTarget;
        boolean result = performAttack(yaw, pitch);
        deferredAttack = false; deferredTarget = null;
        return result;
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (attackCooldownTicks > 0) attackCooldownTicks--;

        boolean useRots = rotations.getIndex() >= 2; // SILENT or LOCK_VIEW

        // --- Deferred attack (post block-release) ---
        if (deferredAttack && deferredTarget != null) {
            if (useRots) {
                float[] r = advanceSmoothedRotation(deferredTarget.getBox());
                event.setRotation(r[0], r[1], 1);
                if (rotations.getIndex() == 3) Myau.rotationManager.setRotation(r[0], r[1], 1, true);
                if (moveFix.getIndex() != 0 || rotations.getIndex() == 3) event.setPervRotation(r[0], 1);
                performDeferredAttack(r[0], r[1]);
            } else {
                performDeferredAttack(event.getYaw(), event.getPitch());
            }
            return;
        }

        // --- Find / refresh target ---
        if (target == null || !isValidTarget(target.getEntity())
                || timer.hasTimeElapsed((long) switchDelay.getValue())) {
            AttackData prev = target;
            target = findTarget();
            timer.reset();
            // Reset smoothed state when target changes so we don't glide from old position
            if (target != prev) {
                smoothYaw   = Float.NaN;
                smoothPitch = Float.NaN;
            }
        }

        if (target == null) return;

        // --- Rotations + attack ---
        // advanceSmoothedRotation is called every tick regardless of whether we
        // attack so the aim is already close when the attack fires.
        if (useRots) {
            float[] r = advanceSmoothedRotation(target.getBox());
            event.setRotation(r[0], r[1], 1);
            if (rotations.getIndex() == 3) Myau.rotationManager.setRotation(r[0], r[1], 1, true);
            if (moveFix.getIndex() != 0 || rotations.getIndex() == 3) event.setPervRotation(r[0], 1);
            if (canAttack()) performAttack(r[0], r[1]);
        } else {
            if (canAttack()) performAttack(event.getYaw(), event.getPitch());
        }
    }

    private AttackData findTarget() {
        if (mc.theWorld == null) return null;
        ArrayList<AttackData> list = new ArrayList<>();
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof EntityLivingBase)) continue;
            if (!isValidTarget((EntityLivingBase) e)) continue;
            list.add(new AttackData((EntityLivingBase) e));
        }
        if (list.isEmpty()) return null;
        switch (sort.getIndex()) {
            case 0: list.sort((a, b) -> Double.compare(mc.thePlayer.getDistanceToEntity(a.getEntity()), mc.thePlayer.getDistanceToEntity(b.getEntity()))); break;
            case 1: list.sort((a, b) -> Float.compare(a.getEntity().getHealth(), b.getEntity().getHealth())); break;
            case 2: list.sort((a, b) -> Integer.compare(b.getEntity().hurtTime, a.getEntity().hurtTime)); break;
            case 3: list.sort((a, b) -> Float.compare(RotationUtil.angleToEntity(a.getEntity()), RotationUtil.angleToEntity(b.getEntity()))); break;
        }
        return list.get(0);
    }
}
