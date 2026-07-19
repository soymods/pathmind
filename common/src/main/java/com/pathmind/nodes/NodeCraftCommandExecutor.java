package com.pathmind.nodes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pathmind.util.EntityCompatibilityBridge;
import com.pathmind.util.LoaderMetadata;
import com.pathmind.util.RecipeCompatibilityBridge;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import it.unimi.dsi.fastutil.ints.IntList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class NodeCraftCommandExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeCraftCommandExecutor.class);
    private static final long CRAFTING_ACTION_DELAY_MS = 75L;
    private static final int CRAFTING_OUTPUT_POLL_LIMIT = 20;
    private static final String RECIPE_CACHE_FILE_NAME = "recipe_cache.json";
    private static final int RECIPE_CACHE_VERSION = 2;
    private static final int RECIPE_WARMUP_RECIPE_BATCH_SIZE = 8;
    private static final int RECIPE_WARMUP_DISPLAY_BATCH_SIZE = 4;
    private static final int RECIPE_WARMUP_SAVE_INTERVAL = 64;
    private static final Gson RECIPE_CACHE_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object RECIPE_CACHE_LOCK = new Object();
    private static volatile CachedRecipeBook cachedRecipeBook;
    private static volatile RecipeCacheWarmupState recipeCacheWarmupState;
    private static volatile boolean recipeCacheWarmupRequested;

    private final Node owner;

    NodeCraftCommandExecutor(Node owner) {
        this.owner = owner;
    }

    void executeCraftCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        String itemId = "stick";
        int quantity = getRequestedCraftQuantity();

        NodeParameter itemParam = owner.getParameter("Item");

        if (itemParam != null) {
            itemId = itemParam.getStringValue();
        }
        String requestedItemLabel = itemId;

        if (itemId != null && !itemId.isEmpty()) {
            String sanitized = owner.sanitizeResourceId(itemId);
            if (sanitized != null && !sanitized.isEmpty()) {
                String normalized = owner.normalizeResourceId(sanitized, "minecraft");
                if (normalized != null && !normalized.isEmpty()) {
                    itemId = normalized;
                    if (!normalized.equals(requestedItemLabel)) {
                        owner.setParameterValueAndPropagate("Item", normalized);
                    }
                }
            }
        }

        NodeMode craftMode = owner.getMode() != null ? owner.getMode() : NodeMode.CRAFT_PLAYER_GUI;

        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();

        Identifier identifier = Identifier.tryParse(itemId);
        if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
            String errorLabel = (requestedItemLabel != null && !requestedItemLabel.isEmpty()) ? requestedItemLabel : itemId;
            NodeExecutionCompletion.fail(owner, client, future,
                "Cannot craft \"" + errorLabel + "\": unknown item identifier.");
            return;
        }

        Item targetItem = BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
        if (client == null || client.player == null || client.level == null) {
            NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("Minecraft client not available"));
            return;
        }

        if (!isCraftingScreenAvailable(client, craftMode)) {
            String unavailableMessage = craftMode == NodeMode.CRAFT_CRAFTING_TABLE
                    ? "Cannot craft: open a crafting table GUI before running this node."
                    : "Cannot craft: open your inventory or a crafting table GUI before running this node.";
            NodeExecutionCompletion.fail(owner, client, future, unavailableMessage);
            return;
        }

        String itemDisplayName = new ItemStack(targetItem).getHoverName().getString();

        AbstractContainerMenu handler = client.player.containerMenu;
        if (!isCompatibleCraftingHandler(handler, craftMode)) {
            NodeExecutionCompletion.fail(owner, client, future,
                "Cannot craft " + itemDisplayName + ": the crafting screen closed.");
            return;
        }

        final NodeMode effectiveCraftMode;
        if (craftMode == NodeMode.CRAFT_PLAYER_GUI && handler instanceof CraftingMenu) {
            effectiveCraftMode = NodeMode.CRAFT_CRAFTING_TABLE;
        } else {
            effectiveCraftMode = craftMode;
        }

        Object serverRegistryManager = client.getSingleplayerServer() != null
            ? client.getSingleplayerServer().registryAccess()
            : null;
        net.minecraft.world.level.Level clientWorld;
        try {
            clientWorld = owner.supplyFromClient(client, () -> {
                net.minecraft.world.level.Level world = EntityCompatibilityBridge.getWorld(client.player);
                if (world == null) {
                    world = client.level;
                }
                return world;
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            NodeExecutionCompletion.complete(future);
            return;
        }
        Object clientRegistryManager = clientWorld != null ? clientWorld.registryAccess() : null;

        java.util.concurrent.atomic.AtomicBoolean requiresCraftingTable = new java.util.concurrent.atomic.AtomicBoolean(false);
        RecipeHolder<CraftingRecipe> recipeEntry;
        Object displayEntry = null;
        try {
            recipeEntry = owner.supplyFromClient(client, () -> findCraftingRecipe(client, targetItem, effectiveCraftMode, requiresCraftingTable));
            if (recipeEntry == null) {
                displayEntry = owner.supplyFromClient(client, () -> findCraftingDisplayEntry(client, targetItem, effectiveCraftMode, requiresCraftingTable, clientWorld));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            NodeExecutionCompletion.complete(future);
            return;
        }
        CachedRecipe cachedRecipe = findCachedRecipe(client, targetItem, effectiveCraftMode);
        if (recipeEntry == null && displayEntry == null && cachedRecipe == null) {
            String message;
            if (effectiveCraftMode == NodeMode.CRAFT_PLAYER_GUI && requiresCraftingTable.get()) {
                message = "Cannot craft " + itemDisplayName + ": recipe requires a crafting table.";
            } else {
                message = "Cannot craft " + itemDisplayName + ": no matching recipe found. "
                    + "To cache recipes for multiplayer, open a singleplayer world and choose "
                    + "Settings > Cache recipes.";
            }
            NodeExecutionCompletion.fail(owner, client, future, message);
            return;
        }

        ItemStack outputTemplate;
        if (cachedRecipe != null && recipeEntry == null && displayEntry == null) {
            int outputCount = Math.max(1, cachedRecipe.outputCount);
            outputTemplate = new ItemStack(targetItem, outputCount);
        } else if (recipeEntry != null) {
            outputTemplate = getRecipeOutput(recipeEntry.value(), serverRegistryManager);
            if (outputTemplate.isEmpty() && clientRegistryManager != serverRegistryManager) {
                outputTemplate = getRecipeOutput(recipeEntry.value(), clientRegistryManager);
            }
        } else {
            outputTemplate = getDisplayOutput(RecipeCompatibilityBridge.getDisplayFromEntry(displayEntry), clientWorld);
        }
        if ((outputTemplate == null || outputTemplate.isEmpty()) && cachedRecipe != null) {
            int outputCount = Math.max(1, cachedRecipe.outputCount);
            outputTemplate = new ItemStack(targetItem, outputCount);
        }
        if (outputTemplate == null || outputTemplate.isEmpty()) {
            NodeExecutionCompletion.fail(owner, client, future,
                "Cannot craft " + itemDisplayName + ": the recipe produced no output.");
            return;
        }

        int desiredCount = Math.max(1, quantity);
        int perCraftOutput = Math.max(1, outputTemplate.getCount());
        int craftsRequested = Math.max(1, (int) Math.ceil(desiredCount / (double) perCraftOutput));

        Object ingredientRegistryManager = clientWorld;
        List<GridIngredient> gridIngredients;
        if (cachedRecipe != null && recipeEntry == null && displayEntry == null) {
            gridIngredients = buildGridIngredientsFromCache(cachedRecipe);
        } else if (recipeEntry != null) {
            gridIngredients = resolveGridIngredients(recipeEntry.value(), effectiveCraftMode, ingredientRegistryManager);
        } else {
            gridIngredients = resolveDisplayGridIngredients(RecipeCompatibilityBridge.getDisplayFromEntry(displayEntry), effectiveCraftMode, ingredientRegistryManager);
        }
        if ((gridIngredients == null || gridIngredients.isEmpty()) && cachedRecipe != null) {
            gridIngredients = buildGridIngredientsFromCache(cachedRecipe);
        }
        if (gridIngredients.isEmpty()) {
            NodeExecutionCompletion.fail(owner, client, future,
                "Cannot craft " + itemDisplayName + ": the recipe has no ingredients.");
            return;
        }

        if (recipeEntry != null && client.getSingleplayerServer() != null) {
            cacheCraftingRecipe(client, targetItem, recipeEntry.value(), outputTemplate.getCount(), clientWorld);
        }

        int[] craftingGridSlots = getCraftingGridSlots(effectiveCraftMode);
        List<GridIngredient> finalGridIngredients = gridIngredients;

        CompletableFuture
            .supplyAsync(() -> {
                try {
                    return craftRecipeUsingScreen(client, effectiveCraftMode, recipeEntry, targetItem, craftsRequested, desiredCount, itemDisplayName, finalGridIngredients, craftingGridSlots, ingredientRegistryManager);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new java.util.concurrent.CompletionException(e);
                }
            })
            .whenComplete((summary, throwable) -> {
                if (throwable != null) {
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    if (!(cause instanceof InterruptedException)) {
                        NodeExecutionCompletion.fail(owner, client, future,
                            "Cannot craft " + itemDisplayName + ": " + cause.getMessage());
                        return;
                    }
                    NodeExecutionCompletion.complete(future);
                    return;
                }

                if (summary.failureMessage != null) {
                    owner.sendNodeErrorMessage(client, summary.failureMessage);
                }

                NodeExecutionCompletion.complete(future);
            });
    }
    boolean isCraftingScreenAvailable(net.minecraft.client.Minecraft client, NodeMode craftMode) {
        if (client == null) {
            return false;
        }

        if (craftMode == NodeMode.CRAFT_CRAFTING_TABLE) {
            return client.screen instanceof CraftingScreen;
        }

        if (craftMode == NodeMode.CRAFT_PLAYER_GUI) {
            return client.screen instanceof InventoryScreen || client.screen instanceof CraftingScreen;
        }

        return false;
    }

    int getRequestedCraftQuantity() {
        return Math.max(1, Node.parseNodeInt(owner, "Amount", 1));
    }

    boolean isCompatibleCraftingHandler(AbstractContainerMenu handler, NodeMode craftMode) {
        if (handler == null) {
            return false;
        }

        if (craftMode == NodeMode.CRAFT_CRAFTING_TABLE) {
            return handler instanceof CraftingMenu;
        }

        if (craftMode == NodeMode.CRAFT_PLAYER_GUI) {
            return handler instanceof InventoryMenu || handler instanceof CraftingMenu;
        }

        return false;
    }

    RecipeHolder<CraftingRecipe> findCraftingRecipe(net.minecraft.client.Minecraft client,
                                                           Item targetItem,
                                                           NodeMode craftMode,
                                                           java.util.concurrent.atomic.AtomicBoolean requiresCraftingTable) {
        List<Object> managers = getRecipeManagers(client);
        int totalEntries = 0;
        int craftingEntries = 0;
        int emptyOutputs = 0;
        int matchingOutputs = 0;
        List<String> sampleOutputs = new ArrayList<>();
        boolean debugLogged = false;
        Object serverRegistryManager = client.getSingleplayerServer() != null ? client.getSingleplayerServer().registryAccess() : null;
        net.minecraft.world.level.Level clientWorld = EntityCompatibilityBridge.getWorld(client.player);
        Object clientRegistryManager = clientWorld != null ? clientWorld.registryAccess() : null;
        Object ingredientRegistryManager = clientWorld != null ? clientWorld : clientRegistryManager;
        AbstractContainerMenu handler = client.player != null ? client.player.containerMenu : null;
        List<String> managerTypes = new ArrayList<>();
        RecipeHolder<CraftingRecipe> fallbackMatch = null;
        for (Object manager : managers) {
            if (manager == null) {
                continue;
            }
            managerTypes.add(manager.getClass().getName());
            for (RecipeHolder<?> entry : getCraftingRecipeEntries(manager)) {
                totalEntries++;
                if (!(entry.value() instanceof CraftingRecipe craftingRecipe)) {
                    continue;
                }
                craftingEntries++;

                ItemStack result = getRecipeOutput(craftingRecipe, serverRegistryManager);
                if (result.isEmpty() && clientRegistryManager != serverRegistryManager) {
                    result = getRecipeOutput(craftingRecipe, clientRegistryManager);
                }
                if (result.isEmpty()) {
                    emptyOutputs++;
                    if (!debugLogged) {
                        logRecipeOutputDebug(craftingRecipe, serverRegistryManager, clientRegistryManager);
                        debugLogged = true;
                    }
                } else if (sampleOutputs.size() < 5) {
                    Identifier itemId = BuiltInRegistries.ITEM.getKey(result.getItem());
                    if (itemId != null) {
                        sampleOutputs.add(itemId.toString());
                    }
                }
                if (!result.is(targetItem)) {
                    continue;
                }
                matchingOutputs++;

                if (craftMode == NodeMode.CRAFT_PLAYER_GUI && !recipeFitsPlayerGrid(craftingRecipe)) {
                    if (requiresCraftingTable != null) {
                        requiresCraftingTable.set(true);
                    }
                    continue;
                }

                @SuppressWarnings("unchecked")
                RecipeHolder<CraftingRecipe> castEntry = (RecipeHolder<CraftingRecipe>) entry;
                if (fallbackMatch == null) {
                    fallbackMatch = castEntry;
                }

                List<GridIngredient> gridIngredients = resolveGridIngredients(craftingRecipe, craftMode, ingredientRegistryManager);
                if (canSatisfyGridIngredients(handler, gridIngredients, ingredientRegistryManager)) {
                    return castEntry;
                }
            }
        }

        RecipeHolder<CraftingRecipe> recipeBookMatch = findCraftingRecipeInRecipeBookCollections(
            client,
            targetItem,
            craftMode,
            requiresCraftingTable,
            handler,
            ingredientRegistryManager,
            serverRegistryManager,
            clientRegistryManager
        );
        if (recipeBookMatch != null) {
            return recipeBookMatch;
        }

        logCraftDebug(targetItem, craftMode, managers.size(), totalEntries, craftingEntries, emptyOutputs, matchingOutputs, sampleOutputs, managerTypes);
        return fallbackMatch;
    }

    private RecipeHolder<CraftingRecipe> findCraftingRecipeInRecipeBookCollections(net.minecraft.client.Minecraft client,
                                                                                   Item targetItem,
                                                                                   NodeMode craftMode,
                                                                                   java.util.concurrent.atomic.AtomicBoolean requiresCraftingTable,
                                                                                   AbstractContainerMenu handler,
                                                                                   Object ingredientRegistryManager,
                                                                                   Object serverRegistryManager,
                                                                                   Object clientRegistryManager) {
        if (client == null || client.player == null) {
            return null;
        }
        if (!(client.player.getRecipeBook() instanceof ClientRecipeBook clientRecipeBook)) {
            return null;
        }
        List<RecipeCollection> collections = clientRecipeBook.getCollections();
        if (collections == null || collections.isEmpty()) {
            return null;
        }

        RecipeHolder<CraftingRecipe> fallbackMatch = null;
        for (RecipeCollection collection : collections) {
            if (collection == null) {
                continue;
            }
            List<?> entries = RecipeCompatibilityBridge.getAllRecipesFromCollection(collection);
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            for (Object entry : entries) {
                if (!(entry instanceof RecipeHolder<?> recipeEntry) || !(recipeEntry.value() instanceof CraftingRecipe craftingRecipe)) {
                    continue;
                }

                ItemStack result = getRecipeOutput(craftingRecipe, serverRegistryManager);
                if (result.isEmpty() && clientRegistryManager != serverRegistryManager) {
                    result = getRecipeOutput(craftingRecipe, clientRegistryManager);
                }
                if (result.isEmpty() || !result.is(targetItem)) {
                    continue;
                }

                if (craftMode == NodeMode.CRAFT_PLAYER_GUI && !recipeFitsPlayerGrid(craftingRecipe)) {
                    if (requiresCraftingTable != null) {
                        requiresCraftingTable.set(true);
                    }
                    continue;
                }

                @SuppressWarnings("unchecked")
                RecipeHolder<CraftingRecipe> castEntry = (RecipeHolder<CraftingRecipe>) recipeEntry;
                if (fallbackMatch == null) {
                    fallbackMatch = castEntry;
                }

                List<GridIngredient> gridIngredients = resolveGridIngredients(craftingRecipe, craftMode, ingredientRegistryManager);
                if (canSatisfyGridIngredients(handler, gridIngredients, ingredientRegistryManager)) {
                    return castEntry;
                }
            }
        }
        return fallbackMatch;
    }

    Object findCraftingDisplayEntry(net.minecraft.client.Minecraft client,
                                            Item targetItem,
                                            NodeMode craftMode,
                                            java.util.concurrent.atomic.AtomicBoolean requiresCraftingTable,
                                            Object registryManager) {
        if (client == null || client.player == null) {
            return null;
        }
        if (!(client.player.getRecipeBook() instanceof ClientRecipeBook clientRecipeBook)) {
            return null;
        }
        List<RecipeCollection> collections = clientRecipeBook.getCollections();
        if (collections == null || collections.isEmpty()) {
            return null;
        }
        AbstractContainerMenu handler = client.player != null ? client.player.containerMenu : null;
        Object fallbackMatch = null;
        for (RecipeCollection collection : collections) {
            if (collection == null) {
                continue;
            }
            List<?> entries = RecipeCompatibilityBridge.getAllRecipesFromCollection(collection);
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            for (Object entry : entries) {
                if (entry == null) {
                    continue;
                }
                Object display = RecipeCompatibilityBridge.getDisplayFromEntry(entry);
                if (!RecipeCompatibilityBridge.isCraftingDisplay(display)) {
                    continue;
                }
                ItemStack output = getDisplayOutput(display, registryManager);
                if (output == null || output.isEmpty() || !output.is(targetItem)) {
                    continue;
                }
                if (craftMode == NodeMode.CRAFT_PLAYER_GUI && !displayFitsPlayerGrid(display, registryManager)) {
                    if (requiresCraftingTable != null) {
                        requiresCraftingTable.set(true);
                    }
                    continue;
                }
                if (fallbackMatch == null) {
                    fallbackMatch = entry;
                }
                List<GridIngredient> gridIngredients = resolveDisplayGridIngredients(display, craftMode, registryManager);
                if (canSatisfyGridIngredients(handler, gridIngredients, registryManager)) {
                    return entry;
                }
            }
        }
        return fallbackMatch;
    }
       boolean displayFitsPlayerGrid(Object display, Object registryManager) {
        if (RecipeCompatibilityBridge.isShapedCraftingDisplay(display)) {
            return RecipeCompatibilityBridge.getShapedWidth(display) <= 2
                && RecipeCompatibilityBridge.getShapedHeight(display) <= 2;
        }
        if (RecipeCompatibilityBridge.isShapelessCraftingDisplay(display)) {
            List<?> slots = RecipeCompatibilityBridge.getDisplayIngredientSlots(display);
            int count = countDisplayIngredients(slots, registryManager);
            return count > 0 && count <= 4;
        }
        return false;
    }

    private int countDisplayIngredients(List<?> slots, Object registryManager) {
        if (slots == null || slots.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Object slot : slots) {
            Ingredient ingredient = RecipeCompatibilityBridge.extractDisplayIngredient(slot, registryManager);
            if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                continue;
            }
            count++;
        }
        return count;
    }

    void logRecipeOutputDebug(CraftingRecipe recipe, Object serverRegistryManager, Object clientRegistryManager) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        String recipeClass = recipe != null ? recipe.getClass().getName() : "null";
        String serverMgr = serverRegistryManager != null ? serverRegistryManager.getClass().getName() : "null";
        String clientMgr = clientRegistryManager != null ? clientRegistryManager.getClass().getName() : "null";
        boolean serverLookup = resolveWrapperLookup(serverRegistryManager) != null;
        boolean clientLookup = resolveWrapperLookup(clientRegistryManager) != null;
        List<String> outputMethods = new ArrayList<>();
        List<String> craftMethods = new ArrayList<>();
        List<String> allMethodsSample = new ArrayList<>();
        if (recipe != null) {
            int sampleLimit = 8;
            for (java.lang.reflect.Method method : getAllMethods(recipe.getClass())) {
                if (ItemStack.class.isAssignableFrom(method.getReturnType()) && method.getParameterCount() == 1) {
                    Class<?> paramType = method.getParameterTypes()[0];
                    outputMethods.add(method.getName() + "(" + paramType.getName() + ")");
                }
                if ("craft".equals(method.getName()) && method.getParameterCount() == 2) {
                    Class<?>[] params = method.getParameterTypes();
                    craftMethods.add(method.getName() + "(" + params[0].getName() + "," + params[1].getName() + ")");
                }
                if (allMethodsSample.size() < sampleLimit) {
                    allMethodsSample.add(method.getName() + Arrays.toString(method.getParameterTypes()) + " -> " + method.getReturnType().getName());
                }
            }
        }
        LOGGER.debug(
            "Pathmind craft output debug: recipeClass={} serverRegistry={} clientRegistry={} serverLookup={} clientLookup={} outputMethods={} craftMethods={} methodsSample={}",
            recipeClass,
            serverMgr,
            clientMgr,
            serverLookup,
            clientLookup,
            outputMethods,
            craftMethods,
            allMethodsSample
        );
    }

    List<java.lang.reflect.Method> getAllMethods(Class<?> type) {
        List<java.lang.reflect.Method> methods = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        collectMethodsRecursive(type, methods, seen);
        return methods;
    }

    private void collectMethodsRecursive(Class<?> type, List<java.lang.reflect.Method> methods, java.util.Set<String> seen) {
        if (type == null || type == Object.class) {
            return;
        }
        try {
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                String key = method.getName() + Arrays.toString(method.getParameterTypes()) + method.getReturnType().getName();
                if (seen.add(key)) {
                    method.setAccessible(true);
                    methods.add(method);
                }
            }
        } catch (SecurityException ignored) {
            // Skip inaccessible class declarations.
        }
        for (Class<?> iface : type.getInterfaces()) {
            collectMethodsRecursive(iface, methods, seen);
        }
        collectMethodsRecursive(type.getSuperclass(), methods, seen);
    }

    void logCraftDebug(Item targetItem,
                               NodeMode craftMode,
                               int managerCount,
                               int totalEntries,
                               int craftingEntries,
                               int emptyOutputs,
                               int matchingOutputs,
                               List<String> sampleOutputs,
                               List<String> managerTypes) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        String targetId = targetItem != null ? BuiltInRegistries.ITEM.getKey(targetItem).toString() : "unknown";
        LOGGER.debug(
            "Pathmind craft debug: target={} mode={} managers={} managerTypes={} entries={} craftingEntries={} emptyOutputs={} matchingOutputs={} sampleOutputs={}",
            targetId,
            craftMode,
            managerCount,
            managerTypes,
            totalEntries,
            craftingEntries,
            emptyOutputs,
            matchingOutputs,
            sampleOutputs
        );
    }

    List<RecipeHolder<?>> getCraftingRecipeEntries(Object manager) {
        if (manager == null) {
            return List.of();
        }

        List<RecipeHolder<?>> entries = new ArrayList<>();
        if (tryCollectClientRecipeManagerEntries(manager, entries)) {
            return entries;
        }
        RecipeType<?> craftingType = RecipeType.CRAFTING;
        List<String> preferredNames = List.of("listAllOfType", "getAllOfType", "getAllRecipes", "getRecipes");
        for (String name : preferredNames) {
            try {
                java.lang.reflect.Method method = manager.getClass().getMethod(name, RecipeType.class);
                method.setAccessible(true);
                Object result = method.invoke(manager, craftingType);
                if (collectRecipeEntries(result, entries)) {
                    return entries;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next candidate.
            }
        }

        for (java.lang.reflect.Method method : manager.getClass().getMethods()) {
            if (method.getParameterCount() != 1 || !RecipeType.class.isAssignableFrom(method.getParameterTypes()[0])) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (!Iterable.class.isAssignableFrom(returnType) && !java.util.Map.class.isAssignableFrom(returnType)) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object result = method.invoke(manager, craftingType);
                if (collectRecipeEntries(result, entries)) {
                    return entries;
                }
            } catch (ReflectiveOperationException ignored) {
                // Keep scanning.
            }
        }

        entries.addAll(getRecipeEntries(manager));
        if (entries.isEmpty()) {
            collectRecipeEntriesFromFields(manager, entries, 0, new java.util.IdentityHashMap<>());
        }
        return entries;
    }

    private boolean tryCollectClientRecipeManagerEntries(Object manager, List<RecipeHolder<?>> entries) {
        if (manager == null || entries == null) {
            return false;
        }
        String className = manager.getClass().getName();
        if (!className.contains("ClientRecipeManager") && !className.contains("class_10333")) {
            return false;
        }

        // Newer client recipe manager wraps a RecipeManager and a recipes store.
        Object recipeManager = tryGetFieldValue(manager, "recipeManager", "field_54850");
        if (recipeManager != null && recipeManager != manager) {
            entries.addAll(getRecipeEntries(recipeManager));
            if (!entries.isEmpty()) {
                return true;
            }
        }

        Object recipesStore = tryGetFieldValue(manager, "recipes", "field_54854");
        if (collectRecipeEntries(recipesStore, entries)) {
            return true;
        }

        return false;
    }

    private Object tryGetFieldValue(Object target, String... fieldNames) {
        if (target == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            try {
                java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
                if (!field.canAccess(target)) {
                    field.setAccessible(true);
                }
                Object value = field.get(target);
                if (value != null) {
                    return value;
                }
            } catch (NoSuchFieldException | IllegalAccessException | RuntimeException ignored) {
            }
        }
        return null;
    }

    CachedRecipe findCachedRecipe(net.minecraft.client.Minecraft client, Item targetItem, NodeMode craftMode) {
        if (client == null || targetItem == null || craftMode == null) {
            return null;
        }
        CachedRecipeBook book = loadRecipeCache(client);
        if (book == null || book.recipesByOutput == null) {
            return null;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(targetItem);
        if (id == null) {
            return null;
        }
        List<CachedRecipe> recipes = book.recipesByOutput.get(id.toString());
        if (recipes == null || recipes.isEmpty()) {
            return null;
        }
        AbstractContainerMenu handler = client.player != null ? client.player.containerMenu : null;
        CachedRecipe fallbackMatch = null;
        for (CachedRecipe recipe : recipes) {
            if (recipe == null || !craftMode.name().equals(recipe.mode)) {
                continue;
            }
            if (fallbackMatch == null) {
                fallbackMatch = recipe;
            }
            List<GridIngredient> gridIngredients = buildGridIngredientsFromCache(recipe);
            if (canSatisfyGridIngredients(handler, gridIngredients, client.level)) {
                return recipe;
            }
        }
        return fallbackMatch;
    }

    void cacheCraftingRecipe(net.minecraft.client.Minecraft client,
                                     Item targetItem,
                                     CraftingRecipe recipe,
                                     int outputCount,
                                     Object registryManager) {
        if (client == null || targetItem == null || recipe == null) {
            return;
        }
        if (client.getSingleplayerServer() == null) {
            return;
        }
        CachedRecipeBook book = loadRecipeCache(client);
        if (book == null) {
            return;
        }
        if (book.recipesByOutput == null || book.recipesByOutput.isEmpty()) {
            cacheAllCraftingRecipes(book, client, registryManager);
        }

        cacheRecipeForMode(book, targetItem, recipe, outputCount, NodeMode.CRAFT_CRAFTING_TABLE, registryManager);
        if (recipeFitsPlayerGrid(recipe)) {
            cacheRecipeForMode(book, targetItem, recipe, outputCount, NodeMode.CRAFT_PLAYER_GUI, registryManager);
        }

        saveRecipeCache(client, book);
    }

    private void cacheAllCraftingRecipes(CachedRecipeBook book,
                                         net.minecraft.client.Minecraft client,
                                         Object registryManager) {
        if (book == null || client == null || client.getSingleplayerServer() == null) {
            return;
        }
        Object manager = client.getSingleplayerServer().getRecipeManager();
        if (manager == null) {
            return;
        }
        Object serverRegistryManager = client.getSingleplayerServer().registryAccess();
        for (RecipeHolder<?> entry : getCraftingRecipeEntries(manager)) {
            if (!(entry.value() instanceof CraftingRecipe craftingRecipe)) {
                continue;
            }
            ItemStack output = getRecipeOutput(craftingRecipe, serverRegistryManager);
            if ((output == null || output.isEmpty()) && registryManager != serverRegistryManager) {
                output = getRecipeOutput(craftingRecipe, registryManager);
            }
            if (output == null || output.isEmpty()) {
                continue;
            }
            Item outputItem = output.getItem();
            int count = output.getCount();
            cacheRecipeForMode(book, outputItem, craftingRecipe, count, NodeMode.CRAFT_CRAFTING_TABLE, registryManager);
            if (recipeFitsPlayerGrid(craftingRecipe)) {
                cacheRecipeForMode(book, outputItem, craftingRecipe, count, NodeMode.CRAFT_PLAYER_GUI, registryManager);
            }
        }
    }

    private void cacheAllCraftingDisplays(CachedRecipeBook book,
                                          net.minecraft.client.Minecraft client,
                                          Object registryManager) {
        if (book == null || client == null || client.player == null) {
            return;
        }
        if (!(client.player.getRecipeBook() instanceof ClientRecipeBook clientRecipeBook)) {
            return;
        }
        List<RecipeCollection> collections = clientRecipeBook.getCollections();
        if (collections == null || collections.isEmpty()) {
            return;
        }
        for (RecipeCollection collection : collections) {
            if (collection == null) {
                continue;
            }
            List<?> entries = RecipeCompatibilityBridge.getAllRecipesFromCollection(collection);
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            for (Object entry : entries) {
                if (entry == null) {
                    continue;
                }
                Object display = RecipeCompatibilityBridge.getDisplayFromEntry(entry);
                if (!RecipeCompatibilityBridge.isCraftingDisplay(display)) {
                    continue;
                }
                ItemStack output = getDisplayOutput(display, registryManager);
                if (output == null || output.isEmpty()) {
                    continue;
                }
                cacheDisplayForMode(book, output.getItem(), output.getCount(), display, NodeMode.CRAFT_CRAFTING_TABLE, registryManager);
                if (displayFitsPlayerGrid(display, registryManager)) {
                    cacheDisplayForMode(book, output.getItem(), output.getCount(), display, NodeMode.CRAFT_PLAYER_GUI, registryManager);
                }
            }
        }
    }

    void cacheDisplayForMode(CachedRecipeBook book,
                                     Item targetItem,
                                     int outputCount,
                                     Object display,
                                     NodeMode mode,
                                     Object registryManager) {
        if (book == null || targetItem == null || display == null || mode == null) {
            return;
        }
        List<GridIngredient> grid = resolveDisplayGridIngredients(display, mode, registryManager);
        if (grid == null || grid.isEmpty()) {
            return;
        }
        List<CachedGridIngredient> cachedGrid = new ArrayList<>();
        for (GridIngredient ingredient : grid) {
            if (ingredient == null || ingredient.ingredient() == null) {
                continue;
            }
            List<ItemStack> stacks = RecipeCompatibilityBridge.getIngredientStacks(ingredient.ingredient(), registryManager);
            if (stacks == null || stacks.isEmpty()) {
                stacks = resolveIngredientStacksByTesting(ingredient.ingredient());
            }
            if (stacks == null || stacks.isEmpty()) {
                continue;
            }
            List<String> itemIds = new ArrayList<>();
            for (ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id != null) {
                    itemIds.add(id.toString());
                }
            }
            if (itemIds.isEmpty()) {
                continue;
            }
            CachedGridIngredient cachedIngredient = new CachedGridIngredient();
            cachedIngredient.slotIndex = ingredient.slotIndex();
            cachedIngredient.itemIds = itemIds;
            cachedGrid.add(cachedIngredient);
        }
        if (cachedGrid.isEmpty()) {
            return;
        }

        CachedRecipe cachedRecipe = new CachedRecipe();
        cachedRecipe.mode = mode.name();
        cachedRecipe.outputCount = Math.max(1, outputCount);
        cachedRecipe.grid = cachedGrid;

        Identifier outputId = BuiltInRegistries.ITEM.getKey(targetItem);
        if (outputId == null) {
            return;
        }
        String key = outputId.toString();
        addCachedRecipe(book, key, cachedRecipe);
    }

    void cacheRecipeForMode(CachedRecipeBook book,
                                    Item targetItem,
                                    CraftingRecipe recipe,
                                    int outputCount,
                                    NodeMode mode,
                                    Object registryManager) {
        if (book == null || targetItem == null || recipe == null || mode == null) {
            return;
        }
        List<GridIngredient> grid = resolveGridIngredients(recipe, mode, registryManager);
        if (grid == null || grid.isEmpty()) {
            return;
        }
        List<CachedGridIngredient> cachedGrid = new ArrayList<>();
        for (GridIngredient ingredient : grid) {
            if (ingredient == null || ingredient.ingredient() == null) {
                continue;
            }
            List<ItemStack> stacks = RecipeCompatibilityBridge.getIngredientStacks(ingredient.ingredient(), registryManager);
            if (stacks == null || stacks.isEmpty()) {
                stacks = resolveIngredientStacksByTesting(ingredient.ingredient());
            }
            if (stacks == null || stacks.isEmpty()) {
                continue;
            }
            List<String> itemIds = new ArrayList<>();
            for (ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id != null) {
                    itemIds.add(id.toString());
                }
            }
            if (itemIds.isEmpty()) {
                continue;
            }
            CachedGridIngredient cachedIngredient = new CachedGridIngredient();
            cachedIngredient.slotIndex = ingredient.slotIndex();
            cachedIngredient.itemIds = itemIds;
            cachedGrid.add(cachedIngredient);
        }
        if (cachedGrid.isEmpty()) {
            return;
        }

        CachedRecipe cachedRecipe = new CachedRecipe();
        cachedRecipe.mode = mode.name();
        cachedRecipe.outputCount = Math.max(1, outputCount);
        cachedRecipe.grid = cachedGrid;

        Identifier outputId = BuiltInRegistries.ITEM.getKey(targetItem);
        if (outputId == null) {
            return;
        }
        String key = outputId.toString();
        addCachedRecipe(book, key, cachedRecipe);
    }

    private void addCachedRecipe(CachedRecipeBook book, String key, CachedRecipe cachedRecipe) {
        if (book == null || key == null || key.isBlank() || cachedRecipe == null) {
            return;
        }
        book.recipesByOutput.computeIfAbsent(key, unused -> new ArrayList<>());
        List<CachedRecipe> list = book.recipesByOutput.get(key);
        String signature = getCachedRecipeSignature(cachedRecipe);
        list.removeIf(existing -> existing != null && getCachedRecipeSignature(existing).equals(signature));
        list.add(cachedRecipe);
    }

    List<GridIngredient> buildGridIngredientsFromCache(CachedRecipe cachedRecipe) {
        List<GridIngredient> result = new ArrayList<>();
        if (cachedRecipe == null || cachedRecipe.grid == null) {
            return result;
        }
        boolean legacyZeroBasedSlots = isLegacyZeroBasedCachedRecipe(cachedRecipe);
        for (CachedGridIngredient cachedIngredient : cachedRecipe.grid) {
            if (cachedIngredient == null || cachedIngredient.itemIds == null || cachedIngredient.itemIds.isEmpty()) {
                continue;
            }
            Ingredient ingredient = buildIngredientFromItemIds(cachedIngredient.itemIds);
            if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient)) {
                continue;
            }
            int slotIndex = normalizeCachedRecipeSlotIndex(cachedIngredient.slotIndex, legacyZeroBasedSlots);
            result.add(new GridIngredient(slotIndex, ingredient, false));
        }
        return result;
    }

    private boolean isLegacyZeroBasedCachedRecipe(CachedRecipe cachedRecipe) {
        if (cachedRecipe == null || cachedRecipe.grid == null || cachedRecipe.grid.isEmpty()) {
            return false;
        }
        for (CachedGridIngredient ingredient : cachedRecipe.grid) {
            if (ingredient != null && ingredient.slotIndex == 0) {
                return true;
            }
        }
        return false;
    }
        ItemStack getRecipeOutput(CraftingRecipe recipe, Object registryManager) {
        if (recipe == null) {
            return ItemStack.EMPTY;
        }
        HolderLookup.Provider lookup = resolveWrapperLookup(registryManager);
        Object registryArg = registryManager;
        CraftingInput input = buildRecipeOutputInput(recipe, registryManager);
        for (java.lang.reflect.Method method : getAllMethods(recipe.getClass())) {
            if (!ItemStack.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (method.getParameterCount() == 1) {
                try {
                    Class<?> paramType = method.getParameterTypes()[0];
                    Object arg = null;
                    if (input != null && paramType.isInstance(input)) {
                        // Minecraft 26.2 changed Recipe#assemble from
                        // assemble(input, registries) to assemble(input).
                        arg = input;
                    } else if (registryArg != null && paramType.isInstance(registryArg)) {
                        arg = registryArg;
                    } else if (lookup != null && paramType.isInstance(lookup)) {
                        arg = lookup;
                    }
                    if (arg == null) {
                        continue;
                    }
                    Object result = method.invoke(recipe, arg);
                    if (result instanceof ItemStack stack) {
                        return stack;
                    }
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                    // Keep scanning.
                }
            }
        }
        for (java.lang.reflect.Method method : getAllMethods(recipe.getClass())) {
            if (!ItemStack.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (!params[0].isInstance(input)) {
                continue;
            }
            Object arg = null;
            if (lookup != null && params[1].isInstance(lookup)) {
                arg = lookup;
            } else if (registryArg != null && params[1].isInstance(registryArg)) {
                arg = registryArg;
            }
            if (arg == null) {
                continue;
            }
            try {
                Object result = method.invoke(recipe, input, arg);
                if (result instanceof ItemStack stack) {
                    return stack;
                }
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                // Keep scanning.
            }
        }
        for (java.lang.reflect.Method method : getAllMethods(recipe.getClass())) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            if (!ItemStack.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            try {
                Object result = method.invoke(recipe);
                if (result instanceof ItemStack stack) {
                    return stack;
                }
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                // Keep scanning.
            }
        }
        return ItemStack.EMPTY;
    }

    private CraftingInput buildRecipeOutputInput(CraftingRecipe recipe, Object registryManager) {
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        if (recipe == null) {
            return CraftingInput.of(3, 3, grid);
        }

        List<GridIngredient> resolvedGrid = resolveGridIngredients(recipe, NodeMode.CRAFT_CRAFTING_TABLE, registryManager);
        if (resolvedGrid != null && !resolvedGrid.isEmpty()) {
            for (GridIngredient gridIngredient : resolvedGrid) {
                if (gridIngredient == null || gridIngredient.ingredient() == null) {
                    continue;
                }
                ItemStack stack = getRepresentativeIngredientStack(gridIngredient.ingredient(), registryManager);
                if (stack.isEmpty()) {
                    continue;
                }
                int slot = gridIngredient.slotIndex() - 1;
                if (slot >= 0 && slot < grid.size()) {
                    grid.set(slot, stack);
                }
            }
            return CraftingInput.of(3, 3, grid);
        }

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            List<?> ingredients = RecipeCompatibilityBridge.getRecipeIngredients(recipe);
            if ((ingredients == null || ingredients.isEmpty())) {
                ingredients = readRecipeIngredients(recipe);
            }
            int width = Math.min(3, Math.max(1, shapedRecipe.getWidth()));
            int height = Math.min(3, Math.max(1, shapedRecipe.getHeight()));
            int limit = Math.min(ingredients != null ? ingredients.size() : 0, width * height);
            for (int i = 0; i < limit; i++) {
                Ingredient ingredient = unwrapRecipeIngredient(ingredients.get(i));
                ItemStack stack = getRepresentativeIngredientStack(ingredient, registryManager);
                if (stack.isEmpty()) {
                    continue;
                }
                int x = i % width;
                int y = i / width;
                int slot = x + (y * 3);
                if (slot >= 0 && slot < grid.size()) {
                    grid.set(slot, stack);
                }
            }
            return CraftingInput.of(3, 3, grid);
        }

        List<?> ingredients = RecipeCompatibilityBridge.getRecipeIngredients(recipe);
        if ((ingredients == null || ingredients.isEmpty())) {
            ingredients = readRecipeIngredients(recipe);
        }
        if (ingredients != null && !ingredients.isEmpty()) {
            int placed = 0;
            for (Object entry : ingredients) {
                if (placed >= 9) {
                    break;
                }
                Ingredient ingredient = unwrapRecipeIngredient(entry);
                ItemStack stack = getRepresentativeIngredientStack(ingredient, registryManager);
                if (stack.isEmpty()) {
                    continue;
                }
                grid.set(placed, stack);
                placed++;
            }
        }
        return CraftingInput.of(3, 3, grid);
    }

    private ItemStack getRepresentativeIngredientStack(Ingredient ingredient, Object registryManager) {
        if (ingredient == null || RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> stacks = RecipeCompatibilityBridge.getIngredientStacks(ingredient, registryManager);
        if (stacks == null || stacks.isEmpty()) {
            stacks = resolveIngredientStacksByTesting(ingredient);
        }
        if (stacks == null || stacks.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                return stack.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    ItemStack getDisplayOutput(Object display, Object registryManager) {
        if (display == null) {
            return ItemStack.EMPTY;
        }
        Object result = RecipeCompatibilityBridge.getResultSlotDisplay(display);
        if (result == null) {
            return ItemStack.EMPTY;
        }
        return RecipeCompatibilityBridge.getSlotDisplayFirst(result, registryManager);
    }

    List<GridIngredient> resolveDisplayGridIngredients(Object display, NodeMode craftMode, Object registryManager) {
        if (RecipeCompatibilityBridge.isShapedCraftingDisplay(display)) {
            return resolveShapedDisplayGridIngredients(display, craftMode, registryManager);
        }
        if (RecipeCompatibilityBridge.isShapelessCraftingDisplay(display)) {
            return resolveShapelessDisplayGridIngredients(display, craftMode, registryManager);
        }
        return Collections.emptyList();
    }

    private List<GridIngredient> resolveShapedDisplayGridIngredients(Object display,
                                                                     NodeMode craftMode,
                                                                     Object registryManager) {
        List<GridIngredient> result = new ArrayList<>();
        if (display == null) {
            return result;
        }
        int recipeWidth = Math.max(RecipeCompatibilityBridge.getShapedWidth(display), 1);
        int recipeHeight = Math.max(RecipeCompatibilityBridge.getShapedHeight(display), 1);
        if (craftMode == NodeMode.CRAFT_PLAYER_GUI && (recipeWidth > 2 || recipeHeight > 2)) {
            return result;
        }
        int gridWidth = craftMode == NodeMode.CRAFT_CRAFTING_TABLE ? 3 : 2;
        int width = Math.min(recipeWidth, gridWidth);
        int height = Math.min(recipeHeight, gridWidth);
        List<?> slots = RecipeCompatibilityBridge.getDisplayIngredientSlots(display);
        if (slots == null || slots.isEmpty()) {
            return result;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = x + (y * recipeWidth);
                if (index < 0 || index >= slots.size()) {
                    continue;
                }
                Ingredient ingredient = RecipeCompatibilityBridge.extractDisplayIngredient(slots.get(index), registryManager);
                if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                    continue;
                }
                int slotIndex = 1 + x + (y * gridWidth);
                result.add(new GridIngredient(slotIndex, ingredient, false));
            }
        }
        return result;
    }

    private List<GridIngredient> resolveShapelessDisplayGridIngredients(Object display,
                                                                        NodeMode craftMode,
                                                                        Object registryManager) {
        List<GridIngredient> result = new ArrayList<>();
        if (display == null) {
            return result;
        }
        List<?> slots = RecipeCompatibilityBridge.getDisplayIngredientSlots(display);
        if (slots == null || slots.isEmpty()) {
            return result;
        }
        int gridLimit = craftMode == NodeMode.CRAFT_CRAFTING_TABLE ? 9 : 4;
        int placed = 0;
        for (Object slot : slots) {
            if (placed >= gridLimit) {
                break;
            }
            Ingredient ingredient = RecipeCompatibilityBridge.extractDisplayIngredient(slot, registryManager);
            if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                continue;
            }
            result.add(new GridIngredient(1 + placed, ingredient, false));
            placed++;
        }
        return result;
    }


    HolderLookup.Provider resolveWrapperLookup(Object registryManager) {
        if (registryManager == null) {
            return null;
        }
        if (registryManager instanceof HolderLookup.Provider wrapper) {
            return wrapper;
        }
        for (String methodName : new String[]{"getWrapperLookup", "getRegistryLookup", "getLookup"}) {
            try {
                java.lang.reflect.Method method = registryManager.getClass().getMethod(methodName);
                method.setAccessible(true);
                Object result = method.invoke(registryManager);
                if (result instanceof HolderLookup.Provider wrapper) {
                    return wrapper;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next candidate.
            }
        }
        for (java.lang.reflect.Method method : registryManager.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            if (!HolderLookup.Provider.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object result = method.invoke(registryManager);
                if (result instanceof HolderLookup.Provider wrapper) {
                    return wrapper;
                }
            } catch (ReflectiveOperationException ignored) {
                // Keep scanning.
            }
        }
        return null;
    }

    boolean recipeFitsPlayerGrid(CraftingRecipe recipe) {
        if (recipe == null) {
            return false;
        }

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            return shapedRecipe.getWidth() <= 2 && shapedRecipe.getHeight() <= 2;
        }

        Object placement = RecipeCompatibilityBridge.getIngredientPlacement(recipe);
        if (placement == null) {
            List<?> ingredients = RecipeCompatibilityBridge.getRecipeIngredients(recipe);
            if (ingredients == null || ingredients.isEmpty()) {
                ingredients = readRecipeIngredients(recipe);
            }
            if (ingredients == null || ingredients.isEmpty()) {
                return false;
            }
            int nonEmpty = 0;
            for (Object entry : ingredients) {
                Ingredient ingredient = unwrapRecipeIngredient(entry);
                if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient)) {
                    continue;
                }
                nonEmpty++;
            }
            return nonEmpty <= 4;
        }

        if (RecipeCompatibilityBridge.hasNoPlacement(placement)) {
            return RecipeCompatibilityBridge.getPlacementIngredients(placement).size() <= 4;
        }

        IntList slots = RecipeCompatibilityBridge.toPlacementSlots(placement);
        if (slots == null || slots.isEmpty()) {
            return RecipeCompatibilityBridge.getPlacementIngredients(placement).size() <= 4;
        }

        int minX = 3;
        int minY = 3;
        int maxX = -1;
        int maxY = -1;
        for (int slot : slots) {
            int x = slot % 3;
            int y = slot / 3;
            if (x < minX) {
                minX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y > maxY) {
                maxY = y;
            }
        }

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        return width <= 2 && height <= 2;
    }

    CraftingSummary craftRecipeUsingScreen(net.minecraft.client.Minecraft client,
                                                   NodeMode craftMode,
                                                   RecipeHolder<CraftingRecipe> recipeEntry,
                                                   Item targetItem,
                                                   int craftsRequested,
                                                   int desiredCount,
                                                   String itemDisplayName,
                                                   List<GridIngredient> gridIngredients,
                                                   int[] gridSlots,
                                                   Object registryManager) throws InterruptedException {
        int totalProduced = 0;
        String failureMessage = null;

        for (int attempt = 0; attempt < craftsRequested; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            if (!isCraftingScreenAvailable(client, craftMode)) {
                failureMessage = craftMode == NodeMode.CRAFT_CRAFTING_TABLE
                        ? "Cannot craft " + itemDisplayName + ": open a crafting table GUI before running this node."
                        : "Cannot craft " + itemDisplayName + ": open your inventory or a crafting table GUI before running this node.";
                break;
            }

            AbstractContainerMenu handler = client.player != null ? client.player.containerMenu : null;
            if (!isCompatibleCraftingHandler(handler, craftMode)) {
                failureMessage = "Cannot craft " + itemDisplayName + ": the crafting screen closed.";
                break;
            }

            int craftsRemaining = Math.max(1, craftsRequested - attempt);
            int craftsThisAttempt = getCraftBatchSize(client, gridIngredients, registryManager, craftsRemaining);
            if (craftsThisAttempt <= 0) {
                failureMessage = "Cannot craft " + itemDisplayName + ": missing required ingredients.";
                break;
            }

            CraftingAttemptResult attemptResult = performCraftingAttempt(client, targetItem, itemDisplayName, gridIngredients, gridSlots, craftMode, registryManager, craftsThisAttempt);
            if (attemptResult.errorMessage != null) {
                failureMessage = attemptResult.errorMessage;
                if (attemptResult.produced > 0) {
                    totalProduced += attemptResult.produced;
                }
                break;
            }

            if (attemptResult.produced <= 0) {
                failureMessage = "Cannot craft " + itemDisplayName + ": missing required ingredients.";
                break;
            }

            totalProduced += attemptResult.produced;
            attempt += Math.max(0, craftsThisAttempt - 1);

            if (totalProduced >= desiredCount) {
                break;
            }
        }

        if (totalProduced <= 0 && failureMessage == null) {
            failureMessage = "Cannot craft " + itemDisplayName + ": missing required ingredients.";
        }

        return new CraftingSummary(totalProduced, failureMessage);
    }

    private CraftingAttemptResult performCraftingAttempt(net.minecraft.client.Minecraft client,
                                                         Item targetItem,
                                                         String itemDisplayName,
                                                         List<GridIngredient> gridIngredients,
                                                         int[] gridSlots,
                                                         NodeMode craftMode,
                                                         Object registryManager,
                                                         int craftsThisAttempt) throws InterruptedException {
        java.util.concurrent.atomic.AtomicReference<String> errorRef = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger producedRef = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<List<Integer>> plannedSourceSlotsRef = new java.util.concurrent.atomic.AtomicReference<>();

        owner.runOnClientThread(client, () -> {
            MultiPlayerGameMode interactionManager = client.gameMode;
            if (interactionManager == null) {
                errorRef.set("Cannot craft " + itemDisplayName + ": interaction manager unavailable.");
                return;
            }

            AbstractContainerMenu handler = client.player != null ? client.player.containerMenu : null;
            if (handler == null) {
                errorRef.set("Cannot craft " + itemDisplayName + ": the crafting screen closed.");
                return;
            }

            clearCraftingGrid(client, interactionManager, handler, gridSlots, craftMode);
            List<Integer> plannedSourceSlots = planIngredientSourceSlots(handler, gridIngredients, registryManager, craftsThisAttempt);
            if (plannedSourceSlots == null || plannedSourceSlots.size() != gridIngredients.size()) {
                errorRef.set("Cannot craft " + itemDisplayName + ": missing required ingredients.");
                return;
            }
            plannedSourceSlotsRef.set(plannedSourceSlots);
        });

        if (errorRef.get() != null) {
            return new CraftingAttemptResult(0, errorRef.get());
        }

        List<Integer> plannedSourceSlots = plannedSourceSlotsRef.get();
        if (plannedSourceSlots == null || plannedSourceSlots.size() != gridIngredients.size()) {
            return new CraftingAttemptResult(0, "Cannot craft " + itemDisplayName + ": missing required ingredients.");
        }

        for (int ingredientIndex = 0; ingredientIndex < gridIngredients.size(); ingredientIndex++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            GridIngredient ingredient = gridIngredients.get(ingredientIndex);
            if (ingredient == null) {
                continue;
            }
            if (!ingredient.allowEmpty() && RecipeCompatibilityBridge.isIngredientEmpty(ingredient.ingredient(), registryManager)) {
                continue;
            }

            java.util.concurrent.atomic.AtomicBoolean placed = new java.util.concurrent.atomic.AtomicBoolean(false);
            final int plannedSourceSlot = plannedSourceSlots.get(ingredientIndex);

            owner.runOnClientThread(client, () -> {
                MultiPlayerGameMode interactionManager = client.gameMode;
                if (interactionManager == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": interaction manager unavailable.");
                    return;
                }

                AbstractContainerMenu handler = client.player != null ? client.player.containerMenu : null;
                if (handler == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": the crafting screen closed.");
                    return;
                }

                int sourceSlot = plannedSourceSlot;
                if (sourceSlot == -1) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": missing required ingredients.");
                    return;
                }

                int targetSlot = mapLogicalSlotToHandlerSlot(handler, craftMode, ingredient.slotIndex());
                if (targetSlot < 0) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": crafting grid slot unavailable.");
                    return;
                }

                ItemStack sourceBefore = safeCopySlotStack(handler, sourceSlot);
                ItemStack targetBefore = safeCopySlotStack(handler, targetSlot);
                placeIngredientStackCount(interactionManager, handler, client.player, sourceSlot, targetSlot, craftsThisAttempt);

                ItemStack sourceAfter = safeCopySlotStack(handler, sourceSlot);
                ItemStack targetAfter = safeCopySlotStack(handler, targetSlot);
                ItemStack cursorAfter = handler.getCarried() == null ? ItemStack.EMPTY : handler.getCarried().copy();
                if (stackMatchesIngredient(targetAfter, ingredient.ingredient(), registryManager)
                    && targetAfter.getCount() >= craftsThisAttempt) {
                    placed.set(true);
                    return;
                }

                LOGGER.warn(
                    "Crafting '{}' failed to place ingredient. mode={}, handler={}, logicalSlot={}, sourceSlot={}, targetSlot={}, sourceBefore={}, sourceAfter={}, targetBefore={}, targetAfter={}, cursorAfter={}",
                    itemDisplayName,
                    craftMode,
                    handler.getClass().getSimpleName(),
                    ingredient.slotIndex(),
                    sourceSlot,
                    targetSlot,
                    describeItemStack(sourceBefore),
                    describeItemStack(sourceAfter),
                    describeItemStack(targetBefore),
                    describeItemStack(targetAfter),
                    describeItemStack(cursorAfter)
                );
                errorRef.set(
                    "Cannot craft " + itemDisplayName + ": failed to place ingredient into grid slot "
                        + ingredient.slotIndex() + "."
                );
            });

            if (errorRef.get() != null) {
                return new CraftingAttemptResult(producedRef.get(), errorRef.get());
            }

            if (!placed.get()) {
                return new CraftingAttemptResult(producedRef.get(), "Cannot craft " + itemDisplayName + ": failed to place ingredients.");
            }

            Thread.sleep(CRAFTING_ACTION_DELAY_MS);
        }

        for (int poll = 0; poll < CRAFTING_OUTPUT_POLL_LIMIT && producedRef.get() <= 0 && errorRef.get() == null; poll++) {
            owner.runOnClientThread(client, () -> {
                MultiPlayerGameMode interactionManager = client.gameMode;
                if (interactionManager == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": interaction manager unavailable.");
                    return;
                }

                AbstractContainerMenu handler = client.player != null ? client.player.containerMenu : null;
                if (handler == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": the crafting screen closed.");
                    return;
                }

                Slot outputSlot;
                try {
                    outputSlot = handler.getSlot(0);
                } catch (IndexOutOfBoundsException e) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": crafting output unavailable.");
                    return;
                }

                ItemStack resultStack = outputSlot.getItem();
                if (resultStack.isEmpty() || !resultStack.is(targetItem)) {
                    return;
                }

                producedRef.set(Math.max(1, resultStack.getCount()) * Math.max(1, craftsThisAttempt));
                interactionManager.handleInventoryMouseClick(handler.containerId, 0, 0, ClickType.QUICK_MOVE, client.player);
            });

            if (producedRef.get() > 0 || errorRef.get() != null) {
                break;
            }

            Thread.sleep(CRAFTING_ACTION_DELAY_MS);
        }

        if (producedRef.get() > 0) {
            owner.runOnClientThread(client, () -> {
                MultiPlayerGameMode interactionManager = client.gameMode;
                if (interactionManager == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": interaction manager unavailable.");
                    return;
                }

                AbstractContainerMenu handler = client.player != null ? client.player.containerMenu : null;
                if (handler == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": the crafting screen closed.");
                    return;
                }

                clearCraftingGrid(client, interactionManager, handler, gridSlots, craftMode);
            });

            if (errorRef.get() != null) {
                return new CraftingAttemptResult(producedRef.get(), errorRef.get());
            }

            Thread.sleep(CRAFTING_ACTION_DELAY_MS);
            return new CraftingAttemptResult(producedRef.get(), null);
        }

        if (errorRef.get() != null) {
            return new CraftingAttemptResult(producedRef.get(), errorRef.get());
        }

        String outputFailureMessage = logCraftingOutputFailure(client, itemDisplayName, craftMode, gridIngredients, plannedSourceSlots);
        return new CraftingAttemptResult(0, outputFailureMessage);
    }

    private String logCraftingOutputFailure(net.minecraft.client.Minecraft client,
                                            String itemDisplayName,
                                            NodeMode craftMode,
                                            List<GridIngredient> gridIngredients,
                                            List<Integer> plannedSourceSlots) {
        if (client == null || client.player == null) {
            return "Cannot craft " + itemDisplayName + ": crafting failed before the result could be inspected.";
        }

        AbstractContainerMenu handler = client.player.containerMenu;
        if (handler == null) {
            LOGGER.warn("Crafting '{}' failed before output appeared: handler missing after placement.", itemDisplayName);
            return "Cannot craft " + itemDisplayName + ": crafting screen closed before the result appeared.";
        }

        String outputDescription = "missing";
        try {
            Slot outputSlot = handler.getSlot(0);
            ItemStack outputStack = outputSlot.getItem();
            if (!outputStack.isEmpty()) {
                Identifier outputId = BuiltInRegistries.ITEM.getKey(outputStack.getItem());
                outputDescription = outputId + " x" + outputStack.getCount();
            } else {
                outputDescription = "empty";
            }
        } catch (RuntimeException ignored) {
            outputDescription = "unavailable";
        }
        String cursorDescription = describeItemStack(handler.getCarried());

        LOGGER.warn(
            "Crafting '{}' produced no output after ingredient placement. mode={}, handler={}, outputSlot={}, cursor={}, sources={}, grid={}",
            itemDisplayName,
            craftMode,
            handler.getClass().getSimpleName(),
            outputDescription,
            cursorDescription,
            plannedSourceSlots,
            describeCraftingGridIngredients(gridIngredients)
        );
        return "Cannot craft " + itemDisplayName + ": ingredients were placed, but the result slot stayed "
            + outputDescription + " (cursor " + cursorDescription + ").";
    }

    private ItemStack safeCopySlotStack(AbstractContainerMenu handler, int slotIndex) {
        if (handler == null || slotIndex < 0 || slotIndex >= handler.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = handler.getSlot(slotIndex);
        if (slot == null || slot.getItem() == null) {
            return ItemStack.EMPTY;
        }
        return slot.getItem().copy();
    }

    private boolean stackMatchesIngredient(ItemStack stack, Ingredient ingredient, Object registryManager) {
        if (stack == null || stack.isEmpty() || ingredient == null) {
            return false;
        }
        if (ingredient.test(stack)) {
            return true;
        }
        return matchesCandidateStack(RecipeCompatibilityBridge.getIngredientStacks(ingredient, registryManager), stack);
    }

    private String describeItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String id = itemId != null ? itemId.toString() : stack.getItem().toString();
        return id + " x" + stack.getCount();
    }

    private int getCraftBatchSize(net.minecraft.client.Minecraft client,
                                  List<GridIngredient> gridIngredients,
                                  Object registryManager,
                                  int requestedCrafts) throws InterruptedException {
        if (client == null || requestedCrafts <= 0) {
            return 0;
        }

        java.util.concurrent.atomic.AtomicInteger batchSizeRef = new java.util.concurrent.atomic.AtomicInteger();
        owner.runOnClientThread(client, () -> {
            AbstractContainerMenu handler = client.player != null ? client.player.containerMenu : null;
            batchSizeRef.set(calculateCraftBatchSize(handler, gridIngredients, registryManager, requestedCrafts));
        });
        return batchSizeRef.get();
    }

    private int calculateCraftBatchSize(AbstractContainerMenu handler,
                                        List<GridIngredient> gridIngredients,
                                        Object registryManager,
                                        int requestedCrafts) {
        if (handler == null || gridIngredients == null || gridIngredients.isEmpty() || requestedCrafts <= 0) {
            return 0;
        }

        int upperBound = Math.max(1, requestedCrafts);
        for (GridIngredient ingredient : gridIngredients) {
            if (ingredient == null || RecipeCompatibilityBridge.isIngredientEmpty(ingredient.ingredient(), registryManager)) {
                continue;
            }
            ItemStack representative = getRepresentativeIngredientStack(ingredient.ingredient(), registryManager);
            int maxStackCount = representative.isEmpty() ? 64 : Math.max(1, representative.getMaxStackSize());
            upperBound = Math.min(upperBound, maxStackCount);
        }

        for (int batchSize = upperBound; batchSize >= 1; batchSize--) {
            if (planIngredientSourceSlots(handler, gridIngredients, registryManager, batchSize) != null) {
                return batchSize;
            }
        }
        return 0;
    }

    private void placeIngredientStackCount(MultiPlayerGameMode interactionManager,
                                           AbstractContainerMenu handler,
                                           net.minecraft.world.entity.player.Player player,
                                           int sourceSlot,
                                           int targetSlot,
                                           int count) {
        if (interactionManager == null || handler == null || player == null || count <= 0) {
            return;
        }

        interactionManager.handleInventoryMouseClick(handler.containerId, sourceSlot, 0, ClickType.PICKUP, player);
        int cursorCount = handler.getCarried() == null ? 0 : handler.getCarried().getCount();
        if (cursorCount <= count) {
            interactionManager.handleInventoryMouseClick(handler.containerId, targetSlot, 0, ClickType.PICKUP, player);
            return;
        }

        for (int placed = 0; placed < count; placed++) {
            interactionManager.handleInventoryMouseClick(handler.containerId, targetSlot, 1, ClickType.PICKUP, player);
        }
        if (handler.getCarried() != null && !handler.getCarried().isEmpty()) {
            interactionManager.handleInventoryMouseClick(handler.containerId, sourceSlot, 0, ClickType.PICKUP, player);
        }
    }

    private List<Integer> planIngredientSourceSlots(AbstractContainerMenu handler,
                                                    List<GridIngredient> gridIngredients,
                                                    Object registryManager) {
        return planIngredientSourceSlots(handler, gridIngredients, registryManager, 1);
    }

    private List<Integer> planIngredientSourceSlots(AbstractContainerMenu handler,
                                                    List<GridIngredient> gridIngredients,
                                                    Object registryManager,
                                                    int countPerIngredient) {
        if (handler == null || gridIngredients == null) {
            return null;
        }

        List<Integer> handlerSlots = new ArrayList<>();
        List<ItemStack> inventoryStacks = new ArrayList<>();
        List<IngredientReservation> reservations = new ArrayList<>();
        List<Slot> slots = handler.slots;
        for (int slotIdx = 0; slotIdx < slots.size(); slotIdx++) {
            Slot slot = slots.get(slotIdx);
            if (!(slot.container instanceof Inventory)) {
                continue;
            }

            int inventoryIndex = slot.getContainerSlot();
            if (inventoryIndex < 0 || inventoryIndex >= Inventory.INVENTORY_SIZE) {
                continue;
            }

            handlerSlots.add(slotIdx);
            inventoryStacks.add(slot.getItem().copy());
        }

        for (GridIngredient ingredient : gridIngredients) {
            if (ingredient == null) {
                reservations.add(new IngredientReservation(-1));
                continue;
            }
            if (!ingredient.allowEmpty() && RecipeCompatibilityBridge.isIngredientEmpty(ingredient.ingredient(), registryManager)) {
                reservations.add(new IngredientReservation(-1));
                continue;
            }

            int inventorySlotIndex = findIngredientInventorySlot(inventoryStacks, ingredient.ingredient(), registryManager, ingredient.allowEmpty());
            if (inventorySlotIndex == -1) {
                return null;
            }

            ItemStack reservedStack = inventoryStacks.get(inventorySlotIndex);
            if (reservedStack.getCount() < countPerIngredient) {
                return null;
            }

            reservedStack.shrink(countPerIngredient);
            reservations.add(new IngredientReservation(handlerSlots.get(inventorySlotIndex)));
        }

        List<Integer> plannedSlots = new ArrayList<>(reservations.size());
        for (IngredientReservation reservation : reservations) {
            plannedSlots.add(reservation.handlerSlot());
        }
        return plannedSlots;
    }

    private void clearCraftingGrid(net.minecraft.client.Minecraft client,
                                   MultiPlayerGameMode interactionManager,
                                   AbstractContainerMenu handler,
                                   int[] gridSlots,
                                   NodeMode craftMode) {
        if (client.player == null || interactionManager == null || handler == null || gridSlots == null) {
            return;
        }

        int[] actualSlots = mapGridSlotsForHandler(handler, craftMode, gridSlots);

        for (int slotIndex : actualSlots) {
            try {
                Slot slot = handler.getSlot(slotIndex);
                if (slot != null && slot.hasItem()) {
                    interactionManager.handleInventoryMouseClick(handler.containerId, slotIndex, 0, ClickType.QUICK_MOVE, client.player);
                }
            } catch (IndexOutOfBoundsException ignored) {
                // Ignore missing grid slots for the current handler.
            }
        }
    }

    static List<Integer> planIngredientSourceSlotsForTests(List<ItemStack> inventoryStacks,
                                                           List<Ingredient> ingredients,
                                                           Object registryManager) {
        if (inventoryStacks == null || ingredients == null) {
            return null;
        }

        List<ItemStack> simulatedInventory = new ArrayList<>(inventoryStacks.size());
        for (ItemStack stack : inventoryStacks) {
            simulatedInventory.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }

        List<Integer> plannedSlots = new ArrayList<>(ingredients.size());
        for (Ingredient ingredient : ingredients) {
            int inventorySlot = findIngredientInventorySlot(simulatedInventory, ingredient, registryManager, false);
            if (inventorySlot == -1) {
                return null;
            }

            simulatedInventory.get(inventorySlot).shrink(1);
            plannedSlots.add(inventorySlot);
        }
        return plannedSlots;
    }

    static List<Integer> planIngredientSourceSlotsForTests(List<TestIngredientStack> inventoryStacks,
                                                           List<String> ingredientKeys) {
        if (inventoryStacks == null || ingredientKeys == null) {
            return null;
        }

        List<TestIngredientStack> simulatedInventory = new ArrayList<>(inventoryStacks.size());
        for (TestIngredientStack stack : inventoryStacks) {
            simulatedInventory.add(stack == null ? new TestIngredientStack("", 0) : stack);
        }

        List<Integer> plannedSlots = new ArrayList<>(ingredientKeys.size());
        for (String ingredientKey : ingredientKeys) {
            int inventorySlot = -1;
            for (int slotIdx = 0; slotIdx < simulatedInventory.size(); slotIdx++) {
                TestIngredientStack stack = simulatedInventory.get(slotIdx);
                if (stack == null || stack.count() <= 0) {
                    continue;
                }
                if (Objects.equals(stack.key(), ingredientKey)) {
                    inventorySlot = slotIdx;
                    simulatedInventory.set(slotIdx, new TestIngredientStack(stack.key(), stack.count() - 1));
                    break;
                }
            }
            if (inventorySlot == -1) {
                return null;
            }
            plannedSlots.add(inventorySlot);
        }
        return plannedSlots;
    }

    private int findIngredientSourceSlot(AbstractContainerMenu handler,
                                         Ingredient ingredient,
                                         Object registryManager,
                                         boolean allowEmpty) {
        if (handler == null || ingredient == null) {
            return -1;
        }
        if (!allowEmpty && RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
            return -1;
        }

        List<ItemStack> candidates = RecipeCompatibilityBridge.getIngredientStacks(ingredient, registryManager);
        List<Slot> slots = handler.slots;
        for (int slotIdx = 0; slotIdx < slots.size(); slotIdx++) {
            Slot slot = slots.get(slotIdx);
            if (!(slot.container instanceof Inventory)) {
                continue;
            }

            int inventoryIndex = slot.getContainerSlot();
            if (inventoryIndex < 0 || inventoryIndex >= Inventory.INVENTORY_SIZE) {
                continue;
            }

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }

            if (ingredient.test(stack) || matchesCandidateStack(candidates, stack)) {
                return slotIdx;
            }
        }

        return -1;
    }

    private static int findIngredientInventorySlot(List<ItemStack> inventoryStacks,
                                                   Ingredient ingredient,
                                                   Object registryManager,
                                                   boolean allowEmpty) {
        if (inventoryStacks == null || ingredient == null) {
            return -1;
        }
        if (!allowEmpty && RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
            return -1;
        }

        List<ItemStack> candidates = RecipeCompatibilityBridge.getIngredientStacks(ingredient, registryManager);
        for (int slotIdx = 0; slotIdx < inventoryStacks.size(); slotIdx++) {
            ItemStack stack = inventoryStacks.get(slotIdx);
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            if (ingredient.test(stack) || matchesCandidateStack(candidates, stack)) {
                return slotIdx;
            }
        }

        return -1;
    }

    private static boolean matchesCandidateStack(List<ItemStack> candidates, ItemStack stack) {
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (ItemStack candidate : candidates) {
            if (candidate != null && candidate.getItem() == stack.getItem()) {
                return true;
            }
        }
        return false;
    }

    boolean canSatisfyGridIngredients(AbstractContainerMenu handler,
                                              List<GridIngredient> gridIngredients,
                                              Object registryManager) {
        if (handler == null || gridIngredients == null || gridIngredients.isEmpty()) {
            return false;
        }
        List<Integer> plannedSourceSlots = planIngredientSourceSlots(handler, gridIngredients, registryManager);
        return plannedSourceSlots != null && plannedSourceSlots.size() == gridIngredients.size();
    }

    private String describeCraftingGridIngredients(List<GridIngredient> gridIngredients) {
        if (gridIngredients == null || gridIngredients.isEmpty()) {
            return "[]";
        }

        List<String> parts = new ArrayList<>(gridIngredients.size());
        for (GridIngredient ingredient : gridIngredients) {
            if (ingredient == null) {
                parts.add("null");
                continue;
            }

            List<ItemStack> candidates = RecipeCompatibilityBridge.getIngredientStacks(ingredient.ingredient(), null);
            String candidateDescription = "empty";
            if (candidates != null && !candidates.isEmpty()) {
                List<String> ids = new ArrayList<>();
                for (ItemStack candidate : candidates) {
                    if (candidate == null || candidate.isEmpty()) {
                        continue;
                    }
                    ids.add(String.valueOf(BuiltInRegistries.ITEM.getKey(candidate.getItem())));
                }
                if (!ids.isEmpty()) {
                    candidateDescription = String.join("|", ids);
                }
            }

            parts.add(ingredient.slotIndex() + ":" + candidateDescription);
        }
        return parts.toString();
    }

    private int[] mapGridSlotsForHandler(AbstractContainerMenu handler, NodeMode craftMode, int[] logicalSlots) {
        if (handler == null || logicalSlots == null) {
            return new int[0];
        }
        int[] mapped = new int[logicalSlots.length];
        int count = 0;
        for (int logical : logicalSlots) {
            int actual = mapLogicalSlotToHandlerSlot(handler, craftMode, logical);
            if (actual >= 0) {
                mapped[count++] = actual;
            }
        }
        if (count == mapped.length) {
            return mapped;
        }
        int[] compact = new int[count];
        for (int i = 0; i < count; i++) {
            compact[i] = mapped[i];
        }
        return compact;
    }

    static record TestIngredientStack(String key, int count) {
    }

    private record IngredientReservation(int handlerSlot) {
    }

    private int mapLogicalSlotToHandlerSlot(AbstractContainerMenu handler, NodeMode craftMode, int logicalSlot) {
        if (handler == null) {
            return -1;
        }

        if (craftMode == NodeMode.CRAFT_PLAYER_GUI && handler instanceof CraftingMenu) {
            return switch (logicalSlot) {
                case 1 -> 1;
                case 2 -> 2;
                case 3 -> 4;
                case 4 -> 5;
                default -> -1;
            };
        }

        return logicalSlot;
    }

    int mapPlayerInventorySlot(AbstractContainerMenu handler, int inventorySlot) {
        if (handler == null) {
            return -1;
        }
        List<Slot> slots = handler.slots;
        for (int slotIdx = 0; slotIdx < slots.size(); slotIdx++) {
            Slot slot = slots.get(slotIdx);
            if (slot.container instanceof Inventory && slot.getContainerSlot() == inventorySlot) {
                return slotIdx;
            }
        }
        return -1;
    }

    List<GridIngredient> resolveGridIngredients(CraftingRecipe recipe, NodeMode craftMode, Object registryManager) {
        List<GridIngredient> result = new ArrayList<>();
        if (recipe == null) {
            return result;
        }

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            if (craftMode == NodeMode.CRAFT_PLAYER_GUI) {
                return resolvePlayerGridIngredients(shapedRecipe, registryManager);
            }
            return resolveCraftingTableGridIngredients(shapedRecipe, registryManager);
        }

        Object placement = RecipeCompatibilityBridge.getIngredientPlacement(recipe);
        if (placement == null) {
            return resolveFallbackGridIngredients(recipe, craftMode, registryManager);
        }

        List<Ingredient> ingredients = RecipeCompatibilityBridge.getPlacementIngredients(placement);
        if (ingredients == null || ingredients.isEmpty()) {
            return resolveFallbackGridIngredients(recipe, craftMode, registryManager);
        }

        IntList slots = RecipeCompatibilityBridge.toPlacementSlots(placement);
        int gridLimit = craftMode == NodeMode.CRAFT_CRAFTING_TABLE ? 9 : 4;

        if (RecipeCompatibilityBridge.hasNoPlacement(placement) || slots == null || slots.isEmpty()) {
            int limit = Math.min(ingredients.size(), gridLimit);
            for (int i = 0; i < limit; i++) {
                Ingredient ingredient = ingredients.get(i);
                if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                    continue;
                }
                result.add(new GridIngredient(1 + i, ingredient, false));
            }
            if (result.isEmpty()) {
                logEmptyPlacementIngredients(ingredients, registryManager);
            }
            return result;
        }

        int limit = Math.min(ingredients.size(), slots.size());
        for (int i = 0; i < limit; i++) {
            Ingredient ingredient = ingredients.get(i);
            if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                continue;
            }

            int logicalSlot = slots.getInt(i);
            int resolvedSlot;
            if (craftMode == NodeMode.CRAFT_PLAYER_GUI) {
                int localX = logicalSlot % 3;
                int localY = logicalSlot / 3;

                if (localX >= 2 || localY >= 2) {
                    continue;
                }

                resolvedSlot = 1 + localX + (localY * 2);
            } else {
                resolvedSlot = 1 + logicalSlot;
            }

            if (resolvedSlot > gridLimit) {
                continue;
            }

            result.add(new GridIngredient(resolvedSlot, ingredient, false));
    }

        if (result.isEmpty()) {
            logEmptyPlacementIngredients(ingredients, registryManager);
        }
        return result;
    }

    private List<GridIngredient> resolveFallbackGridIngredients(CraftingRecipe recipe, NodeMode craftMode, Object registryManager) {
        List<GridIngredient> result = new ArrayList<>();
        if (recipe == null) {
            return result;
        }
        List<?> ingredients = RecipeCompatibilityBridge.getRecipeIngredients(recipe);
        if (ingredients == null || ingredients.isEmpty()) {
            ingredients = readRecipeIngredients(recipe);
        }
        if (ingredients == null || ingredients.isEmpty()) {
            logMissingRecipeIngredients(recipe);
            return result;
        }
        int gridLimit = craftMode == NodeMode.CRAFT_CRAFTING_TABLE ? 9 : 4;

        List<Ingredient> displayIngredients = RecipeCompatibilityBridge.extractDisplayIngredients(ingredients, registryManager);
        if (!displayIngredients.isEmpty()) {
            int limit = Math.min(displayIngredients.size(), gridLimit);
            for (int i = 0; i < limit; i++) {
                Ingredient ingredient = displayIngredients.get(i);
                if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                    continue;
                }
                result.add(new GridIngredient(1 + i, ingredient, false));
            }
            if (result.isEmpty()) {
                logEmptyPlacementIngredients(displayIngredients, registryManager);
            }
            return result;
        }
        if (ingredients.size() == 1) {
            Object entry = ingredients.get(0);
            List<?> slots = RecipeCompatibilityBridge.getDisplayIngredientSlots(entry);
            if (slots != null && !slots.isEmpty()) {
                List<Ingredient> slotIngredients = new ArrayList<>();
                for (Object slot : slots) {
                    Ingredient ingredient = RecipeCompatibilityBridge.extractDisplayIngredient(slot, registryManager);
                    if (!RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                        slotIngredients.add(ingredient);
                    }
                }
                if (!slotIngredients.isEmpty()) {
                    int limit = Math.min(slotIngredients.size(), gridLimit);
                    for (int i = 0; i < limit; i++) {
                        Ingredient ingredient = slotIngredients.get(i);
                        if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                            continue;
                        }
                        result.add(new GridIngredient(1 + i, ingredient, false));
                    }
                    if (result.isEmpty()) {
                        logEmptyPlacementIngredients(slotIngredients, registryManager);
                    }
                    return result;
                }
            }
        }

        int limit = Math.min(ingredients.size(), gridLimit);
        boolean loggedUnknown = false;
        boolean loggedSummary = false;
        int loggedEmpty = 0;
        for (int i = 0; i < limit; i++) {
            Object entry = ingredients.get(i);
            Ingredient ingredient = unwrapRecipeIngredient(entry);
            if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                if (!loggedSummary) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(
                            "Pathmind craft debug: ingredient list type={} size={}",
                            ingredients.getClass().getName(),
                            ingredients.size()
                        );
                    }
                    loggedSummary = true;
                }
                if (loggedEmpty < 3) {
                    int matches = RecipeCompatibilityBridge.getIngredientStacks(ingredient, registryManager).size();
                    String entryType = entry != null ? entry.getClass().getName() : "null";
                    String ingredientType = ingredient != null ? ingredient.getClass().getName() : "null";
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(
                            "Pathmind craft debug: empty ingredient entryType={} ingredientType={} matches={}",
                            entryType,
                            ingredientType,
                            matches
                        );
                    }
                    loggedEmpty++;
                }
                if (!loggedUnknown && ingredient == null && entry != null) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(
                            "Pathmind craft debug: unresolved ingredient entry type={}",
                            entry.getClass().getName()
                        );
                    }
                    logUnresolvedIngredientEntry(entry);
                    loggedUnknown = true;
                }
                continue;
            }
            result.add(new GridIngredient(1 + i, ingredient, false));
        }
        return result;
    }

    private List<?> readRecipeIngredients(CraftingRecipe recipe) {
        if (recipe == null) {
            return Collections.emptyList();
        }
        List<?> bestList = null;
        int bestScore = -1;
        int bestSize = 0;
        for (java.lang.reflect.Method method : getAllMethods(recipe.getClass())) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            try {
                Class<?> returnType = method.getReturnType();
                Object result = method.invoke(recipe);
                if (result instanceof Ingredient[] ingredientArray) {
                    return java.util.Arrays.asList(ingredientArray);
                }
                if (result instanceof Object[] objectArray) {
                    return java.util.Arrays.asList(objectArray);
                }
                if (!java.util.List.class.isAssignableFrom(returnType)) {
                    continue;
                }
                if (result instanceof List<?> list) {
                    int score = 0;
                    for (Object entry : list) {
                        Ingredient ingredient = unwrapRecipeIngredient(entry);
                        if (!RecipeCompatibilityBridge.isIngredientEmpty(ingredient)) {
                            score++;
                        }
                    }
                    int size = list.size();
                    if (score > bestScore || (score == bestScore && size > bestSize)) {
                        bestScore = score;
                        bestSize = size;
                        bestList = list;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Ignore and continue scanning other methods.
            }
        }
        if (bestList != null) {
            return bestList;
        }
        return Collections.emptyList();
    }

    private void logMissingRecipeIngredients(CraftingRecipe recipe) {
        if (recipe == null || !LOGGER.isDebugEnabled()) {
            return;
        }
        try {
            StringBuilder builder = new StringBuilder();
            builder.append("Pathmind craft debug: missing ingredients for recipeClass=")
                .append(recipe.getClass().getName());
            int logged = 0;
            for (java.lang.reflect.Method method : getAllMethods(recipe.getClass())) {
                if (method.getParameterCount() != 0) {
                    continue;
                }
                Class<?> returnType = method.getReturnType();
                if (!java.util.List.class.isAssignableFrom(returnType)
                    && !returnType.isArray()) {
                    continue;
                }
                Object result = null;
                try {
                    result = method.invoke(recipe);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    continue;
                }
                if (result == null) {
                    continue;
                }
                if (logged < 6) {
                    builder.append(" method=")
                        .append(method.getName())
                        .append(" type=")
                        .append(result.getClass().getName())
                        .append(" size=")
                        .append(result instanceof java.util.List<?> list ? list.size() : java.lang.reflect.Array.getLength(result));
                    logged++;
                }
            }
            LOGGER.debug(builder.toString());
        } catch (RuntimeException ignored) {
            // Avoid breaking crafting flow on debug failure.
        }
    }

    private void logEmptyPlacementIngredients(List<Ingredient> ingredients, Object registryManager) {
        if (ingredients == null || ingredients.isEmpty() || !LOGGER.isDebugEnabled()) {
            return;
        }
        LOGGER.debug(
            "Pathmind craft debug: placement ingredients all empty size={} listType={}",
            ingredients.size(),
            ingredients.getClass().getName()
        );
        int logged = 0;
        for (Ingredient ingredient : ingredients) {
            if (logged >= 3) {
                break;
            }
            int matches = RecipeCompatibilityBridge.getIngredientStacks(ingredient, registryManager).size();
            String ingredientType = ingredient != null ? ingredient.getClass().getName() : "null";
            LOGGER.debug(
                "Pathmind craft debug: placement ingredient type={} matches={}",
                ingredientType,
                matches
            );
            logged++;
        }
    }

    private void logIngredientListIfEmpty(String source, List<?> ingredients, Object registryManager) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        if (ingredients == null || ingredients.isEmpty()) {
            LOGGER.debug("Pathmind craft debug: {} ingredient list empty", source);
            return;
        }
        LOGGER.debug(
            "Pathmind craft debug: {} ingredient list type={} size={}",
            source,
            ingredients.getClass().getName(),
            ingredients.size()
        );
        int logged = 0;
        for (Object entry : ingredients) {
            if (logged >= 3) {
                break;
            }
            Ingredient ingredient = unwrapRecipeIngredient(entry);
            int matches = RecipeCompatibilityBridge.getIngredientStacks(ingredient, registryManager).size();
            String entryType = entry != null ? entry.getClass().getName() : "null";
            String ingredientType = ingredient != null ? ingredient.getClass().getName() : "null";
            LOGGER.debug(
                "Pathmind craft debug: {} entryType={} ingredientType={} matches={}",
                source,
                entryType,
                ingredientType,
                matches
            );
            if (ingredient == null && entry != null) {
                logUnresolvedIngredientEntry(entry);
            }
            logged++;
        }
    }

    private void logUnresolvedIngredientEntry(Object entry) {
        if (entry == null || !LOGGER.isDebugEnabled()) {
            return;
        }
        try {
            StringBuilder builder = new StringBuilder();
            Class<?> entryClass = entry.getClass();
            builder.append("Pathmind craft debug: unresolved entry details class=")
                .append(entryClass.getName());
            if (entryClass.isRecord()) {
                builder.append(" recordComponents=");
                java.lang.reflect.RecordComponent[] components = entryClass.getRecordComponents();
                for (int i = 0; i < Math.min(components.length, 4); i++) {
                    java.lang.reflect.RecordComponent component = components[i];
                    builder.append(component.getName()).append(":").append(component.getType().getName()).append(" ");
                }
            }
            int loggedMethods = 0;
            for (java.lang.reflect.Method method : entryClass.getMethods()) {
                if (method.getParameterCount() != 0) {
                    continue;
                }
                if (loggedMethods >= 6) {
                    break;
                }
                builder.append(" method=").append(method.getName())
                    .append("->").append(method.getReturnType().getName());
                loggedMethods++;
            }
            int loggedFields = 0;
            for (java.lang.reflect.Field field : entryClass.getDeclaredFields()) {
                if (loggedFields >= 4) {
                    break;
                }
                builder.append(" field=").append(field.getName())
                    .append(":").append(field.getType().getName());
                loggedFields++;
            }
            LOGGER.debug(builder.toString());
        } catch (RuntimeException ignored) {
            // Avoid breaking crafting flow on debug failure.
        }
    }

    private List<GridIngredient> resolvePlayerGridIngredients(ShapedRecipe recipe, Object registryManager) {
        List<GridIngredient> result = new ArrayList<>();
        List<?> ingredients = recipe.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            logIngredientListIfEmpty("playerGrid", ingredients, registryManager);
            return result;
        }

        int width = Math.min(recipe.getWidth(), 2);
        int height = Math.min(recipe.getHeight(), 2);
        int recipeWidth = Math.max(recipe.getWidth(), 1);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = x + (y * recipeWidth);
                if (index < 0 || index >= ingredients.size()) {
                    continue;
                }

                Ingredient ingredient = unwrapRecipeIngredient(ingredients.get(index));
                int slotIndex = 1 + x + (y * 2);
                if (ingredient == null || RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                    continue;
                }
                result.add(new GridIngredient(slotIndex, ingredient, false));
            }
        }

        if (result.isEmpty()) {
            logIngredientListIfEmpty("playerGrid", ingredients, registryManager);
        }
        return result;
    }

    private List<GridIngredient> resolveCraftingTableGridIngredients(ShapedRecipe recipe, Object registryManager) {
        List<GridIngredient> result = new ArrayList<>();
        List<?> ingredients = recipe.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            logIngredientListIfEmpty("craftingTableGrid", ingredients, registryManager);
            return result;
        }

        int width = Math.min(recipe.getWidth(), 3);
        int height = Math.min(recipe.getHeight(), 3);
        int recipeWidth = Math.max(recipe.getWidth(), 1);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = x + (y * recipeWidth);
                if (index < 0 || index >= ingredients.size()) {
                    continue;
                }

                Ingredient ingredient = unwrapRecipeIngredient(ingredients.get(index));
                int slotIndex = 1 + x + (y * 3);
                if (ingredient == null || RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                    continue;
                }
                result.add(new GridIngredient(slotIndex, ingredient, false));
            }
        }

        if (result.isEmpty()) {
            logIngredientListIfEmpty("craftingTableGrid", ingredients, registryManager);
        }
        return result;
    }

    private Ingredient unwrapRecipeIngredient(Object entry) {
        if (entry instanceof Ingredient ingredientValue) {
            return ingredientValue;
        }
        if (entry instanceof Item item) {
            return Ingredient.of(item);
        }
        if (entry instanceof ItemStack stack && !stack.isEmpty()) {
            return Ingredient.of(stack.getItem());
        }
        if (entry instanceof Holder<?> registryEntry) {
            Object value = registryEntry.value();
            if (value instanceof Ingredient registryIngredient) {
                return registryIngredient;
            }
            if (value instanceof Item item) {
                return Ingredient.of(item);
            }
        }
        Ingredient candidate = RecipeCompatibilityBridge.tryCreateIngredientFromEntry(entry);
        if (candidate != null) {
            return candidate;
        }
        if (entry instanceof Optional<?> optional) {
            Object value = optional.orElse(null);
            if (value != null) {
                return unwrapRecipeIngredient(value);
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

    private Ingredient resolveIngredientFromEntry(Object entry, String methodName) {
        try {
            java.lang.reflect.Method method = entry.getClass().getMethod(methodName);
            if (Ingredient.class.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                Object value = method.invoke(entry);
                return value instanceof Ingredient ingredient ? ingredient : null;
            }
        } catch (NoSuchMethodException ignored) {
            // Try declared methods/fields next.
        } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException ignored) {
            return null;
        }
        try {
            java.lang.reflect.Method method = entry.getClass().getDeclaredMethod(methodName);
            if (!Ingredient.class.isAssignableFrom(method.getReturnType())) {
                return null;
            }
            method.setAccessible(true);
            Object value = method.invoke(entry);
            return value instanceof Ingredient ingredient ? ingredient : null;
        } catch (NoSuchMethodException ignored) {
            // Try fields next.
        } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException ignored) {
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

    int[] getCraftingGridSlots(NodeMode craftMode) {
        if (craftMode == NodeMode.CRAFT_CRAFTING_TABLE) {
            return new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9};
        }
        return new int[] {1, 2, 3, 4};
    }

        int normalizeCachedRecipeSlotIndex(int slotIndex, boolean legacyZeroBasedSlots) {
        if (!legacyZeroBasedSlots) {
            return slotIndex;
        }
        return slotIndex + 1;
    }

    static List<Integer> normalizeCachedRecipeSlotIndexesForTests(List<Integer> slotIndexes) {
        if (slotIndexes == null || slotIndexes.isEmpty()) {
            return List.of();
        }
        boolean legacyZeroBasedSlots = false;
        for (Integer slotIndex : slotIndexes) {
            if (slotIndex != null && slotIndex.intValue() == 0) {
                legacyZeroBasedSlots = true;
                break;
            }
        }
        List<Integer> normalized = new ArrayList<>(slotIndexes.size());
        for (Integer slotIndex : slotIndexes) {
            if (slotIndex == null) {
                continue;
            }
            normalized.add(legacyZeroBasedSlots ? slotIndex.intValue() + 1 : slotIndex.intValue());
        }
        return normalized;
    }

    String getCachedRecipeSignature(CachedRecipe recipe) {
        if (recipe == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (recipe.grid != null) {
            for (CachedGridIngredient ingredient : recipe.grid) {
                if (ingredient == null) {
                    continue;
                }
                List<String> ids = ingredient.itemIds != null ? new ArrayList<>(ingredient.itemIds) : List.of();
                parts.add(ingredient.slotIndex + "=" + String.join("|", ids));
            }
        }
        Collections.sort(parts);
        return recipe.mode + ":" + recipe.outputCount + ":" + String.join(",", parts);
    }

    List<ItemStack> resolveIngredientStacksByTesting(Ingredient ingredient) {
        List<ItemStack> stacks = new ArrayList<>();
        if (ingredient == null) {
            return stacks;
        }
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null || item == Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            try {
                if (ingredient.test(stack)) {
                    stacks.add(stack);
                }
            } catch (RuntimeException ignored) {
                // Skip items that trip custom ingredient checks.
            }
        }
        return stacks;
    }

    Ingredient buildIngredientFromItemIds(List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return null;
        }
        List<Item> items = new ArrayList<>();
        for (String idString : itemIds) {
            Identifier id = Identifier.tryParse(idString);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            items.add(BuiltInRegistries.ITEM.getOptional(id).orElse(null));
        }
        if (items.isEmpty()) {
            return null;
        }
        return Ingredient.of(items.toArray(new Item[0]));
    }

    CachedRecipeBook loadRecipeCache(net.minecraft.client.Minecraft client) {
        synchronized (RECIPE_CACHE_LOCK) {
            if (cachedRecipeBook != null) {
                return cachedRecipeBook;
            }
            Path path = getRecipeCachePath(client);
            if (path == null || !Files.exists(path)) {
                cachedRecipeBook = new CachedRecipeBook();
                return cachedRecipeBook;
            }
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                CachedRecipeBook loaded = RECIPE_CACHE_GSON.fromJson(json, CachedRecipeBook.class);
                if (loaded == null || loaded.schemaVersion != RECIPE_CACHE_VERSION) {
                    cachedRecipeBook = new CachedRecipeBook();
                    return cachedRecipeBook;
                }
                if (loaded.recipesByOutput == null) {
                    loaded.recipesByOutput = new HashMap<>();
                }
                cachedRecipeBook = loaded;
                return cachedRecipeBook;
            } catch (Exception e) {
                cachedRecipeBook = new CachedRecipeBook();
                return cachedRecipeBook;
            }
        }
    }

    void saveRecipeCache(net.minecraft.client.Minecraft client, CachedRecipeBook book) {
        if (client == null || book == null) {
            return;
        }
        synchronized (RECIPE_CACHE_LOCK) {
            Path path = getRecipeCachePath(client);
            if (path == null) {
                return;
            }
            try {
                boolean existed = Files.exists(path);
                Path parent = path.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
                book.schemaVersion = RECIPE_CACHE_VERSION;
                try {
                    book.gameVersion = client.getLaunchedVersion();
                } catch (RuntimeException ignored) {
                    book.gameVersion = null;
                }
                String json = RECIPE_CACHE_GSON.toJson(book);
                Files.writeString(path, json, StandardCharsets.UTF_8);
                if (!existed) {
                    LOGGER.debug("Pathmind recipe cache created at {}", path.toAbsolutePath());
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static Path getRecipeCachePath(net.minecraft.client.Minecraft client) {
        Path base = getPathmindDirectory(client);
        if (base == null) {
            return null;
        }
        return base.resolve(RECIPE_CACHE_FILE_NAME);
    }

    private static Path getPathmindDirectory(net.minecraft.client.Minecraft client) {
        Path minecraftDirectory = null;
        if (client != null && client.gameDirectory != null) {
            minecraftDirectory = client.gameDirectory.toPath();
        } else {
            try {
                minecraftDirectory = LoaderMetadata.getGameFolder();
            } catch (Throwable ignored) {
                // Unit tests run without a platform; fall through to the default .minecraft path.
            }
        }
        if (minecraftDirectory == null) {
            minecraftDirectory = Paths.get(System.getProperty("user.home"), ".minecraft");
        }
        return minecraftDirectory.resolve("pathmind");
    }

    List<RecipeHolder<?>> getRecipeEntries(Object manager) {
        List<RecipeHolder<?>> entries = new ArrayList<>();
        if (manager == null) {
            return entries;
        }

        List<String> preferredNames = List.of("values", "getRecipes", "getAllRecipes", "getAll");
        for (String name : preferredNames) {
            try {
                java.lang.reflect.Method method = manager.getClass().getMethod(name);
                method.setAccessible(true);
                Object result = method.invoke(manager);
                if (collectRecipeEntries(result, entries)) {
                    return entries;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next candidate.
            }
        }

        for (java.lang.reflect.Method method : manager.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (!Iterable.class.isAssignableFrom(returnType) && !java.util.Map.class.isAssignableFrom(returnType)) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object result = method.invoke(manager);
                if (collectRecipeEntries(result, entries)) {
                    return entries;
                }
            } catch (ReflectiveOperationException ignored) {
                // Keep scanning.
            }
        }

        if (entries.isEmpty()) {
            collectRecipeEntriesFromFields(manager, entries, 0, new java.util.IdentityHashMap<>());
        }
        return entries;
    }

    boolean collectRecipeEntries(Object result, List<RecipeHolder<?>> entries) {
        if (result == null || entries == null) {
            return false;
        }
        if (result instanceof RecipeHolder<?> entry) {
            entries.add(entry);
            return true;
        }
        if (result instanceof Iterable<?> iterable) {
            boolean added = false;
            for (Object item : iterable) {
                if (item instanceof RecipeHolder<?> recipeEntry) {
                    entries.add(recipeEntry);
                    added = true;
                } else if (item instanceof java.util.Map<?, ?> map) {
                    if (collectRecipeEntries(map.values(), entries)) {
                        added = true;
                    }
                } else if (item instanceof Iterable<?> nested) {
                    if (collectRecipeEntries(nested, entries)) {
                        added = true;
                    }
                }
            }
            return added;
        }
        if (result instanceof java.util.Iterator<?> iterator) {
            boolean added = false;
            while (iterator.hasNext()) {
                Object item = iterator.next();
                if (item instanceof RecipeHolder<?> recipeEntry) {
                    entries.add(recipeEntry);
                    added = true;
                } else if (item instanceof java.util.Map<?, ?> map) {
                    if (collectRecipeEntries(map.values(), entries)) {
                        added = true;
                    }
                } else if (item instanceof Iterable<?> nested) {
                    if (collectRecipeEntries(nested, entries)) {
                        added = true;
                    }
                }
            }
            return added;
        }
        if (result instanceof java.util.Map<?, ?> map) {
            return collectRecipeEntries(map.values(), entries);
        }
        // Handle container-like objects with values() or iterator() accessors.
        try {
            java.lang.reflect.Method valuesMethod = result.getClass().getMethod("values");
            if (valuesMethod.getParameterCount() == 0) {
                valuesMethod.setAccessible(true);
                Object values = valuesMethod.invoke(result);
                if (collectRecipeEntries(values, entries)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Ignore missing values() method.
        }
        try {
            java.lang.reflect.Method iteratorMethod = result.getClass().getMethod("iterator");
            if (iteratorMethod.getParameterCount() == 0) {
                iteratorMethod.setAccessible(true);
                Object iter = iteratorMethod.invoke(result);
                if (collectRecipeEntries(iter, entries)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Ignore missing iterator() method.
        }
        return false;
    }

    void collectRecipeEntriesFromFields(Object manager,
                                                List<RecipeHolder<?>> entries,
                                                int depth,
                                                java.util.IdentityHashMap<Object, Boolean> seen) {
        if (manager == null || entries == null) {
            return;
        }
        if (isJdkType(manager.getClass())) {
            return;
        }
        if (depth > 3) {
            return;
        }
        if (seen.put(manager, Boolean.TRUE) != null) {
            return;
        }
        for (java.lang.reflect.Field field : getAllFields(manager.getClass())) {
            try {
                Object accessTarget = java.lang.reflect.Modifier.isStatic(field.getModifiers()) ? null : manager;
                if (!field.canAccess(accessTarget)) {
                    try {
                        field.setAccessible(true);
                    } catch (RuntimeException ignored) {
                        continue;
                    }
                }
                Object value = field.get(accessTarget);
                if (value == null || seen.containsKey(value)) {
                    continue;
                }
                if (isJdkType(value.getClass())) {
                    continue;
                }
                if (collectRecipeEntries(value, entries)) {
                    continue;
                }
                // Dive into nested containers (common in recipe managers).
                if (value instanceof java.util.Map<?, ?> map) {
                    Object crafting = map.get(RecipeType.CRAFTING);
                    if (collectRecipeEntries(crafting, entries)) {
                        continue;
                    }
                    for (Object nested : map.values()) {
                        collectRecipeEntriesFromFields(nested, entries, depth + 1, seen);
                    }
                } else if (value instanceof Iterable<?> iterable) {
                    for (Object nested : iterable) {
                        collectRecipeEntriesFromFields(nested, entries, depth + 1, seen);
                    }
                } else {
                    collectRecipeEntriesFromFields(value, entries, depth + 1, seen);
                }
            } catch (IllegalAccessException ignored) {
                // Skip inaccessible fields.
            }
        }
    }

    private List<java.lang.reflect.Field> getAllFields(Class<?> type) {
        List<java.lang.reflect.Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            if (isJdkType(current)) {
                break;
            }
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private boolean isJdkType(Class<?> type) {
        if (type == null) {
            return true;
        }
        Package pkg = type.getPackage();
        String name = pkg != null ? pkg.getName() : "";
        return name.startsWith("java.")
            || name.startsWith("javax.")
            || name.startsWith("jdk.")
            || name.startsWith("sun.")
            || name.startsWith("com.sun.");
    }

    List<Object> getRecipeManagers(net.minecraft.client.Minecraft client) {
        List<Object> managers = new ArrayList<>();
        if (client == null) {
            return managers;
        }
        MinecraftServer server = client.getSingleplayerServer();
        if (server != null) {
            Object manager = server.getRecipeManager();
            if (manager != null && !managers.contains(manager)) {
                managers.add(manager);
            }
        }
        if (client.getConnection() != null) {
            try {
                java.lang.reflect.Method method = client.getConnection().getClass().getMethod("getRecipeManager");
                method.setAccessible(true);
                Object result = method.invoke(client.getConnection());
                if (result != null && !managers.contains(result)) {
                    managers.add(result);
                }
            } catch (ReflectiveOperationException ignored) {
                // Ignore network handlers without recipe managers.
            }
        }
        if (client.level != null) {
            try {
                Object manager = findRecipeAccess(client.level);
                if (manager != null && !managers.contains(manager)) {
                    managers.add(manager);
                }
            } catch (RuntimeException ignored) {
                // Ignore client worlds without a recipe manager.
            }
        }
        return managers;
    }

    private Object findRecipeAccess(Object level) {
        if (level == null) {
            return null;
        }
        for (String methodName : List.of("recipeAccess", "getRecipeManager")) {
            try {
                java.lang.reflect.Method method = level.getClass().getMethod(methodName);
                method.setAccessible(true);
                Object result = method.invoke(level);
                if (result != null) {
                    return result;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next API-family name.
            }
        }
        return null;
    }

    static class CraftingSummary {
        final int produced;
        final String failureMessage;

        CraftingSummary(int produced, String failureMessage) {
            this.produced = produced;
            this.failureMessage = failureMessage;
        }
    }

    static class CachedRecipeBook {
        int schemaVersion = RECIPE_CACHE_VERSION;
        String gameVersion;
        Map<String, List<CachedRecipe>> recipesByOutput = new HashMap<>();
    }

    static class CachedRecipe {
        String mode;
        int outputCount;
        List<CachedGridIngredient> grid = new ArrayList<>();
    }

    static class CachedGridIngredient {
        int slotIndex;
        List<String> itemIds = new ArrayList<>();
    }

    private static class RecipeCacheWarmupState {
        private final Path cachePath;
        private final CachedRecipeBook book;
        private final Object registryManager;
        private final Object serverRegistryManager;
        private final List<RecipeHolder<?>> craftingEntries;
        private final List<RecipeCollection> recipeCollections;
        private final int totalDisplayEntries;
        private int recipeIndex;
        private int collectionIndex;
        private int displayIndex;
        private boolean dirty;
        private int unsavedChanges;

        RecipeCacheWarmupState(Path cachePath,
                               CachedRecipeBook book,
                               Object registryManager,
                               Object serverRegistryManager,
                               List<RecipeHolder<?>> craftingEntries,
                               List<RecipeCollection> recipeCollections,
                               int totalDisplayEntries) {
            this.cachePath = cachePath;
            this.book = book;
            this.registryManager = registryManager;
            this.serverRegistryManager = serverRegistryManager;
            this.craftingEntries = craftingEntries != null ? craftingEntries : List.of();
            this.recipeCollections = recipeCollections != null ? recipeCollections : List.of();
            this.totalDisplayEntries = Math.max(0, totalDisplayEntries);
        }

        boolean matches(net.minecraft.client.Minecraft client) {
            return Objects.equals(cachePath, getRecipeCachePath(client));
        }

        int getCompletedUnits() {
            return Math.min(getTotalUnits(), recipeIndex + getCompletedDisplayEntries());
        }

        int getTotalUnits() {
            return craftingEntries.size() + totalDisplayEntries;
        }

        private int getCompletedDisplayEntries() {
            int completed = 0;
            for (int i = 0; i < collectionIndex && i < recipeCollections.size(); i++) {
                RecipeCollection collection = recipeCollections.get(i);
                List<?> entries = collection != null ? RecipeCompatibilityBridge.getAllRecipesFromCollection(collection) : null;
                completed += entries != null ? entries.size() : 0;
            }
            completed += displayIndex;
            return Math.min(completed, totalDisplayEntries);
        }
    }

    public record RecipeCacheWarmupProgress(int completed, int total) {
        public float fraction() {
            if (total <= 0) {
                return 0.0f;
            }
            return Math.max(0.0f, Math.min(1.0f, completed / (float) total));
        }
    }

    public static boolean warmRecipeCache(net.minecraft.client.Minecraft client) {
        if (client == null || client.getSingleplayerServer() == null || !recipeCacheWarmupRequested) {
            return false;
        }
        return new NodeCraftCommandExecutor(new Node(NodeType.CRAFT, 0, 0)).warmRecipeCacheInternal(client);
    }

    public static boolean requestRecipeCacheWarmup(net.minecraft.client.Minecraft client) {
        if (client == null || client.getSingleplayerServer() == null) {
            return false;
        }
        synchronized (RECIPE_CACHE_LOCK) {
            recipeCacheWarmupState = null;
            recipeCacheWarmupRequested = true;
        }
        return true;
    }

    public static boolean isRecipeCacheWarmupRequested() {
        return recipeCacheWarmupRequested;
    }

    public static boolean hasUsableRecipeCache(net.minecraft.client.Minecraft client) {
        if (client == null) {
            return false;
        }
        return new NodeCraftCommandExecutor(new Node(NodeType.CRAFT, 0, 0)).hasUsableRecipeCacheInternal(client);
    }

    public static void resetRecipeCacheWarmup() {
        synchronized (RECIPE_CACHE_LOCK) {
            cachedRecipeBook = null;
            recipeCacheWarmupState = null;
            recipeCacheWarmupRequested = false;
        }
    }

    public static boolean clearRecipeCache(net.minecraft.client.Minecraft client) {
        synchronized (RECIPE_CACHE_LOCK) {
            cachedRecipeBook = null;
            recipeCacheWarmupState = null;
            recipeCacheWarmupRequested = false;

            Path path = getRecipeCachePath(client);
            if (path == null) {
                return false;
            }

            try {
                return Files.deleteIfExists(path);
            } catch (IOException e) {
                LOGGER.warn("Failed to clear Pathmind cache file at {}", path.toAbsolutePath(), e);
                return false;
            }
        }
    }

    public static boolean isRecipeCacheWarmupInProgress(net.minecraft.client.Minecraft client) {
        RecipeCacheWarmupState state = recipeCacheWarmupState;
        return state != null && state.matches(client);
    }

    public static RecipeCacheWarmupProgress getRecipeCacheWarmupProgress(net.minecraft.client.Minecraft client) {
        RecipeCacheWarmupState state = recipeCacheWarmupState;
        if (state == null || !state.matches(client)) {
            return null;
        }
        int total = state.getTotalUnits();
        if (total <= 0) {
            return null;
        }
        return new RecipeCacheWarmupProgress(state.getCompletedUnits(), total);
    }

    private boolean warmRecipeCacheInternal(net.minecraft.client.Minecraft client) {
        if (client == null || client.getSingleplayerServer() == null) {
            return false;
        }
        RecipeCacheWarmupState state = recipeCacheWarmupState;
        if (state == null || !state.matches(client)) {
            state = createRecipeCacheWarmupState(client);
            recipeCacheWarmupState = state;
        }
        if (state == null) {
            recipeCacheWarmupRequested = false;
            return false;
        }

        int recipesProcessed = 0;
        while (recipesProcessed < RECIPE_WARMUP_RECIPE_BATCH_SIZE && state.recipeIndex < state.craftingEntries.size()) {
            RecipeHolder<?> entry = state.craftingEntries.get(state.recipeIndex++);
            processWarmupRecipeEntry(state, entry);
            recipesProcessed++;
        }

        int displaysProcessed = 0;
        while (displaysProcessed < RECIPE_WARMUP_DISPLAY_BATCH_SIZE && state.collectionIndex < state.recipeCollections.size()) {
            RecipeCollection collection = state.recipeCollections.get(state.collectionIndex);
            List<?> entries = collection != null ? RecipeCompatibilityBridge.getAllRecipesFromCollection(collection) : null;
            if (entries == null || entries.isEmpty() || state.displayIndex >= entries.size()) {
                state.collectionIndex++;
                state.displayIndex = 0;
                continue;
            }
            Object entry = entries.get(state.displayIndex++);
            processWarmupDisplayEntry(state, entry);
            displaysProcessed++;
        }

        if (state.dirty && state.unsavedChanges >= RECIPE_WARMUP_SAVE_INTERVAL) {
            saveRecipeCache(client, state.book);
            state.unsavedChanges = 0;
            state.dirty = false;
        }

        if (state.recipeIndex < state.craftingEntries.size() || state.collectionIndex < state.recipeCollections.size()) {
            return false;
        }

        if (state.dirty || state.unsavedChanges > 0) {
            saveRecipeCache(client, state.book);
            state.unsavedChanges = 0;
            state.dirty = false;
        }
        recipeCacheWarmupState = null;
        recipeCacheWarmupRequested = false;
        return state.book.recipesByOutput != null && !state.book.recipesByOutput.isEmpty();
    }

    private boolean hasUsableRecipeCacheInternal(net.minecraft.client.Minecraft client) {
        Path path = getRecipeCachePath(client);
        if (path == null || !Files.exists(path)) {
            return false;
        }
        CachedRecipeBook book = loadRecipeCache(client);
        return isRecipeCacheUsable(book);
    }

    static boolean isRecipeCacheUsableForTests(Map<String, List<Map<String, Object>>> rawRecipesByOutput) {
        CachedRecipeBook book = new CachedRecipeBook();
        book.recipesByOutput = new HashMap<>();
        if (rawRecipesByOutput != null) {
            for (Map.Entry<String, List<Map<String, Object>>> entry : rawRecipesByOutput.entrySet()) {
                List<CachedRecipe> recipes = new ArrayList<>();
                if (entry.getValue() != null) {
                    for (Map<String, Object> rawRecipe : entry.getValue()) {
                        if (rawRecipe == null) {
                            continue;
                        }
                        CachedRecipe recipe = new CachedRecipe();
                        Object mode = rawRecipe.get("mode");
                        if (mode instanceof String modeString) {
                            recipe.mode = modeString;
                        }
                        Object outputCount = rawRecipe.get("outputCount");
                        if (outputCount instanceof Number number) {
                            recipe.outputCount = number.intValue();
                        }
                        Object rawGrid = rawRecipe.get("grid");
                        if (rawGrid instanceof List<?> gridList) {
                            for (Object rawIngredient : gridList) {
                                if (!(rawIngredient instanceof Map<?, ?> ingredientMap)) {
                                    continue;
                                }
                                CachedGridIngredient ingredient = new CachedGridIngredient();
                                Object slotIndex = ingredientMap.get("slotIndex");
                                if (slotIndex instanceof Number number) {
                                    ingredient.slotIndex = number.intValue();
                                }
                                Object itemIds = ingredientMap.get("itemIds");
                                if (itemIds instanceof List<?> ids) {
                                    for (Object id : ids) {
                                        if (id instanceof String idString) {
                                            ingredient.itemIds.add(idString);
                                        }
                                    }
                                }
                                recipe.grid.add(ingredient);
                            }
                        }
                        recipes.add(recipe);
                    }
                }
                book.recipesByOutput.put(entry.getKey(), recipes);
            }
        }
        return isRecipeCacheUsable(book);
    }

    private static boolean isRecipeCacheUsable(CachedRecipeBook book) {
        if (book == null || book.schemaVersion != RECIPE_CACHE_VERSION || book.recipesByOutput == null || book.recipesByOutput.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, List<CachedRecipe>> entry : book.recipesByOutput.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getKey().trim().isEmpty()) {
                continue;
            }
            List<CachedRecipe> recipes = entry.getValue();
            if (recipes == null || recipes.isEmpty()) {
                continue;
            }
            for (CachedRecipe recipe : recipes) {
                if (recipe == null || recipe.mode == null || recipe.mode.trim().isEmpty() || recipe.outputCount <= 0) {
                    continue;
                }
                if (recipe.grid == null || recipe.grid.isEmpty()) {
                    continue;
                }
                boolean hasIngredient = false;
                for (CachedGridIngredient ingredient : recipe.grid) {
                    if (ingredient == null || ingredient.itemIds == null || ingredient.itemIds.isEmpty()) {
                        continue;
                    }
                    boolean hasValidId = false;
                    for (String itemId : ingredient.itemIds) {
                        if (itemId != null && !itemId.trim().isEmpty()) {
                            hasValidId = true;
                            break;
                        }
                    }
                    if (hasValidId) {
                        hasIngredient = true;
                        break;
                    }
                }
                if (hasIngredient) {
                    return true;
                }
            }
        }
        return false;
    }

    private RecipeCacheWarmupState createRecipeCacheWarmupState(net.minecraft.client.Minecraft client) {
        if (client == null || client.getSingleplayerServer() == null) {
            return null;
        }
        if (hasUsableRecipeCacheInternal(client) && !recipeCacheWarmupRequested) {
            return null;
        }
        Object manager = client.getSingleplayerServer().getRecipeManager();
        if (manager == null) {
            return null;
        }
        CachedRecipeBook book = loadRecipeCache(client);
        if (book == null) {
            return null;
        }
        List<RecipeHolder<?>> craftingEntries = getCraftingRecipeEntries(manager);
        Object registryManager = client.level;
        if (registryManager == null) {
            registryManager = client.getSingleplayerServer().registryAccess();
        }
        List<RecipeCollection> collections = List.of();
        if (client.player != null && client.player.getRecipeBook() instanceof ClientRecipeBook clientRecipeBook) {
            List<RecipeCollection> orderedResults = clientRecipeBook.getCollections();
            if (orderedResults != null && !orderedResults.isEmpty()) {
                collections = new ArrayList<>(orderedResults);
            }
        }
        boolean hasExistingCache = book.recipesByOutput != null && !book.recipesByOutput.isEmpty();
        if (craftingEntries.isEmpty() && collections.isEmpty() && !hasExistingCache) {
            return null;
        }
        int totalDisplayEntries = countRecipeDisplayEntries(collections);
        return new RecipeCacheWarmupState(
            getRecipeCachePath(client),
            book,
            registryManager,
            client.getSingleplayerServer().registryAccess(),
            new ArrayList<>(craftingEntries),
            collections,
            totalDisplayEntries
        );
    }

    private int countRecipeDisplayEntries(List<RecipeCollection> collections) {
        if (collections == null || collections.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (RecipeCollection collection : collections) {
            List<?> entries = collection != null ? RecipeCompatibilityBridge.getAllRecipesFromCollection(collection) : null;
            if (entries != null) {
                total += entries.size();
            }
        }
        return total;
    }

    private void processWarmupRecipeEntry(RecipeCacheWarmupState state, RecipeHolder<?> entry) {
        if (state == null || entry == null || !(entry.value() instanceof CraftingRecipe craftingRecipe)) {
            return;
        }
        ItemStack output = getRecipeOutput(craftingRecipe, state.serverRegistryManager);
        if ((output == null || output.isEmpty()) && state.registryManager != state.serverRegistryManager) {
            output = getRecipeOutput(craftingRecipe, state.registryManager);
        }
        if (output == null || output.isEmpty()) {
            return;
        }
        cacheRecipeForMode(state.book, output.getItem(), craftingRecipe, output.getCount(), NodeMode.CRAFT_CRAFTING_TABLE, state.registryManager);
        if (recipeFitsPlayerGrid(craftingRecipe)) {
            cacheRecipeForMode(state.book, output.getItem(), craftingRecipe, output.getCount(), NodeMode.CRAFT_PLAYER_GUI, state.registryManager);
        }
        state.dirty = true;
        state.unsavedChanges++;
    }

    private void processWarmupDisplayEntry(RecipeCacheWarmupState state, Object entry) {
        if (state == null || entry == null) {
            return;
        }
        Object display = RecipeCompatibilityBridge.getDisplayFromEntry(entry);
        if (!RecipeCompatibilityBridge.isCraftingDisplay(display)) {
            return;
        }
        ItemStack output = getDisplayOutput(display, state.registryManager);
        if (output == null || output.isEmpty()) {
            return;
        }
        cacheDisplayForMode(state.book, output.getItem(), output.getCount(), display, NodeMode.CRAFT_CRAFTING_TABLE, state.registryManager);
        if (displayFitsPlayerGrid(display, state.registryManager)) {
            cacheDisplayForMode(state.book, output.getItem(), output.getCount(), display, NodeMode.CRAFT_PLAYER_GUI, state.registryManager);
        }
        state.dirty = true;
        state.unsavedChanges++;
    }

    static class GridIngredient {
        private final int slotIndex;
        private final Ingredient ingredient;
        private final boolean allowEmpty;

        GridIngredient(int slotIndex, Ingredient ingredient, boolean allowEmpty) {
            this.slotIndex = slotIndex;
            this.ingredient = ingredient;
            this.allowEmpty = allowEmpty;
        }

        int slotIndex() {
            return slotIndex;
        }

        Ingredient ingredient() {
            return ingredient;
        }

        boolean allowEmpty() {
            return allowEmpty;
        }
    }

    static class CraftingAttemptResult {
        final int produced;
        final String errorMessage;

        CraftingAttemptResult(int produced, String errorMessage) {
            this.produced = produced;
            this.errorMessage = errorMessage;
        }
    }
}
