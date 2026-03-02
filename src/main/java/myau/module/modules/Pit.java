package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

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

    public Pit() {
        super("Pit", false);
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
}
