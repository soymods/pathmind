package com.pathmind.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.item.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Bridges recipe APIs that differ between 1.21.x patch releases.
 */
public final class RecipeCompatibilityBridge {
    private static final Method INGREDIENT_IS_EMPTY_METHOD = resolveIngredientEmptyMethod();
    private static final Method INGREDIENT_MATCHING_STACKS_METHOD = resolveMatchingStacksMethod();
    private static final Method RECIPE_PLACEMENT_METHOD = resolveRecipePlacementMethod();

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
                    if (entry instanceof Ingredient ingredient) {
                        cast.add(ingredient);
                    }
                }
                return cast;
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }
        return Collections.emptyList();
    }

    public static boolean isIngredientEmpty(Ingredient ingredient) {
        if (ingredient == null) {
            return true;
        }
        if (INGREDIENT_IS_EMPTY_METHOD != null) {
            try {
                return (boolean) INGREDIENT_IS_EMPTY_METHOD.invoke(ingredient);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }

        ItemStack[] stacks = getMatchingStacks(ingredient);
        if (stacks == null || stacks.length == 0) {
            return true;
        }
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                return false;
            }
        }
        return true;
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

    private static Method resolveMatchingStacksMethod() {
        for (String name : new String[]{"getMatchingStacks", "getMatchingStacksClient"}) {
            try {
                Method method = Ingredient.class.getMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
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

    private static ItemStack[] getMatchingStacks(Ingredient ingredient) {
        if (INGREDIENT_MATCHING_STACKS_METHOD == null) {
            return new ItemStack[0];
        }
        try {
            return (ItemStack[]) INGREDIENT_MATCHING_STACKS_METHOD.invoke(ingredient);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return new ItemStack[0];
        }
    }
}
