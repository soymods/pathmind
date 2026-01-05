package com.pathmind.screen;

import com.pathmind.PathmindMod;
import com.pathmind.util.BaritoneDependencyChecker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.lang.reflect.Constructor;

/**
 * Centralized helpers for opening Pathmind screens without crashing when dependencies are missing.
 */
public final class PathmindScreens {
    private static final String VISUAL_EDITOR_CLASS = "com.pathmind.screen.PathmindVisualEditorScreen";

    private PathmindScreens() {
    }

    /**
     * Opens the visual editor if Baritone is present, otherwise shows a warning screen.
     *
     * @param client Minecraft client instance
     * @param parent Screen to return to when closing the warning
     */
    public static void openVisualEditorOrWarn(MinecraftClient client, Screen parent) {
        if (client == null) {
            return;
        }

        if (isVisualEditor(client.currentScreen)) {
            return;
        }

        try {
            client.setScreen(instantiateVisualEditor());
        } catch (ReflectiveOperationException | LinkageError e) {
            PathmindMod.LOGGER.error("Failed to open Pathmind visual editor due to missing Baritone classes", e);
            BaritoneDependencyChecker.markUnavailable();
            showMissingScreen(client, parent);
        }
    }

    public static void showMissingScreen(MinecraftClient client) {
        showMissingScreen(client, client != null ? client.currentScreen : null);
    }

    private static void showMissingScreen(MinecraftClient client, Screen parent) {
        if (client == null) {
            return;
        }

        if (!(client.currentScreen instanceof MissingBaritoneApiScreen)) {
            client.setScreen(new MissingBaritoneApiScreen(parent));
        }
    }

    private static boolean isVisualEditor(Screen currentScreen) {
        return currentScreen != null && VISUAL_EDITOR_CLASS.equals(currentScreen.getClass().getName());
    }

    private static Screen instantiateVisualEditor() throws ReflectiveOperationException {
        Class<?> clazz = Class.forName(VISUAL_EDITOR_CLASS);
        if (!Screen.class.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Pathmind visual editor screen does not extend Screen");
        }

        Constructor<? extends Screen> ctor = clazz.asSubclass(Screen.class).getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }
}
