package com.pathmind.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class MathNodeDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.CALCULATE)
            .parameterBehavior(MathNodeDefinition::exportValues)
            .comparableBehavior(NodeBehaviorDefinitionSupport.numberComparable(MathNodeDefinition::resolveComparableNumber))
            .build();
    }

    // Numeric slot keys consumed across node types (amount/duration/distance/range/...). Calculate
    // publishes its value under all of them so it can drive any numeric parameter it is dropped into.
    private static final String[] NUMERIC_VALUE_KEYS = {
        "Amount", "Count", "Threshold", "Value",
        "Duration", "IntervalSeconds", "WaitSeconds", "DurationSeconds",
        "Distance", "Range", "Radius"
    };

    private static Map<String, String> exportValues(Node node, Map<String, String> values) {
        List<String> resolvedValues = resolveExpressions(node);
        // When used as a parameter, Calculate resolves to its first output (A) only.
        String amount = resolvedValues.isEmpty() ? "0" : resolvedValues.getFirst();
        for (String key : NUMERIC_VALUE_KEYS) {
            NodeBehaviorDefinitionSupport.put(values, key, amount);
        }
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
            // Lines are "Name = expression"; evaluate the right-hand side (the whole line if no '=').
            int equalsIndex = raw.indexOf('=');
            String expression = equalsIndex > 0 ? raw.substring(equalsIndex + 1).trim() : raw;
            String resolved = node.resolveRuntimeVariablesInText(expression);
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
