package com.pathmind.util;

import net.minecraft.client.gui.DrawContext;

import java.lang.reflect.Method;

/**
 * Compatibility helpers for DrawContext border rendering across 1.21.x.
 */
public final class DrawContextBridge {
    private static final Method DRAW_BORDER = resolveMethod("drawBorder");
    private static final Method DRAW_STROKED_RECTANGLE = resolveMethod("drawStrokedRectangle");
    private static final Object GUI_OVERLAY_LAYER = resolveGuiOverlayLayer();
    private static final Method FILL_LAYER = resolveFillLayerMethod();

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

    public static void fillOverlay(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        if (context == null) {
            return;
        }
        if (FILL_LAYER != null && GUI_OVERLAY_LAYER != null) {
            try {
                FILL_LAYER.invoke(context, GUI_OVERLAY_LAYER, x1, y1, x2, y2, color);
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Fall back to the default fill below.
            }
        }
        context.fill(x1, y1, x2, y2, color);
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

    private static Method resolveFillLayerMethod() {
        Class<?> renderLayerClass = tryLoadClass("net.minecraft.client.render.RenderLayer", "net.minecraft.class_1921");
        if (renderLayerClass == null) {
            return null;
        }
        for (Method method : DrawContext.class.getMethods()) {
            if (!"fill".equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 6 && params[0].isAssignableFrom(renderLayerClass)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Object resolveGuiOverlayLayer() {
        Class<?> renderLayerClass = tryLoadClass("net.minecraft.client.render.RenderLayer", "net.minecraft.class_1921");
        if (renderLayerClass == null) {
            return null;
        }
        String[] candidateNames = {"getGuiOverlay", "getGui"};
        for (String name : candidateNames) {
            try {
                Method method = renderLayerClass.getMethod(name);
                if (!renderLayerClass.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                method.setAccessible(true);
                return method.invoke(null);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Class<?> tryLoadClass(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }
}
