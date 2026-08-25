package com.pathmind.schematic;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Immutable, world-independent description of a schematic build.  The planner and
 * executors added in later passes consume this instead of parsing files themselves.
 */
public record SchematicBuildPlan(
    Path source,
    Dimensions dimensions,
    BlockPos schematicOffset,
    List<Placement> placements,
    Map<String, Integer> requiredMaterials,
    int ignoredAirBlocks
) {
    public SchematicBuildPlan {
        placements = List.copyOf(placements);
        requiredMaterials = Map.copyOf(requiredMaterials);
    }

    public record Dimensions(int width, int height, int length) {
        public long volume() {
            return (long) width * height * length;
        }
    }

    /**
     * The user-facing placement anchor: centre of the occupied footprint on
     * the schematic's lowest occupied layer. Export dimensions often include
     * empty margins, so anchoring to raw (0,0,0) makes a requested coordinate
     * feel visibly displaced from the structure itself.
     */
    public BlockPos placementAnchor() {
        if (placements.isEmpty()) {
            return BlockPos.ZERO;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Placement placement : placements) {
            BlockPos position = placement.relativePosition();
            minX = Math.min(minX, position.getX());
            maxX = Math.max(maxX, position.getX());
            minY = Math.min(minY, position.getY());
            minZ = Math.min(minZ, position.getZ());
            maxZ = Math.max(maxZ, position.getZ());
        }
        return new BlockPos(minX + (maxX - minX) / 2, minY, minZ + (maxZ - minZ) / 2);
    }

    /** A desired non-air block, relative to the schematic's declared origin. */
    public record Placement(BlockPos relativePosition, BlockState state, String stateId) {
    }
}
