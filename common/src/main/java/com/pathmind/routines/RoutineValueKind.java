package com.pathmind.routines;

import com.pathmind.nodes.NodeValueTrait;

import java.util.Locale;
import java.util.Set;

/** Stable, user-facing value families supported by routine inputs. */
public enum RoutineValueKind {
    ANY(NodeValueTrait.ANY),
    TEXT(NodeValueTrait.MESSAGE),
    NUMBER(NodeValueTrait.NUMBER),
    BOOLEAN(NodeValueTrait.BOOLEAN),
    BLOCK(NodeValueTrait.BLOCK),
    ITEM(NodeValueTrait.ITEM),
    COORDINATE(NodeValueTrait.COORDINATE),
    PLAYER(NodeValueTrait.PLAYER),
    ENTITY(NodeValueTrait.ENTITY),
    ROTATION(NodeValueTrait.ROTATION),
    DIRECTION(NodeValueTrait.DIRECTION),
    DURATION(NodeValueTrait.DURATION),
    INVENTORY_SLOT(NodeValueTrait.INVENTORY_SLOT),
    GUI(NodeValueTrait.GUI);

    private final Set<NodeValueTrait> defaultTraits;

    RoutineValueKind(NodeValueTrait defaultTrait) {
        this.defaultTraits = Set.of(defaultTrait);
    }

    public Set<NodeValueTrait> getDefaultTraits() {
        return defaultTraits;
    }

    public static RoutineValueKind fromSerialized(String value) {
        if (value == null || value.isBlank()) {
            return ANY;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("MESSAGE".equals(normalized) || "STRING".equals(normalized)) {
            return TEXT;
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return ANY;
        }
    }
}
