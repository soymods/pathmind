package com.pathmind.nodes;

import java.util.Map;

final class VillagerTradeParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_VILLAGER_TRADE)
            .parameterBehavior(VillagerTradeParameterDefinition::exportValues)
            .build();
    }

    private static Map<String, String> exportValues(Node node, Map<String, String> values) {
        String item = values.get("Item");
        if (item != null && !item.isEmpty()) {
            NodeBehaviorDefinitionSupport.put(values, "Items", item);
        }
        return values;
    }

    private VillagerTradeParameterDefinition() {
    }
}
