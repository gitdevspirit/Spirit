package myau.mixin;

import myau.Myau;
import myau.module.modules.LobbyIntel;
import myau.ui.intel.IntelManager;
import myau.ui.intel.IntelPlayer;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Adds LobbyIntel's cached stats to the vanilla tab list without replacing it. */
@Mixin(GuiPlayerTabOverlay.class)
public abstract class MixinGuiPlayerTabOverlay {

    @Shadow public abstract String getPlayerName(NetworkPlayerInfo networkPlayerInfoIn);

    @Redirect(
            method = "renderPlayerlist",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;getPlayerName(Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;"
            )
    )
    private String appendLobbyIntelStats(GuiPlayerTabOverlay overlay, NetworkPlayerInfo info) {
        String vanillaName = this.getPlayerName(info);

        LobbyIntel lobbyIntel = (LobbyIntel) Myau.moduleManager.getModule(LobbyIntel.class);
        if (lobbyIntel == null || !lobbyIntel.tabStats.getValue()) {
            return vanillaName;
        }

        IntelPlayer player = IntelManager.getInstance()
                .getPlayer(info.getGameProfile().getName());

        if (player == null || player.loading) {
            return vanillaName;
        }

        StringBuilder stats = new StringBuilder(" §8| §6✫")
                .append(player.star)
                .append(" §7FKDR §f")
                .append(String.format(java.util.Locale.ROOT, "%.1f", player.fkdr))
                .append(" §7WLR §f")
                .append(String.format(java.util.Locale.ROOT, "%.1f", player.wlr));

        String tag = player.getTagBadge();
        if (!tag.isEmpty()) {
            stats.append(" §d").append(tag);
        }

        return vanillaName + stats;
    }
}
