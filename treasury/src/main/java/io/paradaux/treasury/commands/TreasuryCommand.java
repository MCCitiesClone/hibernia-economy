package io.paradaux.treasury.commands;

import com.google.inject.Inject;
import io.paradaux.hibernia.framework.commander.annotations.*;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.i18n.Message;
import lombok.extern.slf4j.Slf4j;
import io.paradaux.treasury.Treasury;
import io.paradaux.treasury.api.ingest.IngestApi;
import io.paradaux.treasury.api.ingest.IngestReport;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.economy.TransferRequest;
import io.paradaux.treasury.services.AccountService;
import io.paradaux.treasury.services.ConfigReloadService;
import io.paradaux.treasury.services.LedgerService;
import io.paradaux.treasury.services.MembershipService;
import io.paradaux.treasury.utils.GovDedupKeys;
import io.paradaux.treasury.utils.Money;
import io.paradaux.treasury.utils.TreasuryConstants;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Command({"treasury", "tr"})
@Permission("treasury.command")
public class TreasuryCommand implements CommandHandler {

    private final Treasury plugin;
    private final Message message;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final MembershipService membershipService;
    private final AccountResolver accountResolver;
    private final ConfigReloadService configReloadService;

    @Inject
    public TreasuryCommand(Treasury plugin, Message message,
                           AccountService accountService, LedgerService ledgerService,
                           MembershipService membershipService, AccountResolver accountResolver,
                           ConfigReloadService configReloadService) {
        this.plugin = plugin;
        this.message = message;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.membershipService = membershipService;
        this.accountResolver = accountResolver;
        this.configReloadService = configReloadService;
    }

    /**
     * {@code /treasury reload} — re-read config.yml + messages.properties and
     * refresh the live config objects (salary amounts, tax brackets/rates,
     * government account names, log level). DB pool, schedules and the currency
     * formatter still need a restart.
     */
    @Route("reload")
    @Permission("treasury.admin.reload")
    @Description("Admin: reload config.yml and messages.properties at runtime")
    public void reload(@Sender CommandSender sender) {
        try {
            configReloadService.reloadAll();
        } catch (RuntimeException e) {
            log.warn("Config reload failed", e);
            sender.sendMessage("§cReload failed: " + e.getMessage() + " (see console).");
            return;
        }
        sender.sendMessage("§aReloaded config.yml and messages.properties "
                + "(salaries, tax brackets/rates, gov accounts, log level). "
                + "DB pool, schedules and currency format still need a restart.");
    }

    @Route("")
    @Description("Show Treasury plugin information")
    public void info(@Sender CommandSender sender) {
        message.send(sender, "treasury.info",
                "version", plugin.getDescription().getVersion());
    }

    @Route("help")
    @Description("Show Treasury help index")
    public void help(@Sender CommandSender sender) {
        message.send(sender, "treasury.help");
    }

    @Route("help balance")
    @Description("Show balance / baltop help")
    public void helpBalance(@Sender CommandSender sender) {
        message.send(sender, "treasury.help.balance");
    }

    @Route("help pay")
    @Description("Show /pay help")
    public void helpPay(@Sender CommandSender sender) {
        message.send(sender, "treasury.help.pay");
    }

    @Route("help transactions")
    @Description("Show /transactions help")
    public void helpTransactions(@Sender CommandSender sender) {
        message.send(sender, "treasury.help.transactions");
    }

    @Route("help eco")
    @Description("Show /eco (admin) help")
    public void helpEco(@Sender CommandSender sender) {
        message.send(sender, "treasury.help.eco");
    }

    @Route("help fine")
    @Description("Show /fine help")
    public void helpFine(@Sender CommandSender sender) {
        message.send(sender, "treasury.help.fine");
    }

    @Route("help gov")
    @Description("Show /government help")
    public void helpGov(@Sender CommandSender sender) {
        message.send(sender, "treasury.help.gov");
    }

