package com.pathmind.ui.graph;

import net.minecraft.util.Mth;

import org.lwjgl.glfw.GLFW;

/**
 * A reusable single-line-ish text editing engine: owns an edit buffer, caret,
 * and selection range, and implements the shared key/char mechanics (caret
 * movement, shift-selection, word-wise backspace, clipboard, insert) that every
 * inline field editor in {@link NodeGraph} previously duplicated.
 *
 * <p>Editor-specific policy — where the value loads from, how it commits, input
 * filtering, and what to recompute after a change — is supplied per use via
 * {@link Policy}. Shared host services (clipboard, shortcut modifier, word
 * boundaries) come from {@link Host}.
 */
final class InlineTextEditor {

    interface Host {
        boolean isTextShortcutDown(int modifiers);

        String getClipboardText();

        void setClipboardText(String text);

        int findPreviousWordBoundary(String text, int fromPosition);
    }

    interface Policy {
        /** Recompute anything derived from the buffer (field content width, previews, ...). */
        void onBufferChanged();

        /** Filter text about to be inserted (e.g. numeric-only fields). Default: unchanged. */
        default String filterInsert(String text) {
            return text;
        }

        /** Enter / keypad-enter pressed. */
        void onEnter();

        /** Escape pressed. */
        void onEscape();
    }

    private static final long CARET_BLINK_INTERVAL_MS = 500L;

    private final Host host;

    private String buffer = "";
    private int caretPosition = 0;
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private int selectionAnchor = -1;
    private boolean caretVisible = true;
    private long caretLastToggleTime = 0L;

    InlineTextEditor(Host host) {
        this.host = host;
    }

    // --- State access (used by the owning editor's start/stop/commit policy) ---

    String getBuffer() {
        return buffer;
    }

    void setBuffer(String value) {
        buffer = value == null ? "" : value;
    }

    int getCaretPosition() {
        return caretPosition;
    }

    boolean isCaretVisible() {
        return caretVisible;
    }

    int getSelectionStart() {
        return selectionStart;
    }

    int getSelectionEnd() {
        return selectionEnd;
    }

    boolean hasSelection() {
        return selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd;
    }

    /** Load a value and place the caret, clearing any selection. */
    void begin(String initialBuffer, int caret) {
        buffer = initialBuffer == null ? "" : initialBuffer;
        caretPosition = Mth.clamp(caret, 0, buffer.length());
        selectionAnchor = -1;
        resetSelectionRange();
        resetCaretBlink();
    }

    /** Reset to the empty, not-editing state. */
    void clear() {
        buffer = "";
        caretPosition = 0;
        caretVisible = true;
        selectionAnchor = -1;
        resetSelectionRange();
    }

    // --- Caret blink ---

    void updateCaretBlink() {
        long now = System.currentTimeMillis();
        if (now - caretLastToggleTime >= CARET_BLINK_INTERVAL_MS) {
            caretVisible = !caretVisible;
            caretLastToggleTime = now;
        }
    }

    void resetCaretBlink() {
        caretVisible = true;
        caretLastToggleTime = System.currentTimeMillis();
    }

    // --- Caret / selection movement ---

    private void resetSelectionRange() {
        selectionStart = -1;
        selectionEnd = -1;
    }

    void setCaretPosition(int position) {
        caretPosition = Mth.clamp(position, 0, buffer.length());
        selectionAnchor = -1;
        resetSelectionRange();
        resetCaretBlink();
    }

    void moveCaretTo(int position, boolean extendSelection) {
        position = Mth.clamp(position, 0, buffer.length());
        if (extendSelection) {
            if (selectionAnchor == -1) {
                selectionAnchor = caretPosition;
            }
            int start = Math.min(selectionAnchor, position);
            int end = Math.max(selectionAnchor, position);
            if (start == end) {
                resetSelectionRange();
            } else {
                selectionStart = start;
                selectionEnd = end;
            }
        } else {
            selectionAnchor = -1;
            resetSelectionRange();
        }
        caretPosition = position;
        resetCaretBlink();
    }

