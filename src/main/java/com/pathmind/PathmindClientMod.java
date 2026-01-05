package com.pathmind;

import com.pathmind.data.PresetManager;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.screen.PathmindMainMenuIntegration;
import com.pathmind.screen.PathmindScreens;
import com.pathmind.ui.overlay.ActiveNodeOverlay;
import com.pathmind.ui.overlay.VariablesOverlay;
import com.pathmind.util.BaritoneDependencyChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Pathmind client mod");

        PresetManager.initialize();
        baritoneAvailable = BaritoneDependencyChecker.isBaritoneApiPresent();
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
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            worldShutdownHandled = false;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            handleClientShutdown("play disconnect", false);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            handleClientShutdown("client stopping", true);
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

        if (!baritoneAvailable) {
            return;
        }

        ExecutionManager manager = ExecutionManager.getInstance();
        manager.setSingleplayerPaused(
            client.isInSingleplayer() && client.isPaused()
        );

        if (client.world == null) {
            handleClientShutdown("world unavailable", false);
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

}
