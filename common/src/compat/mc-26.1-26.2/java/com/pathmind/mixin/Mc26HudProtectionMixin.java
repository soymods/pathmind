package com.pathmind.mixin;

import com.pathmind.screen.PathmindScreens;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps vanilla and third-party HUD layers out of Pathmind's visual editor.
 *
 * <p>Minecraft 26.2 moved HUD extraction from {@code Gui} to {@code Hud}. The
 * source preparation boundary retargets this mixin accordingly, allowing screen
 * extraction to continue while only gameplay HUD layers are suppressed.
 */
@Mixin(net.minecraft.client.gui.Gui.class)
public final class Mc26HudProtectionMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void pathmind$preventHudWhenScreenOpen(
            GuiGraphics context,
            DeltaTracker deltaTracker,
            CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null && PathmindScreens.isVisualEditorScreen(client.screen)) {
            ci.cancel();
        }
    }
}
