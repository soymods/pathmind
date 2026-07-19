package com.pathmind.ui.graph;

/**
 * Downward-only services that {@link StickyNoteController} needs from its host
 * editor ({@link NodeGraph}). The controller fully owns sticky-note text-editing
 * state; it never reaches back into host state except through these methods.
 */
interface StickyNoteHost {
    /** Stop every other inline editor (coordinate, amount, variable, parameter, message, ...) before a sticky note takes focus. */
    void stopOtherEditors();

    /** Mark the workspace dirty and persist as usual. */
    void markWorkspaceDirty();

    boolean isWorkspaceDirty();

    void setWorkspaceDirty(boolean dirty);

    void invalidateValidation();

    void invalidateRenderCaches();

    /** Persist the workspace now. */
    boolean save();

    /** Whether the platform text-shortcut modifier (ctrl/cmd) is held. */
    boolean isTextShortcutDown(int modifiers);

    String getClipboardText();

    void setClipboardText(String text);
}
