package com.pathmind.execution;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

final class NavigatorPlanningCache {
    final Level world;
    final Map<BlockPos, BlockState> blockStates = new HashMap<>();
    final Map<BlockPos, FluidState> fluidStates = new HashMap<>();
    final Map<BlockPos, VoxelShape> collisionShapes = new HashMap<>();
    final Map<BlockPos, OptionalDouble> supportSurfaces = new HashMap<>();
    final Map<BlockPos, Boolean> navigableNodes = new HashMap<>();
    final Map<BlockPos, Boolean> standableNodes = new HashMap<>();
    final Map<BlockPos, Boolean> waterNodes = new HashMap<>();
    final Map<BlockPos, Boolean> hardDanger = new HashMap<>();
    final Map<BlockPos, Boolean> nearDanger = new HashMap<>();
    final Map<BlockPos, Boolean> treeCanopyNodes = new HashMap<>();
    final Map<BlockPos, Boolean> hasCollision = new HashMap<>();
    final Map<BlockPos, Boolean> occupiable = new HashMap<>();
    final Map<NavigatorNodeFitKey, Boolean> nodeFit = new HashMap<>();
    final Map<NavigatorNodeFitKey, Boolean> supportPlacement = new HashMap<>();
    final Map<NavigatorBodyKey, Boolean> bodyCollisions = new HashMap<>();
    final Map<Long, Boolean> loadedChunks = new HashMap<>();
    final Map<NavigatorMovementQueryKey, NavigatorBreakTargetCacheEntry> breakTargets = new HashMap<>();
    final Map<NavigatorMovementQueryKey, Boolean> interactableTraversal = new HashMap<>();
    final Map<NavigatorMovementQueryKey, Boolean> pathOpenableAhead = new HashMap<>();
    final Map<NavigatorMovementQueryKey, Boolean> stepJumps = new HashMap<>();
    final Map<NavigatorMovementQueryKey, Boolean> jumpAttempts = new HashMap<>();
    final Map<NavigatorMovementQueryKey, Boolean> safeDrops = new HashMap<>();
    int blockStateHits;
    int collisionShapeHits;
    int expandedNodes;
    int movementEvaluations;
    int cleanSearches;
    int modifiedSearches;
    boolean allowWorldModification = true;

    NavigatorPlanningCache(Level world) {
        this.world = world;
    }
}

record NavigatorNodeFitKey(BlockPos pos, boolean requireSupport) {
}

record NavigatorBodyKey(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    static NavigatorBodyKey of(AABB body) {
        return new NavigatorBodyKey(body.minX, body.minY, body.minZ, body.maxX, body.maxY, body.maxZ);
    }
}

record NavigatorMovementQueryKey(BlockPos from, BlockPos to) {
}

record NavigatorBreakTargetCacheEntry(boolean valid, List<BlockPos> targets) {
}
