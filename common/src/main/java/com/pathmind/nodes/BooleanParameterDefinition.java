package com.pathmind.nodes;

import java.util.Map;
import java.util.Optional;

final class BooleanParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_BOOLEAN)
            .parameterBehavior(BooleanParameterDefinition::exportValues)
            .build();
    }

    private static Map<String, String> exportValues(Node node, Map<String, String> values) {
        node.ensureBooleanParameters();
        String modeValue = node.isBooleanModeLiteral() ? "literal" : "variable";
        NodeBehaviorDefinitionSupport.put(values, "Mode", modeValue);
        NodeParameter variableParameter = node.getParameter("Variable");
        if (variableParameter != null) {
            NodeBehaviorDefinitionSupport.put(values, "Variable", variableParameter.getStringValue());
        }
        Optional<Boolean> resolvedToggle = node.resolveBooleanNodeValue(node);
        String toggle = resolvedToggle.map(String::valueOf).orElseGet(() -> values.get("Toggle"));
        if (toggle == null) {
            toggle = values.get(Node.normalizeParameterKey("Toggle"));
        }
        if (toggle != null) {
            NodeBehaviorDefinitionSupport.put(values, "Active", toggle);
            NodeBehaviorDefinitionSupport.put(values, "Enabled", toggle);
            NodeBehaviorDefinitionSupport.put(values, "Toggle", toggle);
        }
        return values;
    }

    private BooleanParameterDefinition() {
    }
}