    void selectAll() {
        selectionAnchor = 0;
        if (buffer.isEmpty()) {
            resetSelectionRange();
        } else {
            selectionStart = 0;
            selectionEnd = buffer.length();
        }
        caretPosition = buffer.length();
        resetCaretBlink();
    }

    // --- Mutations ---

    private boolean deleteSelection(Policy policy) {
        if (!hasSelection()) {
            return false;
        }
        buffer = buffer.substring(0, selectionStart) + buffer.substring(selectionEnd);
        setCaretPosition(selectionStart);
        policy.onBufferChanged();
        return true;
    }

    private void copySelection() {
        if (!hasSelection()) {
            return;
        }
        host.setClipboardText(buffer.substring(selectionStart, selectionEnd));
    }

    private void cutSelection(Policy policy) {
        if (!hasSelection()) {
            return;
        }
        copySelection();
        deleteSelection(policy);
    }

    boolean insert(String text, Policy policy) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String filtered = policy.filterInsert(text.replace("\r", "").replace("\n", ""));
        if (filtered.isEmpty()) {
            return false;
        }
        String working = buffer;
        int caret = caretPosition;
        if (hasSelection()) {
            working = working.substring(0, selectionStart) + working.substring(selectionEnd);
            caret = selectionStart;
        }
        boolean inserted = false;
        for (int i = 0; i < filtered.length(); i++) {
            char c = filtered.charAt(i);
            working = working.substring(0, caret) + c + working.substring(caret);
            caret++;
            inserted = true;
        }
        if (!inserted) {
            return false;
        }
        buffer = working;
        setCaretPosition(caret);
        policy.onBufferChanged();
        return true;
    }

    // --- Input dispatch ---

    boolean handleKeyPressed(int keyCode, int modifiers, Policy policy) {
        boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean controlHeld = host.isTextShortcutDown(modifiers);
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (deleteSelection(policy)) {
                    return true;
                }
                if (controlHeld && caretPosition > 0) {
                    int deleteToPos = host.findPreviousWordBoundary(buffer, caretPosition);
                    buffer = buffer.substring(0, deleteToPos) + buffer.substring(caretPosition);
                    setCaretPosition(deleteToPos);
                    policy.onBufferChanged();
                } else if (caretPosition > 0 && !buffer.isEmpty()) {
                    buffer = buffer.substring(0, caretPosition - 1) + buffer.substring(caretPosition);
                    setCaretPosition(caretPosition - 1);
                    policy.onBufferChanged();
                }
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (deleteSelection(policy)) {
                    return true;
                }
                if (caretPosition < buffer.length()) {
                    buffer = buffer.substring(0, caretPosition) + buffer.substring(caretPosition + 1);
                    setCaretPosition(caretPosition);
                    policy.onBufferChanged();
                }
                return true;
            case GLFW.GLFW_KEY_LEFT:
                moveCaretTo(caretPosition - 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                moveCaretTo(caretPosition + 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_HOME:
                moveCaretTo(0, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_END:
                moveCaretTo(buffer.length(), shiftHeld);
                return true;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                policy.onEnter();
                return true;
            case GLFW.GLFW_KEY_ESCAPE:
                policy.onEscape();
                return true;
            case GLFW.GLFW_KEY_A:
                if (controlHeld) {
                    selectAll();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_C:
                if (controlHeld) {
                    copySelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_X:
                if (controlHeld) {
                    cutSelection(policy);
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_V:
                if (controlHeld) {
                    insert(host.getClipboardText(), policy);
                    return true;
                }
                break;
            default:
                return false;
        }
        return false;
    }

    boolean handleCharTyped(char chr, Policy policy) {
        if (chr == '\n' || chr == '\r') {
            return false;
        }
        return insert(String.valueOf(chr), policy);
    }
}
