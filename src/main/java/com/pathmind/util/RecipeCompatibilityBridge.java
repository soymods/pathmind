package com.pathmind.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Bridges recipe APIs that differ between 1.21.x patch releases.
 */
public final class RecipeCompatibilityBridge {
    private static final Method INGREDIENT_IS_EMPTY_METHOD = resolveIngredientEmptyMethod();
    private static final Method INGREDIENT_MATCHING_ITEMS_METHOD = resolveMatchingItemsMethod();
    private static final Method INGREDIENT_MATCHING_ITEMS_WITH_LOOKUP_METHOD = resolveMatchingItemsWithLookupMethod();
    private static final List<Method> INGREDIENT_FACTORY_METHODS = resolveIngredientFactoryMethods();
    private static final Method RECIPE_PLACEMENT_METHOD = resolveRecipePlacementMethod();
    private static final Method RECIPE_INGREDIENTS_METHOD = resolveRecipeIngredientsMethod();

    private RecipeCompatibilityBridge() {
    }

    public static Object getIngredientPlacement(CraftingRecipe recipe) {
        if (recipe == null || RECIPE_PLACEMENT_METHOD == null) {
            return null;
        }
        try {
            return RECIPE_PLACEMENT_METHOD.invoke(recipe);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    public static boolean hasNoPlacement(Object placement) {
        if (placement == null) {
            return true;
        }
        try {
            Method method = placement.getClass().getMethod("hasNoPlacement");
            Object result = method.invoke(placement);
            return result instanceof Boolean bool && bool;
        } catch (NoSuchMethodException e) {
            return true;
        } catch (IllegalAccessException | InvocationTargetException e) {
            return true;
        }
    }

    public static List<Ingredient> getPlacementIngredients(Object placement) {
        if (placement == null) {
            return Collections.emptyList();
        }
        try {
            Method method = placement.getClass().getMethod("getIngredients");
            Object result = method.invoke(placement);
            if (result instanceof List<?> list) {
                List<Ingredient> cast = new ArrayList<>(list.size());
                for (Object entry : list) {
                    Ingredient ingredient = extractIngredient(entry);
                    if (ingredient != null) {
                        cast.add(ingredient);
                    }
                }
                return cast;
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }
        return Collections.emptyList();
    }

    public static List<?> getRecipeIngredients(CraftingRecipe recipe) {
        if (recipe == null || RECIPE_INGREDIENTS_METHOD == null) {
            return Collections.emptyList();
        }
        try {
            Object result = RECIPE_INGREDIENTS_METHOD.invoke(recipe);
            if (result instanceof List<?> list) {
                return list;
            }
            if (result instanceof Object[] array) {
                return Arrays.asList(array);
            }
        } catch (IllegalAccessException | InvocationTargetException ignored) {
        }
        return Collections.emptyList();
    }

    public static List<Ingredient> extractDisplayIngredients(List<?> entries, Object registryManager) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<Ingredient> ingredients = new ArrayList<>();
        for (Object entry : entries) {
            List<Ingredient> extracted = extractDisplayIngredients(entry, registryManager);
            if (extracted != null && !extracted.isEmpty()) {
                ingredients.addAll(extracted);
            }
        }
        return ingredients;
    }

    public static boolean isIngredientEmpty(Ingredient ingredient) {
        return isIngredientEmpty(ingredient, null);
    }

    public static boolean isIngredientEmpty(Ingredient ingredient, Object registryManager) {
        if (ingredient == null) {
            return true;
        }
        if (INGREDIENT_IS_EMPTY_METHOD != null) {
            try {
                return (boolean) INGREDIENT_IS_EMPTY_METHOD.invoke(ingredient);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        if (INGREDIENT_MATCHING_ITEMS_METHOD != null) {
            try {
                Object result = invokeMatchingItems(ingredient, registryManager);
                Boolean empty = isMatchingItemsEmpty(result);
                if (empty != null) {
                    return empty;
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        try {
            Object emptyIngredient = Ingredient.class.getField("EMPTY").get(null);
            if (emptyIngredient == ingredient) {
                return true;
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }
        return false;
    }

    public static IntList toPlacementSlots(Object placement) {
        if (placement == null) {
            return IntLists.EMPTY_LIST;
        }

        Object rawSlots = invokePlacementMethod(placement, "getPlacementSlots");
        if (rawSlots instanceof IntList intList) {
            return intList;
        }

        if (rawSlots instanceof List<?> list) {
            IntArrayList converted = new IntArrayList(list.size());
            for (Object entry : list) {
                if (!(entry instanceof Optional<?> optional) || optional.isEmpty()) {
                    continue;
                }
                Object slotObj = optional.get();
                Integer slotIndex = extractPlacementSlotIndex(slotObj);
                if (slotIndex != null) {
                    converted.add(slotIndex);
                }
            }
            return converted;
        }

        return IntLists.EMPTY_LIST;
    }

    private static Method resolveIngredientEmptyMethod() {
        try {
            return Ingredient.class.getMethod("isEmpty");
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method resolveMatchingItemsMethod() {
        try {
            Method method = Ingredient.class.getMethod("getMatchingItems");
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method resolveMatchingItemsWithLookupMethod() {
        for (Method method : Ingredient.class.getMethods()) {
            if (!"getMatchingItems".equals(method.getName())) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                continue;
            }
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    private static Method resolveRecipePlacementMethod() {
        try {
            Method method = CraftingRecipe.class.getMethod("getIngredientPlacement");
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method resolveRecipeIngredientsMethod() {
        try {
            Method method = CraftingRecipe.class.getMethod("getIngredients");
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Integer extractPlacementSlotIndex(Object slotObj) {
        if (slotObj == null) {
            return null;
        }
        try {
            for (Method method : slotObj.getClass().getMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == int.class) {
                    method.setAccessible(true);
                    return (Integer) method.invoke(slotObj);
                }
            }
            return null;
        } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    private static Object invokePlacementMethod(Object placement, String methodName) {
        try {
            Method method = placement.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(placement);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    private static Boolean isMatchingItemsEmpty(Object result) {
        if (result == null) {
            return true;
        }
        if (result instanceof java.util.stream.Stream<?> stream) {
            try (java.util.stream.Stream<?> closable = stream) {
                return closable.findAny().isEmpty();
            }
        }
        if (result instanceof java.util.Collection<?> collection) {
            return collection.isEmpty();
        }
        if (result instanceof Iterable<?> iterable) {
            return !iterable.iterator().hasNext();
        }
        if (result instanceof Object[] array) {
            return array.length == 0;
        }
        return null;
    }

    public static Ingredient tryCreateIngredientFromEntry(Object entry) {
        if (entry == null || INGREDIENT_FACTORY_METHODS.isEmpty()) {
            return null;
        }
        List<Object> candidates = gatherEntryCandidates(entry);
        try {
            for (Method method : INGREDIENT_FACTORY_METHODS) {
                Class<?> paramType = method.getParameterTypes()[0];
                for (Object candidate : candidates) {
                    Object arg = coerceFactoryArg(paramType, candidate);
                    if (arg == null) {
                        continue;
                    }
                    try {
                        Object result = method.invoke(null, arg);
                        if (result instanceof Ingredient ingredient && !isIngredientEmpty(ingredient)) {
                            return ingredient;
                        }
                    } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
                        // Try next candidate.
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static List<Object> gatherEntryCandidates(Object entry) {
        List<Object> candidates = new ArrayList<>();
        addCandidate(candidates, entry);

        Object entries = resolveEntryList(entry);
        addCandidate(candidates, entries);

        if (entries instanceof Iterable<?> iterable) {
            addCandidate(candidates, java.util.stream.StreamSupport.stream(iterable.spliterator(), false));
        }

        addCandidatesFromMethods(entry, candidates, entry.getClass().getMethods());
        addCandidatesFromMethods(entry, candidates, entry.getClass().getDeclaredMethods());
        addCandidatesFromFields(entry, candidates, entry.getClass().getDeclaredFields());

        return candidates;
    }

    private static void addCandidatesFromMethods(Object entry, List<Object> candidates, Method[] methods) {
        if (methods == null) {
            return;
        }
        int logged = 0;
        for (Method method : methods) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType == void.class || returnType.isPrimitive()) {
                continue;
            }
            String name = method.getName();
            if ("getClass".equals(name) || "hashCode".equals(name) || "toString".equals(name)) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object value = method.invoke(entry);
                addCandidate(candidates, value);
                logged++;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Skip failing accessors.
            }
            if (logged >= 12) {
                return;
            }
        }
    }

    private static void addCandidatesFromFields(Object entry, List<Object> candidates, java.lang.reflect.Field[] fields) {
        if (fields == null) {
            return;
        }
        int logged = 0;
        for (java.lang.reflect.Field field : fields) {
            if (field.getType().isPrimitive()) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(entry);
                addCandidate(candidates, value);
                logged++;
            } catch (IllegalAccessException | RuntimeException ignored) {
                // Skip failing accessors.
            }
            if (logged >= 8) {
                return;
            }
        }
    }

    private static void addCandidate(List<Object> candidates, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Optional<?> optional) {
            Object unwrapped = optional.orElse(null);
            if (unwrapped != null) {
                candidates.add(unwrapped);
            }
            return;
        }
        candidates.add(value);
        if (value instanceof Iterable<?> iterable) {
            candidates.add(iterable);
        }
        if (value.getClass().isArray() && java.lang.reflect.Array.getLength(value) > 0) {
            Object first = java.lang.reflect.Array.get(value, 0);
            if (first != null) {
                candidates.add(first);
            }
        }
    }

    private static Object resolveEntryList(Object entry) {
        if (entry == null) {
            return null;
        }
        List<String> preferred = List.of("comp_3271", "entries", "getEntries");
        for (String name : preferred) {
            try {
                Method method = entry.getClass().getMethod(name);
                if (method.getParameterCount() != 0) {
                    continue;
                }
                method.setAccessible(true);
                Object result = method.invoke(entry);
                if (result instanceof Iterable<?> iterable) {
                    return iterable;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next candidate.
            }
        }
        for (Method method : entry.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (!Iterable.class.isAssignableFrom(returnType)) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object result = method.invoke(entry);
                if (result instanceof Iterable<?> iterable) {
                    return iterable;
                }
            } catch (ReflectiveOperationException ignored) {
                // Keep scanning.
            }
        }
        return null;
    }

    private static List<Method> resolveIngredientFactoryMethods() {
        List<Method> methods = new ArrayList<>();
        for (Method method : Ingredient.class.getMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!Ingredient.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                continue;
            }
            method.setAccessible(true);
            methods.add(method);
        }
        return methods;
    }

    private static Object coerceFactoryArg(Class<?> paramType, Object candidate) {
        if (candidate == null) {
            return null;
        }
        if (paramType.isInstance(candidate)) {
            return candidate;
        }
        if (paramType.isArray()) {
            Object array = createArrayArg(paramType.getComponentType(), candidate);
            if (array != null) {
                return array;
            }
        }
        if ((Iterable.class.isAssignableFrom(paramType) || java.util.Collection.class.isAssignableFrom(paramType))
            && candidate instanceof Iterable<?> iterable) {
            return iterable;
        }
        if (java.util.stream.Stream.class.isAssignableFrom(paramType) && candidate instanceof Iterable<?> iterable) {
            return java.util.stream.StreamSupport.stream(iterable.spliterator(), false);
        }
        return null;
    }

    private static Object createArrayArg(Class<?> componentType, Object candidate) {
        if (componentType.isInstance(candidate)) {
            Object array = java.lang.reflect.Array.newInstance(componentType, 1);
            java.lang.reflect.Array.set(array, 0, candidate);
            return array;
        }
        if (candidate instanceof Iterable<?> iterable) {
            java.util.ArrayList<Object> list = new java.util.ArrayList<>();
            for (Object value : iterable) {
                if (value == null || !componentType.isInstance(value)) {
                    return null;
                }
                list.add(value);
            }
            Object array = java.lang.reflect.Array.newInstance(componentType, list.size());
            for (int i = 0; i < list.size(); i++) {
                java.lang.reflect.Array.set(array, i, list.get(i));
            }
            return array;
        }
        return null;
    }

    private static Ingredient extractIngredient(Object entry) {
        if (entry instanceof Ingredient ingredientValue) {
            return ingredientValue;
        }
        if (entry instanceof RegistryEntry<?> registryEntry) {
            Object value = registryEntry.value();
            if (value instanceof Ingredient registryIngredient) {
                return registryIngredient;
            }
        }
        Ingredient candidate = tryCreateIngredientFromEntry(entry);
        if (candidate != null) {
            return candidate;
        }
        if (entry instanceof Optional<?> optional) {
            Object value = optional.orElse(null);
            if (value instanceof Ingredient optionalIngredient) {
                return optionalIngredient;
            }
        }
        if (entry != null) {
            Ingredient resolved = resolveIngredientFromEntry(entry, "ingredient");
            if (resolved != null) {
                return resolved;
            }
            resolved = resolveIngredientFromEntry(entry, "value");
            if (resolved != null) {
                return resolved;
            }
            resolved = resolveIngredientFromEntry(entry, "getIngredient");
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private static Ingredient resolveIngredientFromEntry(Object entry, String methodName) {
        try {
            Method method = entry.getClass().getMethod(methodName);
            if (Ingredient.class.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                Object value = method.invoke(entry);
                return value instanceof Ingredient ingredient ? ingredient : null;
            }
        } catch (NoSuchMethodException ignored) {
            // Try declared methods/fields next.
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
        try {
            Method method = entry.getClass().getDeclaredMethod(methodName);
            if (!Ingredient.class.isAssignableFrom(method.getReturnType())) {
                return null;
            }
            method.setAccessible(true);
            Object value = method.invoke(entry);
            return value instanceof Ingredient ingredient ? ingredient : null;
        } catch (NoSuchMethodException ignored) {
            // Try fields next.
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
        try {
            java.lang.reflect.Field field = entry.getClass().getDeclaredField(methodName);
            if (!Ingredient.class.isAssignableFrom(field.getType())) {
                return null;
            }
            field.setAccessible(true);
            Object value = field.get(entry);
            return value instanceof Ingredient ingredient ? ingredient : null;
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            return null;
        }
    }

    public static List<ItemStack> getIngredientStacks(Ingredient ingredient, Object registryManager) {
        List<ItemStack> stacks = new ArrayList<>();
        if (ingredient == null) {
            return stacks;
        }
        if (INGREDIENT_MATCHING_ITEMS_METHOD == null && INGREDIENT_MATCHING_ITEMS_WITH_LOOKUP_METHOD == null) {
            return stacks;
        }
        try {
            Object result = invokeMatchingItems(ingredient, registryManager);
            if (result instanceof java.util.stream.Stream<?> stream) {
                stream.forEach(entry -> collectItemStack(entry, stacks));
            }
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            // Return whatever we collected so far.
        }
        return stacks;
    }

    private static Object invokeMatchingItems(Ingredient ingredient, Object registryManager)
            throws IllegalAccessException, InvocationTargetException {
        if (ingredient == null) {
            return null;
        }
        if (INGREDIENT_MATCHING_ITEMS_WITH_LOOKUP_METHOD != null && registryManager != null) {
            Class<?> paramType = INGREDIENT_MATCHING_ITEMS_WITH_LOOKUP_METHOD.getParameterTypes()[0];
            Object arg = null;
            if (paramType.isInstance(registryManager)) {
                arg = registryManager;
            } else if (RegistryWrapper.WrapperLookup.class.isAssignableFrom(paramType)) {
                arg = resolveWrapperLookup(registryManager);
            }
            if (arg != null) {
                return INGREDIENT_MATCHING_ITEMS_WITH_LOOKUP_METHOD.invoke(ingredient, arg);
            }
        }
        if (INGREDIENT_MATCHING_ITEMS_METHOD != null) {
            return INGREDIENT_MATCHING_ITEMS_METHOD.invoke(ingredient);
        }
        return null;
    }

    private static RegistryWrapper.WrapperLookup resolveWrapperLookup(Object registryManager) {
        if (registryManager == null) {
            return null;
        }
        if (registryManager instanceof RegistryWrapper.WrapperLookup wrapper) {
            return wrapper;
        }
        for (String methodName : new String[]{"getWrapperLookup", "getRegistryLookup", "getLookup"}) {
            try {
                Method method = registryManager.getClass().getMethod(methodName);
                method.setAccessible(true);
                Object result = method.invoke(registryManager);
                if (result instanceof RegistryWrapper.WrapperLookup wrapper) {
                    return wrapper;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next candidate.
            }
        }
        for (Method method : registryManager.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            if (!RegistryWrapper.WrapperLookup.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object result = method.invoke(registryManager);
                if (result instanceof RegistryWrapper.WrapperLookup wrapper) {
                    return wrapper;
                }
            } catch (ReflectiveOperationException ignored) {
                // Keep scanning.
            }
        }
        return null;
    }

    private static void collectItemStack(Object entry, List<ItemStack> stacks) {
        if (entry instanceof RegistryEntry<?> registryEntry) {
            Object value = registryEntry.value();
            if (value instanceof Item item) {
                stacks.add(new ItemStack(item));
            }
        }
    }

    private static List<Ingredient> extractDisplayIngredients(Object entry, Object registryManager) {
        if (entry == null) {
            return Collections.emptyList();
        }
        if (isShapelessDisplay(entry)) {
            List<?> slots = extractDisplayEntries(entry, "comp_3271", "ingredients");
            if (slots == null || slots.isEmpty()) {
                return Collections.emptyList();
            }
            List<Ingredient> ingredients = new ArrayList<>();
            for (Object slot : slots) {
                Ingredient ingredient = ingredientFromSlotDisplay(slot, registryManager);
                if (ingredient != null && !isIngredientEmpty(ingredient, registryManager)) {
                    ingredients.add(ingredient);
                }
            }
            return ingredients;
        }

        Ingredient slotIngredient = ingredientFromSlotDisplay(entry, registryManager);
        if (slotIngredient != null) {
            return List.of(slotIngredient);
        }
        return Collections.emptyList();
    }

    private static boolean isShapelessDisplay(Object entry) {
        String name = entry.getClass().getName();
        if (name.contains("ShapelessCraftingRecipeDisplay") || name.endsWith("class_10301")) {
            return true;
        }
        return hasAccessor(entry.getClass(), "comp_3271") || hasAccessor(entry.getClass(), "ingredients");
    }

    private static Ingredient ingredientFromSlotDisplay(Object slotDisplay, Object registryManager) {
        if (slotDisplay == null) {
            return null;
        }

        Ingredient direct = tryCreateIngredientFromEntry(slotDisplay);
        if (direct != null && !isIngredientEmpty(direct, registryManager)) {
            return direct;
        }

        Object itemEntry = accessValue(slotDisplay, "comp_3273", "item");
        if (itemEntry instanceof RegistryEntry<?> registryEntry && registryEntry.value() instanceof Item item) {
            return Ingredient.ofItems(item);
        }

        Ingredient tagIngredient = ingredientFromTag(accessValue(slotDisplay, "comp_3275", "tag"), registryManager);
        if (tagIngredient != null) {
            return tagIngredient;
        }

        return null;
    }

    private static Ingredient ingredientFromTag(Object tagCandidate, Object registryManager) {
        if (!(tagCandidate instanceof TagKey<?> tagKey)) {
            return null;
        }
        if (!tagKey.isOf(RegistryKeys.ITEM)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        TagKey<Item> itemTag = (TagKey<Item>) tagKey;

        RegistryWrapper.WrapperLookup lookup = resolveWrapperLookup(registryManager);
        if (lookup != null) {
            try {
                RegistryWrapper.Impl<Item> itemWrapper = lookup.getOrThrow(RegistryKeys.ITEM);
                Optional<? extends RegistryEntryList<Item>> optional = itemWrapper.getOptional(itemTag);
                if (optional.isPresent()) {
                    Ingredient ingredient = Ingredient.ofTag(optional.get());
                    if (!isIngredientEmpty(ingredient, registryManager)) {
                        return ingredient;
                    }
                }
            } catch (RuntimeException ignored) {
                // Give up if the wrapper cannot resolve the tag.
            }
        }

        return null;
    }

    private static List<?> extractDisplayEntries(Object display, String... names) {
        for (String name : names) {
            try {
                Method method = display.getClass().getMethod(name);
                method.setAccessible(true);
                Object result = method.invoke(display);
                if (result instanceof List<?> list) {
                    return list;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try next accessor.
            }
            try {
                java.lang.reflect.Field field = display.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(display);
                if (value instanceof List<?> list) {
                    return list;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next candidate name.
            }
        }
        return null;
    }

    private static boolean hasAccessor(Class<?> type, String name) {
        try {
            type.getMethod(name);
            return true;
        } catch (NoSuchMethodException ignored) {
        }
        try {
            type.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException ignored) {
            return false;
        }
    }

    private static Object accessValue(Object target, String... names) {
        for (String name : names) {
            try {
                java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(target);
                if (value != null) {
                    return value;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try methods below.
            }
            try {
                Method method = target.getClass().getMethod(name);
                method.setAccessible(true);
                Object value = method.invoke(target);
                if (value != null) {
                    return value;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try next name.
            }
        }
        return null;
    }
}
