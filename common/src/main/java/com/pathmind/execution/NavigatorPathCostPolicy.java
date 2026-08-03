package com.pathmind.execution;

import java.util.List;
import net.minecraft.core.BlockPos;

final class NavigatorPathCostPolicy {
    enum MoveType {
        STRAIGHT,
        DIAGONAL,
        STEP_UP,
        DROP,
        WATER_ENTER,
        WATER_SWIM,
        WATER_EXIT,
        CLIMB_UP,
        CLIMB_DOWN,
        INTERACTABLE
    }

    private NavigatorPathCostPolicy() {
    }

    static double moveTypePenalty(MoveType moveType) {
        return switch (moveType) {
            case STRAIGHT -> 0.0D;
            case DIAGONAL -> 0.22D;
            case STEP_UP -> 0.7D;
            case DROP -> 0.12D;
            case WATER_ENTER -> 0.8D;
            case WATER_SWIM -> 1.1D;
            case WATER_EXIT -> 0.55D;
            case CLIMB_UP -> 0.55D;
            case CLIMB_DOWN -> 0.2D;
            case INTERACTABLE -> 0.18D;
        };
    }

    static double turnPenalty(
        BlockPos previous,
        BlockPos current,
        BlockPos next,
        double diagonalPenalty,
        double cornerPenalty,
        double reversePenalty
    ) {
        if (previous == null || current == null || next == null) {
            return 0.0D;
        }
        int prevDx = Integer.compare(current.getX() - previous.getX(), 0);
        int prevDz = Integer.compare(current.getZ() - previous.getZ(), 0);
        int nextDx = Integer.compare(next.getX() - current.getX(), 0);
        int nextDz = Integer.compare(next.getZ() - current.getZ(), 0);
        if (prevDx == nextDx && prevDz == nextDz) {
            return 0.0D;
        }
        if (prevDx == -nextDx && prevDz == -nextDz) {
            return reversePenalty;
        }
        boolean diagonalTurn = Math.abs(prevDx + prevDz) == 1 && Math.abs(nextDx + nextDz) == 2
            || Math.abs(prevDx + prevDz) == 2 && Math.abs(nextDx + nextDz) == 1;
        return diagonalTurn ? diagonalPenalty : cornerPenalty;
    }

    static double heuristic(BlockPos pos, List<BlockPos> goals, double weight) {
        double best = Double.POSITIVE_INFINITY;
        for (BlockPos goal : goals) {
            double dx = Math.abs(pos.getX() - goal.getX());
            double dz = Math.abs(pos.getZ() - goal.getZ());
            double min = Math.min(dx, dz);
            double max = Math.max(dx, dz);
            double octile = min * Math.sqrt(2.0D) + (max - min);
            double verticalPenalty = Math.abs(pos.getY() - goal.getY()) * 1.15D;
            best = Math.min(best, octile + verticalPenalty);
        }
        return best == Double.POSITIVE_INFINITY ? 0.0D : best * weight;
    }

    static double elevationPenalty(BlockPos from, BlockPos to) {
        int delta = to.getY() - from.getY();
        if (delta > 0) {
            return delta * 0.35D;
        }
        if (delta < 0) {
            return Math.abs(delta) * 0.12D;
        }
        return 0.0D;
    }

    static double horizontalDistanceSq(BlockPos first, BlockPos second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }
}
