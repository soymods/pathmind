package com.pathmind.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class MathNodeDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.CHANGE_VARIABLE)
            .parameterBehavior(MathNodeDefinition::exportValues)
            .comparableBehavior(NodeBehaviorDefinitionSupport.numberComparable(MathNodeDefinition::resolveComparableNumber))
            .build();
    }

    private static Map<String, String> exportValues(Node node, Map<String, String> values) {
        List<String> resolvedValues = resolveExpressions(node);
        String amount = resolvedValues.isEmpty() ? "0" : String.join(", ", resolvedValues);
        NodeBehaviorDefinitionSupport.put(values, "Amount", amount);
        NodeBehaviorDefinitionSupport.put(values, "Count", amount);
        NodeBehaviorDefinitionSupport.put(values, "Threshold", amount);
        NodeBehaviorDefinitionSupport.put(values, "Value", amount);
        return values;
    }

    private static Optional<Double> resolveComparableNumber(Node owner, Node node) {
        List<String> resolvedValues = resolveExpressions(node);
        if (resolvedValues.isEmpty()) {
            return Optional.of(0.0);
        }
        return Optional.of(Node.parseDoubleOrDefault(resolvedValues.getFirst(), 0.0));
    }

    private static List<String> resolveExpressions(Node node) {
        List<String> resolvedValues = new ArrayList<>();
        if (node == null) {
            return resolvedValues;
        }
        for (String line : node.getMessageLines()) {
            String raw = line == null ? "" : line.trim();
            if (raw.isEmpty()) {
                continue;
            }
            String resolved = node.resolveRuntimeVariablesInText(raw);
            Double evaluated = Node.evaluateNumericExpression(resolved);
            if (evaluated != null) {
                resolvedValues.add(formatNumber(evaluated));
            } else {
                resolvedValues.add("0");
            }
        }
        if (resolvedValues.isEmpty()) {
            resolvedValues.add("0");
        }
        return resolvedValues;
    }

    private static String formatNumber(double value) {
        return Math.abs(value - Math.rint(value)) < 1.0E-9
            ? Long.toString(Math.round(value))
            : Double.toString(value);
    }

    private MathNodeDefinition() {
    }
}
