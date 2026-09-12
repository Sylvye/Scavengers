package dev.tigermce.scavengers;

import dev.tigermce.scavengers.model.HuntState;
import dev.tigermce.scavengers.model.PrizeTier;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HuntManagerTest {
    @Test void everyPlaceReceivesCompletionPrize() {
        HuntState state = new HuntState();
        ItemStack completion = stackWithClone();
        state.prizes.get(PrizeTier.COMPLETION).add(completion);

        for (int place = 1; place <= 4; place++) {
            assertEquals(1, HuntManager.prizesFor(state, place).size());
        }
    }

    @Test void topThreeAlsoReceiveTheirPlacementPrize() {
        HuntState state = new HuntState();
        state.prizes.get(PrizeTier.COMPLETION).add(stackWithClone());
        ItemStack placementCopy = mock(ItemStack.class);
        ItemStack placement = mock(ItemStack.class);
        when(placement.clone()).thenReturn(placementCopy);
        state.prizes.get(PrizeTier.SECOND).add(placement);

        var prizes = HuntManager.prizesFor(state, 2);
        assertEquals(2, prizes.size());
        assertSame(placementCopy, prizes.get(1));
    }

    private static ItemStack stackWithClone() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.clone()).thenReturn(mock(ItemStack.class));
        return stack;
    }
}
