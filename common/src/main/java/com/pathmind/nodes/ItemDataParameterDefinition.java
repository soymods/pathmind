package com.pathmind.nodes;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Exports a selected piece of data from an attached item target. */
final class ItemDataParameterDefinition {
    static final String FIELD_ITEM_ID = "item_id";
    static final String FIELD_CUSTOM_NAME = "custom_name";
    static final String FIELD_HAS_CUSTOM_NAME = "has_custom_name";
    static final String FIELD_LORE = "lore";
    static final String FIELD_COUNT = "count";
    static final String FIELD_DAMAGE = "damage";
    static final String FIELD_MAX_DAMAGE = "max_damage";
    static final String FIELD_ENCHANTMENTS = "enchantments";
    static final String FIELD_CUSTOM_DATA = "custom_data";

    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_ITEM_DATA)
            .parameterBehavior(ItemDataParameterDefinition::exportValues)
            .comparableBehavior(NodeBehaviorDefinitionSupport.combinedComparable(
                (owner, node) -> Optional.of(resolveValue(node)),
                (owner, node) -> resolveNumber(node)))
            .build();
    }

    static EnumSet<NodeValueTrait> providedTraits(Node node) {
        String field = field(node);
        if (FIELD_COUNT.equals(field) || FIELD_DAMAGE.equals(field) || FIELD_MAX_DAMAGE.equals(field)) {
            return EnumSet.of(NodeValueTrait.NUMBER);
        }
        if (FIELD_HAS_CUSTOM_NAME.equals(field)) {
            return EnumSet.of(NodeValueTrait.BOOLEAN);
        }
        return EnumSet.of(NodeValueTrait.MESSAGE);
    }

    private static Map<String, String> exportValues(Node node, Map<String, String> values) {
        String value = resolveValue(node);
        NodeBehaviorDefinitionSupport.put(values, "Value", value);
        NodeBehaviorDefinitionSupport.put(values, "Text", value);
        NodeBehaviorDefinitionSupport.put(values, "Message", value);
        if (providedTraits(node).contains(NodeValueTrait.NUMBER)) {
            NodeBehaviorDefinitionSupport.put(values, "Amount", value);
            NodeBehaviorDefinitionSupport.put(values, "Count", value);
        }
        return values;
    }

    private static Optional<Double> resolveNumber(Node node) {
        if (!providedTraits(node).contains(NodeValueTrait.NUMBER)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(resolveValue(node)));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static String resolveValue(Node node) {
        ItemStack stack = resolveStack(node);
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return switch (field(node)) {
            case FIELD_ITEM_ID -> itemId(stack);
            case FIELD_CUSTOM_NAME -> componentText(stack.get(DataComponents.CUSTOM_NAME));
            case FIELD_HAS_CUSTOM_NAME -> Boolean.toString(stack.get(DataComponents.CUSTOM_NAME) != null);
            case FIELD_LORE -> loreText(stack.get(DataComponents.LORE));
            case FIELD_COUNT -> Integer.toString(stack.getCount());
            case FIELD_DAMAGE -> Integer.toString(stack.getDamageValue());
            case FIELD_MAX_DAMAGE -> Integer.toString(stack.getMaxDamage());
            case FIELD_ENCHANTMENTS -> enchantmentText(stack.getEnchantments());
            case FIELD_CUSTOM_DATA -> customDataText(stack.get(DataComponents.CUSTOM_DATA));
            default -> "";
        };
    }

    private static ItemStack resolveStack(Node node) {
        if (node == null) {
            return null;
        }
        Node targetNode = node.getAttachedParameter(0);
        if (targetNode == null) {
            return null;
        }
        if (node.providesTrait(targetNode, NodeValueTrait.INVENTORY_SLOT)) {
            return InventorySlotValueResolver.resolveComparableInventorySlotStack(targetNode.exportParameterValues());
        }
        if (!node.providesTrait(targetNode, NodeValueTrait.ITEM)) {
            return null;
        }
        for (String itemId : node.resolveItemIdsFromParameter(targetNode)) {
            Identifier identifier = Identifier.tryParse(itemId);
            Item item = identifier == null ? null : BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
            if (item != null) {
                return new ItemStack(item);
            }
        }
        return null;
    }

    private static String field(Node node) {
        String value = Node.getParameterString(node, "Field");
        return value == null || value.isBlank() ? FIELD_ITEM_ID : value;
    }

    private static String itemId(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private static String componentText(Component component) {
        return component == null ? "" : component.getString();
    }

    private static String loreText(ItemLore lore) {
        if (lore == null || lore.lines().isEmpty()) {
            return "";
        }
        return lore.lines().stream().map(Component::getString).collect(Collectors.joining("\n"));
    }

    private static String enchantmentText(ItemEnchantments enchantments) {
        if (enchantments == null || enchantments.isEmpty()) {
            return "";
        }
        return enchantments.keySet().stream()
            .map(holder -> holder.unwrapKey().map(key -> key.identifier().toString()).orElse(""))
            .filter(value -> !value.isEmpty())
            .collect(Collectors.joining(","));
    }

    private static String customDataText(CustomData customData) {
        return customData == null || customData.isEmpty() ? "" : customData.copyTag().toString();
    }

    private ItemDataParameterDefinition() {
    }
}
