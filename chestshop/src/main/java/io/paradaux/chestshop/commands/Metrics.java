package io.paradaux.chestshop.commands;

import com.google.inject.Inject;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.listeners.modules.MetricsModule;
import io.paradaux.chestshop.services.AccountService;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Description;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.i18n.Message;
import org.bukkit.command.CommandSender;

/**
 * @author Acrobot
 */
@Command({"chestshop", "cs"})
@io.paradaux.hibernia.framework.commander.annotations.Permission(Permission.Node.ADMIN)
public class Metrics implements CommandHandler {

    private final AccountService accounts;
    private final Message message;

    @Inject
    public Metrics(AccountService accounts, Message message) {
        this.accounts = accounts;
        this.message = message;
    }

    @Route("metrics")
    @Description("Show aggregate ChestShop transaction metrics")
    public void metrics(@Sender CommandSender sender) {
        message.send(sender, "chestshop.METRICS", "prefix", "",
                "accounts", String.valueOf(accounts.getAccountCount()),
                "totalTransactions", String.valueOf(MetricsModule.getTotalTransactions()),
                "buyTransactions", String.valueOf(MetricsModule.getBuyTransactions()),
                "sellTransactions", String.valueOf(MetricsModule.getSellTransactions()),
                "totalItems", String.valueOf(MetricsModule.getTotalItemsCount()),
                "boughtItems", String.valueOf(MetricsModule.getBoughtItemsCount()),
                "soldItems", String.valueOf(MetricsModule.getSoldItemsCount())
        );
    }
}
