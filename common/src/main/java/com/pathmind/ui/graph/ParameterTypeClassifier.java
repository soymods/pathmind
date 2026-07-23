package com.pathmind.ui.graph;

import java.util.List;

import com.pathmind.nodes.AttributeDetectionConfig;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.ParameterType;

/**
 * Pure classification of node parameters by their semantic kind (block, item,
 * entity, direction, hand, etc.). These predicates depend only on the node,
 * its parameters, and static config -- never on editor UI state -- so they live
 * apart from {@link NodeGraph} and are consumed there via static import.
 */
final class ParameterTypeClassifier {

    private ParameterTypeClassifier() {
    }

    static boolean isPlayerParameter(Node node, NodeParameter parameter) {
        if (node == null || parameter == null) {
            return false;
        }
        if (node.getType() != NodeType.PARAM_PLAYER) {
            return false;
        }
        return "Player".equalsIgnoreCase(parameter.getName());
    }

    static boolean isMessageParameter(Node node, NodeParameter parameter) {
        if (node == null || parameter == null) {
            return false;
        }
        if (node.getType() != NodeType.PARAM_MESSAGE) {
            return false;
        }
        return "Text".equalsIgnoreCase(parameter.getName()) || "Message".equalsIgnoreCase(parameter.getName());
    }

    static boolean isSeedParameter(Node node, NodeParameter parameter) {
        if (node == null || parameter == null) {
            return false;
        }
        if (node.getType() != NodeType.OPERATOR_RANDOM) {
            return false;
        }
        return "Seed".equalsIgnoreCase(parameter.getName());
    }

    static boolean isAmountParameter(Node node, NodeParameter parameter) {
        if (node == null || parameter == null) {
            return false;
        }
        if (node.getType() != NodeType.PARAM_AMOUNT) {
            return false;
        }
        return "Amount".equalsIgnoreCase(parameter.getName());
    }

    static boolean isTradeInlineParameter(Node node, NodeParameter parameter) {
        if (node == null || parameter == null || node.getType() != NodeType.TRADE) {
            return false;
        }
        return "Count".equalsIgnoreCase(parameter.getName());
    }

