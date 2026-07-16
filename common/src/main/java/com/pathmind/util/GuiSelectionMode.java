package com.pathmind.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

public enum GuiSelectionMode {
    PLAYER_INVENTORY("player_inventory", "pathmind.gui.mode.playerInventory"),
    CRAFTING_TABLE("crafting_table", "pathmind.gui.mode.craftingTable"),
    FURNACE("furnace", "pathmind.gui.mode.furnace"),
    BLAST_FURNACE("blast_furnace", "pathmind.gui.mode.blastFurnace"),
    SMOKER("smoker", "pathmind.gui.mode.smoker"),
    ENCHANTING_TABLE("enchanting_table", "pathmind.gui.mode.enchantingTable"),
    BREWING_STAND("brewing_stand", "pathmind.gui.mode.brewingStand"),
    ANVIL("anvil", "pathmind.gui.mode.anvil"),
    GRINDSTONE("grindstone", "pathmind.gui.mode.grindstone"),
    STONECUTTER("stonecutter", "pathmind.gui.mode.stonecutter"),
    SMITHING_TABLE("smithing_table", "pathmind.gui.mode.smithingTable"),
    LOOM("loom", "pathmind.gui.mode.loom"),
    CARTOGRAPHY_TABLE("cartography_table", "pathmind.gui.mode.cartographyTable"),
    BARREL("barrel", "pathmind.gui.mode.barrel"),
    CHEST_DOUBLE("double_chest", "pathmind.gui.mode.doubleChest"),
    SHULKER_BOX("shulker_box", "pathmind.gui.mode.shulkerBox"),
    HOPPER("hopper", "pathmind.gui.mode.hopper"),
    DISPENSER("dispenser", "pathmind.gui.mode.dispenser"),
    BEACON("beacon", "pathmind.gui.mode.beacon");

    private final String id;
    private final String translationKey;

    GuiSelectionMode(String id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return Component.translatable(translationKey).getString();
    }

    public static String getDisplayNameOrFallback(String id) {
        if (id == null) {
            return Component.translatable("pathmind.option.any").getString();
        }
        String trimmed = id.trim();
        if (trimmed.isEmpty() || "any".equalsIgnoreCase(trimmed)) {
            return Component.translatable("pathmind.option.any").getString();
        }
        GuiSelectionMode mode = fromId(trimmed);
        return mode != null ? mode.getDisplayName() : trimmed;
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
        return buildDisplayNames();
    }

    private static List<String> buildDisplayNames() {
        List<String> names = new ArrayList<>();
        for (GuiSelectionMode mode : values()) {
            names.add(mode.getDisplayName());
        }
        return Collections.unmodifiableList(names);
    }
}
