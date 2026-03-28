package com.pathmind.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.pathmind.ui.overlay.NavigatorWorldOverlay;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class NavigatorWorldRendererMixin {
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;renderLateDebug(Lnet/minecraft/client/render/FrameGraphBuilder;Lnet/minecraft/util/math/Vec3d;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/render/Frustum;)V",
            shift = At.Shift.BEFORE
        )
    )
    private void pathmind$renderNavigatorOverlay(
        ObjectAllocator allocator,
        RenderTickCounter tickCounter,
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
        NavigatorWorldOverlay.render(positionMatrix);
    }
}
