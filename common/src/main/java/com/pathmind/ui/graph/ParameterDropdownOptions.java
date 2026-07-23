package com.pathmind.ui.graph;

import static com.pathmind.ui.graph.ParameterTypeClassifier.isAttributeDetectionAttributeParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isAttributeDetectionBooleanValueParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockFaceParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockStateParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBooleanLiteralParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isDirectionParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isEntityParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isEntityStateParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isFabricEventSensorParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isHandParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isItemParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isMouseButtonParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isVillagerProfessionParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isVillagerTradeParameter;
import static com.pathmind.util.PathmindI18n.tr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.pathmind.nodes.AttributeDetectionConfig;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.compat.VillagerTradeCatalog;
import com.pathmind.util.BlockSelection;
import com.pathmind.util.EntityStateOptions;
import com.pathmind.util.FabricEventTracker;
import com.pathmind.util.GuiSelectionMode;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Pure builders that turn a node + parameter index + query into the list of
 * selectable dropdown options (and the item-stack icon for an option). These
 * depend only on the node, registries, and static config -- never on editor UI
 * state -- so they live apart from {@link NodeGraph} and are consumed there via
 * static import.
 */
final class ParameterDropdownOptions {

    private ParameterDropdownOptions() {
    }

    static List<ParameterDropdownOption> getParameterDropdownOptions(Node node, int index, String query) {
        String lowered = query == null ? "" : query.toLowerCase(Locale.ROOT);
        if (isAttributeDetectionAttributeParameter(node, index)) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            for (AttributeDetectionConfig.AttributeOption option : AttributeDetectionConfig.getAttributesForTarget(getAttributeDetectionTargetKind(node))) {
                result.add(new ParameterDropdownOption(option.label(), option.id()));
            }
            return filterDropdownOptions(result, lowered);
        }
        if (isAttributeDetectionBooleanValueParameter(node, index)) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            result.add(new ParameterDropdownOption(tr("pathmind.option.true"), "true"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.false"), "false"));
            return filterDropdownOptions(result, lowered);
        }
        if (isVillagerProfessionParameter(node, index)) {
            return getVillagerProfessionDropdownOptions(lowered);
        }
        if (isVillagerTradeParameter(node, index)) {
            return getVillagerTradeDropdownOptions(node, lowered);
        }
        if (isBlockStateParameter(node, index)) {
            return getBlockStateDropdownOptions(node, lowered);
        }
        if (isEntityStateParameter(node, index)) {
            return getEntityStateDropdownOptions(node, lowered);
        }
        if (isFabricEventSensorParameter(node, index)) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            result.add(new ParameterDropdownOption(tr("pathmind.option.any"), "Any"));
            for (String eventName : FabricEventTracker.getSupportedEvents()) {
                result.add(new ParameterDropdownOption(eventName, eventName));
            }
            return filterDropdownOptions(result, lowered);
        }

