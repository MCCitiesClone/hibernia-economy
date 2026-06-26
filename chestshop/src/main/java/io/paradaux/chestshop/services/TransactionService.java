package io.paradaux.chestshop.services;

import com.google.inject.Singleton;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.PreTransactionEvent;
import io.paradaux.chestshop.events.TransactionEvent;
import io.paradaux.chestshop.listeners.pretransaction.AmountAndPriceChecker;
import io.paradaux.chestshop.listeners.pretransaction.CreativeModeIgnorer;
import io.paradaux.chestshop.listeners.pretransaction.ErrorMessageSender;
import io.paradaux.chestshop.listeners.pretransaction.FreeShopBreaker;
import io.paradaux.chestshop.listeners.pretransaction.InvalidNameIgnorer;
import io.paradaux.chestshop.listeners.pretransaction.PartialTransactionModule;
import io.paradaux.chestshop.listeners.pretransaction.PermissionChecker;
import io.paradaux.chestshop.listeners.pretransaction.PriceValidator;
import io.paradaux.chestshop.listeners.pretransaction.ShopValidator;
import io.paradaux.chestshop.listeners.pretransaction.StockFittingChecker;
import io.paradaux.chestshop.listeners.modules.MetricsModule;
import io.paradaux.chestshop.listeners.modules.StockCounterModule;
import io.paradaux.chestshop.listeners.posttransaction.EmptyShopDeleter;
import io.paradaux.chestshop.listeners.posttransaction.TransactionLogger;
import io.paradaux.chestshop.listeners.posttransaction.TransactionMessageSender;
import io.paradaux.chestshop.market.MarketListener;
import io.paradaux.chestshop.signs.RestrictedSign;
import io.paradaux.chestshop.utils.ImplementationAdapter;
import io.paradaux.chestshop.utils.InventoryUtil;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import static io.paradaux.chestshop.events.TransactionEvent.TransactionType.BUY;

/**
 * Owns the atomic core of a shop trade — the goods leg and the money leg — in one
 * place, with an explicit ordering and rollback, instead of spreading it across two
 * priority-ordered listeners ({@code ItemManager} @NORMAL + {@code EconomicModule}
 * @HIGH) that coordinated through a shared mutable {@link TransactionEvent}. This is
 * the atomicity guarantee ADT-4 was about, now readable and unit-testable as a
 * single method.
 *
 * <p>The money leg settles directly through {@link ChestShop#economy()} (a single
 * buyer→seller {@code TreasuryApi} transfer); the goods are reversed if it fails, so
 * the trade is all-or-nothing. Downstream post-transaction reactions (empty-shop
 * cleanup, messages, logging) remain their own listeners for now.
 */
@Singleton
public class TransactionService {

    /**
     * Run a shop interaction through the pre-transaction validation steps, cancelling
     * the {@link PreTransactionEvent} (and adjusting its stock/price) as needed. The
     * former {@code pretransaction} validators are invoked here in their exact former
     * priority + registration order; each self-guards on {@code isCancelled} (except
     * AmountAndPriceChecker, which ran with {@code ignoreCancelled} and is guarded
     * here). PartialTransactionModule and AmountAndPriceChecker are config-selected
     * alternatives ({@code ALLOW_PARTIAL_TRANSACTIONS}), exactly as registered.
     */
    public void validate(PreTransactionEvent ctx) {
        // priority LOWEST
        InvalidNameIgnorer.onPreTransaction(ctx);
        CreativeModeIgnorer.onPreTransaction(ctx);
        FreeShopBreaker.onPreTransaction(ctx);
        PriceValidator.onPriceCheck(ctx);
        ShopValidator.onCheck(ctx);
        // priority LOW — partial fulfilment (registered instead of AmountAndPriceChecker)
        if (Properties.ALLOW_PARTIAL_TRANSACTIONS) {
            PartialTransactionModule.onPreBuyTransaction(ctx);
            PartialTransactionModule.onPreSellTransaction(ctx);
        }
        // RestrictedSign ran @LOW (after PartialTransactionModule); it self-guards on
        // isCancelled and gates trade access to [restricted] shops.
        RestrictedSign.onPreTransaction(ctx);
        // priority NORMAL
        if (!Properties.ALLOW_PARTIAL_TRANSACTIONS && !ctx.isCancelled()) {
            // AmountAndPriceChecker ran with ignoreCancelled=true
            AmountAndPriceChecker.onBuyItemCheck(ctx);
            AmountAndPriceChecker.onSellItemCheck(ctx);
        }
        PermissionChecker.onPermissionCheck(ctx);
        StockFittingChecker.onSellCheck(ctx);
        StockFittingChecker.onBuyCheck(ctx);
        // priority MONITOR
        ErrorMessageSender.onMessage(ctx);
    }

