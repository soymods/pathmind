package com.pathmind.ui.graph;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class ScreenCoordinateCaptureController {

    interface Host {
        void stopOtherEditors();
        void notifyNodeParametersChanged(Node node);
        void drawNodeText(GuiGraphics context, Font renderer, Component text, int x, int y, int color);
    }

    private final Host host;
    private Node screenCoordinateCaptureNode = null;
    private int screenCoordinatePreviewX = 0;
    private int screenCoordinatePreviewY = 0;

    ScreenCoordinateCaptureController(Host host) {
        this.host = host;
    }

    boolean isActive() {
        return screenCoordinateCaptureNode != null;
    }

    boolean isActiveFor(Node node) {
        return node != null && node == screenCoordinateCaptureNode;
    }

    int getPreviewX() {
        return screenCoordinatePreviewX;
    }

    int getPreviewY() {
        return screenCoordinatePreviewY;
    }

    void start(Node node) {
        if (node == null || !node.hasScreenCoordinatePickerButton()) {
            cancel();
            return;
        }
        host.stopOtherEditors();
        screenCoordinateCaptureNode = node;
        NodeParameter xParam = node.getParameter("X");
        NodeParameter yParam = node.getParameter("Y");
        screenCoordinatePreviewX = xParam != null ? xParam.getIntValue() : 0;
        screenCoordinatePreviewY = yParam != null ? yParam.getIntValue() : 0;
    }

    void cancel() {
        screenCoordinateCaptureNode = null;
    }

    void updatePreview(int screenX, int screenY) {
        if (!isActive()) {
            return;
        }
        screenCoordinatePreviewX = Math.max(0, screenX);
        screenCoordinatePreviewY = Math.max(0, screenY);
    }

    boolean commit(int screenX, int screenY) {
        if (!isActive()) {
            return false;
        }
        Node node = screenCoordinateCaptureNode;
        screenCoordinateCaptureNode = null;
        if (node == null) {
            return false;
        }
        node.setParameterValueAndPropagate("X", Integer.toString(Math.max(0, screenX)));
        node.setParameterValueAndPropagate("Y", Integer.toString(Math.max(0, screenY)));
        node.recalculateDimensions();
        host.notifyNodeParametersChanged(node);
        return true;
    }

    void renderOverlay(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        if (context == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        int clampedX = Mth.clamp(mouseX, 0, Math.max(0, width - 1));
        int clampedY = Mth.clamp(mouseY, 0, Math.max(0, height - 1));

        int lineColor = 0xE6FFFFFF;
        int glowColor = 0x33FFFFFF;
        int panelFill = 0xCC111111;
        int panelBorder = 0xE6FFFFFF;
        int accentFill = 0xE6FFFFFF;
        int textColor = UITheme.TEXT_PRIMARY;

        context.fill(clampedX - 1, 0, clampedX + 1, height, glowColor);
        context.fill(0, clampedY - 1, width, clampedY + 1, glowColor);
        context.vLine(clampedX, 0, height - 1, lineColor);
        context.hLine(0, width - 1, clampedY, lineColor);

        int crossRadius = 6;
        context.fill(clampedX - crossRadius, clampedY - 1, clampedX + crossRadius + 1, clampedY + 1, accentFill);
        context.fill(clampedX - 1, clampedY - crossRadius, clampedX + 1, clampedY + crossRadius + 1, accentFill);
        context.fill(clampedX - 2, clampedY - 2, clampedX + 3, clampedY + 3, 0xFF101010);
        context.fill(clampedX - 1, clampedY - 1, clampedX + 2, clampedY + 2, accentFill);

        if (textRenderer != null) {
            String label = "Pick Mode  " + clampedX + ", " + clampedY;
            int textWidth = textRenderer.width(label);
            int boxWidth = textWidth + 12;
            int boxHeight = 18;
            int boxX = Mth.clamp(clampedX + 12, 4, Math.max(4, width - boxWidth - 4));
            int boxY = clampedY > height - 28 ? clampedY - 24 : clampedY + 12;
            context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, panelFill);
            DrawContextBridge.drawBorderInLayer(context, boxX, boxY, boxWidth, boxHeight, panelBorder);
            host.drawNodeText(context, textRenderer, Component.literal(label), boxX + 6, boxY + 5, textColor);
        }
    }
}
