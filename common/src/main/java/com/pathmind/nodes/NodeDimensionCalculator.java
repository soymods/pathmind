package com.pathmind.nodes;

/**
 * Editor sizing rules for nodes.
 *
 * <p>Node remains the compatibility API while this class owns the width/height
 * calculation during the GUI/runtime split.
 */
final class NodeDimensionCalculator {
    private NodeDimensionCalculator() {
    }

    static boolean recalculate(Node node, NodeLayoutState layoutState) {
        NodeType type = node.getType();
        if (type == NodeType.START) {
            layoutState.setSize(Node.START_END_SIZE, Node.START_END_SIZE);
            return false;
        }
        if (node.isStickyNote()) {
            layoutState.setSize(
                Math.max(Node.STICKY_NOTE_MIN_WIDTH, layoutState.getStickyNoteWidthOverride()),
                Math.max(Node.STICKY_NOTE_MIN_HEIGHT, layoutState.getStickyNoteHeightOverride()));
            return false;
        }
        if (type == NodeType.TEMPLATE) {
            layoutState.setSize(Node.TEMPLATE_NODE_WIDTH, Node.TEMPLATE_NODE_HEIGHT);
            return false;
        }

        node.normalizeAttributeDetectionParameters();

        int computedNodeWidth = computeWidth(node, layoutState);
        int computedNodeHeight = computeHeight(node);
        layoutState.setSize(computedNodeWidth, computedNodeHeight);
        return true;
    }

