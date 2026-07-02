package io.paradaux.chestshop.services;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.permission.Permissions;
import io.paradaux.chestshop.model.config.ChestShopConfiguration;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.chestshop.model.Account;
import io.paradaux.chestshop.context.PreTransactionContext;
import io.paradaux.chestshop.context.ShopDestroyedContext;
import io.paradaux.chestshop.context.TransactionContext;
import io.paradaux.chestshop.listeners.SignBreak;
import io.paradaux.chestshop.listeners.StockCounterModule;
import io.paradaux.chestshop.market.MarketListener;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.signs.RestrictedSign;
import io.paradaux.chestshop.utils.ImplementationAdapter;
import io.paradaux.chestshop.utils.InventoryUtil;
import io.paradaux.chestshop.utils.LocationUtil;
import io.paradaux.chestshop.plugins.ShowItemHook;
import io.paradaux.chestshop.utils.PriceUtil;
import io.paradaux.chestshop.utils.ShopBlockUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static io.paradaux.chestshop.context.PreTransactionContext.TransactionOutcome.CLIENT_DEPOSIT_FAILED;
import static io.paradaux.chestshop.context.PreTransactionContext.TransactionOutcome.CLIENT_DOES_NOT_HAVE_ENOUGH_MONEY;
import static io.paradaux.chestshop.context.PreTransactionContext.TransactionOutcome.CLIENT_DOES_NOT_HAVE_PERMISSION;
import static io.paradaux.chestshop.context.PreTransactionContext.TransactionOutcome.CREATIVE_MODE_PROTECTION;
import static io.paradaux.chestshop.context.PreTransactionContext.TransactionOutcome.INVALID_CLIENT_NAME;
import static io.paradaux.chestshop.context.PreTransactionContext.TransactionOutcome.INVALID_SHOP;
import static io.paradaux.chestshop.context.PreTransactionContext.TransactionOutcome.NOT_ENOUGH_SPACE_IN_CHEST;
import static io.paradaux.chestshop.context.PreTransactionContext.TransactionOutcome.NOT_ENOUGH_SPACE_IN_INVENTORY;
import static io.paradaux.chestshop.context.PreTransactionContext.TransactionOutcome.NOT_ENOUGH_STOCK_IN_CHEST;
import static io.paradaux.chestshop.context.PreTransactionContext.TransactionOutcome.NOT_ENOUGH_STOCK_IN_INVENTORY;
import static io.paradaux.chestshop.context.PreTransactionContext.TransactionOutcome.SHOP_DEPOSIT_FAILED;
import static io.paradaux.chestshop.context.PreTransactionContext.TransactionOutcome.SHOP_DOES_NOT_BUY_THIS_ITEM;
import static io.paradaux.chestshop.context.PreTransactionContext.TransactionOutcome.SHOP_DOES_NOT_HAVE_ENOUGH_MONEY;
import static io.paradaux.chestshop.context.PreTransactionContext.TransactionOutcome.SHOP_DOES_NOT_SELL_THIS_ITEM;
import static io.paradaux.chestshop.context.TransactionContext.TransactionType.BUY;
import static io.paradaux.chestshop.context.TransactionContext.TransactionType.SELL;
import static io.paradaux.chestshop.utils.PriceUtil.NO_PRICE;

/**
 * Owns a shop trade end to end: pre-trade validation ({@link #validate}), the atomic
 * goods+money settlement ({@link #execute}), and the post-trade reactions
 * ({@link #process}). This replaces ChestShop's old "Bukkit events as middleware"
 * design — a {@code PreTransactionContext}/{@code TransactionContext} fanned out to ~20
 * priority-ordered listener classes coordinating through a mutable bag — with one
 * service whose steps are ordinary, ordered private methods (PAR-282). It is the
 * atomicity guarantee ADT-4 asked for, readable and unit-testable in one place.
 *
 * <p>The money leg settles directly through {@link ChestShop#economy()} (a single
 * buyer→seller {@code TreasuryApi} transfer); the goods are reversed if it fails, so a
 * trade is all-or-nothing. The genuine cross-cutting hooks (market-DB sync, bStats,
 * stock counter) and the cross-plugin sign listener {@code RestrictedSign} stay.
 */
@Singleton
public class TransactionService {

    /** Cooldown table for owner out-of-stock / full-shop notifications: (owner, msgKey) → last-sent ms. */
    private final Table<UUID, String, Long> notificationCooldowns = HashBasedTable.create();

    // Cache the compiled valid-playername pattern, rebuilt only when the config string
    // changes, instead of recompiling the regex on every click (per-click hot path).
    private volatile String cachedPlayernameRegexp;
    private volatile Pattern cachedPlayernamePattern;

    private final EconomyService economy;
    private final ShopService shops;
    private final AccountService accounts;
    private final SignBreak signBreak;
    private final StockCounterModule stockCounter;
    private final Message message;
    private final ItemService items;
    private final MarketListener market;
    private final ChestShopConfiguration config;
    private final ChestShopSign chestShopSign;
    private final ShopBlockUtil shopBlockUtil;
    private final InventoryService inventoryService;
    private final MaterialService materialService;
    private final ShowItemHook showItem;

