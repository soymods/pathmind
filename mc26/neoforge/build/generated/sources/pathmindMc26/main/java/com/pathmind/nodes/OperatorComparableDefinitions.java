package com.pathmind.nodes;

import java.util.Optional;

final class OperatorComparableDefinitions {
    static Optional<NodeComparableBehavior> definition(NodeType type) {
        return switch (type) {
            case OPERATOR_RANDOM, OPERATOR_MOD, LIST_LENGTH -> Optional.of(
                NodeBehaviorDefinitionSupport.numberComparable(OperatorComparableDefinitions::resolveAmount));
            default -> Optional.empty();
        };
    }

    private static Optional<Double> resolveAmount(Node owner, Node node) {
        return Optional.of(Node.parseNodeDouble(node, "Amount", 0.0));
    }

    private OperatorComparableDefinitions() {
    }
}
