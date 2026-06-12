package com.pathmind.nodes;

import java.util.Optional;

final class MessageParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_MESSAGE)
            .parameterBehavior((node, values) -> NodeBehaviorDefinitionSupport.copyIfPresent(values, "Text", "Message"))
            .comparableBehavior(NodeBehaviorDefinitionSupport.stringComparable(MessageParameterDefinition::resolveComparableString))
            .build();
    }

    private static Optional<String> resolveComparableString(Node owner, Node node) {
        String text = Node.getParameterString(node, "Text");
        if (text == null || text.trim().isEmpty()) {
            text = Node.getParameterString(node, "Message");
        }
        return text == null || text.trim().isEmpty() ? Optional.empty() : Optional.of(text.trim());
    }

    private MessageParameterDefinition() {
    }
}
