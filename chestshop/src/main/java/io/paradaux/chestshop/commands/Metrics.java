package io.paradaux.chestshop.commands;

import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.configuration.Messages;
import io.paradaux.chestshop.listeners.modules.MetricsModule;
import io.paradaux.chestshop.players.NameManager;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Description;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import org.bukkit.command.CommandSender;

/**
 * @author Acrobot
 */
@Command({"chestshop", "cs"})
@io.paradaux.hibernia.framework.commander.annotations.Permission(Permission.Node.ADMIN)
public class Metrics implements CommandHandler {

    @Route("metrics")
    @Description("Show aggregate ChestShop transaction metrics")
    public void metrics(@Sender CommandSender sender) {
        Messages.METRICS.send(sender,
                "accounts", String.valueOf(NameManager.getAccountCount()),
                "totalTransactions", String.valueOf(MetricsModule.getTotalTransactions()),
                "buyTransactions", String.valueOf(MetricsModule.getBuyTransactions()),
                "sellTransactions", String.valueOf(MetricsModule.getSellTransactions()),
                "totalItems", String.valueOf(MetricsModule.getTotalItemsCount()),
                "boughtItems", String.valueOf(MetricsModule.getBoughtItemsCount()),
                "soldItems", String.valueOf(MetricsModule.getSoldItemsCount())
        );
    }
}
