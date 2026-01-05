package com.pathmind.util;

import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection-based wrapper for Baritone API usage when the dependency is optional.
 */
public final class BaritoneApiProxy {
    private static final String BARITONE_API_CLASS = "baritone.api.BaritoneAPI";
    private static final String GOAL_BLOCK_CLASS = "baritone.api.pathing.goals.GoalBlock";
    private static final String GOAL_NEAR_CLASS = "baritone.api.pathing.goals.GoalNear";
    private static final String BLOCK_OPTIONAL_META_CLASS = "baritone.api.utils.BlockOptionalMeta";

    private static final String SETTINGS_FIELD_ALLOW_BREAK = "allowBreak";
    private static final String SETTINGS_VALUE_FIELD = "value";

    private BaritoneApiProxy() {
    }

    public static Object getPrimaryBaritone() {
        if (!BaritoneDependencyChecker.isBaritoneApiPresent()) {
            return null;
        }
        try {
            Class<?> apiClass = Class.forName(BARITONE_API_CLASS);
            Object provider = invokeStatic(apiClass, "getProvider");
            return invoke(provider, "getPrimaryBaritone");
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object getSettings() {
        try {
            Class<?> apiClass = Class.forName(BARITONE_API_CLASS);
            return invokeStatic(apiClass, "getSettings");
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Boolean getAllowBreak(Object settings) {
        Object allowBreak = getFieldValue(settings, SETTINGS_FIELD_ALLOW_BREAK);
        Object value = getFieldValue(allowBreak, SETTINGS_VALUE_FIELD);
        return value instanceof Boolean bool ? bool : null;
    }

    public static void setAllowBreak(Object settings, boolean value) {
        Object allowBreak = getFieldValue(settings, SETTINGS_FIELD_ALLOW_BREAK);
        setFieldValue(allowBreak, SETTINGS_VALUE_FIELD, value);
    }

    public static Object getCustomGoalProcess(Object baritone) {
        return invoke(baritone, "getCustomGoalProcess");
    }

    public static Object getGetToBlockProcess(Object baritone) {
        return invoke(baritone, "getGetToBlockProcess");
    }

    public static Object getMineProcess(Object baritone) {
        return invoke(baritone, "getMineProcess");
    }

    public static Object getExploreProcess(Object baritone) {
        return invoke(baritone, "getExploreProcess");
    }

    public static Object getFarmProcess(Object baritone) {
        return invoke(baritone, "getFarmProcess");
    }

    public static Object getBuilderProcess(Object baritone) {
        return invoke(baritone, "getBuilderProcess");
    }

    public static Object getPathingBehavior(Object baritone) {
        return invoke(baritone, "getPathingBehavior");
    }

    public static boolean isProcessActive(Object process) {
        Object result = invoke(process, "isActive");
        return result instanceof Boolean bool && bool;
    }

    public static void onLostControl(Object process) {
        invoke(process, "onLostControl");
    }

    public static boolean isPathing(Object pathingBehavior) {
        Object result = invoke(pathingBehavior, "isPathing");
        return result instanceof Boolean bool && bool;
    }

    public static boolean hasPath(Object pathingBehavior) {
        Object result = invoke(pathingBehavior, "hasPath");
        return result instanceof Boolean bool && bool;
    }

    public static void cancelEverything(Object pathingBehavior) {
        invoke(pathingBehavior, "cancelEverything");
    }

    public static void cancelMine(Object mineProcess) {
        invoke(mineProcess, "cancel");
    }

    public static void mineByName(Object mineProcess, int amount, String[] targets) {
        invoke(mineProcess, "mineByName", new Class<?>[]{int.class, String[].class}, amount, targets);
    }

    public static void mineByName(Object mineProcess, String[] targets) {
        invoke(mineProcess, "mineByName", new Class<?>[]{String[].class}, (Object) targets);
    }

    public static void explore(Object exploreProcess, int x, int z) {
        invoke(exploreProcess, "explore", new Class<?>[]{int.class, int.class}, x, z);
    }

    public static void farm(Object farmProcess, int range) {
        invoke(farmProcess, "farm", new Class<?>[]{int.class}, range);
    }

    public static void setGoalAndPath(Object customGoalProcess, Object goal) {
        invokeBestMatch(customGoalProcess, "setGoalAndPath", goal);
    }

    public static void setGoal(Object customGoalProcess, Object goal) {
        invokeBestMatch(customGoalProcess, "setGoal", goal);
    }

    public static void path(Object customGoalProcess) {
        invoke(customGoalProcess, "path");
    }

    public static Object createGoalBlock(int x, int y, int z) {
        return construct(GOAL_BLOCK_CLASS, new Class<?>[]{int.class, int.class, int.class}, x, y, z);
    }

    public static Object createGoalNear(BlockPos pos, int range) {
        return construct(GOAL_NEAR_CLASS, new Class<?>[]{BlockPos.class, int.class}, pos, range);
    }

    public static Object createBlockOptionalMeta(String blockId) {
        return construct(BLOCK_OPTIONAL_META_CLASS, new Class<?>[]{String.class}, blockId);
    }

    public static void getToBlock(Object getToBlockProcess, Object blockOptionalMeta) {
        invokeBestMatch(getToBlockProcess, "getToBlock", blockOptionalMeta);
    }

    private static Object invokeStatic(Class<?> clazz, String methodName) {
        if (clazz == null) {
            return null;
        }
        try {
            Method method = clazz.getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeBestMatch(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }
        Method best = null;
        Method[] methods = target.getClass().getMethods();
        for (Method method : methods) {
            if (!methodName.equals(method.getName())) {
                continue;
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != args.length) {
                continue;
            }
            boolean compatible = true;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!isCompatible(paramTypes[i], args[i])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                best = method;
                break;
            }
        }
        if (best == null) {
            return null;
        }
        try {
            best.setAccessible(true);
            return best.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isCompatible(Class<?> paramType, Object arg) {
        if (arg == null) {
            return !paramType.isPrimitive();
        }
        if (paramType.isPrimitive()) {
            if (paramType == boolean.class) {
                return arg instanceof Boolean;
            }
            if (paramType == int.class) {
                return arg instanceof Integer;
            }
            if (paramType == long.class) {
                return arg instanceof Long;
            }
            if (paramType == float.class) {
                return arg instanceof Float;
            }
            if (paramType == double.class) {
                return arg instanceof Double;
            }
            if (paramType == short.class) {
                return arg instanceof Short;
            }
            if (paramType == byte.class) {
                return arg instanceof Byte;
            }
            if (paramType == char.class) {
                return arg instanceof Character;
            }
            return false;
        }
        return paramType.isInstance(arg);
    }

    private static Object construct(String className, Class<?>[] paramTypes, Object... args) {
        try {
            Class<?> clazz = Class.forName(className);
            Constructor<?> ctor = clazz.getConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object getFieldValue(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        try {
            Field field = target.getClass().getField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setFieldValue(Object target, String fieldName, Object value) {
        if (target == null) {
            return;
        }
        try {
            Field field = target.getClass().getField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Throwable ignored) {
        }
    }
}