    private static int computeWidth(Node node, NodeLayoutState layoutState) {
        NodeType type = node.getType();
        int baseTextLength;
        if (type == NodeType.ROUTINE_INPUT) {
            baseTextLength = NodeType.VARIABLE.getDisplayName().length();
        } else if (type == NodeType.ROUTINE_ENTRY || type == NodeType.ROUTINE_CALL) {
            baseTextLength = node.getDisplayName().getString().length();
        } else {
            baseTextLength = type.getDisplayName().length();
        }
        int maxTextLength = Math.max(baseTextLength, 1);
        if (node.isInlineParameterNode() || node.shouldRenderInlineParameters()) {
            for (NodeParameter param : node.getParameters()) {
                String paramText = node.getParameterWidthLabel(param);
                if (paramText == null || paramText.isEmpty()) {
                    continue;
                }
                if (paramText.length() > maxTextLength) {
                    maxTextLength = paramText.length();
                }
            }

            if (node.supportsModeSelection()) {
                String modeLabel = node.getModeDisplayLabel();
                if (!modeLabel.isEmpty()) {
                    maxTextLength = Math.max(maxTextLength, modeLabel.length());
                }
            }
        }

        int computedWidth = maxTextLength * Node.CHAR_PIXEL_WIDTH + 24;
        if (node.isInlineParameterNode() || node.shouldRenderInlineParameters()) {
            computedWidth = applyInlineParameterWidth(node, layoutState, computedWidth);
        }
        if (type == NodeType.PARAM_BOOLEAN) {
            computedWidth = Math.max(computedWidth, 132);
        }
        if (node.hasParameterSlot()) {
            computedWidth = applyParameterSlotWidth(node, layoutState, computedWidth);
        }
        if (node.showsModeFieldAboveParameterSlot() && !node.hasParameterSlot()) {
            int modeWidth = node.getModeDisplayLabel().length() * Node.CHAR_PIXEL_WIDTH
                + 24
                + 2 * Node.PARAMETER_SLOT_MARGIN_HORIZONTAL;
            computedWidth = Math.max(computedWidth, modeWidth);
        }
        if (node.hasCoordinateInputFields()) {
            int coordinateWidth = node.getCoordinateFieldTotalWidth() + 2 * Node.PARAMETER_SLOT_MARGIN_HORIZONTAL;
            computedWidth = Math.max(computedWidth, coordinateWidth);
        }
        if (node.hasAmountInputField()) {
            computedWidth = applyAmountFieldWidth(node, layoutState, computedWidth);
        }
        if (node.hasRandomRoundingField()) {
            int requiredWidth = 40 + 10;
            if (node.hasRandomRoundingToggle()) {
                requiredWidth += Node.RANDOM_ROUNDING_TOGGLE_WIDTH + Node.RANDOM_ROUNDING_TOGGLE_SPACING;
            }
            computedWidth = Math.max(computedWidth, requiredWidth);
        }
        if (node.hasSensorSlot()) {
            Node sensor = node.getAttachedSensor();
            int sensorContentWidth = Node.SENSOR_SLOT_MIN_CONTENT_WIDTH;
            if (sensor != null) {
                sensorContentWidth = Math.max(sensorContentWidth, sensor.getWidth());
            }
            int requiredWidth = sensorContentWidth + 2 * (Node.SENSOR_SLOT_INNER_PADDING + Node.SENSOR_SLOT_MARGIN_HORIZONTAL);
            computedWidth = Math.max(computedWidth, requiredWidth);
        }
        if (node.hasActionSlot()) {
            Node actionNode = node.getAttachedActionNode();
            int actionContentWidth = Node.ACTION_SLOT_MIN_CONTENT_WIDTH;
            if (actionNode != null) {
                actionContentWidth = Math.max(actionContentWidth, actionNode.getWidth());
            }
            int requiredWidth = actionContentWidth + 2 * (Node.ACTION_SLOT_INNER_PADDING + Node.ACTION_SLOT_MARGIN_HORIZONTAL);
            computedWidth = Math.max(computedWidth, requiredWidth);
        }
        if (node.hasStopTargetInputField()) {
            int fieldMinimum = type == NodeType.RUN_PRESET ? Node.RUN_PRESET_FIELD_MIN_WIDTH : Node.STOP_TARGET_FIELD_MIN_WIDTH;
            int requiredWidth = Math.max(fieldMinimum, layoutState.getStopTargetFieldWidthOverride())
                + 2 * Node.STOP_TARGET_FIELD_MARGIN_HORIZONTAL;
            computedWidth = Math.max(computedWidth, requiredWidth);
        }
        if (node.hasVariableInputField() || type == NodeType.ROUTINE_INPUT) {
            int requiredWidth = Math.max(Node.VARIABLE_FIELD_MIN_WIDTH, layoutState.getVariableFieldWidthOverride())
                + 2 * Node.VARIABLE_FIELD_MARGIN_HORIZONTAL;
            computedWidth = Math.max(computedWidth, requiredWidth);
        }
        if (node.hasMessageInputFields()) {
            computedWidth = applyMessageFieldWidth(node, layoutState, computedWidth);
        }
        if (node.hasBookTextInput()) {
            int bookTextWidth = Node.BOOK_TEXT_BUTTON_MIN_WIDTH + 2 * Node.BOOK_TEXT_BUTTON_MARGIN_HORIZONTAL;
            computedWidth = Math.max(computedWidth, bookTextWidth);
        }
        if (node.hasPopupEditButton()) {
            int editButtonWidth = Node.POPUP_EDIT_BUTTON_MIN_WIDTH + 2 * Node.POPUP_EDIT_BUTTON_MARGIN_HORIZONTAL;
            computedWidth = Math.max(computedWidth, editButtonWidth);
        }
        int minWidth = node.usesMinimalNodePresentation() ? 70 : Node.MIN_WIDTH;
        return Math.max(minWidth, computedWidth);
    }

    private static int applyInlineParameterWidth(Node node, NodeLayoutState layoutState, int computedWidth) {
        int maxParameterWidth = 0;
        for (NodeParameter param : node.getParameters()) {
            if (param == null) {
                continue;
            }
            String widthLabel = node.getParameterWidthLabel(param);
            if (widthLabel.isEmpty()) {
                continue;
            }
            String label = node.getParameterDisplayName(param);
            String value = node.getParameterWidthDisplayValue(param);
            int labelLength = label != null ? label.length() : 0;
            int valueLength = value != null ? value.length() : 0;
            int estimatedWidth = (labelLength + valueLength) * Node.CHAR_PIXEL_WIDTH + Node.PARAMETER_FIELD_PADDING;
            maxParameterWidth = Math.max(maxParameterWidth, estimatedWidth);
        }
        if (node.supportsModeSelection()) {
            String modeLabel = node.getModeDisplayLabel();
            if (!modeLabel.isEmpty()) {
                maxParameterWidth = Math.max(maxParameterWidth, modeLabel.length() * Node.CHAR_PIXEL_WIDTH + Node.PARAMETER_FIELD_PADDING);
            }
        }
        int requiredFieldWidth = maxParameterWidth;
        if (layoutState.getParameterFieldWidthOverride() > 0) {
            requiredFieldWidth = Math.max(requiredFieldWidth, layoutState.getParameterFieldWidthOverride());
        }
        if (requiredFieldWidth > 0) {
            computedWidth = Math.max(computedWidth, requiredFieldWidth + 10);
        }
        return computedWidth;
    }

