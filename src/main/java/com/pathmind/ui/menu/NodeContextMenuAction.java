package com.pathmind.ui.menu;

public enum NodeContextMenuAction {
    COPY("Copy"),
    DUPLICATE("Duplicate"),
    PASTE("Paste"),
    DELETE("Delete");

    private final String label;

    NodeContextMenuAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
