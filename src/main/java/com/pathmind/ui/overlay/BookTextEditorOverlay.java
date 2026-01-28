package com.pathmind.ui.overlay;

import com.pathmind.nodes.Node;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.animation.PopupAnimationHandler;
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
    private static final int POPUP_WIDTH = 340;
    private static final int POPUP_HEIGHT = 280;
    private static final int TITLE_HEIGHT = 30;
    private static final int TEXT_AREA_MARGIN = 16;
    private static final int TEXT_AREA_PADDING = 8;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 10;
    private static final int BUTTON_BOTTOM_MARGIN = 12;
    private static final int CHAR_COUNTER_MARGIN = 8;

    private final Node node;
    private final int screenWidth;
    private final int screenHeight;
    private final Runnable onClose;
    private final Consumer<Node> onSave;

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
    private boolean pendingClose = false;

    private long caretBlinkLastToggle = 0L;
    private boolean caretVisible = true;
    private int scrollOffset = 0;
    private int maxChars;

    public BookTextEditorOverlay(Node node, int screenWidth, int screenHeight,
                                  Runnable onClose, Consumer<Node> onSave) {
        this.node = node;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.onClose = onClose;
        this.onSave = onSave;
        this.textContent = node.getBookText();
        this.maxChars = node.getBookTextMaxChars();
        this.caretPosition = textContent.length();
        this.selectionStart = -1;
        this.selectionEnd = -1;
        this.selectionAnchor = -1;

        updatePopupPosition();
    }

    private void updatePopupPosition() {
        popupX = (screenWidth - POPUP_WIDTH) / 2;
        popupY = (screenHeight - POPUP_HEIGHT) / 2;
    }

    public void init() {
        int buttonY = popupY + POPUP_HEIGHT - BUTTON_BOTTOM_MARGIN - BUTTON_HEIGHT;
        int buttonsWidth = BUTTON_WIDTH * 2 + BUTTON_SPACING;
        int buttonStartX = popupX + (POPUP_WIDTH - buttonsWidth) / 2;

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

        // Render semi-transparent background overlay
        context.fill(0, 0, screenWidth, screenHeight,
            popupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND));

        float popupAlpha = popupAnimation.getPopupAlpha();
        RenderStateBridge.setShaderColor(1f, 1f, 1f, popupAlpha);

        // Get animated popup bounds
        int[] bounds = popupAnimation.getScaledPopupBounds(screenWidth, screenHeight, POPUP_WIDTH, POPUP_HEIGHT);
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
        String title = "Edit Book Text";
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(title),
            popupX + (POPUP_WIDTH - textRenderer.getWidth(title)) / 2,
            popupY + 12,
            UITheme.TEXT_HEADER
        );

        // Render text area
        int textAreaX = popupX + TEXT_AREA_MARGIN;
        int textAreaY = popupY + TITLE_HEIGHT + 8;
        int textAreaWidth = POPUP_WIDTH - 2 * TEXT_AREA_MARGIN;
        int textAreaHeight = POPUP_HEIGHT - TITLE_HEIGHT - 8 - BUTTON_HEIGHT - BUTTON_BOTTOM_MARGIN - 30;

        context.fill(textAreaX, textAreaY, textAreaX + textAreaWidth, textAreaY + textAreaHeight, UITheme.BACKGROUND_INPUT);
        DrawContextBridge.drawBorder(context, textAreaX, textAreaY, textAreaWidth, textAreaHeight, UITheme.ACCENT_DEFAULT);

        // Render text content with word wrapping
        int textX = textAreaX + TEXT_AREA_PADDING;
        int textY = textAreaY + TEXT_AREA_PADDING;
        int maxTextWidth = textAreaWidth - 2 * TEXT_AREA_PADDING;
        int lineHeight = textRenderer.fontHeight + 2;

        // Enable scissor for text area
        context.enableScissor(textAreaX + 1, textAreaY + 1, textAreaX + textAreaWidth - 1, textAreaY + textAreaHeight - 1);

        // Render the text with wrapping
        String displayText = textContent != null ? textContent : "";
        renderWrappedText(context, textRenderer, displayText, textX, textY - scrollOffset, maxTextWidth, lineHeight, textAreaHeight);

        // Render caret
        updateCaretBlinkState();
        if (caretVisible) {
            int[] caretPos = getCaretScreenPosition(textRenderer, textX, textY - scrollOffset, maxTextWidth, lineHeight);
            context.fill(caretPos[0], caretPos[1], caretPos[0] + 1, caretPos[1] + textRenderer.fontHeight, UITheme.TEXT_PRIMARY);
        }

        context.disableScissor();

        // Render character counter
        int charCount = textContent != null ? textContent.length() : 0;
        String counterText = charCount + "/" + maxChars;
        int counterColor = charCount >= maxChars ? UITheme.STATE_WARNING : UITheme.TEXT_SECONDARY;
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(counterText),
            textAreaX + textAreaWidth - textRenderer.getWidth(counterText) - CHAR_COUNTER_MARGIN,
            textAreaY + textAreaHeight + 4,
            counterColor
        );

        // Render buttons
        renderButton(context, textRenderer, saveButton, mouseX, mouseY);
        renderButton(context, textRenderer, cancelButton, mouseX, mouseY);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderWrappedText(DrawContext context, TextRenderer textRenderer, String text,
                                    int x, int y, int maxWidth, int lineHeight, int areaHeight) {
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
                    context.drawTextWithShadow(textRenderer, Text.literal(currentLine.toString()), currentX, currentY, UITheme.TEXT_PRIMARY);
                }
                currentLine = new StringBuilder();
                currentY += lineHeight;
                continue;
            }

            String testLine = currentLine.toString() + c;
            if (textRenderer.getWidth(testLine) > maxWidth) {
                // Line is full, render it and start new line
                if (currentY >= y - lineHeight && currentY < y + areaHeight + lineHeight) {
                    context.drawTextWithShadow(textRenderer, Text.literal(currentLine.toString()), currentX, currentY, UITheme.TEXT_PRIMARY);
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
            context.drawTextWithShadow(textRenderer, Text.literal(currentLine.toString()), currentX, currentY, UITheme.TEXT_PRIMARY);
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

    private void renderButton(DrawContext context, TextRenderer textRenderer, ButtonWidget button, int mouseX, int mouseY) {
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
                    button.getY() + button.getHeight(), bgColor);
        DrawContextBridge.drawBorder(context, button.getX(), button.getY(),
                                     button.getWidth(), button.getHeight(), borderColor);

        String label = button.getMessage().getString();
        int textX = button.getX() + (button.getWidth() - textRenderer.getWidth(label)) / 2;
        int textY = button.getY() + (button.getHeight() - textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(textRenderer, Text.literal(label), textX, textY, UITheme.TEXT_PRIMARY);
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

        int remaining = maxChars - textContent.length();
        if (remaining <= 0) return;

        String toInsert = text.length() <= remaining ? text : text.substring(0, remaining);
        textContent = textContent.substring(0, caretPosition) + toInsert + textContent.substring(caretPosition);
        caretPosition += toInsert.length();
        resetCaretBlink();
        persistText();
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
        if (mouseX < popupX || mouseX > popupX + POPUP_WIDTH ||
            mouseY < popupY || mouseY > popupY + POPUP_HEIGHT) {
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
        node.setBookText(textContent);
        if (onSave != null) {
            onSave.accept(node);
        }
        closeInternal();
    }

    private void persistText() {
        node.setBookText(textContent);
        if (onSave != null) {
            onSave.accept(node);
        }
    }

    private void closeInternal() {
        hide();
    }

    private void closeWithoutSave() {
        closeInternal();
    }
}
