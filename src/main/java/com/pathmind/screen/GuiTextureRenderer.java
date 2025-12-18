package com.pathmind.screen;

import com.pathmind.PathmindMod;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Draws un-atlased GUI textures across the wildly different 1.21.x renderer APIs.
 */
final class GuiTextureRenderer {
    private static final RendererBackend BACKEND = detectBackend();
    private static final AtomicBoolean FAILED_ONCE = new AtomicBoolean(false);

    private GuiTextureRenderer() {
    }

    static void drawIcon(DrawContext context, Identifier texture, int x, int y, int size, int color) {
        try {
            BACKEND.draw(context, texture, x, y, size, color);
        } catch (RuntimeException exception) {
            if (FAILED_ONCE.compareAndSet(false, true)) {
                PathmindMod.LOGGER.error("Failed to render Pathmind icon. Rendering will be skipped.", exception);
            }
        }
    }

    private static RendererBackend detectBackend() {
        RendererBackend backend = LegacyBackend.tryCreate();
        if (backend != null) {
            PathmindMod.LOGGER.debug("Using legacy RenderLayer GUI renderer.");
            return backend;
        }

        backend = PipelineBackend.tryCreate();
        if (backend != null) {
            PathmindMod.LOGGER.debug("Using RenderPipeline GUI renderer.");
            return backend;
        }

        PathmindMod.LOGGER.warn("Could not initialize a GUI texture renderer. The Pathmind menu icon will not render.");
        return RendererBackend.NO_OP;
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

    private interface RendererBackend {
        void draw(DrawContext context, Identifier texture, int x, int y, int size, int color);

        RendererBackend NO_OP = (context, texture, x, y, size, color) -> {
        };
    }

    private static final class PipelineBackend implements RendererBackend {
        private final Object pipelineInstance;
        private final Method drawTextureMethod;
        private final boolean supportsTint;

        private PipelineBackend(Object pipelineInstance, Method drawTextureMethod) {
            this.pipelineInstance = pipelineInstance;
            this.drawTextureMethod = drawTextureMethod;
            this.supportsTint = drawTextureMethod.getParameterCount() == 11;
        }

        static RendererBackend tryCreate() {
            Class<?> pipelineClass = tryLoadClass("com.mojang.blaze3d.pipeline.RenderPipeline");
            if (pipelineClass == null) {
                return null;
            }
            Class<?> pipelinesClass = tryLoadClass(
                "net.minecraft.client.gl.RenderPipelines",
                "net.minecraft.class_10799"
            );
            if (pipelinesClass == null) {
                return null;
            }
            try {
                Object pipeline = locateGuiPipeline(pipelinesClass, pipelineClass);
                if (pipeline == null) {
                    return null;
                }
                Method method = findDrawTextureMethod(pipelineClass);
                if (method == null) {
                    return null;
                }
                return new PipelineBackend(pipeline, method);
            } catch (IllegalAccessException exception) {
                return null;
            }
        }

        private static Object locateGuiPipeline(Class<?> pipelinesClass, Class<?> pipelineClass) throws IllegalAccessException {
            try {
                Method getLocationMethod = pipelineClass.getMethod("getLocation");
                Object fallback = null;
                for (Field field : pipelinesClass.getFields()) {
                    if (!pipelineClass.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    Object candidate = field.get(null);
                    if (candidate == null) {
                        continue;
                    }
                    Object location = getLocationMethod.invoke(candidate);
                    if (location instanceof Identifier identifier) {
                        String path = identifier.getPath();
                        if (path.endsWith("gui_textured")) {
                            return candidate;
                        }
                        if (fallback == null && path.contains("gui")) {
                            fallback = candidate;
                        }
                    }
                }
                return fallback;
            } catch (InvocationTargetException | NoSuchMethodException exception) {
                PathmindMod.LOGGER.debug("Failed to scan RenderPipelines fields", exception);
                return null;
            }
        }

        private static Method findDrawTextureMethod(Class<?> pipelineClass) {
            for (Method method : DrawContext.class.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!matchesCommonParameters(parameters, pipelineClass)) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
            return null;
        }

        @Override
        public void draw(DrawContext context, Identifier texture, int x, int y, int size, int color) {
            try {
                Object[] parameters = createCommonParameters(pipelineInstance, texture, x, y, size, supportsTint, color);
                drawTextureMethod.invoke(context, parameters);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new RuntimeException("RenderPipeline backend failed", exception);
            }
        }
    }

    private static final class LegacyBackend implements RendererBackend {
        private final Method drawTextureMethod;
        private final Function<Identifier, Object> renderLayerFactory;
        private final boolean supportsTint;

        private LegacyBackend(Method drawTextureMethod, Function<Identifier, Object> renderLayerFactory) {
            this.drawTextureMethod = drawTextureMethod;
            this.renderLayerFactory = renderLayerFactory;
            this.supportsTint = drawTextureMethod.getParameterCount() == 11;
        }

        static RendererBackend tryCreate() {
            try {
                Method method = findDrawTextureMethod();
                if (method == null) {
                    return null;
                }
                Function<Identifier, Object> factory = locateRenderLayerFactory();
                if (factory == null) {
                    return null;
                }
                return new LegacyBackend(method, factory);
            } catch (Exception exception) {
                return null;
            }
        }

        private static Method findDrawTextureMethod() {
            for (Method method : DrawContext.class.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!matchesCommonParameters(parameters, Function.class)) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
            return null;
        }

        private static Function<Identifier, Object> locateRenderLayerFactory() {
            Class<?> renderLayerClass = tryLoadClass(
                "net.minecraft.client.render.RenderLayer",
                "net.minecraft.class_1921"
            );
            if (renderLayerClass == null) {
                return null;
            }
            Method method = findGuiTexturedMethod(renderLayerClass);
            if (method == null) {
                return null;
            }
            return identifier -> {
                try {
                    return method.invoke(null, identifier);
                } catch (IllegalAccessException | InvocationTargetException exception) {
                    throw new RuntimeException(exception);
                }
            };
        }

        private static Method findGuiTexturedMethod(Class<?> renderLayerClass) {
            String[] candidateNames = {
                "getGuiTextured",
                "method_62277"
            };
            for (String name : candidateNames) {
                try {
                    Method method = renderLayerClass.getMethod(name, Identifier.class);
                    if (!renderLayerClass.isAssignableFrom(method.getReturnType())) {
                        continue;
                    }
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
            return null;
        }

        @Override
        public void draw(DrawContext context, Identifier texture, int x, int y, int size, int color) {
            try {
                Object[] parameters = createCommonParameters(renderLayerFactory, texture, x, y, size, supportsTint, color);
                drawTextureMethod.invoke(context, parameters);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new RuntimeException("Legacy RenderLayer backend failed", exception);
            }
        }
    }

    private static boolean matchesCommonParameters(Class<?>[] parameters, Class<?> firstParameterType) {
        if (parameters.length != 10 && parameters.length != 11) {
            return false;
        }
        if (!firstParameterType.isAssignableFrom(parameters[0])) {
            return false;
        }
        if (!Identifier.class.isAssignableFrom(parameters[1])) {
            return false;
        }
        if (parameters[2] != int.class || parameters[3] != int.class) {
            return false;
        }
        if (parameters[4] != float.class || parameters[5] != float.class) {
            return false;
        }
        if (parameters[6] != int.class || parameters[7] != int.class || parameters[8] != int.class || parameters[9] != int.class) {
            return false;
        }
        if (parameters.length == 11 && parameters[10] != int.class) {
            return false;
        }
        return true;
    }

    private static Object[] createCommonParameters(
        Object firstParameter,
        Identifier texture,
        int x,
        int y,
        int size,
        boolean includeTint,
        int color
    ) {
        Object[] parameters = new Object[includeTint ? 11 : 10];
        parameters[0] = firstParameter;
        parameters[1] = texture;
        parameters[2] = x;
        parameters[3] = y;
        parameters[4] = 0.0F;
        parameters[5] = 0.0F;
        parameters[6] = size;
        parameters[7] = size;
        parameters[8] = size;
        parameters[9] = size;
        if (includeTint) {
            parameters[10] = color;
        }
        return parameters;
    }
}
