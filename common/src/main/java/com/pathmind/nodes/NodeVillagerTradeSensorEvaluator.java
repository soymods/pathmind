package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

final class NodeVillagerTradeSensorEvaluator {
    private final Node owner;

    NodeVillagerTradeSensorEvaluator(Node owner) {
        this.owner = owner;
    }

    boolean evaluateVillagerTrade() {
        MerchantOffers tradeOffers = getOpenTradeOffers();
        if (tradeOffers == null || tradeOffers.isEmpty()) {
            return false;
        }
        if (owner.shouldUseLegacyVillagerTradeSelection()) {
            return findTradeIndexFromLegacySelection(tradeOffers, false, false) >= 0;
        }
        int tradeIndex = owner.getConfiguredVillagerTradeNumber() - 1;
        return tradeIndex >= 0 && tradeIndex < tradeOffers.size() && tradeOffers.get(tradeIndex) != null;
    }

    boolean evaluateInStock() {
        MerchantOffers tradeOffers = getOpenTradeOffers();
        if (tradeOffers == null || tradeOffers.isEmpty()) {
            return false;
        }
        if (owner.shouldUseLegacyVillagerTradeSelection()) {
            return findTradeIndexFromLegacySelection(tradeOffers, true, false) >= 0;
        }
        int tradeIndex = owner.getConfiguredVillagerTradeNumber() - 1;
        return tradeIndex >= 0
            && tradeIndex < tradeOffers.size()
            && tradeOffers.get(tradeIndex) != null
            && !tradeOffers.get(tradeIndex).isOutOfStock();
    }

