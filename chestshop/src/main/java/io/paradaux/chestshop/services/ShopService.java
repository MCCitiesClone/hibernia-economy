package io.paradaux.chestshop.services;

import com.google.inject.Singleton;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.economy.Economy;
import io.paradaux.chestshop.events.AccountQueryEvent;
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

        AccountQueryEvent query = new AccountQueryEvent(ChestShopSign.getOwner(sign));
        ChestShop.callEvent(query);
        Account account = query.getAccount();
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
