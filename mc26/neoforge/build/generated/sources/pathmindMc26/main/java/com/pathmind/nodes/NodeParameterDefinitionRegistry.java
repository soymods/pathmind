package com.pathmind.nodes;

import java.util.List;

/**
 * Compatibility facade for default node parameter templates.
 *
 * <p>New node defaults belong in {@link NodeCatalog}. This class remains while
 * older initialization and completeness checks still call the registry name.
 */
final class NodeParameterDefinitionRegistry {
    private NodeParameterDefinitionRegistry() {
    }

    static void initialize(List<NodeParameter> parameters, NodeType type, NodeMode mode) {
        NodeCatalog.initializeParameters(parameters, type, mode);
    }

    static boolean hasDefinitions(NodeType type) {
        return NodeCatalog.hasParameterDefinitions(type);
    }

    static boolean hasDefinitions(NodeMode mode) {
        return NodeCatalog.hasParameterDefinitions(mode);
    }
}
