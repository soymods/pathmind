package com.pathmind.ui.overlay;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.ParameterType;
import com.pathmind.ui.control.InventorySlotSelector;
import com.pathmind.ui.control.VillagerTradeSelector;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.InventorySlotModeHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.font.TextRenderer;
import com.pathmind.util.RenderStateBridge;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.InputCompatibilityBridge;
import com.pathmind.util.ButtonWidgetCompatibilityBridge;

/**
 * Overlay widget for editing node parameters.
 * Appears on top of the existing GUI without replacing it.
 */
public class NodeParameterOverlay {
    private static final int CONTENT_START_OFFSET = 32;
    private static final int LABEL_TO_FIELD_OFFSET = 18;
    private static final int FIELD_HEIGHT = 20;
    private static final int SECTION_SPACING = 12;
    private static final int BUTTON_TOP_MARGIN = 8;
    private static final int BOTTOM_PADDING = 16;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MIN_POPUP_HEIGHT = 140;
    private static final int MIN_POPUP_WIDTH = 300;
    private static final int APPROX_CHAR_WIDTH = 6;
    private static final int POPUP_VERTICAL_MARGIN = 40;
    private static final int SCROLL_STEP = 18;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int KEY_SELECTOR_KEY_HEIGHT = 18;
    private static final int KEY_SELECTOR_ROW_GAP = 6;
    private static final int KEY_SELECTOR_KEY_GAP = 4;
    private static final int KEY_SELECTOR_PADDING = 6;

    private static final KeySpec[][] KEY_SELECTOR_LAYOUT = new KeySpec[][]{
        new KeySpec[]{
            key("Esc", "GLFW_KEY_ESCAPE", 1),
            key("1", "GLFW_KEY_1", 1),
            key("2", "GLFW_KEY_2", 1),
            key("3", "GLFW_KEY_3", 1),
            key("4", "GLFW_KEY_4", 1),
            key("5", "GLFW_KEY_5", 1),
            key("6", "GLFW_KEY_6", 1),
            key("7", "GLFW_KEY_7", 1),
            key("8", "GLFW_KEY_8", 1),
            key("9", "GLFW_KEY_9", 1),
            key("0", "GLFW_KEY_0", 1),
            key("-", "GLFW_KEY_MINUS", 1),
            key("=", "GLFW_KEY_EQUAL", 1),
            key("Back", "GLFW_KEY_BACKSPACE", 2)
        },
        new KeySpec[]{
            key("Tab", "GLFW_KEY_TAB", 2),
            key("Q", "GLFW_KEY_Q", 1),
            key("W", "GLFW_KEY_W", 1),
            key("E", "GLFW_KEY_E", 1),
            key("R", "GLFW_KEY_R", 1),
            key("T", "GLFW_KEY_T", 1),
            key("Y", "GLFW_KEY_Y", 1),
            key("U", "GLFW_KEY_U", 1),
            key("I", "GLFW_KEY_I", 1),
            key("O", "GLFW_KEY_O", 1),
            key("P", "GLFW_KEY_P", 1),
            key("[", "GLFW_KEY_LEFT_BRACKET", 1),
            key("]", "GLFW_KEY_RIGHT_BRACKET", 1),
            key("\\", "GLFW_KEY_BACKSLASH", 1)
        },
        new KeySpec[]{
            key("Caps", "GLFW_KEY_CAPS_LOCK", 2),
            key("A", "GLFW_KEY_A", 1),
            key("S", "GLFW_KEY_S", 1),
            key("D", "GLFW_KEY_D", 1),
            key("F", "GLFW_KEY_F", 1),
            key("G", "GLFW_KEY_G", 1),
            key("H", "GLFW_KEY_H", 1),
            key("J", "GLFW_KEY_J", 1),
            key("K", "GLFW_KEY_K", 1),
            key("L", "GLFW_KEY_L", 1),
            key(";", "GLFW_KEY_SEMICOLON", 1),
            key("'", "GLFW_KEY_APOSTROPHE", 1),
            key("Enter", "GLFW_KEY_ENTER", 2)
        },
        new KeySpec[]{
            key("Shift", "GLFW_KEY_LEFT_SHIFT", 2),
            key("Z", "GLFW_KEY_Z", 1),
            key("X", "GLFW_KEY_X", 1),
            key("C", "GLFW_KEY_C", 1),
            key("V", "GLFW_KEY_V", 1),
            key("B", "GLFW_KEY_B", 1),
            key("N", "GLFW_KEY_N", 1),
            key("M", "GLFW_KEY_M", 1),
            key(",", "GLFW_KEY_COMMA", 1),
            key(".", "GLFW_KEY_PERIOD", 1),
            key("/", "GLFW_KEY_SLASH", 1),
            key("Shift", "GLFW_KEY_RIGHT_SHIFT", 2)
        },
        new KeySpec[]{
            key("Ctrl", "GLFW_KEY_LEFT_CONTROL", 2),
            key("Alt", "GLFW_KEY_LEFT_ALT", 2),
            key("Space", "GLFW_KEY_SPACE", 6),
            key("Alt", "GLFW_KEY_RIGHT_ALT", 2),
            key("Ctrl", "GLFW_KEY_RIGHT_CONTROL", 2)
        }
    };

    private static final class KeySpec {
        private final String label;
        private final String value;
        private final int units;

        private KeySpec(String label, String value, int units) {
            this.label = label;
            this.value = value;
            this.units = units;
        }
    }

    private final Node node;
    private final List<String> parameterValues;
    private final List<Boolean> placeholderActive = new ArrayList<>();
    private int popupWidth = MIN_POPUP_WIDTH;
    private final int screenWidth;
    private final int screenHeight;
    private final int topBarHeight;
    private int popupHeight;
    private int popupX;
    private int popupY;
    private int totalContentHeight;
    private int maxScroll;
    private int scrollOffset;
    private ButtonWidget saveButton;
    private ButtonWidget cancelButton;
    private final Runnable onClose;
    private final Consumer<Node> onSave;
    private final PopupAnimationHandler popupAnimation = new PopupAnimationHandler();
    private boolean pendingClose = false;
    private int focusedFieldIndex = -1;
    private final boolean inventorySlotEditorActive;
    private final int inventorySlotParamIndex;
    private final int inventoryModeParamIndex;
    private final InventorySlotSelector inventorySlotSelector;
    private Boolean inventorySlotSelectionIsPlayer = null;
    private boolean suppressInventorySelectorCallbacks = false;
    private final boolean villagerTradeEditorActive;
    private final int villagerTradeParamIndex;
    private final int villagerProfessionParamIndex;
    private final VillagerTradeSelector villagerTradeSelector;
    private int textLineHeight = 9;
    private final List<Integer> caretPositions = new ArrayList<>();
    private final List<Integer> selectionStarts = new ArrayList<>();
    private final List<Integer> selectionEnds = new ArrayList<>();
    private final List<Integer> selectionAnchors = new ArrayList<>();
    private long caretBlinkLastToggle = 0L;
    private boolean caretVisible = true;
    private boolean scissorEnabled = false;

