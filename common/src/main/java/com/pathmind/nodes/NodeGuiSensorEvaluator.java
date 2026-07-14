package com.pathmind.nodes;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.BeaconScreenHandler;
import net.minecraft.screen.BlastFurnaceScreenHandler;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.CartographyTableScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.FurnaceScreenHandler;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.LoomScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.SmokerScreenHandler;
import net.minecraft.screen.StonecutterScreenHandler;

import com.pathmind.util.GuiSelectionMode;

import java.util.Map;
import java.util.Optional;

final class NodeGuiSensorEvaluator {
    private final Node owner;

    NodeGuiSensorEvaluator(Node owner) {
        this.owner = owner;
    }

    boolean isOpenGuiFilled() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        PlayerInventory inventory = client.player.getInventory();
        if (inventory == null) {
            return false;
        }
        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null) {
            return false;
        }

        Node guiNode = owner.resolveSensorParameterNode(owner.getAttachedParameter(0), 0);
        GuiSelectionMode guiMode = null;
        if (guiNode != null && guiNode.getType() == NodeType.PARAM_GUI) {
            guiMode = GuiSelectionMode.fromId(Node.getParameterString(guiNode, "GUI"));
        }
        if (guiMode == GuiSelectionMode.PLAYER_INVENTORY) {
            return isPlayerInventoryFilled(inventory);
        }
        if (guiMode != null && guiMode != GuiSelectionMode.PLAYER_INVENTORY && !matchesGuiMode(handler, guiMode)) {
            return false;
        }

        boolean inspectPlayerInventory = guiMode == GuiSelectionMode.PLAYER_INVENTORY
            || (guiMode == null && (handler instanceof PlayerScreenHandler || !hasContainerSlots(handler)));
        boolean inspectContainer = guiMode != GuiSelectionMode.PLAYER_INVENTORY && (!inspectPlayerInventory);
        boolean foundRelevantSlots = false;

        for (Slot slot : handler.slots) {
            if (slot == null) {
                continue;
            }
            boolean playerSlot = slot.inventory instanceof PlayerInventory;
            if (inspectPlayerInventory != playerSlot) {
                continue;
            }
            foundRelevantSlots = true;
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) {
                return false;
            }
        }
        return foundRelevantSlots;
    }

    Optional<CurrentGui> getCurrentGui() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.currentScreen == null) {
            return Optional.empty();
        }
        GuiSelectionMode mode = resolveGuiMode(client.player.currentScreenHandler, client.currentScreen);
        String id = mode != null ? mode.getId() : screenId(client.currentScreen);
        String title = client.currentScreen.getTitle() != null ? client.currentScreen.getTitle().getString() : "";
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new CurrentGui(id, title));
    }

    Map<String, String> exportCurrentGuiValues(Map<String, String> values) {
        getCurrentGui().ifPresent(currentGui -> {
            put(values, "GUI", currentGui.id());
            put(values, "Mode", currentGui.id());
            put(values, "GuiMode", currentGui.id());
            put(values, "Selection", currentGui.id());
            put(values, "Title", currentGui.title());
        });
        return values;
    }

    private boolean isPlayerInventoryFilled(PlayerInventory inventory) {
        if (inventory == null) {
            return false;
        }
        int slotCount = Math.min(36, inventory.size());
        if (slotCount <= 0) {
            return false;
        }
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            ItemStack stack = inventory.getStack(slotIndex);
            if (stack == null || stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasContainerSlots(ScreenHandler handler) {
        if (handler == null) {
            return false;
        }
        for (Slot slot : handler.slots) {
            if (slot != null && !(slot.inventory instanceof PlayerInventory)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesGuiMode(ScreenHandler handler, GuiSelectionMode mode) {
        if (handler == null || mode == null) {
            return false;
        }
        return switch (mode) {
            case PLAYER_INVENTORY -> true;
            case CRAFTING_TABLE -> handler instanceof CraftingScreenHandler && !(handler instanceof PlayerScreenHandler);
            case FURNACE -> handler instanceof FurnaceScreenHandler;
            case BLAST_FURNACE -> handler instanceof BlastFurnaceScreenHandler;
            case SMOKER -> handler instanceof SmokerScreenHandler;
            case ENCHANTING_TABLE -> handler instanceof EnchantmentScreenHandler;
            case BREWING_STAND -> handler instanceof BrewingStandScreenHandler;
            case ANVIL -> handler instanceof AnvilScreenHandler;
            case GRINDSTONE -> handler instanceof GrindstoneScreenHandler;
            case STONECUTTER -> handler instanceof StonecutterScreenHandler;
            case SMITHING_TABLE -> handler instanceof SmithingScreenHandler;
            case LOOM -> handler instanceof LoomScreenHandler;
            case CARTOGRAPHY_TABLE -> handler instanceof CartographyTableScreenHandler;
            case BARREL -> handler instanceof GenericContainerScreenHandler generic && generic.getRows() == 3;
            case CHEST_DOUBLE -> handler instanceof GenericContainerScreenHandler generic && generic.getRows() >= 6;
            case SHULKER_BOX -> handler instanceof GenericContainerScreenHandler generic && generic.getRows() == 3;
            case HOPPER -> handler instanceof HopperScreenHandler;
            case DISPENSER -> handler instanceof Generic3x3ContainerScreenHandler;
            case BEACON -> handler instanceof BeaconScreenHandler;
        };
    }

    private GuiSelectionMode resolveGuiMode(ScreenHandler handler, Screen screen) {
        if (handler == null) {
            return null;
        }
        String title = screen != null && screen.getTitle() != null
            ? screen.getTitle().getString().toLowerCase(java.util.Locale.ROOT)
            : "";
        if (handler instanceof PlayerScreenHandler) {
            return GuiSelectionMode.PLAYER_INVENTORY;
        }
        if (handler instanceof CraftingScreenHandler) {
            return GuiSelectionMode.CRAFTING_TABLE;
        }
        if (handler instanceof FurnaceScreenHandler) {
            return GuiSelectionMode.FURNACE;
        }
        if (handler instanceof BlastFurnaceScreenHandler) {
            return GuiSelectionMode.BLAST_FURNACE;
        }
        if (handler instanceof SmokerScreenHandler) {
            return GuiSelectionMode.SMOKER;
        }
        if (handler instanceof EnchantmentScreenHandler) {
            return GuiSelectionMode.ENCHANTING_TABLE;
        }
        if (handler instanceof BrewingStandScreenHandler) {
            return GuiSelectionMode.BREWING_STAND;
        }
        if (handler instanceof AnvilScreenHandler) {
            return GuiSelectionMode.ANVIL;
        }
        if (handler instanceof GrindstoneScreenHandler) {
            return GuiSelectionMode.GRINDSTONE;
        }
        if (handler instanceof StonecutterScreenHandler) {
            return GuiSelectionMode.STONECUTTER;
        }
        if (handler instanceof SmithingScreenHandler) {
            return GuiSelectionMode.SMITHING_TABLE;
        }
        if (handler instanceof LoomScreenHandler) {
            return GuiSelectionMode.LOOM;
        }
        if (handler instanceof CartographyTableScreenHandler) {
            return GuiSelectionMode.CARTOGRAPHY_TABLE;
        }
        if (handler instanceof HopperScreenHandler) {
            return GuiSelectionMode.HOPPER;
        }
        if (handler instanceof Generic3x3ContainerScreenHandler) {
            return GuiSelectionMode.DISPENSER;
        }
        if (handler instanceof BeaconScreenHandler) {
            return GuiSelectionMode.BEACON;
        }
        if (handler instanceof GenericContainerScreenHandler generic) {
            if (generic.getRows() >= 6) {
                return GuiSelectionMode.CHEST_DOUBLE;
            }
            if (title.contains("shulker")) {
                return GuiSelectionMode.SHULKER_BOX;
            }
            if (title.contains("barrel")) {
                return GuiSelectionMode.BARREL;
            }
            return generic.getRows() == 3 ? GuiSelectionMode.BARREL : null;
        }
        return null;
    }

    private String screenId(Screen screen) {
        String simpleName = screen.getClass().getSimpleName();
        if (simpleName == null || simpleName.isBlank()) {
            return "";
        }
        return simpleName.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    private void put(Map<String, String> values, String key, String value) {
        if (values == null || key == null) {
            return;
        }
        String safeValue = value == null ? "" : value;
        values.put(key, safeValue);
        values.put(key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", ""), safeValue);
    }

    record CurrentGui(String id, String title) {
    }
}
