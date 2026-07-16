package com.pathmind.nodes;

import java.util.Map;

final class DurationParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_DURATION)
            .parameterBehavior(DurationParameterDefinition::exportValues)
            .build();
    }

    private static Map<String, String> exportValues(Node node, Map<String, String> values) {
        String duration = values.get("Duration");
        if (duration != null) {
            String secondsValue = Double.toString(
                NodeBehaviorDefinitionSupport.parseNonNegativeDouble(duration, 1.0)
                    * NodeBehaviorDefinitionSupport.durationUnitSeconds(node.getMode()));
            NodeBehaviorDefinitionSupport.put(values, "Duration", secondsValue);
            NodeBehaviorDefinitionSupport.put(values, "IntervalSeconds", secondsValue);
            NodeBehaviorDefinitionSupport.put(values, "WaitSeconds", secondsValue);
            NodeBehaviorDefinitionSupport.put(values, "DurationSeconds", secondsValue);
        }
        return values;
    }

    private DurationParameterDefinition() {
    }
}
