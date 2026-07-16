package com.pathmind.nodes;

import java.util.List;

final class NodeBooleanParameters {
    private NodeBooleanParameters() {
    }

    static boolean isBooleanModeLiteral(Node node, String literalModeValue, String variableModeValue) {
        if (node.getType() != NodeType.PARAM_BOOLEAN) {
            return false;
        }
        ensureBooleanParameters(node, literalModeValue);
        NodeParameter modeParameter = node.getParameter("Mode");
        String rawMode = modeParameter != null ? modeParameter.getStringValue() : null;
        if (rawMode != null && !rawMode.trim().isEmpty()) {
            return !variableModeValue.equalsIgnoreCase(rawMode.trim());
        }
        NodeParameter variableParameter = node.getParameter("Variable");
        String variableValue = variableParameter != null ? variableParameter.getStringValue() : null;
        return variableValue == null || variableValue.trim().isEmpty();
    }

    static void setBooleanModeLiteral(Node node, boolean literalMode, String literalModeValue, String variableModeValue) {
        if (node.getType() != NodeType.PARAM_BOOLEAN) {
            return;
        }
        ensureBooleanParameters(node, literalModeValue);
        String modeValue = literalMode ? literalModeValue : variableModeValue;
        node.setParameterValueAndPropagate("Mode", modeValue);
        NodeParameter modeParameter = node.getParameter("Mode");
        if (modeParameter != null) {
            modeParameter.setUserEdited(true);
        }
    }

    static void ensureBooleanParameters(Node node, String literalModeValue) {
        if (node.getType() != NodeType.PARAM_BOOLEAN) {
            return;
        }
        ensureBooleanParameter(node, "boolean_mode", "Mode", ParameterType.STRING, literalModeValue, 0);
        ensureBooleanParameter(node, "boolean_toggle", "Toggle", ParameterType.BOOLEAN, "true", 1);
        ensureBooleanParameter(node, "boolean_variable", "Variable", ParameterType.STRING, "", 2);
    }

    private static void ensureBooleanParameter(Node node, String id, String name, ParameterType parameterType, String defaultValue, int targetIndex) {
        if (node.getParameter(name) != null) {
            return;
        }
        List<NodeParameter> parameters = node.getParameters();
        NodeParameter parameter = new NodeParameter(id, name, parameterType, defaultValue);
        int insertIndex = Math.max(0, Math.min(targetIndex, parameters.size()));
        parameters.add(insertIndex, parameter);
    }
}
