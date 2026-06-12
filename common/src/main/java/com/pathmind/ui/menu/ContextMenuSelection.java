package com.pathmind.ui.menu;

import com.pathmind.nodes.NodeType;

public final class ContextMenuSelection {
    private final NodeType nodeType;
    private final boolean openSearch;
    private final boolean createStickyNote;

    private ContextMenuSelection(NodeType nodeType, boolean openSearch, boolean createStickyNote) {
        this.nodeType = nodeType;
        this.openSearch = openSearch;
        this.createStickyNote = createStickyNote;
    }

    public static ContextMenuSelection forNode(NodeType nodeType) {
        return new ContextMenuSelection(nodeType, false, false);
    }

    public static ContextMenuSelection openSearch() {
        return new ContextMenuSelection(null, true, false);
    }

    public static ContextMenuSelection createStickyNote() {
        return new ContextMenuSelection(null, false, true);
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public boolean shouldOpenSearch() {
        return openSearch;
    }

    public boolean shouldCreateStickyNote() {
        return createStickyNote;
    }
}