        List<String> source;
        if (node != null && node.getType() == NodeType.PARAM_GUI) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            result.add(new ParameterDropdownOption(tr("pathmind.option.any"), ""));
            for (GuiSelectionMode mode : GuiSelectionMode.valuesList()) {
                result.add(new ParameterDropdownOption(mode.getDisplayName(), mode.getId()));
            }
            return filterDropdownOptions(result, lowered);
        }
        if (isMouseButtonParameter(node, null)) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            result.add(new ParameterDropdownOption(tr("pathmind.option.mouse.left"), "Left"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.mouse.right"), "Right"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.mouse.middle"), "Middle"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.mouse.button4"), "Button 4"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.mouse.button5"), "Button 5"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.mouse.button6"), "Button 6"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.mouse.button7"), "Button 7"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.mouse.button8"), "Button 8"));
            return filterDropdownOptions(result, lowered);
        }
        if (isHandParameter(node, null)) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            result.add(new ParameterDropdownOption(tr("pathmind.option.hand.main"), "main"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.hand.offhand"), "offhand"));
            return filterDropdownOptions(result, lowered);
        }
        if (isDirectionParameter(node, index)) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            result.add(new ParameterDropdownOption(tr("pathmind.option.direction.north"), "north"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.direction.south"), "south"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.direction.east"), "east"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.direction.west"), "west"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.direction.up"), "up"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.direction.down"), "down"));
            return filterDropdownOptions(result, lowered);
        }
        if (isBooleanLiteralParameter(node, index)) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            result.add(new ParameterDropdownOption(tr("pathmind.option.true"), "true"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.false"), "false"));
            return filterDropdownOptions(result, lowered);
        }
        if (isBlockFaceParameter(node, index)) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            result.add(new ParameterDropdownOption(tr("pathmind.option.direction.north"), "north"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.direction.south"), "south"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.direction.east"), "east"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.direction.west"), "west"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.direction.up"), "up"));
            result.add(new ParameterDropdownOption(tr("pathmind.option.direction.down"), "down"));
            return filterDropdownOptions(result, lowered);
        }
        if (isBlockParameter(node, index)) {
            source = RegistryStringCache.BLOCK_IDS;
        } else if (isItemParameter(node, index)) {
            source = RegistryStringCache.ITEM_IDS;
        } else if (isEntityParameter(node, index)) {
            source = RegistryStringCache.ENTITY_IDS;
        } else {
            return Collections.emptyList();
        }

        List<ParameterDropdownOption> starts = new ArrayList<>();
        List<ParameterDropdownOption> contains = new ArrayList<>();
        List<ParameterDropdownOption> result = new ArrayList<>(65);
        result.add(new ParameterDropdownOption(tr("pathmind.option.any"), ""));
        if (lowered.isEmpty()) {
            int limit = 64;
            int added = 0;
            for (String option : source) {
                result.add(new ParameterDropdownOption(option, option));
                added++;
                if (added >= limit) {
                    break;
                }
            }
            return result;
        }
        for (String option : source) {
            String lower = option.toLowerCase(Locale.ROOT);
            if (!lower.contains(lowered)) {
                continue;
            }
            ParameterDropdownOption entry = new ParameterDropdownOption(option, option);
            if (lower.startsWith(lowered)) {
                starts.add(entry);
            } else {
                contains.add(entry);
            }
            if (starts.size() + contains.size() >= 64) {
                break;
            }
        }
        result.addAll(starts);
        result.addAll(contains);
        return result;
    }

    static List<ParameterDropdownOption> filterDropdownOptions(List<ParameterDropdownOption> options, String lowered) {
        if (options == null || options.isEmpty()) {
            return Collections.emptyList();
        }
        if (lowered == null || lowered.isEmpty()) {
            return options;
        }
        List<ParameterDropdownOption> starts = new ArrayList<>();
        List<ParameterDropdownOption> contains = new ArrayList<>();
        for (ParameterDropdownOption option : options) {
            if (option == null || option.label() == null) {
                continue;
            }
            String labelLower = option.label().toLowerCase(Locale.ROOT);
            String valueLower = option.value() == null ? "" : option.value().toLowerCase(Locale.ROOT);
            if (!labelLower.contains(lowered) && !valueLower.contains(lowered)) {
                continue;
            }
            if (labelLower.startsWith(lowered) || valueLower.startsWith(lowered)) {
                starts.add(option);
            } else {
                contains.add(option);
            }
        }
        List<ParameterDropdownOption> result = new ArrayList<>(starts.size() + contains.size());
        result.addAll(starts);
        result.addAll(contains);
        return result;
    }

    static List<ParameterDropdownOption> getBlockStateDropdownOptions(Node node, String loweredQuery) {
        if (node == null) {
            return Collections.emptyList();
        }
        String blockId = getNormalizedBlockIdForStateOptions(node);
        if (blockId.isEmpty()) {
            return Collections.emptyList();
        }
        List<BlockSelection.StateOption> options = BlockSelection.getStateOptions(blockId);
        if (options.isEmpty()) {
            return Collections.emptyList();
        }
        List<ParameterDropdownOption> results = new ArrayList<>();
        boolean includeAnyState = loweredQuery == null || loweredQuery.isEmpty();
        if (includeAnyState) {
            results.add(new ParameterDropdownOption(tr("pathmind.option.anyState"), ""));
        }
        String lowered = loweredQuery == null ? "" : loweredQuery;
        for (BlockSelection.StateOption option : options) {
            String value = option.value();
            String label = option.displayText();
            if (!lowered.isEmpty()) {
                String valueLower = value.toLowerCase(Locale.ROOT);
                String labelLower = label.toLowerCase(Locale.ROOT);
                if (!valueLower.contains(lowered) && !labelLower.contains(lowered)) {
                    continue;
                }
            }
            results.add(new ParameterDropdownOption(label, value));
            if (results.size() >= 64) {
                break;
            }
        }
        return results;
    }

    static List<ParameterDropdownOption> getEntityStateDropdownOptions(Node node, String loweredQuery) {
        if (node == null) {
            return Collections.emptyList();
        }
        String entityId = getNormalizedEntityIdForStateOptions(node);
        if (entityId.isEmpty()) {
            return Collections.emptyList();
        }
        Identifier id = Identifier.tryParse(entityId);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return Collections.emptyList();
        }
        Minecraft client = Minecraft.getInstance();
        net.minecraft.world.level.Level world = client != null ? client.level : null;
        List<EntityStateOptions.StateOption> options = EntityStateOptions.getOptions(BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null), world);
        if (options.isEmpty()) {
            return Collections.emptyList();
        }
        List<ParameterDropdownOption> results = new ArrayList<>();
        boolean includeAnyState = loweredQuery == null || loweredQuery.isEmpty();
        if (includeAnyState) {
            results.add(new ParameterDropdownOption(tr("pathmind.option.anyState"), ""));
        }
        String lowered = loweredQuery == null ? "" : loweredQuery;
        for (EntityStateOptions.StateOption option : options) {
            String value = option.value();
            String label = option.displayText();
            if (!lowered.isEmpty()) {
                String valueLower = value.toLowerCase(Locale.ROOT);
                String labelLower = label.toLowerCase(Locale.ROOT);
                if (!valueLower.contains(lowered) && !labelLower.contains(lowered)) {
                    continue;
                }
            }
            results.add(new ParameterDropdownOption(label, value));
            if (results.size() >= 64) {
                break;
            }
        }
        return results;
    }

    static String getNormalizedBlockIdForStateOptions(Node node) {
        if (node == null || node.getType() != NodeType.PARAM_BLOCK) {
            return "";
        }
        NodeParameter blockParam = node.getParameter("Block");
        if (blockParam == null) {
            return "";
        }
        String raw = blockParam.getStringValue();
        if (raw == null) {
            return "";
        }
        int comma = raw.indexOf(',');
        if (comma >= 0) {
            raw = raw.substring(0, comma);
        }
        String stripped = BlockSelection.stripState(raw);
        if (stripped == null || stripped.isEmpty()) {
            return "";
        }
        String fullId = stripped.contains(":") ? stripped : "minecraft:" + stripped;
        Identifier id = Identifier.tryParse(fullId);
        if (id == null) {
            return "";
        }
        return id.toString();
    }

    static String getNormalizedEntityIdForStateOptions(Node node) {
        if (node == null || node.getType() != NodeType.PARAM_ENTITY) {
            return "";
        }
        NodeParameter entityParam = node.getParameter("Entity");
        if (entityParam == null) {
            return "";
        }
        String raw = entityParam.getStringValue();
        if (raw == null) {
            return "";
        }
        int comma = raw.indexOf(',');
        if (comma >= 0) {
            raw = raw.substring(0, comma);
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String sanitized = trimmed.toLowerCase(Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9_:\\/.-]", "");
        if (sanitized.isEmpty()) {
            return "";
        }
        String fullId = sanitized.contains(":") ? sanitized : "minecraft:" + sanitized;
        Identifier id = Identifier.tryParse(fullId);
        if (id == null) {
            return "";
        }
        return id.toString();
    }

    static AttributeDetectionConfig.TargetKind getAttributeDetectionTargetKind(Node node) {
        if (node == null || !node.isAttributeDetectionSensor()) {
            return null;
        }
        Node attached = node.getAttachedParameter(0);
        if (attached == null) {
            return null;
        }
        return AttributeDetectionConfig.inferTargetKind(attached.getType());
    }

    static ItemStack resolveParameterDropdownIcon(Node node, int index, String optionValue) {
        if (node == null || optionValue == null || optionValue.isEmpty()
            || isBlockStateParameter(node, index)
            || isEntityStateParameter(node, index)) {
            return ItemStack.EMPTY;
        }
        String iconValue = isVillagerTradeParameter(node, index)
            ? getTradeKeySellItemId(optionValue)
            : optionValue;
        String fullId = iconValue.contains(":") ? iconValue : "minecraft:" + iconValue;
        Identifier id = Identifier.tryParse(fullId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        if (isBlockParameter(node, index)) {
            var block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (block == null) {
                return ItemStack.EMPTY;
            }
            Item item = block.asItem();
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item);
        }
        if (isItemParameter(node, index) || isVillagerTradeParameter(node, index)) {
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item);
        }
        if (isEntityParameter(node, index)) {
            var entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (entityType == null) {
                return ItemStack.EMPTY;
            }
            try {
                Item spawnEgg = SpawnEggItem.byId(entityType);
                if (spawnEgg == null || spawnEgg == Items.AIR) {
                    return ItemStack.EMPTY;
                }
                return new ItemStack(spawnEgg);
            } catch (RuntimeException e) {
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }

    private static List<ParameterDropdownOption> getVillagerProfessionDropdownOptions(String loweredQuery) {
        List<ParameterDropdownOption> result = new ArrayList<>();
        for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
            Identifier id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            if (id == null || "none".equals(id.getPath())) {
                continue;
            }
            String professionId = id.getPath();
            result.add(new ParameterDropdownOption(titleCase(professionId), professionId));
        }
        result.sort((left, right) -> left.label().compareToIgnoreCase(right.label()));
        return filterDropdownOptions(result, loweredQuery);
    }

    private static List<ParameterDropdownOption> getVillagerTradeDropdownOptions(Node node, String loweredQuery) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || node == null) {
            return Collections.emptyList();
        }
        NodeParameter professionParameter = node.getParameter("Profession");
        String professionId = professionParameter != null ? professionParameter.getStringValue() : "";
        if (professionId == null || professionId.isBlank()) {
            return Collections.emptyList();
        }
        String normalizedProfession = professionId.contains(":")
            ? professionId.substring(professionId.indexOf(':') + 1)
            : professionId;
        Identifier professionIdentifier = Identifier.tryParse("minecraft:" + normalizedProfession.toLowerCase(Locale.ROOT));
        if (professionIdentifier == null) {
            return Collections.emptyList();
        }
        VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.getOptional(professionIdentifier).orElse(null);
        if (profession == null) {
            return Collections.emptyList();
        }
        Holder<VillagerProfession> professionHolder = BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(profession);
        ResourceKey<VillagerProfession> professionKey = professionHolder.unwrapKey()
            .orElse(ResourceKey.create(Registries.VILLAGER_PROFESSION, professionIdentifier));
        ServerLevel serverLevel = client.getSingleplayerServer() != null
            ? client.getSingleplayerServer().overworld()
            : null;
        List<ParameterDropdownOption> result = new ArrayList<>();
        for (VillagerTradeCatalog.Offer offer : VillagerTradeCatalog.load(
            normalizedProfession,
            professionKey,
            professionHolder,
            serverLevel,
            client.level
        )) {
            String tradeKey = buildTradeKey(offer.firstBuy(), offer.secondBuy(), offer.sell());
            result.add(new ParameterDropdownOption(
                formatTradeOption(offer.level(), offer.firstBuy(), offer.secondBuy(), offer.sell()),
                tradeKey
            ));
        }
        result.sort((left, right) -> left.label().compareToIgnoreCase(right.label()));
        return filterDropdownOptions(result, loweredQuery);
    }

    private static String formatTradeOption(int level, ItemStack firstBuy, ItemStack secondBuy, ItemStack sell) {
        StringBuilder label = new StringBuilder("Lvl ").append(level).append(": ");
        appendTradeStack(label, firstBuy);
        if (secondBuy != null && !secondBuy.isEmpty()) {
            label.append(" + ");
            appendTradeStack(label, secondBuy);
        }
        label.append(" -> ");
        appendTradeStack(label, sell);
        return label.toString();
    }

    private static void appendTradeStack(StringBuilder label, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (stack.getCount() > 1) {
            label.append(stack.getCount()).append("x ");
        }
        label.append(stack.getHoverName().getString());
    }

    private static String buildTradeKey(ItemStack firstBuy, ItemStack secondBuy, ItemStack sell) {
        return buildTradeKeyPart(firstBuy) + "|"
            + buildTradeKeyPart(secondBuy) + "|"
            + buildTradeKeyPart(sell);
    }

    private static String buildTradeKeyPart(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "none@0";
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return (id != null ? id.toString() : "unknown") + "@" + stack.getCount();
    }

    private static String getTradeKeySellItemId(String tradeKey) {
        if (tradeKey == null || tradeKey.isEmpty()) {
            return "";
        }
        String[] parts = tradeKey.split("\\|");
        String sellPart = parts[parts.length - 1];
        int countSeparator = sellPart.indexOf('@');
        return countSeparator >= 0 ? sellPart.substring(0, countSeparator) : sellPart;
    }

    private static String titleCase(String value) {
        StringBuilder result = new StringBuilder();
        for (String part : value.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                result.append(part.substring(1));
            }
        }
        return result.toString();
    }
}