    void exportTradeSlotValues(Map<String, String> values) {
        if (values == null) {
            return;
        }
        MerchantOffers tradeOffers = getOpenTradeOffers();
        if (tradeOffers == null || tradeOffers.isEmpty()) {
            return;
        }
        Node numberNode = owner.resolveSensorParameterNode(owner.getAttachedParameter(0), 0);
        if (numberNode == null) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                owner.sendNodeErrorMessage(client, tr("pathmind.error.requiresParameterNode", owner.getType().getDisplayName()));
            }
            return;
        }
        if (!owner.providesTrait(numberNode, NodeValueTrait.NUMBER)) {
            owner.sendIncompatibleParameterMessage(numberNode);
            return;
        }
        Optional<Double> resolvedNumber = owner.resolveComparableNumber(numberNode);
        int tradeNumber = resolvedNumber.map(value -> Math.max(1, (int) Math.round(value))).orElse(1);
        int tradeIndex = tradeNumber - 1;
        if (tradeIndex < 0 || tradeIndex >= tradeOffers.size() || tradeOffers.get(tradeIndex) == null) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                owner.sendNodeErrorMessage(client, tr("pathmind.error.tradeUnavailable", tradeNumber));
            }
            return;
        }
        MerchantOffer offer = tradeOffers.get(tradeIndex);
        String tradeKey = buildTradeKey(
            offer.getCostA(),
            offer.getCostB(),
            offer.getResult()
        );
        values.put("Trade", tradeKey);
        values.put(Node.normalizeParameterKey("Trade"), tradeKey);
        values.put("Item", tradeKey);
        values.put(Node.normalizeParameterKey("Item"), tradeKey);
        values.put("Items", tradeKey);
        values.put(Node.normalizeParameterKey("Items"), tradeKey);
    }

    private MerchantOffers getOpenTradeOffers() {
        owner.ensureVillagerTradeNumberParameter();
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return null;
        }
        Screen currentScreen = client.screen;
        if (!(currentScreen instanceof MerchantScreen merchantScreen)) {
            owner.sendNodeErrorMessage(client, tr("pathmind.error.noVillagerTradingScreen"));
            return null;
        }
        MerchantMenu screenHandler = merchantScreen.getMenu();
        if (screenHandler == null) {
            return null;
        }
        return screenHandler.getOffers();
    }

    private String getTradeKeySellItemId(String tradeKey) {
        if (tradeKey == null || tradeKey.isEmpty()) {
            return "";
        }
        if (tradeKey.contains("|") && tradeKey.contains("@")) {
            String[] parts = tradeKey.split("\\|");
            if (parts.length > 0) {
                String sellPart = parts[parts.length - 1];
                int atIndex = sellPart.indexOf('@');
                if (atIndex > 0) {
                    return sellPart.substring(0, atIndex);
                }
            }
        }
        return tradeKey;
    }

    private String buildTradeKey(ItemStack firstBuy, ItemStack secondBuy, ItemStack sell) {
        return buildTradeKeyPart(firstBuy) + "|"
            + buildTradeKeyPart(secondBuy) + "|"
            + buildTradeKeyPart(sell);
    }

    private String buildTradeKeyPart(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "none@0";
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemId = id.toString();
        return itemId + "@" + stack.getCount();
    }

    int findTradeIndexFromLegacySelection(MerchantOffers tradeOffers, boolean requireInStock, boolean requireAffordable) {
        List<Integer> matches = findTradeIndexesFromLegacySelection(tradeOffers, requireInStock, requireAffordable);
        return matches.isEmpty() ? -1 : matches.getFirst();
    }

    @SuppressWarnings("unused")
    private boolean hasMultipleVillagerTradeSelections(Node parameterNode) {
        if (parameterNode == null || !owner.providesTrait(parameterNode, NodeValueTrait.VILLAGER_TRADE)) {
            return false;
        }
        Set<String> selections = new LinkedHashSet<>();
        for (String entry : owner.splitMultiValueList(Node.getParameterString(parameterNode, "Trade"))) {
            if (entry != null && !entry.isEmpty()) {
                selections.add(entry);
            }
        }
        for (String entry : owner.splitMultiValueList(Node.getParameterString(parameterNode, "Item"))) {
            if (entry != null && !entry.isEmpty()) {
                selections.add(entry);
            }
        }
        return selections.size() > 1;
    }

    private List<Integer> findTradeIndexesFromLegacySelection(MerchantOffers tradeOffers,
                                                              boolean requireInStock,
                                                              boolean requireAffordable) {
        if (tradeOffers == null || tradeOffers.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> desiredItemIds = new ArrayList<>();
        List<String> desiredTradeKeys = new ArrayList<>();
        RuntimeParameterData parameterData = owner.runtimeState().runtimeParameterData;
        if (parameterData != null && parameterData.targetItemId != null && !parameterData.targetItemId.isEmpty()) {
            desiredItemIds.add(parameterData.targetItemId);
        }
        if (parameterData != null && parameterData.targetTradeKey != null && !parameterData.targetTradeKey.isEmpty()) {
            desiredTradeKeys.add(parameterData.targetTradeKey);
        }

        Node parameterNode = owner.resolveSensorParameterNode(owner.getAttachedParameter(), 0);
        if (parameterNode != null && owner.providesTrait(parameterNode, NodeValueTrait.VILLAGER_TRADE)) {
            for (String entry : owner.splitMultiValueList(Node.getParameterString(parameterNode, "Trade"))) {
                if (entry.contains("|") && entry.contains("@")) {
                    if (!desiredTradeKeys.contains(entry)) {
                        desiredTradeKeys.add(entry);
                    }
                    String sellItemId = getTradeKeySellItemId(entry);
                    if (!sellItemId.isEmpty() && !desiredItemIds.contains(sellItemId)) {
                        desiredItemIds.add(sellItemId);
                    }
                }
            }
            for (String entry : owner.splitMultiValueList(Node.getParameterString(parameterNode, "Item"))) {
                if (entry.contains("|") && entry.contains("@")) {
                    if (!desiredTradeKeys.contains(entry)) {
                        desiredTradeKeys.add(entry);
                    }
                    String sellItemId = getTradeKeySellItemId(entry);
                    if (!sellItemId.isEmpty() && !desiredItemIds.contains(sellItemId)) {
                        desiredItemIds.add(sellItemId);
                    }
                } else if (!entry.isEmpty() && !desiredItemIds.contains(entry)) {
                    desiredItemIds.add(entry);
                }
            }
        }

        Minecraft client = Minecraft.getInstance();
        MerchantMenu screenHandler = null;
        if (client != null && client.screen instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen merchantScreen) {
            screenHandler = merchantScreen.getMenu();
        }

        List<Integer> matches = new ArrayList<>();
        Set<Integer> seenMatches = new LinkedHashSet<>();
        List<String> orderedSelections = new ArrayList<>(desiredTradeKeys);
        for (String itemId : desiredItemIds) {
            if (!orderedSelections.contains(itemId)) {
                orderedSelections.add(itemId);
            }
        }

        for (String desired : orderedSelections) {
            for (int i = 0; i < tradeOffers.size(); i++) {
                MerchantOffer offer = tradeOffers.get(i);
                if (!isLegacyTradeSelectionMatch(client, screenHandler, offer, desired, requireInStock, requireAffordable)) {
                    continue;
                }
                if (seenMatches.add(i)) {
                    matches.add(i);
                }
            }
        }

        if (!matches.isEmpty()) {
            return matches;
        }

        for (int i = 0; i < tradeOffers.size(); i++) {
            MerchantOffer offer = tradeOffers.get(i);
            if (isLegacyTradeSelectionMatch(client, screenHandler, offer, null, requireInStock, requireAffordable)) {
                matches.add(i);
            }
        }

        return matches;
    }

    private boolean isLegacyTradeSelectionMatch(Minecraft client,
                                                MerchantMenu screenHandler,
                                                MerchantOffer offer,
                                                String desiredSelection,
                                                boolean requireInStock,
                                                boolean requireAffordable) {
        if (offer == null) {
            return false;
        }
        if (requireInStock && offer.isOutOfStock()) {
            return false;
        }
        if (requireAffordable && (client == null || client.player == null || screenHandler == null
            || !owner.canAffordTrade(client.player, screenHandler, offer))) {
            return false;
        }
        if (desiredSelection == null || desiredSelection.isEmpty()) {
            return true;
        }
        String offerKey = buildTradeKey(
            offer.getCostA(),
            offer.getCostB(),
            offer.getResult()
        );
        if (desiredSelection.contains("|") && desiredSelection.contains("@")) {
            return desiredSelection.equals(offerKey);
        }
        return desiredSelection.equals(getTradeKeySellItemId(offerKey));
    }

}