    public NodeParameterOverlay(Node node, int screenWidth, int screenHeight, int topBarHeight, Runnable onClose,
                                Consumer<Node> onSave) {
        this.node = node;
        this.onClose = onClose;
        this.onSave = onSave;
        this.parameterValues = new ArrayList<>();
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.topBarHeight = topBarHeight;

        int slotIndex = -1;
        int modeIndex = -1;
        int tradeIndex = -1;
        int professionIndex = -1;
        List<NodeParameter> params = node.getParameters();
        if (node.getType() == NodeType.PARAM_INVENTORY_SLOT) {
            for (int i = 0; i < params.size(); i++) {
                NodeParameter param = params.get(i);
                if (param == null) {
                    continue;
                }
                String name = param.getName();
                if ("Slot".equalsIgnoreCase(name)) {
                    slotIndex = i;
                } else if ("Mode".equalsIgnoreCase(name)) {
                    modeIndex = i;
                }
            }
        }
        if (node.getType() == NodeType.PARAM_VILLAGER_TRADE) {
            for (int i = 0; i < params.size(); i++) {
                NodeParameter param = params.get(i);
                if (param == null) {
                    continue;
                }
                String name = param.getName();
                if ("Trade".equalsIgnoreCase(name) || "Item".equalsIgnoreCase(name)) {
                    tradeIndex = i;
                } else if ("Profession".equalsIgnoreCase(name)) {
                    professionIndex = i;
                }
            }
        }
        this.inventorySlotParamIndex = slotIndex;
        this.inventoryModeParamIndex = modeIndex;
        this.inventorySlotEditorActive = slotIndex >= 0;
        if (inventorySlotEditorActive) {
            this.inventorySlotSelector = new InventorySlotSelector(new InventorySlotSelector.Listener() {
                @Override
                public void onSlotChanged(int slotId, boolean isPlayerSection) {
                    if (suppressInventorySelectorCallbacks) {
                        inventorySlotSelectionIsPlayer = isPlayerSection;
                        return;
                    }
                    inventorySlotSelectionIsPlayer = isPlayerSection;
                    if (inventorySlotParamIndex >= 0 && inventorySlotParamIndex < parameterValues.size()) {
                        setParameterValue(inventorySlotParamIndex, String.valueOf(slotId));
                    }
                    persistInventorySlotModeValue();
                }

                @Override
                public void onModeChanged(String modeId) {
                    if (suppressInventorySelectorCallbacks) {
                        return;
                    }
                    inventorySlotSelectionIsPlayer = null;
                    if (inventoryModeParamIndex >= 0 && inventoryModeParamIndex < parameterValues.size()) {
                        setParameterValue(inventoryModeParamIndex, InventorySlotModeHelper.buildStoredModeValue(modeId, null));
                    }
                }

                @Override
                public void requestLayoutRefresh() {
                    updatePopupDimensions();
                    recreateButtons();
                }
            });
        } else {
            this.inventorySlotSelector = null;
        }

        this.villagerTradeParamIndex = tradeIndex;
        this.villagerProfessionParamIndex = professionIndex;
        this.villagerTradeEditorActive = tradeIndex >= 0;
        if (villagerTradeEditorActive) {
            this.villagerTradeSelector = new VillagerTradeSelector(new VillagerTradeSelector.Listener() {
                @Override
                public void onProfessionChanged(String professionId) {
                    if (villagerProfessionParamIndex >= 0 && villagerProfessionParamIndex < parameterValues.size()) {
                        setParameterValue(villagerProfessionParamIndex, professionId);
                    }
                }

                @Override
                public void onTradeChanged(String tradeItemId) {
                    if (villagerTradeParamIndex >= 0 && villagerTradeParamIndex < parameterValues.size()) {
                        setParameterValue(villagerTradeParamIndex, tradeItemId);
                    }
                }

                @Override
                public void requestLayoutRefresh() {
                    updatePopupDimensions();
                    recreateButtons();
                }
            });
        } else {
            this.villagerTradeSelector = null;
        }

        updatePopupDimensions();
    }

    public void init() {
        resetParameterFields();
        if (inventorySlotEditorActive && inventorySlotSelector != null) {
            Boolean storedSelection = null;
            if (inventoryModeParamIndex >= 0 && inventoryModeParamIndex < parameterValues.size()) {
                String storedModeValue = parameterValues.get(inventoryModeParamIndex);
                storedSelection = InventorySlotModeHelper.extractPlayerSelectionFlag(storedModeValue);
                String storedModeId = InventorySlotModeHelper.extractModeId(storedModeValue);
                suppressInventorySelectorCallbacks = true;
                inventorySlotSelector.setModeById(storedModeId);
                suppressInventorySelectorCallbacks = false;
            }
            if (inventorySlotParamIndex >= 0 && inventorySlotParamIndex < parameterValues.size()) {
                try {
                    int storedSlot = Integer.parseInt(parameterValues.get(inventorySlotParamIndex));
                    suppressInventorySelectorCallbacks = true;
                    inventorySlotSelector.setSelectedSlot(storedSlot, storedSelection);
                    suppressInventorySelectorCallbacks = false;
                    inventorySlotSelectionIsPlayer = storedSelection;
                } catch (NumberFormatException e) {
                    inventorySlotSelectionIsPlayer = null;
                    suppressInventorySelectorCallbacks = true;
                    inventorySlotSelector.setSelectedSlot(0, null);
                    suppressInventorySelectorCallbacks = false;
                }
            } else {
                inventorySlotSelectionIsPlayer = storedSelection;
            }
            persistInventorySlotModeValue();
        }
        if (villagerTradeEditorActive && villagerTradeSelector != null) {
            if (villagerProfessionParamIndex >= 0 && villagerProfessionParamIndex < parameterValues.size()) {
                villagerTradeSelector.setProfessionById(parameterValues.get(villagerProfessionParamIndex));
            }
            if (villagerTradeParamIndex >= 0 && villagerTradeParamIndex < parameterValues.size()) {
                villagerTradeSelector.setSelectedTradeKey(parameterValues.get(villagerTradeParamIndex));
            }
        }

        updatePopupDimensions();
        recreateButtons();
        scrollOffset = Math.min(scrollOffset, maxScroll);
        updateButtonPositions();
    }

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        scissorEnabled = false;
        popupAnimation.tick();
        if (pendingClose && popupAnimation.isFullyHidden()) {
            pendingClose = false;
            if (onClose != null) {
                onClose.run();
            }
            return;
        }
        if (!popupAnimation.isVisible()) return;

        float popupAlpha = popupAnimation.getPopupAlpha();
        RenderStateBridge.setShaderColor(1f, 1f, 1f, popupAlpha);

