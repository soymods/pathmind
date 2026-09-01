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
        return resolveComparisonEvaluation(evaluateOperatorComparison(), false);
    }

    boolean evaluateOperatorNot() {
        return resolveComparisonEvaluation(evaluateOperatorComparison(), true);
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
        return resolveComparisonEvaluation(evaluateOperatorOrdering(true), false);
    }

    boolean evaluateOperatorLess() {
        return resolveComparisonEvaluation(evaluateOperatorOrdering(false), false);
    }

    private NodeComparisonEvaluator.ComparisonEvaluation evaluateOperatorComparison() {
        Node left = owner.getAttachedParameter(0);
        Node right = owner.getAttachedParameter(1);
        return comparisonEvaluator().evaluateComparisonOperands(left, right);
    }

    private NodeComparisonEvaluator.ComparisonEvaluation evaluateOperatorOrdering(boolean greater) {
        Node left = owner.getAttachedParameter(0);
        Node right = owner.getAttachedParameter(1);
        return comparisonEvaluator().evaluateOrderingOperands(
            left, right, greater, owner.getBooleanParameter("Inclusive", false));
    }

    private boolean resolveComparisonEvaluation(NodeComparisonEvaluator.ComparisonEvaluation evaluation, boolean invert) {
        if (evaluation == null) {
            return false;
        }
        if (evaluation.isInvalid()) {
            owner.reportRuntimeDiagnostic("comparison:" + evaluation.errorMessage(), evaluation.errorMessage());
            return false;
        }
        owner.clearRuntimeDiagnostic();
        return evaluation.value().map(value -> invert ? !value : value).orElse(false);
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
