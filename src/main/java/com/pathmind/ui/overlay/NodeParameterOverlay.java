package com.pathmind.ui.overlay;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeMode;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.ParameterType;
import com.pathmind.ui.control.InventorySlotSelector;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.BlockSelection;
import com.pathmind.util.InventorySlotModeHelper;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
    private static final int DROPDOWN_OPTION_HEIGHT = 20;
    private static final int MIN_POPUP_WIDTH = 300;
    private static final int APPROX_CHAR_WIDTH = 6;
    private static final int POPUP_VERTICAL_MARGIN = 40;
    private static final int SCROLL_STEP = 18;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int KEY_SELECTOR_KEY_HEIGHT = 18;
    private static final int KEY_SELECTOR_ROW_GAP = 6;
    private static final int KEY_SELECTOR_KEY_GAP = 4;
    private static final int KEY_SELECTOR_PADDING = 6;
    private static final int SUGGESTION_MAX_OPTIONS = 8;
    private static final int SUGGESTION_ICON_SIZE = 16;
    private static final int SUGGESTION_ICON_TEXT_GAP = 6;

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
    private final Supplier<List<String>> functionNameSupplier;
    private final PopupAnimationHandler popupAnimation = new PopupAnimationHandler();
    private int focusedFieldIndex = -1;
    
    // Mode selection fields
    private NodeMode selectedMode;
    private final List<NodeMode> availableModes;
    private boolean modeDropdownOpen = false;
    private int modeDropdownHoverIndex = -1;
    
    // Function selection dropdown (Call Function node)
    private final List<String> functionNameOptions;
    private int functionDropdownParamIndex = -1;
    private boolean functionDropdownOpen = false;
    private int functionDropdownHoverIndex = -1;
    private int functionDropdownFieldX = 0;
    private int functionDropdownFieldY = 0;
    private int functionDropdownFieldWidth = 0;
    private int functionDropdownFieldHeight = 0;
    private boolean functionDropdownEnabled = false;
    private final boolean inventorySlotEditorActive;
    private final int inventorySlotParamIndex;
    private final int inventoryModeParamIndex;
    private final InventorySlotSelector inventorySlotSelector;
    private Boolean inventorySlotSelectionIsPlayer = null;
    private boolean suppressInventorySelectorCallbacks = false;
    private boolean blockStateEditorActive;
    private int blockParameterIndex;
    private int blockStateParamIndex;
    private final List<BlockSelection.StateOption> blockStateOptions = new ArrayList<>();
    private String cachedBlockIdForStateOptions = "";
    private boolean blockStateDropdownOpen = false;
    private int blockStateDropdownHoverIndex = -1;
    private int blockStateDropdownScrollOffset = 0;
    private int blockStateFieldX;
    private int blockStateFieldY;
    private int blockStateFieldWidth;
    private int blockStateFieldHeight;
    private int textLineHeight = 9;
    private final List<Integer> caretPositions = new ArrayList<>();
    private final List<Integer> selectionStarts = new ArrayList<>();
    private final List<Integer> selectionEnds = new ArrayList<>();
    private final List<Integer> selectionAnchors = new ArrayList<>();
    private long caretBlinkLastToggle = 0L;
    private boolean caretVisible = true;
    private boolean blockItemDropdownOpen = false;
    private int blockItemDropdownHoverIndex = -1;
    private int blockItemDropdownFieldIndex = -1;
    private int blockItemDropdownFieldX = 0;
    private int blockItemDropdownFieldY = 0;
    private int blockItemDropdownFieldWidth = 0;
    private int blockItemDropdownFieldHeight = 0;
    private int blockItemDropdownScrollOffset = 0;
    private String blockItemDropdownQuery = "";
    private final List<RegistryOption> blockItemDropdownOptions = new ArrayList<>();
    private int blockItemDropdownSuppressedField = -1;

    public NodeParameterOverlay(Node node, int screenWidth, int screenHeight, int topBarHeight, Runnable onClose,
                                Consumer<Node> onSave,
                                Supplier<List<String>> functionNameSupplier) {
        this.node = node;
        this.onClose = onClose;
        this.onSave = onSave;
        this.parameterValues = new ArrayList<>();
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.topBarHeight = topBarHeight;
        this.functionNameSupplier = functionNameSupplier;
        
        // Initialize mode selection
        this.availableModes = new ArrayList<>();
        this.functionNameOptions = new ArrayList<>();
        NodeMode[] modes = NodeMode.getModesForNodeType(node.getType());
        if (modes != null) {
            for (NodeMode mode : modes) {
                this.availableModes.add(mode);
            }
        }
        this.selectedMode = node.getMode();
        this.functionDropdownParamIndex = findFunctionDropdownIndex(node);

        int slotIndex = -1;
        int modeIndex = -1;
        if (node.getType() == NodeType.PARAM_INVENTORY_SLOT) {
            List<NodeParameter> params = node.getParameters();
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

        int tempBlockIndex = -1;
        int tempBlockStateIndex = -1;
        if (node.getType() == NodeType.PARAM_BLOCK) {
            List<NodeParameter> params = node.getParameters();
            for (int i = 0; i < params.size(); i++) {
                NodeParameter param = params.get(i);
                if (param == null) {
                    continue;
                }
                String name = param.getName();
                if ("Block".equalsIgnoreCase(name)) {
                    tempBlockIndex = i;
                } else if ("State".equalsIgnoreCase(name)) {
                    tempBlockStateIndex = i;
                }
            }
        }
        this.blockParameterIndex = tempBlockIndex;
        this.blockStateParamIndex = tempBlockStateIndex;
        this.blockStateEditorActive = tempBlockStateIndex >= 0;
        
        updatePopupDimensions();
    }

    public void init() {
        resetParameterFields();
        refreshFunctionNameOptions();
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

        updatePopupDimensions();
        recreateButtons();
        scrollOffset = Math.min(scrollOffset, maxScroll);
        updateButtonPositions();
    }

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        popupAnimation.tick();
        if (!popupAnimation.isVisible()) return;
        updateBlockStateOptions(false);
        if (focusedFieldIndex < 0 || !isBlockOrItemParameter(focusedFieldIndex) || isBlockItemDropdownSuppressed(focusedFieldIndex)) {
            blockItemDropdownOpen = false;
            blockItemDropdownHoverIndex = -1;
            blockItemDropdownFieldIndex = -1;
            blockItemDropdownOptions.clear();
        }

        // Render semi-transparent background overlay
        context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(),
            popupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND));

        // Get animated popup bounds
        int[] bounds = popupAnimation.getScaledPopupBounds(context.getScaledWindowWidth(), context.getScaledWindowHeight(), popupWidth, popupHeight);
        int scaledX = bounds[0];
        int scaledY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];

        // Render popup background
        context.fill(scaledX, scaledY, scaledX + scaledWidth, scaledY + scaledHeight, UITheme.BACKGROUND_SECONDARY);
        DrawContextBridge.drawBorder(context, scaledX, scaledY, scaledWidth, scaledHeight, UITheme.BORDER_HIGHLIGHT); // Grey outline

        // Render title
        context.drawTextWithShadow(
            textRenderer,
            Text.literal("Edit Parameters: " + node.getType().getDisplayName()),
            popupX + 20,
            popupY + 15,
            UITheme.TEXT_PRIMARY
        );

        updateButtonPositions();

        int contentTop = getScrollAreaTop();
        int contentBottom = getScrollAreaBottom();
        int contentRight = popupX + popupWidth;

        context.enableScissor(popupX + 1, contentTop, contentRight - 1, contentBottom);

        int sectionY = contentTop - scrollOffset;
        if (hasModeSelection()) {
            context.drawTextWithShadow(
                textRenderer,
                Text.literal("Mode:"),
                popupX + 20,
                sectionY + 4,
                UITheme.TEXT_PRIMARY
            );

            int modeButtonX = popupX + 20;
            int modeButtonY = sectionY + LABEL_TO_FIELD_OFFSET;
            int modeButtonWidth = popupWidth - 40;
            int modeButtonHeight = FIELD_HEIGHT;

            boolean modeButtonHovered = mouseX >= modeButtonX && mouseX <= modeButtonX + modeButtonWidth &&
                                      mouseY >= modeButtonY && mouseY <= modeButtonY + modeButtonHeight;

            int modeBgColor = modeButtonHovered ? adjustColorBrightness(UITheme.BACKGROUND_SIDEBAR, 1.1f) : UITheme.BACKGROUND_SIDEBAR;
            int modeBorderColor = modeButtonHovered ? UITheme.ACCENT_DEFAULT : UITheme.BORDER_HIGHLIGHT;

            context.fill(modeButtonX, modeButtonY, modeButtonX + modeButtonWidth, modeButtonY + modeButtonHeight, modeBgColor);
            DrawContextBridge.drawBorder(context, modeButtonX, modeButtonY, modeButtonWidth, modeButtonHeight, modeBorderColor);

            String modeText = selectedMode != null ? selectedMode.getDisplayName() : "Select Mode";
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(modeText),
                modeButtonX + 4,
                modeButtonY + 6,
                UITheme.TEXT_PRIMARY
            );

            context.drawTextWithShadow(
                textRenderer,
                Text.literal("▼"),
                modeButtonX + modeButtonWidth - 16,
                modeButtonY + 6,
                UITheme.TEXT_PRIMARY
            );

            sectionY = modeButtonY + modeButtonHeight + SECTION_SPACING;
        }

        this.textLineHeight = textRenderer.fontHeight;
        for (int i = 0; i < node.getParameters().size(); i++) {
            if (!shouldDisplayParameter(i)) {
                continue;
            }
            NodeParameter param = node.getParameters().get(i);
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(param.getName() + " (" + param.getType().getDisplayName() + "):"),
                popupX + 20,
                sectionY + 4,
                UITheme.TEXT_PRIMARY
            );

            int fieldX = popupX + 20;
            int fieldY = sectionY + LABEL_TO_FIELD_OFFSET;
            int fieldWidth = popupWidth - 40;
            int fieldHeight = FIELD_HEIGHT;
            if (inventorySlotEditorActive && i == inventorySlotParamIndex && inventorySlotSelector != null) {
                int selectorHeight = inventorySlotSelector.render(context, textRenderer, fieldX, fieldY, fieldWidth, mouseX, mouseY);
                sectionY = fieldY + selectorHeight + SECTION_SPACING;
                continue;
            }

            boolean isBlockStateField = blockStateEditorActive && i == blockStateParamIndex && !blockStateOptions.isEmpty();
            if (isBlockStateField) {
                renderBlockStateField(context, textRenderer, fieldX, fieldY, fieldWidth, fieldHeight, mouseX, mouseY);
                sectionY = fieldY + fieldHeight + SECTION_SPACING;
                continue;
            }
            if (usesKeySelectorForIndex(i)) {
                int selectorHeight = renderKeySelector(context, textRenderer, fieldX, fieldY, fieldWidth, mouseX, mouseY, i);
                sectionY = fieldY + selectorHeight + SECTION_SPACING;
                continue;
            }

            boolean isDropdownField = usesFunctionDropdownForIndex(i);
            if (isDropdownField) {
                functionDropdownFieldX = fieldX;
                functionDropdownFieldY = fieldY;
                functionDropdownFieldWidth = fieldWidth;
                functionDropdownFieldHeight = fieldHeight;
            }

            boolean dropdownActive = isDropdownField && functionDropdownOpen;
            boolean isFocused = !isDropdownField && i == focusedFieldIndex;
            boolean isBlockItemField = isBlockOrItemParameter(i);
            int bgColor;
            int borderColor;
            if (dropdownActive) {
                bgColor = adjustColorBrightness(UITheme.BACKGROUND_SIDEBAR, 1.2f);
                borderColor = UITheme.ACCENT_DEFAULT;
            } else if (isFocused) {
                bgColor = UITheme.BACKGROUND_SECONDARY;
                borderColor = UITheme.ACCENT_DEFAULT;
            } else {
                bgColor = UITheme.BACKGROUND_SIDEBAR;
                borderColor = UITheme.BORDER_HIGHLIGHT;
            }

            context.fill(fieldX, fieldY, fieldX + fieldWidth, fieldY + fieldHeight, bgColor);
            DrawContextBridge.drawBorder(context, fieldX, fieldY, fieldWidth, fieldHeight, borderColor);

            String text = parameterValues.get(i);
            if (isDropdownField) {
                String displayValue;
                int textColor;
                if (!functionDropdownEnabled) {
                    displayValue = "";
                    textColor = UITheme.BORDER_HIGHLIGHT;
                } else {
                    displayValue = (text == null || text.isEmpty()) ? "select function" : text;
                    textColor = (text == null || text.isEmpty()) ? UITheme.TEXT_SECONDARY : UITheme.TEXT_PRIMARY;
                }
                int availableWidth = fieldWidth - 24;
                String displayText = trimDisplayString(textRenderer, displayValue, availableWidth);
                context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(displayText),
                    fieldX + 4,
                    fieldY + 6,
                    textColor
                );
                context.drawTextWithShadow(
                    textRenderer,
                    Text.literal("▼"),
                    fieldX + fieldWidth - 14,
                    fieldY + 6,
                    functionDropdownEnabled ? UITheme.TEXT_PRIMARY : UITheme.TEXT_TERTIARY
                );
            } else {
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
                        context.fill(selectionStartX, fieldY + 4, selectionEndX, fieldY + fieldHeight - 4, 0x80426AD5);
                    }
                }

                if (!displayText.isEmpty()) {
                    if (isFocused && isBlockItemField && !showingPlaceholder) {
                        renderBlockItemSuggestionText(context, textRenderer, baseValue, i, textX, textY, fieldWidth - 8);
                    } else {
                        context.drawTextWithShadow(
                            textRenderer,
                            Text.literal(displayText),
                            textX,
                            textY,
                            textColor
                        );
                    }
                }

                if (isFocused) {
                    updateCaretBlinkState();
                    if (caretVisible) {
                        int caretIndex = showingPlaceholder ? 0 : MathHelper.clamp(caretPositions.get(i), 0, baseValue.length());
                        int caretX = textX + textRenderer.getWidth(baseValue.substring(0, caretIndex));
                        caretX = Math.min(caretX, fieldX + fieldWidth - 2);
                        context.fill(caretX, fieldY + 4, caretX + 1, fieldY + fieldHeight - 4, UITheme.TEXT_PRIMARY);
                    }
                }
            }

            if (isFocused && isBlockItemField && !isBlockItemDropdownSuppressed(i)) {
                if (fieldY + fieldHeight >= contentTop && fieldY <= contentBottom) {
                    List<RegistryOption> options = getBlockItemDropdownOptions(i);
                    if (!options.isEmpty()) {
                        SegmentInfo segmentInfo = getBlockItemSegmentInfo(i);
                        String query = segmentInfo == null ? "" : segmentInfo.trimmed().toLowerCase();
                        if (blockItemDropdownFieldIndex != i || !Objects.equals(blockItemDropdownQuery, query)) {
                            blockItemDropdownScrollOffset = 0;
                            blockItemDropdownQuery = query;
                        }
                        blockItemDropdownOpen = true;
                        blockItemDropdownFieldIndex = i;
                        blockItemDropdownFieldX = fieldX;
                        blockItemDropdownFieldY = fieldY;
                        blockItemDropdownFieldWidth = fieldWidth;
                        blockItemDropdownFieldHeight = fieldHeight;
                        blockItemDropdownOptions.clear();
                        blockItemDropdownOptions.addAll(options);
                    } else if (blockItemDropdownFieldIndex == i) {
                        blockItemDropdownOpen = false;
                        blockItemDropdownHoverIndex = -1;
                        blockItemDropdownOptions.clear();
                    }
                }
            }

            sectionY = fieldY + fieldHeight + SECTION_SPACING;
        }

        context.disableScissor();

        renderButton(context, textRenderer, saveButton, mouseX, mouseY);
        renderButton(context, textRenderer, cancelButton, mouseX, mouseY);

        if (hasModeSelection() && modeDropdownOpen) {
            int modeButtonX = popupX + 20;
            int modeButtonY = popupY + CONTENT_START_OFFSET + LABEL_TO_FIELD_OFFSET - scrollOffset;
            int modeButtonWidth = popupWidth - 40;
            int modeButtonHeight = FIELD_HEIGHT;

            int dropdownY = modeButtonY + modeButtonHeight;
            int dropdownHeight = availableModes.size() * DROPDOWN_OPTION_HEIGHT;

            modeDropdownHoverIndex = -1;
            if (mouseX >= modeButtonX && mouseX <= modeButtonX + modeButtonWidth &&
                mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight) {
                int hoverIndex = (mouseY - dropdownY) / DROPDOWN_OPTION_HEIGHT;
                if (hoverIndex >= 0 && hoverIndex < availableModes.size()) {
                    modeDropdownHoverIndex = hoverIndex;
                }
            }

            context.fill(modeButtonX, dropdownY, modeButtonX + modeButtonWidth, dropdownY + dropdownHeight, UITheme.BACKGROUND_SIDEBAR);
            DrawContextBridge.drawBorder(context, modeButtonX, dropdownY, modeButtonWidth, dropdownHeight, UITheme.BORDER_HIGHLIGHT);

            for (int i = 0; i < availableModes.size(); i++) {
                NodeMode mode = availableModes.get(i);
                int optionY = dropdownY + i * DROPDOWN_OPTION_HEIGHT;

                boolean isSelected = selectedMode == mode;
                boolean isHovered = i == modeDropdownHoverIndex;
                int optionColor = isSelected ? adjustColorBrightness(UITheme.BACKGROUND_SIDEBAR, 0.9f) : UITheme.BACKGROUND_SIDEBAR;
                if (isHovered) {
                    optionColor = adjustColorBrightness(optionColor, 1.2f);
                }
                context.fill(modeButtonX, optionY, modeButtonX + modeButtonWidth, optionY + DROPDOWN_OPTION_HEIGHT, optionColor);

                context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(mode.getDisplayName()),
                    modeButtonX + 4,
                    optionY + 6,
                    UITheme.TEXT_PRIMARY
                );
            }
        }

        if (functionDropdownOpen) {
            renderFunctionDropdown(context, textRenderer, mouseX, mouseY);
        }
        if (blockStateDropdownOpen) {
            renderBlockStateDropdown(context, textRenderer, mouseX, mouseY);
        }
        if (blockItemDropdownOpen) {
            renderBlockItemDropdown(context, textRenderer, mouseX, mouseY);
        }

        renderScrollbar(context, contentTop, contentBottom);
    }

    private void renderButton(DrawContext context, TextRenderer textRenderer, ButtonWidget button, int mouseX, int mouseY) {
        if (button == null) {
            return;
        }

        boolean hovered = mouseX >= button.getX() && mouseX <= button.getX() + button.getWidth() &&
                         mouseY >= button.getY() && mouseY <= button.getY() + button.getHeight();

        int bgColor = hovered ? UITheme.BUTTON_DEFAULT_HOVER : UITheme.BUTTON_DEFAULT_BG;
        context.fill(button.getX(), button.getY(), button.getX() + button.getWidth(), button.getY() + button.getHeight(), bgColor);
        float hoverProgress = HoverAnimator.getProgress(button, hovered);
        int borderColor = AnimationHelper.lerpColor(
            UITheme.BORDER_HIGHLIGHT,
            UITheme.BUTTON_HOVER_OUTLINE,
            hoverProgress
        );
        DrawContextBridge.drawBorder(context, button.getX(), button.getY(), button.getWidth(), button.getHeight(), borderColor);

        // Render button text
        context.drawCenteredTextWithShadow(
            textRenderer,
            button.getMessage(),
            button.getX() + button.getWidth() / 2,
            button.getY() + 6,
            UITheme.TEXT_PRIMARY
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

        context.fill(trackLeft, trackTop, trackRight, trackBottom, UITheme.BACKGROUND_SIDEBAR);
        DrawContextBridge.drawBorder(context, trackLeft, trackTop, SCROLLBAR_WIDTH, trackHeight, UITheme.BORDER_DEFAULT);

        int visibleScrollableHeight = Math.max(1, contentBottom - contentTop);
        int totalScrollableHeight = Math.max(visibleScrollableHeight, visibleScrollableHeight + maxScroll);
        int knobHeight = Math.max(20, (int) ((float) visibleScrollableHeight / totalScrollableHeight * trackHeight));
        int maxKnobTravel = Math.max(0, trackHeight - knobHeight);
        int knobOffset = maxKnobTravel <= 0 ? 0 : (int) ((float) scrollOffset / (float) maxScroll * maxKnobTravel);
        int knobTop = trackTop + knobOffset;

        context.fill(trackLeft + 1, knobTop, trackRight - 1, knobTop + knobHeight, UITheme.BORDER_DEFAULT);
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
        if (blockStateEditorActive && index == blockParameterIndex) {
            updateBlockStateOptions(true);
        }
    }

    private boolean shouldDisplayParameter(int index) {
        if (inventorySlotEditorActive && index == inventoryModeParamIndex) {
            return false;
        }
        if (blockStateEditorActive && index == blockStateParamIndex && blockStateOptions.isEmpty()) {
            return false;
        }
        return true;
    }

    private void updateBlockStateOptions(boolean forceRefresh) {
        boolean previouslyHadOptions = !blockStateOptions.isEmpty();
        if (!blockStateEditorActive || blockParameterIndex < 0 || blockParameterIndex >= parameterValues.size()) {
            blockStateOptions.clear();
            cachedBlockIdForStateOptions = "";
            blockStateDropdownOpen = false;
            blockStateDropdownScrollOffset = 0;
            return;
        }

        String rawBlock = parameterValues.get(blockParameterIndex);
        String normalized = rawBlock != null ? rawBlock.trim() : "";
        normalized = getFirstMultiValueEntry(normalized);
        if (!normalized.isEmpty()) {
            String stripped = BlockSelection.stripState(normalized);
            normalized = stripped != null ? stripped : normalized;
        }
        if (!forceRefresh && Objects.equals(normalized, cachedBlockIdForStateOptions)) {
            return;
        }

        cachedBlockIdForStateOptions = normalized;
        blockStateDropdownOpen = false;
        blockStateDropdownHoverIndex = -1;
        blockStateDropdownScrollOffset = 0;
        blockStateOptions.clear();
        if (!normalized.isEmpty()) {
            List<BlockSelection.StateOption> resolvedOptions = BlockSelection.getStateOptions(normalized);
            if (!resolvedOptions.isEmpty()) {
                blockStateOptions.add(new BlockSelection.StateOption("", "Any State"));
                blockStateOptions.addAll(resolvedOptions);
            }
        }
        ensureValidBlockStateSelection();
        boolean currentlyHasOptions = !blockStateOptions.isEmpty();
        if (previouslyHadOptions != currentlyHasOptions) {
            updatePopupDimensions();
            recreateButtons();
        }
    }

    private void ensureValidBlockStateSelection() {
        if (!blockStateEditorActive || blockStateParamIndex < 0 || blockStateParamIndex >= parameterValues.size()) {
            return;
        }
        String currentValue = parameterValues.get(blockStateParamIndex);
        boolean valid = blockStateOptions.stream().anyMatch(option -> option.value().equalsIgnoreCase(currentValue));
        if (!valid) {
            if (blockStateOptions.isEmpty()) {
                setParameterValue(blockStateParamIndex, "");
            } else {
                setParameterValue(blockStateParamIndex, blockStateOptions.get(0).value());
            }
        }
    }

    private void renderFunctionDropdown(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (!functionDropdownOpen || functionDropdownParamIndex < 0 || !functionDropdownEnabled) {
            return;
        }

        int dropdownX = functionDropdownFieldX;
        int dropdownY = functionDropdownFieldY + functionDropdownFieldHeight;
        int dropdownWidth = functionDropdownFieldWidth;
        List<String> options = functionNameOptions;
        int optionCount = Math.max(1, options.isEmpty() ? 1 : options.size());
        int dropdownHeight = optionCount * DROPDOWN_OPTION_HEIGHT;

        boolean hoverInside = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                              mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
        functionDropdownHoverIndex = -1;
        if (hoverInside && !options.isEmpty()) {
            int hoverIndex = (mouseY - dropdownY) / DROPDOWN_OPTION_HEIGHT;
            if (hoverIndex >= 0 && hoverIndex < options.size()) {
                functionDropdownHoverIndex = hoverIndex;
            }
        }

        context.fill(dropdownX, dropdownY, dropdownX + dropdownWidth, dropdownY + dropdownHeight, UITheme.BACKGROUND_SIDEBAR);
        DrawContextBridge.drawBorder(context, dropdownX, dropdownY, dropdownWidth, dropdownHeight, UITheme.BORDER_HIGHLIGHT);

        if (options.isEmpty()) {
            int textY = dropdownY + (DROPDOWN_OPTION_HEIGHT - textRenderer.fontHeight) / 2 + 1;
            context.drawTextWithShadow(
                textRenderer,
                Text.literal("No functions available"),
                dropdownX + 4,
                textY,
                UITheme.TEXT_SECONDARY
            );
            return;
        }

        String currentValue = parameterValues.size() > functionDropdownParamIndex
            ? parameterValues.get(functionDropdownParamIndex)
            : null;

        for (int i = 0; i < options.size(); i++) {
            int optionTop = dropdownY + i * DROPDOWN_OPTION_HEIGHT;
            boolean isHovered = i == functionDropdownHoverIndex;
            boolean isSelected = currentValue != null && currentValue.equals(options.get(i));

            int optionColor = isSelected ? adjustColorBrightness(UITheme.BACKGROUND_SIDEBAR, 0.9f) : UITheme.BACKGROUND_SIDEBAR;
            if (isHovered) {
                optionColor = adjustColorBrightness(optionColor, 1.2f);
            }

            context.fill(dropdownX, optionTop, dropdownX + dropdownWidth, optionTop + DROPDOWN_OPTION_HEIGHT, optionColor);
            String display = trimDisplayString(textRenderer, options.get(i), dropdownWidth - 8);
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(display),
                dropdownX + 4,
                optionTop + 6,
                UITheme.TEXT_PRIMARY
            );
        }
    }

    private void renderBlockStateField(DrawContext context, TextRenderer textRenderer, int fieldX, int fieldY, int fieldWidth, int fieldHeight, int mouseX, int mouseY) {
        blockStateFieldX = fieldX;
        blockStateFieldY = fieldY;
        blockStateFieldWidth = fieldWidth;
        blockStateFieldHeight = fieldHeight;

        boolean hasOptions = !blockStateOptions.isEmpty();
        boolean hovered = mouseX >= fieldX && mouseX <= fieldX + fieldWidth &&
                          mouseY >= fieldY && mouseY <= fieldY + fieldHeight;
        boolean dropdownActive = blockStateDropdownOpen;
        int bgColor;
        int borderColor;
        if (dropdownActive) {
            bgColor = adjustColorBrightness(UITheme.BACKGROUND_SIDEBAR, 1.2f);
            borderColor = UITheme.ACCENT_DEFAULT;
        } else if (hovered) {
            bgColor = adjustColorBrightness(UITheme.BACKGROUND_SIDEBAR, 1.1f);
            borderColor = UITheme.ACCENT_DEFAULT;
        } else {
            bgColor = UITheme.BACKGROUND_SIDEBAR;
            borderColor = UITheme.BORDER_HIGHLIGHT;
        }

        context.fill(fieldX, fieldY, fieldX + fieldWidth, fieldY + fieldHeight, bgColor);
        DrawContextBridge.drawBorder(context, fieldX, fieldY, fieldWidth, fieldHeight, borderColor);

        String display = getBlockStateDisplayText(hasOptions);
        int textColor = hasOptions ? UITheme.TEXT_PRIMARY : UITheme.BORDER_DEFAULT;
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(display),
            fieldX + 4,
            fieldY + 6,
            textColor
        );

        int arrowColor = hasOptions ? UITheme.TEXT_PRIMARY : UITheme.TEXT_TERTIARY;
        context.drawTextWithShadow(
            textRenderer,
            Text.literal("▼"),
            fieldX + fieldWidth - 14,
            fieldY + 6,
            arrowColor
        );
    }

    private String getBlockStateDisplayText(boolean hasOptions) {
        String currentValue = "";
        if (blockStateParamIndex >= 0 && blockStateParamIndex < parameterValues.size()) {
            currentValue = parameterValues.get(blockStateParamIndex);
        }
        for (BlockSelection.StateOption option : blockStateOptions) {
            if (option.value().equalsIgnoreCase(currentValue)) {
                return option.displayText();
            }
        }
        if (!hasOptions) {
            return "no states available";
        }
        return "select block state";
    }

    private void renderBlockStateDropdown(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (!blockStateDropdownOpen || !blockStateEditorActive || blockStateParamIndex < 0 || blockStateOptions.isEmpty()) {
            return;
        }

        int dropdownX = blockStateFieldX;
        int dropdownY = blockStateFieldY + blockStateFieldHeight;
        int dropdownWidth = blockStateFieldWidth;
        int optionCount = blockStateOptions.size();
        int visibleCount = Math.min(SUGGESTION_MAX_OPTIONS, optionCount);
        int dropdownHeight = visibleCount * DROPDOWN_OPTION_HEIGHT;
        int maxScroll = Math.max(0, optionCount - visibleCount);
        blockStateDropdownScrollOffset = MathHelper.clamp(blockStateDropdownScrollOffset, 0, maxScroll);

        context.fill(dropdownX, dropdownY, dropdownX + dropdownWidth, dropdownY + dropdownHeight, UITheme.BACKGROUND_SIDEBAR);
        DrawContextBridge.drawBorder(context, dropdownX, dropdownY, dropdownWidth, dropdownHeight, UITheme.BORDER_HIGHLIGHT);

        blockStateDropdownHoverIndex = -1;
        if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
            mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight) {
            int hoverIndex = blockStateDropdownScrollOffset + (int) ((mouseY - dropdownY) / DROPDOWN_OPTION_HEIGHT);
            if (hoverIndex >= 0 && hoverIndex < optionCount) {
                blockStateDropdownHoverIndex = hoverIndex;
            }
        }

        String currentValue = "";
        if (blockStateParamIndex >= 0 && blockStateParamIndex < parameterValues.size()) {
            currentValue = parameterValues.get(blockStateParamIndex);
        }

        int startIndex = blockStateDropdownScrollOffset;
        int endIndex = Math.min(optionCount, startIndex + visibleCount);
        for (int i = startIndex; i < endIndex; i++) {
            int optionTop = dropdownY + (i - startIndex) * DROPDOWN_OPTION_HEIGHT;
            BlockSelection.StateOption option = blockStateOptions.get(i);
            boolean isSelected = option.value().equalsIgnoreCase(currentValue);
            boolean isHovered = i == blockStateDropdownHoverIndex;
            int optionColor = isSelected ? adjustColorBrightness(UITheme.BACKGROUND_SIDEBAR, 0.9f) : UITheme.BACKGROUND_SIDEBAR;
            if (isHovered) {
                optionColor = adjustColorBrightness(optionColor, 1.2f);
            }
            context.fill(dropdownX, optionTop, dropdownX + dropdownWidth, optionTop + DROPDOWN_OPTION_HEIGHT, optionColor);
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(option.displayText()),
                dropdownX + 4,
                optionTop + 6,
                UITheme.TEXT_PRIMARY
            );
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!popupAnimation.isVisible()) return false;

        updateButtonPositions();

        if (inventorySlotEditorActive && inventorySlotSelector != null) {
            if (inventorySlotSelector.mouseClicked(mouseX, mouseY)) {
                functionDropdownOpen = false;
                functionDropdownHoverIndex = -1;
                modeDropdownOpen = false;
                modeDropdownHoverIndex = -1;
                blockStateDropdownOpen = false;
                blockStateDropdownHoverIndex = -1;
                blockStateDropdownScrollOffset = 0;
                return true;
            }
        }

        // Prepare scrollable bounds for subsequent hit checks
        int contentTop = getScrollAreaTop();
        int contentBottom = getScrollAreaBottom();
        int labelY = contentTop - scrollOffset;
        if (hasModeSelection()) {
            int modeButtonX = popupX + 20;
            int modeButtonY = labelY + LABEL_TO_FIELD_OFFSET;
            int modeButtonWidth = popupWidth - 40;
            int modeButtonHeight = FIELD_HEIGHT;

            boolean modeVisible = modeButtonY <= contentBottom && modeButtonY + modeButtonHeight >= contentTop;

            if (modeDropdownOpen) {
                int dropdownY = modeButtonY + modeButtonHeight;
                int dropdownHeight = availableModes.size() * DROPDOWN_OPTION_HEIGHT;

                if (mouseX >= modeButtonX && mouseX <= modeButtonX + modeButtonWidth &&
                    mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight) {
                    int optionIndex = (int) ((mouseY - dropdownY) / DROPDOWN_OPTION_HEIGHT);
                    if (optionIndex >= 0 && optionIndex < availableModes.size()) {
                        selectedMode = availableModes.get(optionIndex);
                        node.setMode(selectedMode);
                        resetParameterFields();
                        updatePopupDimensions();
                        recreateButtons();
                        updateButtonPositions();
                    }
                    modeDropdownOpen = false;
                    modeDropdownHoverIndex = -1;
                    return true;
                }
            }

            if (modeVisible && mouseX >= modeButtonX && mouseX <= modeButtonX + modeButtonWidth &&
                mouseY >= Math.max(modeButtonY, contentTop) && mouseY <= Math.min(modeButtonY + modeButtonHeight, contentBottom)) {
                functionDropdownOpen = false;
                functionDropdownHoverIndex = -1;
                blockStateDropdownOpen = false;
                blockStateDropdownHoverIndex = -1;
                blockStateDropdownScrollOffset = 0;
                modeDropdownOpen = !modeDropdownOpen;
                modeDropdownHoverIndex = -1;
                return true;
            }

            labelY = modeButtonY + modeButtonHeight + SECTION_SPACING;
        }

        if (blockStateDropdownOpen && blockStateEditorActive && blockStateParamIndex >= 0 && !blockStateOptions.isEmpty()) {
            int dropdownX = blockStateFieldX;
            int dropdownY = blockStateFieldY + blockStateFieldHeight;
            int dropdownWidth = blockStateFieldWidth;
            int visibleCount = Math.min(SUGGESTION_MAX_OPTIONS, blockStateOptions.size());
            int dropdownHeight = visibleCount * DROPDOWN_OPTION_HEIGHT;
            if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight) {
                int optionIndex = blockStateDropdownScrollOffset + (int) ((mouseY - dropdownY) / DROPDOWN_OPTION_HEIGHT);
                if (optionIndex >= 0 && optionIndex < blockStateOptions.size()) {
                    setParameterValue(blockStateParamIndex, blockStateOptions.get(optionIndex).value());
                }
                blockStateDropdownOpen = false;
                blockStateDropdownHoverIndex = -1;
                blockStateDropdownScrollOffset = 0;
                return true;
            }
        }

        if (blockItemDropdownOpen && blockItemDropdownFieldIndex >= 0 && !blockItemDropdownOptions.isEmpty()) {
            int dropdownX = blockItemDropdownFieldX;
            int dropdownY = blockItemDropdownFieldY + blockItemDropdownFieldHeight;
            int dropdownWidth = blockItemDropdownFieldWidth;
            int visibleCount = Math.min(SUGGESTION_MAX_OPTIONS, blockItemDropdownOptions.size());
            int dropdownHeight = visibleCount * DROPDOWN_OPTION_HEIGHT;
            int maxScroll = Math.max(0, blockItemDropdownOptions.size() - visibleCount);
            blockItemDropdownScrollOffset = MathHelper.clamp(blockItemDropdownScrollOffset, 0, maxScroll);
            if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight) {
                int optionIndex = blockItemDropdownScrollOffset + (int) ((mouseY - dropdownY) / DROPDOWN_OPTION_HEIGHT);
                if (optionIndex >= 0 && optionIndex < blockItemDropdownOptions.size()) {
                    applySuggestionForField(blockItemDropdownFieldIndex, blockItemDropdownOptions.get(optionIndex), true);
                }
                return true;
            }
        }

        // Check button clicks after handling dropdown interactions so dropdown selections aren't swallowed by buttons beneath
        if (saveButton != null && saveButton.isMouseOver(mouseX, mouseY)) {
            ButtonWidgetCompatibilityBridge.press(saveButton);
            return true;
        }
        if (cancelButton != null && cancelButton.isMouseOver(mouseX, mouseY)) {
            ButtonWidgetCompatibilityBridge.press(cancelButton);
            return true;
        }

        // Check field clicks
        boolean shiftClick = InputCompatibilityBridge.hasShiftDown();
        for (int i = 0; i < node.getParameters().size(); i++) {
            if (!shouldDisplayParameter(i)) {
                continue;
            }
            int fieldX = popupX + 20;
            int fieldY = labelY + LABEL_TO_FIELD_OFFSET; // Match the rendering position
            int fieldWidth = popupWidth - 40;
            int fieldHeight = FIELD_HEIGHT;
            boolean isDropdownField = usesFunctionDropdownForIndex(i);
            if (isDropdownField) {
                functionDropdownFieldX = fieldX;
                functionDropdownFieldY = fieldY;
                functionDropdownFieldWidth = fieldWidth;
                functionDropdownFieldHeight = fieldHeight;
            }

            if (usesKeySelectorForIndex(i)) {
                int selectorHeight = getKeySelectorHeight();
                if (mouseX >= fieldX && mouseX <= fieldX + fieldWidth &&
                    mouseY >= Math.max(fieldY, contentTop) && mouseY <= Math.min(fieldY + selectorHeight, contentBottom)) {
                    if (handleKeySelectorClick(mouseX, mouseY, fieldX, fieldY, fieldWidth, i)) {
                        return true;
                    }
                    return true;
                }
                labelY = fieldY + selectorHeight + SECTION_SPACING;
                continue;
            }

            if (mouseX >= fieldX && mouseX <= fieldX + fieldWidth &&
                mouseY >= Math.max(fieldY, contentTop) && mouseY <= Math.min(fieldY + fieldHeight, contentBottom)) {
                if (blockStateEditorActive && i == blockStateParamIndex) {
                    if (!blockStateOptions.isEmpty()) {
                        blockStateDropdownOpen = !blockStateDropdownOpen;
                        blockStateDropdownHoverIndex = -1;
                        if (blockStateDropdownOpen) {
                            blockStateDropdownScrollOffset = 0;
                        }
                        functionDropdownOpen = false;
                        functionDropdownHoverIndex = -1;
                        modeDropdownOpen = false;
                        modeDropdownHoverIndex = -1;
                    }
                    return true;
                }
                if (isDropdownField) {
                    if (functionDropdownEnabled) {
                        toggleFunctionDropdown();
                    }
                } else {
                    focusField(i);
                    setCaretFromClick(i, mouseX, fieldX, fieldWidth, shiftClick);
                }
                return true;
            }

            labelY = fieldY + fieldHeight + SECTION_SPACING;
        }

        if (handleFunctionDropdownClick(mouseX, mouseY)) {
            return true;
        }

        // Close dropdown if clicking outside of it
        if (hasModeSelection() && modeDropdownOpen) {
            int modeButtonX = popupX + 20;
            int modeButtonY = popupY + CONTENT_START_OFFSET + LABEL_TO_FIELD_OFFSET - scrollOffset;
            int modeButtonWidth = popupWidth - 40;
            int modeButtonHeight = FIELD_HEIGHT;
            int dropdownY = modeButtonY + modeButtonHeight;
            int dropdownHeight = availableModes.size() * DROPDOWN_OPTION_HEIGHT;

            // Check if click is outside dropdown area
            if (!(mouseX >= modeButtonX && mouseX <= modeButtonX + modeButtonWidth &&
                  mouseY >= modeButtonY && mouseY <= dropdownY + dropdownHeight)) {
                modeDropdownOpen = false; // Close dropdown
                modeDropdownHoverIndex = -1;
            }
        }
        
        if (functionDropdownOpen) {
            int dropdownX = functionDropdownFieldX;
            int dropdownY = functionDropdownFieldY + functionDropdownFieldHeight;
            int dropdownWidth = functionDropdownFieldWidth;
            int optionCount = Math.max(1, functionNameOptions.isEmpty() ? 1 : functionNameOptions.size());
            int dropdownHeight = optionCount * DROPDOWN_OPTION_HEIGHT;
            boolean insideField = mouseX >= functionDropdownFieldX && mouseX <= functionDropdownFieldX + functionDropdownFieldWidth &&
                                  mouseY >= functionDropdownFieldY && mouseY <= functionDropdownFieldY + functionDropdownFieldHeight;
            boolean insideDropdown = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                                     mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
            if (!insideField && !insideDropdown) {
                functionDropdownOpen = false;
                functionDropdownHoverIndex = -1;
            }
        }

        if (blockStateDropdownOpen) {
            int dropdownX = blockStateFieldX;
            int dropdownY = blockStateFieldY + blockStateFieldHeight;
            int dropdownWidth = blockStateFieldWidth;
            int visibleCount = Math.min(SUGGESTION_MAX_OPTIONS, blockStateOptions.size());
            int dropdownHeight = visibleCount * DROPDOWN_OPTION_HEIGHT;
            boolean insideField = mouseX >= blockStateFieldX && mouseX <= blockStateFieldX + blockStateFieldWidth &&
                                  mouseY >= blockStateFieldY && mouseY <= blockStateFieldY + blockStateFieldHeight;
            boolean insideDropdown = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                                     mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
            if (!insideField && !insideDropdown) {
                blockStateDropdownOpen = false;
                blockStateDropdownHoverIndex = -1;
                blockStateDropdownScrollOffset = 0;
            }
        }

        if (blockItemDropdownOpen) {
            int dropdownX = blockItemDropdownFieldX;
            int dropdownY = blockItemDropdownFieldY + blockItemDropdownFieldHeight;
            int dropdownWidth = blockItemDropdownFieldWidth;
            int visibleCount = Math.min(SUGGESTION_MAX_OPTIONS, blockItemDropdownOptions.size());
            int dropdownHeight = visibleCount * DROPDOWN_OPTION_HEIGHT;
            boolean insideField = mouseX >= blockItemDropdownFieldX && mouseX <= blockItemDropdownFieldX + blockItemDropdownFieldWidth &&
                                  mouseY >= blockItemDropdownFieldY && mouseY <= blockItemDropdownFieldY + blockItemDropdownFieldHeight;
            boolean insideDropdown = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                                     mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
            if (!insideField && !insideDropdown) {
                blockItemDropdownOpen = false;
                blockItemDropdownHoverIndex = -1;
                blockItemDropdownSuppressedField = blockItemDropdownFieldIndex;
            }
        }
        
        // Close if clicking outside the popup
        boolean insidePopupBounds = mouseX >= popupX && mouseX <= popupX + popupWidth &&
                                    mouseY >= popupY && mouseY <= popupY + popupHeight;
        if (!insidePopupBounds && !isPointInFunctionDropdownArea(mouseX, mouseY)) {
            close();
            return true;
        }
        
        // Always consume mouse events when popup is visible to prevent underlying UI interaction
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (!popupAnimation.isVisible()) return false;

        if (inventorySlotEditorActive && inventorySlotSelector != null) {
            if (inventorySlotSelector.mouseScrolled(mouseX, mouseY, verticalAmount)) {
                return true;
            }
        }

        if (blockStateDropdownOpen && !blockStateOptions.isEmpty()) {
            int dropdownX = blockStateFieldX;
            int dropdownY = blockStateFieldY + blockStateFieldHeight;
            int dropdownWidth = blockStateFieldWidth;
            int visibleCount = Math.min(SUGGESTION_MAX_OPTIONS, blockStateOptions.size());
            int dropdownHeight = visibleCount * DROPDOWN_OPTION_HEIGHT;
            int maxScroll = Math.max(0, blockStateOptions.size() - visibleCount);
            if (maxScroll > 0) {
                boolean insideField = mouseX >= blockStateFieldX && mouseX <= blockStateFieldX + blockStateFieldWidth &&
                                      mouseY >= blockStateFieldY && mouseY <= blockStateFieldY + blockStateFieldHeight;
                boolean insideDropdown = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                                         mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
                boolean insidePopup = mouseX >= popupX && mouseX <= popupX + popupWidth &&
                                      mouseY >= popupY && mouseY <= popupY + popupHeight;
                if (insideDropdown || insideField || insidePopup) {
                    int delta = (int) Math.signum(verticalAmount);
                    if (delta != 0) {
                        int nextOffset = MathHelper.clamp(blockStateDropdownScrollOffset - delta, 0, maxScroll);
                        if (nextOffset != blockStateDropdownScrollOffset) {
                            blockStateDropdownScrollOffset = nextOffset;
                        }
                    }
                    return true;
                }
            }
        }

        if (blockItemDropdownOpen && !blockItemDropdownOptions.isEmpty()) {
            int dropdownX = blockItemDropdownFieldX;
            int dropdownY = blockItemDropdownFieldY + blockItemDropdownFieldHeight;
            int dropdownWidth = blockItemDropdownFieldWidth;
            int visibleCount = Math.min(SUGGESTION_MAX_OPTIONS, blockItemDropdownOptions.size());
            int dropdownHeight = visibleCount * DROPDOWN_OPTION_HEIGHT;
            if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight) {
                int maxScroll = Math.max(0, blockItemDropdownOptions.size() - visibleCount);
                int delta = (int) Math.signum(verticalAmount);
                if (delta != 0) {
                    int nextOffset = MathHelper.clamp(blockItemDropdownScrollOffset - delta, 0, maxScroll);
                    if (nextOffset != blockItemDropdownScrollOffset) {
                        blockItemDropdownScrollOffset = nextOffset;
                    }
                }
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

        boolean handledFieldInput = false;
        if (focusedFieldIndex >= 0
            && focusedFieldIndex < parameterValues.size()
            && !usesFunctionDropdownForIndex(focusedFieldIndex)) {
            handledFieldInput = handleFocusedFieldKeyPressed(keyCode, modifiers);
            if (handledFieldInput) {
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_TAB) {
            boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            if (!shiftHeld && tryAcceptBlockItemSuggestion()) {
                return true;
            }
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
        
        if (focusedFieldIndex >= 0
            && focusedFieldIndex < parameterValues.size()
            && !usesFunctionDropdownForIndex(focusedFieldIndex)) {
            if (chr >= 32 && chr != 127) {
                return insertTextForField(focusedFieldIndex, String.valueOf(chr));
            }
        }
        
        return false;
    }

    private void saveParameters() {
        // Update node mode if applicable
        if (hasModeSelection() && selectedMode != null) {
            node.setMode(selectedMode);
        }
        
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
            if (empty && !isPlaceholderActive(i)) {
                emptyParameterNames.add(param.getName());
            }
        }
        if (!emptyParameterNames.isEmpty()) {
            warnEmptyParameters(emptyParameterNames);
        }
        for (int i = 0; i < parameters.size() && i < parameterValues.size(); i++) {
            NodeParameter param = parameters.get(i);
            String value = parameterValues.get(i);
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
        modeDropdownOpen = false;
        modeDropdownHoverIndex = -1;
        functionDropdownOpen = false;
        functionDropdownHoverIndex = -1;
        blockStateDropdownOpen = false;
        blockStateDropdownHoverIndex = -1;
        blockStateDropdownScrollOffset = 0;
        focusedFieldIndex = -1;
        if (inventorySlotSelector != null) {
            inventorySlotSelector.closeDropdown();
        }
        if (onClose != null) {
            onClose.run();
        }
    }

    public void show() {
        popupAnimation.show();
        focusedFieldIndex = -1;
        modeDropdownOpen = false;
        modeDropdownHoverIndex = -1;
        functionDropdownOpen = false;
        functionDropdownHoverIndex = -1;
        blockStateDropdownOpen = false;
        blockStateDropdownHoverIndex = -1;
        blockStateDropdownScrollOffset = 0;
        scrollOffset = 0;
        updateButtonPositions();
    }

    public boolean isVisible() {
        return popupAnimation.isVisible();
    }

    public PopupAnimationHandler getPopupAnimation() {
        return popupAnimation;
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
        refreshBlockStateIndices();
        updateBlockStateOptions(true);
    }

    private boolean shouldUsePlaceholder(NodeParameter parameter, String value) {
        if (parameter == null || parameter.getType() != ParameterType.STRING) {
            return false;
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

    private void refreshBlockStateIndices() {
        int tempBlockIndex = -1;
        int tempBlockStateIndex = -1;
        if (node.getType() == NodeType.PARAM_BLOCK) {
            List<NodeParameter> params = node.getParameters();
            for (int i = 0; i < params.size(); i++) {
                NodeParameter param = params.get(i);
                if (param == null) {
                    continue;
                }
                String name = param.getName();
                if ("Block".equalsIgnoreCase(name)) {
                    tempBlockIndex = i;
                } else if ("State".equalsIgnoreCase(name)) {
                    tempBlockStateIndex = i;
                }
            }
        }
        blockParameterIndex = tempBlockIndex;
        blockStateParamIndex = tempBlockStateIndex;
        blockStateEditorActive = tempBlockStateIndex >= 0;
        if (!blockStateEditorActive) {
            blockStateDropdownOpen = false;
            blockStateDropdownHoverIndex = -1;
            blockStateDropdownScrollOffset = 0;
            blockStateOptions.clear();
            cachedBlockIdForStateOptions = "";
        }
    }
    
    private void updatePopupDimensions() {
        int longestLineLength = ("Edit Parameters: " + node.getType().getDisplayName()).length();

        if (hasModeSelection()) {
            longestLineLength = Math.max(longestLineLength, "Mode:".length());
            String modeText = selectedMode != null ? selectedMode.getDisplayName() : "Select Mode";
            longestLineLength = Math.max(longestLineLength, modeText.length());
        }

        for (int i = 0; i < node.getParameters().size(); i++) {
            if (!shouldDisplayParameter(i)) {
                continue;
            }
            NodeParameter param = node.getParameters().get(i);
            String label = param.getName() + " (" + param.getType().getDisplayName() + "):";
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
        if (hasModeSelection()) {
            contentHeight += LABEL_TO_FIELD_OFFSET + FIELD_HEIGHT;
            if (!node.getParameters().isEmpty()) {
                contentHeight += SECTION_SPACING;
            }
        }

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
    
    private boolean hasModeSelection() {
        return !availableModes.isEmpty();
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

    private int findFunctionDropdownIndex(Node node) {
        if (node == null || node.getType() != NodeType.EVENT_CALL) {
            return -1;
        }
        List<NodeParameter> params = node.getParameters();
        for (int i = 0; i < params.size(); i++) {
            NodeParameter param = params.get(i);
            if (param != null && "Name".equals(param.getName())) {
                return i;
            }
        }
        return -1;
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
            if (!usesFunctionDropdownForIndex(startIndex) && !usesKeySelectorForIndex(startIndex)) {
                focusField(startIndex);
                return;
            }
        }
        focusedFieldIndex = -1;
    }

    private boolean usesFunctionDropdownForIndex(int index) {
        return functionDropdownParamIndex >= 0 && index == functionDropdownParamIndex;
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
                                  int mouseX, int mouseY, int paramIndex) {
        int totalHeight = getKeySelectorHeight();
        int backgroundColor = UITheme.BACKGROUND_SIDEBAR;
        int borderColor = UITheme.BORDER_HIGHLIGHT;
        context.fill(fieldX, fieldY, fieldX + fieldWidth, fieldY + totalHeight, backgroundColor);
        DrawContextBridge.drawBorder(context, fieldX, fieldY, fieldWidth, totalHeight, borderColor);

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
                context.fill(keyX, rowTop, keyX + keyWidth, rowTop + keyHeight, keyBase);
                DrawContextBridge.drawBorder(context, keyX, rowTop, keyWidth, keyHeight, keyBorder);

                String label = key.label;
                int textWidth = textRenderer.getWidth(label);
                int textX = keyX + Math.max(2, (keyWidth - textWidth) / 2);
                int textY = rowTop + (keyHeight - textRenderer.fontHeight) / 2 + 1;
                context.drawTextWithShadow(textRenderer, Text.literal(label), textX, textY, UITheme.TEXT_PRIMARY);

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

    private void toggleFunctionDropdown() {
        if (functionDropdownParamIndex < 0 || !functionDropdownEnabled) {
            return;
        }
        if (functionDropdownOpen) {
            functionDropdownOpen = false;
            functionDropdownHoverIndex = -1;
            return;
        }
        modeDropdownOpen = false;
        modeDropdownHoverIndex = -1;
        refreshFunctionNameOptions();
        functionDropdownOpen = true;
        functionDropdownHoverIndex = -1;
        focusedFieldIndex = -1;
    }

    private void focusField(int index) {
        if (index < 0 || index >= parameterValues.size() || usesFunctionDropdownForIndex(index)) {
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
        clearBlockItemDropdownSuppression(index);
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
        clearBlockItemDropdownSuppression(index);
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
        clearBlockItemDropdownSuppression(index);
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
            clearBlockItemDropdownSuppression(index);
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
        boolean controlHeld = InputCompatibilityBridge.hasControlDown();

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (clearPlaceholderIfActive(index)) {
                    return true;
                }
                if (!deleteSelectionForField(index)) {
                    deleteCharBeforeCaret(index);
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

    private boolean handleFunctionDropdownClick(double mouseX, double mouseY) {
        if (!functionDropdownOpen || functionDropdownParamIndex < 0 || !functionDropdownEnabled) {
            return false;
        }
        int dropdownX = functionDropdownFieldX;
        int dropdownY = functionDropdownFieldY + functionDropdownFieldHeight;
        int dropdownWidth = functionDropdownFieldWidth;
        int optionCount = Math.max(1, functionNameOptions.isEmpty() ? 1 : functionNameOptions.size());
        int dropdownHeight = optionCount * DROPDOWN_OPTION_HEIGHT;

        if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
            mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight) {
            if (!functionNameOptions.isEmpty()) {
                int optionIndex = (int) ((mouseY - dropdownY) / DROPDOWN_OPTION_HEIGHT);
                    if (optionIndex >= 0 && optionIndex < functionNameOptions.size()) {
                        setParameterValue(functionDropdownParamIndex, functionNameOptions.get(optionIndex));
                    }
            }
            functionDropdownOpen = false;
            functionDropdownHoverIndex = -1;
            return true;
        }
        return false;
    }

    private void refreshFunctionNameOptions() {
        functionNameOptions.clear();
        if (functionNameSupplier != null) {
            List<String> supplied = functionNameSupplier.get();
            if (supplied != null) {
                for (String name : supplied) {
                    if (name == null) {
                        continue;
                    }
                    String trimmed = name.trim();
                    if (!trimmed.isEmpty() && !functionNameOptions.contains(trimmed)) {
                        functionNameOptions.add(trimmed);
                    }
                }
            }
        }
        functionDropdownEnabled = !functionNameOptions.isEmpty();
        if (!functionDropdownEnabled) {
            functionDropdownOpen = false;
            functionDropdownHoverIndex = -1;
        }
    }

    private boolean isPointInFunctionDropdownArea(double mouseX, double mouseY) {
        if (functionDropdownParamIndex < 0 || !functionDropdownEnabled) {
            return false;
        }
        boolean insideField = mouseX >= functionDropdownFieldX &&
                              mouseX <= functionDropdownFieldX + functionDropdownFieldWidth &&
                              mouseY >= functionDropdownFieldY &&
                              mouseY <= functionDropdownFieldY + functionDropdownFieldHeight;
        if (insideField) {
            return true;
        }
        if (!functionDropdownOpen) {
            return false;
        }
        int dropdownX = functionDropdownFieldX;
        int dropdownY = functionDropdownFieldY + functionDropdownFieldHeight;
        int dropdownWidth = functionDropdownFieldWidth;
        int optionCount = Math.max(1, functionNameOptions.isEmpty() ? 1 : functionNameOptions.size());
        int dropdownHeight = optionCount * DROPDOWN_OPTION_HEIGHT;
        return mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
               mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
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

    private void renderBlockItemDropdown(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (!blockItemDropdownOpen || blockItemDropdownFieldIndex < 0 || blockItemDropdownOptions.isEmpty()) {
            return;
        }
        int dropdownX = blockItemDropdownFieldX;
        int dropdownY = blockItemDropdownFieldY + blockItemDropdownFieldHeight;
        int dropdownWidth = blockItemDropdownFieldWidth;
        int visibleCount = Math.min(SUGGESTION_MAX_OPTIONS, blockItemDropdownOptions.size());
        int dropdownHeight = visibleCount * DROPDOWN_OPTION_HEIGHT;
        int maxScroll = Math.max(0, blockItemDropdownOptions.size() - visibleCount);
        blockItemDropdownScrollOffset = MathHelper.clamp(blockItemDropdownScrollOffset, 0, maxScroll);

        context.fill(dropdownX, dropdownY, dropdownX + dropdownWidth, dropdownY + dropdownHeight, UITheme.BACKGROUND_SIDEBAR);
        DrawContextBridge.drawBorder(context, dropdownX, dropdownY, dropdownWidth, dropdownHeight, UITheme.BORDER_HIGHLIGHT);

        blockItemDropdownHoverIndex = -1;
        if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
            mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight) {
            int hoverIndex = blockItemDropdownScrollOffset + (int) ((mouseY - dropdownY) / DROPDOWN_OPTION_HEIGHT);
            if (hoverIndex >= 0 && hoverIndex < blockItemDropdownOptions.size()) {
                blockItemDropdownHoverIndex = hoverIndex;
            }
        }

        boolean renderEntityIcons = isEntityParameter(blockItemDropdownFieldIndex);
        int startIndex = blockItemDropdownScrollOffset;
        int endIndex = Math.min(blockItemDropdownOptions.size(), startIndex + visibleCount);
        for (int i = startIndex; i < endIndex; i++) {
            int optionTop = dropdownY + (i - startIndex) * DROPDOWN_OPTION_HEIGHT;
            boolean isHovered = i == blockItemDropdownHoverIndex;
            int optionColor = UITheme.BACKGROUND_SIDEBAR;
            if (isHovered) {
                optionColor = adjustColorBrightness(optionColor, 1.2f);
            }
            context.fill(dropdownX, optionTop, dropdownX + dropdownWidth, optionTop + DROPDOWN_OPTION_HEIGHT, optionColor);

            RegistryOption option = blockItemDropdownOptions.get(i);
            ItemStack stack = option.stack();
            int iconX = dropdownX + 4;
            int iconY = optionTop + (DROPDOWN_OPTION_HEIGHT - SUGGESTION_ICON_SIZE) / 2;
            boolean iconRendered = false;
            if (renderEntityIcons) {
                iconRendered = renderEntityOptionIcon(context, option, iconX, iconY, SUGGESTION_ICON_SIZE);
            }
            if (!iconRendered && !stack.isEmpty()) {
                context.drawItem(stack, iconX, iconY);
            }

            int textX = iconX + SUGGESTION_ICON_SIZE + SUGGESTION_ICON_TEXT_GAP;
            int availableTextWidth = dropdownWidth - (textX - dropdownX) - 6;
            String display = trimDisplayString(textRenderer, option.value(), availableTextWidth);
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(display),
                textX,
                optionTop + 6,
                UITheme.TEXT_PRIMARY
            );
        }
    }

    private boolean renderEntityOptionIcon(DrawContext context, RegistryOption option, int x, int y, int size) {
        if (context == null || option == null) {
            return false;
        }
        Identifier entityId = Identifier.tryParse(option.fullId());
        if (entityId == null) {
            return false;
        }
        LivingEntity entity = createEntityForIcon(entityId);
        if (entity == null) {
            return false;
        }
        int x2 = x + size;
        int y2 = y + size;
        float mouseX = (x + x2) / 2.0f;
        float mouseY = (y + y2) / 2.0f;
        InventoryScreen.drawEntity(context, x, y, x2, y2, size, mouseX, mouseY, 0.0f, entity);
        return true;
    }

    private static LivingEntity createEntityForIcon(Identifier entityId) {
        if (entityId == null) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return null;
        }
        EntityType<?> entityType = Registries.ENTITY_TYPE.get(entityId);
        if (entityType == null) {
            return null;
        }
        // Pick a spawn reason that exists in the current runtime; fall back to the first available value.
        SpawnReason reason = null;
        for (String candidate : new String[]{"SPAWN_EGG", "SPAWN_ITEM", "DISPENSER", "COMMAND", "NATURAL"}) {
            try {
                reason = SpawnReason.valueOf(candidate);
                break;
            } catch (IllegalArgumentException ignored) {
                // Candidate not present in this version; try the next one.
            }
        }
        if (reason == null) {
            SpawnReason[] reasons = SpawnReason.values();
            if (reasons.length > 0) {
                reason = reasons[0];
            }
        }
        if (reason == null) {
            return null;
        }
        try {
            Entity entity = null;
            try {
                Method createWithReason = EntityType.class.getMethod("create", World.class, SpawnReason.class);
                entity = (Entity) createWithReason.invoke(entityType, client.world, reason);
            } catch (NoSuchMethodException ignored) {
                Method createNoReason = EntityType.class.getMethod("create", World.class);
                entity = (Entity) createNoReason.invoke(entityType, client.world);
            }
            if (entity instanceof LivingEntity living) {
                return living;
            }
        } catch (Exception ignored) {
            // If creation fails for any reason, let caller fall back to icon stack.
        }
        return null;
    }

    private void renderBlockItemSuggestionText(DrawContext context, TextRenderer textRenderer, String baseValue, int index,
                                               int textX, int textY, int availableWidth) {
        String value = baseValue == null ? "" : baseValue;
        String suggestion = getBlockItemAutocomplete(index);
        if (suggestion == null || suggestion.isEmpty()) {
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(value),
                textX,
                textY,
                UITheme.TEXT_PRIMARY
            );
            return;
        }

        SegmentInfo segmentInfo = getBlockItemSegmentInfo(index);
        if (segmentInfo == null) {
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(value),
                textX,
                textY,
                UITheme.TEXT_PRIMARY
            );
            return;
        }
        String fullSuggestion = segmentInfo.prefix() + segmentInfo.leadingWhitespace() + suggestion
            + segmentInfo.trailingWhitespace() + segmentInfo.suffix();
        String lowerValue = value.toLowerCase();
        String lowerSuggestion = fullSuggestion.toLowerCase();
        if (value.isEmpty() || !lowerSuggestion.startsWith(lowerValue)) {
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(value),
                textX,
                textY,
                UITheme.TEXT_PRIMARY
            );
            return;
        }

        int valueWidth = textRenderer.getWidth(value);
        if (valueWidth >= availableWidth) {
            String trimmed = trimDisplayString(textRenderer, value, availableWidth);
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(trimmed),
                textX,
                textY,
                UITheme.TEXT_PRIMARY
            );
            return;
        }

        context.drawTextWithShadow(
            textRenderer,
            Text.literal(value),
            textX,
            textY,
            UITheme.TEXT_PRIMARY
        );

        String remainder = fullSuggestion.substring(value.length());
        if (!remainder.isEmpty()) {
            int remainingWidth = Math.max(0, availableWidth - valueWidth);
            String trimmedRemainder = textRenderer.trimToWidth(remainder, remainingWidth);
            if (!trimmedRemainder.isEmpty()) {
                context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(trimmedRemainder),
                    textX + valueWidth,
                    textY,
                    UITheme.BORDER_DEFAULT
                );
            }
        }
    }

    private boolean tryAcceptBlockItemSuggestion() {
        if (focusedFieldIndex < 0 || !isBlockOrItemParameter(focusedFieldIndex)) {
            return false;
        }
        String suggestion = getBlockItemAutocomplete(focusedFieldIndex);
        if (suggestion == null || suggestion.isEmpty()) {
            return false;
        }
        applySuggestionForField(focusedFieldIndex, suggestion, false);
        return true;
    }

    private void applySuggestionForField(int index, RegistryOption option, boolean suppressDropdown) {
        if (option == null) {
            return;
        }
        applySuggestionForField(index, option.value(), suppressDropdown);
    }

    private void applySuggestionForField(int index, String value, boolean suppressDropdown) {
        if (index < 0 || index >= parameterValues.size()) {
            return;
        }
        if (isBlockOrItemParameter(index)) {
            SegmentInfo segmentInfo = getBlockItemSegmentInfo(index);
            if (segmentInfo != null) {
                String updated = segmentInfo.prefix() + segmentInfo.leadingWhitespace() + value
                    + segmentInfo.trailingWhitespace() + segmentInfo.suffix();
                setParameterValue(index, updated);
            } else {
                setParameterValue(index, value);
            }
        } else {
            setParameterValue(index, value);
        }
        focusField(index);
        if (suppressDropdown) {
            blockItemDropdownSuppressedField = index;
        }
    }

    private boolean isBlockOrItemParameter(int index) {
        return isBlockParameter(index) || isItemParameter(index) || isEntityParameter(index);
    }

    private boolean isBlockItemDropdownSuppressed(int index) {
        return blockItemDropdownSuppressedField == index;
    }

    private void clearBlockItemDropdownSuppression(int index) {
        if (isBlockOrItemParameter(index)) {
            blockItemDropdownSuppressedField = -1;
        }
    }

    private boolean isBlockParameter(int index) {
        if (index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        if (param.getType() == ParameterType.BLOCK_TYPE) {
            return true;
        }
        String name = param.getName();
        return "Block".equalsIgnoreCase(name) || "Blocks".equalsIgnoreCase(name);
    }

    private boolean isItemParameter(int index) {
        if (index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        return "Item".equalsIgnoreCase(param.getName());
    }

    private boolean isEntityParameter(int index) {
        if (index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        return "Entity".equalsIgnoreCase(param.getName());
    }

    private SegmentInfo getBlockItemSegmentInfo(int index) {
        if (index < 0 || index >= parameterValues.size()) {
            return null;
        }
        ensureCaretEntry(index);
        String value = getFieldValue(index);
        int caret = MathHelper.clamp(caretPositions.get(index), 0, value.length());
        int start = findSegmentStart(value, caret);
        int end = findSegmentEnd(value, caret);
        String segment = value.substring(start, end);
        int leadingEnd = 0;
        while (leadingEnd < segment.length() && Character.isWhitespace(segment.charAt(leadingEnd))) {
            leadingEnd++;
        }
        int trailingStart = segment.length();
        while (trailingStart > leadingEnd && Character.isWhitespace(segment.charAt(trailingStart - 1))) {
            trailingStart--;
        }
        String leading = segment.substring(0, leadingEnd);
        String trimmed = segment.substring(leadingEnd, trailingStart);
        String trailing = segment.substring(trailingStart);
        return new SegmentInfo(value.substring(0, start), value.substring(end), leading, trimmed, trailing);
    }

    private int findSegmentStart(String value, int caret) {
        int depth = 0;
        for (int i = caret - 1; i >= 0; i--) {
            char c = value.charAt(i);
            if (c == ']') {
                depth++;
            } else if (c == '[') {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && (c == ',' || c == ';')) {
                return i + 1;
            }
        }
        return 0;
    }

    private int findSegmentEnd(String value, int caret) {
        int depth = 0;
        for (int i = caret; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && (c == ',' || c == ';')) {
                return i;
            }
        }
        return value.length();
    }

    private String getFirstMultiValueEntry(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && (c == ',' || c == ';')) {
                return value.substring(0, i).trim();
            }
        }
        return value.trim();
    }

    private record SegmentInfo(String prefix, String suffix, String leadingWhitespace, String trimmed, String trailingWhitespace) {
    }

    private String getBlockItemAutocomplete(int index) {
        SegmentInfo segmentInfo = getBlockItemSegmentInfo(index);
        if (segmentInfo == null || segmentInfo.trimmed().isEmpty()) {
            return "";
        }
        List<RegistryOption> options = getBlockItemSuggestions(index, SUGGESTION_MAX_OPTIONS);
        String lowerCurrent = segmentInfo.trimmed().toLowerCase();
        for (RegistryOption option : options) {
            String value = option.value();
            if (value.toLowerCase().startsWith(lowerCurrent)) {
                return value;
            }
        }
        return "";
    }

    private List<RegistryOption> getBlockItemSuggestions(int index, int maxOptions) {
        List<RegistryOption> matches = getBlockItemDropdownOptions(index);
        if (matches.isEmpty()) {
            return List.of();
        }
        if (matches.size() <= maxOptions) {
            return matches;
        }
        return new ArrayList<>(matches.subList(0, maxOptions));
    }

    private List<RegistryOption> getBlockItemDropdownOptions(int index) {
        if (index < 0 || index >= parameterValues.size()) {
            return List.of();
        }
        List<RegistryOption> source = getRegistryOptions(index);
        if (source.isEmpty()) {
            return List.of();
        }
        SegmentInfo segmentInfo = getBlockItemSegmentInfo(index);
        String query = segmentInfo == null ? "" : segmentInfo.trimmed().toLowerCase();
        if (query.isEmpty()) {
            return new ArrayList<>(source);
        }

        List<MatchOption> matches = new ArrayList<>();
        for (RegistryOption option : source) {
            int score = Math.max(scoreMatch(query, option.value().toLowerCase()), scoreMatch(query, option.fullId().toLowerCase()));
            if (score >= 0) {
                matches.add(new MatchOption(option, score));
            }
        }
        matches.sort((a, b) -> {
            int scoreCompare = Integer.compare(b.score(), a.score());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return a.option().value().compareToIgnoreCase(b.option().value());
        });

        List<RegistryOption> results = new ArrayList<>(matches.size());
        for (int i = 0; i < matches.size(); i++) {
            results.add(matches.get(i).option());
        }
        return results;
    }

    private static int scoreMatch(String query, String candidate) {
        if (query.isEmpty()) {
            return 0;
        }
        if (candidate.equals(query)) {
            return 10000;
        }
        if (candidate.startsWith(query)) {
            return 9000 - Math.max(0, candidate.length() - query.length());
        }
        int index = candidate.indexOf(query);
        if (index >= 0) {
            return 7000 - index;
        }
        return -1;
    }

    private static boolean isMinecraftNamespace(Identifier id) {
        return id != null && "minecraft".equals(id.getNamespace());
    }

    private static List<RegistryOption> getBlockOptions() {
        return RegistryOptionCache.BLOCK_OPTIONS;
    }

    private static List<RegistryOption> getItemOptions() {
        return RegistryOptionCache.ITEM_OPTIONS;
    }

    private static List<RegistryOption> getEntityOptions() {
        return RegistryOptionCache.ENTITY_OPTIONS;
    }

    private List<RegistryOption> getRegistryOptions(int index) {
        if (isBlockParameter(index)) {
            return getBlockOptions();
        }
        if (isItemParameter(index)) {
            return getItemOptions();
        }
        if (isEntityParameter(index)) {
            return getEntityOptions();
        }
        return List.of();
    }

    private record RegistryOption(String value, String fullId, ItemStack stack) {
    }

    private record MatchOption(RegistryOption option, int score) {
    }

    private static final class RegistryOptionCache {
        private static final List<RegistryOption> BLOCK_OPTIONS = buildBlockOptions();
        private static final List<RegistryOption> ITEM_OPTIONS = buildItemOptions();
        private static final List<RegistryOption> ENTITY_OPTIONS = buildEntityOptions();

        private static List<RegistryOption> buildBlockOptions() {
            List<RegistryOption> options = new ArrayList<>();
            for (Identifier id : Registries.BLOCK.getIds()) {
                String fullId = id.toString();
                String value = isMinecraftNamespace(id) ? id.getPath() : fullId;
                Block block = Registries.BLOCK.get(id);
                Item item = block != null ? block.asItem() : Items.AIR;
                ItemStack stack = item != null && item != Items.AIR ? new ItemStack(item) : ItemStack.EMPTY;
                options.add(new RegistryOption(value, fullId, stack));
            }
            options.sort((a, b) -> a.value().compareToIgnoreCase(b.value()));
            return options;
        }

        private static List<RegistryOption> buildItemOptions() {
            List<RegistryOption> options = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds()) {
                Item item = Registries.ITEM.get(id);
                if (item == null || item == Items.AIR) {
                    continue;
                }
                String fullId = id.toString();
                String value = isMinecraftNamespace(id) ? id.getPath() : fullId;
                options.add(new RegistryOption(value, fullId, new ItemStack(item)));
            }
            options.sort((a, b) -> a.value().compareToIgnoreCase(b.value()));
            return options;
        }

        private static List<RegistryOption> buildEntityOptions() {
            List<RegistryOption> options = new ArrayList<>();
            for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
                EntityType<?> entityType = Registries.ENTITY_TYPE.get(id);
                String fullId = id.toString();
                String value = isMinecraftNamespace(id) ? id.getPath() : fullId;
                ItemStack iconStack = ItemStack.EMPTY;
                if (entityType != null) {
                    Item spawnEgg = SpawnEggItem.forEntity(entityType);
                    if (spawnEgg != null) {
                        iconStack = new ItemStack(spawnEgg);
                    }
                }
                options.add(new RegistryOption(value, fullId, iconStack));
            }
            options.sort((a, b) -> a.value().compareToIgnoreCase(b.value()));
            return options;
        }
    }

}
