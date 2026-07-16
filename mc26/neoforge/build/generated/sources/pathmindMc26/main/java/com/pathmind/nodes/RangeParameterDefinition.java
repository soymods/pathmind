package com.pathmind.nodes;

import java.util.Map;

final class RangeParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_RANGE)
            .parameterBehavior(RangeParameterDefinition::exportValues)
            .build();
    }

    private static Map<String, String> exportValues(Node node, Map<String, String> values) {
        String range = values.get("Range");
        if (range != null) {
            NodeBehaviorDefinitionSupport.put(values, "Distance", range);
            NodeBehaviorDefinitionSupport.put(values, "Radius", range);
        }
        return values;
    }

    private RangeParameterDefinition() {
    }
}
