package io.paradaux.business.services.impl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import io.paradaux.hibernia.framework.exceptions.BadCommandException;
import io.paradaux.hibernia.framework.exceptions.ExceedsLimitException;
import io.paradaux.hibernia.framework.exceptions.NoPermissionException;
import io.paradaux.hibernia.framework.utils.StringUtils;
import io.paradaux.business.mappers.FirmMapper;
import io.paradaux.business.mappers.FirmRoleMapper;
import io.paradaux.business.model.Firm;
import io.paradaux.business.model.FirmRole;
import io.paradaux.business.model.FirmRolePermission;
import io.paradaux.business.model.RolePermission;
import io.paradaux.business.mappers.FirmAccountsMapper;
import io.paradaux.business.model.config.FirmConfiguration;
import io.paradaux.business.services.FirmAreaShopService;
import io.paradaux.business.services.FirmService;
import io.paradaux.business.services.FirmStaffService;
import io.paradaux.business.utils.NameValidator;
import io.paradaux.business.utils.RoleUtils;
import io.paradaux.business.model.FirmAccount;
import io.paradaux.treasury.api.TreasuryApi;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.economy.AccountType;
import io.paradaux.treasury.model.economy.TransferRequest;
import org.mybatis.guice.transactional.Transactional;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Singleton
public class FirmServiceImpl implements FirmService {

    private static final int FIRM_NAME_LENGTH_LIMIT = 32;

    private final FirmMapper firms;
    private final TreasuryApi treasury;
    private final FirmAccountsMapper accounts;
    private final FirmRoleMapper roles;
    /** Lazy-injected to break the FirmService ↔ FirmStaffService cycle. */
    private final Provider<FirmStaffService> staffProvider;
    private final FirmConfiguration firmConfig;
    private final FirmAreaShopService areas;

    @Inject
    public FirmServiceImpl(FirmMapper firms,
                           TreasuryApi treasury,
                           FirmAccountsMapper accounts,
                           FirmRoleMapper roles,
                           Provider<FirmStaffService> staffProvider,
                           FirmConfiguration firmConfig,
                           FirmAreaShopService areas) {
        this.firms = firms;
        this.treasury = treasury;
        this.accounts = accounts;
        this.roles = roles;
        this.staffProvider = staffProvider;
        this.firmConfig = firmConfig;
        this.areas = areas;
    }

    private boolean canAdministerFirm(int firmId, UUID actorId) {
        return staffProvider.get().hasPermission(firmId, actorId, RolePermission.ADMIN);
    }

    /**
     * Creates a firm with default roles, permissions, and a Treasury account.
     *
     * <p>Wrapped in a JDBC transaction so the local-DB writes (firm, roles,
     * permissions, firm_accounts, default_account_id update) are atomic. The
     * Treasury account-creation calls in the middle are external IPC and
     * cannot participate in a JDBC transaction — if the DB rolls back after
     * a successful Treasury account creation, the Treasury account is left
     * orphaned (still owned by the actor, no firm linkage). Operators should
     * watch for that pattern in Treasury logs and clean up manually if needed.
     */
    @Transactional
    @Override
    public Firm createFirm(String name, UUID actorId) {
        validateFirmName(name);

        // Create a blank firm
        Firm firm = new Firm();
        firm.setDisplayName(name);
        firm.setProprietorUuid(actorId.toString());

        // Validation checks, name uniqueness / ownership limit
        if (firms.getFirmsByNameCount(name) > 0) {
            throw new ExceedsLimitException("Firm with name '" + name + "' already exists");
        }

        if (firmConfig.hasOwnedFirmLimit()
                && firms.getFirmsOwnedByCount(actorId.toString()) >= firmConfig.getOwnedFirmLimit()) {
            throw new ExceedsLimitException("User exceeds firm ownership limit of " + firmConfig.getOwnedFirmLimit());
        }

        // Throttle rapid firm creation (incl. create/disband cycling) per player (PAR-25).
        if (firmConfig.hasCreateCooldown()) {
            Long since = firms.secondsSinceLastCreation(actorId.toString());
            if (since != null && since < firmConfig.getCreateCooldownSeconds()) {
                long remaining = firmConfig.getCreateCooldownSeconds() - since;
                throw new ExceedsLimitException("You must wait " + remaining + "s before creating another firm.");
            }
        }

        // Put it into the DB to get an ID
        firms.createFirm(firm);

        // Create a treasury account via the TreasuryApi
        Account account = treasury.createAccount(AccountType.BUSINESS, actorId, name + " Corporate Account");

        // Auto-add the owner as a member + authorizer on the treasury account
        treasury.addMember(account.getAccountId(), actorId, actorId);
        treasury.addAuthorizer(account.getAccountId(), actorId, actorId);

        // Create a record of the firm owning this account
        accounts.insertFirmAccount(firm.getFirmId(), account.getAccountId());

        // Set it as the firm's default treasury account.
        firm.setDefaultAccountId(account.getAccountId());

        // Add the default roles
        for (FirmRole role : RoleUtils.getDefaultRoles(firm.getFirmId())) {
            roles.insertRole(role);
        }

        // Add the default permissions for every role
        for (FirmRolePermission permission : RoleUtils.getDefaultPermissions(firm.getFirmId())) {
            roles.addRolePermission(permission);
        }

        // Update the firm with the updated default account.
        firms.updateFirm(firm);
        return firm;
    }

