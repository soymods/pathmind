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
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class NodeComparisonEvaluator {
    private final Node owner;

    NodeComparisonEvaluator(Node owner) {
        this.owner = owner;
    }

    Optional<Boolean> compareComparisonOperands(Node left, Node right) {
        return evaluateComparisonOperands(left, right).value();
    }

    ComparisonEvaluation evaluateComparisonOperands(Node left, Node right) {
        if (left == null || right == null) {
            return ComparisonEvaluation.unresolved();
        }
        if (left.getType() == NodeType.VARIABLE) {
            left = owner.resolveVariableValueNode(left, 0, null);
        }
        if (right.getType() == NodeType.VARIABLE) {
            right = owner.resolveVariableValueNode(right, 1, null);
        }
        if (left == null || right == null) {
            return ComparisonEvaluation.unresolved();
        }
        String typeError = comparisonTypeError(left, right);
        if (typeError != null) {
            return ComparisonEvaluation.invalid(typeError);
        }
        if (isComparisonGroupOperator(left)) {
            return ComparisonEvaluation.resolved(compareGroupOperand(left, right));
        }
        if (isComparisonGroupOperator(right)) {
            return ComparisonEvaluation.resolved(compareGroupOperand(right, left));
        }
        return ComparisonEvaluation.resolved(compareParameterNodes(left, right));
    }

    ComparisonEvaluation evaluateOrderingOperands(Node left, Node right, boolean greater, boolean inclusive) {
        if (left == null || right == null) {
            return ComparisonEvaluation.unresolved();
        }
        if (left.getType() == NodeType.VARIABLE) {
            left = owner.resolveVariableValueNode(left, 0, null);
        }
        if (right.getType() == NodeType.VARIABLE) {
            right = owner.resolveVariableValueNode(right, 1, null);
        }
        if (left == null || right == null) {
            return ComparisonEvaluation.unresolved();
        }
        String typeError = orderingTypeError(left, right);
        if (typeError != null) {
            return ComparisonEvaluation.invalid(typeError);
        }
        Optional<Double> leftNumber = resolveComparableNumber(left);
        Optional<Double> rightNumber = resolveComparableNumber(right);
        if (leftNumber.isEmpty() || rightNumber.isEmpty()) {
            return ComparisonEvaluation.unresolved();
        }
        double leftValue = leftNumber.get();
        double rightValue = rightNumber.get();
        boolean result = greater
            ? (inclusive ? leftValue >= rightValue : leftValue > rightValue)
            : (inclusive ? leftValue <= rightValue : leftValue < rightValue);
        return ComparisonEvaluation.resolved(Optional.of(result));
    }

    private String comparisonTypeError(Node left, Node right) {
        if (isComparisonGroupOperator(left) || isComparisonGroupOperator(right)) {
            return null;
        }
        boolean leftBoolean = hasBooleanType(left);
        boolean rightBoolean = hasBooleanType(right);
        if (leftBoolean == rightBoolean || (!isDefinitelyNonBoolean(left) && !isDefinitelyNonBoolean(right))) {
            return null;
        }
        return Node.tr("pathmind.error.comparisonBooleanTypeMismatch",
            left.getType().getDisplayName(), right.getType().getDisplayName());
    }

    private String orderingTypeError(Node left, Node right) {
        Node invalid = isDefinitelyNonNumeric(left) ? left : (isDefinitelyNonNumeric(right) ? right : null);
        return invalid == null ? null : Node.tr("pathmind.error.orderingNonNumericOperand",
            owner.getType().getDisplayName(), invalid.getType().getDisplayName());
    }

    private boolean hasBooleanType(Node node) {
        return node != null && (NodeCatalog.isBooleanSensor(node.getType())
            || node.getProvidedTraits().contains(NodeValueTrait.BOOLEAN));
    }

    private boolean isDefinitelyNonBoolean(Node node) {
        return node != null && !hasBooleanType(node) && hasKnownStaticType(node);
    }

    private boolean isDefinitelyNonNumeric(Node node) {
        if (node == null) {
            return false;
        }
        Set<NodeValueTrait> traits = node.getProvidedTraits();
        boolean numeric = traits.contains(NodeValueTrait.NUMBER)
            || traits.contains(NodeValueTrait.DISTANCE)
            || traits.contains(NodeValueTrait.DURATION);
        return !numeric && hasKnownStaticType(node);
    }

    private boolean hasKnownStaticType(Node node) {
        if (node == null || node.getType() == NodeType.VARIABLE || node.getType() == NodeType.ROUTINE_INPUT) {
            return false;
        }
        Set<NodeValueTrait> traits = node.getProvidedTraits();
        return !traits.isEmpty() && !traits.contains(NodeValueTrait.ANY);
    }

    record ComparisonEvaluation(Optional<Boolean> value, String errorMessage) {
        static ComparisonEvaluation resolved(Optional<Boolean> value) {
            return new ComparisonEvaluation(value == null ? Optional.empty() : value, null);
        }

        static ComparisonEvaluation unresolved() {
            return resolved(Optional.empty());
        }

        static ComparisonEvaluation invalid(String errorMessage) {
            return new ComparisonEvaluation(Optional.empty(), errorMessage);
        }

        boolean isInvalid() {
            return errorMessage != null && !errorMessage.isBlank();
        }
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
        if (node.getType() != NodeType.OPERATOR_BOOLEAN_OR && node.getType() != NodeType.OPERATOR_BOOLEAN_AND) {
            return false;
        }
        for (int slotIndex = 0; slotIndex < node.getParameterSlotCount(); slotIndex++) {
            if (node.getAttachedParameter(slotIndex) != null) {
                return true;
            }
        }
        return false;
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
        if (node.getType() == NodeType.PARAM_ITEM_DATA
            && node.getProvidedTraits().contains(NodeValueTrait.BOOLEAN)) {
            String value = owner.getRuntimeValue(node.exportParameterValues(), "Value");
            if ("true".equalsIgnoreCase(value)) {
                return Optional.of(true);
            }
            if ("false".equalsIgnoreCase(value)) {
                return Optional.of(false);
            }
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
        Optional<Boolean> targetedBlockFaceComparison = compareTargetedBlockFaceValues(left, right);
        if (targetedBlockFaceComparison.isPresent()) {
            return targetedBlockFaceComparison;
        }
        Optional<Boolean> emptyTargetedBlockComparison = compareEmptyTargetedBlockValues(left, leftValues, right, rightValues);
        if (emptyTargetedBlockComparison.isPresent()) {
            return emptyTargetedBlockComparison;
        }
        if (leftValues != null && !leftValues.isEmpty() && rightValues != null && !rightValues.isEmpty()) {
            Optional<Boolean> coordinateComparison = comparePositionCoordinateValues(left, leftValues, right, rightValues);
            if (coordinateComparison.isPresent()) {
                return coordinateComparison;
            }
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
            Optional<Boolean> villagerTradeComparison = compareVillagerTradeValues(left, leftValues, right, rightValues);
            if (villagerTradeComparison.isPresent()) {
                return villagerTradeComparison;
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

    /**
     * A Block Face parameter can carry a target block/coordinate.  When it is
     * compared to Targeted Block Face, that target qualifies the hit as well as
     * the face direction.  Previously the comparison only considered "up",
     * "north", etc., so a top face on any block matched a selected wheat block.
     */
    private Optional<Boolean> compareTargetedBlockFaceValues(Node left, Node right) {
        Node faceParameter;
        if (left.getType() == NodeType.SENSOR_TARGETED_BLOCK_FACE
            && right.getType() == NodeType.PARAM_BLOCK_FACE) {
            faceParameter = right;
        } else if (right.getType() == NodeType.SENSOR_TARGETED_BLOCK_FACE
            && left.getType() == NodeType.PARAM_BLOCK_FACE) {
            faceParameter = left;
        } else {
            return Optional.empty();
        }

        Optional<BlockHitResult> hit = owner.getCurrentBlockHitResult();
        if (hit.isEmpty() || hit.get().getDirection() == null) {
            return Optional.of(false);
        }
        String expectedFace = Node.getParameterString(faceParameter, "Face");
        if (expectedFace == null || expectedFace.isBlank()) {
            expectedFace = Node.getParameterString(faceParameter, "Side");
        }
        if (expectedFace == null || !expectedFace.trim().equalsIgnoreCase(
            hit.get().getDirection().toString())) {
            return Optional.of(false);
        }

        Node target = faceParameter.getAttachedParameter(0);
        if (target == null) {
            return Optional.of(true);
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || hit.get().getBlockPos() == null) {
            return Optional.of(false);
        }
        return Optional.of(targetedBlockFaceTargetMatches(
            faceParameter, hit.get(), client.level.getBlockState(hit.get().getBlockPos())));
    }

    boolean targetedBlockFaceTargetMatches(Node faceParameter, BlockHitResult hit, BlockState hitState) {
        if (faceParameter == null || hit == null || hit.getBlockPos() == null) {
            return false;
        }
        Node target = faceParameter.getAttachedParameter(0);
        if (target == null) {
            return true;
        }
        if (owner.providesTrait(target, NodeValueTrait.BLOCK)) {
            List<BlockSelection> selections = owner.resolveBlocksFromParameter(target);
            if (selections.isEmpty() || hitState == null) {
                return false;
            }
            return owner.matchesAnyBlock(selections, hitState);
        }
        if (owner.providesTrait(target, NodeValueTrait.COORDINATE)) {
            Optional<Vec3> targetPosition = owner.resolvePositionTarget(target, null, null);
            if (targetPosition.isEmpty()) {
                return false;
            }
            Vec3 position = targetPosition.get();
            BlockPos targetBlockPos = new BlockPos(
                Mth.floor(position.x), Mth.floor(position.y), Mth.floor(position.z));
            return targetBlockPos.equals(hit.getBlockPos());
        }
        return false;
    }

    Optional<Boolean> comparePositionCoordinateValues(Node left, Map<String, String> leftValues,
                                                       Node right, Map<String, String> rightValues) {
        Node positionNode;
        Map<String, String> positionValues;
        Node coordinateNode;
        Map<String, String> coordinateValues;
        if (left.getType() == NodeType.SENSOR_POSITION_OF && right.getType() == NodeType.PARAM_COORDINATE) {
            positionNode = left;
            positionValues = leftValues;
            coordinateNode = right;
            coordinateValues = rightValues;
        } else if (right.getType() == NodeType.SENSOR_POSITION_OF && left.getType() == NodeType.PARAM_COORDINATE) {
            positionNode = right;
            positionValues = rightValues;
            coordinateNode = left;
            coordinateValues = leftValues;
        } else {
            return Optional.empty();
        }

        if (positionNode.isSensorPositionSingleAxisMode()) {
            return Optional.empty();
        }
        for (String axis : List.of("X", "Y", "Z")) {
            Double liveValue = Node.parseDoubleOrNull(owner.getRuntimeValue(positionValues, axis));
            String rawTarget = Node.getParameterString(coordinateNode, axis);
            Double targetValue = Node.parseDoubleOrNull(owner.getRuntimeValue(coordinateValues, axis));
            if (liveValue == null || targetValue == null || rawTarget == null) {
                return Optional.empty();
            }
            boolean preciseAxis = rawTarget.trim().indexOf('.') >= 0;
            boolean matches = preciseAxis
                ? Double.compare(liveValue, targetValue) == 0
                : Math.floor(liveValue) == Math.floor(targetValue);
            if (!matches) {
                return Optional.of(false);
            }
        }
        return Optional.of(true);
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

    private Optional<Boolean> compareVillagerTradeValues(Node left, Map<String, String> leftValues,
                                                         Node right, Map<String, String> rightValues) {
        boolean leftIsTrade = owner.providesTrait(left, NodeValueTrait.VILLAGER_TRADE);
        boolean rightIsTrade = owner.providesTrait(right, NodeValueTrait.VILLAGER_TRADE);
        if (!leftIsTrade && !rightIsTrade) {
            return Optional.empty();
        }
        if (!leftIsTrade || !rightIsTrade) {
            // A trade compared against a plain item node matches on the sold item.
            Map<String, String> tradeValues = leftIsTrade ? leftValues : rightValues;
            Map<String, String> itemValues = leftIsTrade ? rightValues : leftValues;
            List<String> soldItems = resolveComparableVillagerTradeSellItems(tradeValues);
            List<String> itemSelections = resolveComparableItemSelections(itemValues);
            if (soldItems.isEmpty() || itemSelections.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(selectionsOverlap(soldItems, itemSelections));
        }
        String leftVariant = owner.getRuntimeValue(leftValues, "variant");
        String rightVariant = owner.getRuntimeValue(rightValues, "variant");
        if (!leftVariant.isEmpty()
            && !rightVariant.isEmpty()
            && !leftVariant.equalsIgnoreCase(rightVariant)) {
            return Optional.of(false);
        }
        List<String> leftTrades = resolveComparableVillagerTrades(leftValues);
        List<String> rightTrades = resolveComparableVillagerTrades(rightValues);
        if (leftTrades.isEmpty() || rightTrades.isEmpty()) {
            return Optional.of(false);
        }
        for (String leftTrade : leftTrades) {
            for (String rightTrade : rightTrades) {
                if (leftTrade.equalsIgnoreCase(rightTrade)) {
                    return Optional.of(true);
                }
                String leftSellItem = getVillagerTradeSellItemId(leftTrade);
                String rightSellItem = getVillagerTradeSellItemId(rightTrade);
                if ((!isFullVillagerTradeKey(leftTrade) || !isFullVillagerTradeKey(rightTrade))
                    && !leftSellItem.isEmpty()
                    && leftSellItem.equalsIgnoreCase(rightSellItem)) {
                    return Optional.of(true);
                }
            }
        }
        return Optional.of(false);
    }

    private boolean isFullVillagerTradeKey(String value) {
        return value != null && value.contains("|") && value.contains("@");
    }

    private String getVillagerTradeSellItemId(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String sellPart = value;
        if (value.contains("|")) {
            String[] parts = value.split("\\|");
            sellPart = parts[parts.length - 1];
        }
        int countSeparator = sellPart.indexOf('@');
        return countSeparator >= 0 ? sellPart.substring(0, countSeparator) : sellPart;
    }

    private List<String> resolveComparableVillagerTradeSellItems(Map<String, String> values) {
        List<String> sellItems = new ArrayList<>();
        for (String trade : resolveComparableVillagerTrades(values)) {
            String sellItem = getVillagerTradeSellItemId(trade);
            if (!sellItem.isEmpty()) {
                owner.addItemIdentifier(sellItems, sellItem);
            }
        }
        return sellItems;
    }

    private List<String> resolveComparableVillagerTrades(Map<String, String> values) {
        String tradeValue = owner.getRuntimeValue(values, "trade");
        if (tradeValue.isEmpty()) {
            tradeValue = owner.getRuntimeValue(values, "item");
        }
        if (tradeValue.isEmpty()) {
            tradeValue = owner.getRuntimeValue(values, "items");
        }
        return owner.splitMultiValueList(tradeValue);
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
