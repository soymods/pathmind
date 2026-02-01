package com.pathmind.mixin;

import com.pathmind.util.OverlayProtection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes mouse input directly to the Pathmind screen and blocks global mouse listeners
 * from other mods while the visual editor is open in-game.
 */
@Mixin(value = Mouse.class, priority = 10000)
public abstract class MouseInputBlockerMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private double x;

    @Shadow
    private double y;

    @Inject(method = "onMouseButton(JIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void pathmind$blockMouseButton(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (!OverlayProtection.isProtectionActive() || client.currentScreen == null) {
            return;
        }

        Window w = client.getWindow();
        double mouseX = x * (double) w.getScaledWidth() / (double) w.getWidth();
        double mouseY = y * (double) w.getScaledHeight() / (double) w.getHeight();

        if (action == GLFW.GLFW_PRESS) {
            client.currentScreen.mouseClicked(mouseX, mouseY, button);
        } else if (action == GLFW.GLFW_RELEASE) {
            client.currentScreen.mouseReleased(mouseX, mouseY, button);
        }

        ci.cancel();
    }


    @Inject(method = "onMouseScroll(JDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void pathmind$blockMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!OverlayProtection.isProtectionActive() || client.currentScreen == null) {
            return;
        }

        Window w = client.getWindow();
        double mouseX = x * (double) w.getScaledWidth() / (double) w.getWidth();
        double mouseY = y * (double) w.getScaledHeight() / (double) w.getHeight();

        client.currentScreen.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        ci.cancel();
    }
}
