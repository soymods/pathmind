package com.pathmind.execution;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigatorPlanModelsTest {

    @Test
    void chainedPillarsRemainAtomicWorldModificationSteps() {
        BlockPos base = new BlockPos(4, 70, -3);
        PlannedPrimitive first = pillar(base.above(), base);
        PlannedPrimitive second = pillar(base.above(2), base.above());

        assertTrue(first.isPillar());
        assertTrue(second.isPillar());
        assertTrue(first.requiresPlace());
        assertTrue(second.requiresPlace());
        assertTrue(first.requiresWorldModification());
        assertTrue(second.requiresWorldModification());
        assertFalse(first.isSimpleMovementStep());
        assertFalse(second.isSimpleMovementStep());
    }

    @Test
    void pillarPlacementBudgetCountsEveryLevelOfAnAscent() {
        BlockPos base = BlockPos.ZERO;

        assertTrue(PathmindPathPlanner.requiredPlacementBlocks(List.of(
            pillar(base.above(), base),
            pillar(base.above(2), base.above())
        )) == 2);
    }

    @Test
    void recoveryPolicyIsTypedRatherThanDrivenByLogLabels() {
        assertTrue(RecoveryCause.BLOCKED_JUMP.requiresFreshRoute());
        assertTrue(RecoveryCause.JUMP_RETRY_LIMIT.requiresFreshRoute());
        assertTrue(RecoveryCause.PASSIVE_PATH_STALL.invalidatesPassivePrimitive());
        assertFalse(RecoveryCause.GENERIC.requiresFreshRoute());
        assertFalse(RecoveryCause.GENERIC.invalidatesPassivePrimitive());
    }

    @Test
    void legacyRecoveryLabelsAreMappedOnceAtThePolicyBoundary() {
        assertTrue(RecoveryCause.fromLegacyLabels("blocked jump", "anything") == RecoveryCause.BLOCKED_JUMP);
        assertTrue(RecoveryCause.fromLegacyLabels("anything", "no progress") == RecoveryCause.PASSIVE_PATH_STALL);
        assertTrue(RecoveryCause.fromLegacyLabels("anything", "unknown") == RecoveryCause.GENERIC);
    }

    private static PlannedPrimitive pillar(BlockPos target, BlockPos support) {
        return new PlannedPrimitive(
            target,
            SearchPrimitiveType.PILLAR,
            PlannedPrimitiveType.PILLAR,
            PrimitiveTraversal.VERTICAL_ASCENT,
            PrimitiveExecution.PLACE_THEN_MOVE,
            1,
            0,
            true,
            List.of(),
            support
        );
    }
}
