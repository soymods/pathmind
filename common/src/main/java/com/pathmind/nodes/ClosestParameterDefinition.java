package com.pathmind.nodes;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class ClosestParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_CLOSEST)
            .parameterBehavior((node, values) -> NodeBehaviorDefinitionSupport.copyIfPresent(values, "Range", "Distance"))
            .runtimeBehavior(ClosestParameterDefinition::resolvePositionTarget)
            .build();
    }

    private static Optional<Vec3d> resolvePositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                         CompletableFuture<Void> future) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return Optional.empty();
        }
        int range = Math.max(1, Node.parseNodeInt(parameterNode, "Range", 5));
        Optional<BlockPos> open = owner.findNearestOpenBlock(client, range);
        if (open.isEmpty()) {
            owner.sendParameterSearchFailure(NodeBehaviorDefinitionSupport.noOpenBlockMessage(owner), future);
            return Optional.empty();
        }
        if (data != null) {
            data.targetBlockPos = open.get();
        }
        return Optional.of(Vec3d.ofCenter(open.get()));
    }

    private ClosestParameterDefinition() {
    }
}
