package com.pathmind.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import org.lwjgl.glfw.GLFW;

public final class CharacterEventModifiers {
    private CharacterEventModifiers() {
    }

    public static int get(CharacterEvent input, Minecraft minecraft) {
        int modifiers = 0;
        if (minecraft != null && minecraft.hasShiftDown()) modifiers |= GLFW.GLFW_MOD_SHIFT;
        if (minecraft != null && minecraft.hasControlDown()) modifiers |= GLFW.GLFW_MOD_CONTROL;
        if (minecraft != null && minecraft.hasAltDown()) modifiers |= GLFW.GLFW_MOD_ALT;
        return modifiers;
    }
}
