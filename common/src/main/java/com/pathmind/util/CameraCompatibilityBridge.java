package com.pathmind.util;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Bridges camera position accessors across 1.21.x.
 */
public final class CameraCompatibilityBridge {
    private static final Method GET_POS = resolveMethod("getPos");
    private static final Method GET_CAMERA_POS = resolveMethod("getCameraPos");

    private CameraCompatibilityBridge() {
    }

    public static Vec3d getPos(Camera camera) {
        if (camera == null) {
            return null;
        }
        Vec3d value = invokeVec(camera, GET_POS);
        if (value != null) {
            return value;
        }
        return invokeVec(camera, GET_CAMERA_POS);
    }

    private static Vec3d invokeVec(Camera camera, Method method) {
        if (method == null) {
            return null;
        }
        try {
            Object value = method.invoke(camera);
            return value instanceof Vec3d vec ? vec : null;
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static Method resolveMethod(String name) {
        try {
            Method method = Camera.class.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
