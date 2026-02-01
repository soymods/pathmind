package com.pathmind.mixin;

import com.pathmind.util.OverlayProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hard-block UI-Utils overlay rendering and input while the Pathmind editor is open.
 * Uses @Pseudo to avoid hard dependency on UI-Utils.
 */
@Pseudo
@Mixin(targets = "com.mrbreaknfix.ui_utils.gui.BaseOverlay", priority = 10000)
public class UiUtilsOverlayBlockerMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void pathmind$blockRender(CallbackInfo ci) {
        if (OverlayProtection.isProtectionActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseClick", at = @At("HEAD"), cancellable = true, require = 0)
    private void pathmind$blockMouseClick(CallbackInfoReturnable<Boolean> cir) {
        if (OverlayProtection.isProtectionActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void pathmind$blockMouseScroll(CallbackInfoReturnable<Boolean> cir) {
        if (OverlayProtection.isProtectionActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true, require = 0)
    private void pathmind$blockChar(CallbackInfoReturnable<Boolean> cir) {
        if (OverlayProtection.isProtectionActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true, require = 0)
    private void pathmind$blockKey(CallbackInfoReturnable<Boolean> cir) {
        if (OverlayProtection.isProtectionActive()) {
            cir.setReturnValue(false);
        }
    }
}