        // Get animated popup bounds
        int[] bounds = popupAnimation.getScaledPopupBoundsFromTopLeft(popupX, popupY, popupWidth, popupHeight);
        int scaledX = bounds[0];
        int scaledY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];

        // Render popup background
        context.fill(scaledX, scaledY, scaledX + scaledWidth, scaledY + scaledHeight,
            applyPopupAlpha(UITheme.BACKGROUND_SECONDARY, popupAlpha));
        DrawContextBridge.drawBorder(context, scaledX, scaledY, scaledWidth, scaledHeight,
            applyPopupAlpha(UITheme.BORDER_HIGHLIGHT, popupAlpha)); // Grey outline

        int clipLeft = scaledX;
        int clipTop = scaledY;
        int clipRight = scaledX + scaledWidth;
        int clipBottom = scaledY + scaledHeight;
        setScissor(context, clipLeft, clipTop, clipRight, clipBottom);

        // Render title
        context.drawTextWithShadow(
            textRenderer,
            Text.literal("Edit Parameters: " + node.getType().getDisplayName()),
            popupX + 20,
            popupY + 15,
            applyPopupAlpha(UITheme.TEXT_PRIMARY, popupAlpha)
        );

        updateButtonPositions();

        int contentTop = getScrollAreaTop();
        int contentBottom = getScrollAreaBottom();
        int contentRight = popupX + popupWidth;

        int contentClipLeft = Math.max(popupX + 1, clipLeft);
        int contentClipTop = Math.max(contentTop, clipTop);
        int contentClipRight = Math.min(contentRight - 1, clipRight);
        int contentClipBottom = Math.min(contentBottom, clipBottom);
        if (contentClipRight > contentClipLeft && contentClipBottom > contentClipTop) {
            setScissor(context, contentClipLeft, contentClipTop, contentClipRight, contentClipBottom);
        } else {
            setScissor(context, 0, 0, 0, 0);
        }

        int sectionY = contentTop - scrollOffset;

        this.textLineHeight = textRenderer.fontHeight;
        for (int i = 0; i < node.getParameters().size(); i++) {
            if (!shouldDisplayParameter(i)) {
                continue;
            }
            NodeParameter param = node.getParameters().get(i);
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(getParameterDisplayName(param) + " (" + param.getType().getDisplayName() + "):"),
                popupX + 20,
                sectionY + 4,
                applyPopupAlpha(UITheme.TEXT_PRIMARY, popupAlpha)
            );

            int fieldX = popupX + 20;
            int fieldY = sectionY + LABEL_TO_FIELD_OFFSET;
            int fieldWidth = popupWidth - 40;
            int fieldHeight = FIELD_HEIGHT;
            if (inventorySlotEditorActive && i == inventorySlotParamIndex && inventorySlotSelector != null) {
                int selectorHeight = inventorySlotSelector.render(context, textRenderer, fieldX, fieldY, fieldWidth, mouseX, mouseY, popupAlpha);
                sectionY = fieldY + selectorHeight + SECTION_SPACING;
                continue;
            }
            if (villagerTradeEditorActive && i == villagerTradeParamIndex && villagerTradeSelector != null) {
                int selectorHeight = villagerTradeSelector.render(context, textRenderer, fieldX, fieldY, fieldWidth, mouseX, mouseY, popupAlpha);
                sectionY = fieldY + selectorHeight + SECTION_SPACING;
                continue;
            }

            if (usesKeySelectorForIndex(i)) {
                int selectorHeight = renderKeySelector(context, textRenderer, fieldX, fieldY, fieldWidth, mouseX, mouseY, i, popupAlpha);
                sectionY = fieldY + selectorHeight + SECTION_SPACING;
                continue;
            }

            boolean isFocused = i == focusedFieldIndex;
            int bgColor;
            int borderColor;
            if (isFocused) {
                bgColor = UITheme.BACKGROUND_SECONDARY;
                borderColor = UITheme.ACCENT_DEFAULT;
            } else {
                bgColor = UITheme.BACKGROUND_SIDEBAR;
                borderColor = UITheme.BORDER_HIGHLIGHT;
            }

            context.fill(fieldX, fieldY, fieldX + fieldWidth, fieldY + fieldHeight, applyPopupAlpha(bgColor, popupAlpha));
            DrawContextBridge.drawBorder(context, fieldX, fieldY, fieldWidth, fieldHeight, applyPopupAlpha(borderColor, popupAlpha));

            String text = parameterValues.get(i);
            String baseValue = text != null ? text : "";
            boolean showingPlaceholder = isPlaceholderActive(i);
            String displayText;
            int textColor;
            if (showingPlaceholder) {
                String placeholder = getPlaceholderText(i);
                displayText = trimDisplayString(textRenderer, placeholder, fieldWidth - 8);
                textColor = UITheme.TEXT_TERTIARY;
            } else if (isFocused) {
                displayText = baseValue;
                textColor = UITheme.TEXT_PRIMARY;
            } else {
                displayText = trimDisplayString(textRenderer, baseValue, fieldWidth - 8);
                textColor = UITheme.TEXT_PRIMARY;
            }
            int textX = fieldX + 4;
            int textY = fieldY + 6;

                if (!showingPlaceholder && isFocused && hasSelectionForField(i)) {
                    String value = baseValue;
                    int start = Math.max(0, selectionStarts.get(i));
                    int end = Math.max(0, selectionEnds.get(i));
                    int clampedStart = Math.min(start, value.length());
                    int clampedEnd = Math.min(end, value.length());
                    if (clampedEnd < clampedStart) {
                        int tmp = clampedStart;
                        clampedStart = clampedEnd;
                        clampedEnd = tmp;
                    }
                    int selectionStartX = textX + textRenderer.getWidth(value.substring(0, clampedStart));
                    int selectionEndX = textX + textRenderer.getWidth(value.substring(0, clampedEnd));
                    selectionStartX = MathHelper.clamp(selectionStartX, fieldX + 2, fieldX + fieldWidth - 2);
                    selectionEndX = MathHelper.clamp(selectionEndX, fieldX + 2, fieldX + fieldWidth - 2);
                    if (selectionEndX != selectionStartX) {
                        context.fill(selectionStartX, fieldY + 4, selectionEndX, fieldY + fieldHeight - 4,
                            applyPopupAlpha(0x80426AD5, popupAlpha));
                    }
                }

            if (!displayText.isEmpty()) {
                context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(displayText),
                    textX,
                    textY,
                    applyPopupAlpha(textColor, popupAlpha)
                );
            }

            if (isFocused) {
                updateCaretBlinkState();
                if (caretVisible) {
                    int caretIndex = showingPlaceholder ? 0 : MathHelper.clamp(caretPositions.get(i), 0, baseValue.length());
                    int caretX = textX + textRenderer.getWidth(baseValue.substring(0, caretIndex));
                    caretX = Math.min(caretX, fieldX + fieldWidth - 2);
                    context.fill(caretX, fieldY + 4, caretX + 1, fieldY + fieldHeight - 4,
                        applyPopupAlpha(UITheme.TEXT_PRIMARY, popupAlpha));
                }
            }

            sectionY = fieldY + fieldHeight + SECTION_SPACING;
        }

        clearScissor(context);
        setScissor(context, clipLeft, clipTop, clipRight, clipBottom);

        renderButton(context, textRenderer, saveButton, mouseX, mouseY);
        renderButton(context, textRenderer, cancelButton, mouseX, mouseY);

        setScissor(context, clipLeft, clipTop, clipRight, clipBottom);

        renderScrollbar(context, contentTop, contentBottom);
        clearScissor(context);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderButton(DrawContext context, TextRenderer textRenderer, ButtonWidget button, int mouseX, int mouseY) {
        if (button == null) {
            return;
        }

        boolean hovered = mouseX >= button.getX() && mouseX <= button.getX() + button.getWidth() &&
                         mouseY >= button.getY() && mouseY <= button.getY() + button.getHeight();

        float popupAlpha = popupAnimation.getPopupAlpha();
        int bgColor = hovered ? UITheme.BUTTON_DEFAULT_HOVER : UITheme.BUTTON_DEFAULT_BG;
        context.fill(button.getX(), button.getY(), button.getX() + button.getWidth(), button.getY() + button.getHeight(),
            applyPopupAlpha(bgColor, popupAlpha));
        float hoverProgress = HoverAnimator.getProgress(button, hovered);
        int borderColor = AnimationHelper.lerpColor(
            UITheme.BORDER_HIGHLIGHT,
            UITheme.BUTTON_HOVER_OUTLINE,
            hoverProgress
        );
        DrawContextBridge.drawBorder(context, button.getX(), button.getY(), button.getWidth(), button.getHeight(),
            applyPopupAlpha(borderColor, popupAlpha));

        // Render button text
        context.drawCenteredTextWithShadow(
            textRenderer,
            button.getMessage(),
            button.getX() + button.getWidth() / 2,
            button.getY() + 6,
            applyPopupAlpha(UITheme.TEXT_PRIMARY, popupAlpha)
        );
    }

    private void renderScrollbar(DrawContext context, int contentTop, int contentBottom) {
        if (maxScroll <= 0) {
            return;
        }

        int trackLeft = popupX + popupWidth - 12;
        int trackRight = trackLeft + SCROLLBAR_WIDTH;
        int trackTop = contentTop;
        int trackBottom = contentBottom;
        int trackHeight = Math.max(1, trackBottom - trackTop);

        float popupAlpha = popupAnimation.getPopupAlpha();
        context.fill(trackLeft, trackTop, trackRight, trackBottom, applyPopupAlpha(UITheme.BACKGROUND_SIDEBAR, popupAlpha));
        DrawContextBridge.drawBorder(context, trackLeft, trackTop, SCROLLBAR_WIDTH, trackHeight,
            applyPopupAlpha(UITheme.BORDER_DEFAULT, popupAlpha));

        int visibleScrollableHeight = Math.max(1, contentBottom - contentTop);
        int totalScrollableHeight = Math.max(visibleScrollableHeight, visibleScrollableHeight + maxScroll);
        int knobHeight = Math.max(20, (int) ((float) visibleScrollableHeight / totalScrollableHeight * trackHeight));
        int maxKnobTravel = Math.max(0, trackHeight - knobHeight);
        int knobOffset = maxKnobTravel <= 0 ? 0 : (int) ((float) scrollOffset / (float) maxScroll * maxKnobTravel);
        int knobTop = trackTop + knobOffset;

        context.fill(trackLeft + 1, knobTop, trackRight - 1, knobTop + knobHeight,
            applyPopupAlpha(UITheme.BORDER_DEFAULT, popupAlpha));
    }

    private int applyPopupAlpha(int color, float alphaMultiplier) {
        int baseAlpha = (color >>> 24) & 0xFF;
        int applied = Math.round(baseAlpha * MathHelper.clamp(alphaMultiplier, 0f, 1f));
        return (applied << 24) | (color & 0x00FFFFFF);
    }

    private void setScissor(DrawContext context, int left, int top, int right, int bottom) {
        if (scissorEnabled) {
            context.disableScissor();
            scissorEnabled = false;
        }
        context.enableScissor(left, top, right, bottom);
        scissorEnabled = true;
    }

    private void clearScissor(DrawContext context) {
        if (!scissorEnabled) {
            return;
        }
        context.disableScissor();
        scissorEnabled = false;
    }

    private void persistInventorySlotModeValue() {
        if (!inventorySlotEditorActive || inventorySlotSelector == null) {
            return;
        }
        if (inventoryModeParamIndex < 0 || inventoryModeParamIndex >= parameterValues.size()) {
            return;
        }
        setParameterValue(
            inventoryModeParamIndex,
            InventorySlotModeHelper.buildStoredModeValue(
                inventorySlotSelector.getModeId(),
                inventorySlotSelectionIsPlayer
            )
        );
    }

    private void setParameterValue(int index, String value) {
        if (index < 0 || index >= parameterValues.size()) {
            return;
        }
        ensureCaretEntry(index);
        String normalized = value != null ? value : "";
        parameterValues.set(index, normalized);
        caretPositions.set(index, normalized.length());
        placeholderActive.set(index, false);
        handleParameterValueChanged(index);
    }

    private void handleParameterValueChanged(int index) {
    }

    private boolean shouldDisplayParameter(int index) {
        if (inventorySlotEditorActive && index == inventoryModeParamIndex) {
            return false;
        }
        if (villagerTradeEditorActive && index == villagerProfessionParamIndex) {
            return false;
        }
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!popupAnimation.isVisible()) return false;

        updateButtonPositions();

        int[] bounds = popupAnimation.getScaledPopupBoundsFromTopLeft(popupX, popupY, popupWidth, popupHeight);
        int scaledX = bounds[0];
        int scaledY = bounds[1];
        float scaleX = popupWidth > 0 ? (float) popupWidth / bounds[2] : 1f;
        float scaleY = popupHeight > 0 ? (float) popupHeight / bounds[3] : 1f;
        double adjustedMouseX = popupX + (mouseX - scaledX) * scaleX;
        double adjustedMouseY = popupY + (mouseY - scaledY) * scaleY;

        if (saveButton != null && saveButton.isMouseOver(adjustedMouseX, adjustedMouseY)) {
            saveParameters();
            return true;
        }
        if (cancelButton != null && cancelButton.isMouseOver(adjustedMouseX, adjustedMouseY)) {
            close();
            return true;
        }

        if (inventorySlotEditorActive && inventorySlotSelector != null) {
            if (inventorySlotSelector.mouseClicked(adjustedMouseX, adjustedMouseY)) {
                return true;
            }
        }
        if (villagerTradeEditorActive && villagerTradeSelector != null) {
            if (villagerTradeSelector.mouseClicked(adjustedMouseX, adjustedMouseY)) {
                return true;
            }
        }

        int contentTop = getScrollAreaTop();
        int contentBottom = getScrollAreaBottom();
        int labelY = contentTop - scrollOffset;

        boolean shiftClick = InputCompatibilityBridge.hasShiftDown();
        for (int i = 0; i < node.getParameters().size(); i++) {
            if (!shouldDisplayParameter(i)) {
                continue;
            }
            int fieldX = popupX + 20;
            int fieldY = labelY + LABEL_TO_FIELD_OFFSET;
            int fieldWidth = popupWidth - 40;
            int fieldHeight = FIELD_HEIGHT;

            if (usesKeySelectorForIndex(i)) {
                int selectorHeight = getKeySelectorHeight();
                if (adjustedMouseX >= fieldX && adjustedMouseX <= fieldX + fieldWidth &&
                    adjustedMouseY >= Math.max(fieldY, contentTop) && adjustedMouseY <= Math.min(fieldY + selectorHeight, contentBottom)) {
                    if (handleKeySelectorClick(adjustedMouseX, adjustedMouseY, fieldX, fieldY, fieldWidth, i)) {
                        return true;
                    }
                    return true;
                }
                labelY = fieldY + selectorHeight + SECTION_SPACING;
                continue;
            }

            if (adjustedMouseX >= fieldX && adjustedMouseX <= fieldX + fieldWidth &&
                adjustedMouseY >= Math.max(fieldY, contentTop) && adjustedMouseY <= Math.min(fieldY + fieldHeight, contentBottom)) {
                focusField(i);
                setCaretFromClick(i, adjustedMouseX, fieldX, fieldWidth, shiftClick);
                return true;
            }

            labelY = fieldY + fieldHeight + SECTION_SPACING;
        }

        boolean insidePopupBounds = adjustedMouseX >= popupX && adjustedMouseX <= popupX + popupWidth &&
                                    adjustedMouseY >= popupY && adjustedMouseY <= popupY + popupHeight;
        if (!insidePopupBounds) {
            close();
            return true;
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (!popupAnimation.isVisible()) return false;

        if (inventorySlotEditorActive && inventorySlotSelector != null) {
            if (inventorySlotSelector.mouseScrolled(mouseX, mouseY, verticalAmount)) {
                return true;
            }
        }
        if (villagerTradeEditorActive && villagerTradeSelector != null) {
            if (villagerTradeSelector.mouseScrolled(mouseX, mouseY, verticalAmount)) {
                return true;
            }
        }

        if (mouseX < popupX || mouseX > popupX + popupWidth || mouseY < popupY || mouseY > popupY + popupHeight) {
            return true;
        }

        if (maxScroll <= 0) {
            return true;
        }

        double newOffset = scrollOffset - verticalAmount * SCROLL_STEP;
        int clampedOffset = (int) Math.round(newOffset);
        clampedOffset = Math.max(0, Math.min(maxScroll, clampedOffset));
        if (clampedOffset != scrollOffset) {
            scrollOffset = clampedOffset;
            updateButtonPositions();
        }

        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!popupAnimation.isVisible()) return false;

        if (villagerTradeEditorActive && villagerTradeSelector != null && villagerTradeSelector.hasFocusedInput()) {
            if (villagerTradeSelector.keyPressed(keyCode, modifiers)) {
                return true;
            }
        }

        boolean handledFieldInput = false;
        if (focusedFieldIndex >= 0
            && focusedFieldIndex < parameterValues.size()) {
            handledFieldInput = handleFocusedFieldKeyPressed(keyCode, modifiers);
            if (handledFieldInput) {
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_TAB) {
            boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            focusAdjacentEditableField(shiftHeld);
            return true;
        }
        
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            saveParameters();
            return true;
        }
        
        return handledFieldInput;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!popupAnimation.isVisible()) return false;

        if (villagerTradeEditorActive && villagerTradeSelector != null && villagerTradeSelector.hasFocusedInput()) {
            if (villagerTradeSelector.charTyped(chr, modifiers)) {
                return true;
            }
        }

        if (focusedFieldIndex >= 0
            && focusedFieldIndex < parameterValues.size()) {
            if (chr >= 32 && chr != 127) {
                return insertTextForField(focusedFieldIndex, String.valueOf(chr));
            }
        }
        
        return false;
    }

    private void saveParameters() {
        // Update node parameters with field values
        List<NodeParameter> parameters = node.getParameters();
        List<String> emptyParameterNames = new ArrayList<>();
        for (int i = 0; i < parameters.size() && i < parameterValues.size(); i++) {
            if (!shouldDisplayParameter(i)) {
                continue;
            }
            NodeParameter param = parameters.get(i);
            if (param == null || param.getType() != ParameterType.STRING) {
                continue;
            }
            String value = parameterValues.get(i);
            boolean empty = value == null || value.trim().isEmpty();
            if (empty && !isPlaceholderActive(i) && !isPlayerParameter(param) && !isMessageParameter(param)) {
                emptyParameterNames.add(getParameterDisplayName(param));
            }
        }
        if (!emptyParameterNames.isEmpty()) {
            warnEmptyParameters(emptyParameterNames);
        }
        for (int i = 0; i < parameters.size() && i < parameterValues.size(); i++) {
            NodeParameter param = parameters.get(i);
            String value = parameterValues.get(i);
            if (param != null && (isPlayerParameter(param) || isMessageParameter(param))) {
                if (value == null || value.trim().isEmpty()) {
                    String placeholder = getPlaceholderText(i);
                    String applied = placeholder != null
                        ? placeholder
                        : (isPlayerParameter(param) ? "Self" : "Any");
                    param.setStringValue(applied);
                    param.setUserEdited(false);
                    parameterValues.set(i, applied);
                    placeholderActive.set(i, true);
                    continue;
                }
            }
            if (isPlaceholderActive(i)) {
                param.setStringValue(value);
            } else {
                param.setStringValueFromUser(value);
            }
        }

        if (node.isParameterNode() && node.getParentParameterHost() != null) {
            Node host = node.getParentParameterHost();
            int slotIndex = node.getParentParameterSlotIndex();
            if (slotIndex < 0) {
                slotIndex = 0;
            }
            host.attachParameter(node, slotIndex);
        }

        node.setParameterFieldWidthOverride(0);
        node.recalculateDimensions();

        if (onSave != null) {
            onSave.accept(node);
        }

        close();
    }

    private void warnEmptyParameters(List<String> parameterNames) {
        if (parameterNames == null || parameterNames.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        String joined = String.join(", ", parameterNames);
        client.player.sendMessage(Text.literal("Pathmind: " + joined + " cannot be empty."), false);
    }

    public void close() {
        popupAnimation.hide();
        pendingClose = true;
        focusedFieldIndex = -1;
        if (inventorySlotSelector != null) {
            inventorySlotSelector.closeDropdown();
        }
        if (villagerTradeSelector != null) {
            villagerTradeSelector.closeDropdown();
        }
    }

    public void show() {
        popupAnimation.show();
        pendingClose = false;
        focusedFieldIndex = -1;
        scrollOffset = 0;
        updateButtonPositions();
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
    
    private void resetParameterFields() {
        parameterValues.clear();
        caretPositions.clear();
        selectionStarts.clear();
        selectionEnds.clear();
        selectionAnchors.clear();
        placeholderActive.clear();
       
        for (NodeParameter param : node.getParameters()) {
            String value = param.getStringValue();
            boolean usesPlaceholder = shouldUsePlaceholder(param, value);
            parameterValues.add(value);
            int caretPos = usesPlaceholder ? 0 : (value != null ? value.length() : 0);
            caretPositions.add(caretPos);
            selectionStarts.add(-1);
            selectionEnds.add(-1);
            selectionAnchors.add(-1);
            placeholderActive.add(usesPlaceholder);
        }
        focusedFieldIndex = -1;
        resetCaretBlink();
    }

    private boolean shouldUsePlaceholder(NodeParameter parameter, String value) {
        if (parameter == null || parameter.getType() != ParameterType.STRING) {
            return false;
        }
        if ((isPlayerParameter(parameter) || isMessageParameter(parameter))
            && (value == null || value.trim().isEmpty())) {
            return true;
        }
        if (parameter.isUserEdited()) {
            return false;
        }
        String placeholder = parameter.getDefaultValue();
        if (placeholder == null || placeholder.isEmpty()) {
            return false;
        }
        return Objects.equals(placeholder, value);
    }

    private boolean isPlayerParameter(NodeParameter parameter) {
        if (parameter == null) {
            return false;
        }
        return node.getType() == NodeType.PARAM_PLAYER && "Player".equalsIgnoreCase(parameter.getName());
    }

    private boolean isMessageParameter(NodeParameter parameter) {
        if (parameter == null) {
            return false;
        }
        if (node.getType() != NodeType.PARAM_MESSAGE) {
            return false;
        }
        String name = parameter.getName();
        return "Text".equalsIgnoreCase(name) || "Message".equalsIgnoreCase(name);
    }

    private String getParameterDisplayName(NodeParameter parameter) {
        if (parameter == null) {
            return "";
        }
        String name = parameter.getName();
        if (node.getType() == NodeType.PARAM_PLAYER && "Player".equalsIgnoreCase(name)) {
            return "User";
        }
        if (node.getType() == NodeType.PARAM_MESSAGE && "Text".equalsIgnoreCase(name)) {
            return "Message";
        }
        if (node.getType() == NodeType.PARAM_VILLAGER_TRADE
            && ("Item".equalsIgnoreCase(name) || "Trade".equalsIgnoreCase(name))) {
            return "Trade";
        }
        return name;
    }

    private void updatePopupDimensions() {
        int longestLineLength = ("Edit Parameters: " + node.getType().getDisplayName()).length();

        for (int i = 0; i < node.getParameters().size(); i++) {
            if (!shouldDisplayParameter(i)) {
                continue;
            }
            NodeParameter param = node.getParameters().get(i);
            String label = getParameterDisplayName(param) + " (" + param.getType().getDisplayName() + "):";
            longestLineLength = Math.max(longestLineLength, label.length());
            String value = param.getStringValue();
            if (value != null) {
                longestLineLength = Math.max(longestLineLength, value.length());
            }
        }

        for (int i = 0; i < parameterValues.size(); i++) {
            if (!shouldDisplayParameter(i)) {
                continue;
            }
            String value = parameterValues.get(i);
            if (value != null) {
                longestLineLength = Math.max(longestLineLength, value.length());
            }
        }

        int computedWidth = longestLineLength * APPROX_CHAR_WIDTH + 64; // Padding for margins and borders
        int maxAllowedWidth = Math.max(MIN_POPUP_WIDTH, screenWidth - 40);
        this.popupWidth = Math.min(Math.max(MIN_POPUP_WIDTH, computedWidth), maxAllowedWidth);

        int contentHeight = CONTENT_START_OFFSET;

        int paramCount = node.getParameters().size();
        int visibleProcessed = 0;
        int visibleTotal = 0;
        for (int i = 0; i < paramCount; i++) {
            if (shouldDisplayParameter(i)) {
                visibleTotal++;
            }
        }
        for (int i = 0; i < paramCount; i++) {
            if (!shouldDisplayParameter(i)) {
                continue;
            }
            if (inventorySlotEditorActive && i == inventorySlotParamIndex && inventorySlotSelector != null) {
                int estimatedHeight = inventorySlotSelector.getEstimatedHeight(textLineHeight);
                contentHeight += LABEL_TO_FIELD_OFFSET + estimatedHeight;
            } else if (villagerTradeEditorActive && i == villagerTradeParamIndex && villagerTradeSelector != null) {
                int estimatedHeight = villagerTradeSelector.getEstimatedHeight(textLineHeight);
                contentHeight += LABEL_TO_FIELD_OFFSET + estimatedHeight;
            } else if (usesKeySelectorForIndex(i)) {
                contentHeight += LABEL_TO_FIELD_OFFSET + getKeySelectorHeight();
            } else {
                contentHeight += LABEL_TO_FIELD_OFFSET + FIELD_HEIGHT;
            }
            visibleProcessed++;
            if (visibleProcessed < visibleTotal) {
                contentHeight += SECTION_SPACING;
            }
        }

        contentHeight += BUTTON_TOP_MARGIN + BUTTON_HEIGHT + BOTTOM_PADDING;

        this.totalContentHeight = contentHeight;

        int maxPopupHeight = Math.max(MIN_POPUP_HEIGHT, screenHeight - topBarHeight - POPUP_VERTICAL_MARGIN);
        this.popupHeight = Math.min(Math.max(MIN_POPUP_HEIGHT, contentHeight), maxPopupHeight);
        this.popupX = Math.max(0, (screenWidth - popupWidth) / 2);
        int availableHeight = Math.max(0, screenHeight - topBarHeight);
        int centeredOffset = Math.max(0, (availableHeight - popupHeight) / 2);
        this.popupY = topBarHeight + centeredOffset;

        this.maxScroll = Math.max(0, totalContentHeight - popupHeight);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }
    
    private void recreateButtons() {
        this.saveButton = ButtonWidget.builder(
            Text.literal("Save"),
            b -> saveParameters()
        ).dimensions(popupX + 20, computeVisibleButtonY(), BUTTON_WIDTH, BUTTON_HEIGHT).build();

        this.cancelButton = ButtonWidget.builder(
            Text.literal("Cancel"),
            b -> close()
        ).dimensions(popupX + popupWidth - (BUTTON_WIDTH + 20), computeVisibleButtonY(), BUTTON_WIDTH, BUTTON_HEIGHT).build();

        updateButtonPositions();
    }
    

    private int computeButtonY() {
        int contentEnd = totalContentHeight - (BUTTON_TOP_MARGIN + BUTTON_HEIGHT + BOTTOM_PADDING);
        return popupY + contentEnd + BUTTON_TOP_MARGIN;
    }

    private void updateButtonPositions() {
        int adjustedY = computeVisibleButtonY();

        if (saveButton != null) {
            saveButton.setX(popupX + 20);
            saveButton.setY(adjustedY);
        }

        if (cancelButton != null) {
            cancelButton.setX(popupX + popupWidth - (BUTTON_WIDTH + 20));
            cancelButton.setY(adjustedY);
        }
    }

    private int computeVisibleButtonY() {
        int base = computeButtonY();
        int bottomLimit = popupY + popupHeight - BOTTOM_PADDING - BUTTON_HEIGHT;
        int topLimit = popupY + CONTENT_START_OFFSET;
        int clamped = Math.min(base, bottomLimit);
        return Math.max(clamped, topLimit);
    }

    private void focusAdjacentEditableField(boolean backwards) {
        List<NodeParameter> params = node.getParameters();
        int total = params.size();
        if (total == 0) {
            focusedFieldIndex = -1;
            return;
        }
        int startIndex = focusedFieldIndex;
        if (startIndex < 0 || startIndex >= total) {
            startIndex = backwards ? total : -1;
        }
        for (int attempts = 0; attempts < total; attempts++) {
            startIndex = (startIndex + (backwards ? -1 : 1) + total) % total;
            if (!usesKeySelectorForIndex(startIndex)) {
                focusField(startIndex);
                return;
            }
        }
        focusedFieldIndex = -1;
    }

    private boolean usesKeySelectorForIndex(int index) {
        if (node.getType() != NodeType.PARAM_KEY) {
            return false;
        }
        if (index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        return param != null && "Key".equalsIgnoreCase(param.getName());
    }

    private int renderKeySelector(DrawContext context, TextRenderer textRenderer, int fieldX, int fieldY, int fieldWidth,
                                  int mouseX, int mouseY, int paramIndex, float popupAlpha) {
        int totalHeight = getKeySelectorHeight();
        int backgroundColor = UITheme.BACKGROUND_SIDEBAR;
        int borderColor = UITheme.BORDER_HIGHLIGHT;
        context.fill(fieldX, fieldY, fieldX + fieldWidth, fieldY + totalHeight, applyPopupAlpha(backgroundColor, popupAlpha));
        DrawContextBridge.drawBorder(context, fieldX, fieldY, fieldWidth, totalHeight, applyPopupAlpha(borderColor, popupAlpha));

        String currentValue = paramIndex >= 0 && paramIndex < parameterValues.size()
            ? parameterValues.get(paramIndex)
            : "";
        int rowTop = fieldY + KEY_SELECTOR_PADDING;
        int usableWidth = fieldWidth - 2 * KEY_SELECTOR_PADDING;
        int keyHeight = KEY_SELECTOR_KEY_HEIGHT;

        for (KeySpec[] row : KEY_SELECTOR_LAYOUT) {
            int totalUnits = 0;
            for (KeySpec key : row) {
                totalUnits += key.units;
            }
            int gapCount = Math.max(0, row.length - 1);
            int availableWidth = Math.max(0, usableWidth - gapCount * KEY_SELECTOR_KEY_GAP);
            float unitWidth = totalUnits > 0 ? (float) availableWidth / (float) totalUnits : 0f;
            int keyX = fieldX + KEY_SELECTOR_PADDING;
            for (KeySpec key : row) {
                int keyWidth = Math.max(8, Math.round(unitWidth * key.units));
                boolean hovered = mouseX >= keyX && mouseX <= keyX + keyWidth &&
                    mouseY >= rowTop && mouseY <= rowTop + keyHeight;
                boolean selected = currentValue != null && currentValue.equalsIgnoreCase(key.value);

                int keyBase = selected ? UITheme.KEY_SELECTED_BG : UITheme.BACKGROUND_SECONDARY;
                if (hovered) {
                    keyBase = adjustColorBrightness(keyBase, 1.2f);
                }
                int keyBorder = selected ? UITheme.ACCENT_DEFAULT : UITheme.TEXT_TERTIARY;
                context.fill(keyX, rowTop, keyX + keyWidth, rowTop + keyHeight, applyPopupAlpha(keyBase, popupAlpha));
                DrawContextBridge.drawBorder(context, keyX, rowTop, keyWidth, keyHeight, applyPopupAlpha(keyBorder, popupAlpha));

                String label = key.label;
                int textWidth = textRenderer.getWidth(label);
                int textX = keyX + Math.max(2, (keyWidth - textWidth) / 2);
                int textY = rowTop + (keyHeight - textRenderer.fontHeight) / 2 + 1;
                context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(label),
                    textX,
                    textY,
                    applyPopupAlpha(UITheme.TEXT_PRIMARY, popupAlpha)
                );

                keyX += keyWidth + KEY_SELECTOR_KEY_GAP;
            }
            rowTop += keyHeight + KEY_SELECTOR_ROW_GAP;
        }

        return totalHeight;
    }

    private boolean handleKeySelectorClick(double mouseX, double mouseY, int fieldX, int fieldY, int fieldWidth, int paramIndex) {
        int rowTop = fieldY + KEY_SELECTOR_PADDING;
        int usableWidth = fieldWidth - 2 * KEY_SELECTOR_PADDING;
        int keyHeight = KEY_SELECTOR_KEY_HEIGHT;
        for (KeySpec[] row : KEY_SELECTOR_LAYOUT) {
            int totalUnits = 0;
            for (KeySpec key : row) {
                totalUnits += key.units;
            }
            int gapCount = Math.max(0, row.length - 1);
            int availableWidth = Math.max(0, usableWidth - gapCount * KEY_SELECTOR_KEY_GAP);
            float unitWidth = totalUnits > 0 ? (float) availableWidth / (float) totalUnits : 0f;
            int keyX = fieldX + KEY_SELECTOR_PADDING;
            for (KeySpec key : row) {
                int keyWidth = Math.max(8, Math.round(unitWidth * key.units));
                if (mouseX >= keyX && mouseX <= keyX + keyWidth &&
                    mouseY >= rowTop && mouseY <= rowTop + keyHeight) {
                    setParameterValue(paramIndex, key.value);
                    return true;
                }
                keyX += keyWidth + KEY_SELECTOR_KEY_GAP;
            }
            rowTop += keyHeight + KEY_SELECTOR_ROW_GAP;
        }
        return false;
    }

    private int getKeySelectorHeight() {
        int rows = KEY_SELECTOR_LAYOUT.length;
        return KEY_SELECTOR_PADDING * 2 + rows * KEY_SELECTOR_KEY_HEIGHT + (rows - 1) * KEY_SELECTOR_ROW_GAP;
    }

    private static KeySpec key(String label, String value, int units) {
        return new KeySpec(label, value, units);
    }


    private void focusField(int index) {
        if (index < 0 || index >= parameterValues.size()) {
            focusedFieldIndex = -1;
            return;
        }
        focusedFieldIndex = index;
        ensureCaretEntry(index);
        String value = getFieldValue(index);
        caretPositions.set(index, MathHelper.clamp(caretPositions.get(index), 0, value.length()));
        clearSelectionForField(index);
        resetCaretBlink();
    }

    private void setCaretFromClick(int index, double mouseX, int fieldX, int fieldWidth, boolean extendSelection) {
        if (index < 0 || index >= parameterValues.size()) {
            return;
        }
        TextRenderer textRenderer = getClientTextRenderer();
        if (textRenderer == null) {
            return;
        }
        int relativeX = (int)Math.round(mouseX) - (fieldX + 4);
        int clampedX = MathHelper.clamp(relativeX, 0, fieldWidth - 8);
        String value = getFieldValue(index);
        int caretIndex = getCaretIndexForPixel(value, clampedX, textRenderer);
        moveCaretForField(index, caretIndex, extendSelection);
    }

    private int getCaretIndexForPixel(String value, int targetX, TextRenderer textRenderer) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int charWidth = textRenderer.getWidth(String.valueOf(c));
            if (width + charWidth / 2 >= targetX) {
                return i;
            }
            width += charWidth;
        }
        return value.length();
    }

    private void moveCaretForField(int index, int position, boolean extendSelection) {
        ensureCaretEntry(index);
        String value = getFieldValue(index);
        int clamped = MathHelper.clamp(position, 0, value.length());
        if (extendSelection) {
            if (selectionAnchors.get(index) == -1) {
                selectionAnchors.set(index, caretPositions.get(index));
            }
            int anchor = selectionAnchors.get(index);
            int start = Math.min(anchor, clamped);
            int end = Math.max(anchor, clamped);
            if (start == end) {
                clearSelectionForField(index);
            } else {
                selectionStarts.set(index, start);
                selectionEnds.set(index, end);
            }
        } else {
            selectionAnchors.set(index, -1);
            clearSelectionForField(index);
        }
        caretPositions.set(index, clamped);
        resetCaretBlink();
    }

    private void ensureCaretEntry(int index) {
        while (caretPositions.size() <= index) {
            caretPositions.add(0);
            selectionStarts.add(-1);
            selectionEnds.add(-1);
            selectionAnchors.add(-1);
            placeholderActive.add(false);
        }
    }

    private boolean isPlaceholderActive(int index) {
        return index >= 0 && index < placeholderActive.size() && placeholderActive.get(index);
    }

    private String getPlaceholderText(int index) {
        if (index < 0 || index >= node.getParameters().size()) {
            return "";
        }
        NodeParameter param = node.getParameters().get(index);
        return param != null ? param.getDefaultValue() : "";
    }

    private boolean clearPlaceholderIfActive(int index) {
        if (!isPlaceholderActive(index)) {
            return false;
        }
        ensureCaretEntry(index);
        placeholderActive.set(index, false);
        parameterValues.set(index, "");
        selectionStarts.set(index, -1);
        selectionEnds.set(index, -1);
        selectionAnchors.set(index, -1);
        caretPositions.set(index, 0);
        resetCaretBlink();
        return true;
    }

    private boolean hasSelectionForField(int index) {
        if (index < 0 || index >= selectionStarts.size()) {
            return false;
        }
        int start = selectionStarts.get(index);
        int end = selectionEnds.get(index);
        return start >= 0 && end >= 0 && start != end;
    }

    private void clearSelectionForField(int index) {
        if (index < 0 || index >= selectionStarts.size()) {
            return;
        }
        selectionStarts.set(index, -1);
        selectionEnds.set(index, -1);
        selectionAnchors.set(index, -1);
    }

    private boolean deleteSelectionForField(int index) {
        if (clearPlaceholderIfActive(index)) {
            return false;
        }
        if (!hasSelectionForField(index)) {
            return false;
        }
        String value = getFieldValue(index);
        int start = Math.min(selectionStarts.get(index), selectionEnds.get(index));
        int end = Math.max(selectionStarts.get(index), selectionEnds.get(index));
        if (start < 0 || end < 0 || start >= value.length() + 1) {
            clearSelectionForField(index);
            return false;
        }
        start = MathHelper.clamp(start, 0, value.length());
        end = MathHelper.clamp(end, 0, value.length());
        String updated = value.substring(0, start) + value.substring(end);
        setParameterValue(index, updated);
        caretPositions.set(index, start);
        clearSelectionForField(index);
        resetCaretBlink();
        return true;
    }

    private void deleteCharBeforeCaret(int index) {
        if (clearPlaceholderIfActive(index)) {
            return;
        }
        String value = getFieldValue(index);
        int caret = MathHelper.clamp(caretPositions.get(index), 0, value.length());
        if (caret == 0) {
            return;
        }
        String updated = value.substring(0, caret - 1) + value.substring(caret);
        setParameterValue(index, updated);
        caretPositions.set(index, caret - 1);
        resetCaretBlink();
    }

    private void deleteWordBeforeCaret(int index) {
        if (clearPlaceholderIfActive(index)) {
            return;
        }
        String value = getFieldValue(index);
        int caret = MathHelper.clamp(caretPositions.get(index), 0, value.length());
        if (caret == 0) {
            return;
        }
        int deleteToPos = findPreviousWordBoundary(value, caret);
        String updated = value.substring(0, deleteToPos) + value.substring(caret);
        setParameterValue(index, updated);
        caretPositions.set(index, deleteToPos);
        resetCaretBlink();
    }

    private int findPreviousWordBoundary(String text, int fromPosition) {
        if (text == null || fromPosition <= 0) {
            return 0;
        }
        int pos = fromPosition - 1;

        // Skip any whitespace immediately before the caret
        while (pos > 0 && Character.isWhitespace(text.charAt(pos))) {
            pos--;
        }

        // If we're on alphanumeric, skip back to start of word
        if (pos >= 0 && Character.isLetterOrDigit(text.charAt(pos))) {
            while (pos > 0 && Character.isLetterOrDigit(text.charAt(pos - 1))) {
                pos--;
            }
        }
        // If we're on non-alphanumeric (like punctuation), skip similar characters
        else if (pos >= 0) {
            while (pos > 0 && !Character.isLetterOrDigit(text.charAt(pos - 1))
                   && !Character.isWhitespace(text.charAt(pos - 1))) {
                pos--;
            }
        }

        return pos;
    }

    private void deleteCharAfterCaret(int index) {
        if (clearPlaceholderIfActive(index)) {
            return;
        }
        String value = getFieldValue(index);
        int caret = MathHelper.clamp(caretPositions.get(index), 0, value.length());
        if (caret >= value.length()) {
            return;
        }
        String updated = value.substring(0, caret) + value.substring(caret + 1);
        setParameterValue(index, updated);
        caretPositions.set(index, caret);
        resetCaretBlink();
    }

    private void selectAllForField(int index) {
        String value = getFieldValue(index);
        if (value.isEmpty()) {
            clearSelectionForField(index);
            caretPositions.set(index, 0);
            return;
        }
        selectionStarts.set(index, 0);
        selectionEnds.set(index, value.length());
        selectionAnchors.set(index, 0);
        caretPositions.set(index, value.length());
        resetCaretBlink();
    }

    private void copySelectionForField(int index) {
        if (!hasSelectionForField(index)) {
            return;
        }
        String value = getFieldValue(index);
        int start = Math.min(selectionStarts.get(index), selectionEnds.get(index));
        int end = Math.max(selectionStarts.get(index), selectionEnds.get(index));
        start = MathHelper.clamp(start, 0, value.length());
        end = MathHelper.clamp(end, 0, value.length());
        if (start >= end) {
            return;
        }
        setClipboardText(value.substring(start, end));
    }

    private void cutSelectionForField(int index) {
        if (!hasSelectionForField(index)) {
            return;
        }
        copySelectionForField(index);
        deleteSelectionForField(index);
    }

    private boolean insertTextForField(int index, String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        TextRenderer textRenderer = getClientTextRenderer();
        if (textRenderer == null) {
            return false;
        }
        ensureCaretEntry(index);
        clearPlaceholderIfActive(index);
        String value = getFieldValue(index);
        int caret = MathHelper.clamp(caretPositions.get(index), 0, value.length());
        if (hasSelectionForField(index)) {
            int start = Math.min(selectionStarts.get(index), selectionEnds.get(index));
            int end = Math.max(selectionStarts.get(index), selectionEnds.get(index));
            start = MathHelper.clamp(start, 0, value.length());
            end = MathHelper.clamp(end, 0, value.length());
            value = value.substring(0, start) + value.substring(end);
            caret = start;
            caretPositions.set(index, caret);
            clearSelectionForField(index);
        }
        boolean inserted = false;
        int availableWidth = getMaxFieldTextWidth();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 32 || c == 127) {
                continue;
            }
            String candidate = value.substring(0, caret) + c + value.substring(caret);
            if (textRenderer.getWidth(candidate) > availableWidth) {
                break;
            }
            value = candidate;
            caret++;
            inserted = true;
        }
        if (inserted) {
            setParameterValue(index, value);
            caretPositions.set(index, caret);
            resetCaretBlink();
        }
        return inserted;
    }

    private String getFieldValue(int index) {
        if (index < 0 || index >= parameterValues.size()) {
            return "";
        }
        if (isPlaceholderActive(index)) {
            return "";
        }
        String value = parameterValues.get(index);
        return value == null ? "" : value;
    }

    private void resetCaretBlink() {
        caretVisible = true;
        caretBlinkLastToggle = System.currentTimeMillis();
    }

    private void updateCaretBlinkState() {
        long now = System.currentTimeMillis();
        if (now - caretBlinkLastToggle >= 500L) {
            caretVisible = !caretVisible;
            caretBlinkLastToggle = now;
        }
    }

    private TextRenderer getClientTextRenderer() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.textRenderer : null;
    }

    private String getClipboardText() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.keyboard != null) {
            return client.keyboard.getClipboard();
        }
        return "";
    }

    private void setClipboardText(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.keyboard != null) {
            client.keyboard.setClipboard(text == null ? "" : text);
        }
    }

    private int getMaxFieldTextWidth() {
        return Math.max(10, popupWidth - 48);
    }

    private boolean handleFocusedFieldKeyPressed(int keyCode, int modifiers) {
        if (focusedFieldIndex < 0 || focusedFieldIndex >= parameterValues.size()) {
            return false;
        }
        int index = focusedFieldIndex;
        ensureCaretEntry(index);
        boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean controlHeld = isTextShortcutDown(modifiers);

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (clearPlaceholderIfActive(index)) {
                    return true;
                }
                if (!deleteSelectionForField(index)) {
                    if (controlHeld) {
                        deleteWordBeforeCaret(index);
                    } else {
                        deleteCharBeforeCaret(index);
                    }
                }
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (clearPlaceholderIfActive(index)) {
                    return true;
                }
                if (!deleteSelectionForField(index)) {
                    deleteCharAfterCaret(index);
                }
                return true;
            case GLFW.GLFW_KEY_LEFT:
                moveCaretForField(index, caretPositions.get(index) - 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                moveCaretForField(index, caretPositions.get(index) + 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_HOME:
                moveCaretForField(index, 0, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_END:
                moveCaretForField(index, getFieldValue(index).length(), shiftHeld);
                return true;
            case GLFW.GLFW_KEY_A:
                if (controlHeld) {
                    selectAllForField(index);
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_C:
                if (controlHeld) {
                    copySelectionForField(index);
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_X:
                if (controlHeld) {
                    cutSelectionForField(index);
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_V:
                if (controlHeld) {
                    String clipboardText = getClipboardText();
                    if (clipboardText != null) {
                        insertTextForField(index, clipboardText);
                    }
                    return true;
                }
                break;
        }
        return false;
    }

    private boolean isTextShortcutDown(int modifiers) {
        return InputCompatibilityBridge.hasControlDown()
            || (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
    }

    private String trimDisplayString(TextRenderer renderer, String text, int availableWidth) {
        if (text == null) {
            return "";
        }
        if (renderer.getWidth(text) <= availableWidth) {
            return text;
        }
        int ellipsisWidth = renderer.getWidth("...");
        int trimmedWidth = Math.max(0, availableWidth - ellipsisWidth);
        String trimmed = renderer.trimToWidth(text, trimmedWidth);
        return trimmed + "...";
    }

    private int adjustColorBrightness(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, Math.max(0, Math.round(((color >> 16) & 0xFF) * factor)));
        int g = Math.min(255, Math.max(0, Math.round(((color >> 8) & 0xFF) * factor)));
        int b = Math.min(255, Math.max(0, Math.round((color & 0xFF) * factor)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int getScrollAreaTop() {
        return popupY + CONTENT_START_OFFSET;
    }

    private int getScrollAreaBottom() {
        int top = getScrollAreaTop();
        int baseBottom = popupY + popupHeight - BOTTOM_PADDING;
        int buttonTop = saveButton != null ? saveButton.getY() : computeVisibleButtonY();
        int limitedBottom = buttonTop - 4;
        int bottom = Math.min(baseBottom, limitedBottom);
        if (bottom <= top) {
            bottom = Math.max(top + 1, baseBottom);
        }
        return bottom;
    }
}
