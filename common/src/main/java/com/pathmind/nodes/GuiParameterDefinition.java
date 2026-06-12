package com.pathmind.nodes;

import java.util.Map;

final class GuiParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_GUI)
            .parameterBehavior(GuiParameterDefinition::exportValues)
            .build();
    }

    private static Map<String, String> exportValues(Node node, Map<String, String> values) {
        String gui = values.get("GUI");
        if (gui != null) {
            NodeBehaviorDefinitionSupport.put(values, "Mode", gui);
            NodeBehaviorDefinitionSupport.put(values, "GuiMode", gui);
            NodeBehaviorDefinitionSupport.put(values, "Selection", gui);
        }
        return values;
    }

    private GuiParameterDefinition() {
    }
}
