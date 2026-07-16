package com.pathmind.nodes;

import java.util.function.Supplier;

/**
 * Immutable template for a node parameter.
 */
final class NodeParameterDefinition {
    private final String id;
    private final String name;
    private final ParameterType type;
    private final Supplier<String> defaultValueSupplier;

    private NodeParameterDefinition(String id, String name, ParameterType type, Supplier<String> defaultValueSupplier) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.defaultValueSupplier = defaultValueSupplier;
    }

    static NodeParameterDefinition of(String name, ParameterType type, String defaultValue) {
        return of(null, name, type, defaultValue);
    }

    static NodeParameterDefinition of(String id, String name, ParameterType type, String defaultValue) {
        return new NodeParameterDefinition(id, name, type, () -> defaultValue);
    }

    static NodeParameterDefinition dynamic(String id, String name, ParameterType type, Supplier<String> defaultValueSupplier) {
        return new NodeParameterDefinition(id, name, type, defaultValueSupplier);
    }

    NodeParameter createParameter() {
        String defaultValue = defaultValueSupplier.get();
        if (id == null || id.isBlank()) {
            return new NodeParameter(name, type, defaultValue);
        }
        return new NodeParameter(id, name, type, defaultValue);
    }
}
