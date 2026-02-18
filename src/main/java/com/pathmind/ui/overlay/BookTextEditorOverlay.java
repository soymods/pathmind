package com.pathmind.ui.overlay;

import com.pathmind.nodes.Node;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import com.pathmind.util.RenderStateBridge;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Overlay for editing book text in WRITE_BOOK nodes.
 * Provides a large text area for entering book page content.
 */
public class BookTextEditorOverlay {
    private static final int TITLE_HEIGHT = 30;
    private static final int TEXT_AREA_MARGIN = 16;
    private static final int TEXT_AREA_PADDING = 8;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 10;
    private static final int BUTTON_BOTTOM_MARGIN = 12;
    private static final int CHAR_COUNTER_MARGIN = 8;
    private static final int PAGE_BUTTON_SIZE = 18;
    private static final int PAGE_BUTTON_GAP = 6;

    private final Node node;
    private final int screenWidth;
    private final int screenHeight;
    private final Runnable onClose;
    private final Consumer<Node> onSave;

    private final int popupWidth;
    private final int popupHeight;
    private final int maxCharsPerLine;
    private final int maxLines;
    private int popupX;
    private int popupY;
    private String textContent;
    private int caretPosition;
    private int selectionStart;
    private int selectionEnd;
    private int selectionAnchor;
    private final PopupAnimationHandler popupAnimation = new PopupAnimationHandler();
    private ButtonWidget saveButton;
    private ButtonWidget cancelButton;
    private ButtonWidget prevPageButton;
    private ButtonWidget nextPageButton;
    private boolean pendingClose = false;

    private long caretBlinkLastToggle = 0L;
    private boolean caretVisible = true;
    private int scrollOffset = 0;
    private int maxChars;
    private int currentPage;

    public BookTextEditorOverlay(Node node, int screenWidth, int screenHeight,
                                  Runnable onClose, Consumer<Node> onSave) {
        this.node = node;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.onClose = onClose;
        this.onSave = onSave;
        this.popupWidth = Math.max(220, node.getBookTextPopupWidth());
        this.popupHeight = Math.max(180, node.getBookTextPopupHeight());
        this.maxCharsPerLine = node.getBookTextMaxCharsPerLine();
        this.maxLines = node.getBookTextMaxLines();
        this.currentPage = getPageNumberFromNode();
        this.textContent = constrainText(node.getBookTextForPage(currentPage));
        this.maxChars = node.getBookTextMaxChars();
        this.caretPosition = textContent.length();
        this.selectionStart = -1;
        this.selectionEnd = -1;
        this.selectionAnchor = -1;

        updatePopupPosition();
    }

    private void updatePopupPosition() {
        popupX = (screenWidth - popupWidth) / 2;
        popupY = (screenHeight - popupHeight) / 2;
    }

    public void init() {
        int buttonY = popupY + popupHeight - BUTTON_BOTTOM_MARGIN - BUTTON_HEIGHT;
        int buttonsWidth = BUTTON_WIDTH * 2 + BUTTON_SPACING;
        int buttonStartX = popupX + (popupWidth - buttonsWidth) / 2;

        int pageButtonY = popupY + 8;
        int pageButtonRight = popupX + popupWidth - TEXT_AREA_MARGIN;
        int nextButtonX = pageButtonRight - PAGE_BUTTON_SIZE;
        int prevButtonX = nextButtonX - PAGE_BUTTON_SIZE - PAGE_BUTTON_GAP;

        if (node.hasBookTextPageInput()) {
            prevPageButton = ButtonWidget.builder(Text.literal("<"), button -> changePage(currentPage - 1))
                .dimensions(prevButtonX, pageButtonY, PAGE_BUTTON_SIZE, PAGE_BUTTON_SIZE)
                .build();

            nextPageButton = ButtonWidget.builder(Text.literal(">"), button -> changePage(currentPage + 1))
                .dimensions(nextButtonX, pageButtonY, PAGE_BUTTON_SIZE, PAGE_BUTTON_SIZE)
                .build();
        } else {
            prevPageButton = null;
            nextPageButton = null;
        }

        saveButton = ButtonWidget.builder(Text.literal("Save"), button -> save())
            .dimensions(buttonStartX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build();

        cancelButton = ButtonWidget.builder(Text.literal("Cancel"), button -> closeWithoutSave())
            .dimensions(buttonStartX + BUTTON_WIDTH + BUTTON_SPACING, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build();
    }

    public void show() {
        popupAnimation.show();
        pendingClose = false;
    }

    public void hide() {
        popupAnimation.hide();
        pendingClose = true;
    }

    public boolean isVisible() {
        return popupAnimation.isVisible();
    }

    public PopupAnimationHandler getPopupAnimation() {
        return popupAnimation;
    }

    public int getScrimColor() {
        return popupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND);
    }

    public int[] getScaledPopupBounds() {
        return popupAnimation.getScaledPopupBoundsFromTopLeft(popupX, popupY, popupWidth, popupHeight);
    }

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        popupAnimation.tick();
        if (pendingClose && popupAnimation.isFullyHidden()) {
            pendingClose = false;
            if (onClose != null) {
                onClose.run();
            }
            return;
        }
        if (!popupAnimation.isVisible()) return;

        float popupAlpha = popupAnimation.getPopupProgress();
        RenderStateBridge.setShaderColor(1f, 1f, 1f, popupAlpha);

        // Get animated popup bounds
        int[] bounds = popupAnimation.getScaledPopupBounds(screenWidth, screenHeight, popupWidth, popupHeight);
        int scaledX = bounds[0];
        int scaledY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];

