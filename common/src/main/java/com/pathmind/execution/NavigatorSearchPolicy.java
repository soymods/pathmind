package com.pathmind.execution;

import net.minecraft.core.BlockPos;

final class NavigatorSearchPolicy {
    private static final double MINIMUM_PROGRESS_BLOCKS = 1.0D;
    private static final double NEAR_GOAL_DISTANCE_SQ = 4.0D;

    private NavigatorSearchPolicy() {
    }

    static boolean isUsefulPartialPath(
        int pathSize,
        double startDistanceSq,
        double endDistanceSq,
        boolean endActionable
    ) {
        if (!endActionable
            || pathSize < 2
            || !Double.isFinite(startDistanceSq)
            || !Double.isFinite(endDistanceSq)
            || startDistanceSq < 0.0D
            || endDistanceSq < 0.0D) {
            return false;
        }
        if (endDistanceSq <= NEAR_GOAL_DISTANCE_SQ) {
            return true;
        }
        double progress = Math.sqrt(startDistanceSq) - Math.sqrt(endDistanceSq);
        return progress >= MINIMUM_PROGRESS_BLOCKS;
    }

    static long cleanSearchBudgetMillis(long remainingMillis, double fraction) {
        if (remainingMillis <= 0L) {
            return 1L;
        }
        double boundedFraction = Math.max(0.0D, Math.min(1.0D, fraction));
        return Math.max(1L, Math.min(remainingMillis, (long) Math.ceil(remainingMillis * boundedFraction)));
    }

    static boolean shouldUseModifiedFallback(boolean hasPath, boolean reachedGoal, boolean usefulPartial) {
        return !hasPath || (!reachedGoal && !usefulPartial);
    }

    static boolean isProtectedGoal(BlockPos candidate, BlockPos requestedGoal) {
        return candidate != null && requestedGoal != null && candidate.equals(requestedGoal);
    }
}
