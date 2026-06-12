package com.pathmind.nodes;

import java.util.List;

final class NodeParameterDefaults {
    private NodeParameterDefaults() {
    }

    static void initialize(List<NodeParameter> parameters, NodeType type, NodeMode mode) {
        NodeParameterDefinitionRegistry.initialize(parameters, type, mode);
    }
}