    @Inject
    public TransactionService(EconomyService economy, ShopService shops, AccountService accounts, SignBreak signBreak, StockCounterModule stockCounter, Message message, ItemService items, MarketListener market,
                              ChestShopConfiguration config, ChestShopSign chestShopSign, ShopBlockUtil shopBlockUtil, InventoryService inventoryService, MaterialService materialService, ShowItemHook showItem) {
        this.economy = economy;
        this.shops = shops;
        this.accounts = accounts;
        this.signBreak = signBreak;
        this.stockCounter = stockCounter;
        this.message = message;
        this.items = items;
        this.market = market;
        this.config = config;
        this.chestShopSign = chestShopSign;
        this.shopBlockUtil = shopBlockUtil;
        this.inventoryService = inventoryService;
        this.materialService = materialService;
        this.showItem = showItem;
    }

    private static final String BUY_LOG = "%1$s bought %2$s for %3$.2f from %4$s at %5$s";
    private static final String SELL_LOG = "%1$s sold %2$s for %3$.2f to %4$s at %5$s";

    // ===================== pre-trade validation =====================

    /**
     * Run a shop interaction through the pre-transaction validation steps, mutating the
     * {@link PreTransactionContext} context (cancelling it, or adjusting its stock/price)
     * as needed. The steps run in the exact order the former priority-ordered validators
     * fired; each self-guards on {@code isCancelled} where the original did.
     * {@code PartialTransactionModule} and the whole-amount {@code checkFundsAndStock}
     * are config-selected alternatives ({@code ALLOW_PARTIAL_TRANSACTIONS}).
     */
    public void validate(PreTransactionContext ctx) {
        rejectInvalidClientName(ctx);
        rejectCreativeMode(ctx);
        flagFreeShop(ctx);
        rejectMissingPrice(ctx);
        rejectInvalidShop(ctx);

        if (config.isAllowPartialTransactions()) {
            adjustPartialBuy(ctx);
            adjustPartialSell(ctx);
        }

        // RestrictedSign is a genuine cross-plugin sign listener; its pre-trade access
        // gate stays where it lives and is invoked here in the former @LOW slot.
        RestrictedSign.onPreTransaction(ctx);

        if (!config.isAllowPartialTransactions() && !ctx.isCancelled()) {
            checkFundsAndStock(ctx);
        }
        checkPermissions(ctx);
        checkStockFits(ctx);

        sendErrorMessage(ctx);

        // Side effects run only after validation has fully concluded: a legacy free shop
        // flagged above is removed here, exactly once, so the validation steps themselves
        // stay pure outcome-setters (ADT-139).
        removeFlaggedFreeShop(ctx);
    }

    private Pattern playernamePattern() {
        String regexp = config.getValidPlayernameRegexp();
        if (!regexp.equals(cachedPlayernameRegexp)) {
            cachedPlayernamePattern = Pattern.compile(regexp);
            cachedPlayernameRegexp = regexp;
        }
        return cachedPlayernamePattern;
    }

    /** Block trades by an admin-shop name or a client whose name fails the valid-name regex. */
    private void rejectInvalidClientName(PreTransactionContext ctx) {
        if (ctx.isCancelled()) {
            return;
        }
        String name = ctx.getClient().getName();
        if (chestShopSign.isAdminShop(name) || !playernamePattern().matcher(name).matches()) {
            ctx.setCancelled(INVALID_CLIENT_NAME);
        }
    }

    /** Optionally block creative-mode players from trading. */
    private void rejectCreativeMode(PreTransactionContext ctx) {
        if (ctx.isCancelled()) {
            return;
        }
        if (config.isIgnoreCreativeMode() && ctx.getClient().getGameMode() == GameMode.CREATIVE) {
            ctx.setCancelled(CREATIVE_MODE_PROTECTION);
        }
    }

    /**
     * Detect a free (price-0) shop that pre-dates the no-free-shops rule, unless
     * {@code ALLOW_FREE_SHOPS} permits them (PAR-88). Pure: it only cancels with
     * INVALID_SHOP (which sends the "invalid shop" message) and flags the shop for
     * removal — the actual de-registration + block break happens once, after validation,
     * in {@link #removeFlaggedFreeShop} (ADT-139).
     */
    private void flagFreeShop(PreTransactionContext ctx) {
        if (config.isAllowFreeShops() || ctx.isCancelled()) {
            return;
        }
        Sign sign = ctx.getSign();
        if (sign == null) {
            return;
        }
        String price = ChestShopSign.getPrice(sign);
        if (!isFree(PriceUtil.getExactBuyPrice(price)) && !isFree(PriceUtil.getExactSellPrice(price))) {
            return;
        }
        ctx.setCancelled(INVALID_SHOP);
        ctx.setRejectedAsFreeShop(true);
    }

