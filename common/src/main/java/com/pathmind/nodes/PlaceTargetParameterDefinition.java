package com.pathmind.nodes;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class PlaceTargetParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_PLACE_TARGET)
            .parameterBehavior((node, values) -> NodeBehaviorDefinitionSupport.copyIfPresent(values, "Block", "BlockId"))
            .runtimeBehavior(PlaceTargetParameterDefinition::resolvePositionTarget)
            .build();
    }

    private static Optional<Vec3d> resolvePositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                         CompletableFuture<Void> future) {
        BlockPos pos = CoordinateParameterDefinition.resolveBlockPosition(parameterNode);
        if (data != null) {
            data.targetBlockPos = pos;
            data.targetBlockId = owner.getBlockParameterValue(parameterNode);
        }
        return Optional.of(Vec3d.ofCenter(pos));
    }

    private PlaceTargetParameterDefinition() {
    }
}
