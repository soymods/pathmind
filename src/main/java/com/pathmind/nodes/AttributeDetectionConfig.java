package com.pathmind.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

public final class AttributeDetectionConfig {
    public enum TargetKind {
        ENTITY,
        PLAYER,
        ITEM
    }

    public enum ValueType {
        STRING,
        NUMBER,
        BOOLEAN
    }

    public enum AttributeOption {
        NAME("name", "Name", ValueType.STRING, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER, TargetKind.ITEM)),
        CUSTOM_NAME("custom_name", "Custom Name", ValueType.STRING, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER, TargetKind.ITEM)),
        TYPE("type", "Type", ValueType.STRING, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        HEALTH("health", "Health", ValueType.NUMBER, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        MAX_HEALTH("max_health", "Max Health", ValueType.NUMBER, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        IS_BABY("is_baby", "Is Baby", ValueType.BOOLEAN, EnumSet.of(TargetKind.ENTITY)),
        TAG("tag", "Tag", ValueType.STRING, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        ITEM_ID("item_id", "Item", ValueType.STRING, EnumSet.of(TargetKind.ITEM)),
        COUNT("count", "Count", ValueType.NUMBER, EnumSet.of(TargetKind.ITEM)),
        DAMAGE("damage", "Damage", ValueType.NUMBER, EnumSet.of(TargetKind.ITEM)),
        MAX_DAMAGE("max_damage", "Max Damage", ValueType.NUMBER, EnumSet.of(TargetKind.ITEM)),
        IS_STACKABLE("is_stackable", "Is Stackable", ValueType.BOOLEAN, EnumSet.of(TargetKind.ITEM)),
        IS_ENCHANTED("is_enchanted", "Is Enchanted", ValueType.BOOLEAN, EnumSet.of(TargetKind.ITEM));

        private final String id;
        private final String label;
        private final ValueType valueType;
        private final EnumSet<TargetKind> targets;

        AttributeOption(String id, String label, ValueType valueType, EnumSet<TargetKind> targets) {
            this.id = id;
            this.label = label;
            this.valueType = valueType;
            this.targets = targets;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public ValueType valueType() {
            return valueType;
        }

        public boolean supports(TargetKind targetKind) {
            return targetKind != null && targets.contains(targetKind);
        }
    }

    public enum OperatorOption {
        EQUALS("equals", "Equals", EnumSet.of(ValueType.STRING, ValueType.NUMBER, ValueType.BOOLEAN)),
        NOT_EQUALS("not_equals", "Does Not Equal", EnumSet.of(ValueType.STRING, ValueType.NUMBER, ValueType.BOOLEAN)),
        CONTAINS("contains", "Contains", EnumSet.of(ValueType.STRING)),
        STARTS_WITH("starts_with", "Starts With", EnumSet.of(ValueType.STRING)),
        GREATER_THAN("greater_than", "Greater Than", EnumSet.of(ValueType.NUMBER)),
        GREATER_OR_EQUAL("greater_or_equal", "Greater Or Equal", EnumSet.of(ValueType.NUMBER)),
        LESS_THAN("less_than", "Less Than", EnumSet.of(ValueType.NUMBER)),
        LESS_OR_EQUAL("less_or_equal", "Less Or Equal", EnumSet.of(ValueType.NUMBER));

        private final String id;
        private final String label;
        private final EnumSet<ValueType> valueTypes;

        OperatorOption(String id, String label, EnumSet<ValueType> valueTypes) {
            this.id = id;
            this.label = label;
            this.valueTypes = valueTypes;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public boolean supports(ValueType valueType) {
            return valueType != null && valueTypes.contains(valueType);
        }
    }

    private AttributeDetectionConfig() {
    }

    public static TargetKind inferTargetKind(NodeType nodeType) {
        if (nodeType == null) {
            return null;
        }
        return switch (nodeType) {
            case PARAM_ENTITY -> TargetKind.ENTITY;
            case PARAM_PLAYER -> TargetKind.PLAYER;
            case PARAM_ITEM -> TargetKind.ITEM;
            default -> null;
        };
    }

    public static List<AttributeOption> getAttributesForTarget(TargetKind targetKind) {
        List<AttributeOption> results = new ArrayList<>();
        for (AttributeOption option : AttributeOption.values()) {
            if (targetKind == null || option.supports(targetKind)) {
                results.add(option);
            }
        }
        return Collections.unmodifiableList(results);
    }

    public static List<OperatorOption> getOperatorsForAttribute(AttributeOption attributeOption) {
        if (attributeOption == null) {
            return Collections.emptyList();
        }
        List<OperatorOption> results = new ArrayList<>();
        for (OperatorOption option : OperatorOption.values()) {
            if (option.supports(attributeOption.valueType())) {
                results.add(option);
            }
        }
        return Collections.unmodifiableList(results);
    }

    public static AttributeOption getAttribute(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (AttributeOption option : AttributeOption.values()) {
            if (option.id().equals(normalized)) {
                return option;
            }
        }
        return null;
    }

    public static OperatorOption getOperator(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (OperatorOption option : OperatorOption.values()) {
            if (option.id().equals(normalized)) {
                return option;
            }
        }
        return null;
    }

    public static AttributeOption getDefaultAttribute(TargetKind targetKind) {
        List<AttributeOption> options = getAttributesForTarget(targetKind);
        return options.isEmpty() ? AttributeOption.NAME : options.get(0);
    }

    public static OperatorOption getDefaultOperator(AttributeOption attributeOption) {
        if (attributeOption == null) {
            return OperatorOption.EQUALS;
        }
        return switch (attributeOption.valueType()) {
            case NUMBER, BOOLEAN -> OperatorOption.EQUALS;
            case STRING -> switch (attributeOption) {
                case NAME, CUSTOM_NAME, TAG -> OperatorOption.CONTAINS;
                default -> OperatorOption.EQUALS;
            };
        };
    }

    public static String getDefaultValue(AttributeOption attributeOption) {
        if (attributeOption == null) {
            return "";
        }
        return attributeOption.valueType() == ValueType.BOOLEAN ? "true" : "";
    }
}
