package com.pathmind.nodes;

import java.util.Map;

final class HandParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_HAND)
            .parameterBehavior(HandParameterDefinition::exportValues)
            .build();
    }

    private static Map<String, String> exportValues(Node node, Map<String, String> values) {
        String hand = values.get("Hand");
        if (hand != null) {
            NodeBehaviorDefinitionSupport.put(values, "SourceHand", hand);
            NodeBehaviorDefinitionSupport.put(values, "TargetHand", hand);
            NodeBehaviorDefinitionSupport.put(values, "SelectedHand", hand);
        }
        return values;
    }

    private HandParameterDefinition() {
    }
}
