package dev.tigermce.scavengers.model;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HuntItemTest {
    @Test void snapshotsAndNormalizesTheConfiguredStack() {
        ItemStack source = mock(ItemStack.class);
        ItemStack snapshot = mock(ItemStack.class);
        ItemStack readCopy = mock(ItemStack.class);
        when(source.clone()).thenReturn(snapshot);
        when(snapshot.clone()).thenReturn(readCopy);
        HuntItem target = new HuntItem(source, 100);
        verify(snapshot).setAmount(1);
        assertEquals(100, target.amount());
        assertSame(readCopy, target.item());
    }

    @Test void clampsInvalidAmounts() {
        ItemStack source = mock(ItemStack.class);
        when(source.clone()).thenReturn(mock(ItemStack.class));
        assertEquals(1, new HuntItem(source, 0).amount());
    }
}
