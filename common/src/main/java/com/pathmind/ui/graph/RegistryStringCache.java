package com.pathmind.ui.graph;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Lazily-built, sorted lists of block/item/entity registry path strings used to
 * populate parameter dropdown options. Values are captured once at class load.
 */
final class RegistryStringCache {
    static final List<String> BLOCK_IDS = buildBlockIds();
    static final List<String> ITEM_IDS = buildItemIds();
    static final List<String> ENTITY_IDS = buildEntityIds();

    private RegistryStringCache() {
    }

    private static List<String> buildBlockIds() {
        List<String> options = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            if (id == null) {
                continue;
            }
            options.add(id.getPath());
        }
        options.sort(String::compareToIgnoreCase);
        return options;
    }

    private static List<String> buildItemIds() {
        List<String> options = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            if (id == null) {
                continue;
            }
            options.add(id.getPath());
        }
        options.sort(String::compareToIgnoreCase);
        return options;
    }

    private static List<String> buildEntityIds() {
        List<String> options = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            if (id == null) {
                continue;
            }
            options.add(id.getPath());
        }
        options.sort(String::compareToIgnoreCase);
        return options;
    }
}
