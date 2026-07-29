package com.pathmind.nodes;

/**
 * Presentation policy and geometry for fields rendered directly inside nodes.
 */
final class NodeInlineFieldLayout {
    private final Node owner;
    private final NodeLayoutState layoutState;

    NodeInlineFieldLayout(Node owner, NodeLayoutState layoutState) {
        this.owner = owner;
        this.layoutState = layoutState;
    }

    boolean hasCoordinateInputFields() {
        return owner.getType() == NodeType.CLICK_SCREEN;
    }

    String[] getCoordinateFieldAxes() {
        if (owner.getType() == NodeType.CLICK_SCREEN) {
            return new String[]{"X", "Y"};
        }
        return new String[]{"X", "Y", "Z"};
    }

    int getCoordinateFieldDisplayHeight() {
        if (!hasCoordinateInputFields()) {
            return 0;
        }
        int height = Node.COORDINATE_FIELD_TOP_MARGIN + Node.COORDINATE_FIELD_LABEL_HEIGHT + Node.COORDINATE_FIELD_HEIGHT;
        if (hasScreenCoordinatePickerButton()) {
            height += Node.SCREEN_PICK_BUTTON_TOP_MARGIN + Node.SCREEN_PICK_BUTTON_HEIGHT + Node.SCREEN_PICK_BUTTON_BOTTOM_MARGIN;
        } else {
            height += Node.COORDINATE_FIELD_BOTTOM_MARGIN;
        }
        return height;
    }

    boolean showsModeFieldAboveParameterSlot() {
        return (owner.getType() == NodeType.SENSOR_POSITION_OF || owner.getType() == NodeType.SENSOR_LOOK_DIRECTION)
            && owner.supportsModeSelection()
            && !owner.isInlineParameterNode()
            && !owner.shouldRenderInlineParameters()
            && owner.getType() != NodeType.WAIT
            && owner.getType() != NodeType.PARAM_DURATION;
    }

    int getModeFieldDisplayHeight() {
        if (!showsModeFieldAboveParameterSlot()) {
            return 0;
        }
        return Node.MODE_FIELD_TOP_MARGIN + Node.MODE_FIELD_LABEL_HEIGHT + Node.MODE_FIELD_HEIGHT + Node.MODE_FIELD_BOTTOM_MARGIN;
    }

    int getModeFieldTop() {
        int top = owner.getY() + Node.HEADER_HEIGHT;
        if (hasSchematicDropdownField()) {
            top += getSchematicFieldDisplayHeight();
        }
        if (hasVariableInputField()) {
            top += getVariableFieldDisplayHeight();
        }
        return top + Node.MODE_FIELD_TOP_MARGIN + Node.MODE_FIELD_LABEL_HEIGHT;
    }

    int getModeFieldLeft() {
        return owner.getParameterSlotLeft();
    }

    int getModeFieldWidth() {
        return owner.getParameterSlotWidth();
    }

    int getModeFieldHeight() {
        return Node.MODE_FIELD_HEIGHT;
    }

    String getModeFieldLabelText() {
        if (owner.getType() == NodeType.SENSOR_POSITION_OF || owner.getType() == NodeType.SENSOR_LOOK_DIRECTION) {
            return "Axis:";
        }
        return "Mode:";
    }

    int getCoordinateFieldLabelTop() {
        return owner.getParameterSlotsBottom() + Node.COORDINATE_FIELD_TOP_MARGIN;
    }

    int getCoordinateFieldInputTop() {
        return getCoordinateFieldLabelTop() + Node.COORDINATE_FIELD_LABEL_HEIGHT;
    }

    int getCoordinateFieldLabelHeight() {
        return Node.COORDINATE_FIELD_LABEL_HEIGHT;
    }

    int getCoordinateFieldHeight() {
        return Node.COORDINATE_FIELD_HEIGHT;
    }

    int getCoordinateFieldWidth() {
        return Math.max(Node.COORDINATE_FIELD_WIDTH, layoutState.getCoordinateFieldWidthOverride());
    }

    int getCoordinateFieldSpacing() {
        return Node.COORDINATE_FIELD_SPACING;
    }

