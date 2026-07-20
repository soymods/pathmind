package com.pathmind.ui.graph;

import java.util.List;
import java.util.Objects;

import com.pathmind.nodes.Node;
import com.pathmind.ui.menu.ContextMenuRenderer;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import org.lwjgl.glfw.GLFW;

/**
 * Owns the text-editing state machine for sticky notes: the active note, the
 * edit buffer, caret position/blink, and the deferred-save timer. Input events
 * (key/char) are routed here; persistence and cross-editor focus are delegated
 * to the {@link StickyNoteHost}. Rendering reads this state through the accessors
 * at the bottom.
 */
final class StickyNoteController {

    private static final long CARET_BLINK_INTERVAL_MS = 500L;
    private static final int MAX_CHARS = 4096;

    private final StickyNoteHost host;

    private Node editingNode = null;
    private String editBuffer = "";
    private String editOriginalValue = "";
    private long caretLastToggleTime = 0L;
    private boolean caretVisible = true;
    private int caretPosition = 0;
    private long deferredSaveAtMillis = 0L;
    private Node resizingStickyNote;
    private StickyNoteResizeCorner stickyNoteResizeCorner;
    private int stickyNoteResizeStartX;
    private int stickyNoteResizeStartY;
    private int stickyNoteResizeStartWidth;
    private int stickyNoteResizeStartHeight;

    StickyNoteController(StickyNoteHost host) {
        this.host = host;
    }

    boolean isEditing() {
        return editingNode != null;
    }

    void updateCaretBlink() {
        long now = System.currentTimeMillis();
        if (now - caretLastToggleTime >= CARET_BLINK_INTERVAL_MS) {
            caretVisible = !caretVisible;
            caretLastToggleTime = now;
        }
    }

    private void resetCaretBlink() {
        caretVisible = true;
        caretLastToggleTime = System.currentTimeMillis();
    }

    void startEditing(Node node) {
        if (node == null || !node.isStickyNote()) {
            stopEditing(false);
            return;
        }
        if (editingNode == node) {
            return;
        }

        stopEditing(true);
        host.stopOtherEditors();

        editingNode = node;
        editBuffer = node.getStickyNoteText();
        editOriginalValue = editBuffer;
        caretPosition = editBuffer.length();
        resetCaretBlink();
    }

    void stopEditing(boolean commit) {
        if (!isEditing()) {
            return;
        }
        if (commit) {
            commitEditBuffer(true);
        } else {
            editingNode.setStickyNoteText(editOriginalValue);
        }
        editingNode = null;
        editBuffer = "";
        editOriginalValue = "";
        caretPosition = 0;
        caretVisible = true;
    }

    private void commitEditBuffer(boolean saveNow) {
        if (!isEditing()) {
            return;
        }
        boolean changed = !Objects.equals(editingNode.getStickyNoteText(), editBuffer);
        if (!changed) {
            editOriginalValue = editBuffer;
            if (saveNow && host.isWorkspaceDirty()) {
                host.markWorkspaceDirty();
            }
            return;
        }
        editingNode.setStickyNoteText(editBuffer);
        editOriginalValue = editBuffer;
        if (saveNow) {
            host.markWorkspaceDirty();
        } else {
            markDirtyWithoutSaving();
        }
    }

    private void markDirtyWithoutSaving() {
        host.setWorkspaceDirty(true);
        host.invalidateValidation();
        host.invalidateRenderCaches();
        deferredSaveAtMillis = System.currentTimeMillis() + 750L;
    }

    void flushDeferredSaveIfDue() {
        if (deferredSaveAtMillis <= 0L || System.currentTimeMillis() < deferredSaveAtMillis) {
            return;
        }
        deferredSaveAtMillis = 0L;
        if (host.isWorkspaceDirty()) {
            host.save();
        }
    }

    /** Cancel a pending deferred save (e.g. because the host is saving now). */
    void cancelDeferredSave() {
        deferredSaveAtMillis = 0L;
    }

    /** Flush the current edit buffer into the active node without forcing an immediate save. */
    void commitPendingEdit() {
        commitEditBuffer(false);
    }

