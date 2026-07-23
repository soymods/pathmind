package com.pathmind.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

class VillagerTradeCatalogTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void previewsAreAvailableWithoutALoadedWorld() {
        Identifier id = Identifier.parse("minecraft:armorer");
        VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.getOptional(id).orElse(null);
        assertNotNull(profession);
        Holder<VillagerProfession> holder = BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(profession);
        ResourceKey<VillagerProfession> key = holder.unwrapKey()
            .orElse(ResourceKey.create(Registries.VILLAGER_PROFESSION, id));

        assertFalse(VillagerTradeCatalog.loadPreviews("armorer", key, holder).isEmpty());
    }
}
