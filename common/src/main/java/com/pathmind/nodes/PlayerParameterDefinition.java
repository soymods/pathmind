package com.pathmind.nodes;

import com.pathmind.util.EntityCompatibilityBridge;
import com.pathmind.util.GameProfileCompatibilityBridge;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class PlayerParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_PLAYER)
            .parameterBehavior((node, values) -> NodeBehaviorDefinitionSupport.copyIfPresent(values, "Player", "Name"))
            .runtimeBehavior(PlayerParameterDefinition::resolvePositionTarget)
            .listEntryBehavior(PlayerParameterDefinition::resolveListEntry)
            .gotoFallbackTargetBehavior(PlayerParameterDefinition::resolveGotoFallbackTarget)
            .build();
    }

    static NodeBehaviorDefinition listItemDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.LIST_ITEM)
            .gotoFallbackTargetBehavior(PlayerParameterDefinition::resolveListItemGotoFallbackTarget)
            .build();
    }

    private static Optional<Vec3d> resolvePositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                         CompletableFuture<Void> future) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return Optional.empty();
        }
        String playerName = Node.getParameterString(parameterNode, "Player");
        Optional<AbstractClientPlayerEntity> player = findPlayer(client, playerName);
        if (player.isEmpty()) {
            owner.sendParameterSearchFailure(NodeBehaviorDefinitionSupport.playerSearchFailureMessage(owner, playerName), future);
            return Optional.empty();
        }
        String resolvedName = GameProfileCompatibilityBridge.getName(player.get().getGameProfile());
        if (data != null) {
            data.targetPlayerName = resolvedName != null ? resolvedName : playerName;
            data.targetEntity = player.get();
            data.targetBlockPos = player.get().getBlockPos();
        }
        Vec3d playerPos = EntityCompatibilityBridge.getPos(player.get());
        if (playerPos == null) {
            playerPos = Vec3d.ofCenter(player.get().getBlockPos());
        }
        return Optional.of(playerPos);
    }

    private static Node.ListValueEntry resolveListEntry(Node owner, Node parameterNode, MinecraftClient client) {
        String playerName = Node.getParameterString(parameterNode, "Player");
        if (Node.isSelfPlayerValue(playerName)) {
            return new Node.ListValueEntry(NodeType.PARAM_PLAYER, client.player.getUuidAsString());
        }
        Optional<AbstractClientPlayerEntity> player = findPlayer(client, playerName);
        return player.map(match -> new Node.ListValueEntry(NodeType.PARAM_PLAYER, match.getUuidAsString())).orElse(null);
    }

    private static BlockPos resolveGotoFallbackTarget(Node owner, Node parameterNode, MinecraftClient client,
                                                      CompletableFuture<Void> future) {
        if (client == null || client.player == null || client.world == null) {
            return null;
        }
        String playerName = Node.getParameterString(parameterNode, "Player");
        if (Node.isSelfPlayerValue(playerName)) {
            future.complete(null);
            return null;
        }
        Optional<AbstractClientPlayerEntity> match = findPlayer(client, playerName);
        if (match.isEmpty()) {
            owner.sendNodeErrorMessage(client, NodeBehaviorDefinitionSupport.playerSearchFailureMessage(owner, playerName));
            future.complete(null);
            return null;
        }

        RuntimeParameterData data = owner.getRuntimeState().runtimeParameterData;
        if (data != null) {
            data.targetBlockPos = match.get().getBlockPos();
            data.targetEntity = match.get();
        }
        return match.get().getBlockPos();
    }

    private static BlockPos resolveListItemGotoFallbackTarget(Node owner, Node parameterNode, MinecraftClient client,
                                                              CompletableFuture<Void> future) {
        RuntimeParameterData data = owner.getRuntimeState().runtimeParameterData;
        Entity target = owner.resolveListItemEntity(parameterNode, data, future);
        if (target == null) {
            return null;
        }
        if (data != null) {
            data.targetBlockPos = target.getBlockPos();
            data.targetEntity = target;
        }
        return target.getBlockPos();
    }

    private static Optional<AbstractClientPlayerEntity> findPlayer(MinecraftClient client, String playerName) {
        if (Node.isAnyPlayerValue(playerName)) {
            return Node.findNearestPlayer(client, client.player);
        }
        if (Node.isSelfPlayerValue(playerName)) {
            return Optional.of(client.player);
        }
        return client.world.getPlayers().stream()
            .filter(player -> playerName.equalsIgnoreCase(GameProfileCompatibilityBridge.getName(player.getGameProfile())))
            .findFirst();
    }

    private PlayerParameterDefinition() {
    }
}
