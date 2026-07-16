package com.pathmind.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pathmind.ui.overlay.NavigatorWorldOverlay;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class NavigatorWorldRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void pathmind$renderNavigatorOverlay(
        PoseStack matrices,
        net.minecraft.client.renderer.culling.Frustum frustum,
        MultiBufferSource.BufferSource consumers,
        double cameraX,
        double cameraY,
        double cameraZ,
        boolean blockOutlineEnabled,
        CallbackInfo ci
    ) {
        NavigatorWorldOverlay.render(matrices, consumers, cameraX, cameraY, cameraZ);
    }
}
