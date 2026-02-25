package myau.module.modules;

import myau.event.EventTarget;
import myau.events.RenderLivingEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

public class Chams extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanSetting players  = register(new BooleanSetting("Players",   true));
    public final BooleanSetting friends  = register(new BooleanSetting("Friends",   true));
    public final BooleanSetting enemiess = register(new BooleanSetting("Enemies",   true));
    public final BooleanSetting bosses   = register(new BooleanSetting("Bosses",    false));
    public final BooleanSetting mobs     = register(new BooleanSetting("Mobs",      false));
    public final BooleanSetting creepers = register(new BooleanSetting("Creepers",  false));
    public final BooleanSetting enderman = register(new BooleanSetting("Endermen",  false));
    public final BooleanSetting blaze    = register(new BooleanSetting("Blazes",    false));
    public final BooleanSetting animals  = register(new BooleanSetting("Animals",   false));
    public final BooleanSetting self     = register(new BooleanSetting("Self",      false));
    public final BooleanSetting bots     = register(new BooleanSetting("Bots",      false));

    public Chams() {
        super("Chams", false);
    }

    private boolean shouldRenderChams(EntityLivingBase entity) {
        if (entity.deathTime > 0) return false;
        if (mc.getRenderViewEntity().getDistanceToEntity(entity) > 512.0F) return false;
        if (entity instanceof EntityPlayer) {
            if (entity == mc.thePlayer || entity == mc.getRenderViewEntity()) {
                return self.getValue() && mc.gameSettings.thirdPersonView != 0;
            }
            if (TeamUtil.isBot((EntityPlayer) entity))    return bots.getValue();
            if (TeamUtil.isFriend((EntityPlayer) entity)) return friends.getValue();
            return TeamUtil.isTarget((EntityPlayer) entity) ? enemiess.getValue() : players.getValue();
        }
        if (entity instanceof EntityDragon || entity instanceof EntityWither)
            return !entity.isInvisible() && bosses.getValue();
        if (entity instanceof EntityCreeper) return creepers.getValue();
        if (entity instanceof EntityEnderman) return enderman.getValue();
        if (entity instanceof EntityBlaze) return blaze.getValue();
        if (entity instanceof EntityMob || entity instanceof EntitySlime) return mobs.getValue();
        if (entity instanceof EntityAnimal
                || entity instanceof EntityBat
                || entity instanceof EntitySquid
                || entity instanceof EntityVillager)
            return animals.getValue();
        return false;
    }

    @EventTarget
    public void onRenderLiving(RenderLivingEvent event) {
        if (!isEnabled() || !shouldRenderChams(event.getEntity())) return;
        switch (event.getType()) {
            case PRE:
                GL11.glEnable(32823);
                GL11.glPolygonOffset(1.0F, -2500000.0F);
                break;
            case POST:
                GL11.glPolygonOffset(1.0F, 2500000.0F);
                GL11.glDisable(32823);
                break;
        }
    }
}