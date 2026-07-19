package com.pathmind.mixin;

import com.pathmind.execution.NavigatorCameraController;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MouseHandler.class)
public abstract class NavigatorMouseHandlerMixin {
    @Redirect(
        method = "turnPlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
        )
    )
    private void pathmind$redirectNavigatorMouseLook(LocalPlayer player, double yawDelta, double pitchDelta) {
        if (!NavigatorCameraController.redirectMouseLook(player, yawDelta, pitchDelta)) {
            player.turn(yawDelta, pitchDelta);
        }
    }
}
