package com.pathmind.schematic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

/**
 * Reconciles a loaded schematic with the current world without changing either.
 * The executor in the next pass consumes the resulting steps and asks native
 * navigation to visit one of each step's safe standing candidates.
 */
public final class SchematicPlacementPlanner {
    private static final double INTERACTION_RANGE_SQ = 4.50D * 4.50D;
    private static final int MAX_APPROACHES_PER_STEP = 16;
    private static final Direction[] SUPPORT_ORDER = {
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP
    };

    private SchematicPlacementPlanner() {
    }

    public static ConstructionPlan plan(Level world, SchematicBuildPlan schematic, BlockPos origin, BlockPos playerFeet) {
        return plan(world, schematic, origin, playerFeet, ConflictPolicy.KEEP_EXISTING);
    }

    /**
     * Reconciles the schematic against the world using an explicit conflict
     * policy.  The default never destroys a world block: callers must opt into
     * state repair or destructive rebuilding deliberately.
     */
    public static ConstructionPlan plan(
        Level world, SchematicBuildPlan schematic, BlockPos origin, BlockPos playerFeet, ConflictPolicy conflictPolicy
    ) {
        if (world == null) {
            throw new IllegalArgumentException("A loaded world is required to plan a schematic build.");
        }
        if (schematic == null || origin == null) {
            throw new IllegalArgumentException("A schematic and origin are required to plan a build.");
        }

        Map<BlockPos, SchematicBuildPlan.Placement> desiredByWorldPosition = new HashMap<>();
        for (SchematicBuildPlan.Placement placement : schematic.placements()) {
            desiredByWorldPosition.put(toWorldPosition(origin, schematic.placementAnchor(), placement.relativePosition()), placement);
        }

        ConflictPolicy policy = conflictPolicy == null ? ConflictPolicy.KEEP_EXISTING : conflictPolicy;
        List<ConstructionStep> steps = new ArrayList<>();
        int skipped = 0;
        int placements = 0;
        int replacements = 0;
        int blocked = 0;
        int conflicts = 0;
        for (SchematicBuildPlan.Placement placement : schematic.placements()) {
            BlockPos target = toWorldPosition(origin, schematic.placementAnchor(), placement.relativePosition());
            if (!world.hasChunkAt(target)) {
                steps.add(new ConstructionStep(placement, target, StepAction.BLOCKED, List.of(), List.of(),
                    "Target chunk is not loaded."));
                blocked++;
                continue;
            }
            BlockState current = world.getBlockState(target);
            if (current.equals(placement.state())) {
                steps.add(new ConstructionStep(placement, target, StepAction.SKIP, List.of(), List.of(), null));
                skipped++;
                continue;
            }

            List<BlockPos> dependencies = plannedSupportDependencies(desiredByWorldPosition, target);
            List<PlacementApproach> approaches = findApproaches(world, target, desiredByWorldPosition, playerFeet);
            if (approaches.isEmpty()) {
                steps.add(new ConstructionStep(placement, target, StepAction.BLOCKED, dependencies, List.of(),
                    "No solid neighboring face has a safe placement position; scaffolding or an earlier support is required."));
                blocked++;
                continue;
            }
            StepAction action;
            if (current.isAir() || isWaterloggedPlacementIntoFluid(current, placement.state())) {
                action = StepAction.PLACE;
            } else if (policy == ConflictPolicy.KEEP_EXISTING) {
                steps.add(new ConstructionStep(placement, target, StepAction.CONFLICT, dependencies, approaches,
                    "Existing " + blockId(current) + " is preserved by the current conflict policy."));
                conflicts++;
                continue;
            } else if (policy == ConflictPolicy.REPLACE_MATCHING_BLOCK && current.getBlock() != placement.state().getBlock()) {
                steps.add(new ConstructionStep(placement, target, StepAction.CONFLICT, dependencies, approaches,
                    "Existing " + blockId(current) + " differs from " + placement.stateId() + "; only matching block states may be repaired."));
                conflicts++;
                continue;
            } else if (isUnsafeReplacement(world, target, current)) {
                steps.add(new ConstructionStep(placement, target, StepAction.CONFLICT, dependencies, approaches,
                    "Existing " + blockId(current) + " is protected or contains data and will not be replaced automatically."));
                conflicts++;
                continue;
            } else {
                action = StepAction.REPLACE;
            }
            steps.add(new ConstructionStep(placement, target, action, dependencies, approaches, null));
            if (action == StepAction.PLACE) {
                placements++;
            } else {
                replacements++;
            }
        }
        return new ConstructionPlan(schematic, origin.immutable(), List.copyOf(steps), skipped, placements, replacements, blocked, conflicts);
    }

