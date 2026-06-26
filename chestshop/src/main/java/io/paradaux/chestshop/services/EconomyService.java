package io.paradaux.chestshop.services;

import com.google.inject.Singleton;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.database.Account;
import io.paradaux.treasury.api.TreasuryApi;
import io.paradaux.treasury.model.economy.AccountType;
import io.paradaux.treasury.model.economy.TransferRequest;
import io.paradaux.treasury.utils.Idempotency;
import org.bukkit.ChatColor;
import org.bukkit.World;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The internal economy API: ChestShop's single point of contact with the Treasury
 * ledger ({@link TreasuryApi}). Services call these methods directly instead of
 * firing the old internal {@code Currency*Event}s and routing them through
 * {@code TreasuryListener} — replacing the economy event bus with a
 * treasury-api-style service boundary.
 *
 * <p>The live {@link TreasuryApi} handle + the ChestShop SYSTEM account id are
 * {@linkplain #bind bound} once Treasury is resolved at enable (from
 * {@code TreasuryListener.prepareListener}); ChestShop requires Treasury, so by the
 * time any economy call runs they are set. The genuine cross-plugin event
 * integration points (AccountQuery/AccountAccess) stay as events.
 *
 * <p>The account-resolution helpers below are, for now, duplicated from
 * {@code TreasuryListener} (which still needs them for the not-yet-migrated
 * check/transfer handlers); they collapse into this single home when the transfer
 * leg moves here.
 */
@Singleton
public class EconomyService {

    private static final long BUSINESS_UUID_MSB = 0xC5B0000000000000L;
    private static final UUID CHESTSHOP_SYSTEM_UUID = new UUID(0xC5B0FFFFFFFFFFFEL, 0xFFFFFFFFFFFFFFFEL);

    private volatile TreasuryApi treasury;
    private volatile int systemAccountId;

    /** Wire the resolved Treasury handle + SYSTEM account in once available (enable time). */
    public void bind(TreasuryApi treasury, int systemAccountId) {
        this.treasury = treasury;
        this.systemAccountId = systemAccountId;
    }

    /** Render a money amount for display through Treasury, honouring STRIP_PRICE_COLORS. */
    public String format(BigDecimal amount) {
        TreasuryApi t = treasury;
        if (t == null) {
            return amount.toPlainString();
        }
        try {
            String formatted = t.formatAmount(amount);
            return Properties.STRIP_PRICE_COLORS ? ChatColor.stripColor(formatted) : formatted;
        } catch (Exception e) {
            ChestShop.getBukkitLogger().log(Level.WARNING, "Treasury: Could not format amount " + amount, e);
            return amount.toPlainString();
        }
    }

    /**
     * Credit {@code amount} to {@code target} (a SYSTEM→target transfer). Admin-shop
     * targets are redirected to the configured server-economy account, or swallowed
     * if none is configured (was {@code CurrencyAddEvent} + {@code ServerAccountCorrector}).
     */
    public void deposit(UUID target, BigDecimal amount, World world) {
        UUID resolved = normaliseAdminTarget(target);
        if (resolved == null) {
            return; // admin shop with no server-economy account → nothing to credit
        }
        try {
            int targetAccountId = resolveAccountId(resolved);
            byte[] dedupKey = Idempotency.sha256(
                    "chestshop:add:" + resolved + ":" + amount + ":" + System.nanoTime());
            treasury.transfer(new TransferRequest(
                    systemAccountId, targetAccountId, amount, "ChestShop deposit",
                    CHESTSHOP_SYSTEM_UUID, null, "ChestShop", dedupKey));
        } catch (Exception e) {
            ChestShop.getBukkitLogger().log(Level.WARNING, "Treasury: Could not add " + amount + " to " + resolved, e);
        }
    }

    /**
     * Debit {@code amount} from {@code target} (a target→SYSTEM transfer). Returns
     * whether the debit succeeded — {@code false} means it could not be taken (e.g.
     * insufficient funds), which the caller treats as "can't afford". Admin-shop
     * targets are redirected to the server-economy account, or treated as an
     * unlimited success if none is configured (was {@code CurrencySubtractEvent} +
     * {@code ServerAccountCorrector}).
     */
    public boolean withdraw(UUID target, BigDecimal amount, World world) {
        UUID resolved = normaliseAdminTarget(target);
        if (resolved == null) {
            return true; // admin shop with no server-economy account → unlimited
        }
        try {
            int targetAccountId = resolveAccountId(resolved);
            byte[] dedupKey = Idempotency.sha256(
                    "chestshop:sub:" + resolved + ":" + amount + ":" + System.nanoTime());
            // The account owner authorises the withdrawal for personal accounts.
            UUID initiator = isBusinessUuid(resolved) ? CHESTSHOP_SYSTEM_UUID : resolved;
            treasury.transfer(new TransferRequest(
                    targetAccountId, systemAccountId, amount, "ChestShop withdrawal",
                    initiator, null, "ChestShop", dedupKey));
            return true;
        } catch (Exception e) {
            ChestShop.getBukkitLogger().log(Level.WARNING, "Treasury: Could not subtract " + amount + " from " + resolved, e);
            return false;
        }
    }

    /**
     * Apply the admin-shop → server-economy redirect: a plain target is returned
     * unchanged; an admin-shop target becomes the server-economy account's UUID, or
     * {@code null} when no server-economy account is configured (the operation is then
     * a no-op / unlimited, matching the old {@code ServerAccountCorrector}).
     */
    private UUID normaliseAdminTarget(UUID target) {
        if (!ChestShop.accounts().isAdminShop(target) || ChestShop.accounts().isServerEconomyAccount(target)) {
            return target;
        }
        Account server = ChestShop.accounts().getServerEconomyAccount();
        return server != null ? server.getUuid() : null;
    }

    // ── Treasury account resolution (duplicated from TreasuryListener for now) ──────

    private static boolean isBusinessUuid(UUID uuid) {
        return uuid.getMostSignificantBits() == BUSINESS_UUID_MSB;
    }

    private int resolveAccountId(UUID uuid) {
        if (isBusinessUuid(uuid)) {
            return (int) uuid.getLeastSignificantBits();
        }
        Integer governmentAccountId = resolveGovernmentAccountId(uuid);
        if (governmentAccountId != null) {
            return governmentAccountId;
        }
        return treasury.resolveOrCreatePersonal(uuid).getAccountId();
    }

    private Integer resolveGovernmentAccountId(UUID uuid) {
        try {
            List<io.paradaux.treasury.model.economy.Account> governmentAccounts =
                    treasury.getAccountsByTypeAndOwner(AccountType.GOVERNMENT, uuid);
            if (governmentAccounts != null && !governmentAccounts.isEmpty()) {
                return governmentAccounts.get(0).getAccountId();
            }
        } catch (Exception e) {
            ChestShop.getBukkitLogger().log(Level.WARNING,
                    "Treasury: government account lookup failed for " + uuid, e);
        }
        return null;
    }
}
