package com.pathmind.mixin;

import com.pathmind.util.OverlayProtection;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Aggressive mixin to block DrawContext operations from other mods when Pathmind GUI is open.
 * This catches overlay mods that try to render using DrawContext after the screen is rendered.
 */
@Mixin(value = DrawContext.class, priority = 2000)
public class DrawContextMixin {

    private static boolean pathmind$shouldBlockExternalDraw() {
        return OverlayProtection.shouldBlockExternalDraw();
    }

    /**
     * Block fill operations from other mods when our screen is open.
     * NOTE: These methods with RenderPipeline/TextureSetup don't exist in MC 1.21.1
     */
    // @Inject(
    //     method = "fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/texture/TextureSetup;IIII)V",
    //     at = @At("HEAD"),
    //     cancellable = true,
    //     require = 0
    // )
    // private void pathmind$blockFill(
    //     @Coerce Object pipeline,
    //     @Coerce Object textureSetup,
    //     int x1,
    //     int y1,
    //     int x2,
    //     int y2,
    //     CallbackInfo ci
    // ) {
    //     if (pathmind$shouldBlockExternalDraw()) {
    //         ci.cancel();
    //     }
    // }

    // @Inject(
    //     method = "fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/texture/TextureSetup;IIIIILjava/lang/Integer;)V",
    //     at = @At("HEAD"),
    //     cancellable = true,
    //     require = 0
    // )
    // private void pathmind$blockFillExtended(
    //     @Coerce Object pipeline,
    //     @Coerce Object textureSetup,
    //     int x1,
    //     int y1,
    //     int x2,
    //     int y2,
    //     int color,
    //     Integer z,
    //     CallbackInfo ci
    // ) {
    //     if (pathmind$shouldBlockExternalDraw()) {
    //         ci.cancel();
    //     }
    // }

    /**
     * Block text rendering operations from other mods when our screen is open.
     * NOTE: These void methods don't exist in MC 1.21.1 - drawText returns int
     */
    // @Inject(
    //     method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V",
    //     at = @At("HEAD"),
    //     cancellable = true,
    //     require = 0
    // )
    // private void pathmind$blockDrawTextString(
    //     net.minecraft.client.font.TextRenderer textRenderer,
    //     String text,
    //     int x,
    //     int y,
    //     int color,
    //     boolean shadow,
    //     CallbackInfo ci
    // ) {
    //     if (pathmind$shouldBlockExternalDraw()) {
    //         ci.cancel();
    //     }
    // }

    // @Inject(
    //     method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V",
    //     at = @At("HEAD"),
    //     cancellable = true,
    //     require = 0
    // )
    // private void pathmind$blockDrawTextOrdered(
    //     net.minecraft.client.font.TextRenderer textRenderer,
    //     net.minecraft.text.OrderedText text,
    //     int x,
    //     int y,
    //     int color,
    //     boolean shadow,
    //     CallbackInfo ci
    // ) {
    //     if (pathmind$shouldBlockExternalDraw()) {
    //         ci.cancel();
    //     }
    // }

    /**
     * Block shadowed text rendering operations from other mods.
     * NOTE: These void methods don't exist in MC 1.21.1 - drawTextWithShadow returns int
     */
    // @Inject(
    //     method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V",
    //     at = @At("HEAD"),
    //     cancellable = true,
    //     require = 0
    // )
    // private void pathmind$blockDrawTextWithShadowString(
    //     net.minecraft.client.font.TextRenderer textRenderer,
    //     String text,
    //     int x,
    //     int y,
    //     int color,
    //     CallbackInfo ci
    // ) {
    //     if (pathmind$shouldBlockExternalDraw()) {
    //         ci.cancel();
    //     }
    // }

    // @Inject(
    //     method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V",
    //     at = @At("HEAD"),
    //     cancellable = true,
    //     require = 0
    // )
    // private void pathmind$blockDrawTextWithShadowOrdered(
    //     net.minecraft.client.font.TextRenderer textRenderer,
    //     net.minecraft.text.OrderedText text,
    //     int x,
    //     int y,
    //     int color,
    //     CallbackInfo ci
    // ) {
    //     if (pathmind$shouldBlockExternalDraw()) {
    //         ci.cancel();
    //     }
    // }

    // @Inject(
    //     method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V",
    //     at = @At("HEAD"),
    //     cancellable = true,
    //     require = 0
    // )
    // private void pathmind$blockDrawTextWithShadowText(
    //     net.minecraft.client.font.TextRenderer textRenderer,
    //     net.minecraft.text.Text text,
    //     int x,
    //     int y,
    //     int color,
    //     CallbackInfo ci
    // ) {
    //     if (pathmind$shouldBlockExternalDraw()) {
    //         ci.cancel();
    //     }
    // }

    /**
     * Block texture/icon rendering from overlay mods.
     * NOTE: These methods with RenderPipeline don't exist in MC 1.21.1
     */
    // @Inject(
    //     method = "drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V",
    //     at = @At("HEAD"),
    //     cancellable = true,
    //     require = 0
    // )
    // private void pathmind$blockDrawTextureFull(
    //     @Coerce Object pipeline,
    //     net.minecraft.util.Identifier sprite,
    //     int x,
    //     int y,
    //     float u,
    //     float v,
    //     int width,
    //     int height,
    //     int regionWidth,
    //     int regionHeight,
    //     int textureWidth,
    //     int textureHeight,
    //     int color,
    //     CallbackInfo ci
    // ) {
    //     if (pathmind$shouldBlockExternalDraw()) {
    //         ci.cancel();
    //     }
    // }

