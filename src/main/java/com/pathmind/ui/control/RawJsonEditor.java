package com.pathmind.ui.control;

import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class RawJsonEditor {
    private static final int PANEL_PADDING = 12;
    private static final int HEADER_HEIGHT = 28;
    private static final int STATUS_HEIGHT = 18;
    private static final int GUTTER_PADDING = 8;
    private static final int TEXT_PADDING = 8;
    private static final int SCROLLBAR_THICKNESS = 4;
    private static final int SCROLLBAR_MARGIN = 3;
    private static final int MIN_SCROLLBAR_THUMB_SIZE = 18;

    private int x;
    private int y;
    private int width;
    private int height;
    private String text = "";
    private int caretPosition = 0;
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private int selectionAnchor = -1;
    private int preferredColumn = -1;
    private int verticalScroll = 0;
    private int horizontalScroll = 0;
    private boolean draggingSelection = false;
    private boolean draggingVerticalScrollbar = false;
    private boolean draggingHorizontalScrollbar = false;
    private int scrollbarDragOffset = 0;
    private long caretBlinkLastToggleMs = 0L;
    private boolean caretVisible = true;
    private String statusMessage = "";
    private int statusColor = UITheme.TEXT_SECONDARY;
    private Runnable textChangedListener;
    private final Deque<EditorState> undoStack = new ArrayDeque<>();
    private final Deque<EditorState> redoStack = new ArrayDeque<>();

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
        this.caretPosition = MathHelper.clamp(this.caretPosition, 0, this.text.length());
        clearSelection();
        preferredColumn = -1;
        verticalScroll = 0;
        horizontalScroll = 0;
        undoStack.clear();
        redoStack.clear();
        resetCaretBlink();
    }

    public String getText() {
        return text;
    }

    public void setStatus(String statusMessage, int statusColor) {
        this.statusMessage = statusMessage == null ? "" : statusMessage;
        this.statusColor = statusColor;
    }

    public void clearStatus() {
        this.statusMessage = "";
        this.statusColor = UITheme.TEXT_SECONDARY;
    }

    public void setTextChangedListener(Runnable textChangedListener) {
        this.textChangedListener = textChangedListener;
    }

    public boolean undo(TextRenderer textRenderer) {
        if (undoStack.isEmpty()) {
            return false;
        }
        redoStack.push(captureState());
        restoreState(undoStack.pop(), textRenderer);
        return true;
    }

    public boolean redo(TextRenderer textRenderer) {
        if (redoStack.isEmpty()) {
            return false;
        }
        undoStack.push(captureState());
        restoreState(redoStack.pop(), textRenderer);
        return true;
    }

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        updateCaretBlink();
        UIStyleHelper.drawBeveledPanel(
            context,
            x,
            y,
            width,
            height,
            UITheme.BACKGROUND_SECTION,
            UITheme.BORDER_DEFAULT,
            UITheme.PANEL_INNER_BORDER
        );

        int headerBottom = y + HEADER_HEIGHT;
        context.drawTextWithShadow(textRenderer, "Raw JSON", x + PANEL_PADDING, y + 9, UITheme.TEXT_PRIMARY);
        if (!statusMessage.isEmpty()) {
            int statusY = headerBottom + 4;
            context.drawTextWithShadow(textRenderer, statusMessage, x + PANEL_PADDING, statusY, statusColor);
        }

        int editorX = x + PANEL_PADDING;
        int editorY = y + HEADER_HEIGHT + STATUS_HEIGHT;
        int editorWidth = Math.max(0, width - PANEL_PADDING * 2);
        int editorHeight = Math.max(0, height - HEADER_HEIGHT - STATUS_HEIGHT - PANEL_PADDING);
        context.fill(editorX, editorY, editorX + editorWidth, editorY + editorHeight, UITheme.BACKGROUND_INPUT);
        DrawContextBridge.drawBorderInLayer(context, editorX, editorY, editorWidth, editorHeight, UITheme.BORDER_HIGHLIGHT);

        int gutterWidth = getGutterWidth(textRenderer);
        int lineHeight = textRenderer.fontHeight + 2;
        int textAreaX = editorX + gutterWidth;
        int textAreaY = editorY + TEXT_PADDING;
        int textAreaWidth = Math.max(1, editorWidth - gutterWidth - TEXT_PADDING);
        int visibleLines = Math.max(1, (editorHeight - TEXT_PADDING * 2) / lineHeight);
        int firstLine = Math.max(0, verticalScroll / lineHeight);
        int lineOffsetY = -(verticalScroll % lineHeight);

        context.fill(editorX + 1, editorY + 1, editorX + gutterWidth - 1, editorY + editorHeight - 1, UITheme.BACKGROUND_TERTIARY);
        context.enableScissor(editorX + 1, editorY + 1, editorX + editorWidth - 1, editorY + editorHeight - 1);

        List<String> lines = getLines();
        for (int visualLine = 0; visualLine <= visibleLines; visualLine++) {
            int lineIndex = firstLine + visualLine;
            if (lineIndex >= lines.size()) {
                break;
            }
            int lineY = textAreaY + lineOffsetY + visualLine * lineHeight;
            if (lineY + lineHeight < editorY || lineY > editorY + editorHeight) {
                continue;
            }

            int lineNumberColor = lineIndex == getCaretLine() ? UITheme.TEXT_PRIMARY : UITheme.TEXT_TERTIARY;
            String lineNumber = Integer.toString(lineIndex + 1);
            int lineNumberX = editorX + gutterWidth - GUTTER_PADDING - textRenderer.getWidth(lineNumber);
            context.drawText(textRenderer, lineNumber, lineNumberX, lineY, lineNumberColor, false);

            renderSelectionForLine(context, textRenderer, lineIndex, lineY, textAreaX, lineHeight);
            String lineText = lines.get(lineIndex);
            int textX = textAreaX + TEXT_PADDING - horizontalScroll;
            context.drawText(textRenderer, lineText, textX, lineY, UITheme.TEXT_PRIMARY, false);
        }

        if (caretVisible) {
            int[] caret = getCaretRenderPosition(textRenderer);
            UIStyleHelper.drawTextCaret(
                context,
                caret[0],
                caret[1],
                caret[1] + textRenderer.fontHeight,
                editorX + editorWidth - TEXT_PADDING,
                UITheme.TEXT_PRIMARY
            );
        }

        context.disableScissor();
        renderScrollIndicators(context, textRenderer, editorX, editorY, editorWidth, editorHeight, gutterWidth);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, TextRenderer textRenderer) {
        if (!isInsideEditor(mouseX, mouseY)) {
            draggingSelection = false;
            draggingVerticalScrollbar = false;
            draggingHorizontalScrollbar = false;
            return false;
        }
        if (button != 0) {
            return true;
        }
        ScrollbarMetrics verticalScrollbar = getVerticalScrollbar(textRenderer);
        if (verticalScrollbar != null && verticalScrollbar.thumbContains((int) mouseX, (int) mouseY)) {
            draggingVerticalScrollbar = true;
            draggingHorizontalScrollbar = false;
            draggingSelection = false;
            scrollbarDragOffset = (int) mouseY - verticalScrollbar.thumbStart();
            return true;
        }
        ScrollbarMetrics horizontalScrollbar = getHorizontalScrollbar(textRenderer);
        if (horizontalScrollbar != null && horizontalScrollbar.thumbContains((int) mouseX, (int) mouseY)) {
            draggingHorizontalScrollbar = true;
            draggingVerticalScrollbar = false;
            draggingSelection = false;
            scrollbarDragOffset = (int) mouseX - horizontalScrollbar.thumbStart();
            return true;
        }
        int newCaret = getIndexAtPoint((int) mouseX, (int) mouseY, textRenderer);
        caretPosition = newCaret;
        selectionAnchor = newCaret;
        selectionStart = newCaret;
        selectionEnd = newCaret;
        preferredColumn = -1;
        draggingSelection = true;
        ensureCaretVisible(textRenderer);
        resetCaretBlink();
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, TextRenderer textRenderer) {
        if (button != 0) {
            return false;
        }
        if (draggingVerticalScrollbar) {
            ScrollbarMetrics scrollbar = getVerticalScrollbar(textRenderer);
            if (scrollbar == null) {
                return false;
            }
            setVerticalScrollFromThumb((int) mouseY - scrollbarDragOffset, scrollbar);
            resetCaretBlink();
            return true;
        }
        if (draggingHorizontalScrollbar) {
            ScrollbarMetrics scrollbar = getHorizontalScrollbar(textRenderer);
            if (scrollbar == null) {
                return false;
            }
            setHorizontalScrollFromThumb((int) mouseX - scrollbarDragOffset, scrollbar);
            resetCaretBlink();
            return true;
        }
        if (!draggingSelection) {
            return false;
        }
        int newCaret = getIndexAtPoint((int) mouseX, (int) mouseY, textRenderer);
        caretPosition = newCaret;
        updateSelectionFromAnchor(newCaret);
        preferredColumn = -1;
        ensureCaretVisible(textRenderer);
        resetCaretBlink();
        return true;
    }

    public boolean mouseReleased(int button) {
        if (button == 0) {
            draggingSelection = false;
            draggingVerticalScrollbar = false;
            draggingHorizontalScrollbar = false;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double amount, TextRenderer textRenderer) {
        if (isShiftDown()) {
            int horizontalStep = Math.max(12, textRenderer.getWidth("    "));
            int maxHorizontalScroll = getMaxHorizontalScroll(textRenderer);
            horizontalScroll = MathHelper.clamp(horizontalScroll - (int) Math.round(amount * horizontalStep), 0, maxHorizontalScroll);
        } else {
            int lineHeight = textRenderer.fontHeight + 2;
            int maxScroll = getMaxVerticalScroll(textRenderer);
            verticalScroll = MathHelper.clamp(verticalScroll - (int) Math.round(amount * lineHeight * 2), 0, maxScroll);
        }
        return true;
    }

    private boolean isShiftDown() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return false;
        }
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    public boolean keyPressed(int keyCode, int modifiers, TextRenderer textRenderer) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || (modifiers & GLFW.GLFW_MOD_SUPER) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (ctrl) {
            if (keyCode == GLFW.GLFW_KEY_A) {
                selectionAnchor = 0;
                selectionStart = 0;
                selectionEnd = text.length();
                caretPosition = text.length();
                ensureCaretVisible(textRenderer);
                resetCaretBlink();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_C) {
                copySelection();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Z) {
                if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                    return redo(textRenderer);
                }
                return undo(textRenderer);
            }
            if (keyCode == GLFW.GLFW_KEY_X) {
                if (hasSelection()) {
                    copySelection();
                    deleteSelection();
                    ensureCaretVisible(textRenderer);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V) {
                insertText(getClipboardText(), textRenderer);
                return true;
            }
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) {
                    pushUndoState();
                    deleteSelection();
                } else if (caretPosition > 0) {
                    pushUndoState();
                    text = text.substring(0, caretPosition - 1) + text.substring(caretPosition);
                    caretPosition--;
                    notifyTextChanged();
                }
                clearSelectionIfCollapsed();
                ensureCaretVisible(textRenderer);
                resetCaretBlink();
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) {
                    pushUndoState();
                    deleteSelection();
                } else if (caretPosition < text.length()) {
                    pushUndoState();
                    text = text.substring(0, caretPosition) + text.substring(caretPosition + 1);
                    notifyTextChanged();
                }
                clearSelectionIfCollapsed();
                ensureCaretVisible(textRenderer);
                resetCaretBlink();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                insertText("\n", textRenderer);
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                insertText("  ", textRenderer);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                moveCaretHorizontal(-1, shift, textRenderer);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                moveCaretHorizontal(1, shift, textRenderer);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                moveCaretVertical(-1, shift, textRenderer);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveCaretVertical(1, shift, textRenderer);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                moveCaretToLineBoundary(true, shift, textRenderer);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                moveCaretToLineBoundary(false, shift, textRenderer);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public boolean charTyped(char chr, int modifiers, TextRenderer textRenderer) {
        if (chr < 32 || chr == 127) {
            return false;
        }
        insertText(String.valueOf(chr), textRenderer);
        return true;
    }

    private void insertText(String insert, TextRenderer textRenderer) {
        if (insert == null || insert.isEmpty()) {
            return;
        }
        pushUndoState();
        if (hasSelection()) {
            deleteSelection();
        }
        text = text.substring(0, caretPosition) + insert + text.substring(caretPosition);
        caretPosition += insert.length();
        clearSelection();
        preferredColumn = -1;
        notifyTextChanged();
        ensureCaretVisible(textRenderer);
        resetCaretBlink();
    }

    private void moveCaretHorizontal(int delta, boolean extendSelection, TextRenderer textRenderer) {
        int target = MathHelper.clamp(caretPosition + delta, 0, text.length());
        moveCaretTo(target, extendSelection, textRenderer, false);
    }

    private void moveCaretVertical(int deltaLines, boolean extendSelection, TextRenderer textRenderer) {
        LineColumn current = getLineColumnForIndex(caretPosition);
        if (preferredColumn < 0) {
            preferredColumn = current.column();
        }
        List<String> lines = getLines();
        int targetLine = MathHelper.clamp(current.line() + deltaLines, 0, Math.max(0, lines.size() - 1));
        int targetColumn = Math.min(preferredColumn, lines.get(targetLine).length());
        int targetIndex = getIndexForLineColumn(targetLine, targetColumn);
        moveCaretTo(targetIndex, extendSelection, textRenderer, true);
    }

    private void moveCaretToLineBoundary(boolean start, boolean extendSelection, TextRenderer textRenderer) {
        LineColumn current = getLineColumnForIndex(caretPosition);
        int targetIndex = getIndexForLineColumn(current.line(), start ? 0 : getLines().get(current.line()).length());
        moveCaretTo(targetIndex, extendSelection, textRenderer, false);
    }

    private void moveCaretTo(int target, boolean extendSelection, TextRenderer textRenderer, boolean keepPreferredColumn) {
        int previousCaret = caretPosition;
        caretPosition = MathHelper.clamp(target, 0, text.length());
        if (extendSelection) {
            if (selectionAnchor < 0) {
                selectionAnchor = hasSelection() ? previousCaret : previousCaret;
            }
            updateSelectionFromAnchor(caretPosition);
        } else {
            clearSelection();
        }
        if (!keepPreferredColumn) {
            preferredColumn = -1;
        }
        ensureCaretVisible(textRenderer);
        resetCaretBlink();
    }

    private void renderSelectionForLine(DrawContext context, TextRenderer textRenderer, int lineIndex, int lineY, int textX, int lineHeight) {
        if (!hasSelection()) {
            return;
        }
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        LineColumn startLc = getLineColumnForIndex(start);
        LineColumn endLc = getLineColumnForIndex(end);
        if (lineIndex < startLc.line() || lineIndex > endLc.line()) {
            return;
        }
        String line = getLines().get(lineIndex);
        int selectionStartColumn = lineIndex == startLc.line() ? startLc.column() : 0;
        int selectionEndColumn = lineIndex == endLc.line() ? endLc.column() : line.length();
        int selectionLeft = textX + TEXT_PADDING - horizontalScroll + textRenderer.getWidth(line.substring(0, Math.min(selectionStartColumn, line.length())));
        int selectionRight = textX + TEXT_PADDING - horizontalScroll + textRenderer.getWidth(line.substring(0, Math.min(selectionEndColumn, line.length())));
        if (selectionRight > selectionLeft) {
            context.fill(selectionLeft, lineY - 1, selectionRight, lineY + lineHeight - 1, 0x664F86C6);
        }
    }

    private int[] getCaretRenderPosition(TextRenderer textRenderer) {
        LineColumn lc = getLineColumnForIndex(caretPosition);
        int lineHeight = textRenderer.fontHeight + 2;
        int gutterWidth = getGutterWidth(textRenderer);
        int editorX = x + PANEL_PADDING;
        int editorY = y + HEADER_HEIGHT + STATUS_HEIGHT;
        int textAreaX = editorX + gutterWidth;
        int textAreaY = editorY + TEXT_PADDING;
        String line = getLines().get(lc.line());
        int caretX = textAreaX + TEXT_PADDING - horizontalScroll + textRenderer.getWidth(line.substring(0, Math.min(lc.column(), line.length())));
        int caretY = textAreaY - verticalScroll + lc.line() * lineHeight;
        return new int[]{caretX, caretY};
    }

    private int getIndexAtPoint(int mouseX, int mouseY, TextRenderer textRenderer) {
        List<String> lines = getLines();
        int lineHeight = textRenderer.fontHeight + 2;
        int gutterWidth = getGutterWidth(textRenderer);
        int editorX = x + PANEL_PADDING;
        int editorY = y + HEADER_HEIGHT + STATUS_HEIGHT;
        int textAreaX = editorX + gutterWidth + TEXT_PADDING;
        int textAreaY = editorY + TEXT_PADDING;
        int lineIndex = MathHelper.clamp((mouseY - textAreaY + verticalScroll) / lineHeight, 0, Math.max(0, lines.size() - 1));
        String line = lines.get(lineIndex);
        int localX = mouseX - textAreaX + horizontalScroll;
        int column = getColumnAtPixel(line, Math.max(0, localX), textRenderer);
        return getIndexForLineColumn(lineIndex, column);
    }

    private int getColumnAtPixel(String line, int pixelX, TextRenderer textRenderer) {
        int bestColumn = 0;
        for (int i = 1; i <= line.length(); i++) {
            int width = textRenderer.getWidth(line.substring(0, i));
            if (width > pixelX) {
                int prevWidth = textRenderer.getWidth(line.substring(0, i - 1));
                return pixelX - prevWidth < width - pixelX ? i - 1 : i;
            }
            bestColumn = i;
        }
        return bestColumn;
    }

    private int getGutterWidth(TextRenderer textRenderer) {
        int digits = Integer.toString(Math.max(1, getLines().size())).length();
        return digits * textRenderer.getWidth("0") + GUTTER_PADDING * 2;
    }

    private int getCaretLine() {
        return getLineColumnForIndex(caretPosition).line();
    }

    private int getMaxVerticalScroll(TextRenderer textRenderer) {
        int lineHeight = textRenderer.fontHeight + 2;
        int contentHeight = getLines().size() * lineHeight;
        int visibleHeight = Math.max(1, height - HEADER_HEIGHT - STATUS_HEIGHT - PANEL_PADDING - TEXT_PADDING * 2);
        return Math.max(0, contentHeight - visibleHeight);
    }

    private int getMaxLineWidth(TextRenderer textRenderer) {
        int maxWidth = 0;
        for (String line : getLines()) {
            maxWidth = Math.max(maxWidth, textRenderer.getWidth(line));
        }
        return maxWidth;
    }

    private int getMaxHorizontalScroll(TextRenderer textRenderer) {
        int gutterWidth = getGutterWidth(textRenderer);
        int editorWidth = Math.max(1, width - PANEL_PADDING * 2);
        int visibleWidth = Math.max(1, editorWidth - gutterWidth - TEXT_PADDING * 2 - SCROLLBAR_THICKNESS - SCROLLBAR_MARGIN);
        return Math.max(0, getMaxLineWidth(textRenderer) - visibleWidth);
    }

    private void renderScrollIndicators(DrawContext context, TextRenderer textRenderer, int editorX, int editorY, int editorWidth, int editorHeight, int gutterWidth) {
        int trackColor = UITheme.BORDER_DEFAULT;
        int thumbColor = UITheme.TEXT_TERTIARY;
        int activeThumbColor = UITheme.TEXT_SECONDARY;

        ScrollbarMetrics verticalScrollbar = getVerticalScrollbar(textRenderer);
        if (verticalScrollbar != null) {
            context.fill(
                verticalScrollbar.trackX(),
                verticalScrollbar.trackY(),
                verticalScrollbar.trackX() + verticalScrollbar.trackThickness(),
                verticalScrollbar.trackY() + verticalScrollbar.trackLength(),
                trackColor
            );
            context.fill(
                verticalScrollbar.trackX(),
                verticalScrollbar.thumbStart(),
                verticalScrollbar.trackX() + verticalScrollbar.trackThickness(),
                verticalScrollbar.thumbStart() + verticalScrollbar.thumbLength(),
                draggingVerticalScrollbar ? activeThumbColor : thumbColor
            );
        }

        ScrollbarMetrics horizontalScrollbar = getHorizontalScrollbar(textRenderer);
        if (horizontalScrollbar != null) {
            context.fill(
                horizontalScrollbar.trackX(),
                horizontalScrollbar.trackY(),
                horizontalScrollbar.trackX() + horizontalScrollbar.trackLength(),
                horizontalScrollbar.trackY() + horizontalScrollbar.trackThickness(),
                trackColor
            );
            context.fill(
                horizontalScrollbar.thumbStart(),
                horizontalScrollbar.trackY(),
                horizontalScrollbar.thumbStart() + horizontalScrollbar.thumbLength(),
                horizontalScrollbar.trackY() + horizontalScrollbar.trackThickness(),
                draggingHorizontalScrollbar ? activeThumbColor : thumbColor
            );
        }
    }

    private ScrollbarMetrics getVerticalScrollbar(TextRenderer textRenderer) {
        int editorX = x + PANEL_PADDING;
        int editorY = y + HEADER_HEIGHT + STATUS_HEIGHT;
        int editorWidth = Math.max(1, width - PANEL_PADDING * 2);
        int editorHeight = Math.max(1, height - HEADER_HEIGHT - STATUS_HEIGHT - PANEL_PADDING);
        int trackX = editorX + editorWidth - SCROLLBAR_THICKNESS - SCROLLBAR_MARGIN;
        int trackY = editorY + SCROLLBAR_MARGIN;
        int trackLength = Math.max(0, editorHeight - SCROLLBAR_MARGIN * 2 - SCROLLBAR_THICKNESS - SCROLLBAR_MARGIN);
        int maxVerticalScroll = getMaxVerticalScroll(textRenderer);
        if (maxVerticalScroll <= 0 || trackLength <= 0) {
            return null;
        }
        int visibleHeight = Math.max(1, editorHeight - TEXT_PADDING * 2);
        int contentHeight = getLines().size() * (textRenderer.fontHeight + 2);
        int thumbLength = Math.max(MIN_SCROLLBAR_THUMB_SIZE, Math.round((visibleHeight / (float) Math.max(visibleHeight, contentHeight)) * trackLength));
        thumbLength = Math.min(trackLength, thumbLength);
        int thumbTravel = Math.max(0, trackLength - thumbLength);
        int thumbStart = trackY + Math.round((verticalScroll / (float) maxVerticalScroll) * thumbTravel);
        return new ScrollbarMetrics(false, trackX, trackY, SCROLLBAR_THICKNESS, trackLength, thumbStart, thumbLength, maxVerticalScroll);
    }

    private ScrollbarMetrics getHorizontalScrollbar(TextRenderer textRenderer) {
        int editorX = x + PANEL_PADDING;
        int editorY = y + HEADER_HEIGHT + STATUS_HEIGHT;
        int editorWidth = Math.max(1, width - PANEL_PADDING * 2);
        int gutterWidth = getGutterWidth(textRenderer);
        int trackX = editorX + gutterWidth + TEXT_PADDING;
        int trackY = editorY + editorHeight() - SCROLLBAR_THICKNESS - SCROLLBAR_MARGIN;
        int trackLength = Math.max(0, editorWidth - gutterWidth - TEXT_PADDING * 2 - SCROLLBAR_THICKNESS - SCROLLBAR_MARGIN * 2);
        int maxHorizontalScroll = getMaxHorizontalScroll(textRenderer);
        if (maxHorizontalScroll <= 0 || trackLength <= 0) {
            return null;
        }
        int visibleWidth = Math.max(1, editorWidth - gutterWidth - TEXT_PADDING * 2 - SCROLLBAR_THICKNESS - SCROLLBAR_MARGIN);
        int contentWidth = Math.max(visibleWidth, getMaxLineWidth(textRenderer));
        int thumbLength = Math.max(MIN_SCROLLBAR_THUMB_SIZE, Math.round((visibleWidth / (float) contentWidth) * trackLength));
        thumbLength = Math.min(trackLength, thumbLength);
        int thumbTravel = Math.max(0, trackLength - thumbLength);
        int thumbStart = trackX + Math.round((horizontalScroll / (float) maxHorizontalScroll) * thumbTravel);
        return new ScrollbarMetrics(true, trackX, trackY, SCROLLBAR_THICKNESS, trackLength, thumbStart, thumbLength, maxHorizontalScroll);
    }

    private void setVerticalScrollFromThumb(int thumbStart, ScrollbarMetrics scrollbar) {
        int thumbTravel = Math.max(0, scrollbar.trackLength() - scrollbar.thumbLength());
        if (thumbTravel <= 0) {
            verticalScroll = 0;
            return;
        }
        int clampedThumbStart = MathHelper.clamp(thumbStart, scrollbar.trackY(), scrollbar.trackY() + thumbTravel);
        float progress = (clampedThumbStart - scrollbar.trackY()) / (float) thumbTravel;
        verticalScroll = MathHelper.clamp(Math.round(progress * scrollbar.maxScroll()), 0, scrollbar.maxScroll());
    }

    private void setHorizontalScrollFromThumb(int thumbStart, ScrollbarMetrics scrollbar) {
        int thumbTravel = Math.max(0, scrollbar.trackLength() - scrollbar.thumbLength());
        if (thumbTravel <= 0) {
            horizontalScroll = 0;
            return;
        }
        int clampedThumbStart = MathHelper.clamp(thumbStart, scrollbar.trackX(), scrollbar.trackX() + thumbTravel);
        float progress = (clampedThumbStart - scrollbar.trackX()) / (float) thumbTravel;
        horizontalScroll = MathHelper.clamp(Math.round(progress * scrollbar.maxScroll()), 0, scrollbar.maxScroll());
    }

    private int editorHeight() {
        return Math.max(1, height - HEADER_HEIGHT - STATUS_HEIGHT - PANEL_PADDING);
    }

    private void ensureCaretVisible(TextRenderer textRenderer) {
        int[] caret = getCaretRenderPosition(textRenderer);
        int editorX = x + PANEL_PADDING;
        int editorY = y + HEADER_HEIGHT + STATUS_HEIGHT;
        int editorWidth = Math.max(1, width - PANEL_PADDING * 2);
        int editorHeight = Math.max(1, height - HEADER_HEIGHT - STATUS_HEIGHT - PANEL_PADDING);
        if (caret[1] < editorY + TEXT_PADDING) {
            verticalScroll = Math.max(0, verticalScroll - ((editorY + TEXT_PADDING) - caret[1]));
        } else if (caret[1] + textRenderer.fontHeight > editorY + editorHeight - TEXT_PADDING) {
            verticalScroll += caret[1] + textRenderer.fontHeight - (editorY + editorHeight - TEXT_PADDING);
        }
        if (caret[0] < editorX + getGutterWidth(textRenderer) + TEXT_PADDING) {
            horizontalScroll = Math.max(0, horizontalScroll - ((editorX + getGutterWidth(textRenderer) + TEXT_PADDING) - caret[0]));
        } else if (caret[0] > editorX + editorWidth - TEXT_PADDING) {
            horizontalScroll += caret[0] - (editorX + editorWidth - TEXT_PADDING);
        }
        verticalScroll = MathHelper.clamp(verticalScroll, 0, getMaxVerticalScroll(textRenderer));
        horizontalScroll = MathHelper.clamp(horizontalScroll, 0, getMaxHorizontalScroll(textRenderer));
    }

    private boolean isInsideEditor(double mouseX, double mouseY) {
        int editorX = x + PANEL_PADDING;
        int editorY = y + HEADER_HEIGHT + STATUS_HEIGHT;
        int editorWidth = Math.max(0, width - PANEL_PADDING * 2);
        int editorHeight = Math.max(0, height - HEADER_HEIGHT - STATUS_HEIGHT - PANEL_PADDING);
        return mouseX >= editorX && mouseX <= editorX + editorWidth && mouseY >= editorY && mouseY <= editorY + editorHeight;
    }

    private List<String> getLines() {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines.add(text.substring(start, i));
                start = i + 1;
            }
        }
        lines.add(text.substring(start));
        return lines;
    }

    private LineColumn getLineColumnForIndex(int index) {
        int clampedIndex = MathHelper.clamp(index, 0, text.length());
        int line = 0;
        int column = 0;
        for (int i = 0; i < clampedIndex; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        return new LineColumn(line, column);
    }

    private int getIndexForLineColumn(int targetLine, int targetColumn) {
        List<String> lines = getLines();
        int line = MathHelper.clamp(targetLine, 0, Math.max(0, lines.size() - 1));
        int column = Math.max(0, targetColumn);
        int index = 0;
        for (int i = 0; i < line; i++) {
            index += lines.get(i).length() + 1;
        }
        return index + Math.min(column, lines.get(line).length());
    }

    private void updateSelectionFromAnchor(int caret) {
        if (selectionAnchor < 0) {
            selectionAnchor = caret;
        }
        selectionStart = Math.min(selectionAnchor, caret);
        selectionEnd = Math.max(selectionAnchor, caret);
    }

    private boolean hasSelection() {
        return selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd;
    }

    private void deleteSelection() {
        if (!hasSelection()) {
            return;
        }
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        text = text.substring(0, start) + text.substring(end);
        caretPosition = start;
        clearSelection();
        notifyTextChanged();
    }

    private void clearSelection() {
        selectionStart = -1;
        selectionEnd = -1;
        selectionAnchor = -1;
    }

    private void clearSelectionIfCollapsed() {
        if (selectionStart == selectionEnd) {
            clearSelection();
        }
    }

    private void copySelection() {
        if (!hasSelection()) {
            return;
        }
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.keyboard != null) {
            client.keyboard.setClipboard(text.substring(start, end));
        }
    }

    private String getClipboardText() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.keyboard != null) {
            String clipboard = client.keyboard.getClipboard();
            return clipboard == null ? "" : clipboard;
        }
        return "";
    }

    private void updateCaretBlink() {
        long now = System.currentTimeMillis();
        if (now - caretBlinkLastToggleMs >= 530L) {
            caretVisible = !caretVisible;
            caretBlinkLastToggleMs = now;
        }
    }

    private void resetCaretBlink() {
        caretVisible = true;
        caretBlinkLastToggleMs = System.currentTimeMillis();
    }

    private void notifyTextChanged() {
        if (textChangedListener != null) {
            textChangedListener.run();
        }
    }

    private void pushUndoState() {
        undoStack.push(captureState());
        while (undoStack.size() > 200) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    private EditorState captureState() {
        return new EditorState(
            text,
            caretPosition,
            selectionStart,
            selectionEnd,
            selectionAnchor,
            preferredColumn,
            verticalScroll,
            horizontalScroll
        );
    }

    private void restoreState(EditorState state, TextRenderer textRenderer) {
        if (state == null) {
            return;
        }
        text = state.text();
        caretPosition = MathHelper.clamp(state.caretPosition(), 0, text.length());
        selectionStart = state.selectionStart();
        selectionEnd = state.selectionEnd();
        selectionAnchor = state.selectionAnchor();
        preferredColumn = state.preferredColumn();
        verticalScroll = Math.max(0, state.verticalScroll());
        horizontalScroll = Math.max(0, state.horizontalScroll());
        ensureCaretVisible(textRenderer);
        resetCaretBlink();
        notifyTextChanged();
    }

    private record LineColumn(int line, int column) {}
    private record ScrollbarMetrics(
        boolean horizontal,
        int trackX,
        int trackY,
        int trackThickness,
        int trackLength,
        int thumbStart,
        int thumbLength,
        int maxScroll
    ) {
        private boolean thumbContains(int mouseX, int mouseY) {
            if (horizontal) {
                return mouseX >= thumbStart && mouseX <= thumbStart + thumbLength && mouseY >= trackY && mouseY <= trackY + trackThickness;
            }
            return mouseX >= trackX && mouseX <= trackX + trackThickness && mouseY >= thumbStart && mouseY <= thumbStart + thumbLength;
        }
    }
    private record EditorState(
        String text,
        int caretPosition,
        int selectionStart,
        int selectionEnd,
        int selectionAnchor,
        int preferredColumn,
        int verticalScroll,
        int horizontalScroll
    ) {}
}
