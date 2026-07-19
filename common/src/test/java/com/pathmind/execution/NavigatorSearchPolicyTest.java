package com.pathmind.execution;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigatorSearchPolicyTest {
    @Test
    void acceptsShortSegmentThatMakesRealProgress() {
        assertTrue(NavigatorSearchPolicy.isUsefulPartialPath(3, 36.0D, 9.0D, true));
    }

    @Test
    void acceptsActionableSegmentNearGoal() {
        assertTrue(NavigatorSearchPolicy.isUsefulPartialPath(2, 16.0D, 4.0D, true));
    }

    @Test
    void rejectsNonProgressingOrUnactionableSegment() {
        assertFalse(NavigatorSearchPolicy.isUsefulPartialPath(3, 36.0D, 36.0D, true));
        assertFalse(NavigatorSearchPolicy.isUsefulPartialPath(3, 36.0D, 9.0D, false));
        assertFalse(NavigatorSearchPolicy.isUsefulPartialPath(1, 36.0D, 9.0D, true));
    }

    @Test
    void reservesPartOfDeadlineForModifiedFallback() {
        assertTrue(NavigatorSearchPolicy.cleanSearchBudgetMillis(45L, 0.72D) < 45L);
        assertTrue(NavigatorSearchPolicy.cleanSearchBudgetMillis(45L, 0.72D) >= 30L);
    }

    @Test
    void modifiedSearchOnlyRunsWhenCleanSearchMadeNoUsefulProgress() {
        assertFalse(NavigatorSearchPolicy.shouldUseModifiedFallback(true, true, false));
        assertFalse(NavigatorSearchPolicy.shouldUseModifiedFallback(true, false, true));
        assertTrue(NavigatorSearchPolicy.shouldUseModifiedFallback(false, false, false));
        assertTrue(NavigatorSearchPolicy.shouldUseModifiedFallback(true, false, false));
    }

    @Test
    void requestedGoalIsProtectedFromFailureMemory() {
        BlockPos goal = new BlockPos(4, 64, 9);
        assertTrue(NavigatorSearchPolicy.isProtectedGoal(goal, goal));
        assertFalse(NavigatorSearchPolicy.isProtectedGoal(goal.west(), goal));
    }
}
