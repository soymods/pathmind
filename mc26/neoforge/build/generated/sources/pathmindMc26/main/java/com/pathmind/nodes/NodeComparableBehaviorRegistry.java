package com.pathmind.nodes;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

final class NodeComparableBehaviorRegistry {
    private static final Map<NodeType, NodeComparableBehavior> BEHAVIORS = new EnumMap<>(NodeType.class);

    static {
        for (NodeType type : NodeType.values()) {
            directDefinition(type).ifPresent(behavior -> BEHAVIORS.put(type, behavior));
        }
    }

    static NodeComparableBehavior get(NodeType type) {
        return BEHAVIORS.get(type);
    }

    static Map<NodeType, NodeComparableBehavior> snapshot() {
        return new EnumMap<>(BEHAVIORS);
    }

    private static Optional<NodeComparableBehavior> directDefinition(NodeType type) {
        return SensorComparableDefinitions.definition(type)
            .or(() -> OperatorComparableDefinitions.definition(type));
    }

    private NodeComparableBehaviorRegistry() {
    }
}
