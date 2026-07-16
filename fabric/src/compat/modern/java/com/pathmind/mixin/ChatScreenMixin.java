package com.pathmind.mixin;

import com.pathmind.ui.overlay.NavigatorChatSuggestions;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void pathmind$renderNavigatorSuggestions(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        NavigatorChatSuggestions.getInstance().render((ChatScreen) (Object) this, context, mouseX, mouseY);
    }

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void pathmind$consumeNavigatorKeys(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (input != null && NavigatorChatSuggestions.getInstance().handleKeyPressed((ChatScreen) (Object) this, input.key())) {
            cir.setReturnValue(true);
        }
    }
}
