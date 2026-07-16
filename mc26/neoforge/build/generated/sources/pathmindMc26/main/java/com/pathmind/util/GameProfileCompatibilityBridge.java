package com.pathmind.util;

import com.mojang.authlib.GameProfile;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Bridges GameProfile name accessors across authlib versions.
 */
public final class GameProfileCompatibilityBridge {
    private static final Method GET_NAME = resolveMethod("getName");
    private static final Method NAME = resolveMethod("name");

    private GameProfileCompatibilityBridge() {
    }

    public static String getName(GameProfile profile) {
        if (profile == null) {
            return null;
        }
        String name = invokeString(profile, GET_NAME);
        if (name != null) {
            return name;
        }
        return invokeString(profile, NAME);
    }

    private static String invokeString(GameProfile profile, Method method) {
        if (method == null) {
            return null;
        }
        try {
            Object value = method.invoke(profile);
            return value instanceof String str ? str : null;
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static Method resolveMethod(String name) {
        try {
            Method method = GameProfile.class.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