    /** Finds air-borne, in-range placement positions for a creative-mode player. */
    public static List<PlacementApproach> findCreativeFlightApproaches(
        Level world, ConstructionStep step, BlockPos playerFeet
    ) {
        if (world == null || step == null) {
            return List.of();
        }
        List<PlacementApproach> approaches = new ArrayList<>();
        BlockPos target = step.worldPosition();
        for (Direction direction : SUPPORT_ORDER) {
            BlockPos support = target.relative(direction);
            if (!hasCollisionShape(world.getBlockState(support))) {
                continue;
            }
            Direction face = direction.getOpposite();
            Vec3 hit = Vec3.atCenterOf(support).add(face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D);
            for (int x = target.getX() - 4; x <= target.getX() + 4; x++) {
                for (int y = target.getY() - 3; y <= target.getY() + 3; y++) {
                    for (int z = target.getZ() - 4; z <= target.getZ() + 4; z++) {
                        BlockPos hover = new BlockPos(x, y, z);
                        if (!SchematicFlightPlanner.isFlyable(world, hover)) {
                            continue;
                        }
                        Vec3 eye = new Vec3(x + 0.5D, y + 1.62D, z + 0.5D);
                        double interactionDistanceSq = eye.distanceToSqr(hit);
                        if (interactionDistanceSq > INTERACTION_RANGE_SQ) {
                            continue;
                        }
                        double playerDistanceSq = playerFeet == null ? 0.0D : hover.distSqr(playerFeet);
                        approaches.add(new PlacementApproach(hover.immutable(), support.immutable(), face, hit,
                            playerDistanceSq, interactionDistanceSq));
                    }
                }
            }
        }
        approaches.sort(Comparator.comparingDouble(PlacementApproach::playerDistanceSq)
            .thenComparingDouble(PlacementApproach::interactionDistanceSq));
        return approaches.size() > MAX_APPROACHES_PER_STEP
            ? List.copyOf(approaches.subList(0, MAX_APPROACHES_PER_STEP)) : List.copyOf(approaches);
    }

    /** Finds normal walking placement approaches for a temporary support block. */
    public static List<PlacementApproach> findLiveApproaches(Level world, BlockPos target, BlockPos playerFeet) {
        return world == null || target == null ? List.of() : findApproaches(world, target, Map.of(), playerFeet);
    }

    private static BlockPos toWorldPosition(BlockPos origin, BlockPos anchor, BlockPos relative) {
        // Sponge exporters commonly retain the source world's absolute
        // position in Offset. Pathmind instead anchors the occupied build
        // footprint's bottom-centre at the user-selected world position.
        return origin.offset(relative.subtract(anchor)).immutable();
    }

    private static List<BlockPos> plannedSupportDependencies(
        Map<BlockPos, SchematicBuildPlan.Placement> desiredByWorldPosition, BlockPos target
    ) {
        Set<BlockPos> dependencies = new LinkedHashSet<>();
        for (Direction direction : SUPPORT_ORDER) {
            BlockPos neighbor = target.relative(direction);
            SchematicBuildPlan.Placement desired = desiredByWorldPosition.get(neighbor);
            if (desired != null && !desired.state().isAir() && hasCollisionShape(desired.state())) {
                dependencies.add(neighbor.immutable());
            }
        }
        SchematicBuildPlan.Placement desiredAtTarget = desiredByWorldPosition.get(target);
        if (desiredAtTarget != null) {
            String half = propertyValue(desiredAtTarget.state(), "half");
            if ("upper".equals(half)) {
                addMatchingMultipartDependency(desiredByWorldPosition, dependencies, target.below(), desiredAtTarget.state().getBlock());
            }
            String part = propertyValue(desiredAtTarget.state(), "part");
            Direction facing = directionValue(desiredAtTarget.state(), "facing");
            if ("head".equals(part) && facing != null) {
                addMatchingMultipartDependency(desiredByWorldPosition, dependencies, target.relative(facing.getOpposite()), desiredAtTarget.state().getBlock());
            }
        }
        return List.copyOf(dependencies);
    }

    private static void addMatchingMultipartDependency(
        Map<BlockPos, SchematicBuildPlan.Placement> desiredByWorldPosition, Set<BlockPos> dependencies,
        BlockPos candidate, net.minecraft.world.level.block.Block requiredBlock
    ) {
        SchematicBuildPlan.Placement dependency = desiredByWorldPosition.get(candidate);
        if (dependency != null && dependency.state().getBlock() == requiredBlock) {
            dependencies.add(candidate.immutable());
        }
    }

    private static List<PlacementApproach> findApproaches(
        Level world, BlockPos target, Map<BlockPos, SchematicBuildPlan.Placement> desiredByWorldPosition, BlockPos playerFeet
    ) {
        List<PlacementApproach> candidates = new ArrayList<>();
        for (Direction direction : SUPPORT_ORDER) {
            BlockPos support = target.relative(direction);
            if (!isSolidNowOrPlanned(world, support, desiredByWorldPosition)) {
                continue;
            }
            Direction face = direction.getOpposite();
            Vec3 hit = Vec3.atCenterOf(support).add(face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D);
            addStandingCandidates(world, target, support, face, hit, playerFeet, candidates);
        }
        candidates.sort(Comparator
            .comparingDouble((PlacementApproach approach) -> approach.playerDistanceSq())
            .thenComparingDouble(PlacementApproach::interactionDistanceSq)
            .thenComparingInt(approach -> approach.standingPosition().getY()));
        if (candidates.size() > MAX_APPROACHES_PER_STEP) {
            return List.copyOf(candidates.subList(0, MAX_APPROACHES_PER_STEP));
        }
        return List.copyOf(candidates);
    }

