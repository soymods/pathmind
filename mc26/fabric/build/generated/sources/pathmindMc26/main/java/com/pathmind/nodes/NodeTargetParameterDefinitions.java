package com.pathmind.nodes;

import java.util.Optional;

final class NodeTargetParameterDefinitions {
    static Optional<NodeBehaviorDefinition> definition(NodeType type) {
        return switch (type) {
            case PARAM_ITEM -> Optional.of(ItemParameterDefinition.create());
            case PARAM_BLOCK -> Optional.of(BlockParameterDefinition.create());
            case PARAM_ENTITY -> Optional.of(EntityParameterDefinition.create());
            case PARAM_PLAYER -> Optional.of(PlayerParameterDefinition.create());
            case LIST_ITEM -> Optional.of(PlayerParameterDefinition.listItemDefinition());
            default -> Optional.empty();
        };
    }

    private NodeTargetParameterDefinitions() {
    }
}
