package com.pathmind.nodes;

import com.pathmind.util.BlockSelection;
import com.pathmind.util.EntityCompatibilityBridge;
import com.pathmind.util.EntityStateOptions;
import com.pathmind.util.GameProfileCompatibilityBridge;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

final class NodeBehaviorDefinitionRegistry {
    private static final Map<NodeType, NodeBehaviorDefinition> DEFINITIONS = new EnumMap<>(NodeType.class);

    static {
        for (NodeType type : NodeType.values()) {
            NodeBehaviorDefinition definition = directDefinition(type).orElseGet(() -> composedDefinition(type));
            if (definition.hasAnyBehavior()) {
                DEFINITIONS.put(type, definition);
            }
        }
    }

    static NodeBehaviorDefinition get(NodeType type) {
        return DEFINITIONS.get(type);
    }

    static Map<NodeType, NodeBehaviorDefinition> snapshot() {
        return new EnumMap<>(DEFINITIONS);
    }

    private static NodeBehaviorDefinition composedDefinition(NodeType type) {
        return NodeBehaviorDefinition.builder(type)
            .comparableBehavior(NodeComparableBehaviorRegistry.get(type))
            .build();
    }

    private static Optional<NodeBehaviorDefinition> directDefinition(NodeType type) {
        return switch (type) {
            case PARAM_ITEM -> Optional.of(itemDefinition());
            case PARAM_BLOCK -> Optional.of(blockDefinition());
            case PARAM_ENTITY -> Optional.of(entityDefinition());
            case PARAM_PLAYER -> Optional.of(playerDefinition());
            case PARAM_COORDINATE -> Optional.of(coordinateDefinition());
            case PARAM_SCHEMATIC -> Optional.of(schematicDefinition());
            case PARAM_PLACE_TARGET -> Optional.of(placeTargetDefinition());
            case PARAM_ROTATION -> Optional.of(rotationDefinition());
            case PARAM_DIRECTION -> Optional.of(directionDefinition());
            case PARAM_BLOCK_FACE -> Optional.of(blockFaceDefinition());
            case PARAM_CLOSEST -> Optional.of(closestDefinition());
            case PARAM_AMOUNT -> Optional.of(amountDefinition());
            case PARAM_DURATION -> Optional.of(durationDefinition());
            case PARAM_RANGE -> Optional.of(rangeDefinition());
            case PARAM_DISTANCE -> Optional.of(distanceDefinition());
            case PARAM_BOOLEAN -> Optional.of(booleanDefinition());
            case PARAM_HAND -> Optional.of(handDefinition());
            case PARAM_MESSAGE -> Optional.of(messageDefinition());
            case PARAM_INVENTORY_SLOT -> Optional.of(inventorySlotDefinition());
            case PARAM_VILLAGER_TRADE -> Optional.of(villagerTradeDefinition());
            case PARAM_WAYPOINT -> Optional.of(waypointDefinition());
            case LIST_ITEM -> Optional.of(listItemDefinition());
            default -> Optional.empty();
        };
    }

