package com.pathmind.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Bridges Entity world/position accessors across 1.21.x.
 */
public final class EntityCompatibilityBridge {
    private static final Method GET_POS = resolveMethod("getPos");
    private static final Method GET_ENTITY_POS = resolveMethod("getEntityPos");
    private static final Method GET_SYNCED_POS = resolveMethod("getSyncedPos");
    private static final Method GET_LERPED_POS = resolveMethod("getLerpedPos", float.class);
    private static final Method GET_WORLD = resolveMethod("getWorld");
    private static final Method GET_ENTITY_WORLD = resolveMethod("getEntityWorld");
    private static final Field WORLD_FIELD = resolveWorldField();

    private EntityCompatibilityBridge() {
    }

    public static Vec3d getPos(Entity entity) {
        if (entity == null) {
            return null;
        }
        Vec3d result = invokeVec(entity, GET_POS);
        if (result != null) {
            return result;
        }
        result = invokeVec(entity, GET_ENTITY_POS);
        if (result != null) {
            return result;
        }
        result = invokeVec(entity, GET_SYNCED_POS);
        if (result != null) {
            return result;
        }
        if (GET_LERPED_POS != null) {
            try {
                Object value = GET_LERPED_POS.invoke(entity, 1.0f);
                if (value instanceof Vec3d vec) {
                    return vec;
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                return null;
            }
        }
        return null;
    }

    public static World getWorld(Entity entity) {
        if (entity == null) {
            return null;
        }
        World world = invokeWorld(entity, GET_WORLD);
        if (world != null) {
            return world;
        }
        world = invokeWorld(entity, GET_ENTITY_WORLD);
        if (world != null) {
            return world;
        }
        if (WORLD_FIELD != null) {
            try {
                Object value = WORLD_FIELD.get(entity);
                return value instanceof World worldValue ? worldValue : null;
            } catch (IllegalAccessException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Vec3d invokeVec(Entity entity, Method method) {
        if (method == null) {
            return null;
        }
        try {
            Object value = method.invoke(entity);
            return value instanceof Vec3d vec ? vec : null;
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static World invokeWorld(Entity entity, Method method) {
        if (method == null) {
            return null;
        }
        try {
            Object value = method.invoke(entity);
            return value instanceof World world ? world : null;
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static Method resolveMethod(String name, Class<?>... params) {
        try {
            Method method = Entity.class.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Field resolveWorldField() {
        try {
            Field field = Entity.class.getDeclaredField("world");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }
}