    @Transactional
    @Override
    public void disbandFirm(String firmName, UUID actorId) {
        // Archived-inclusive: a disbanded firm must report "already disbanded", not "not found".
        Firm firm = getAnyFirmByNameOrId(firmName);
        if (firm == null) {
            throw new BadCommandException("Firm not found: " + firmName);
        }
        if (Boolean.TRUE.equals(firm.getArchived())) {
            throw new BadCommandException("Firm already disbanded: " + firmName);
        }
        if (!isProprietor(firm.getFirmId(), actorId)) {
            throw new NoPermissionException("Only the proprietor can disband a firm.");
        }
        disbandInternal(firm);
    }

    @Transactional
    @Override
    public void adminDisbandFirm(String firmName) {
        Firm firm = getAnyFirmByNameOrId(firmName);
        if (firm == null) {
            throw new BadCommandException("Firm not found: " + firmName);
        }
        if (Boolean.TRUE.equals(firm.getArchived())) {
            throw new BadCommandException("Firm already disbanded: " + firmName);
        }
        disbandInternal(firm);
    }

    /** Shared disband mechanics: drains balances to the proprietor and archives the firm. */
    private void disbandInternal(Firm firm) {
        UUID proprietorUuid = UUID.fromString(firm.getProprietorUuid());
        Account personal = treasury.resolveOrCreatePersonal(proprietorUuid);

        // Withdraw balances and archive every firm account
        List<FirmAccount> firmAccountList = accounts.listAccountsByFirm(firm.getFirmId());
        for (FirmAccount fa : firmAccountList) {
            BigDecimal balance = treasury.getBalanceByAccountId(fa.getAccountId());
            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                TransferRequest req = new TransferRequest(
                        fa.getAccountId(),
                        personal.getAccountId(),
                        balance,
                        "Firm disbanded",
                        proprietorUuid,
                        proprietorUuid,
                        "BusinessPlugin",
                        null
                );
                treasury.transfer(req);
            }
            treasury.archiveAccount(fa.getAccountId());
            accounts.removeFirmAccount(firm.getFirmId(), fa.getAccountId());
        }

