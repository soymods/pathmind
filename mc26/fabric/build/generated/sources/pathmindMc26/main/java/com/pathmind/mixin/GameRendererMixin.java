package com.pathmind.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to prevent other mods from rendering overlays during or after the Pathmind screen renders.
 * This targets the main game rendering pipeline to catch aggressive overlay mods.
 */
@Mixin(value = GameRenderer.class, priority = 1500)
public class GameRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    private static final ThreadLocal<Boolean> pathmind$finalRenderPass =
        ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<GuiGraphicsExtractor> pathmind$lastDrawContext = new ThreadLocal<>();
    private static final ThreadLocal<Integer> pathmind$lastMouseX = new ThreadLocal<>();
    private static final ThreadLocal<Integer> pathmind$lastMouseY = new ThreadLocal<>();
    private static final ThreadLocal<Float> pathmind$lastDelta = new ThreadLocal<>();

    /**
     * Inject at the very end of the render method with high priority to ensure
     * we execute after other mods that try to render overlays.
     *
     * This doesn't prevent the rendering itself (that would be too invasive),
     * but it helps us detect when overlays might be rendered.
     * The actual blocking is done by the screen rendering a blocking overlay.
     */
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            shift = At.Shift.AFTER
        ),
        cancellable = false,
        require = 0
    )
    private void pathmind$afterScreenRender(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
        // This injection point allows us to track when the screen finishes rendering
        // The actual overlay blocking is handled by rendering a final overlay layer
        // in the PathmindVisualEditorScreen itself
    }

    @ModifyArg(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
        ),
        require = 0,
        index = 0
    )
    private GuiGraphicsExtractor pathmind$captureDrawContext(GuiGraphicsExtractor context) {
        if (!pathmind$finalRenderPass.get()) {
            pathmind$lastDrawContext.set(context);
        }
        return context;
    }

    @ModifyArg(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
        ),
        require = 0,
        index = 1
    )
    private int pathmind$captureMouseX(int mouseX) {
        if (!pathmind$finalRenderPass.get()) {
            pathmind$lastMouseX.set(mouseX);
        }
        return mouseX;
    }

    @ModifyArg(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
        ),
        require = 0,
        index = 2
    )
    private int pathmind$captureMouseY(int mouseY) {
        if (!pathmind$finalRenderPass.get()) {
            pathmind$lastMouseY.set(mouseY);
        }
        return mouseY;
    }

    @ModifyArg(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
        ),
        require = 0,
        index = 3
    )
    private float pathmind$captureDelta(float delta) {
        if (!pathmind$finalRenderPass.get()) {
            pathmind$lastDelta.set(delta);
        }
        return delta;
    }

    /**
     * Final catch-all to draw the Pathmind screen after other mods render.
     */
    @Inject(
        method = "render",
        at = @At("TAIL"),
        cancellable = false,
        require = 0
    )
    private void pathmind$finalScreenRender(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
        if (pathmind$finalRenderPass.get()) {
            return;
        }

        if (minecraft == null || minecraft.gui.screen() == null) {
            return;
        }

        if (!com.pathmind.screen.PathmindScreens.isVisualEditorScreen(minecraft.gui.screen())) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        GuiGraphicsExtractor context = pathmind$lastDrawContext.get();
        Integer mouseX = pathmind$lastMouseX.get();
        Integer mouseY = pathmind$lastMouseY.get();
        Float delta = pathmind$lastDelta.get();
        if (context == null || mouseX == null || mouseY == null || delta == null) {
            return;
        }

        pathmind$finalRenderPass.set(true);
        com.pathmind.util.OverlayProtection.setPathmindRendering(true);
        try {
            minecraft.gui.screen().extractRenderStateWithTooltipAndSubtitles(context, mouseX, mouseY, delta);
        } catch (Throwable ignored) {
            // Avoid crashing render if another mod misbehaves.
        } finally {
            com.pathmind.util.OverlayProtection.setPathmindRendering(false);
            pathmind$finalRenderPass.set(false);
        }
    }
}
