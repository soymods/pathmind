package com.pathmind.screen;

import com.pathmind.PathmindCommon;
import com.pathmind.util.BaritoneDependencyChecker;
import java.lang.reflect.Constructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

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
    public static void openVisualEditorOrWarn(Minecraft client, Screen parent) {
        if (client == null) {
            return;
        }

        if (isVisualEditor(client.gui.screen())) {
            return;
        }

        try {
            client.gui.setScreen(instantiateVisualEditor());
        } catch (ReflectiveOperationException | LinkageError e) {
            PathmindCommon.LOGGER.error("Failed to open Pathmind visual editor", e);
        }
    }

    public static void showMissingScreen(Minecraft client) {
        showMissingScreen(client, client != null ? client.gui.screen() : null);
    }

    public static boolean isVisualEditorScreen(Screen screen) {
        return isVisualEditor(screen);
    }

    private static void showMissingScreen(Minecraft client, Screen parent) {
        if (client == null) {
            return;
        }

        if (!(client.gui.screen() instanceof MissingBaritoneApiScreen)) {
            client.gui.setScreen(new MissingBaritoneApiScreen(parent));
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