    // @Inject(
    //     method = "drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIII)V",
    //     at = @At("HEAD"),
    //     cancellable = true,
    //     require = 0
    // )
    // private void pathmind$blockDrawTextureRegion(
    //     @Coerce Object pipeline,
    //     net.minecraft.util.Identifier sprite,
    //     int x,
    //     int y,
    //     float u,
    //     float v,
    //     int width,
    //     int height,
    //     int textureWidth,
    //     int textureHeight,
    //     int color,
    //     CallbackInfo ci
    // ) {
    //     if (pathmind$shouldBlockExternalDraw()) {
    //         ci.cancel();
    //     }
    // }

    // @Inject(
    //     method = "drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIIII)V",
    //     at = @At("HEAD"),
    //     cancellable = true,
    //     require = 0
    // )
    // private void pathmind$blockDrawTextureRegionSize(
    //     @Coerce Object pipeline,
    //     net.minecraft.util.Identifier sprite,
    //     int x,
    //     int y,
    //     float u,
    //     float v,
    //     int width,
    //     int height,
    //     int regionWidth,
    //     int regionHeight,
    //     int textureWidth,
    //     int textureHeight,
    //     CallbackInfo ci
    // ) {
    //     if (pathmind$shouldBlockExternalDraw()) {
    //         ci.cancel();
    //     }
    // }

    // @Inject(
    //     method = "drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIII)V",
    //     at = @At("HEAD"),
    //     cancellable = true,
    //     require = 0
    // )
    // private void pathmind$blockDrawTextureSimple(
    //     @Coerce Object pipeline,
    //     net.minecraft.util.Identifier sprite,
    //     int x,
    //     int y,
    //     float u,
    //     float v,
    //     int width,
    //     int height,
    //     int textureWidth,
    //     int textureHeight,
    //     CallbackInfo ci
    // ) {
    //     if (pathmind$shouldBlockExternalDraw()) {
    //         ci.cancel();
    //     }
    // }

    /**
     * Block textured quad rendering from overlay mods.
     * NOTE: Method signature doesn't match MC 1.21.1
     */
    // @Inject(
    //     method = "drawTexturedQuad(Lnet/minecraft/util/Identifier;IIIIFFFF)V",
    //     at = @At("HEAD"),
    //     cancellable = true,
    //     require = 0
    // )
    // private void pathmind$blockDrawTexturedQuad(
    //     net.minecraft.util.Identifier sprite,
    //     int x1,
    //     int y1,
    //     int x2,
    //     int y2,
    //     float u1,
    //     float u2,
    //     float v1,
    //     float v2,
    //     CallbackInfo ci
    // ) {
    //     if (pathmind$shouldBlockExternalDraw()) {
    //         ci.cancel();
    //     }
    // }

    /**
     * Block item rendering from overlay mods.
     */
    @Inject(
        method = "drawItem(Lnet/minecraft/item/ItemStack;II)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawItem(
        net.minecraft.item.ItemStack stack,
        int x,
        int y,
        CallbackInfo ci
    ) {
        if (pathmind$shouldBlockExternalDraw()) {
            ci.cancel();
        }
    }

    /**
     * Legacy overloads for older DrawContext signatures.
     */
    @Inject(
        method = "fill(IIIII)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockFillLegacy(int x1, int y1, int x2, int y2, int color, CallbackInfo ci) {
        if (pathmind$shouldBlockExternalDraw()) {
            ci.cancel();
        }
    }

    @Inject(
        method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIII)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawTextStringLegacy(CallbackInfoReturnable<Integer> cir) {
        if (pathmind$shouldBlockExternalDraw()) {
            cir.setReturnValue(0);
        }
    }

    @Inject(
        method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIII)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawTextTextLegacy(CallbackInfoReturnable<Integer> cir) {
        if (pathmind$shouldBlockExternalDraw()) {
            cir.setReturnValue(0);
        }
    }

    @Inject(
        method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawTextOrderedLegacy(CallbackInfoReturnable<Integer> cir) {
        if (pathmind$shouldBlockExternalDraw()) {
            cir.setReturnValue(0);
        }
    }

    @Inject(
        method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawTextWithShadowStringLegacy(CallbackInfoReturnable<Integer> cir) {
        if (pathmind$shouldBlockExternalDraw()) {
            cir.setReturnValue(0);
        }
    }

    @Inject(
        method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawTextWithShadowTextLegacy(CallbackInfoReturnable<Integer> cir) {
        if (pathmind$shouldBlockExternalDraw()) {
            cir.setReturnValue(0);
        }
    }

    @Inject(
        method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawTextWithShadowOrderedLegacy(CallbackInfoReturnable<Integer> cir) {
        if (pathmind$shouldBlockExternalDraw()) {
            cir.setReturnValue(0);
        }
    }

    @Inject(
        method = "drawTexture(Lnet/minecraft/util/Identifier;IIFFIIIIFF)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawTextureLegacy1(CallbackInfo ci) {
        if (pathmind$shouldBlockExternalDraw()) {
            ci.cancel();
        }
    }

    @Inject(
        method = "drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawTextureLegacy2(CallbackInfo ci) {
        if (pathmind$shouldBlockExternalDraw()) {
            ci.cancel();
        }
    }

    @Inject(
        method = "drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;II)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawItemInSlotLegacy(CallbackInfo ci) {
        if (pathmind$shouldBlockExternalDraw()) {
            ci.cancel();
        }
    }
}
