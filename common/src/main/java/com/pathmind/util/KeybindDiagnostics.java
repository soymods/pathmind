package com.pathmind.util;

import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class KeybindDiagnostics {
    private KeybindDiagnostics() {
    }

    public static String describeConflict(Minecraft client, KeyMapping target, KeyMapping... ignoredMappings) {
        if (target == null) {
            return Component.translatable("pathmind.keybind.unavailable").getString();
        }
        if (target.isUnbound()) {
            return Component.translatable("pathmind.keybind.unbound").getString();
        }
        if (client == null || client.options == null || client.options.keyMappings == null) {
            return null;
        }
        Set<KeyMapping> ignored = ignoredMappings == null ? Set.of() : Set.of(ignoredMappings);
        for (KeyMapping candidate : client.options.keyMappings) {
            if (candidate == null || candidate == target || ignored.contains(candidate)) {
                continue;
            }
            if (target.same(candidate)) {
                String action = Component.translatable(candidate.getName()).getString();
                return Component.translatable("pathmind.keybind.conflict", action).getString();
            }
        }
        return null;
    }
}
