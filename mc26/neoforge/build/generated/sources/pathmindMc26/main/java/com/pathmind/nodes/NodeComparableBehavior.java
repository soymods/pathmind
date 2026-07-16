package com.pathmind.nodes;

import java.util.Optional;

interface NodeComparableBehavior {
    default Optional<String> resolveString(Node owner, Node node) {
        return Optional.empty();
    }

    default Optional<Double> resolveNumber(Node owner, Node node) {
        return Optional.empty();
    }
}
