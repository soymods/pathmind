package com.pathmind.util;

import net.minecraft.client.gui.widget.ButtonWidget;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Bridges ButtonWidget press helpers across 1.21.x.
 */
public final class ButtonWidgetCompatibilityBridge {
    private static final Class<?> ABSTRACT_INPUT_CLASS = resolveClass("net.minecraft.client.input.AbstractInput");
    private static final Method ON_PRESS_WITH_INPUT = resolveMethod(ABSTRACT_INPUT_CLASS);
    private static final Method ON_PRESS_NO_ARGS = resolveMethod();
    private static final Method LEGACY_PRESS_METHOD = resolveLegacyPressMethod();
    private static final Object DUMMY_INPUT = createDummyInput();

    private ButtonWidgetCompatibilityBridge() {
    }

    public static void press(ButtonWidget button) {
        if (button == null) {
            return;
        }
        if (ON_PRESS_WITH_INPUT != null && DUMMY_INPUT != null) {
            try {
                ON_PRESS_WITH_INPUT.invoke(button, DUMMY_INPUT);
                return;
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // Fall back to no-arg method.
            }
        }
        if (ON_PRESS_NO_ARGS != null) {
            try {
                ON_PRESS_NO_ARGS.invoke(button);
                return;
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // Fall through to legacy method.
            }
        }
        if (LEGACY_PRESS_METHOD != null) {
            try {
                LEGACY_PRESS_METHOD.invoke(button);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // No-op.
            }
        }
    }

    private static Method resolveMethod() {
        try {
            Method method = ButtonWidget.class.getMethod("onPress");
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method resolveMethod(Class<?> inputClass) {
        if (inputClass == null) {
            return null;
        }
        try {
            Method method = ButtonWidget.class.getMethod("onPress", inputClass);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method resolveLegacyPressMethod() {
        try {
            Method method = ButtonWidget.class.getMethod("method_25306");
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object createDummyInput() {
        if (ABSTRACT_INPUT_CLASS == null) {
            return null;
        }
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            Class<?> returnType = method.getReturnType();
            if ("getKeycode".equals(name)) {
                return -1;
            }
            if ("modifiers".equals(name)) {
                return 0;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            return null;
        };
        return Proxy.newProxyInstance(
            ABSTRACT_INPUT_CLASS.getClassLoader(),
            new Class<?>[] { ABSTRACT_INPUT_CLASS },
            handler
        );
    }

    private static Class<?> resolveClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
