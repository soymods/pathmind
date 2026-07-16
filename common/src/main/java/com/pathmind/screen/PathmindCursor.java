package com.pathmind.screen;

import com.pathmind.PathmindCommon;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.MatrixStackBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

final class PathmindCursor {
    static final Identifier DEFAULT_TEXTURE = PathmindCommon.id("textures/cursor/cursor_default.png");
    static final Identifier GRAB_TEXTURE = PathmindCommon.id("textures/cursor/cursor_grab.png");
    static final Identifier GRABBING_TEXTURE = PathmindCommon.id("textures/cursor/cursor_grabbing.png");
    static final Identifier CUT_TEXTURE = PathmindCommon.id("textures/cursor/cursor_cut.png");
    static final Identifier POINTER_TEXTURE = PathmindCommon.id("textures/cursor/cursor_pointer.png");
    static final Identifier SCALE_TEXTURE = PathmindCommon.id("textures/cursor/cursor_scale.png");
    static final Identifier SCALE_BOTTOM_LEFT_TEXTURE = PathmindCommon.id("textures/cursor/cursor_scale_bottom_left.png");
    static final Identifier SCALE_TOP_RIGHT_TEXTURE = PathmindCommon.id("textures/cursor/cursor_scale_top_right.png");
    static final Identifier SCALE_TOP_LEFT_TEXTURE = PathmindCommon.id("textures/cursor/cursor_scale_top_left.png");
    static final Identifier DISABLED_TEXTURE = PathmindCommon.id("textures/cursor/cursor_disabled.png");
    private static final int SOURCE_SIZE = 16;
    static final int SIZE = 8;
    static final int HOTSPOT_X = Math.round(3f * SIZE / SOURCE_SIZE);
    static final int HOTSPOT_Y = Math.round(1f * SIZE / SOURCE_SIZE);
    private static final int CURSOR_TINT = 0xFFFFFFFF;
    private static boolean fallbackSystemCursorVisible = false;

    private PathmindCursor() {
    }

    static void hideSystemCursor(Minecraft client) {
        if (client == null) {
            return;
        }
        GLFW.glfwSetInputMode(client.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        fallbackSystemCursorVisible = false;
    }

    static void showSystemCursor(Minecraft client) {
        if (client == null) {
            return;
        }
        GLFW.glfwSetInputMode(client.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        fallbackSystemCursorVisible = false;
    }

    static void renderDefault(GuiGraphics context, int mouseX, int mouseY) {
        render(context, DEFAULT_TEXTURE, mouseX, mouseY);
    }

    static void render(GuiGraphics context, Identifier texture, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        DrawContextBridge.startNewRootLayer(context);
        Object matrices = context.pose();
        boolean rendered = false;
        MatrixStackBridge.push(matrices);
        try {
            MatrixStackBridge.translateZ(matrices, 1000.0f);
            rendered = GuiTextureRenderer.tryDrawIcon(context, texture, mouseX - HOTSPOT_X, mouseY - HOTSPOT_Y, SIZE, CURSOR_TINT);
            DrawContextBridge.flush(context);
        } finally {
            MatrixStackBridge.pop(matrices);
        }
        syncSystemCursorFallback(client, rendered);
    }

    private static void syncSystemCursorFallback(Minecraft client, boolean customCursorRendered) {
        if (client == null) {
            return;
        }
        long window = client.getWindow().handle();
        if (customCursorRendered) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
            fallbackSystemCursorVisible = false;
            return;
        }
        if (!fallbackSystemCursorVisible) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            fallbackSystemCursorVisible = true;
        }
    }
}
