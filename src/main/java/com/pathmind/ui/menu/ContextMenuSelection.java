package com.pathmind.ui.menu;

import com.pathmind.nodes.NodeType;

public final class ContextMenuSelection {
    private final NodeType nodeType;
    private final boolean openSearch;

    private ContextMenuSelection(NodeType nodeType, boolean openSearch) {
        this.nodeType = nodeType;
        this.openSearch = openSearch;
    }

    public static ContextMenuSelection forNode(NodeType nodeType) {
        return new ContextMenuSelection(nodeType, false);
    }

    public static ContextMenuSelection openSearch() {
        return new ContextMenuSelection(null, true);
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public boolean shouldOpenSearch() {
        return openSearch;
    }
}
