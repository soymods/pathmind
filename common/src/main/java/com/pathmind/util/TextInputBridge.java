package com.pathmind.util;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;

/**
 * Keeps SDL text input started for Pathmind's custom (non-{@code EditBox}) text editors.
 *
 * <p>Minecraft 26.3 only delivers character events while text input is started through
 * {@code TextInputManager}; vanilla widgets manage that themselves, but Pathmind's
 * inline editors draw and buffer text directly. Reflective so older versions, which
 * always deliver character input, no-op.
 */
public final class TextInputBridge {
    private static final Method MANAGER_ACCESSOR = resolveManagerAccessor();
    private static final Method START = resolveTextInputMethod("startTextInput");
    private static final Method STOP = resolveTextInputMethod("stopTextInput");

    private TextInputBridge() {
    }

    public static void start(Object owner) {
        invoke(START, owner);
    }

    public static void stop(Object owner) {
        invoke(STOP, owner);
    }

    private static void invoke(Method method, Object owner) {
        Object manager = manager();
        if (manager == null || method == null) {
            return;
        }
        try {
            method.invoke(manager, owner);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Object manager() {
        if (MANAGER_ACCESSOR == null) {
            return null;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return null;
        }
        try {
            return MANAGER_ACCESSOR.invoke(client);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method resolveManagerAccessor() {
        try {
            return Minecraft.class.getMethod("textInputManager");
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method resolveTextInputMethod(String name) {
        try {
            Class<?> managerClass = Class.forName("com.mojang.blaze3d.platform.TextInputManager");
            return managerClass.getMethod(name, Object.class);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
