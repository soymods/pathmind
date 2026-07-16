package com.pathmind.nodes;

/**
 * Editor layout state for a node.
 *
 * <p>This keeps GUI-only position and dimensions separate from the logical node
 * identity, parameters, attachments, and execution state that still live on
 * Node during the migration.
 */
final class NodeLayoutState {
    private int x;
    private int y;
    private int width;
    private int height;
    private int stickyNoteWidthOverride;
    private int stickyNoteHeightOverride;
    private int messageFieldContentWidthOverride;
    private int parameterFieldWidthOverride;
    private int coordinateFieldWidthOverride;
    private int amountFieldWidthOverride;
    private int stopTargetFieldWidthOverride;
    private int variableFieldWidthOverride;

    NodeLayoutState(int x, int y, int defaultStickyNoteWidth, int defaultStickyNoteHeight) {
        this.x = x;
        this.y = y;
        this.stickyNoteWidthOverride = defaultStickyNoteWidth;
        this.stickyNoteHeightOverride = defaultStickyNoteHeight;
    }

    int getX() {
        return x;
    }

    int getY() {
        return y;
    }

    void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }

    void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    boolean containsPoint(int pointX, int pointY) {
        return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
    }

    int getStickyNoteWidthOverride() {
        return stickyNoteWidthOverride;
    }

    int getStickyNoteHeightOverride() {
        return stickyNoteHeightOverride;
    }

    void setStickyNoteSize(int width, int height) {
        this.stickyNoteWidthOverride = width;
        this.stickyNoteHeightOverride = height;
    }

    int getMessageFieldContentWidthOverride() {
        return messageFieldContentWidthOverride;
    }

    void setMessageFieldContentWidthOverride(int messageFieldContentWidthOverride) {
        this.messageFieldContentWidthOverride = messageFieldContentWidthOverride;
    }

    void clearMessageFieldContentWidthOverride() {
        this.messageFieldContentWidthOverride = 0;
    }

    int getParameterFieldWidthOverride() {
        return parameterFieldWidthOverride;
    }

    void setParameterFieldWidthOverride(int parameterFieldWidthOverride) {
        this.parameterFieldWidthOverride = parameterFieldWidthOverride;
    }

    int getCoordinateFieldWidthOverride() {
        return coordinateFieldWidthOverride;
    }

    void setCoordinateFieldWidthOverride(int coordinateFieldWidthOverride) {
        this.coordinateFieldWidthOverride = coordinateFieldWidthOverride;
    }

    int getAmountFieldWidthOverride() {
        return amountFieldWidthOverride;
    }

    void setAmountFieldWidthOverride(int amountFieldWidthOverride) {
        this.amountFieldWidthOverride = amountFieldWidthOverride;
    }

    int getStopTargetFieldWidthOverride() {
        return stopTargetFieldWidthOverride;
    }

    void setStopTargetFieldWidthOverride(int stopTargetFieldWidthOverride) {
        this.stopTargetFieldWidthOverride = stopTargetFieldWidthOverride;
    }

    int getVariableFieldWidthOverride() {
        return variableFieldWidthOverride;
    }

    void setVariableFieldWidthOverride(int variableFieldWidthOverride) {
        this.variableFieldWidthOverride = variableFieldWidthOverride;
    }
}
