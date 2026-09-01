package com.pathmind.execution;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

enum ControllerMode {
    FOLLOW_PATH,
    RECOVER_JUMP,
    RECOVER_BREAK,
    RECOVER_PILLAR,
    RECOVER_ESCAPE,
    BREAK_BLOCK,
    PILLAR,
    COMMIT_JUMP,
    DROP,
    ESCAPE_HOLE
}

enum FollowSegmentType {
    GROUND,
    CLIMB,
    DROP
}

/** Why recovery was entered. Debug labels are separate from this policy value. */
enum RecoveryCause {
    GENERIC(false),
    BLOCKED_JUMP(true),
    JUMP_RETRY_LIMIT(true),
    PASSIVE_PATH_STALL(false);

    private final boolean requiresFreshRoute;

    RecoveryCause(boolean requiresFreshRoute) {
        this.requiresFreshRoute = requiresFreshRoute;
    }

    boolean requiresFreshRoute() {
        return requiresFreshRoute;
    }

    boolean invalidatesPassivePrimitive() {
        return this == PASSIVE_PATH_STALL;
    }

    static RecoveryCause fromLegacyLabels(String replanReason, String stuckReason) {
        if ("blocked jump".equals(replanReason)) return BLOCKED_JUMP;
        if ("jump retry limit".equals(replanReason)) return JUMP_RETRY_LIMIT;
        if ("front blocked".equals(stuckReason)
            || "ground".equals(stuckReason)
            || "no progress".equals(stuckReason)) return PASSIVE_PATH_STALL;
        return GENERIC;
    }
}

/**
 * Passive compatibility model for one planned route step.  Pass one populates
 * this from the existing state; later passes will make executors own it.
 */
enum RouteStepLifecycle {
    IDLE,
    PENDING,
    EXECUTING,
    WAITING_FOR_CONFIRMATION,
    COMPLETE,
    FAILED
}

record RouteStepExecution(
    PlannedPrimitive primitive,
    BlockPos waypoint,
    BlockPos expectedWorldChange,
    RouteStepLifecycle lifecycle,
    long activatedAtMs
) {
    static RouteStepExecution idle() {
        return new RouteStepExecution(null, null, null, RouteStepLifecycle.IDLE, 0L);
    }
}

record MiningProgress(boolean completed, int resumeIndex, boolean minedAscent) {
    static MiningProgress incomplete() {
        return new MiningProgress(false, -1, false);
    }
}

record MiningTargetState(List<BlockPos> requiredTargets, BlockPos target, boolean currentlyActive, boolean completed) {
    static MiningTargetState incomplete(List<BlockPos> requiredTargets) {
        return new MiningTargetState(requiredTargets != null ? List.copyOf(requiredTargets) : List.of(), null, false, false);
    }

    static MiningTargetState complete(List<BlockPos> requiredTargets) {
        return new MiningTargetState(requiredTargets != null ? List.copyOf(requiredTargets) : List.of(), null, false, true);
    }
}

record PlacementTargetState(BlockPos target, boolean completed) {
    static PlacementTargetState incomplete(BlockPos target) {
        return new PlacementTargetState(target != null ? target.immutable() : null, false);
    }

    static PlacementTargetState complete(BlockPos target) {
        return new PlacementTargetState(target != null ? target.immutable() : null, true);
    }
}

record PlacementProgress(boolean completed, int resumeIndex) {
    static PlacementProgress incomplete() {
        return new PlacementProgress(false, -1);
    }
}

enum MiningAscentPhase {
    CLEARANCE,
    ADVANCE,
    JUMP
}

enum PillarPhase {
    CENTER,
    ASCEND,
    PLACE,
    SUPPORT_READY
}

record BreakTargeting(BlockPos target, Direction face, Vec3 hitPos) {
}

enum EscapePrimitiveType {
    MOVE,
    MINE,
    PILLAR
}

record EscapePrimitive(EscapePrimitiveType type, BlockPos target) {
}

record EscapePlan(Direction direction, List<BlockPos> route, List<EscapePrimitive> primitives) {
    static EscapePlan empty() {
        return new EscapePlan(Direction.NORTH, List.of(), List.of());
    }

    boolean isEmpty() {
        return primitives == null || primitives.isEmpty();
    }

    List<BlockPos> breakTargets() {
        if (primitives == null || primitives.isEmpty()) {
            return List.of();
        }
        List<BlockPos> targets = new ArrayList<>();
        for (EscapePrimitive primitive : primitives) {
            if (primitive != null
                && primitive.type() == EscapePrimitiveType.MINE
                && primitive.target() != null
                && !targets.contains(primitive.target())) {
                targets.add(primitive.target());
            }
        }
        return List.copyOf(targets);
    }
}

record ExcavationPlan(EscapePlan escapePlan) {
}

record StairEscapePlan(EscapePlan escapePlan) {
    List<BlockPos> route() {
        return escapePlan.route();
    }
}

record PlacementTarget(BlockPos supportPos, Direction face, Vec3 hitPos) {
}
