package com.pathmind.nodes;

/**
 * Editor slot layout calculations for sensor, action, and parameter attachment
 * areas.
 */
final class NodeSlotLayout {
    private NodeSlotLayout() {
    }

    static int sensorSlotLeft(Node node) {
        return node.getX() + Node.SENSOR_SLOT_MARGIN_HORIZONTAL;
    }

    static int sensorSlotTop(Node node) {
        int top = slotAreaStartY(node);
        if (node.showsSensorSlotHeader()) {
            top += Node.PARAMETER_SLOT_LABEL_HEIGHT;
        }
        return top;
    }

    static int sensorSlotWidth(Node node) {
        int minWidth = Node.SENSOR_SLOT_MIN_CONTENT_WIDTH + 2 * Node.SENSOR_SLOT_INNER_PADDING;
        int widthWithMargins = node.getWidth() - 2 * Node.SENSOR_SLOT_MARGIN_HORIZONTAL;
        return Math.max(minWidth, widthWithMargins);
    }

    static int sensorSlotHeight(Node node) {
        Node sensor = node.getAttachedSensor();
        int sensorContentHeight = sensor != null ? sensor.getHeight() : Node.SENSOR_SLOT_MIN_CONTENT_HEIGHT;
        return sensorContentHeight + 2 * Node.SENSOR_SLOT_INNER_PADDING;
    }

    static boolean isPointInsideSensorSlot(Node node, int pointX, int pointY) {
        if (!node.hasSensorSlot()) {
            return false;
        }
        return NodeGeometry.containsPoint(
            sensorSlotLeft(node),
            sensorSlotTop(node),
            sensorSlotWidth(node),
            sensorSlotHeight(node),
            pointX,
            pointY);
    }

    static int parameterSlotLeft(Node node) {
        return node.getX() + Node.PARAMETER_SLOT_MARGIN_HORIZONTAL;
    }

    static int parameterSlotLeft(Node node, int slotIndex) {
        if (node.isComparisonOperator()) {
            int slotWidth = parameterSlotWidth(node, slotIndex);
            int baseLeft = node.getX() + Node.PARAMETER_SLOT_MARGIN_HORIZONTAL;
            if (node.usesMinimalNodePresentation()) {
                int contentLeft = node.getX() + Node.MINIMAL_NODE_TAB_WIDTH + Node.PARAMETER_SLOT_MARGIN_HORIZONTAL;
                int contentWidth = Math.max(0, node.getWidth() - Node.MINIMAL_NODE_TAB_WIDTH - 2 * Node.PARAMETER_SLOT_MARGIN_HORIZONTAL);
                int groupWidth = slotWidth * 2 + Node.OPERATOR_SLOT_GAP;
                int startX = contentLeft + Math.max(0, (contentWidth - groupWidth) / 2);
                if (slotIndex <= 0) {
                    return startX;
                }
                return startX + slotWidth + Node.OPERATOR_SLOT_GAP;
            }
            if (slotIndex <= 0) {
                return baseLeft;
            }
            return baseLeft + slotWidth + Node.OPERATOR_SLOT_GAP;
        }
        return parameterSlotLeft(node);
    }

    static int parameterSlotTop(Node node, int slotIndex) {
        int top = node.getY() + Node.HEADER_HEIGHT + Node.PARAMETER_SLOT_LABEL_HEIGHT;
        if ((node.isInlineParameterNode() || node.shouldRenderInlineParameters()) && node.hasParameterSlot()) {
            top = node.getY() + Node.HEADER_HEIGHT;
            int parameterDisplayHeight = node.getParameterDisplayHeight();
            if (parameterDisplayHeight > 0) {
                top += parameterDisplayHeight;
            } else if (node.isInlineParameterNode()) {
                top += Node.BODY_PADDING_NO_PARAMS;
            }
            if (node.isInlineParameterNode() && node.hasPopupEditButton()) {
                top += node.getPopupEditButtonDisplayHeight();
            }
            top += Node.PARAMETER_SLOT_LABEL_HEIGHT;
        }
        if (node.hasSchematicDropdownField()) {
            top += node.getSchematicFieldDisplayHeight();
        }
        if (node.hasVariableInputField()) {
            top += node.getVariableFieldDisplayHeight();
        }
        if (node.showsModeFieldAboveParameterSlot()) {
            top += node.getModeFieldDisplayHeight();
        }
        if (node.isComparisonOperator()) {
            if (node.usesMinimalNodePresentation()) {
                int slotHeight = parameterSlotHeight(node, slotIndex);
                return node.getY() + Math.max(0, (node.getHeight() - slotHeight) / 2);
            }
            return top;
        }
        for (int i = 0; i < slotIndex; i++) {
            top += parameterSlotHeight(node, i) + Node.PARAMETER_SLOT_BOTTOM_PADDING + Node.PARAMETER_SLOT_LABEL_HEIGHT;
        }
        return top;
    }

