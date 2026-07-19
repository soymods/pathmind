package com.pathmind.neoforge;

import com.pathmind.PathmindCommon;
import com.pathmind.execution.BackgroundStartRunner;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.StartLaunchMode;
import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.ChatMessageTracker;
import com.pathmind.util.FabricEventTracker;
import com.pathmind.util.KeybindDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mod(PathmindCommon.MOD_ID)
public class PathmindNeoForge {
    private static final String RECIPE_CACHE_NOTIFICATION_KEY = "recipe_cache_warmup";
    private static final int NAVIGATOR_NOTIFICATION_COLOR = 0xFF66D8FF;
    private static final double NAVIGATOR_PARAMETER_SEARCH_RADIUS = 64.0D;
    private static final int MAIN_MENU_BUTTON_SIZE = 20;
    private static final int MAIN_MENU_BUTTON_MARGIN = 8;

    private static final String EVT_FABRIC_CLIENT_LIFECYCLE_STARTED = "fabric.client.lifecycle.client_started";
    private static final String EVT_FABRIC_CLIENT_TICK_END = "fabric.client.lifecycle.end_client_tick";
    private static final String EVT_FABRIC_CLIENT_TICK_START = "fabric.client.lifecycle.start_client_tick";
    private static final String EVT_FABRIC_CLIENT_WORLD_TICK_END = "fabric.client.lifecycle.end_world_tick";
    private static final String EVT_FABRIC_CLIENT_WORLD_TICK_START = "fabric.client.lifecycle.start_world_tick";
    private static final String EVT_FABRIC_CLIENT_PLAY_DISCONNECT = "fabric.client.networking.play_connection_disconnect";
    private static final String EVT_FABRIC_CLIENT_PLAY_JOIN = "fabric.client.networking.play_connection_join";
    private static final String EVT_FABRIC_MESSAGE_RECEIVE_CHAT = "fabric.client.message.receive_chat";
    private static final String EVT_FABRIC_MESSAGE_RECEIVE_GAME = "fabric.client.message.receive_game";
    private static final String EVT_FABRIC_MESSAGE_SEND_CHAT = "fabric.client.message.send_chat";
    private static final String EVT_FABRIC_MESSAGE_SEND_COMMAND = "fabric.client.message.send_command";
    private static final String EVT_FABRIC_RENDER_HUD = "fabric.client.render.hud";
    private static final String EVT_FABRIC_PLAYER_ATTACK_BLOCK = "fabric.player.attack_block";
    private static final String EVT_FABRIC_PLAYER_ATTACK_ENTITY = "fabric.player.attack_entity";
    private static final String EVT_FABRIC_PLAYER_USE_BLOCK = "fabric.player.use_block";
    private static final String EVT_FABRIC_PLAYER_USE_ENTITY = "fabric.player.use_entity";
    private static final String EVT_FABRIC_PLAYER_USE_ITEM = "fabric.player.use_item";

    private static final String EVT_NEOFORGE_CLIENT_LIFECYCLE_STARTED = "neoforge.client.lifecycle.client_started";
    private static final String EVT_NEOFORGE_CLIENT_TICK_END = "neoforge.client.lifecycle.end_client_tick";
    private static final String EVT_NEOFORGE_CLIENT_TICK_START = "neoforge.client.lifecycle.start_client_tick";
    private static final String EVT_NEOFORGE_CLIENT_WORLD_TICK_END = "neoforge.client.lifecycle.end_world_tick";
    private static final String EVT_NEOFORGE_CLIENT_WORLD_TICK_START = "neoforge.client.lifecycle.start_world_tick";
    private static final String EVT_NEOFORGE_CLIENT_PLAY_DISCONNECT = "neoforge.client.networking.play_connection_disconnect";
    private static final String EVT_NEOFORGE_CLIENT_PLAY_JOIN = "neoforge.client.networking.play_connection_join";
    private static final String EVT_NEOFORGE_MESSAGE_RECEIVE_CHAT = "neoforge.client.message.receive_chat";
    private static final String EVT_NEOFORGE_MESSAGE_RECEIVE_GAME = "neoforge.client.message.receive_game";
    private static final String EVT_NEOFORGE_MESSAGE_SEND_CHAT = "neoforge.client.message.send_chat";
    private static final String EVT_NEOFORGE_MESSAGE_SEND_COMMAND = "neoforge.client.message.send_command";
    private static final String EVT_NEOFORGE_RENDER_HUD = "neoforge.client.render.hud";
    private static final String EVT_NEOFORGE_RENDER_LEVEL_AFTER_ENTITIES = "neoforge.client.render.level_after_entities";
    private static final String EVT_NEOFORGE_INPUT_MOVEMENT_UPDATE = "neoforge.client.input.movement_update";
    private static final String EVT_NEOFORGE_INPUT_SCREEN_KEY_PRESSED = "neoforge.client.input.screen_key_pressed";
    private static final String EVT_NEOFORGE_PLAYER_ATTACK_BLOCK = "neoforge.player.attack_block";
    private static final String EVT_NEOFORGE_PLAYER_ATTACK_ENTITY = "neoforge.player.attack_entity";
    private static final String EVT_NEOFORGE_PLAYER_USE_BLOCK = "neoforge.player.use_block";
    private static final String EVT_NEOFORGE_PLAYER_USE_ENTITY = "neoforge.player.use_entity";
    private static final String EVT_NEOFORGE_PLAYER_USE_ITEM = "neoforge.player.use_item";

    private Object executionManager;
    private Method setSingleplayerPausedMethod;
    private Method requestStopAllMethod;
    private Method playAllGraphsWithResultMethod;
    private Method openVisualEditorOrWarnMethod;
    private Method isVisualEditorScreenMethod;
    private Object navigatorChatSuggestions;
    private Method navigatorChatSuggestionsTickMethod;
    private Object navigatorDebugOverlay;
    private Method navigatorDebugOverlayToggleMethod;
    private Object pathmindNavigator;
    private Method pathmindNavigatorIsActiveMethod;
    private Method pathmindNavigatorTickMethod;
    private Method pathmindNavigatorResetMethod;
    private Method pathmindNavigatorStopMethod;
    private Method pathmindNavigatorStartGotoMethod;
    private Method pathmindNavigatorStartGotoNearBlockMethod;
    private Method pathmindNavigatorPreviewPathMethod;
    private Method pathmindNavigatorPreviewPathNearBlockMethod;
    private Method pathmindNavigatorSetWaterModeMethod;
    private Method pathmindNavigatorSetBlockBreakingAllowedMethod;
    private Method pathmindNavigatorSetBlockPlacingAllowedMethod;
    private Method pathmindNavigatorSetEventLoggingEnabledMethod;
    private Object pathmindNavigatorWaterModeNormal;
    private Object pathmindNavigatorWaterModeAvoid;
    private Method previewResultMessageMethod;
    private Method recipeWarmRecipeCacheMethod;
    private Method recipeIsRecipeCacheWarmupRequestedMethod;
    private Method recipeHasUsableRecipeCacheMethod;
    private Method recipeResetRecipeCacheWarmupMethod;
    private Method recipeIsRecipeCacheWarmupInProgressMethod;
    private Method recipeGetRecipeCacheWarmupProgressMethod;
    private Method recipeWarmupProgressCompletedMethod;
    private Method recipeWarmupProgressTotalMethod;
    private Method recipeWarmupProgressFractionMethod;
    private Method blockSelectionParseMethod;
    private Method blockSelectionMatchesMethod;
    private Method inputTickNoArgsMethod;
    private Method inputTickLegacyMethod;
    private Method serverJoinTrackerRecordClientJoinMethod;
    private Method serverJoinTrackerTickMethod;
    private Method serverJoinTrackerClearMethod;
    private NodeErrorNotificationOverlay nodeErrorNotificationOverlay;
    private Method nodeErrorNotificationClearMethod;
    private Method renderHudOverlaysMethod;
    private Method renderHudNotificationsMethod;
    private boolean commonBridgeReady;
    private boolean bridgeErrorLogged;
    private boolean hudRenderErrorLogged;
    private boolean worldOverlayErrorLogged;
    private boolean worldShutdownHandled;
    private boolean recipeCacheWarmed;
    private int recipeCacheWarmupCooldownTicks;
    private boolean playGraphsKeyDown;
    private boolean stopGraphsKeyDown;
    private boolean pendingClientLaunch;
    private boolean pendingWorldJoinLaunch;
    private Screen lastObservedScreen;

