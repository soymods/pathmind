package com.pathmind.routines;

import com.pathmind.nodes.NodeValueTrait;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Live routine input model. Bindings use {@code id}, never the editable label or order. */
public final class RoutineInputDefinition {
    private final String id;
    private final String label;
    private final RoutineValueKind valueKind;
    private final Set<NodeValueTrait> acceptedTraits;
    private final boolean required;
    private final String defaultValue;
    private final int order;

    public RoutineInputDefinition(String id, String label, RoutineValueKind valueKind,
                                  Set<NodeValueTrait> acceptedTraits, boolean required,
                                  String defaultValue, int order) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.trim();
        this.label = label == null || label.isBlank() ? "Input" : label.trim();
        this.valueKind = valueKind == null ? RoutineValueKind.ANY : valueKind;
        LinkedHashSet<NodeValueTrait> traits = new LinkedHashSet<>();
        if (acceptedTraits != null) {
            acceptedTraits.stream().filter(java.util.Objects::nonNull).forEach(traits::add);
        }
        if (traits.isEmpty()) {
            traits.addAll(this.valueKind.getDefaultTraits());
        }
        this.acceptedTraits = Set.copyOf(traits);
        this.required = required;
        this.defaultValue = defaultValue == null ? "" : defaultValue;
        this.order = Math.max(0, order);
    }

    public static RoutineInputDefinition create(String label, RoutineValueKind valueKind, int order) {
        return new RoutineInputDefinition(null, label, valueKind, null, false, "", order);
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public RoutineValueKind getValueKind() { return valueKind; }
    public Set<NodeValueTrait> getAcceptedTraits() { return acceptedTraits; }
    public boolean isRequired() { return required; }
    public String getDefaultValue() { return defaultValue; }
    public int getOrder() { return order; }
}
