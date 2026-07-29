package com.pathmind.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;

public final class CharacterEventModifiers {
    private CharacterEventModifiers() {
    }

    public static int get(CharacterEvent input, Minecraft minecraft) {
        return input.modifiers();
    }
}
