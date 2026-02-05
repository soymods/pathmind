package com.pathmind.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public enum GuiSelectionMode {
    PLAYER_INVENTORY("player_inventory", "Player Inventory"),
    CRAFTING_TABLE("crafting_table", "Crafting Table"),
    FURNACE("furnace", "Furnace"),
    BLAST_FURNACE("blast_furnace", "Blast Furnace"),
    SMOKER("smoker", "Smoker"),
    ENCHANTING_TABLE("enchanting_table", "Enchanting Table"),
    BREWING_STAND("brewing_stand", "Brewing Stand"),
    ANVIL("anvil", "Anvil"),
    GRINDSTONE("grindstone", "Grindstone"),
    STONECUTTER("stonecutter", "Stonecutter"),
    SMITHING_TABLE("smithing_table", "Smithing Table"),
    LOOM("loom", "Loom"),
    CARTOGRAPHY_TABLE("cartography_table", "Cartography Table"),
    BARREL("barrel", "Barrel / Single Chest"),
    CHEST_DOUBLE("double_chest", "Double Chest"),
    SHULKER_BOX("shulker_box", "Shulker Box"),
    HOPPER("hopper", "Hopper"),
    DISPENSER("dispenser", "Dispenser / Dropper"),
    BEACON("beacon", "Beacon");

    private static final List<String> DISPLAY_NAMES = buildDisplayNames();

    private final String id;
    private final String displayName;

    GuiSelectionMode(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static String getDisplayNameOrFallback(String id) {
        if (id == null) {
            return "Any";
        }
        String trimmed = id.trim();
        if (trimmed.isEmpty() || "any".equalsIgnoreCase(trimmed)) {
            return "Any";
        }
        GuiSelectionMode mode = fromId(trimmed);
        return mode != null ? mode.displayName : trimmed;
    }

    public static GuiSelectionMode fromId(String id) {
        if (id == null || id.trim().isEmpty() || "any".equalsIgnoreCase(id.trim())) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (GuiSelectionMode mode : values()) {
            if (mode.id.equals(normalized)) {
                return mode;
            }
        }
        return null;
    }

    public static List<GuiSelectionMode> valuesList() {
        return List.of(values());
    }

    public static List<String> getDisplayNames() {
        return DISPLAY_NAMES;
    }

    private static List<String> buildDisplayNames() {
        List<String> names = new ArrayList<>();
        for (GuiSelectionMode mode : values()) {
            names.add(mode.displayName);
        }
        return Collections.unmodifiableList(names);
    }
}
