package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.management.RotationState;
import myau.events.MoveInputEvent;
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
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.AxisAlignedBB;
import myau.mixin.IAccessorRenderManager;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

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
    public final DropdownSetting movement    = register(new DropdownSetting("Movement", 0, "Proper", "Slow", "None"));
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
    private EntityPlayer attackingTarget = null;
    private long lastAttackMs = 0;
    private long nextAttackMs = 0;
    private long breakPauseUntil = 0;
    private float silentYaw    = 0;
    private float silentPitch  = 0;
    private boolean pendingAttack = false;

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

        if (disableOnDeath.getValue() && mc.thePlayer.getHealth() <= 0) {
            setEnabled(false);
            return;
        }

        if (requireMouse.getValue() && !org.lwjgl.input.Mouse.isButtonDown(0)) {
            currentTarget = null;
            return;
        }

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

        if (limitItems.getValue() && !isAllowedItem()) {
            currentTarget = null;
            return;
        }

        currentTarget = findTarget();
        if (currentTarget == null) return;

        float[] rot = calcRotation(currentTarget);
        silentYaw   = rot[0];
        silentPitch = rot[1];

        float angleDiff = getAngleDiff(silentYaw, silentPitch);
        if (angleDiff > maxAngle.getValue()) {
            currentTarget = null;
            return;
        }

        RotationState.applyState(true, silentYaw, silentPitch, silentYaw, 10);

        double dist = RotationUtil.distanceToEntity(currentTarget);
        double attackRange = range.getValue() + extraSwing.getValue();
        pendingAttack = dist <= attackRange && System.currentTimeMillis() >= nextAttackMs;
    }

    // ── Silent rotation injection ─────────────────────────────────────────────
    @EventTarget
    public void onUpdateEvent(UpdateEvent event) {
        if (!isEnabled() || currentTarget == null) return;

        if (event.getType() == EventType.PRE) {
            if (pendingAttack) {
                event.setRotation(silentYaw, silentPitch, 10);
            }
        }
    }

    // ── Player update (movement + attack) ─────────────────────────────────────
    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!isEnabled()) return;

        // ───────────────────────────────────────────────────────────────
        //  PROPER MOVEMENT FIX (Grim-safe)
        // ───────────────────────────────────────────────────────────────
        if (movement.getIndex() == 0 && currentTarget != null) {

            float serverYaw = silentYaw;
            float clientYaw = mc.thePlayer.rotationYaw;

            float diff = MathHelper.wrapAngleTo180_float(serverYaw - clientYaw);
            if (Math.abs(diff) > 0.1f) {

                // 1. Rotate movement input BEFORE physics
                float forward = mc.thePlayer.movementInput.moveForward;
                float strafe  = mc.thePlayer.movementInput.moveStrafe;

                float rad = (float) Math.toRadians(diff);
                float cos = MathHelper.cos(rad);
                float sin = MathHelper.sin(rad);

                float newForward = forward * cos - strafe * sin;
                float newStrafe  = forward * sin + strafe * cos;

                mc.thePlayer.movementInput.moveForward = newForward;
                mc.thePlayer.movementInput.moveStrafe  = newStrafe;

                // 2. Rotate velocity AFTER physics
                double speed = MoveUtil.getSpeed();
                if (speed > 0.001) {
                    float dir = (float) Math.toDegrees(Math.atan2(-mc.thePlayer.motionX, mc.thePlayer.motionZ));
                    float newDir = dir + diff;

                    mc.thePlayer.motionX = -Math.sin(Math.toRadians(newDir)) * speed;
                    mc.thePlayer.motionZ =  Math.cos(Math.toRadians(newDir)) * speed;
                }

                // 3. Fix jump direction
                if (mc.thePlayer.isAirBorne && mc.thePlayer.motionY > 0) {
                    float jumpDir = (float) Math.toDegrees(Math.atan2(-mc.thePlayer.motionX, mc.thePlayer.motionZ));
                    float newJumpDir = jumpDir + diff;

                    double jumpSpeed = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX +
                                                 mc.thePlayer.motionZ * mc.thePlayer.motionZ);

                    mc.thePlayer.motionX = -Math.sin(Math.toRadians(newJumpDir)) * jumpSpeed;
                    mc.thePlayer.motionZ =  Math.cos(Math.toRadians(newJumpDir)) * jumpSpeed;
                }
            }
        }

        // ───────────────────────────────────────────────────────────────
        //  ATTACK
        // ───────────────────────────────────────────────────────────────
        if (!pendingAttack || currentTarget == null || currentTarget.isDead) return;

        PacketUtil.sendPacketNoEvent(new C02PacketUseEntity(currentTarget, C02PacketUseEntity.Action.ATTACK));
        mc.thePlayer.swingItem();
        attackingTarget = currentTarget;
        lastAttackMs = System.currentTimeMillis();
        scheduleNextAttack();
        pendingAttack = false;
    }

    // ── Slow movement mode ────────────────────────────────────────────────────
    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled() || currentTarget == null) return;
        if (movement.getIndex() == 1) {
            mc.thePlayer.movementInput.moveForward *= 0.6f;
            mc.thePlayer.movementInput.moveStrafe  *= 0.6f;
        }
    }

    // ── ESP rendering ─────────────────────────────────────────────────────────
    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled()) return;

        if (aimIndicator.getValue() && currentTarget != null) {
            IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();
            float pt = event.getPartialTicks();
            double tx = currentTarget.lastTickPosX + (currentTarget.posX - currentTarget.lastTickPosX) * pt - rm.getRenderPosX();
            double ty = currentTarget.lastTickPosY + (currentTarget.posY - currentTarget.lastTickPosY) * pt - rm.getRenderPosY()
                    + currentTarget.height * 0.5;
            double tz = currentTarget.lastTickPosZ + (currentTarget.posZ - currentTarget.lastTickPosZ) * pt - rm.getRenderPosZ();

            net.minecraft.util.Vec3 eyes = mc.thePlayer.getPositionEyes(pt);
            double ox = eyes.xCoord - rm.getRenderPosX();
            double oy = eyes.yCoord - rm.getRenderPosY();
            double oz = eyes.zCoord - rm.getRenderPosZ();

            net.minecraft.client.renderer.GlStateManager.pushMatrix();
            net.minecraft.client.renderer.GlStateManager.disableTexture2D();
            net.minecraft.client.renderer.GlStateManager.disableDepth();
            net.minecraft.client.renderer.GlStateManager.enableBlend();
            net.minecraft.client.renderer.GlStateManager.blendFunc(770, 771);
            GL11.glLineWidth(1.5f);
            net.minecraft.client.renderer.GlStateManager.color(1.0f, 0.47f, 0.67f, 0.8f);
            net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
            net.minecraft.client.renderer.WorldRenderer wr = tess.getWorldRenderer();
            wr.begin(1, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
            wr.pos(ox, oy, oz).endVertex();
            wr.pos(tx, ty, tz).endVertex();
            tess.draw();
            net.minecraft.client.renderer.GlStateManager.enableDepth();
            net.minecraft.client.renderer.GlStateManager.enableTexture2D();
            net.minecraft.client.renderer.GlStateManager.disableBlend();
            net.minecraft.client.renderer.GlStateManager.popMatrix();
        }

        if (!showTarget.getValue()) return;

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
            case 0:
                targets.sort(Comparator.comparingDouble(RotationUtil::distanceToEntity));
                break;
            case 1:
                targets.sort(Comparator.comparingDouble(p -> getAngleDiff(calcRotation(p)[0], calcRotation(p)[1])));
                break;
            case 2:
                targets.sort(Comparator.comparingInt(p -> p.getTotalArmorValue()));
                break;
            case 3:
                targets.sort(Comparator.comparingDouble((EntityPlayer p) -> getThreat(p)).reversed());
                break;
            case 4:
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
            return RotationUtil.getRotationsToBox(bb,
                    mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                    180.0f, smooth);
        } else {
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
        if (held == null)