    int getCoordinateFieldStartX() {
        int slotLeft = owner.getParameterSlotLeft();
        int slotWidth = owner.getParameterSlotWidth();
        int totalFieldWidth = getCoordinateFieldTotalWidth();
        if (totalFieldWidth >= slotWidth) {
            return slotLeft;
        }
        return slotLeft + (slotWidth - totalFieldWidth) / 2;
    }

    int getCoordinateFieldTotalWidth() {
        int axisCount = getCoordinateFieldAxes().length;
        return (getCoordinateFieldWidth() * axisCount)
            + (Node.COORDINATE_FIELD_SPACING * Math.max(0, axisCount - 1));
    }

    boolean hasScreenCoordinatePickerButton() {
        return owner.getType() == NodeType.CLICK_SCREEN;
    }

    int getScreenCoordinatePickerButtonTop() {
        return getCoordinateFieldInputTop() + Node.COORDINATE_FIELD_HEIGHT + Node.SCREEN_PICK_BUTTON_TOP_MARGIN;
    }

    int getScreenCoordinatePickerButtonLeft() {
        return owner.getX() + Node.POPUP_EDIT_BUTTON_MARGIN_HORIZONTAL;
    }

    int getScreenCoordinatePickerButtonWidth() {
        return Math.max(Node.SCREEN_PICK_BUTTON_MIN_WIDTH,
            owner.getWidth() - 2 * Node.POPUP_EDIT_BUTTON_MARGIN_HORIZONTAL);
    }

    int getScreenCoordinatePickerButtonHeight() {
        return Node.SCREEN_PICK_BUTTON_HEIGHT;
    }

    boolean hasAmountInputField() {
        NodeType type = owner.getType();
        NodeMode mode = owner.getMode();
        return
            (type == NodeType.COLLECT
                && (mode == null || mode == NodeMode.COLLECT_SINGLE))
            || (type == NodeType.CRAFT
                && (mode == null || mode == NodeMode.CRAFT_PLAYER_GUI || mode == NodeMode.CRAFT_CRAFTING_TABLE))
            || type == NodeType.MOVE_ITEM
            || type == NodeType.CONTROL_REPEAT
            || type == NodeType.SENSOR_ITEM_IN_INVENTORY
            || type == NodeType.SENSOR_ITEM_IN_SLOT
            || type == NodeType.SENSOR_HEALTH_BELOW
            || type == NodeType.SENSOR_HUNGER_BELOW
            || type == NodeType.SENSOR_CHAT_MESSAGE
            || type == NodeType.SENSOR_VILLAGER_TRADE
            || type == NodeType.SENSOR_IN_STOCK
            || type == NodeType.WAIT
            || type == NodeType.PARAM_DURATION
            || type == NodeType.USE
            || type == NodeType.PRESS_KEY
            || type == NodeType.SWING
            || type == NodeType.DROP_ITEM;
    }

    boolean hasRandomRoundingField() {
        return owner.getType() == NodeType.OPERATOR_RANDOM;
    }

    boolean hasSchematicDropdownField() {
        return owner.getType() == NodeType.BUILD;
    }

    boolean hasStopTargetInputField() {
        NodeType type = owner.getType();
        return type == NodeType.STOP_CHAIN || type == NodeType.START_CHAIN || type == NodeType.RUN_PRESET
            || type == NodeType.TEMPLATE;
    }

    boolean hasVariableInputField() {
        NodeType type = owner.getType();
        return type == NodeType.CREATE_LIST
            || type == NodeType.ADD_TO_LIST
            || type == NodeType.REMOVE_FIRST_FROM_LIST
            || type == NodeType.REMOVE_LAST_FROM_LIST
            || type == NodeType.REMOVE_LIST_ITEM
            || type == NodeType.REMOVE_FROM_LIST
            || type == NodeType.LIST_LENGTH;
    }

