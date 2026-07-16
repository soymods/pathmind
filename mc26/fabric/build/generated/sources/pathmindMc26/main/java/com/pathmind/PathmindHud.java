package com.pathmind;

import com.pathmind.data.SettingsManager;
import com.pathmind.ui.overlay.ActiveNodeOverlay;
import com.pathmind.ui.overlay.NavigatorDebugOverlay;
import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import com.pathmind.ui.overlay.VariablesOverlay;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.MatrixStackBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class PathmindHud {

    private static ActiveNodeOverlay activeNodeOverlay;
    private static NavigatorDebugOverlay navigatorDebugOverlay;
    private static NodeErrorNotificationOverlay nodeErrorNotificationOverlay;
    private static VariablesOverlay variablesOverlay;

    private PathmindHud() {}

    public static void initialize(
            ActiveNodeOverlay active,
            NavigatorDebugOverlay debug,
            NodeErrorNotificationOverlay notifications,
            VariablesOverlay variables) {
        activeNodeOverlay = active;
        navigatorDebugOverlay = debug;
        nodeErrorNotificationOverlay = notifications;
        variablesOverlay = variables;
    }

    public static void renderHudOverlays(GuiGraphicsExtractor drawContext, Minecraft client) {
        if (client == null || client.player == null || client.font == null) {
            return;
        }
        Boolean showHudOverlays = SettingsManager.getCurrent().showHudOverlays;
        if (showHudOverlays != null && !showHudOverlays) {
            return;
        }
        int scaledWidth = client.getWindow().getGuiScaledWidth();
        int scaledHeight = client.getWindow().getGuiScaledHeight();
        DrawContextBridge.startNewRootLayer(drawContext);
        Object matrices = drawContext.pose();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.translateZ(matrices, 500.0f);
        try {
            if (activeNodeOverlay != null) {
                activeNodeOverlay.render(drawContext, client.font, scaledWidth, scaledHeight);
            }
            if (variablesOverlay != null) {
                variablesOverlay.render(drawContext, client.font, scaledWidth, scaledHeight);
            }
            if (navigatorDebugOverlay != null) {
                navigatorDebugOverlay.render(drawContext, client.font, scaledWidth, scaledHeight);
            }
        } finally {
            MatrixStackBridge.pop(matrices);
        }
    }

    public static void renderHudNotifications(GuiGraphicsExtractor drawContext, Minecraft client) {
        if (client == null || client.font == null || nodeErrorNotificationOverlay == null) {
            return;
        }
        Boolean showHudOverlays = SettingsManager.getCurrent().showHudOverlays;
        if (showHudOverlays != null && !showHudOverlays) {
            return;
        }
        int scaledWidth = client.getWindow().getGuiScaledWidth();
        int scaledHeight = client.getWindow().getGuiScaledHeight();
        DrawContextBridge.startNewRootLayer(drawContext);
        Object matrices = drawContext.pose();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.translateZ(matrices, 500.0f);
        try {
            nodeErrorNotificationOverlay.render(drawContext, client.font, scaledWidth, scaledHeight);
        } finally {
            MatrixStackBridge.pop(matrices);
        }
    }

    public static void renderScreenActiveNodeOverlay(GuiGraphicsExtractor drawContext, Minecraft client) {
        if (client == null || client.player != null || client.font == null || activeNodeOverlay == null) {
            return;
        }
        Boolean showHudOverlays = SettingsManager.getCurrent().showHudOverlays;
        if (showHudOverlays != null && !showHudOverlays) {
            return;
        }
        int scaledWidth = client.getWindow().getGuiScaledWidth();
        int scaledHeight = client.getWindow().getGuiScaledHeight();
        DrawContextBridge.startNewRootLayer(drawContext);
        Object matrices = drawContext.pose();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.translateZ(matrices, 500.0f);
        try {
            activeNodeOverlay.renderCompact(drawContext, client.font, scaledWidth, scaledHeight);
        } finally {
            MatrixStackBridge.pop(matrices);
        }
    }
}
