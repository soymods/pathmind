package com.pathmind.nodes;

public final class RelativeInputSupport {
    private RelativeInputSupport() {
    }

    public static boolean isRelativeExpression(String value) {
        if (value == null) {
            return false;
        }
        return value.trim().startsWith("~");
    }

    public static Double resolveRelativeExpression(String value, double baseValue) {
        if (!isRelativeExpression(value)) {
            return null;
        }
        String trimmed = value.trim();
        String offsetExpression = trimmed.substring(1).trim();
        if (offsetExpression.isEmpty()) {
            return baseValue;
        }
        Double offset = Node.evaluateNumericExpression(offsetExpression);
        return offset != null ? baseValue + offset : null;
    }

    public static boolean supportsRelativeCoordinate(Node node, String parameterName) {
        if (node == null || parameterName == null) {
            return false;
        }
        if (!"X".equalsIgnoreCase(parameterName)
            && !"Y".equalsIgnoreCase(parameterName)
            && !"Z".equalsIgnoreCase(parameterName)) {
            return false;
        }
        NodeType type = node.getType();
        return type == NodeType.PARAM_COORDINATE
            || type == NodeType.PARAM_PLACE_TARGET
            || type == NodeType.PARAM_SCHEMATIC;
    }

    public static boolean supportsRelativeLook(Node node, String parameterName) {
        if (node == null || parameterName == null) {
            return false;
        }
        if (!"Yaw".equalsIgnoreCase(parameterName) && !"Pitch".equalsIgnoreCase(parameterName)) {
            return false;
        }
        NodeType type = node.getType();
        return type == NodeType.PARAM_DIRECTION || type == NodeType.PARAM_ROTATION;
    }
}
