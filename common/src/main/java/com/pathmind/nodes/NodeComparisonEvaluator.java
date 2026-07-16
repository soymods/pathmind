package com.pathmind.nodes;

import com.pathmind.execution.ExecutionManager;
import com.pathmind.util.BlockSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.world.item.ItemStack;

final class NodeComparisonEvaluator {
    private final Node owner;

    NodeComparisonEvaluator(Node owner) {
        this.owner = owner;
    }

    Optional<Boolean> compareComparisonOperands(Node left, Node right) {
        if (left == null || right == null) {
            return Optional.empty();
        }
        if (isComparisonGroupOperator(left)) {
            return compareGroupOperand(left, right);
        }
        if (isComparisonGroupOperator(right)) {
            return compareGroupOperand(right, left);
        }
        if (left.getType() == NodeType.VARIABLE) {
            left = owner.resolveVariableValueNode(left, 0, null);
        }
        if (right.getType() == NodeType.VARIABLE) {
            right = owner.resolveVariableValueNode(right, 1, null);
        }
        if (left == null || right == null) {
            return Optional.empty();
        }
        return compareParameterNodes(left, right);
    }

    private Optional<Boolean> compareGroupOperand(Node groupNode, Node comparisonNode) {
        if (!isComparisonGroupOperator(groupNode) || comparisonNode == null) {
            return Optional.empty();
        }
        boolean requireAllMatches = groupNode.getType() == NodeType.OPERATOR_BOOLEAN_AND;
        boolean sawComparableOption = false;
        for (int slotIndex = 0; slotIndex < groupNode.getParameterSlotCount(); slotIndex++) {
            Node option = groupNode.getAttachedParameter(slotIndex);
            if (option == null) {
                continue;
            }
            Optional<Boolean> comparison = compareComparisonOperands(option, comparisonNode);
            if (comparison.isEmpty()) {
                continue;
            }
            sawComparableOption = true;
            if (requireAllMatches) {
                if (!comparison.get()) {
                    return Optional.of(false);
                }
            } else if (comparison.get()) {
                return Optional.of(true);
            }
        }
        if (!sawComparableOption) {
            return Optional.empty();
        }
        return Optional.of(requireAllMatches);
    }

    private boolean isComparisonGroupOperator(Node node) {
        if (node == null) {
            return false;
        }
        return node.getType() == NodeType.OPERATOR_BOOLEAN_OR
            || node.getType() == NodeType.OPERATOR_BOOLEAN_AND;
    }

    Optional<Boolean> resolveBooleanOperandWithVariables(Node operand, int slotIndex) {
        if (operand == null) {
            return Optional.empty();
        }
        if (operand.isSensorNode() && NodeCatalog.isBooleanSensor(operand.getType())) {
            return Optional.of(operand.evaluateSensor());
        }
        if (operand.getType() == NodeType.VARIABLE) {
            Node resolved = owner.resolveVariableValueNode(operand, slotIndex, null);
            if (resolved == null) {
                return Optional.empty();
            }
            return resolveBooleanFromNode(resolved);
        }
        return resolveBooleanFromNode(operand);
    }

    Optional<Boolean> resolveBooleanFromNode(Node node) {
        if (node == null) {
            return Optional.empty();
        }
        if (node.getType() == NodeType.PARAM_BOOLEAN) {
            node.ensureBooleanParameters();
            if (node.isBooleanModeVariable()) {
                NodeParameter variableParameter = node.getParameter("Variable");
                String variableValue = variableParameter != null ? variableParameter.getStringValue() : null;
                return node.resolveBooleanValueFromRaw(variableValue, true);
            }
            NodeParameter parameter = node.getParameter("Toggle");
            String value = parameter != null ? parameter.getStringValue() : null;
            if ((value == null || value.trim().isEmpty()) && parameter != null) {
                value = parameter.getDefaultValue();
            }
            return node.resolveBooleanValueFromRaw(value, false);
        }
        return Optional.empty();
    }

