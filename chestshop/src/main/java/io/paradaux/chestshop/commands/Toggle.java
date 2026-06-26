package io.paradaux.chestshop.commands;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.players.NameManager;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Level;

/**
 * @author KingFaris10
 */
@Command({"chestshop", "cs"})
@io.paradaux.hibernia.framework.commander.annotations.Permission(Permission.Node.NOTIFY_TOGGLE)
public class Toggle implements CommandHandler {

    @Route("notify")
    @io.paradaux.hibernia.framework.commander.annotations.Description("Toggle shop sale notifications on/off")
    public void toggle(@Sender Player player) {
        Account account = NameManager.getOrCreateAccount(player);
        account.setIgnoreMessages(!account.isIgnoringMessages());

        if (account.isIgnoringMessages()) {
            ChestShop.message().send(player, "chestshop.TOGGLE_MESSAGES_OFF");
        } else {
            ChestShop.message().send(player, "chestshop.TOGGLE_MESSAGES_ON");
        }

        try {
            NameManager.storeAccount(account);
        } catch (Exception e) {
            ChestShop.getBukkitLogger().log(Level.WARNING, "Error while updating account " + account + ":", e);
            ChestShop.message().send(player, "chestshop.ERROR_OCCURRED", "error", "Unable to store account data.");
        }
    }

    public static boolean isIgnoring(OfflinePlayer player) {
        return player != null && NameManager.getOrCreateAccount(player).isIgnoringMessages();
    }

    public static boolean isIgnoring(UUID playerId) {
        Account account = NameManager.getAccount(playerId);
        return account != null && account.isIgnoringMessages();
    }

    /**
     * @deprecated Use {@link #isIgnoring(UUID)}
     */
    @Deprecated
    public static boolean isIgnoring(String playerName) {
        return isIgnoring(Bukkit.getOfflinePlayer(playerName));
    }

}
