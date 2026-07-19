package com.pathmind.execution;

import net.minecraft.client.player.LocalPlayer;

/**
 * Keeps the user's rendered view independent from the rotation used by navigation.
 * The navigator continues rotating the real player, so movement, interactions, and
 * server-bound rotation packets retain their normal behavior.
 */
public final class NavigatorCameraController {
    private static boolean active;
    private static LocalPlayer owner;
    private static float cameraYaw;
    private static float cameraPitch;

    private NavigatorCameraController() {
    }

    public static synchronized void begin(LocalPlayer player) {
        if (player == null) {
            active = false;
            owner = null;
            return;
        }
        owner = player;
        cameraYaw = player.getYRot();
        cameraPitch = player.getXRot();
        active = true;
    }

    public static synchronized void end(LocalPlayer player) {
        if (!active) {
            owner = null;
            return;
        }
        LocalPlayer target = player != null ? player : owner;
        if (target != null) {
            target.setYRot(cameraYaw);
            target.setYHeadRot(cameraYaw);
            target.setYBodyRot(cameraYaw);
            target.setXRot(cameraPitch);
            target.yRotO = cameraYaw;
            target.xRotO = cameraPitch;
        }
        active = false;
        owner = null;
    }

    public static synchronized boolean redirectMouseLook(LocalPlayer player, double yawDelta, double pitchDelta) {
        if (!active || player == null || player != owner) {
            return false;
        }
        cameraYaw = NavigatorGeometry.applyMouseYaw(cameraYaw, yawDelta);
        cameraPitch = NavigatorGeometry.applyMousePitch(cameraPitch, pitchDelta);
        return true;
    }

    public static synchronized boolean isActive() {
        return active;
    }

    public static synchronized float cameraYaw(boolean mirrored) {
        return NavigatorGeometry.cameraYawForMode(cameraYaw, mirrored);
    }

    public static synchronized float cameraPitch(boolean mirrored) {
        return NavigatorGeometry.cameraPitchForMode(cameraPitch, mirrored);
    }
}
