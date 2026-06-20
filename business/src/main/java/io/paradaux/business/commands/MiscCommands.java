package io.paradaux.business.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.commander.annotations.*;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.business.exceptions.NoFirmAccountException;
import io.paradaux.business.model.Firm;
import io.paradaux.business.model.FirmPlayer;
import io.paradaux.business.model.RolePermission;
import io.paradaux.business.services.*;
import io.paradaux.business.utils.resolvers.FirmName;
import io.paradaux.business.utils.resolvers.OnlineFirmName;
import io.paradaux.treasury.api.TreasuryApi;
import io.paradaux.treasury.model.Page;
import io.paradaux.treasury.model.economy.TransactionEntry;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Singleton
@Command({"db", "democracybusiness", "business", "firm", "company"})
public class MiscCommands implements CommandHandler {

    private static final int TX_PAGE_SIZE = 10;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM/dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final FirmService firms;
    private final FirmStaffService staff;
    private final FirmRoleService roles;
    private final FirmTransactionService audit;
    private final FirmAreaShopService areas;
    private final TreasuryApi treasury;
    private final Message message;
    private final FirmNotificationService notifications;

    @Inject
    public MiscCommands(FirmService firms, FirmStaffService staff, FirmRoleService roles,
                        FirmTransactionService audit,
                        FirmAreaShopService areas, TreasuryApi treasury, Message message,
                        FirmNotificationService notifications) {
        this.firms = firms;
        this.staff = staff;
        this.roles = roles;
        this.audit = audit;
        this.areas = areas;
        this.treasury = treasury;
        this.message = message;
        this.notifications = notifications;
    }

    // ---- BALANCE ----------------------------------------------------------------

    @Route("balance <firm>")
    @Permission("business.finance")
    @Async
    @Description("Show the balance of a firm's treasury account")
    public void balance(@Sender Player sender, @Arg("firm") FirmName firmRef) {
        String firm = firmRef.value();
        Firm f = firms.getFirmByNameOrId(firm);
        if (f == null) {
            message.send(sender, "business.firm.not-found", "firm", firm);
            return;
        }

        String balance = audit.getFormattedAggregateBalance(f.getFirmId());
        message.send(sender, "business.finance.balance", "firm", f.getDisplayName(), "balance", balance);
    }

    // ---- DEPOSIT ----------------------------------------------------------------

    @Route("deposit <firm> <amount>")
    @Permission("business.finance")
    @Async
    @Description("Deposit from your personal account into a firm's treasury account")
    public void deposit(@Sender Player sender, @Arg("firm") FirmName firmRef, @Arg("amount") BigDecimal amount) {
        doDeposit(sender, firmRef, amount, null);
    }

    @Route("deposit <firm> <amount> <memo>")
    @Permission("business.finance")
    @Async
    @Description("Deposit into a firm's treasury with a memo recorded on the transaction")
    public void deposit(@Sender Player sender, @Arg("firm") FirmName firmRef, @Arg("amount") BigDecimal amount,
                        @GreedyArg("memo") String memo) {
        doDeposit(sender, firmRef, amount, memo);
    }

    private void doDeposit(Player sender, FirmName firmRef, BigDecimal amount, String memo) {
        String firm = firmRef.value();
        Firm f = firms.getFirmByNameOrId(firm);
        if (f == null) {
            message.send(sender, "business.firm.not-found", "firm", firm);
            return;
        }

        if (!canAccessFirmFinances(f, sender)) {
            message.send(sender, "business.general.no-permission");
            return;
        }

        try {
            audit.deposit(f.getFirmId(), sender.getUniqueId(), amount, memo);
            String formatted = treasury.formatAmount(amount);
            message.send(sender, "business.finance.deposit.success", "firm", f.getDisplayName(), "amount", formatted);
        } catch (IllegalArgumentException e) {
            message.send(sender, "business.finance.invalid-amount");
        } catch (IllegalStateException e) {
            message.send(sender, "business.finance.insufficient-personal");
        } catch (SecurityException e) {
            message.send(sender, "business.general.no-permission");
        }
    }

    // ---- WITHDRAW ---------------------------------------------------------------

