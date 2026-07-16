package com.pathmind.nodes;

import java.util.Map;
import java.util.Optional;

final class KeyParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_KEY)
            .parameterBehavior(KeyParameterDefinition::exportValues)
            .comparableBehavior(NodeBehaviorDefinitionSupport.stringComparable(KeyParameterDefinition::resolveComparableString))
            .build();
    }

    private static Map<String, String> exportValues(Node node, Map<String, String> values) {
        String key = values.get("Key");
        if (key != null) {
            NodeBehaviorDefinitionSupport.put(values, "Button", key);
            NodeBehaviorDefinitionSupport.put(values, "Input", key);
        }
        return values;
    }

    private static Optional<String> resolveComparableString(Node owner, Node node) {
        String key = Node.getParameterString(node, "Key");
        return key == null || key.trim().isEmpty() ? Optional.empty() : Optional.of(key.trim());
    }

    private KeyParameterDefinition() {
    }
}