    private static int applyParameterSlotWidth(Node node, NodeLayoutState layoutState, int computedWidth) {
        int parameterContentWidth = Node.PARAMETER_SLOT_MIN_CONTENT_WIDTH;
        for (Node parameterNode : node.getAttachedParameterNodes()) {
            if (parameterNode != null) {
                parameterContentWidth = Math.max(parameterContentWidth, parameterNode.getWidth());
            }
        }
        if (node.isComparisonOperator()) {
            if (node.isExpandableBooleanOperator()) {
                int requiredWidth = parameterContentWidth + 2 * (Node.PARAMETER_SLOT_INNER_PADDING + Node.PARAMETER_SLOT_MARGIN_HORIZONTAL);
                return Math.max(computedWidth, requiredWidth);
            }
            int slotWidth = parameterContentWidth + 2 * Node.PARAMETER_SLOT_INNER_PADDING;
            int requiredWidth = (slotWidth * 2) + Node.OPERATOR_SLOT_GAP + 2 * Node.PARAMETER_SLOT_MARGIN_HORIZONTAL;
            return Math.max(computedWidth, requiredWidth);
        }

        int requiredWidth = parameterContentWidth + 2 * (Node.PARAMETER_SLOT_INNER_PADDING + Node.PARAMETER_SLOT_MARGIN_HORIZONTAL);
        computedWidth = Math.max(computedWidth, requiredWidth);
        if (node.hasCoordinateInputFields()) {
            int coordinateWidth = node.getCoordinateFieldTotalWidth() + 2 * Node.PARAMETER_SLOT_MARGIN_HORIZONTAL;
            computedWidth = Math.max(computedWidth, coordinateWidth);
        }
        if (node.hasAmountInputField()) {
            computedWidth = applyAmountFieldWidth(node, layoutState, computedWidth);
        }
        return computedWidth;
    }

    private static int applyAmountFieldWidth(Node node, NodeLayoutState layoutState, int computedWidth) {
        int amountContentWidth = Math.max(Node.PARAMETER_SLOT_MIN_CONTENT_WIDTH, layoutState.getAmountFieldWidthOverride());
        if (node.hasAmountToggle()) {
            amountContentWidth += Node.AMOUNT_TOGGLE_WIDTH + Node.AMOUNT_TOGGLE_SPACING;
        }
        int amountWidth = amountContentWidth + 2 * Node.PARAMETER_SLOT_MARGIN_HORIZONTAL;
        return Math.max(computedWidth, amountWidth);
    }

    private static int applyMessageFieldWidth(Node node, NodeLayoutState layoutState, int computedWidth) {
        int maxMessageLength = 0;
        for (String line : node.getMessageLines()) {
            if (line != null) {
                maxMessageLength = Math.max(maxMessageLength, line.length());
            }
        }
        int messageContentWidth = Math.max(
            Node.MESSAGE_FIELD_MIN_CONTENT_WIDTH,
            maxMessageLength * Node.CHAR_PIXEL_WIDTH + (Node.MESSAGE_FIELD_TEXT_PADDING * 2)
        );
        if (layoutState.getMessageFieldContentWidthOverride() > 0) {
            messageContentWidth = Math.max(messageContentWidth, layoutState.getMessageFieldContentWidthOverride());
        }
        int messageFieldWidth = messageContentWidth + 2 * Node.MESSAGE_FIELD_MARGIN_HORIZONTAL;
        int buttonWidth = (Node.MESSAGE_BUTTON_SIZE * 2) + Node.MESSAGE_BUTTON_SPACING + (Node.MESSAGE_BUTTON_PADDING * 2);
        return Math.max(computedWidth, Math.max(messageFieldWidth, buttonWidth));
    }

