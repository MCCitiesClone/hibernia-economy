package io.paradaux.treasury.commands;

import com.google.inject.Inject;
import io.paradaux.hibernia.framework.commander.annotations.*;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.treasury.commands.resolvers.PayTarget;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.economy.TransferRequest;
import io.paradaux.treasury.services.AccountService;
import io.paradaux.treasury.services.LedgerService;
import io.paradaux.treasury.utils.Money;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;

@Command({"pay"})
@Permission("treasury.pay")
public class PayCommand implements CommandHandler {

    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final Message message;

    @Inject
    public PayCommand(AccountService accountService, LedgerService ledgerService, Message message) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.message = message;
    }

    @Route("")
    @Description("Show /pay help")
    public void root(@Sender Player sender) {
        message.send(sender, "treasury.help.pay");
    }

    @Route("help")
    @Description("Show /pay help")
    public void help(@Sender Player sender) {
        message.send(sender, "treasury.help.pay");
    }

    @Route("<target> <amount>")
    @Async
    @Description("Pay another player or government account")
    public void pay(@Sender Player sender,
                    @Arg("target") PayTarget target,
                    @Arg("amount") BigDecimal amount) {
        doPay(sender, target.name(), amount, null);
    }

    @Route("<target> <amount> <memo>")
    @Async
    @Description("Pay another player or government account with a memo")
    public void payMemo(@Sender Player sender,
                        @Arg("target") PayTarget target,
                        @Arg("amount") BigDecimal amount,
                        @GreedyArg("memo") String memo) {
        doPay(sender, target.name(), amount, memo);
    }

    private void doPay(Player sender, String targetName, BigDecimal amount, String memo) {
        BigDecimal normalized = Money.normalize(amount);
        if (normalized.signum() <= 0) {
            message.send(sender, "treasury.general.invalid-amount");
            return;
        }

        // Try to resolve as a known player first
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
        boolean knownPlayer = target != null && (target.hasPlayedBefore() || target.isOnline());

        if (knownPlayer) {
            payPlayer(sender, target, targetName, normalized, memo);
        } else {
            // Fall back to government account by display name. For anything
            // ambiguous (a player and a business/government sharing a name) use
            // /pay-account <type> <name> <amount> to disambiguate explicitly.
            Account govAccount = accountService.getGovernmentAccountByName(targetName);
            if (govAccount == null || govAccount.isArchived()) {
                message.send(sender, "treasury.general.unknown-player");
                return;
            }
            payGovernmentAccount(sender, govAccount, normalized, memo);
        }
    }

    private void payPlayer(Player sender, OfflinePlayer target, String targetName, BigDecimal normalized, String memo) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            message.send(sender, "treasury.pay.self");
            return;
        }

        int senderAccountId = accountService.getOrCreatePersonalAccountId(sender.getUniqueId());
        BigDecimal senderBalance = accountService.getBalanceReadOnly(senderAccountId);
        if (senderBalance.compareTo(normalized) < 0) {
            message.send(sender, "treasury.pay.insufficient",
                    "balance", accountService.formatAmount(senderBalance));
            return;
        }

        int targetAccountId = accountService.getOrCreatePersonalAccountId(target.getUniqueId());
        String memoLine = "Payment from " + sender.getName() + " to " + target.getName()
                + (memo != null ? ": " + memo : "");
        try {
            ledgerService.transfer(new TransferRequest(
                    senderAccountId,
                    targetAccountId,
                    normalized,
                    memoLine,
                    sender.getUniqueId(),
                    null,
                    null,
                    null
            ));
        } catch (IllegalStateException e) {
            BigDecimal currentBalance = accountService.getBalanceReadOnly(senderAccountId);
            message.send(sender, "treasury.pay.insufficient",
                    "balance", accountService.formatAmount(currentBalance));
            return;
        }

        String formattedAmount = accountService.formatAmount(normalized);
        message.send(sender, "treasury.pay.success",
                "target", target.getName(),
                "amount", formattedAmount);

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            message.send(onlineTarget, "treasury.pay.received",
                    "amount", formattedAmount,
                    "sender", sender.getName());
        }
    }

    private void payGovernmentAccount(Player sender, Account govAccount, BigDecimal normalized, String memo) {
        int senderAccountId = accountService.getOrCreatePersonalAccountId(sender.getUniqueId());
        BigDecimal senderBalance = accountService.getBalanceReadOnly(senderAccountId);
        if (senderBalance.compareTo(normalized) < 0) {
            message.send(sender, "treasury.pay.insufficient",
                    "balance", accountService.formatAmount(senderBalance));
            return;
        }

        String memoLine = "Payment from " + sender.getName() + " to " + govAccount.getDisplayName()
                + (memo != null ? ": " + memo : "");
        try {
            ledgerService.transfer(new TransferRequest(
                    senderAccountId,
                    govAccount.getAccountId(),
                    normalized,
                    memoLine,
                    sender.getUniqueId(),
                    null,
                    null,
                    null
            ));
        } catch (IllegalStateException e) {
            BigDecimal currentBalance = accountService.getBalanceReadOnly(senderAccountId);
            message.send(sender, "treasury.pay.insufficient",
                    "balance", accountService.formatAmount(currentBalance));
            return;
        }

        String formattedAmount = accountService.formatAmount(normalized);
        message.send(sender, "treasury.pay.success",
                "target", govAccount.getDisplayName(),
                "amount", formattedAmount);
    }
}
