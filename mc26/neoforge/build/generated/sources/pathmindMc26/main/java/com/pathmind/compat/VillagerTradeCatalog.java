package com.pathmind.compat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/** Typed adapter for Minecraft 26.1's data-driven villager trade registries. */
public final class VillagerTradeCatalog {
    private VillagerTradeCatalog() {
    }

    public record Offer(int level, ItemStack firstBuy, ItemStack secondBuy, ItemStack sell) {
    }

    public static List<Offer> load(
        String professionId,
        ResourceKey<VillagerProfession> professionKey,
        Holder<VillagerProfession> profession,
        ServerLevel serverLevel,
        Level clientLevel
    ) {
        // Trade predicates and item modifiers require a server loot context.
        // On remote servers the open-merchant path remains the authoritative source.
        if (profession == null || serverLevel == null) return List.of();

        Registry<TradeSet> tradeSets = serverLevel.registryAccess().lookupOrThrow(Registries.TRADE_SET);
        Villager villager = new Villager(EntityType.VILLAGER, serverLevel);
        LootParams params = new LootParams.Builder(serverLevel)
            .withParameter(LootContextParams.ORIGIN, villager.position())
            .withParameter(LootContextParams.THIS_ENTITY, villager)
            .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
            .create(LootContextParamSets.VILLAGER_TRADE);

        List<Offer> result = new ArrayList<>();
        for (int level = 1; level <= 5; level++) {
            ResourceKey<TradeSet> tradeSetKey = profession.value().getTrades(level);
            if (tradeSetKey == null) continue;
            TradeSet tradeSet = tradeSets.getOptional(tradeSetKey).orElse(null);
            if (tradeSet == null) continue;

            LootContext context = new LootContext.Builder(params).create(tradeSet.randomSequence());
            for (Holder<VillagerTrade> trade : tradeSet.getTrades()) {
                MerchantOffer offer = trade.value().getOffer(context);
                if (offer == null || offer.getResult().isEmpty()) continue;
                result.add(new Offer(level, offer.getCostA(), offer.getCostB(), offer.getResult()));
            }
        }
        return result;
    }
}