    static int parameterSlotWidth(Node node) {
        int widthWithMargins = node.getWidth() - 2 * Node.PARAMETER_SLOT_MARGIN_HORIZONTAL;
        return Math.max(Node.PARAMETER_SLOT_MIN_CONTENT_WIDTH, widthWithMargins);
    }

    static int parameterSlotWidth(Node node, int slotIndex) {
        if (node.isComparisonOperator()) {
            int widthWithMargins = node.getWidth() - 2 * Node.PARAMETER_SLOT_MARGIN_HORIZONTAL;
            if (node.usesMinimalNodePresentation()) {
                widthWithMargins = Math.max(0, node.getWidth() - Node.MINIMAL_NODE_TAB_WIDTH - 2 * Node.PARAMETER_SLOT_MARGIN_HORIZONTAL);
            }
            int minCombinedWidth = Node.PARAMETER_SLOT_MIN_CONTENT_WIDTH * 2 + Node.OPERATOR_SLOT_GAP;
            int effectiveWidth = Math.max(minCombinedWidth, widthWithMargins);
            int available = effectiveWidth - Node.OPERATOR_SLOT_GAP;
            return Math.max(Node.PARAMETER_SLOT_MIN_CONTENT_WIDTH, available / 2);
        }
        return parameterSlotWidth(node);
    }

    static int parameterSlotHeight(Node node, int slotIndex) {
        Node parameter = node.getAttachedParameter(slotIndex);
        int contentHeight = parameter != null ? parameter.getHeight() : Node.PARAMETER_SLOT_MIN_CONTENT_HEIGHT;
        return contentHeight + 2 * Node.PARAMETER_SLOT_INNER_PADDING;
    }

    static int parameterSlotsBottom(Node node) {
        int slotCount = node.getParameterSlotCount();
        if (slotCount <= 0) {
            return node.getY() + Node.HEADER_HEIGHT;
        }
        if (node.isComparisonOperator()) {
            int leftHeight = parameterSlotHeight(node, 0);
            int rightHeight = parameterSlotHeight(node, 1);
            int maxHeight = Math.max(leftHeight, rightHeight);
            return parameterSlotTop(node, 0) + maxHeight;
        }
        int lastIndex = slotCount - 1;
        return parameterSlotTop(node, lastIndex) + parameterSlotHeight(node, lastIndex);
    }

    static int parameterSlotIndexAt(Node node, int pointX, int pointY) {
        if (!node.hasParameterSlot()) {
            return -1;
        }
        int slotCount = node.getParameterSlotCount();
        for (int i = 0; i < slotCount; i++) {
            if (NodeGeometry.containsPoint(
                    parameterSlotLeft(node, i),
                    parameterSlotTop(node, i),
                    parameterSlotWidth(node, i),
                    parameterSlotHeight(node, i),
                    pointX,
                    pointY)) {
                return i;
            }
        }
        return -1;
    }

    static int actionSlotLeft(Node node) {
        return node.getX() + Node.ACTION_SLOT_MARGIN_HORIZONTAL;
    }

    static int actionSlotTop(Node node) {
        int top = slotAreaStartY(node);
        if (node.hasSensorSlot()) {
            if (node.showsSensorSlotHeader()) {
                top += Node.PARAMETER_SLOT_LABEL_HEIGHT;
            }
            top += sensorSlotHeight(node);
            if (node.hasActionSlot()) {
                top += Node.SLOT_VERTICAL_SPACING;
            }
        }
        if (node.showsActionSlotHeader()) {
            top += Node.PARAMETER_SLOT_LABEL_HEIGHT;
        }
        return top;
    }