    int getAmountFieldDisplayHeight() {
        if (!hasAmountInputField()) {
            return 0;
        }
        if (owner.getType() == NodeType.WAIT || owner.getType() == NodeType.PARAM_DURATION) {
            return Node.AMOUNT_FIELD_TOP_MARGIN + getAmountFieldLabelHeight()
                + Node.WAIT_AMOUNT_FIELD_GAP + Node.AMOUNT_FIELD_HEIGHT + Node.AMOUNT_FIELD_BOTTOM_MARGIN;
        }
        return Node.AMOUNT_FIELD_TOP_MARGIN + Node.AMOUNT_FIELD_LABEL_HEIGHT
            + Node.AMOUNT_FIELD_HEIGHT + Node.AMOUNT_FIELD_BOTTOM_MARGIN;
    }

    int getAmountFieldLabelTop() {
        if (owner.getType() == NodeType.CONTROL_REPEAT) {
            int top = owner.getActionSlotTop() + owner.getActionSlotHeight();
            return top + Node.SLOT_AREA_PADDING_BOTTOM + Node.AMOUNT_FIELD_TOP_MARGIN;
        }
        int top = owner.getParameterSlotsBottom();
        if (hasCoordinateInputFields()) {
            top += getCoordinateFieldDisplayHeight();
        }
        return top + Node.AMOUNT_FIELD_TOP_MARGIN;
    }

    int getAmountFieldInputTop() {
        if (owner.getType() == NodeType.WAIT || owner.getType() == NodeType.PARAM_DURATION) {
            return getAmountFieldLabelTop() + getAmountFieldLabelHeight() + Node.WAIT_AMOUNT_FIELD_GAP;
        }
        return getAmountFieldLabelTop() + Node.AMOUNT_FIELD_LABEL_HEIGHT;
    }

    int getAmountFieldLabelHeight() {
        if (owner.getType() == NodeType.WAIT || owner.getType() == NodeType.PARAM_DURATION) {
            return Node.AMOUNT_FIELD_HEIGHT;
        }
        return Node.AMOUNT_FIELD_LABEL_HEIGHT;
    }

    String getAmountFieldLabel() {
        if (usesVillagerTradeNumberField()) {
            return "Number";
        }
        return switch (owner.getType()) {
            case NodeType.USE, NodeType.PRESS_KEY, NodeType.SWING -> "Hold Duration";
            case NodeType.SENSOR_CHAT_MESSAGE -> "Seconds";
            case NodeType.SENSOR_HEALTH_BELOW -> "Health";
            case NodeType.SENSOR_HUNGER_BELOW -> "Hunger";
            case NodeType.WAIT, NodeType.PARAM_DURATION ->
                switch (owner.getMode() == null ? NodeMode.WAIT_SECONDS : owner.getMode()) {
                    case WAIT_TICKS -> "Ticks";
                    case WAIT_MINUTES -> "Minutes";
                    case WAIT_HOURS -> "Hours";
                    default -> "Seconds";
                };
            case NodeType.CONTROL_REPEAT -> "Times";
            default -> "Amount";
        };
    }

    String getAmountParameterKey() {
        if (usesVillagerTradeNumberField()) {
            return "Number";
        }
        return switch (owner.getType()) {
            case NodeType.MOVE_ITEM, NodeType.CONTROL_REPEAT, NodeType.DROP_ITEM -> "Count";
            case NodeType.WAIT, NodeType.PARAM_DURATION, NodeType.SWING, NodeType.PRESS_KEY -> "Duration";
            case NodeType.USE -> "UseDurationSeconds";
            default -> "Amount";
        };
    }

    int getAmountFieldHeight() {
        return Node.AMOUNT_FIELD_HEIGHT;
    }

    int getAmountFieldWidth() {
        int width = owner.getParameterSlotWidth();
        if (hasAmountToggle()) {
            width = Math.max(40, width - (Node.AMOUNT_TOGGLE_WIDTH + Node.AMOUNT_TOGGLE_SPACING));
        }
        return Math.max(width, layoutState.getAmountFieldWidthOverride());
    }

    int getAmountFieldLeft() {
        return owner.getParameterSlotLeft();
    }

