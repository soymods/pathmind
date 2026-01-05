package com.pathmind.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Bridges input helpers that shifted across 1.21.x.
 */
public final class InputCompatibilityBridge {
    private static final Method SCREEN_HAS_CONTROL_DOWN = resolveScreenMethod("hasControlDown");
    private static final Method SCREEN_HAS_SHIFT_DOWN = resolveScreenMethod("hasShiftDown");
    private static final Method IS_KEY_PRESSED_WINDOW = resolveIsKeyPressed(Window.class);
    private static final Method IS_KEY_PRESSED_HANDLE = resolveIsKeyPressed(long.class);

    private InputCompatibilityBridge() {
    }

    public static boolean hasControlDown() {
        Boolean screenValue = invokeScreenBoolean(SCREEN_HAS_CONTROL_DOWN);
        if (screenValue != null) {
            return screenValue;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return isKeyPressed(client, InputUtil.GLFW_KEY_LEFT_CONTROL)
            || isKeyPressed(client, InputUtil.GLFW_KEY_RIGHT_CONTROL);
    }

    public static boolean hasShiftDown() {
        Boolean screenValue = invokeScreenBoolean(SCREEN_HAS_SHIFT_DOWN);
        if (screenValue != null) {
            return screenValue;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return isKeyPressed(client, InputUtil.GLFW_KEY_LEFT_SHIFT)
            || isKeyPressed(client, InputUtil.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean isKeyPressed(MinecraftClient client, int keyCode) {
        if (client == null) {
            return false;
        }
        Window window = client.getWindow();
        if (window == null) {
            return false;
        }
        if (IS_KEY_PRESSED_WINDOW != null) {
            try {
                Object result = IS_KEY_PRESSED_WINDOW.invoke(null, window, keyCode);
                return result instanceof Boolean value && value;
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                return false;
            }
        }
        if (IS_KEY_PRESSED_HANDLE != null) {
            try {
                Object result = IS_KEY_PRESSED_HANDLE.invoke(null, window.getHandle(), keyCode);
                return result instanceof Boolean value && value;
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                return false;
            }
        }
        return false;
    }

    private static Method resolveScreenMethod(String name) {
        try {
            Method method = Screen.class.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Boolean invokeScreenBoolean(Method method) {
        if (method == null) {
            return null;
        }
        try {
            Object result = method.invoke(null);
            return result instanceof Boolean value ? value : null;
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static Method resolveIsKeyPressed(Class<?> firstParam) {
        try {
            Method method = InputUtil.class.getMethod("isKeyPressed", firstParam, int.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
