package com.pathmind;

import com.pathmind.data.PresetManager;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.nodes.Node;
import com.pathmind.screen.PathmindMainMenuIntegration;
import com.pathmind.screen.PathmindScreens;
import com.pathmind.ui.overlay.ActiveNodeOverlay;
import com.pathmind.ui.overlay.VariablesOverlay;
import com.pathmind.ui.control.VillagerTradeSelector;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.ChatMessageTracker;
import com.pathmind.util.FabricEventTracker;
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
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * The client-side mod class for Pathmind.
 * This class initializes client-specific features and event handlers.
 */
public class PathmindClientMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pathmind/Client");
    private ActiveNodeOverlay activeNodeOverlay;
    private VariablesOverlay variablesOverlay;
    private volatile boolean worldShutdownHandled;
    private boolean baritoneAvailable;
    private boolean recipeCacheWarmed;
    private int recipeCacheWarmupCooldownTicks;
    private boolean playGraphsKeyDown;
    private boolean stopGraphsKeyDown;
    private boolean merchantScreenOpen;
    private boolean villagerTradeCacheWarmed;
    private int villagerTradeCacheCooldownTicks;

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
    private static final String EVT_CLIENT_WORLD_AFTER_CHANGE = "fabric.client.lifecycle.after_client_world_change";
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
        baritoneAvailable = BaritoneDependencyChecker.isBaritonePresent();
        this.activeNodeOverlay = new ActiveNodeOverlay();
        this.variablesOverlay = new VariablesOverlay();

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
            handleMerchantTradeCache(client);
            handleSingleplayerTradeCache(client);
            fireFabricEvent(EVT_CLIENT_TICK_END);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            worldShutdownHandled = false;
            ChatMessageTracker.clear();
            FabricEventTracker.clear();
            recipeCacheWarmed = false;
            recipeCacheWarmupCooldownTicks = 0;
            villagerTradeCacheWarmed = false;
            villagerTradeCacheCooldownTicks = 60;
            fireFabricEvent(EVT_CLIENT_PLAY_JOIN);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            handleClientShutdown("play disconnect", false);
            ChatMessageTracker.clear();
            FabricEventTracker.clear();
            fireFabricEvent(EVT_CLIENT_PLAY_DISCONNECT);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            handleClientShutdown("client stopping", true);
            ChatMessageTracker.clear();
            FabricEventTracker.clear();
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
        
        // Register HUD render callback for the active node overlay
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.textRenderer != null) {
                if (activeNodeOverlay != null) {
                    activeNodeOverlay.render(drawContext, client.textRenderer, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
                }
                if (variablesOverlay != null) {
                    variablesOverlay.render(drawContext, client.textRenderer, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
                }
            }
            fireFabricEvent(EVT_RENDER_HUD);
        });
        
        LOGGER.info("Pathmind client mod initialized successfully");
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
            return true;
        });
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            fireFabricEvent(EVT_MESSAGE_SEND_ALLOW_COMMAND);
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
        registerUseItemCallbackCompat();
    }

    private void fireFabricEvent(String eventName) {
        if (eventName == null || eventName.isEmpty()) {
            return;
        }
        FabricEventTracker.record(eventName);
    }

    private void registerUseItemCallbackCompat() {
        try {
            Method registerMethod = null;
            for (Method method : UseItemCallback.EVENT.getClass().getMethods()) {
                if ("register".equals(method.getName()) && method.getParameterCount() == 1) {
                    registerMethod = method;
                    break;
                }
            }
            if (registerMethod == null) {
                LOGGER.warn("Pathmind: could not find UseItemCallback register method; use-item event tracking disabled");
                return;
            }

            Class<?> callbackType = registerMethod.getParameterTypes()[0];
            InvocationHandler handler = (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> "PathmindUseItemCallbackCompat";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                }

                fireFabricEvent(EVT_PLAYER_USE_ITEM);
                Class<?> returnType = method.getReturnType();
                if (returnType == void.class) {
                    return null;
                }
                if (returnType == ActionResult.class) {
                    return ActionResult.PASS;
                }
                if ("net.minecraft.util.TypedActionResult".equals(returnType.getName())) {
                    Object stack = null;
                    if (args != null && args.length >= 3 && args[0] != null && args[2] != null) {
                        try {
                            Method getStackInHand = args[0].getClass().getMethod("getStackInHand", args[2].getClass());
                            stack = getStackInHand.invoke(args[0], args[2]);
                        } catch (ReflectiveOperationException ignored) {
                        }
                    }
                    for (Method candidate : returnType.getMethods()) {
                        if (!Modifier.isStatic(candidate.getModifiers())) {
                            continue;
                        }
                        if (!"pass".equals(candidate.getName()) || candidate.getParameterCount() != 1) {
                            continue;
                        }
                        try {
                            return candidate.invoke(null, stack);
                        } catch (ReflectiveOperationException ignored) {
                        }
                    }
                }
                return ActionResult.PASS;
            };

            Object callback = Proxy.newProxyInstance(
                callbackType.getClassLoader(),
                new Class<?>[]{callbackType},
                handler
            );
            registerMethod.invoke(UseItemCallback.EVENT, callback);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Pathmind: failed to register use-item callback compatibility bridge", e);
        }
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
        if (baritoneAvailable) {
            ExecutionManager.getInstance().requestStopAll();
        }
    }

    private void handleMerchantTradeCache(MinecraftClient client) {
        boolean isMerchantScreen = client != null && client.currentScreen instanceof MerchantScreen;
        if (isMerchantScreen && !merchantScreenOpen) {
            VillagerTradeSelector.cacheOpenMerchantTrades();
        }
        merchantScreenOpen = isMerchantScreen;
    }

    private void handleSingleplayerTradeCache(MinecraftClient client) {
        if (villagerTradeCacheWarmed) {
            return;
        }
        if (client == null || !client.isInSingleplayer()) {
            return;
        }
        if (client.world == null || client.getServer() == null) {
            return;
        }
        if (villagerTradeCacheCooldownTicks > 0) {
            villagerTradeCacheCooldownTicks--;
            return;
        }
        VillagerTradeSelector.cacheAllProfessionTrades(client);
        villagerTradeCacheWarmed = true;
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
        if (client == null || recipeCacheWarmed || !client.isInSingleplayer()) {
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
        recipeCacheWarmed = true;
        if (cached) {
            LOGGER.info("Pathmind recipe cache populated from singleplayer recipes.");
        } else {
            LOGGER.debug("Pathmind recipe cache warmup attempted but no recipes found.");
        }
    }

}
