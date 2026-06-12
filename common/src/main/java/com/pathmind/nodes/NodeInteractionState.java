package com.pathmind.nodes;

/**
 * Editor interaction state for a node.
 */
final class NodeInteractionState {
    private boolean selected;
    private boolean dragging;
    private boolean socketsHidden;
    private int dragOffsetX;
    private int dragOffsetY;

    boolean isSelected() {
        return selected;
    }

    void setSelected(boolean selected) {
        this.selected = selected;
    }

    boolean isDragging() {
        return dragging;
    }

    void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    boolean areSocketsHidden() {
        return socketsHidden;
    }

    void setSocketsHidden(boolean socketsHidden) {
        this.socketsHidden = socketsHidden;
    }

    int getDragOffsetX() {
        return dragOffsetX;
    }

    void setDragOffsetX(int dragOffsetX) {
        this.dragOffsetX = dragOffsetX;
    }

    int getDragOffsetY() {
        return dragOffsetY;
    }

    void setDragOffsetY(int dragOffsetY) {
        this.dragOffsetY = dragOffsetY;
    }
}
