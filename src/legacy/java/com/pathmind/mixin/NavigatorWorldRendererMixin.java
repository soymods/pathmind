package com.pathmind.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.pathmind.ui.overlay.NavigatorWorldOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class NavigatorWorldRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void pathmind$renderLegacyNavigatorOverlay(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) {
            return;
        }
        NavigatorWorldOverlay.render(RenderSystem.getModelViewMatrix(), client.gameRenderer.getCamera());
    }
}
