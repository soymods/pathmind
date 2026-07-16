package com.pathmind.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * Bridges VillagerData.withProfession() across Minecraft versions.
 * Some versions take RegistryEntry&lt;VillagerProfession&gt;, others take VillagerProfession directly.
 */
public final class VillagerDataCompatibilityBridge {
    private static final Method WITH_PROFESSION_ENTRY = resolveMethod("withProfession", Holder.class);
    private static final Method WITH_PROFESSION_VALUE = resolveMethod("withProfession", VillagerProfession.class);

    private VillagerDataCompatibilityBridge() {
    }

    public static VillagerData withProfession(VillagerData data, Holder<VillagerProfession> professionEntry) {
        if (data == null || professionEntry == null) {
            return data;
        }

        // Try method that takes RegistryEntry (newer versions)
        if (WITH_PROFESSION_ENTRY != null) {
            try {
                Object result = WITH_PROFESSION_ENTRY.invoke(data, professionEntry);
                if (result instanceof VillagerData villagerData) {
                    return villagerData;
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }

        // Try method that takes VillagerProfession directly (older versions)
        if (WITH_PROFESSION_VALUE != null) {
            try {
                Object result = WITH_PROFESSION_VALUE.invoke(data, professionEntry.value());
                if (result instanceof VillagerData villagerData) {
                    return villagerData;
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }

        // Fallback: return original data
        return data;
    }

    private static Method resolveMethod(String name, Class<?>... params) {
        try {
            Method method = VillagerData.class.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