    /** Remove a free shop flagged by {@link #flagFreeShop}, after validation has concluded (ADT-139). */
    private void removeFlaggedFreeShop(PreTransactionContext ctx) {
        if (!ctx.isRejectedAsFreeShop()) {
            return;
        }
        Sign sign = ctx.getSign();
        if (sign == null) {
            return;
        }
        signBreak.sendShopDestroyed(sign, ctx.getClient());
        sign.getBlock().breakNaturally();
    }

    private static boolean isFree(BigDecimal price) {
        return price.compareTo(PriceUtil.FREE) == 0;
    }

    /** Cancel if the shop has no price for the requested direction. */
    private void rejectMissingPrice(PreTransactionContext ctx) {
        if (ctx.isCancelled()) {
            return;
        }
        if (ctx.getExactPrice().equals(NO_PRICE)) {
            ctx.setCancelled(ctx.getTransactionType() == BUY ? SHOP_DOES_NOT_SELL_THIS_ITEM : SHOP_DOES_NOT_BUY_THIS_ITEM);
        }
    }

    /** Cancel if the trade has no stock, or a non-admin shop has no backing container. */
    private void rejectInvalidShop(PreTransactionContext ctx) {
        if (ctx.isCancelled()) {
            return;
        }
        if (isEmpty(ctx.getStock())) {
            ctx.setCancelled(INVALID_SHOP);
            return;
        }
        if (!chestShopSign.isAdminShop(ctx.getSign()) && ctx.getOwnerInventory() == null) {
            ctx.setCancelled(INVALID_SHOP);
        }
    }

    private static boolean isEmpty(ItemStack[] array) {
        for (ItemStack element : array) {
            if (element != null) {
                return false;
            }
        }
        return true;
    }

    /** Whole-amount funds + stock check (used when partial transactions are disabled). */
    private void checkFundsAndStock(PreTransactionContext ctx) {
        if (ctx.getTransactionType() == BUY) {
            if (!economy.hasFunds(ctx.getClient().getUniqueId(), ctx.getExactPrice())) {
                ctx.setCancelled(CLIENT_DOES_NOT_HAVE_ENOUGH_MONEY);
                return;
            }
            if (!inventoryService.hasItems(ctx.getStock(), ctx.getOwnerInventory())) {
                ctx.setCancelled(NOT_ENOUGH_STOCK_IN_CHEST);
            }
        } else {
            if (!economy.hasFunds(ctx.getOwnerAccount().getUuid(), ctx.getExactPrice())) {
                ctx.setCancelled(SHOP_DOES_NOT_HAVE_ENOUGH_MONEY);
                return;
            }
            if (!inventoryService.hasItems(ctx.getStock(), ctx.getClientInventory())) {
                ctx.setCancelled(NOT_ENOUGH_STOCK_IN_INVENTORY);
            }
        }
    }

    /** Per-item buy/sell permission check (supports per-material and #-prefixed nodes). */
    private void checkPermissions(PreTransactionContext ctx) {
        if (ctx.isCancelled()) {
            return;
        }
        Player client = ctx.getClient();
        boolean buy = ctx.getTransactionType() == BUY;

        String itemLine = ChestShopSign.getItem(ctx.getSign());
        if (itemLine.contains("#") && Permissions.hasPermissionSetFalse(client, (buy ? Permissions.BUY_ID : Permissions.SELL_ID) + itemLine)) {
            ctx.setCancelled(CLIENT_DOES_NOT_HAVE_PERMISSION);
            return;
        }

        for (ItemStack stock : ctx.getStock()) {
            String matID = stock.getType().toString().toLowerCase(Locale.ROOT);
            boolean hasPerm = buy
                    ? Permissions.has(client, Permissions.BUY) && !Permissions.hasPermissionSetFalse(client, Permissions.BUY_ID + matID)
                            || Permissions.has(client, Permissions.BUY_ID + matID)
                    : Permissions.has(client, Permissions.SELL) && !Permissions.hasPermissionSetFalse(client, Permissions.SELL_ID + matID)
                            || Permissions.has(client, Permissions.SELL_ID + matID);
            if (!hasPerm) {
                ctx.setCancelled(CLIENT_DOES_NOT_HAVE_PERMISSION);
                return;
            }
        }
    }

    /** Cancel if the receiving inventory (chest on SELL, client on BUY) can't hold the stock. */
    private void checkStockFits(PreTransactionContext ctx) {
        if (ctx.isCancelled()) {
            return;
        }
        if (ctx.getTransactionType() == SELL) {
            if (!inventoryService.fits(ctx.getStock(), ctx.getOwnerInventory())) {
                ctx.setCancelled(NOT_ENOUGH_SPACE_IN_CHEST);
            }
        } else {
            if (!inventoryService.fits(ctx.getStock(), ctx.getClientInventory())) {
                ctx.setCancelled(NOT_ENOUGH_SPACE_IN_INVENTORY);
            }
        }
    }

    // ---- partial-fulfilment maths (was PartialTransactionModule) ----------------

