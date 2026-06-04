package com.pathmind.nodes;

import net.minecraft.client.MinecraftClient;
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
}
