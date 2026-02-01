package com.pathmind.mixin;

import com.pathmind.util.OverlayProtection;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block direct item rendering from overlay mods when the Pathmind GUI is open.
 */
@Mixin(value = ItemRenderer.class, priority = 2000)
public class ItemRendererMixin {

    private static boolean pathmind$shouldBlockExternalDraw() {
        return OverlayProtection.shouldBlockExternalDraw();
    }

    @Inject(
        method = "renderInGui(Lnet/minecraft/item/ItemStack;II)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockRenderInGui(ItemStack stack, int x, int y, CallbackInfo ci) {
        if (pathmind$shouldBlockExternalDraw()) {
            ci.cancel();
        }
    }

    @Inject(
        method = "renderInGuiWithOverrides(Lnet/minecraft/item/ItemStack;II)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void pathmind$blockRenderInGuiWithOverrides(ItemStack stack, int x, int y, CallbackInfo ci) {
        if (pathmind$shouldBlockExternalDraw()) {
            ci.cancel();
        }
    }
}
