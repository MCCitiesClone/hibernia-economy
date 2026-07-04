package io.paradaux.chestshop.services.impl;

import io.paradaux.chestshop.model.Transaction;
import io.paradaux.chestshop.model.config.ChestShopConfiguration;
import io.paradaux.chestshop.support.ServerTest;
import io.paradaux.chestshop.support.TestConfigs;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.paradaux.chestshop.model.Transaction.TransactionType.BUY;
import static io.paradaux.chestshop.model.Transaction.TransactionType.SELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Real end-to-end goods movement on a live MockBukkit server: {@link GoodsTransfer} over the real
 * {@link InventoryServiceImpl}/{@link MaterialServiceImpl}, so items actually move between real
 * inventories and the atomic snapshot/restore is exercised for real.
 */
class GoodsTransferTest extends ServerTest {

    private ChestShopConfiguration config;
    private GoodsTransfer transfer;

    @BeforeEach
    void wire() {
        config = TestConfigs.defaults();
        MaterialServiceImpl material = new MaterialServiceImpl(config);
        InventoryServiceImpl inv = new InventoryServiceImpl(config, material);
        transfer = new GoodsTransfer(inv, config);
    }

    private static int count(Inventory inv, Material m) {
        int n = 0;
        for (ItemStack s : inv.getContents()) {
            if (s != null && s.getType() == m) {
                n += s.getAmount();
            }
        }
        return n;
    }

    @Test
    void transfer_movesAllStock_andReportsSuccess() {
        Inventory source = chestWith(27, item(Material.DIAMOND, 32));
        Inventory target = chest(27);

        boolean ok = transfer.transfer(source, target, new ItemStack[]{item(Material.DIAMOND, 32)});

        assertThat(ok).isTrue();
        assertThat(count(source, Material.DIAMOND)).isZero();
        assertThat(count(target, Material.DIAMOND)).isEqualTo(32);
    }

    @Test
    void transfer_restoresBothInventories_whenTargetCannotHold() {
        Inventory source = chestWith(9, item(Material.DIAMOND, 64));
        // A target with every slot full of a different item: the diamonds cannot land.
        Inventory target = server.createInventory(null, 9);
        for (int i = 0; i < 9; i++) {
            target.setItem(i, item(Material.STONE, 64));
        }

        boolean ok = transfer.transfer(source, target, new ItemStack[]{item(Material.DIAMOND, 64)});

        assertThat(ok).isFalse();
        assertThat(count(source, Material.DIAMOND)).isEqualTo(64); // source restored
        assertThat(count(target, Material.STONE)).isEqualTo(9 * 64); // target untouched
    }

    @Test
    void moveUnlimited_add_spawnsStockIntoClient() {
        Inventory client = chest(27);
        boolean ok = transfer.moveUnlimited(client, new ItemStack[]{item(Material.DIAMOND, 10)}, true);
        assertThat(ok).isTrue();
        assertThat(count(client, Material.DIAMOND)).isEqualTo(10);
    }

    @Test
    void moveUnlimited_remove_takesStockFromClient() {
        Inventory client = chestWith(27, item(Material.DIAMOND, 10));
        boolean ok = transfer.moveUnlimited(client, new ItemStack[]{item(Material.DIAMOND, 4)}, false);
        assertThat(ok).isTrue();
        assertThat(count(client, Material.DIAMOND)).isEqualTo(6);
    }

    @Test
    void moveUnlimited_remove_restoresClient_whenItLacksTheStock() {
        Inventory client = chest(27); // empty — cannot remove
        boolean ok = transfer.moveUnlimited(client, new ItemStack[]{item(Material.DIAMOND, 4)}, false);
        assertThat(ok).isFalse();
        assertThat(count(client, Material.DIAMOND)).isZero();
    }

    @Test
    void reverse_buy_movesGoodsBackFromClientToOwner() {
        Inventory owner = chest(27);
        Inventory client = chestWith(27, item(Material.DIAMOND, 8));
        Transaction event = txn(BUY, false, owner, client, item(Material.DIAMOND, 8));

        transfer.reverse(event);

        assertThat(count(client, Material.DIAMOND)).isZero();
        assertThat(count(owner, Material.DIAMOND)).isEqualTo(8);
    }

    @Test
    void reverse_sell_movesGoodsBackFromOwnerToClient() {
        Inventory owner = chestWith(27, item(Material.DIAMOND, 8));
        Inventory client = chest(27);
        Transaction event = txn(SELL, false, owner, client, item(Material.DIAMOND, 8));

        transfer.reverse(event);

        assertThat(count(owner, Material.DIAMOND)).isZero();
        assertThat(count(client, Material.DIAMOND)).isEqualTo(8);
    }

    @Test
    void reverse_unlimited_removesTheSpawnedStockFromClient() {
        Inventory client = chestWith(27, item(Material.DIAMOND, 5));
        Transaction event = txn(BUY, true, null, client, item(Material.DIAMOND, 5));

        transfer.reverse(event);

        assertThat(count(client, Material.DIAMOND)).isZero();
    }

    @Test
    void reverse_logsButDoesNotThrow_whenItCannotComplete() {
        Inventory client = chest(27); // nothing to remove — reverse fails internally, only logs
        Transaction event = txn(BUY, true, null, client, item(Material.DIAMOND, 5));
        transfer.reverse(event); // must not throw
        assertThat(count(client, Material.DIAMOND)).isZero();
    }

    private Transaction txn(Transaction.TransactionType type, boolean unlimited,
                            Inventory owner, Inventory client, ItemStack... stock) {
        Transaction t = Mockito.mock(Transaction.class);
        when(t.getTransactionType()).thenReturn(type);
        when(t.isUnlimitedOwner()).thenReturn(unlimited);
        when(t.getOwnerInventory()).thenReturn(owner);
        when(t.getClientInventory()).thenReturn(client);
        when(t.getStock()).thenReturn(stock);
        when(t.getSign()).thenReturn(null);
        return t;
    }
}
