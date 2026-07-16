package com.pathmind.mixin;

import com.pathmind.PathmindHud;
import com.pathmind.screen.PathmindScreens;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric HUD rendering hook for the 26.x generation.
 *
 * <p>The source preparation boundary retargets this mixin from {@code Gui} on
 * 26.1 to {@code Hud} on 26.2. NeoForge uses its typed RenderGui event instead.
 */
@Mixin(net.minecraft.client.gui.Gui.class)
public final class Mc26HudOverlayMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("TAIL")
    )
    private void pathmind$renderAboveVanilla(
            GuiGraphics context,
            DeltaTracker deltaTracker,
            CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null && PathmindScreens.isVisualEditorScreen(client.screen)) {
            return;
        }
        PathmindHud.renderHudOverlays(context, client);
        PathmindHud.renderHudNotifications(context, client);
    }
}
