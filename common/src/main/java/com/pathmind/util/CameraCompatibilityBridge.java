package com.pathmind.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

/**
 * Bridges camera position accessors across 1.21.x.
 */
public final class CameraCompatibilityBridge {
    private static final Method GET_POS = resolveMethod("getPos");
    private static final Method GET_CAMERA_POS = resolveMethod("getCameraPos");

    private CameraCompatibilityBridge() {
    }

    public static Vec3 getPos(Camera camera) {
        if (camera == null) {
            return null;
        }
        Vec3 value = invokeVec(camera, GET_POS);
        if (value != null) {
            return value;
        }
        return invokeVec(camera, GET_CAMERA_POS);
    }

    private static Vec3 invokeVec(Camera camera, Method method) {
        if (method == null) {
            return null;
        }
        try {
            Object value = method.invoke(camera);
            return value instanceof Vec3 vec ? vec : null;
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