    private static void addStandingCandidates(
        Level world, BlockPos target, BlockPos support, Direction face, Vec3 hit, BlockPos playerFeet, List<PlacementApproach> output
    ) {
        for (int x = target.getX() - 4; x <= target.getX() + 4; x++) {
            for (int y = target.getY() - 3; y <= target.getY() + 3; y++) {
                for (int z = target.getZ() - 4; z <= target.getZ() + 4; z++) {
                    BlockPos standing = new BlockPos(x, y, z);
                    if (!isSafeStandingPosition(world, standing)) {
                        continue;
                    }
                    Vec3 eye = new Vec3(x + 0.5D, y + 1.62D, z + 0.5D);
                    double interactionDistanceSq = eye.distanceToSqr(hit);
                    if (interactionDistanceSq > INTERACTION_RANGE_SQ) {
                        continue;
                    }
                    double playerDistanceSq = playerFeet == null ? 0.0D : standing.distSqr(playerFeet);
                    output.add(new PlacementApproach(standing.immutable(), support.immutable(), face, hit,
                        playerDistanceSq, interactionDistanceSq));
                }
            }
        }
    }

    private static boolean isSolidNowOrPlanned(
        Level world, BlockPos position, Map<BlockPos, SchematicBuildPlan.Placement> desiredByWorldPosition
    ) {
        SchematicBuildPlan.Placement desired = desiredByWorldPosition.get(position);
        return desired != null && hasCollisionShape(desired.state())
            || desired == null && hasCollisionShape(world.getBlockState(position));
    }

    private static boolean isSafeStandingPosition(Level world, BlockPos feet) {
        if (!world.hasChunkAt(feet) || !world.hasChunkAt(feet.above()) || !world.hasChunkAt(feet.below())) {
            return false;
        }
        BlockState feetState = world.getBlockState(feet);
        BlockState headState = world.getBlockState(feet.above());
        BlockState belowState = world.getBlockState(feet.below());
        return feetState.getFluidState().isEmpty()
            && headState.getFluidState().isEmpty()
            && !hasCollisionShape(feetState)
            && !hasCollisionShape(headState)
            && hasCollisionShape(belowState);
    }

    private static boolean hasCollisionShape(BlockState state) {
        return state != null && !state.isAir()
            && !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    /** A waterlogged state is placed into an existing fluid cell, not empty air. */
    private static boolean isWaterloggedPlacementIntoFluid(BlockState current, BlockState desired) {
        return "true".equals(propertyValue(desired, "waterlogged")) && !current.getFluidState().isEmpty();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValue(BlockState state, String name) {
        if (state == null || name == null) return null;
        Property property = state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) return null;
        Comparable value = state.getValue(property);
        return value == null ? null : value.toString();
    }

    private static Direction directionValue(BlockState state, String name) {
        String value = propertyValue(state, name);
        return value == null ? null : Direction.byName(value);
    }

    private static boolean isUnsafeReplacement(Level world, BlockPos position, BlockState state) {
        return state.getDestroySpeed(world, position) < 0.0F || world.getBlockEntity(position) != null;
    }

    private static String blockId(BlockState state) {
        return state == null ? "unknown block" : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    public enum StepAction {
        /** The current world already matches the schematic. */
        SKIP,
        /** The target is air and needs a block placed. */
        PLACE,
        /** A different existing block must be removed before placing the desired state. */
        REPLACE,
        /** A world block differs, but the selected policy explicitly preserves it. */
        CONFLICT,
        /** No safe, currently viable placement approach exists yet. */
        BLOCKED
    }

    public record ConstructionPlan(
        SchematicBuildPlan schematic,
        BlockPos origin,
        List<ConstructionStep> steps,
        int skippedCount,
        int placementCount,
        int replacementCount,
        int blockedCount,
        int conflictCount
    ) {
        public ConstructionPlan {
            steps = List.copyOf(steps);
        }

        public int actionableCount() {
            return placementCount + replacementCount;
        }
    }

    public enum ConflictPolicy {
        /** Preserve every existing block. */
        KEEP_EXISTING,
        /** Replace only a wrong block state when the block type already matches. */
        REPLACE_MATCHING_BLOCK,
        /** Explicitly break ordinary conflicting blocks and rebuild them. */
        DESTRUCTIVE_REBUILD
    }

    public record ConstructionStep(
        SchematicBuildPlan.Placement desired,
        BlockPos worldPosition,
        StepAction action,
        List<BlockPos> dependencies,
        List<PlacementApproach> approaches,
        String blockedReason
    ) {
        public ConstructionStep {
            dependencies = List.copyOf(dependencies);
            approaches = List.copyOf(approaches);
        }
    }

    /** A valid feet position plus the exact face the executor will target. */
    public record PlacementApproach(
        BlockPos standingPosition,
        BlockPos supportPosition,
        Direction face,
        Vec3 hitPosition,
        double playerDistanceSq,
        double interactionDistanceSq
    ) {
    }
}
