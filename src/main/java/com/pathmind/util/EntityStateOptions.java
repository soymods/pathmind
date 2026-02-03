package com.pathmind.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.DyeColor;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EntityStateOptions {
    private EntityStateOptions() {
    }

    public record StateOption(String value, String displayText) {
    }

    public static List<StateOption> getOptions(EntityType<?> type, World world) {
        if (type == null) {
            return List.of();
        }

        List<StateOption> options = new ArrayList<>();

        Identifier typeId = Registries.ENTITY_TYPE.getId(type);
        if (typeId != null) {
            String path = typeId.getPath();
            if ("axolotl".equals(path)) {
                addVariantOptions(options, "lucy", "wild", "gold", "cyan", "blue");
            } else if ("frog".equals(path)) {
                addVariantOptions(options, "temperate", "warm", "cold");
            } else if ("cat".equals(path)) {
                addVariantOptions(options, "tabby", "tuxedo", "red", "siamese", "british_shorthair",
                    "calico", "persian", "ragdoll", "white", "jellie", "black");
            } else if ("horse".equals(path)) {
                addVariantOptions(options, "white", "creamy", "chestnut", "brown", "black", "gray", "dark_brown");
            } else if ("llama".equals(path)) {
                addVariantOptions(options, "creamy", "white", "brown", "gray");
            } else if ("parrot".equals(path)) {
                addVariantOptions(options, "red_blue", "blue", "green", "yellow_blue", "gray");
            } else if ("rabbit".equals(path)) {
                addVariantOptions(options, "brown", "white", "black", "black_and_white", "gold",
                    "salt", "white_splotched", "evil");
            } else if ("mooshroom".equals(path)) {
                addVariantOptions(options, "red", "brown");
            } else if ("fox".equals(path)) {
                addVariantOptions(options, "red", "snow");
            } else if ("panda".equals(path)) {
                addVariantOptions(options, "normal", "lazy", "worried", "playful", "brown", "weak", "aggressive");
            }
        }

        Class<?> entityClass = resolveEntityClass(type);
        if (entityClass == null && world != null) {
            SpawnReason[] reasons = SpawnReason.values();
            SpawnReason reason = reasons.length > 0 ? reasons[0] : null;
            Entity entity = reason != null ? type.create(world, reason) : null;
            if (entity != null) {
                entityClass = entity.getClass();
            }
        }

        if (entityClass != null && hasMethod(entityClass, "isBaby")) {
            options.add(new StateOption("age=baby", "Age: Baby"));
            options.add(new StateOption("age=adult", "Age: Adult"));
        }
        if (entityClass != null && hasMethod(entityClass, "isTamed")) {
            options.add(new StateOption("tamed=true", "Tamed"));
            options.add(new StateOption("tamed=false", "Untamed"));
            if (hasMethod(entityClass, "isInSittingPose")) {
                options.add(new StateOption("sitting=true", "Sitting"));
                options.add(new StateOption("sitting=false", "Standing"));
            }
        }
        if (entityClass != null && (hasMethod(entityClass, "getAngerTime") || hasMethod(entityClass, "isAngry"))) {
            options.add(new StateOption("angry=true", "Angry"));
            options.add(new StateOption("angry=false", "Calm"));
        }
        if (entityClass != null && hasMethod(entityClass, "isSaddled")) {
            options.add(new StateOption("saddled=true", "Saddled"));
            options.add(new StateOption("saddled=false", "Unsaddled"));
        }
        if (entityClass != null && hasMethod(entityClass, "isSheared")) {
            options.add(new StateOption("sheared=true", "Sheared"));
            options.add(new StateOption("sheared=false", "Not Sheared"));
            if (hasMethod(entityClass, "getColor")) {
                for (DyeColor color : DyeColor.values()) {
                    String name = color.asString();
                    options.add(new StateOption("color=" + name, "Color: " + titleCase(name)));
                }
            }
        }
        if (entityClass != null && hasMethod(entityClass, "isCharged")) {
            options.add(new StateOption("charged=true", "Charged"));
            options.add(new StateOption("charged=false", "Uncharged"));
        }
        Method variantMethod = entityClass != null ? resolveMethod(entityClass, "getVariant") : null;
        if (variantMethod != null) {
            Class<?> returnType = variantMethod.getReturnType();
            if (returnType != null && returnType.isEnum()) {
                Object[] values = returnType.getEnumConstants();
                if (values != null) {
                    for (Object value : values) {
                        String name = value.toString().toLowerCase(Locale.ROOT);
                        options.add(new StateOption("variant=" + name, "Variant: " + titleCase(name)));
                    }
                }
            }
        }
        boolean addProfessions = false;
        if (typeId != null) {
            String path = typeId.getPath();
            if ("villager".equals(path) || "zombie_villager".equals(path)) {
                addProfessions = true;
            }
        }
        if (!addProfessions && entityClass != null && hasMethod(entityClass, "getVillagerData")) {
            addProfessions = true;
        }
        if (addProfessions) {
            for (Identifier id : Registries.VILLAGER_PROFESSION.getIds()) {
                if (id == null) {
                    continue;
                }
                String name = id.getPath();
                options.add(new StateOption("profession=" + name, "Profession: " + titleCase(name)));
            }
        }

        if (options.isEmpty()) {
            return List.of();
        }

        Map<String, StateOption> deduped = new LinkedHashMap<>();
        for (StateOption option : options) {
            deduped.put(option.value(), option);
        }
        List<StateOption> sorted = new ArrayList<>(deduped.values());
        sorted.sort(Comparator.comparing(StateOption::value, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private static void addVariantOptions(List<StateOption> options, String... variants) {
        if (options == null || variants == null) {
            return;
        }
        for (String variant : variants) {
            if (variant == null || variant.isEmpty()) {
                continue;
            }
            options.add(new StateOption("variant=" + variant, "Variant: " + titleCase(variant)));
        }
    }

    public static boolean isStateSupported(EntityType<?> type, World world, String state) {
        if (state == null || state.trim().isEmpty()) {
            return true;
        }
        if (type == null) {
            return false;
        }
        if (world == null) {
            return true;
        }
        String trimmed = state.trim();
        if ("any".equalsIgnoreCase(trimmed) || "any state".equalsIgnoreCase(trimmed)) {
            return true;
        }
        for (StateOption option : getOptions(type, world)) {
            if (option.value().equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesState(Entity entity, String rawState) {
        if (entity == null) {
            return false;
        }
        if (rawState == null || rawState.trim().isEmpty()) {
            return true;
        }
        String state = rawState.trim();
        if ("any".equalsIgnoreCase(state) || "any state".equalsIgnoreCase(state)) {
            return true;
        }
        String[] parts = state.split(",");
        for (String part : parts) {
            String trimmed = part == null ? "" : part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!matchesStatePart(entity, trimmed)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesStatePart(Entity entity, String statePart) {
        String normalized = statePart.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return true;
        }
        String key = normalized;
        String value = "";
        int eq = normalized.indexOf('=');
        if (eq >= 0) {
            key = normalized.substring(0, eq).trim();
            value = normalized.substring(eq + 1).trim();
        }

        switch (key) {
            case "age":
                return matchesAge(entity, value);
            case "baby":
                return matchesAge(entity, "baby");
            case "adult":
                return matchesAge(entity, "adult");
            case "tamed":
                return matchesTamed(entity, value);
            case "sitting":
                return matchesSitting(entity, value);
            case "angry":
                return matchesAngry(entity, value);
            case "saddled":
                return matchesSaddled(entity, value);
            case "sheared":
                return matchesSheared(entity, value);
            case "color":
                return matchesColor(entity, value);
            case "charged":
                return matchesCharged(entity, value);
            case "variant":
                return matchesVariant(entity, value);
            case "profession":
                return matchesProfession(entity, value);
            default:
                return false;
        }
    }

    private static boolean matchesAge(Entity entity, String value) {
        Boolean isBaby = invokeBoolean(entity, "isBaby");
        if (isBaby == null) {
            return false;
        }
        boolean wantBaby = "baby".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
        boolean wantAdult = "adult".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
        if (wantBaby) {
            return isBaby;
        }
        if (wantAdult) {
            return !isBaby;
        }
        return false;
    }

    private static boolean matchesTamed(Entity entity, String value) {
        Boolean tamed = invokeBoolean(entity, "isTamed");
        if (tamed == null) {
            return false;
        }
        boolean desired = parseBoolean(value);
        return tamed == desired;
    }

    private static boolean matchesSitting(Entity entity, String value) {
        Boolean sitting = invokeBoolean(entity, "isInSittingPose");
        if (sitting == null) {
            return false;
        }
        boolean desired = parseBoolean(value);
        return sitting == desired;
    }

    private static boolean matchesAngry(Entity entity, String value) {
        boolean desired = parseBoolean(value);
        Integer angerTime = invokeInt(entity, "getAngerTime");
        if (angerTime != null) {
            return (angerTime > 0) == desired;
        }
        Boolean angry = invokeBoolean(entity, "isAngry");
        if (angry == null) {
            return false;
        }
        return angry == desired;
    }

    private static boolean matchesSaddled(Entity entity, String value) {
        Boolean saddled = invokeBoolean(entity, "isSaddled");
        if (saddled == null) {
            return false;
        }
        boolean desired = parseBoolean(value);
        return saddled == desired;
    }

    private static boolean matchesSheared(Entity entity, String value) {
        Boolean sheared = invokeBoolean(entity, "isSheared");
        if (sheared == null) {
            return false;
        }
        boolean desired = parseBoolean(value);
        return sheared == desired;
    }

    private static boolean matchesColor(Entity entity, String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        Object color = invokeObject(entity, "getColor");
        if (color instanceof DyeColor dyeColor) {
            return dyeColor.asString().equalsIgnoreCase(value.trim());
        }
        return false;
    }

    private static boolean matchesCharged(Entity entity, String value) {
        Boolean charged = invokeBoolean(entity, "isCharged");
        if (charged == null) {
            return false;
        }
        boolean desired = parseBoolean(value);
        return charged == desired;
    }

    private static boolean matchesVariant(Entity entity, String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        Object variant = invokeObject(entity, "getVariant");
        if (variant == null) {
            return false;
        }
        return variant.toString().equalsIgnoreCase(value.trim());
    }

    private static boolean matchesProfession(Entity entity, String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        Object villagerData = invokeObject(entity, "getVillagerData");
        if (villagerData == null) {
            return false;
        }
        Object profession = invokeObject(villagerData, "getProfession");
        if (profession == null) {
            profession = invokeObject(villagerData, "profession");
        }
        if (profession == null) {
            return false;
        }
        String desired = value.trim().toLowerCase(Locale.ROOT);
        if (desired.isEmpty()) {
            return false;
        }
        if (profession instanceof VillagerProfession villagerProfession) {
            Identifier id = Registries.VILLAGER_PROFESSION.getId(villagerProfession);
            if (id != null && id.getPath().equalsIgnoreCase(desired)) {
                return true;
            }
        }
        String raw = profession.toString();
        if (raw != null && raw.toLowerCase(Locale.ROOT).contains(desired)) {
            return true;
        }
        return false;
    }

    private static boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return false;
        }
        return "true".equals(trimmed) || "yes".equals(trimmed) || "1".equals(trimmed) || "on".equals(trimmed);
    }

    private static boolean hasMethod(Class<?> targetClass, String name) {
        return resolveMethod(targetClass, name) != null;
    }

    private static Method resolveMethod(Class<?> targetClass, String name) {
        if (targetClass == null || name == null || name.isEmpty()) {
            return null;
        }
        try {
            return targetClass.getMethod(name);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object invokeObject(Object target, String name) {
        Method method = resolveMethod(target != null ? target.getClass() : null, name);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static Boolean invokeBoolean(Object target, String name) {
        Object value = invokeObject(target, name);
        return value instanceof Boolean bool ? bool : null;
    }

    private static Integer invokeInt(Object target, String name) {
        Object value = invokeObject(target, name);
        return value instanceof Integer integer ? integer : null;
    }

    private static Class<?> resolveEntityClass(EntityType<?> type) {
        if (type == null) {
            return null;
        }
        try {
            Method method = EntityType.class.getMethod("getBaseClass");
            Object value = method.invoke(type);
            if (value instanceof Class<?> cls) {
                return cls;
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }
        try {
            Method method = EntityType.class.getMethod("getEntityClass");
            Object value = method.invoke(type);
            if (value instanceof Class<?> cls) {
                return cls;
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }
        return null;
    }

    private static String titleCase(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String spaced = raw.replace('_', ' ');
        StringBuilder builder = new StringBuilder(spaced.length());
        boolean capitalize = true;
        for (int i = 0; i < spaced.length(); i++) {
            char c = spaced.charAt(i);
            if (Character.isWhitespace(c)) {
                builder.append(c);
                capitalize = true;
                continue;
            }
            if (capitalize) {
                builder.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }
}
