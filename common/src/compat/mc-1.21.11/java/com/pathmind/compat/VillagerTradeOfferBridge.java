package com.pathmind.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;

final class VillagerTradeOfferBridge {
    private VillagerTradeOfferBridge() {
    }

    static MerchantOffer create(
        VillagerTrades.ItemListing factory,
        ServerLevel serverLevel,
        Villager villager,
        RandomSource random
    ) {
        return factory == null || serverLevel == null
            ? null
            : factory.getOffer(serverLevel, villager, random);
    }
}