    @Route("withdraw <firm> <amount>")
    @Permission("business.finance")
    @Async
    @Description("Withdraw from a firm's treasury account into your personal account")
    public void withdraw(@Sender Player sender, @Arg("firm") FirmName firmRef, @Arg("amount") BigDecimal amount) {
        String firm = firmRef.value();
        Firm f = firms.getFirmByNameOrId(firm);
        if (f == null) {
            message.send(sender, "business.firm.not-found", "firm", firm);
            return;
        }

        if (!canAccessFirmFinances(f, sender)) {
            message.send(sender, "business.general.no-permission");
            return;
        }

        try {
            audit.withdraw(f.getFirmId(), sender.getUniqueId(), amount);
            String formatted = treasury.formatAmount(amount);
            message.send(sender, "business.finance.withdraw.success", "firm", f.getDisplayName(), "amount", formatted);
        } catch (IllegalArgumentException e) {
            message.send(sender, "business.finance.invalid-amount");
        } catch (NoFirmAccountException e) {
            // Distinct from "insufficient funds" — a broken/missing default account
            // was the real reason proprietors "couldn't withdraw" (PAR-45).
            message.send(sender, "business.finance.no-account");
        } catch (IllegalStateException e) {
            message.send(sender, "business.finance.insufficient-business");
        } catch (SecurityException e) {
            message.send(sender, "business.finance.not-authorizer");
        }
    }

    // ---- PAY: PLAYER -> BUSINESS ------------------------------------------------

    @Route("pay into <firm> <amount>")
    @Permission("business.pay")
    @Async
    @Description("Pay money from your personal account into a business")
    public void payInto(@Sender Player sender, @Arg("firm") FirmName firmRef, @Arg("amount") BigDecimal amount) {
        String firm = firmRef.value();
        Firm f = firms.getFirmByNameOrId(firm);
        if (f == null) {
            message.send(sender, "business.firm.not-found", "firm", firm);
            return;
        }

        try {
            audit.payIntoFirm(f.getFirmId(), sender.getUniqueId(), amount);
            String formatted = treasury.formatAmount(amount);
            message.send(sender, "business.finance.pay.into.success", "firm", f.getDisplayName(), "amount", formatted);
            notifications.notifyFirmExcept(f.getFirmId(), sender.getUniqueId(), "business.notify.transfer.incoming",
                    "firm", f.getDisplayName(), "amount", formatted, "sender", sender.getName());
        } catch (IllegalArgumentException e) {
            message.send(sender, "business.finance.invalid-amount");
        } catch (IllegalStateException e) {
            message.send(sender, "business.finance.insufficient-personal");
        }
    }

    // ---- PAY: BUSINESS -> PLAYER ------------------------------------------------

    @Route("pay player <firm> <player> <amount>")
    @Permission("business.finance")
    @Async
    @Description("Pay money from a business to a player")
    public void payPlayer(@Sender Player sender, @Arg("firm") FirmName firmRef,
                          @Arg("player") FirmPlayer target, @Arg("amount") BigDecimal amount) {
        String firm = firmRef.value();
        Firm f = firms.getFirmByNameOrId(firm);
        if (f == null) {
            message.send(sender, "business.firm.not-found", "firm", firm);
            return;
        }

        if (!canAccessFirmFinances(f, sender)) {
            message.send(sender, "business.general.no-permission");
            return;
        }

        try {
            audit.payPlayer(f.getFirmId(), target.getUniqueId(), sender.getUniqueId(), amount);
            String formatted = treasury.formatAmount(amount);
            message.send(sender, "business.finance.pay.player.success",
                    "firm", f.getDisplayName(), "player", target.getCurrentName(), "amount", formatted);
        } catch (IllegalArgumentException e) {
            message.send(sender, "business.finance.invalid-amount");
        } catch (IllegalStateException e) {
            message.send(sender, "business.finance.insufficient-business");
        } catch (SecurityException e) {
            message.send(sender, "business.finance.not-authorizer");
        }
    }

    // ---- PAY: BUSINESS -> BUSINESS ----------------------------------------------

