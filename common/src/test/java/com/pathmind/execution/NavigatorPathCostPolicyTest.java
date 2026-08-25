package com.pathmind.execution;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigatorPathCostPolicyTest {
    @Test
    void heuristicUsesOctileHorizontalDistanceAndVerticalWeight() {
        BlockPos start = new BlockPos(0, 64, 0);
        BlockPos goal = new BlockPos(3, 66, 4);

        double expected = (3.0D * Math.sqrt(2.0D) + 1.0D + 2.0D * 1.15D) * 1.18D;

        assertEquals(expected, NavigatorPathCostPolicy.heuristic(start, List.of(goal), 1.18D));
        assertEquals(0.0D, NavigatorPathCostPolicy.heuristic(start, List.of(), 1.18D));
    }

    @Test
    void turnPenaltyDistinguishesStraightCornerDiagonalAndReverse() {
        BlockPos origin = BlockPos.ZERO;
        BlockPos east = origin.east();

        assertEquals(0.0D, NavigatorPathCostPolicy.turnPenalty(origin, east, east.east(), 0.08D, 0.28D, 0.9D));
        assertEquals(0.9D, NavigatorPathCostPolicy.turnPenalty(origin, east, origin, 0.08D, 0.28D, 0.9D));
        assertEquals(0.28D, NavigatorPathCostPolicy.turnPenalty(origin, east, east.south(), 0.08D, 0.28D, 0.9D));
        assertEquals(0.08D, NavigatorPathCostPolicy.turnPenalty(origin, east, east.east().south(), 0.08D, 0.28D, 0.9D));
    }

    @Test
    void elevationAndMovementPenaltiesPreservePlannerPreferences() {
        BlockPos origin = BlockPos.ZERO;

        assertEquals(0.35D, NavigatorPathCostPolicy.elevationPenalty(origin, origin.above()));
        assertEquals(0.12D, NavigatorPathCostPolicy.elevationPenalty(origin, origin.below()));
        assertTrue(NavigatorPathCostPolicy.moveTypePenalty(NavigatorPathCostPolicy.MoveType.STEP_UP)
            > NavigatorPathCostPolicy.moveTypePenalty(NavigatorPathCostPolicy.MoveType.DROP));
        assertTrue(NavigatorPathCostPolicy.moveTypePenalty(NavigatorPathCostPolicy.MoveType.WATER_SWIM)
            > NavigatorPathCostPolicy.moveTypePenalty(NavigatorPathCostPolicy.MoveType.STRAIGHT));
    }

    @Test
    void horizontalDistanceIgnoresElevation() {
        assertEquals(25.0D, NavigatorPathCostPolicy.horizontalDistanceSq(
            new BlockPos(1, 10, 2),
            new BlockPos(4, 90, 6)
        ));
    }

    @Test
    void placementBudgetCountsPillarsAndSupportPlacements() {
        BlockPos origin = BlockPos.ZERO;
        PlannedPrimitive pillar = new PlannedPrimitive(
            origin.above(), SearchPrimitiveType.PILLAR, PlannedPrimitiveType.PILLAR,
            PrimitiveTraversal.VERTICAL_ASCENT, PrimitiveExecution.PLACE_THEN_MOVE,
            1, 0, true, List.of(), origin
        );
        PlannedPrimitive support = new PlannedPrimitive(
            origin.east(), SearchPrimitiveType.PLACE_FORWARD, PlannedPrimitiveType.WALK,
            PrimitiveTraversal.GROUND, PrimitiveExecution.PLACE_THEN_MOVE,
            0, 1, false, List.of(), origin.east().below()
        );
        PlannedPrimitive walk = new PlannedPrimitive(
            origin.south(), SearchPrimitiveType.WALK, PlannedPrimitiveType.WALK,
            PrimitiveTraversal.GROUND, PrimitiveExecution.CONTINUOUS_MOVEMENT,
            0, 1, false, List.of(), null
        );

        assertEquals(2, PathmindPathPlanner.requiredPlacementBlocks(List.of(pillar, support, walk)));
    }
}
