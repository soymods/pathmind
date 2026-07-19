package com.pathmind.ui.graph;

import java.util.Objects;

import com.pathmind.nodes.Node;

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

    // --- Read accessors for rendering (NodeGraph renders sticky notes) ---

    Node getEditingNode() {
        return editingNode;
    }

    String getEditBuffer() {
        return editBuffer;
    }

    int getCaretPosition() {
        return caretPosition;
    }

    boolean isCaretVisible() {
        return caretVisible;
    }
}