    @Route("pay business <firm> <target> <amount>")
    @Permission("business.finance")
    @Async
    @Description("Pay money from one business to another")
    public void payBusiness(@Sender Player sender, @Arg("firm") FirmName firmRef,
                            @Arg("target") OnlineFirmName targetRef, @Arg("amount") BigDecimal amount) {
        String firm = firmRef.value();
        String targetFirm = targetRef.value();
        Firm f = firms.getFirmByNameOrId(firm);
        if (f == null) {
            message.send(sender, "business.firm.not-found", "firm", firm);
            return;
        }

        Firm targetF = firms.getFirmByNameOrId(targetFirm);
        if (targetF == null) {
            message.send(sender, "business.firm.not-found", "firm", targetFirm);
            return;
        }

        if (targetF.getFirmId().equals(f.getFirmId())) {
            message.send(sender, "business.finance.pay.same-firm");
            return;
        }

        if (!canAccessFirmFinances(f, sender)) {
            message.send(sender, "business.general.no-permission");
            return;
        }

        try {
            audit.payFirm(f.getFirmId(), targetF.getFirmId(), sender.getUniqueId(), amount);
            String formatted = treasury.formatAmount(amount);
            message.send(sender, "business.finance.pay.business.success",
                    "firm", f.getDisplayName(), "target", targetF.getDisplayName(), "amount", formatted);
            notifications.notifyFirmExcept(targetF.getFirmId(), sender.getUniqueId(), "business.notify.transfer.incoming",
                    "firm", targetF.getDisplayName(), "amount", formatted, "sender", f.getDisplayName());
        } catch (IllegalArgumentException e) {
            message.send(sender, "business.finance.invalid-amount");
        } catch (IllegalStateException e) {
            message.send(sender, "business.finance.insufficient-business");
        } catch (SecurityException e) {
            message.send(sender, "business.finance.not-authorizer");
        }
    }

    // ---- TRANSACTIONS -----------------------------------------------------------

    @Route("transactions <firm>")
    @Permission("business.finance")
    @Async
    @Description("Show firm transaction history (page 1)")
    public void transactions(@Sender Player sender, @Arg("firm") FirmName firmRef) {
        transactions(sender, firmRef, 1);
    }

    @Route("transactions <firm> <page>")
    @Permission("business.finance")
    @Async
    @Description("Show firm transaction history")
    public void transactions(@Sender Player sender, @Arg("firm") FirmName firmRef, @Arg("page") Integer page) {
        String firm = firmRef.value();
        Firm f = firms.getFirmByNameOrId(firm);
        if (f == null) {
            message.send(sender, "business.firm.not-found", "firm", firm);
            return;
        }

        if (!canAccessFirmFinances(f, sender)) {
            message.send(sender, "business.general.no-permission");
            return;
        }

        Page<TransactionEntry> txPage = audit.getAggregateTransactions(f.getFirmId(), page, TX_PAGE_SIZE);

        if (txPage.items().isEmpty()) {
            message.send(sender, "business.finance.transactions.empty", "firm", f.getDisplayName());
            return;
        }

        message.send(sender, "business.finance.transactions.header",
                "firm", f.getDisplayName(), "page", txPage.pageNumber(), "totalPages", txPage.totalPages());

        for (TransactionEntry entry : txPage.items()) {
            String sign = entry.getAmount().signum() >= 0 ? "+" : "";
            String formatted = sign + treasury.formatAmount(entry.getAmount());
            String time = TIME_FMT.format(entry.getSettlementTime());
            String msg = entry.getMessage() != null ? entry.getMessage() : "";
            message.send(sender, "business.finance.transactions.line",
                    "time", time, "amount", formatted, "message", msg);
        }

        if (txPage.hasMore()) {
            message.send(sender, "business.finance.transactions.next-page",
                    "firm", f.getDisplayName(), "nextPage", txPage.pageNumber() + 1);
        }
    }

    private boolean canAccessFirmFinances(Firm f, Player player) {
        return firms.isProprietor(f.getFirmId(), player.getUniqueId())
                || staff.hasPermission(f.getFirmId(), player.getUniqueId(), RolePermission.ADMIN)
                || staff.hasPermission(f.getFirmId(), player.getUniqueId(), RolePermission.FINANCIAL);
    }
}
