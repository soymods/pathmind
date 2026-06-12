package com.pathmind.util;

import net.minecraft.text.Text;

public final class PathmindI18n {
    private PathmindI18n() {
    }

    public static String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }
}