    private static NodeBehaviorDefinition itemDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_ITEM)
            .parameterBehavior(NodeBehaviorDefinitionRegistry::exportItemValues)
            .runtimeBehavior(NodeBehaviorDefinitionRegistry::resolveItemPositionTarget)
            .listEntryBehavior(NodeBehaviorDefinitionRegistry::resolveItemListEntry)
            .gotoFallbackTargetBehavior(NodeBehaviorDefinitionRegistry::resolveItemGotoFallbackTarget)
            .build();
    }

    private static NodeBehaviorDefinition blockDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_BLOCK)
            .parameterBehavior(NodeBehaviorDefinitionRegistry::exportBlockValues)
            .runtimeBehavior(NodeBehaviorDefinitionRegistry::resolveBlockPositionTarget)
            .gotoFallbackTargetBehavior(NodeBehaviorDefinitionRegistry::resolveBlockGotoFallbackTarget)
            .build();
    }

    private static NodeBehaviorDefinition entityDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_ENTITY)
            .parameterBehavior((node, values) -> copyIfPresent(values, "Range", "Distance"))
            .runtimeBehavior(NodeBehaviorDefinitionRegistry::resolveEntityPositionTarget)
            .listEntryBehavior(NodeBehaviorDefinitionRegistry::resolveEntityListEntry)
            .gotoFallbackTargetBehavior(NodeBehaviorDefinitionRegistry::resolveEntityGotoFallbackTarget)
            .build();
    }

    private static NodeBehaviorDefinition playerDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_PLAYER)
            .parameterBehavior((node, values) -> copyIfPresent(values, "Player", "Name"))
            .runtimeBehavior(NodeBehaviorDefinitionRegistry::resolvePlayerPositionTarget)
            .listEntryBehavior(NodeBehaviorDefinitionRegistry::resolvePlayerListEntry)
            .gotoFallbackTargetBehavior(NodeBehaviorDefinitionRegistry::resolvePlayerGotoFallbackTarget)
            .build();
    }

    private static NodeBehaviorDefinition coordinateDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_COORDINATE)
            .runtimeBehavior(NodeBehaviorDefinitionRegistry::resolveCoordinatePositionTarget)
            .comparableBehavior(stringComparable((owner, node) -> {
                String formatted = owner.formatCoordinateValues(node.exportParameterValues());
                return formatted.isEmpty() ? Optional.empty() : Optional.of(formatted);
            }))
            .build();
    }

    private static NodeBehaviorDefinition schematicDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_SCHEMATIC)
            .runtimeBehavior(NodeBehaviorDefinitionRegistry::resolveSchematicPositionTarget)
            .build();
    }

    private static NodeBehaviorDefinition placeTargetDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_PLACE_TARGET)
            .parameterBehavior((node, values) -> copyIfPresent(values, "Block", "BlockId"))
            .runtimeBehavior(NodeBehaviorDefinitionRegistry::resolvePlaceTargetPositionTarget)
            .build();
    }

    private static NodeBehaviorDefinition rotationDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_ROTATION)
            .parameterBehavior(NodeBehaviorDefinitionRegistry::exportRotationValues)
            .runtimeBehavior(NodeBehaviorDefinitionRegistry::resolveDirectionalPositionTarget)
            .comparableBehavior(stringComparable((owner, node) -> {
                String formatted = owner.formatRotationValues(node.exportParameterValues());
                return formatted.isEmpty() ? Optional.empty() : Optional.of(formatted);
            }))
            .build();
    }

    private static NodeBehaviorDefinition directionDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_DIRECTION)
            .parameterBehavior(NodeBehaviorDefinitionRegistry::exportDirectionValues)
            .runtimeBehavior(NodeBehaviorDefinitionRegistry::resolveDirectionalPositionTarget)
            .comparableBehavior(stringComparable(NodeBehaviorDefinitionRegistry::resolveDirectionComparableString))
            .build();
    }

    private static NodeBehaviorDefinition blockFaceDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_BLOCK_FACE)
            .parameterBehavior(NodeBehaviorDefinitionRegistry::exportBlockFaceValues)
            .runtimeBehavior(NodeBehaviorDefinitionRegistry::resolveDirectionalPositionTarget)
            .comparableBehavior(stringComparable(NodeBehaviorDefinitionRegistry::resolveBlockFaceComparableString))
            .build();
    }

    private static NodeBehaviorDefinition closestDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_CLOSEST)
            .parameterBehavior((node, values) -> copyIfPresent(values, "Range", "Distance"))
            .runtimeBehavior(NodeBehaviorDefinitionRegistry::resolveClosestPositionTarget)
            .build();
    }

    private static NodeBehaviorDefinition amountDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_AMOUNT)
            .parameterBehavior(NodeBehaviorDefinitionRegistry::exportAmountValues)
            .comparableBehavior(numberComparable((owner, node) -> Optional.of(Node.parseNodeDouble(node, "Amount", 0.0))))
            .build();
    }

    private static NodeBehaviorDefinition durationDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_DURATION)
            .parameterBehavior(NodeBehaviorDefinitionRegistry::exportDurationValues)
            .build();
    }

    private static NodeBehaviorDefinition rangeDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_RANGE)
            .parameterBehavior(NodeBehaviorDefinitionRegistry::exportRangeValues)
            .build();
    }

    private static NodeBehaviorDefinition distanceDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_DISTANCE)
            .comparableBehavior(numberComparable((owner, node) -> Optional.of(Node.parseNodeDouble(node, "Distance", 0.0))))
            .build();
    }

    private static NodeBehaviorDefinition booleanDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_BOOLEAN)
            .parameterBehavior(NodeBehaviorDefinitionRegistry::exportBooleanValues)
            .build();
    }

    private static NodeBehaviorDefinition handDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_HAND)
            .parameterBehavior(NodeBehaviorDefinitionRegistry::exportHandValues)
            .build();
    }

    private static NodeBehaviorDefinition messageDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_MESSAGE)
            .parameterBehavior((node, values) -> copyIfPresent(values, "Text", "Message"))
            .comparableBehavior(stringComparable(NodeBehaviorDefinitionRegistry::resolveMessageComparableString))
            .build();
    }

    private static NodeBehaviorDefinition inventorySlotDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_INVENTORY_SLOT)
            .parameterBehavior(NodeBehaviorDefinitionRegistry::exportInventorySlotValues)
            .comparableBehavior(numberComparable((owner, node) ->
                owner.resolveInventorySlotCount(node).map(count -> (double) count)))
            .build();
    }

    private static NodeBehaviorDefinition villagerTradeDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_VILLAGER_TRADE)
            .parameterBehavior(NodeBehaviorDefinitionRegistry::exportVillagerTradeValues)
            .build();
    }

    private static NodeBehaviorDefinition waypointDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_WAYPOINT)
            .parameterBehavior((node, values) -> copyIfPresent(values, "Waypoint", "Name"))
            .build();
    }

    private static NodeBehaviorDefinition listItemDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.LIST_ITEM)
            .gotoFallbackTargetBehavior(NodeBehaviorDefinitionRegistry::resolveListItemGotoFallbackTarget)
            .build();
    }

    private static Map<String, String> exportItemValues(Node node, Map<String, String> values) {
        syncSingularAndPlural(values, "Item", "Items");
        String amount = values.get("Amount");
        if (amount != null) {
            put(values, "Count", amount);
        }
        return values;
    }

    private static Map<String, String> exportBlockValues(Node node, Map<String, String> values) {
        syncSingularAndPlural(values, "Block", "Blocks");
        return values;
    }

    private static Map<String, String> exportRotationValues(Node node, Map<String, String> values) {
        String yaw = values.get("Yaw");
        if (yaw != null) {
            put(values, "YawOffset", yaw);
        }
        String pitch = values.get("Pitch");
        if (pitch != null) {
            put(values, "PitchOffset", pitch);
        }
        return values;
    }

    private static Map<String, String> exportDirectionValues(Node node, Map<String, String> values) {
        String modeValue = node.isDirectionModeExact() ? "exact" : "cardinal";
        put(values, "Mode", modeValue);
        String direction = values.get("Direction");
        if ("cardinal".equals(modeValue) && direction != null && !direction.trim().isEmpty()) {
            applyCardinalDirection(values, direction);
        }
        return values;
    }

    private static Map<String, String> exportBlockFaceValues(Node node, Map<String, String> values) {
        String face = values.get("Face");
        if (face != null && !face.trim().isEmpty()) {
            put(values, "Side", face);
            put(values, "Direction", face);
        }
        return values;
    }

    private static Map<String, String> exportAmountValues(Node node, Map<String, String> values) {
        String amount = values.get("Amount");
        if (amount != null) {
            put(values, "Count", amount);
            put(values, "Threshold", amount);
            put(values, "Value", amount);
        }
        return values;
    }

    private static Map<String, String> exportDurationValues(Node node, Map<String, String> values) {
        String duration = values.get("Duration");
        if (duration != null) {
            String secondsValue = Double.toString(parseNonNegativeDouble(duration, 1.0) * durationUnitSeconds(node.getMode()));
            put(values, "Duration", secondsValue);
            put(values, "IntervalSeconds", secondsValue);
            put(values, "WaitSeconds", secondsValue);
            put(values, "DurationSeconds", secondsValue);
        }
        return values;
    }

    private static Map<String, String> exportRangeValues(Node node, Map<String, String> values) {
        String range = values.get("Range");
        if (range != null) {
            put(values, "Distance", range);
            put(values, "Radius", range);
        }
        return values;
    }

    private static Map<String, String> exportBooleanValues(Node node, Map<String, String> values) {
        node.ensureBooleanParameters();
        String modeValue = node.isBooleanModeLiteral() ? "literal" : "variable";
        put(values, "Mode", modeValue);
        NodeParameter variableParameter = node.getParameter("Variable");
        if (variableParameter != null) {
            put(values, "Variable", variableParameter.getStringValue());
        }
        Optional<Boolean> resolvedToggle = node.resolveBooleanNodeValue(node);
        String toggle = resolvedToggle.map(String::valueOf).orElseGet(() -> values.get("Toggle"));
        if (toggle == null) {
            toggle = values.get(Node.normalizeParameterKey("Toggle"));
        }
        if (toggle != null) {
            put(values, "Active", toggle);
            put(values, "Enabled", toggle);
            put(values, "Toggle", toggle);
        }
        return values;
    }

    private static Map<String, String> exportHandValues(Node node, Map<String, String> values) {
        String hand = values.get("Hand");
        if (hand != null) {
            put(values, "SourceHand", hand);
            put(values, "TargetHand", hand);
            put(values, "SelectedHand", hand);
        }
        return values;
    }

    private static Map<String, String> exportInventorySlotValues(Node node, Map<String, String> values) {
        String slot = values.get("Slot");
        if (slot != null) {
            put(values, "SourceSlot", slot);
            put(values, "TargetSlot", slot);
            put(values, "FirstSlot", slot);
            put(values, "SecondSlot", slot);
        }
        ItemStack resolvedStack = InventorySlotValueResolver.resolveComparableInventorySlotStack(values);
        if (resolvedStack != null && !resolvedStack.isEmpty()) {
            Identifier itemId = Registries.ITEM.getId(resolvedStack.getItem());
            if (itemId != null) {
                String itemValue = itemId.toString();
                put(values, "Item", itemValue);
                put(values, "Items", itemValue);
            }
            String countValue = Integer.toString(resolvedStack.getCount());
            put(values, "Count", countValue);
            put(values, "Amount", countValue);
        }
        return values;
    }

    private static Map<String, String> exportVillagerTradeValues(Node node, Map<String, String> values) {
        String item = values.get("Item");
        if (item != null && !item.isEmpty()) {
            put(values, "Items", item);
        }
        return values;
    }

    private static Optional<Vec3d> resolveCoordinatePositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                                   CompletableFuture<Void> future) {
        BlockPos pos = resolveBlockPosition(parameterNode);
        if (data != null) {
            data.targetBlockPos = pos;
        }
        return Optional.of(Vec3d.ofCenter(pos));
    }

    private static Optional<Vec3d> resolveSchematicPositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                                  CompletableFuture<Void> future) {
        BlockPos pos = resolveBlockPosition(parameterNode);
        if (data != null) {
            data.targetBlockPos = pos;
            data.schematicName = Node.getParameterString(parameterNode, "Schematic");
        }
        return Optional.of(Vec3d.ofCenter(pos));
    }

    private static Optional<Vec3d> resolvePlaceTargetPositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                                    CompletableFuture<Void> future) {
        BlockPos pos = resolveBlockPosition(parameterNode);
        if (data != null) {
            data.targetBlockPos = pos;
            data.targetBlockId = owner.getBlockParameterValue(parameterNode);
        }
        return Optional.of(Vec3d.ofCenter(pos));
    }

    private static Optional<Vec3d> resolveDirectionalPositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                                    CompletableFuture<Void> future) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return Optional.empty();
        }
        Vec3d origin = EntityCompatibilityBridge.getPos(client.player);
        if (origin == null) {
            return Optional.empty();
        }

        NodeType parameterType = parameterNode.getType();
        Float yawParam = Node.parseNodeFloat(parameterNode, "Yaw");
        Float pitchParam = Node.parseNodeFloat(parameterNode, "Pitch");
        float yaw = yawParam != null ? yawParam : client.player.getYaw();
        float pitch = pitchParam != null ? pitchParam : client.player.getPitch();

        Orientation orientation = resolveNamedOrientation(parameterType, parameterNode, yaw, pitch);
        yaw = orientation.yaw;
        pitch = orientation.pitch;

        if (isGotoLike(owner.getType()) && !isCoordinateMode(owner.getMode())) {
            return Optional.empty();
        }

        Float yawOffset = Node.parseNodeFloat(parameterNode, "YawOffset");
        Float pitchOffset = Node.parseNodeFloat(parameterNode, "PitchOffset");
        if (yawOffset != null) {
            yaw += yawOffset;
        }
        if (pitchOffset != null) {
            pitch += pitchOffset;
        }

        double distance = Math.max(0.0, Node.parseNodeDouble(parameterNode, "Distance", defaultDirectionDistance(parameterType, parameterNode)));
        Vec3d target = projectTarget(origin, yaw, pitch, distance);
        if (data != null) {
            data.targetVector = target;
            data.targetBlockPos = new BlockPos(MathHelper.floor(target.x), MathHelper.floor(target.y), MathHelper.floor(target.z));
            data.resolvedYaw = yaw;
            data.resolvedPitch = pitch;
        }
        return Optional.of(target);
    }

    private static Optional<Vec3d> resolveClosestPositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                                CompletableFuture<Void> future) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return Optional.empty();
        }
        int range = Math.max(1, Node.parseNodeInt(parameterNode, "Range", 5));
        Optional<BlockPos> open = owner.findNearestOpenBlock(client, range);
        if (open.isEmpty()) {
            owner.sendParameterSearchFailure(noOpenBlockMessage(owner), future);
            return Optional.empty();
        }
        if (data != null) {
            data.targetBlockPos = open.get();
        }
        return Optional.of(Vec3d.ofCenter(open.get()));
    }

    private static Optional<Vec3d> resolveItemPositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                             CompletableFuture<Void> future) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return Optional.empty();
        }
        List<String> itemIds = owner.resolveItemIdsFromParameter(parameterNode);
        if (itemIds.isEmpty()) {
            owner.sendParameterSearchFailure("No item selected on parameter for " + owner.getType().getDisplayName() + ".", future);
            return Optional.empty();
        }
        double defaultRange = owner.getType() == NodeType.SENSOR_DISTANCE_BETWEEN ? 256.0 : Node.PARAMETER_SEARCH_RADIUS;
        double range = Node.parseNodeDouble(parameterNode, "Range", defaultRange);
        boolean hasValidCandidate = false;
        for (String candidateId : itemIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            hasValidCandidate = true;
            Item item = Registries.ITEM.get(identifier);
            Optional<BlockPos> match = owner.findNearestDroppedItem(client, item, range);
            if (match.isEmpty()) {
                continue;
            }
            if (data != null) {
                data.targetBlockPos = match.get();
                data.targetItem = item;
                data.targetItemId = candidateId;
            }
            return Optional.of(Vec3d.ofCenter(match.get()));
        }
        if (!hasValidCandidate) {
            owner.sendParameterSearchFailure(unknownItemMessage(owner, itemIds.get(0)), future);
            return Optional.empty();
        }
        owner.sendParameterSearchFailure(noDroppedItemMessage(owner, itemIds), future);
        return Optional.empty();
    }

    private static Optional<Vec3d> resolveBlockPositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                              CompletableFuture<Void> future) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return Optional.empty();
        }
        String rawBlock = Node.getParameterString(parameterNode, "Block");
        List<BlockSelection> blocks = owner.resolveBlocksFromParameter(parameterNode);
        double range = Node.parseNodeDouble(parameterNode, "Range", Node.PARAMETER_SEARCH_RADIUS);
        if (blocks.isEmpty()) {
            if (!Node.isAnySelectionValue(rawBlock)) {
                owner.sendParameterSearchFailure(noBlocksDefinedMessage(owner), future);
                return Optional.empty();
            }
            Optional<BlockPos> nearest = owner.findNearestAnyBlock(client, range);
            if (nearest.isEmpty()) {
                owner.sendParameterSearchFailure(noNearbyBlockMessage(owner), future);
                return Optional.empty();
            }
            if (data != null) {
                data.targetBlockPos = nearest.get();
                data.targetBlockIds = new ArrayList<>();
            }
            return Optional.of(Vec3d.ofCenter(nearest.get()));
        }

        Optional<BlockPos> match = owner.findNearestBlock(client, blocks, range);
        if (match.isEmpty()) {
            owner.sendParameterSearchFailure(noMatchingBlockMessage(owner), future);
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
        return Optional.of(Vec3d.ofCenter(match.get()));
    }

    private static Optional<Vec3d> resolveEntityPositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                               CompletableFuture<Void> future) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return Optional.empty();
        }
        String state = owner.getEntityParameterState(parameterNode);
        double defaultRange = owner.getType() == NodeType.SENSOR_DISTANCE_BETWEEN ? 256.0 : Node.PARAMETER_SEARCH_RADIUS;
        double range = Node.parseNodeDouble(parameterNode, "Range", defaultRange);
        String rawEntity = Node.getParameterString(parameterNode, "Entity");

        if (Node.isAnySelectionValue(rawEntity)) {
            if (client.world == null) {
                return Optional.empty();
            }
            Optional<Entity> nearest = findNearestAnyEntity(client, range, state);
            if (nearest.isEmpty()) {
                owner.sendParameterSearchFailure(noNearbyEntityMessage(owner), future);
                return Optional.empty();
            }
            Identifier nearestIdentifier = Registries.ENTITY_TYPE.getId(nearest.get().getType());
            return resolvedEntityPosition(nearest.get(), nearestIdentifier != null ? nearestIdentifier.toString() : null, data);
        }

        List<String> entityIds = owner.resolveEntityIdsFromParameter(parameterNode);
        if (entityIds.isEmpty()) {
            owner.sendParameterSearchFailure("No entity selected on parameter for " + owner.getType().getDisplayName() + ".", future);
            return Optional.empty();
        }
        Entity nearest = null;
        String nearestId = null;
        double nearestDistance = Double.MAX_VALUE;
        for (String candidateId : entityIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                continue;
            }
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
            Optional<Entity> entity = owner.findNearestEntity(client, entityType, range, state);
            if (entity.isEmpty()) {
                continue;
            }
            double distance = entity.get().squaredDistanceTo(client.player);
            if (distance < nearestDistance) {
                nearest = entity.get();
                nearestId = identifier.toString();
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            owner.sendParameterSearchFailure(noNearbyEntityMessage(owner), future);
            return Optional.empty();
        }
        return resolvedEntityPosition(nearest, nearestId, data);
    }

    private static Optional<Vec3d> resolvePlayerPositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                               CompletableFuture<Void> future) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return Optional.empty();
        }
        String playerName = Node.getParameterString(parameterNode, "Player");
        Optional<AbstractClientPlayerEntity> player = findPlayer(client, playerName);
        if (player.isEmpty()) {
            owner.sendParameterSearchFailure(playerSearchFailureMessage(owner, playerName), future);
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

    private static Node.ListValueEntry resolveItemListEntry(Node owner, Node parameterNode, MinecraftClient client) {
        double range = Node.parseDoubleOrDefault(Node.getParameterString(parameterNode, "Range"), Node.PARAMETER_SEARCH_RADIUS);
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (String candidateId : owner.resolveItemIdsFromParameter(parameterNode)) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            Item item = Registries.ITEM.get(identifier);
            for (ItemEntity itemEntity : owner.findItemsByType(client, item, range)) {
                if (itemEntity == null || itemEntity.isRemoved()) {
                    continue;
                }
                double distance = itemEntity.squaredDistanceTo(client.player);
                if (distance < nearestDistance) {
                    nearest = itemEntity;
                    nearestDistance = distance;
                }
            }
        }
        return nearest != null ? new Node.ListValueEntry(NodeType.PARAM_ITEM, nearest.getUuidAsString()) : null;
    }

    private static Node.ListValueEntry resolveEntityListEntry(Node owner, Node parameterNode, MinecraftClient client) {
        String state = owner.getEntityParameterState(parameterNode);
        double range = Node.parseDoubleOrDefault(Node.getParameterString(parameterNode, "Range"), Node.PARAMETER_SEARCH_RADIUS);
        String rawEntity = Node.getParameterString(parameterNode, "Entity");
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        if (Node.isAnySelectionValue(rawEntity)) {
            double searchRadius = Math.max(1.0, range);
            Box searchBox = client.player.getBoundingBox().expand(searchRadius);
            for (Entity candidate : client.world.getOtherEntities(
                client.player,
                searchBox,
                entity -> entity != null && !entity.isRemoved() && EntityStateOptions.matchesState(entity, state))) {
                double distance = candidate.squaredDistanceTo(client.player);
                if (distance < nearestDistance) {
                    nearest = candidate;
                    nearestDistance = distance;
                }
            }
        } else {
            for (String candidateId : owner.resolveEntityIdsFromParameter(parameterNode)) {
                Identifier identifier = Identifier.tryParse(candidateId);
                if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                    continue;
                }
                EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
                Optional<Entity> candidate = owner.findNearestEntity(client, entityType, range, state);
                if (candidate.isEmpty()) {
                    continue;
                }
                double distance = candidate.get().squaredDistanceTo(client.player);
                if (distance < nearestDistance) {
                    nearest = candidate.get();
                    nearestDistance = distance;
                }
            }
        }
        return nearest != null ? new Node.ListValueEntry(NodeType.PARAM_ENTITY, nearest.getUuidAsString()) : null;
    }

    private static Node.ListValueEntry resolvePlayerListEntry(Node owner, Node parameterNode, MinecraftClient client) {
        String playerName = Node.getParameterString(parameterNode, "Player");
        if (Node.isSelfPlayerValue(playerName)) {
            return new Node.ListValueEntry(NodeType.PARAM_PLAYER, client.player.getUuidAsString());
        }
        Optional<AbstractClientPlayerEntity> player = findPlayer(client, playerName);
        return player.map(match -> new Node.ListValueEntry(NodeType.PARAM_PLAYER, match.getUuidAsString())).orElse(null);
    }

    private static BlockPos resolveItemGotoFallbackTarget(Node owner, Node parameterNode, MinecraftClient client,
                                                          CompletableFuture<Void> future) {
        if (client == null || client.player == null || client.world == null) {
            return null;
        }
        List<String> itemIds = owner.resolveItemIdsFromParameter(parameterNode);
        if (itemIds.isEmpty()) {
            owner.sendNodeErrorMessage(client, "No item selected for " + owner.getType().getDisplayName() + ".");
            future.complete(null);
            return null;
        }
        double searchRange = Node.parseDoubleOrDefault(Node.getParameterString(parameterNode, "Range"), Node.PARAMETER_SEARCH_RADIUS);
        Optional<BlockPos> matchedPosition = Optional.empty();
        Item matchedItem = null;
        String matchedItemId = null;

        for (String candidateId : itemIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            Item candidateItem = Registries.ITEM.get(identifier);
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
            owner.sendNodeErrorMessage(client, "No dropped " + reference + " found nearby for " + owner.getType().getDisplayName() + ".");
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

    private static BlockPos resolveBlockGotoFallbackTarget(Node owner, Node parameterNode, MinecraftClient client,
                                                           CompletableFuture<Void> future) {
        String blockId = owner.getBlockParameterValue(parameterNode);
        BlockPos pos = owner.resolveGotoFallbackTargetFromBlockId(blockId, future);
        RuntimeParameterData data = owner.getRuntimeState().runtimeParameterData;
        if (pos != null && data != null) {
            data.targetBlockPos = pos;
        }
        return pos;
    }

    private static BlockPos resolveEntityGotoFallbackTarget(Node owner, Node parameterNode, MinecraftClient client,
                                                            CompletableFuture<Void> future) {
        if (client == null || client.player == null || client.world == null) {
            return null;
        }
        List<String> entityIds = owner.resolveEntityIdsFromParameter(parameterNode);
        if (entityIds.isEmpty()) {
            owner.sendNodeErrorMessage(client, "No entity selected for " + owner.getType().getDisplayName() + ".");
            future.complete(null);
            return null;
        }
        String state = owner.getEntityParameterState(parameterNode);
        double range = Node.parseDoubleOrDefault(Node.getParameterString(parameterNode, "Range"), Node.PARAMETER_SEARCH_RADIUS);
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (String candidateId : entityIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                continue;
            }
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
            Optional<Entity> target = owner.findNearestEntity(client, entityType, range, state);
            if (target.isEmpty()) {
                continue;
            }
            double distance = target.get().squaredDistanceTo(client.player);
            if (distance < nearestDistance) {
                nearest = target.get();
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            owner.sendNodeErrorMessage(client, "No matching entity found nearby for " + owner.getType().getDisplayName() + ".");
            future.complete(null);
            return null;
        }
        RuntimeParameterData data = owner.getRuntimeState().runtimeParameterData;
        if (data != null) {
            data.targetBlockPos = nearest.getBlockPos();
            data.targetEntity = nearest;
        }
        return nearest.getBlockPos();
    }

    private static BlockPos resolvePlayerGotoFallbackTarget(Node owner, Node parameterNode, MinecraftClient client,
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
            owner.sendNodeErrorMessage(client, playerSearchFailureMessage(owner, playerName));
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

    static String playerSearchFailureMessage(Node owner, String playerName) {
        if (Node.isAnyPlayerValue(playerName)) {
            return "No players nearby for " + owner.getType().getDisplayName() + ".";
        }
        if (Node.isSelfPlayerValue(playerName)) {
            return "Local player unavailable for " + owner.getType().getDisplayName() + ".";
        }
        return "Player \"" + playerName + "\" is not nearby for " + owner.getType().getDisplayName() + ".";
    }

    static String noNearbyEntityMessage(Node owner) {
        return "No nearby entity found for " + owner.getType().getDisplayName() + ".";
    }

    static String unknownItemMessage(Node owner, String reference) {
        return "Unknown item \"" + reference + "\" for " + owner.getType().getDisplayName() + ".";
    }

    static String noDroppedItemMessage(Node owner, java.util.List<String> itemIds) {
        return "No dropped " + String.join(", ", itemIds) + " found for " + owner.getType().getDisplayName() + ".";
    }

    static String noBlocksDefinedMessage(Node owner) {
        return "No blocks defined on parameter for " + owner.getType().getDisplayName() + ".";
    }

    static String noNearbyBlockMessage(Node owner) {
        return "No nearby block found for " + owner.getType().getDisplayName() + ".";
    }

    static String noMatchingBlockMessage(Node owner) {
        return "No matching block from parameter found for " + owner.getType().getDisplayName() + ".";
    }

    static String noOpenBlockMessage(Node owner) {
        return "No open block found within range for " + owner.getType().getDisplayName() + ".";
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

    private static Optional<Entity> findNearestAnyEntity(MinecraftClient client, double range, String state) {
        double searchRadius = Math.max(1.0, range);
        Box searchBox = client.player.getBoundingBox().expand(searchRadius);
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : client.world.getOtherEntities(client.player, searchBox)) {
            if (entity == null || entity.isRemoved()) {
                continue;
            }
            if (!EntityStateOptions.matchesState(entity, state)) {
                continue;
            }
            double distance = entity.squaredDistanceTo(client.player);
            if (nearest == null || distance < nearestDistance) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static Optional<Vec3d> resolvedEntityPosition(Entity entity, String entityId, RuntimeParameterData data) {
        if (data != null) {
            data.targetEntity = entity;
            data.targetEntityId = entityId;
            data.targetBlockPos = entity.getBlockPos();
        }
        Vec3d entityPos = EntityCompatibilityBridge.getPos(entity);
        if (entityPos != null) {
            return Optional.of(entityPos);
        }
        return Optional.of(Vec3d.ofCenter(entity.getBlockPos()));
    }

    private static BlockPos resolveBlockPosition(Node parameterNode) {
        int x = Node.parseNodeInt(parameterNode, "X", 0);
        int y = Node.parseNodeInt(parameterNode, "Y", 0);
        int z = Node.parseNodeInt(parameterNode, "Z", 0);
        return new BlockPos(x, y, z);
    }

    private static Optional<String> resolveDirectionComparableString(Node owner, Node node) {
        String formatted = owner.formatRotationValues(node.exportParameterValues());
        if (!formatted.isEmpty()) {
            return Optional.of(formatted);
        }
        String direction = Node.getParameterString(node, "Direction");
        if (direction == null || direction.trim().isEmpty()) {
            direction = Node.getParameterString(node, "Side");
        }
        if (direction == null || direction.trim().isEmpty()) {
            direction = Node.getParameterString(node, "Face");
        }
        return direction == null || direction.trim().isEmpty() ? Optional.empty() : Optional.of(direction.trim());
    }

    private static Optional<String> resolveBlockFaceComparableString(Node owner, Node node) {
        String face = Node.getParameterString(node, "Face");
        if (face == null || face.trim().isEmpty()) {
            face = Node.getParameterString(node, "Side");
        }
        if (face == null || face.trim().isEmpty()) {
            face = Node.getParameterString(node, "Direction");
        }
        return face == null || face.trim().isEmpty() ? Optional.empty() : Optional.of(face.trim());
    }

    private static Optional<String> resolveMessageComparableString(Node owner, Node node) {
        String text = Node.getParameterString(node, "Text");
        if (text == null || text.trim().isEmpty()) {
            text = Node.getParameterString(node, "Message");
        }
        return text == null || text.trim().isEmpty() ? Optional.empty() : Optional.of(text.trim());
    }

    static Orientation applyDirection(String direction, float currentYaw, float currentPitch) {
        if (direction == null) {
            return new Orientation(currentYaw, currentPitch);
        }
        return switch (direction.trim().toLowerCase(Locale.ROOT)) {
            case "north" -> new Orientation(180.0F, 0.0F);
            case "south" -> new Orientation(0.0F, 0.0F);
            case "west" -> new Orientation(90.0F, 0.0F);
            case "east" -> new Orientation(-90.0F, 0.0F);
            case "up" -> new Orientation(currentYaw, -90.0F);
            case "down" -> new Orientation(currentYaw, 90.0F);
            default -> new Orientation(currentYaw, currentPitch);
        };
    }

    private static Orientation resolveNamedOrientation(NodeType parameterType, Node parameterNode, float currentYaw, float currentPitch) {
        if (parameterType == NodeType.PARAM_DIRECTION && parameterNode.isDirectionModeCardinal()) {
            String direction = Node.getParameterString(parameterNode, "Direction");
            return applyDirection(direction, currentYaw, currentPitch);
        }
        if (parameterType == NodeType.PARAM_BLOCK_FACE) {
            String direction = Node.getParameterString(parameterNode, "Face");
            if (direction == null || direction.trim().isEmpty()) {
                direction = Node.getParameterString(parameterNode, "Side");
            }
            return applyDirection(direction, currentYaw, currentPitch);
        }
        return new Orientation(currentYaw, currentPitch);
    }

    private static boolean isGotoLike(NodeType ownerType) {
        return ownerType == NodeType.GOTO || ownerType == NodeType.TRAVEL || ownerType == NodeType.GOAL;
    }

    private static boolean isCoordinateMode(NodeMode mode) {
        return mode == NodeMode.GOTO_XYZ
            || mode == NodeMode.GOTO_XZ
            || mode == NodeMode.GOAL_XYZ
            || mode == NodeMode.GOAL_XZ;
    }

    private static double defaultDirectionDistance(NodeType parameterType, Node parameterNode) {
        if (parameterType == NodeType.PARAM_DIRECTION) {
            return parameterNode.isDirectionModeExact() ? Node.DEFAULT_DIRECTION_DISTANCE : 1.0;
        }
        if (parameterType == NodeType.PARAM_BLOCK_FACE) {
            return 1.0;
        }
        return Node.DEFAULT_DIRECTION_DISTANCE;
    }

    private static Vec3d projectTarget(Vec3d origin, float yaw, float pitch, double distance) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double xDir = -Math.sin(yawRad) * Math.cos(pitchRad);
        double yDir = -Math.sin(pitchRad);
        double zDir = Math.cos(yawRad) * Math.cos(pitchRad);
        return origin.add(xDir * distance, yDir * distance, zDir * distance);
    }

    private static void applyCardinalDirection(Map<String, String> values, String direction) {
        String normalized = direction.trim().toLowerCase(Locale.ROOT);
        Double yaw = null;
        Double pitch = null;
        switch (normalized) {
            case "north":
                yaw = 180.0;
                break;
            case "south":
                yaw = 0.0;
                break;
            case "west":
                yaw = 90.0;
                break;
            case "east":
                yaw = -90.0;
                break;
            case "up":
                pitch = -90.0;
                break;
            case "down":
                pitch = 90.0;
                break;
            default:
                break;
        }
        if (yaw != null) {
            put(values, "Yaw", Double.toString(yaw));
        }
        if (pitch != null) {
            put(values, "Pitch", Double.toString(pitch));
        }
        put(values, "Side", direction);
        put(values, "Face", direction);
        put(values, "Text", direction);
        put(values, "Message", direction);
    }

    private static Map<String, String> copyIfPresent(Map<String, String> values, String sourceKey, String targetKey) {
        String value = values.get(sourceKey);
        if (value != null) {
            put(values, targetKey, value);
        }
        return values;
    }

    private static double parseNonNegativeDouble(String value, double defaultValue) {
        String trimmed = value == null ? "" : value.trim();
        double parsed = defaultValue;
        if (!trimmed.isEmpty()) {
            try {
                parsed = Double.parseDouble(trimmed);
            } catch (NumberFormatException ignored) {
                parsed = 0.0;
            }
        }
        return Math.max(0.0, parsed);
    }

    private static double durationUnitSeconds(NodeMode mode) {
        NodeMode durationMode = mode != null ? mode : NodeMode.WAIT_SECONDS;
        return switch (durationMode) {
            case WAIT_TICKS -> 0.05;
            case WAIT_MINUTES -> 60.0;
            case WAIT_HOURS -> 3600.0;
            case WAIT_SECONDS -> 1.0;
            default -> 1.0;
        };
    }

    static final class Orientation {
        final float yaw;
        final float pitch;

        Orientation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static NodeComparableBehavior stringComparable(ComparableStringResolver resolver) {
        return new NodeComparableBehavior() {
            @Override
            public Optional<String> resolveString(Node owner, Node node) {
                return resolver.resolve(owner, node);
            }
        };
    }

    private static NodeComparableBehavior numberComparable(ComparableNumberResolver resolver) {
        return new NodeComparableBehavior() {
            @Override
            public Optional<Double> resolveNumber(Node owner, Node node) {
                return resolver.resolve(owner, node);
            }
        };
    }

    @FunctionalInterface
    private interface ComparableStringResolver {
        Optional<String> resolve(Node owner, Node node);
    }

    @FunctionalInterface
    private interface ComparableNumberResolver {
        Optional<Double> resolve(Node owner, Node node);
    }

    private static void syncSingularAndPlural(Map<String, String> values, String singularKey, String pluralKey) {
        String singular = values.get(singularKey);
        String plural = values.get(pluralKey);
        if ((plural == null || plural.isEmpty()) && singular != null && !singular.isEmpty()) {
            put(values, pluralKey, singular);
            return;
        }
        if ((singular == null || singular.isEmpty()) && plural != null && !plural.isEmpty()) {
            String first = firstCommaSeparatedEntry(plural);
            if (first != null && !first.isEmpty()) {
                put(values, singularKey, first);
            }
        }
    }

    private static String firstCommaSeparatedEntry(String value) {
        for (String entry : value.split(",")) {
            String trimmed = entry == null ? null : entry.trim();
            if (trimmed != null && !trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    private static void put(Map<String, String> values, String key, String value) {
        values.put(key, value);
        values.put(Node.normalizeParameterKey(key), value);
    }

    private NodeBehaviorDefinitionRegistry() {
    }
}
