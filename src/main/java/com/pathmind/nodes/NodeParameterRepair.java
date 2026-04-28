package com.pathmind.nodes;

import com.pathmind.data.SettingsManager;
import java.util.List;

final class NodeParameterRepair {
    private NodeParameterRepair() {
    }

    static void ensureAmountToggleParameters(Node node) {
        if (!node.hasAmountToggle()) {
            return;
        }
        List<NodeParameter> parameters = node.getParameters();
        String amountKey = node.getAmountParameterKey();
        if (node.getParameter(amountKey) == null) {
            NodeType type = node.getType();
            boolean decimalAmount = type == NodeType.USE
                || type == NodeType.SWING
                || type == NodeType.SENSOR_CHAT_MESSAGE
                || type == NodeType.SENSOR_FABRIC_EVENT;
            ParameterType amountType = decimalAmount ? ParameterType.DOUBLE : ParameterType.INTEGER;
            String defaultValue = decimalAmount ? "0.0" : "1";
            if (type == NodeType.CHANGE_VARIABLE) {
                parameters.add(new NodeParameter("change_variable_amount", amountKey, amountType, defaultValue));
            } else {
                parameters.add(new NodeParameter(amountKey, amountType, defaultValue));
            }
        }
        if (node.getParameter("UseAmount") == null) {
            boolean defaultEnabled = false;
            if (node.getType() == NodeType.DROP_ITEM) {
                NodeParameter count = node.getParameter("Count");
                defaultEnabled = count != null && count.getIntValue() > 1;
            }
            parameters.add(new NodeParameter("UseAmount", ParameterType.BOOLEAN, Boolean.toString(defaultEnabled)));
        }
    }

    static void ensureVillagerTradeNumberParameter(Node node) {
        if (!usesVillagerTradeNumberField(node.getType())) {
            return;
        }
        List<NodeParameter> parameters = node.getParameters();
        if (node.getType() == NodeType.TRADE) {
            NodeParameter number = node.getParameter("Number");
            NodeParameter count = node.getParameter("Count");
            NodeParameter legacyAmount = node.getParameter("Amount");
            if (number == null) {
                number = new NodeParameter("trade_number", "Number", ParameterType.INTEGER, "1");
                parameters.add(number);
            }
            if (count == null) {
                String countValue = legacyAmount != null && legacyAmount.getStringValue() != null && !legacyAmount.getStringValue().isEmpty()
                    ? legacyAmount.getStringValue()
                    : "1";
                count = new NodeParameter("trade_count", "Count", ParameterType.INTEGER, countValue);
                if (legacyAmount != null) {
                    count.setUserEdited(legacyAmount.isUserEdited());
                }
                parameters.add(count);
            }
            if (legacyAmount != null) {
                parameters.remove(legacyAmount);
            }
            NodeParameter legacyToggle = node.getParameter("UseAmount");
            if (legacyToggle != null) {
                parameters.remove(legacyToggle);
            }
            return;
        }
        NodeParameter number = node.getParameter("Number");
        if (number == null) {
            NodeParameter legacy = node.getParameter("Amount");
            String value = legacy != null && legacy.getStringValue() != null && !legacy.getStringValue().isEmpty()
                ? legacy.getStringValue()
                : "1";
            number = new NodeParameter("trade_number", "Number", ParameterType.INTEGER, value);
            if (legacy != null) {
                number.setUserEdited(legacy.isUserEdited());
                parameters.remove(legacy);
            }
            parameters.add(number);
        }
    }

    static void ensureCreateListRadiusParameters(Node node) {
        if (node.getType() != NodeType.CREATE_LIST) {
            return;
        }
        List<NodeParameter> parameters = node.getParameters();
        SettingsManager.Settings settings = SettingsManager.getCurrent();
        String useRadiusDefault = Boolean.toString(Boolean.TRUE.equals(settings.createListUseCustomRadius));
        String radiusDefault = Double.toString(settings.createListRadius == null ? 64 : settings.createListRadius);
        if (node.getParameter("UseRadius") == null) {
            parameters.add(new NodeParameter("create_list_use_radius", "UseRadius", ParameterType.BOOLEAN, useRadiusDefault));
        }
        if (node.getParameter("Radius") == null) {
            parameters.add(new NodeParameter("create_list_radius", "Radius", ParameterType.DOUBLE, radiusDefault));
        }
        if (node.getParameter("UseBlockCap") == null) {
            parameters.add(new NodeParameter("create_list_use_block_cap", "UseBlockCap", ParameterType.BOOLEAN, "false"));
        }
        if (node.getParameter("MaxBlocks") == null) {
            parameters.add(new NodeParameter("create_list_max_blocks", "MaxBlocks", ParameterType.INTEGER, "256"));
        }
    }

    static void ensureRandomRoundingParameters(Node node) {
        if (!node.hasRandomRoundingField()) {
            return;
        }
        List<NodeParameter> parameters = node.getParameters();
        if (node.getParameter("Rounding") == null) {
            parameters.add(new NodeParameter("random_rounding_mode", "Rounding", ParameterType.STRING, "round"));
        }
        if (node.getParameter("UseRounding") == null) {
            parameters.add(new NodeParameter("random_use_rounding", "UseRounding", ParameterType.BOOLEAN, "false"));
        }
    }

    private static boolean usesVillagerTradeNumberField(NodeType type) {
        return type == NodeType.TRADE
            || type == NodeType.SENSOR_VILLAGER_TRADE
            || type == NodeType.SENSOR_IN_STOCK;
    }
}
