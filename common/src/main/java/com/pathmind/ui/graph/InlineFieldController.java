package com.pathmind.ui.graph;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;

final class InlineFieldController {

    interface Host extends InlineTextEditor.Host {
        Font getClientTextRenderer();
        void closeSchematicDropdown();
        void closeRunPresetDropdown();
        void closeRandomRoundingDropdown();
        void stopParameterEditing(boolean commit);
        void stopStickyNoteEditing(boolean commit);
        void cancelScreenCoordinateCapture();
        void notifyNodeParametersChanged(Node node);
        boolean isPresetSelectorNode(Node node);
        boolean isNumericOrVariableReference(String value, Node node, boolean allowDecimal, boolean allowNegative);
        String getStopTargetParameterKey(Node node);
        String getNumberExpressionErrorMessage();
    }

    private final Host host;

    private Node coordinateEditingNode = null;
    private int coordinateEditingAxis = -1;
    private String coordinateEditOriginalValue = "";
    private final InlineTextEditor coordinateEditor;
    private final InlineTextEditor.Policy coordinatePolicy;

    private Node amountEditingNode = null;
    private String amountEditOriginalValue = "";
    private final InlineTextEditor amountEditor;
    private final InlineTextEditor.Policy amountPolicy;

    private Node messageEditingNode = null;
    private int messageEditingIndex = -1;
    private String messageEditOriginalValue = "";
    private final InlineTextEditor messageEditor;
    private final InlineTextEditor.Policy messagePolicy;

    private Node eventNameEditingNode = null;
    private String eventNameEditOriginalValue = "";
    private final InlineTextEditor eventNameEditor;
    private final InlineTextEditor.Policy eventNamePolicy;

    private Node stopTargetEditingNode = null;
    private String stopTargetEditOriginalValue = "";
    private final InlineTextEditor stopTargetEditor;
    private final InlineTextEditor.Policy stopTargetPolicy;

    private Node variableEditingNode = null;
    private String variableEditOriginalValue = "";
    private final InlineTextEditor variableEditor;
    private final InlineTextEditor.Policy variablePolicy;

    InlineFieldController(Host host) {
        this.host = host;
        coordinateEditor = new InlineTextEditor(host);
        coordinatePolicy = new InlineTextEditor.Policy() {
            @Override
            public void onBufferChanged() {
                updateCoordinateFieldContentWidth(host.getClientTextRenderer());
            }

            @Override
            public void onEnter() {
                stopCoordinateEditing(true);
            }

            @Override
            public void onEscape() {
                stopCoordinateEditing(true);
            }
        };
        amountEditor = new InlineTextEditor(host);
        amountPolicy = new InlineTextEditor.Policy() {
            @Override
            public void onBufferChanged() {
                updateAmountFieldContentWidth(host.getClientTextRenderer());
            }

            @Override
            public void onEnter() {
                stopAmountEditing(true);
            }

            @Override
            public void onEscape() {
                stopAmountEditing(true);
            }
        };
        messageEditor = new InlineTextEditor(host);
        messagePolicy = new InlineTextEditor.Policy() {
            @Override
            public void onBufferChanged() {
                updateMessageFieldContentWidth(host.getClientTextRenderer());
            }

            @Override
            public void onEnter() {
                stopMessageEditing(true);
            }

            @Override
            public void onEscape() {
                stopMessageEditing(true);
            }
        };
        eventNameEditor = new InlineTextEditor(host);
        eventNamePolicy = new InlineTextEditor.Policy() {
            @Override
            public void onBufferChanged() {
            }

            @Override
            public void onEnter() {
                stopEventNameEditing(true);
            }

            @Override
            public void onEscape() {
                stopEventNameEditing(true);
            }
        };
        stopTargetEditor = new InlineTextEditor(host);
        stopTargetPolicy = new InlineTextEditor.Policy() {
            @Override
            public void onBufferChanged() {
                updateStopTargetFieldContentWidth(host.getClientTextRenderer());
            }

            @Override
            public void onEnter() {
                stopStopTargetEditing(true);
            }

            @Override
            public void onEscape() {
                stopStopTargetEditing(true);
            }
        };
        variableEditor = new InlineTextEditor(host);
        variablePolicy = new InlineTextEditor.Policy() {
            @Override
            public void onBufferChanged() {
                updateVariableFieldContentWidth(host.getClientTextRenderer());
            }

            @Override
            public void onEnter() {
                stopVariableEditing(true);
            }

            @Override
            public void onEscape() {
                stopVariableEditing(true);
            }
        };
    }