    private void adjustPartialBuy(PreTransactionContext ctx) {
        if (ctx.isCancelled() || ctx.getTransactionType() != BUY) {
            return;
        }
        int itemCount = InventoryUtil.countItems(ctx.getStock());
        if (itemCount <= 0) {
            return;
        }
        Player client = ctx.getClient();
        BigDecimal pricePerItem = ctx.getExactPrice().divide(BigDecimal.valueOf(itemCount), MathContext.DECIMAL128);
        BigDecimal walletMoney = economy.getBalance(client.getUniqueId());

        if (!economy.hasFunds(client.getUniqueId(), ctx.getExactPrice())) {
            int amountAffordable = getAmountOfAffordableItems(walletMoney, pricePerItem);
            if (amountAffordable < 1) {
                ctx.setCancelled(CLIENT_DOES_NOT_HAVE_ENOUGH_MONEY);
                return;
            }
            BigDecimal scaled = scalePrice(pricePerItem, amountAffordable);
            if (roundedToZero(pricePerItem, scaled)) {
                ctx.setCancelled(CLIENT_DOES_NOT_HAVE_ENOUGH_MONEY);
                return;
            }
            ctx.setExactPrice(scaled);
            ctx.setStock(getCountedItemStack(ctx.getStock(), amountAffordable));
        }

        if (!inventoryService.hasItems(ctx.getStock(), ctx.getOwnerInventory())) {
            ItemStack[] itemsHad = getItems(ctx.getStock(), ctx.getOwnerInventory());
            int possessed = InventoryUtil.countItems(itemsHad);
            if (possessed <= 0) {
                ctx.setCancelled(NOT_ENOUGH_STOCK_IN_CHEST);
                return;
            }
            BigDecimal scaled = scalePrice(pricePerItem, possessed);
            if (roundedToZero(pricePerItem, scaled)) {
                ctx.setCancelled(NOT_ENOUGH_STOCK_IN_CHEST);
                return;
            }
            ctx.setExactPrice(scaled);
            ctx.setStock(itemsHad);
        }

        if (!inventoryService.fits(ctx.getStock(), ctx.getClientInventory())) {
            ItemStack[] itemsFit = getItemsThatFit(ctx.getStock(), ctx.getClientInventory());
            int possessed = InventoryUtil.countItems(itemsFit);
            if (possessed <= 0) {
                ctx.setCancelled(NOT_ENOUGH_SPACE_IN_INVENTORY);
                return;
            }
            BigDecimal scaled = scalePrice(pricePerItem, possessed);
            if (roundedToZero(pricePerItem, scaled)) {
                ctx.setCancelled(NOT_ENOUGH_SPACE_IN_INVENTORY);
                return;
            }
            ctx.setExactPrice(scaled);
            ctx.setStock(itemsFit);
        }

        if (!economy.canHold(ctx.getOwnerAccount().getUuid(), ctx.getExactPrice())) {
            ctx.setCancelled(SHOP_DEPOSIT_FAILED);
        }
    }

    private void adjustPartialSell(PreTransactionContext ctx) {
        if (ctx.isCancelled() || ctx.getTransactionType() != SELL) {
            return;
        }
        int itemCount = InventoryUtil.countItems(ctx.getStock());
        if (itemCount <= 0) {
            return;
        }
        Player client = ctx.getClient();
        UUID owner = ctx.getOwnerAccount().getUuid();
        BigDecimal pricePerItem = ctx.getExactPrice().divide(BigDecimal.valueOf(itemCount), MathContext.DECIMAL128);

        if (economy.isOwnerEconomicallyActive(ctx.getOwnerInventory())
                && !economy.hasFunds(owner, ctx.getExactPrice())) {
            BigDecimal walletMoney = economy.getBalance(owner);
            int amountAffordable = getAmountOfAffordableItems(walletMoney, pricePerItem);
            if (amountAffordable < 1) {
                ctx.setCancelled(SHOP_DOES_NOT_HAVE_ENOUGH_MONEY);
                return;
            }
            BigDecimal scaled = scalePrice(pricePerItem, amountAffordable);
            if (roundedToZero(pricePerItem, scaled)) {
                ctx.setCancelled(SHOP_DOES_NOT_HAVE_ENOUGH_MONEY);
                return;
            }
            ctx.setExactPrice(scaled);
            ctx.setStock(getCountedItemStack(ctx.getStock(), amountAffordable));
        }

        if (!inventoryService.hasItems(ctx.getStock(), ctx.getClientInventory())) {
            ItemStack[] itemsHad = getItems(ctx.getStock(), ctx.getClientInventory());
            int possessed = InventoryUtil.countItems(itemsHad);
            if (possessed <= 0) {
                ctx.setCancelled(NOT_ENOUGH_STOCK_IN_INVENTORY);
                return;
            }
            BigDecimal scaled = scalePrice(pricePerItem, possessed);
            if (roundedToZero(pricePerItem, scaled)) {
                ctx.setCancelled(NOT_ENOUGH_STOCK_IN_INVENTORY);
                return;
            }
            ctx.setExactPrice(scaled);
            ctx.setStock(itemsHad);
        }

        if (!inventoryService.fits(ctx.getStock(), ctx.getOwnerInventory())) {
            ItemStack[] itemsFit = getItemsThatFit(ctx.getStock(), ctx.getOwnerInventory());
            int possessed = InventoryUtil.countItems(itemsFit);
            if (possessed <= 0) {
                ctx.setCancelled(NOT_ENOUGH_SPACE_IN_CHEST);
                return;
            }
            BigDecimal scaled = scalePrice(pricePerItem, possessed);
            if (roundedToZero(pricePerItem, scaled)) {
                ctx.setCancelled(NOT_ENOUGH_SPACE_IN_CHEST);
                return;
            }
            ctx.setExactPrice(scaled);
            ctx.setStock(itemsFit);
        }

        if (!economy.canHold(client.getUniqueId(), ctx.getExactPrice())) {
            ctx.setCancelled(CLIENT_DEPOSIT_FAILED);
        }
    }

