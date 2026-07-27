package com.pathmind.ui.graph;

import static com.pathmind.ui.graph.ParameterDropdownOptions.getParameterDropdownOptions;
import static com.pathmind.ui.graph.ParameterDropdownOptions.resolveParameterDropdownIcon;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isAttributeDetectionDropdownParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockFaceParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockItemParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockStateParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBooleanLiteralParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isDirectionParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isFabricEventSensorParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isGuiParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isHandParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isInlineDropdownParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isMouseButtonParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isVillagerProfessionParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isVillagerTradeParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isVillagerTradeVariantParameter;
import static com.pathmind.util.PathmindI18n.tr;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DropdownLayoutHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Owns the lifecycle and rendering of inline parameter dropdowns. Editing
 * primitives remain supplied by the host so this controller never reads
 * {@link NodeGraph} state directly.
 */
final class ParameterDropdownController {
    private static final int MAX_ROWS = 8;
    private static final int ROW_HEIGHT = 16;
    private static final int SIDE_PADDING = 6;
    private static final int SCROLLBAR_ALLOWANCE = 8;
    private static final int ICON_ALLOWANCE = 24;

    interface Host {
        boolean isEditingParameterField();
        Node parameterEditingNode();
        int parameterEditingIndex();
        String parameterEditBuffer();
        int parameterCaretPosition();
        void replaceParameterEditBuffer(String value, int caretPosition);
        void updateParameterFieldContentWidth(Node node, Font textRenderer, int index, String value);
        void refreshStateParameterPreview();
        boolean applyParameterEdit();
        void notifyNodeParametersChanged(Node node);
        void closeModeDropdown();
        void closeSchematicDropdown();
        void closeRunPresetDropdown();
        void closeRandomRoundingDropdown();
        void stopParameterEditing(boolean commit);
        int parameterFieldLeft(Node node);
        int inlineParameterFieldTop(Node node, int index);
        int parameterFieldWidth(Node node);
        int parameterFieldHeight();
        int cameraX();
        int cameraY();
        int screenToUiX(int screenX);
        int screenToUiY(int screenY);
        float zoomScale();
        Font getClientTextRenderer();
        float dropdownAnimationProgress(AnimatedValue animation, boolean open);
        int selectedNodeAccentColor();
        void enableDropdownScissor(GuiGraphics context, int x, int y, int width, int height);
        String trimTextToWidth(String text, Font renderer, int maxWidth);
        void drawNodeText(GuiGraphics context, Font renderer, Component text, int x, int y, int color);
    }

    private final Host host;
    private final AnimatedValue animation = AnimatedValue.forHover();
    private final List<ParameterDropdownOption> options = new ArrayList<>();
    private Node node;
    private int index = -1;
    private boolean open;
    private int hoverIndex = -1;
    private int scrollOffset;
    private int fieldX;
    private int fieldY;
    private int fieldWidth;
    private int fieldHeight;
    private String query = "";
    private Node suppressedNode;
    private int suppressedIndex = -1;
    private String suppressedQuery = "";

    ParameterDropdownController(Host host) {
        this.host = host;
    }

    boolean isOpen() {
        return open;
    }

    Node getNode() {
        return node;
    }

    int getIndex() {
        return index;
    }

