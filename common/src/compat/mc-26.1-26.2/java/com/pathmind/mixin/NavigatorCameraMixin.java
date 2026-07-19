package com.pathmind.mixin;

import com.pathmind.execution.NavigatorCameraController;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class NavigatorCameraMixin {
    @Shadow
    private float eyeHeight;

    @Shadow
    private float eyeHeightOld;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void move(float forward, float up, float left);

    @Invoker("getMaxZoom")
    protected abstract float pathmind$getMaxZoom(float desiredDistance);

    @Inject(
        method = "update",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
            shift = At.Shift.AFTER
        )
    )
    private void pathmind$applyIndependentNavigatorView(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!NavigatorCameraController.isActive()) {
            return;
        }

        Camera camera = (Camera) (Object) this;
        Entity entity = camera.entity();
        if (entity == null) {
            return;
        }
        CameraType cameraType = Minecraft.getInstance().options.getCameraType();
        boolean detached = !cameraType.isFirstPerson();
        boolean mirrored = cameraType.isMirrored();
        setRotation(
            NavigatorCameraController.cameraYaw(mirrored),
            NavigatorCameraController.cameraPitch(mirrored)
        );
        if (!detached) {
            return;
        }

        float partialTick = camera.getCameraEntityPartialTicks(deltaTracker);
        setPosition(
            Mth.lerp(partialTick, entity.xo, entity.getX()),
            Mth.lerp(partialTick, entity.yo, entity.getY())
                + Mth.lerp(partialTick, eyeHeightOld, eyeHeight),
            Mth.lerp(partialTick, entity.zo, entity.getZ())
        );
        move(-pathmind$getMaxZoom(4.0F), 0.0F, 0.0F);
    }
}
