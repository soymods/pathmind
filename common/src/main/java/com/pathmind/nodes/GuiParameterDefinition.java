package com.pathmind.nodes;

import java.util.Map;

final class GuiParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_GUI)
            .parameterBehavior(GuiParameterDefinition::exportValues)
            .comparableBehavior(NodeBehaviorDefinitionSupport.stringComparable(GuiParameterDefinition::resolveComparableString))
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

    private static java.util.Optional<String> resolveComparableString(Node owner, Node node) {
        Map<String, String> values = node.exportParameterValues();
        String gui = owner.getRuntimeValue(values, "gui");
        if (gui.isEmpty()) {
            gui = owner.getRuntimeValue(values, "mode");
        }
        if (gui.isEmpty()) {
            gui = owner.getRuntimeValue(values, "guimode");
        }
        if (gui.isEmpty()) {
            gui = owner.getRuntimeValue(values, "selection");
        }
        return gui.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(gui.trim());
    }

    private GuiParameterDefinition() {
    }
}