    boolean hasAmountToggle() {
        NodeType type = owner.getType();
        return type == NodeType.SENSOR_ITEM_IN_INVENTORY
            || type == NodeType.SENSOR_ITEM_IN_SLOT
            || type == NodeType.SENSOR_CHAT_MESSAGE
            || type == NodeType.USE
            || type == NodeType.PRESS_KEY
            || type == NodeType.SWING
            || type == NodeType.DROP_ITEM;
    }

    int getAmountToggleLeft() {
        return getAmountFieldLeft() + getAmountFieldWidth() + Node.AMOUNT_TOGGLE_SPACING;
    }

    int getAmountToggleTop() {
        return getAmountFieldInputTop() + (getAmountFieldHeight() - Node.AMOUNT_TOGGLE_HEIGHT) / 2;
    }

    int getAmountToggleWidth() {
        return Node.AMOUNT_TOGGLE_WIDTH;
    }

    int getAmountToggleHeight() {
        return Node.AMOUNT_TOGGLE_HEIGHT;
    }

    int getRandomRoundingFieldDisplayHeight() {
        if (!hasRandomRoundingField()) {
            return 0;
        }
        return Node.RANDOM_ROUNDING_FIELD_TOP_MARGIN + Node.RANDOM_ROUNDING_FIELD_LABEL_HEIGHT
            + Node.RANDOM_ROUNDING_FIELD_HEIGHT + Node.RANDOM_ROUNDING_FIELD_BOTTOM_MARGIN;
    }

    int getRandomRoundingFieldLabelTop() {
        return owner.getY() + Node.HEADER_HEIGHT + owner.getParameterDisplayHeight()
            + Node.RANDOM_ROUNDING_FIELD_TOP_MARGIN;
    }

    int getRandomRoundingFieldInputTop() {
        return getRandomRoundingFieldLabelTop() + Node.RANDOM_ROUNDING_FIELD_LABEL_HEIGHT;
    }

    int getRandomRoundingFieldLabelHeight() {
        return Node.RANDOM_ROUNDING_FIELD_LABEL_HEIGHT;
    }

    int getRandomRoundingFieldHeight() {
        return Node.RANDOM_ROUNDING_FIELD_HEIGHT;
    }

    int getRandomRoundingFieldWidth() {
        int width = Math.max(20, owner.getWidth() - 10);
        if (hasRandomRoundingToggle()) {
            width = Math.max(40, width - (Node.RANDOM_ROUNDING_TOGGLE_WIDTH + Node.RANDOM_ROUNDING_TOGGLE_SPACING));
        }
        return width;
    }

    int getRandomRoundingFieldLeft() {
        return owner.getX() + 5;
    }

    boolean hasRandomRoundingToggle() {
        return owner.getType() == NodeType.OPERATOR_RANDOM;
    }

    int getRandomRoundingToggleLeft() {
        return getRandomRoundingFieldLeft() + getRandomRoundingFieldWidth() + Node.RANDOM_ROUNDING_TOGGLE_SPACING;
    }

    int getRandomRoundingToggleTop() {
        return getRandomRoundingFieldInputTop()
            + (getRandomRoundingFieldHeight() - Node.RANDOM_ROUNDING_TOGGLE_HEIGHT) / 2;
    }

    int getRandomRoundingToggleWidth() {
        return Node.RANDOM_ROUNDING_TOGGLE_WIDTH;
    }

    int getRandomRoundingToggleHeight() {
        return Node.RANDOM_ROUNDING_TOGGLE_HEIGHT;
    }

    int getSchematicFieldDisplayHeight() {
        if (!hasSchematicDropdownField()) {
            return 0;
        }
        return Node.SCHEMATIC_FIELD_TOP_MARGIN + Node.SCHEMATIC_FIELD_LABEL_HEIGHT
            + Node.SCHEMATIC_FIELD_HEIGHT + Node.SCHEMATIC_FIELD_BOTTOM_MARGIN;
    }

    int getSchematicFieldLabelTop() {
        return owner.getY() + Node.HEADER_HEIGHT + Node.SCHEMATIC_FIELD_TOP_MARGIN;
    }

    int getSchematicFieldInputTop() {
        return getSchematicFieldLabelTop() + Node.SCHEMATIC_FIELD_LABEL_HEIGHT;
    }

