package com.pathmind.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;

/** Typed adapter for the pre-26 static villager trade tables. */
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
        var levels = resolveLevels(VillagerTrades.TRADES, professionId, professionKey, profession);
        if ((levels == null || levels.isEmpty()) && VillagerTrades.EXPERIMENTAL_TRADES != null) {
            levels = resolveLevels(VillagerTrades.EXPERIMENTAL_TRADES, professionId, professionKey, profession);
        }
        Level activeLevel = serverLevel != null ? serverLevel : clientLevel;
        if (levels == null || levels.isEmpty() || activeLevel == null) {
            return List.of();
        }

        Villager villager = new Villager(EntityType.VILLAGER, activeLevel);
        RandomSource random = RandomSource.create();
        List<Offer> result = new ArrayList<>();
        for (var levelEntry : levels.int2ObjectEntrySet()) {
            VillagerTrades.ItemListing[] factories = levelEntry.getValue();
            if (factories == null) continue;
            for (VillagerTrades.ItemListing factory : factories) {
                MerchantOffer offer = VillagerTradeOfferBridge.create(factory, serverLevel, villager, random);
                if (offer == null || offer.getResult().isEmpty()) continue;
                result.add(new Offer(levelEntry.getIntKey(), offer.getCostA(), offer.getCostB(), offer.getResult()));
            }
        }
        return result;
    }

    private static it.unimi.dsi.fastutil.ints.Int2ObjectMap<VillagerTrades.ItemListing[]> resolveLevels(
        Map<?, it.unimi.dsi.fastutil.ints.Int2ObjectMap<VillagerTrades.ItemListing[]>> table,
        String professionId,
        ResourceKey<VillagerProfession> professionKey,
        Holder<VillagerProfession> profession
    ) {
        if (table == null || table.isEmpty()) return null;
        var levels = table.get(professionKey);
        if ((levels == null || levels.isEmpty()) && profession != null) levels = table.get(profession);
        if ((levels == null || levels.isEmpty()) && profession != null) levels = table.get(profession.value());
        if (levels != null && !levels.isEmpty()) return levels;

        for (var entry : table.entrySet()) {
            Identifier id = keyIdentifier(entry.getKey());
            if (id != null && id.getPath().equals(professionId)) return entry.getValue();
        }
        return levels;
    }

    private static Identifier keyIdentifier(Object key) {
        if (key instanceof ResourceKey<?> resourceKey) return resourceKey.identifier();
        if (key instanceof Holder<?> holder) {
            ResourceKey<?> resourceKey = holder.unwrapKey().orElse(null);
            if (resourceKey != null) return resourceKey.identifier();
            if (holder.value() instanceof VillagerProfession profession) {
                return BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            }
        }
        if (key instanceof VillagerProfession profession) {
            return BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        }
        return key instanceof Identifier id ? id : null;
    }

}
