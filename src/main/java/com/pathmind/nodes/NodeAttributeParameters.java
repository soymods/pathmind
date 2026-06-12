package com.pathmind.nodes;

import com.pathmind.util.GuiSelectionMode;
import java.util.List;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

final class NodeAttributeParameters {
    private NodeAttributeParameters() {
    }

    static String getParameterDisplayName(Node node, NodeParameter parameter) {
        if (parameter == null) {
            return "";
        }
        String name = parameter.getName();
        NodeType type = node.getType();
        if (type == NodeType.PARAM_BOOLEAN && "Toggle".equalsIgnoreCase(name)) {
            return "Value";
        }
        if (type == NodeType.USE && "UseDurationSeconds".equalsIgnoreCase(name)) {
            return "Hold Duration";
        }
        if (type == NodeType.PARAM_MESSAGE && "Text".equalsIgnoreCase(name)) {
            return "Text";
        }
        if (type == NodeType.PARAM_PLAYER && "Player".equalsIgnoreCase(name)) {
            return "User";
        }
        if (type == NodeType.PARAM_VILLAGER_TRADE
            && ("Item".equalsIgnoreCase(name) || "Trade".equalsIgnoreCase(name))) {
            return "Trade";
        }
        if (type == NodeType.CREATE_LIST && "UseRadius".equalsIgnoreCase(name)) {
            return "Use Custom Radius";
        }
        if (type == NodeType.CREATE_LIST && "UseBlockCap".equalsIgnoreCase(name)) {
            return "Use Block Cap";
        }
        return name;
    }

    static String getParameterDisplayValue(Node node, NodeParameter parameter) {
        if (parameter == null) {
            return "";
        }
        String value = parameter.getDisplayValue();
        NodeType type = node.getType();
        if (isAttributeDetectionSensor(node)) {
            if ("Attribute".equalsIgnoreCase(parameter.getName())) {
                AttributeDetectionConfig.AttributeOption attribute = AttributeDetectionConfig.getAttribute(value);
                return attribute != null ? attribute.label() : value;
            }
            if ("Value".equalsIgnoreCase(parameter.getName())) {
                AttributeDetectionConfig.AttributeOption attribute =
                    AttributeDetectionConfig.getAttribute(Node.getParameterString(node, "Attribute"));
                if (attribute != null && attribute.valueType() == AttributeDetectionConfig.ValueType.BOOLEAN) {
                    return parseBooleanLike(value) ? "True" : "False";
                }
            }
        }
        if (type == NodeType.PARAM_GUI && "GUI".equalsIgnoreCase(parameter.getName())) {
            return GuiSelectionMode.getDisplayNameOrFallback(value);
        }
        if (type == NodeType.PARAM_VILLAGER_TRADE
            && ("Item".equalsIgnoreCase(parameter.getName()) || "Trade".equalsIgnoreCase(parameter.getName()))) {
            return formatVillagerTradeDisplayValue(value);
        }
        return value;
    }

    static String getParameterLabel(Node node, NodeParameter parameter) {
        if (parameter == null) {
            return "";
        }
        NodeType type = node.getType();
        if (type == NodeType.PARAM_DIRECTION) {
            String parameterName = parameter.getName();
            boolean exactMode = node.isDirectionModeExact();
            if ("Mode".equalsIgnoreCase(parameterName)) {
                return "";
            }
            if ("Direction".equalsIgnoreCase(parameterName) && exactMode) {
                return "";
            }
            if (("Yaw".equalsIgnoreCase(parameterName)
                || "Pitch".equalsIgnoreCase(parameterName)
                || "YawOffset".equalsIgnoreCase(parameterName)
                || "PitchOffset".equalsIgnoreCase(parameterName)
                || "Distance".equalsIgnoreCase(parameterName))
                && !exactMode) {
                return "";
            }
        }
        if (type == NodeType.PARAM_BOOLEAN) {
            String parameterName = parameter.getName();
            boolean literalMode = node.isBooleanModeLiteral();
            if ("Mode".equalsIgnoreCase(parameterName)) {
                return "";
            }
            if ("Toggle".equalsIgnoreCase(parameterName) && !literalMode) {
                return "";
            }
            if ("Variable".equalsIgnoreCase(parameterName) && literalMode) {
                return "";
            }
        }
        if (node.isRandomRoundingParameter(parameter)) {
            return "";
        }
        if (type == NodeType.CREATE_LIST) {
            String paramName = parameter.getName();
            if ("UseRadius".equalsIgnoreCase(paramName)
                || "Radius".equalsIgnoreCase(paramName)
                || "UseBlockCap".equalsIgnoreCase(paramName)
                || "MaxBlocks".equalsIgnoreCase(paramName)) {
                return "";
            }
        }
        if (type == NodeType.USE) {
            String paramName = parameter.getName();
            if ("UseDurationSeconds".equalsIgnoreCase(paramName) || "UseAmount".equalsIgnoreCase(paramName)) {
                return "";
            }
        }
        if (type == NodeType.SWING) {
            String paramName = parameter.getName();
            if ("Duration".equalsIgnoreCase(paramName) || "UseAmount".equalsIgnoreCase(paramName)) {
                return "";
            }
        }
        if ("State".equalsIgnoreCase(parameter.getName()) && !node.shouldShowStateParameter()) {
            return "";
        }
        if (type == NodeType.SENSOR_FABRIC_EVENT) {
            String paramName = parameter.getName();
            if ("Amount".equalsIgnoreCase(paramName) || "UseAmount".equalsIgnoreCase(paramName)) {
                return "";
            }
        }
        String name = getParameterDisplayName(node, parameter);
        String text = name + ": " + parameter.getDisplayValue();
        if (text.length() <= node.getMaxParameterLabelLength()) {
            return text;
        }
        int maxContentLength = Math.max(0, node.getMaxParameterLabelLength() - 3);
        return text.substring(0, maxContentLength) + "...";
    }

