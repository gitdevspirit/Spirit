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

        StringBuilder stats = new StringBuilder(" §8|");

        if (lobbyIntel.tabShowStar.getValue()) {
            String starCode = myau.ui.intel.IntelColors.nearestCode(
                    myau.ui.intel.IntelColors.getPrestigeColor(player.star));
            stats.append(" ").append(starCode).append("\u272A").append(player.star);
        }

        if (lobbyIntel.tabShowFkdr.getValue()) {
            String fkdrCode = myau.ui.intel.IntelColors.nearestCode(
                    myau.ui.intel.IntelColors.getStatColor(player.fkdr, 3, 6));
            stats.append(" §7FKDR ").append(fkdrCode)
                    .append(String.format(java.util.Locale.ROOT, "%.1f", player.fkdr));
        }

        if (lobbyIntel.tabShowWlr.getValue()) {
            String wlrCode = myau.ui.intel.IntelColors.nearestCode(
                    myau.ui.intel.IntelColors.getStatColor(player.wlr, 2, 4));
            stats.append(" §7WLR ").append(wlrCode)
                    .append(String.format(java.util.Locale.ROOT, "%.1f", player.wlr));
        }

        if (lobbyIntel.tabShowBblr.getValue()) {
            double bblr = player.bedsLost == 0
                    ? player.bedsBroken
                    : (double) player.bedsBroken / player.bedsLost;
            stats.append(" §7BBLR §f").append(String.format(java.util.Locale.ROOT, "%.1f", bblr));
        }

        if (lobbyIntel.tabShowFinalKills.getValue()) {
            stats.append(" §7FK §f").append(player.finalKills);
        }

        if (lobbyIntel.tabShowFinalDeaths.getValue()) {
            stats.append(" §7FD §f").append(player.finalDeaths);
        }

        if (lobbyIntel.tabShowKills.getValue()) {
            stats.append(" §7K §f").append(player.kills);
        }

        if (lobbyIntel.tabShowDeaths.getValue()) {
            stats.append(" §7D §f").append(player.deaths);
        }

        if (lobbyIntel.tabShowBedsBroken.getValue()) {
            stats.append(" §7BB §f").append(player.bedsBroken);
        }

        if (lobbyIntel.tabShowBedsLost.getValue()) {
            stats.append(" §7BL §f").append(player.bedsLost);
        }

        if (lobbyIntel.tabShowWinstreak.getValue()) {
            stats.append(" §7WS §f").append(player.winstreak);
        }

        if (lobbyIntel.tabShowWins.getValue()) {
            stats.append(" §7W §f").append(player.wins);
        }

        if (lobbyIntel.tabShowLosses.getValue()) {
            stats.append(" §7L §f").append(player.losses);
        }

        String tag = player.getTagBadge();
        if (!tag.isEmpty() && lobbyIntel.tabShowTag.getValue()) {
            // Closet cheater specifically renders gold in the tab list;
            // everything else uses the nearest code to its usual color.
            String tagCode = tag.equals("C") ? "§6"
                    : myau.ui.intel.IntelColors.nearestCode(player.getTagColor());
            stats.append(" ").append(tagCode).append(tag);
        }

        if (stats.length() <= " §8|".length()) {
            return vanillaName;
        }

        return vanillaName + stats;
    }
}
