package com.pathmind.compat;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.sdl.SDLMouse;

/**
 * SDL-backed input helpers for Minecraft 26.3, which replaced GLFW with SDL.
 */
public final class Mc26Input {
    // 26.3 only updates MouseHandler's isLeftPressed/isMiddlePressed/isRightPressed fields while no
    // screen is open (onButton sets them after its "gui.screen() == null" branch). Inside a GUI they
    // stay false, which made PathmindWorkspaceDragController#recoverStaleLeftMouseDrag believe the
    // button was released and cancel every drag on the next frame. The active button is set on press
    // and cleared on release regardless of the screen, and it is the same state MouseHandler checks
    // before dispatching mouseDragged, so it is the reliable source of the physical button state.
    private static final Field ACTIVE_BUTTON = resolveActiveButton();

    private Mc26Input() {
    }

    public static boolean isMouseButtonPressed(Minecraft client, int buttonCode) {
        if (client == null || client.mouseHandler == null) {
            return false;
        }
        MouseHandler mouse = client.mouseHandler;
        MouseButtonInfo activeButton = activeButton(mouse);
        if (activeButton != null) {
            return activeButton.button() == buttonCode;
        }
        return switch (buttonCode) {
            case InputConstants.MOUSE_BUTTON_LEFT -> mouse.isLeftPressed();
            case InputConstants.MOUSE_BUTTON_RIGHT -> mouse.isRightPressed();
            case InputConstants.MOUSE_BUTTON_MIDDLE -> mouse.isMiddlePressed();
            default -> false;
        };
    }

    private static MouseButtonInfo activeButton(MouseHandler mouse) {
        if (ACTIVE_BUTTON == null) {
            return null;
        }
        try {
            return ACTIVE_BUTTON.get(mouse) instanceof MouseButtonInfo button ? button : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Field resolveActiveButton() {
        try {
            Field field = MouseHandler.class.getDeclaredField("activeButton");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | SecurityException ignored) {
            return null;
        }
    }

    public static void warpCursor(long windowHandle, double x, double y) {
        SDLMouse.SDL_WarpMouseInWindow(windowHandle, (float) x, (float) y);
    }

    public static int legacyButton(int sdlButton) {
        return switch (sdlButton) {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
            default -> Math.max(0, sdlButton - 1);
        };
    }

    public static void hideCursor() {
        SDLMouse.SDL_HideCursor();
    }

    public static void showCursor() {
        SDLMouse.SDL_ShowCursor();
    }
}