    void update(Node candidate, int candidateIndex, Font textRenderer,
                int candidateFieldX, int candidateFieldY, int candidateFieldWidth, int candidateFieldHeight) {
        if (!host.isEditingParameterField()
            || host.parameterEditingNode() != candidate
            || host.parameterEditingIndex() != candidateIndex) {
            return;
        }
        if (!isBlockItemParameter(candidate, candidateIndex)
            && !isGuiParameter(candidate, null)
            && !isMouseButtonParameter(candidate, null)
            && !isHandParameter(candidate, null)
            && !isDirectionParameter(candidate, candidateIndex)
            && !isBooleanLiteralParameter(candidate, candidateIndex)
            && !isAttributeDetectionDropdownParameter(candidate, candidateIndex)
            && !isBlockFaceParameter(candidate, candidateIndex)
            && !isFabricEventSensorParameter(candidate, candidateIndex)
            && !isVillagerProfessionParameter(candidate, candidateIndex)
            && !isVillagerTradeParameter(candidate, candidateIndex)
            && !isVillagerTradeVariantParameter(candidate, candidateIndex)) {
            close();
            return;
        }
        ParameterSegment segment = getSegment(host.parameterEditBuffer(), host.parameterCaretPosition());
        String candidateQuery = segment.trimmedSegment == null ? "" : segment.trimmedSegment.trim();
        if ((isVillagerProfessionParameter(candidate, candidateIndex)
            || isVillagerTradeParameter(candidate, candidateIndex)
            || isVillagerTradeVariantParameter(candidate, candidateIndex))
            && candidateIndex < candidate.getParameters().size()
            && Objects.equals(host.parameterEditBuffer(), candidate.getParameters().get(candidateIndex).getStringValue())) {
            candidateQuery = "";
        }

        if (isSuppressed(candidate, candidateIndex, candidateQuery)) {
            close();
            return;
        }

        List<ParameterDropdownOption> candidateOptions =
            getParameterDropdownOptions(candidate, candidateIndex, candidateQuery);
        if (!host.parameterEditBuffer().trim().isEmpty()) {
            candidateOptions.removeIf(option -> option.value().isEmpty());
        }
        boolean changed = candidate != node
            || candidateIndex != index
            || !Objects.equals(query, candidateQuery);
        if (changed) {
            scrollOffset = 0;
            hoverIndex = -1;
        }

        node = candidate;
        index = candidateIndex;
        fieldX = candidateFieldX;
        fieldY = candidateFieldY;
        fieldWidth = candidateFieldWidth;
        fieldHeight = candidateFieldHeight;
        query = candidateQuery;
        options.clear();
        options.addAll(candidateOptions);
        open = true;
    }

    void close() {
        open = false;
        hoverIndex = -1;
    }

    void clearSuppression() {
        suppressedNode = null;
        suppressedIndex = -1;
        suppressedQuery = "";
    }

    void openInline(Node candidate, int candidateIndex) {
        if (candidate == null || !isInlineDropdownParameter(candidate, candidateIndex)) {
            return;
        }
        host.closeModeDropdown();
        host.closeSchematicDropdown();
        host.closeRunPresetDropdown();
        host.closeRandomRoundingDropdown();
        host.stopParameterEditing(false);
        node = candidate;
        index = candidateIndex;
        fieldX = host.parameterFieldLeft(candidate) - host.cameraX();
        fieldY = host.inlineParameterFieldTop(candidate, candidateIndex) - host.cameraY();
        fieldWidth = host.parameterFieldWidth(candidate);
        fieldHeight = host.parameterFieldHeight();
        query = "";
        scrollOffset = 0;
        hoverIndex = -1;
        options.clear();
        options.addAll(getParameterDropdownOptions(candidate, candidateIndex, ""));
        open = true;
    }

    boolean handleClick(double screenX, double screenY) {
        if (!open) {
            return false;
        }
        int x = (int) screenX;
        int y = (int) screenY;
        if (isPointInsideList(x, y)) {
            int selectedIndex = getIndexAt(x, y);
            if (selectedIndex >= 0) {
                applySelection(selectedIndex);
            }
            return true;
        }
        int transformedX = host.screenToUiX(x);
        int transformedY = host.screenToUiY(y);
        if (transformedX >= fieldX && transformedX <= fieldX + fieldWidth
            && transformedY >= fieldY && transformedY <= fieldY + fieldHeight) {
            if (!host.isEditingParameterField()
                && node != null
                && isInlineDropdownParameter(node, index)) {
                close();
            }
            return true;
        }
        if (host.isEditingParameterField()) {
            ParameterSegment segment = getSegment(host.parameterEditBuffer(), host.parameterCaretPosition());
            String currentQuery = segment.trimmedSegment == null ? "" : segment.trimmedSegment.trim();
            suppress(host.parameterEditingNode(), host.parameterEditingIndex(), currentQuery);
        }
        close();
        return false;
    }