        // Render popup background
        context.fill(scaledX, scaledY, scaledX + scaledWidth, scaledY + scaledHeight,
            popupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECONDARY));
        DrawContextBridge.drawBorder(context, scaledX, scaledY, scaledWidth, scaledHeight,
            popupAnimation.getAnimatedPopupColor(UITheme.BORDER_HIGHLIGHT));

        // Render title
        String title = node.getBookTextEditorTitle();
        int titleColor = applyPopupAlpha(UITheme.TEXT_HEADER, popupAlpha);
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(title),
            popupX + (popupWidth - textRenderer.getWidth(title)) / 2,
            popupY + 12,
            titleColor
        );

        if (node.hasBookTextPageInput()) {
            String pageLabel = "Page " + currentPage;
            int pageLabelWidth = textRenderer.getWidth(pageLabel);
            int pageLabelX = popupX + popupWidth - TEXT_AREA_MARGIN - PAGE_BUTTON_SIZE * 2 - PAGE_BUTTON_GAP - 8 - pageLabelWidth;
            int pageLabelY = popupY + 12;
            int pageLabelColor = applyPopupAlpha(UITheme.TEXT_SECONDARY, popupAlpha);
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(pageLabel),
                Math.max(popupX + TEXT_AREA_MARGIN, pageLabelX),
                pageLabelY,
                pageLabelColor
            );
        }

        // Render text area
        int textAreaX = popupX + TEXT_AREA_MARGIN;
        int textAreaY = popupY + TITLE_HEIGHT + 8;
        int textAreaWidth = popupWidth - 2 * TEXT_AREA_MARGIN;
        int textAreaHeight = popupHeight - TITLE_HEIGHT - 8 - BUTTON_HEIGHT - BUTTON_BOTTOM_MARGIN - 30;

        context.fill(textAreaX, textAreaY, textAreaX + textAreaWidth, textAreaY + textAreaHeight,
            applyPopupAlpha(UITheme.BACKGROUND_INPUT, popupAlpha));
        DrawContextBridge.drawBorder(context, textAreaX, textAreaY, textAreaWidth, textAreaHeight,
            applyPopupAlpha(UITheme.ACCENT_DEFAULT, popupAlpha));

        // Render text content with word wrapping
        int textX = textAreaX + TEXT_AREA_PADDING;
        int textY = textAreaY + TEXT_AREA_PADDING;
        int maxTextWidth = textAreaWidth - 2 * TEXT_AREA_PADDING;
        int lineHeight = textRenderer.fontHeight + 2;

        // Enable scissor for text area
        context.enableScissor(textAreaX + 1, textAreaY + 1, textAreaX + textAreaWidth - 1, textAreaY + textAreaHeight - 1);

        // Render the text with wrapping
        String displayText = textContent != null ? textContent : "";
        renderWrappedText(context, textRenderer, displayText, textX, textY - scrollOffset, maxTextWidth, lineHeight, textAreaHeight, popupAlpha);

        // Render caret
        updateCaretBlinkState();
        if (caretVisible) {
            int[] caretPos = getCaretScreenPosition(textRenderer, textX, textY - scrollOffset, maxTextWidth, lineHeight);
            UIStyleHelper.drawTextCaret(
                context,
                caretPos[0],
                caretPos[1],
                caretPos[1] + textRenderer.fontHeight,
                applyPopupAlpha(UITheme.TEXT_PRIMARY, popupAlpha)
            );
        }

        context.disableScissor();

        // Render character counter
        int charCount = textContent != null ? textContent.length() : 0;
        String counterText = charCount + "/" + maxChars;
        int counterColor = charCount >= maxChars ? UITheme.STATE_WARNING : UITheme.TEXT_SECONDARY;
        counterColor = applyPopupAlpha(counterColor, popupAlpha);
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(counterText),
            textAreaX + textAreaWidth - textRenderer.getWidth(counterText) - CHAR_COUNTER_MARGIN,
            textAreaY + textAreaHeight + 4,
            counterColor
        );

        // Render buttons
        if (node.hasBookTextPageInput()) {
            renderButton(context, textRenderer, prevPageButton, mouseX, mouseY, popupAlpha);
            renderButton(context, textRenderer, nextPageButton, mouseX, mouseY, popupAlpha);
        }
        renderButton(context, textRenderer, saveButton, mouseX, mouseY, popupAlpha);
        renderButton(context, textRenderer, cancelButton, mouseX, mouseY, popupAlpha);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderWrappedText(DrawContext context, TextRenderer textRenderer, String text,
                                    int x, int y, int maxWidth, int lineHeight, int areaHeight, float popupAlpha) {
        if (text == null || text.isEmpty()) {
            return;
        }

        int currentY = y;
        int currentX = x;
        StringBuilder currentLine = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\n') {
                // Render current line and move to next
                if (currentY >= y - lineHeight && currentY < y + areaHeight + lineHeight) {
                    context.drawTextWithShadow(textRenderer, Text.literal(currentLine.toString()), currentX, currentY,
                        applyPopupAlpha(UITheme.TEXT_PRIMARY, popupAlpha));
                }
                currentLine = new StringBuilder();
                currentY += lineHeight;
                continue;
            }

            String testLine = currentLine.toString() + c;
            if (textRenderer.getWidth(testLine) > maxWidth) {
                // Line is full, render it and start new line
                if (currentY >= y - lineHeight && currentY < y + areaHeight + lineHeight) {
                    context.drawTextWithShadow(textRenderer, Text.literal(currentLine.toString()), currentX, currentY,
                        applyPopupAlpha(UITheme.TEXT_PRIMARY, popupAlpha));
                }
                currentLine = new StringBuilder();
                currentLine.append(c);
                currentY += lineHeight;
            } else {
                currentLine.append(c);
            }
        }

        // Render remaining text
        if (currentLine.length() > 0 && currentY >= y - lineHeight && currentY < y + areaHeight + lineHeight) {
            context.drawTextWithShadow(textRenderer, Text.literal(currentLine.toString()), currentX, currentY,
                applyPopupAlpha(UITheme.TEXT_PRIMARY, popupAlpha));
        }
    }

    private int[] getCaretScreenPosition(TextRenderer textRenderer, int startX, int startY, int maxWidth, int lineHeight) {
        String text = textContent != null ? textContent : "";
        int pos = MathHelper.clamp(caretPosition, 0, text.length());

        int currentY = startY;
        int currentX = startX;
        int charIndex = 0;
        StringBuilder currentLine = new StringBuilder();

        for (int i = 0; i < text.length() && charIndex < pos; i++) {
            char c = text.charAt(i);
            charIndex++;

            if (c == '\n') {
                currentLine = new StringBuilder();
                currentY += lineHeight;
                currentX = startX;
                continue;
            }

            String testLine = currentLine.toString() + c;
            if (textRenderer.getWidth(testLine) > maxWidth) {
                currentLine = new StringBuilder();
                currentLine.append(c);
                currentY += lineHeight;
                currentX = startX + textRenderer.getWidth(currentLine.toString());
            } else {
                currentLine.append(c);
                currentX = startX + textRenderer.getWidth(currentLine.toString());
            }
        }

        return new int[]{currentX, currentY};
    }

    private void renderButton(DrawContext context, TextRenderer textRenderer, ButtonWidget button, int mouseX, int mouseY, float popupAlpha) {
        if (button == null) return;

        boolean hovered = mouseX >= button.getX() && mouseX <= button.getX() + button.getWidth() &&
                         mouseY >= button.getY() && mouseY <= button.getY() + button.getHeight();

        int bgColor = hovered ? UITheme.BUTTON_DEFAULT_HOVER : UITheme.BUTTON_DEFAULT_BG;
        float hoverProgress = HoverAnimator.getProgress(button, hovered);
        int borderColor = AnimationHelper.lerpColor(
            UITheme.BUTTON_DEFAULT_BORDER,
            UITheme.BUTTON_HOVER_OUTLINE,
            hoverProgress
        );

        context.fill(button.getX(), button.getY(),
                    button.getX() + button.getWidth(),
                    button.getY() + button.getHeight(), applyPopupAlpha(bgColor, popupAlpha));
        DrawContextBridge.drawBorder(context, button.getX(), button.getY(),
                                     button.getWidth(), button.getHeight(), applyPopupAlpha(borderColor, popupAlpha));

        String label = button.getMessage().getString();
        int textX = button.getX() + (button.getWidth() - textRenderer.getWidth(label)) / 2;
        int textY = button.getY() + (button.getHeight() - textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(textRenderer, Text.literal(label), textX, textY,
            applyPopupAlpha(UITheme.TEXT_PRIMARY, popupAlpha));
    }

    private int applyPopupAlpha(int color, float alphaMultiplier) {
        int baseAlpha = (color >>> 24) & 0xFF;
        int applied = Math.round(baseAlpha * MathHelper.clamp(alphaMultiplier, 0f, 1f));
        return (applied << 24) | (color & 0x00FFFFFF);
    }

    private void updateCaretBlinkState() {
        long now = System.currentTimeMillis();
        if (now - caretBlinkLastToggle >= 530) {
            caretVisible = !caretVisible;
            caretBlinkLastToggle = now;
        }
    }

    private void resetCaretBlink() {
        caretVisible = true;
        caretBlinkLastToggle = System.currentTimeMillis();
    }

    public void handleKeyInput(int key, int scanCode, int modifiers) {
        if (!popupAnimation.isVisible()) return;

        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || (modifiers & GLFW.GLFW_MOD_SUPER) != 0;

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            closeWithoutSave();
            return;
        }

        if (key == GLFW.GLFW_KEY_ENTER) {
            if (ctrl) {
                save();
            } else {
                // Insert newline
                insertText("\n");
            }
            return;
        }

        if (ctrl && key == GLFW.GLFW_KEY_A) {
            // Select all
            selectionStart = 0;
            selectionEnd = textContent.length();
            selectionAnchor = 0;
            caretPosition = textContent.length();
            resetCaretBlink();
            return;
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (hasSelection()) {
                deleteSelection();
            } else if (caretPosition > 0) {
                textContent = textContent.substring(0, caretPosition - 1) + textContent.substring(caretPosition);
                caretPosition--;
                persistText();
            }
            resetCaretBlink();
            return;
        }

        if (key == GLFW.GLFW_KEY_DELETE) {
            if (hasSelection()) {
                deleteSelection();
            } else if (caretPosition < textContent.length()) {
                textContent = textContent.substring(0, caretPosition) + textContent.substring(caretPosition + 1);
                persistText();
            }
            resetCaretBlink();
            return;
        }

        if (key == GLFW.GLFW_KEY_LEFT) {
            if (caretPosition > 0) {
                caretPosition--;
                clearSelection();
            }
            resetCaretBlink();
            return;
        }

        if (key == GLFW.GLFW_KEY_RIGHT) {
            if (caretPosition < textContent.length()) {
                caretPosition++;
                clearSelection();
            }
            resetCaretBlink();
            return;
        }

        if (key == GLFW.GLFW_KEY_HOME) {
            caretPosition = 0;
            clearSelection();
            resetCaretBlink();
            return;
        }

        if (key == GLFW.GLFW_KEY_END) {
            caretPosition = textContent.length();
            clearSelection();
            resetCaretBlink();
            return;
        }
    }

    public void handleCharInput(char chr) {
        if (!popupAnimation.isVisible()) return;

        if (chr >= 32 && chr != 127) {
            insertText(String.valueOf(chr));
        }
    }

    private void insertText(String text) {
        if (hasSelection()) {
            deleteSelection();
        }

        if (text == null || text.isEmpty()) {
            return;
        }

        StringBuilder inserted = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (textContent.length() >= maxChars) {
                break;
            }
            char chr = text.charAt(i);
            if (chr == '\n' && maxLines > 0 && getLineCount(textContent) >= maxLines) {
                continue;
            }
            if (chr != '\n' && maxCharsPerLine > 0 && getCurrentLineLength() >= maxCharsPerLine) {
                continue;
            }

            textContent = textContent.substring(0, caretPosition) + chr + textContent.substring(caretPosition);
            caretPosition++;
            inserted.append(chr);
        }
        if (inserted.length() == 0) {
            return;
        }
        resetCaretBlink();
        persistText();
    }

    private int getCurrentLineLength() {
        int start = textContent.lastIndexOf('\n', Math.max(0, caretPosition - 1));
        start = start == -1 ? 0 : start + 1;
        int end = textContent.indexOf('\n', caretPosition);
        if (end == -1) {
            end = textContent.length();
        }
        return Math.max(0, end - start);
    }

    private int getLineCount(String text) {
        if (text == null || text.isEmpty()) {
            return 1;
        }
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private String constrainText(String text) {
        String normalized = text == null ? "" : text;
        if (normalized.length() > maxChars) {
            normalized = normalized.substring(0, maxChars);
        }
        if (maxCharsPerLine <= 0 && maxLines <= 0) {
            return normalized;
        }

        String[] lines = normalized.split("\\n", -1);
        int limit = maxLines > 0 ? Math.min(maxLines, lines.length) : lines.length;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            String line = lines[i] == null ? "" : lines[i];
            if (maxCharsPerLine > 0 && line.length() > maxCharsPerLine) {
                line = line.substring(0, maxCharsPerLine);
            }
            if (i > 0) {
                out.append('\n');
            }
            out.append(line);
        }
        String constrained = out.toString();
        if (constrained.length() > maxChars) {
            constrained = constrained.substring(0, maxChars);
        }
        return constrained;
    }

    private boolean hasSelection() {
        return selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd;
    }

    private void deleteSelection() {
        if (!hasSelection()) return;

        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        textContent = textContent.substring(0, start) + textContent.substring(end);
        caretPosition = start;
        clearSelection();
        persistText();
    }

    private void clearSelection() {
        selectionStart = -1;
        selectionEnd = -1;
        selectionAnchor = -1;
    }

    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (!popupAnimation.isVisible()) return false;

        // Check if click is outside popup
        if (mouseX < popupX || mouseX > popupX + popupWidth ||
            mouseY < popupY || mouseY > popupY + popupHeight) {
            // Treat clicking outside as confirm/save so user input isn't lost
            save();
            return true;
        }

        // Check button clicks
        if (saveButton != null && isOverButton(saveButton, mouseX, mouseY)) {
            save();
            return true;
        }

        if (cancelButton != null && isOverButton(cancelButton, mouseX, mouseY)) {
            closeWithoutSave();
            return true;
        }

        if (prevPageButton != null && isOverButton(prevPageButton, mouseX, mouseY)) {
            changePage(currentPage - 1);
            return true;
        }

        if (nextPageButton != null && isOverButton(nextPageButton, mouseX, mouseY)) {
            changePage(currentPage + 1);
            return true;
        }

        return true;
    }

    private boolean isOverButton(ButtonWidget button, double mouseX, double mouseY) {
        return mouseX >= button.getX() && mouseX <= button.getX() + button.getWidth() &&
               mouseY >= button.getY() && mouseY <= button.getY() + button.getHeight();
    }

    public boolean handleMouseScroll(double mouseX, double mouseY, double amount) {
        if (!popupAnimation.isVisible()) return false;

        // Scroll the text area
        scrollOffset -= (int)(amount * 12);
        scrollOffset = Math.max(0, scrollOffset);
        return true;
    }

    private void save() {
        node.setBookTextForPage(currentPage, textContent);
        if (onSave != null) {
            onSave.accept(node);
        }
        closeInternal();
    }

    private void persistText() {
        node.setBookTextForPage(currentPage, textContent);
        if (onSave != null) {
            onSave.accept(node);
        }
    }

    private int getPageNumberFromNode() {
        int value = 1;
        if (node != null) {
            String raw = null;
            if (node.getParameter("Page") != null) {
                raw = node.getParameter("Page").getStringValue();
            }
            if (raw != null && !raw.isEmpty()) {
                try {
                    value = Integer.parseInt(raw.trim());
                } catch (NumberFormatException ignored) {
                    value = 1;
                }
            }
        }
        return Math.max(1, value);
    }

    private void changePage(int requestedPage) {
        if (!node.hasBookTextPageInput()) {
            return;
        }
        int nextPage = Math.max(1, requestedPage);
        if (nextPage == currentPage) {
            return;
        }
        persistText();
        currentPage = nextPage;
        if (node != null) {
            node.setParameterValueAndPropagate("Page", Integer.toString(currentPage));
            if (onSave != null) {
                onSave.accept(node);
            }
        }
        textContent = constrainText(node.getBookTextForPage(currentPage));
        caretPosition = textContent.length();
        clearSelection();
        scrollOffset = 0;
        resetCaretBlink();
    }

    private void closeInternal() {
        hide();
    }

    private void closeWithoutSave() {
        closeInternal();
    }
}