    boolean isEditingCoordinateField() {
        return coordinateEditingNode != null && coordinateEditingAxis >= 0;
    }

    void startCoordinateEditing(Node node, int axisIndex) {
        String[] axes = getCoordinateAxes(node);
        if (node == null || !node.hasCoordinateInputFields() || axisIndex < 0
            || axisIndex >= axes.length) {
            stopCoordinateEditing(false);
            return;
        }

        host.cancelScreenCoordinateCapture();

        host.closeSchematicDropdown();
        host.closeRunPresetDropdown();
        host.closeRandomRoundingDropdown();
        stopAmountEditing(true);
        stopStopTargetEditing(true);
        stopVariableEditing(true);
        stopMessageEditing(true);
        host.stopParameterEditing(true);
        stopEventNameEditing(true);

        if (isEditingCoordinateField()) {
            if (coordinateEditingNode == node && coordinateEditingAxis == axisIndex) {
                return;
            }
            boolean changed = applyCoordinateEdit();
            if (changed) {
                host.notifyNodeParametersChanged(coordinateEditingNode);
            }
        }

        coordinateEditingNode = node;
        coordinateEditingAxis = axisIndex;

        NodeParameter parameter = getCoordinateParameter(node, axisIndex);
        coordinateEditOriginalValue = parameter != null ? parameter.getStringValue() : "";
        coordinateEditor.begin(coordinateEditOriginalValue, coordinateEditOriginalValue.length());
        updateCoordinateFieldContentWidth(host.getClientTextRenderer());
    }

    void stopCoordinateEditing(boolean commit) {
        if (!isEditingCoordinateField()) {
            return;
        }

        boolean changed = false;
        if (commit) {
            changed = applyCoordinateEdit();
        } else {
            revertCoordinateEdit();
        }

        if (commit && changed) {
            host.notifyNodeParametersChanged(coordinateEditingNode);
        }

        coordinateEditingNode = null;
        coordinateEditingAxis = -1;
        coordinateEditOriginalValue = "";
        coordinateEditor.clear();
    }

    private boolean applyCoordinateEdit() {
        if (!isEditingCoordinateField()) {
            return false;
        }
        String value = coordinateEditor.getBuffer().trim();
        if (!value.isEmpty() && !"-".equals(value)
            && !host.isNumericOrVariableReference(value, coordinateEditingNode, false, true)) {
            coordinateEditingNode.sendNodeErrorMessageToPlayer(host.getNumberExpressionErrorMessage());
            return false;
        }
        if (value.isEmpty() || "-".equals(value)) {
            value = "0";
        }
        String axisName = getCoordinateAxes(coordinateEditingNode)[coordinateEditingAxis];
        NodeParameter parameter = getCoordinateParameter(coordinateEditingNode, coordinateEditingAxis);
        String previous = parameter != null ? parameter.getStringValue() : "";
        coordinateEditingNode.setParameterValueAndPropagate(axisName, value);
        coordinateEditingNode.recalculateDimensions();
        return !Objects.equals(previous, value);
    }

    private void revertCoordinateEdit() {
        if (!isEditingCoordinateField()) {
            return;
        }
        String axisName = getCoordinateAxes(coordinateEditingNode)[coordinateEditingAxis];
        coordinateEditingNode.setParameterValueAndPropagate(axisName, coordinateEditOriginalValue);
        coordinateEditingNode.recalculateDimensions();
    }

    private NodeParameter getCoordinateParameter(Node node, int axisIndex) {
        String[] axes = getCoordinateAxes(node);
        if (node == null || axisIndex < 0 || axisIndex >= axes.length) {
            return null;
        }
        return node.getParameter(axes[axisIndex]);
    }

