package com.pathmind.screen;

import com.pathmind.mixin.ScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Hooks into the main menu to add the Pathmind visual editor button and key handling.
 */
public final class PathmindMainMenuIntegration {
    private static final int BUTTON_SIZE = 20;
    private static final int BUTTON_MARGIN = 8;

    private PathmindMainMenuIntegration() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                addButton(screen);
                registerKeyHandler(client, screen);
            }
        });
    }

    private static void addButton(Screen screen) {
        int x = BUTTON_MARGIN;
        int y = BUTTON_MARGIN;

        ((ScreenAccessor) screen).pathmind$addDrawableChild(new PathmindMainMenuButton(x, y, BUTTON_SIZE, button -> {
            Minecraft client = Minecraft.getInstance();
            PathmindScreens.openVisualEditorOrWarn(client, screen);
        }));
    }

    private static void registerKeyHandler(Minecraft client, Screen screen) {
        ScreenKeyboardEvents.afterKeyPress(screen).register((currentScreen, keyCode, scanCode, modifiers) -> {
            if (!(currentScreen instanceof TitleScreen)) {
                return;
            }

            if (keyCode == GLFW.GLFW_KEY_RIGHT_ALT) {
                PathmindScreens.openVisualEditorOrWarn(client, currentScreen);
            }
        });
    }
}
