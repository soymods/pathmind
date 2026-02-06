package com.pathmind;

import com.pathmind.data.PresetManager;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.nodes.Node;
import com.pathmind.screen.PathmindMainMenuIntegration;
import com.pathmind.screen.PathmindScreens;
import com.pathmind.ui.overlay.ActiveNodeOverlay;
import com.pathmind.ui.overlay.VariablesOverlay;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.ChatMessageTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        // Register client tick events for keybind handling
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleKeybinds(client);
            handleRecipeCacheWarmup(client);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            worldShutdownHandled = false;
            ChatMessageTracker.clear();
            recipeCacheWarmed = false;
            recipeCacheWarmupCooldownTicks = 0;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            handleClientShutdown("play disconnect", false);
            ChatMessageTracker.clear();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            handleClientShutdown("client stopping", true);
            ChatMessageTracker.clear();
        });

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (sender == null || message == null) {
                return;
            }
            long timestamp = receptionTimestamp != null ? receptionTimestamp.toEpochMilli() : System.currentTimeMillis();
            ChatMessageTracker.record(com.pathmind.util.GameProfileCompatibilityBridge.getName(sender), message.getString(), timestamp);
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
        });
        
        LOGGER.info("Pathmind client mod initialized successfully");
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
        boolean screenPauses = client.currentScreen != null && client.currentScreen.shouldPause();
        manager.setSingleplayerPaused(
            client.isInSingleplayer() && (client.isPaused() || screenPauses || editorOpen)
        );

        if (client.world == null) {
            if (!PathmindScreens.isVisualEditorScreen(client.currentScreen)) {
                handleClientShutdown("world unavailable", false);
            }
            return;
        }

        while (PathmindKeybinds.STOP_GRAPHS.wasPressed()) {
            ExecutionManager.getInstance().requestStopAll();
        }

        if (client.player == null) {
            return;
        }

        while (PathmindKeybinds.PLAY_GRAPHS.wasPressed()) {
            ExecutionManager.getInstance().playAllGraphs();
        }
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
        if (cached) {
            recipeCacheWarmed = true;
            LOGGER.info("Pathmind recipe cache populated from singleplayer recipes.");
        } else {
            recipeCacheWarmupCooldownTicks = 40;
        }
    }

}
