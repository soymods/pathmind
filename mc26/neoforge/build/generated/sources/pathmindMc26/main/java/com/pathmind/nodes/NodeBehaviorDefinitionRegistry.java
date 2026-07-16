package com.pathmind.nodes;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

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
        return NodeTargetParameterDefinitions.definition(type)
            .or(() -> NodePositionParameterDefinitions.definition(type))
            .or(() -> NodeScalarParameterDefinitions.definition(type));
    }

    static String playerSearchFailureMessage(Node owner, String playerName) {
        return NodeBehaviorDefinitionSupport.playerSearchFailureMessage(owner, playerName);
    }

    static String noNearbyEntityMessage(Node owner) {
        return NodeBehaviorDefinitionSupport.noNearbyEntityMessage(owner);
    }

    static String unknownItemMessage(Node owner, String reference) {
        return NodeBehaviorDefinitionSupport.unknownItemMessage(owner, reference);
    }

    static String noDroppedItemMessage(Node owner, java.util.List<String> itemIds) {
        return NodeBehaviorDefinitionSupport.noDroppedItemMessage(owner, itemIds);
    }

    static String noBlocksDefinedMessage(Node owner) {
        return NodeBehaviorDefinitionSupport.noBlocksDefinedMessage(owner);
    }

    static String noNearbyBlockMessage(Node owner) {
        return NodeBehaviorDefinitionSupport.noNearbyBlockMessage(owner);
    }

    static String noMatchingBlockMessage(Node owner) {
        return NodeBehaviorDefinitionSupport.noMatchingBlockMessage(owner);
    }

    static String noOpenBlockMessage(Node owner) {
        return NodeBehaviorDefinitionSupport.noOpenBlockMessage(owner);
    }

    static Orientation applyDirection(String direction, float currentYaw, float currentPitch) {
        NodeBehaviorDefinitionSupport.Orientation orientation =
            NodePositionParameterDefinitions.applyDirection(direction, currentYaw, currentPitch);
        return new Orientation(orientation.yaw, orientation.pitch);
    }

    static final class Orientation {
        final float yaw;
        final float pitch;

        Orientation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private NodeBehaviorDefinitionRegistry() {
    }
}
