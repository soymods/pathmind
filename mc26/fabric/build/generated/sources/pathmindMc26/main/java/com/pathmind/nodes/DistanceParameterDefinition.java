package com.pathmind.nodes;

import java.util.Optional;

final class DistanceParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_DISTANCE)
            .comparableBehavior(NodeBehaviorDefinitionSupport.numberComparable((owner, node) ->
                Optional.of(Node.parseNodeDouble(node, "Distance", 0.0))))
            .build();
    }

    private DistanceParameterDefinition() {
    }
}
