package com.pathmind.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import static com.pathmind.execution.PathmindNavigator.MAX_DROP_DOWN;
import static com.pathmind.execution.PathmindNavigator.MAX_GOAL_PATH_ATTEMPTS;
import static com.pathmind.execution.PathmindNavigator.PLACE_MOVE_PENALTY;

final class PathmindPathPlanner {
    private static final double PLAYER_HALF_WIDTH = 0.30D;
    private static final double PLAYER_HEIGHT = 1.80D;
    private static final double COLLISION_EPSILON = 1.0E-5D;
    private static final double MAX_AUTOSTEP_HEIGHT = 0.60D;
    private static final double STEERING_LOOKAHEAD_DISTANCE = 1.35D;
    private static final long FAILED_JUMP_MEMORY_MS = 9000L;
    private static final long FAILED_BREAK_MEMORY_MS = 9000L;
    private static final long FAILED_DROP_MEMORY_MS = 9000L;
    private static final long FAILED_PLACE_MEMORY_MS = 10000L;
    private static final long FAILED_PILLAR_MEMORY_MS = 12000L;
    private static final long FAILED_MOVE_MEMORY_MS = 7000L;
    private static final double FAILED_MOVE_PENALTY = 8.0D;
    private static final int MAX_STEP_UP = 1;
    private static final int MAX_SAFE_FALL_DISTANCE = 3;
    private static final int MAX_PLANNED_PILLAR_STEPS = 8;
    private static final int SEARCH_RADIUS = 56;
    private static final int MAX_SEARCH_RADIUS = 72;
    private static final int SEARCH_HEIGHT = 18;
    private static final int MAX_SEARCH_HEIGHT = 48;
    private static final int GOAL_SEARCH_RADIUS = 5;
    private static final int MAX_EXPANSIONS = 64000;
    private static final int MAX_GOAL_CANDIDATES = 10;
    private static final int MAX_VISIBLE_CANDIDATE_PATHS = 3;
    private static final long PATHFIND_TIME_BUDGET_MS = 45L;
    private static final double CLEAN_SEARCH_BUDGET_FRACTION = 0.72D;
    private static final long COARSE_PATHFIND_TIME_BUDGET_MS = 20L;
    private static final int COARSE_MAX_EXPANSIONS = 90000;
    private static final int COARSE_LOOKAHEAD_STEPS = 18;
    private static final double COARSE_PLANNING_DISTANCE_SQ = 18.0D * 18.0D;
    private static final double WATER_PENALTY = 3.5D;
    private static final double WATER_AVOIDANCE_PENALTY = 12.0D;
    private static final double FLOWING_WATER_PENALTY = 2.5D;
    private static final double DEEP_WATER_PENALTY = 2.0D;
    private static final double WATER_DANGER_PENALTY = 10.0D;
    private static final double WATER_NO_EXIT_PENALTY = 4.0D;
    private static final double EDGE_PENALTY = 1.25D;
    private static final double DANGER_PENALTY = 12.0D;
    private static final double BREAK_MOVE_PENALTY = 4.5D;
    private static final double BREAK_ASSIST_SURCHARGE = 14.0D;
    private static final double PLACE_ASSIST_SURCHARGE = 32.0D;
    private static final double PATH_BREAK_ROUTE_PENALTY = 240.0D;
    private static final double PATH_PLACE_ROUTE_PENALTY = 420.0D;
    private static final double SEARCH_JUMP_PENALTY = 0.65D;
    private static final double SEARCH_DESCEND_PENALTY = 0.08D;
    private static final double SEARCH_CLIMB_PENALTY = 0.45D;
    private static final double SEARCH_SWIM_PENALTY = 0.95D;
    private static final double SEARCH_INTERACT_PENALTY = 0.16D;
    private static final double SEARCH_BREAK_PENALTY = 4.5D;
    private static final double SEARCH_PLACE_PENALTY = 7.5D;
    private static final double SEARCH_PILLAR_PENALTY = 12.0D;
    private static final double LOCAL_TARGET_PROGRESS_WEIGHT = 1.6D;
    private static final double LOCAL_TARGET_STEP_WEIGHT = 0.45D;
    private static final double LOCAL_TARGET_MODIFICATION_WEIGHT = 2.2D;
    private static final double LOCAL_TARGET_COMMITTED_WEIGHT = 0.9D;
    private static final int LOCAL_TARGET_TAIL_WINDOW = 6;
    private static final double GOAL_MODIFICATION_AVOID_DISTANCE_SQ = 6.25D;
    private static final double TREE_CANOPY_PENALTY = 26.0D;
    private static final double TREE_CANOPY_MODIFICATION_PENALTY = 18.0D;
    private static final double TURN_PENALTY_DIAGONAL = 0.08D;
    private static final double TURN_PENALTY_CORNER = 0.28D;
    private static final double TURN_PENALTY_REVERSE = 0.9D;
    private static final double HEURISTIC_WEIGHT = 1.18D;
    private static final int MIN_PARTIAL_PATH_LENGTH = 6;
    private static final double DIG_ESCAPE_MOVE_PENALTY = 1.35D;
    private static final double DIG_BREAKOUT_MOVE_PENALTY = 1.1D;
    private static final Move[] MOVES = {
        new Move(0, -1, 1.0D),
        new Move(0, 1, 1.0D),
        new Move(-1, 0, 1.0D),
        new Move(1, 0, 1.0D)
    };

    record SteeringLookahead(BlockPos waypoint, PlannedPrimitive primitive) {
    }

    interface Host {
        boolean allowBlockBreaking();
        boolean allowBlockPlacing();
        int availablePlacementBlocks();
        PathmindNavigator.WaterMode waterMode();
        BlockPos targetPos();
        boolean isProtectedNavigationGoal(BlockPos pos);
        void recordPlanningDiagnostics(NavigatorPlanningCache cache, PathComputation result, long elapsedMs);
        String formatDebugPos(BlockPos pos);
        boolean isWaypointActionable(Level world, BlockPos waypoint);
        List<PlannedPrimitive> buildPlannedPrimitives(Level world, List<BlockPos> path, BlockPos startPos);
        boolean requiresBreakingForWaypoint(Level world, BlockPos waypoint);
        PlannedPrimitive createPlannedPrimitive(Level world, BlockPos from, BlockPos to,
                                                List<BlockPos> breakTargets, BlockPos placeTarget);
        Direction preferredEscapeDirection(Level world, BlockPos current, BlockPos goal, long now);
        boolean isDirectGoalCompletionCandidate(BlockPos candidate, BlockPos target);
        boolean isJumpPrimitive(PlannedPrimitive primitive);
        SteeringLookahead steeringLookahead(BlockPos activeWaypoint);
    }

    private final Host host;
    private final NavigatorFailureMemory failureMemory = new NavigatorFailureMemory();
    private final ThreadLocal<NavigatorPlanningCache> activePlanningCache = new ThreadLocal<>();

    PathmindPathPlanner(Host host) {
        this.host = host;
    }

    void clearFailureMemory() {
        failureMemory.clear();
    }

    PathComputation findPath(ClientLevel world, BlockPos start, BlockPos target) {
        if (world == null || start == null || target == null) {
            return new PathComputation(List.of(), List.of(), List.of(), null, GoalMode.EXACT, FailureReason.CLIENT_UNAVAILABLE, null);
        }

        NavigatorPlanningCache previousCache = activePlanningCache.get();
        NavigatorPlanningCache cache = new NavigatorPlanningCache(world);
        activePlanningCache.set(cache);
        long startedNanos = System.nanoTime();
        PathComputation result = null;
        try {
            result = findPathCached(world, start, target);
            return result;
        } finally {
            long elapsedMs = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
            host.recordPlanningDiagnostics(cache, result, elapsedMs);
            if (previousCache == null) {
                activePlanningCache.remove();
            } else {
                activePlanningCache.set(previousCache);
            }
        }
    }

    PathComputation findPathCached(ClientLevel world, BlockPos start, BlockPos target) {

        long overallDeadlineMs = System.currentTimeMillis() + PATHFIND_TIME_BUDGET_MS;
        BlockPos normalizedStart = isNavigableNode(world, start) ? start.immutable() : findNearbyStandable(world, start, 2);
        if (normalizedStart == null) {
            return new PathComputation(List.of(), List.of(), List.of(), null, GoalMode.EXACT, FailureReason.NO_START_SPACE, "Move to a standable block before retrying.");
        }

        BlockPos planningTarget = resolvePlanningTarget(world, normalizedStart, target);
        if (planningTarget == null) {
            return new PathComputation(List.of(), List.of(), List.of(), null, GoalMode.EXACT, FailureReason.NO_LOADED_FRONTIER, "The planner could not project a loaded route corridor toward " + host.formatDebugPos(target) + ".");
        }

        BlockPos exactPlanningTarget = planningTarget;
        if (shouldUseHierarchicalPlanning(normalizedStart, planningTarget)) {
            List<BlockPos> coarsePath = findCoarsePath(world, normalizedStart, planningTarget);
            BlockPos localPlanningTarget = selectLocalPlanningTarget(world, normalizedStart, coarsePath, planningTarget);
            if (localPlanningTarget != null) {
                planningTarget = localPlanningTarget;
            }
        }

        GoalSearchOutcome searchOutcome = searchPlanningTarget(world, normalizedStart, planningTarget, target, overallDeadlineMs);
        List<ScoredPath> scoredPaths = searchOutcome.scoredPaths();
        FailureReason lastFailure = searchOutcome.failureReason();
        String lastFailureDetail = searchOutcome.failureDetail();

        if (scoredPaths.isEmpty() && !planningTarget.equals(exactPlanningTarget)) {
            searchOutcome = searchPlanningTarget(world, normalizedStart, exactPlanningTarget, target, overallDeadlineMs);
            scoredPaths = searchOutcome.scoredPaths();
            lastFailure = searchOutcome.failureReason();
            lastFailureDetail = searchOutcome.failureDetail();
            planningTarget = exactPlanningTarget;
        }

        if (scoredPaths.isEmpty()) {
            return new PathComputation(List.of(), List.of(), List.of(), null, GoalMode.EXACT, lastFailure, lastFailureDetail);
        }

        List<ScoredPath> cleanScoredPaths = scoredPaths.stream()
            .filter(path -> !pathRequiresModification(path.plannedPrimitives()))
            .collect(Collectors.toCollection(ArrayList::new));
        if (!cleanScoredPaths.isEmpty()) {
            scoredPaths = cleanScoredPaths;
        }
        scoredPaths.sort(Comparator.comparingDouble(ScoredPath::cost));
        List<List<BlockPos>> visibleCandidates = scoredPaths.stream()
            .map(ScoredPath::path)
            .limit(MAX_VISIBLE_CANDIDATE_PATHS)
            .toList();
        BlockPos resolvedGoal = scoredPaths.get(0).path().isEmpty() ? null : scoredPaths.get(0).path().get(scoredPaths.get(0).path().size() - 1);
        GoalMode goalMode = resolvedGoal != null && resolvedGoal.equals(target) ? GoalMode.EXACT : GoalMode.NEAREST_STANDABLE;
        return new PathComputation(scoredPaths.get(0).path(), scoredPaths.get(0).plannedPrimitives(), visibleCandidates, resolvedGoal, goalMode, null, null);
    }

    GoalSearchOutcome searchPlanningTarget(
        ClientLevel world,
        BlockPos start,
        BlockPos planningTarget,
        BlockPos exactTarget,
        long deadlineMs
    ) {
        boolean planningExactRequestedBlock = planningTarget.equals(exactTarget);
        boolean exactGoalNeedsUnavailableSupport = planningExactRequestedBlock
            && needsPlacedSupport(world, exactTarget)
            && (!host.allowBlockPlacing() || host.availablePlacementBlocks() <= 0);
        List<BlockPos> goalCandidates = planningExactRequestedBlock
            && !exactGoalNeedsUnavailableSupport
            && isWithinSearchBounds(start, exactTarget, exactTarget)
            && isChunkLoaded(world, exactTarget)
            && isGoalNodeReachable(world, exactTarget)
            && !isHardDanger(world, exactTarget)
                ? List.of(exactTarget.immutable())
                : planningExactRequestedBlock
                    ? List.of()
                    : collectGoalCandidates(world, start, planningTarget);
        if (goalCandidates.isEmpty() && !planningExactRequestedBlock) {
            BlockPos nearby = findNearbyStandable(world, planningTarget, 4);
            if (nearby != null) {
                goalCandidates = List.of(nearby);
            }
        }
        if (goalCandidates.isEmpty()) {
            String detail = exactGoalNeedsUnavailableSupport
                ? "The exact target block " + host.formatDebugPos(exactTarget) + " needs a solid placement block for support, but none is available."
                : planningExactRequestedBlock
                ? "The exact target block " + host.formatDebugPos(exactTarget) + " is not a safe occupiable endpoint."
                : "No walkable endpoint was found near " + host.formatDebugPos(planningTarget) + ".";
            return new GoalSearchOutcome(List.of(), FailureReason.NO_GOAL_SPACE, detail);
        }

        List<ScoredPath> scoredPaths = new ArrayList<>();
        FailureReason lastFailure = FailureReason.NO_ROUTE;
        String lastFailureDetail = "The planner did not find a viable route toward " + host.formatDebugPos(planningTarget) + ".";
        int candidateCount = Math.min(MAX_GOAL_PATH_ATTEMPTS, goalCandidates.size());
        for (int i = 0; i < candidateCount; i++) {
            if (System.currentTimeMillis() >= deadlineMs) {
                lastFailure = FailureReason.SEARCH_LIMIT;
                lastFailureDetail = "The bounded planner exhausted its per-tick search budget toward " + host.formatDebugPos(planningTarget) + ".";
                break;
            }
            BlockPos candidateGoal = goalCandidates.get(i);
            long searchStartedMs = System.currentTimeMillis();
            long remainingMs = Math.max(1L, deadlineMs - searchStartedMs);
            long cleanBudgetMs = NavigatorSearchPolicy.cleanSearchBudgetMillis(remainingMs, CLEAN_SEARCH_BUDGET_FRACTION);
            long cleanDeadlineMs = Math.min(deadlineMs, searchStartedMs + cleanBudgetMs);
            PathSearchResult result = findPathToGoal(world, start, candidateGoal, cleanDeadlineMs, false);
            if (shouldTryModifiedSearch(world, start, candidateGoal, result)
                && (host.allowBlockBreaking() || host.allowBlockPlacing())
                && System.currentTimeMillis() < deadlineMs) {
                result = findPathToGoal(world, start, candidateGoal, deadlineMs, true);
            }
            if (!result.path().isEmpty()) {
                List<BlockPos> candidatePath = result.path();
                List<PlannedPrimitive> candidatePlan = result.plannedPrimitives();
                boolean exactPath = endsAtGoal(candidatePath, candidateGoal);
                if (!exactPath) {
                    BlockPos partialEnd = candidatePath.get(candidatePath.size() - 1);
                    boolean usefulPartial = NavigatorSearchPolicy.isUsefulPartialPath(
                        candidatePath.size(),
                        horizontalDistanceSq(start, candidateGoal),
                        horizontalDistanceSq(partialEnd, candidateGoal),
                        host.isWaypointActionable(world, partialEnd)
                    );
                    if (candidateGoal.equals(exactTarget)) {
                        boolean acceptableNearGoal = partialEnd != null
                            && horizontalDistanceSq(partialEnd, exactTarget) <= 4.0D
                            && Math.abs(partialEnd.getY() - exactTarget.getY()) <= MAX_DROP_DOWN
                            && host.isWaypointActionable(world, partialEnd);
                        if (!acceptableNearGoal && !usefulPartial) {
                            lastFailure = result.timedOut() ? FailureReason.SEARCH_LIMIT : FailureReason.NO_ROUTE;
                            lastFailureDetail = result.timedOut()
                                ? "Search reached the bounded planning deadline after making insufficient progress toward " + host.formatDebugPos(candidateGoal) + "."
                                : "The exact target " + host.formatDebugPos(candidateGoal) + " could not be reached exactly.";
                            continue;
                        }
                    }
                    if (!usefulPartial) {
                        lastFailure = result.timedOut() ? FailureReason.SEARCH_LIMIT : FailureReason.NO_ROUTE;
                        lastFailureDetail = result.timedOut()
                            ? "Search reached the bounded planning deadline before producing a useful route segment toward " + host.formatDebugPos(candidateGoal) + "."
                            : "Only a non-progressing partial path was found toward " + host.formatDebugPos(candidateGoal) + ".";
                        continue;
                    }
                }
                if (!isViablePlannedPath(world, candidatePath, candidatePlan)) {
                    lastFailure = FailureReason.NO_ROUTE;
                    lastFailureDetail = "The planner produced an invalid movement sequence toward " + host.formatDebugPos(candidateGoal) + ".";
                    continue;
                }
                int requiredPlacementBlocks = requiredPlacementBlocks(candidatePlan);
                int availablePlacementBlocks = host.availablePlacementBlocks();
                if (requiredPlacementBlocks > availablePlacementBlocks) {
                    lastFailure = FailureReason.NO_ROUTE;
                    lastFailureDetail = "The route to " + host.formatDebugPos(candidateGoal)
                        + " needs " + requiredPlacementBlocks + " placement blocks, but only " + availablePlacementBlocks + " are available.";
                    continue;
                }
                double scoredCost = result.cost()
                    + pathStructurePenalty(candidatePath, candidatePlan)
                    + pathModificationPenalty(candidatePlan);
                scoredPaths.add(new ScoredPath(candidatePath, candidatePlan, scoredCost));
                if (candidateGoal.equals(exactTarget)) {
                    break;
                }
            } else if (result.failureReason() != null) {
                lastFailure = result.failureReason();
                lastFailureDetail = result.failureDetail();
            }
        }
        return new GoalSearchOutcome(scoredPaths, lastFailure, lastFailureDetail);
    }

