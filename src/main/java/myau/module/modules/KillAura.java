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

    private final TimerUtil timer = new TimerUtil();
    private final TimerUtil attackTimer = new TimerUtil();
    private AttackData target = null;
    private boolean hitRegistered = false;

    // Grim-compatible delay when using Autoblock: defer attack to next tick after releasing block
    private boolean deferredAttack = false;
    private AttackData deferredTarget = null;
    public static int attackCooldownTicks = 0;

    // Mode
    public final DropdownSetting mode          = new DropdownSetting("Mode",        0, "SINGLE", "SWITCH");
    public final DropdownSetting sort          = new DropdownSetting("Sort",        0, "DISTANCE", "HEALTH", "HURT_TIME", "FOV");

    // Range
    public final SliderSetting swingRange      = new SliderSetting("Swing Range",  3.5, 3.0, 6.0, 0.1);
    public final SliderSetting attackRange     = new SliderSetting("Attack Range", 3.0, 3.0, 6.0, 0.1);
    public final SliderSetting fov             = new SliderSetting("FOV",          360,  30, 360,   1);

    // CPS
    public final SliderSetting minCPS          = new SliderSetting("Min CPS",       8,   1,  20,   1);
    public final SliderSetting maxCPS          = new SliderSetting("Max CPS",      12,   1,  20,   1);
    public final SliderSetting switchDelay     = new SliderSetting("Switch Delay", 150,   0, 1000, 10);

    // Rotations
    public final DropdownSetting rotations     = new DropdownSetting("Rotations",  2, "NONE", "LEGIT", "SILENT", "LOCK_VIEW");
    public final DropdownSetting moveFix       = new DropdownSetting("Move Fix",   1, "NONE", "SILENT", "STRICT");
    public final SliderSetting smoothing       = new SliderSetting("Smoothing",   85,   0, 100,   1);  // 85 = very smooth default for Lock View
    public final SliderSetting angleStep       = new SliderSetting("Angle Step", 180,  30, 180,   1);  // 180 = no per-tick cap, smoother

    // Behaviour
    public final BooleanSetting throughWalls   = new BooleanSetting("Through Walls",  true);
    public final BooleanSetting requirePress   = new BooleanSetting("Require Press",  false);
    public final BooleanSetting allowMining    = new BooleanSetting("Allow Mining",   true);
    public final BooleanSetting weaponsOnly    = new BooleanSetting("Weapons Only",   false);
    public final BooleanSetting allowTools     = new BooleanSetting("Allow Tools",    true);
    public final BooleanSetting inventoryCheck = new BooleanSetting("Inv Check",      true);
    public final BooleanSetting botCheck       = new BooleanSetting("Bot Check",      true);

    // Targets
    public final BooleanSetting players        = new BooleanSetting("Players",    true);
    public final BooleanSetting bosses         = new BooleanSetting("Bosses",     false);
    public final BooleanSetting mobs           = new BooleanSetting("Mobs",       false);
    public final BooleanSetting animals        = new BooleanSetting("Animals",    false);
    public final BooleanSetting golems         = new BooleanSetting("Golems",     false);
    public final BooleanSetting silverfish     = new BooleanSetting("Silverfish", false);
    public final BooleanSetting teams          = new BooleanSetting("Teams",      true);

    // Display
    public final DropdownSetting showTarget    = new DropdownSetting("Show Target", 0, "NONE", "DEFAULT", "HUD");
    public final DropdownSetting debugLog      = new DropdownSetting("Debug Log",   0, "NONE", "HEALTH");

    @Override
    public void onDisabled() {
        deferredAttack = false;
        deferredTarget = null;
        attackCooldownTicks = 0;
    }

    public KillAura() {
        super("KillAura", false);
        register(mode);
        register(sort);
        register(swingRange);
        register(attackRange);
        register(fov);
        register(minCPS);
        register(maxCPS);
        register(switchDelay);
        register(rotations);
        register(moveFix);
        register(smoothing);
        register(angleStep);
        register(throughWalls);
        register(requirePress);
        register(allowMining);
        register(weaponsOnly);
        register(allowTools);
        register(inventoryCheck);
        register(botCheck);
        register(players);
        register(bosses);
        register(mobs);
        register(animals);
        register(golems);
        register(silverfish);
        register(teams);
        register(showTarget);
        register(debugLog);
    }

    private long getAttackDelay() {
        int min = Math.min((int) minCPS.getValue(), (int) maxCPS.getValue());
        int max = Math.max((int) minCPS.getValue(), (int) maxCPS.getValue());
        return 1000L / RandomUtil.nextLong(min, max);
    }

    public EntityLivingBase getTarget() {
        return target != null ? target.getEntity() : null;
    }

    // Public method for other modules to check if KillAura is ready to attack
    public boolean isAttackAllowed() {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        
        try {
            Scaffold scaffold = (Scaffold) Myau.moduleManager.modules.get(Scaffold.class);
            if (scaffold != null && scaffold.isEnabled()) return false;
        } catch (Exception e) {
            // Ignore scaffold check errors
        }
        
        if (!weaponsOnly.getValue() 
                || ItemUtil.hasRawUnbreakingEnchant() 
                || (allowTools.getValue() && ItemUtil.isHoldingTool())) {
            return !requirePress.getValue() 
                    || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
        }
        return false;
    }

    // Private method for internal attack checks
    private boolean canAttack() {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        if (inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) return false;
        
        // Check if we need to press attack key
        if (requirePress.getValue() && !mc.gameSettings.keyBindAttack.isKeyDown()) return false;
        
        // Check weapon/tool requirements
        if (weaponsOnly.getValue() && !ItemUtil.hasRawUnbreakingEnchant() && 
            !(allowTools.getValue() && ItemUtil.isHoldingTool())) {
            return false;
        }

        // Check if player is doing other actions
        if (((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock() && !allowMining.getValue()) return false;
        if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) return false;

        // Check other modules
        try {
            AutoHeal autoHeal = (AutoHeal) Myau.moduleManager.modules.get(AutoHeal.class);
            if (autoHeal != null && autoHeal.isEnabled() && autoHeal.isSwitching()) return false;

            BedNuker bedNuker = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
            if (bedNuker != null && bedNuker.isEnabled() && bedNuker.isReady()) return false;

            if (Myau.moduleManager.modules.get(Scaffold.class).isEnabled()) return false;
        } catch (Exception e) {
            // Ignore module check errors
        }

        return true;
    }

    private boolean isValidTarget(EntityLivingBase entity) {
        if (entity == null) return false;
        if (!mc.theWorld.loadedEntityList.contains(entity)) return false;
        if (entity == mc.thePlayer || entity == mc.thePlayer.ridingEntity) return false;
        if (entity == mc.getRenderViewEntity() || entity == mc.getRenderViewEntity().ridingEntity) return false;
        if (entity.deathTime > 0 || entity.getHealth() <= 0) return false;
        
        // Distance check
        double distance = mc.thePlayer.getDistanceToEntity(entity);
        if (distance > swingRange.getValue()) return false;
        
        // FOV check
        if (fov.getValue() < 360 && RotationUtil.angleToEntity(entity) > (float) fov.getValue() / 2.0f) return false;
        
        // Wall check
        if (!throughWalls.getValue() && !mc.thePlayer.canEntityBeSeen(entity)) return false;

        // Entity type checks
        if (entity instanceof EntityOtherPlayerMP) {
            if (!players.getValue()) return false;
            if (TeamUtil.isFriend((EntityPlayer) entity)) return false;
            if (teams.getValue() && TeamUtil.isSameTeam((EntityPlayer) entity)) return false;
            if (botCheck.getValue() && TeamUtil.isBot((EntityPlayer) entity)) return false;
            return true;
        }

        if (entity instanceof EntityDragon || entity instanceof EntityWither)
            return bosses.getValue();

        if (entity instanceof EntityMob || entity instanceof EntitySlime) {
            if (entity instanceof EntitySilverfish)
                return silverfish.getValue() && (!teams.getValue() || !TeamUtil.hasTeamColor(entity));
            return mobs.getValue();
        }

        if (entity instanceof EntityAnimal
                || entity instanceof EntityBat
                || entity instanceof EntitySquid
                || entity instanceof EntityVillager)
            return animals.getValue();

        if (entity instanceof EntityIronGolem)
            return golems.getValue() && (!teams.getValue() || !TeamUtil.hasTeamColor(entity));

        return false;
    }

    private boolean isBoxInSwingRange(AxisAlignedBB box) {
        return RotationUtil.distanceToBox(box) <= swingRange.getValue();
    }

    private boolean isBoxInAttackRange(AxisAlignedBB box) {
        return RotationUtil.distanceToBox(box) <= attackRange.getValue();
    }

    private boolean performAttack(float yaw, float pitch) {
        if (target == null) return false;
        if (Myau.playerStateManager.digging || Myau.playerStateManager.placing) return false;
        
        // Check attack delay
        if (!attackTimer.hasTimeElapsed(getAttackDelay())) return false;

        // Stop autoblock before attacking - Grim requires 1 tick between release and attack
        try {
            Autoblock autoblock = (Autoblock) Myau.moduleManager.modules.get(Autoblock.class);
            if (autoblock != null && autoblock.isEnabled() && autoblock.isPlayerBlocking()) {
                // LEGITFULL hold phase - don't interrupt, skip attack this tick
                if (autoblock.isInLegitFullHoldPhase()) return false;
                autoblock.stopBlock();
                // Defer attack to next tick to avoid PacketOrderI / RotationBreak
                deferredAttack = true;
                deferredTarget = target;
                return false;
            }
        } catch (Exception e) {
            // Ignore autoblock errors
        }

        // Swing arm
        mc.thePlayer.swingItem();

        // Check if we can actually hit the target
        EntityLivingBase targetEntity = target.getEntity();
        double distance = mc.thePlayer.getDistanceToEntity(targetEntity);
        
        if (distance <= attackRange.getValue()) {
            // Send attack packet
            AttackEvent event = new AttackEvent(targetEntity);
            EventManager.call(event);

            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
            PacketUtil.sendPacket(new C02PacketUseEntity(targetEntity, Action.ATTACK));

            if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
                PlayerUtil.attackEntity(targetEntity);
            }

            hitRegistered = true;
            attackTimer.reset();
            attackCooldownTicks = 5;  // Prevents Autoblock from re-blocking too soon (Grim place/use PacketOrderI)
            return true;
        }

        attackTimer.reset();
        return false;
    }

    private boolean performDeferredAttack(float yaw, float pitch) {
        if (deferredTarget == null || !isValidTarget(deferredTarget.getEntity())) {
            deferredAttack = false;
            deferredTarget = null;
            return false;
        }
        // Must not be blocking when we execute deferred attack
        try {
            Autoblock autoblock = (Autoblock) Myau.moduleManager.modules.get(Autoblock.class);
            if (autoblock != null && autoblock.isEnabled() && autoblock.isPlayerBlocking()) {
                return false;  // Still blocking, wait another tick
            }
        } catch (Exception e) { /* ignore */ }
        target = deferredTarget;
        boolean result = performAttack(yaw, pitch);
        deferredAttack = false;
        deferredTarget = null;
        return result;
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // Decay attack cooldown for Autoblock coordination
        if (attackCooldownTicks > 0) attackCooldownTicks--;

        // Execute deferred attack from previous tick (after block release)
        if (deferredAttack && deferredTarget != null) {
            if (rotations.getIndex() == 2 || rotations.getIndex() == 3) {
                float[] rots = RotationUtil.getRotationsToBox(
                        deferredTarget.getBox(),
                        event.getYaw(),
                        event.getPitch(),
                        (float) angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F),
                        (float) smoothing.getValue() / 100.0F);
                event.setRotation(rots[0], rots[1], 1);
                if (rotations.getIndex() == 3)
                    Myau.rotationManager.setRotation(rots[0], rots[1], 1, true);
                if (moveFix.getIndex() != 0 || rotations.getIndex() == 3)
                    event.setPervRotation(rots[0], 1);
                performDeferredAttack(rots[0], rots[1]);
            } else {
                performDeferredAttack(event.getYaw(), event.getPitch());
            }
            return;  // Don't process normal target this tick
        }

        // Find target if we don't have one
        if (target == null || !isValidTarget(target.getEntity()) || 
            timer.hasTimeElapsed((long) switchDelay.getValue())) {
            target = findTarget();
            timer.reset();
        }

        // Attack if we have a target and can attack
        if (target != null && canAttack()) {
            EntityLivingBase targetEntity = target.getEntity();
            
            // Handle rotations
            if (rotations.getIndex() == 2 || rotations.getIndex() == 3) { // SILENT or LOCK_VIEW
                float[] rots = RotationUtil.getRotationsToBox(
                        target.getBox(),
                        event.getYaw(),
                        event.getPitch(),
                        (float) angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F),
                        (float) smoothing.getValue() / 100.0F);

                event.setRotation(rots[0], rots[1], 1);

                if (rotations.getIndex() == 3) // LOCK_VIEW
                    Myau.rotationManager.setRotation(rots[0], rots[1], 1, true);

                if (moveFix.getIndex() != 0 || rotations.getIndex() == 3)
                    event.setPervRotation(rots[0], 1);
                    
                performAttack(rots[0], rots[1]);
            } else {
                // No rotations or legit rotations
                performAttack(event.getYaw(), event.getPitch());
            }
        }
    }

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

        // Sort targets
        switch (sort.getIndex()) {
            case 0: // DISTANCE
                targets.sort((a, b) -> Double.compare(
                    mc.thePlayer.getDistanceToEntity(a.getEntity()), 
                    mc.thePlayer.getDistanceToEntity(b.getEntity()))); 
                break;
            case 1: // HEALTH
                targets.sort((a, b) -> Float.compare(a.getEntity().getHealth(), b.getEntity().getHealth())); 
                break;
            case 2: // HURT_TIME
                targets.sort((a, b) -> Integer.compare(b.getEntity().hurtTime, a.getEntity().hurtTime)); 
                break;
            case 3: // FOV
                targets.sort((a, b) -> Float.compare(
                    RotationUtil.angleToEntity(a.getEntity()), 
                    RotationUtil.angleToEntity(b.getEntity()))); 
                break;
        }
        
        return targets.get(0);
    }
}
