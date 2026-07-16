package com.pathmind.nodes;

import com.pathmind.util.GuiSelectionMode;

import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;

final class NodeGuiSensorEvaluator {
    private final Node owner;

    NodeGuiSensorEvaluator(Node owner) {
        this.owner = owner;
    }

    boolean isOpenGuiFilled() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        Inventory inventory = client.player.getInventory();
        if (inventory == null) {
            return false;
        }
        AbstractContainerMenu handler = client.player.containerMenu;
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
            || (guiMode == null && (handler instanceof InventoryMenu || !hasContainerSlots(handler)));
        boolean inspectContainer = guiMode != GuiSelectionMode.PLAYER_INVENTORY && (!inspectPlayerInventory);
        boolean foundRelevantSlots = false;

        for (Slot slot : handler.slots) {
            if (slot == null) {
                continue;
            }
            boolean playerSlot = slot.container instanceof Inventory;
            if (inspectPlayerInventory != playerSlot) {
                continue;
            }
            foundRelevantSlots = true;
            ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty()) {
                return false;
            }
        }
        return foundRelevantSlots;
    }

    Optional<CurrentGui> getCurrentGui() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.gui.screen() == null) {
            return Optional.empty();
        }
        GuiSelectionMode mode = resolveGuiMode(client.player.containerMenu, client.gui.screen());
        String id = mode != null ? mode.getId() : screenId(client.gui.screen());
        String title = client.gui.screen().getTitle() != null ? client.gui.screen().getTitle().getString() : "";
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

    private boolean isPlayerInventoryFilled(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        int slotCount = Math.min(36, inventory.getContainerSize());
        if (slotCount <= 0) {
            return false;
        }
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            ItemStack stack = inventory.getItem(slotIndex);
            if (stack == null || stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasContainerSlots(AbstractContainerMenu handler) {
        if (handler == null) {
            return false;
        }
        for (Slot slot : handler.slots) {
            if (slot != null && !(slot.container instanceof Inventory)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesGuiMode(AbstractContainerMenu handler, GuiSelectionMode mode) {
        if (handler == null || mode == null) {
            return false;
        }
        return switch (mode) {
            case PLAYER_INVENTORY -> true;
            case CRAFTING_TABLE -> handler instanceof CraftingMenu && !(handler instanceof InventoryMenu);
            case FURNACE -> handler instanceof FurnaceMenu;
            case BLAST_FURNACE -> handler instanceof BlastFurnaceMenu;
            case SMOKER -> handler instanceof SmokerMenu;
            case ENCHANTING_TABLE -> handler instanceof EnchantmentMenu;
            case BREWING_STAND -> handler instanceof BrewingStandMenu;
            case ANVIL -> handler instanceof AnvilMenu;
            case GRINDSTONE -> handler instanceof GrindstoneMenu;
            case STONECUTTER -> handler instanceof StonecutterMenu;
            case SMITHING_TABLE -> handler instanceof SmithingMenu;
            case LOOM -> handler instanceof LoomMenu;
            case CARTOGRAPHY_TABLE -> handler instanceof CartographyTableMenu;
            case BARREL -> handler instanceof ChestMenu generic && generic.getRowCount() == 3;
            case CHEST_DOUBLE -> handler instanceof ChestMenu generic && generic.getRowCount() >= 6;
            case SHULKER_BOX -> handler instanceof ChestMenu generic && generic.getRowCount() == 3;
            case HOPPER -> handler instanceof HopperMenu;
            case DISPENSER -> handler instanceof DispenserMenu;
            case BEACON -> handler instanceof BeaconMenu;
        };
    }

    private GuiSelectionMode resolveGuiMode(AbstractContainerMenu handler, Screen screen) {
        if (handler == null) {
            return null;
        }
        String title = screen != null && screen.getTitle() != null
            ? screen.getTitle().getString().toLowerCase(java.util.Locale.ROOT)
            : "";
        if (handler instanceof InventoryMenu) {
            return GuiSelectionMode.PLAYER_INVENTORY;
        }
        if (handler instanceof CraftingMenu) {
            return GuiSelectionMode.CRAFTING_TABLE;
        }
        if (handler instanceof FurnaceMenu) {
            return GuiSelectionMode.FURNACE;
        }
        if (handler instanceof BlastFurnaceMenu) {
            return GuiSelectionMode.BLAST_FURNACE;
        }
        if (handler instanceof SmokerMenu) {
            return GuiSelectionMode.SMOKER;
        }
        if (handler instanceof EnchantmentMenu) {
            return GuiSelectionMode.ENCHANTING_TABLE;
        }
        if (handler instanceof BrewingStandMenu) {
            return GuiSelectionMode.BREWING_STAND;
        }
        if (handler instanceof AnvilMenu) {
            return GuiSelectionMode.ANVIL;
        }
        if (handler instanceof GrindstoneMenu) {
            return GuiSelectionMode.GRINDSTONE;
        }
        if (handler instanceof StonecutterMenu) {
            return GuiSelectionMode.STONECUTTER;
        }
        if (handler instanceof SmithingMenu) {
            return GuiSelectionMode.SMITHING_TABLE;
        }
        if (handler instanceof LoomMenu) {
            return GuiSelectionMode.LOOM;
        }
        if (handler instanceof CartographyTableMenu) {
            return GuiSelectionMode.CARTOGRAPHY_TABLE;
        }
        if (handler instanceof HopperMenu) {
            return GuiSelectionMode.HOPPER;
        }
        if (handler instanceof DispenserMenu) {
            return GuiSelectionMode.DISPENSER;
        }
        if (handler instanceof BeaconMenu) {
            return GuiSelectionMode.BEACON;
        }
        if (handler instanceof ChestMenu generic) {
            if (generic.getRowCount() >= 6) {
                return GuiSelectionMode.CHEST_DOUBLE;
            }
            if (title.contains("shulker")) {
                return GuiSelectionMode.SHULKER_BOX;
            }
            if (title.contains("barrel")) {
                return GuiSelectionMode.BARREL;
            }
            return generic.getRowCount() == 3 ? GuiSelectionMode.BARREL : null;
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