    boolean handleKeyPressed(int keyCode, int modifiers) {
        if (!isEditing()) {
            return false;
        }
        boolean controlHeld = host.isTextShortcutDown(modifiers);
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (caretPosition > 0 && !editBuffer.isEmpty()) {
                    editBuffer = editBuffer.substring(0, caretPosition - 1)
                        + editBuffer.substring(caretPosition);
                    caretPosition--;
                    resetCaretBlink();
                    commitEditBuffer(false);
                }
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (caretPosition < editBuffer.length()) {
                    editBuffer = editBuffer.substring(0, caretPosition)
                        + editBuffer.substring(caretPosition + 1);
                    resetCaretBlink();
                    commitEditBuffer(false);
                }
                return true;
            case GLFW.GLFW_KEY_LEFT:
                caretPosition = Math.max(0, caretPosition - 1);
                resetCaretBlink();
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                caretPosition = Math.min(editBuffer.length(), caretPosition + 1);
                resetCaretBlink();
                return true;
            case GLFW.GLFW_KEY_HOME:
                caretPosition = 0;
                resetCaretBlink();
                return true;
            case GLFW.GLFW_KEY_END:
                caretPosition = editBuffer.length();
                resetCaretBlink();
                return true;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                return insertText("\n");
            case GLFW.GLFW_KEY_ESCAPE:
                stopEditing(true);
                return true;
            case GLFW.GLFW_KEY_A:
                if (controlHeld) {
                    caretPosition = editBuffer.length();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_C:
                if (controlHeld) {
                    host.setClipboardText(editBuffer);
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_X:
                if (controlHeld) {
                    host.setClipboardText(editBuffer);
                    editBuffer = "";
                    caretPosition = 0;
                    commitEditBuffer(false);
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_V:
                if (controlHeld) {
                    return insertText(host.getClipboardText());
                }
                break;
            default:
                return false;
        }
        return false;
    }

    boolean handleCharTyped(char chr, int modifiers) {
        if (!isEditing()) {
            return false;
        }
        if (chr == '\r') {
            return false;
        }
        return insertText(String.valueOf(chr));
    }

    boolean isResizing() {
        return resizingStickyNote != null && stickyNoteResizeCorner != null;
    }

    boolean handleResizeHandleClick(Node node, int screenX, int screenY) {
        if (node == null || !node.isStickyNote()) {
            return false;
        }
        StickyNoteResizeCorner corner = getResizeCornerAt(node, screenX, screenY);
        if (corner == null) {
            return false;
        }
        stopEditing(true);
        host.beginDragOperation();
        resizingStickyNote = node;
        stickyNoteResizeCorner = corner;
        stickyNoteResizeStartX = node.getX();
        stickyNoteResizeStartY = node.getY();
        stickyNoteResizeStartWidth = node.getWidth();
        stickyNoteResizeStartHeight = node.getHeight();
        return true;
    }

    StickyNoteResizeCorner getResizeCornerAt(Node node, int screenX, int screenY) {
        if (node == null || !node.isStickyNote()) {
            return null;
        }
        int worldX = host.screenToWorldX(screenX);
        int worldY = host.screenToWorldY(screenY);
        return getResizeCornerAtWorld(node, worldX, worldY);
    }

    StickyNoteResizeCorner getResizeCornerAtWorld(Node node, int worldX, int worldY) {
        if (node == null || !node.isStickyNote()) {
            return null;
        }
        int size = node.getStickyNoteResizeHandleSize();
        int half = size / 2;
        if (ContextMenuRenderer.isPointInRect(worldX, worldY, node.getX() - half, node.getY() - half, size, size)) {
            return StickyNoteResizeCorner.TOP_LEFT;
        }
        if (ContextMenuRenderer.isPointInRect(worldX, worldY, node.getX() + node.getWidth() - half, node.getY() - half, size, size)) {
            return StickyNoteResizeCorner.TOP_RIGHT;
        }
        if (ContextMenuRenderer.isPointInRect(worldX, worldY, node.getX() - half, node.getY() + node.getHeight() - half, size, size)) {
            return StickyNoteResizeCorner.BOTTOM_LEFT;
        }
        if (ContextMenuRenderer.isPointInRect(worldX, worldY, node.getX() + node.getWidth() - half, node.getY() + node.getHeight() - half, size, size)) {
            return StickyNoteResizeCorner.BOTTOM_RIGHT;
        }
        return null;
    }

    void updateResize(int worldMouseX, int worldMouseY) {
        if (resizingStickyNote == null || stickyNoteResizeCorner == null) {
            return;
        }
        int left = stickyNoteResizeStartX;
        int top = stickyNoteResizeStartY;
        int right = stickyNoteResizeStartX + stickyNoteResizeStartWidth;
        int bottom = stickyNoteResizeStartY + stickyNoteResizeStartHeight;

        switch (stickyNoteResizeCorner) {
            case TOP_LEFT -> {
                left = Math.min(worldMouseX, right - 120);
                top = Math.min(worldMouseY, bottom - 84);
            }
            case TOP_RIGHT -> {
                right = Math.max(worldMouseX, left + 120);
                top = Math.min(worldMouseY, bottom - 84);
            }
            case BOTTOM_LEFT -> {
                left = Math.min(worldMouseX, right - 120);
                bottom = Math.max(worldMouseY, top + 84);
            }
            case BOTTOM_RIGHT -> {
                right = Math.max(worldMouseX, left + 120);
                bottom = Math.max(worldMouseY, top + 84);
            }
        }

        int newWidth = right - left;
        int newHeight = bottom - top;
        if (left != resizingStickyNote.getX() || top != resizingStickyNote.getY()
            || newWidth != resizingStickyNote.getWidth() || newHeight != resizingStickyNote.getHeight()) {
            host.markDragOperationChanged();
        }
        resizingStickyNote.setPosition(left, top);
        resizingStickyNote.setStickyNoteSize(newWidth, newHeight);
        host.invalidateHierarchyCache();
    }

    Node finishResize() {
        Node resized = resizingStickyNote;
        resizingStickyNote = null;
        stickyNoteResizeCorner = null;
        return resized;
    }

    void cancelResize() {
        resizingStickyNote = null;
        stickyNoteResizeCorner = null;
    }

    private boolean insertText(String text) {
        if (!isEditing() || text == null || text.isEmpty()) {
            return false;
        }
        int allowed = MAX_CHARS - editBuffer.length();
        if (allowed <= 0) {
            return true;
        }
        String safe = text.length() > allowed ? text.substring(0, allowed) : text;
        editBuffer = editBuffer.substring(0, caretPosition)
            + safe
            + editBuffer.substring(caretPosition);
        caretPosition += safe.length();
        resetCaretBlink();
        commitEditBuffer(false);
        return true;
    }

    // --- Rendering ---

    void render(GuiGraphics context, Font textRenderer, Node node, int x, int y, int width, int height,
                boolean isOverSidebar) {
        int paperColor = isOverSidebar ? UITheme.NODE_DIMMED_BG : 0xFFF3E28A;
        int headerColor = isOverSidebar ? UITheme.NODE_HEADER_DIMMED : 0xFFE2C65A;
        int borderColor = node.isSelected() ? host.selectedNodeAccentColor() : (isOverSidebar ? UITheme.BORDER_SUBTLE : 0xFFC2A748);
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : 0xFF3F3420;

        context.fill(x, y, x + width, y + height, paperColor);
        context.fill(x + 1, y + 1, x + width - 1, y + node.getStickyNoteHeaderHeight(), headerColor);
        DrawContextBridge.drawBorderInLayer(context, x, y, width, height, borderColor);

        int bodyLeft = node.getStickyNoteBodyLeft() - host.cameraX();
        int bodyTop = node.getStickyNoteBodyTop() - host.cameraY();
        int maxLines = Math.max(1, node.getStickyNoteBodyHeight() / Math.max(1, textRenderer.lineHeight + 1));
        List<String> lines = getDisplayLines(node, textRenderer, maxLines);
        for (int i = 0; i < lines.size(); i++) {
            int lineY = bodyTop + i * (textRenderer.lineHeight + 1);
            if (lineY + textRenderer.lineHeight > y + height - 4) {
                break;
            }
            host.drawNodeText(context, textRenderer, lines.get(i), bodyLeft, lineY, textColor);
        }

        if (editingNode == node) {
            updateCaretBlink();
            if (caretVisible) {
                StickyNoteCaretRenderPosition caretPos = getCaretRenderPosition(node, textRenderer, maxLines);
                if (caretPos != null) {
                    int caretY = bodyTop + caretPos.lineIndex * (textRenderer.lineHeight + 1);
                    int caretX = bodyLeft + textRenderer.width(caretPos.lineTextBeforeCaret);
                    UIStyleHelper.drawTextCaretAtBaseline(context, textRenderer, caretX, caretY + textRenderer.lineHeight - 1, x + width - 6, 0xFF000000);
                }
            }
        }

        if (node.isSelected()) {
            renderResizeHandle(context, node, StickyNoteResizeCorner.TOP_LEFT, borderColor);
            renderResizeHandle(context, node, StickyNoteResizeCorner.TOP_RIGHT, borderColor);
            renderResizeHandle(context, node, StickyNoteResizeCorner.BOTTOM_LEFT, borderColor);
            renderResizeHandle(context, node, StickyNoteResizeCorner.BOTTOM_RIGHT, borderColor);
        }
    }

    private List<String> getDisplayLines(Node node, Font textRenderer, int maxLines) {
        String source = editingNode == node ? editBuffer : node.getStickyNoteText();
        return StickyNoteTextLayout.wrapLines(source, textRenderer, Math.max(1, node.getStickyNoteBodyWidth()), maxLines);
    }

    private StickyNoteCaretRenderPosition getCaretRenderPosition(Node node, Font textRenderer, int maxLines) {
        if (!isEditing() || editingNode != node) {
            return null;
        }
        int caretOffset = Mth.clamp(caretPosition, 0, editBuffer.length());
        String textBeforeCaret = editBuffer.substring(0, caretOffset);
        List<String> wrappedBeforeCaret = StickyNoteTextLayout.wrapLines(
            textBeforeCaret,
            textRenderer,
            Math.max(1, node.getStickyNoteBodyWidth()),
            maxLines + 1);
        if (wrappedBeforeCaret.isEmpty()) {
            return new StickyNoteCaretRenderPosition(0, "");
        }
        int lineIndex = wrappedBeforeCaret.size() - 1;
        if (lineIndex >= maxLines) {
            return null;
        }
        return new StickyNoteCaretRenderPosition(lineIndex, wrappedBeforeCaret.get(lineIndex));
    }

    private void renderResizeHandle(GuiGraphics context, Node node, StickyNoteResizeCorner corner, int color) {
        int size = node.getStickyNoteResizeHandleSize();
        int left = switch (corner) {
            case TOP_LEFT, BOTTOM_LEFT -> node.getX() - host.cameraX() - size / 2;
            case TOP_RIGHT, BOTTOM_RIGHT -> node.getX() + node.getWidth() - host.cameraX() - size / 2;
        };
        int top = switch (corner) {
            case TOP_LEFT, TOP_RIGHT -> node.getY() - host.cameraY() - size / 2;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> node.getY() + node.getHeight() - host.cameraY() - size / 2;
        };
        context.fill(left, top, left + size, top + size, UITheme.TEXT_PRIMARY);
        DrawContextBridge.drawBorderInLayer(context, left, top, size, size, color);
    }

    private static final class StickyNoteCaretRenderPosition {
        final int lineIndex;
        final String lineTextBeforeCaret;

        StickyNoteCaretRenderPosition(int lineIndex, String lineTextBeforeCaret) {
            this.lineIndex = lineIndex;
            this.lineTextBeforeCaret = lineTextBeforeCaret;
        }
    }
}
