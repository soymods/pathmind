package com.pathmind.util;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;

import java.lang.reflect.Constructor;

/**
 * Bridges ChatScreen constructors across 1.21.x.
 */
public final class ChatScreenCompatibilityBridge {
    private static final Constructor<?> CTOR_WITH_BOOLEAN = resolveConstructor(String.class, boolean.class);
    private static final Constructor<?> CTOR_SIMPLE = resolveConstructor(String.class);

    private ChatScreenCompatibilityBridge() {
    }

    public static Screen create(String initialText) {
        String text = initialText == null ? "" : initialText;
        if (CTOR_WITH_BOOLEAN != null) {
            try {
                Object screen = CTOR_WITH_BOOLEAN.newInstance(text, false);
                return screen instanceof Screen cast ? cast : null;
            } catch (ReflectiveOperationException ignored) {
                // Fall back to other constructor.
            }
        }
        if (CTOR_SIMPLE != null) {
            try {
                Object screen = CTOR_SIMPLE.newInstance(text);
                return screen instanceof Screen cast ? cast : null;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Constructor<?> resolveConstructor(Class<?>... params) {
        try {
            Constructor<?> ctor = ChatScreen.class.getConstructor(params);
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
