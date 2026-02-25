package myau.util;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;

public class AttackData {

    private final EntityLivingBase entity;
    private final AxisAlignedBB box;
    private final double x;
    private final double y;
    private final double z;

    public AttackData(EntityLivingBase entity) {
        this.entity = entity;
        this.box = entity.getEntityBoundingBox();
        this.x = entity.posX;
        this.y = entity.posY;
        this.z = entity.posZ;
    }

    public EntityLivingBase getEntity() {
        return this.entity;
    }

    public AxisAlignedBB getBox() {
        return this.box;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }
}
