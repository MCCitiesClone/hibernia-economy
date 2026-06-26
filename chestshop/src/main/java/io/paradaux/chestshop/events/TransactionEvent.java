package io.paradaux.chestshop.events;

import io.paradaux.chestshop.database.Account;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;

/**
 * The mutable carrier for a completed shop trade, threaded through the
 * post-transaction pipeline run by {@link io.paradaux.chestshop.services.TransactionService#process}.
 * Formerly a Bukkit event fired to a chain of priority-ordered listeners; those
 * reactions (goods+money execution, empty-shop cleanup, logging, messaging, market
 * sync, metrics) are now invoked directly as ordered service steps, so this is a
 * plain transaction context/result — {@code TransactionService} may {@link #setCancelled}
 * it if the atomic goods/money leg fails, and the {@code ignoreCancelled} reactions
 * are guarded on that flag.
 *
 * @author Acrobot
 */
public class TransactionEvent {
    private final TransactionType type;

    private final Inventory ownerInventory;
    private final Inventory clientInventory;

    private final Player client;
    private final Account ownerAccount;

    private final ItemStack[] stock;
    private final BigDecimal exactPrice;

    private final Sign sign;

    private boolean cancelled = false;

    public TransactionEvent(PreTransactionEvent event, Sign sign) {
        this.type = event.getTransactionType();

        this.ownerInventory = event.getOwnerInventory();
        this.clientInventory = event.getClientInventory();

        this.client = event.getClient();
        this.ownerAccount = event.getOwnerAccount();

        this.stock = event.getStock();
        this.exactPrice = event.getExactPrice();

        this.sign = sign;
    }

    public TransactionEvent(TransactionType type, Inventory ownerInventory, Inventory clientInventory, Player client, Account ownerAccount, ItemStack[] stock, BigDecimal exactPrice, Sign sign) {
        this.type = type;

        this.ownerInventory = ownerInventory;
        this.clientInventory = clientInventory;

        this.client = client;
        this.ownerAccount = ownerAccount;

        this.stock = stock;
        this.exactPrice = exactPrice;

        this.sign = sign;
    }

    /**
     * @deprecated Use {@link #TransactionEvent(TransactionType, Inventory, Inventory, Player, Account, ItemStack[], BigDecimal, Sign)}
     */
    @Deprecated
    public TransactionEvent(TransactionType type, Inventory ownerInventory, Inventory clientInventory, Player client, Account ownerAccount, ItemStack[] stock, double price, Sign sign) {
        this(type, ownerInventory, clientInventory, client, ownerAccount, stock, BigDecimal.valueOf(price), sign);
    }

    /**
     * @return Type of the transaction
     */
    public TransactionType getTransactionType() {
        return type;
    }

    /**
     * @return Owner's inventory
     */
    public Inventory getOwnerInventory() {
        return ownerInventory;
    }

    /**
     * @return Client's inventory
     */
    public Inventory getClientInventory() {
        return clientInventory;
    }

    /**
     * @return Shop's client
     */
    public Player getClient() {
        return client;
    }

    /**
     * @return Account of the shop's owner
     */
    public Account getOwnerAccount() {
        return ownerAccount;
    }

    /**
     * @return Shop's owner
     * @deprecated Use {@link #getOwnerAccount}
     */
    @Deprecated
    public OfflinePlayer getOwner() {
        return Bukkit.getOfflinePlayer(ownerAccount.getUuid());
    }

    /**
     * @return Stock available
     */
    public ItemStack[] getStock() {
        return stock;
    }

    /**
     * Get the exact total price
     *
     * @return Exact total price of the items
     */
    public BigDecimal getExactPrice() {
        return exactPrice;
    }

    /**
     * Get the total price
     *
     * @return Total price of the items
     * @deprecated Use {@link #getExactPrice()}
     */
    @Deprecated
    public double getPrice() {
        return exactPrice.doubleValue();
    }

    /**
     * @return Shop's sign
     */
    public Sign getSign() {
        return sign;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    /**
     * Possible transaction types
     */
    public enum TransactionType {
        BUY,
        SELL
    }
}
