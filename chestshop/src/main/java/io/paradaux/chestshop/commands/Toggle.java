package io.paradaux.chestshop.commands;

import com.google.inject.Inject;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.services.AccountService;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Description;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.i18n.Message;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * {@code /chestshop notify} — toggle the player's shop sale/stock notifications. The
 * "is this player ignoring notifications" query now lives on {@link AccountService}
 * (it's account state); this command just flips and persists the flag.
 *
 * @author KingFaris10
 */
@Command({"chestshop", "cs"})
@io.paradaux.hibernia.framework.commander.annotations.Permission("ChestShop.toggle")
public class Toggle implements CommandHandler {

    private final AccountService accounts;
    private final Message message;

    @Inject
    public Toggle(AccountService accounts, Message message) {
        this.accounts = accounts;
        this.message = message;
    }

    @Route("notify")
    @Description("Toggle shop sale notifications on/off")
    public void toggle(@Sender Player player) {
        Account account = accounts.getOrCreateAccount(player);
        account.setIgnoreMessages(!account.isIgnoringMessages());

        message.send(player, account.isIgnoringMessages()
                ? "chestshop.TOGGLE_MESSAGES_OFF" : "chestshop.TOGGLE_MESSAGES_ON");

        try {
            accounts.storeAccount(account);
        } catch (Exception e) {
            ChestShop.getBukkitLogger().log(Level.WARNING, "Error while updating account " + account + ":", e);
            message.send(player, "chestshop.ERROR_OCCURRED", "error", "Unable to store account data.");
        }
    }
}
