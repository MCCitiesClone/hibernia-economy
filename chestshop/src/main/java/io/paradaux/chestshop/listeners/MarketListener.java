package io.paradaux.chestshop.listeners;

import io.paradaux.chestshop.services.MarketRecords;
import io.paradaux.chestshop.services.MarketHook;
import io.paradaux.chestshop.services.ShopBlockService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.model.CreatedShop;
import io.paradaux.chestshop.model.DestroyedShop;
import io.paradaux.chestshop.model.Transaction;
import io.paradaux.chestshop.services.SignService;
import io.paradaux.treasury.api.MarketApi;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Keeps the ChestShop sales tracker + live shop registry up to date:
 *  - {@link #onTransaction}: record the trade + upsert the shop (lazy-registers
 *    pre-existing shops on first activity and refreshes post-trade stock).
 *  - {@link #onShopCreated}: register/refresh the shop.
 *  - {@link #onShopDestroyed}: mark it inactive.
 *  - {@link #onInventoryClose}: a manual restock — recount stock for the
 *    connected shop signs (mirrors ChestShop's own sign-counter refresh).
 *
 * <p>The first three are invoked directly by {@code TransactionService}/{@code ShopService}
 * at the MONITOR point of their pipelines (they were {@code Transaction}/
 * {@code CreatedShop}/{@code DestroyedShop} listeners before those events were
 * collapsed); {@link #onInventoryClose} is the only remaining Bukkit handler, which is why
 * this is still a registered {@link Listener}. All are fully guarded: analytics must never
 * disrupt a trade.
 */
@Singleton
public class MarketListener implements Listener {

    private final MarketRecords records;
    private final io.paradaux.chestshop.services.ItemCodeService itemCodes;
    private final SignService signService;
    private final ShopBlockService shopBlockService;

    @Inject
    public MarketListener(MarketRecords records, io.paradaux.chestshop.services.ItemCodeService itemCodes,
                          SignService signService, ShopBlockService shopBlockService) {
        this.records = records;
        this.itemCodes = itemCodes;
        this.signService = signService;
        this.shopBlockService = shopBlockService;
    }

    // Invoked directly by TransactionService#process (was a @MONITOR Transaction listener).
    public void onTransaction(Transaction event) {
        if (!MarketHook.enabled()) return;
        try {
            ItemStack[] stock = event.getStock();
            if (stock == null || stock.length == 0 || stock[0] == null) return;
            ItemStack item = stock[0];
            int quantity = records.totalAmount(stock);
            Sign sign = event.getSign();
            boolean admin = signService.isAdminShop(sign);
            UUID ownerUuid = event.getOwnerAccount() != null ? event.getOwnerAccount().getUuid() : null;
            MarketRecords.Owner owner = records.ownerFromUuid(ownerUuid, admin);
            String direction = event.getTransactionType() == Transaction.TransactionType.BUY ? "BUY" : "SELL";
            Integer shopStock = admin ? null : records.stockOf(item, event.getOwnerInventory());
            Integer capacity = admin ? null : records.capacityOf(item, event.getOwnerInventory());

            MarketApi market = MarketHook.market();
            market.recordSale(records.sale(sign, item, quantity, event.getClient().getUniqueId(),
                    owner, event.getExactPrice(), event.getSalesTax(), direction, shopStock, event.getSettlementTxnId()));
            market.upsertShop(records.shop(sign, item, owner, shopStock, capacity));
        } catch (Throwable ignored) {
            // analytics only
        }
    }

    // Invoked directly by ShopService#onCreated (was a @MONITOR CreatedShop listener).
    public void onShopCreated(CreatedShop event) {
        if (!MarketHook.enabled()) return;
        try {
            Sign sign = event.getSign();
            ItemStack item = itemCodes.decode(event.getSignLines()[SignService.ITEM_LINE]);
            if (item == null) return;
            boolean admin = signService.isAdminShop(event.getSignLines());
            UUID ownerUuid = event.getOwnerAccount() != null ? event.getOwnerAccount().getUuid() : null;
            MarketRecords.Owner owner = records.ownerFromUuid(ownerUuid, admin);
            Inventory container = (!admin && event.getContainer() != null)
                    ? event.getContainer().getInventory() : null;
            Integer stock = container != null ? records.stockOf(item, container) : null;
            Integer capacity = container != null ? records.capacityOf(item, container) : null;
            MarketHook.market().upsertShop(records.shop(sign, item, owner, stock, capacity));
        } catch (Throwable ignored) {
        }
    }

    // Invoked directly by ShopService#onDestroyed (was a @MONITOR DestroyedShop listener).
    public void onShopDestroyed(DestroyedShop event) {
        if (!MarketHook.enabled()) return;
        try {
            Location l = event.getSign().getLocation();
            MarketHook.market().deactivateShop(
                    l.getWorld() != null ? l.getWorld().getName() : null,
                    l.getBlockX(), l.getBlockY(), l.getBlockZ());
        } catch (Throwable ignored) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!MarketHook.enabled()) return;
        try {
            InventoryHolder holder = event.getInventory().getHolder();
            if (holder == null) return;
            List<Sign> signs = shopBlockService.findConnectedShopSigns(holder);
            if (signs.isEmpty()) return;
            Inventory inv = event.getInventory();
            MarketApi market = MarketHook.market();
            for (Sign sign : signs) {
                if (signService.isAdminShop(sign)) continue;
                String itemName = SignService.getItem(sign);
                ItemStack item = itemName != null ? itemCodes.decode(itemName) : null;
                if (item == null) continue;
                Location l = sign.getLocation();
                market.updateShopStock(
                        l.getWorld() != null ? l.getWorld().getName() : null,
                        l.getBlockX(), l.getBlockY(), l.getBlockZ(),
                        records.stockOf(item, inv), records.capacityOf(item, inv));
            }
        } catch (Throwable ignored) {
        }
    }
}
