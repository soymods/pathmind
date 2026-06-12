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
        HAS_CUSTOM_NAME("has_custom_name", "Has Custom Name", ValueType.BOOLEAN, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER, TargetKind.ITEM)),
        TYPE("type", "Type", ValueType.STRING, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        UUID("uuid", "UUID", ValueType.STRING, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        HEALTH("health", "Health", ValueType.NUMBER, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        MAX_HEALTH("max_health", "Max Health", ValueType.NUMBER, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        X("x", "X", ValueType.NUMBER, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER, TargetKind.ITEM)),
        Y("y", "Y", ValueType.NUMBER, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER, TargetKind.ITEM)),
        Z("z", "Z", ValueType.NUMBER, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER, TargetKind.ITEM)),
        YAW("yaw", "Yaw", ValueType.NUMBER, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        PITCH("pitch", "Pitch", ValueType.NUMBER, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        IS_ALIVE("is_alive", "Is Alive", ValueType.BOOLEAN, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        IS_ON_GROUND("is_on_ground", "Is On Ground", ValueType.BOOLEAN, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        IS_ON_FIRE("is_on_fire", "Is On Fire", ValueType.BOOLEAN, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        IS_SNEAKING("is_sneaking", "Is Sneaking", ValueType.BOOLEAN, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        IS_SPRINTING("is_sprinting", "Is Sprinting", ValueType.BOOLEAN, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        IS_SWIMMING("is_swimming", "Is Swimming", ValueType.BOOLEAN, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        IS_BABY("is_baby", "Is Baby", ValueType.BOOLEAN, EnumSet.of(TargetKind.ENTITY)),
        TAG("tag", "Tag", ValueType.STRING, EnumSet.of(TargetKind.ENTITY, TargetKind.PLAYER)),
        ITEM_ID("item_id", "Item", ValueType.STRING, EnumSet.of(TargetKind.ITEM)),
        COUNT("count", "Count", ValueType.NUMBER, EnumSet.of(TargetKind.ITEM)),
        MAX_COUNT("max_count", "Max Stack Size", ValueType.NUMBER, EnumSet.of(TargetKind.ITEM)),
        DAMAGE("damage", "Damage", ValueType.NUMBER, EnumSet.of(TargetKind.ITEM)),
        MAX_DAMAGE("max_damage", "Max Damage", ValueType.NUMBER, EnumSet.of(TargetKind.ITEM)),
        IS_STACKABLE("is_stackable", "Is Stackable", ValueType.BOOLEAN, EnumSet.of(TargetKind.ITEM)),
        IS_ENCHANTED("is_enchanted", "Is Enchanted", ValueType.BOOLEAN, EnumSet.of(TargetKind.ITEM)),
        IS_DAMAGEABLE("is_damageable", "Is Damageable", ValueType.BOOLEAN, EnumSet.of(TargetKind.ITEM));

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

    public static AttributeOption getDefaultAttribute(TargetKind targetKind) {
        List<AttributeOption> options = getAttributesForTarget(targetKind);
        return options.isEmpty() ? AttributeOption.NAME : options.get(0);
    }

    public static String getDefaultValue(AttributeOption attributeOption) {
        if (attributeOption == null) {
            return "";
        }
        return attributeOption.valueType() == ValueType.BOOLEAN ? "true" : "";
    }
}