    private Optional<Boolean> compareVariableNodes(Node left, Node right) {
        if (left == null || right == null) {
            return Optional.empty();
        }
        boolean leftIsVariable = left.getType() == NodeType.VARIABLE;
        boolean rightIsVariable = right.getType() == NodeType.VARIABLE;
        ExecutionManager manager = ExecutionManager.getInstance();
        Node startNode = owner.getOwningStartNode();
        if (startNode == null && owner.getParentControl() != null) {
            startNode = owner.getParentControl().getOwningStartNode();
        }
        if (leftIsVariable && rightIsVariable) {
            String leftName = Node.getParameterString(left, "Variable");
            String rightName = Node.getParameterString(right, "Variable");
            if (leftName == null || leftName.trim().isEmpty() || rightName == null || rightName.trim().isEmpty()) {
                return Optional.empty();
            }
            ExecutionManager.RuntimeVariable leftVar = manager.getRuntimeVariable(
                startNode, leftName.trim(), left.getRuntimeValueScope());
            ExecutionManager.RuntimeVariable rightVar = manager.getRuntimeVariable(
                startNode, rightName.trim(), right.getRuntimeValueScope());
            if (leftVar == null || rightVar == null) {
                return Optional.empty();
            }
            Node leftSnapshot = createRuntimeVariableSnapshot(leftVar);
            Node rightSnapshot = createRuntimeVariableSnapshot(rightVar);
            if (leftSnapshot == null || rightSnapshot == null) {
                return Optional.empty();
            }
            return compareParameterNodes(leftSnapshot, rightSnapshot);
        }
        Node variableNode = leftIsVariable ? left : right;
        Node valueNode = leftIsVariable ? right : left;
        String variableName = Node.getParameterString(variableNode, "Variable");
        if (variableName == null || variableName.trim().isEmpty()) {
            return Optional.empty();
        }
        ExecutionManager.RuntimeVariable variable = manager.getRuntimeVariable(
            startNode, variableName.trim(), variableNode.getRuntimeValueScope());
        if (variable == null) {
            return Optional.empty();
        }
        Node variableSnapshot = createRuntimeVariableSnapshot(variable);
        if (variableSnapshot == null) {
            return Optional.empty();
        }
        return compareParameterNodes(variableSnapshot, valueNode);
    }

    Node createRuntimeVariableSnapshot(ExecutionManager.RuntimeVariable runtimeVariable) {
        if (runtimeVariable == null || runtimeVariable.getType() == null) {
            return null;
        }
        NodeType runtimeType = runtimeVariable.getType();
        NodeType snapshotType = runtimeType == NodeType.LIST_LENGTH
            ? NodeType.PARAM_AMOUNT
            : runtimeType;
        Node snapshot = new Node(snapshotType, 0, 0);
        snapshot.setSocketsHidden(true);
        Map<String, String> values = runtimeVariable.getValues();
        if (!values.isEmpty()) {
            snapshot.applyParameterValuesFromMap(values);
        }
        return snapshot;
    }

