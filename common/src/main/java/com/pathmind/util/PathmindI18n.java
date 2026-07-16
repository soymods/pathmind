package com.pathmind.util;

import net.minecraft.network.chat.Component;

public final class PathmindI18n {
    private PathmindI18n() {
    }

    public static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
