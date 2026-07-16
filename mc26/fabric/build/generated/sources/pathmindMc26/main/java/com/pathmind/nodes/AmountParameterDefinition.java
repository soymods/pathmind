package com.pathmind.nodes;

import java.util.Map;
import java.util.Optional;

final class AmountParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_AMOUNT)
            .parameterBehavior(AmountParameterDefinition::exportValues)
            .comparableBehavior(NodeBehaviorDefinitionSupport.numberComparable((owner, node) ->
                Optional.of(Node.parseNodeDouble(node, "Amount", 0.0))))
            .build();
    }

    private static Map<String, String> exportValues(Node node, Map<String, String> values) {
        String amount = values.get("Amount");
        if (amount != null) {
            NodeBehaviorDefinitionSupport.put(values, "Count", amount);
            NodeBehaviorDefinitionSupport.put(values, "Threshold", amount);
            NodeBehaviorDefinitionSupport.put(values, "Value", amount);
        }
        return values;
    }

    private AmountParameterDefinition() {
    }
}
