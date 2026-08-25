package com.pathmind.execution;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NavigatorPlacementPolicyTest {
    private static final BlockPos TARGET = new BlockPos(4, 65, 9);

    @Test
    void waitsForServerConfirmationBeforeRetrying() {
        assertEquals(
            NavigatorPlacementPolicy.Verification.WAITING,
            NavigatorPlacementPolicy.verify(TARGET, TARGET, false, 1, 1_200L, 1_500L)
        );
    }

    @Test
    void acceptsConfirmedWorldUpdateImmediately() {
        assertEquals(
            NavigatorPlacementPolicy.Verification.CONFIRMED,
            NavigatorPlacementPolicy.verify(TARGET, TARGET, true, 3, 2_000L, 1_000L)
        );
    }

    @Test
    void retriesThenExhaustsBoundedPlacementAttempts() {
        assertEquals(
            NavigatorPlacementPolicy.Verification.PROCEED,
            NavigatorPlacementPolicy.verify(TARGET, TARGET, false, 2, 1_500L, 1_500L)
        );
        assertEquals(
            NavigatorPlacementPolicy.Verification.EXHAUSTED,
            NavigatorPlacementPolicy.verify(TARGET, TARGET, false, 3, 1_500L, 1_500L)
        );
        assertEquals(1, NavigatorPlacementPolicy.nextAttemptCount(0));
        assertEquals(3, NavigatorPlacementPolicy.nextAttemptCount(2));
    }

    @Test
    void doesNotCarryVerificationStateToAnotherTarget() {
        assertEquals(
            NavigatorPlacementPolicy.Verification.PROCEED,
            NavigatorPlacementPolicy.verify(TARGET, TARGET.above(), false, 3, 2_000L, 3_000L)
        );
    }
}
