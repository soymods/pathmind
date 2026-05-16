package com.pathmind.nodes;

import java.util.Map;
import java.util.Optional;

final class MouseButtonParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_MOUSE_BUTTON)
            .parameterBehavior(MouseButtonParameterDefinition::exportValues)
            .comparableBehavior(NodeBehaviorDefinitionSupport.stringComparable(MouseButtonParameterDefinition::resolveComparableString))
            .build();
    }

    private static Map<String, String> exportValues(Node node, Map<String, String> values) {
        String mouseButton = values.get("MouseButton");
        if (mouseButton != null) {
            NodeBehaviorDefinitionSupport.put(values, "Button", mouseButton);
            NodeBehaviorDefinitionSupport.put(values, "Input", mouseButton);
        }
        return values;
    }

    private static Optional<String> resolveComparableString(Node owner, Node node) {
        String mouseButton = Node.getParameterString(node, "MouseButton");
        return mouseButton == null || mouseButton.trim().isEmpty() ? Optional.empty() : Optional.of(mouseButton.trim());
    }

    private MouseButtonParameterDefinition() {
    }
}