    int getSchematicFieldLabelHeight() {
        return Node.SCHEMATIC_FIELD_LABEL_HEIGHT;
    }

    int getSchematicFieldHeight() {
        return Node.SCHEMATIC_FIELD_HEIGHT;
    }

    int getSchematicFieldWidth() {
        return owner.getParameterSlotWidth();
    }

    int getSchematicFieldLeft() {
        return owner.getParameterSlotLeft();
    }

    int getStopTargetFieldDisplayHeight() {
        if (!hasStopTargetInputField()) {
            return 0;
        }
        if (owner.getType() == NodeType.TEMPLATE) {
            return 24;
        }
        return Node.STOP_TARGET_FIELD_TOP_MARGIN + Node.STOP_TARGET_FIELD_HEIGHT + Node.STOP_TARGET_FIELD_BOTTOM_MARGIN;
    }

    int getStopTargetFieldLabelTop() {
        if (owner.getType() == NodeType.TEMPLATE) {
            return owner.getY() + Node.HEADER_HEIGHT + 4;
        }
        return owner.getParameterSlotsBottom() + Node.STOP_TARGET_FIELD_TOP_MARGIN;
    }

    int getStopTargetFieldInputTop() {
        return getStopTargetFieldLabelTop() + Node.STOP_TARGET_FIELD_LABEL_HEIGHT;
    }

    int getStopTargetFieldLabelHeight() {
        return Node.STOP_TARGET_FIELD_LABEL_HEIGHT;
    }

    int getStopTargetFieldHeight() {
        if (owner.getType() == NodeType.TEMPLATE) {
            return 16;
        }
        return Node.STOP_TARGET_FIELD_HEIGHT;
    }

    int getStopTargetFieldWidth() {
        if (owner.getType() == NodeType.TEMPLATE) {
            return Math.max(72, owner.getWidth() - 12);
        }
        int minimum = owner.getType() == NodeType.RUN_PRESET
            ? Node.RUN_PRESET_FIELD_MIN_WIDTH
            : Node.STOP_TARGET_FIELD_MIN_WIDTH;
        return Math.max(minimum, layoutState.getStopTargetFieldWidthOverride());
    }

    int getStopTargetFieldLeft() {
        if (owner.getType() == NodeType.TEMPLATE) {
            return owner.getX() + 6;
        }
        return owner.getX() + Math.max(Node.STOP_TARGET_FIELD_MARGIN_HORIZONTAL,
            (owner.getWidth() - getStopTargetFieldWidth()) / 2);
    }

    int getVariableFieldDisplayHeight() {
        if (!hasVariableInputField() && owner.getType() != NodeType.ROUTINE_INPUT) {
            return 0;
        }
        return Node.VARIABLE_FIELD_TOP_MARGIN + Node.VARIABLE_FIELD_HEIGHT + Node.VARIABLE_FIELD_BOTTOM_MARGIN;
    }

    int getVariableFieldLabelTop() {
        return owner.getY() + Node.HEADER_HEIGHT + Node.VARIABLE_FIELD_TOP_MARGIN;
    }

    int getVariableFieldInputTop() {
        return getVariableFieldLabelTop() + Node.VARIABLE_FIELD_LABEL_HEIGHT;
    }

    int getVariableFieldLabelHeight() {
        return Node.VARIABLE_FIELD_LABEL_HEIGHT;
    }

    int getVariableFieldHeight() {
        return Node.VARIABLE_FIELD_HEIGHT;
    }

    int getVariableFieldWidth() {
        return Math.max(Node.VARIABLE_FIELD_MIN_WIDTH, layoutState.getVariableFieldWidthOverride());
    }

    int getVariableFieldLeft() {
        return owner.getX() + Math.max(Node.VARIABLE_FIELD_MARGIN_HORIZONTAL,
            (owner.getWidth() - getVariableFieldWidth()) / 2);
    }

    private boolean usesVillagerTradeNumberField() {
        NodeType type = owner.getType();
        return type == NodeType.TRADE
            || type == NodeType.SENSOR_VILLAGER_TRADE
            || type == NodeType.SENSOR_IN_STOCK;
    }
}
