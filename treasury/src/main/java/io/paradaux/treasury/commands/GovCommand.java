package io.paradaux.treasury.commands;

import com.google.inject.Inject;
import io.paradaux.hibernia.framework.commander.annotations.*;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.treasury.api.exceptions.GovAccountNotFoundException;
import io.paradaux.treasury.api.exceptions.PrimitiveAccountException;
import io.paradaux.treasury.model.Page;
import io.paradaux.treasury.model.economy.*;
import io.paradaux.treasury.services.AccountService;
import io.paradaux.treasury.services.GovService;
import io.paradaux.treasury.services.LedgerService;
import io.paradaux.treasury.services.MembershipService;
import io.paradaux.treasury.utils.GovDedupKeys;
import io.paradaux.treasury.utils.Money;
import io.paradaux.treasury.utils.TreasuryConstants;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Command({"government", "gov"})
@Permission("treasury.gov")
public class GovCommand implements CommandHandler {

    private static final int PAGE_SIZE = 10;
    /** Hard cap so /gov account history <name> <huge> can't push MariaDB into a giant OFFSET scan. */
    private static final int MAX_PAGE = 10_000;
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault());

    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final MembershipService membershipService;
    private final GovService govService;
    private final Message message;

    @Inject
    public GovCommand(AccountService accountService,
                      LedgerService ledgerService,
                      MembershipService membershipService,
                      GovService govService,
                      Message message) {
        this.accountService   = accountService;
        this.ledgerService    = ledgerService;
        this.membershipService = membershipService;
        this.govService       = govService;
        this.message          = message;
    }

    // =====================================================================
    // HELP
    // =====================================================================

    @Route("")
    @Description("Show /government help")
    public void root(@Sender CommandSender sender) {
        message.send(sender, "treasury.help.gov");
    }

    @Route("help")
    @Description("Show /government help")
    public void help(@Sender CommandSender sender) {
        message.send(sender, "treasury.help.gov");
    }

    @Route("help account")
    @Description("Show government account lifecycle help")
    public void helpAccount(@Sender CommandSender sender) {
        message.send(sender, "treasury.help.gov.account");
    }

    @Route("help member")
    @Description("Show government member-management help")
    public void helpMember(@Sender CommandSender sender) {
        message.send(sender, "treasury.help.gov.member");
    }

    @Route("help auth")
    @Description("Show government authorizer-management help")
    public void helpAuth(@Sender CommandSender sender) {
        message.send(sender, "treasury.help.gov.auth");
    }

    @Route("help transfer")
    @Description("Show government transfer / pay / payout help")
    public void helpTransfer(@Sender CommandSender sender) {
        message.send(sender, "treasury.help.gov.transfer");
    }

    // =====================================================================
    // ACCOUNT MANAGEMENT
    // =====================================================================

    @Route("account create <name>")
    @Async
    @Description("Create a new government department account")
    public void accountCreate(@Sender CommandSender sender, @Arg("name") String name) {
        if (!isAllowed(sender,"treasury.gov.account.create")) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        try {
            Account account = govService.createDepartmentAccount(name, actorOf(sender));
            message.send(sender, "treasury.gov.account.created", "name", account.getDisplayName());
        } catch (IllegalArgumentException e) {
            message.send(sender, "treasury.gov.account.already-exists", "name", name);
        }
    }

    @Route("account archive <name>")
    @Async
    @Description("Archive a government department account")
    public void accountArchive(@Sender CommandSender sender, @Arg("name") String name) {
        if (!isAllowed(sender,"treasury.gov.admin")) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        try {
            govService.archiveDepartmentAccount(name, actorOf(sender));
            message.send(sender, "treasury.gov.account.archived", "name", name);
        } catch (PrimitiveAccountException e) {
            message.send(sender, "treasury.gov.account.primitive-archive");
        } catch (GovAccountNotFoundException e) {
            message.send(sender, "treasury.gov.not-found", "account", name);
        }
    }

    @Route("account list")
    @Async
    @Description("List all government accounts")
    public void accountList(@Sender CommandSender sender) {
        if (!isAllowed(sender,"treasury.gov.account.list")) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        List<Account> accounts = govService.listGovernmentAccounts();
        if (accounts.isEmpty()) {
            message.send(sender, "treasury.gov.account.list.empty");
            return;
        }
        message.send(sender, "treasury.gov.account.list.header");
        for (Account acc : accounts) {
            BigDecimal balance = accountService.getBalanceReadOnly(acc.getAccountId());
            message.send(sender, "treasury.gov.account.list.entry",
                    "id", String.valueOf(acc.getAccountId()),
                    "name", acc.getDisplayName(),
                    "balance", accountService.formatAmount(balance));
        }
    }

    @Route("account balance <name>")
    @Async
    @Description("View the balance of a government account")
    public void accountBalance(@Sender CommandSender sender, @Arg("name") String name) {
        if (!isAllowed(sender,"treasury.gov.account.view")) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        Account account = resolveGovAccount(name, sender);
        if (account == null) return;
        if (!hasMemberAccess(sender, account)) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        BigDecimal balance = accountService.getBalanceReadOnly(account.getAccountId());
        message.send(sender, "treasury.gov.account.balance",
                "name", account.getDisplayName(),
                "balance", accountService.formatAmount(balance));
    }

    @Route("account history <name>")
    @Async
    @Description("View the transaction history of a government account")
    public void accountHistory(@Sender CommandSender sender, @Arg("name") String name) {
        showHistory(sender, name, 1);
    }

    @Route("account history <name> <page>")
    @Async
    @Description("View the transaction history of a government account (paged)")
    public void accountHistoryPage(@Sender CommandSender sender,
                                   @Arg("name") String name,
                                   @Arg("page") int page) {
        showHistory(sender, name, page);
    }

    @Route("account transfer <from> <to> <amount>")
    @Async
    @Description("Transfer funds between government accounts")
    public void accountTransfer(@Sender CommandSender sender,
                                @Arg("from") String fromName,
                                @Arg("to") String toName,
                                @Arg("amount") BigDecimal amount) {
        doTransfer(sender, fromName, toName, amount, null);
    }

    @Route("account transfer <from> <to> <amount> <reason>")
    @Async
    @Description("Transfer funds between government accounts with a reason")
    public void accountTransferReason(@Sender CommandSender sender,
                                      @Arg("from") String fromName,
                                      @Arg("to") String toName,
                                      @Arg("amount") BigDecimal amount,
                                      @GreedyArg("reason") String reason) {
        doTransfer(sender, fromName, toName, amount, reason);
    }

    @Route("pay <from> <to> <amount>")
    @Async
    @Description("Transfer funds between government accounts (alias)")
    public void pay(@Sender CommandSender sender,
                    @Arg("from") String fromName,
                    @Arg("to") String toName,
                    @Arg("amount") BigDecimal amount) {
        doTransfer(sender, fromName, toName, amount, null);
    }

    @Route("pay <from> <to> <amount> <reason>")
    @Async
    @Description("Transfer funds between government accounts with a reason (alias)")
    public void payReason(@Sender CommandSender sender,
                          @Arg("from") String fromName,
                          @Arg("to") String toName,
                          @Arg("amount") BigDecimal amount,
                          @GreedyArg("reason") String reason) {
        doTransfer(sender, fromName, toName, amount, reason);
    }

    @Route("payout <from> <to> <amount>")
    @Async
    @Description("Pay a player or business account from a government account")
    public void payout(@Sender CommandSender sender,
                       @Arg("from") String fromName,
                       @Arg("to") String toName,
                       @Arg("amount") BigDecimal amount) {
        doExternalPayout(sender, fromName, toName, amount, null);
    }

    @Route("payout <from> <to> <amount> <reason>")
    @Async
    @Description("Pay a player or business account from a government account with a reason")
    public void payoutReason(@Sender CommandSender sender,
                             @Arg("from") String fromName,
                             @Arg("to") String toName,
                             @Arg("amount") BigDecimal amount,
                             @GreedyArg("reason") String reason) {
        doExternalPayout(sender, fromName, toName, amount, reason);
    }

    // =====================================================================
    // MEMBER MANAGEMENT
    // =====================================================================

    @Route("account member add <account> <player>")
    @Async
    @Description("Add a player as a member of a government account")
    public void memberAdd(@Sender CommandSender sender,
                          @Arg("account") String accountName,
                          @Arg("player") OfflinePlayer target) {
        if (!isAllowed(sender,"treasury.gov.account.manage")) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        Account account = resolveGovAccount(accountName, sender);
        if (account == null) return;
        if (!hasAuthorizerAccess(sender, account)) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            message.send(sender, "treasury.general.unknown-player");
            return;
        }
        membershipService.addMember(account.getAccountId(), target.getUniqueId(), actorOf(sender));
        message.send(sender, "treasury.gov.member.added",
                "player", target.getName(), "account", account.getDisplayName());
    }

    @Route("account member remove <account> <player>")
    @Async
    @Description("Remove a player's membership from a government account")
    public void memberRemove(@Sender CommandSender sender,
                             @Arg("account") String accountName,
                             @Arg("player") OfflinePlayer target) {
        if (!isAllowed(sender,"treasury.gov.account.manage")) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        Account account = resolveGovAccount(accountName, sender);
        if (account == null) return;
        if (!hasAuthorizerAccess(sender, account)) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            message.send(sender, "treasury.general.unknown-player");
            return;
        }
        membershipService.removeMember(account.getAccountId(), target.getUniqueId());
        message.send(sender, "treasury.gov.member.removed",
                "player", target.getName(), "account", account.getDisplayName());
    }

    @Route("account member addgroup <account> <group>")
    @Async
    @Description("Add an LP group as a member of a government account")
    public void memberAddGroup(@Sender CommandSender sender,
                               @Arg("account") String accountName,
                               @Arg("group") String group) {
        if (!isAllowed(sender,"treasury.gov.account.manage")) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        Account account = resolveGovAccount(accountName, sender);
        if (account == null) return;
        if (!hasAuthorizerAccess(sender, account)) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        membershipService.addGroupMember(account.getAccountId(), group, actorOf(sender));
        message.send(sender, "treasury.gov.member.group-added",
                "group", group, "account", account.getDisplayName());
    }

    @Route("account member removegroup <account> <group>")
    @Async
    @Description("Remove an LP group's membership from a government account")
    public void memberRemoveGroup(@Sender CommandSender sender,
                                  @Arg("account") String accountName,
                                  @Arg("group") String group) {
        if (!isAllowed(sender,"treasury.gov.account.manage")) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        Account account = resolveGovAccount(accountName, sender);
        if (account == null) return;
        if (!hasAuthorizerAccess(sender, account)) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        membershipService.removeGroupMember(account.getAccountId(), group);
        message.send(sender, "treasury.gov.member.group-removed",
                "group", group, "account", account.getDisplayName());
    }

    @Route("account member list <account>")
    @Async
    @Description("List members of a government account")
    public void memberList(@Sender CommandSender sender, @Arg("account") String accountName) {
        if (!isAllowed(sender,"treasury.gov.account.view")) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        Account account = resolveGovAccount(accountName, sender);
        if (account == null) return;
        if (!hasMemberAccess(sender, account)) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }

        List<AccountMember> uuidMembers = membershipService.getMembers(account.getAccountId());
        List<String> groupMembers = membershipService.getGroupMembers(account.getAccountId());

        if (uuidMembers.isEmpty() && groupMembers.isEmpty()) {
            message.send(sender, "treasury.gov.member.list.empty");
            return;
        }

        message.send(sender, "treasury.gov.member.list.header", "account", account.getDisplayName());
        for (AccountMember m : uuidMembers) {
            String name = resolvePlayerName(m.getMemberUuid());
            message.send(sender, "treasury.gov.member.list.uuid", "player", name);
        }
        for (String g : groupMembers) {
            message.send(sender, "treasury.gov.member.list.group", "group", g);
        }
    }

    // =====================================================================
    // AUTHORIZER MANAGEMENT
    // =====================================================================

    @Route("account auth add <account> <player>")
    @Async
    @Description("Add a player as an authorizer of a government account")
    public void authAdd(@Sender CommandSender sender,
                        @Arg("account") String accountName,
                        @Arg("player") OfflinePlayer target) {
        if (!isAllowed(sender,"treasury.gov.admin")) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        Account account = resolveGovAccount(accountName, sender);
        if (account == null) return;
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            message.send(sender, "treasury.general.unknown-player");
            return;
        }
        // Authorizers must be explicit members first. The earlier
        // implementation auto-promoted to member silently, which made the
        // `treasury.gov.auth.not-member` i18n key unreachable and meant
        // operators couldn't tell at a glance whether a player had been
        // intentionally added as a member or implicitly granted via auth.
        boolean isMember = membershipService.getMembers(account.getAccountId()).stream()
                .anyMatch(m -> m.getMemberUuid().equals(target.getUniqueId()));
        if (!isMember) {
            message.send(sender, "treasury.gov.auth.not-member", "player", target.getName());
            return;
        }
        try {
            membershipService.addAuthorizer(account.getAccountId(), target.getUniqueId(), actorOf(sender));
        } catch (IllegalStateException e) {
            message.send(sender, "treasury.gov.auth.not-member", "player", target.getName());
            return;
        }
        message.send(sender, "treasury.gov.auth.added",
                "player", target.getName(), "account", account.getDisplayName());
    }

    @Route("account auth remove <account> <player>")
    @Async
    @Description("Remove a player's authorizer status from a government account")
    public void authRemove(@Sender CommandSender sender,
                           @Arg("account") String accountName,
                           @Arg("player") OfflinePlayer target) {
        if (!isAllowed(sender,"treasury.gov.admin")) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        Account account = resolveGovAccount(accountName, sender);
        if (account == null) return;
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            message.send(sender, "treasury.general.unknown-player");
            return;
        }
        membershipService.removeAuthorizer(account.getAccountId(), target.getUniqueId());
        message.send(sender, "treasury.gov.auth.removed",
                "player", target.getName(), "account", account.getDisplayName());
    }

    @Route("account auth addgroup <account> <group>")
    @Async
    @Description("Add an LP group as an authorizer of a government account")
    public void authAddGroup(@Sender CommandSender sender,
                             @Arg("account") String accountName,
                             @Arg("group") String group) {
        if (!isAllowed(sender,"treasury.gov.admin")) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        Account account = resolveGovAccount(accountName, sender);
        if (account == null) return;
        // Symmetric with the per-UUID authAdd path: a group must be an
        // explicit group MEMBER before it can be made a group AUTHORIZER.
        // The earlier implementation silently auto-promoted, which made the
        // `treasury.gov.auth.group-not-member` i18n key unreachable and
        // hid mis-typed group names behind a successful-looking message.
        List<String> groupMembers = membershipService.getGroupMembers(account.getAccountId());
        if (!groupMembers.contains(group)) {
            message.send(sender, "treasury.gov.auth.group-not-member", "group", group);
            return;
        }
        try {
            membershipService.addGroupAuthorizer(account.getAccountId(), group, actorOf(sender));
        } catch (IllegalStateException e) {
            message.send(sender, "treasury.gov.auth.group-not-member", "group", group);
            return;
        }
        message.send(sender, "treasury.gov.auth.group-added",
                "group", group, "account", account.getDisplayName());
    }

    @Route("account auth removegroup <account> <group>")
    @Async
    @Description("Remove an LP group's authorizer status from a government account")
    public void authRemoveGroup(@Sender CommandSender sender,
                                @Arg("account") String accountName,
                                @Arg("group") String group) {
        if (!isAllowed(sender,"treasury.gov.admin")) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        Account account = resolveGovAccount(accountName, sender);
        if (account == null) return;
        membershipService.removeGroupAuthorizer(account.getAccountId(), group);
        message.send(sender, "treasury.gov.auth.group-removed",
                "group", group, "account", account.getDisplayName());
    }

    @Route("account auth list <account>")
    @Async
    @Description("List authorizers of a government account")
    public void authList(@Sender CommandSender sender, @Arg("account") String accountName) {
        if (!isAllowed(sender,"treasury.gov.account.view")) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        Account account = resolveGovAccount(accountName, sender);
        if (account == null) return;
        if (!hasMemberAccess(sender, account)) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }

        List<AccountMember> uuidAuths = membershipService.getAuthorizers(account.getAccountId());
        List<String> groupAuths = membershipService.getGroupAuthorizers(account.getAccountId());

        if (uuidAuths.isEmpty() && groupAuths.isEmpty()) {
            message.send(sender, "treasury.gov.auth.list.empty");
            return;
        }

        message.send(sender, "treasury.gov.auth.list.header", "account", account.getDisplayName());
        for (AccountMember a : uuidAuths) {
            String name = resolvePlayerName(a.getMemberUuid());
            message.send(sender, "treasury.gov.auth.list.uuid", "player", name);
        }
        for (String g : groupAuths) {
            message.send(sender, "treasury.gov.auth.list.group", "group", g);
        }
    }

    // =====================================================================
    // Private helpers
    // =====================================================================

    private void showHistory(CommandSender sender, String name, int page) {
        if (!isAllowed(sender,"treasury.gov.account.view")) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        Account account = resolveGovAccount(name, sender);
        if (account == null) return;
        if (!hasMemberAccess(sender, account)) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }
        if (page < 1) page = 1;
        if (page > MAX_PAGE) page = MAX_PAGE;
        int offset = (page - 1) * PAGE_SIZE;
        Page<TransactionEntry> result = ledgerService.getTransactionHistory(account.getAccountId(), offset, PAGE_SIZE);

        if (result.items().isEmpty()) {
            message.send(sender, "treasury.gov.account.history.empty");
            return;
        }

        message.send(sender, "treasury.gov.account.history.header",
                "name", account.getDisplayName(),
                "page", String.valueOf(result.pageNumber()),
                "pages", String.valueOf(result.totalPages()));

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

            message.send(sender, "treasury.transactions.entry",
                    "txn", String.valueOf(entry.getTxnId()),
                    "amount", coloredAmount,
                    "memo", memo,
                    "time", time);
        }

        if (result.hasMore()) {
            message.send(sender, "treasury.transactions.footer",
                    "next", String.valueOf(page + 1));
        }
    }

    private void doTransfer(CommandSender sender, String fromName, String toName, BigDecimal amount, String reason) {
        // Access is decided per-source-account by canTransferFrom (member /
        // authorizer / global node / admin) once the account is resolved below.
        BigDecimal normalized = Money.normalize(amount);
        if (normalized.signum() <= 0) {
            message.send(sender, "treasury.general.invalid-amount");
            return;
        }

        Account from = resolveGovAccount(fromName, sender);
        if (from == null) return;
        Account to = resolveGovAccount(toName, sender);
        if (to == null) return;

        // Reject self-transfer outright — net-zero balance change but still
        // pollutes the ledger. Account.getAccountId() returns boxed Integer;
        // comparing with `==` is reference equality and silently misfires for
        // IDs outside the JVM's -128..127 cache.
        if (from.getAccountId().intValue() == to.getAccountId().intValue()) {
            message.send(sender, "treasury.gov.account.transfer.same-account",
                    "name", from.getDisplayName());
            return;
        }

        if (!canTransferFrom(sender, from)) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }

        if (from.getAccountType() != AccountType.GOVERNMENT || to.getAccountType() != AccountType.GOVERNMENT) {
            message.send(sender, "treasury.gov.account.not-government",
                    "name", from.getAccountType() != AccountType.GOVERNMENT ? fromName : toName);
            return;
        }

        String memo = reason != null
                ? "Gov transfer: " + reason
                : "Gov transfer from " + from.getDisplayName() + " to " + to.getDisplayName();

        byte[] dedupKey = GovDedupKeys.transfer(
                actorOf(sender), from.getAccountId(), to.getAccountId(), normalized, Instant.now());

        try {
            TransferRequest req = new TransferRequest(
                    from.getAccountId(),
                    to.getAccountId(),
                    normalized,
                    memo,
                    actorOf(sender),
                    null,
                    TreasuryConstants.TREASURY_PLUGIN_NAME,
                    dedupKey);
            // Console bypasses the requires_authorization gate as well (it already
            // skipped the membership check above); players go through the normal path.
            if (sender instanceof Player) {
                ledgerService.transfer(req);
            } else {
                ledgerService.adminTransfer(req);
            }
        } catch (IllegalStateException e) {
            BigDecimal balance = accountService.getBalanceReadOnly(from.getAccountId());
            message.send(sender, "treasury.gov.account.transfer.insufficient",
                    "from", from.getDisplayName(),
                    "balance", accountService.formatAmount(balance));
            return;
        }

        message.send(sender, "treasury.gov.account.transfer.success",
                "from", from.getDisplayName(),
                "to", to.getDisplayName(),
                "amount", accountService.formatAmount(normalized));
    }

    private void doExternalPayout(CommandSender sender, String fromName, String toName, BigDecimal amount, String reason) {
        // Access is decided per-source-account by canTransferFrom (member /
        // authorizer / global node / admin) once the account is resolved below.
        BigDecimal normalized = Money.normalize(amount);
        if (normalized.signum() <= 0) {
            message.send(sender, "treasury.general.invalid-amount");
            return;
        }

        Account from = resolveGovAccount(fromName, sender);
        if (from == null) return;

        if (!canTransferFrom(sender, from)) {
            message.send(sender, "treasury.gov.no-access");
            return;
        }

        int toAccountId;
        String toDisplayName;
        OfflinePlayer onlineTarget = null;

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayerIfCached(toName);
        boolean knownPlayer = targetPlayer != null && (targetPlayer.hasPlayedBefore() || targetPlayer.isOnline());

        if (knownPlayer) {
            toAccountId = accountService.getOrCreatePersonalAccountId(targetPlayer.getUniqueId());
            toDisplayName = targetPlayer.getName();
            onlineTarget = targetPlayer;
        } else {
            // BUSINESS account display names follow the convention
            // "<FirmName> Corporate Account" (FirmServiceImpl line 109). The
            // <to> arg type is word()-tokenised, so a user typing the full
            // multi-word display name would split across two args. Try the
            // bare token first, then fall back to the canonical suffix —
            // letting `/gov payout DCGov Acme 100 …` resolve to Acme's
            // corporate account without users having to know the convention.
            Account businessAccount = accountService.getBusinessAccountByName(toName);
            if (businessAccount == null) {
                businessAccount = accountService.getBusinessAccountByName(toName + " Corporate Account");
            }
            if (businessAccount == null) {
                message.send(sender, "treasury.gov.payout.unknown-recipient", "name", toName);
                return;
            }
            toAccountId = businessAccount.getAccountId();
            toDisplayName = businessAccount.getDisplayName();
        }

        String memo = reason != null
                ? "Gov payout: " + reason
                : "Gov payout from " + from.getDisplayName() + " to " + toDisplayName;

        byte[] dedupKey = GovDedupKeys.payout(
                actorOf(sender), from.getAccountId(), toAccountId, normalized, Instant.now());

        try {
            TransferRequest req = new TransferRequest(
                    from.getAccountId(),
                    toAccountId,
                    normalized,
                    memo,
                    actorOf(sender),
                    null,
                    TreasuryConstants.TREASURY_PLUGIN_NAME,
                    dedupKey);
            // Console bypasses requires_authorization too; players use the normal path.
            if (sender instanceof Player) {
                ledgerService.transfer(req);
            } else {
                ledgerService.adminTransfer(req);
            }
        } catch (IllegalStateException e) {
            BigDecimal balance = accountService.getBalanceReadOnly(from.getAccountId());
            message.send(sender, "treasury.gov.account.transfer.insufficient",
                    "from", from.getDisplayName(),
                    "balance", accountService.formatAmount(balance));
            return;
        }

        message.send(sender, "treasury.gov.payout.success",
                "from", from.getDisplayName(),
                "to", toDisplayName,
                "amount", accountService.formatAmount(normalized));

        if (onlineTarget != null) {
            Player onlinePlayer = onlineTarget.getPlayer();
            if (onlinePlayer != null) {
                message.send(onlinePlayer, "treasury.gov.payout.received",
                        "amount", accountService.formatAmount(normalized),
                        "from", from.getDisplayName());
            }
        }
    }

    private Account resolveGovAccount(String name, CommandSender sender) {
        Account account = accountService.getGovernmentAccountByName(name);
        if (account == null || account.isArchived()) {
            message.send(sender, "treasury.gov.not-found", "account", name);
            return null;
        }
        return account;
    }

    private boolean hasMemberAccess(CommandSender sender, Account account) {
        if (!(sender instanceof Player p)) {
            return true; // console / RCON
        }
        return p.hasPermission("treasury.gov.admin")
                || membershipService.isMember(account.getAccountId(), p.getUniqueId());
    }

    /**
     * Who may move money <em>out of</em> a government account. Members and
     * authorizers of the account can transfer/pay out from it to anyone (this is
     * how access is delegated: add an LP group as a member with
     * {@code /gov account member addgroup} and that group can spend, without
     * being able to modify members — that needs {@code treasury.gov.account.manage}
     * <em>and</em> authorizer access, see {@link #hasAuthorizerAccess}).
     * {@code treasury.gov.account.transfer} is a global grant over all gov
     * accounts; {@code treasury.gov.admin} is full access.
     */
    private boolean canTransferFrom(CommandSender sender, Account account) {
        // Console / RCON bypasses the per-account membership gate — the server is
        // the authority. A plain player needs the global transfer/admin node, or
        // membership/authorizer on this specific account.
        if (!(sender instanceof Player p)) {
            return true;
        }
        return p.hasPermission("treasury.gov.admin")
                || p.hasPermission("treasury.gov.account.transfer")
                || membershipService.isMember(account.getAccountId(), p.getUniqueId())
                || membershipService.isAuthorizer(account.getAccountId(), p.getUniqueId());
    }

    /**
     * Stricter gate for <em>managing</em> an account's members/authorizers:
     * authorizer (or admin) only — a plain member must not be able to add or
     * remove other members.
     */
    private boolean hasAuthorizerAccess(CommandSender sender, Account account) {
        if (!(sender instanceof Player p)) {
            return true; // console / RCON
        }
        return p.hasPermission("treasury.gov.admin")
                || membershipService.isAuthorizer(account.getAccountId(), p.getUniqueId());
    }

    /** A permission gate that always passes for console / RCON (non-player
     *  senders), since many gov nodes are {@code default: false} and would
     *  otherwise deny even the console. Players get the normal permission check. */
    private static boolean isAllowed(CommandSender sender, String node) {
        return !(sender instanceof Player) || sender.hasPermission(node);
    }

    /** The initiator UUID to attribute an action to: the player, or the virtual
     *  console initiator when run from console / RCON. */
    private static UUID actorOf(CommandSender sender) {
        return sender instanceof Player p
                ? p.getUniqueId()
                : TreasuryConstants.VIRTUAL_TREASURY_INITIATOR;
    }

    private String resolvePlayerName(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String name = op.getName();
        return name != null ? name : uuid.toString();
    }

    private static String sanitize(String input) {
        return input.replace("<", "\\<");
    }
}