    boolean handleScroll(double screenX, double screenY, double verticalAmount) {
        if (!open || !isPointInsideList((int) screenX, (int) screenY)) {
            return false;
        }
        DropdownLayoutHelper.Layout layout = getLayout();
        if (layout.maxScrollOffset <= 0) {
            return false;
        }
        int delta = (int) Math.signum(verticalAmount);
        if (delta == 0) {
            return false;
        }
        scrollOffset = Mth.clamp(scrollOffset - delta, 0, layout.maxScrollOffset);
        return true;
    }

    void render(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        float animationProgress = host.dropdownAnimationProgress(animation, open);
        if (node == null) {
            return;
        }
        if (animationProgress <= 0.001f) {
            clearState();
            return;
        }
        int optionCount = Math.max(1, options.size());
        float zoom = host.zoomScale();
        int transformedMouseX = Math.round(mouseX / zoom);
        int transformedMouseY = Math.round(mouseY / zoom);
        int dropdownWidth = getDropdownWidth();
        DropdownLayoutHelper.Layout layout = getLayout();
        int listTop = getListTop();
        int listLeft = fieldX;
        int listRight = listLeft + dropdownWidth;
        int listHeight = layout.height;
        int listBottom = listTop + listHeight;
        int animatedHeight = Math.max(1, (int) (listHeight * animationProgress));
        int accentColor = host.selectedNodeAccentColor();
        UIStyleHelper.ScrollContainerPalette containerPalette =
            UIStyleHelper.getScrollContainerPalette(accentColor, animationProgress, true, false);

        host.enableDropdownScissor(context, listLeft, listTop, dropdownWidth, animatedHeight);
        UIStyleHelper.drawScrollContainer(context, listLeft, listTop, dropdownWidth, listHeight, containerPalette);

        scrollOffset = Mth.clamp(scrollOffset, 0, layout.maxScrollOffset);
        hoverIndex = -1;
        if (animationProgress >= 1f
            && transformedMouseX >= listLeft && transformedMouseX <= listRight
            && transformedMouseY >= listTop && transformedMouseY <= listBottom) {
            int row = (transformedMouseY - listTop) / ROW_HEIGHT;
            if (row >= 0 && row < layout.visibleCount) {
                hoverIndex = scrollOffset + row;
            }
        }

        int iconSize = 16;
        int padding = 4;
        for (int row = 0; row < layout.visibleCount; row++) {
            int optionIndex = scrollOffset + row;
            String optionLabel = options.isEmpty()
                ? tr("pathmind.dropdown.noMatches")
                : options.get(optionIndex).label();
            int rowTop = listTop + row * ROW_HEIGHT;
            boolean hovered = options.isEmpty()
                ? row == 0 && hoverIndex >= 0
                : optionIndex == hoverIndex;
            UIStyleHelper.DropdownRowPalette rowPalette =
                UIStyleHelper.getDropdownRowPalette(accentColor, hovered ? 1f : 0f, false, false);
            if (hovered) {
                UIStyleHelper.drawDropdownRow(context, listLeft + 1, rowTop + 1,
                    dropdownWidth - 2, ROW_HEIGHT - 1, rowPalette);
            }
            int iconX = listLeft + padding;
            int iconY = rowTop + (ROW_HEIGHT - iconSize) / 2;
            String optionValue = options.isEmpty() ? "" : options.get(optionIndex).value();
            ItemStack icon = resolveParameterDropdownIcon(node, index, optionValue);
            if (!icon.isEmpty()) {
                context.renderItem(icon, iconX, iconY);
            }
            int textPadding = 3;
            int textX = !icon.isEmpty() ? iconX + iconSize + padding : listLeft + textPadding;
            int maxTextWidth = dropdownWidth - (textX - listLeft) - textPadding - SCROLLBAR_ALLOWANCE;
            String rowText = host.trimTextToWidth(optionLabel, textRenderer, Math.max(0, maxTextWidth));
            host.drawNodeText(context, textRenderer, Component.literal(rowText), textX, rowTop + 4,
                hovered ? rowPalette.textColor() : UITheme.TEXT_PRIMARY);
        }

        DropdownLayoutHelper.drawScrollBar(
            context, listLeft, listTop, dropdownWidth, listHeight, optionCount,
            layout.visibleCount, scrollOffset, layout.maxScrollOffset,
            containerPalette.trackColor(), containerPalette.thumbColor());
        DropdownLayoutHelper.drawOutline(
            context, listLeft, listTop, dropdownWidth, listHeight, containerPalette.borderColor());
        context.disableScissor();
    }

