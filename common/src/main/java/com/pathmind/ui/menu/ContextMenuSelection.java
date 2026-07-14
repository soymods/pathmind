package com.pathmind.ui.menu;

import com.pathmind.nodes.NodeType;

public final class ContextMenuSelection {
    private final NodeType nodeType;
    private final boolean openSearch;
    private final boolean createStickyNote;
    private final String customNodePresetName;

    private ContextMenuSelection(NodeType nodeType, boolean openSearch, boolean createStickyNote, String customNodePresetName) {
        this.nodeType = nodeType;
        this.openSearch = openSearch;
        this.createStickyNote = createStickyNote;
        this.customNodePresetName = customNodePresetName;
    }

    public static ContextMenuSelection forNode(NodeType nodeType) {
        return new ContextMenuSelection(nodeType, false, false, null);
    }

    public static ContextMenuSelection forCustomNode(String presetName) {
        return new ContextMenuSelection(NodeType.CUSTOM_NODE, false, false, presetName);
    }

    public static ContextMenuSelection openSearch() {
        return new ContextMenuSelection(null, true, false, null);
    }

    public static ContextMenuSelection createStickyNote() {
        return new ContextMenuSelection(null, false, true, null);
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

    public String getCustomNodePresetName() {
        return customNodePresetName;
    }
}
