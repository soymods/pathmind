package com.pathmind.ui.graph;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Downward-only services that {@link StickyNoteController} needs from its host
 * editor ({@link NodeGraph}). The controller fully owns sticky-note text-editing
 * state; it never reaches back into host state except through these methods.
 */
interface StickyNoteHost {
    /** Camera scroll offset, world-to-screen. */
    int cameraX();

    int cameraY();

    /** Draw node body text using the editor's shared text renderer/layer. */
    void drawNodeText(GuiGraphics context, Font renderer, String text, int x, int y, int color);

    /** Accent color for the border of the currently-selected node. */
    int selectedNodeAccentColor();

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