    static boolean isAttributeDetectionSensor(Node node) {
        return node.getType() == NodeType.SENSOR_ATTRIBUTE_DETECTION;
    }

    static void normalizeAttributeDetectionParameters(Node node) {
        if (!isAttributeDetectionSensor(node)) {
            return;
        }
        List<NodeParameter> parameters = node.getParameters();
        parameters.removeIf(param -> param != null && "Operator".equalsIgnoreCase(param.getName()));
        ensureAttributeDetectionParameter(node, "Attribute", AttributeDetectionConfig.AttributeOption.NAME.id(), 0);
        ensureAttributeDetectionParameter(node, "Value", "", 1);

        AttributeDetectionConfig.TargetKind targetKind = null;
        Node targetNode = node.getAttachedParameter(0);
        if (targetNode != null) {
            targetKind = AttributeDetectionConfig.inferTargetKind(targetNode.getType());
        }

        NodeParameter attributeParameter = node.getParameter("Attribute");
        NodeParameter valueParameter = node.getParameter("Value");
        if (attributeParameter == null || valueParameter == null) {
            return;
        }

        AttributeDetectionConfig.AttributeOption attribute = AttributeDetectionConfig.getAttribute(attributeParameter.getStringValue());
        if (attribute == null || (targetKind != null && !attribute.supports(targetKind))) {
            attribute = AttributeDetectionConfig.getDefaultAttribute(targetKind);
            attributeParameter.setStringValue(attribute.id());
        }

        String normalizedValue = valueParameter.getStringValue();
        if (attribute.valueType() == AttributeDetectionConfig.ValueType.BOOLEAN) {
            if (normalizedValue == null || normalizedValue.isBlank()) {
                normalizedValue = AttributeDetectionConfig.getDefaultValue(attribute);
            } else {
                normalizedValue = Boolean.toString(parseBooleanLike(normalizedValue));
            }
            valueParameter.setStringValue(normalizedValue);
        }
    }

    static boolean parseBooleanLike(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return "true".equals(normalized)
            || "1".equals(normalized)
            || "yes".equals(normalized)
            || "on".equals(normalized);
    }

    private static String formatVillagerTradeDisplayValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (!value.contains("|") || !value.contains("@")) {
            return value;
        }
        String[] parts = value.split("\\|");
        TradeKeyPart first = parts.length > 0 ? parseTradeKeyPart(parts[0]) : null;
        TradeKeyPart second = parts.length > 1 ? parseTradeKeyPart(parts[1]) : null;
        TradeKeyPart sell = parts.length > 2 ? parseTradeKeyPart(parts[2]) : null;
        if (sell == null || !sell.isValid()) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        if (first != null && first.isValid()) {
            builder.append(first.format());
        }
        if (second != null && second.isValid()) {
            if (builder.length() > 0) {
                builder.append(" + ");
            }
            builder.append(second.format());
        }
        if (builder.length() > 0) {
            builder.append(" -> ");
        }
        builder.append(sell.format());
        return builder.toString();
    }

    private static TradeKeyPart parseTradeKeyPart(String part) {
        if (part == null || part.isEmpty() || "none@0".equals(part)) {
            return TradeKeyPart.empty();
        }
        int atIndex = part.indexOf('@');
        if (atIndex <= 0) {
            return TradeKeyPart.empty();
        }
        String itemId = part.substring(0, atIndex);
        String countRaw = part.substring(atIndex + 1);
        int count = 1;
        try {
            count = Math.max(1, Integer.parseInt(countRaw));
        } catch (NumberFormatException ignored) {
            count = 1;
        }
        Identifier identifier = Identifier.tryParse(itemId);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) {
            return TradeKeyPart.empty();
        }
        return new TradeKeyPart(Registries.ITEM.get(identifier).getName().getString(), count);
    }

    private static final class TradeKeyPart {
        private static final TradeKeyPart EMPTY = new TradeKeyPart("", 0);
        private final String name;
        private final int count;

        private TradeKeyPart(String name, int count) {
            this.name = name;
            this.count = count;
        }

        private static TradeKeyPart empty() {
            return EMPTY;
        }

        private boolean isValid() {
            return name != null && !name.isEmpty() && count > 0;
        }

        private String format() {
            if (count > 1) {
                return count + "x " + name;
            }
            return name;
        }
    }

    private static void ensureAttributeDetectionParameter(Node node, String name, String defaultValue, int targetIndex) {
        if (node.getParameter(name) != null) {
            return;
        }
        List<NodeParameter> parameters = node.getParameters();
        NodeParameter parameter = new NodeParameter(name, ParameterType.STRING, defaultValue);
        int insertIndex = Math.max(0, Math.min(targetIndex, parameters.size()));
        parameters.add(insertIndex, parameter);
    }
}