    /**
     * Run the post-transaction pipeline for a validated trade, in the exact priority +
     * registration order the former {@link TransactionEvent} listeners fired in —
     * replacing the Bukkit event dispatch with ordered service calls:
     * <ol>
     *   <li>NORMAL — {@link #execute} the goods + money legs atomically (may cancel);</li>
     *   <li>HIGH — {@code StockCounterModule} refreshes the sign counter (ran with the
     *       default {@code ignoreCancelled=false}, so it fires even on a cancelled trade);</li>
     *   <li>HIGHEST — {@code EmptyShopDeleter} removes a now-empty shop;</li>
     *   <li>MONITOR — legacy business-sign migration, market sync, transaction log,
     *       buyer/seller messages, metrics.</li>
     * </ol>
     * Everything from HIGHEST on ran {@code ignoreCancelled=true}, so a cancelled trade
     * (failed goods or money leg) stops the pipeline after the stock counter.
     */
    public void process(TransactionEvent event) {
        // NORMAL (ignoreCancelled=true, but this is the first reaction so the event is
        // never already cancelled): atomic goods + money. May cancel on failure.
        execute(event);

        // HIGH (ignoreCancelled=false → runs regardless of cancellation).
        StockCounterModule.onTransaction(event);

        // HIGHEST + MONITOR all ran ignoreCancelled=true.
        if (event.isCancelled()) {
            return;
        }
        EmptyShopDeleter.onTransaction(event);

        // MONITOR reactions, in registration order.
        ChestShop.economy().migrateLegacyBusinessSign(event);
        MarketListener.onTransaction(event);
        TransactionLogger.onTransaction(event);
        TransactionMessageSender.onTransaction(event);
        MetricsModule.onTransaction(event);
    }

    /**
     * Run a validated transaction to completion: move the goods, then settle the
     * money — reversing the goods if the (pre-validated, so exceptional) money leg
     * fails, so the trade is all-or-nothing.
     */
    public void execute(TransactionEvent event) {
        boolean buy = event.getTransactionType() == BUY;

        // Goods leg (was ItemManager @NORMAL): BUY moves owner → client, SELL the
        // reverse. transferItems reverts both inventories on any shortfall.
        Inventory from = buy ? event.getOwnerInventory() : event.getClientInventory();
        Inventory to = buy ? event.getClientInventory() : event.getOwnerInventory();
        if (!transferItems(from, to, event.getStock())) {
            cancelOnShortfall(event);
            return;
        }

        // Money leg: settle directly through the EconomyService (a direct buyer→seller
        // TreasuryApi transfer), replacing the old CurrencyTransferEvent fan-out.
        if (!ChestShop.economy().settle(event.getExactPrice(), event.getClient(),
                event.getOwnerAccount().getUuid(), buy, event)) {
            // The goods already moved but the money didn't settle — put the goods
            // back and cancel, keeping the trade atomic.
            reverseTransfer(event);
            event.setCancelled(true);
        }
    }

    private static void cancelOnShortfall(TransactionEvent event) {
        event.setCancelled(true);
        ChestShop.getBukkitLogger().severe(
                "Aborted a ChestShop transaction at "
                + (event.getSign() != null ? event.getSign().getLocation() : "<unknown location>")
                + ": the goods could not be transferred in full, so no money was moved and both "
                + "inventories were left untouched. This should not normally happen — the "
                + "PreTransaction checks validate stock and space beforehand.");
    }

    /**
     * Move every stack from {@code source} to {@code target}. Both inventories are
     * snapshotted first; on any shortfall both are restored and {@code false} is
     * returned so the caller can cancel before money moves.
     */
    static boolean transferItems(Inventory source, Inventory target, ItemStack[] items) {
        ItemStack[] sourceSnapshot = cloneContents(source);
        ItemStack[] targetSnapshot = cloneContents(target);

        int leftOver = 0;
        for (ItemStack item : items) {
            leftOver += Properties.STACK_TO_64
                    ? InventoryUtil.transfer(item, source, target, 64)
                    : InventoryUtil.transfer(item, source, target);
        }

        if (leftOver > 0) {
            source.setContents(sourceSnapshot);
            target.setContents(targetSnapshot);
            return false;
        }

        update(source.getHolder());
        update(target.getHolder());
        return true;
    }

    /** Reverse a completed goods move after a failed money leg, keeping the trade atomic. */
    static void reverseTransfer(TransactionEvent event) {
        boolean reversed = event.getTransactionType() == BUY
                ? transferItems(event.getClientInventory(), event.getOwnerInventory(), event.getStock())
                : transferItems(event.getOwnerInventory(), event.getClientInventory(), event.getStock());

        if (!reversed) {
            ChestShop.getBukkitLogger().severe(
                    "Failed to reverse the goods of a ChestShop transaction at "
                    + (event.getSign() != null ? event.getSign().getLocation() : "<unknown location>")
                    + " after the money leg failed. The trade may be in an inconsistent state.");
        }
    }

    private static ItemStack[] cloneContents(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private static void update(InventoryHolder holder) {
        if (holder instanceof Player player) {
            player.updateInventory();
        } else if (holder instanceof BlockState blockState) {
            blockState.update();
        } else if (holder instanceof DoubleChest doubleChest) {
            update(ImplementationAdapter.getLeftSide(doubleChest, false));
            update(ImplementationAdapter.getRightSide(doubleChest, false));
        }
    }
}
