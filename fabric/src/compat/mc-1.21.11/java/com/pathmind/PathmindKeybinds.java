package com.pathmind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Manages all keybindings for the Pathmind mod.
 */
public class PathmindKeybinds {
    public static KeyMapping OPEN_VISUAL_EDITOR;
    public static KeyMapping PLAY_GRAPHS;
    public static KeyMapping STOP_GRAPHS;
    private static final KeyMapping.Category GENERAL_CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("pathmind", "general"));
    
    public static void registerKeybinds() {
        OPEN_VISUAL_EDITOR = new KeyMapping(
                "key.pathmind.open_visual_editor",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_ALT, // Right Alt/Option key
                GENERAL_CATEGORY
        );
        PLAY_GRAPHS = new KeyMapping(
                "key.pathmind.play_graphs",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                GENERAL_CATEGORY
        );
        STOP_GRAPHS = new KeyMapping(
                "key.pathmind.stop_graphs",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                GENERAL_CATEGORY
        );
    }
}
