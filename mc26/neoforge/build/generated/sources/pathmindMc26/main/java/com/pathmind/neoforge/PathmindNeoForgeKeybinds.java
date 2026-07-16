package com.pathmind.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import com.pathmind.PathmindCommon;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

final class PathmindNeoForgeKeybinds {
    private static final String LEGACY_GENERAL_CATEGORY = "key.categories.pathmind.general";
    private static final Object GENERAL_CATEGORY = createCategory();

    static final KeyMapping OPEN_VISUAL_EDITOR = createKeyMapping(
        "key.pathmind.open_visual_editor",
        GLFW.GLFW_KEY_RIGHT_ALT
    );

    static final KeyMapping PLAY_GRAPHS = createKeyMapping(
        "key.pathmind.play_graphs",
        GLFW.GLFW_KEY_K
    );

    static final KeyMapping STOP_GRAPHS = createKeyMapping(
        "key.pathmind.stop_graphs",
        GLFW.GLFW_KEY_J
    );

    private PathmindNeoForgeKeybinds() {
    }

    static void registerCategory(RegisterKeyMappingsEvent event) {
        if (event == null || GENERAL_CATEGORY == null) {
            return;
        }
        for (Method method : event.getClass().getMethods()) {
            if (!"registerCategory".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            if (!method.getParameterTypes()[0].isInstance(GENERAL_CATEGORY)) {
                continue;
            }
            try {
                method.invoke(event, GENERAL_CATEGORY);
            } catch (ReflectiveOperationException ignored) {
                // Older NeoForge versions do not need explicit category registration.
            }
            return;
        }
    }

    private static KeyMapping createKeyMapping(String translationKey, int keyCode) {
        if (GENERAL_CATEGORY != null) {
            try {
                Constructor<KeyMapping> constructor = KeyMapping.class.getConstructor(
                    String.class,
                    InputConstants.Type.class,
                    int.class,
                    GENERAL_CATEGORY.getClass()
                );
                return constructor.newInstance(translationKey, InputConstants.Type.KEYSYM, keyCode, GENERAL_CATEGORY);
            } catch (ReflectiveOperationException ignored) {
                // Fall through to the legacy string-category constructor.
            }
        }

        try {
            Constructor<KeyMapping> constructor = KeyMapping.class.getConstructor(
                String.class,
                InputConstants.Type.class,
                int.class,
                String.class
            );
            return constructor.newInstance(translationKey, InputConstants.Type.KEYSYM, keyCode, LEGACY_GENERAL_CATEGORY);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create Pathmind key mapping " + translationKey, e);
        }
    }

    private static Object createCategory() {
        try {
            Class<?> categoryClass = Class.forName("net.minecraft.client.KeyMapping$Category");
            Class<?> identifierClass = findClass("net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation");
            Object identifier = createIdentifier(identifierClass);
            Method registerMethod = categoryClass.getMethod("register", identifierClass);
            return registerMethod.invoke(null, identifier);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static Class<?> findClass(String... names) throws ClassNotFoundException {
        ClassNotFoundException lastFailure = null;
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                lastFailure = e;
            }
        }
        throw lastFailure != null ? lastFailure : new ClassNotFoundException();
    }

    private static Object createIdentifier(Class<?> identifierClass) throws ReflectiveOperationException {
        try {
            Method factory = identifierClass.getMethod("fromNamespaceAndPath", String.class, String.class);
            return factory.invoke(null, PathmindCommon.MOD_ID, "general");
        } catch (NoSuchMethodException ignored) {
            Constructor<?> constructor = identifierClass.getConstructor(String.class, String.class);
            return constructor.newInstance(PathmindCommon.MOD_ID, "general");
        }
    }
}
