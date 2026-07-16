package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

final class ItemParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_ITEM)
            .parameterBehavior(ItemParameterDefinition::exportValues)
            .runtimeBehavior(ItemParameterDefinition::resolvePositionTarget)
            .listEntryBehavior(ItemParameterDefinition::resolveListEntry)
            .gotoFallbackTargetBehavior(ItemParameterDefinition::resolveGotoFallbackTarget)
            .build();
    }

    private static java.util.Map<String, String> exportValues(Node node, java.util.Map<String, String> values) {
        NodeBehaviorDefinitionSupport.syncSingularAndPlural(values, "Item", "Items");
        String amount = values.get("Amount");
        if (amount != null) {
            NodeBehaviorDefinitionSupport.put(values, "Count", amount);
        }
        return values;
    }

    private static Optional<Vec3> resolvePositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                         CompletableFuture<Void> future) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return Optional.empty();
        }
        List<String> itemIds = owner.resolveItemIdsFromParameter(parameterNode);
        if (itemIds.isEmpty()) {
            owner.sendParameterSearchFailure(tr("pathmind.error.noItemSelectedOnParameter", owner.getType().getDisplayName()), future);
            return Optional.empty();
        }
        double defaultRange = owner.getType() == NodeType.SENSOR_DISTANCE_BETWEEN ? 256.0 : Node.PARAMETER_SEARCH_RADIUS;
        double range = Node.parseNodeDouble(parameterNode, "Range", defaultRange);
        boolean hasValidCandidate = false;
        for (String candidateId : itemIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
                continue;
            }
            hasValidCandidate = true;
            Item item = BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
            Optional<BlockPos> match = owner.findNearestDroppedItem(client, item, range);
            if (match.isEmpty()) {
                continue;
            }
            if (data != null) {
                data.targetBlockPos = match.get();
                data.targetItem = item;
                data.targetItemId = candidateId;
            }
            return Optional.of(Vec3.atCenterOf(match.get()));
        }
        if (!hasValidCandidate) {
            owner.sendParameterSearchFailure(NodeBehaviorDefinitionSupport.unknownItemMessage(owner, itemIds.get(0)), future);
            return Optional.empty();
        }
        owner.sendParameterSearchFailure(NodeBehaviorDefinitionSupport.noDroppedItemMessage(owner, itemIds), future);
        return Optional.empty();
    }

    private static Node.ListValueEntry resolveListEntry(Node owner, Node parameterNode, Minecraft client) {
        double range = Node.parseDoubleOrDefault(Node.getParameterString(parameterNode, "Range"), Node.PARAMETER_SEARCH_RADIUS);
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (String candidateId : owner.resolveItemIdsFromParameter(parameterNode)) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
            for (ItemEntity itemEntity : owner.findItemsByType(client, item, range)) {
                if (itemEntity == null || itemEntity.isRemoved()) {
                    continue;
                }
                double distance = itemEntity.distanceToSqr(client.player);
                if (distance < nearestDistance) {
                    nearest = itemEntity;
                    nearestDistance = distance;
                }
            }
        }
        return nearest != null ? new Node.ListValueEntry(NodeType.PARAM_ITEM, nearest.getStringUUID()) : null;
    }

    private static BlockPos resolveGotoFallbackTarget(Node owner, Node parameterNode, Minecraft client,
                                                      CompletableFuture<Void> future) {
        if (client == null || client.player == null || client.level == null) {
            return null;
        }
        List<String> itemIds = owner.resolveItemIdsFromParameter(parameterNode);
        if (itemIds.isEmpty()) {
            owner.sendNodeErrorMessage(client, tr("pathmind.error.noItemSelectedForNode", owner.getType().getDisplayName()));
            future.complete(null);
            return null;
        }
        double searchRange = Node.parseDoubleOrDefault(Node.getParameterString(parameterNode, "Range"), Node.PARAMETER_SEARCH_RADIUS);
        Optional<BlockPos> matchedPosition = Optional.empty();
        Item matchedItem = null;
        String matchedItemId = null;

        for (String candidateId : itemIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
                continue;
            }
            Item candidateItem = BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
            Optional<BlockPos> target = owner.findNearestDroppedItem(client, candidateItem, searchRange);
            if (target.isPresent()) {
                matchedPosition = target;
                matchedItem = candidateItem;
                matchedItemId = candidateId;
                break;
            }
        }

        if (matchedPosition.isEmpty()) {
            String reference = String.join(", ", itemIds);
            owner.sendNodeErrorMessage(client, tr("pathmind.error.noDroppedItemNearby", reference, owner.getType().getDisplayName()));
            future.complete(null);
            return null;
        }

        RuntimeParameterData data = owner.getRuntimeState().runtimeParameterData;
        if (data != null) {
            data.targetBlockPos = matchedPosition.get();
            data.targetItem = matchedItem;
            data.targetItemId = matchedItemId;
        }

        return matchedPosition.get();
    }

    private ItemParameterDefinition() {
    }
}
