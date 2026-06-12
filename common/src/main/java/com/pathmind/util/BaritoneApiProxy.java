package com.pathmind.util;

import net.minecraft.util.math.BlockPos;

import java.io.File;
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
    private static final String GOAL_XZ_CLASS = "baritone.api.pathing.goals.GoalXZ";
    private static final String GOAL_Y_LEVEL_CLASS = "baritone.api.pathing.goals.GoalYLevel";
    private static final String BLOCK_OPTIONAL_META_CLASS = "baritone.api.utils.BlockOptionalMeta";

    private static final String SETTINGS_FIELD_ALLOW_BREAK = "allowBreak";
    private static final String SETTINGS_FIELD_ALLOW_PLACE = "allowPlace";
    private static final String SETTINGS_FIELD_CHUNK_CACHING = "chunkCaching";
    private static final String SETTINGS_FIELD_PATH_THROUGH_CACHED_ONLY = "pathThroughCachedOnly";
    private static final String SETTINGS_FIELD_EXPLORE_FOR_BLOCKS = "exploreForBlocks";
    private static final String SETTINGS_FIELD_SPLICE_PATH = "splicePath";
    private static final String SETTINGS_FIELD_MAX_PATH_HISTORY_LENGTH = "maxPathHistoryLength";
    private static final String SETTINGS_FIELD_PATH_HISTORY_CUTOFF_AMOUNT = "pathHistoryCutoffAmount";
    private static final String SETTINGS_FIELD_MAX_CACHED_WORLD_SCAN_COUNT = "maxCachedWorldScanCount";
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
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public static Object getSettings() {
        try {
            Class<?> apiClass = Class.forName(BARITONE_API_CLASS);
            return invokeStatic(apiClass, "getSettings");
        } catch (ReflectiveOperationException | LinkageError ignored) {
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

    public static Boolean getAllowPlace(Object settings) {
        Object allowPlace = getFieldValue(settings, SETTINGS_FIELD_ALLOW_PLACE);
        Object value = getFieldValue(allowPlace, SETTINGS_VALUE_FIELD);
        return value instanceof Boolean bool ? bool : null;
    }

    public static void setAllowPlace(Object settings, boolean value) {
        Object allowPlace = getFieldValue(settings, SETTINGS_FIELD_ALLOW_PLACE);
        setFieldValue(allowPlace, SETTINGS_VALUE_FIELD, value);
    }

    public static Boolean getChunkCaching(Object settings) {
        return getBooleanSettingValue(settings, SETTINGS_FIELD_CHUNK_CACHING);
    }

    public static void setChunkCaching(Object settings, boolean value) {
        setBooleanSettingValue(settings, SETTINGS_FIELD_CHUNK_CACHING, value);
    }

    public static Boolean getPathThroughCachedOnly(Object settings) {
        return getBooleanSettingValue(settings, SETTINGS_FIELD_PATH_THROUGH_CACHED_ONLY);
    }

    public static void setPathThroughCachedOnly(Object settings, boolean value) {
        setBooleanSettingValue(settings, SETTINGS_FIELD_PATH_THROUGH_CACHED_ONLY, value);
    }

    public static Boolean getExploreForBlocks(Object settings) {
        return getBooleanSettingValue(settings, SETTINGS_FIELD_EXPLORE_FOR_BLOCKS);
    }

    public static void setExploreForBlocks(Object settings, boolean value) {
        setBooleanSettingValue(settings, SETTINGS_FIELD_EXPLORE_FOR_BLOCKS, value);
    }

    public static Boolean getSplicePath(Object settings) {
        return getBooleanSettingValue(settings, SETTINGS_FIELD_SPLICE_PATH);
    }

    public static void setSplicePath(Object settings, boolean value) {
        setBooleanSettingValue(settings, SETTINGS_FIELD_SPLICE_PATH, value);
    }

    public static Integer getMaxPathHistoryLength(Object settings) {
        return getIntegerSettingValue(settings, SETTINGS_FIELD_MAX_PATH_HISTORY_LENGTH);
    }

    public static void setMaxPathHistoryLength(Object settings, int value) {
        setIntegerSettingValue(settings, SETTINGS_FIELD_MAX_PATH_HISTORY_LENGTH, value);
    }

    public static Integer getPathHistoryCutoffAmount(Object settings) {
        return getIntegerSettingValue(settings, SETTINGS_FIELD_PATH_HISTORY_CUTOFF_AMOUNT);
    }

    public static void setPathHistoryCutoffAmount(Object settings, int value) {
        setIntegerSettingValue(settings, SETTINGS_FIELD_PATH_HISTORY_CUTOFF_AMOUNT, value);
    }

    public static Integer getMaxCachedWorldScanCount(Object settings) {
        return getIntegerSettingValue(settings, SETTINGS_FIELD_MAX_CACHED_WORLD_SCAN_COUNT);
    }

    public static void setMaxCachedWorldScanCount(Object settings, int value) {
        setIntegerSettingValue(settings, SETTINGS_FIELD_MAX_CACHED_WORLD_SCAN_COUNT, value);
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

    public static boolean build(Object builderProcess, String schematicFile, BlockPos origin) {
        Object result = invokeBestMatch(builderProcess, "build", schematicFile, origin);
        return !(result instanceof Boolean bool) || bool;
    }

    public static boolean build(Object builderProcess, String name, File schematic, Object origin) {
        Object result = invokeBestMatch(builderProcess, "build", name, schematic, origin);
        return !(result instanceof Boolean bool) || bool;
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

    public static void forceCancel(Object pathingBehavior) {
        invoke(pathingBehavior, "forceCancel");
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

    public static Object createGoalXZ(int x, int z) {
        return construct(GOAL_XZ_CLASS, new Class<?>[]{int.class, int.class}, x, z);
    }

    public static Object createGoalYLevel(int y) {
        return construct(GOAL_Y_LEVEL_CLASS, new Class<?>[]{int.class}, y);
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
        } catch (ReflectiveOperationException | LinkageError ignored) {
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
        } catch (ReflectiveOperationException | LinkageError ignored) {
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
        } catch (ReflectiveOperationException | LinkageError ignored) {
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
        } catch (ReflectiveOperationException | LinkageError ignored) {
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
        } catch (ReflectiveOperationException | LinkageError ignored) {
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
        } catch (ReflectiveOperationException | LinkageError ignored) {
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
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static Boolean getBooleanSettingValue(Object settings, String fieldName) {
        Object setting = getFieldValue(settings, fieldName);
        Object value = getFieldValue(setting, SETTINGS_VALUE_FIELD);
        return value instanceof Boolean bool ? bool : null;
    }

    private static void setBooleanSettingValue(Object settings, String fieldName, boolean value) {
        Object setting = getFieldValue(settings, fieldName);
        setFieldValue(setting, SETTINGS_VALUE_FIELD, value);
    }

    private static Integer getIntegerSettingValue(Object settings, String fieldName) {
        Object setting = getFieldValue(settings, fieldName);
        Object value = getFieldValue(setting, SETTINGS_VALUE_FIELD);
        return value instanceof Integer integer ? integer : null;
    }

    private static void setIntegerSettingValue(Object settings, String fieldName, int value) {
        Object setting = getFieldValue(settings, fieldName);
        setFieldValue(setting, SETTINGS_VALUE_FIELD, value);
    }
}
