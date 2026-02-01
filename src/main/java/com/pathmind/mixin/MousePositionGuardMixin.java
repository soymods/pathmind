package com.pathmind.mixin;

import com.pathmind.util.OverlayProtection;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Masks mouse coordinates from external overlays while the Pathmind editor is open in-game.
 */
@Mixin(value = Mouse.class, priority = 10000)
public class MousePositionGuardMixin {

    @Inject(method = "getX()D", at = @At("HEAD"), cancellable = true, require = 0)
    private void pathmind$guardMouseX(CallbackInfoReturnable<Double> cir) {
        if (OverlayProtection.isProtectionActive() && !OverlayProtection.isPathmindInternalCallStack()) {
            cir.setReturnValue(-1.0D);
        }
    }

    @Inject(method = "getY()D", at = @At("HEAD"), cancellable = true, require = 0)
    private void pathmind$guardMouseY(CallbackInfoReturnable<Double> cir) {
        if (OverlayProtection.isProtectionActive() && !OverlayProtection.isPathmindInternalCallStack()) {
            cir.setReturnValue(-1.0D);
        }
    }
}
