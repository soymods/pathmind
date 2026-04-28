package com.pathmind.screen;

import com.pathmind.PathmindMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

final class PathmindCursor {
    static final Identifier DEFAULT_TEXTURE = PathmindMod.id("textures/cursor/cursor_default.png");
    static final Identifier GRAB_TEXTURE = PathmindMod.id("textures/cursor/cursor_grab.png");
    static final Identifier GRABBING_TEXTURE = PathmindMod.id("textures/cursor/cursor_grabbing.png");
    private static final int SOURCE_SIZE = 16;
    static final int SIZE = 8;
    static final int HOTSPOT_X = Math.round(3f * SIZE / SOURCE_SIZE);
    static final int HOTSPOT_Y = Math.round(1f * SIZE / SOURCE_SIZE);
    private static final int CURSOR_TINT = 0xFFFFFFFF;

    private PathmindCursor() {
    }

    static void hideSystemCursor(MinecraftClient client) {
        if (client == null) {
            return;
        }
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    static void showSystemCursor(MinecraftClient client) {
        if (client == null) {
            return;
        }
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }

    static void renderDefault(DrawContext context, int mouseX, int mouseY) {
        render(context, DEFAULT_TEXTURE, mouseX, mouseY);
    }

    static void render(DrawContext context, Identifier texture, int mouseX, int mouseY) {
        GuiTextureRenderer.drawIcon(context, texture, mouseX - HOTSPOT_X, mouseY - HOTSPOT_Y, SIZE, CURSOR_TINT);
    }
}
