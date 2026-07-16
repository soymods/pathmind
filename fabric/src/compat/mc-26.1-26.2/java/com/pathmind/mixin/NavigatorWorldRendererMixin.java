package com.pathmind.mixin;

import com.pathmind.ui.overlay.NavigatorWorldOverlay;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class NavigatorWorldRendererMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void pathmind$renderNavigatorOverlay(CallbackInfo ci) {
        NavigatorWorldOverlay.render((LevelRenderer) (Object) this);
    }
}
