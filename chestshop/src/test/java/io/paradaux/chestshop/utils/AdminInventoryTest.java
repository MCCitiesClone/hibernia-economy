package io.paradaux.chestshop.utils;

import io.paradaux.chestshop.utils.MaterialUtil;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for {@link AdminInventory} (ADT-45): {@code setItem} must
 * grow the backing array so the target index is writable, and {@code contains}
 * must total the matched stacks' amounts rather than the query stack's.
 */
class AdminInventoryTest {

    @Test
    void setItem_indexBeyondCurrentLength_growsArrayInsteadOfThrowing() {
        AdminInventory inventory = new AdminInventory(new ItemStack[0], mock(MaterialUtil.class));
        ItemStack stack = mock(ItemStack.class);

        // Previously copyOfRange(content, 0, i) produced a length-i array and
        // writing content[i] threw ArrayIndexOutOfBoundsException.
        inventory.setItem(3, stack);

        assertThat(inventory.getItem(3)).isSameAs(stack);
        assertThat(inventory.getContents()).hasSize(4);
    }

    @Test
    void contains_totalsMatchedStackAmounts_notTheQueryAmount() {
        ItemStack stored1 = mock(ItemStack.class);
        ItemStack stored2 = mock(ItemStack.class);
        when(stored1.getAmount()).thenReturn(10);
        when(stored2.getAmount()).thenReturn(10);

        MaterialUtil materialUtil = mock(MaterialUtil.class);
        when(materialUtil.equals(any(), any())).thenReturn(true);
        AdminInventory inventory = new AdminInventory(new ItemStack[]{stored1, stored2}, materialUtil);

        ItemStack query = mock(ItemStack.class);
        when(query.getAmount()).thenReturn(1); // query amount must be ignored

        // Two matched stacks of 10 each = 20 available, so 15 is satisfiable.
        assertThat(inventory.contains(query, 15)).isTrue();
        // ...but 25 is not. (The old bug summed the query's amount of 1
        // per match = 2, making even small requests fail.)
        assertThat(inventory.contains(query, 25)).isFalse();
    }
}