    Optional<Boolean> compareParameterNodes(Node left, Node right) {
        if (left == null || right == null) {
            return Optional.empty();
        }
        Optional<Boolean> leftBoolean = resolveComparableBoolean(left);
        Optional<Boolean> rightBoolean = resolveComparableBoolean(right);
        if (leftBoolean.isPresent() && rightBoolean.isPresent()) {
            return Optional.of(leftBoolean.get().equals(rightBoolean.get()));
        }
        if (leftBoolean.isPresent() || rightBoolean.isPresent()) {
            return Optional.empty();
        }
        Map<String, String> leftValues = left.exportParameterValues();
        Map<String, String> rightValues = right.exportParameterValues();
        Optional<Boolean> emptyTargetedBlockComparison = compareEmptyTargetedBlockValues(left, leftValues, right, rightValues);
        if (emptyTargetedBlockComparison.isPresent()) {
            return emptyTargetedBlockComparison;
        }
        if (leftValues != null && !leftValues.isEmpty() && rightValues != null && !rightValues.isEmpty()) {
            Optional<Boolean> blockComparison = compareBlockSelectionValues(leftValues, rightValues);
            if (blockComparison.isPresent()) {
                return blockComparison;
            }
            Optional<Boolean> entityComparison = compareEntitySelectionValues(leftValues, rightValues);
            if (entityComparison.isPresent()) {
                return entityComparison;
            }
            Optional<Boolean> inventorySlotComparison = compareInventorySlotValues(left, leftValues, right, rightValues);
            if (inventorySlotComparison.isPresent()) {
                return inventorySlotComparison;
            }
            Optional<Boolean> itemComparison = compareItemSelectionValues(leftValues, rightValues);
            if (itemComparison.isPresent()) {
                return itemComparison;
            }
        }
        Optional<Double> leftNumber = resolveComparableNumber(left);
        Optional<Double> rightNumber = resolveComparableNumber(right);
        if (leftNumber.isPresent() && rightNumber.isPresent()) {
            return Optional.of(Double.compare(leftNumber.get(), rightNumber.get()) == 0);
        }
        if (leftNumber.isPresent() || rightNumber.isPresent()) {
            return Optional.empty();
        }
        Optional<String> leftString = resolveComparableString(left);
        Optional<String> rightString = resolveComparableString(right);
        if (leftString.isPresent() && rightString.isPresent()) {
            String l = leftString.get();
            String r = rightString.get();
            return Optional.of(l.equalsIgnoreCase(r));
        }
        if (leftString.isPresent() || rightString.isPresent()) {
            return Optional.empty();
        }
        if (leftValues == null || rightValues == null || leftValues.isEmpty() || rightValues.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(canonicalizeValueMap(leftValues).equals(canonicalizeValueMap(rightValues)));
    }

    private Map<String, String> canonicalizeValueMap(Map<String, String> values) {
        Map<String, String> canonical = new TreeMap<>();
        if (values == null || values.isEmpty()) {
            return canonical;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            String normalizedKey = Node.normalizeParameterKey(entry.getKey());
            if (normalizedKey.isEmpty()) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (value.isEmpty()) {
                continue;
            }
            canonical.putIfAbsent(normalizedKey, value);
        }
        return canonical;
    }

    String formatCanonicalValueMap(Map<String, String> values) {
        Map<String, String> canonical = canonicalizeValueMap(values);
        if (canonical.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : canonical.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private Optional<Boolean> resolveComparableBoolean(Node node) {
        if (node == null) {
            return Optional.empty();
        }
        if (node.getType() == NodeType.LIST_ITEM) {
            Node resolved = owner.resolveListItemValueNode(node, null, false, null);
            return resolved != null ? resolveComparableBoolean(resolved) : Optional.empty();
        }
        if (node.isSensorNode() && NodeCatalog.isBooleanSensor(node.getType())) {
            return Optional.of(node.evaluateSensor());
        }
        return resolveBooleanFromNode(node);
    }

    private Optional<Boolean> compareEmptyTargetedBlockValues(Node left, Map<String, String> leftValues,
                                                              Node right, Map<String, String> rightValues) {
        if (left == null || right == null) {
            return Optional.empty();
        }
        boolean leftMissingTargetedBlock = left.getType() == NodeType.SENSOR_TARGETED_BLOCK
            && (leftValues == null || leftValues.isEmpty());
        boolean rightMissingTargetedBlock = right.getType() == NodeType.SENSOR_TARGETED_BLOCK
            && (rightValues == null || rightValues.isEmpty());

        if (leftMissingTargetedBlock && rightMissingTargetedBlock) {
            return Optional.of(true);
        }
        if (leftMissingTargetedBlock && isBlockComparableNode(right)) {
            return Optional.of(false);
        }
        if (rightMissingTargetedBlock && isBlockComparableNode(left)) {
            return Optional.of(false);
        }
        boolean leftMissingTargetedEntity = left.getType() == NodeType.SENSOR_TARGETED_ENTITY
            && (leftValues == null || leftValues.isEmpty());
        boolean rightMissingTargetedEntity = right.getType() == NodeType.SENSOR_TARGETED_ENTITY
            && (rightValues == null || rightValues.isEmpty());
        if (leftMissingTargetedEntity && rightMissingTargetedEntity) {
            return Optional.of(true);
        }
        if (leftMissingTargetedEntity && isEntityComparableNode(right)) {
            return Optional.of(false);
        }
        if (rightMissingTargetedEntity && isEntityComparableNode(left)) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    private boolean isBlockComparableNode(Node node) {
        if (node == null) {
            return false;
        }
        if (node.getType() == NodeType.PARAM_BLOCK || node.getType() == NodeType.SENSOR_TARGETED_BLOCK) {
            return true;
        }
        Map<String, String> values = node.exportParameterValues();
        return values != null && !owner.getRuntimeValue(values, "block").isEmpty();
    }

    private boolean isEntityComparableNode(Node node) {
        if (node == null) {
            return false;
        }
        if (node.getType() == NodeType.PARAM_ENTITY || node.getType() == NodeType.SENSOR_TARGETED_ENTITY) {
            return true;
        }
        Map<String, String> values = node.exportParameterValues();
        return values != null && !owner.getRuntimeValue(values, "entity").isEmpty();
    }

    private Optional<Boolean> compareBlockSelectionValues(Map<String, String> leftValues, Map<String, String> rightValues) {
        String leftBlock = owner.getRuntimeValue(leftValues, "block");
        String rightBlock = owner.getRuntimeValue(rightValues, "block");
        if (leftBlock.isEmpty() || rightBlock.isEmpty()) {
            return Optional.empty();
        }
        boolean leftWildcard = Node.isAnySelectionValue(leftBlock);
        boolean rightWildcard = Node.isAnySelectionValue(rightBlock);
        String leftCombined = normalizeBlockSelection(leftBlock, "");
        String rightCombined = normalizeBlockSelection(rightBlock, "");
        if (!leftWildcard && !rightWildcard && (leftCombined.isEmpty() || rightCombined.isEmpty())) {
            return Optional.empty();
        }
        if (!leftWildcard && !rightWildcard && !leftCombined.equalsIgnoreCase(rightCombined)) {
            return Optional.of(false);
        }
        String leftState = owner.getRuntimeValue(leftValues, "state");
        String rightState = owner.getRuntimeValue(rightValues, "state");
        return Optional.of(statesMatch(leftState, rightState));
    }

    private Optional<Boolean> compareEntitySelectionValues(Map<String, String> leftValues, Map<String, String> rightValues) {
        String leftEntity = owner.getRuntimeValue(leftValues, "entity");
        String rightEntity = owner.getRuntimeValue(rightValues, "entity");
        if (leftEntity.isEmpty() || rightEntity.isEmpty()) {
            return Optional.empty();
        }
        boolean leftWildcard = Node.isAnySelectionValue(leftEntity);
        boolean rightWildcard = Node.isAnySelectionValue(rightEntity);
        String leftCombined = normalizeEntitySelection(leftEntity, "");
        String rightCombined = normalizeEntitySelection(rightEntity, "");
        if (!leftWildcard && !rightWildcard && (leftCombined.isEmpty() || rightCombined.isEmpty())) {
            return Optional.empty();
        }
        if (!leftWildcard && !rightWildcard && !leftCombined.equalsIgnoreCase(rightCombined)) {
            return Optional.of(false);
        }
        String leftState = owner.getRuntimeValue(leftValues, "state");
        String rightState = owner.getRuntimeValue(rightValues, "state");
        return Optional.of(statesMatch(leftState, rightState));
    }

    private Optional<Boolean> compareItemSelectionValues(Map<String, String> leftValues, Map<String, String> rightValues) {
        List<String> leftItems = resolveComparableItemSelections(leftValues);
        List<String> rightItems = resolveComparableItemSelections(rightValues);
        if (leftItems.isEmpty() || rightItems.isEmpty()) {
            return Optional.empty();
        }
        if (!selectionsOverlap(leftItems, rightItems)) {
            return Optional.of(false);
        }

        Optional<Integer> leftCount = resolveComparableItemCount(leftValues);
        Optional<Integer> rightCount = resolveComparableItemCount(rightValues);
        if (leftCount.isPresent() && rightCount.isPresent()) {
            return Optional.of(leftCount.get().intValue() == rightCount.get().intValue());
        }
        return Optional.of(true);
    }

    private Optional<Boolean> compareInventorySlotValues(Node left, Map<String, String> leftValues,
                                                         Node right, Map<String, String> rightValues) {
        boolean leftIsSlot = isInventorySlotComparableNode(left, leftValues);
        boolean rightIsSlot = isInventorySlotComparableNode(right, rightValues);
        if (!leftIsSlot && !rightIsSlot) {
            return Optional.empty();
        }

        if (leftIsSlot && rightIsSlot) {
            Integer leftSlot = resolveComparableSlotIndex(leftValues);
            Integer rightSlot = resolveComparableSlotIndex(rightValues);
            if (leftSlot == null || rightSlot == null) {
                return Optional.empty();
            }
            return Optional.of(
                leftSlot.intValue() == rightSlot.intValue()
                    && resolveComparableSlotSelectionType(leftValues) == resolveComparableSlotSelectionType(rightValues)
            );
        }

        Map<String, String> slotValues = leftIsSlot ? leftValues : rightValues;
        Map<String, String> itemValues = leftIsSlot ? rightValues : leftValues;
        List<String> itemSelections = resolveComparableItemSelections(itemValues);
        if (itemSelections.isEmpty()) {
            return Optional.empty();
        }

        // Prefer the slot's already-exported item/count snapshot when available so
        // LIST_ITEM(gui) comparisons do not depend on a second live handler lookup.
        List<String> slotSelections = resolveComparableItemSelections(slotValues);
        if (!slotSelections.isEmpty()) {
            if (!selectionsOverlap(slotSelections, itemSelections)) {
                return Optional.of(false);
            }

            Optional<Integer> slotCount = resolveComparableItemCount(slotValues);
            Optional<Integer> requiredCount = resolveComparableItemCount(itemValues);
            if (slotCount.isPresent() && requiredCount.isPresent()) {
                return Optional.of(slotCount.get().intValue() == requiredCount.get().intValue());
            }
            return Optional.of(true);
        }

        ItemStack stack = resolveComparableInventorySlotStack(slotValues);
        if (stack == null || stack.isEmpty()) {
            return Optional.of(false);
        }
        if (!owner.stackMatchesAnyItem(stack, itemSelections)) {
            return Optional.of(false);
        }

        Optional<Integer> requiredCount = resolveComparableItemCount(itemValues);
        if (requiredCount.isPresent()) {
            return Optional.of(stack.getCount() == requiredCount.get().intValue());
        }
        return Optional.of(true);
    }

    private boolean isInventorySlotComparableNode(Node node, Map<String, String> values) {
        if (node != null && node.getType() == NodeType.PARAM_INVENTORY_SLOT) {
            return true;
        }
        return resolveComparableSlotIndex(values) != null;
    }

    private Integer resolveComparableSlotIndex(Map<String, String> values) {
        return InventorySlotValueResolver.resolveComparableSlotIndex(values);
    }

    private SlotSelectionType resolveComparableSlotSelectionType(Map<String, String> values) {
        return InventorySlotValueResolver.resolveComparableSlotSelectionType(values);
    }

    private ItemStack resolveComparableInventorySlotStack(Map<String, String> values) {
        return InventorySlotValueResolver.resolveComparableInventorySlotStack(values);
    }

    private List<String> resolveComparableItemSelections(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> itemIds = new ArrayList<>();
        for (String entry : owner.splitMultiValueList(owner.getRuntimeValue(values, "items"))) {
            owner.addItemIdentifier(itemIds, entry);
        }
        for (String entry : owner.splitMultiValueList(owner.getRuntimeValue(values, "item"))) {
            owner.addItemIdentifier(itemIds, entry);
        }
        return itemIds;
    }

    private Optional<Integer> resolveComparableItemCount(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        Integer count = Node.parseIntOrNull(owner.getRuntimeValue(values, "count"));
        if (count != null) {
            return Optional.of(count);
        }
        Integer amount = Node.parseIntOrNull(owner.getRuntimeValue(values, "amount"));
        return amount != null ? Optional.of(amount) : Optional.empty();
    }

    private boolean selectionsOverlap(List<String> leftValues, List<String> rightValues) {
        if (leftValues == null || rightValues == null || leftValues.isEmpty() || rightValues.isEmpty()) {
            return false;
        }
        for (String left : leftValues) {
            String normalizedLeft = normalizeComparableItemSelection(left);
            if (normalizedLeft.isEmpty()) {
                continue;
            }
            for (String right : rightValues) {
                String normalizedRight = normalizeComparableItemSelection(right);
                if (!normalizedRight.isEmpty() && normalizedLeft.equalsIgnoreCase(normalizedRight)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalizeComparableItemSelection(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = owner.sanitizeResourceId(value);
        if (sanitized == null || sanitized.isEmpty()) {
            return "";
        }
        return owner.normalizeResourceId(sanitized, "minecraft");
    }

    private boolean statesMatch(String leftState, String rightState) {
        boolean leftWildcard = Node.isAnySelectionValue(leftState);
        boolean rightWildcard = Node.isAnySelectionValue(rightState);
        if (leftWildcard || rightWildcard) {
            return true;
        }
        Set<String> leftParts = splitSelectionParts(leftState);
        Set<String> rightParts = splitSelectionParts(rightState);
        if (leftParts.isEmpty() || rightParts.isEmpty()) {
            return true;
        }
        return leftParts.containsAll(rightParts) || rightParts.containsAll(leftParts);
    }

    private Set<String> splitSelectionParts(String rawState) {
        if (Node.isAnySelectionValue(rawState)) {
            return Collections.emptySet();
        }
        Set<String> parts = new LinkedHashSet<>();
        if (rawState == null) {
            return parts;
        }
        for (String part : rawState.split(",")) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }

    private String normalizeEntitySelection(String entity, String state) {
        if (entity == null || entity.trim().isEmpty()) {
            return "";
        }
        String normalizedEntity = owner.normalizeResourceId(entity, "minecraft");
        if (normalizedEntity == null || normalizedEntity.isEmpty()) {
            return "";
        }
        String trimmedState = state == null ? "" : state.trim();
        if (trimmedState.isEmpty()) {
            return normalizedEntity;
        }
        return normalizedEntity + "[" + trimmedState.toLowerCase(Locale.ROOT) + "]";
    }

    private String normalizeBlockSelection(String block, String state) {
        if (block == null || block.trim().isEmpty()) {
            return "";
        }
        String normalizedBlock = owner.normalizeResourceId(block, "minecraft");
        if (normalizedBlock == null || normalizedBlock.isEmpty()) {
            return "";
        }
        String trimmedState = state == null ? "" : state.trim();
        if (trimmedState.isEmpty()) {
            return normalizedBlock;
        }
        return BlockSelection.combine(normalizedBlock, trimmedState).orElse(normalizedBlock + "[" + trimmedState + "]");
    }

    private Optional<String> resolveComparableString(Node node) {
        if (node == null) {
            return Optional.empty();
        }
        if (node.getType() == NodeType.LIST_ITEM) {
            Node resolved = owner.resolveListItemValueNode(node, null, false, null);
            return resolved != null ? resolveComparableString(resolved) : Optional.empty();
        }
        NodeBehaviorDefinition behaviorDefinition = NodeBehaviorDefinitionRegistry.get(node.getType());
        return behaviorDefinition != null ? behaviorDefinition.resolveComparableString(owner, node) : Optional.empty();
    }

    Optional<Double> resolveComparableNumber(Node node) {
        if (node == null) {
            return Optional.empty();
        }
        if (node.getType() == NodeType.LIST_ITEM) {
            Node resolved = owner.resolveListItemValueNode(node, null, false, null);
            return resolved != null ? resolveComparableNumber(resolved) : Optional.empty();
        }
        NodeBehaviorDefinition behaviorDefinition = NodeBehaviorDefinitionRegistry.get(node.getType());
        return behaviorDefinition != null ? behaviorDefinition.resolveComparableNumber(owner, node) : Optional.empty();
    }

    Optional<Double> resolveComparableNumberWithVariables(Node node, int slotIndex) {
        if (node == null) {
            return Optional.empty();
        }
        if (node.getType() == NodeType.VARIABLE) {
            Node resolved = owner.resolveVariableValueNode(node, slotIndex, null);
            if (resolved == null) {
                return Optional.empty();
            }
            return resolveComparableNumber(resolved);
        }
        return resolveComparableNumber(node);
    }

}
