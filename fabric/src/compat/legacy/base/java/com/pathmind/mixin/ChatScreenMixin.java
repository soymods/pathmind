package com.pathmind.mixin;

import com.pathmind.ui.overlay.NavigatorChatSuggestions;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.MatrixStackBridge;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void pathmind$renderNavigatorSuggestions(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        DrawContextBridge.startNewRootLayer(context);
        Object matrices = context.getMatrices();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.translateZ(matrices, 500.0f);
        try {
            NavigatorChatSuggestions.getInstance().render((ChatScreen) (Object) this, context, mouseX, mouseY);
        } finally {
            MatrixStackBridge.pop(matrices);
        }
    }

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true)
    private void pathmind$consumeNavigatorKeys(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (NavigatorChatSuggestions.getInstance().handleKeyPressed((ChatScreen) (Object) this, keyCode)) {
            cir.setReturnValue(true);
        }
    }
}