    static int actionSlotWidth(Node node) {
        int minWidth = Node.ACTION_SLOT_MIN_CONTENT_WIDTH + 2 * Node.ACTION_SLOT_INNER_PADDING;
        int widthWithMargins = node.getWidth() - 2 * Node.ACTION_SLOT_MARGIN_HORIZONTAL;
        return Math.max(minWidth, widthWithMargins);
    }

    static int actionSlotHeight(Node node) {
        Node actionNode = node.getAttachedActionNode();
        int contentHeight = actionNode != null ? actionNode.getHeight() : Node.ACTION_SLOT_MIN_CONTENT_HEIGHT;
        return contentHeight + 2 * Node.ACTION_SLOT_INNER_PADDING;
    }

    static boolean isPointInsideActionSlot(Node node, int pointX, int pointY) {
        if (!node.hasActionSlot()) {
            return false;
        }
        return NodeGeometry.containsPoint(
            actionSlotLeft(node),
            actionSlotTop(node),
            actionSlotWidth(node),
            actionSlotHeight(node),
            pointX,
            pointY);
    }

    static int slotAreaStartY(Node node) {
        int top = node.getY() + Node.HEADER_HEIGHT;
        if (node.isParameterNode()) {
            int lineCount = node.getVisibleParameterLineCount();
            if (lineCount > 0) {
                top += Node.PARAM_PADDING_TOP + lineCount * Node.PARAM_LINE_HEIGHT + Node.PARAM_PADDING_BOTTOM;
                if (node.hasPopupEditButton()) {
                    top += node.getPopupEditButtonDisplayHeight();
                }
                if (node.hasParameterSlot()) {
                    top += parameterSlotsHeightWithLabels(node);
                }
            } else {
                top += Node.BODY_PADDING_NO_PARAMS;
            }
        } else if (node.shouldRenderInlineParameters()) {
            int parameterDisplayHeight = node.getParameterDisplayHeight();
            if (parameterDisplayHeight > 0) {
                top += parameterDisplayHeight;
            } else {
                top += Node.BODY_PADDING_NO_PARAMS;
            }
            if (node.hasParameterSlot()) {
                top += parameterSlotsHeightWithLabels(node);
            }
            if (node.hasVariableInputField()) {
                top += node.getVariableFieldDisplayHeight();
            }
            if (node.hasCoordinateInputFields()) {
                top += node.getCoordinateFieldDisplayHeight();
            }
            if (node.hasSensorSlot() || node.hasActionSlot()) {
                top += Node.SLOT_AREA_PADDING_TOP;
            }
        } else if (node.hasParameterSlot()) {
            top += parameterSlotsHeightWithLabels(node);
            if (node.hasVariableInputField()) {
                top += node.getVariableFieldDisplayHeight();
            }
            if (node.hasCoordinateInputFields()) {
                top += node.getCoordinateFieldDisplayHeight();
            }
            if (node.hasSensorSlot() || node.hasActionSlot()) {
                top += Node.SLOT_AREA_PADDING_TOP;
            }
        } else if (node.hasAmountInputField() && node.getType() != NodeType.CONTROL_REPEAT) {
            top += node.getAmountFieldDisplayHeight();
            if (node.hasSensorSlot() || node.hasActionSlot()) {
                top += Node.SLOT_AREA_PADDING_TOP;
            }
        } else if (node.hasCoordinateInputFields()) {
            top += node.getCoordinateFieldDisplayHeight();
            if (node.hasSensorSlot() || node.hasActionSlot()) {
                top += Node.SLOT_AREA_PADDING_TOP;
            }
        } else if (node.hasSensorSlot() || node.hasActionSlot()) {
            top += Node.SLOT_AREA_PADDING_TOP;
        } else if (node.hasBooleanToggle()) {
            top += node.getBooleanToggleAreaHeight();
        } else {
            top += Node.BODY_PADDING_NO_PARAMS;
        }
        return top;
    }

    private static int parameterSlotsHeightWithLabels(Node node) {
        int height = 0;
        int slotCount = node.getParameterSlotCount();
        for (int i = 0; i < slotCount; i++) {
            height += Node.PARAMETER_SLOT_LABEL_HEIGHT + parameterSlotHeight(node, i) + Node.PARAMETER_SLOT_BOTTOM_PADDING;
        }
        return height;
    }
}
