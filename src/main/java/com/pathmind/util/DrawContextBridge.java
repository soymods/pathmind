package com.pathmind.util;

import net.minecraft.client.gui.DrawContext;

import java.lang.reflect.Method;

/**
 * Compatibility helpers for DrawContext border rendering across 1.21.x.
 */
public final class DrawContextBridge {
    private static final Method DRAW_BORDER = resolveMethod("drawBorder");
    private static final Method DRAW_STROKED_RECTANGLE = resolveMethod("drawStrokedRectangle");

    private DrawContextBridge() {
    }

    public static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        if (context == null || width <= 0 || height <= 0) {
            return;
        }
        if (invokeBorderMethod(context, DRAW_BORDER, x, y, width, height, color)) {
            return;
        }
        if (invokeBorderMethod(context, DRAW_STROKED_RECTANGLE, x, y, width, height, color)) {
            return;
        }

        int right = x + width - 1;
        int bottom = y + height - 1;
        context.drawHorizontalLine(x, right, y, color);
        context.drawHorizontalLine(x, right, bottom, color);
        context.drawVerticalLine(x, y, bottom, color);
        context.drawVerticalLine(right, y, bottom, color);
    }

    private static boolean invokeBorderMethod(DrawContext context, Method method,
                                              int x, int y, int width, int height, int color) {
        if (method == null) {
            return false;
        }
        try {
            method.invoke(context, x, y, width, height, color);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static Method resolveMethod(String name) {
        try {
            Method method = DrawContext.class.getMethod(name, int.class, int.class, int.class, int.class, int.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
