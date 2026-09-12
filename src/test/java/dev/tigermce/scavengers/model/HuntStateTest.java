package dev.tigermce.scavengers.model;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HuntStateTest {
    @Test void unknownPlayersBeginAtFirstObjective() {
        assertEquals(0, new HuntState().progress(UUID.randomUUID()));
    }
}
