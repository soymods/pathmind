package com.pathmind.mixin;

import com.pathmind.util.OverlayProtection;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
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
     * Block fill operations from other mods when our screen is open.
     * This prevents overlay mods from drawing rectangles/backgrounds over our GUI.
     */
    @Inject(
        method = "fill(IIIII)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void pathmind$blockFill(int x1, int y1, int x2, int y2, int color, CallbackInfo ci) {
        if (pathmind$shouldBlockExternalDraw()) {
            ci.cancel();
        }
    }

    /**
     * Block text rendering operations from other mods when our screen is open.
     * Targets the common String-based drawText method.
     */
    @Inject(
        method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIII)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0  // Don't fail if this method doesn't exist
    )
    private void pathmind$blockDrawTextString(CallbackInfoReturnable<Integer> cir) {
        if (pathmind$shouldBlockExternalDraw()) {
            cir.setReturnValue(0);
        }
    }

    /**
     * Block text rendering operations from other mods when our screen is open.
     * Targets the Text-based drawText method without shadow parameter.
     */
    @Inject(
        method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIII)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0  // Don't fail if this method doesn't exist
    )
    private void pathmind$blockDrawTextComponent(CallbackInfoReturnable<Integer> cir) {
        if (pathmind$shouldBlockExternalDraw()) {
            cir.setReturnValue(0);
        }
    }

    /**
     * Block OrderedText rendering operations from other mods.
     * Many overlay mods use OrderedText for rendering.
     */
    @Inject(
        method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawTextOrdered(CallbackInfoReturnable<Integer> cir) {
        if (pathmind$shouldBlockExternalDraw()) {
            cir.setReturnValue(0);
        }
    }

    /**
     * Block texture/icon rendering from overlay mods.
     * Targets one of the common drawTexture method signatures.
     */
    @Inject(
        method = "drawTexture(Lnet/minecraft/util/Identifier;IIFFIIIIFF)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawTexture1(CallbackInfo ci) {
        if (pathmind$shouldBlockExternalDraw()) {
            ci.cancel();
        }
    }

    /**
     * Block texture/icon rendering - alternative signature.
     */
    @Inject(
        method = "drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawTexture2(CallbackInfo ci) {
        if (pathmind$shouldBlockExternalDraw()) {
            ci.cancel();
        }
    }

    /**
     * Block shadowed text rendering operations from other mods.
     */
    @Inject(
        method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawTextWithShadowString(CallbackInfoReturnable<Integer> cir) {
        if (pathmind$shouldBlockExternalDraw()) {
            cir.setReturnValue(0);
        }
    }

    /**
     * Block shadowed text rendering operations from other mods.
     */
    @Inject(
        method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawTextWithShadowComponent(CallbackInfoReturnable<Integer> cir) {
        if (pathmind$shouldBlockExternalDraw()) {
            cir.setReturnValue(0);
        }
    }

    /**
     * Block shadowed OrderedText rendering operations from other mods.
     */
    @Inject(
        method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawTextWithShadowOrdered(CallbackInfoReturnable<Integer> cir) {
        if (pathmind$shouldBlockExternalDraw()) {
            cir.setReturnValue(0);
        }
    }

    /**
     * Block item rendering from overlay mods.
     */
    @Inject(
        method = "drawItem(Lnet/minecraft/item/ItemStack;II)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawItem(CallbackInfo ci) {
        if (pathmind$shouldBlockExternalDraw()) {
            ci.cancel();
        }
    }

    /**
     * Block item-in-slot rendering from overlay mods.
     */
    @Inject(
        method = "drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;II)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockDrawItemInSlot(CallbackInfo ci) {
        if (pathmind$shouldBlockExternalDraw()) {
            ci.cancel();
        }
    }
}
