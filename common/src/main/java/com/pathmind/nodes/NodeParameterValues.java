package com.pathmind.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class NodeParameterValues {
    private static final Set<String> MOVE_ITEM_SOURCE_KEYS = createParameterKeySet("SourceSlot", "FirstSlot", "Count", "Amount");
    private static final Set<String> MOVE_ITEM_TARGET_KEYS = createParameterKeySet("TargetSlot", "SecondSlot", "Count", "Amount");
    private static final Set<String> PLACE_POSITION_BLOCK_KEYS = createParameterKeySet("Block", "Blocks", "BlockId");
    private static final Set<String> HOTBAR_INVENTORY_SLOT_ITEM_KEYS = createParameterKeySet("Item", "Items", "Count", "Amount");

    private final Node owner;

    NodeParameterValues(Node owner) {
        this.owner = owner;
    }

    void initializeParameters() {
        NodeParameterDefaults.initialize(owner.getParameters(), owner.getType(), owner.getMode());
    }

    void resetParametersToDefaults() {
        if (owner.isParameterNode()) {
            return;
        }
        owner.getParameters().clear();
        initializeParameters();
    }

    NodeParameter getParameter(String name) {
        String normalizedId = NodeParameter.createDefaultId(name);
        for (NodeParameter param : owner.getParameters()) {
            if (param.getName().equals(name) || param.getId().equals(normalizedId)) {
                return param;
            }
        }
        if ("Duration".equals(name) && (owner.getType() == NodeType.WAIT || owner.getType() == NodeType.PARAM_DURATION)) {
            String defaultValue = owner.getType() == NodeType.PARAM_DURATION ? "" : "0.0";
            NodeParameter duration = new NodeParameter("Duration", ParameterType.DOUBLE, defaultValue);
            owner.getParameters().add(duration);
            return duration;
        }
        return null;
    }

    void setParameterValueAndPropagate(String name, String value) {
        if (name == null || value == null) {
            return;
        }

        NodeParameter parameter = getParameter(name);
        if (parameter != null) {
            parameter.setStringValue(value);
        }

        if (owner.getType() == NodeType.PARAM_ENTITY && "Entity".equalsIgnoreCase(name)) {
            NodeParameter stateParam = getParameter("State");
            if (stateParam != null && stateParam.getStringValue() != null && !stateParam.getStringValue().isEmpty()) {
                stateParam.setStringValue("");
            }
        }

        NodeAttachments attachments = owner.getAttachments();
        if (attachments.hasAttachedParameters()) {
            for (Node parameterNode : attachments.getAttachedParameterNodes()) {
                if (parameterNode == null || !parameterNode.isParameterNode()) {
                    continue;
                }
                if (isListIdentityParameter(owner, name) && isListIdentityParameter(parameterNode, name)) {
                    continue;
                }
                NodeParameter attachedParam = parameterNode.getParameter(name);
                if (attachedParam != null) {
                    attachedParam.setStringValue(value);
                    parameterNode.recalculateDimensions();
                }
            }
        }
    }

    boolean applyParameterValuesFromMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        boolean applied = false;
        for (NodeParameter target : owner.getParameters()) {
            String key = target.getName();
            String value = values.get(key);
            if (value == null) {
                value = values.get(Node.normalizeParameterKey(key));
            }
            if (value == null) {
                value = values.get(key.toLowerCase(Locale.ROOT));
            }
            if (value == null && "Resource".equalsIgnoreCase(key)) {
                value = values.get("Block");
                if (value == null) {
                    value = values.get(Node.normalizeParameterKey("Block"));
                }
                if (value == null) {
                    value = values.get("Blocks");
                }
                if (value == null) {
                    value = values.get(Node.normalizeParameterKey("Blocks"));
                }
                if (value == null) {
                    value = values.get("Item");
                }
                if (value == null) {
                    value = values.get(Node.normalizeParameterKey("Item"));
                }
                if (value == null) {
                    value = values.get("Entity");
                }
                if (value == null) {
                    value = values.get(Node.normalizeParameterKey("Entity"));
                }
                if (value == null) {
                    value = values.get("Player");
                }
                if (value == null) {
                    value = values.get(Node.normalizeParameterKey("Player"));
                }
            }
            if (value != null) {
                target.setStringValue(value);
                applied = true;
            }
        }
        return applied;
    }

    Map<String, String> adjustParameterValuesForSlot(Map<String, String> values, int slotIndex, Node parameterNode) {
        if (values == null || values.isEmpty() || slotIndex < 0) {
            return values;
        }
        return switch (owner.getType()) {
            case HOTBAR -> {
                if (parameterNode != null && parameterNode.getType() == NodeType.PARAM_INVENTORY_SLOT) {
                    Map<String, String> adjusted = new HashMap<>(filterParameterMap(values, HOTBAR_INVENTORY_SLOT_ITEM_KEYS));
                    adjusted.put("Item", "");
                    adjusted.put(Node.normalizeParameterKey("Item"), "");
                    yield adjusted;
                }
                yield values;
            }
            case CONTROL_REPEAT -> {
                if (parameterNode != null) {
                    if (!values.containsKey("Count")) {
                        String fallback = values.get("Amount");
                        if (fallback == null) {
                            fallback = values.get("Duration");
                        }
                        if (fallback == null) {
                            fallback = values.get("Value");
                        }
                        if (fallback != null) {
                            Map<String, String> adjusted = new HashMap<>(values);
                            adjusted.put("Count", fallback);
                            adjusted.put(Node.normalizeParameterKey("Count"), fallback);
                            yield adjusted;
                        }
                    }
                }
                yield values;
            }
            case MOVE_ITEM -> {
                if (slotIndex == 0) {
                    yield filterParameterMap(values, MOVE_ITEM_TARGET_KEYS);
                } else if (slotIndex == 1) {
                    yield filterParameterMap(values, MOVE_ITEM_SOURCE_KEYS);
                }
                yield values;
            }
            case PLACE, PLACE_HAND -> {
                if (slotIndex == 1 && parameterNode != null) {
                    NodeType parameterType = parameterNode.getType();
                    if (parameterType == NodeType.PARAM_BLOCK || parameterType == NodeType.PARAM_PLACE_TARGET) {
                        yield filterParameterMap(values, PLACE_POSITION_BLOCK_KEYS);
                    }
                }
                yield values;
            }
            case LOOK -> {
                if (slotIndex == 0 && parameterNode != null) {
                    Map<String, String> remapped = owner.remapSingleAxisLookValues(values, parameterNode);
                    if (remapped != values) {
                        yield remapped;
                    }
                }
                yield values;
            }
            default -> values;
        };
    }

    void refreshAttachedParameterValues() {
        if (owner.isParameterNode()) {
            return;
        }
        Map<String, String> existingValues = owner.exportParameterValues();
        resetParametersToDefaults();
        if (!existingValues.isEmpty()) {
            applyParameterValuesFromMap(existingValues);
        }
        NodeAttachments attachments = owner.getAttachments();
        if (!attachments.hasAttachedParameters()) {
            return;
        }
        List<Integer> slotIndices = new ArrayList<>(attachments.getAttachedParameterSlotIndices());
        Collections.sort(slotIndices);
        for (Integer slotIndex : slotIndices) {
            Node parameter = attachments.getAttachedParameter(slotIndex);
            if (parameter == null) {
                continue;
            }
            Map<String, String> exported = parameter.exportParameterValues();
            if (!exported.isEmpty()) {
                Map<String, String> adjusted = adjustParameterValuesForSlot(exported, slotIndex, parameter);
                applyParameterValuesFromMap(adjusted);
            }
        }
    }

    private static Map<String, String> filterParameterMap(Map<String, String> values, Set<String> keysToRemove) {
        if (values == null || values.isEmpty() || keysToRemove == null || keysToRemove.isEmpty()) {
            return values;
        }
        boolean needsFiltering = false;
        for (String key : keysToRemove) {
            if (values.containsKey(key)) {
                needsFiltering = true;
                break;
            }
        }
        if (!needsFiltering) {
            return values;
        }
        Map<String, String> filtered = new HashMap<>(values);
        for (String key : keysToRemove) {
            filtered.remove(key);
        }
        return filtered;
    }

    private static Set<String> createParameterKeySet(String... keys) {
        Set<String> keySet = new HashSet<>();
        if (keys == null) {
            return keySet;
        }
        for (String key : keys) {
            if (key == null || key.isEmpty()) {
                continue;
            }
            keySet.add(key);
            keySet.add(key.toLowerCase(Locale.ROOT));
            keySet.add(Node.normalizeParameterKey(key));
        }
        return keySet;
    }

    private static boolean isListIdentityParameter(Node node, String name) {
        if (node == null || !"List".equals(name)) {
            return false;
        }
        return switch (node.getType()) {
            case CREATE_LIST, ADD_TO_LIST, REMOVE_FIRST_FROM_LIST, REMOVE_LAST_FROM_LIST,
                REMOVE_LIST_ITEM, REMOVE_FROM_LIST, LIST_ITEM, LIST_LENGTH -> true;
            default -> false;
        };
    }
}
