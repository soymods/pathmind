package com.pathmind.nodes;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class ClosestParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_CLOSEST)
            .parameterBehavior((node, values) -> NodeBehaviorDefinitionSupport.copyIfPresent(values, "Range", "Distance"))
            .runtimeBehavior(ClosestParameterDefinition::resolvePositionTarget)
            .build();
    }

    private static Optional<Vec3> resolvePositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                         CompletableFuture<Void> future) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
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
        return Optional.of(Vec3.atCenterOf(open.get()));
    }

    private ClosestParameterDefinition() {
    }
}
