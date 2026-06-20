package io.paradaux.treasury.commands;

import com.google.inject.Inject;
import io.paradaux.hibernia.framework.commander.annotations.*;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.treasury.services.AccountService;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;

@Command({"bal", "balance", "money"})
@Permission("treasury.balance")
public class BalanceCommand implements CommandHandler {

    private final AccountService accountService;
    private final Message message;

    @Inject
    public BalanceCommand(AccountService accountService, Message message) {
        this.accountService = accountService;
        this.message = message;
    }

    @Route("help")
    @Description("Show /balance help")
    public void help(@Sender CommandSender sender) {
        message.send(sender, "treasury.help.balance");
    }

    @Route("")
    @Async
    @Description("Check your balance")
    public void balance(@Sender Player sender) {
        int accountId = accountService.getOrCreatePersonalAccountId(sender.getUniqueId());
        BigDecimal balance = accountService.getBalanceReadOnly(accountId);
        message.send(sender, "treasury.balance.self",
                "balance", accountService.formatAmount(balance));
    }

    @Route("<target>")
    @Permission("treasury.balance.others")
    @Async
    @Description("Check another player's balance")
    public void balanceOther(@Sender CommandSender sender,
                             @Arg("target") OfflinePlayer target) {
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            message.send(sender, "treasury.general.unknown-player");
            return;
        }
        // Use the read-only path: don't auto-create an account just because someone
        // queried this UUID. Bukkit returns a phantom UUID for unknown name lookups,
        // and minting a starting-balance for that phantom would be a faucet exploit.
        BigDecimal balance = accountService.getBalanceByOwnerUuid(target.getUniqueId());
        message.send(sender, "treasury.balance.other",
                "player", target.getName(),
                "balance", accountService.formatAmount(balance));
    }
}
