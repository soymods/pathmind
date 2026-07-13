package com.pathmind.nodes;

/**
 * Compatibility facade for node metadata.
 *
 * <p>New metadata should be added to {@link NodeCatalog}. This class remains so
 * existing call sites do not need to know where metadata is stored.
 */
final class NodeTypeDefinition {
    private NodeTypeDefinition() {
    }

    static NodeCategory getCategory(NodeType type) {
        return NodeCatalog.category(type);
    }

    static boolean hasParameters(NodeType type) {
        return NodeCatalog.hasParameters(type);
    }

    static boolean isDraggableFromSidebar(NodeType type) {
        return NodeCatalog.isDraggableFromSidebar(type);
    }

    static boolean requiresBaritone(NodeType type) {
        return NodeCatalog.requiresBaritone(type);
    }

    static boolean requiresUiUtils(NodeType type) {
        return NodeCatalog.requiresUiUtils(type);
    }
}
