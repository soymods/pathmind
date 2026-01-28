package com.pathmind.util;

import java.lang.reflect.Method;

/**
 * Compatibility helpers for render state across 1.21.x.
 */
public final class RenderStateBridge {
    private static final Method SET_SHADER_COLOR = resolveSetShaderColor();

    private RenderStateBridge() {
    }

    public static void setShaderColor(float red, float green, float blue, float alpha) {
        if (SET_SHADER_COLOR == null) {
            return;
        }
        try {
            SET_SHADER_COLOR.invoke(null, red, green, blue, alpha);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Method resolveSetShaderColor() {
        try {
            Class<?> renderSystem = Class.forName("com.mojang.blaze3d.systems.RenderSystem");
            Method method = renderSystem.getMethod("setShaderColor", float.class, float.class, float.class, float.class);
            method.setAccessible(true);
            return method;
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        }
    }
}
