package com.pathmind.nodes;

import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

final class InventorySlotParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_INVENTORY_SLOT)
            .parameterBehavior(InventorySlotParameterDefinition::exportValues)
            .comparableBehavior(NodeBehaviorDefinitionSupport.numberComparable((owner, node) ->
                owner.resolveInventorySlotCount(node).map(count -> (double) count)))
            .build();
    }

    private static Map<String, String> exportValues(Node node, Map<String, String> values) {
        String slot = values.get("Slot");
        if (slot != null) {
            NodeBehaviorDefinitionSupport.put(values, "SourceSlot", slot);
            NodeBehaviorDefinitionSupport.put(values, "TargetSlot", slot);
            NodeBehaviorDefinitionSupport.put(values, "FirstSlot", slot);
            NodeBehaviorDefinitionSupport.put(values, "SecondSlot", slot);
        }
        ItemStack resolvedStack = InventorySlotValueResolver.resolveComparableInventorySlotStack(values);
        if (resolvedStack != null && !resolvedStack.isEmpty()) {
            Identifier itemId = Registries.ITEM.getId(resolvedStack.getItem());
            if (itemId != null) {
                String itemValue = itemId.toString();
                NodeBehaviorDefinitionSupport.put(values, "Item", itemValue);
                NodeBehaviorDefinitionSupport.put(values, "Items", itemValue);
            }
            String countValue = Integer.toString(resolvedStack.getCount());
            NodeBehaviorDefinitionSupport.put(values, "Count", countValue);
            NodeBehaviorDefinitionSupport.put(values, "Amount", countValue);
        }
        return values;
    }

    private InventorySlotParameterDefinition() {
    }
}
