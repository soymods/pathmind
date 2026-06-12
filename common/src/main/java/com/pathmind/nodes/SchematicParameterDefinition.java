package com.pathmind.nodes;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class SchematicParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_SCHEMATIC)
            .runtimeBehavior(SchematicParameterDefinition::resolvePositionTarget)
            .build();
    }

    private static Optional<Vec3d> resolvePositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                         CompletableFuture<Void> future) {
        BlockPos pos = CoordinateParameterDefinition.resolveBlockPosition(parameterNode);
        if (data != null) {
            data.targetBlockPos = pos;
            data.schematicName = Node.getParameterString(parameterNode, "Schematic");
        }
        return Optional.of(Vec3d.ofCenter(pos));
    }

    private SchematicParameterDefinition() {
    }
}
