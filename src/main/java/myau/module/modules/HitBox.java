package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LeftClickMouseEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class HitBox extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private MovingObjectPosition targetEntity = null;

    public final SliderSetting   multiplier  = new SliderSetting("Multiplier",  1.2, 1.0, 5.0, 0.1);
    public final DropdownSetting showHitbox  = new DropdownSetting("Show Hitbox", 0, "NONE", "PLAYERS", "MOBS", "ANIMALS", "ALL");
    // ColorProperty workaround: R/G/B sliders
    public final SliderSetting   colorR      = new SliderSetting("Color R",   255,   0, 255, 1);
    public final SliderSetting   colorG      = new SliderSetting("Color G",   255,   0, 255, 1);
    public final SliderSetting   colorB      = new SliderSetting("Color B",   255,   0, 255, 1);
    public final BooleanSetting  teams       = new BooleanSetting("Teams",    true);
    public final BooleanSetting  botCheck    = new BooleanSetting("Bot Check", true);

    public HitBox() {
        super("HitBox", false);
        register(multiplier); register(showHitbox);
        register(colorR); register(colorG); register(colorB);
        register(teams); register(botCheck);
    }

    public static float getExpansion(Entity entity) {
        HitBox hitBox = (HitBox) Myau.moduleManager.modules.get(HitBox.class);
        if (hitBox != null && hitBox.isEnabled() && entity instanceof EntityLivingBase)
            return (float) hitBox.multiplier.getValue();
        return 1.0F;
    }

    private void calculateMouseOver(float partialTicks) {
        if (mc.getRenderViewEntity() == null || mc.theWorld == null) return;
        mc.pointedEntity = null;
        Entity pointedEntity = null;
        double reach = 3.0;
        targetEntity = mc.getRenderViewEntity().rayTrace(reach, partialTicks);
        double distance = reach;
        Vec3 eyePos = mc.getRenderViewEntity().getPositionEyes(partialTicks);
        if (targetEntity != null) distance = targetEntity.hitVec.distanceTo(eyePos);
        Vec3 lookVec  = mc.getRenderViewEntity().getLook(partialTicks);
        Vec3 reachVec = eyePos.addVector(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach);
        Vec3 hitVec = null;
        List<Entity> entities = mc.theWorld.getEntitiesWithinAABBExcludingEntity(
                mc.getRenderViewEntity(),
                mc.getRenderViewEntity().getEntityBoundingBox()
                        .addCoord(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach)
                        .expand(1.0F, 1.0F, 1.0F));
        double closestDistance = distance;
        for (Entity entity : entities) {
            if (!entity.canBeCollidedWith()) continue;
            float collisionSize = (float) ((double) entity.getCollisionBorderSize() * getExpansion(entity));
            AxisAlignedBB expandedBox = entity.getEntityBoundingBox().expand(collisionSize, collisionSize, collisionSize);
            MovingObjectPosition intercept = expandedBox.calculateIntercept(eyePos, reachVec);
            if (expandedBox.isVecInside(eyePos)) {
                if (0.0 < closestDistance || closestDistance == 0.0) {
                    pointedEntity = entity;
                    hitVec = intercept == null ? eyePos : intercept.hitVec;
                    closestDistance = 0.0;
                }
            } else if (intercept != null) {
                double interceptDistance = eyePos.distanceTo(intercept.hitVec);
                if (interceptDistance < closestDistance || closestDistance == 0.0) {
                    if (entity == mc.getRenderViewEntity().ridingEntity && !entity.canRiderInteract()) {
                        if (closestDistance == 0.0) { pointedEntity = entity; hitVec = intercept.hitVec; }
                    } else { pointedEntity = entity; hitVec = intercept.hitVec; closestDistance = interceptDistance; }
                }
            }
        }
        if (pointedEntity != null && (closestDistance < distance || targetEntity == null)) {
            targetEntity = new MovingObjectPosition(pointedEntity, hitVec);
            if (pointedEntity instanceof EntityLivingBase || pointedEntity instanceof EntityItemFrame)
                mc.pointedEntity = pointedEntity;
        }
    }

    private boolean shouldShowEntity(EntityLivingBase entity) {
        if (entity == mc.thePlayer) return false;
        if (entity.deathTime > 0 || entity instanceof EntityArmorStand || entity.isInvisible()) return false;
        if (mc.getRenderViewEntity().getDistanceToEntity(entity) > 128.0F) return false;
        if (!entity.ignoreFrustumCheck && !RenderUtil.isInViewFrustum(entity.getEntityBoundingBox(), 0.1F)) return false;
        switch (showHitbox.getIndex()) {
            case 0: return false;
            case 1:
                if (!(entity instanceof EntityPlayer)) return false;
                EntityPlayer p1 = (EntityPlayer) entity;
                if (TeamUtil.isFriend(p1)) return false;
                if (teams.getValue() && TeamUtil.isSameTeam(p1)) return false;
                if (botCheck.getValue() && TeamUtil.isBot(p1)) return false;
                return true;
            case 2:
                if (entity instanceof EntityDragon || entity instanceof EntityWither) return true;
                if (entity instanceof EntityMob || entity instanceof EntitySlime) return !(entity instanceof EntitySilverfish);
                return false;
            case 3:
                return entity instanceof EntityAnimal || entity instanceof EntityBat
                        || entity instanceof EntitySquid || entity instanceof EntityVillager
                        || entity instanceof EntityIronGolem;
            case 4:
                if (entity instanceof EntityPlayer) {
                    EntityPlayer p4 = (EntityPlayer) entity;
                    if (TeamUtil.isFriend(p4)) return false;
                    if (teams.getValue() && TeamUtil.isSameTeam(p4)) return false;
                    if (botCheck.getValue() && TeamUtil.isBot(p4)) return false;
                }
                return true;
            default: return false;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (isEnabled() && event.getType() == EventType.PRE) calculateMouseOver(1.0F);
    }

    @EventTarget(Priority.HIGH)
    public void onLeftClick(LeftClickMouseEvent event) {
        if (isEnabled() && !event.isCancelled() && targetEntity != null)
            mc.objectMouseOver = targetEntity;
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!isEnabled() || showHitbox.getIndex() == 0) return;
        List<EntityLivingBase> entities = mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityLivingBase)
                .map(e -> (EntityLivingBase) e)
                .filter(this::shouldShowEntity)
                .collect(Collectors.toList());
        if (entities.isEmpty()) return;
        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();
        RenderUtil.enableRenderState();
        int r = (int) colorR.getValue(), g = (int) colorG.getValue(), b = (int) colorB.getValue();
        for (EntityLivingBase entity : entities) {
            float collisionSize = (float) ((double) entity.getCollisionBorderSize() * multiplier.getValue());
            AxisAlignedBB expandedBox = entity.getEntityBoundingBox().expand(collisionSize, collisionSize, collisionSize);
            double ix = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, event.getPartialTicks()) - rm.getRenderPosX();
            double iy = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, event.getPartialTicks()) - rm.getRenderPosY();
            double iz = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, event.getPartialTicks()) - rm.getRenderPosZ();
            AxisAlignedBB offsetBox = new AxisAlignedBB(
                    expandedBox.minX - entity.posX + ix, expandedBox.minY - entity.posY + iy, expandedBox.minZ - entity.posZ + iz,
                    expandedBox.maxX - entity.posX + ix, expandedBox.maxY - entity.posY + iy, expandedBox.maxZ - entity.posZ + iz);
            RenderUtil.drawBoundingBox(offsetBox, r, g, b, 150, 1.5F);
        }
        RenderUtil.disableRenderState();
    }

    @Override
    public String[] getSuffix() { return new String[]{ String.format("%.1fx", multiplier.getValue()) }; }
}