package io.paradaux.chestshop.services;

import com.google.inject.Singleton;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.events.TransactionEvent;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.ItemUtil;
import io.paradaux.treasury.api.TaxApi;
import io.paradaux.treasury.api.TreasuryApi;
import io.paradaux.treasury.model.economy.AccountType;
import io.paradaux.treasury.model.economy.TransferRequest;
import io.paradaux.treasury.model.tax.TaxResult;
import io.paradaux.treasury.utils.Idempotency;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
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

    private static final int MAX_MESSAGE_LENGTH = 250;

    private volatile TreasuryApi treasury;
    private volatile int systemAccountId;
    private volatile TaxApi taxApi;

    /** Wire the resolved Treasury handle + SYSTEM account + tax API in once available (enable time). */
    public void bind(TreasuryApi treasury, int systemAccountId, TaxApi taxApi) {
        this.treasury = treasury;
        this.systemAccountId = systemAccountId;
        this.taxApi = taxApi;
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
     * Whether {@code account} can afford {@code amount}. An admin shop with no
     * server-economy account is unlimited (was {@code CurrencyCheckEvent}).
     */
    public boolean hasFunds(UUID account, BigDecimal amount) {
        UUID resolved = normaliseAdminTarget(account);
        if (resolved == null) {
            return true;
        }
        try {
            return treasury.hasFunds(resolveAccountId(resolved), amount);
        } catch (Exception e) {
            ChestShop.getBukkitLogger().log(Level.WARNING, "Treasury: Could not check funds for " + resolved, e);
            return false;
        }
    }

    /**
     * The balance of {@code account}. An admin shop with no server-economy account
     * reports an unlimited balance (was {@code CurrencyAmountEvent}).
     */
    public BigDecimal getBalance(UUID account) {
        UUID resolved = normaliseAdminTarget(account);
        if (resolved == null) {
            return BigDecimal.valueOf(Double.MAX_VALUE);
        }
        try {
            if (isBusinessUuid(resolved)) {
                return treasury.getBalanceByAccountId((int) resolved.getLeastSignificantBits());
            }
            Integer governmentAccountId = resolveGovernmentAccountId(resolved);
            return governmentAccountId != null
                    ? treasury.getBalanceByAccountId(governmentAccountId)
                    : treasury.getBalanceByOwnerUuid(resolved);
        } catch (Exception e) {
            ChestShop.getBukkitLogger().log(Level.WARNING, "Treasury: Could not get balance for " + resolved, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Whether {@code account} can receive {@code amount}. Treasury accounts allow
     * overdraft, so this is always true — the old {@code CurrencyHoldEvent} path was
     * unconditionally permissive; kept as a named check for the call sites.
     */
    public boolean canHold(UUID account, BigDecimal amount) {
        return true;
    }

    /**
     * Settle the money leg of a trade: move {@code amount} from the buyer to the
     * seller. {@code initiator} is the player who clicked the shop and {@code partner}
     * the shop-owner account; {@code buy} selects the direction (a buy pays the owner,
     * a sell pays the client). Returns whether settlement succeeded — {@code false}
     * tells the caller to reverse the goods leg.
     *
     * <p>When both sides are real accounts (the common case) this is a single direct
     * buyer→seller {@link TreasuryApi#transfer} — atomic, so there's no SYSTEM-account
     * hop and no manual rollback. Admin shops have no real account, so they use the
     * ChestShop SYSTEM account as a money sink (paying into an admin shop) or source
     * (an admin shop paying out); an admin shop is first redirected to the configured
     * server-economy account when there is one. Replaces {@code CurrencyTransferEvent}
     * + {@code TreasuryListener.onCurrencyTransfer} + {@code ServerAccountCorrector}.
     */
    public boolean settle(BigDecimal amount, Player initiator, UUID partner, boolean buy, TransactionEvent txn) {
        UUID resolvedPartner = partner;
        if (ChestShop.accounts().isAdminShop(resolvedPartner) && !ChestShop.accounts().isServerEconomyAccount(resolvedPartner)) {
            Account server = ChestShop.accounts().getServerEconomyAccount();
            if (server != null) {
                resolvedPartner = server.getUuid();
            }
        }

        UUID sender = buy ? initiator.getUniqueId() : resolvedPartner;
        UUID receiver = buy ? resolvedPartner : initiator.getUniqueId();
        boolean senderIsAdmin = ChestShop.accounts().isAdminShop(sender);
        boolean receiverIsAdmin = ChestShop.accounts().isAdminShop(receiver);

        String memo = buildTransferMessage(txn);

        int receiverAccountId = -1;
        try {
            if (!senderIsAdmin && !receiverIsAdmin) {
                // Direct buyer → seller transfer — atomic, no SYSTEM hop, no rollback.
                int senderAccountId = resolveAccountId(sender);
                receiverAccountId = resolveAccountId(receiver);
                transfer(senderAccountId, receiverAccountId, amount, memo, transferInitiator(sender), sender, receiver);
            } else if (receiverIsAdmin && !senderIsAdmin) {
                // Paying into an admin shop with no server account → money sink (→ SYSTEM).
                int senderAccountId = resolveAccountId(sender);
                transfer(senderAccountId, systemAccountId, amount, memo, transferInitiator(sender), sender, receiver);
            } else if (senderIsAdmin && !receiverIsAdmin) {
                // An admin shop with no server account pays out → money source (SYSTEM →).
                receiverAccountId = resolveAccountId(receiver);
                transfer(systemAccountId, receiverAccountId, amount, memo, CHESTSHOP_SYSTEM_UUID, sender, receiver);
            }
            // both admin → no real accounts, nothing moves
        } catch (Exception e) {
            ChestShop.getBukkitLogger().log(Level.WARNING,
                    "Treasury: Could not settle " + amount + " from " + sender + " to " + receiver, e);
            return false;
        }

        // Sales tax — best-effort; the primary transfer has already committed.
        if (taxApi != null && !receiverIsAdmin && receiverAccountId > 0) {
            collectSalesTax(receiverAccountId, receiver, amount, resolvedPartner, initiator, memo);
        }
        return true;
    }

    private void transfer(int from, int to, BigDecimal amount, String memo, UUID initiator, UUID a, UUID b) {
        byte[] dedupKey = Idempotency.sha256("chestshop:settle:" + a + ":" + b + ":" + amount + ":" + System.nanoTime());
        treasury.transfer(new TransferRequest(from, to, amount, memo, initiator, null, "ChestShop", dedupKey));
    }

    private UUID transferInitiator(UUID sender) {
        // A business account authorises through the SYSTEM account; a personal account authorises itself.
        return isBusinessUuid(sender) ? CHESTSHOP_SYSTEM_UUID : sender;
    }

    private void collectSalesTax(int receiverAccountId, UUID receiver, BigDecimal amount, UUID partner, Player initiator, String memo) {
        BigDecimal rate = resolveTaxRate(partner);
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (initiator != null && Permission.has(initiator, Permission.NO_BUY_TAX)) {
            return;
        }
        try {
            UUID initiatorUuid = isBusinessUuid(receiver) ? CHESTSHOP_SYSTEM_UUID : receiver;
            byte[] dedupKey = Idempotency.sha256("chestshop:tax:" + receiver + ":" + amount + ":" + System.nanoTime());
            TaxResult result = taxApi.collectRateTax(
                    receiverAccountId, amount, rate, "chestshop-sales-tax",
                    "ChestShop sales tax (" + rate.movePointRight(2).stripTrailingZeros().toPlainString()
                            + "% of " + amount + ") — " + memo,
                    initiatorUuid, "ChestShop", dedupKey);
            if (result instanceof TaxResult.Failed f) {
                ChestShop.getBukkitLogger().warning(
                        "Treasury: sales-tax collection failed for accountId=" + receiverAccountId + ": " + f.errorMessage());
            }
        } catch (Exception e) {
            ChestShop.getBukkitLogger().log(Level.WARNING,
                    "Treasury: sales-tax collection threw for receiver " + receiver, e);
        }
    }

    /** Tax rate as a fraction: {@code SERVER_TAX_AMOUNT} for admin/server counterparties, else {@code TAX_AMOUNT}. */
    private static BigDecimal resolveTaxRate(UUID partner) {
        double pct = (partner != null
                && (ChestShop.accounts().isAdminShop(partner) || ChestShop.accounts().isServerEconomyAccount(partner)))
                ? Properties.SERVER_TAX_AMOUNT
                : Properties.TAX_AMOUNT;
        if (pct == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(pct).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    }

    private static String buildTransferMessage(TransactionEvent txn) {
        int totalItems = Arrays.stream(txn.getStock()).mapToInt(ItemStack::getAmount).sum();
        String itemName = transferItemName(txn);
        String ownerName = txn.getOwnerAccount().getName();
        String clientName = txn.getClient().getName();
        boolean isBuy = txn.getTransactionType() == TransactionEvent.TransactionType.BUY;

        String prefix = clientName + (isBuy ? " bought x" : " sold x") + totalItems + " ";
        String suffix = (isBuy ? " from " : " to ") + ownerName;
        int available = MAX_MESSAGE_LENGTH - prefix.length() - suffix.length();

        if (available < 1) {
            return (prefix + suffix).substring(0, MAX_MESSAGE_LENGTH);
        }
        if (itemName.length() > available) {
            itemName = itemName.substring(0, available);
        }
        return prefix + itemName + suffix;
    }

    private static String transferItemName(TransactionEvent txn) {
        ItemStack[] stock = txn.getStock();
        if (stock != null && stock.length > 0 && stock[0] != null) {
            try {
                String code = ItemUtil.getName(stock[0], 0);
                if (code != null && !code.isBlank()) {
                    return code;
                }
            } catch (RuntimeException ignored) {
                // Code didn't round-trip — fall back to the sign line below.
            }
        }
        return ChestShopSign.getItem(txn.getSign());
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
