package com.pathmind.mixin;

import com.pathmind.screen.PathmindScreens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to prevent other mods' HUD overlays from rendering when the Pathmind GUI is open.
 * This ensures that overlay mods (like minimaps, HUD additions, etc.) don't interfere
 * with the Pathmind visual editor screen.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    /**
     * Cancels HUD rendering when the Pathmind visual editor screen is open.
     * This prevents other mods from rendering overlays on top of our GUI.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void pathmind$preventHudWhenScreenOpen(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        // If the Pathmind visual editor is open, cancel all HUD rendering
        // This prevents other mods' overlays from showing on top of our GUI
        if (client.currentScreen != null && PathmindScreens.isVisualEditorScreen(client.currentScreen)) {
            ci.cancel();
        }
    }
}
