package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.management.RotationState;
import myau.event.types.EventType;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.module.*;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook;
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook;
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
    private float silentYaw   = 0;
    private float silentPitch = 0;

    public SilentAura() {
        super("SilentAura", false);
    }

    @Override
    public void onDisabled() {
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
        if (currentTarget == null) return;

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

        // Apply body rotation via RotationState — body turns, camera stays locked
        RotationState.applyState(true, silentYaw, silentPitch, silentYaw, 10);

        // Send silent look packet so server registers the rotation
        PacketUtil.sendPacketNoEvent(new C06PacketPlayerPosLook(
                mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                silentYaw, silentPitch, mc.thePlayer.onGround
        ));

        // Attack if cooldown elapsed and in range
        double dist = RotationUtil.distanceToEntity(currentTarget);
        double attackRange = range.getValue() + extraSwing.getValue();
        if (dist <= attackRange && System.currentTimeMillis() >= nextAttackMs) {
            // Send attack packet silently
            PacketUtil.sendPacketNoEvent(new C02PacketUseEntity(currentTarget, C02PacketUseEntity.Action.ATTACK));
            mc.thePlayer.swingItem(); // client-side swing animation
            attackingTarget = currentTarget;
            lastAttackMs = System.currentTimeMillis();
            scheduleNextAttack();
        }
    }

    // ── ESP rendering ─────────────────────────────────────────────────────────
    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || !showTarget.getValue()) return;

        if (currentTarget != null && currentTarget != attackingTarget) {
            int col = buildColor((int)targetR.getValue(), (int)targetG.getValue(), (int)targetB.getValue(), 180);
            drawEntityBox(currentTarget, col, event.getPartialTicks());
        }
        if (attackingTarget != null) {
            int col = buildColor((int)attackR.getValue(), (int)attackG.getValue(), (int)attackB.getValue(), 220);
            drawEntityBox(attackingTarget, col, event.getPartialTicks());
        }
    }

    // ── Target selection ──────────────────────────────────────────────────────
    private EntityPlayer findTarget() {
        if (mc.theWorld == null) return null;

        List<EntityPlayer> targets = mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityPlayer)
                .map(e -> (EntityPlayer) e)
                .filter(this::isValidTarget)
                .collect(Collectors.toList());

        if (targets.isEmpty()) return null;

        switch (targetMode.getIndex()) {
            case 0: // Distance
                targets.sort(Comparator.comparingDouble(RotationUtil::distanceToEntity));
                break;
            case 1: // Yaw
                targets.sort(Comparator.comparingDouble(p -> getAngleDiff(calcRotation(p)[0], calcRotation(p)[1])));
                break;
            case 2: // Armor (weakest first)
                targets.sort(Comparator.comparingInt(p -> p.getTotalArmorValue()));
                break;
            case 3: // Threat (weapon damage, highest first)
                targets.sort(Comparator.comparingDouble((EntityPlayer p) -> getThreat(p)).reversed());
                break;
            case 4: // Health (lowest first)
                targets.sort(Comparator.comparingDouble(EntityLivingBase::getHealth));
                break;
        }

        return targets.get(0);
    }

    private boolean isValidTarget(EntityPlayer p) {
        if (p == mc.thePlayer) return false;
        if (p.deathTime > 0 || p.isDead) return false;
        if (p.isInvisible()) return false;
        if (RotationUtil.distanceToEntity(p) > range.getValue() + extraSwing.getValue()) return false;
        if (friendCheck.getValue() && TeamUtil.isFriend(p)) return false;
        if (teamCheck.getValue() && TeamUtil.isSameTeam(p)) return false;
        if (botCheck.getValue() && TeamUtil.isBot(p)) return false;
        return true;
    }

    // ── Rotation calculation ──────────────────────────────────────────────────
    private float[] calcRotation(EntityPlayer target) {
        AxisAlignedBB bb = target.getEntityBoundingBox();
        float smooth = 1.0f - (float) aimSpeed.getValue() / 100.0f;

        if (targetArea.getIndex() == 1) {
            // Closest point on hitbox
            return RotationUtil.getRotationsToBox(bb,
                    mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                    180.0f, smooth);
        } else {
            // Center
            double cx = (bb.minX + bb.maxX) / 2.0;
            double cy = (bb.minY + bb.maxY) / 2.0;
            double cz = (bb.minZ + bb.maxZ) / 2.0;
            net.minecraft.util.Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
            return RotationUtil.getRotations(
                    cx - eyes.xCoord, cy - eyes.yCoord, cz - eyes.zCoord,
                    mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                    180.0f, smooth);
        }
    }

    private float getAngleDiff(float yaw, float pitch) {
        float yawDiff   = Math.abs(MathHelper.wrapAngleTo180_float(yaw   - mc.thePlayer.rotationYaw));
        float pitchDiff = Math.abs(MathHelper.wrapAngleTo180_float(pitch - mc.thePlayer.rotationPitch));
        return (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }

    // ── Attack timing ─────────────────────────────────────────────────────────
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
