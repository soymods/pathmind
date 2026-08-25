package com.pathmind.schematic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded 3D A* used by the creative schematic builder's flight controller. */
final class SchematicFlightPlanner {
    private static final int MAX_NODES = 12_000;

    private SchematicFlightPlanner() {
    }

    static List<BlockPos> findPath(Level world, BlockPos start, BlockPos goal) {
        if (world == null || start == null || goal == null || !isFlyable(world, goal)) {
            return List.of();
        }
        int directDistance = Math.abs(goal.getX() - start.getX())
            + Math.abs(goal.getY() - start.getY())
            + Math.abs(goal.getZ() - start.getZ());
        // A takeoff/cruise/descent pattern makes nearby interactions look
        // wildly indirect (one block sideways becomes three blocks upward,
        // over, then back down). Prefer the collision-aware direct route for
        // short legs; only use a cruise lane when a local obstacle actually
        // makes that necessary.
        if (directDistance <= 5) {
            List<BlockPos> direct = findSegment(world, start, goal);
            if (!direct.isEmpty()) {
                return direct;
            }
        }
        // Do not skim terrain just because the destination is near ground
        // level. A creative flight leg always takes off first, cruises above
        // local obstacles, then descends into interaction range.
        int cruiseY = Math.min(world.getMinY() + world.getHeight() - 3, Math.max(start.getY(), goal.getY()) + 3);
        BlockPos departure = new BlockPos(start.getX(), cruiseY, start.getZ());
        BlockPos arrival = new BlockPos(goal.getX(), cruiseY, goal.getZ());
        List<BlockPos> ascent = findSegment(world, start, departure);
        List<BlockPos> cruise = ascent.isEmpty() ? List.of() : findSegment(world, departure, arrival);
        List<BlockPos> descent = cruise.isEmpty() ? List.of() : findSegment(world, arrival, goal);
        if (!ascent.isEmpty() && !cruise.isEmpty() && !descent.isEmpty()) {
            List<BlockPos> route = new ArrayList<>(ascent.size() + cruise.size() + descent.size());
            appendDistinct(route, ascent);
            appendDistinct(route, cruise);
            appendDistinct(route, descent);
            return simplify(route);
        }
        // Tight interiors may not have room for a cruise lane. Retain the
        // collision-aware direct route rather than failing a valid build.
        return findSegment(world, start, goal);
    }

    private static List<BlockPos> findSegment(Level world, BlockPos start, BlockPos goal) {
        int horizontalDistance = Math.abs(goal.getX() - start.getX()) + Math.abs(goal.getZ() - start.getZ());
        int radius = Math.min(48, Math.max(12, horizontalDistance + 8));
        int minX = Math.min(start.getX(), goal.getX()) - radius;
        int maxX = Math.max(start.getX(), goal.getX()) + radius;
        int minY = Math.max(world.getMinY(), Math.min(start.getY(), goal.getY()) - 8);
        int maxY = Math.min(world.getMinY() + world.getHeight() - 3, Math.max(start.getY(), goal.getY()) + 16);
        int minZ = Math.min(start.getZ(), goal.getZ()) - radius;
        int maxZ = Math.max(start.getZ(), goal.getZ()) + radius;

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Double> gScore = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        BlockPos immutableStart = start.immutable();
        open.add(new Node(immutableStart, 0.0D, heuristic(immutableStart, goal)));
        gScore.put(immutableStart, 0.0D);

        int visited = 0;
        while (!open.isEmpty() && visited++ < MAX_NODES) {
            Node node = open.poll();
            BlockPos current = node.position();
            if (!closed.add(current)) {
                continue;
            }
            if (current.equals(goal)) {
                return simplify(reconstruct(cameFrom, current));
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (next.getX() < minX || next.getX() > maxX || next.getY() < minY || next.getY() > maxY
                    || next.getZ() < minZ || next.getZ() > maxZ || closed.contains(next) || !isFlyable(world, next)) {
                    continue;
                }
                double nextCost = node.cost() + (direction.getAxis().isVertical() ? 1.12D : 1.0D);
                if (nextCost >= gScore.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    continue;
                }
                BlockPos immutableNext = next.immutable();
                cameFrom.put(immutableNext, current);
                gScore.put(immutableNext, nextCost);
                open.add(new Node(immutableNext, nextCost, nextCost + heuristic(immutableNext, goal)));
            }
        }
        return List.of();
    }

    private static void appendDistinct(List<BlockPos> output, List<BlockPos> segment) {
        for (BlockPos position : segment) {
            if (output.isEmpty() || !output.get(output.size() - 1).equals(position)) {
                output.add(position);
            }
        }
    }

    static boolean isFlyable(Level world, BlockPos feet) {
        if (world == null || feet == null || !world.hasChunkAt(feet) || !world.hasChunkAt(feet.above())
            || !world.hasChunkAt(feet.above(2))) {
            return false;
        }
        BlockState feetState = world.getBlockState(feet);
        BlockState headState = world.getBlockState(feet.above());
        BlockState clearanceState = world.getBlockState(feet.above(2));
        // A flight cell needs a full block of extra headroom. The player's
        // actual Y position can float within the feet cell; two nominally
        // empty cells alone still let the head graze the block above during
        // an ascent or a route turn.
        return feetState.getFluidState().isEmpty() && headState.getFluidState().isEmpty()
            && clearanceState.getFluidState().isEmpty()
            && !hasCollision(feetState) && !hasCollision(headState) && !hasCollision(clearanceState);
    }

    private static boolean hasCollision(BlockState state) {
        return state != null && !state.isAir()
            && !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    private static double heuristic(BlockPos from, BlockPos to) {
        return Math.abs(from.getX() - to.getX()) + Math.abs(from.getY() - to.getY()) * 1.12D + Math.abs(from.getZ() - to.getZ());
    }

    private static List<BlockPos> reconstruct(Map<BlockPos, BlockPos> cameFrom, BlockPos end) {
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos cursor = end; cursor != null; cursor = cameFrom.get(cursor)) {
            path.add(cursor);
        }
        Collections.reverse(path);
        return path;
    }

    private static List<BlockPos> simplify(List<BlockPos> raw) {
        if (raw.size() < 3) {
            return raw;
        }
        List<BlockPos> simplified = new ArrayList<>();
        simplified.add(raw.get(0));
        Direction previousDirection = direction(raw.get(0), raw.get(1));
        for (int index = 1; index < raw.size() - 1; index++) {
            Direction nextDirection = direction(raw.get(index), raw.get(index + 1));
            if (nextDirection != previousDirection) {
                simplified.add(raw.get(index));
                previousDirection = nextDirection;
            }
        }
        simplified.add(raw.get(raw.size() - 1));
        return List.copyOf(simplified);
    }

    private static Direction direction(BlockPos from, BlockPos to) {
        for (Direction direction : Direction.values()) {
            if (from.relative(direction).equals(to)) {
                return direction;
            }
        }
        return Direction.UP;
    }

    private record Node(BlockPos position, double cost, double score) {
    }
}