    private static int computeHeight(Node node) {
        NodeType type = node.getType();
        int contentHeight = Node.HEADER_HEIGHT;
        boolean hasSlots = node.hasSensorSlot() || node.hasActionSlot();

        if (node.isInlineParameterNode()) {
            contentHeight = applyInlineParameterHeight(node, contentHeight, hasSlots);
        } else if (node.shouldRenderInlineParameters()) {
            contentHeight = applyRenderedInlineParameterHeight(node, contentHeight, hasSlots);
        } else if ((type == NodeType.EVENT_FUNCTION || type == NodeType.EVENT_CALL || type == NodeType.ROUTINE_ENTRY)
            && !node.usesMinimalNodePresentation()) {
            contentHeight += Node.EVENT_NAME_FIELD_TOP_MARGIN + Node.EVENT_NAME_FIELD_HEIGHT + Node.EVENT_NAME_FIELD_BOTTOM_MARGIN;
        } else if (node.hasParameterSlot()) {
            contentHeight = applyParameterSlotHeight(node, contentHeight, hasSlots);
            if (node.hasBooleanToggle()) {
                contentHeight += node.getBooleanToggleAreaHeight();
            }
        } else if (node.showsModeFieldAboveParameterSlot()) {
            contentHeight += node.getModeFieldDisplayHeight();
            if (hasSlots) {
                contentHeight += Node.SLOT_AREA_PADDING_TOP;
            }
        } else if (node.hasAmountInputField()) {
            if (type != NodeType.CONTROL_REPEAT) {
                contentHeight += node.getAmountFieldDisplayHeight();
            }
            if (hasSlots) {
                contentHeight += Node.SLOT_AREA_PADDING_TOP;
            }
        } else if (node.hasCoordinateInputFields()) {
            contentHeight += node.getCoordinateFieldDisplayHeight();
            if (hasSlots) {
                contentHeight += Node.SLOT_AREA_PADDING_TOP;
            }
        } else if (hasSlots) {
            contentHeight += Node.SLOT_AREA_PADDING_TOP;
        } else if (type == NodeType.ROUTINE_INPUT) {
            contentHeight += node.getVariableFieldDisplayHeight();
        } else if (node.hasMessageInputFields()) {
            contentHeight += node.getMessageFieldDisplayHeight();
        } else if (node.hasBookTextInput()) {
            contentHeight += node.getBookTextDisplayHeight();
        } else if (node.hasStopTargetInputField()) {
            contentHeight += node.getStopTargetFieldDisplayHeight();
        } else if (node.hasBooleanToggle()) {
            contentHeight += node.getBooleanToggleAreaHeight();
        } else {
            contentHeight += Node.BODY_PADDING_NO_PARAMS;
        }

        if (node.hasSensorSlot()) {
            if (node.showsSensorSlotHeader()) {
                contentHeight += Node.PARAMETER_SLOT_LABEL_HEIGHT;
            }
            contentHeight += node.getSensorSlotHeight();
        }

        if (node.hasActionSlot()) {
            if (node.hasSensorSlot()) {
                contentHeight += Node.SLOT_VERTICAL_SPACING;
            }
            if (node.showsActionSlotHeader()) {
                contentHeight += Node.PARAMETER_SLOT_LABEL_HEIGHT;
            }
            contentHeight += node.getActionSlotHeight();
        }

        if (hasSlots) {
            contentHeight += Node.SLOT_AREA_PADDING_BOTTOM;
        }
        if (type == NodeType.CONTROL_REPEAT && node.hasAmountInputField()) {
            contentHeight += node.getAmountFieldDisplayHeight();
        }

        int minHeight = node.usesMinimalNodePresentation() ? 32 : Node.MIN_HEIGHT;
        int computedHeight = Math.max(minHeight, contentHeight);
        if (((type == NodeType.EVENT_FUNCTION || type == NodeType.ROUTINE_ENTRY)
            && !node.usesMinimalNodePresentation()) || type == NodeType.VARIABLE || type == NodeType.ROUTINE_INPUT) {
            return Math.max(Node.EVENT_FUNCTION_MIN_HEIGHT, contentHeight);
        }
        return computedHeight;
    }

