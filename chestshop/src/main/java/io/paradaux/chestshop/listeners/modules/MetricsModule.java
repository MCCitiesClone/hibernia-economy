package io.paradaux.chestshop.listeners.modules;

import io.paradaux.chestshop.utils.NumberUtil;
import io.paradaux.chestshop.context.TransactionContext;
import org.bukkit.inventory.ItemStack;

/**
 * Rolling buy/sell transaction + item counters surfaced by the {@code /csmetrics}
 * command and bStats. Formerly a {@code @MONITOR(ignoreCancelled=true)}
 * {@link TransactionContext} listener; {@link #onTransaction} is now invoked directly by
 * {@link io.paradaux.chestshop.services.TransactionService#process}.
 *
 * @author Acrobot
 */
public class MetricsModule {

    private static final long RESET_MINUTES = 30;

    private static long lastReset = System.currentTimeMillis();

    private static int buyTransactionsLast = -1;
    private static int sellTransactionsLast = -1;
    private static long buyTransactionsCurrent = 0;
    private static long sellTransactionsCurrent = 0;

    private static int boughtItemsLast = -1;
    private static int soldItemsLast = -1;
    private static long boughtItemsCurrent = 0;
    private static long soldItemsCurrent = 0;

    public static void onTransaction(final TransactionContext event) {
        checkReset();
        switch (event.getTransactionType()) {
            case BUY:
                buyTransactionsCurrent++;
                for (ItemStack itemStack : event.getStock()) {
                    boughtItemsCurrent += itemStack.getAmount();
                }
                break;
            case SELL:
                sellTransactionsCurrent++;
                for (ItemStack itemStack : event.getStock()) {
                    soldItemsCurrent += itemStack.getAmount();
                }
                break;
        }
    }

    public static int getBuyTransactions() {
        checkReset();
        return buyTransactionsLast > -1 ? buyTransactionsLast : NumberUtil.toInt(buyTransactionsCurrent);
    }

    public static int getSellTransactions() {
        checkReset();
        return sellTransactionsLast > -1 ? sellTransactionsLast : NumberUtil.toInt(sellTransactionsCurrent);
    }

    public static int getTotalTransactions() {
        checkReset();
        return getBuyTransactions() + getSellTransactions();
    }

    public static int getBoughtItemsCount() {
        checkReset();
        return boughtItemsLast > -1 ? boughtItemsLast : NumberUtil.toInt(boughtItemsCurrent);
    }

    public static int getSoldItemsCount() {
        checkReset();
        return soldItemsLast > -1 ? soldItemsLast : NumberUtil.toInt(soldItemsCurrent);
    }

    public static int getTotalItemsCount() {
        checkReset();
        return getBoughtItemsCount() + getSoldItemsCount();
    }

    private static void checkReset() {
        if (lastReset + RESET_MINUTES * 60 * 1000 < System.currentTimeMillis()) {
            lastReset = System.currentTimeMillis();
            buyTransactionsLast = NumberUtil.toInt(buyTransactionsCurrent);
            buyTransactionsCurrent = 0;
            sellTransactionsLast = NumberUtil.toInt(sellTransactionsCurrent);
            sellTransactionsCurrent = 0;
            
            boughtItemsLast = NumberUtil.toInt(boughtItemsCurrent);
            boughtItemsCurrent = 0;
            soldItemsLast = NumberUtil.toInt(soldItemsCurrent);
            soldItemsCurrent = 0;
        }
    }
}