    void clearState() {
        if (open) {
            return;
        }
        node = null;
        index = -1;
        hoverIndex = -1;
        scrollOffset = 0;
        query = "";
        options.clear();
    }

    private boolean applySelection(int optionIndex) {
        if (!open || options.isEmpty() || optionIndex < 0 || optionIndex >= options.size()) {
            return false;
        }
        if (!host.isEditingParameterField()) {
            if (node == null || !isInlineDropdownParameter(node, index)) {
                return false;
            }
            ParameterDropdownOption option = options.get(optionIndex);
            NodeParameter parameter = node.getParameters().get(index);
            if (parameter == null || option == null) {
                return false;
            }
            parameter.setStringValueFromUser(option.value());
            node.setParameterValueAndPropagate(parameter.getName(), option.value());
            node.recalculateDimensions();
            host.notifyNodeParametersChanged(node);
            close();
            return true;
        }

        ParameterDropdownOption option = options.get(optionIndex);
        String editBuffer = host.parameterEditBuffer();
        ParameterSegment segment = getSegment(editBuffer, host.parameterCaretPosition());
        String prefix = editBuffer.substring(0, segment.start);
        String suffix = editBuffer.substring(segment.end);
        Node editingNode = host.parameterEditingNode();
        int editingIndex = host.parameterEditingIndex();
        boolean keepMouseButtonDefaultPlaceholder = isMouseButtonParameter(editingNode, null)
            && "Left".equalsIgnoreCase(option.value())
            && (segment.trimmedSegment == null || segment.trimmedSegment.trim().isEmpty());
        boolean keepHandDefaultPlaceholder = isHandParameter(editingNode, null)
            && "main".equalsIgnoreCase(option.value())
            && (segment.trimmedSegment == null || segment.trimmedSegment.trim().isEmpty());
        boolean keepDirectionDefaultPlaceholder = isDirectionParameter(editingNode, editingIndex)
            && "north".equalsIgnoreCase(option.value());
        boolean keepBooleanDefaultPlaceholder = isBooleanLiteralParameter(editingNode, editingIndex)
            && "true".equalsIgnoreCase(option.value());
        String replacement = keepMouseButtonDefaultPlaceholder
            || keepHandDefaultPlaceholder
            || keepDirectionDefaultPlaceholder
            || keepBooleanDefaultPlaceholder
            ? ""
            : segment.leadingWhitespace + option.value();
        String newBuffer = prefix + replacement + suffix;
        int newCaret = prefix.length() + replacement.length();
        host.replaceParameterEditBuffer(newBuffer, newCaret);
        host.updateParameterFieldContentWidth(editingNode, host.getClientTextRenderer(), editingIndex, newBuffer);
        host.refreshStateParameterPreview();
        boolean changed = host.applyParameterEdit();
        if (changed) {
            host.notifyNodeParametersChanged(editingNode);
        }
        ParameterSegment updatedSegment = getSegment(newBuffer, newCaret);
        String updatedQuery = updatedSegment.trimmedSegment == null ? "" : updatedSegment.trimmedSegment.trim();
        suppress(editingNode, editingIndex, updatedQuery);
        close();
        return true;
    }