    // Package-private so the money-math is unit-testable directly (ADT-138): these
    // are the exact spots where a scale/rounding/off-by-one slip would leak or
    // destroy currency on a partial fill.
    BigDecimal scalePrice(BigDecimal pricePerItem, int count) {
        return pricePerItem.multiply(new BigDecimal(count)).setScale(config.getPricePrecision(), RoundingMode.HALF_UP);
    }

    /** A positive per-item price that scales to zero means the partial amount is unaffordable. */
    static boolean roundedToZero(BigDecimal pricePerItem, BigDecimal scaled) {
        return pricePerItem.compareTo(BigDecimal.ZERO) > 0 && scaled.compareTo(BigDecimal.ZERO) == 0;
    }

    static int getAmountOfAffordableItems(BigDecimal walletMoney, BigDecimal pricePerItem) {
        return walletMoney.divide(pricePerItem, 0, RoundingMode.FLOOR).intValueExact();
    }

    private ItemStack[] getItems(ItemStack[] stock, Inventory inventory) {
        List<ItemStack> toReturn = new LinkedList<>();
        for (Map.Entry<ItemStack, Integer> entry : inventoryService.getItemCounts(stock).entrySet()) {
            int amount = inventoryService.getAmount(entry.getKey(), inventory);
            Collections.addAll(toReturn, inventoryService.getItemStacked(entry.getKey(), Math.min(amount, entry.getValue())));
        }
        return toReturn.toArray(new ItemStack[0]);
    }

    private ItemStack[] getCountedItemStack(ItemStack[] stock, int numberOfItems) {
        int left = numberOfItems;
        LinkedList<ItemStack> stacks = new LinkedList<>();

        for (ItemStack stack : stock) {
            int count = stack.getAmount();
            ItemStack toAdd;
            if (left > count) {
                toAdd = stack;
                left -= count;
            } else {
                ItemStack clone = stack.clone();
                clone.setAmount(left);
                toAdd = clone;
                left = 0;
            }

            boolean added = false;
            int maxStackSize = inventoryService.getMaxStackSize(stack);
            for (ItemStack iStack : stacks) {
                if (iStack.getAmount() < maxStackSize && materialService.equals(toAdd, iStack)) {
                    int newAmount = iStack.getAmount() + toAdd.getAmount();
                    if (newAmount > maxStackSize) {
                        iStack.setAmount(maxStackSize);
                        toAdd.setAmount(newAmount - maxStackSize);
                    } else {
                        iStack.setAmount(newAmount);
                        added = true;
                    }
                    break;
                }
            }

            if (!added) {
                Collections.addAll(stacks, inventoryService.getItemsStacked(toAdd));
            }
            if (left <= 0) {
                break;
            }
        }
        return stacks.toArray(new ItemStack[0]);
    }

    private ItemStack[] getItemsThatFit(ItemStack[] stock, Inventory inventory) {
        List<ItemStack> resultStock = new LinkedList<>();
        int emptySlots = InventoryUtil.countEmpty(inventory);

        for (Map.Entry<ItemStack, Integer> entry : inventoryService.getItemCounts(stock).entrySet()) {
            ItemStack item = entry.getKey();
            int amount = entry.getValue();
            int maxStackSize = inventoryService.getMaxStackSize(item);
            int free = 0;
            for (ItemStack itemInInventory : inventory.getContents()) {
                if (materialService.equals(item, itemInInventory) && itemInInventory != null) {
                    free += (maxStackSize - itemInInventory.getAmount()) % maxStackSize;
                }
            }

            if (free == 0 && emptySlots == 0) {
                continue;
            }

            if (amount > free) {
                if (emptySlots > 0) {
                    int requiredSlots = (int) Math.ceil(((double) amount - free) / maxStackSize);
                    if (requiredSlots <= emptySlots) {
                        emptySlots = emptySlots - requiredSlots;
                    } else {
                        amount = free + maxStackSize * emptySlots;
                        emptySlots = 0;
                    }
                } else {
                    amount = free;
                }
            }
            Collections.addAll(resultStock, inventoryService.getItemStacked(item, amount));
        }
        return resultStock.toArray(new ItemStack[0]);
    }

    // ---- error messaging (was the MONITOR ErrorMessageSender) -------------------

