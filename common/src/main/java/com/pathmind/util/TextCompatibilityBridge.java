package com.pathmind.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Bridges Text factory methods across 1.21.x.
 */
public final class TextCompatibilityBridge {
    private static final Method TRANSLATABLE = resolveStatic("translatable", String.class);
    private static final Method TRANSLATABLE_WITH_ARGS = resolveStatic("translatable", String.class, Object[].class);
    private static final Method TRANSLATABLE_WITH_FALLBACK =
        resolveStatic("translatableWithFallback", String.class, String.class);
    private static final Method TRANSLATABLE_WITH_FALLBACK_ARGS =
        resolveStatic("translatableWithFallback", String.class, String.class, Object[].class);
    private static final Method EMPTY = resolveStatic("empty");
    private static final Method COPY = resolveVirtual("copy");
    private static final Method COPY_CONTENT_ONLY = resolveVirtual("copyContentOnly");

    private TextCompatibilityBridge() {
    }

    public static MutableText translatable(String key) {
        return translatable(key, new Object[0]);
    }

    public static MutableText translatable(String key, Object... args) {
        Object[] safeArgs = args == null ? new Object[0] : args;
        MutableText value = invokeStatic(TRANSLATABLE_WITH_ARGS, key, safeArgs);
        if (value != null) {
            return value;
        }
        if (safeArgs.length == 0) {
            value = invokeStatic(TRANSLATABLE, key);
            if (value != null) {
                return value;
            }
        }
        return literal(key == null ? "" : key);
    }

    public static MutableText translatableWithFallback(String key, String fallback) {
        return translatableWithFallback(key, fallback, new Object[0]);
    }

    public static MutableText translatableWithFallback(String key, String fallback, Object... args) {
        Object[] safeArgs = args == null ? new Object[0] : args;
        MutableText value = invokeStatic(TRANSLATABLE_WITH_FALLBACK_ARGS, key, fallback, safeArgs);
        if (value != null) {
            return value;
        }
        value = invokeStatic(TRANSLATABLE_WITH_FALLBACK, key, fallback);
        if (value != null) {
            return value;
        }
        value = translatable(key, safeArgs);
        if (value != null) {
            if (fallback != null && !fallback.isEmpty() && key != null && key.equals(value.getString())) {
                return literal(fallback);
            }
            return value;
        }
        return literal(fallback == null ? "" : fallback);
    }

    public static MutableText empty() {
        MutableText value = invokeStatic(EMPTY);
        if (value != null) {
            return value;
        }
        return literal("");
    }

    public static MutableText copy(Text text) {
        if (text == null) {
            return empty();
        }
        MutableText value = invokeVirtual(text, COPY);
        if (value != null) {
            return value;
        }
        value = invokeVirtual(text, COPY_CONTENT_ONLY);
        if (value != null) {
            return value;
        }
        return literal(text.getString());
    }

    public static MutableText literal(String text) {
        try {
            return Text.literal(text == null ? "" : text);
        } catch (RuntimeException ignored) {
            return Text.of(text == null ? "" : text).copy();
        }
    }

    private static MutableText invokeStatic(Method method, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            Object result = method.invoke(null, args);
            if (result instanceof MutableText mutable) {
                return mutable;
            }
            if (result instanceof Text text) {
                return copy(text);
            }
            return null;
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static MutableText invokeVirtual(Text text, Method method) {
        if (method == null) {
            return null;
        }
        try {
            Object result = method.invoke(text);
            return result instanceof MutableText mutable ? mutable : null;
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static Method resolveStatic(String name, Class<?>... params) {
        try {
            Method method = Text.class.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method resolveVirtual(String name) {
        try {
            Method method = Text.class.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