    boolean shouldTryModifiedSearch(
        Level world,
        BlockPos start,
        BlockPos goal,
        PathSearchResult cleanResult
    ) {
        boolean hasPath = cleanResult != null && cleanResult.path() != null && !cleanResult.path().isEmpty();
        boolean reachedGoal = hasPath && endsAtGoal(cleanResult.path(), goal);
        boolean usefulPartial = false;
        if (hasPath && !reachedGoal) {
            BlockPos partialEnd = cleanResult.path().get(cleanResult.path().size() - 1);
            usefulPartial = NavigatorSearchPolicy.isUsefulPartialPath(
                cleanResult.path().size(),
                horizontalDistanceSq(start, goal),
                horizontalDistanceSq(partialEnd, goal),
                host.isWaypointActionable(world, partialEnd)
            );
        }
        return NavigatorSearchPolicy.shouldUseModifiedFallback(hasPath, reachedGoal, usefulPartial);
    }

    boolean shouldUseHierarchicalPlanning(BlockPos start, BlockPos target) {
        if (start == null || target == null) {
            return false;
        }
        return horizontalDistanceSq(start, target) >= COARSE_PLANNING_DISTANCE_SQ;
    }

    BlockPos selectLocalPlanningTarget(Level world, BlockPos start, List<BlockPos> coarsePath, BlockPos fallbackTarget) {
        if (fallbackTarget == null) {
            return null;
        }
        if (coarsePath == null || coarsePath.isEmpty()) {
            return fallbackTarget;
        }
        if (coarsePath.size() <= COARSE_LOOKAHEAD_STEPS + 1) {
            return coarsePath.get(coarsePath.size() - 1);
        }
        int upperBound = Math.min(coarsePath.size() - 1, COARSE_LOOKAHEAD_STEPS);
        BlockPos selected = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 1; i <= upperBound; i++) {
            BlockPos candidate = coarsePath.get(i);
            if (candidate == null || !host.isWaypointActionable(world, candidate)) {
                continue;
            }
            double score = scoreLocalPlanningCandidate(world, start, coarsePath, i, fallbackTarget);
            if (selected == null || score > bestScore) {
                selected = candidate.immutable();
                bestScore = score;
            }
        }
        return selected != null ? selected : fallbackTarget;
    }

    double scoreLocalPlanningCandidate(
        Level world,
        BlockPos start,
        List<BlockPos> coarsePath,
        int candidateIndex,
        BlockPos fallbackTarget
    ) {
        if (world == null || start == null || coarsePath == null || coarsePath.isEmpty() || candidateIndex <= 0 || candidateIndex >= coarsePath.size()) {
            return Double.NEGATIVE_INFINITY;
        }
        BlockPos candidate = coarsePath.get(candidateIndex);
        if (candidate == null) {
            return Double.NEGATIVE_INFINITY;
        }

        double startDistance = Math.sqrt(horizontalDistanceSq(start, fallbackTarget));
        double candidateDistance = Math.sqrt(horizontalDistanceSq(candidate, fallbackTarget));
        double progressScore = Math.max(0.0D, startDistance - candidateDistance) * LOCAL_TARGET_PROGRESS_WEIGHT;
        double stepScore = candidateIndex * LOCAL_TARGET_STEP_WEIGHT;

        List<BlockPos> prefix = List.copyOf(coarsePath.subList(0, candidateIndex + 1));
        List<PlannedPrimitive> primitives = host.buildPlannedPrimitives(world, prefix, start);
        if (primitives.isEmpty()) {
            return progressScore + stepScore;
        }

        int tailStart = Math.max(0, primitives.size() - LOCAL_TARGET_TAIL_WINDOW);
        double tailPenalty = 0.0D;
        for (int i = tailStart; i < primitives.size(); i++) {
            PlannedPrimitive primitive = primitives.get(i);
            if (primitive == null) {
                continue;
            }
            if (primitive.requiresWorldModification()) {
                tailPenalty += LOCAL_TARGET_MODIFICATION_WEIGHT;
            }
            if (primitive.isCommittedTraversal()) {
                tailPenalty += LOCAL_TARGET_COMMITTED_WEIGHT;
            }
            if (primitive.isPillar()) {
                tailPenalty += LOCAL_TARGET_MODIFICATION_WEIGHT * 1.5D;
            }
        }

        double totalModificationPenalty = pathModificationPenalty(primitives) * 0.01D;
        double totalStructurePenalty = pathStructurePenalty(prefix, primitives) * 0.20D;
        return progressScore + stepScore - tailPenalty - totalModificationPenalty - totalStructurePenalty;
    }

    List<BlockPos> findCoarsePath(ClientLevel world, BlockPos start, BlockPos goal) {
        if (world == null || start == null || goal == null) {
            return List.of();
        }
        long deadlineMs = System.currentTimeMillis() + COARSE_PATHFIND_TIME_BUDGET_MS;
        PriorityQueue<CoarseSearchNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(CoarseSearchNode::fScore));
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Double> gScore = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        BlockPos bestPartial = start;
        double bestPartialHeuristic = heuristic(start, List.of(goal));

        gScore.put(start, 0.0D);
        openSet.add(new CoarseSearchNode(start, heuristic(start, List.of(goal)), 0.0D));

        int expansions = 0;
        while (!openSet.isEmpty() && expansions < COARSE_MAX_EXPANSIONS) {
            if (System.currentTimeMillis() >= deadlineMs) {
                break;
            }
            CoarseSearchNode current = openSet.poll();
            BlockPos currentPos = current.pos();
            if (!closed.add(currentPos)) {
                continue;
            }
            if (currentPos.equals(goal)) {
                return reconstructCoarsePath(cameFrom, currentPos);
            }
            double currentHeuristic = heuristic(currentPos, List.of(goal));
            if (currentHeuristic < bestPartialHeuristic) {
                bestPartial = currentPos;
                bestPartialHeuristic = currentHeuristic;
            }
            expansions++;

            for (CoarseNeighbor neighbor : getCoarseNeighbors(world, currentPos, start, goal)) {
                if (closed.contains(neighbor.pos())) {
                    continue;
                }
                BlockPos previous = cameFrom.get(currentPos);
                double tentativeG = current.gScore()
                    + neighbor.cost()
                    + elevationPenalty(currentPos, neighbor.pos())
                    + turnPenalty(previous, currentPos, neighbor.pos())
                    + terrainPenalty(world, currentPos, neighbor.pos());
                double knownG = gScore.getOrDefault(neighbor.pos(), Double.POSITIVE_INFINITY);
                if (tentativeG >= knownG) {
                    continue;
                }
                cameFrom.put(neighbor.pos(), currentPos);
                gScore.put(neighbor.pos(), tentativeG);
                openSet.add(new CoarseSearchNode(neighbor.pos(), tentativeG + heuristic(neighbor.pos(), List.of(goal)), tentativeG));
            }
        }

        if (bestPartial != null && !bestPartial.equals(start)) {
            return reconstructCoarsePath(cameFrom, bestPartial);
        }
        return List.of();
    }

    List<BlockPos> reconstructCoarsePath(Map<BlockPos, BlockPos> cameFrom, BlockPos end) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos cursor = end;
        while (cursor != null) {
            path.add(cursor.immutable());
            cursor = cameFrom.get(cursor);
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }

    boolean endsAtGoal(List<BlockPos> path, BlockPos goal) {
        if (path == null || path.isEmpty() || goal == null) {
            return false;
        }
        BlockPos last = path.get(path.size() - 1);
        return goal.equals(last);
    }

    PathSearchResult findPathToGoal(
        ClientLevel world,
        BlockPos start,
        BlockPos goal,
        long deadlineMs,
        boolean allowWorldModification
    ) {
        NavigatorPlanningCache cache = planningCacheFor(world);
        boolean previousModificationMode = cache == null || cache.allowWorldModification;
        if (cache != null) {
            cache.allowWorldModification = allowWorldModification;
            if (allowWorldModification) {
                cache.modifiedSearches++;
            } else {
                cache.cleanSearches++;
            }
        }
        try {
            return findPathToGoalInMode(world, start, goal, deadlineMs);
        } finally {
            if (cache != null) {
                cache.allowWorldModification = previousModificationMode;
            }
        }
    }

    PathSearchResult findPathToGoalInMode(ClientLevel world, BlockPos start, BlockPos goal, long deadlineMs) {
        Set<BlockPos> goalSet = Set.of(goal);
        PriorityQueue<SearchNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(node -> node.fScore()));
        BlockPos startNode = start.immutable();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, PlannedPrimitive> cameByPrimitive = new HashMap<>();
        Map<BlockPos, Double> gScore = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        BlockPos bestPartial = startNode;
        double bestPartialScore = heuristic(startNode, List.of(goal));
        double bestPartialDistanceSq = horizontalDistanceSq(startNode, goal);

        gScore.put(startNode, 0.0D);
        openSet.add(new SearchNode(startNode, heuristic(startNode, List.of(goal)), 0.0D));

        int expansions = 0;
        boolean timedOut = false;
        while (!openSet.isEmpty() && expansions < MAX_EXPANSIONS) {
            if (System.currentTimeMillis() >= deadlineMs) {
                timedOut = true;
                break;
            }
            SearchNode current = openSet.poll();
            if (closed.contains(current.pos())) {
                continue;
            }
            if (isGoal(current.pos(), goal, goalSet)) {
                ReconstructedPath reconstructed = reconstructPath(world, cameFrom, cameByPrimitive, current.pos(), start);
                return new PathSearchResult(reconstructed.path(), reconstructed.plannedPrimitives(), current.gScore(), false, null, null);
            }
            double currentHeuristic = heuristic(current.pos(), List.of(goal));
            double currentDistanceSq = horizontalDistanceSq(current.pos(), goal) + Math.abs(current.pos().getY() - goal.getY());
            if (currentHeuristic < bestPartialScore
                || (Math.abs(currentHeuristic - bestPartialScore) < 0.001D && currentDistanceSq < bestPartialDistanceSq)) {
                bestPartial = current.pos();
                bestPartialScore = currentHeuristic;
                bestPartialDistanceSq = currentDistanceSq;
            }
            closed.add(current.pos());
            expansions++;

            for (Neighbor neighbor : getNeighbors(world, current.pos(), start, goal, cameByPrimitive.get(current.pos()))) {
                if (closed.contains(neighbor.pos())) {
                    continue;
                }
                BlockPos previous = cameFrom.get(current.pos());
                double tentativeG = current.gScore()
                    + neighbor.cost()
                    + elevationPenalty(current.pos(), neighbor.pos())
                    + turnPenalty(previous, current.pos(), neighbor.pos())
                    + terrainPenalty(world, current.pos(), neighbor.pos());
                double knownG = gScore.getOrDefault(neighbor.pos(), Double.POSITIVE_INFINITY);
                if (tentativeG >= knownG) {
                    continue;
                }
                cameFrom.put(neighbor.pos(), current.pos());
                cameByPrimitive.put(neighbor.pos(), neighbor.primitive());
                gScore.put(neighbor.pos(), tentativeG);
                openSet.add(new SearchNode(neighbor.pos(), tentativeG + heuristic(neighbor.pos(), List.of(goal)), tentativeG));
            }
        }

        if (bestPartial != null && !bestPartial.equals(start)) {
            ReconstructedPath reconstructed = reconstructPath(world, cameFrom, cameByPrimitive, bestPartial, start);
            List<BlockPos> partialPath = reconstructed.path();
            if (partialPath.size() >= MIN_PARTIAL_PATH_LENGTH || horizontalDistanceSq(bestPartial, goal) <= 36.0D) {
                return new PathSearchResult(
                    partialPath,
                    reconstructed.plannedPrimitives(),
                    gScore.getOrDefault(bestPartial, Double.POSITIVE_INFINITY),
                    timedOut,
                    null,
                    null
                );
            }
        }

        return new PathSearchResult(
            List.of(),
            List.of(),
            Double.POSITIVE_INFINITY,
            timedOut,
            timedOut || expansions >= MAX_EXPANSIONS ? FailureReason.SEARCH_LIMIT : FailureReason.NO_ROUTE,
            timedOut
                ? "Search reached the bounded planning deadline while routing toward " + host.formatDebugPos(goal) + "."
                : expansions >= MAX_EXPANSIONS
                ? "Search exhausted " + MAX_EXPANSIONS + " expansions while routing toward " + host.formatDebugPos(goal) + "."
                : "No traversable primitive sequence was found toward " + host.formatDebugPos(goal) + "."
        );
    }

    ReconstructedPath reconstructPath(
        Level world,
        Map<BlockPos, BlockPos> cameFrom,
        Map<BlockPos, PlannedPrimitive> cameByPrimitive,
        BlockPos end,
        BlockPos start
    ) {
        List<BlockPos> path = new ArrayList<>();
        List<PlannedPrimitive> primitives = new ArrayList<>();
        BlockPos cursor = end;
        while (cursor != null) {
            path.add(cursor);
            PlannedPrimitive primitive = cameByPrimitive.get(cursor);
            if (primitive != null) {
                primitives.add(primitive);
            }
            cursor = cameFrom.get(cursor);
        }
        Collections.reverse(path);
        Collections.reverse(primitives);
        List<BlockPos> cleanedPath = postProcessPath(world, path);
        List<PlannedPrimitive> cleanedPrimitives = host.buildPlannedPrimitives(world, cleanedPath, start);
        return new ReconstructedPath(cleanedPath, cleanedPrimitives);
    }

    List<BlockPos> postProcessPath(Level world, List<BlockPos> rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return List.of();
        }
        List<BlockPos> cleaned = new ArrayList<>(rawPath.size());
        for (BlockPos step : rawPath) {
            if (step == null) {
                continue;
            }
            BlockPos immutableStep = step.immutable();
            if (!cleaned.isEmpty() && immutableStep.equals(cleaned.get(cleaned.size() - 1))) {
                continue;
            }
            if (cleaned.size() >= 2 && immutableStep.equals(cleaned.get(cleaned.size() - 2))) {
                cleaned.remove(cleaned.size() - 1);
                continue;
            }
            while (cleaned.size() >= 2) {
                BlockPos previous = cleaned.get(cleaned.size() - 2);
                BlockPos middle = cleaned.get(cleaned.size() - 1);
                if (!canSkipMiddleWaypoint(world, previous, middle, immutableStep)) {
                    break;
                }
                cleaned.remove(cleaned.size() - 1);
            }
            cleaned.add(immutableStep);
        }
        return List.copyOf(cleaned);
    }

    boolean canSkipMiddleWaypoint(Level world, BlockPos previous, BlockPos middle, BlockPos next) {
        if (world == null || previous == null || middle == null || next == null) {
            return false;
        }
        if (previous.equals(next)) {
            return true;
        }
        int dx = Math.abs(next.getX() - previous.getX());
        int dz = Math.abs(next.getZ() - previous.getZ());
        int dy = Math.abs(next.getY() - previous.getY());
        if (dx > 1 || dz > 1 || dy > 1 || (dx == 0 && dz == 0)) {
            return false;
        }
        Neighbor directNeighbor = resolveNeighborAccess(world, previous, next);
        if (directNeighbor == null) {
            return false;
        }
        if (!isPlannerTraversableMove(world, previous, next)) {
            return false;
        }
        if ((host.requiresBreakingForWaypoint(world, middle) || needsPlacedSupport(world, middle))
            && !host.requiresBreakingForWaypoint(world, next)
            && !needsPlacedSupport(world, next)
            && horizontalDistanceSq(previous, next) <= 1.0D
            && Math.abs(next.getY() - previous.getY()) <= 1) {
            return true;
        }
        return true;
    }

    double pathStructurePenalty(List<BlockPos> path, List<PlannedPrimitive> plannedPrimitives) {
        if (path == null || path.size() < 3) {
            return 0.0D;
        }
        double penalty = 0.0D;
        int lastDx = 0;
        int lastDz = 0;
        int lastDy = 0;
        Set<BlockPos> seen = new HashSet<>();
        BlockPos first = path.get(0);
        if (first != null) {
            seen.add(first);
        }
        for (int i = 1; i < path.size(); i++) {
            BlockPos previous = path.get(i - 1);
            BlockPos current = path.get(i);
            if (previous == null || current == null) {
                continue;
            }
            if (!seen.add(current)) {
                penalty += 8.0D;
            }
            if (i >= 2 && current.equals(path.get(i - 2))) {
                penalty += 6.0D;
            }
            int dx = Integer.compare(current.getX() - previous.getX(), 0);
            int dz = Integer.compare(current.getZ() - previous.getZ(), 0);
            int dy = Integer.compare(current.getY() - previous.getY(), 0);
            if (i > 1) {
                if (dx == -lastDx && dz == -lastDz && dy == -lastDy) {
                    penalty += 4.5D;
                } else {
                    if (dx != lastDx || dz != lastDz) {
                        penalty += 0.35D;
                    }
                    if (dy != 0 && lastDy != 0 && dy != lastDy) {
                        penalty += 1.0D;
                    }
                    if (dy != 0 && (dx != lastDx || dz != lastDz)) {
                        penalty += 0.7D;
                    }
                }
            }
            if (plannedPrimitives != null && i < plannedPrimitives.size()) {
                PlannedPrimitive primitive = plannedPrimitives.get(i);
                if (primitive != null) {
                    penalty += pathSearchTypePenalty(primitive);
                }
            }
            lastDx = dx;
            lastDz = dz;
            lastDy = dy;
        }
        return penalty;
    }

    double pathModificationPenalty(List<PlannedPrimitive> plannedPrimitives) {
        if (plannedPrimitives == null || plannedPrimitives.isEmpty()) {
            return 0.0D;
        }
        double penalty = 0.0D;
        for (PlannedPrimitive primitive : plannedPrimitives) {
            if (primitive == null) {
                continue;
            }
            penalty += pathModificationPenaltyForPrimitive(primitive);
        }
        return penalty;
    }

    double pathSearchTypePenalty(PlannedPrimitive primitive) {
        if (primitive == null || primitive.searchType() == null) {
            return 0.0D;
        }
        return switch (primitive.searchType()) {
            case BREAK_FORWARD, MINE_ASCEND -> 0.45D;
            case PLACE_FORWARD -> 0.55D;
            case PILLAR -> 0.80D;
            case JUMP_ASCEND, DESCEND, CLIMB, SWIM, INTERACT -> 0.20D;
            case WALK -> 0.0D;
        };
    }

    double pathModificationPenaltyForPrimitive(PlannedPrimitive primitive) {
        if (primitive == null || primitive.searchType() == null) {
            return 0.0D;
        }
        return switch (primitive.searchType()) {
            case BREAK_FORWARD, MINE_ASCEND -> PATH_BREAK_ROUTE_PENALTY;
            case PLACE_FORWARD -> PATH_PLACE_ROUTE_PENALTY;
            case PILLAR -> PATH_PLACE_ROUTE_PENALTY + 120.0D;
            case WALK, INTERACT, JUMP_ASCEND, DESCEND, CLIMB, SWIM -> 0.0D;
        };
    }

    boolean pathRequiresModification(List<PlannedPrimitive> plannedPrimitives) {
        if (plannedPrimitives == null || plannedPrimitives.isEmpty()) {
            return false;
        }
        for (PlannedPrimitive primitive : plannedPrimitives) {
            if (primitive == null) {
                continue;
            }
            if (primitive.requiresWorldModification()) {
                return true;
            }
        }
        return false;
    }

    static int requiredPlacementBlocks(List<PlannedPrimitive> plannedPrimitives) {
        if (plannedPrimitives == null || plannedPrimitives.isEmpty()) {
            return 0;
        }
        int required = 0;
        for (PlannedPrimitive primitive : plannedPrimitives) {
            if (primitive != null && primitive.requiresPlace()) {
                required++;
            }
        }
        return required;
    }

    boolean isViablePlannedPath(Level world, List<BlockPos> path, List<PlannedPrimitive> plannedPrimitives) {
        if (world == null || path == null || path.isEmpty()) {
            return false;
        }
        for (int i = 0; i < path.size(); i++) {
            BlockPos step = path.get(i);
            PlannedPrimitive primitive = plannedPrimitives != null && i < plannedPrimitives.size() ? plannedPrimitives.get(i) : null;
            if (step == null || (!host.isWaypointActionable(world, step)
                && (primitive == null || !primitive.isPillar()))) {
                return false;
            }
            if (primitive != null && primitive.target() != null && !primitive.target().equals(step)) {
                return false;
            }
            if (primitive != null && primitive.requiresBreak() && primitive.requiresPlace()) {
                return false;
            }
            if (i == 0) {
                continue;
            }
            BlockPos previous = path.get(i - 1);
            if (!isViablePlannedStep(world, previous, step, primitive)) {
                return false;
            }
        }
        return true;
    }

    boolean isViablePlannedStep(Level world, BlockPos from, BlockPos to, PlannedPrimitive primitive) {
        if (world == null || from == null || to == null) {
            return false;
        }
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        int dy = to.getY() - from.getY();
        if (dx > 1 || dz > 1 || Math.abs(dy) > MAX_STEP_UP + MAX_SAFE_FALL_DISTANCE) {
            return false;
        }
        if (dx == 0
            && dz == 0
            && dy > 0
            && (primitive == null
                || (!primitive.isClimb() && !primitive.isPillar()))) {
            return false;
        }
        if (dx == 0
            && dz == 0
            && dy < 0
            && (primitive == null
                || (!primitive.isClimb() && !primitive.isSwim()))) {
            return false;
        }
        if (dx == 1 && dz == 1 && dy != 0) {
            return false;
        }
        if (primitive != null && primitive.isPillar()) {
            return dy == 1
                && primitive.requiresPlace()
                && canOccupy(world, to)
                && canOccupy(world, to.above())
                && !isHardDanger(world, to)
                && !isWaterNode(world, to);
        }
        if (!isPlannerTraversableMove(world, from, to)) {
            return false;
        }
        if (dy > 0 && !requiresInteractableTraversal(world, from, to) && !isClimbTransition(world, from, to)) {
            if (!canTraverseAscendingStep(world, from, to)) {
                return false;
            }
        }
        if (dy < 0 && !canSafelyDropTo(world, from, to)) {
            return false;
        }
        return true;
    }

    double turnPenalty(BlockPos previous, BlockPos current, BlockPos next) {
        return NavigatorPathCostPolicy.turnPenalty(
            previous,
            current,
            next,
            TURN_PENALTY_DIAGONAL,
            TURN_PENALTY_CORNER,
            TURN_PENALTY_REVERSE
        );
    }

    List<Neighbor> getNeighbors(Level world, BlockPos current, BlockPos start, BlockPos goal) {
        return getNeighbors(world, current, start, goal, null);
    }

    List<Neighbor> getNeighbors(
        Level world,
        BlockPos current,
        BlockPos start,
        BlockPos goal,
        PlannedPrimitive arrivalPrimitive
    ) {
        List<Neighbor> neighbors = new ArrayList<>(MOVES.length + 8);
        NavigatorPlanningCache cache = planningCacheFor(world);
        if (cache != null) {
            cache.expandedNodes++;
        }
        long now = System.currentTimeMillis();
        boolean trappedExcavation = isTrappedExcavationState(world, current, goal, now);
        Direction escapeDirection = trappedExcavation ? getPreferredEscapeDirection(world, current, goal, now) : null;
        for (Move move : MOVES) {
            if (trappedExcavation) {
                if (Math.abs(move.dx()) + Math.abs(move.dz()) == 2) {
                    continue;
                }
                if (!matchesEscapeDirection(move, escapeDirection)) {
                    continue;
                }
            }
            addDirectedPrimitiveNeighbors(world, current, move.dx(), move.dz(), start, goal, neighbors, now);
        }
        if (trappedExcavation) {
            if (worldModificationAllowed(world)) {
                addDigEscapeNeighbors(world, current, start, goal, neighbors, now);
            }
        }
        addClimbNeighbors(world, current, start, goal, neighbors, now);
        addSafeDropNeighbors(world, current, start, goal, neighbors, now);
        if (worldModificationAllowed(world)) {
            addPillarNeighbors(world, current, start, goal, neighbors, now, arrivalPrimitive != null && arrivalPrimitive.isPillar());
        }
        return neighbors;
    }

    void addDirectedPrimitiveNeighbors(
        Level world,
        BlockPos current,
        int dx,
        int dz,
        BlockPos start,
        BlockPos goal,
        List<Neighbor> neighbors,
        long now
    ) {
        if (world == null || current == null || (dx == 0 && dz == 0)) {
            return;
        }
        if (Math.abs(dx) + Math.abs(dz) == 2
            && (!hasDirectedPrimitiveAccess(world, current, dx, 0, start, goal, now)
            || !hasDirectedPrimitiveAccess(world, current, 0, dz, start, goal, now))) {
            return;
        }

        BlockPos flatCandidate = new BlockPos(current.getX() + dx, current.getY(), current.getZ() + dz);
        addBestPrimitiveNeighborIfPresent(world, current, flatCandidate, start, goal, neighbors, now);

        BlockPos ascendCandidate = flatCandidate.above();
        addBestPrimitiveNeighborIfPresent(world, current, ascendCandidate, start, goal, neighbors, now);

        for (int drop = 1; drop <= MAX_DROP_DOWN; drop++) {
            BlockPos descendCandidate = new BlockPos(current.getX() + dx, current.getY() - drop, current.getZ() + dz);
            addBestPrimitiveNeighborIfPresent(world, current, descendCandidate, start, goal, neighbors, now);
        }
    }

    void addBestPrimitiveNeighborIfPresent(
        Level world,
        BlockPos from,
        BlockPos candidate,
        BlockPos start,
        BlockPos goal,
        List<Neighbor> neighbors,
        long now
    ) {
        Neighbor neighbor = buildBestPrimitiveNeighbor(world, from, candidate, start, goal, now);
        if (neighbor != null) {
            neighbors.add(neighbor);
        }
    }

    Neighbor buildBestPrimitiveNeighbor(
        Level world,
        BlockPos from,
        BlockPos candidate,
        BlockPos start,
        BlockPos goal,
        long now
    ) {
        if (world == null || from == null || candidate == null) {
            return null;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        if (cache != null) {
            cache.movementEvaluations++;
        }
        int dy = candidate.getY() - from.getY();
        if (dy < 0) {
            return buildPrimitiveNeighbor(world, from, candidate, SearchPrimitiveType.DESCEND, start, goal, now);
        }
        if (dy > 1) {
            return null;
        }

        boolean interactable = requiresInteractableTraversal(world, from, candidate) || hasPathOpenableAhead(world, from, candidate);
        List<BlockPos> breakTargets = getRequiredBreakTargets(world, from, candidate);
        if (breakTargets == null) {
            return null;
        }
        boolean hasBreaks = !breakTargets.isEmpty();
        boolean requiresSupport = host.allowBlockPlacing() && needsPlacedSupport(world, candidate);
        if (!worldModificationAllowed(world) && (hasBreaks || requiresSupport)) {
            return null;
        }
        SearchPrimitiveType family;
        if (dy > 0) {
            family = hasBreaks ? SearchPrimitiveType.MINE_ASCEND : SearchPrimitiveType.JUMP_ASCEND;
        } else if (interactable) {
            family = SearchPrimitiveType.INTERACT;
        } else if (hasBreaks) {
            family = SearchPrimitiveType.BREAK_FORWARD;
        } else if (requiresSupport) {
            family = SearchPrimitiveType.PLACE_FORWARD;
        } else if (shouldStepJump(world, from, candidate)) {
            family = SearchPrimitiveType.JUMP_ASCEND;
        } else {
            family = SearchPrimitiveType.WALK;
        }
        return buildPrimitiveNeighbor(world, from, candidate, family, start, goal, now);
    }

    boolean hasDirectedPrimitiveAccess(
        Level world,
        BlockPos current,
        int dx,
        int dz,
        BlockPos start,
        BlockPos goal,
        long now
    ) {
        if (world == null || current == null || (dx == 0 && dz == 0)) {
            return false;
        }
        BlockPos flatCandidate = new BlockPos(current.getX() + dx, current.getY(), current.getZ() + dz);
        if (buildBestPrimitiveNeighbor(world, current, flatCandidate, start, goal, now) != null) {
            return true;
        }
        BlockPos ascendCandidate = flatCandidate.above();
        return buildBestPrimitiveNeighbor(world, current, ascendCandidate, start, goal, now) != null;
    }

    Neighbor buildPrimitiveNeighbor(
        Level world,
        BlockPos from,
        BlockPos candidate,
        SearchPrimitiveType family,
        BlockPos start,
        BlockPos goal,
        long now
    ) {
        if (world == null || from == null || candidate == null || family == null) {
            return null;
        }
        if (!isWithinSearchBounds(start, candidate, goal)
            || !isChunkLoaded(world, candidate)
            || isHardDanger(world, candidate)
            || isFailedNode(candidate, now)
            || isFailedEdge(from, candidate, now)) {
            return null;
        }

        int dx = Math.abs(candidate.getX() - from.getX());
        int dz = Math.abs(candidate.getZ() - from.getZ());
        int dy = candidate.getY() - from.getY();
        if (dx > 1 || dz > 1 || (dx == 0 && dz == 0) || (dx == 1 && dz == 1 && dy != 0)) {
            return null;
        }

        if (family == SearchPrimitiveType.INTERACT || family == SearchPrimitiveType.WALK
            || family == SearchPrimitiveType.BREAK_FORWARD || family == SearchPrimitiveType.PLACE_FORWARD) {
            if (dy != 0) {
                return null;
            }
        } else if (family == SearchPrimitiveType.JUMP_ASCEND || family == SearchPrimitiveType.MINE_ASCEND) {
            boolean fractionalAscent = family == SearchPrimitiveType.JUMP_ASCEND
                && dy == 0
                && shouldStepJump(world, from, candidate);
            if ((dy != 1 && !fractionalAscent) || isFailedJump(from, candidate, now)) {
                return null;
            }
        } else if (family == SearchPrimitiveType.DESCEND) {
            if (dy >= 0 || !canSafelyDropTo(world, from, candidate)) {
                return null;
            }
        }

        boolean interactable = requiresInteractableTraversal(world, from, candidate) || hasPathOpenableAhead(world, from, candidate);
        List<BlockPos> breakTargets = getRequiredBreakTargets(world, from, candidate);
        if (breakTargets == null) {
            return null;
        }
        boolean hasBreaks = !breakTargets.isEmpty();
        boolean requiresSupport = host.allowBlockPlacing() && needsPlacedSupport(world, candidate);
        if (!worldModificationAllowed(world) && (hasBreaks || requiresSupport)) {
            return null;
        }

        switch (family) {
            case INTERACT -> {
                if (!interactable || hasBreaks || requiresSupport || !isNavigableNode(world, candidate)) {
                    return null;
                }
            }
            case WALK -> {
                if (interactable || hasBreaks || requiresSupport || !isNavigableNode(world, candidate)) {
                    return null;
                }
            }
            case BREAK_FORWARD -> {
                if (interactable || !hasBreaks || requiresSupport || !host.allowBlockBreaking() || isFailedBreak(from, candidate, now)) {
                    return null;
                }
            }
            case PLACE_FORWARD -> {
                if (interactable || hasBreaks || !requiresSupport || isFailedPlace(from, candidate, now)) {
                    return null;
                }
            }
            case JUMP_ASCEND -> {
                if (interactable || hasBreaks || requiresSupport || !canAttemptJump(world, from, candidate)) {
                    return null;
                }
            }
            case MINE_ASCEND -> {
                if (interactable || !hasBreaks || requiresSupport || !host.allowBlockBreaking() || isFailedBreak(from, candidate, now) || !canTraverseAscendingStep(world, from, candidate)) {
                    return null;
                }
            }
            case DESCEND -> {
                if (hasBreaks || requiresSupport) {
                    return null;
                }
            }
        }

        if (hasBreaks) {
            if (shouldAvoidGoalModification(world, candidate) || hasInteractableAlternative(world, from, candidate, host.targetPos())) {
                return null;
            }
        }
        if (requiresSupport) {
            BlockPos activeTarget = host.targetPos();
            if ((activeTarget != null && candidate.below().equals(activeTarget))
                || shouldAvoidGoalModification(world, candidate)
                || !canUsePlacedSupportMove(world, from, candidate)
                || hasNaturalGroundAlternative(world, from, candidate, activeTarget)
                || !canPlaceSupportAt(world, candidate.below())) {
                return null;
            }
        } else if (!hasBreaks && !isWaterNode(world, candidate) && resolveSupportSurfaceY(world, candidate).isEmpty()) {
            return null;
        }

        List<BlockPos> normalizedBreakTargets = breakTargets.stream()
            .filter(pos -> pos != null && isBreakableForNavigator(world, pos))
            .map(BlockPos::immutable)
            .toList();
        BlockPos placeTarget = requiresSupport ? candidate.below().immutable() : null;
        PlannedPrimitive primitive = host.createPlannedPrimitive(world, from, candidate, normalizedBreakTargets, placeTarget);
        if (!matchesPrimitiveFamily(primitive, family)) {
            return null;
        }
        return new Neighbor(
            searchPosition(candidate),
            primitiveStepBaseCost(from, candidate) + primitiveSearchPenalty(world, from, candidate, primitive),
            primitive
        );
    }

    boolean matchesPrimitiveFamily(PlannedPrimitive primitive, SearchPrimitiveType family) {
        if (primitive == null || family == null) {
            return false;
        }
        return primitive.searchType() == family;
    }

    List<CoarseNeighbor> getCoarseNeighbors(Level world, BlockPos current, BlockPos start, BlockPos goal) {
        List<CoarseNeighbor> neighbors = new ArrayList<>(MOVES.length + 8);
        long now = System.currentTimeMillis();
        for (Move move : MOVES) {
            addDirectedCoarsePrimitiveNeighbors(world, current, move.dx(), move.dz(), start, goal, neighbors, now);
        }
        addCoarseClimbNeighbors(world, current, start, goal, neighbors, now);
        addCoarseDropNeighbors(world, current, start, goal, neighbors, now);
        return neighbors;
    }

    void addDirectedCoarsePrimitiveNeighbors(
        Level world,
        BlockPos current,
        int dx,
        int dz,
        BlockPos start,
        BlockPos goal,
        List<CoarseNeighbor> neighbors,
        long now
    ) {
        if (world == null || current == null || (dx == 0 && dz == 0)) {
            return;
        }
        if (Math.abs(dx) + Math.abs(dz) == 2
            && (!hasDirectedCoarsePrimitiveAccess(world, current, dx, 0, start, goal, now)
            || !hasDirectedCoarsePrimitiveAccess(world, current, 0, dz, start, goal, now))) {
            return;
        }

        BlockPos flatCandidate = new BlockPos(current.getX() + dx, current.getY(), current.getZ() + dz);
        addCoarsePrimitiveNeighborIfPresent(world, current, flatCandidate, SearchPrimitiveType.INTERACT, start, goal, neighbors, now);
        addCoarsePrimitiveNeighborIfPresent(world, current, flatCandidate, SearchPrimitiveType.WALK, start, goal, neighbors, now);
        addCoarsePrimitiveNeighborIfPresent(world, current, flatCandidate, SearchPrimitiveType.JUMP_ASCEND, start, goal, neighbors, now);

        BlockPos ascendCandidate = flatCandidate.above();
        addCoarsePrimitiveNeighborIfPresent(world, current, ascendCandidate, SearchPrimitiveType.JUMP_ASCEND, start, goal, neighbors, now);

        for (int drop = 1; drop <= MAX_DROP_DOWN; drop++) {
            BlockPos descendCandidate = new BlockPos(current.getX() + dx, current.getY() - drop, current.getZ() + dz);
            addCoarsePrimitiveNeighborIfPresent(world, current, descendCandidate, SearchPrimitiveType.DESCEND, start, goal, neighbors, now);
        }
    }

    boolean hasDirectedCoarsePrimitiveAccess(
        Level world,
        BlockPos current,
        int dx,
        int dz,
        BlockPos start,
        BlockPos goal,
        long now
    ) {
        if (world == null || current == null || (dx == 0 && dz == 0)) {
            return false;
        }
        BlockPos flatCandidate = new BlockPos(current.getX() + dx, current.getY(), current.getZ() + dz);
        if (buildCoarsePrimitiveNeighbor(world, current, flatCandidate, SearchPrimitiveType.INTERACT, start, goal, now) != null
            || buildCoarsePrimitiveNeighbor(world, current, flatCandidate, SearchPrimitiveType.WALK, start, goal, now) != null) {
            return true;
        }
        BlockPos ascendCandidate = flatCandidate.above();
        return buildCoarsePrimitiveNeighbor(world, current, ascendCandidate, SearchPrimitiveType.JUMP_ASCEND, start, goal, now) != null;
    }

    void addCoarsePrimitiveNeighborIfPresent(
        Level world,
        BlockPos from,
        BlockPos candidate,
        SearchPrimitiveType family,
        BlockPos start,
        BlockPos goal,
        List<CoarseNeighbor> neighbors,
        long now
    ) {
        CoarseNeighbor neighbor = buildCoarsePrimitiveNeighbor(world, from, candidate, family, start, goal, now);
        if (neighbor != null) {
            neighbors.add(neighbor);
        }
    }

    CoarseNeighbor buildCoarsePrimitiveNeighbor(
        Level world,
        BlockPos from,
        BlockPos candidate,
        SearchPrimitiveType family,
        BlockPos start,
        BlockPos goal,
        long now
    ) {
        if (world == null || from == null || candidate == null || family == null) {
            return null;
        }
        if (!isWithinSearchBounds(start, candidate, goal)
            || !isChunkLoaded(world, candidate)
            || isHardDanger(world, candidate)
            || isFailedNode(candidate, now)
            || isFailedEdge(from, candidate, now)
            || !isCoarseNavigableNode(world, candidate)) {
            return null;
        }

        int dx = Math.abs(candidate.getX() - from.getX());
        int dz = Math.abs(candidate.getZ() - from.getZ());
        int dy = candidate.getY() - from.getY();
        if (dx > 1 || dz > 1 || (dx == 0 && dz == 0) || (dx == 1 && dz == 1)) {
            return null;
        }

        boolean interactable = requiresInteractableTraversal(world, from, candidate) || hasPathOpenableAhead(world, from, candidate);
        switch (family) {
            case INTERACT -> {
                if (dy != 0 || !interactable) {
                    return null;
                }
            }
            case WALK -> {
                if (dy != 0 || interactable || !isCoarsePlannerTraversableMove(world, from, candidate)) {
                    return null;
                }
            }
            case JUMP_ASCEND -> {
                boolean fractionalAscent = dy == 0 && shouldStepJump(world, from, candidate);
                if ((dy != 1 && !fractionalAscent)
                    || isFailedJump(from, candidate, now)
                    || interactable
                    || !canAttemptJump(world, from, candidate)) {
                    return null;
                }
            }
            case DESCEND -> {
                if (dy >= 0 || !canSafelyDropTo(world, from, candidate)) {
                    return null;
                }
            }
            case BREAK_FORWARD, PLACE_FORWARD, MINE_ASCEND -> {
                return null;
            }
        }

        PlannedPrimitive primitive = host.createPlannedPrimitive(world, from, candidate, List.of(), null);
        if (!matchesPrimitiveFamily(primitive, family)) {
            return null;
        }
        return new CoarseNeighbor(
            candidate.immutable(),
            primitiveStepBaseCost(from, candidate) + primitiveSearchPenalty(world, from, candidate, primitive),
            primitive
        );
    }

    void addCoarseClimbNeighbors(Level world, BlockPos current, BlockPos start, BlockPos goal, List<CoarseNeighbor> neighbors, long now) {
        if (world == null || current == null || !isClimbableNode(world, current)) {
            return;
        }
        for (int dy : new int[]{1, -1}) {
            BlockPos candidate = current.offset(0, dy, 0);
            if (!isWithinSearchBounds(start, candidate, goal)
                || !isChunkLoaded(world, candidate)
                || isHardDanger(world, candidate)
                || isFailedNode(candidate, now)
                || isFailedEdge(current, candidate, now)
                || !isCoarseNavigableNode(world, candidate)
                || !isClimbTransition(world, current, candidate)) {
                continue;
            }
            PlannedPrimitive primitive = host.createPlannedPrimitive(world, current, candidate, List.of(), null);
            neighbors.add(new CoarseNeighbor(
                candidate.immutable(),
                primitiveStepBaseCost(current, candidate) + primitiveSearchPenalty(world, current, candidate, primitive),
                primitive
            ));
        }
    }

    void addCoarseDropNeighbors(Level world, BlockPos current, BlockPos start, BlockPos goal, List<CoarseNeighbor> neighbors, long now) {
        if (world == null || current == null) {
            return;
        }
        for (int drop = 1; drop <= MAX_DROP_DOWN; drop++) {
            BlockPos candidate = current.offset(0, -drop, 0);
            if (!isWithinSearchBounds(start, candidate, goal)
                || !isChunkLoaded(world, candidate)
                || isHardDanger(world, candidate)
                || isFailedNode(candidate, now)
                || isFailedEdge(current, candidate, now)
                || isFailedDrop(current, candidate, now)
                || !isCoarseNavigableNode(world, candidate)
                || !canSafelyDropTo(world, current, candidate)) {
                continue;
            }
            PlannedPrimitive primitive = host.createPlannedPrimitive(world, current, candidate, List.of(), null);
            neighbors.add(new CoarseNeighbor(
                candidate.immutable(),
                primitiveStepBaseCost(current, candidate) + primitiveSearchPenalty(world, current, candidate, primitive),
                primitive
            ));
        }
    }

    boolean isCoarseNavigableNode(Level world, BlockPos footPos) {
        return isNavigableNode(world, footPos) && !host.requiresBreakingForWaypoint(world, footPos) && !needsPlacedSupport(world, footPos);
    }

    boolean isCoarsePlannerTraversableMove(Level world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null) {
            return false;
        }
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        int dy = to.getY() - from.getY();
        if (dx > 1 || dz > 1 || (dx == 0 && dz == 0)) {
            return false;
        }
        if (dx == 1 && dz == 1) {
            return false;
        }
        if (dy > 0) {
            if (isClimbTransition(world, from, to)) {
                return true;
            }
            if (requiresInteractableTraversal(world, from, to)) {
                return false;
            }
            return canAttemptJump(world, from, to);
        }
        if (dy < 0 && dx == 0 && dz == 0) {
            return canSafelyDropTo(world, from, to);
        }
        if (requiresInteractableTraversal(world, from, to)) {
            return false;
        }
        return true;
    }

    boolean isTrappedExcavationState(Level world, BlockPos current, BlockPos goal, long now) {
        if (world == null || current == null || goal == null) {
            return false;
        }
        if (goal.getY() < current.getY()) {
            return false;
        }
        return countDirectWalkNeighbors(world, current, current, goal, now) <= 1;
    }

    boolean matchesEscapeDirection(Move move, Direction direction) {
        if (move == null || direction == null) {
            return true;
        }
        return move.dx() == direction.getStepX() && move.dz() == direction.getStepZ();
    }

    Direction getPreferredEscapeDirection(Level world, BlockPos current, BlockPos goal, long now) {
        return host.preferredEscapeDirection(world, current, goal, now);
    }

    void addDigEscapeNeighbors(Level world, BlockPos current, BlockPos start, BlockPos goal, List<Neighbor> neighbors, long now) {
        if (world == null || current == null) {
            return;
        }

        if (!canOccupy(world, current.above())) {
            addDigEscapeNeighbor(world, current, current.above(), start, goal, neighbors, now, DIG_ESCAPE_MOVE_PENALTY);
        }
        Direction escapeDirection = getPreferredEscapeDirection(world, current, goal, now);
        if (escapeDirection != null) {
            addDirectedDigEscapeNeighbors(world, current, start, goal, neighbors, now, escapeDirection.getStepX(), escapeDirection.getStepZ());
        } else {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos breakout = current.relative(direction).above();
                addDigEscapeNeighbor(world, current, breakout, start, goal, neighbors, now, DIG_BREAKOUT_MOVE_PENALTY + 0.35D);
            }
        }
    }

    void addDirectedDigEscapeNeighbors(
        Level world,
        BlockPos current,
        BlockPos start,
        BlockPos goal,
        List<Neighbor> neighbors,
        long now,
        int primaryDx,
        int primaryDz
    ) {
        if (primaryDx != 0 || primaryDz != 0) {
            addDigEscapeNeighbor(world, current, current.offset(primaryDx, 1, primaryDz), start, goal, neighbors, now, DIG_BREAKOUT_MOVE_PENALTY);
            addDigEscapeNeighbor(world, current, current.offset(primaryDx, 0, primaryDz), start, goal, neighbors, now, DIG_BREAKOUT_MOVE_PENALTY + 0.2D);
        }
    }

    void addDigEscapeNeighbor(
        Level world,
        BlockPos current,
        BlockPos candidate,
        BlockPos start,
        BlockPos goal,
        List<Neighbor> neighbors,
        long now,
        double extraPenalty
    ) {
        if (candidate == null
            || !isWithinSearchBounds(start, candidate, goal)
            || !isChunkLoaded(world, candidate)
            || isHardDanger(world, candidate)
            || isFailedNode(candidate, now)
            || isFailedEdge(current, candidate, now)) {
            return;
        }

        Neighbor assisted = resolveNeighborAccess(world, current, candidate);
        if (assisted == null) {
            return;
        }
        if (!host.requiresBreakingForWaypoint(world, candidate) && !needsPlacedSupport(world, candidate)) {
            return;
        }

        neighbors.add(new Neighbor(assisted.pos(), assisted.cost() + extraPenalty, assisted.primitive()));
    }

    int countDirectWalkNeighbors(Level world, BlockPos current, BlockPos start, BlockPos goal, long now) {
        int count = 0;
        for (Move move : MOVES) {
            Neighbor neighbor = findNeighbor(world, current, move.dx(), move.dz(), start, goal, true);
            if (neighbor == null) {
                continue;
            }
            if (isFailedNode(neighbor.pos(), now) || isFailedEdge(current, neighbor.pos(), now)) {
                continue;
            }
            count++;
        }
        return count;
    }

    void addClimbNeighbors(Level world, BlockPos current, BlockPos start, BlockPos goal, List<Neighbor> neighbors, long now) {
        if (world == null || current == null || !isClimbableNode(world, current)) {
            return;
        }
        for (int dy : new int[]{1, -1}) {
            BlockPos candidate = current.offset(0, dy, 0);
            if (!isWithinSearchBounds(start, candidate, goal)
                || !isChunkLoaded(world, candidate)
                || !isClimbTransition(world, current, candidate)
                || isHardDanger(world, candidate)
                || isFailedNode(candidate, now)
                || isFailedEdge(current, candidate, now)) {
                continue;
            }
            PlannedPrimitive primitive = host.createPlannedPrimitive(world, current, candidate, List.of(), null);
            neighbors.add(new Neighbor(
                searchPosition(candidate),
                primitiveStepBaseCost(current, candidate) + primitiveSearchPenalty(world, current, candidate, primitive),
                primitive
            ));
        }
    }

    void addSafeDropNeighbors(Level world, BlockPos current, BlockPos start, BlockPos goal, List<Neighbor> neighbors, long now) {
        if (world == null || current == null) {
            return;
        }
        for (int drop = 1; drop <= MAX_DROP_DOWN; drop++) {
            BlockPos candidate = current.offset(0, -drop, 0);
            if (!isWithinSearchBounds(start, candidate, goal)
                || !isChunkLoaded(world, candidate)
                || isHardDanger(world, candidate)
                || isFailedNode(candidate, now)
                || isFailedEdge(current, candidate, now)
                || isFailedDrop(current, candidate, now)
                || !canSafelyDropTo(world, current, candidate)) {
                continue;
            }
            PlannedPrimitive primitive = host.createPlannedPrimitive(world, current, candidate, List.of(), null);
            neighbors.add(new Neighbor(
                searchPosition(candidate),
                primitiveStepBaseCost(current, candidate) + primitiveSearchPenalty(world, current, candidate, primitive),
                primitive
            ));
        }
    }

    void addPillarNeighbors(Level world, BlockPos current, BlockPos start, BlockPos goal, List<Neighbor> neighbors, long now) {
        addPillarNeighbors(world, current, start, goal, neighbors, now, false);
    }

    void addPillarNeighbors(
        Level world,
        BlockPos current,
        BlockPos start,
        BlockPos goal,
        List<Neighbor> neighbors,
        long now,
        boolean standsOnPlannedPillar
    ) {
        if (world == null
            || current == null
            || neighbors == null
            || host.availablePlacementBlocks() <= 0) {
            return;
        }
        BlockPos candidate = current.above();
        if (!isWithinSearchBounds(start, candidate, goal)
            || !isChunkLoaded(world, candidate)
            || isHardDanger(world, candidate)
            || isFailedNode(candidate, now)
            || isFailedEdge(current, candidate, now)
            || isFailedPillar(current, candidate, now)) {
            return;
        }
        boolean physicallySupported = canPillarTo(world, current, candidate);
        boolean virtuallySupported = standsOnPlannedPillar && canExtendPlannedPillarTo(world, current, candidate);
        if (!physicallySupported && !virtuallySupported) {
            return;
        }
        PlannedPrimitive primitive = host.createPlannedPrimitive(world, current, candidate, List.of(), candidate.below());
        neighbors.add(new Neighbor(
            searchPosition(candidate),
            primitiveStepBaseCost(current, candidate) + primitiveSearchPenalty(world, current, candidate, primitive),
            primitive
        ));
    }

    Neighbor findNeighbor(Level world, BlockPos current, int dx, int dz, BlockPos start, BlockPos goal, boolean allowRelaxedBounds) {
        if (dx == 0 && dz == 0) {
            return null;
        }

        if (Math.abs(dx) + Math.abs(dz) == 2) {
            if (findNeighbor(world, current, dx, 0, start, goal, true) == null
                || findNeighbor(world, current, 0, dz, start, goal, true) == null) {
                return null;
            }
        }

        int baseX = current.getX() + dx;
        int baseZ = current.getZ() + dz;
        for (int dy = MAX_STEP_UP; dy >= -MAX_DROP_DOWN; dy--) {
            BlockPos candidate = new BlockPos(baseX, current.getY() + dy, baseZ);
            if (!allowRelaxedBounds && !isWithinSearchBounds(start, candidate, goal)) {
                continue;
            }
            if (!isChunkLoaded(world, candidate)) {
                continue;
            }
            Neighbor assistedNeighbor = resolveNeighborAccess(world, current, candidate);
            if (assistedNeighbor == null) {
                continue;
            }
            if (Math.abs(candidate.getY() - current.getY()) > MAX_STEP_UP && candidate.getY() > current.getY()) {
                continue;
            }
            if (isHardDanger(world, candidate)) {
                continue;
            }
            if (!isPlannerTraversableMove(world, current, candidate)) {
                continue;
            }
            return assistedNeighbor;
        }
        return null;
    }

    boolean isPlannerTraversableMove(Level world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null) {
            return false;
        }
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        int dy = to.getY() - from.getY();

        if (dx == 1 && dz == 1 && dy != 0) {
            return false;
        }
        if ((dy > 0 || shouldStepJump(world, from, to)) && !requiresInteractableTraversal(world, from, to)) {
            return canTraverseAscendingStep(world, from, to);
        }
        return true;
    }

    boolean canTraverseAscendingStep(Level world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null) {
            return false;
        }
        if (canAttemptJump(world, from, to)) {
            return true;
        }
        return canExcavateJumpCorridor(world, from, to);
    }

    boolean canExcavateJumpCorridor(Level world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null) {
            return false;
        }
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        int dy = to.getY() - from.getY();
        if (dy <= 0 || dx > 1 || dz > 1 || (dx == 1 && dz == 1)) {
            return false;
        }
        BlockPos[] requiredClearance = new BlockPos[] {
            from.above(),
            from.above(2),
            to,
            to.above(),
            to.above(2)
        };
        for (BlockPos pos : requiredClearance) {
            if (!isExcavationClearable(world, pos)) {
                return false;
            }
        }
        return true;
    }

    boolean isExcavationClearable(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return canOccupy(world, pos) || isBreakableForNavigator(world, pos);
    }

    boolean isWithinSearchBounds(BlockPos start, BlockPos candidate, BlockPos target) {
        int radius = getSearchRadius(start, target);
        int height = getSearchHeight(start, target);
        return Math.abs(candidate.getX() - start.getX()) <= radius
            && Math.abs(candidate.getZ() - start.getZ()) <= radius
            && Math.abs(candidate.getY() - start.getY()) <= height;
    }

    List<BlockPos> collectGoalCandidates(Level world, BlockPos start, BlockPos target) {
        boolean exactTargetNavigable = target != null && isNavigableNode(world, target);
        if (target != null
            && isWithinSearchBounds(start, target, target)
            && isChunkLoaded(world, target)
            && isGoalNodeReachable(world, target)
            && !isHardDanger(world, target)) {
            return List.of(target.immutable());
        }

        List<ScoredPos> scored = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (int radius = 0; radius <= GOAL_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = MAX_STEP_UP + 1; dy >= -MAX_DROP_DOWN; dy--) {
                        BlockPos candidate = new BlockPos(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
                        if (!isWithinSearchBounds(start, candidate, target)
                            || !isChunkLoaded(world, candidate)
                            || !isGoalNodeReachable(world, candidate)
                            || isHardDanger(world, candidate)
                            || (!exactTargetNavigable && !host.isDirectGoalCompletionCandidate(candidate, target))
                            || !seen.add(candidate)) {
                            continue;
                        }
                        scored.add(new ScoredPos(candidate.immutable(), scoreGoalCandidate(world, start, candidate, target, exactTargetNavigable)));
                    }
                }
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredPos::score));
        List<BlockPos> result = new ArrayList<>(Math.min(MAX_GOAL_CANDIDATES, scored.size()));
        for (int i = 0; i < scored.size() && i < MAX_GOAL_CANDIDATES; i++) {
            result.add(scored.get(i).pos());
        }
        return result;
    }

    double scoreGoalCandidate(Level world, BlockPos start, BlockPos candidate, BlockPos target, boolean exactTargetNavigable) {
        double horizontal = Math.sqrt(horizontalDistanceSq(candidate, target));
        double startDistance = start == null ? 0.0D : Math.sqrt(horizontalDistanceSq(start, candidate)) * 0.42D;
        double verticalPenalty = Math.abs(candidate.getY() - target.getY()) * 1.35D;
        double opennessBonus = countOpenNeighbors(world, candidate) * -0.12D;
        double exactTargetBias = candidate.equals(target) ? -2.5D : 0.0D;
        double failedPenalty = isFailedNode(candidate, System.currentTimeMillis()) ? FAILED_MOVE_PENALTY : 0.0D;
        double approachPenalty = 0.0D;
        if (!exactTargetNavigable) {
            if (candidate.below().equals(target)) {
                approachPenalty += 0.9D;
            }
            if (candidate.getY() != target.getY()) {
                approachPenalty += 1.6D;
            }
        }
        double modificationPenalty = 0.0D;
        List<BlockPos> breakTargets = getRequiredBreakTargets(world, candidate);
        if (breakTargets != null && !breakTargets.isEmpty()) {
            modificationPenalty += PATH_BREAK_ROUTE_PENALTY + BREAK_ASSIST_SURCHARGE + (breakTargets.size() * 3.0D);
        }
        if (needsPlacedSupport(world, candidate)) {
            modificationPenalty += PATH_PLACE_ROUTE_PENALTY + PLACE_ASSIST_SURCHARGE;
        }
        return horizontal
            + startDistance
            + verticalPenalty
            + opennessBonus
            + exactTargetBias
            + failedPenalty
            + approachPenalty
            + modificationPenalty
            + terrainPenalty(world, candidate, candidate);
    }

    int countOpenNeighbors(Level world, BlockPos pos) {
        int count = 0;
        for (Move move : MOVES) {
            if (findNeighbor(world, pos, move.dx(), move.dz(), pos, pos, true) != null) {
                count++;
            }
        }
        return count;
    }

    boolean isGoalNodeReachable(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        if (isNavigableNode(world, pos)) {
            return true;
        }
        // An exact air target can be a valid endpoint when the navigator can create
        // its support. This includes a short chained pillar after mining the top of
        // an obstructing column; the search accounts for each placement and rejects
        // the route if the inventory cannot fund it.
        if (host.allowBlockPlacing()
            && needsPlacedSupport(world, pos)
            && canOccupy(world, pos)
            && canOccupy(world, pos.above())
            && hasPillarSupportPath(world, pos)) {
            return true;
        }
        if (getRequiredBreakTargets(world, pos) == null) {
            return false;
        }
        if (needsPlacedSupport(world, pos)) {
            return false;
        }
        return resolveSupportSurfaceY(world, pos).isPresent() || isWaterNode(world, pos);
    }

    boolean hasPillarSupportPath(Level world, BlockPos target) {
        if (world == null || target == null || !host.allowBlockPlacing()) {
            return false;
        }
        for (int steps = 1; steps <= MAX_PLANNED_PILLAR_STEPS; steps++) {
            BlockPos firstPillarTarget = target.below(steps);
            if (!canOccupy(world, firstPillarTarget) || !canOccupy(world, firstPillarTarget.above())) {
                continue;
            }
            boolean clearColumn = true;
            for (BlockPos columnPos = firstPillarTarget.above(); columnPos.getY() <= target.above().getY(); columnPos = columnPos.above()) {
                if (!canOccupy(world, columnPos)) {
                    clearColumn = false;
                    break;
                }
            }
            if (clearColumn && canPlaceSupportAt(world, firstPillarTarget.below(), true)) {
                return true;
            }
        }
        return false;
    }

    Neighbor resolveNeighborAccess(Level world, BlockPos from, BlockPos candidate) {
        if (world == null || from == null || candidate == null) {
            return null;
        }
        boolean sameColumnUp = candidate.getX() == from.getX()
            && candidate.getZ() == from.getZ()
            && candidate.getY() > from.getY();
        boolean climbTransition = sameColumnUp && isClimbTransition(world, from, candidate);
        if (sameColumnUp && !climbTransition && !canPillarTo(world, from, candidate)) {
            return null;
        }
        List<BlockPos> breakTargets = getRequiredBreakTargets(world, from, candidate);
        if (breakTargets == null) {
            return null;
        }
        if (sameColumnUp && !climbTransition && !breakTargets.isEmpty()) {
            return null;
        }
        boolean navigableCandidate = isNavigableNode(world, candidate);
        boolean requiresSupport = host.allowBlockPlacing() && needsPlacedSupport(world, candidate);
        if (navigableCandidate && breakTargets.isEmpty() && !requiresSupport) {
            PlannedPrimitive primitive = host.createPlannedPrimitive(world, from, candidate, List.of(), null);
            return new Neighbor(
                searchPosition(candidate),
                primitiveSearchPenalty(world, from, candidate, primitive),
                primitive
            );
        }
        if (!breakTargets.isEmpty()) {
            if (!host.allowBlockBreaking()) {
                return null;
            }
            if (shouldAvoidGoalModification(world, candidate)) {
                return null;
            }
            if (hasInteractableAlternative(world, from, candidate, host.targetPos())) {
                return null;
            }
        }

        if (requiresSupport) {
            BlockPos activeTarget = host.targetPos();
            if (activeTarget != null && candidate.below().equals(activeTarget)) {
                return null;
            }
            if (shouldAvoidGoalModification(world, candidate)) {
                return null;
            }
            if (!canUsePlacedSupportMove(world, from, candidate)) {
                return null;
            }
            if (hasNaturalGroundAlternative(world, from, candidate, activeTarget)) {
                return null;
            }
            if (!canPlaceSupportAt(world, candidate.below())) {
                return null;
            }
        } else if (resolveSupportSurfaceY(world, candidate).isEmpty() && !isWaterNode(world, candidate)) {
            return null;
        }

        BlockPos placeTarget = requiresSupport ? candidate.below().immutable() : null;
        List<BlockPos> normalizedBreakTargets = breakTargets == null ? List.of() : breakTargets.stream()
            .filter(pos -> pos != null && isBreakableForNavigator(world, pos))
            .map(BlockPos::immutable)
            .toList();
        PlannedPrimitive primitive = host.createPlannedPrimitive(world, from, candidate, normalizedBreakTargets, placeTarget);
        return new Neighbor(
            searchPosition(candidate),
            primitiveSearchPenalty(world, from, candidate, primitive),
            primitive
        );
    }

    double primitiveStepBaseCost(BlockPos from, BlockPos to) {
        if (from == null || to == null) {
            return 1.0D;
        }
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        if (dx == 1 && dz == 1) {
            return Math.sqrt(2.0D);
        }
        return 1.0D;
    }

    double primitiveSearchPenalty(Level world, BlockPos from, BlockPos to, PlannedPrimitive primitive) {
        if (primitive == null) {
            return moveTypePenalty(world, from, to);
        }
        double penalty = moveTypePenalty(world, from, to);
        if (primitive.isPillar()) {
            penalty += SEARCH_PILLAR_PENALTY;
        } else if (primitive.isJump()) {
            penalty += SEARCH_JUMP_PENALTY;
        } else if (primitive.isDescend()) {
            penalty += SEARCH_DESCEND_PENALTY;
        } else if (primitive.isClimb()) {
            penalty += SEARCH_CLIMB_PENALTY;
        } else if (primitive.isSwim()) {
            penalty += SEARCH_SWIM_PENALTY;
        } else if (primitive.isInteractable()) {
            penalty += SEARCH_INTERACT_PENALTY;
        }
        if (primitive.requiresBreak()) {
            penalty += SEARCH_BREAK_PENALTY + BREAK_ASSIST_SURCHARGE;
            for (BlockPos breakTarget : primitive.breakTargets()) {
                penalty += breakPenalty(world, breakTarget) * 1.25D;
            }
        }
        if (primitive.requiresPlace()) {
            penalty += SEARCH_PLACE_PENALTY + PLACE_MOVE_PENALTY + PLACE_ASSIST_SURCHARGE;
        }
        if (primitive.requiresWorldModification() && isTreeCanopyNode(world, to)) {
            penalty += TREE_CANOPY_MODIFICATION_PENALTY;
        }
        return penalty;
    }

    boolean canPillarTo(Level world, BlockPos from, BlockPos candidate) {
        if (!host.allowBlockPlacing() || world == null || from == null || candidate == null) {
            return false;
        }
        if (candidate.getX() != from.getX() || candidate.getZ() != from.getZ() || candidate.getY() != from.getY() + 1) {
            return false;
        }
        if (!canOccupy(world, candidate) || !canOccupy(world, candidate.above())) {
            return false;
        }
        if (isHardDanger(world, candidate) || isWaterNode(world, candidate)) {
            return false;
        }
        return canPlaceSupportAt(world, candidate.below(), true);
    }

    boolean canExtendPlannedPillarTo(Level world, BlockPos from, BlockPos candidate) {
        if (!host.allowBlockPlacing() || world == null || from == null || candidate == null) {
            return false;
        }
        if (candidate.getX() != from.getX() || candidate.getZ() != from.getZ() || candidate.getY() != from.getY() + 1) {
            return false;
        }
        return canOccupy(world, candidate)
            && canOccupy(world, candidate.above())
            && !isHardDanger(world, candidate)
            && !isWaterNode(world, candidate);
    }

    boolean canContinuePillarTo(Level world, BlockPos pillarBase, BlockPos pillarTarget) {
        if (!host.allowBlockPlacing() || world == null || pillarBase == null || pillarTarget == null) {
            return false;
        }
        if (!pillarTarget.equals(pillarBase.above())) {
            return false;
        }
        if (!canOccupy(world, pillarTarget) || !canOccupy(world, pillarTarget.above())) {
            return false;
        }
        if (isHardDanger(world, pillarTarget) || isWaterNode(world, pillarTarget)) {
            return false;
        }
        return canPlaceSupportAt(world, pillarBase, true);
    }

    boolean canUsePlacedSupportMove(Level world, BlockPos from, BlockPos candidate) {
        if (world == null || from == null || candidate == null) {
            return false;
        }
        if (candidate.getY() != from.getY()) {
            return false;
        }
        int dx = Math.abs(candidate.getX() - from.getX());
        int dz = Math.abs(candidate.getZ() - from.getZ());
        if (dx + dz != 1) {
            return false;
        }
        if (!canOccupy(world, candidate) || !canOccupy(world, candidate.above())) {
            return false;
        }
        if (host.requiresBreakingForWaypoint(world, candidate)) {
            return false;
        }
        return true;
    }

    boolean hasNaturalGroundAlternative(Level world, BlockPos from, BlockPos candidate, BlockPos activeTarget) {
        if (world == null || from == null || candidate == null) {
            return false;
        }
        double candidateTargetDistance = activeTarget == null
            ? horizontalDistanceSq(from, candidate)
            : horizontalDistanceSq(candidate, activeTarget);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos alternative = from.relative(direction);
            if (alternative.equals(candidate) || alternative.equals(from)) {
                continue;
            }
            if (!isNavigableNode(world, alternative)) {
                continue;
            }
            if (host.requiresBreakingForWaypoint(world, alternative) || needsPlacedSupport(world, alternative)) {
                continue;
            }
            if (isHardDanger(world, alternative)) {
                continue;
            }
            double alternativeTargetDistance = activeTarget == null
                ? horizontalDistanceSq(from, alternative)
                : horizontalDistanceSq(alternative, activeTarget);
            if (alternativeTargetDistance <= candidateTargetDistance + 1.0D) {
                return true;
            }
        }
        return false;
    }

    boolean hasInteractableAlternative(Level world, BlockPos from, BlockPos candidate, BlockPos activeTarget) {
        if (world == null || from == null || candidate == null) {
            return false;
        }
        double candidateTargetDistance = activeTarget == null
            ? horizontalDistanceSq(from, candidate)
            : horizontalDistanceSq(candidate, activeTarget);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos alternative = from.relative(direction);
            if (alternative.equals(candidate) || alternative.equals(from)) {
                continue;
            }
            if (isHardDanger(world, alternative)) {
                continue;
            }
            if (!(requiresInteractableTraversal(world, from, alternative) || hasPathOpenableAhead(world, from, alternative))) {
                continue;
            }
            if (!host.isWaypointActionable(world, alternative)) {
                continue;
            }
            double alternativeTargetDistance = activeTarget == null
                ? horizontalDistanceSq(from, alternative)
                : horizontalDistanceSq(alternative, activeTarget);
            if (alternativeTargetDistance <= candidateTargetDistance + 2.0D) {
                return true;
            }
        }
        return false;
    }

    boolean shouldAvoidGoalModification(Level world, BlockPos candidate) {
        if (world == null || candidate == null) {
            return false;
        }
        BlockPos activeTarget = host.targetPos();
        if (activeTarget == null) {
            return false;
        }
        if (!isStandable(world, activeTarget)) {
            return false;
        }
        if (candidate.equals(activeTarget)) {
            return false;
        }
        return horizontalDistanceSq(candidate, activeTarget) <= GOAL_MODIFICATION_AVOID_DISTANCE_SQ
            && Math.abs(candidate.getY() - activeTarget.getY()) <= 1;
    }

    BlockPos findNearbyStandable(Level world, BlockPos around, int maxRadius) {
        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = MAX_STEP_UP + 1; dy >= -MAX_DROP_DOWN; dy--) {
                        BlockPos candidate = new BlockPos(around.getX() + dx, around.getY() + dy, around.getZ() + dz);
                        if (isChunkLoaded(world, candidate) && isNavigableNode(world, candidate) && !isHardDanger(world, candidate)) {
                            return candidate.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    double moveTypePenalty(Level world, BlockPos from, BlockPos to) {
        return NavigatorPathCostPolicy.moveTypePenalty(classifyMoveType(world, from, to));
    }

NavigatorPathCostPolicy.MoveType classifyMoveType(Level world, BlockPos from, BlockPos to) {
        if (from == null || to == null) {
            return NavigatorPathCostPolicy.MoveType.STRAIGHT;
        }
        boolean fromWater = isWaterNode(world, from);
        boolean toWater = isWaterNode(world, to);
        if (!fromWater && toWater) {
            return NavigatorPathCostPolicy.MoveType.WATER_ENTER;
        }
        if (fromWater && toWater) {
            return NavigatorPathCostPolicy.MoveType.WATER_SWIM;
        }
        if (fromWater) {
            return NavigatorPathCostPolicy.MoveType.WATER_EXIT;
        }
        if (from.getX() == to.getX() && from.getZ() == to.getZ()) {
            return to.getY() > from.getY()
                ? NavigatorPathCostPolicy.MoveType.CLIMB_UP
                : NavigatorPathCostPolicy.MoveType.CLIMB_DOWN;
        }
        if (requiresInteractableTraversal(world, from, to)) {
            return NavigatorPathCostPolicy.MoveType.INTERACTABLE;
        }

        int deltaY = to.getY() - from.getY();
        if (deltaY > 0) {
            return NavigatorPathCostPolicy.MoveType.STEP_UP;
        }
        if (deltaY < 0) {
            return NavigatorPathCostPolicy.MoveType.DROP;
        }
        return (from.getX() != to.getX() && from.getZ() != to.getZ())
            ? NavigatorPathCostPolicy.MoveType.DIAGONAL
            : NavigatorPathCostPolicy.MoveType.STRAIGHT;
    }

    void rememberFailedMove(BlockPos from, BlockPos to, long now) {
        boolean protectedGoal = isProtectedNavigationGoal(to);
        failureMemory.rememberMove(from, to, now, FAILED_MOVE_MEMORY_MS, protectedGoal);
    }

    boolean isProtectedNavigationGoal(BlockPos pos) {
        return host.isProtectedNavigationGoal(pos);
    }

    void rememberFailedBreak(BlockPos from, BlockPos to, long now) {
        rememberFailedMove(from, to, now);
        failureMemory.rememberAction(NavigatorFailureMemory.Action.BREAK, from, to, now, FAILED_BREAK_MEMORY_MS);
    }

    void rememberFailedJump(BlockPos from, BlockPos to, long now) {
        rememberFailedMove(from, to, now);
        failureMemory.rememberAction(NavigatorFailureMemory.Action.JUMP, from, to, now, FAILED_JUMP_MEMORY_MS);
    }

    void rememberFailedDrop(BlockPos from, BlockPos to, long now) {
        rememberFailedMove(from, to, now);
        failureMemory.rememberAction(NavigatorFailureMemory.Action.DROP, from, to, now, FAILED_DROP_MEMORY_MS);
    }

    void rememberFailedPlace(BlockPos from, BlockPos to, long now) {
        rememberFailedMove(from, to, now);
        failureMemory.rememberAction(NavigatorFailureMemory.Action.PLACE, from, to, now, FAILED_PLACE_MEMORY_MS);
    }

    void rememberFailedPillar(BlockPos from, BlockPos to, long now) {
        rememberFailedMove(from, to, now);
        failureMemory.rememberAction(NavigatorFailureMemory.Action.PILLAR, from, to, now, FAILED_PILLAR_MEMORY_MS);
    }

    void pruneFailureMemory(long now) {
        failureMemory.prune(now);
    }

    boolean isFailedNode(BlockPos pos, long now) {
        return failureMemory.isFailedNode(pos, now);
    }

    boolean isFailedEdge(BlockPos from, BlockPos to, long now) {
        return failureMemory.isFailedEdge(from, to, now);
    }

    boolean isFailedBreak(BlockPos from, BlockPos to, long now) {
        return failureMemory.isFailedAction(NavigatorFailureMemory.Action.BREAK, from, to, now);
    }

    boolean isFailedJump(BlockPos from, BlockPos to, long now) {
        return failureMemory.isFailedAction(NavigatorFailureMemory.Action.JUMP, from, to, now);
    }

    boolean isFailedDrop(BlockPos from, BlockPos to, long now) {
        return failureMemory.isFailedAction(NavigatorFailureMemory.Action.DROP, from, to, now);
    }

    boolean isFailedPlace(BlockPos from, BlockPos to, long now) {
        return failureMemory.isFailedAction(NavigatorFailureMemory.Action.PLACE, from, to, now);
    }

    boolean isFailedPillar(BlockPos from, BlockPos to, long now) {
        return failureMemory.isFailedAction(NavigatorFailureMemory.Action.PILLAR, from, to, now);
    }

    boolean isGoal(BlockPos pos, BlockPos target, Set<BlockPos> goalSet) {
        return goalSet.contains(pos);
    }

    double heuristic(BlockPos pos, List<BlockPos> goals) {
        return NavigatorPathCostPolicy.heuristic(pos, goals, HEURISTIC_WEIGHT);
    }

    double elevationPenalty(BlockPos from, BlockPos to) {
        return NavigatorPathCostPolicy.elevationPenalty(from, to);
    }

    double horizontalDistanceSq(BlockPos a, BlockPos b) {
        return NavigatorPathCostPolicy.horizontalDistanceSq(a, b);
    }

    BlockPos searchPosition(BlockPos pos) {
        return pos == null ? null : pos.immutable();
    }

    boolean hasReachedExactGoal(BlockPos playerFootPos, BlockPos requestedTarget) {
        if (playerFootPos == null || requestedTarget == null) {
            return false;
        }
        return NavigatorGeometry.isExactGoalBlock(
            playerFootPos.getX(),
            playerFootPos.getY(),
            playerFootPos.getZ(),
            requestedTarget.getX(),
            requestedTarget.getY(),
            requestedTarget.getZ()
        );
    }

    boolean isNavigableNode(Level world, BlockPos footPos) {
        if (world == null || footPos == null) {
            return false;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        BlockPos key = footPos.immutable();
        if (cache != null) {
            Boolean cached = cache.navigableNodes.get(key);
            if (cached != null) {
                return cached;
            }
        }
        boolean result = isStandable(world, footPos) || isClimbNode(world, footPos);
        if (cache != null) {
            cache.navigableNodes.put(key, result);
        }
        return result;
    }

    boolean isStandable(Level world, BlockPos footPos) {
        if (world == null || footPos == null) {
            return false;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        BlockPos key = footPos.immutable();
        if (cache != null) {
            Boolean cached = cache.standableNodes.get(key);
            if (cached != null) {
                return cached;
            }
        }
        boolean result = computeStandable(world, footPos);
        if (cache != null) {
            cache.standableNodes.put(key, result);
        }
        return result;
    }

    boolean computeStandable(Level world, BlockPos footPos) {
        if (!world.isInWorldBounds(footPos) || !isChunkLoaded(world, footPos)) {
            return false;
        }
        if (isLava(cachedFluidState(world, footPos)) || isLava(cachedFluidState(world, footPos.above()))) {
            return false;
        }
        if (isWaterNode(world, footPos)) {
            return canPlayerFitAtNode(world, footPos, false);
        }
        return canPlayerFitAtNode(world, footPos, true);
    }

    boolean isClimbNode(Level world, BlockPos footPos) {
        if (world == null || footPos == null) {
            return false;
        }
        if (!world.isInWorldBounds(footPos) || !isChunkLoaded(world, footPos)) {
            return false;
        }
        if (isLava(cachedFluidState(world, footPos)) || isLava(cachedFluidState(world, footPos.above()))) {
            return false;
        }
        if (!canOccupy(world, footPos) || !canOccupy(world, footPos.above())) {
            return false;
        }
        return isClimbableNode(world, footPos)
            || isClimbableNode(world, footPos.above())
            || isClimbableNode(world, footPos.below());
    }

    boolean hasCollision(Level world, BlockPos pos) {
        NavigatorPlanningCache cache = planningCacheFor(world);
        BlockPos key = pos == null ? null : pos.immutable();
        if (cache != null && key != null) {
            Boolean cached = cache.hasCollision.get(key);
            if (cached != null) {
                return cached;
            }
        }
        BlockState state = cachedBlockState(world, pos);
        if (state == null || state.isAir()) {
            return false;
        }
        boolean result = !cachedCollisionShape(world, pos, state).isEmpty();
        if (cache != null && key != null) {
            cache.hasCollision.put(key, result);
        }
        return result;
    }

    boolean needsPlacedSupport(Level world, BlockPos footPos) {
        return world != null
            && footPos != null
            && resolveSupportSurfaceY(world, footPos).isEmpty()
            && !isWaterNode(world, footPos);
    }

    boolean canPlayerFitAtNode(Level world, BlockPos footPos, boolean requireSupport) {
        if (world == null || footPos == null || !isChunkLoaded(world, footPos)) {
            return false;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        NavigatorNodeFitKey key = cache == null ? null : new NavigatorNodeFitKey(footPos.immutable(), requireSupport);
        if (cache != null) {
            Boolean cached = cache.nodeFit.get(key);
            if (cached != null) {
                return cached;
            }
        }
        OptionalDouble supportY = resolveSupportSurfaceY(world, footPos);
        if (requireSupport && supportY.isEmpty()) {
            if (cache != null) {
                cache.nodeFit.put(key, false);
            }
            return false;
        }
        double feetY = supportY.orElse(footPos.getY());
        boolean result = !hasTraversalCollision(world, playerBodyAt(footPos.getX() + 0.5D, feetY, footPos.getZ() + 0.5D));
        if (cache != null) {
            cache.nodeFit.put(key, result);
        }
        return result;
    }

    OptionalDouble resolveSupportSurfaceY(Level world, BlockPos footPos) {
        if (world == null || footPos == null) {
            return OptionalDouble.empty();
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        BlockPos key = footPos.immutable();
        if (cache != null) {
            OptionalDouble cached = cache.supportSurfaces.get(key);
            if (cached != null) {
                return cached;
            }
        }
        OptionalDouble result = computeSupportSurfaceY(world, footPos);
        if (cache != null) {
            cache.supportSurfaces.put(key, result);
        }
        return result;
    }

    OptionalDouble computeSupportSurfaceY(Level world, BlockPos footPos) {
        double minX = footPos.getX() + 0.5D - PLAYER_HALF_WIDTH + COLLISION_EPSILON;
        double maxX = footPos.getX() + 0.5D + PLAYER_HALF_WIDTH - COLLISION_EPSILON;
        double minZ = footPos.getZ() + 0.5D - PLAYER_HALF_WIDTH + COLLISION_EPSILON;
        double maxZ = footPos.getZ() + 0.5D + PLAYER_HALF_WIDTH - COLLISION_EPSILON;
        double minimumSurface = footPos.getY() - COLLISION_EPSILON;
        double maximumSurface = footPos.getY() + 1.0D - COLLISION_EPSILON;
        List<AABB> supportBoxes = new ArrayList<>();
        for (int blockY = footPos.getY() - 1; blockY <= footPos.getY(); blockY++) {
            for (int blockX = Mth.floor(minX); blockX <= Mth.floor(maxX); blockX++) {
                for (int blockZ = Mth.floor(minZ); blockZ <= Mth.floor(maxZ); blockZ++) {
                    BlockPos supportPos = new BlockPos(blockX, blockY, blockZ);
                    if (!isChunkLoaded(world, supportPos)) {
                        continue;
                    }
                    BlockState state = cachedBlockState(world, supportPos);
                    boolean closedTrapdoor = state != null
                        && state.is(BlockTags.TRAPDOORS)
                        && state.hasProperty(BlockStateProperties.OPEN)
                        && !state.getValue(BlockStateProperties.OPEN);
                    if (state == null
                        || state.isAir()
                        || (isPathOpenable(state) && !closedTrapdoor)
                        || isClimbableBlock(state)) {
                        continue;
                    }
                    VoxelShape shape = cachedCollisionShape(world, supportPos, state);
                    for (AABB localBox : shape.toAabbs()) {
                        supportBoxes.add(localBox.move(supportPos.getX(), supportPos.getY(), supportPos.getZ()));
                    }
                }
            }
        }
        return NavigatorGeometry.highestSupportingSurface(
            minX,
            maxX,
            minZ,
            maxZ,
            minimumSurface,
            maximumSurface,
            supportBoxes
        );
    }

    AABB playerBodyAt(double centerX, double feetY, double centerZ) {
        return new AABB(
            centerX - PLAYER_HALF_WIDTH + COLLISION_EPSILON,
            feetY + COLLISION_EPSILON,
            centerZ - PLAYER_HALF_WIDTH + COLLISION_EPSILON,
            centerX + PLAYER_HALF_WIDTH - COLLISION_EPSILON,
            feetY + PLAYER_HEIGHT - COLLISION_EPSILON,
            centerZ + PLAYER_HALF_WIDTH - COLLISION_EPSILON
        );
    }

    AABB playerBodyAtNode(Level world, BlockPos footPos, boolean requireSupport) {
        if (world == null || footPos == null) {
            return null;
        }
        OptionalDouble supportY = resolveSupportSurfaceY(world, footPos);
        if (requireSupport && supportY.isEmpty()) {
            return null;
        }
        return playerBodyAt(footPos.getX() + 0.5D, supportY.orElse(footPos.getY()), footPos.getZ() + 0.5D);
    }

    boolean hasTraversalCollision(Level world, AABB body) {
        if (world == null || body == null) {
            return true;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        NavigatorBodyKey bodyKey = cache == null ? null : NavigatorBodyKey.of(body);
        if (cache != null) {
            Boolean cached = cache.bodyCollisions.get(bodyKey);
            if (cached != null) {
                return cached;
            }
        }
        boolean result = computeTraversalCollision(world, body);
        if (cache != null) {
            cache.bodyCollisions.put(bodyKey, result);
        }
        return result;
    }

    boolean computeTraversalCollision(Level world, AABB body) {
        int minX = Mth.floor(body.minX + COLLISION_EPSILON);
        int maxX = Mth.floor(body.maxX - COLLISION_EPSILON);
        int minY = Mth.floor(body.minY + COLLISION_EPSILON);
        int maxY = Mth.floor(body.maxY - COLLISION_EPSILON);
        int minZ = Mth.floor(body.minZ + COLLISION_EPSILON);
        int maxZ = Mth.floor(body.maxZ - COLLISION_EPSILON);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!isChunkLoaded(world, pos)) {
                        return true;
                    }
                    BlockState state = cachedBlockState(world, pos);
                    if (state == null || state.isAir()) {
                        continue;
                    }
                    VoxelShape shape = traversalCollisionShape(world, pos, state);
                    for (AABB localBox : shape.toAabbs()) {
                        AABB box = localBox.move(x, y, z);
                        if (intersectsStrictly(body, box)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    VoxelShape traversalCollisionShape(Level world, BlockPos pos, BlockState state) {
        if (state == null) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        if (isPathOpenable(state)
            && state.hasProperty(BlockStateProperties.OPEN)
            && !state.getValue(BlockStateProperties.OPEN)) {
            BlockState opened = state.setValue(BlockStateProperties.OPEN, true);
            return opened.getCollisionShape(world, pos);
        }
        return cachedCollisionShape(world, pos, state);
    }

    boolean intersectsStrictly(AABB first, AABB second) {
        return NavigatorGeometry.intersectsStrictly(first, second, COLLISION_EPSILON);
    }

    boolean canOccupy(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        BlockPos key = pos.immutable();
        if (cache != null) {
            Boolean cached = cache.occupiable.get(key);
            if (cached != null) {
                return cached;
            }
        }
        BlockState state = cachedBlockState(world, pos);
        boolean result;
        if (state == null || state.isAir()) {
            result = true;
        } else if (state.canBeReplaced()) {
            result = true;
        } else if (isClimbableBlock(state) || isPathOpenable(state)) {
            result = true;
        } else {
            result = cachedCollisionShape(world, pos, state).isEmpty();
        }
        if (cache != null) {
            cache.occupiable.put(key, result);
        }
        return result;
    }

    NavigatorPlanningCache planningCacheFor(Level world) {
        NavigatorPlanningCache cache = activePlanningCache.get();
        return cache != null && cache.world == world ? cache : null;
    }

    boolean worldModificationAllowed(Level world) {
        NavigatorPlanningCache cache = planningCacheFor(world);
        return cache == null || cache.allowWorldModification;
    }

    BlockState cachedBlockState(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        if (cache == null) {
            return world.getBlockState(pos);
        }
        BlockPos key = pos.immutable();
        BlockState cached = cache.blockStates.get(key);
        if (cached != null) {
            cache.blockStateHits++;
            return cached;
        }
        BlockState state = world.getBlockState(pos);
        if (state != null) {
            cache.blockStates.put(key, state);
        }
        return state;
    }

    FluidState cachedFluidState(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        if (cache == null) {
            return world.getFluidState(pos);
        }
        BlockPos key = pos.immutable();
        FluidState cached = cache.fluidStates.get(key);
        if (cached != null) {
            return cached;
        }
        FluidState state = world.getFluidState(pos);
        if (state != null) {
            cache.fluidStates.put(key, state);
        }
        return state;
    }

    VoxelShape cachedCollisionShape(Level world, BlockPos pos, BlockState state) {
        if (state == null) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        if (cache == null || pos == null) {
            return state.getCollisionShape(world, pos);
        }
        BlockPos key = pos.immutable();
        VoxelShape cached = cache.collisionShapes.get(key);
        if (cached != null) {
            cache.collisionShapeHits++;
            return cached;
        }
        VoxelShape shape = state.getCollisionShape(world, pos);
        cache.collisionShapes.put(key, shape);
        return shape;
    }

    boolean isUnstableSupportBlock(BlockState state) {
        return state != null && state.is(BlockTags.LEAVES);
    }

    boolean isTreeCanopyNode(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        BlockPos key = pos.immutable();
        if (cache != null) {
            Boolean cached = cache.treeCanopyNodes.get(key);
            if (cached != null) {
                return cached;
            }
        }
        boolean result = computeTreeCanopyNode(world, pos);
        if (cache != null) {
            cache.treeCanopyNodes.put(key, result);
        }
        return result;
    }

    boolean computeTreeCanopyNode(Level world, BlockPos pos) {
        if (isUnstableSupportBlock(cachedBlockState(world, pos.below()))) {
            return true;
        }
        int leafCount = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos sample = pos.offset(dx, dy, dz);
                    if (isUnstableSupportBlock(cachedBlockState(world, sample))) {
                        leafCount++;
                        if (leafCount >= 2) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    List<BlockPos> getRequiredBreakTargets(Level world, BlockPos footPos) {
        return getRequiredBreakTargets(world, footPos, footPos);
    }

    List<BlockPos> getRequiredBreakTargets(Level world, BlockPos from, BlockPos footPos) {
        if (world == null || footPos == null) {
            return null;
        }
        BlockPos normalizedFrom = from == null ? footPos.immutable() : from.immutable();
        BlockPos normalizedTo = footPos.immutable();
        NavigatorPlanningCache cache = planningCacheFor(world);
        NavigatorMovementQueryKey key = cache == null ? null : new NavigatorMovementQueryKey(normalizedFrom, normalizedTo);
        if (cache != null) {
            NavigatorBreakTargetCacheEntry cached = cache.breakTargets.get(key);
            if (cached != null) {
                return cached.valid() ? cached.targets() : null;
            }
        }
        List<BlockPos> result = computeRequiredBreakTargets(world, from, footPos);
        if (cache != null) {
            cache.breakTargets.put(
                key,
                result == null
                    ? new NavigatorBreakTargetCacheEntry(false, List.of())
                    : new NavigatorBreakTargetCacheEntry(true, result)
            );
        }
        return result;
    }

    List<BlockPos> computeRequiredBreakTargets(Level world, BlockPos from, BlockPos footPos) {
        LinkedHashSet<BlockPos> targets = new LinkedHashSet<>();
        AABB destinationBody = playerBodyAtNode(world, footPos, false);
        if (destinationBody == null || !collectBreakTargetsIntersectingBody(world, destinationBody, targets)) {
            return null;
        }

        if (from != null && !from.equals(footPos)) {
            AABB sourceBody = playerBodyAtNode(world, from, false);
            if (sourceBody == null) {
                return null;
            }
            int deltaY = footPos.getY() - from.getY();
            double sourceFeetY = sourceBody.minY - COLLISION_EPSILON;
            double destinationFeetY = destinationBody.minY - COLLISION_EPSILON;
            double supportDelta = destinationFeetY - sourceFeetY;
            if (deltaY > 0) {
                AABB jumpApexBody = sourceBody.move(0.0D, Math.min(0.65D, Math.max(0.35D, supportDelta)), 0.0D);
                if (!collectBreakTargetsIntersectingBody(world, jumpApexBody, targets)) {
                    return null;
                }
            } else if (deltaY == 0 && Math.abs(supportDelta) <= COLLISION_EPSILON) {
                for (int sample = 1; sample <= 3; sample++) {
                    double progress = sample / 4.0D;
                    double centerX = Mth.lerp(progress, from.getX() + 0.5D, footPos.getX() + 0.5D);
                    double centerZ = Mth.lerp(progress, from.getZ() + 0.5D, footPos.getZ() + 0.5D);
                    AABB sampleBody = playerBodyAt(centerX, sourceFeetY, centerZ);
                    if (!collectBreakTargetsIntersectingBody(world, sampleBody, targets)) {
                        return null;
                    }
                }
            }
        }
        return List.copyOf(targets);
    }

    boolean collectBreakTargetsIntersectingBody(Level world, AABB body, Set<BlockPos> targets) {
        if (world == null || body == null || targets == null) {
            return false;
        }
        int minX = Mth.floor(body.minX + COLLISION_EPSILON);
        int maxX = Mth.floor(body.maxX - COLLISION_EPSILON);
        int minY = Mth.floor(body.minY + COLLISION_EPSILON);
        int maxY = Mth.floor(body.maxY - COLLISION_EPSILON);
        int minZ = Mth.floor(body.minZ + COLLISION_EPSILON);
        int maxZ = Mth.floor(body.maxZ - COLLISION_EPSILON);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!isChunkLoaded(world, pos)) {
                        return false;
                    }
                    BlockState state = cachedBlockState(world, pos);
                    if (state == null || state.isAir()) {
                        continue;
                    }
                    boolean intersects = false;
                    VoxelShape traversalShape = traversalCollisionShape(world, pos, state);
                    for (AABB localBox : traversalShape.toAabbs()) {
                        if (intersectsStrictly(body, localBox.move(x, y, z))) {
                            intersects = true;
                            break;
                        }
                    }
                    if (!intersects) {
                        continue;
                    }
                    if (isPathOpenable(state) || !isBreakableForNavigator(world, pos)) {
                        return false;
                    }
                    targets.add(pos.immutable());
                }
            }
        }
        return true;
    }

    BlockPos resolvePlanningTarget(ClientLevel world, BlockPos start, BlockPos target) {
        if (isWithinSearchBounds(start, target, target) && isChunkLoaded(world, target)) {
            return target.immutable();
        }

        Vec3 startCenter = Vec3.atCenterOf(start);
        Vec3 targetCenter = Vec3.atCenterOf(target);
        Vec3 direction = targetCenter.subtract(startCenter);
        double horizontalDistance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (horizontalDistance < 0.001D) {
            return findNearbyStandable(world, target, 4);
        }

        Vec3 normalized = direction.normalize();
        int searchRadius = getSearchRadius(start, target);
        int searchHeight = getSearchHeight(start, target);
        double maxDistance = Math.min(horizontalDistance, searchRadius - 2.0D);
        BlockPos best = null;
        for (double distance = Math.max(4.0D, maxDistance); distance >= 4.0D; distance -= 2.0D) {
            Vec3 sample = startCenter.add(normalized.scale(distance));
            BlockPos projected = BlockPos.containing(
                sample.x,
                Mth.clamp((int) Math.round(targetCenter.y), start.getY() - searchHeight, start.getY() + searchHeight),
                sample.z
            );
            BlockPos candidate = findNearbyStandable(world, projected, 4);
            if (candidate != null && isChunkLoaded(world, candidate)) {
                best = candidate;
                break;
            }
        }

        if (best != null) {
            return best;
        }

        return findLoadedFrontierNear(world, start, target);
    }

    BlockPos findLoadedFrontierNear(ClientLevel world, BlockPos start, BlockPos target) {
        BlockPos best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int searchRadius = getSearchRadius(start, target);
        int searchHeight = getSearchHeight(start, target);
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) < searchRadius - 2) {
                    continue;
                }
                for (int dy = -searchHeight; dy <= searchHeight; dy++) {
                    BlockPos candidate = new BlockPos(start.getX() + dx, start.getY() + dy, start.getZ() + dz);
                    if (!isChunkLoaded(world, candidate) || !isNavigableNode(world, candidate) || isHardDanger(world, candidate)) {
                        continue;
                    }
                    double score = horizontalDistanceSq(candidate, target) + terrainPenalty(world, candidate, candidate);
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate.immutable();
                    }
                }
            }
        }
        return best;
    }

    int getSearchRadius(BlockPos start, BlockPos target) {
        if (start == null || target == null) {
            return SEARCH_RADIUS;
        }
        int horizontal = (int) Math.ceil(Math.sqrt(horizontalDistanceSq(start, target)));
        return Math.max(SEARCH_RADIUS, Math.min(MAX_SEARCH_RADIUS, horizontal + 8));
    }

    int getSearchHeight(BlockPos start, BlockPos target) {
        if (start == null || target == null) {
            return SEARCH_HEIGHT;
        }
        int vertical = Math.abs(target.getY() - start.getY());
        return Math.max(SEARCH_HEIGHT, Math.min(MAX_SEARCH_HEIGHT, vertical + 8));
    }

    boolean isChunkLoaded(Level world, BlockPos pos) {
        if (!(world instanceof ClientLevel clientWorld) || pos == null) {
            return false;
        }
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        NavigatorPlanningCache cache = planningCacheFor(world);
        if (cache == null) {
            return clientWorld.hasChunk(chunkX, chunkZ);
        }
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
        Boolean cached = cache.loadedChunks.get(key);
        if (cached != null) {
            return cached;
        }
        boolean loaded = clientWorld.hasChunk(chunkX, chunkZ);
        cache.loadedChunks.put(key, loaded);
        return loaded;
    }

    boolean shouldStepJump(Level world, BlockPos from, BlockPos waypoint) {
        if (world == null || from == null || waypoint == null) {
            return false;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        NavigatorMovementQueryKey key = cache == null ? null : new NavigatorMovementQueryKey(from.immutable(), waypoint.immutable());
        if (cache != null) {
            Boolean cached = cache.stepJumps.get(key);
            if (cached != null) {
                return cached;
            }
        }
        boolean result = computeShouldStepJump(world, from, waypoint);
        if (cache != null) {
            cache.stepJumps.put(key, result);
        }
        return result;
    }

    boolean computeShouldStepJump(Level world, BlockPos from, BlockPos waypoint) {
        if (requiresInteractableTraversal(world, from, waypoint)) {
            return false;
        }
        if (waypoint.getY() > from.getY()) {
            return true;
        }
        int stepX = Integer.compare(waypoint.getX(), from.getX());
        int stepZ = Integer.compare(waypoint.getZ(), from.getZ());
        if (stepX == 0 && stepZ == 0) {
            return false;
        }
        BlockPos front = new BlockPos(from.getX() + stepX, from.getY(), from.getZ() + stepZ);
        OptionalDouble fromSurface = resolveSupportSurfaceY(world, from);
        OptionalDouble frontSurface = resolveSupportSurfaceY(world, front);
        if (fromSurface.isPresent() && frontSurface.isPresent()) {
            return frontSurface.getAsDouble() - fromSurface.getAsDouble() > MAX_AUTOSTEP_HEIGHT;
        }
        return false;
    }

    boolean isBlockedTowardWaypoint(Level world, BlockPos from, BlockPos waypoint) {
        if (world == null || from == null || waypoint == null) {
            return false;
        }
        int stepX = Integer.compare(waypoint.getX(), from.getX());
        int stepZ = Integer.compare(waypoint.getZ(), from.getZ());
        if (stepX == 0 && stepZ == 0) {
            return false;
        }
        if (stepX != 0 && stepZ != 0) {
            BlockPos xFront = new BlockPos(from.getX() + stepX, from.getY(), from.getZ());
            BlockPos zFront = new BlockPos(from.getX(), from.getY(), from.getZ() + stepZ);
            boolean xBlocked = isMovementObstructed(world, xFront);
            boolean zBlocked = isMovementObstructed(world, zFront);
            return xBlocked
                && zBlocked
                && !host.requiresBreakingForWaypoint(world, waypoint)
                && !requiresInteractableTraversal(world, from, waypoint)
                && !hasPathOpenableAhead(world, from, waypoint);
        }
        BlockPos front = new BlockPos(from.getX() + stepX, from.getY(), from.getZ() + stepZ);
        return isMovementObstructed(world, front)
            && !host.requiresBreakingForWaypoint(world, waypoint)
            && !requiresInteractableTraversal(world, from, waypoint)
            && !hasPathOpenableAhead(world, from, waypoint);
    }

    boolean isMovementObstructed(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return !canPlayerFitAtNode(world, pos, false);
    }

    boolean canAttemptJump(Level world, BlockPos from, BlockPos waypoint) {
        if (world == null || from == null || waypoint == null) {
            return false;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        NavigatorMovementQueryKey key = cache == null ? null : new NavigatorMovementQueryKey(from.immutable(), waypoint.immutable());
        if (cache != null) {
            Boolean cached = cache.jumpAttempts.get(key);
            if (cached != null) {
                return cached;
            }
        }
        boolean result = computeCanAttemptJump(world, from, waypoint);
        if (cache != null) {
            cache.jumpAttempts.put(key, result);
        }
        return result;
    }

    boolean computeCanAttemptJump(Level world, BlockPos from, BlockPos waypoint) {
        if (!canOccupy(world, from.above(2))) {
            return false;
        }

        int stepX = Integer.compare(waypoint.getX(), from.getX());
        int stepZ = Integer.compare(waypoint.getZ(), from.getZ());
        BlockPos front = new BlockPos(from.getX() + stepX, from.getY(), from.getZ() + stepZ);

        if (stepX != 0 || stepZ != 0) {
            if (!canOccupy(world, front.above())) {
                return false;
            }
            if (waypoint.getY() > from.getY() && !canOccupy(world, front.above(2))) {
                return false;
            }
            if (waypoint.getY() > from.getY() && isCorneredJump(world, from, stepX, stepZ)) {
                return false;
            }
        }

        if (waypoint.getY() > from.getY() && !canOccupy(world, waypoint.above())) {
            return false;
        }

        return true;
    }

    boolean hasJumpUpOpportunity(Level world, BlockPos from, BlockPos waypoint) {
        if (world == null || from == null || waypoint == null) {
            return false;
        }
        return (waypoint.getY() > from.getY() || shouldStepJump(world, from, waypoint))
            && canAttemptJump(world, from, waypoint);
    }

    BlockPos resolveJumpUpApproachTarget(Level world, BlockPos from, BlockPos waypoint) {
        if (world == null || from == null || waypoint == null) {
            return waypoint;
        }
        int stepX = Integer.compare(waypoint.getX(), from.getX());
        int stepZ = Integer.compare(waypoint.getZ(), from.getZ());
        if (stepX == 0 && stepZ == 0) {
            return waypoint;
        }
        BlockPos candidate = new BlockPos(from.getX() + stepX, from.getY() + 1, from.getZ() + stepZ);
        if (canOccupy(world, candidate) && canOccupy(world, candidate.above())) {
            return candidate;
        }
        return waypoint;
    }

    Vec3 resolveWaypointAimPoint(
        Level world,
        BlockPos playerFootPos,
        BlockPos waypoint,
        BlockPos climbAnchor,
        PlannedPrimitive plannedPrimitive,
        double playerY
    ) {
        BlockPos horizontalAim = climbAnchor != null ? climbAnchor : waypoint;
        if (horizontalAim == null) {
            return Vec3.ZERO;
        }
        double aimX = horizontalAim.getX() + 0.5D;
        double aimZ = horizontalAim.getZ() + 0.5D;
        Vec3 cornerAim = resolveCornerApproachAimPoint(world, playerFootPos, waypoint, climbAnchor, plannedPrimitive, playerY);
        if (cornerAim != null) {
            return cornerAim;
        }
        if (playerFootPos != null
            && waypoint != null
            && plannedPrimitive != null
            && host.isJumpPrimitive(plannedPrimitive)
            && waypoint.getY() > playerFootPos.getY()) {
            int stepX = Integer.compare(waypoint.getX(), playerFootPos.getX());
            int stepZ = Integer.compare(waypoint.getZ(), playerFootPos.getZ());
            if (stepX != 0 || stepZ != 0) {
                aimX -= stepX * 0.32D;
                aimZ -= stepZ * 0.32D;
            }
        }
        return new Vec3(aimX, playerY, aimZ);
    }

    Vec3 resolveSmoothedSteeringAimPoint(
        Level world,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive,
        Vec3 currentPos,
        Vec3 fallbackAim
    ) {
        if (world == null
            || playerFootPos == null
            || waypoint == null
            || currentPos == null
            || fallbackAim == null
            || plannedPrimitive == null
            || !plannedPrimitive.isSimpleMovementStep()) {
            return fallbackAim;
        }
        double distanceToWaypoint = Math.sqrt(
            square(fallbackAim.x - currentPos.x) + square(fallbackAim.z - currentPos.z)
        );
        if (distanceToWaypoint > STEERING_LOOKAHEAD_DISTANCE) {
            return fallbackAim;
        }

        SteeringLookahead lookahead = host.steeringLookahead(waypoint);
        if (lookahead == null) {
            return fallbackAim;
        }
        BlockPos nextWaypoint = lookahead.waypoint();
        PlannedPrimitive nextPrimitive = lookahead.primitive();
        if (nextWaypoint == null
            || nextPrimitive == null
            || !nextPrimitive.isSimpleMovementStep()
            || Math.abs(nextWaypoint.getY() - waypoint.getY()) > 1) {
            return fallbackAim;
        }

        OptionalDouble nextSupport = resolveSupportSurfaceY(world, nextWaypoint);
        double nextFeetY = nextSupport.orElse(nextWaypoint.getY());
        Vec3 nextAim = new Vec3(nextWaypoint.getX() + 0.5D, nextFeetY, nextWaypoint.getZ() + 0.5D);
        if (!isSteeringCorridorClear(world, currentPos, nextAim)) {
            return fallbackAim;
        }

        double blend = NavigatorGeometry.steeringLookaheadBlend(distanceToWaypoint, STEERING_LOOKAHEAD_DISTANCE);
        return new Vec3(
            Mth.lerp(blend, fallbackAim.x, nextAim.x),
            fallbackAim.y,
            Mth.lerp(blend, fallbackAim.z, nextAim.z)
        );
    }

    boolean isSteeringCorridorClear(Level world, Vec3 start, Vec3 end) {
        if (world == null || start == null || end == null) {
            return false;
        }
        for (int sample = 1; sample <= 6; sample++) {
            double progress = sample / 6.0D;
            AABB body = playerBodyAt(
                Mth.lerp(progress, start.x, end.x),
                Mth.lerp(progress, start.y, end.y),
                Mth.lerp(progress, start.z, end.z)
            );
            if (hasTraversalCollision(world, body)) {
                return false;
            }
        }
        return true;
    }

    double square(double value) {
        return value * value;
    }

    Vec3 resolveCornerApproachAimPoint(
        Level world,
        BlockPos playerFootPos,
        BlockPos waypoint,
        BlockPos climbAnchor,
        PlannedPrimitive plannedPrimitive,
        double playerY
    ) {
        if (world == null || playerFootPos == null || waypoint == null || climbAnchor != null || plannedPrimitive == null) {
            return null;
        }
        if (plannedPrimitive.requiresCommittedAction()) {
            return null;
        }
        int stepX = Integer.compare(waypoint.getX(), playerFootPos.getX());
        int stepZ = Integer.compare(waypoint.getZ(), playerFootPos.getZ());
        if (stepX == 0 || stepZ == 0) {
            return null;
        }
        if (Math.abs(waypoint.getY() - playerFootPos.getY()) > 1) {
            return null;
        }
        BlockPos xFront = new BlockPos(playerFootPos.getX() + stepX, playerFootPos.getY(), playerFootPos.getZ());
        BlockPos zFront = new BlockPos(playerFootPos.getX(), playerFootPos.getY(), playerFootPos.getZ() + stepZ);
        boolean xBlocked = isMovementObstructed(world, xFront);
        boolean zBlocked = isMovementObstructed(world, zFront);
        if (xBlocked == zBlocked) {
            return null;
        }
        BlockPos approach = xBlocked ? zFront : xFront;
        if (!canOccupy(world, approach) || !canOccupy(world, approach.above())) {
            return null;
        }
        return new Vec3(approach.getX() + 0.5D, playerY, approach.getZ() + 0.5D);
    }

    BlockPos resolveMinedAscentAdvanceBlock(BlockPos from, BlockPos waypoint) {
        if (from == null || waypoint == null) {
            return null;
        }
        int stepX = Integer.compare(waypoint.getX(), from.getX());
        int stepZ = Integer.compare(waypoint.getZ(), from.getZ());
        if (stepX == 0 && stepZ == 0) {
            return null;
        }
        return new BlockPos(from.getX() + stepX, from.getY(), from.getZ() + stepZ);
    }

    boolean canAttemptMiningAdvanceJump(Level world, BlockPos from, BlockPos waypoint) {
        if (world == null || from == null || waypoint == null) {
            return false;
        }

        int stepX = Integer.compare(waypoint.getX(), from.getX());
        int stepZ = Integer.compare(waypoint.getZ(), from.getZ());
        BlockPos front = new BlockPos(from.getX() + stepX, from.getY(), from.getZ() + stepZ);
        return NavigatorGeometry.hasMinedAscentJumpClearance(
            canOccupy(world, from.above(2)),
            canOccupy(world, front.above()),
            canOccupy(world, front.above(2)),
            canOccupy(world, waypoint.above())
        );
    }

    boolean isCorneredJump(Level world, BlockPos from, int stepX, int stepZ) {
        if (world == null || from == null || (stepX == 0 && stepZ == 0)) {
            return false;
        }
        BlockPos left = new BlockPos(from.getX() - stepZ, from.getY(), from.getZ() + stepX);
        BlockPos right = new BlockPos(from.getX() + stepZ, from.getY(), from.getZ() - stepX);
        boolean leftBlocked = hasCollision(world, left) && hasCollision(world, left.above());
        boolean rightBlocked = hasCollision(world, right) && hasCollision(world, right.above());
        return leftBlocked && rightBlocked;
    }

    double terrainPenalty(Level world, BlockPos from, BlockPos to) {
        double penalty = 0.0D;
        if (isWaterNode(world, to)) {
            penalty += WATER_PENALTY;
            if (host.waterMode() == PathmindNavigator.WaterMode.AVOID) {
                penalty += WATER_AVOIDANCE_PENALTY;
            }
            if (!isStillWater(world, to)) {
                penalty += FLOWING_WATER_PENALTY;
            }
            if (waterDepth(world, to) >= 2) {
                penalty += DEEP_WATER_PENALTY;
            }
            if (isDangerousWater(world, to)) {
                penalty += WATER_DANGER_PENALTY;
            }
            if (!hasSafeWaterExit(world, to)) {
                penalty += WATER_NO_EXIT_PENALTY;
            }
        }
        if (!hasCollision(world, to.below()) && !isWaterNode(world, to)) {
            penalty += EDGE_PENALTY;
        }
        if (isNearDanger(world, to)) {
            penalty += DANGER_PENALTY;
        }
        if (from != null && isWaterNode(world, from) && !isWaterNode(world, to)) {
            penalty += 0.5D;
        }
        if (isTreeCanopyNode(world, to)) {
            penalty += TREE_CANOPY_PENALTY;
        }
        return penalty;
    }

    boolean isWaterNode(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        BlockPos key = pos.immutable();
        if (cache != null) {
            Boolean cached = cache.waterNodes.get(key);
            if (cached != null) {
                return cached;
            }
        }
        boolean result = isWater(cachedFluidState(world, pos)) || isWater(cachedFluidState(world, pos.above()));
        if (cache != null) {
            cache.waterNodes.put(key, result);
        }
        return result;
    }

    boolean isStillWater(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        FluidState fluid = cachedFluidState(world, pos);
        FluidState fluidAbove = cachedFluidState(world, pos.above());
        return (isWater(fluid) && fluid.isSource()) || (isWater(fluidAbove) && fluidAbove.isSource());
    }

    int waterDepth(Level world, BlockPos pos) {
        if (world == null || pos == null || !isWaterNode(world, pos)) {
            return 0;
        }
        int depth = 0;
        BlockPos cursor = pos;
        while (depth < 4 && isWaterNode(world, cursor)) {
            depth++;
            cursor = cursor.below();
        }
        return depth;
    }

    boolean hasSafeWaterExit(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        for (Move move : MOVES) {
            BlockPos candidate = new BlockPos(pos.getX() + move.dx(), pos.getY(), pos.getZ() + move.dz());
            if (isChunkLoaded(world, candidate) && !isWaterNode(world, candidate) && isStandable(world, candidate) && !isHardDanger(world, candidate)) {
                return true;
            }
        }
        return false;
    }

    boolean isDangerousWater(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockState state = cachedBlockState(world, pos);
        BlockState below = cachedBlockState(world, pos.below());
        if (state.is(Blocks.BUBBLE_COLUMN) || below.is(Blocks.BUBBLE_COLUMN)) {
            return true;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos adjacent = pos.offset(dx, 0, dz);
                if (isLava(cachedFluidState(world, adjacent)) || isLava(cachedFluidState(world, adjacent.above()))) {
                    return true;
                }
                if (isDangerousBlock(cachedBlockState(world, adjacent)) || isDangerousBlock(cachedBlockState(world, adjacent.below()))) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean isWater(FluidState fluidState) {
        return fluidState != null && fluidState.is(Fluids.WATER);
    }

    boolean isLava(FluidState fluidState) {
        return fluidState != null && fluidState.is(Fluids.LAVA);
    }

    boolean isHardDanger(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        BlockPos key = pos.immutable();
        if (cache != null) {
            Boolean cached = cache.hardDanger.get(key);
            if (cached != null) {
                return cached;
            }
        }
        boolean result = isDangerousBlock(cachedBlockState(world, pos))
            || isDangerousBlock(cachedBlockState(world, pos.below()))
            || isLava(cachedFluidState(world, pos))
            || isLava(cachedFluidState(world, pos.above()));
        if (cache != null) {
            cache.hardDanger.put(key, result);
        }
        return result;
    }

    boolean isNearDanger(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        BlockPos key = pos.immutable();
        if (cache != null) {
            Boolean cached = cache.nearDanger.get(key);
            if (cached != null) {
                return cached;
            }
        }
        boolean result = computeNearDanger(world, pos);
        if (cache != null) {
            cache.nearDanger.put(key, result);
        }
        return result;
    }

    boolean computeNearDanger(Level world, BlockPos pos) {
        if (isHardDanger(world, pos)) {
            return true;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos adjacent = pos.offset(dx, 0, dz);
                if (isHardDanger(world, adjacent)) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean isDangerousBlock(BlockState state) {
        if (state == null) {
            return false;
        }
        return state.is(Blocks.LAVA)
            || state.is(Blocks.FIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.CACTUS)
            || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.SOUL_CAMPFIRE)
            || state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.SWEET_BERRY_BUSH)
            || state.is(Blocks.WITHER_ROSE)
            || state.is(Blocks.POWDER_SNOW);
    }

    boolean isBreakableForNavigator(Level world, BlockPos pos) {
        if (!host.allowBlockBreaking() || world == null || pos == null) {
            return false;
        }
        BlockState state = cachedBlockState(world, pos);
        if (state == null || state.isAir() || state.canBeReplaced() || isPathOpenable(state) || isClimbableBlock(state)) {
            return false;
        }
        if (state.is(Blocks.BEDROCK)
            || state.is(Blocks.BARRIER)
            || state.is(Blocks.COMMAND_BLOCK)
            || state.is(Blocks.CHAIN_COMMAND_BLOCK)
            || state.is(Blocks.REPEATING_COMMAND_BLOCK)
            || state.is(Blocks.STRUCTURE_BLOCK)
            || state.is(Blocks.STRUCTURE_VOID)
            || state.is(Blocks.JIGSAW)
            || state.is(Blocks.END_PORTAL_FRAME)
            || state.is(Blocks.END_PORTAL)
            || state.is(Blocks.NETHER_PORTAL)) {
            return false;
        }
        return state.getDestroySpeed(world, pos) >= 0.0F;
    }

    double breakPenalty(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return BREAK_MOVE_PENALTY;
        }
        BlockState state = cachedBlockState(world, pos);
        if (state == null || state.isAir()) {
            return 0.0D;
        }
        float hardness = state.getDestroySpeed(world, pos);
        if (hardness < 0.0F) {
            return Double.POSITIVE_INFINITY;
        }
        return BREAK_MOVE_PENALTY + Math.max(0.0D, hardness * 1.2D);
    }

    boolean canPlaceSupportAt(Level world, BlockPos pos) {
        return canPlaceSupportAt(world, pos, false);
    }

    boolean canPlaceSupportAt(Level world, BlockPos pos, boolean allowOccupied) {
        if (!host.allowBlockPlacing() || world == null || pos == null) {
            return false;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        NavigatorNodeFitKey key = cache == null ? null : new NavigatorNodeFitKey(pos.immutable(), allowOccupied);
        if (cache != null) {
            Boolean cached = cache.supportPlacement.get(key);
            if (cached != null) {
                return cached;
            }
        }
        boolean result = computeCanPlaceSupportAt(world, pos, allowOccupied);
        if (cache != null) {
            cache.supportPlacement.put(key, result);
        }
        return result;
    }

    boolean computeCanPlaceSupportAt(Level world, BlockPos pos, boolean allowOccupied) {
        if (!allowOccupied && !canOccupy(world, pos)) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.relative(direction);
            if (adjacent.equals(pos.above())) {
                continue;
            }
            if (hasCollision(world, adjacent)) {
                return true;
            }
        }
        return false;
    }

    boolean isClimbableNode(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return isClimbableBlock(cachedBlockState(world, pos));
    }

    BlockPos resolveClimbAnchor(Level world, BlockPos playerFootPos, BlockPos waypoint) {
        if (world == null) {
            return null;
        }
        if (isClimbableNode(world, playerFootPos)) {
            return playerFootPos;
        }
        if (playerFootPos != null && isClimbableNode(world, playerFootPos.above())) {
            return playerFootPos.above();
        }
        if (waypoint != null && isClimbableNode(world, waypoint)) {
            return waypoint;
        }
        if (waypoint != null && isClimbableNode(world, waypoint.below())) {
            return waypoint.below();
        }
        return null;
    }

    boolean isClimbableBlock(BlockState state) {
        return state != null && state.is(BlockTags.CLIMBABLE);
    }

    boolean isPathOpenable(BlockState state) {
        return state != null
            && (state.is(BlockTags.DOORS)
            || state.is(BlockTags.TRAPDOORS)
            || state.is(BlockTags.FENCE_GATES));
    }

    boolean isClimbTransition(Level world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null) {
            return false;
        }
        return canPlayerFitAtNode(world, to, false)
            && (isClimbableNode(world, from)
            || isClimbableNode(world, to)
            || isClimbableNode(world, from.above())
            || isClimbableNode(world, to.above())
            || isClimbableNode(world, to.below()));
    }

    boolean canSafelyDropTo(Level world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null || to.getY() >= from.getY()) {
            return false;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        NavigatorMovementQueryKey key = cache == null ? null : new NavigatorMovementQueryKey(from.immutable(), to.immutable());
        if (cache != null) {
            Boolean cached = cache.safeDrops.get(key);
            if (cached != null) {
                return cached;
            }
        }
        boolean result = computeCanSafelyDropTo(world, from, to);
        if (cache != null) {
            cache.safeDrops.put(key, result);
        }
        return result;
    }

    boolean computeCanSafelyDropTo(Level world, BlockPos from, BlockPos to) {
        if (from.getY() - to.getY() > MAX_SAFE_FALL_DISTANCE) {
            return false;
        }
        int horizontalDx = Math.abs(to.getX() - from.getX());
        int horizontalDz = Math.abs(to.getZ() - from.getZ());
        if (horizontalDx > 1 || horizontalDz > 1 || horizontalDx + horizontalDz > 1) {
            return false;
        }
        if (!isNavigableNode(world, to)) {
            return false;
        }
        AABB sourceBody = playerBodyAtNode(world, from, false);
        AABB destinationBody = playerBodyAtNode(world, to, false);
        if (sourceBody == null || destinationBody == null) {
            return false;
        }
        double sourceFeetY = sourceBody.minY - COLLISION_EPSILON;
        double destinationFeetY = destinationBody.minY - COLLISION_EPSILON;
        double dropDistance = sourceFeetY - destinationFeetY;
        if (dropDistance < -COLLISION_EPSILON || dropDistance > MAX_SAFE_FALL_DISTANCE + 0.75D) {
            return false;
        }
        int samples = Math.max(2, (int) Math.ceil(dropDistance * 2.0D));
        for (int sample = 1; sample <= samples; sample++) {
            double progress = sample / (double) samples;
            double centerX = Mth.lerp(progress, from.getX() + 0.5D, to.getX() + 0.5D);
            double centerZ = Mth.lerp(progress, from.getZ() + 0.5D, to.getZ() + 0.5D);
            double feetY = Mth.lerp(progress, sourceFeetY, destinationFeetY);
            if (hasTraversalCollision(world, playerBodyAt(centerX, feetY, centerZ))) {
                return false;
            }
        }
        return true;
    }

    boolean requiresInteractableTraversal(Level world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null) {
            return false;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        NavigatorMovementQueryKey key = cache == null ? null : new NavigatorMovementQueryKey(from.immutable(), to.immutable());
        if (cache != null) {
            Boolean cached = cache.interactableTraversal.get(key);
            if (cached != null) {
                return cached;
            }
        }
        boolean result = computeRequiresInteractableTraversal(world, from, to);
        if (cache != null) {
            cache.interactableTraversal.put(key, result);
        }
        return result;
    }

    boolean computeRequiresInteractableTraversal(Level world, BlockPos from, BlockPos to) {
        int stepX = Integer.compare(to.getX(), from.getX());
        int stepZ = Integer.compare(to.getZ(), from.getZ());
        if (stepX == 0 && stepZ == 0) {
            return false;
        }
        return isBlockingInteractableForTraversal(world, new BlockPos(from.getX() + stepX, from.getY(), from.getZ() + stepZ), from, to)
            || isBlockingInteractableForTraversal(world, new BlockPos(from.getX() + stepX, from.getY() + 1, from.getZ() + stepZ), from, to)
            || isBlockingInteractableForTraversal(world, to, from, to)
            || isBlockingInteractableForTraversal(world, to.above(), from, to);
    }

    boolean hasPathOpenableAhead(Level world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null) {
            return false;
        }
        NavigatorPlanningCache cache = planningCacheFor(world);
        NavigatorMovementQueryKey key = cache == null ? null : new NavigatorMovementQueryKey(from.immutable(), to.immutable());
        if (cache != null) {
            Boolean cached = cache.pathOpenableAhead.get(key);
            if (cached != null) {
                return cached;
            }
        }
        boolean result = computePathOpenableAhead(world, from, to);
        if (cache != null) {
            cache.pathOpenableAhead.put(key, result);
        }
        return result;
    }

    boolean computePathOpenableAhead(Level world, BlockPos from, BlockPos to) {
        int stepX = Integer.compare(to.getX(), from.getX());
        int stepZ = Integer.compare(to.getZ(), from.getZ());
        if (stepX == 0 && stepZ == 0) {
            return false;
        }
        BlockPos front = new BlockPos(from.getX() + stepX, from.getY(), from.getZ() + stepZ);
        return isBlockingInteractableForTraversal(world, front, from, to)
            || isBlockingInteractableForTraversal(world, front.above(), from, to);
    }

    boolean isBlockingInteractable(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockState state = cachedBlockState(world, pos);
        if (!isPathOpenable(state)) {
            return false;
        }
        return state.hasProperty(BlockStateProperties.OPEN) && !state.getValue(BlockStateProperties.OPEN);
    }

    boolean isBlockingInteractableForTraversal(Level world, BlockPos pos, BlockPos from, BlockPos to) {
        if (!isBlockingInteractable(world, pos) || from == null || to == null) {
            return false;
        }
        BlockState state = cachedBlockState(world, pos);
        VoxelShape closedShape = cachedCollisionShape(world, pos, state);
        AABB sourceBody = playerBodyAtNode(world, from, false);
        AABB destinationBody = playerBodyAtNode(world, to, false);
        if (sourceBody == null || destinationBody == null) {
            return true;
        }
        List<AABB> probes = new ArrayList<>(5);
        probes.add(sourceBody);
        probes.add(destinationBody);
        double sourceFeetY = sourceBody.minY - COLLISION_EPSILON;
        double destinationFeetY = destinationBody.minY - COLLISION_EPSILON;
        for (int sample = 1; sample <= 3; sample++) {
            double progress = sample / 4.0D;
            probes.add(playerBodyAt(
                Mth.lerp(progress, from.getX() + 0.5D, to.getX() + 0.5D),
                Mth.lerp(progress, sourceFeetY, destinationFeetY),
                Mth.lerp(progress, from.getZ() + 0.5D, to.getZ() + 0.5D)
            ));
        }
        for (AABB localBox : closedShape.toAabbs()) {
            AABB worldBox = localBox.move(pos.getX(), pos.getY(), pos.getZ());
            for (AABB probe : probes) {
                if (intersectsStrictly(probe, worldBox)) {
                    return true;
                }
            }
        }
        return false;
    }

}