    boolean handleCoordinateKeyPressed(int keyCode, int modifiers) {
        if (!isEditingCoordinateField()) {
            return false;
        }
        // Coordinate-specific keys the shared engine does not handle:
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            Node node = coordinateEditingNode;
            int direction = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
            int axisCount = getCoordinateAxes(node).length;
            int nextAxis = (coordinateEditingAxis + direction + axisCount) % axisCount;
            startCoordinateEditing(node, nextAxis);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_V && host.isTextShortcutDown(modifiers)) {
            // Smart paste: fills multiple axes from "x, y, z" clipboard content.
            Font textRenderer = host.getClientTextRenderer();
            if (textRenderer != null) {
                smartPasteCoordinates(host.getClipboardText(), textRenderer);
            }
            return true;
        }
        return coordinateEditor.handleKeyPressed(keyCode, modifiers, coordinatePolicy);
    }

    boolean handleCoordinateCharTyped(char chr) {
        if (!isEditingCoordinateField()) {
            return false;
        }
        return coordinateEditor.handleCharTyped(chr, coordinatePolicy);
    }

    boolean isEditingAmountField() {
        return amountEditingNode != null;
    }

    void startAmountEditing(Node node) {
        if (node == null || !node.hasAmountInputField() || !node.isAmountInputEnabled()) {
            stopAmountEditing(false);
            return;
        }

        host.closeSchematicDropdown();
        host.closeRunPresetDropdown();
        if (isEditingAmountField()) {
            if (amountEditingNode == node) {
                return;
            }
            boolean changed = applyAmountEdit();
            if (changed) {
                host.notifyNodeParametersChanged(amountEditingNode);
            }
        }

        stopCoordinateEditing(true);
        stopStopTargetEditing(true);
        stopVariableEditing(true);
        stopMessageEditing(true);
        host.stopStickyNoteEditing(true);
        host.stopParameterEditing(true);
        stopEventNameEditing(true);

        amountEditingNode = node;
        String amountKey = node.getAmountParameterKey();
        NodeParameter amountParam = node.getParameter(amountKey);
        String initialBuffer = amountParam != null ? amountParam.getStringValue() : "";
        if (node.getType() == NodeType.MOVE_ITEM && isMoveItemAllAmountValue(initialBuffer)) {
            initialBuffer = "";
        }
        amountEditOriginalValue = initialBuffer;
        amountEditor.begin(initialBuffer, initialBuffer.length());
        updateAmountFieldContentWidth(host.getClientTextRenderer());
    }

    void stopAmountEditing(boolean commit) {
        if (!isEditingAmountField()) {
            return;
        }

        boolean changed = false;
        if (commit) {
            changed = applyAmountEdit();
        } else {
            revertAmountEdit();
        }

        if (commit && changed) {
            host.notifyNodeParametersChanged(amountEditingNode);
        }

        amountEditingNode = null;
        amountEditOriginalValue = "";
        amountEditor.clear();
    }

    private boolean applyAmountEdit() {
        if (!isEditingAmountField()) {
            return false;
        }

        String value = amountEditor.getBuffer().trim();
        if ((amountEditingNode.getType() == NodeType.PARAM_DURATION
            || amountEditingNode.getType() == NodeType.USE
            || amountEditingNode.getType() == NodeType.SWING)
            && !value.startsWith("$")) {
            // Accept locale decimal input like "1,5" for duration-style fields.
            value = value.replace(',', '.');
        }
        if (amountEditingNode.getType() == NodeType.MOVE_ITEM && isMoveItemAllAmountValue(value)) {
            value = "0";
        }
        if (amountEditingNode.getType() != NodeType.PARAM_DURATION
            && amountEditingNode.getType() != NodeType.USE
            && amountEditingNode.getType() != NodeType.SWING
            && !value.isEmpty()
            && !host.isNumericOrVariableReference(value, amountEditingNode, true, false)) {
            amountEditingNode.sendNodeErrorMessageToPlayer(host.getNumberExpressionErrorMessage());
            return false;
        }
        if (value.isEmpty()) {
            if (amountEditingNode.getType() == NodeType.MOVE_ITEM) {
                value = "0";
            } else if (amountEditingNode.getType() == NodeType.TRADE
                || amountEditingNode.getType() == NodeType.SENSOR_VILLAGER_TRADE
                || amountEditingNode.getType() == NodeType.SENSOR_IN_STOCK) {
                value = "1";
            } else if (amountEditingNode.getType() == NodeType.PARAM_DURATION
                || amountEditingNode.getType() == NodeType.USE
                || amountEditingNode.getType() == NodeType.SWING) {
                value = "";
            } else {
                value = amountEditOriginalValue != null && !amountEditOriginalValue.isEmpty()
                    ? amountEditOriginalValue
                    : "0";
            }
        }

        String amountKey = amountEditingNode.getAmountParameterKey();
        NodeParameter amountParam = amountEditingNode.getParameter(amountKey);
        String previous = amountParam != null ? amountParam.getStringValue() : "";
        amountEditingNode.setParameterValueAndPropagate(amountKey, value);
        amountEditingNode.recalculateDimensions();
        return !Objects.equals(previous, value);
    }

    private void revertAmountEdit() {
        if (!isEditingAmountField()) {
            return;
        }
        String amountKey = amountEditingNode.getAmountParameterKey();
        amountEditingNode.setParameterValueAndPropagate(amountKey, amountEditOriginalValue);
        amountEditingNode.recalculateDimensions();
    }

    boolean handleAmountKeyPressed(int keyCode, int modifiers) {
        if (!isEditingAmountField()) {
            return false;
        }
        return amountEditor.handleKeyPressed(keyCode, modifiers, amountPolicy);
    }

    boolean handleAmountCharTyped(char chr) {
        if (!isEditingAmountField()) {
            return false;
        }
        return amountEditor.handleCharTyped(chr, amountPolicy);
    }

    boolean isEditingStopTargetField() {
        return stopTargetEditingNode != null;
    }

    void startStopTargetEditing(Node node) {
        if (node == null || !node.hasStopTargetInputField() || host.isPresetSelectorNode(node)) {
            stopStopTargetEditing(false);
            return;
        }

        host.closeSchematicDropdown();
        host.closeRunPresetDropdown();
        if (isEditingStopTargetField()) {
            if (stopTargetEditingNode == node) {
                return;
            }
            boolean changed = applyStopTargetEdit();
            if (changed) {
                host.notifyNodeParametersChanged(stopTargetEditingNode);
            }
        }

        stopCoordinateEditing(true);
        stopAmountEditing(true);
        stopMessageEditing(true);
        host.stopParameterEditing(true);
        stopEventNameEditing(true);
        stopVariableEditing(true);
        host.stopStickyNoteEditing(true);

        stopTargetEditingNode = node;
        NodeParameter targetParam = node.getParameter(host.getStopTargetParameterKey(node));
        stopTargetEditOriginalValue = targetParam != null ? targetParam.getStringValue() : "";
        stopTargetEditor.begin(stopTargetEditOriginalValue, stopTargetEditOriginalValue.length());
        updateStopTargetFieldContentWidth(host.getClientTextRenderer());
    }

    void stopStopTargetEditing(boolean commit) {
        if (!isEditingStopTargetField()) {
            return;
        }

        boolean changed = false;
        if (commit) {
            changed = applyStopTargetEdit();
        } else {
            revertStopTargetEdit();
        }

        if (commit && changed) {
            host.notifyNodeParametersChanged(stopTargetEditingNode);
        }

        stopTargetEditingNode = null;
        stopTargetEditOriginalValue = "";
        stopTargetEditor.clear();
    }

    private boolean applyStopTargetEdit() {
        if (!isEditingStopTargetField()) {
            return false;
        }

        String value = stopTargetEditor.getBuffer().trim();
        if (!host.isPresetSelectorNode(stopTargetEditingNode)
            && !value.isEmpty()
            && !host.isNumericOrVariableReference(value, stopTargetEditingNode, false, false)) {
            stopTargetEditingNode.sendNodeErrorMessageToPlayer(host.getNumberExpressionErrorMessage());
            return false;
        }
        String keyName = host.getStopTargetParameterKey(stopTargetEditingNode);
        NodeParameter targetParam = stopTargetEditingNode.getParameter(keyName);
        String previous = targetParam != null ? targetParam.getStringValue() : "";
        stopTargetEditingNode.setParameterValueAndPropagate(keyName, value);
        stopTargetEditingNode.recalculateDimensions();
        return !Objects.equals(previous, value);
    }

    private void revertStopTargetEdit() {
        if (!isEditingStopTargetField()) {
            return;
        }
        stopTargetEditingNode.setParameterValueAndPropagate(
            host.getStopTargetParameterKey(stopTargetEditingNode),
            stopTargetEditOriginalValue
        );
        stopTargetEditingNode.recalculateDimensions();
    }

    boolean handleStopTargetKeyPressed(int keyCode, int modifiers) {
        if (!isEditingStopTargetField()) {
            return false;
        }
        return stopTargetEditor.handleKeyPressed(keyCode, modifiers, stopTargetPolicy);
    }

    boolean handleStopTargetCharTyped(char chr) {
        if (!isEditingStopTargetField()) {
            return false;
        }
        return stopTargetEditor.handleCharTyped(chr, stopTargetPolicy);
    }

    boolean isEditingVariableField() {
        return variableEditingNode != null;
    }

    void startVariableEditing(Node node) {
        if (node == null || !node.hasVariableInputField()) {
            stopVariableEditing(false);
            return;
        }

        host.closeSchematicDropdown();
        host.closeRunPresetDropdown();
        if (isEditingVariableField()) {
            if (variableEditingNode == node) {
                return;
            }
            boolean changed = applyVariableEdit();
            if (changed) {
                host.notifyNodeParametersChanged(variableEditingNode);
            }
        }

        stopCoordinateEditing(true);
        stopAmountEditing(true);
        stopStopTargetEditing(true);
        stopMessageEditing(true);
        host.stopParameterEditing(true);
        stopEventNameEditing(true);
        host.stopStickyNoteEditing(true);

        variableEditingNode = node;
        String keyName = node.getVariableFieldParameterKey();
        NodeParameter variableParam = node.getParameter(keyName);
        variableEditOriginalValue = variableParam != null ? variableParam.getStringValue() : "";
        variableEditor.begin(variableEditOriginalValue, variableEditOriginalValue.length());
        updateVariableFieldContentWidth(host.getClientTextRenderer());
    }

    void stopVariableEditing(boolean commit) {
        if (!isEditingVariableField()) {
            return;
        }

        boolean changed = false;
        if (commit) {
            changed = applyVariableEdit();
        } else {
            revertVariableEdit();
        }

        if (commit && changed) {
            host.notifyNodeParametersChanged(variableEditingNode);
        }

        variableEditingNode = null;
        variableEditOriginalValue = "";
        variableEditor.clear();
    }

    private boolean applyVariableEdit() {
        if (!isEditingVariableField()) {
            return false;
        }

        String value = variableEditor.getBuffer();
        String keyName = variableEditingNode.getVariableFieldParameterKey();
        NodeParameter variableParam = variableEditingNode.getParameter(keyName);
        String previous = variableParam != null ? variableParam.getStringValue() : "";
        variableEditingNode.setParameterValueAndPropagate(keyName, value);
        variableEditingNode.recalculateDimensions();
        return !Objects.equals(previous, value);
    }

    private void revertVariableEdit() {
        if (!isEditingVariableField()) {
            return;
        }
        String keyName = variableEditingNode.getVariableFieldParameterKey();
        variableEditingNode.setParameterValueAndPropagate(keyName, variableEditOriginalValue);
        variableEditingNode.recalculateDimensions();
    }

    boolean handleVariableKeyPressed(int keyCode, int modifiers) {
        if (!isEditingVariableField()) {
            return false;
        }
        return variableEditor.handleKeyPressed(keyCode, modifiers, variablePolicy);
    }

    boolean handleVariableCharTyped(char chr) {
        if (!isEditingVariableField()) {
            return false;
        }
        return variableEditor.handleCharTyped(chr, variablePolicy);
    }

    boolean isEditingMessageField() {
        return messageEditingNode != null && messageEditingIndex >= 0;
    }

    void startMessageEditing(Node node, int index) {
        if (node == null || !node.hasMessageInputFields() || index < 0 || index >= node.getMessageFieldCount()) {
            stopMessageEditing(false);
            return;
        }

        host.closeSchematicDropdown();
        host.closeRunPresetDropdown();
        if (isEditingMessageField()) {
            if (messageEditingNode == node && messageEditingIndex == index) {
                return;
            }
            boolean changed = applyMessageEdit();
            if (changed) {
                host.notifyNodeParametersChanged(messageEditingNode);
            }
        }

        stopCoordinateEditing(true);
        stopAmountEditing(true);
        stopStopTargetEditing(true);
        stopVariableEditing(true);
        stopEventNameEditing(true);
        host.stopParameterEditing(true);

        messageEditingNode = node;
        messageEditingIndex = index;
        messageEditOriginalValue = node.getMessageLine(index);
        messageEditor.begin(messageEditOriginalValue, messageEditOriginalValue.length());
        updateMessageFieldContentWidth(host.getClientTextRenderer());
    }

    void stopMessageEditing(boolean commit) {
        if (!isEditingMessageField()) {
            return;
        }

        boolean changed = false;
        if (commit) {
            changed = applyMessageEdit();
        } else {
            revertMessageEdit();
        }

        if (commit && changed) {
            host.notifyNodeParametersChanged(messageEditingNode);
        }

        messageEditingNode = null;
        messageEditingIndex = -1;
        messageEditOriginalValue = "";
        messageEditor.clear();
    }

    private boolean applyMessageEdit() {
        if (!isEditingMessageField()) {
            return false;
        }
        String value = messageEditor.getBuffer();
        String previous = messageEditingNode.getMessageLine(messageEditingIndex);
        messageEditingNode.setMessageLine(messageEditingIndex, value);
        messageEditingNode.recalculateDimensions();
        return !Objects.equals(previous, value);
    }

    private void revertMessageEdit() {
        if (!isEditingMessageField()) {
            return;
        }
        messageEditingNode.setMessageLine(messageEditingIndex, messageEditOriginalValue);
        messageEditingNode.recalculateDimensions();
    }

    boolean handleMessageKeyPressed(int keyCode, int modifiers) {
        if (!isEditingMessageField()) {
            return false;
        }
        return messageEditor.handleKeyPressed(keyCode, modifiers, messagePolicy);
    }

    boolean handleMessageCharTyped(char chr) {
        if (!isEditingMessageField()) {
            return false;
        }
        return messageEditor.handleCharTyped(chr, messagePolicy);
    }

    boolean isEditingEventNameField() {
        return eventNameEditingNode != null;
    }

    void startEventNameEditing(Node node) {
        if (node == null || (node.getType() != NodeType.EVENT_FUNCTION && node.getType() != NodeType.EVENT_CALL && node.getType() != NodeType.ROUTINE_ENTRY)) {
            stopEventNameEditing(false);
            return;
        }

        host.closeSchematicDropdown();
        if (isEditingEventNameField()) {
            if (eventNameEditingNode == node) {
                return;
            }
            boolean changed = applyEventNameEdit();
            if (changed) {
                host.notifyNodeParametersChanged(eventNameEditingNode);
            }
        }

        stopCoordinateEditing(true);
        stopAmountEditing(true);
        stopStopTargetEditing(true);
        stopVariableEditing(true);
        stopMessageEditing(true);
        stopEventNameEditing(true);
        host.stopParameterEditing(true);
        host.stopStickyNoteEditing(true);

        eventNameEditingNode = node;
        NodeParameter nameParam = node.getParameter("Name");
        eventNameEditOriginalValue = nameParam != null ? nameParam.getStringValue() : "";
        eventNameEditor.begin(eventNameEditOriginalValue, eventNameEditOriginalValue.length());
    }

    void stopEventNameEditing(boolean commit) {
        if (!isEditingEventNameField()) {
            return;
        }

        boolean changed = false;
        if (commit) {
            changed = applyEventNameEdit();
        } else {
            revertEventNameEdit();
        }

        if (commit && changed) {
            host.notifyNodeParametersChanged(eventNameEditingNode);
        }

        eventNameEditingNode = null;
        eventNameEditOriginalValue = "";
        eventNameEditor.clear();
    }

    private boolean applyEventNameEdit() {
        if (!isEditingEventNameField()) {
            return false;
        }
        String value = eventNameEditor.getBuffer();
        NodeParameter nameParam = eventNameEditingNode.getParameter("Name");
        String previous = nameParam != null ? nameParam.getStringValue() : "";
        eventNameEditingNode.setParameterValueAndPropagate("Name", value);
        eventNameEditingNode.recalculateDimensions();
        return !Objects.equals(previous, value);
    }

    private void revertEventNameEdit() {
        if (!isEditingEventNameField()) {
            return;
        }
        eventNameEditingNode.setParameterValueAndPropagate("Name", eventNameEditOriginalValue);
        eventNameEditingNode.recalculateDimensions();
    }

    boolean handleEventNameKeyPressed(int keyCode, int modifiers) {
        if (!isEditingEventNameField()) {
            return false;
        }
        return eventNameEditor.handleKeyPressed(keyCode, modifiers, eventNamePolicy);
    }

    boolean handleEventNameCharTyped(char chr) {
        if (!isEditingEventNameField()) {
            return false;
        }
        return eventNameEditor.handleCharTyped(chr, eventNamePolicy);
    }

    private void smartPasteCoordinates(String clipboardText, Font textRenderer) {
        if (!isEditingCoordinateField() || textRenderer == null || clipboardText == null || clipboardText.isEmpty()) {
            return;
        }

        // Try to parse as multi-coordinate format (e.g., "2313 123 -32131" or "100,200,300")
        String[] parts = clipboardText.trim().split("[\\s,]+");
        String[] axes = getCoordinateAxes(coordinateEditingNode);
        if (parts.length == axes.length) {
            String[] parsedCoords = new String[axes.length];
            boolean allValid = true;

            for (int i = 0; i < axes.length; i++) {
                String trimmed = parts[i].trim();
                // Remove any non-digit/minus characters
                StringBuilder cleaned = new StringBuilder();
                for (int j = 0; j < trimmed.length(); j++) {
                    char c = trimmed.charAt(j);
                    if (Character.isDigit(c) || (c == '-' && j == 0)) {
                        cleaned.append(c);
                    }
                }

                String value = cleaned.toString();
                if (value.isEmpty() || !isValidCoordinateValue(value)) {
                    allValid = false;
                    break;
                }
                parsedCoords[i] = value;
            }

            // If we successfully parsed all three coordinates, apply them
            if (allValid) {
                // Save reference to node before stopping editing
                Node node = coordinateEditingNode;
                if (node == null) {
                    return;
                }

                // Stop current coordinate editing
                stopCoordinateEditing(false);

                for (int i = 0; i < axes.length; i++) {
                    node.setParameterValueAndPropagate(axes[i], parsedCoords[i]);
                }
                node.recalculateDimensions();
                host.notifyNodeParametersChanged(node);
                return;
            }
        }

        // Fall back to single-axis paste
        insertCoordinateText(clipboardText, textRenderer);
    }

    private boolean insertCoordinateText(String text, Font textRenderer) {
        if (!isEditingCoordinateField() || textRenderer == null) {
            return false;
        }
        return coordinateEditor.insert(text, coordinatePolicy);
    }

    private boolean isValidCoordinateValue(String value) {
        if (value.isEmpty()) {
            return true;
        }
        if ("-".equals(value)) {
            return true;
        }
        int startIndex = 0;
        if (value.charAt(0) == '-') {
            startIndex = 1;
            if (startIndex >= value.length()) {
                return false;
            }
        }
        for (int i = startIndex; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isMoveItemAllAmountValue(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty()
            || "0".equals(trimmed)
            || "all".equalsIgnoreCase(trimmed)
            || "any".equalsIgnoreCase(trimmed);
    }

    private String[] getCoordinateAxes(Node node) {
        if (node == null) {
            return new String[0];
        }
        return node.getCoordinateFieldAxes();
    }

    void updateMessageFieldContentWidth(Font textRenderer) {
        if (!isEditingMessageField() || messageEditingNode == null || textRenderer == null) {
            return;
        }
        int maxWidth = 0;
        int fieldCount = messageEditingNode.getMessageFieldCount();
        for (int i = 0; i < fieldCount; i++) {
            String line = i == messageEditingIndex ? messageEditor.getBuffer() : messageEditingNode.getMessageLine(i);
            if (line == null) {
                line = "";
            }
            maxWidth = Math.max(maxWidth, textRenderer.width(line));
        }
        messageEditingNode.setMessageFieldTextWidth(maxWidth);
        messageEditingNode.recalculateDimensions();
    }

    void updateCoordinateFieldContentWidth(Font textRenderer) {
        if (!isEditingCoordinateField() || coordinateEditingNode == null || textRenderer == null) {
            return;
        }
        int maxWidth = 0;
        String[] axes = getCoordinateAxes(coordinateEditingNode);
        for (int i = 0; i < axes.length; i++) {
            NodeParameter parameter = getCoordinateParameter(coordinateEditingNode, i);
            String value = i == coordinateEditingAxis ? coordinateEditor.getBuffer() : (parameter != null ? parameter.getStringValue() : "");
            if (value == null) {
                value = "";
            }
            maxWidth = Math.max(maxWidth, textRenderer.width(value));
        }
        coordinateEditingNode.setCoordinateFieldTextWidth(maxWidth);
        coordinateEditingNode.recalculateDimensions();
    }

    void updateAmountFieldContentWidth(Font textRenderer) {
        if (!isEditingAmountField() || amountEditingNode == null || textRenderer == null) {
            return;
        }
        String value = amountEditor.getBuffer();
        amountEditingNode.setAmountFieldTextWidth(textRenderer.width(value));
        amountEditingNode.recalculateDimensions();
    }

    void updateStopTargetFieldContentWidth(Font textRenderer) {
        if (!isEditingStopTargetField() || stopTargetEditingNode == null || textRenderer == null) {
            return;
        }
        String value = stopTargetEditor.getBuffer();
        stopTargetEditingNode.setStopTargetFieldTextWidth(textRenderer.width(value));
        stopTargetEditingNode.recalculateDimensions();
    }

    void updateVariableFieldContentWidth(Font textRenderer) {
        if (!isEditingVariableField() || variableEditingNode == null || textRenderer == null) {
            return;
        }
        String value = variableEditor.getBuffer();
        variableEditingNode.setVariableFieldTextWidth(textRenderer.width(value));
        variableEditingNode.recalculateDimensions();
    }

    void replaceStopTargetEditValue(String value) {
        stopTargetEditOriginalValue = value;
        stopTargetEditor.begin(value, value.length());
    }

    Node getCoordinateEditingNode() { return coordinateEditingNode; }
    int getCoordinateEditingAxis() { return coordinateEditingAxis; }
    InlineTextEditor getCoordinateEditor() { return coordinateEditor; }
    Node getAmountEditingNode() { return amountEditingNode; }
    InlineTextEditor getAmountEditor() { return amountEditor; }
    Node getMessageEditingNode() { return messageEditingNode; }
    int getMessageEditingIndex() { return messageEditingIndex; }
    InlineTextEditor getMessageEditor() { return messageEditor; }
    Node getEventNameEditingNode() { return eventNameEditingNode; }
    InlineTextEditor getEventNameEditor() { return eventNameEditor; }
    Node getStopTargetEditingNode() { return stopTargetEditingNode; }
    InlineTextEditor getStopTargetEditor() { return stopTargetEditor; }
    Node getVariableEditingNode() { return variableEditingNode; }
    InlineTextEditor getVariableEditor() { return variableEditor; }
}
