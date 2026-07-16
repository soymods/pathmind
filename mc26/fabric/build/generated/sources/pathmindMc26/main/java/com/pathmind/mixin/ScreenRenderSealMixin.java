package com.pathmind.mixin;

import com.pathmind.PathmindHud;
import com.pathmind.screen.PathmindScreens;
import com.pathmind.util.OverlayProtection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Final render pass to ensure Pathmind draws on top of after-render overlays.
 */
@Mixin(Screen.class)
public class ScreenRenderSealMixin {

    private static final ThreadLocal<Boolean> pathmind$finalRenderPass =
        ThreadLocal.withInitial(() -> false);

    @Inject(
        method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
        at = @At("HEAD"),
        cancellable = false,
        require = 0
    )
    private void pathmind$markRenderStart(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (PathmindScreens.isVisualEditorScreen(self)
            && net.minecraft.client.Minecraft.getInstance().player != null
            && net.minecraft.client.Minecraft.getInstance().level != null) {
            OverlayProtection.setPathmindRendering(true);
        }
    }

    @Inject(
        method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
        at = @At("RETURN"),
        cancellable = false,
        require = 0
    )
    private void pathmind$markRenderEnd(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (PathmindScreens.isVisualEditorScreen(self)
            && net.minecraft.client.Minecraft.getInstance().player != null
            && net.minecraft.client.Minecraft.getInstance().level != null) {
            OverlayProtection.setPathmindRendering(false);
        }
    }

    @Inject(
        method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
        at = @At("TAIL"),
        cancellable = false,
        require = 0
    )
    private void pathmind$finalRenderPass(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (pathmind$finalRenderPass.get()) {
            return;
        }

        Screen self = (Screen) (Object) this;
        if (!PathmindScreens.isVisualEditorScreen(self)) {
            PathmindHud.renderHudNotifications(context, Minecraft.getInstance());
            PathmindHud.renderScreenActiveNodeOverlay(context, Minecraft.getInstance());
            return;
        }

        pathmind$finalRenderPass.set(true);
        OverlayProtection.setPathmindRendering(true);
        try {
            self.extractRenderState(context, mouseX, mouseY, delta);
        } finally {
            OverlayProtection.setPathmindRendering(false);
            pathmind$finalRenderPass.set(false);
        }
        PathmindHud.renderScreenActiveNodeOverlay(context, Minecraft.getInstance());
    }
}