    /** Drop a player's notification-cooldown rows (called from PlayerConnect on quit). */
    public void clearNotificationCooldowns(UUID playerUuid) {
        if (config.getNotificationMessageCooldown() > 0) {
            notificationCooldowns.rowMap().remove(playerUuid);
        }
    }

    /** Tell the client (and, for full/out-of-stock, the owner) why a trade was cancelled. */
    private void sendErrorMessage(PreTransactionContext ctx) {
        if (!ctx.isCancelled()) {
            return;
        }

        String messageKey = null;
        switch (ctx.getTransactionOutcome()) {
            case SHOP_DOES_NOT_BUY_THIS_ITEM -> messageKey = "chestshop.NO_SELLING_HERE";
            case SHOP_DOES_NOT_SELL_THIS_ITEM -> messageKey = "chestshop.NO_BUYING_HERE";
            case CLIENT_DOES_NOT_HAVE_PERMISSION -> messageKey = "chestshop.NO_PERMISSION";
            case CLIENT_DOES_NOT_HAVE_ENOUGH_MONEY -> messageKey = "chestshop.NOT_ENOUGH_MONEY";
            case SHOP_DOES_NOT_HAVE_ENOUGH_MONEY -> messageKey = "chestshop.NOT_ENOUGH_MONEY_SHOP";
            case NOT_ENOUGH_SPACE_IN_CHEST -> {
                if (config.isShowMessageFullShop() && !config.isCstoggleTogglesFullShop() || !accounts.isIgnoring(ctx.getOwnerAccount().getUuid())) {
                    sendShopLocationMessage(ctx, "chestshop.NOT_ENOUGH_SPACE_IN_YOUR_SHOP", "seller");
                }
                messageKey = "chestshop.NOT_ENOUGH_SPACE_IN_CHEST";
            }
            case NOT_ENOUGH_SPACE_IN_INVENTORY -> messageKey = "chestshop.NOT_ENOUGH_SPACE_IN_INVENTORY";
            case NOT_ENOUGH_STOCK_IN_INVENTORY -> messageKey = "chestshop.NOT_ENOUGH_ITEMS_TO_SELL";
            case NOT_ENOUGH_STOCK_IN_CHEST -> {
                if (config.isShowMessageOutOfStock() && !config.isCstoggleTogglesOutOfStock() || !accounts.isIgnoring(ctx.getOwnerAccount().getUuid())) {
                    sendShopLocationMessage(ctx, "chestshop.NOT_ENOUGH_STOCK_IN_YOUR_SHOP", "buyer");
                }
                messageKey = "chestshop.NOT_ENOUGH_STOCK";
            }
            case CLIENT_DEPOSIT_FAILED -> messageKey = "chestshop.CLIENT_DEPOSIT_FAILED";
            case SHOP_DEPOSIT_FAILED -> {
                sendMessageToOwner(ctx.getOwnerAccount(), "chestshop.CLIENT_DEPOSIT_FAILED", new String[0]);
                messageKey = "chestshop.SHOP_DEPOSIT_FAILED";
            }
            case SHOP_IS_RESTRICTED -> messageKey = "chestshop.ACCESS_DENIED";
            case INVALID_SHOP -> messageKey = "chestshop.INVALID_SHOP_DETECTED";
            case INVALID_CLIENT_NAME -> messageKey = "chestshop.INVALID_CLIENT_NAME";
            case CREATIVE_MODE_PROTECTION -> messageKey = "chestshop.TRADE_DENIED_CREATIVE_MODE";
            default -> { }
        }

        if (messageKey != null) {
            message.send(ctx.getClient(), messageKey);
        }
    }

    private void sendShopLocationMessage(PreTransactionContext ctx, String key, String actorPlaceholder) {
        Location loc = ctx.getSign().getLocation();
        sendMessageToOwner(ctx.getOwnerAccount(), key, new String[]{
                "price", economy.format(ctx.getExactPrice()),
                actorPlaceholder, ctx.getClient().getName(),
                "world", loc.getWorld() != null ? loc.getWorld().getName() : "?", // ADT-140: world may be unloaded
                "x", String.valueOf(loc.getBlockX()),
                "y", String.valueOf(loc.getBlockY()),
                "z", String.valueOf(loc.getBlockZ())
        }, ctx.getStock());
    }

    private void sendMessageToOwner(Account ownerAccount, String key, String[] replacements, ItemStack... stock) {
        Player player = Bukkit.getPlayer(ownerAccount.getUuid());
        if (player == null && !config.isBungeecordMessages()) {
            return;
        }

        if (config.getNotificationMessageCooldown() > 0) {
            String cacheKey = key + "|" + String.join(",", replacements) + "|" + items.getItemList(stock);
            Long last = notificationCooldowns.get(ownerAccount.getUuid(), cacheKey);
            if (last != null && last + config.getNotificationMessageCooldown() * 1000L > System.currentTimeMillis()) {
                return;
            }
            notificationCooldowns.put(ownerAccount.getUuid(), cacheKey, System.currentTimeMillis());
        }

        String itemList = items.getItemList(stock);
        if (player != null) {
            if (config.isShowitemMessage() && showItem.sendMessage(message, player, key, stock, Collections.emptyMap(), replacements)) {
                return;
            }
            player.sendMessage(message.component(key, ChestShop.values(true, ImmutableMap.of("material", itemList, "item", itemList), replacements)));
        } else {
            ChestShop.sendBungeeMessage(ownerAccount.getName(), key, ImmutableMap.of("material", itemList, "item", itemList), replacements);
        }
    }

