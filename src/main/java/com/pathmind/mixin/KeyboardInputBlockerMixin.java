package com.pathmind.mixin;

import com.pathmind.util.OverlayProtection;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents global keyboard listeners from other mods while the Pathmind editor is open in-game.
 */
@Mixin(value = Keyboard.class, priority = 10000)
public abstract class KeyboardInputBlockerMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onKey(JIIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void pathmind$blockKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (!OverlayProtection.isProtectionActive() || client.currentScreen == null) {
            return;
        }

        client.currentScreen.keyPressed(key, scancode, modifiers);
        ci.cancel();
    }


    @Inject(method = "onChar(JII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void pathmind$blockChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (!OverlayProtection.isProtectionActive() || client.currentScreen == null) {
            return;
        }

        client.currentScreen.charTyped((char) codePoint, modifiers);
        ci.cancel();
    }

}