    @Route("help tax")
    @Description("Show /tax help")
    public void helpTax(@Sender CommandSender sender) {
        message.send(sender, "treasury.help.tax");
    }

    @Route("admin ingest <source>")
    @Permission("treasury.admin.ingest")
    @Async
    @Description("Ingest player + balance data from a legacy economy backend")
    public void adminIngest(@Sender CommandSender sender, @Arg("source") String source) {
        String wanted = source.toLowerCase(Locale.ROOT);
        IngestApi target = null;
        for (RegisteredServiceProvider<IngestApi> rsp :
                Bukkit.getServicesManager().getRegistrations(IngestApi.class)) {
            if (rsp.getProvider().supportedSources().contains(wanted)) {
                target = rsp.getProvider();
                break;
            }
        }
        if (target == null) {
            sender.sendMessage("§cNo ingest plugin is registered for source '" + source
                    + "'. Install TreasuryIngest (or another implementing plugin) and retry.");
            return;
        }
        sender.sendMessage("§eStarting " + wanted + " ingest…");
        try {
            IngestReport report = target.ingest(wanted, sender);
            sender.sendMessage("§aIngest complete — created=" + report.playersCreated()
                    + ", skipped=" + report.playersSkipped()
                    + ", failed=" + report.playersFailed()
                    + ", scanned=" + report.filesScanned()
                    + ", total=" + report.totalIngestedAmount()
                    + ", " + report.durationMillis() + "ms");
        } catch (Throwable t) {
            log.error("Ingest from source '{}' failed", wanted, t);
            sender.sendMessage("§cIngest failed: " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
        }
    }

    // =====================================================================
    // ADMIN TRANSFER
    //
    // Explicitly-typed, console-capable transfer between ANY two accounts.
    // Each side names its own kind — player / government / business / account
    // (the last being a raw account id) — so a player and a business that share
    // a display name are never confused. Bypasses requires_authorization (the
    // treasury.admin.transfer permission IS the authority); overdraft rules and
    // the source's funds still apply.
    //
    //   /treasury admin transfer <fromType> <from> <toType> <to> <amount> [reason]
    //   e.g. /tr admin transfer business Acme government DCGovernment 5000 grant
    //        /tr admin transfer account 42 player Steve 100
    // =====================================================================

    @Route("admin transfer <fromType> <from> <toType> <to> <amount>")
    @Permission("treasury.admin.transfer")
    @Async
    @Description("Admin transfer between any two accounts (player/government/business/account)")
    public void adminTransfer(@Sender CommandSender sender,
                              @Arg("fromType") String fromType,
                              @Arg("from") String from,
                              @Arg("toType") String toType,
                              @Arg("to") String to,
                              @Arg("amount") BigDecimal amount) {
        doAdminTransfer(sender, fromType, from, toType, to, amount, null);
    }

    @Route("admin transfer <fromType> <from> <toType> <to> <amount> <reason>")
    @Permission("treasury.admin.transfer")
    @Async
    @Description("Admin transfer between any two accounts with a reason")
    public void adminTransferReason(@Sender CommandSender sender,
                                    @Arg("fromType") String fromType,
                                    @Arg("from") String from,
                                    @Arg("toType") String toType,
                                    @Arg("to") String to,
                                    @Arg("amount") BigDecimal amount,
                                    @GreedyArg("reason") String reason) {
        doAdminTransfer(sender, fromType, from, toType, to, amount, reason);
    }

    @Route("admin balance <type> <id>")
    @Permission("treasury.admin.inspect")
    @Async // resolves + reads balance off the main thread, like every sibling route (ADT-18).
    @Description("Show the balance of any account (player/government/business/account)")
    public void adminBalance(@Sender CommandSender sender,
                             @Arg("type") String type, @Arg("id") String id) {
        AccountResolver.Resolved r = accountResolver.resolve(sender, "target", type, id, false);
        if (r == null) return;
        BigDecimal balance = accountService.getBalanceReadOnly(r.accountId());
        sender.sendMessage("§e" + r.label() + " §7balance: §a" + accountService.formatAmount(balance));
    }

    @Route("admin info <type> <id>")
    @Permission("treasury.admin.inspect")
    @Async // ~5 sequential DB round-trips — must not run on the Bukkit main thread (ADT-18).
    @Description("Dump account details (id, type, balance, flags, members) for debugging")
    public void adminInfo(@Sender CommandSender sender,
                          @Arg("type") String type, @Arg("id") String id) {
        AccountResolver.Resolved r = accountResolver.resolve(sender, "target", type, id, false);
        if (r == null) return;
        Account a = accountService.getAccountById(r.accountId());
        if (a == null) {
            sender.sendMessage("§cAccount " + r.accountId() + " no longer exists.");
            return;
        }
        BigDecimal balance = accountService.getBalanceReadOnly(a.getAccountId());
        sender.sendMessage("§e--- " + r.label() + " ---");
        sender.sendMessage("§7id=§f" + a.getAccountId() + " §7type=§f" + a.getAccountType()
                + " §7name=§f" + a.getDisplayName());
        sender.sendMessage("§7owner=§f" + a.getOwnerUuid());
        sender.sendMessage("§7balance=§a" + accountService.formatAmount(balance));
        sender.sendMessage("§7archived=§f" + a.isArchived()
                + " §7requiresAuth=§f" + a.isRequiresAuthorization()
                + " §7overdraft=§f" + a.isAllowOverdraft()
                + " §7creditLimit=§f" + a.getCreditLimit());
        sender.sendMessage("§7members=§f" + membershipService.getMembers(a.getAccountId()).size()
                + " §7authorizers=§f" + membershipService.getAuthorizers(a.getAccountId()).size());
    }

    private void doAdminTransfer(CommandSender sender, String fromType, String fromToken,
                                 String toType, String toToken, BigDecimal amount, String reason) {
        BigDecimal normalized = Money.normalize(amount);
        if (normalized.signum() <= 0) {
            sender.sendMessage("§cAmount must be positive.");
            return;
        }

        AccountResolver.Resolved from = accountResolver.resolve(sender, "from", fromType, fromToken, true);
        if (from == null) return;
        AccountResolver.Resolved to = accountResolver.resolve(sender, "to", toType, toToken, true);
        if (to == null) return;

        if (from.accountId() == to.accountId()) {
            sender.sendMessage("§cSource and destination are the same account (" + from.label() + ").");
            return;
        }

        UUID initiator = sender instanceof Player p
                ? p.getUniqueId()
                : TreasuryConstants.VIRTUAL_TREASURY_INITIATOR;
        String memo = reason != null
                ? "Admin transfer: " + reason
                : "Admin transfer " + from.label() + " -> " + to.label();
        byte[] dedupKey = GovDedupKeys.adminTransfer(
                initiator, from.accountId(), to.accountId(), normalized, Instant.now());

        try {
            ledgerService.adminTransfer(new TransferRequest(
                    from.accountId(),
                    to.accountId(),
                    normalized,
                    memo,
                    initiator,
                    null,
                    TreasuryConstants.TREASURY_PLUGIN_NAME,
                    dedupKey));
        } catch (IllegalStateException e) {
            BigDecimal balance = accountService.getBalanceReadOnly(from.accountId());
            sender.sendMessage("§cTransfer failed: insufficient funds in " + from.label()
                    + " (balance " + accountService.formatAmount(balance) + ").");
            return;
        } catch (RuntimeException e) {
            log.warn("Admin transfer failed ({} -> {}, {})", from.label(), to.label(), normalized, e);
            sender.sendMessage("§cTransfer failed: " + e.getMessage());
            return;
        }

        sender.sendMessage("§aTransferred " + accountService.formatAmount(normalized)
                + " from " + from.label() + " to " + to.label() + ".");
    }

}
