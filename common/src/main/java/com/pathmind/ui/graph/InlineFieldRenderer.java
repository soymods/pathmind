package com.pathmind.ui.graph;

import static com.pathmind.ui.graph.InlineVariableRenderer.buildInlineVariableRender;
import static com.pathmind.ui.graph.InlineVariableRenderer.isSingleKnownInlineVariableReference;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.graph.InlineVariableRenderer.InlineVariableRender;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class InlineFieldRenderer {

    interface Host {
        int cameraX();
        int cameraY();
        int screenToWorldX(int screenX);
        int screenToWorldY(int screenY);
        int selectedNodeAccentColor();
        float textFieldHighlightProgress(Object key, boolean hovered, boolean active);
        UIStyleHelper.FieldPalette nodeInputPalette(boolean isOverSidebar, int accentColor, float progress,
                                                    boolean active, boolean disabled);
        UIStyleHelper.FieldPalette lowDetailAwareFieldPalette(int backgroundColor, int borderColor,
                                                              int innerBorderColor, int textColor,
                                                              int placeholderColor, boolean isOverSidebar);
        void drawNodeText(GuiGraphics context, Font renderer, Component text, int x, int y, int color);
        String trimTextToWidth(String text, Font renderer, int maxWidth);
        Set<String> collectRuntimeVariableNames(Node node);
        boolean shouldBuildInlineExpressionRender(String rawText, Set<String> variableNames);
        boolean shouldRenderNodeText();
        boolean isCompactViewportMode();
        boolean isScreenCoordinateCaptureActiveFor(Node node);
        int screenCoordinatePreviewX();
        int screenCoordinatePreviewY();
        void renderScreenCoordinatePickerButton(GuiGraphics context, Font textRenderer, Node node,
                                                boolean isOverSidebar, int mouseX, int mouseY);
        void renderDropdownSelectorField(GuiGraphics context, Font textRenderer, Node node,
                                         boolean isOverSidebar, int mouseX, int mouseY,
                                         int fieldLeft, int fieldTop, int fieldWidth, int fieldHeight,
                                         String label, boolean includeValue, String value);
        boolean isMoveItemAllAmountValue(String value);
        void renderAmountToggle(GuiGraphics context, Node node, boolean amountEnabled, boolean isOverSidebar);
        boolean isPresetSelectorNode(Node node);
        void renderPresetSelectorField(GuiGraphics context, Font textRenderer, Node node,
                                       boolean isOverSidebar, int mouseX, int mouseY);
        boolean isRunPresetDropdownOpenFor(Node node);
        String getStopTargetParameterKey(Node node);
        String getStopTargetPlaceholder(Node node);
    }

    private final Host host;
    private final InlineFieldController controller;

    InlineFieldRenderer(Host host, InlineFieldController controller) {
        this.host = host;
        this.controller = controller;
    }

    void renderCoordinateInputFields(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                     int mouseX, int mouseY) {
        String[] axes = node != null ? node.getCoordinateFieldAxes() : new String[0];
        int baseLabelColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_SECONDARY;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int activeTextColor = UITheme.TEXT_EDITING;

        if (controller.isEditingCoordinateField() && controller.getCoordinateEditingNode() == node) {
            controller.getCoordinateEditor().updateCaretBlink();
        }

        int cameraX = host.cameraX();
        int cameraY = host.cameraY();
        int labelTop = node.getCoordinateFieldLabelTop() - cameraY;
        int labelHeight = node.getCoordinateFieldLabelHeight();
        int inputTop = node.getCoordinateFieldInputTop() - cameraY;
        int fieldHeight = node.getCoordinateFieldHeight();
        int fieldWidth = node.getCoordinateFieldWidth();
        int spacing = node.getCoordinateFieldSpacing();
        int startX = node.getCoordinateFieldStartX() - cameraX;
        boolean captureActive = host.isScreenCoordinateCaptureActiveFor(node);
        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);

        for (int i = 0; i < axes.length; i++) {
            int fieldX = startX + i * (fieldWidth + spacing);

            boolean editingAxis = controller.isEditingCoordinateField()
                && controller.getCoordinateEditingNode() == node
                && controller.getCoordinateEditingAxis() == i;

            String axisLabel = axes[i];
            int labelWidth = textRenderer.width(axisLabel);
            int labelX = fieldX + Math.max(0, (fieldWidth - labelWidth) / 2);
            int labelY = labelTop + Math.max(0, (labelHeight - textRenderer.lineHeight) / 2);
            int labelColor = editingAxis ? UITheme.TEXT_EDITING_LABEL : baseLabelColor;
            host.drawNodeText(context, textRenderer, Component.literal(axisLabel), labelX, labelY, labelColor);

            int inputBottom = inputTop + fieldHeight;
            boolean hovered = !isOverSidebar
                && worldMouseX >= fieldX + cameraX
                && worldMouseX <= fieldX + cameraX + fieldWidth
                && worldMouseY >= inputTop + cameraY
                && worldMouseY <= inputTop + cameraY + fieldHeight;
            float progress = host.textFieldHighlightProgress(node.getId() + "#coord:" + i, hovered, editingAxis);
            UIStyleHelper.FieldPalette palette = host.nodeInputPalette(
                isOverSidebar, host.selectedNodeAccentColor(), progress, editingAxis, false);
            int borderColor = palette.borderColor();
            int valueColor = isOverSidebar ? (editingAxis ? activeTextColor : textColor)
                : AnimationHelper.lerpColor(textColor, activeTextColor, progress);
            if (captureActive && !editingAxis) {
                borderColor = host.selectedNodeAccentColor();
                valueColor = UITheme.TEXT_TERTIARY;
            }

            UIStyleHelper.drawFieldFrame(
                context,
                fieldX,
                inputTop,
                fieldWidth,
                fieldHeight,
                host.lowDetailAwareFieldPalette(
                    palette.backgroundColor(),
                    borderColor,
                    palette.innerBorderColor(),
                    palette.textColor(),
                    palette.placeholderColor(),
                    isOverSidebar
                )
            );

            String value;
            if (editingAxis) {
                value = controller.getCoordinateEditor().getBuffer();
            } else if (captureActive) {
                value = Integer.toString(i == 0 ? host.screenCoordinatePreviewX() : host.screenCoordinatePreviewY());
            } else {
                NodeParameter parameter = node.getParameter(axisLabel);
                value = parameter != null ? parameter.getStringValue() : "";
            }
            if (value == null) {
                value = "";
            }

            String display = editingAxis
                ? value
                : host.trimTextToWidth(value, textRenderer, fieldWidth - 6);
            int variableHighlightColor = UITheme.ACCENT_AMBER;
            Set<String> coordVariableNames = host.collectRuntimeVariableNames(node);
            InlineVariableRender coordRenderData = null;
            if (host.shouldBuildInlineExpressionRender(value, coordVariableNames)) {
                InlineVariableRender candidate = buildInlineVariableRender(value, coordVariableNames, valueColor, variableHighlightColor);
                if (editingAxis) {
                    coordRenderData = candidate;
                    display = coordRenderData.displayText;
                } else if (textRenderer.width(candidate.displayText) <= fieldWidth - 6) {
                    coordRenderData = candidate;
                    display = coordRenderData.displayText;
                } else if (isSingleKnownInlineVariableReference(value, coordVariableNames)) {
                    display = host.trimTextToWidth(candidate.displayText, textRenderer, fieldWidth - 6);
                    valueColor = variableHighlightColor;
                }
            }

            int textX = fieldX + 3;
            int textY = inputTop + (fieldHeight - textRenderer.lineHeight) / 2 + 1;
            if (editingAxis && controller.getCoordinateEditor().hasSelection()) {
                int start = controller.getCoordinateEditor().getSelectionStart();
                int end = controller.getCoordinateEditor().getSelectionEnd();
                if (coordRenderData != null) {
                    start = coordRenderData.toDisplayIndex(start);
                    end = coordRenderData.toDisplayIndex(end);
                }
                if (start >= 0 && end >= 0 && start <= display.length() && end <= display.length()) {
                    int selectionStartX = textX + textRenderer.width(display.substring(0, start));
                    int selectionEndX = textX + textRenderer.width(display.substring(0, end));
                    context.fill(selectionStartX, inputTop + 2, selectionEndX, inputBottom - 2, UITheme.TEXT_SELECTION_BG);
                }
            }
            if (coordRenderData != null && host.shouldRenderNodeText()) {
                coordRenderData.draw(context, textRenderer, textX, textY);
            } else {
                host.drawNodeText(context, textRenderer, Component.literal(display), textX, textY, valueColor);
            }

            if (editingAxis && controller.getCoordinateEditor().isCaretVisible()) {
                int caretIndex = controller.getCoordinateEditor().getCaretPosition();
                if (coordRenderData != null) {
                    caretIndex = coordRenderData.toDisplayIndex(caretIndex);
                }
                caretIndex = Mth.clamp(caretIndex, 0, display.length());
                int caretX = textX + textRenderer.width(display.substring(0, caretIndex));
                caretX = Math.min(caretX, fieldX + fieldWidth - 2);
                int caretBaseline = Math.min(textY + textRenderer.lineHeight - 1, inputBottom - 2);
                UIStyleHelper.drawTextCaretAtBaseline(context, textRenderer, caretX, caretBaseline,
                    fieldX + fieldWidth - 2, UITheme.CARET_COLOR);
            }
        }

        if (node.hasScreenCoordinatePickerButton()) {
            host.renderScreenCoordinatePickerButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
    }

    void renderAmountInputField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                int mouseX, int mouseY) {
        int baseLabelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int activeTextColor = UITheme.TEXT_EDITING;
        boolean amountEnabled = node.isAmountInputEnabled();

        boolean editing = controller.isEditingAmountField() && controller.getAmountEditingNode() == node;
        if (editing) {
            controller.getAmountEditor().updateCaretBlink();
        }

        int cameraX = host.cameraX();
        int cameraY = host.cameraY();
        int labelTop = node.getAmountFieldLabelTop() - cameraY;
        int labelHeight = node.getAmountFieldLabelHeight();
        int fieldTop = node.getAmountFieldInputTop() - cameraY;
        int fieldHeight = node.getAmountFieldHeight();
        int fieldLeft = node.getAmountFieldLeft() - cameraX;
        int fieldWidth = node.getAmountFieldWidth();
        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);

        if ((node.getType() == NodeType.WAIT || node.getType() == NodeType.PARAM_DURATION) && node.supportsModeSelection()) {
            int dropdownLeft = fieldLeft;
            int dropdownTop = labelTop;
            int dropdownWidth = fieldWidth;
            int dropdownHeight = labelHeight;
            String unitLabel = node.getAmountFieldLabel();
            host.renderDropdownSelectorField(
                context, textRenderer, node, isOverSidebar, mouseX, mouseY,
                dropdownLeft, dropdownTop, dropdownWidth, dropdownHeight,
                unitLabel, false, null
            );
        } else {
            int labelY = labelTop + Math.max(0, (labelHeight - textRenderer.lineHeight) / 2);
            host.drawNodeText(context, textRenderer, Component.literal(node.getAmountFieldLabel()),
                fieldLeft + 2, labelY, baseLabelColor);
        }

        int fieldBottom = fieldTop + fieldHeight;
        int disabledBg = isOverSidebar ? UITheme.BACKGROUND_TERTIARY
            : (host.isCompactViewportMode() ? UITheme.BACKGROUND_SECONDARY : UITheme.BUTTON_DEFAULT_BG);
        boolean hovered = !isOverSidebar
            && worldMouseX >= node.getAmountFieldLeft()
            && worldMouseX <= node.getAmountFieldLeft() + fieldWidth
            && worldMouseY >= node.getAmountFieldInputTop()
            && worldMouseY <= node.getAmountFieldInputTop() + fieldHeight;
        float progress = amountEnabled
            ? host.textFieldHighlightProgress(node.getId() + "#amount", hovered, editing)
            : 0f;
        UIStyleHelper.FieldPalette palette = host.nodeInputPalette(
            isOverSidebar, host.selectedNodeAccentColor(), progress, editing, !amountEnabled);
        int valueColor = amountEnabled
            ? (isOverSidebar ? ((editing && amountEnabled) ? activeTextColor : textColor)
                : AnimationHelper.lerpColor(textColor, activeTextColor, progress))
            : UITheme.TEXT_SECONDARY;

        UIStyleHelper.drawFieldFrame(
            context,
            fieldLeft,
            fieldTop,
            fieldWidth,
            fieldHeight,
            amountEnabled
                ? palette
                : host.lowDetailAwareFieldPalette(
                    disabledBg,
                    isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT,
                    UITheme.PANEL_INNER_BORDER,
                    palette.textColor(),
                    palette.placeholderColor(),
                    isOverSidebar
                )
        );

        String value;
        if (editing && amountEnabled) {
            value = controller.getAmountEditor().getBuffer();
        } else {
            String amountKey = node.getAmountParameterKey();
            NodeParameter amountParam = node.getParameter(amountKey);
            value = amountParam != null ? amountParam.getStringValue() : "";
            if (node.getType() == NodeType.MOVE_ITEM && host.isMoveItemAllAmountValue(value)) {
                value = "";
            }
        }

        boolean showPlaceholder = amountEnabled && value.isEmpty();
        String display = editing
            ? value
            : host.trimTextToWidth(value, textRenderer, fieldWidth - 6);
        if (showPlaceholder) {
            if (node.getType() == NodeType.MOVE_ITEM) {
                display = "All";
            } else if (node.getType() == NodeType.TRADE
                || node.getType() == NodeType.SENSOR_VILLAGER_TRADE
                || node.getType() == NodeType.SENSOR_IN_STOCK) {
                display = "1";
            } else {
                display = "0";
            }
            valueColor = UITheme.TEXT_TERTIARY;
        }
        int variableHighlightColor = UITheme.ACCENT_AMBER;
        Set<String> amountVariableNames = host.collectRuntimeVariableNames(node);
        InlineVariableRender amountRenderData = null;
        if (amountEnabled && !showPlaceholder && host.shouldBuildInlineExpressionRender(value, amountVariableNames)) {
            InlineVariableRender candidate = buildInlineVariableRender(value, amountVariableNames, valueColor, variableHighlightColor);
            if (editing && amountEnabled) {
                amountRenderData = candidate;
                display = amountRenderData.displayText;
            } else if (textRenderer.width(candidate.displayText) <= fieldWidth - 6) {
                amountRenderData = candidate;
                display = amountRenderData.displayText;
            } else if (isSingleKnownInlineVariableReference(value, amountVariableNames)) {
                display = host.trimTextToWidth(candidate.displayText, textRenderer, fieldWidth - 6);
                valueColor = variableHighlightColor;
            }
        }

        int textX = fieldLeft + 3;
        int textY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2 + 1;
        if (editing && amountEnabled && controller.getAmountEditor().hasSelection()) {
            int start = controller.getAmountEditor().getSelectionStart();
            int end = controller.getAmountEditor().getSelectionEnd();
            if (amountRenderData != null) {
                start = amountRenderData.toDisplayIndex(start);
                end = amountRenderData.toDisplayIndex(end);
            }
            if (start >= 0 && end >= 0 && start <= display.length() && end <= display.length()) {
                int selectionStartX = textX + textRenderer.width(display.substring(0, start));
                int selectionEndX = textX + textRenderer.width(display.substring(0, end));
                context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldBottom - 2, UITheme.TEXT_SELECTION_BG);
            }
        }
        if (amountRenderData != null && host.shouldRenderNodeText()) {
            amountRenderData.draw(context, textRenderer, textX, textY);
        } else {
            host.drawNodeText(context, textRenderer, Component.literal(display), textX, textY, valueColor);
        }

        if (editing && amountEnabled && controller.getAmountEditor().isCaretVisible()) {
            int caretIndex = controller.getAmountEditor().getCaretPosition();
            if (amountRenderData != null) {
                caretIndex = amountRenderData.toDisplayIndex(caretIndex);
            }
            caretIndex = Mth.clamp(caretIndex, 0, display.length());
            int caretX = textX + textRenderer.width(display.substring(0, caretIndex));
            caretX = Math.min(caretX, fieldLeft + fieldWidth - 2);
            int caretBaseline = Math.min(textY + textRenderer.lineHeight - 1, fieldBottom - 2);
            UIStyleHelper.drawTextCaretAtBaseline(context, textRenderer, caretX, caretBaseline,
                fieldLeft + fieldWidth - 2, UITheme.CARET_COLOR);
        }

        if (node.hasAmountToggle()) {
            host.renderAmountToggle(context, node, amountEnabled, isOverSidebar);
        }
    }

    void renderMessageInputFields(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                  int mouseX, int mouseY) {
        int baseLabelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int activeTextColor = UITheme.TEXT_EDITING;
        int variableHighlightColor = UITheme.ACCENT_AMBER;

        boolean editing = controller.isEditingMessageField() && controller.getMessageEditingNode() == node;
        if (editing) {
            controller.getMessageEditor().updateCaretBlink();
        }

        int cameraX = host.cameraX();
        int cameraY = host.cameraY();
        Set<String> runtimeVariableNames = host.collectRuntimeVariableNames(node);
        int fieldCount = node.getMessageFieldCount();
        for (int i = 0; i < fieldCount; i++) {
            int labelTop = node.getMessageFieldLabelTop(i) - cameraY;
            int labelHeight = node.getMessageFieldLabelHeight();
            int fieldTop = node.getMessageFieldInputTop(i) - cameraY;
            int fieldHeight = node.getMessageFieldHeight();
            int fieldLeft = node.getMessageFieldLeft() - cameraX;
            int fieldWidth = node.getMessageFieldWidth();
            int worldMouseX = host.screenToWorldX(mouseX);
            int worldMouseY = host.screenToWorldY(mouseY);

            boolean editingThis = editing && controller.getMessageEditingIndex() == i;
            int labelY = labelTop + Math.max(0, (labelHeight - textRenderer.lineHeight) / 2);
            String label = node.getMessageFieldLabelText(i);
            host.drawNodeText(context, textRenderer, Component.literal(label), fieldLeft + 2, labelY, baseLabelColor);

            int fieldBottom = fieldTop + fieldHeight;
            boolean hovered = !isOverSidebar
                && worldMouseX >= node.getMessageFieldLeft()
                && worldMouseX <= node.getMessageFieldLeft() + fieldWidth
                && worldMouseY >= node.getMessageFieldInputTop(i)
                && worldMouseY <= node.getMessageFieldInputTop(i) + fieldHeight;
            float progress = host.textFieldHighlightProgress(node.getId() + "#message:" + i, hovered, editingThis);
            UIStyleHelper.FieldPalette palette = host.nodeInputPalette(
                isOverSidebar, host.selectedNodeAccentColor(), progress, editingThis, false);
            int valueColor = isOverSidebar ? (editingThis ? activeTextColor : textColor)
                : AnimationHelper.lerpColor(textColor, activeTextColor, progress);

            UIStyleHelper.drawFieldFrame(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, palette);

            String rawValue = editingThis ? controller.getMessageEditor().getBuffer() : node.getMessageLine(i);
            if (rawValue == null) {
                rawValue = "";
            }
            int textX = fieldLeft + 3;
            int textY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2 + 1;
            String fixedPrefix = "";
            int expressionTextX = textX;
            int expressionFieldWidth = Math.max(1, fieldWidth - 6 - textRenderer.width(fixedPrefix));
            String display = editingThis
                ? rawValue
                : host.trimTextToWidth(rawValue, textRenderer, expressionFieldWidth);
            InlineVariableRender renderData = null;
            if (host.shouldBuildInlineExpressionRender(rawValue, runtimeVariableNames)) {
                InlineVariableRender candidate = buildInlineVariableRender(
                    rawValue, runtimeVariableNames, valueColor, variableHighlightColor);
                if (editingThis) {
                    renderData = candidate;
                    display = renderData.displayText;
                } else if (textRenderer.width(candidate.displayText) <= expressionFieldWidth) {
                    renderData = candidate;
                    display = renderData.displayText;
                } else if (isSingleKnownInlineVariableReference(rawValue, runtimeVariableNames)) {
                    display = host.trimTextToWidth(candidate.displayText, textRenderer, expressionFieldWidth);
                    valueColor = variableHighlightColor;
                }
            }

            if (!fixedPrefix.isEmpty()) {
                host.drawNodeText(context, textRenderer, Component.literal(fixedPrefix), textX, textY, UITheme.TEXT_TERTIARY);
            }
            if (editingThis && controller.getMessageEditor().hasSelection()) {
                int start = controller.getMessageEditor().getSelectionStart();
                int end = controller.getMessageEditor().getSelectionEnd();
                if (renderData != null) {
                    start = renderData.toDisplayIndex(start);
                    end = renderData.toDisplayIndex(end);
                }
                if (start >= 0 && end >= 0 && start <= display.length() && end <= display.length()) {
                    int selectionStartX = expressionTextX + textRenderer.width(display.substring(0, start));
                    int selectionEndX = expressionTextX + textRenderer.width(display.substring(0, end));
                    context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldBottom - 2, UITheme.TEXT_SELECTION_BG);
                }
            }
            if (renderData != null) {
                if (host.shouldRenderNodeText()) {
                    renderData.draw(context, textRenderer, expressionTextX, textY);
                }
            } else {
                host.drawNodeText(context, textRenderer, Component.literal(display), expressionTextX, textY, valueColor);
            }

            if (editingThis && controller.getMessageEditor().isCaretVisible()) {
                int caretIndex = controller.getMessageEditor().getCaretPosition();
                if (renderData != null) {
                    caretIndex = renderData.toDisplayIndex(caretIndex);
                }
                caretIndex = Mth.clamp(caretIndex, 0, display.length());
                int caretX = expressionTextX + textRenderer.width(display.substring(0, caretIndex));
                caretX = Math.min(caretX, fieldLeft + fieldWidth - 2);
                int caretBaseline = Math.min(textY + textRenderer.lineHeight - 1, fieldBottom - 2);
                UIStyleHelper.drawTextCaretAtBaseline(context, textRenderer, caretX, caretBaseline,
                    fieldLeft + fieldWidth - 2, UITheme.CARET_COLOR);
            }
        }
    }

    void renderStopTargetInputField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                    int mouseX, int mouseY) {
        boolean isRunPresetNode = host.isPresetSelectorNode(node);
        if (isRunPresetNode) {
            host.renderPresetSelectorField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
            return;
        }
        int baseLabelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        boolean isActivateNode = node.getType() == NodeType.START_CHAIN;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_LABEL;
        int activeTextColor = UITheme.TEXT_LABEL;
        int caretColor = UITheme.TEXT_LABEL;
        boolean presetDropdownOpenForNode = isRunPresetNode && host.isRunPresetDropdownOpenFor(node);
        if (isRunPresetNode) {
            textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
            activeTextColor = UITheme.TEXT_PRIMARY;
            caretColor = UITheme.TEXT_PRIMARY;
        }

        boolean editing = controller.isEditingStopTargetField() && controller.getStopTargetEditingNode() == node;
        if (editing) {
            controller.getStopTargetEditor().updateCaretBlink();
        }

        int cameraX = host.cameraX();
        int cameraY = host.cameraY();
        int fieldTop = node.getStopTargetFieldInputTop() - cameraY;
        int fieldHeight = node.getStopTargetFieldHeight();
        int fieldLeft = node.getStopTargetFieldLeft() - cameraX;
        int fieldWidth = node.getStopTargetFieldWidth();

        int fieldBottom = fieldTop + fieldHeight;
        boolean activeVisual = isRunPresetNode ? presetDropdownOpenForNode : editing;
        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);
        boolean hovered = !isOverSidebar
            && worldMouseX >= node.getStopTargetFieldLeft()
            && worldMouseX <= node.getStopTargetFieldLeft() + fieldWidth
            && worldMouseY >= node.getStopTargetFieldInputTop()
            && worldMouseY <= node.getStopTargetFieldInputTop() + fieldHeight;
        float progress = host.textFieldHighlightProgress(node.getId() + "#stopTarget", hovered, activeVisual);
        int accentColor = isRunPresetNode ? host.selectedNodeAccentColor()
            : (isActivateNode ? UITheme.BORDER_HIGHLIGHT : host.selectedNodeAccentColor());
        UIStyleHelper.FieldPalette palette = host.nodeInputPalette(
            isOverSidebar, accentColor, progress, activeVisual, false);
        int borderColor = palette.borderColor();
        int valueColor = isOverSidebar ? (editing ? activeTextColor : textColor)
            : AnimationHelper.lerpColor(textColor, activeTextColor, progress);
        if (isActivateNode && isOverSidebar && !activeVisual) {
            borderColor = UITheme.BORDER_FOCUS;
        }

        if (isRunPresetNode) {
            int labelY = fieldTop - textRenderer.lineHeight - 2;
            if (labelY >= node.getY() - cameraY + 14) {
                host.drawNodeText(context, textRenderer, Component.translatable("pathmind.field.preset"),
                    fieldLeft, labelY, baseLabelColor);
            }
        }

        UIStyleHelper.drawFieldFrame(
            context,
            fieldLeft,
            fieldTop,
            fieldWidth,
            fieldHeight,
            new UIStyleHelper.FieldPalette(
                palette.backgroundColor(),
                borderColor,
                palette.innerBorderColor(),
                palette.textColor(),
                palette.placeholderColor()
            )
        );

        String value;
        if (editing) {
            value = controller.getStopTargetEditor().getBuffer();
        } else {
            NodeParameter targetParam = node.getParameter(host.getStopTargetParameterKey(node));
            value = targetParam != null ? targetParam.getStringValue() : "";
        }
        if (value == null) {
            value = "";
        }

        String display;
        if (!editing && value.isEmpty()) {
            display = host.getStopTargetPlaceholder(node);
            valueColor = UITheme.TEXT_TERTIARY;
        } else {
            display = value;
        }

        int reservedRightPadding = isRunPresetNode ? 16 : 6;
        display = editing
            ? display
            : host.trimTextToWidth(display, textRenderer, fieldWidth - reservedRightPadding);
        int variableHighlightColor = UITheme.ACCENT_AMBER;
        Set<String> stopTargetVariableNames = host.collectRuntimeVariableNames(node);
        InlineVariableRender stopTargetRenderData = null;
        if (host.shouldBuildInlineExpressionRender(value, stopTargetVariableNames)) {
            InlineVariableRender candidate = buildInlineVariableRender(
                value, stopTargetVariableNames, valueColor, variableHighlightColor);
            if (editing) {
                stopTargetRenderData = candidate;
                display = stopTargetRenderData.displayText;
            } else if (textRenderer.width(candidate.displayText) <= fieldWidth - 6) {
                stopTargetRenderData = candidate;
                display = stopTargetRenderData.displayText;
            } else if (isSingleKnownInlineVariableReference(value, stopTargetVariableNames)) {
                display = host.trimTextToWidth(candidate.displayText, textRenderer, fieldWidth - 6);
                valueColor = variableHighlightColor;
            }
        }

        int textX = fieldLeft + 3;
        int textY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2 + 1;
        if (editing && controller.getStopTargetEditor().hasSelection()) {
            int start = controller.getStopTargetEditor().getSelectionStart();
            int end = controller.getStopTargetEditor().getSelectionEnd();
            if (stopTargetRenderData != null) {
                start = stopTargetRenderData.toDisplayIndex(start);
                end = stopTargetRenderData.toDisplayIndex(end);
            }
            if (start >= 0 && end >= 0 && start <= display.length() && end <= display.length()) {
                int selectionStartX = textX + textRenderer.width(display.substring(0, start));
                int selectionEndX = textX + textRenderer.width(display.substring(0, end));
                int selectionColor = isRunPresetNode ? UITheme.TEXT_SELECTION_BG : UITheme.TEXT_SELECTION_DANGER_BG;
                context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldBottom - 2, selectionColor);
            }
        }
        if (stopTargetRenderData != null && host.shouldRenderNodeText()) {
            stopTargetRenderData.draw(context, textRenderer, textX, textY);
        } else {
            host.drawNodeText(context, textRenderer, Component.literal(display), textX, textY, valueColor);
        }

        if (editing && controller.getStopTargetEditor().isCaretVisible()) {
            int caretIndex = controller.getStopTargetEditor().getCaretPosition();
            if (stopTargetRenderData != null) {
                caretIndex = stopTargetRenderData.toDisplayIndex(caretIndex);
            }
            caretIndex = Mth.clamp(caretIndex, 0, display.length());
            int caretX = textX + textRenderer.width(display.substring(0, caretIndex));
            caretX = Math.min(caretX, fieldLeft + fieldWidth - 2);
            UIStyleHelper.drawTextCaret(context, caretX, fieldTop + 2, fieldBottom - 2, caretColor);
        }

        if (isRunPresetNode) {
            int arrowX = fieldLeft + fieldWidth - 10;
            int arrowY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2 + 1;
            String arrow = presetDropdownOpenForNode ? ">" : "v";
            int arrowColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
            host.drawNodeText(context, textRenderer, Component.literal(arrow), arrowX, arrowY, arrowColor);
        }
    }

    void renderVariableInputField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                  int mouseX, int mouseY) {
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_LABEL;
        int activeTextColor = UITheme.TEXT_LABEL;
        int caretColor = UITheme.TEXT_LABEL;

        boolean editing = controller.isEditingVariableField() && controller.getVariableEditingNode() == node;
        if (editing) {
            controller.getVariableEditor().updateCaretBlink();
        }

        int cameraX = host.cameraX();
        int cameraY = host.cameraY();
        int fieldTop = node.getVariableFieldInputTop() - cameraY;
        int fieldHeight = node.getVariableFieldHeight();
        int fieldLeft = node.getVariableFieldLeft() - cameraX;
        int fieldWidth = node.getVariableFieldWidth();
        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);

        int fieldBottom = fieldTop + fieldHeight;
        boolean hovered = !isOverSidebar
            && worldMouseX >= node.getVariableFieldLeft()
            && worldMouseX <= node.getVariableFieldLeft() + fieldWidth
            && worldMouseY >= node.getVariableFieldInputTop()
            && worldMouseY <= node.getVariableFieldInputTop() + fieldHeight;
        float progress = host.textFieldHighlightProgress(node.getId() + "#variable", hovered, editing);
        UIStyleHelper.FieldPalette palette = host.nodeInputPalette(
            isOverSidebar, host.selectedNodeAccentColor(), progress, editing, false);
        int valueColor = isOverSidebar ? (editing ? activeTextColor : textColor)
            : AnimationHelper.lerpColor(textColor, activeTextColor, progress);

        UIStyleHelper.drawFieldFrame(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, palette);

        String value;
        if (editing) {
            value = controller.getVariableEditor().getBuffer();
        } else {
            String keyName = node.getVariableFieldParameterKey();
            NodeParameter variableParam = node.getParameter(keyName);
            value = variableParam != null ? variableParam.getStringValue() : "";
        }
        if (value == null) {
            value = "";
        }

        String display;
        if (!editing && value.isEmpty()) {
            String keyName = node.getVariableFieldParameterKey();
            display = "List".equalsIgnoreCase(keyName) ? "list" : "Label".equalsIgnoreCase(keyName) ? "input" : "variable";
            valueColor = UITheme.TEXT_TERTIARY;
        } else {
            display = value;
        }

        display = editing
            ? display
            : host.trimTextToWidth(display, textRenderer, fieldWidth - 6);
        int variableHighlightColor = UITheme.ACCENT_AMBER;
        Set<String> variableFieldVariableNames = host.collectRuntimeVariableNames(node);
        InlineVariableRender variableFieldRenderData = null;
        if (host.shouldBuildInlineExpressionRender(value, variableFieldVariableNames)) {
            InlineVariableRender candidate = buildInlineVariableRender(
                value, variableFieldVariableNames, valueColor, variableHighlightColor);
            if (editing) {
                variableFieldRenderData = candidate;
                display = variableFieldRenderData.displayText;
            } else if (textRenderer.width(candidate.displayText) <= fieldWidth - 6) {
                variableFieldRenderData = candidate;
                display = variableFieldRenderData.displayText;
            } else if (isSingleKnownInlineVariableReference(value, variableFieldVariableNames)) {
                display = host.trimTextToWidth(candidate.displayText, textRenderer, fieldWidth - 6);
                valueColor = variableHighlightColor;
            }
        }

        int textX = fieldLeft + 3;
        int textY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2 + 1;
        if (editing && controller.getVariableEditor().hasSelection()) {
            int start = controller.getVariableEditor().getSelectionStart();
            int end = controller.getVariableEditor().getSelectionEnd();
            if (variableFieldRenderData != null) {
                start = variableFieldRenderData.toDisplayIndex(start);
                end = variableFieldRenderData.toDisplayIndex(end);
            }
            if (start >= 0 && end >= 0 && start <= display.length() && end <= display.length()) {
                int selectionStartX = textX + textRenderer.width(display.substring(0, start));
                int selectionEndX = textX + textRenderer.width(display.substring(0, end));
                context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldBottom - 2, UITheme.TEXT_SELECTION_BG);
            }
        }
        if (variableFieldRenderData != null && host.shouldRenderNodeText()) {
            variableFieldRenderData.draw(context, textRenderer, textX, textY);
        } else {
            host.drawNodeText(context, textRenderer, Component.literal(display), textX, textY, valueColor);
        }

        if (editing && controller.getVariableEditor().isCaretVisible()) {
            int caretIndex = controller.getVariableEditor().getCaretPosition();
            if (variableFieldRenderData != null) {
                caretIndex = variableFieldRenderData.toDisplayIndex(caretIndex);
            }
            caretIndex = Mth.clamp(caretIndex, 0, display.length());
            int caretX = textX + textRenderer.width(display.substring(0, caretIndex));
            caretX = Math.min(caretX, fieldLeft + fieldWidth - 2);
            UIStyleHelper.drawTextCaret(context, caretX, fieldTop + 2, fieldBottom - 2, caretColor);
        }
    }
}