        // Atomically flip is_archived and clear default_account_id so the firm
        // doesn't dangle a reference to a now-archived account.
        firms.archiveFirm(firm.getFirmId());
    }

    @Transactional
    @Override
    public void updateDefaultAccount(Integer firmId, Integer accountId) {
        Firm update = new Firm();
        update.setFirmId(firmId);
        update.setDefaultAccountId(accountId);
        firms.updateFirm(update);
    }

    @Override
    public List<Firm> listAllFirms(int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 25; // sane default
        int offset = (page - 1) * pageSize;
        return firms.listAllFiltered(pageSize, offset, false);
    }

    @Override
    public List<Firm> listOwnedOrMemberFirms(UUID playerId) {
        return firms.listOwnedOrMemberFirms(playerId.toString());
    }

    @Override
    public void updateFirmHq(String firmName, String plotName, UUID actorId) {
        Firm firm = getFirmByNameOrId(firmName);
        if (firm == null) {
            throw new BadCommandException("Firm not found: " + firmName);
        }
        if (!canAdministerFirm(firm.getFirmId(), actorId)) {
            throw new NoPermissionException("You don't have permission to manage this firm.");
        }
        // Reject HQ regions the region provider (Realty) doesn't recognise so
        // firms can't claim arbitrary/nonexistent plots. Fails open when no
        // provider is installed (ADT-37). The admin override (adminSetHq) is
        // intentionally exempt.
        if (!areas.isValidPlot(plotName)) {
            throw new BadCommandException("'" + plotName + "' is not a valid HQ region.");
        }
        Firm updatedFirm = new Firm();
        updatedFirm.setFirmId(firm.getFirmId());
        updatedFirm.setHqRegion(plotName);
        firms.updateFirm(updatedFirm);
    }

    @Override
    public void updateFirmDiscord(String firmName, String url, UUID actorId) {
        Firm firm = getFirmByNameOrId(firmName);
        if (firm == null) {
            throw new BadCommandException("Firm not found: " + firmName);
        }
        if (!canAdministerFirm(firm.getFirmId(), actorId)) {
            throw new NoPermissionException("You don't have permission to manage this firm.");
        }
        Firm updatedFirm = new Firm();
        updatedFirm.setFirmId(firm.getFirmId());
        updatedFirm.setDiscordUrl(url);
        firms.updateFirm(updatedFirm);
    }

    /**
     * Promotes a player to proprietor. Internal mutator — must only be called
     * after a transfer has been successfully accepted/confirmed; callers are
     * responsible for that gating since the new proprietor is, by definition,
     * not yet authorized as the proprietor.
     */
    @Override
    public void updateProprietor(Integer firmId, UUID playerId) {
        Firm firm = new Firm();
        firm.setFirmId(firmId);
        firm.setProprietorUuid(playerId.toString());
        firms.updateFirm(firm);
    }

    // ---- Staff/DOC administrative overrides (PAR-11) -------------------------

    @Transactional
    @Override
    public Firm renameFirm(String firmName, String newName) {
        Firm firm = getFirmByNameOrId(firmName);
        if (firm == null) {
            throw new BadCommandException("Firm not found: " + firmName);
        }
        validateFirmName(newName);
        // Allow a no-op/case rename of the firm itself; only reject collisions with a *different* firm.
        if (!newName.equalsIgnoreCase(firm.getDisplayName()) && firms.getFirmsByNameCount(newName) > 0) {
            throw new ExceedsLimitException("Firm with name '" + newName + "' already exists");
        }
        Firm update = new Firm();
        update.setFirmId(firm.getFirmId());
        update.setDisplayName(newName);
        firms.updateFirm(update);
        firm.setDisplayName(newName);
        return firm;
    }

    @Override
    public void adminSetHq(String firmName, String plotName) {
        Firm firm = requireFirm(firmName);
        Firm update = new Firm();
        update.setFirmId(firm.getFirmId());
        update.setHqRegion(plotName);
        firms.updateFirm(update);
    }

    @Override
    public void adminSetDiscord(String firmName, String url) {
        Firm firm = requireFirm(firmName);
        Firm update = new Firm();
        update.setFirmId(firm.getFirmId());
        update.setDiscordUrl(url);
        firms.updateFirm(update);
    }

    @Override
    public void adminSetProprietor(String firmName, UUID newProprietor) {
        Firm firm = requireFirm(firmName);
        updateProprietor(firm.getFirmId(), newProprietor);
    }

    private Firm requireFirm(String firmName) {
        Firm firm = getFirmByNameOrId(firmName);
        if (firm == null) {
            throw new BadCommandException("Firm not found: " + firmName);
        }
        return firm;
    }

    /** Format + length checks shared by {@link #createFirm} and {@link #renameFirm}. */
    private void validateFirmName(String name) {
        if (name == null || name.length() > FIRM_NAME_LENGTH_LIMIT) {
            throw new BadCommandException("This firm name is too long");
        }
        if (StringUtils.startsWithNumber(name)) {
            throw new BadCommandException("Firm names cannot start with a number.");
        }
        if (!NameValidator.isValidFirmName(name)) {
            throw new BadCommandException("Firm name must be 2–32 characters of letters, digits, space, underscore, hyphen, or period.");
        }
    }

    @Override
    public boolean isProprietor(String firmName, UUID playerId) {
        return firms.isProprietorByFirmName(firmName, playerId.toString());
    }

    @Override
    public boolean isProprietor(Integer firmId, UUID playerId) {
        return firms.isProprietorByFirmId(firmId, playerId.toString());
    }

    @Override
    public List<Firm> listAllActiveFirms() {
        return firms.listAllActive();
    }

    @Override
    @Nullable
    public Firm getFirmByNameOrId(String input) {
        Firm firm = getAnyFirmByNameOrId(input);
        // Disbanded firms must stop resolving for command/action paths so they can't
        // be operated on after disband (PAR-87).
        return (firm == null || Boolean.TRUE.equals(firm.getArchived())) ? null : firm;
    }

    @Override
    @Nullable
    public Firm getAnyFirmByNameOrId(String input) {
        if (input != null && input.chars().allMatch(Character::isDigit)) {
            int id = Integer.parseInt(input);
            return firms.getFirmById(id);
        }
        return firms.getFirmByName(input);
    }
}
