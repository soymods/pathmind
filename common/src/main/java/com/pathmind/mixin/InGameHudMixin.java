package com.pathmind.mixin;

import com.pathmind.PathmindHud;
import com.pathmind.screen.PathmindScreens;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to prevent other mods' HUD overlays from rendering when the Pathmind GUI is open.
 * This ensures that overlay mods (like minimaps, HUD additions, etc.) don't interfere
 * with the Pathmind visual editor screen.
 */
@Mixin(Gui.class)
public class InGameHudMixin {

    /**
     * Cancels HUD rendering when the Pathmind visual editor screen is open.
     * This prevents other mods from rendering overlays on top of our GUI.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void pathmind$preventHudWhenScreenOpen(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();

        // If the Pathmind visual editor is open, cancel all HUD rendering
        // This prevents other mods' overlays from showing on top of our GUI
        if (client.screen != null && PathmindScreens.isVisualEditorScreen(client.screen)) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void pathmind$renderHudOverlaysAboveVanilla(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null && PathmindScreens.isVisualEditorScreen(client.screen)) {
            return;
        }
        PathmindHud.renderHudOverlays(context, client);
    }

    @Inject(method = "renderSelectedItemName", at = @At("TAIL"))
    private void pathmind$renderHudNotificationsAboveItems(GuiGraphics context, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null && PathmindScreens.isVisualEditorScreen(client.screen)) {
            return;
        }
        PathmindHud.renderHudNotifications(context, client);
    }
}
