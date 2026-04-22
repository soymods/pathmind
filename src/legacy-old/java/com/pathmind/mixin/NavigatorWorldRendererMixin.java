package com.pathmind.mixin;

import com.pathmind.ui.overlay.NavigatorWorldOverlay;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class NavigatorWorldRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void pathmind$renderLegacyNavigatorOverlay(
        RenderTickCounter tickCounter,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightmapTextureManager lightmapTextureManager,
        Matrix4f positionMatrix,
        Matrix4f projectionMatrix,
        CallbackInfo ci
    ) {
        NavigatorWorldOverlay.render(positionMatrix, camera);
    }
}
