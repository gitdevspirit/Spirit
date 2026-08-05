package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.UpdateEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * KBDisplacement — while attacking a nearby target, alternates ticks between
 * a real hit and a silent "displacement" tick that aims toward a void column
 * (or a fixed offset angle) near the target, so the *next* knockback hit
 * shoves them toward it instead of straight back. Ported from a keystrokesmod
 * reference; the fake-lag/blink piece maps onto Myau's own BlinkManager, and
 * the silent rotation goes through the same UpdateEvent/RotationState/
 * MovementFix pipeline as AimAssist Silent and Clutch.
 */
public class KBDisplacement extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // Void ring-scan (static mode): 8 compass directions around the target.
    private static final int VOID_SCAN_RINGS = 4;
    private static final double VOID_SCAN_STEP = 1.0;
    private static final int VOID_SCAN_DEPTH = 3;
    private static final double[] VOID_SCAN_X = {1, 0.7071, 0, -0.7071, -1, -0.7071, 0, 0.7071};
    private static final double[] VOID_SCAN_Z = {0, 0.7071, 1, 0.7071, 0, -0.7071, -1, -0.7071};

    // Forward-path scan (dynamic mode).
    private static final double DYNAMIC_SCAN_DISTANCE = 3.0;
    private static final double DYNAMIC_SCAN_STEP = 0.5;
    private static final double DYNAMIC_SCAN_SIDE_STEP = 0.3;
    private static final double DYNAMIC_WALL_CHECK_STEP = 0.25;

    // Per-target re-displace throttling.
    private static final int DISPLACE_WINDOW_TICKS = 20;

    // Arrow render geometry.
    private static final long ARROW_FADE_MS = 250L;
    private static final double ARROW_FORWARD_GAP = 0.3;
    private static final double ARROW_BODY_LENGTH = 0.6;
    private static final double ARROW_HEAD_LENGTH = 0.35;
    private static final double ARROW_HEAD_BACKSET = 0.15;
    private static final double ARROW_BODY_HALF_HEIGHT = 0.04;
    private static final double ARROW_HEAD_HALF_HEIGHT = 0.09;

    public final SliderSetting  range        = register(new SliderSetting("Range", 4.0, 2.0, 6.0, 0.1));
    public final BooleanSetting findVoid      = register(new BooleanSetting("Find Void", true));
    public final BooleanSetting dynamicAngle  = register(new BooleanSetting(
            "Dynamic Angle", false, () -> findVoid.getValue()));
    public final BooleanSetting displaceLeftSetting = register(new BooleanSetting(
            "Displace Left", true, () -> !findVoid.getValue() || !dynamicAngle.getValue()));
    public final SliderSetting  yawOffset    = register(new SliderSetting(
            "Yaw Offset", 30, 5, 90, 1, () -> !findVoid.getValue()));
    public final SliderSetting  delay        = register(new SliderSetting("Delay", 0, 0, 1000, 50));
    public final BooleanSetting ignoreTeammates    = register(new BooleanSetting("Ignore Teammates", true));
    public final BooleanSetting requireKnockback   = register(new BooleanSetting("Require Knockback Enchant", false));
    public final BooleanSetting weaponOnly         = register(new BooleanSetting("Weapon Only", false));
    public final BooleanSetting blink              = register(new BooleanSetting("Blink", false));
    public final BooleanSetting showDirection      = register(new BooleanSetting("Show Direction", true));

    private boolean active;
    private boolean displaceThisTick;
    private boolean wasDisplacingLastTick;
    private boolean compensateNextTick;
    private boolean hasKB;
    private boolean displaceLeft;
    private long tickCounter;

    private Float renderDisplaceYaw;
    private EntityPlayer renderTarget;

    private Float fadingDisplaceYaw;
    private EntityPlayer fadingTarget;
    private long arrowFadeStartMs;

    private Float lastRenderedDisplaceYaw;
    private EntityPlayer lastRenderedTarget;
    private long lastRenderedArrowMs;

    private boolean blinking;
    private final Map<Integer, Long> targetWindowStartTicks = new HashMap<>();

    public KBDisplacement() { super("KBDisplacement", false); }

    @Override
    public void onEnabled() {
        tickCounter = 0L;
    }

    @Override
    public void onDisabled() {
        clearActiveState();
        clearArrowState();
        targetWindowStartTicks.clear();
        releaseBlink();
    }

    // ── Main driver ───────────────────────────────────────────────────────────

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            clearActiveState();
            return;
        }

        tickCounter++;
        long currentTick = tickCounter;
        pruneTargetDelayStates();

        if (!passesItemCondition()) {
            clearActiveState();
            return;
        }

        EntityPlayer target = isAttacking() ? findDisplacementTarget() : null;
        boolean hasKBEnchant = EnchantmentHelper.getKnockbackModifier(mc.thePlayer) > 0;
        active = target != null && (hasKBEnchant || anyMovementKey());
        if (!active) {
            clearActiveState();
            return;
        }

        Float voidYaw = findVoid.getValue()
                ? (dynamicAngle.getValue() ? findDynamicVoidYaw(target) : findStaticVoidYaw(target))
                : null;

        if (voidYaw == null && !(findVoid.getValue() && dynamicAngle.getValue())) {
            displaceLeft = displaceLeftSetting.getValue();
        }

        renderDisplaceYaw = voidYaw != null ? voidYaw
                : (findVoid.getValue() && dynamicAngle.getValue()) ? null
                : getFixedDisplaceYaw(event.getYaw());
        renderTarget = renderDisplaceYaw != null ? target : null;

        if (renderDisplaceYaw == null) {
            clearActiveState();
            return;
        }

        hasKB = hasKBEnchant;
        displaceThisTick = !displaceThisTick;

        if (displaceThisTick && !shouldDisplaceInCurrentWindow(target, currentTick)) {
            startArrowFade();
            displaceThisTick = false;
            compensateNextTick = false;
            wasDisplacingLastTick = false;
            renderDisplaceYaw = null;
            renderTarget = null;
            return;
        }

        wasDisplacingLastTick = displaceThisTick;
        if (!displaceThisTick) return;

        myau.management.MovementFix.forceMovementFix = true;
        myau.management.RotationState.applyState(true, renderDisplaceYaw, mc.thePlayer.rotationPitch, renderDisplaceYaw, 20);
        event.setRotation(renderDisplaceYaw, event.getPitch(), 20);
    }

    /** Presses forward then strafes away on alternating ticks when there's no KB enchant to rely on. */
    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled()) return;
        if (!active) {
            compensateNextTick = false;
            return;
        }
        if (mc.thePlayer == null) return;

        if (compensateNextTick && !displaceThisTick) {
            compensateNextTick = false;
            mc.thePlayer.movementInput.moveStrafe = displaceLeft ? -1.0f : 1.0f;
            return;
        }

        if (!displaceThisTick || hasKB) return;
        if (!anyMovementKey()) return;

        mc.thePlayer.movementInput.moveForward = 1.0f;
        compensateNextTick = true;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.SEND) return;
        if (!blink.getValue() || !active || !displaceThisTick || blinking) return;
        if (!(event.getPacket() instanceof C03PacketPlayer)) return;

        Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        blinking = true;
    }

    // ── Targeting / void search ───────────────────────────────────────────────

    private boolean passesItemCondition() {
        if (weaponOnly.getValue() && !ItemUtil.isHoldingSword()) return false;
        if (!requireKnockback.getValue()) return true;
        return EnchantmentHelper.getKnockbackModifier(mc.thePlayer) > 0;
    }

    private boolean anyMovementKey() {
        return mc.gameSettings.keyBindForward.isKeyDown()
                || mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown()
                || mc.gameSettings.keyBindRight.isKeyDown();
    }

    private boolean isAttacking() {
        if (Mouse.isButtonDown(0) || mc.gameSettings.keyBindAttack.isKeyDown()) return true;
        myau.module.Module ka = Myau.moduleManager.getModule("KillAura");
        return ka != null && ka.isEnabled() && (ka instanceof KillAura) && ((KillAura) ka).getTarget() != null;
    }

    private EntityPlayer findDisplacementTarget() {
        double rangeSq = range.getValue() * range.getValue();
        EntityPlayer nearest = null;
        double nearestDistSq = rangeSq;

        for (Object obj : mc.theWorld.playerEntities) {
            EntityPlayer p = (EntityPlayer) obj;
            if (p == mc.thePlayer || p.isDead || p.deathTime > 0) continue;
            if (TeamUtil.isFriend(p)) continue;
            if (ignoreTeammates.getValue() && TeamUtil.isSameTeam(p)) continue;

            double distSq = mc.thePlayer.getDistanceSqToEntity(p);
            if (distSq <= nearestDistSq) {
                nearestDistSq = distSq;
                nearest = p;
            }
        }
        return nearest;
    }

    private Float findStaticVoidYaw(EntityPlayer target) {
        if (target == null) return null;

        double bestX = 0.0, bestZ = 0.0, bestScore = Double.MAX_VALUE;

        for (int ring = 1; ring <= VOID_SCAN_RINGS; ring++) {
            double radius = ring * VOID_SCAN_STEP;
            boolean foundInRing = false;

            for (int i = 0; i < VOID_SCAN_X.length; i++) {
                double x = target.posX + VOID_SCAN_X[i] * radius;
                double z = target.posZ + VOID_SCAN_Z[i] * radius;
                if (!isVoidColumn(x, target.posY, z)) continue;

                double dx = x - mc.thePlayer.posX;
                double dz = z - mc.thePlayer.posZ;
                double score = radius * radius * 1000.0 + (dx * dx + dz * dz);
                if (score < bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestZ = z;
                    foundInRing = true;
                }
            }
            if (foundInRing) break;
        }

        if (bestScore == Double.MAX_VALUE) return null;
        updateDisplaceSide(target, bestX, bestZ);

        double dx = bestX - target.posX;
        double dz = bestZ - target.posZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001) return null;

        double aimRadius = Math.min(dist, Math.max(0.35, target.width * 0.5 + 0.15));
        double aimX = target.posX + dx / dist * aimRadius;
        double aimZ = target.posZ + dz / dist * aimRadius;
        double aimY = target.posY + target.getEyeHeight() * 0.5;

        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        float[] rot = RotationUtil.getRotations(aimX - eyes.xCoord, aimY - eyes.yCoord, aimZ - eyes.zCoord,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, 180.0f, 0.0f);
        return rot[0];
    }

    private Float findDynamicVoidYaw(EntityPlayer target) {
        if (target == null) return null;

        double bestForwardX = 0.0, bestForwardZ = 0.0, bestScore = 0.0;

        for (int i = 0; i < VOID_SCAN_X.length; i++) {
            double score = scoreVoidPath(target, VOID_SCAN_X[i], VOID_SCAN_Z[i]);
            if (score > bestScore) {
                bestScore = score;
                bestForwardX = VOID_SCAN_X[i];
                bestForwardZ = VOID_SCAN_Z[i];
            }
        }

        if (bestScore <= 0.0) return null;
        updateDisplaceSide(target, target.posX + bestForwardX, target.posZ + bestForwardZ);
        return (float) (Math.toDegrees(Math.atan2(bestForwardZ, bestForwardX)) - 90.0);
    }

    private double scoreVoidPath(EntityPlayer target, double forwardX, double forwardZ) {
        double sideX = -forwardZ;
        double sideZ = forwardX;
        double score = 0.0;
        double checkedForward = 0.0;
        int consecutiveCenterVoid = 0;

        int steps = (int) (DYNAMIC_SCAN_DISTANCE / DYNAMIC_SCAN_STEP);
        for (int step = 1; step <= steps; step++) {
            double forward = step * DYNAMIC_SCAN_STEP;
            if (!isDynamicPathClear(target, forwardX, forwardZ, checkedForward, forward)) break;
            checkedForward = forward;

            boolean centerVoid = false;
            for (int side = -1; side <= 1; side++) {
                double sideOffset = side * DYNAMIC_SCAN_SIDE_STEP;
                double x = target.posX + forwardX * forward + sideX * sideOffset;
                double z = target.posZ + forwardZ * forward + sideZ * sideOffset;
                if (isVoidColumn(x, target.posY, z)) {
                    double laneWeight = side == 0 ? 1.4 : 1.0;
                    score += laneWeight * (DYNAMIC_SCAN_DISTANCE + DYNAMIC_SCAN_STEP - forward);
                    centerVoid |= side == 0;
                }
            }
            if (centerVoid) {
                consecutiveCenterVoid++;
                score += consecutiveCenterVoid * 2.0;
            } else {
                consecutiveCenterVoid = 0;
            }
        }
        return score;
    }

    private boolean isDynamicPathClear(EntityPlayer target, double forwardX, double forwardZ, double fromForward, double toForward) {
        for (double forward = fromForward + DYNAMIC_WALL_CHECK_STEP; forward <= toForward + 1.0E-4; forward += DYNAMIC_WALL_CHECK_STEP) {
            double x = target.posX + forwardX * forward;
            double z = target.posZ + forwardZ * forward;
            if (isSolidAt(x, target.posY, z) || isSolidAt(x, target.posY + target.height * 0.5, z)) return false;
        }
        return true;
    }

    private boolean isSolidAt(double x, double y, double z) {
        BlockPos pos = new BlockPos(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        Material material = block.getMaterial();
        return material.isSolid() || material.blocksMovement();
    }

    private boolean isVoidColumn(double x, double y, double z) {
        int blockX = MathHelper.floor_double(x);
        int blockZ = MathHelper.floor_double(z);
        int startY = MathHelper.floor_double(y) - 1;
        int endY = Math.max(0, startY - VOID_SCAN_DEPTH);
        for (int blockY = startY; blockY >= endY; blockY--) {
            if (!mc.theWorld.isAirBlock(new BlockPos(blockX, blockY, blockZ))) return false;
        }
        return true;
    }

    private void updateDisplaceSide(EntityPlayer target, double voidX, double voidZ) {
        double targetDx = target.posX - mc.thePlayer.posX;
        double targetDz = target.posZ - mc.thePlayer.posZ;
        double voidDx = voidX - mc.thePlayer.posX;
        double voidDz = voidZ - mc.thePlayer.posZ;
        double cross = targetDx * voidDz - targetDz * voidDx;
        displaceLeft = cross < 0.0;
    }

    private float getFixedDisplaceYaw(float baseYaw) {
        float offset = (float) yawOffset.getValue();
        return displaceLeftSetting.getValue() ? baseYaw - offset : baseYaw + offset;
    }

    // ── Per-target throttling ─────────────────────────────────────────────────

    private void pruneTargetDelayStates() {
        if (mc.theWorld == null) {
            targetWindowStartTicks.clear();
            return;
        }
        Iterator<Map.Entry<Integer, Long>> it = targetWindowStartTicks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            Entity entity = mc.theWorld.getEntityByID(entry.getKey());
            if (!(entity instanceof EntityPlayer) || entity.isDead || ((EntityPlayer) entity).deathTime != 0) {
                it.remove();
            }
        }
    }

    private boolean shouldDisplaceInCurrentWindow(EntityPlayer target, long currentTick) {
        if (target == null) return true;
        int targetId = target.getEntityId();
        Long windowStartTick = targetWindowStartTicks.get(targetId);
        if (windowStartTick == null || currentTick - windowStartTick >= DISPLACE_WINDOW_TICKS) {
            targetWindowStartTicks.put(targetId, currentTick);
            return true;
        }
        long delayTicks = (long) Math.ceil(delay.getValue() / 50.0);
        if (delayTicks <= 0) return true;
        return (currentTick - windowStartTick) >= delayTicks;
    }

    // ── State reset / blink ───────────────────────────────────────────────────

    private void releaseBlink() {
        if (blinking) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            blinking = false;
        }
    }

    private void clearActiveState() {
        startArrowFade();
        active = false;
        displaceThisTick = false;
        compensateNextTick = false;
        wasDisplacingLastTick = false;
        renderDisplaceYaw = null;
        renderTarget = null;
        releaseBlink();
    }

    private void clearFadingArrow() {
        fadingDisplaceYaw = null;
        fadingTarget = null;
        arrowFadeStartMs = 0L;
    }

    private void clearArrowState() {
        clearFadingArrow();
        lastRenderedDisplaceYaw = null;
        lastRenderedTarget = null;
        lastRenderedArrowMs = 0L;
    }

    private void startArrowFade() {
        long nowMs = System.currentTimeMillis();
        if (lastRenderedDisplaceYaw != null && lastRenderedTarget != null && !lastRenderedTarget.isDead
                && nowMs - lastRenderedArrowMs <= ARROW_FADE_MS) {
            fadingDisplaceYaw = lastRenderedDisplaceYaw;
            fadingTarget = lastRenderedTarget;
            arrowFadeStartMs = nowMs;
        }
        lastRenderedDisplaceYaw = null;
        lastRenderedTarget = null;
        lastRenderedArrowMs = 0L;
    }

    // ── Arrow rendering ───────────────────────────────────────────────────────

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || !showDirection.getValue() || mc.thePlayer == null || mc.theWorld == null) {
            clearArrowState();
            return;
        }

        long nowMs = System.currentTimeMillis();
        boolean activeArrow = active && renderDisplaceYaw != null && renderTarget != null && !renderTarget.isDead;
        Float arrowYaw = renderDisplaceYaw;
        EntityPlayer arrowTarget = renderTarget;
        float alpha = 1.0f;

        if (activeArrow) {
            clearFadingArrow();
        } else {
            if (fadingDisplaceYaw == null || fadingTarget == null || fadingTarget.isDead) {
                clearFadingArrow();
                return;
            }
            long fadeElapsedMs = nowMs - arrowFadeStartMs;
            if (fadeElapsedMs >= ARROW_FADE_MS) {
                clearFadingArrow();
                return;
            }
            arrowYaw = fadingDisplaceYaw;
            arrowTarget = fadingTarget;
            alpha = 1.0f - (float) fadeElapsedMs / (float) ARROW_FADE_MS;
        }

        float partialTicks = event.getPartialTicks();
        double centerX = arrowTarget.lastTickPosX + (arrowTarget.posX - arrowTarget.lastTickPosX) * partialTicks;
        double centerY = arrowTarget.lastTickPosY + (arrowTarget.posY - arrowTarget.lastTickPosY) * partialTicks + arrowTarget.height * 0.5;
        double centerZ = arrowTarget.lastTickPosZ + (arrowTarget.posZ - arrowTarget.lastTickPosZ) * partialTicks;

        double yawRad = Math.toRadians(arrowYaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        double baseOffset = arrowTarget.width * 0.5 + ARROW_FORWARD_GAP;
        double tailX = centerX + forwardX * baseOffset;
        double tailZ = centerZ + forwardZ * baseOffset;
        double bodyEndX = tailX + forwardX * ARROW_BODY_LENGTH;
        double bodyEndZ = tailZ + forwardZ * ARROW_BODY_LENGTH;
        double headBackX = tailX + forwardX * (ARROW_BODY_LENGTH - ARROW_HEAD_BACKSET);
        double headBackZ = tailZ + forwardZ * (ARROW_BODY_LENGTH - ARROW_HEAD_BACKSET);
        double tipX = bodyEndX + forwardX * ARROW_HEAD_LENGTH;
        double tipZ = bodyEndZ + forwardZ * ARROW_HEAD_LENGTH;

        double viewerX = ((myau.mixin.IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double viewerY = ((myau.mixin.IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double viewerZ = ((myau.mixin.IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

        GlStateManager.pushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        GlStateManager.color(1.0f, 1.0f, 1.0f, 0.82f * alpha);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex3d(tailX - viewerX, centerY - viewerY, tailZ - viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, -ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, -ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(headBackX, centerY, headBackZ, -ARROW_HEAD_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glVertex3d(tipX - viewerX, centerY - viewerY, tipZ - viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, -ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glVertex3d(tipX - viewerX, centerY - viewerY, tipZ - viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glVertex3d(tipX - viewerX, centerY - viewerY, tipZ - viewerZ);
        arrowVertex(headBackX, centerY, headBackZ, ARROW_HEAD_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glEnd();

        GL11.glLineWidth(2.0f);
        GlStateManager.color(0.0f, 0.0f, 0.0f, 0.95f * alpha);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(tailX - viewerX, centerY - viewerY, tailZ - viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, -ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(headBackX, centerY, headBackZ, -ARROW_HEAD_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glVertex3d(tipX - viewerX, centerY - viewerY, tipZ - viewerZ);
        arrowVertex(headBackX, centerY, headBackZ, ARROW_HEAD_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glEnd();

        GL11.glPopAttrib();
        GlStateManager.popMatrix();

        if (activeArrow) {
            lastRenderedDisplaceYaw = arrowYaw;
            lastRenderedTarget = arrowTarget;
            lastRenderedArrowMs = nowMs;
        }
    }

    private void arrowVertex(double x, double y, double z, double verticalOffset, double viewerX, double viewerY, double viewerZ) {
        GL11.glVertex3d(x - viewerX, y + verticalOffset - viewerY, z - viewerZ);
    }

    @Override
    public String[] getSuffix() {
        if (!active) return new String[0];
        return new String[]{ displaceThisTick ? "Displacing" : "Waiting" };
    }
}