    // ===================== post-trade pipeline =====================

    /**
     * Run the post-transaction pipeline for a validated trade, in the former priority +
     * registration order: {@link #execute} the goods+money legs atomically (may cancel);
     * the stock counter refreshes regardless; then, only if the trade settled, the empty
     * shop is removed and the MONITOR reactions (legacy-sign migration, market sync, log,
     * messages, metrics) run.
     *
     * <p><b>Main-thread contract (ADT-131).</b> This runs synchronously on the Bukkit main
     * thread from the {@code PlayerInteract} handler, and most of it must: goods move via
     * the main-thread-only inventory API, and the money legs are settled inline so the
     * goods can be reversed if settlement fails ({@link #execute}) — a trade is all-or-nothing.
     * That makes the per-click {@code TreasuryApi.transfer} (one MariaDB write, two with tax)
     * a synchronous cost; settlement is fast-fail (no retries, see {@code EconomyService.settle})
     * and benefits from a warm Treasury connection pool, but it cannot be moved off-thread
     * without breaking the goods/money atomicity. The market sync ({@code market.onTransaction})
     * also stays on-thread because {@code MarketApi.recordSale} fires a synchronous Bukkit
     * event ({@code ChestShopSaleEvent}), which Paper forbids from an async context. The one
     * deferrable step — the shop-log file write — is dispatched async in {@link #logTransaction}.
     * A fuller async redesign would need a goods-move → async-settle → main-thread-reverse
     * handshake and is deliberately out of scope here (tail-latency, not a correctness bug).
     */
    public void process(TransactionContext event) {
        execute(event);

        // Runs regardless of cancellation (was @HIGH ignoreCancelled=false).
        stockCounter.onTransaction(event);

        if (event.isCancelled()) {
            return;
        }
        deleteEmptyShop(event); // was @HIGHEST EmptyShopDeleter

        // MONITOR reactions, in registration order.
        economy.migrateLegacyBusinessSign(event);
        market.onTransaction(event);   // genuine market-DB sync — stays
        logTransaction(event);                 // was @MONITOR TransactionLogger
        sendTransactionMessages(event);        // was @MONITOR TransactionMessageSender
        MetricsModule.onTransaction(event);    // genuine bStats counter — stays
    }

    /**
     * Run a validated transaction to completion: move the goods, then settle the money —
     * reversing the goods if the (pre-validated, so exceptional) money leg fails, so the
     * trade is all-or-nothing.
     */
    public void execute(TransactionContext event) {
        boolean buy = event.getTransactionType() == BUY;

        Inventory from = buy ? event.getOwnerInventory() : event.getClientInventory();
        Inventory to = buy ? event.getClientInventory() : event.getOwnerInventory();
        if (!transferItems(from, to, event.getStock())) {
            cancelOnShortfall(event);
            return;
        }

        if (!economy.settle(event.getExactPrice(), event.getClient(),
                event.getOwnerAccount().getUuid(), buy, event)) {
            // The goods already moved but the money didn't settle — put the goods
            // back and cancel, keeping the trade atomic.
            reverseTransfer(event);
            event.setCancelled(true);
        }
    }

