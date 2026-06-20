package io.paradaux.treasury.services.impl;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import io.paradaux.treasury.model.config.SalaryConfiguration;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.economy.TransferRequest;
import io.paradaux.treasury.model.salary.SalaryPayment;
import io.paradaux.treasury.services.AccountService;
import io.paradaux.treasury.services.EconomyNotifier;
import io.paradaux.treasury.services.LedgerService;
import io.paradaux.treasury.services.SalaryService;
import io.paradaux.treasury.utils.TreasuryConstants;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pays online players a salary from a government account based on their highest
 * LuckPerms-group salary, on a fixed interval. See {@link SalaryConfiguration}.
 */
@Slf4j
public class SalaryServiceImpl implements SalaryService {

    private final SalaryConfiguration config;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final Server server;
    private final EconomyNotifier notifier;

    /** Optional soft-dependency; injected only when LuckPerms is present. */
    private LuckPerms luckPerms;

    @Inject
    public SalaryServiceImpl(SalaryConfiguration config, AccountService accountService,
                             LedgerService ledgerService, Server server, EconomyNotifier notifier) {
        this.config = config;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.server = server;
        this.notifier = notifier;
    }

    @Inject(optional = true)
    public void setLuckPerms(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
        log.info("LuckPerms integration active — government salaries enabled.");
    }

    @Override
    public List<SalaryPayment> planPayroll() {
        if (!config.isEnabled() || config.getAmounts().isEmpty()) {
            return List.of();
        }
        if (luckPerms == null) {
            log.warn("Salaries are enabled but LuckPerms is unavailable — no payroll computed.");
            return List.of();
        }

        List<SalaryPayment> plan = new ArrayList<>();
        for (Player player : server.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            config.highestSalary(groupsOf(uuid)).ifPresent(best ->
                    plan.add(new SalaryPayment(uuid, best.getKey(), best.getValue())));
        }
        return plan;
    }

    @Override
    public int payout(List<SalaryPayment> payments) {
        if (payments == null || payments.isEmpty()) {
            return 0;
        }
        Account gov = accountService.getGovernmentAccountByName(config.getGovernmentAccount());
        if (gov == null) {
            log.warn("Salary government account '{}' not found — no salaries paid.", config.getGovernmentAccount());
            return 0;
        }
        int fromAccountId = gov.getAccountId();

        int paid = 0;
        for (SalaryPayment payment : payments) {
            try {
                int toAccountId = accountService.getOrCreatePersonalAccountId(payment.player());
                ledgerService.transfer(new TransferRequest(
                        fromAccountId,
                        toAccountId,
                        payment.amount(),
                        "Government salary (" + payment.group() + ")",
                        TreasuryConstants.VIRTUAL_TREASURY_INITIATOR,
                        null,
                        "treasury-salary",
                        null));
                notifier.notifySalaryPaid(payment.player(), payment.amount());
                paid++;
            } catch (Exception e) {
                log.warn("Salary payment to {} failed: {}", payment.player(), e.getMessage());
            }
        }
        if (paid > 0) {
            log.info("Paid {} salary(ies) from {}.", paid, config.getGovernmentAccount());
        }
        return paid;
    }

    /** LuckPerms inherited group names for a player (empty if unknown/offline). */
    private Set<String> groupsOf(UUID uuid) {
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) {
            return Set.of();
        }
        return user.getInheritedGroups(user.getQueryOptions()).stream()
                   .map(Group::getName)
                   .collect(Collectors.toSet());
    }
}
