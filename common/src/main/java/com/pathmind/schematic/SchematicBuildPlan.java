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

    /** A desired non-air block, relative to the schematic's declared origin. */
    public record Placement(BlockPos relativePosition, BlockState state, String stateId) {
    }
}
