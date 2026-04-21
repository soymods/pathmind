package com.pathmind.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Constructor;
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
    private static final Method MOUSE_CURSOR_POS_CALLBACK = resolveMouseCursorPosCallback();
    private static final Method MOUSE_BUTTON_CALLBACK = resolveMouseButtonCallback();
    private static final Constructor<?> MOUSE_INPUT_CONSTRUCTOR = resolveMouseInputConstructor();

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
        return GLFW.glfwGetKey(window.getHandle(), keyCode) == GLFW.GLFW_PRESS;
    }

    public static boolean isMouseButtonPressed(MinecraftClient client, int buttonCode) {
        if (client == null) {
            return false;
        }
        Window window = client.getWindow();
        if (window == null) {
            return false;
        }
        return GLFW.glfwGetMouseButton(window.getHandle(), buttonCode) == GLFW.GLFW_PRESS;
    }

    public static boolean dispatchMouseButton(MinecraftClient client, int buttonCode, int action, int mods) {
        if (client == null) {
            return false;
        }
        Window window = client.getWindow();
        if (window == null) {
            return false;
        }
        Mouse mouse = client.mouse;
        if (mouse == null || MOUSE_BUTTON_CALLBACK == null) {
            return false;
        }
        try {
            Class<?>[] parameterTypes = MOUSE_BUTTON_CALLBACK.getParameterTypes();
            if (parameterTypes.length == 4 && parameterTypes[1] == int.class) {
                MOUSE_BUTTON_CALLBACK.invoke(mouse, window.getHandle(), buttonCode, action, mods);
                return true;
            }
            if (parameterTypes.length == 3
                && parameterTypes[0] == long.class
                && parameterTypes[2] == int.class
                && MOUSE_INPUT_CONSTRUCTOR != null) {
                Object mouseInput = MOUSE_INPUT_CONSTRUCTOR.newInstance(buttonCode, action);
                MOUSE_BUTTON_CALLBACK.invoke(mouse, window.getHandle(), mouseInput, mods);
                return true;
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ignored) {
            return false;
        }
        return false;
    }

    public static boolean dispatchCursorPos(MinecraftClient client, double x, double y) {
        if (client == null) {
            return false;
        }
        Window window = client.getWindow();
        if (window == null) {
            return false;
        }
        Mouse mouse = client.mouse;
        if (mouse == null || MOUSE_CURSOR_POS_CALLBACK == null) {
            return false;
        }
        GLFW.glfwSetCursorPos(window.getHandle(), x, y);
        try {
            MOUSE_CURSOR_POS_CALLBACK.invoke(mouse, window.getHandle(), x, y);
            return true;
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return false;
        }
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

    private static Method resolveMouseButtonCallback() {
        for (Method method : Mouse.class.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (method.getReturnType() != void.class) {
                continue;
            }
            if (parameterTypes.length == 4
                && parameterTypes[0] == long.class
                && parameterTypes[1] == int.class
                && parameterTypes[2] == int.class
                && parameterTypes[3] == int.class) {
                method.setAccessible(true);
                return method;
            }
            if (parameterTypes.length == 3
                && parameterTypes[0] == long.class
                && parameterTypes[2] == int.class) {
                Constructor<?> constructor = resolveTwoIntConstructor(parameterTypes[1]);
                if (constructor != null) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static Method resolveMouseCursorPosCallback() {
        for (Method method : Mouse.class.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (method.getReturnType() != void.class) {
                continue;
            }
            if (parameterTypes.length == 3
                && parameterTypes[0] == long.class
                && parameterTypes[1] == double.class
                && parameterTypes[2] == double.class) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Constructor<?> resolveMouseInputConstructor() {
        if (MOUSE_BUTTON_CALLBACK == null) {
            return null;
        }
        Class<?>[] parameterTypes = MOUSE_BUTTON_CALLBACK.getParameterTypes();
        if (parameterTypes.length != 3) {
            return null;
        }
        return resolveTwoIntConstructor(parameterTypes[1]);
    }

    private static Constructor<?> resolveTwoIntConstructor(Class<?> type) {
        if (type == null || type.isPrimitive()) {
            return null;
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(int.class, int.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
