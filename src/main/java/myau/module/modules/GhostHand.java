package myau.module.modules;

import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.TeamUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.Minecraft;

public class GhostHand extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final SliderSetting   range = register(new SliderSetting("Range", 3.0, 3.0, 6.0, 0.1));
    public final BooleanSetting  teamsOnly = register(new BooleanSetting("Team Only", true));
    public final BooleanSetting  ignoreWeapons = register(new BooleanSetting("Ignore Weapons", false));

    public GhostHand() {
        super("GhostHand", false);
    }

    public boolean shouldSkip(Entity entity) {
        return entity instanceof EntityPlayer
                && !TeamUtil.isBot((EntityPlayer) entity)
                && (!this.teamsOnly.getValue() || TeamUtil.isSameTeam((EntityPlayer) entity))
                && (!this.ignoreWeapons.getValue() || !ItemUtil.hasRawUnbreakingEnchant());
    }
}
