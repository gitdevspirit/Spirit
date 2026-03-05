package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.management.RotationState;
import myau.events.PlayerUpdateEvent;
import myau.util.MoveUtil;
import myau.event.types.EventType;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.module.*;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import myau.mixin.IAccessorRenderManager;
import net.minecraft.util.AxisAlignedBB;
import myau.mixin.IAccessorRenderManager;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import org.lwjgl.opengl.GL11;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class SilentAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random rand = new Random();

    // ── Target Settings ───────────────────────────────────────────────────────
    public final SliderSetting  range         = register(new SliderSetting("Range",          4.0, 1.0, 8.0, 0.1));
    public final SliderSetting  maxAngle      = register(new SliderSetting("Max Angle",      180,  1, 180,   1));
    public final DropdownSetting targetMode   = register(new DropdownSetting("Target Mode",  0, "Distance", "Yaw", "Armor", "Threat", "Health"));
    public final DropdownSetting targetArea   = register(new DropdownSetting("Target Area",  0, "Center", "Closest"));
    public final BooleanSetting  teamCheck    = register(new BooleanSetting("Team Check",    true));
    public final BooleanSetting  friendCheck  = register(new BooleanSetting("Friend Check",  true));
    public final BooleanSetting  botCheck     = register(new BooleanSetting("Bot Check",     true));

    // ── Aim Settings ──────────────────────────────────────────────────────────
    public final SliderSetting  aimSpeed      = register(new SliderSetting("Aim Speed",      50,   1, 100,   1));

    // ── Movement Settings ────────────────────────────────────────────────────
    public final BooleanSetting  aimIndicator = register(new BooleanSetting("Aim Indicator", true));
    public final BooleanSetting  thirdPerson  = register(new BooleanSetting("3rd Person Aim", true));

    // ── Attack Settings ───────────────────────────────────────────────────────
    public final SliderSetting  minCPS        = register(new SliderSetting("Min CPS",         8,   1,  20,   1));
    public final SliderSetting  maxCPS        = register(new SliderSetting("Max CPS",        12,   1,  20,   1));
    public final SliderSetting  extraSwing    = register(new SliderSetting("Extra Swing Dist",0.5, 0.0, 2.0, 0.1));

    // ── Behaviour ─────────────────────────────────────────────────────────────
    public final BooleanSetting  requireMouse = register(new BooleanSetting("Require Mouse Down", false));
    public final BooleanSetting  breakBlocks  = register(new BooleanSetting("Break Blocks Pause", true));
    public final SliderSetting   breakDelay   = register(new SliderSetting("  Break Delay (ms)", 200, 0, 1000, 50, () -> breakBlocks.getValue()));
    public final BooleanSetting  disableOnDeath = register(new BooleanSetting("Disable on Death", true));

    // ── Limit to Items ────────────────────────────────────────────────────────
    public final BooleanSetting  limitItems   = register(new BooleanSetting("Limit to Items", false));
    // Stored as a simple string — parsed at runtime
    // Format: "swords, axes, slot 1, Diamond Sword" etc.
    public String itemWhitelist = "swords";

    // ── Show Target ───────────────────────────────────────────────────────────
    public final BooleanSetting  showTarget   = register(new BooleanSetting("Show Target", true));
    public final SliderSetting   targetR      = register(new SliderSetting("  Target R",  255,  0, 255, 1, () -> showTarget.getValue()));
    public final SliderSetting   targetG      = register(new SliderSetting("  Target G",    0,  0, 255, 1, () -> showTarget.getValue()));
    public final SliderSetting   targetB      = register(new SliderSetting("  Target B",  255,  0, 255, 1, () -> showTarget.getValue()));
    public final SliderSetting   attackR      = register(new SliderSetting("  Attack R",  255,  0, 255, 1, () -> showTarget.getValue()));
    public final SliderSetting   attackG      = register(new SliderSetting("  Attack G",   85,  0, 255, 1, () -> showTarget.getValue()));
    public final SliderSetting   attackB      = register(new SliderSetting("  Attack B",   85,  0, 255, 1, () -> showTarget.getValue()));

    // ── State ─────────────────────────────────────────────────────────────────
    private EntityPlayer currentTarget  = null;
    private EntityPlayer attackingTarget = null; // set for one tick when attacking
    private long lastAttackMs = 0;
    private long nextAttackMs = 0;
    private long breakPauseUntil = 0;
    private float silentYaw    = 0;
    private float silentPitch  = 0;
    private boolean pendingAttack = false;
    public static boolean attackingThisTick = false;

    public SilentAura() {
        super("SilentAura", false);
    }

    @Override
    public void onDisabled() {
        attackingThisTick = false;
        currentTarget   = null;
        attackingTarget = null;
    }

    // ── Main tick ─────────────────────────────────────────────────────────────
    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        attackingTarget = null;

        // Disable on death
        if (disableOnDeath.getValue() && mc.thePlayer.getHealth() <= 0) {
            setEnabled(false);
            return;
        }

        // Require mouse down
        if (requireMouse.getValue() && !org.lwjgl.input.Mouse.isButtonDown(0)) {
            currentTarget = null;
            return;
        }

        // Break blocks pause
        if (breakBlocks.getValue()) {
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK
                    && mc.thePlayer.isUsingItem()) {
                breakPauseUntil = System.currentTimeMillis() + (long) breakDelay.getValue();
            }
            if (System.currentTimeMillis() < breakPauseUntil) {
                currentTarget = null;
                return;
            }
        }

        // Item whitelist
        if (limitItems.getValue() && !isAllowedItem()) {
            currentTarget = null;
            return;
        }

        // Find target
        currentTarget = findTarget();
        if (currentTarget == null) {
            silentYaw   = mc.thePlayer.rotationYaw;
            silentPitch = mc.thePlayer.rotationPitch;
            return;
        }

        // Calculate silent rotation toward target
        float[] rot = calcRotation(currentTarget);
        silentYaw   = rot[0];
        silentPitch = rot[1];

        // Check angle constraint
        float angleDiff = getAngleDiff(silentYaw, silentPitch);
        if (angleDiff > maxAngle.getValue()) {
            currentTarget = null;
            return;
        }

        // Body rotation for rendering — always on, no flicker
        RotationState.applyState(true, silentYaw, silentPitch, silentYaw, 10);
        // Actual packet rotation is handled by onUpdateEvent which piggybacks
        // on the game's existing movement packet — no extra packets sent

        // Flag attack intent — actual packet sent in POST update after look packet
        double dist = RotationUtil.distanceToEntity(currentTarget);
        double attackRange = range.getValue() + extraSwing.getValue();
        pendingAttack = dist <= attackRange && System.currentTimeMillis() >= nextAttackMs;
    }


    // ── Silent rotation injected into movement packet ────────────────────────
    @EventTarget
    public void onUpdateEvent(UpdateEvent event) {
        if (!isEnabled() || currentTarget == null) return;
        if (event.getType() != myau.event.types.EventType.PRE) return;
        event.setRotation(silentYaw, silentPitch, 10);
        event.setPervRotation(silentYaw, 10);
    }

    // ── Attack via vanilla playerController (before position packet) ──────────
    // playerController.attackEntity() sends the full vanilla C02 sequence Grim expects
    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!isEnabled() || !pendingAttack) return;
        if (currentTarget == null || currentTarget.isDead) { pendingAttack = false; return; }
        attackingThisTick = true;
        mc.playerController.attackEntity(mc.thePlayer, currentTarget);
        mc.thePlayer.swingItem();
        attackingTarget = currentTarget;
        scheduleNextAttack();
        pendingAttack = false;
    }


    // ── Target finding ────────────────────────────────────────────────────────
    private EntityPlayer findTarget() {
        java.util.List<EntityPlayer> targets = mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityPlayer)
                .map(e -> (EntityPlayer) e)
                .filter(this::isValidTarget)
                .collect(java.util.stream.Collectors.toList());
        if (targets.isEmpty()) return null;

        switch (targetMode.getIndex()) {
            case 1: // Yaw
                targets.sort(java.util.Comparator.comparingDouble(p -> getAngleDiff(calcRotation(p)[0], calcRotation(p)[1])));
                break;
            case 2: // Armor
                targets.sort(java.util.Comparator.comparingInt(p -> {
                    int armor = 0;
                    for (ItemStack s : p.inventory.armorInventory) if (s != null) armor++;
                    return armor;
                }));
                break;
            case 3: // Threat
                targets.sort(java.util.Comparator.comparingDouble(this::getThreat).reversed());
                break;
            case 4: // Health
                targets.sort(java.util.Comparator.comparingDouble(EntityPlayer::getHealth));
                break;
            default: // Distance
                targets.sort(java.util.Comparator.comparingDouble(RotationUtil::distanceToEntity));
                break;
        }
        return targets.get(0);
    }

    private boolean isValidTarget(EntityPlayer p) {
        if (p == mc.thePlayer || p == mc.thePlayer.ridingEntity) return false;
        if (p == mc.getRenderViewEntity() || p == mc.getRenderViewEntity().ridingEntity) return false;
        if (p.deathTime > 0 || p.isDead) return false;
        if (RotationUtil.distanceToEntity(p) > range.getValue() + extraSwing.getValue()) return false;
        if (friendCheck.getValue() && TeamUtil.isFriend(p)) return false;
        if (teamCheck.getValue() && TeamUtil.isSameTeam(p)) return false;
        if (botCheck.getValue() && TeamUtil.isBot(p)) return false;
        return true;
    }

    private float[] calcRotation(EntityPlayer target) {
        AxisAlignedBB bb = target.getEntityBoundingBox();
        float smooth = 1.0f - (float) aimSpeed.getValue() / 100.0f;
        if (targetArea.getIndex() == 1) {
            return RotationUtil.getRotationsToBox(bb, silentYaw, silentPitch, 180.0f, smooth);
        } else {
            double cx = (bb.minX + bb.maxX) / 2.0;
            double cy = (bb.minY + bb.maxY) / 2.0;
            double cz = (bb.minZ + bb.maxZ) / 2.0;
            net.minecraft.util.Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
            return RotationUtil.getRotations(cx - eyes.xCoord, cy - eyes.yCoord, cz - eyes.zCoord,
                    silentYaw, silentPitch, 180.0f, smooth);
        }
    }

    private float getAngleDiff(float yaw, float pitch) {
        float dy = Math.abs(MathHelper.wrapAngleTo180_float(yaw   - mc.thePlayer.rotationYaw));
        float dp = Math.abs(MathHelper.wrapAngleTo180_float(pitch - mc.thePlayer.rotationPitch));
        return Math.max(dy, dp);
    }

    private void scheduleNextAttack() {
        double min = Math.min(minCPS.getValue(), maxCPS.getValue());
        double max = Math.max(minCPS.getValue(), maxCPS.getValue());
        double cps = min + rand.nextDouble() * (max - min);
        long delay = (long)(1000.0 / cps);
        nextAttackMs = System.currentTimeMillis() + delay;
    }

    // ── Item whitelist ────────────────────────────────────────────────────────
    private boolean isAllowedItem() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) {
            return itemWhitelist.toLowerCase().contains("hand");
        }
        Item item = held.getItem();
        String wl  = itemWhitelist.toLowerCase();
        String[] parts = wl.split(",");
        for (String part : parts) {
            String p = part.trim();
            if (p.equals("swords")    && item instanceof ItemSword)   return true;
            if (p.equals("axes")      && item instanceof ItemAxe)     return true;
            if (p.equals("pickaxes")  && item instanceof ItemPickaxe) return true;
            if (p.equals("shovels")   && item instanceof ItemSpade)   return true;
            if (p.equals("blocks")    && item instanceof ItemBlock)   return true;
            if (p.equals("food")      && item instanceof ItemFood)    return true;
            if (p.equals("hand")      && held == null)                return true;
            // Slot: "slot 1" = hotbar slot 0
            if (p.startsWith("slot ")) {
                try {
                    int slot = Integer.parseInt(p.substring(5).trim()) - 1;
                    if (mc.thePlayer.inventory.currentItem == slot) return true;
                } catch (NumberFormatException ignored) {}
            }
            // Item display name match
            if (held.getDisplayName().toLowerCase().contains(p)) return true;
            // Legacy item ID
            try {
                int id = Integer.parseInt(p.contains(":") ? p.split(":")[0] : p);
                int meta = p.contains(":") ? Integer.parseInt(p.split(":")[1]) : -1;
                if (Item.getIdFromItem(item) == id && (meta == -1 || held.getMetadata() == meta)) return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    // ── Threat calculation ────────────────────────────────────────────────────
    private double getThreat(EntityPlayer p) {
        ItemStack weapon = p.getHeldItem();
        if (weapon == null) return 0;
        double dmg = p.getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue();
        dmg += EnchantmentHelper.getModifierForCreature(weapon, mc.thePlayer.getCreatureAttribute());
        return dmg;
    }

    // ── ESP box drawing ───────────────────────────────────────────────────────
    private void drawEntityBox(EntityPlayer p, int color, float partialTicks) {
        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();
        double rx = p.lastTickPosX + (p.posX - p.lastTickPosX) * partialTicks - rm.getRenderPosX();
        double ry = p.lastTickPosY + (p.posY - p.lastTickPosY) * partialTicks - rm.getRenderPosY();
        double rz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partialTicks - rm.getRenderPosZ();

        float w = p.width / 2.0f + 0.05f;
        float h = p.height + 0.1f;

        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableDepth();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.blendFunc(770, 771);

        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >>  8) & 0xFF) / 255.0f;
        float b = ( color        & 0xFF) / 255.0f;

        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer wr  = tess.getWorldRenderer();

        // Filled faces
        net.minecraft.client.renderer.GlStateManager.color(r, g, b, a * 0.25f);
        wr.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        // Bottom
        wr.pos(rx-w, ry,   rz-w).endVertex(); wr.pos(rx+w, ry,   rz-w).endVertex();
        wr.pos(rx+w, ry,   rz+w).endVertex(); wr.pos(rx-w, ry,   rz+w).endVertex();
        // Top
        wr.pos(rx-w, ry+h, rz-w).endVertex(); wr.pos(rx+w, ry+h, rz-w).endVertex();
        wr.pos(rx+w, ry+h, rz+w).endVertex(); wr.pos(rx-w, ry+h, rz+w).endVertex();
        tess.draw();

        // Outline
        net.minecraft.client.renderer.GlStateManager.color(r, g, b, a);
        GL11.glLineWidth(1.5f);
        wr.begin(3, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        wr.pos(rx-w, ry,   rz-w).endVertex(); wr.pos(rx+w, ry,   rz-w).endVertex();
        wr.pos(rx+w, ry,   rz+w).endVertex(); wr.pos(rx-w, ry,   rz+w).endVertex();
        wr.pos(rx-w, ry,   rz-w).endVertex();
        tess.draw();
        wr.begin(3, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        wr.pos(rx-w, ry+h, rz-w).endVertex(); wr.pos(rx+w, ry+h, rz-w).endVertex();
        wr.pos(rx+w, ry+h, rz+w).endVertex(); wr.pos(rx-w, ry+h, rz+w).endVertex();
        wr.pos(rx-w, ry+h, rz-w).endVertex();
        tess.draw();
        wr.begin(1, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        wr.pos(rx-w, ry, rz-w).endVertex(); wr.pos(rx-w, ry+h, rz-w).endVertex();
        wr.pos(rx+w, ry, rz-w).endVertex(); wr.pos(rx+w, ry+h, rz-w).endVertex();
        wr.pos(rx+w, ry, rz+w).endVertex(); wr.pos(rx+w, ry+h, rz+w).endVertex();
        wr.pos(rx-w, ry, rz+w).endVertex(); wr.pos(rx-w, ry+h, rz+w).endVertex();
        tess.draw();

        net.minecraft.client.renderer.GlStateManager.enableDepth();
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }

    private int buildColor(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
