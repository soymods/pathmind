package com.pathmind.mixin;

import com.pathmind.util.OverlayProtection;
import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to block direct TextRenderer calls from overlay mods.
 * Some overlay mods bypass DrawContext and call TextRenderer methods directly.
 */
@Mixin(value = TextRenderer.class, priority = 2000)
public class TextRendererMixin {

    private static boolean pathmind$shouldBlockExternalDraw() {
        if (!OverlayProtection.isProtectionActive()) {
            return false;
        }

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().startsWith("com.pathmind.")) {
                return false;
            }
        }

        return true;
    }

    /**
     * Block direct text rendering from overlay mods.
     * This catches mods that use TextRenderer.draw() directly.
     */
    @Inject(
        method = "draw(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDirectDrawString(CallbackInfoReturnable<Integer> cir) {
        if (pathmind$shouldBlockExternalDraw()) {
            cir.setReturnValue(0);
        }
    }

    /**
     * Block direct text rendering for Text components.
     */
    @Inject(
        method = "draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDirectDrawText(CallbackInfoReturnable<Integer> cir) {
        if (pathmind$shouldBlockExternalDraw()) {
            cir.setReturnValue(0);
        }
    }

    /**
     * Block direct text rendering for OrderedText.
     */
    @Inject(
        method = "draw(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDirectDrawOrdered(CallbackInfoReturnable<Integer> cir) {
        if (pathmind$shouldBlockExternalDraw()) {
            cir.setReturnValue(0);
        }
    }
}