    static boolean isVillagerProfessionParameter(Node node, int index) {
        if (node == null || node.getType() != NodeType.PARAM_VILLAGER_TRADE
            || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter parameter = node.getParameters().get(index);
        return parameter != null && "Profession".equalsIgnoreCase(parameter.getName());
    }

    static boolean isVillagerTradeParameter(Node node, int index) {
        if (node == null || node.getType() != NodeType.PARAM_VILLAGER_TRADE
            || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter parameter = node.getParameters().get(index);
        if (parameter == null) {
            return false;
        }
        return "Item".equalsIgnoreCase(parameter.getName())
            || "Trade".equalsIgnoreCase(parameter.getName());
    }

    static boolean isVillagerTradeVariantParameter(Node node, int index) {
        if (node == null || node.getType() != NodeType.PARAM_VILLAGER_TRADE
            || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter parameter = node.getParameters().get(index);
        return parameter != null && "Variant".equalsIgnoreCase(parameter.getName());
    }

    static boolean isGuiParameter(Node node, NodeParameter parameter) {
        if (node == null) {
            return false;
        }
        if (node.getType() != NodeType.PARAM_GUI) {
            return false;
        }
        if (parameter != null) {
            return "GUI".equalsIgnoreCase(parameter.getName());
        }
        NodeParameter guiParam = node.getParameter("GUI");
        return guiParam != null;
    }

    static boolean isMouseButtonParameter(Node node, NodeParameter parameter) {
        if (node == null) {
            return false;
        }
        if (node.getType() != NodeType.PARAM_MOUSE_BUTTON) {
            return false;
        }
        if (parameter != null) {
            return "MouseButton".equalsIgnoreCase(parameter.getName());
        }
        NodeParameter mouseButtonParam = node.getParameter("MouseButton");
        return mouseButtonParam != null;
    }

    static boolean isHandParameter(Node node, NodeParameter parameter) {
        if (node == null) {
            return false;
        }
        if (node.getType() != NodeType.PARAM_HAND) {
            return false;
        }
        if (parameter != null) {
            return "Hand".equalsIgnoreCase(parameter.getName());
        }
        NodeParameter handParam = node.getParameter("Hand");
        return handParam != null;
    }

    static boolean isDirectionParameter(Node node, int index) {
        if (node == null || node.getType() != NodeType.PARAM_DIRECTION) {
            return false;
        }
        if (index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        return "Direction".equalsIgnoreCase(param.getName());
    }

    static boolean isBooleanLiteralParameter(Node node, int index) {
        if (node == null || node.getType() != NodeType.PARAM_BOOLEAN || !node.isBooleanModeLiteral()) {
            return false;
        }
        if (index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        return "Toggle".equalsIgnoreCase(param.getName());
    }

    static boolean isAttributeDetectionAttributeParameter(Node node, int index) {
        if (node == null || !node.isAttributeDetectionSensor() || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        return param != null && "Attribute".equalsIgnoreCase(param.getName());
    }

    static boolean isAttributeDetectionBooleanValueParameter(Node node, int index) {
        if (node == null || !node.isAttributeDetectionSensor() || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null || !"Value".equalsIgnoreCase(param.getName())) {
            return false;
        }
        AttributeDetectionConfig.AttributeOption attribute =
            AttributeDetectionConfig.getAttribute(node.getParameter("Attribute") != null
                ? node.getParameter("Attribute").getStringValue()
                : "");
        return attribute != null && attribute.valueType() == AttributeDetectionConfig.ValueType.BOOLEAN;
    }

    static boolean isAttributeDetectionDropdownParameter(Node node, int index) {
        return isAttributeDetectionAttributeParameter(node, index)
            || isAttributeDetectionBooleanValueParameter(node, index);
    }

    static boolean isInlineDropdownParameter(Node node, int index) {
        if (node == null || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        return isBooleanLiteralParameter(node, index)
            || isAttributeDetectionDropdownParameter(node, index);
    }

    static boolean isBlockFaceParameter(Node node, int index) {
        if (node == null || node.getType() != NodeType.PARAM_BLOCK_FACE) {
            return false;
        }
        if (index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        return "Face".equalsIgnoreCase(param.getName());
    }

    static boolean isListIndexParameter(Node node, int index) {
        if (node == null || node.getType() != NodeType.LIST_ITEM || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        return param != null && "Index".equalsIgnoreCase(param.getName());
    }

    static boolean isBlockItemParameter(Node node, int index) {
        if (node == null || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        if (param.getType() == ParameterType.BLOCK_TYPE) {
            return true;
        }
        String name = param.getName();
        return isBlockStateParameter(node, index)
            || isEntityStateParameter(node, index)
            || "Block".equalsIgnoreCase(name)
            || "Blocks".equalsIgnoreCase(name)
            || "Item".equalsIgnoreCase(name)
            || "Entity".equalsIgnoreCase(name);
    }

    static boolean isBlockStateParameter(Node node, int index) {
        if (node == null || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        if (node.getType() != NodeType.PARAM_BLOCK) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        return "State".equalsIgnoreCase(param.getName());
    }

    static boolean isEntityStateParameter(Node node, int index) {
        if (node == null || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        if (node.getType() != NodeType.PARAM_ENTITY) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        return "State".equalsIgnoreCase(param.getName());
    }

    static boolean isBlockParameter(Node node, int index) {
        if (node == null || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        if (param.getType() == ParameterType.BLOCK_TYPE) {
            return true;
        }
        String name = param.getName();
        return "Block".equalsIgnoreCase(name) || "Blocks".equalsIgnoreCase(name);
    }

    static boolean isItemParameter(Node node, int index) {
        if (node == null || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        return "Item".equalsIgnoreCase(param.getName());
    }

    static boolean isEntityParameter(Node node, int index) {
        if (node == null || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        return "Entity".equalsIgnoreCase(param.getName());
    }

    static boolean isFabricEventSensorParameter(Node node, int index) {
        if (node == null || node.getType() != NodeType.SENSOR_FABRIC_EVENT || index < 0) {
            return false;
        }
        List<NodeParameter> parameters = node.getParameters();
        if (parameters == null || index >= parameters.size()) {
            return false;
        }
        NodeParameter parameter = parameters.get(index);
        return parameter != null
            && parameter.getType() == ParameterType.STRING
            && "Event".equals(parameter.getName());
    }
}
