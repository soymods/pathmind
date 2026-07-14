package com.pathmind.nodes;

import com.pathmind.execution.ExecutionManager;

import java.util.Map;
import java.util.Optional;

final class NodeOperatorSensorEvaluator {
    private final Node owner;

    NodeOperatorSensorEvaluator(Node owner) {
        this.owner = owner;
    }

    boolean evaluateOperatorEquals() {
        Optional<Boolean> result = evaluateOperatorComparison();
        return result.orElse(false);
    }

    boolean evaluateOperatorNot() {
        Optional<Boolean> result = evaluateOperatorComparison();
        return result.map(value -> !value).orElse(false);
    }

    boolean evaluateOperatorBooleanNot() {
        Optional<Boolean> result = evaluateOperatorBooleanOperand();
        return result.map(value -> !value).orElse(false);
    }

    boolean evaluateOperatorBooleanOr() {
        for (int slotIndex = 0; slotIndex < owner.getParameterSlotCount(); slotIndex++) {
            Node operand = owner.getAttachedParameter(slotIndex);
            if (operand == null) {
                return false;
            }
            Optional<Boolean> value = resolveBooleanOperandWithVariables(operand, slotIndex);
            if (value.isEmpty()) {
                return false;
            }
            if (value.get()) {
                return true;
            }
        }
        return false;
    }

    boolean evaluateOperatorBooleanAnd() {
        for (int slotIndex = 0; slotIndex < owner.getParameterSlotCount(); slotIndex++) {
            Node operand = owner.getAttachedParameter(slotIndex);
            if (operand == null) {
                return false;
            }
            Optional<Boolean> value = resolveBooleanOperandWithVariables(operand, slotIndex);
            if (value.isEmpty() || !value.get()) {
                return false;
            }
        }
        return owner.getParameterSlotCount() > 0;
    }

    boolean evaluateOperatorBooleanXor() {
        Node left = owner.getAttachedParameter(0);
        Node right = owner.getAttachedParameter(1);
        if (left == null || right == null) {
            return false;
        }
        Optional<Boolean> leftValue = resolveBooleanOperandWithVariables(left, 0);
        Optional<Boolean> rightValue = resolveBooleanOperandWithVariables(right, 1);
        if (leftValue.isEmpty() || rightValue.isEmpty()) {
            return false;
        }
        return leftValue.get() ^ rightValue.get();
    }

    boolean evaluateOperatorGreater() {
        Optional<Boolean> result = evaluateOperatorOrdering(true);
        return result.orElse(false);
    }

    boolean evaluateOperatorLess() {
        Optional<Boolean> result = evaluateOperatorOrdering(false);
        return result.orElse(false);
    }

    private Optional<Boolean> evaluateOperatorComparison() {
        Node left = owner.getAttachedParameter(0);
        Node right = owner.getAttachedParameter(1);
        return compareComparisonOperands(left, right);
    }

    private Optional<Boolean> evaluateOperatorOrdering(boolean greater) {
        Node left = owner.getAttachedParameter(0);
        Node right = owner.getAttachedParameter(1);
        if (left == null || right == null) {
            return Optional.empty();
        }
        Optional<Double> leftNumber = resolveComparableNumberWithVariables(left, 0);
        Optional<Double> rightNumber = resolveComparableNumberWithVariables(right, 1);
        if (leftNumber.isEmpty() || rightNumber.isEmpty()) {
            return Optional.empty();
        }
        boolean inclusive = owner.getBooleanParameter("Inclusive", false);
        double l = leftNumber.get();
        double r = rightNumber.get();
        if (greater) {
            return Optional.of(inclusive ? l >= r : l > r);
        }
        return Optional.of(inclusive ? l <= r : l < r);
    }

    private Optional<Boolean> evaluateOperatorBooleanOperand() {
        Node operand = owner.getAttachedParameter(0);
        return resolveBooleanOperandWithVariables(operand, 0);
    }

    private NodeComparisonEvaluator comparisonEvaluator() {
        return new NodeComparisonEvaluator(owner);
    }

    Optional<Boolean> compareComparisonOperands(Node left, Node right) {
        return comparisonEvaluator().compareComparisonOperands(left, right);
    }

    Optional<Boolean> resolveBooleanOperandWithVariables(Node operand, int slotIndex) {
        return comparisonEvaluator().resolveBooleanOperandWithVariables(operand, slotIndex);
    }

    Optional<Boolean> resolveBooleanFromNode(Node node) {
        return comparisonEvaluator().resolveBooleanFromNode(node);
    }

    Node createRuntimeVariableSnapshot(ExecutionManager.RuntimeVariable runtimeVariable) {
        return comparisonEvaluator().createRuntimeVariableSnapshot(runtimeVariable);
    }

    Optional<Boolean> compareParameterNodes(Node left, Node right) {
        return comparisonEvaluator().compareParameterNodes(left, right);
    }

    String formatCanonicalValueMap(Map<String, String> values) {
        return comparisonEvaluator().formatCanonicalValueMap(values);
    }

    Optional<Double> resolveComparableNumber(Node node) {
        return comparisonEvaluator().resolveComparableNumber(node);
    }

    Optional<Double> resolveComparableNumberWithVariables(Node node, int slotIndex) {
        return comparisonEvaluator().resolveComparableNumberWithVariables(node, slotIndex);
    }
}
