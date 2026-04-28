package com.pathmind.nodes;

import java.util.List;

final class NodeDirectionParameters {
    private NodeDirectionParameters() {
    }

    static boolean isDirectionModeExact(Node node, String exactModeValue, String cardinalModeValue, double defaultDirectionDistance) {
        if (node.getType() != NodeType.PARAM_DIRECTION) {
            return false;
        }
        ensureCombinedDirectionParameters(node, exactModeValue, cardinalModeValue, defaultDirectionDistance);
        NodeParameter modeParameter = node.getParameter("Mode");
        String rawMode = modeParameter != null ? modeParameter.getStringValue() : null;
        if (rawMode != null && !rawMode.trim().isEmpty()) {
            return !cardinalModeValue.equalsIgnoreCase(rawMode.trim());
        }
        NodeParameter directionParameter = node.getParameter("Direction");
        String directionValue = directionParameter != null ? directionParameter.getStringValue() : null;
        return directionValue == null || directionValue.trim().isEmpty();
    }

    static void setDirectionModeExact(Node node, boolean exact, String exactModeValue, String cardinalModeValue, double defaultDirectionDistance) {
        if (node.getType() != NodeType.PARAM_DIRECTION) {
            return;
        }
        ensureCombinedDirectionParameters(node, exactModeValue, cardinalModeValue, defaultDirectionDistance);
        String modeValue = exact ? exactModeValue : cardinalModeValue;
        node.setParameterValueAndPropagate("Mode", modeValue);
        NodeParameter modeParameter = node.getParameter("Mode");
        if (modeParameter != null) {
            modeParameter.setUserEdited(true);
        }
    }

    static void ensureCombinedDirectionParameters(Node node, String exactModeValue, String cardinalModeValue, double defaultDirectionDistance) {
        if (node.getType() != NodeType.PARAM_DIRECTION) {
            return;
        }
        NodeParameter directionParameter = node.getParameter("Direction");
        String directionValue = directionParameter != null ? directionParameter.getStringValue() : null;
        String inferredMode = directionValue != null && !directionValue.trim().isEmpty()
            ? cardinalModeValue
            : exactModeValue;
        ensureDirectionParameter(node, "direction_mode", "Mode", ParameterType.STRING, inferredMode, 0);
        ensureDirectionParameter(node, "direction_cardinal", "Direction", ParameterType.STRING, "", 1);
        ensureDirectionParameter(node, "direction_yaw", "Yaw", ParameterType.DOUBLE, "0.0", 2);
        ensureDirectionParameter(node, "direction_pitch", "Pitch", ParameterType.DOUBLE, "0.0", 3);
        ensureDirectionParameter(node, "direction_yaw_offset", "YawOffset", ParameterType.DOUBLE, "0.0", 4);
        ensureDirectionParameter(node, "direction_pitch_offset", "PitchOffset", ParameterType.DOUBLE, "0.0", 5);
        ensureDirectionParameter(node, "direction_distance", "Distance", ParameterType.DOUBLE, Double.toString(defaultDirectionDistance), 6);
    }

    private static void ensureDirectionParameter(Node node, String id, String name, ParameterType parameterType, String defaultValue, int targetIndex) {
        if (node.getParameter(name) != null) {
            return;
        }
        List<NodeParameter> parameters = node.getParameters();
        NodeParameter parameter = new NodeParameter(id, name, parameterType, defaultValue);
        int insertIndex = Math.max(0, Math.min(targetIndex, parameters.size()));
        parameters.add(insertIndex, parameter);
    }
}
