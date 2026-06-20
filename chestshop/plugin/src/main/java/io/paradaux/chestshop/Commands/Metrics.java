package io.paradaux.chestshop.Commands;

import io.paradaux.chestshop.Configuration.Messages;
import io.paradaux.chestshop.Listeners.Modules.MetricsModule;
import io.paradaux.chestshop.UUIDs.NameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * @author Acrobot
 */
public class Metrics implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages.METRICS.send(sender,
                "accounts", String.valueOf(NameManager.getAccountCount()),
                "totalTransactions", String.valueOf(MetricsModule.getTotalTransactions()),
                "buyTransactions", String.valueOf(MetricsModule.getBuyTransactions()),
                "sellTransactions", String.valueOf(MetricsModule.getSellTransactions()),
                "totalItems", String.valueOf(MetricsModule.getTotalItemsCount()),
                "boughtItems", String.valueOf(MetricsModule.getBoughtItemsCount()),
                "soldItems", String.valueOf(MetricsModule.getSoldItemsCount())
        );
        return true;
    }
}
