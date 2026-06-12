package com.pathmind.neoforge;

import com.pathmind.PathmindCommon;
import com.pathmind.execution.BackgroundStartRunner;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.StartLaunchMode;
import com.pathmind.util.ChatMessageTracker;
import com.pathmind.util.FabricEventTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Mod(PathmindCommon.MOD_ID)
public class PathmindNeoForge {
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
    private Method playAllGraphsMethod;
    private Method openVisualEditorOrWarnMethod;
    private Method isVisualEditorScreenMethod;
    private Object navigatorChatSuggestions;
    private Method navigatorChatSuggestionsTickMethod;
    private Object pathmindNavigator;
    private Method pathmindNavigatorIsActiveMethod;
    private Method pathmindNavigatorTickMethod;
    private Method pathmindNavigatorResetMethod;
    private Method inputTickNoArgsMethod;
    private Method inputTickLegacyMethod;
    private Method serverJoinTrackerRecordClientJoinMethod;
    private Method serverJoinTrackerTickMethod;
    private Method serverJoinTrackerClearMethod;
    private Object nodeErrorNotificationOverlay;
    private Method nodeErrorNotificationClearMethod;
    private Method renderHudOverlaysMethod;
    private Method renderHudNotificationsMethod;
    private boolean commonBridgeReady;
    private boolean bridgeErrorLogged;
    private boolean hudRenderErrorLogged;
    private boolean worldOverlayErrorLogged;
    private boolean worldShutdownHandled;
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
        Object navigatorDebugOverlay = newInstance("com.pathmind.ui.overlay.NavigatorDebugOverlay");
        Class<?> nodeErrorClass = Class.forName("com.pathmind.ui.overlay.NodeErrorNotificationOverlay");
        nodeErrorNotificationOverlay = nodeErrorClass.getMethod("getInstance").invoke(null);
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
        playAllGraphsMethod = executionManagerClass.getMethod("playAllGraphs");

        Class<?> suggestionsClass = Class.forName("com.pathmind.ui.overlay.NavigatorChatSuggestions");
        navigatorChatSuggestions = suggestionsClass.getMethod("getInstance").invoke(null);
        navigatorChatSuggestionsTickMethod = suggestionsClass.getMethod("tick", Minecraft.class);

        Class<?> navigatorClass = Class.forName("com.pathmind.execution.PathmindNavigator");
        pathmindNavigator = navigatorClass.getMethod("getInstance").invoke(null);
        pathmindNavigatorIsActiveMethod = navigatorClass.getMethod("isActive");
        pathmindNavigatorTickMethod = navigatorClass.getMethod("tick", Minecraft.class);
        pathmindNavigatorResetMethod = navigatorClass.getMethod("reset");

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
            invokeBridge("play Pathmind graphs", () -> playAllGraphsMethod.invoke(executionManager));
        }
        playGraphsKeyDown = playDown;
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
        return client.screen instanceof ChatScreen || isVisualEditorScreen(client.screen);
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
