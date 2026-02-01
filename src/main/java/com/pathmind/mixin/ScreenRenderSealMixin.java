package com.pathmind.mixin;

import com.pathmind.screen.PathmindScreens;
import com.pathmind.util.OverlayProtection;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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
        method = "renderWithTooltip(Lnet/minecraft/client/gui/DrawContext;IIF)V",
        at = @At("HEAD"),
        cancellable = false,
        require = 0
    )
    private void pathmind$markRenderStart(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (PathmindScreens.isVisualEditorScreen(self)
            && net.minecraft.client.MinecraftClient.getInstance().player != null
            && net.minecraft.client.MinecraftClient.getInstance().world != null) {
            OverlayProtection.setPathmindRendering(true);
        }
    }

    @Inject(
        method = "renderWithTooltip(Lnet/minecraft/client/gui/DrawContext;IIF)V",
        at = @At("RETURN"),
        cancellable = false,
        require = 0
    )
    private void pathmind$markRenderEnd(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (PathmindScreens.isVisualEditorScreen(self)
            && net.minecraft.client.MinecraftClient.getInstance().player != null
            && net.minecraft.client.MinecraftClient.getInstance().world != null) {
            OverlayProtection.setPathmindRendering(false);
        }
    }

    @Inject(
        method = "renderWithTooltip(Lnet/minecraft/client/gui/DrawContext;IIF)V",
        at = @At("TAIL"),
        cancellable = false,
        require = 0
    )
    private void pathmind$finalRenderPass(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (pathmind$finalRenderPass.get()) {
            return;
        }

        Screen self = (Screen) (Object) this;
        if (!PathmindScreens.isVisualEditorScreen(self)) {
            return;
        }
        if (net.minecraft.client.MinecraftClient.getInstance().player == null
            || net.minecraft.client.MinecraftClient.getInstance().world == null) {
            return;
        }

        pathmind$finalRenderPass.set(true);
        OverlayProtection.setPathmindRendering(true);
        try {
            self.render(context, mouseX, mouseY, delta);
        } finally {
            OverlayProtection.setPathmindRendering(false);
            pathmind$finalRenderPass.set(false);
        }
    }
}
