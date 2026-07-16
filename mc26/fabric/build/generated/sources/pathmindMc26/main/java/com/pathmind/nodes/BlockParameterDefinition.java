package com.pathmind.nodes;

import com.pathmind.util.BlockSelection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

final class BlockParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_BLOCK)
            .parameterBehavior(BlockParameterDefinition::exportValues)
            .runtimeBehavior(BlockParameterDefinition::resolvePositionTarget)
            .gotoFallbackTargetBehavior(BlockParameterDefinition::resolveGotoFallbackTarget)
            .build();
    }

    private static java.util.Map<String, String> exportValues(Node node, java.util.Map<String, String> values) {
        NodeBehaviorDefinitionSupport.syncSingularAndPlural(values, "Block", "Blocks");
        return values;
    }

    private static Optional<Vec3> resolvePositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                         CompletableFuture<Void> future) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
            return Optional.empty();
        }
        String rawBlock = Node.getParameterString(parameterNode, "Block");
        List<BlockSelection> blocks = owner.resolveBlocksFromParameter(parameterNode);
        double range = Node.parseNodeDouble(parameterNode, "Range", Node.PARAMETER_SEARCH_RADIUS);
        if (blocks.isEmpty()) {
            if (!Node.isAnySelectionValue(rawBlock)) {
                owner.sendParameterSearchFailure(NodeBehaviorDefinitionSupport.noBlocksDefinedMessage(owner), future);
                return Optional.empty();
            }
            Optional<BlockPos> nearest = owner.findNearestAnyBlock(client, range);
            if (nearest.isEmpty()) {
                owner.sendParameterSearchFailure(NodeBehaviorDefinitionSupport.noNearbyBlockMessage(owner), future);
                return Optional.empty();
            }
            if (data != null) {
                data.targetBlockPos = nearest.get();
                data.targetBlockIds = new ArrayList<>();
            }
            return Optional.of(Vec3.atCenterOf(nearest.get()));
        }

        Optional<BlockPos> match = owner.findNearestBlock(client, blocks, range);
        if (match.isEmpty()) {
            owner.sendParameterSearchFailure(NodeBehaviorDefinitionSupport.noMatchingBlockMessage(owner), future);
            return Optional.empty();
        }
        if (data != null) {
            data.targetBlockPos = match.get();
            data.targetBlockIds = new ArrayList<>();
            for (BlockSelection selection : blocks) {
                Identifier id = selection.getBlockId();
                if (id != null) {
                    data.targetBlockIds.add(selection.asString());
                }
            }
        }
        return Optional.of(Vec3.atCenterOf(match.get()));
    }

    private static BlockPos resolveGotoFallbackTarget(Node owner, Node parameterNode, Minecraft client,
                                                      CompletableFuture<Void> future) {
        String blockId = owner.getBlockParameterValue(parameterNode);
        BlockPos pos = owner.resolveGotoFallbackTargetFromBlockId(blockId, future);
        RuntimeParameterData data = owner.getRuntimeState().runtimeParameterData;
        if (pos != null && data != null) {
            data.targetBlockPos = pos;
        }
        return pos;
    }

    private BlockParameterDefinition() {
    }
}
