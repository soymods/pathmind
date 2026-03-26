package com.pathmind.util;

import net.minecraft.client.gui.DrawContext;

/**
 * Compatibility helpers for DrawContext border rendering across 1.21.x.
 */
public final class DrawContextBridge {
    private static final java.lang.reflect.Method CREATE_NEW_ROOT_LAYER = resolveNoArgMethod("createNewRootLayer");
    private static final Object GUI_OVERLAY_LAYER = resolveGuiOverlayLayer();
    private static final java.lang.reflect.Method FILL_LAYER = resolveFillLayerMethod();

    private DrawContextBridge() {
    }

    public static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        if (context == null || width <= 0 || height <= 0) {
            return;
        }
        drawBorderInLayer(context, x, y, width, height, color);
    }

    public static void drawBorderInLayer(DrawContext context, int x, int y, int width, int height, int color) {
        if (context == null || width <= 0 || height <= 0) {
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

    public static void startNewRootLayer(DrawContext context) {
        if (context == null || CREATE_NEW_ROOT_LAYER == null) {
            return;
        }
        try {
            CREATE_NEW_ROOT_LAYER.invoke(context);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static java.lang.reflect.Method resolveNoArgMethod(String name) {
        try {
            java.lang.reflect.Method method = DrawContext.class.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static java.lang.reflect.Method resolveFillLayerMethod() {
        Class<?> renderLayerClass = tryLoadClass("net.minecraft.client.render.RenderLayer", "net.minecraft.class_1921");
        if (renderLayerClass == null) {
            return null;
        }
        for (java.lang.reflect.Method method : DrawContext.class.getMethods()) {
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
                java.lang.reflect.Method method = renderLayerClass.getMethod(name);
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
