package io.paradaux.treasury.commands;

import com.google.inject.Inject;
import io.paradaux.hibernia.framework.commander.annotations.*;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.i18n.Message;
import lombok.extern.slf4j.Slf4j;
import io.paradaux.treasury.model.Page;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.economy.TransactionEntry;
import io.paradaux.treasury.services.AccountService;
import io.paradaux.treasury.services.DataExportService;
import io.paradaux.treasury.services.LedgerService;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Command({"transactions", "txns"})
@Permission("treasury.transactions")
public class TransactionsCommand implements CommandHandler {

    private static final int PAGE_SIZE = 10;
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault());

    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final DataExportService dataExportService;
    private final Message message;

    @Inject
    public TransactionsCommand(AccountService accountService,
                               LedgerService ledgerService,
                               DataExportService dataExportService,
                               Message message) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.dataExportService = dataExportService;
        this.message = message;
    }

    @Route("help")
    @Description("Show /transactions help")
    public void help(@Sender Player sender) {
        message.send(sender, "treasury.help.transactions");
    }

    @Route("")
    @Async
    @Description("View your transaction history")
    public void transactions(@Sender Player sender) {
        showTransactions(sender, 1);
    }

    @Route("<page>")
    @Async
    @Description("View your transaction history")
    public void transactionsPage(@Sender Player sender,
                                 @Arg("page") int page) {
        showTransactions(sender, page);
    }

    @Route("audit <target>")
    @Permission("treasury.transactions.audit")
    @Async
    @Description("View another player's transaction history (staff/DOC)")
    public void auditPlayer(@Sender Player sender, @Arg("target") OfflinePlayer target) {
        showPlayerAudit(sender, target, 1);
    }

    @Route("audit <target> <page>")
    @Permission("treasury.transactions.audit")
    @Async
    @Description("View another player's transaction history (staff/DOC)")
    public void auditPlayerPage(@Sender Player sender,
                                @Arg("target") OfflinePlayer target,
                                @Arg("page") int page) {
        showPlayerAudit(sender, target, page);
    }

    @Route("auditaccount <accountId>")
    @Permission("treasury.transactions.audit")
    @Async
    @Description("View any account's transaction history by id (staff/DOC)")
    public void auditAccount(@Sender Player sender, @Arg("accountId") int accountId) {
        showAccountAudit(sender, accountId, 1);
    }

    @Route("auditaccount <accountId> <page>")
    @Permission("treasury.transactions.audit")
    @Async
    @Description("View any account's transaction history by id (staff/DOC)")
    public void auditAccountPage(@Sender Player sender,
                                 @Arg("accountId") int accountId,
                                 @Arg("page") int page) {
        showAccountAudit(sender, accountId, page);
    }

    @Route("export")
    @Permission("treasury.transactions.export")
    @Async
    @Description("Export your transaction history as CSV")
    public void exportTransactions(@Sender Player sender) {
        int accountId = accountService.getOrCreatePersonalAccountId(sender.getUniqueId());
        String url = dataExportService.exportTransactionsFor(accountId);
        message.send(sender, "treasury.transactions.export.success", "url", url);
    }

    @Route("export <accountId>")
    @Permission("treasury.transactions.export")
    @Async
    @Description("Export transaction history for an account you are a member of")
    public void exportTransactionsForAccount(@Sender Player sender,
                                             @Arg("accountId") int accountId) {
        if (!accountService.hasAccountByAccountId(accountId)) {
            message.send(sender, "treasury.transactions.export.not-found");
            return;
        }
        if (!accountService.canAccessAccount(sender.getUniqueId(), accountId)) {
            message.send(sender, "treasury.transactions.export.no-access");
            return;
        }
        String url = dataExportService.exportTransactionsFor(accountId);
        message.send(sender, "treasury.transactions.export.success", "url", url);
    }

    /** Hard cap so a malicious /transactions <huge> can't push MariaDB into a giant OFFSET scan. */
    private static final int MAX_PAGE = 10_000;

    private static int clampPage(int page) {
        if (page < 1) return 1;
        return Math.min(page, MAX_PAGE);
    }

    private void showTransactions(Player sender, int page) {
        page = clampPage(page);
        int offset = (page - 1) * PAGE_SIZE;

        int accountId = accountService.getOrCreatePersonalAccountId(sender.getUniqueId());
        Page<TransactionEntry> result = ledgerService.getTransactionHistory(accountId, offset, PAGE_SIZE);

        if (result.items().isEmpty()) {
            message.send(sender, "treasury.transactions.empty");
            return;
        }

        message.send(sender, "treasury.transactions.header",
                "page", String.valueOf(result.pageNumber()),
                "pages", String.valueOf(result.totalPages()));

        sendEntries(sender, result);

        if (result.hasMore()) {
            message.send(sender, "treasury.transactions.footer",
                    "next", String.valueOf(page + 1));
        }
    }

    /**
     * Renders another player's history for an auditor. Requires the target to
     * have actually played (the framework resolver returns a synthetic
     * OfflinePlayer for unknown names) and to own a PERSONAL account — never
     * creates one.
     */
    private void showPlayerAudit(Player viewer, OfflinePlayer target, int page) {
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            message.send(viewer, "treasury.general.unknown-player");
            return;
        }
        Integer accountId = accountService.findPersonalAccountId(target.getUniqueId());
        if (accountId == null) {
            message.send(viewer, "treasury.transactions.audit.no-account", "target", target.getName());
            return;
        }
        if (!canAudit(viewer, accountId)) {
            message.send(viewer, "treasury.transactions.audit.no-access");
            return;
        }
        renderAudit(viewer, accountId, page, target.getName(), "/transactions audit " + target.getName());
    }

    /** Renders any account's history by id for an auditor (covers business/government accounts). */
    private void showAccountAudit(Player viewer, int accountId, int page) {
        if (!accountService.hasAccountByAccountId(accountId)) {
            message.send(viewer, "treasury.transactions.audit.not-found");
            return;
        }
        if (!canAudit(viewer, accountId)) {
            message.send(viewer, "treasury.transactions.audit.no-access");
            return;
        }
        Account account = accountService.getAccountById(accountId);
        String label = account != null && account.getDisplayName() != null
                ? account.getDisplayName() : ("#" + accountId);
        renderAudit(viewer, accountId, page, label, "/transactions auditaccount " + accountId);
    }

    /**
     * Defence-in-depth for the audit routes (ADT-18). The {@code transactions.audit}
     * node alone (op by default, but an operator could grant it to a non-staff
     * rank) must not expose any account's full ledger. A genuine admin — one
     * holding {@code treasury.admin.inspect}, the same node that already dumps any
     * account via {@code /treasury admin info} — may audit any account; everyone
     * else is limited to accounts they can access, mirroring the export route.
     */
    private boolean canAudit(Player viewer, int accountId) {
        return viewer.hasPermission("treasury.admin.inspect")
                || accountService.canAccessAccount(viewer.getUniqueId(), accountId);
    }

    private void renderAudit(Player viewer, int accountId, int page, String subjectLabel, String navBase) {
        page = clampPage(page);
        int offset = (page - 1) * PAGE_SIZE;

        Page<TransactionEntry> result = ledgerService.getTransactionHistory(accountId, offset, PAGE_SIZE);

        // Leave a server-log trail of who audited whom, mirroring the explorer's auditView.
        log.info("{} audited transactions of {} (account #{}, page {})",
                viewer.getName(), subjectLabel, accountId, page);

        if (result.items().isEmpty()) {
            message.send(viewer, "treasury.transactions.audit.empty", "target", subjectLabel);
            return;
        }

        message.send(viewer, "treasury.transactions.audit.header",
                "target", subjectLabel,
                "page", String.valueOf(result.pageNumber()),
                "pages", String.valueOf(result.totalPages()));

        sendEntries(viewer, result);

        if (result.hasMore()) {
            message.send(viewer, "treasury.transactions.audit.footer",
                    "command", navBase + " " + (page + 1));
        }
    }

    private void sendEntries(Player viewer, Page<TransactionEntry> result) {
        for (TransactionEntry entry : result.items()) {
            String formattedAmount = accountService.formatAmount(entry.getAmount().abs());
            String sign = entry.getAmount().signum() >= 0 ? "+" : "-";
            String colorTag = entry.getAmount().signum() >= 0 ? "green" : "red";
            String coloredAmount = "<" + colorTag + ">" + sign + formattedAmount + "</" + colorTag + ">";
            String memo = entry.getMemo() != null ? entry.getMemo() : entry.getMessage();
            if (memo == null) memo = "—";
            memo = sanitize(memo);
            String time = entry.getSettlementTime() != null
                    ? TIME_FMT.format(entry.getSettlementTime()) : "—";

            message.send(viewer, "treasury.transactions.entry",
                    "txn", String.valueOf(entry.getTxnId()),
                    "amount", coloredAmount,
                    "memo", memo,
                    "time", time);
        }
    }

    /** Escapes MiniMessage tag syntax in user-supplied strings to prevent injection. */
    private static String sanitize(String input) {
        return input.replace("<", "\\<");
    }
}