    private void suppress(Node candidate, int candidateIndex, String candidateQuery) {
        suppressedNode = candidate;
        suppressedIndex = candidateIndex;
        suppressedQuery = candidateQuery == null ? "" : candidateQuery;
    }

    private boolean isSuppressed(Node candidate, int candidateIndex, String candidateQuery) {
        if (suppressedNode == null
            || suppressedNode != candidate
            || suppressedIndex != candidateIndex) {
            return false;
        }
        String normalizedQuery = candidateQuery == null ? "" : candidateQuery;
        if (Objects.equals(suppressedQuery, normalizedQuery)) {
            return true;
        }
        clearSuppression();
        return false;
    }

    private int getListTop() {
        return fieldY + fieldHeight;
    }

    private int getDropdownWidth() {
        Font textRenderer = host.getClientTextRenderer();
        if (textRenderer == null) {
            return fieldWidth;
        }
        int longestLabelWidth = textRenderer.width(tr("pathmind.dropdown.noMatches"));
        for (ParameterDropdownOption option : options) {
            if (option != null && option.label() != null) {
                longestLabelWidth = Math.max(longestLabelWidth, textRenderer.width(option.label()));
            }
        }
        return longestLabelWidth + ICON_ALLOWANCE + SIDE_PADDING * 2 + SCROLLBAR_ALLOWANCE;
    }

    private DropdownLayoutHelper.Layout getLayout() {
        int optionCount = Math.max(1, options.size());
        float zoom = Math.max(0.01f, host.zoomScale());
        int transformedScreenHeight =
            Math.round(Minecraft.getInstance().getWindow().getGuiScaledHeight() / zoom);
        return DropdownLayoutHelper.calculate(
            optionCount, ROW_HEIGHT, MAX_ROWS, getListTop(), transformedScreenHeight);
    }

    private boolean isPointInsideList(int screenX, int screenY) {
        if (!open) {
            return false;
        }
        float zoom = host.zoomScale();
        int transformedX = Math.round(screenX / zoom);
        int transformedY = Math.round(screenY / zoom);
        int dropdownWidth = getDropdownWidth();
        DropdownLayoutHelper.Layout layout = getLayout();
        int listTop = getListTop();
        return transformedX >= fieldX && transformedX <= fieldX + dropdownWidth
            && transformedY >= listTop && transformedY <= listTop + layout.height;
    }

    private int getIndexAt(int screenX, int screenY) {
        if (!open) {
            return -1;
        }
        int transformedY = Math.round(screenY / host.zoomScale());
        DropdownLayoutHelper.Layout layout = getLayout();
        int row = (transformedY - getListTop()) / ROW_HEIGHT;
        if (row < 0 || row >= layout.visibleCount || options.isEmpty()) {
            return -1;
        }
        return scrollOffset + row;
    }

    private static ParameterSegment getSegment(String value, int caret) {
        String working = value != null ? value : "";
        int clamped = Mth.clamp(caret, 0, working.length());
        int start = findSegmentStart(working, clamped);
        int end = findSegmentEnd(working, clamped);
        String segment = working.substring(start, end);
        int leadingEnd = 0;
        while (leadingEnd < segment.length() && Character.isWhitespace(segment.charAt(leadingEnd))) {
            leadingEnd++;
        }
        return new ParameterSegment(
            start, end, segment.substring(0, leadingEnd), segment.substring(leadingEnd));
    }

    private static int findSegmentStart(String value, int caret) {
        int found = value.lastIndexOf(',', Math.max(0, caret - 1));
        return found == -1 ? 0 : found + 1;
    }

    private static int findSegmentEnd(String value, int caret) {
        int found = value.indexOf(',', Math.max(0, caret));
        return found == -1 ? value.length() : found;
    }

    private record ParameterSegment(
        int start,
        int end,
        String leadingWhitespace,
        String trimmedSegment
    ) {
    }
}