    private static int applyInlineParameterHeight(Node node, int contentHeight, boolean hasSlots) {
        int parameterLineCount = node.getVisibleParameterLineCount();

        if (parameterLineCount > 0) {
            contentHeight += Node.PARAM_PADDING_TOP + (parameterLineCount * Node.PARAM_LINE_HEIGHT) + Node.PARAM_PADDING_BOTTOM;
            if (node.hasPopupEditButton()) {
                contentHeight += node.getPopupEditButtonDisplayHeight();
            }
            if (node.hasBooleanToggle()) {
                contentHeight += node.getBooleanToggleAreaHeight();
            }
            if (node.hasRandomRoundingField()) {
                contentHeight += node.getRandomRoundingFieldDisplayHeight();
            }
            if (node.hasParameterSlot()) {
                contentHeight += parameterSlotsHeightWithLabels(node);
            }
            if (hasSlots) {
                contentHeight += Node.SLOT_AREA_PADDING_TOP;
            }
        } else if (hasSlots) {
            contentHeight += Node.SLOT_AREA_PADDING_TOP;
        } else {
            contentHeight += Node.BODY_PADDING_NO_PARAMS;
        }
        return contentHeight;
    }

    private static int applyRenderedInlineParameterHeight(Node node, int contentHeight, boolean hasSlots) {
        int parameterLineCount = node.getVisibleParameterLineCount();
        if (parameterLineCount > 0) {
            contentHeight += Node.PARAM_PADDING_TOP + (parameterLineCount * Node.PARAM_LINE_HEIGHT) + Node.PARAM_PADDING_BOTTOM;
        } else {
            contentHeight += Node.BODY_PADDING_NO_PARAMS;
        }
        if (node.hasParameterSlot()) {
            contentHeight += parameterSlotsHeightWithLabels(node);
        }
        if (hasSlots) {
            contentHeight += Node.SLOT_AREA_PADDING_TOP;
        }
        return contentHeight;
    }

    private static int applyParameterSlotHeight(Node node, int contentHeight, boolean hasSlots) {
        if (node.isComparisonOperator()) {
            if (node.isExpandableBooleanOperator()) {
                contentHeight += parameterSlotsHeightWithLabels(node);
                if (hasSlots) {
                    contentHeight += Node.SLOT_AREA_PADDING_TOP;
                }
                return contentHeight;
            }
            int leftHeight = node.getParameterSlotHeight(0);
            int rightHeight = node.getParameterSlotHeight(1);
            int maxHeight = Math.max(leftHeight, rightHeight);
            contentHeight += Node.PARAMETER_SLOT_LABEL_HEIGHT + maxHeight + Node.PARAMETER_SLOT_BOTTOM_PADDING;
            if (hasSlots) {
                contentHeight += Node.SLOT_AREA_PADDING_TOP;
            }
            return contentHeight;
        }

        if (node.hasSchematicDropdownField()) {
            contentHeight += node.getSchematicFieldDisplayHeight();
        }
        if (node.hasVariableInputField()) {
            contentHeight += node.getVariableFieldDisplayHeight();
        }
        if (node.showsModeFieldAboveParameterSlot()) {
            contentHeight += node.getModeFieldDisplayHeight();
        }
        contentHeight += parameterSlotsHeightWithLabels(node);
        if (node.hasCoordinateInputFields()) {
            contentHeight += node.getCoordinateFieldDisplayHeight();
        }
        if (node.hasAmountInputField()) {
            contentHeight += node.getAmountFieldDisplayHeight();
        }
        if (node.hasMessageInputFields()) {
            contentHeight += node.getMessageFieldDisplayHeight();
        }
        if (hasSlots) {
            contentHeight += Node.SLOT_AREA_PADDING_TOP;
        }
        return contentHeight;
    }

    private static int parameterSlotsHeightWithLabels(Node node) {
        int height = 0;
        int slotCount = node.getParameterSlotCount();
        for (int i = 0; i < slotCount; i++) {
            height += Node.PARAMETER_SLOT_LABEL_HEIGHT + node.getParameterSlotHeight(i) + Node.PARAMETER_SLOT_BOTTOM_PADDING;
        }
        return height;
    }
}
