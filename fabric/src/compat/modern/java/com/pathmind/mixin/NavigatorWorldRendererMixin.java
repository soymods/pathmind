package com.pathmind.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.pathmind.ui.overlay.NavigatorWorldOverlay;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class NavigatorWorldRendererMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void pathmind$renderNavigatorOverlay(
        GraphicsResourceAllocator allocator,
        DeltaTracker tickCounter,
        boolean renderBlockOutline,
        Camera camera,
        Matrix4f positionMatrix,
        Matrix4f projectionMatrix,
        Matrix4f worldMatrix,
        GpuBufferSlice fogBuffer,
        Vector4f fogColor,
        boolean tick,
        CallbackInfo ci
    ) {
        NavigatorWorldOverlay.render((LevelRenderer) (Object) this);
    }
}