    private static void cancelOnShortfall(TransactionContext event) {
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
    boolean transferItems(Inventory source, Inventory target, ItemStack[] items) {
        ItemStack[] sourceSnapshot = cloneContents(source);
        ItemStack[] targetSnapshot = cloneContents(target);

        int leftOver = 0;
        for (ItemStack item : items) {
            leftOver += config.isStackTo64()
                    ? inventoryService.transfer(item, source, target, 64)
                    : inventoryService.transfer(item, source, target);
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
    void reverseTransfer(TransactionContext event) {
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

    // ---- post-transaction reactions (were the posttransaction listeners) --------

    /** Remove a shop whose container ran dry after a buy (config-gated, never admin shops). */
    private void deleteEmptyShop(TransactionContext event) {
        if (event.getTransactionType() != BUY) {
            return;
        }
        Sign sign = event.getSign();
        if (chestShopSign.isAdminShop(sign)) {
            return;
        }
        Inventory ownerInventory = event.getOwnerInventory();
        if (!shopShouldBeRemoved(ownerInventory, event.getStock()) || !isInRemoveWorld(sign)) {
            return;
        }

        Container connectedContainer = shopBlockUtil.findConnectedContainer(sign);
        shops.onDestroyed(new ShopDestroyedContext(null, sign, connectedContainer));

        Material signType = sign.getType();
        sign.getBlock().setType(Material.AIR);

        if (config.isRemoveEmptyChests() && !ChestShopSign.isAdminShop(ownerInventory) && InventoryUtil.isEmpty(ownerInventory)) {
            if (connectedContainer != null) {
                connectedContainer.getBlock().setType(Material.AIR);
            }
        } else {
            if (!signType.isItem()) {
                try {
                    signType = Material.valueOf(signType.name().replace("WALL_", ""));
                } catch (IllegalArgumentException ignored) {}
            }
            if (signType.isItem()) {
                ownerInventory.addItem(new ItemStack(signType, 1));
            } else {
                ChestShop.getBukkitLogger().warning("Unable to get item for sign " + signType + " to add to removed shop's container!");
            }
        }
    }

    private boolean shopShouldBeRemoved(Inventory inventory, ItemStack[] stock) {
        if (config.isRemoveEmptyShops()) {
            if (config.isAllowPartialTransactions()) {
                for (ItemStack itemStack : stock) {
                    if (inventory.containsAtLeast(itemStack, 1)) {
                        return false;
                    }
                }
                return true;
            } else if (!inventoryService.hasItems(stock, inventory)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInRemoveWorld(Sign sign) {
        return config.getRemoveEmptyWorlds().isEmpty() || config.getRemoveEmptyWorlds().contains(sign.getWorld().getName());
    }

    /** Write a completed trade to the shop log. */
    private void logTransaction(TransactionContext event) {
        String template = event.getTransactionType() == BUY ? BUY_LOG : SELL_LOG;

        StringBuilder itemList = new StringBuilder(50);
        for (Map.Entry<ItemStack, Integer> entry : inventoryService.getItemCounts(event.getStock()).entrySet()) {
            itemList.append(entry.getValue()).append(' ').append(items.getName(entry.getKey()));
        }

        // Build the line on the main thread (item-name/account/sign reads), then push the
        // file write off the tick (ADT-131): per-trade disk I/O otherwise stalls the main
        // thread on contended shared-host storage. java.util.logging is thread-safe and the
        // line is self-contained + timestamped, so async logging is order-recoverable and
        // can never affect the already-committed, synchronous trade. This is the only
        // post-trade step with no main-thread dependency — the money legs (atomic) and the
        // market sync (recordSale fires a synchronous Bukkit event) must stay on-thread.
        String line = String.format(template,
                event.getClient().getName(),
                itemList.toString(),
                event.getExactPrice(),
                event.getOwnerAccount().getName(),
                LocationUtil.locationToString(event.getSign().getLocation()));
        ChestShop.runInAsyncThread(() -> ChestShop.getShopLogger().info(line));
    }

    /** Notify the buyer and (unless they muted) the owner that a trade settled. */
    private void sendTransactionMessages(TransactionContext event) {
        Player player = event.getClient();
        boolean buy = event.getTransactionType() == BUY;

        if (config.isShowTransactionInformationClient()) {
            sendTradeMessage(player, player.getName(),
                    buy ? "chestshop.YOU_BOUGHT_FROM_SHOP" : "chestshop.YOU_SOLD_TO_SHOP", event,
                    buy ? "owner" : "buyer", event.getOwnerAccount().getName());
        }

        if (config.isShowTransactionInformationOwner() && !accounts.isIgnoring(event.getOwnerAccount().getUuid())) {
            Player owner = Bukkit.getPlayer(event.getOwnerAccount().getUuid());
            sendTradeMessage(owner, event.getOwnerAccount().getName(),
                    buy ? "chestshop.SOMEBODY_BOUGHT_FROM_YOUR_SHOP" : "chestshop.SOMEBODY_SOLD_TO_YOUR_SHOP", event,
                    buy ? "buyer" : "seller", player.getName());
        }
    }

    private void sendTradeMessage(Player player, String playerName, String key, TransactionContext event, String... replacements) {
        Location loc = event.getSign().getLocation();
        Map<String, String> replacementMap = new LinkedHashMap<>();
        replacementMap.put("price", economy.format(event.getExactPrice()));
        replacementMap.put("world", loc.getWorld() != null ? loc.getWorld().getName() : "?"); // ADT-140: world may be unloaded
        replacementMap.put("x", String.valueOf(loc.getBlockX()));
        replacementMap.put("y", String.valueOf(loc.getBlockY()));
        replacementMap.put("z", String.valueOf(loc.getBlockZ()));
        replacementMap.put("material", "%item");

        for (int i = 0; i + 1 < replacements.length; i += 2) {
            replacementMap.put(replacements[i], replacements[i + 1]);
        }

        if (config.isShowitemMessage() && showItem.sendMessage(message, player, playerName, key, event.getStock(), replacementMap)) {
            return;
        }

        if (player != null) {
            replacementMap.put("item", items.getItemList(event.getStock()));
            player.sendMessage(message.component(key, ChestShop.values(true, replacementMap)));
        } else if (playerName != null) {
            replacementMap.put("item", items.getItemList(event.getStock()));
            ChestShop.sendBungeeMessage(playerName, key, replacementMap);
        }
    }
}
