package com.pathmind.execution;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigatorFailureMemoryTest {
    @Test
    void failedMoveTracksDestinationAndDirectedEdgeUntilExpiry() {
        NavigatorFailureMemory memory = new NavigatorFailureMemory();
        BlockPos from = new BlockPos(1, 64, 1);
        BlockPos to = new BlockPos(2, 64, 1);

        memory.rememberMove(from, to, 100L, 50L, false);

        assertTrue(memory.isFailedNode(to, 149L));
        assertTrue(memory.isFailedEdge(from, to, 149L));
        assertFalse(memory.isFailedEdge(to, from, 149L));
        assertFalse(memory.isFailedNode(to, 150L));
    }

    @Test
    void protectedGoalDoesNotBecomeAFailedMove() {
        NavigatorFailureMemory memory = new NavigatorFailureMemory();
        BlockPos from = new BlockPos(1, 64, 1);
        BlockPos goal = new BlockPos(2, 64, 1);

        memory.rememberMove(from, goal, 100L, 50L, true);

        assertFalse(memory.isFailedNode(goal, 120L));
        assertFalse(memory.isFailedEdge(from, goal, 120L));
    }

    @Test
    void actionFailuresRemainIndependent() {
        NavigatorFailureMemory memory = new NavigatorFailureMemory();
        BlockPos from = new BlockPos(1, 64, 1);
        BlockPos to = new BlockPos(2, 65, 1);

        memory.rememberAction(NavigatorFailureMemory.Action.JUMP, from, to, 100L, 75L);

        assertTrue(memory.isFailedAction(NavigatorFailureMemory.Action.JUMP, from, to, 174L));
        assertFalse(memory.isFailedAction(NavigatorFailureMemory.Action.BREAK, from, to, 174L));
        assertFalse(memory.isFailedNode(to, 174L));
    }

    @Test
    void failedPillarDoesNotBlockASeparateHigherPillarStep() {
        NavigatorFailureMemory memory = new NavigatorFailureMemory();
        BlockPos base = new BlockPos(1, 64, 1);
        BlockPos firstPillarTarget = base.above();
        BlockPos secondPillarTarget = firstPillarTarget.above();

        memory.rememberAction(NavigatorFailureMemory.Action.PILLAR, base, firstPillarTarget, 100L, 75L);

        assertTrue(memory.isFailedAction(NavigatorFailureMemory.Action.PILLAR, base, firstPillarTarget, 174L));
        assertFalse(memory.isFailedAction(NavigatorFailureMemory.Action.PILLAR, firstPillarTarget, secondPillarTarget, 174L));
        assertFalse(memory.isFailedAction(NavigatorFailureMemory.Action.PILLAR, base, firstPillarTarget, 175L));
    }

    @Test
    void pruneAndClearRemoveRecordedFailures() {
        NavigatorFailureMemory memory = new NavigatorFailureMemory();
        BlockPos from = BlockPos.ZERO;
        BlockPos to = BlockPos.ZERO.east();

        memory.rememberMove(from, to, 100L, 50L, false);
        memory.rememberAction(NavigatorFailureMemory.Action.PLACE, from, to, 100L, 100L);
        memory.prune(150L);

        assertFalse(memory.isFailedEdge(from, to, 150L));
        assertTrue(memory.isFailedAction(NavigatorFailureMemory.Action.PLACE, from, to, 150L));

        memory.clear();
        assertFalse(memory.isFailedAction(NavigatorFailureMemory.Action.PLACE, from, to, 150L));
    }
}
