package com.pathmind.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.sdl.SDLMouse;

/**
 * SDL-backed input helpers for Minecraft 26.3, which replaced GLFW with SDL.
 */
public final class Mc26Input {
    private Mc26Input() {
    }

    public static boolean isMouseButtonPressed(Minecraft client, int buttonCode) {
        if (client == null || client.mouseHandler == null) {
            return false;
        }
        MouseHandler mouse = client.mouseHandler;
        return switch (buttonCode) {
            case InputConstants.MOUSE_BUTTON_LEFT -> mouse.isLeftPressed();
            case InputConstants.MOUSE_BUTTON_RIGHT -> mouse.isRightPressed();
            case InputConstants.MOUSE_BUTTON_MIDDLE -> mouse.isMiddlePressed();
            default -> false;
        };
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
