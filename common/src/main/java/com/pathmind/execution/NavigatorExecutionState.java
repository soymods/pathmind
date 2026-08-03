package com.pathmind.execution;

import java.util.List;
import net.minecraft.core.BlockPos;

final class NavigatorExecutionState {
    long lastJumpAtMs;
    long lastMiningJumpGateLogAtMs;
    long lastMiningResumeLogAtMs;
    BlockPos committedJumpWaypoint;
    long committedJumpUntilMs;
    BlockPos lastJumpAttemptWaypoint;
    int repeatedJumpAttempts;
    long lastInteractAtMs;
    BlockPos activeBreakTarget;
    MiningAscentPhase activeMiningAscentPhase = MiningAscentPhase.CLEARANCE;
    PillarPhase activePillarPhase = PillarPhase.CENTER;
    List<BlockPos> plannedBreakTargets = List.of();
    BlockPos committedEscapeTarget;
    long committedEscapeUntilMs;
    EscapePlan committedEscape = EscapePlan.empty();
    int committedEscapePrimitiveIndex;
    ControllerMode controllerMode = ControllerMode.FOLLOW_PATH;
    BlockPos controllerTarget;
    long controllerUntilMs;
    long controllerEnteredAtMs;
    long controllerProgressAtMs;
    double controllerBestDistanceSq = Double.POSITIVE_INFINITY;
    BlockPos lastPlaceTarget;
    String lastPlaceResult = "none";
    FollowSegmentType activeFollowSegment = FollowSegmentType.GROUND;
    BlockPos activeFollowSegmentTarget;
    PlannedPrimitive activePlannedPrimitive;
    long activeFollowSegmentEnteredAtMs;
    long activeFollowSegmentProgressAtMs;
    double activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
}
