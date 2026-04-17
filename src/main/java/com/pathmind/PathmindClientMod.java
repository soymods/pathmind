package com.pathmind;

import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.execution.PathmindNavigator;
import com.pathmind.marketplace.MarketplaceAuthManager;
import com.pathmind.nodes.Node;
import com.pathmind.screen.PathmindMainMenuIntegration;
import com.pathmind.screen.PathmindScreens;
import com.pathmind.ui.overlay.ActiveNodeOverlay;
import com.pathmind.ui.overlay.NavigatorChatSuggestions;
import com.pathmind.ui.overlay.NavigatorDebugOverlay;
import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import com.pathmind.ui.overlay.VariablesOverlay;
import com.pathmind.util.ChatMessageTracker;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.FabricEventTracker;
import com.pathmind.util.MatrixStackBridge;
import com.pathmind.util.UseItemCallbackCompat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.C2SConfigurationChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The client-side mod class for Pathmind.
 * This class initializes client-specific features and event handlers.
 */
@SuppressWarnings({"deprecation", "removal"})
public class PathmindClientMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pathmind/Client");
    private static final String RECIPE_CACHE_NOTIFICATION_KEY = "recipe_cache_warmup";
    private static final int NAVIGATOR_NOTIFICATION_COLOR = 0xFF66D8FF;
    private static ActiveNodeOverlay activeNodeOverlay;
    private static NavigatorDebugOverlay navigatorDebugOverlay;
    private static NodeErrorNotificationOverlay nodeErrorNotificationOverlay;
    private static VariablesOverlay variablesOverlay;
    private volatile boolean worldShutdownHandled;
    private boolean recipeCacheWarmed;
    private int recipeCacheWarmupCooldownTicks;
    private boolean playGraphsKeyDown;
    private boolean stopGraphsKeyDown;

    private static final String EVT_CLIENT_BLOCK_ENTITY_LOAD = "fabric.client.lifecycle.block_entity_load";
    private static final String EVT_CLIENT_BLOCK_ENTITY_UNLOAD = "fabric.client.lifecycle.block_entity_unload";
    private static final String EVT_CLIENT_CHUNK_LOAD = "fabric.client.lifecycle.chunk_load";
    private static final String EVT_CLIENT_CHUNK_UNLOAD = "fabric.client.lifecycle.chunk_unload";
    private static final String EVT_CLIENT_ENTITY_LOAD = "fabric.client.lifecycle.entity_load";
    private static final String EVT_CLIENT_ENTITY_UNLOAD = "fabric.client.lifecycle.entity_unload";
    private static final String EVT_CLIENT_LIFECYCLE_STARTED = "fabric.client.lifecycle.client_started";
    private static final String EVT_CLIENT_LIFECYCLE_STOPPING = "fabric.client.lifecycle.client_stopping";
    private static final String EVT_CLIENT_TICK_END = "fabric.client.lifecycle.end_client_tick";
    private static final String EVT_CLIENT_TICK_START = "fabric.client.lifecycle.start_client_tick";
    private static final String EVT_CLIENT_WORLD_TICK_END = "fabric.client.lifecycle.end_world_tick";
    private static final String EVT_CLIENT_WORLD_TICK_START = "fabric.client.lifecycle.start_world_tick";

    private static final String EVT_CLIENT_CONFIG_CHANNEL_REGISTER = "fabric.client.networking.c2s_configuration_channel_register";
    private static final String EVT_CLIENT_CONFIG_CHANNEL_UNREGISTER = "fabric.client.networking.c2s_configuration_channel_unregister";
    private static final String EVT_CLIENT_PLAY_CHANNEL_REGISTER = "fabric.client.networking.c2s_play_channel_register";
    private static final String EVT_CLIENT_PLAY_CHANNEL_UNREGISTER = "fabric.client.networking.c2s_play_channel_unregister";
    private static final String EVT_CLIENT_CONFIGURATION_COMPLETE = "fabric.client.networking.configuration_connection_complete";
    private static final String EVT_CLIENT_CONFIGURATION_DISCONNECT = "fabric.client.networking.configuration_connection_disconnect";
    private static final String EVT_CLIENT_CONFIGURATION_INIT = "fabric.client.networking.configuration_connection_init";
    private static final String EVT_CLIENT_CONFIGURATION_START = "fabric.client.networking.configuration_connection_start";
    private static final String EVT_CLIENT_LOGIN_DISCONNECT = "fabric.client.networking.login_connection_disconnect";
    private static final String EVT_CLIENT_LOGIN_INIT = "fabric.client.networking.login_connection_init";
    private static final String EVT_CLIENT_LOGIN_QUERY_START = "fabric.client.networking.login_connection_query_start";
    private static final String EVT_CLIENT_PLAY_DISCONNECT = "fabric.client.networking.play_connection_disconnect";
    private static final String EVT_CLIENT_PLAY_INIT = "fabric.client.networking.play_connection_init";
    private static final String EVT_CLIENT_PLAY_JOIN = "fabric.client.networking.play_connection_join";

    private static final String EVT_MESSAGE_RECEIVE_ALLOW_CHAT = "fabric.client.message.receive_allow_chat";
    private static final String EVT_MESSAGE_RECEIVE_ALLOW_GAME = "fabric.client.message.receive_allow_game";
    private static final String EVT_MESSAGE_RECEIVE_CHAT = "fabric.client.message.receive_chat";
    private static final String EVT_MESSAGE_RECEIVE_CHAT_CANCELED = "fabric.client.message.receive_chat_canceled";
    private static final String EVT_MESSAGE_RECEIVE_GAME = "fabric.client.message.receive_game";
    private static final String EVT_MESSAGE_RECEIVE_GAME_CANCELED = "fabric.client.message.receive_game_canceled";
    private static final String EVT_MESSAGE_RECEIVE_MODIFY_GAME = "fabric.client.message.receive_modify_game";
    private static final String EVT_MESSAGE_SEND_ALLOW_CHAT = "fabric.client.message.send_allow_chat";
    private static final String EVT_MESSAGE_SEND_ALLOW_COMMAND = "fabric.client.message.send_allow_command";
    private static final String EVT_MESSAGE_SEND_CHAT = "fabric.client.message.send_chat";
    private static final String EVT_MESSAGE_SEND_CHAT_CANCELED = "fabric.client.message.send_chat_canceled";
    private static final String EVT_MESSAGE_SEND_COMMAND = "fabric.client.message.send_command";
    private static final String EVT_MESSAGE_SEND_COMMAND_CANCELED = "fabric.client.message.send_command_canceled";
    private static final String EVT_MESSAGE_SEND_MODIFY_CHAT = "fabric.client.message.send_modify_chat";
    private static final String EVT_MESSAGE_SEND_MODIFY_COMMAND = "fabric.client.message.send_modify_command";

    private static final String EVT_RENDER_HUD = "fabric.client.render.hud";
    private static final String EVT_PLAYER_ATTACK_BLOCK = "fabric.player.attack_block";
    private static final String EVT_PLAYER_ATTACK_ENTITY = "fabric.player.attack_entity";
    private static final String EVT_PLAYER_USE_BLOCK = "fabric.player.use_block";
    private static final String EVT_PLAYER_USE_ENTITY = "fabric.player.use_entity";
    private static final String EVT_PLAYER_USE_ITEM = "fabric.player.use_item";

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Pathmind client mod");

        PresetManager.initialize();
        MarketplaceAuthManager.initialize();
        activeNodeOverlay = new ActiveNodeOverlay();
        navigatorDebugOverlay = new NavigatorDebugOverlay();
        nodeErrorNotificationOverlay = NodeErrorNotificationOverlay.getInstance();
        variablesOverlay = new VariablesOverlay();

        // Register keybindings
        PathmindKeybinds.registerKeybinds();
        KeyBindingHelper.registerKeyBinding(PathmindKeybinds.OPEN_VISUAL_EDITOR);
        KeyBindingHelper.registerKeyBinding(PathmindKeybinds.PLAY_GRAPHS);
        KeyBindingHelper.registerKeyBinding(PathmindKeybinds.STOP_GRAPHS);

        // Hook into the main menu for button and keyboard support
        PathmindMainMenuIntegration.register();

        registerFabricEventForwarders();

        // Register client tick events for keybind handling and event forwarding
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleKeybinds(client);
            handleRecipeCacheWarmup(client);
            NavigatorChatSuggestions.getInstance().tick(client);
            PathmindNavigator.getInstance().tick(client);
            fireFabricEvent(EVT_CLIENT_TICK_END);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            worldShutdownHandled = false;
            ChatMessageTracker.clear();
            FabricEventTracker.clear();
            if (nodeErrorNotificationOverlay != null) {
                nodeErrorNotificationOverlay.clear();
            }
            Node.resetRecipeCacheWarmup();
            recipeCacheWarmed = false;
            recipeCacheWarmupCooldownTicks = 0;
            fireFabricEvent(EVT_CLIENT_PLAY_JOIN);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            handleClientShutdown("play disconnect", false);
            PathmindNavigator.getInstance().reset();
            ChatMessageTracker.clear();
            FabricEventTracker.clear();
            if (nodeErrorNotificationOverlay != null) {
                nodeErrorNotificationOverlay.clear();
            }
            Node.resetRecipeCacheWarmup();
            fireFabricEvent(EVT_CLIENT_PLAY_DISCONNECT);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            handleClientShutdown("client stopping", true);
            PathmindNavigator.getInstance().reset();
            ChatMessageTracker.clear();
            FabricEventTracker.clear();
            if (nodeErrorNotificationOverlay != null) {
                nodeErrorNotificationOverlay.clear();
            }
            Node.resetRecipeCacheWarmup();
            fireFabricEvent(EVT_CLIENT_LIFECYCLE_STOPPING);
        });

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (sender == null || message == null) {
                fireFabricEvent(EVT_MESSAGE_RECEIVE_CHAT);
                return;
            }
            long timestamp = receptionTimestamp != null ? receptionTimestamp.toEpochMilli() : System.currentTimeMillis();
            ChatMessageTracker.record(com.pathmind.util.GameProfileCompatibilityBridge.getName(sender), message.getString(), timestamp);
            fireFabricEvent(EVT_MESSAGE_RECEIVE_CHAT);
        });
        
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            fireFabricEvent(EVT_RENDER_HUD);
        });
        
        LOGGER.info("Pathmind client mod initialized successfully");
    }

    public static void renderHudOverlays(DrawContext drawContext, MinecraftClient client) {
        if (client == null || client.player == null || client.textRenderer == null) {
            return;
        }

        boolean showHudOverlays = SettingsManager.getCurrent().showHudOverlays == null
            || SettingsManager.getCurrent().showHudOverlays;
        if (!showHudOverlays) {
            return;
        }

        int scaledWidth = client.getWindow().getScaledWidth();
        int scaledHeight = client.getWindow().getScaledHeight();
        DrawContextBridge.startNewRootLayer(drawContext);
        Object matrices = drawContext.getMatrices();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.translateZ(matrices, 500.0f);

        try {
            if (activeNodeOverlay != null) {
                activeNodeOverlay.render(drawContext, client.textRenderer, scaledWidth, scaledHeight);
            }
            if (variablesOverlay != null) {
                variablesOverlay.render(drawContext, client.textRenderer, scaledWidth, scaledHeight);
            }
            if (navigatorDebugOverlay != null) {
                navigatorDebugOverlay.render(drawContext, client.textRenderer, scaledWidth, scaledHeight);
            }
            if (nodeErrorNotificationOverlay != null) {
                nodeErrorNotificationOverlay.render(drawContext, client.textRenderer, scaledWidth, scaledHeight);
            }
        } finally {
            MatrixStackBridge.pop(matrices);
        }
    }

    private void registerFabricEventForwarders() {
        // Client lifecycle and world lifecycle events (ordered by API class / field name)
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> fireFabricEvent(EVT_CLIENT_BLOCK_ENTITY_LOAD));
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, world) -> fireFabricEvent(EVT_CLIENT_BLOCK_ENTITY_UNLOAD));
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> fireFabricEvent(EVT_CLIENT_CHUNK_LOAD));
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> fireFabricEvent(EVT_CLIENT_CHUNK_UNLOAD));
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> fireFabricEvent(EVT_CLIENT_ENTITY_LOAD));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> fireFabricEvent(EVT_CLIENT_ENTITY_UNLOAD));
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> fireFabricEvent(EVT_CLIENT_LIFECYCLE_STARTED));
        ClientTickEvents.START_CLIENT_TICK.register(client -> fireFabricEvent(EVT_CLIENT_TICK_START));
        ClientTickEvents.START_WORLD_TICK.register(world -> fireFabricEvent(EVT_CLIENT_WORLD_TICK_START));
        ClientTickEvents.END_WORLD_TICK.register(world -> fireFabricEvent(EVT_CLIENT_WORLD_TICK_END));
        // Client networking events
        C2SConfigurationChannelEvents.REGISTER.register((handler, sender, client, channels) -> fireFabricEvent(EVT_CLIENT_CONFIG_CHANNEL_REGISTER));
        C2SConfigurationChannelEvents.UNREGISTER.register((handler, sender, client, channels) -> fireFabricEvent(EVT_CLIENT_CONFIG_CHANNEL_UNREGISTER));
        C2SPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> fireFabricEvent(EVT_CLIENT_PLAY_CHANNEL_REGISTER));
        C2SPlayChannelEvents.UNREGISTER.register((handler, sender, client, channels) -> fireFabricEvent(EVT_CLIENT_PLAY_CHANNEL_UNREGISTER));
        ClientConfigurationConnectionEvents.INIT.register((handler, client) -> fireFabricEvent(EVT_CLIENT_CONFIGURATION_INIT));
        ClientConfigurationConnectionEvents.START.register((handler, client) -> fireFabricEvent(EVT_CLIENT_CONFIGURATION_START));
        ClientConfigurationConnectionEvents.COMPLETE.register((handler, client) -> fireFabricEvent(EVT_CLIENT_CONFIGURATION_COMPLETE));
        ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) -> fireFabricEvent(EVT_CLIENT_CONFIGURATION_DISCONNECT));
        ClientLoginConnectionEvents.INIT.register((handler, client) -> fireFabricEvent(EVT_CLIENT_LOGIN_INIT));
        ClientLoginConnectionEvents.QUERY_START.register((handler, client) -> fireFabricEvent(EVT_CLIENT_LOGIN_QUERY_START));
        ClientLoginConnectionEvents.DISCONNECT.register((handler, client) -> fireFabricEvent(EVT_CLIENT_LOGIN_DISCONNECT));
        ClientPlayConnectionEvents.INIT.register((handler, client) -> fireFabricEvent(EVT_CLIENT_PLAY_INIT));

        // Client message events
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            fireFabricEvent(EVT_MESSAGE_RECEIVE_ALLOW_CHAT);
            return true;
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            fireFabricEvent(EVT_MESSAGE_RECEIVE_ALLOW_GAME);
            return true;
        });
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            fireFabricEvent(EVT_MESSAGE_RECEIVE_MODIFY_GAME);
            return message;
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> fireFabricEvent(EVT_MESSAGE_RECEIVE_GAME));
        ClientReceiveMessageEvents.CHAT_CANCELED.register((message, signedMessage, sender, params, receptionTimestamp) ->
            fireFabricEvent(EVT_MESSAGE_RECEIVE_CHAT_CANCELED));
        ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> fireFabricEvent(EVT_MESSAGE_RECEIVE_GAME_CANCELED));

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            fireFabricEvent(EVT_MESSAGE_SEND_ALLOW_CHAT);
            if (handlePathmindNavigatorChat(message)) {
                return false;
            }
            return true;
        });
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            fireFabricEvent(EVT_MESSAGE_SEND_ALLOW_COMMAND);
            if (handlePathmindNavigatorCommand(command)) {
                return false;
            }
            return true;
        });
        ClientSendMessageEvents.MODIFY_CHAT.register(message -> {
            fireFabricEvent(EVT_MESSAGE_SEND_MODIFY_CHAT);
            return message;
        });
        ClientSendMessageEvents.MODIFY_COMMAND.register(command -> {
            fireFabricEvent(EVT_MESSAGE_SEND_MODIFY_COMMAND);
            return command;
        });
        ClientSendMessageEvents.CHAT.register(message -> fireFabricEvent(EVT_MESSAGE_SEND_CHAT));
        ClientSendMessageEvents.COMMAND.register(command -> fireFabricEvent(EVT_MESSAGE_SEND_COMMAND));
        ClientSendMessageEvents.CHAT_CANCELED.register(message -> fireFabricEvent(EVT_MESSAGE_SEND_CHAT_CANCELED));
        ClientSendMessageEvents.COMMAND_CANCELED.register(command -> fireFabricEvent(EVT_MESSAGE_SEND_COMMAND_CANCELED));

        // Player interaction events
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            fireFabricEvent(EVT_PLAYER_ATTACK_BLOCK);
            return ActionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            fireFabricEvent(EVT_PLAYER_ATTACK_ENTITY);
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            fireFabricEvent(EVT_PLAYER_USE_BLOCK);
            return ActionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            fireFabricEvent(EVT_PLAYER_USE_ENTITY);
            return ActionResult.PASS;
        });
        UseItemCallbackCompat.register(this::fireFabricEvent, EVT_PLAYER_USE_ITEM);
    }

    private void fireFabricEvent(String eventName) {
        if (eventName == null || eventName.isEmpty()) {
            return;
        }
        FabricEventTracker.record(eventName);
    }

    private void handleClientShutdown(String reason) {
        handleClientShutdown(reason, false);
    }

    private void handleClientShutdown(String reason, boolean force) {
        if (!force && worldShutdownHandled) {
            return;
        }
        worldShutdownHandled = true;
        LOGGER.info("Pathmind: handling client shutdown due to {}", reason);
        ExecutionManager.getInstance().requestStopAll();
    }

    private void handleKeybinds(MinecraftClient client) {
        if (client == null) {
            return;
        }

        // Check if visual editor keybind was pressed (Title screen only)
        while (PathmindKeybinds.OPEN_VISUAL_EDITOR.wasPressed()) {
            if (client.currentScreen != null && !(client.currentScreen instanceof TitleScreen)) {
                continue;
            }
            PathmindScreens.openVisualEditorOrWarn(client, client.currentScreen);
        }

        ExecutionManager manager = ExecutionManager.getInstance();
        boolean editorOpen = PathmindScreens.isVisualEditorScreen(client.currentScreen);
        // Allow execution to continue while GUIs are open so key-pressed sensors can fire.
        manager.setSingleplayerPaused(client.isInSingleplayer() && editorOpen);

        if (client.world == null) {
            if (!PathmindScreens.isVisualEditorScreen(client.currentScreen)) {
                handleClientShutdown("world unavailable", false);
            }
            return;
        }

        // Don't handle k/j keybinds when chat or Pathmind GUI is open
        boolean chatOrGuiOpen = shouldIgnoreKeybinds(client);

        boolean stopDown = PathmindKeybinds.STOP_GRAPHS.isPressed();
        if (!chatOrGuiOpen && stopDown && !stopGraphsKeyDown) {
            ExecutionManager.getInstance().requestStopAll();
        }
        stopGraphsKeyDown = stopDown;

        if (client.player == null) {
            return;
        }

        boolean playDown = PathmindKeybinds.PLAY_GRAPHS.isPressed();
        if (!chatOrGuiOpen && playDown && !playGraphsKeyDown) {
            ExecutionManager.getInstance().playAllGraphs();
        }
        playGraphsKeyDown = playDown;
    }

    private boolean shouldIgnoreKeybinds(MinecraftClient client) {
        if (client == null || client.currentScreen == null) {
            return false;
        }
        // Check if chat screen is open
        if (client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen) {
            return true;
        }
        // Check if Pathmind visual editor is open
        if (PathmindScreens.isVisualEditorScreen(client.currentScreen)) {
            return true;
        }
        return false;
    }

    private void handleRecipeCacheWarmup(MinecraftClient client) {
        if (client == null || !client.isInSingleplayer()) {
            if (nodeErrorNotificationOverlay != null) {
                nodeErrorNotificationOverlay.dismiss(RECIPE_CACHE_NOTIFICATION_KEY);
            }
            return;
        }
        if (recipeCacheWarmed) {
            if (nodeErrorNotificationOverlay != null) {
                nodeErrorNotificationOverlay.dismiss(RECIPE_CACHE_NOTIFICATION_KEY);
            }
            return;
        }
        if (recipeCacheWarmupCooldownTicks > 0) {
            recipeCacheWarmupCooldownTicks--;
            return;
        }
        if (client.getServer() == null) {
            recipeCacheWarmupCooldownTicks = 20;
            return;
        }
        boolean cached = Node.warmRecipeCache(client);
        Node.RecipeCacheWarmupProgress progress = Node.getRecipeCacheWarmupProgress(client);
        if (progress != null && nodeErrorNotificationOverlay != null) {
            nodeErrorNotificationOverlay.showProgress(
                RECIPE_CACHE_NOTIFICATION_KEY,
                "Caching recipes\n" + progress.completed() + " / " + progress.total(),
                com.pathmind.ui.theme.UITheme.ACCENT_SKY,
                progress.fraction()
            );
        }
        if (cached) {
            recipeCacheWarmed = true;
            if (nodeErrorNotificationOverlay != null) {
                nodeErrorNotificationOverlay.dismiss(RECIPE_CACHE_NOTIFICATION_KEY);
                nodeErrorNotificationOverlay.show("Recipe cache ready.", com.pathmind.ui.theme.UITheme.ACCENT_SKY);
            }
            LOGGER.info("Pathmind recipe cache populated from singleplayer recipes.");
        } else if (!Node.isRecipeCacheWarmupInProgress(client)) {
            recipeCacheWarmupCooldownTicks = 100;
            if (nodeErrorNotificationOverlay != null) {
                nodeErrorNotificationOverlay.dismiss(RECIPE_CACHE_NOTIFICATION_KEY);
            }
            LOGGER.debug("Pathmind recipe cache warmup attempted but no recipes found.");
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
        MinecraftClient client = MinecraftClient.getInstance();
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
            PathmindNavigator.getInstance().stop("chat stop");
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
            handleNavigatorDebug();
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

        showNavigatorMessage("Unknown Pathmind Nav command. Use !travel, !path, !nav debug, !nav water, !nav logs, !flag, or !stop.");
        return true;
    }

    private void handleNavigatorGoto(MinecraftClient client, String[] parts) {
        if (client == null || client.player == null || client.world == null) {
            showNavigatorMessage("Pathmind Nav is unavailable right now.");
            return;
        }

        BlockPos targetPos = parseNavigatorTarget(client, parts, 1, "!travel");
        if (targetPos == null) {
            return;
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        if (!PathmindNavigator.getInstance().startGoto(targetPos, "Chat Travel", future)) {
            showNavigatorMessage("Could not start Pathmind Nav.");
            return;
        }
        showNavigatorMessage("Pathmind Nav: heading to " + targetPos.getX() + " " + targetPos.getY() + " " + targetPos.getZ());
    }

    private void handleNavigatorPathPreview(MinecraftClient client, String[] parts) {
        if (client == null || client.player == null || client.world == null) {
            showNavigatorMessage("Pathmind Nav is unavailable right now.");
            return;
        }

        BlockPos targetPos = parseNavigatorTarget(client, parts, 1, "!path");
        if (targetPos == null) {
            return;
        }

        PathmindNavigator.PreviewResult result = PathmindNavigator.getInstance().previewPath(client, targetPos, "Path Preview");
        showNavigatorMessage(result.message());
    }

    private void handleNavigatorDebug() {
        boolean enabled = navigatorDebugOverlay != null && navigatorDebugOverlay.toggle();
        showNavigatorMessage(enabled ? "Pathmind Nav debug overlay enabled." : "Pathmind Nav debug overlay disabled.");
    }

    private BlockPos parseNavigatorTarget(MinecraftClient client, String[] parts, int coordinateStartIndex, String usageCommand) {
        if (client == null || client.player == null) {
            showNavigatorMessage("Pathmind Nav is unavailable right now.");
            return null;
        }

        int remaining = parts.length - coordinateStartIndex;
        try {
            if (remaining == 2) {
                int x = parseNavigatorCoordinate(parts[coordinateStartIndex], client.player.getBlockX(), false);
                int z = parseNavigatorCoordinate(parts[coordinateStartIndex + 1], client.player.getBlockZ(), false);
                return new BlockPos(x, client.player.getBlockY(), z);
            }
            if (remaining == 3) {
                int x = parseNavigatorCoordinate(parts[coordinateStartIndex], client.player.getBlockX(), false);
                int y = parseNavigatorCoordinate(parts[coordinateStartIndex + 1], client.player.getBlockY(), false);
                int z = parseNavigatorCoordinate(parts[coordinateStartIndex + 2], client.player.getBlockZ(), false);
                return new BlockPos(x, y, z);
            }
        } catch (NumberFormatException exception) {
            showNavigatorMessage("Invalid coordinates for " + usageCommand + ".");
            return null;
        }

        showNavigatorMessage("Usage: " + usageCommand + " <x> <y> <z> or " + usageCommand + " <x> <z>");
        return null;
    }

    private int parseNavigatorCoordinate(String token, int base, boolean normalizeAbsoluteHorizontal) {
        if (token == null) {
            throw new NumberFormatException("null coordinate");
        }
        if (!token.startsWith("~")) {
            int absolute = Integer.parseInt(token);
            return absolute;
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
            PathmindNavigator.getInstance().setWaterMode(PathmindNavigator.WaterMode.AVOID);
            showNavigatorMessage("Pathmind Nav water mode: avoid");
            return;
        }
        if (modeToken.equalsIgnoreCase("normal") || modeToken.equalsIgnoreCase("allow")) {
            PathmindNavigator.getInstance().setWaterMode(PathmindNavigator.WaterMode.NORMAL);
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
            PathmindNavigator.getInstance().setEventLoggingEnabled(true);
            showNavigatorMessage("Pathmind Nav logs enabled: logs/navigator-debug.log");
            return;
        }
        if (modeToken.equalsIgnoreCase("disable") || modeToken.equalsIgnoreCase("off")) {
            PathmindNavigator.getInstance().setEventLoggingEnabled(false);
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

        PathmindNavigator navigator = PathmindNavigator.getInstance();
        if (flagName.equalsIgnoreCase("break") || flagName.equalsIgnoreCase("breaking")) {
            navigator.setBlockBreakingAllowed(enable);
            showNavigatorMessage("Pathmind Nav flag break: " + (enable ? "enabled" : "disabled"));
            return;
        }
        if (flagName.equalsIgnoreCase("place") || flagName.equalsIgnoreCase("placing")) {
            navigator.setBlockPlacingAllowed(enable);
            showNavigatorMessage("Pathmind Nav flag place: " + (enable ? "enabled" : "disabled"));
            return;
        }
        showNavigatorMessage("Usage: !flag <break|place> <enable|disable>");
    }

    private String formatDebugPos(BlockPos pos) {
        if (pos == null) {
            return "--";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private String sanitizeDebugText(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.replace(' ', '_');
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

}