    public PathmindNeoForge(IEventBus modEventBus) {
        PathmindCommon.LOGGER.info("Initializing Pathmind (NeoForge)");
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(this::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(this::onLevelTickPre);
        NeoForge.EVENT_BUS.addListener(this::onLevelTickPost);
        NeoForge.EVENT_BUS.addListener(this::onMovementInputUpdate);
        NeoForge.EVENT_BUS.addListener(this::onScreenInitPost);
        NeoForge.EVENT_BUS.addListener(this::onScreenKeyPressedPost);
        NeoForge.EVENT_BUS.addListener(this::onRenderGuiPost);
        registerRenderLevelStageListener();
        NeoForge.EVENT_BUS.addListener(this::onClientChat);
        NeoForge.EVENT_BUS.addListener(this::onClientChatReceived);
        NeoForge.EVENT_BUS.addListener(this::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(this::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(this::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(this::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(this::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(this::onEntityInteractSpecific);
        NeoForge.EVENT_BUS.addListener(this::onClientPlayerLoggingIn);
        NeoForge.EVENT_BUS.addListener(this::onClientPlayerLoggingOut);
    }

    private void registerRenderLevelStageListener() {
        registerRenderLevelStageListener(resolveRenderLevelStageEventClass());
    }

    private <T extends RenderLevelStageEvent> void registerRenderLevelStageListener(Class<T> eventClass) {
        NeoForge.EVENT_BUS.addListener(eventClass, this::onRenderLevelStage);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends RenderLevelStageEvent> resolveRenderLevelStageEventClass() {
        try {
            return (Class<? extends RenderLevelStageEvent>) Class
                .forName("net.neoforged.neoforge.client.event.RenderLevelStageEvent$AfterEntities")
                .asSubclass(RenderLevelStageEvent.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return RenderLevelStageEvent.class;
        }
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(this::initializeClient);
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        PathmindNeoForgeKeybinds.registerCategory(event);
        event.register(PathmindNeoForgeKeybinds.OPEN_VISUAL_EDITOR);
        event.register(PathmindNeoForgeKeybinds.PLAY_GRAPHS);
        event.register(PathmindNeoForgeKeybinds.STOP_GRAPHS);
    }

    private void initializeClient() {
        try {
            invokeStaticNoArgs("com.pathmind.data.PresetManager", "initialize");
            invokeStaticNoArgs("com.pathmind.marketplace.MarketplaceAuthManager", "initialize");
            initializeHud();
            initializeCommonBridge();
            commonBridgeReady = true;
            pendingClientLaunch = true;
            fireLoaderEvent(EVT_NEOFORGE_CLIENT_LIFECYCLE_STARTED, EVT_FABRIC_CLIENT_LIFECYCLE_STARTED);
            PathmindCommon.LOGGER.info("Pathmind NeoForge client setup complete");
        } catch (ReflectiveOperationException | LinkageError e) {
            PathmindCommon.LOGGER.error("Failed to initialize Pathmind NeoForge client bridge", e);
        }
    }

    private void initializeHud() throws ReflectiveOperationException {
        Object activeNodeOverlay = newInstance("com.pathmind.ui.overlay.ActiveNodeOverlay");
        navigatorDebugOverlay = newInstance("com.pathmind.ui.overlay.NavigatorDebugOverlay");
        Class<?> nodeErrorClass = Class.forName("com.pathmind.ui.overlay.NodeErrorNotificationOverlay");
        nodeErrorNotificationOverlay = (NodeErrorNotificationOverlay) nodeErrorClass.getMethod("getInstance").invoke(null);
        Object variablesOverlay = newInstance("com.pathmind.ui.overlay.VariablesOverlay");

        Class<?> activeClass = Class.forName("com.pathmind.ui.overlay.ActiveNodeOverlay");
        Class<?> debugClass = Class.forName("com.pathmind.ui.overlay.NavigatorDebugOverlay");
        Class<?> variablesClass = Class.forName("com.pathmind.ui.overlay.VariablesOverlay");
        Class.forName("com.pathmind.PathmindHud")
            .getMethod("initialize", activeClass, debugClass, nodeErrorClass, variablesClass)
            .invoke(null, activeNodeOverlay, navigatorDebugOverlay, nodeErrorNotificationOverlay, variablesOverlay);
    }

    private void initializeCommonBridge() throws ReflectiveOperationException {
        Class<?> screensClass = Class.forName("com.pathmind.screen.PathmindScreens");
        openVisualEditorOrWarnMethod = screensClass.getMethod("openVisualEditorOrWarn", Minecraft.class, Screen.class);
        isVisualEditorScreenMethod = screensClass.getMethod("isVisualEditorScreen", Screen.class);

        Class<?> executionManagerClass = Class.forName("com.pathmind.execution.ExecutionManager");
        executionManager = executionManagerClass.getMethod("getInstance").invoke(null);
        setSingleplayerPausedMethod = executionManagerClass.getMethod("setSingleplayerPaused", boolean.class);
        requestStopAllMethod = executionManagerClass.getMethod("requestStopAll");
        playAllGraphsWithResultMethod = executionManagerClass.getMethod("playAllGraphsWithResult");

        Class<?> suggestionsClass = Class.forName("com.pathmind.ui.overlay.NavigatorChatSuggestions");
        navigatorChatSuggestions = suggestionsClass.getMethod("getInstance").invoke(null);
        navigatorChatSuggestionsTickMethod = suggestionsClass.getMethod("tick", Minecraft.class);

        Class<?> navigatorClass = Class.forName("com.pathmind.execution.PathmindNavigator");
        pathmindNavigator = navigatorClass.getMethod("getInstance").invoke(null);
        pathmindNavigatorIsActiveMethod = navigatorClass.getMethod("isActive");
        pathmindNavigatorTickMethod = navigatorClass.getMethod("tick", Minecraft.class);
        pathmindNavigatorResetMethod = navigatorClass.getMethod("reset");
        pathmindNavigatorStopMethod = navigatorClass.getMethod("stop", String.class);
        pathmindNavigatorStartGotoMethod = navigatorClass.getMethod("startGoto", BlockPos.class, String.class, CompletableFuture.class);
        pathmindNavigatorStartGotoNearBlockMethod = navigatorClass.getMethod("startGotoNearBlock", BlockPos.class, String.class, CompletableFuture.class);
        pathmindNavigatorPreviewPathMethod = navigatorClass.getMethod("previewPath", Minecraft.class, BlockPos.class, String.class);
        pathmindNavigatorPreviewPathNearBlockMethod = navigatorClass.getMethod("previewPathNearBlock", Minecraft.class, BlockPos.class, String.class);
        pathmindNavigatorSetBlockBreakingAllowedMethod = navigatorClass.getMethod("setBlockBreakingAllowed", boolean.class);
        pathmindNavigatorSetBlockPlacingAllowedMethod = navigatorClass.getMethod("setBlockPlacingAllowed", boolean.class);
        pathmindNavigatorSetEventLoggingEnabledMethod = navigatorClass.getMethod("setEventLoggingEnabled", boolean.class);

        Class<?> waterModeClass = Class.forName("com.pathmind.execution.PathmindNavigator$WaterMode");
        pathmindNavigatorSetWaterModeMethod = navigatorClass.getMethod("setWaterMode", waterModeClass);
        Object[] waterModes = waterModeClass.getEnumConstants();
        for (Object waterMode : waterModes) {
            if ("NORMAL".equals(String.valueOf(waterMode))) {
                pathmindNavigatorWaterModeNormal = waterMode;
            } else if ("AVOID".equals(String.valueOf(waterMode))) {
                pathmindNavigatorWaterModeAvoid = waterMode;
            }
        }

        Class<?> previewResultClass = Class.forName("com.pathmind.execution.PathmindNavigator$PreviewResult");
        previewResultMessageMethod = previewResultClass.getMethod("message");

        navigatorDebugOverlayToggleMethod = navigatorDebugOverlay.getClass().getMethod("toggle");

        Class<?> nodeClass = Class.forName("com.pathmind.nodes.Node");
        recipeWarmRecipeCacheMethod = nodeClass.getMethod("warmRecipeCache", Minecraft.class);
        recipeIsRecipeCacheWarmupRequestedMethod = nodeClass.getMethod("isRecipeCacheWarmupRequested");
        recipeHasUsableRecipeCacheMethod = nodeClass.getMethod("hasUsableRecipeCache", Minecraft.class);
        recipeResetRecipeCacheWarmupMethod = nodeClass.getMethod("resetRecipeCacheWarmup");
        recipeIsRecipeCacheWarmupInProgressMethod = nodeClass.getMethod("isRecipeCacheWarmupInProgress", Minecraft.class);
        recipeGetRecipeCacheWarmupProgressMethod = nodeClass.getMethod("getRecipeCacheWarmupProgress", Minecraft.class);
        Class<?> recipeWarmupProgressClass = Class.forName("com.pathmind.nodes.Node$RecipeCacheWarmupProgress");
        recipeWarmupProgressCompletedMethod = recipeWarmupProgressClass.getMethod("completed");
        recipeWarmupProgressTotalMethod = recipeWarmupProgressClass.getMethod("total");
        recipeWarmupProgressFractionMethod = recipeWarmupProgressClass.getMethod("fraction");

        Class<?> blockSelectionClass = Class.forName("com.pathmind.util.BlockSelection");
        blockSelectionParseMethod = blockSelectionClass.getMethod("parse", String.class);
        blockSelectionMatchesMethod = blockSelectionClass.getMethod("matches", BlockState.class);

        Class<?> serverJoinTrackerClass = Class.forName("com.pathmind.util.ServerJoinTracker");
        serverJoinTrackerRecordClientJoinMethod = serverJoinTrackerClass.getMethod("recordClientJoin", Minecraft.class);
        serverJoinTrackerTickMethod = serverJoinTrackerClass.getMethod("tick", Minecraft.class);
        serverJoinTrackerClearMethod = serverJoinTrackerClass.getMethod("clear");

        nodeErrorNotificationClearMethod = nodeErrorNotificationOverlay.getClass().getMethod("clear");

        Class<?> hudClass = Class.forName("com.pathmind.PathmindHud");
        renderHudOverlaysMethod = hudClass.getMethod("renderHudOverlays", GuiGraphics.class, Minecraft.class);
        renderHudNotificationsMethod = hudClass.getMethod("renderHudNotifications", GuiGraphics.class, Minecraft.class);
    }

    private void onClientTickPre(ClientTickEvent.Pre event) {
        fireLoaderEvent(EVT_NEOFORGE_CLIENT_TICK_START, EVT_FABRIC_CLIENT_TICK_START);
    }

    private void onClientTickPost(ClientTickEvent.Post event) {
        fireLoaderEvent(EVT_NEOFORGE_CLIENT_TICK_END, EVT_FABRIC_CLIENT_TICK_END);
        Minecraft client = Minecraft.getInstance();
        handleKeybinds(client);
        if (!commonBridgeReady || client == null) {
            return;
        }
        invokeBridge("tick Pathmind systems", () -> {
            navigatorChatSuggestionsTickMethod.invoke(navigatorChatSuggestions, client);
            serverJoinTrackerTickMethod.invoke(null, client);
        });
        handleRecipeCacheWarmup(client);
        handlePendingClientLaunch(client);
        handlePendingWorldJoinLaunch(client);
        handleScreenLaunchTriggers(client);
    }

    private void onLevelTickPre(LevelTickEvent.Pre event) {
        if (isClientLevel(event.getLevel())) {
            fireLoaderEvent(EVT_NEOFORGE_CLIENT_WORLD_TICK_START, EVT_FABRIC_CLIENT_WORLD_TICK_START);
        }
    }

    private void onLevelTickPost(LevelTickEvent.Post event) {
        if (isClientLevel(event.getLevel())) {
            fireLoaderEvent(EVT_NEOFORGE_CLIENT_WORLD_TICK_END, EVT_FABRIC_CLIENT_WORLD_TICK_END);
        }
    }

    private void onMovementInputUpdate(MovementInputUpdateEvent event) {
        if (!commonBridgeReady || event.getInput() == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
            return;
        }
        fireLoaderEvent(EVT_NEOFORGE_INPUT_MOVEMENT_UPDATE);
        invokeBridge("tick Pathmind navigator", () -> {
            if (!Boolean.TRUE.equals(pathmindNavigatorIsActiveMethod.invoke(pathmindNavigator))) {
                return;
            }
            pathmindNavigatorTickMethod.invoke(pathmindNavigator, client);
            tickMovementInput(event.getInput());
        });
    }

    private void tickMovementInput(Object input) throws ReflectiveOperationException {
        if (input == null) {
            return;
        }
        if (inputTickNoArgsMethod == null && inputTickLegacyMethod == null) {
            try {
                inputTickNoArgsMethod = input.getClass().getMethod("tick");
            } catch (NoSuchMethodException ignored) {
                inputTickLegacyMethod = input.getClass().getMethod("tick", boolean.class, float.class);
            }
        }

        if (inputTickNoArgsMethod != null) {
            inputTickNoArgsMethod.invoke(input);
        } else {
            inputTickLegacyMethod.invoke(input, false, 1.0F);
        }
    }

    private void handleKeybinds(Minecraft client) {
        if (client == null || !commonBridgeReady) {
            return;
        }

        while (PathmindNeoForgeKeybinds.OPEN_VISUAL_EDITOR.consumeClick()) {
            if (client.screen != null && !(client.screen instanceof TitleScreen)) {
                continue;
            }
            openVisualEditorOrWarn(client, client.screen);
        }

        boolean editorOpen = isVisualEditorScreen(client.screen);
        invokeBridge("update Pathmind pause state", () ->
            setSingleplayerPausedMethod.invoke(executionManager, (client.isSingleplayer() && editorOpen) || client.screen instanceof PauseScreen));

        if (editorOpen) {
            stopGraphsKeyDown = false;
            playGraphsKeyDown = false;
            return;
        }

        if (client.level == null) {
            if (!editorOpen) {
                handleClientShutdown();
            }
            return;
        }

        boolean chatOrGuiOpen = shouldIgnoreKeybinds(client);

        boolean stopDown = PathmindNeoForgeKeybinds.STOP_GRAPHS.isDown();
        if (!chatOrGuiOpen && stopDown && !stopGraphsKeyDown) {
            requestStopAll();
        }
        stopGraphsKeyDown = stopDown;

        if (client.player == null) {
            return;
        }

        boolean playDown = PathmindNeoForgeKeybinds.PLAY_GRAPHS.isDown();
        if (!chatOrGuiOpen && playDown && !playGraphsKeyDown) {
            showPlayKeyDiagnostics(client);
            invokeBridge("play Pathmind graphs", () -> {
                Object result = playAllGraphsWithResultMethod.invoke(executionManager);
                showGraphStartDiagnostic(result);
            });
        }
        playGraphsKeyDown = playDown;
    }

    private void showPlayKeyDiagnostics(Minecraft client) {
        String warning = KeybindDiagnostics.describeConflict(client, PathmindNeoForgeKeybinds.PLAY_GRAPHS,
            PathmindNeoForgeKeybinds.OPEN_VISUAL_EDITOR, PathmindNeoForgeKeybinds.STOP_GRAPHS);
        if (warning != null && nodeErrorNotificationOverlay != null) {
            nodeErrorNotificationOverlay.show(warning, UITheme.STATE_WARNING);
        }
    }

    private void showGraphStartDiagnostic(Object result) {
        String resultName = String.valueOf(result);
        if ("STARTED".equals(resultName) || nodeErrorNotificationOverlay == null) {
            return;
        }
        String key = "NO_START_NODE".equals(resultName) ? "pathmind.keybind.noStartNode" : "pathmind.keybind.noGraph";
        nodeErrorNotificationOverlay.show(Component.translatable(key).getString(), UITheme.STATE_ERROR);
    }

    private void onScreenInitPost(ScreenEvent.Init.Post event) {
        if (event == null || !(event.getScreen() instanceof TitleScreen screen)) {
            return;
        }
        Button button = new PathmindNeoForgeMainMenuButton(
            MAIN_MENU_BUTTON_MARGIN,
            MAIN_MENU_BUTTON_MARGIN,
            MAIN_MENU_BUTTON_SIZE,
            ignored -> openVisualEditorOrWarn(Minecraft.getInstance(), screen)
        );
        addScreenInitButton(event, button);
    }

    private void onScreenKeyPressedPost(ScreenEvent.KeyPressed.Post event) {
        fireLoaderEvent(EVT_NEOFORGE_INPUT_SCREEN_KEY_PRESSED);
        if (event.getScreen() instanceof TitleScreen && event.getKeyCode() == GLFW.GLFW_KEY_RIGHT_ALT) {
            openVisualEditorOrWarn(Minecraft.getInstance(), event.getScreen());
        }
    }

    private void onRenderGuiPost(RenderGuiEvent.Post event) {
        fireLoaderEvent(EVT_NEOFORGE_RENDER_HUD, EVT_FABRIC_RENDER_HUD);
        if (!commonBridgeReady || hudRenderErrorLogged) {
            return;
        }

        try {
            Minecraft client = Minecraft.getInstance();
            renderHudOverlaysMethod.invoke(null, event.getGuiGraphics(), client);
            renderHudNotificationsMethod.invoke(null, event.getGuiGraphics(), client);
        } catch (ReflectiveOperationException | LinkageError e) {
            hudRenderErrorLogged = true;
            PathmindCommon.LOGGER.warn("Pathmind NeoForge HUD bridge failed; HUD overlays are disabled for this session", e);
        }
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!isNavigatorOverlayCollectionStage(event)) {
            return;
        }
        fireLoaderEvent(EVT_NEOFORGE_RENDER_LEVEL_AFTER_ENTITIES);
        if (!commonBridgeReady || worldOverlayErrorLogged) {
            return;
        }

        try {
            PathmindNeoForgeWorldOverlay.render(event);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            worldOverlayErrorLogged = true;
            PathmindCommon.LOGGER.warn("Pathmind NeoForge navigator world overlay failed; route outlines are disabled for this session", e);
        }
    }

    private void onClientChat(ClientChatEvent event) {
        if (event == null) {
            return;
        }
        String message = event.getOriginalMessage();
        if (message == null || message.isBlank()) {
            message = event.getMessage();
        }
        if (message != null) {
            boolean handled = message.startsWith("/")
                ? handlePathmindNavigatorCommand(message.substring(1))
                : handlePathmindNavigatorChat(message);
            if (handled) {
                cancelEvent(event);
                return;
            }
        }
        if (message != null && message.startsWith("/")) {
            fireLoaderEvent(EVT_NEOFORGE_MESSAGE_SEND_COMMAND, EVT_FABRIC_MESSAGE_SEND_COMMAND);
        } else {
            fireLoaderEvent(EVT_NEOFORGE_MESSAGE_SEND_CHAT, EVT_FABRIC_MESSAGE_SEND_CHAT);
        }
    }

    private void onClientChatReceived(ClientChatReceivedEvent event) {
        if (event == null) {
            return;
        }
        Component message = event.getMessage();
        String text = message != null ? message.getString() : "";
        if (!text.isBlank()) {
            String senderName = extractChatSender(text);
            ChatMessageTracker.record(senderName, text, System.currentTimeMillis());
            if (!event.isSystem()) {
                ExecutionManager.getInstance().triggerEventFunction(
                    ExecutionManager.CHAT_MESSAGE_EVENT_NAME,
                    createChatRuntimeVariables(senderName, text)
                );
            }
        }
        if (event.isSystem()) {
            fireLoaderEvent(EVT_NEOFORGE_MESSAGE_RECEIVE_GAME, EVT_FABRIC_MESSAGE_RECEIVE_GAME);
        } else {
            fireLoaderEvent(EVT_NEOFORGE_MESSAGE_RECEIVE_CHAT, EVT_FABRIC_MESSAGE_RECEIVE_CHAT);
        }
    }

    private void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event != null && isLocalPlayer(event.getEntity())) {
            fireLoaderEvent(EVT_NEOFORGE_PLAYER_ATTACK_BLOCK, EVT_FABRIC_PLAYER_ATTACK_BLOCK);
        }
    }

    private void onAttackEntity(AttackEntityEvent event) {
        if (event != null && isLocalPlayer(event.getEntity())) {
            fireLoaderEvent(EVT_NEOFORGE_PLAYER_ATTACK_ENTITY, EVT_FABRIC_PLAYER_ATTACK_ENTITY);
        }
    }

    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event != null && isLocalPlayer(event.getEntity())) {
            fireLoaderEvent(EVT_NEOFORGE_PLAYER_USE_BLOCK, EVT_FABRIC_PLAYER_USE_BLOCK);
        }
    }

    private void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event != null && isLocalPlayer(event.getEntity())) {
            fireLoaderEvent(EVT_NEOFORGE_PLAYER_USE_ITEM, EVT_FABRIC_PLAYER_USE_ITEM);
        }
    }

    private void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event != null && isLocalPlayer(event.getEntity())) {
            fireLoaderEvent(EVT_NEOFORGE_PLAYER_USE_ENTITY, EVT_FABRIC_PLAYER_USE_ENTITY);
        }
    }

    private void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event != null && isLocalPlayer(event.getEntity())) {
            fireLoaderEvent(EVT_NEOFORGE_PLAYER_USE_ENTITY, EVT_FABRIC_PLAYER_USE_ENTITY);
        }
    }

    private void onClientPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        worldShutdownHandled = false;
        pendingWorldJoinLaunch = true;
        ChatMessageTracker.clear();
        FabricEventTracker.clear();
        resetRecipeCacheWarmup();
        recipeCacheWarmed = false;
        recipeCacheWarmupCooldownTicks = 0;
        fireLoaderEvent(EVT_NEOFORGE_CLIENT_PLAY_JOIN, EVT_FABRIC_CLIENT_PLAY_JOIN);
        invokeBridge("clear Pathmind notifications on join", () -> {
            if (nodeErrorNotificationOverlay != null) {
                nodeErrorNotificationClearMethod.invoke(nodeErrorNotificationOverlay);
            }
        });
    }

    private void onClientPlayerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        pendingWorldJoinLaunch = false;
        handleClientShutdown(true);
        ChatMessageTracker.clear();
        FabricEventTracker.clear();
        resetRecipeCacheWarmup();
        fireLoaderEvent(EVT_NEOFORGE_CLIENT_PLAY_DISCONNECT, EVT_FABRIC_CLIENT_PLAY_DISCONNECT);
        invokeBridge("reset Pathmind client state", () -> {
            if (pathmindNavigator != null) {
                pathmindNavigatorResetMethod.invoke(pathmindNavigator);
            }
            if (serverJoinTrackerClearMethod != null) {
                serverJoinTrackerClearMethod.invoke(null);
            }
            if (nodeErrorNotificationOverlay != null) {
                nodeErrorNotificationClearMethod.invoke(nodeErrorNotificationOverlay);
            }
        });
    }

    private boolean shouldIgnoreKeybinds(Minecraft client) {
        if (client == null || client.screen == null) {
            return false;
        }
        if (client.screen instanceof ChatScreen || isVisualEditorScreen(client.screen)) {
            return true;
        }
        return isTextInputFocused(client.screen);
    }

    private boolean isTextInputFocused(Screen screen) {
        GuiEventListener focused = getFocusedElement(screen);
        return focused instanceof EditBox textField && textField.isFocused();
    }

    private GuiEventListener getFocusedElement(Screen screen) {
        if (screen == null) {
            return null;
        }
        try {
            Method method = Screen.class.getMethod("getFocused");
            Object focused = method.invoke(screen);
            return focused instanceof GuiEventListener element ? element : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private void handleRecipeCacheWarmup(Minecraft client) {
        if (client == null || client.getSingleplayerServer() == null) {
            if (nodeErrorNotificationOverlay != null) {
                nodeErrorNotificationOverlay.dismiss(RECIPE_CACHE_NOTIFICATION_KEY);
            }
            recipeCacheWarmed = false;
            return;
        }
        if (!invokeBoolean(recipeIsRecipeCacheWarmupRequestedMethod, null)) {
            if (nodeErrorNotificationOverlay != null) {
                nodeErrorNotificationOverlay.dismiss(RECIPE_CACHE_NOTIFICATION_KEY);
            }
            recipeCacheWarmed = invokeBoolean(recipeHasUsableRecipeCacheMethod, null, client);
            recipeCacheWarmupCooldownTicks = 0;
            return;
        }

        recipeCacheWarmed = false;
        if (nodeErrorNotificationOverlay != null) {
            nodeErrorNotificationOverlay.showProgress(
                RECIPE_CACHE_NOTIFICATION_KEY,
                "Building recipe cache\nPreparing singleplayer recipes...",
                UITheme.ACCENT_SKY,
                0.0f
            );
        }
        if (recipeCacheWarmupCooldownTicks > 0) {
            recipeCacheWarmupCooldownTicks--;
            return;
        }

        boolean cached = invokeBoolean(recipeWarmRecipeCacheMethod, null, client);
        Object progress = invokeObject(recipeGetRecipeCacheWarmupProgressMethod, null, client);
        if (nodeErrorNotificationOverlay != null) {
            String message = progress != null
                ? "Building recipe cache\n" + invokeInt(recipeWarmupProgressCompletedMethod, progress) + " / " + invokeInt(recipeWarmupProgressTotalMethod, progress)
                : "Building recipe cache\nPreparing singleplayer recipes...";
            float fraction = progress != null ? invokeFloat(recipeWarmupProgressFractionMethod, progress) : 0.0f;
            nodeErrorNotificationOverlay.showProgress(RECIPE_CACHE_NOTIFICATION_KEY, message, UITheme.ACCENT_SKY, fraction);
        }
        if (cached) {
            recipeCacheWarmed = true;
            if (nodeErrorNotificationOverlay != null) {
                nodeErrorNotificationOverlay.dismiss(RECIPE_CACHE_NOTIFICATION_KEY);
                nodeErrorNotificationOverlay.show("Recipe cache ready.", UITheme.ACCENT_SKY);
            }
            PathmindCommon.LOGGER.debug("Pathmind recipe cache populated from singleplayer recipes.");
        } else if (!invokeBoolean(recipeIsRecipeCacheWarmupInProgressMethod, null, client)
            && !invokeBoolean(recipeHasUsableRecipeCacheMethod, null, client)) {
            if (nodeErrorNotificationOverlay != null) {
                nodeErrorNotificationOverlay.dismiss(RECIPE_CACHE_NOTIFICATION_KEY);
                nodeErrorNotificationOverlay.show("Recipe cache could not be built.", UITheme.STATE_ERROR);
            }
            PathmindCommon.LOGGER.warn("Pathmind manual recipe cache warmup completed without usable recipes.");
        }
    }

    private boolean handlePathmindNavigatorChat(String message) {
        if (message == null) {
            return false;
        }
        String trimmed = message.trim();
        if (!trimmed.startsWith("!")) {
            return false;
        }
        return runNavigatorCommand(trimmed.substring(1).trim());
    }

    private boolean handlePathmindNavigatorCommand(String command) {
        if (command == null) {
            return false;
        }
        String trimmed = command.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("pathmindnav")) {
            return false;
        }
        return runNavigatorCommand(trimmed.substring("pathmindnav".length()).trim());
    }

    private boolean runNavigatorCommand(String rawCommand) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return true;
        }
        String command = rawCommand == null ? "" : rawCommand.trim();
        if (command.isEmpty() || command.equalsIgnoreCase("help")) {
            showNavigatorMessage("Pathmind Nav: !travel, !path, !nav debug, !stop");
            return true;
        }

        String[] parts = command.split("\\s+");
        if (parts.length == 0) {
            return true;
        }

        if (parts[0].equalsIgnoreCase("stop")) {
            invokeBridge("stop Pathmind navigator from chat", () -> pathmindNavigatorStopMethod.invoke(pathmindNavigator, "chat stop"));
            showNavigatorMessage("Pathmind Nav stopped.");
            return true;
        }
        if (parts[0].equalsIgnoreCase("travel")) {
            handleNavigatorGoto(client, parts);
            return true;
        }
        if (parts[0].equalsIgnoreCase("path")) {
            handleNavigatorPathPreview(client, parts);
            return true;
        }
        if (parts[0].equalsIgnoreCase("nav") && parts.length >= 2 && parts[1].equalsIgnoreCase("debug")) {
            boolean enabled = invokeBoolean(navigatorDebugOverlayToggleMethod, navigatorDebugOverlay);
            showNavigatorMessage(enabled ? "Pathmind Nav debug overlay enabled." : "Pathmind Nav debug overlay disabled.");
            return true;
        }
        if (parts[0].equalsIgnoreCase("nav") && parts.length >= 3 && parts[1].equalsIgnoreCase("water")) {
            handleNavigatorWaterMode(parts[2]);
            return true;
        }
        if (parts[0].equalsIgnoreCase("nav") && parts.length >= 3 && parts[1].equalsIgnoreCase("logs")) {
            handleNavigatorLogs(parts[2]);
            return true;
        }
        if (parts[0].equalsIgnoreCase("flag") && parts.length >= 3) {
            handleNavigatorFlag(parts[1], parts[2]);
            return true;
        }

        showNavigatorMessage("Unknown Pathmind Nav command. Use !travel, !path, !nav water, !nav logs, !flag, or !stop.");
        return true;
    }

    private void handleNavigatorGoto(Minecraft client, String[] parts) {
        if (client == null || client.player == null || client.level == null) {
            showNavigatorMessage("Pathmind Nav is unavailable right now.");
            return;
        }
        NavigatorTarget target = parseNavigatorTarget(client, parts, 1, "!travel");
        if (target == null || target.pos() == null) {
            return;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        boolean started = target.nearBlock()
            ? invokeBoolean(pathmindNavigatorStartGotoNearBlockMethod, pathmindNavigator, target.pos(), "Chat Travel", future)
            : invokeBoolean(pathmindNavigatorStartGotoMethod, pathmindNavigator, target.pos(), "Chat Travel", future);
        if (!started) {
            showNavigatorMessage("Could not start Pathmind Nav.");
            return;
        }
        showNavigatorMessage("Pathmind Nav: heading to " + target.pos().getX() + " " + target.pos().getY() + " " + target.pos().getZ());
    }

    private void handleNavigatorPathPreview(Minecraft client, String[] parts) {
        if (client == null || client.player == null || client.level == null) {
            showNavigatorMessage("Pathmind Nav is unavailable right now.");
            return;
        }
        NavigatorTarget target = parseNavigatorTarget(client, parts, 1, "!path");
        if (target == null || target.pos() == null) {
            return;
        }
        Object result = target.nearBlock()
            ? invokeObject(pathmindNavigatorPreviewPathNearBlockMethod, pathmindNavigator, client, target.pos(), "Path Preview")
            : invokeObject(pathmindNavigatorPreviewPathMethod, pathmindNavigator, client, target.pos(), "Path Preview");
        showNavigatorMessage(result != null ? String.valueOf(invokeObject(previewResultMessageMethod, result)) : "Path preview unavailable.");
    }

    private record NavigatorTarget(BlockPos pos, boolean nearBlock) {
    }

    private NavigatorTarget parseNavigatorTarget(Minecraft client, String[] parts, int coordinateStartIndex, String usageCommand) {
        if (client == null || client.player == null) {
            showNavigatorMessage("Pathmind Nav is unavailable right now.");
            return null;
        }

        int remaining = parts.length - coordinateStartIndex;
        if (remaining >= 2) {
            String modeToken = parts[coordinateStartIndex];
            String rawTarget = parts[coordinateStartIndex + 1];
            if (modeToken.equalsIgnoreCase("block")) {
                return resolveNavigatorBlockTarget(client, rawTarget, usageCommand);
            }
            if (modeToken.equalsIgnoreCase("item")) {
                return resolveNavigatorItemTarget(client, rawTarget, usageCommand);
            }
        }
        try {
            if (remaining == 2) {
                int x = parseNavigatorCoordinate(parts[coordinateStartIndex], client.player.getBlockX());
                int z = parseNavigatorCoordinate(parts[coordinateStartIndex + 1], client.player.getBlockZ());
                return new NavigatorTarget(new BlockPos(x, client.player.getBlockY(), z), false);
            }
            if (remaining == 3) {
                int x = parseNavigatorCoordinate(parts[coordinateStartIndex], client.player.getBlockX());
                int y = parseNavigatorCoordinate(parts[coordinateStartIndex + 1], client.player.getBlockY());
                int z = parseNavigatorCoordinate(parts[coordinateStartIndex + 2], client.player.getBlockZ());
                return new NavigatorTarget(new BlockPos(x, y, z), false);
            }
        } catch (NumberFormatException exception) {
            showNavigatorMessage("Invalid coordinates for " + usageCommand + ".");
            return null;
        }

        showNavigatorMessage("Usage: " + usageCommand + " <x> <y> <z>, " + usageCommand + " <x> <z>, " + usageCommand + " block <block_id>, or " + usageCommand + " item <item_id>");
        return null;
    }

    private NavigatorTarget resolveNavigatorBlockTarget(Minecraft client, String rawBlockId, String usageCommand) {
        if (client == null || client.player == null || client.level == null) {
            showNavigatorMessage("Pathmind Nav is unavailable right now.");
            return null;
        }
        String normalized = normalizeNavigatorResourceId(rawBlockId, true);
        if (normalized == null) {
            showNavigatorMessage("Usage: " + usageCommand + " block <block_id>");
            return null;
        }
        Object identifier = parseIdentifier(normalized);
        if (identifier == null || !registryContains(BuiltInRegistries.BLOCK, identifier)) {
            showNavigatorMessage("Unknown block identifier: " + rawBlockId);
            return null;
        }

        List<Object> selections = new ArrayList<>();
        parseBlockSelection(normalized).ifPresent(selections::add);
        if (selections.isEmpty()) {
            showNavigatorMessage("Unknown block identifier: " + rawBlockId);
            return null;
        }

        Optional<BlockPos> nearest = findNearestBlock(client, selections, NAVIGATOR_PARAMETER_SEARCH_RADIUS);
        if (nearest.isEmpty()) {
            showNavigatorMessage("No nearby block found for " + normalized + ".");
            return null;
        }
        return new NavigatorTarget(nearest.get(), true);
    }

    private NavigatorTarget resolveNavigatorItemTarget(Minecraft client, String rawItemId, String usageCommand) {
        if (client == null || client.player == null || client.level == null) {
            showNavigatorMessage("Pathmind Nav is unavailable right now.");
            return null;
        }
        String normalized = normalizeNavigatorResourceId(rawItemId, false);
        if (normalized == null) {
            showNavigatorMessage("Usage: " + usageCommand + " item <item_id>");
            return null;
        }
        Object identifier = parseIdentifier(normalized);
        if (identifier == null || !registryContains(BuiltInRegistries.ITEM, identifier)) {
            showNavigatorMessage("Unknown item identifier: " + rawItemId);
            return null;
        }

        Item item = registryGetValue(BuiltInRegistries.ITEM, identifier, Item.class);
        Optional<ItemEntity> nearest = findNearestDroppedItemEntity(client, item, NAVIGATOR_PARAMETER_SEARCH_RADIUS);
        if (nearest.isEmpty()) {
            showNavigatorMessage("No nearby dropped item found for " + normalized + ".");
            return null;
        }
        return new NavigatorTarget(nearest.get().blockPosition(), false);
    }

    private String normalizeNavigatorResourceId(String rawId, boolean block) {
        if (rawId == null) {
            return null;
        }
        String trimmed = rawId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (block) {
            return normalizeNavigatorBlockResourceId(trimmed);
        }
        Object identifier = parseIdentifier(trimmed);
        if (identifier != null) {
            return identifier.toString();
        }
        Object namespaced = parseIdentifier("minecraft:" + trimmed.toLowerCase(Locale.ROOT));
        return namespaced != null ? namespaced.toString() : null;
    }

    private String normalizeNavigatorBlockResourceId(String rawId) {
        if (rawId == null) {
            return null;
        }
        String trimmed = rawId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int bracket = trimmed.indexOf('[');
        String blockPart = bracket >= 0 ? trimmed.substring(0, bracket).trim() : trimmed;
        String statePart = bracket >= 0 ? trimmed.substring(bracket).trim() : "";
        Object identifier = parseIdentifier(blockPart);
        if (identifier == null) {
            identifier = parseIdentifier("minecraft:" + blockPart.toLowerCase(Locale.ROOT));
        }
        return identifier != null ? identifier + statePart : null;
    }

    private static Object parseIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (String className : List.of("net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation")) {
            try {
                Class<?> identifierClass = Class.forName(className);
                Method tryParse = identifierClass.getMethod("tryParse", String.class);
                return tryParse.invoke(null, value);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Try the next Minecraft naming generation.
            }
        }
        return null;
    }

    private static boolean registryContains(Object registry, Object identifier) {
        if (registry == null || identifier == null) {
            return false;
        }
        for (String methodName : List.of("containsKey", "containsId")) {
            try {
                Method method = registry.getClass().getMethod(methodName, identifier.getClass());
                Object result = method.invoke(registry, identifier);
                return result instanceof Boolean value && value;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Try another registry API shape.
            }
        }
        return registryGetRaw(registry, identifier) != null;
    }

    private static <T> T registryGetValue(Object registry, Object identifier, Class<T> valueType) {
        Object value = registryGetRaw(registry, identifier);
        return valueType.isInstance(value) ? valueType.cast(value) : null;
    }

    private static Object registryGetRaw(Object registry, Object identifier) {
        if (registry == null || identifier == null) {
            return null;
        }
        for (String methodName : List.of("getValue", "get")) {
            try {
                Method method = registry.getClass().getMethod(methodName, identifier.getClass());
                Object value = method.invoke(registry, identifier);
                if (value instanceof Optional<?> optional) {
                    value = optional.orElse(null);
                }
                if (value != null && value.getClass().getName().endsWith("$Reference")) {
                    value = value.getClass().getMethod("value").invoke(value);
                }
                return value;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Try another registry API shape.
            }
        }
        return null;
    }

    private Optional<Object> parseBlockSelection(String normalized) {
        Object parsed = invokeObject(blockSelectionParseMethod, null, normalized);
        return parsed instanceof Optional<?> optional ? optional.map(value -> value) : Optional.empty();
    }

    private boolean matchesBlockSelection(Object selection, BlockState state) {
        return invokeBoolean(blockSelectionMatchesMethod, selection, state);
    }

    private Optional<BlockPos> findNearestBlock(Minecraft client, List<Object> selections, double range) {
        if (client == null || client.player == null || client.level == null || selections == null || selections.isEmpty()) {
            return Optional.empty();
        }
        int radius = Math.max(1, Math.min((int) Math.ceil(range), 64));
        BlockPos playerPos = client.player.blockPosition();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    BlockState state = client.level.getBlockState(mutable);
                    if (state == null || state.isAir()) {
                        continue;
                    }
                    boolean matches = false;
                    for (Object selection : selections) {
                        if (matchesBlockSelection(selection, state)) {
                            matches = true;
                            break;
                        }
                    }
                    if (!matches) {
                        continue;
                    }
                    double distance = mutable.distSqr(playerPos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = mutable.immutable();
                    }
                }
            }
        }

        return Optional.ofNullable(bestPos);
    }

    private Optional<ItemEntity> findNearestDroppedItemEntity(Minecraft client, Item item, double range) {
        if (client == null || client.player == null || client.level == null || item == null) {
            return Optional.empty();
        }
        double searchRadius = Math.max(1.0D, range);
        AABB searchBox = client.player.getBoundingBox().inflate(searchRadius);
        List<ItemEntity> entities = client.level.getEntitiesOfClass(
            ItemEntity.class,
            searchBox,
            entity -> entity != null && !entity.isRemoved() && !entity.getItem().isEmpty() && entity.getItem().is(item)
        );
        if (entities.isEmpty()) {
            return Optional.empty();
        }
        return entities.stream()
            .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(client.player)))
            .or(Optional::empty);
    }

    private int parseNavigatorCoordinate(String token, int base) {
        if (token == null) {
            throw new NumberFormatException("null coordinate");
        }
        if (!token.startsWith("~")) {
            return Integer.parseInt(token);
        }
        if (token.length() == 1) {
            return base;
        }
        return base + Integer.parseInt(token.substring(1));
    }

    private void handleNavigatorWaterMode(String modeToken) {
        if (modeToken == null) {
            showNavigatorMessage("Usage: !nav water <normal|avoid>");
            return;
        }
        if (modeToken.equalsIgnoreCase("avoid")) {
            invokeBridge("set Pathmind navigator water mode", () ->
                pathmindNavigatorSetWaterModeMethod.invoke(pathmindNavigator, pathmindNavigatorWaterModeAvoid));
            showNavigatorMessage("Pathmind Nav water mode: avoid");
            return;
        }
        if (modeToken.equalsIgnoreCase("normal") || modeToken.equalsIgnoreCase("allow")) {
            invokeBridge("set Pathmind navigator water mode", () ->
                pathmindNavigatorSetWaterModeMethod.invoke(pathmindNavigator, pathmindNavigatorWaterModeNormal));
            showNavigatorMessage("Pathmind Nav water mode: normal");
            return;
        }
        showNavigatorMessage("Usage: !nav water <normal|avoid>");
    }

    private void handleNavigatorLogs(String modeToken) {
        if (modeToken == null) {
            showNavigatorMessage("Usage: !nav logs <enable|disable>");
            return;
        }
        if (modeToken.equalsIgnoreCase("enable") || modeToken.equalsIgnoreCase("on")) {
            invokeBridge("enable Pathmind navigator logs", () -> pathmindNavigatorSetEventLoggingEnabledMethod.invoke(pathmindNavigator, true));
            showNavigatorMessage("Pathmind Nav logs enabled: .pathmind/logs/navigator-debug.log");
            return;
        }
        if (modeToken.equalsIgnoreCase("disable") || modeToken.equalsIgnoreCase("off")) {
            invokeBridge("disable Pathmind navigator logs", () -> pathmindNavigatorSetEventLoggingEnabledMethod.invoke(pathmindNavigator, false));
            showNavigatorMessage("Pathmind Nav logs disabled.");
            return;
        }
        showNavigatorMessage("Usage: !nav logs <enable|disable>");
    }

    private void handleNavigatorFlag(String flagName, String action) {
        if (flagName == null || action == null) {
            showNavigatorMessage("Usage: !flag <break|place> <enable|disable>");
            return;
        }
        boolean enable;
        if (action.equalsIgnoreCase("enable") || action.equalsIgnoreCase("on")) {
            enable = true;
        } else if (action.equalsIgnoreCase("disable") || action.equalsIgnoreCase("off")) {
            enable = false;
        } else {
            showNavigatorMessage("Usage: !flag <break|place> <enable|disable>");
            return;
        }

        if (flagName.equalsIgnoreCase("break") || flagName.equalsIgnoreCase("breaking")) {
            invokeBridge("set Pathmind navigator break flag", () -> pathmindNavigatorSetBlockBreakingAllowedMethod.invoke(pathmindNavigator, enable));
            showNavigatorMessage("Pathmind Nav flag break: " + (enable ? "enabled" : "disabled"));
            return;
        }
        if (flagName.equalsIgnoreCase("place") || flagName.equalsIgnoreCase("placing")) {
            invokeBridge("set Pathmind navigator place flag", () -> pathmindNavigatorSetBlockPlacingAllowedMethod.invoke(pathmindNavigator, enable));
            showNavigatorMessage("Pathmind Nav flag place: " + (enable ? "enabled" : "disabled"));
            return;
        }
        showNavigatorMessage("Usage: !flag <break|place> <enable|disable>");
    }

    private void showNavigatorMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        NodeErrorNotificationOverlay overlay = nodeErrorNotificationOverlay != null
            ? nodeErrorNotificationOverlay
            : NodeErrorNotificationOverlay.getInstance();
        overlay.show(message, NAVIGATOR_NOTIFICATION_COLOR);
    }

    private static void cancelEvent(Object event) {
        try {
            Method setCanceled = event.getClass().getMethod("setCanceled", boolean.class);
            setCanceled.invoke(event, true);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            try {
                Method setCancelled = event.getClass().getMethod("setCancelled", boolean.class);
                setCancelled.invoke(event, true);
            } catch (ReflectiveOperationException | LinkageError ignoredAgain) {
                // If this NeoForge event is not cancellable, the command still executes locally.
            }
        }
    }

    private static void addScreenInitButton(ScreenEvent.Init.Post event, Button button) {
        for (String methodName : List.of("addListener", "addWidget", "addRenderableWidget")) {
            for (Method method : event.getClass().getMethods()) {
                if (!methodName.equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameterType = method.getParameterTypes()[0];
                if (!parameterType.isInstance(button)) {
                    continue;
                }
                try {
                    method.invoke(event, button);
                    return;
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    // Try the next screen init API shape.
                }
            }
        }
    }

    private static void fireLoaderEvent(String eventName, String... compatibilityAliases) {
        FabricEventTracker.record(eventName, compatibilityAliases);
    }

    private Map<String, ExecutionManager.RuntimeVariable> createChatRuntimeVariables(String senderName, String rawMessage) {
        Map<String, ExecutionManager.RuntimeVariable> variables = new LinkedHashMap<>();
        variables.put(
            ExecutionManager.CHAT_SENDER_VARIABLE_NAME,
            createRuntimeVariable(NodeType.PARAM_PLAYER, "Player", senderName)
        );
        variables.put(
            ExecutionManager.CHAT_MESSAGE_VARIABLE_NAME,
            createRuntimeVariable(NodeType.PARAM_MESSAGE, "Text", rawMessage)
        );
        return variables;
    }

    private ExecutionManager.RuntimeVariable createRuntimeVariable(NodeType type, String key, String value) {
        Map<String, String> values = new LinkedHashMap<>();
        String safeValue = value == null ? "" : value;
        values.put(key, safeValue);
        values.put(key.toLowerCase(Locale.ROOT), safeValue);
        return new ExecutionManager.RuntimeVariable(type, values);
    }

    private static boolean isClientLevel(Level level) {
        return level != null && level.isClientSide();
    }

    private static boolean isLocalPlayer(Player player) {
        Minecraft client = Minecraft.getInstance();
        return client != null && player != null && client.player == player;
    }

    private static String extractChatSender(String text) {
        if (text == null) {
            return "Unknown";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("<")) {
            int end = trimmed.indexOf('>');
            if (end > 1) {
                return trimmed.substring(1, end).trim();
            }
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0 && colon <= 32) {
            return trimmed.substring(0, colon).trim();
        }
        return "Unknown";
    }

    private static boolean isNavigatorOverlayCollectionStage(RenderLevelStageEvent event) {
        if (event == null) {
            return false;
        }
        String simpleName = event.getClass().getSimpleName();
        if ("AfterEntities".equals(simpleName)) {
            return true;
        }
        try {
            Method getStageMethod = event.getClass().getMethod("getStage");
            Object stage = getStageMethod.invoke(event);
            return "AFTER_ENTITIES".equals(String.valueOf(stage));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private boolean isVisualEditorScreen(Screen screen) {
        if (screen == null || !commonBridgeReady) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isVisualEditorScreenMethod.invoke(null, screen));
        } catch (ReflectiveOperationException | LinkageError e) {
            logBridgeError("check Pathmind visual editor screen", e);
            return false;
        }
    }

    private void openVisualEditorOrWarn(Minecraft client, Screen parent) {
        if (client == null || !commonBridgeReady) {
            return;
        }
        invokeBridge("open Pathmind visual editor", () -> openVisualEditorOrWarnMethod.invoke(null, client, parent));
    }

    private void handlePendingClientLaunch(Minecraft client) {
        if (!pendingClientLaunch) {
            return;
        }
        if (client == null || client.getWindow() == null) {
            return;
        }
        pendingClientLaunch = false;
        launchStartNodes(StartLaunchMode.CLIENT_LAUNCH, null);
    }

    private void handlePendingWorldJoinLaunch(Minecraft client) {
        if (!pendingWorldJoinLaunch) {
            return;
        }
        if (client == null || client.player == null || client.level == null || client.getConnection() == null) {
            return;
        }
        pendingWorldJoinLaunch = false;
        invokeBridge("record Pathmind client join", () -> serverJoinTrackerRecordClientJoinMethod.invoke(null, client));
        launchStartNodes(StartLaunchMode.WORLD_JOIN, null);
    }

    private void handleScreenLaunchTriggers(Minecraft client) {
        if (client == null) {
            lastObservedScreen = null;
            return;
        }
        Screen currentScreen = client.screen;
        if (currentScreen == lastObservedScreen) {
            return;
        }
        lastObservedScreen = currentScreen;
        if (currentScreen == null) {
            return;
        }
        launchStartNodes(StartLaunchMode.SCREEN_OPENED, getScreenTargetKey(currentScreen));
        if (currentScreen instanceof TitleScreen) {
            launchStartNodes(StartLaunchMode.MAIN_MENU_OPEN, null);
        }
    }

    private void launchStartNodes(StartLaunchMode mode, String screenKey) {
        if (!commonBridgeReady) {
            return;
        }
        try {
            BackgroundStartRunner.getInstance().launch(mode, screenKey);
        } catch (RuntimeException | LinkageError e) {
            logBridgeError("launch Pathmind start nodes", e);
        }
    }

    private String getScreenTargetKey(Screen screen) {
        if (screen instanceof TitleScreen) {
            return "main_menu";
        }
        if (screen instanceof PauseScreen) {
            return "pause_menu";
        }
        if (screen instanceof ChatScreen) {
            return "chat";
        }
        if (screen instanceof InventoryScreen) {
            return "inventory";
        }
        if (screen instanceof MerchantScreen) {
            return "merchant";
        }
        if (isVisualEditorScreen(screen)) {
            return "visual_editor";
        }
        return screen == null ? "" : screen.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    private void handleClientShutdown() {
        handleClientShutdown(false);
    }

    private void handleClientShutdown(boolean force) {
        if (!force && worldShutdownHandled) {
            return;
        }
        worldShutdownHandled = true;
        requestStopAll();
    }

    private void requestStopAll() {
        invokeBridge("stop Pathmind graphs", () -> requestStopAllMethod.invoke(executionManager));
    }

    private void resetRecipeCacheWarmup() {
        invokeBridge("reset Pathmind recipe cache warmup", () -> recipeResetRecipeCacheWarmupMethod.invoke(null));
    }

    private boolean invokeBoolean(Method method, Object target, Object... args) {
        Object result = invokeObject(method, target, args);
        return result instanceof Boolean value && value;
    }

    private int invokeInt(Method method, Object target, Object... args) {
        Object result = invokeObject(method, target, args);
        return result instanceof Number value ? value.intValue() : 0;
    }

    private float invokeFloat(Method method, Object target, Object... args) {
        Object result = invokeObject(method, target, args);
        return result instanceof Number value ? value.floatValue() : 0.0f;
    }

    private Object invokeObject(Method method, Object target, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | LinkageError e) {
            logBridgeError("invoke Pathmind NeoForge bridge method", e);
            return null;
        }
    }

    private void invokeBridge(String action, BridgeCall call) {
        if (!commonBridgeReady) {
            return;
        }
        try {
            call.run();
        } catch (ReflectiveOperationException | LinkageError e) {
            logBridgeError(action, e);
        }
    }

    private void logBridgeError(String action, Throwable error) {
        if (bridgeErrorLogged) {
            return;
        }
        bridgeErrorLogged = true;
        PathmindCommon.LOGGER.warn("Pathmind NeoForge bridge failed while trying to {}; controls may be limited", action, error);
    }

    private static void invokeStaticNoArgs(String className, String methodName) throws ReflectiveOperationException {
        Class.forName(className).getMethod(methodName).invoke(null);
    }

    private static Object newInstance(String className) throws ReflectiveOperationException {
        return Class.forName(className).getDeclaredConstructor().newInstance();
    }

    @FunctionalInterface
    private interface BridgeCall {
        void run() throws ReflectiveOperationException;
    }
}
