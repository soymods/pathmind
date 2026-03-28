package com.pathmind.mixin;

import com.pathmind.ui.overlay.NavigatorWorldOverlay;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class NavigatorWorldRendererMixin {
    @Inject(
        method = "renderLate(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;DDD)V",
        at = @At("TAIL"),
        require = 0
    )
    private void pathmind$renderLegacyLateNavigatorOverlay(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        double cameraX,
        double cameraY,
        double cameraZ,
        CallbackInfo ci
    ) {
        NavigatorWorldOverlay.render(matrices, consumers, cameraX, cameraY, cameraZ);
    }
}
