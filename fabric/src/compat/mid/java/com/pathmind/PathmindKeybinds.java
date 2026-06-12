package com.pathmind;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Manages all keybindings for the Pathmind mod.
 */
public class PathmindKeybinds {
    public static KeyBinding OPEN_VISUAL_EDITOR;
    public static KeyBinding PLAY_GRAPHS;
    public static KeyBinding STOP_GRAPHS;
    private static final KeyBinding.Category GENERAL_CATEGORY =
        KeyBinding.Category.create(Identifier.of("pathmind", "general"));
    
    public static void registerKeybinds() {
        OPEN_VISUAL_EDITOR = new KeyBinding(
                "key.pathmind.open_visual_editor",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_ALT, // Right Alt/Option key
                GENERAL_CATEGORY
        );
        PLAY_GRAPHS = new KeyBinding(
                "key.pathmind.play_graphs",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                GENERAL_CATEGORY
        );
        STOP_GRAPHS = new KeyBinding(
                "key.pathmind.stop_graphs",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                GENERAL_CATEGORY
        );
    }
}
