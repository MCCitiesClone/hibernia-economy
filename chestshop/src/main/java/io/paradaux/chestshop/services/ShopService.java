package io.paradaux.chestshop.services;

import com.google.inject.Singleton;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.economy.Economy;
import io.paradaux.chestshop.events.PreShopCreationEvent;
import io.paradaux.chestshop.listeners.preshopcreation.ChestChecker;
import io.paradaux.chestshop.listeners.preshopcreation.CreationFeeGetter;
import io.paradaux.chestshop.listeners.preshopcreation.ErrorMessageSender;
import io.paradaux.chestshop.listeners.preshopcreation.FreePriceChecker;
import io.paradaux.chestshop.listeners.preshopcreation.ItemChecker;
import io.paradaux.chestshop.listeners.preshopcreation.MoneyChecker;
import io.paradaux.chestshop.listeners.preshopcreation.NameChecker;
import io.paradaux.chestshop.listeners.preshopcreation.PermissionChecker;
import io.paradaux.chestshop.listeners.preshopcreation.PriceChecker;
import io.paradaux.chestshop.listeners.preshopcreation.PriceRatioChecker;
import io.paradaux.chestshop.listeners.preshopcreation.QuantityChecker;
import io.paradaux.chestshop.listeners.preshopcreation.TerrainChecker;
import io.paradaux.chestshop.listeners.modules.StockCounterModule;
import io.paradaux.chestshop.signs.ChestShopSign;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.math.BigDecimal;

import static io.paradaux.chestshop.Permission.NOFEE;
import static io.paradaux.chestshop.signs.ChestShopSign.AUTOFILL_CODE;

/**
 * Owns the shop-lifecycle <em>money</em> logic: the creation fee charged when a shop
 * is made and the refund issued when one is removed, including the mirrored
 * server-economy account movement. This was split across two listeners
 * ({@code CreationFeeGetter} and {@code ShopRefundListener}); the listeners are now
 * thin entrypoints that delegate here, the same shape as
 * {@link TransactionService} owning the money leg of a trade.
 *
 * <p>The money itself still flows through the {@code Currency*Event}s — those are
 * Treasury integration points (the ledger settles them), so this service fires them
 * rather than moving money directly. The shop-creation <em>validation</em> pipeline
 * and the market-DB sync remain their own listeners (genuine event coordination /
 * cross-plugin integration); collapsing those is a later phase.
 *
 * <p>Shops themselves have no ChestShop-owned persistence — they are sign + chest
 * world state — so there is no repository here, only this service.
 */
@Singleton
public class ShopService {

    /**
     * Run a shop-sign creation through the validation steps and return the result.
     * The former {@code preshopcreation} listeners are invoked here in the exact
     * priority + registration order they used to fire as events, over a plain
     * {@link PreShopCreationEvent} carrier — replacing the internal event dispatch.
     * The genuine integration events they fire (ItemParse, AccountQuery/Access,
     * BuildPermission) still fire. The two former {@code ignoreCancelled=true} steps
     * (the creation-fee charge and NameChecker's second pass) run only while the
     * creation is still un-cancelled, matching the old behaviour.
     */
    public PreShopCreationEvent create(Player player, Sign sign, String[] signLines) {
        PreShopCreationEvent ctx = new PreShopCreationEvent(player, sign, signLines);

        // priority LOWEST
        ItemChecker.onPreShopCreation(ctx);
        PriceChecker.onPreShopCreation(ctx);
        QuantityChecker.onPreShopCreation(ctx);
        // priority LOW
        ChestChecker.onPreShopCreation(ctx);
        NameChecker.onPreShopCreation(ctx);
        // priority NORMAL
        MoneyChecker.onPreShopCreation(ctx);
        FreePriceChecker.onPreShopCreation(ctx);
        TerrainChecker.onPreShopCreation(ctx);
        // priority HIGH
        if (Properties.BLOCK_SHOPS_WITH_SELL_PRICE_HIGHER_THAN_BUY_PRICE) {
            PriceRatioChecker.onPreShopCreation(ctx);
        }
        PermissionChecker.onPreShopCreation(ctx);
        if (!ctx.isCancelled()) { // CreationFeeGetter ran @HIGH with ignoreCancelled=true
            CreationFeeGetter.onShopCreation(ctx);
        }
        // StockCounterModule ran @HIGH after CreationFeeGetter with the default
        // ignoreCancelled=false, so it fires regardless: it normalises the quantity
        // line and seeds the stock counter onto the sign.
        StockCounterModule.onPreShopCreation(ctx);
        // priority HIGHEST — NameChecker's second pass (ignoreCancelled=true)
        if (!ctx.isCancelled()) {
            NameChecker.onPreShopCreationHighest(ctx);
        }
        // priority MONITOR
        ErrorMessageSender.onPreShopCreation(ctx);

        return ctx;
    }

    /**
     * Charge the configured creation fee to {@code player} for a shop with the given
     * sign lines. Returns {@code true} if creation may proceed (fee paid, or none is
     * due — zero price, admin shop, or the {@code NOFEE} permission), {@code false} if
     * the player could not afford it (the caller should fail the creation).
     */
    public boolean chargeCreationFee(Player player, String[] signLines) {
        BigDecimal price = Properties.SHOP_CREATION_PRICE;

        if (price.compareTo(BigDecimal.ZERO) == 0
                || ChestShopSign.isAdminShop(signLines)
                || Permission.has(player, NOFEE)) {
            return true;
        }

        if (!ChestShop.economy().withdraw(player.getUniqueId(), price, player.getWorld())) {
            return false;
        }

        creditServerEconomy(price, player.getWorld());
        ChestShop.message().send(player, "chestshop.SHOP_FEE_PAID", "amount", Economy.formatBalance(price));
        return true;
    }

    /**
     * Refund the configured removal price to the shop's owner when {@code destroyer}
     * breaks it, mirroring the deduction from the server-economy account. No-ops when
     * there is no refund due (no destroyer, {@code NOFEE}, zero price, an autofill
     * placeholder sign, or an unknown owner account).
     */
    public void refundOnRemoval(Player destroyer, Sign sign) {
        BigDecimal refund = Properties.SHOP_REFUND_PRICE;

        if (destroyer == null || Permission.has(destroyer, NOFEE) || refund.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        if (ChatColor.stripColor(ChestShopSign.getItem(sign)).equals(AUTOFILL_CODE)) {
            return;
        }

        Account account = ChestShop.accounts().resolveAccount(ChestShopSign.getOwner(sign));
        if (account == null) {
            return;
        }

        ChestShop.economy().deposit(account.getUuid(), refund, sign.getWorld());

        debitServerEconomy(refund, sign.getWorld());
        ChestShop.message().send(destroyer, "chestshop.SHOP_REFUNDED", "amount", Economy.formatBalance(refund));
    }

    /** Mirror a collected creation fee into the server-economy account, if one is configured. */
    private void creditServerEconomy(BigDecimal amount, World world) {
        Account serverAccount = ChestShop.accounts().getServerEconomyAccount();
        if (serverAccount != null) {
            ChestShop.economy().deposit(serverAccount.getUuid(), amount, world);
        }
    }

    /** Mirror an issued refund out of the server-economy account, if one is configured. */
    private void debitServerEconomy(BigDecimal amount, World world) {
        Account serverAccount = ChestShop.accounts().getServerEconomyAccount();
        if (serverAccount != null) {
            ChestShop.economy().withdraw(serverAccount.getUuid(), amount, world);
        }
    }
}
