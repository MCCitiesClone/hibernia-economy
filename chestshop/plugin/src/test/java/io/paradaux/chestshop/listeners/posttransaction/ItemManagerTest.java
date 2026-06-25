package io.paradaux.chestshop.listeners.posttransaction;

import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.TransactionEvent;
import io.paradaux.chestshop.breeze.utils.InventoryUtil;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static io.paradaux.chestshop.events.TransactionEvent.TransactionType.BUY;
import static io.paradaux.chestshop.events.TransactionEvent.TransactionType.SELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the item-side atomicity guarantees of {@link ItemManager} (ADT-4):
 * a fully-achievable move proceeds, an incomplete move reverts both inventories
 * without committing, and the money-leg compensation reverses goods in the
 * correct direction for both buy and sell. The underlying inventory mechanics
 * live in {@link InventoryUtil#transfer}, which is mocked here so the test
 * exercises the rollback wiring rather than Bukkit inventory internals.
 */
class ItemManagerTest {

    private boolean stackTo64;

    @BeforeEach
    void rememberConfig() {
        stackTo64 = Properties.STACK_TO_64;
        // Use the item's own max stack size path (the 3-arg transfer overload).
        Properties.STACK_TO_64 = false;
    }

    @AfterEach
    void restoreConfig() {
        Properties.STACK_TO_64 = stackTo64;
    }

    @Test
    void transferItems_movesEverything_returnsTrueAndDoesNotRevert() {
        Inventory source = mock(Inventory.class);
        Inventory target = mock(Inventory.class);
        when(source.getContents()).thenReturn(new ItemStack[]{null, null});
        when(target.getContents()).thenReturn(new ItemStack[]{null, null});
        ItemStack[] items = {mock(ItemStack.class)};

        try (MockedStatic<InventoryUtil> util = mockStatic(InventoryUtil.class)) {
            util.when(() -> InventoryUtil.transfer(any(ItemStack.class), eq(source), eq(target)))
                    .thenReturn(0);

            boolean moved = ItemManager.transferItems(source, target, items);

            assertThat(moved).isTrue();
        }

        // A complete move must not touch the snapshots.
        verify(source, never()).setContents(any());
        verify(target, never()).setContents(any());
    }

    @Test
    void transferItems_onShortfall_revertsBothInventories_returnsFalse() {
        Inventory source = mock(Inventory.class);
        Inventory target = mock(Inventory.class);
        ItemStack[] sourceSnapshot = {null};
        ItemStack[] targetSnapshot = {null};
        when(source.getContents()).thenReturn(sourceSnapshot);
        when(target.getContents()).thenReturn(targetSnapshot);
        ItemStack[] items = {mock(ItemStack.class)};

        try (MockedStatic<InventoryUtil> util = mockStatic(InventoryUtil.class)) {
            // Report leftovers — the target could not hold everything.
            util.when(() -> InventoryUtil.transfer(any(ItemStack.class), eq(source), eq(target)))
                    .thenReturn(3);

            boolean moved = ItemManager.transferItems(source, target, items);

            assertThat(moved).isFalse();
        }

        // Both inventories must be restored from their snapshots, and the
        // holders must NOT be refreshed (the move did not happen).
        verify(source).setContents(any(ItemStack[].class));
        verify(target).setContents(any(ItemStack[].class));
        verify(source, never()).getHolder();
        verify(target, never()).getHolder();
    }

    @Test
    void reverseTransfer_forBuy_movesGoodsFromClientBackToOwner() {
        TransactionEvent event = mock(TransactionEvent.class);
        Inventory ownerInv = mock(Inventory.class);
        Inventory clientInv = mock(Inventory.class);
        when(event.getTransactionType()).thenReturn(BUY);
        when(event.getOwnerInventory()).thenReturn(ownerInv);
        when(event.getClientInventory()).thenReturn(clientInv);
        when(event.getStock()).thenReturn(new ItemStack[]{mock(ItemStack.class)});
        when(clientInv.getContents()).thenReturn(new ItemStack[]{null});
        when(ownerInv.getContents()).thenReturn(new ItemStack[]{null});

        try (MockedStatic<InventoryUtil> util = mockStatic(InventoryUtil.class)) {
            util.when(() -> InventoryUtil.transfer(any(ItemStack.class), eq(clientInv), eq(ownerInv)))
                    .thenReturn(0);

            ItemManager.reverseTransfer(event);

            // A buy moved owner -> client, so the reversal must move client -> owner.
            util.verify(() -> InventoryUtil.transfer(any(ItemStack.class), eq(clientInv), eq(ownerInv)));
        }
    }

    @Test
    void reverseTransfer_forSell_movesGoodsFromOwnerBackToClient() {
        TransactionEvent event = mock(TransactionEvent.class);
        Inventory ownerInv = mock(Inventory.class);
        Inventory clientInv = mock(Inventory.class);
        when(event.getTransactionType()).thenReturn(SELL);
        when(event.getOwnerInventory()).thenReturn(ownerInv);
        when(event.getClientInventory()).thenReturn(clientInv);
        when(event.getStock()).thenReturn(new ItemStack[]{mock(ItemStack.class)});
        when(clientInv.getContents()).thenReturn(new ItemStack[]{null});
        when(ownerInv.getContents()).thenReturn(new ItemStack[]{null});

        try (MockedStatic<InventoryUtil> util = mockStatic(InventoryUtil.class)) {
            util.when(() -> InventoryUtil.transfer(any(ItemStack.class), eq(ownerInv), eq(clientInv)))
                    .thenReturn(0);

            ItemManager.reverseTransfer(event);

            // A sell moved client -> owner, so the reversal must move owner -> client.
            util.verify(() -> InventoryUtil.transfer(any(ItemStack.class), eq(ownerInv), eq(clientInv)));
        }
    }
}
