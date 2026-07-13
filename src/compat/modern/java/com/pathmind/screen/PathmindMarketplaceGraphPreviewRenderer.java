package com.pathmind.screen;

import com.pathmind.data.NodeGraphData;
import com.pathmind.marketplace.MarketplacePreset;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.MatrixStackBridge;
import com.pathmind.util.TextRenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PathmindMarketplaceGraphPreviewRenderer {
    private final PathmindMarketplaceScreen screen;

    PathmindMarketplaceGraphPreviewRenderer(PathmindMarketplaceScreen screen) {
        this.screen = screen;
    }

    private void drawGraphPreview(DrawContext context, int x, int y, int width, int height, boolean popup) {
        int gridColor = popup ? screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.MARKETPLACE_PREVIEW_GRID) : UITheme.MARKETPLACE_PREVIEW_GRID;
        for (int lineX = x + 12; lineX < x + width - 8; lineX += 18) {
            context.drawVerticalLine(lineX, y + 6, y + height - 7, gridColor);
        }
        for (int lineY = y + 10; lineY < y + height - 8; lineY += 14) {
            context.drawHorizontalLine(x + 6, x + width - 7, lineY, gridColor);
        }
    }

    void renderSurface(DrawContext context, int x, int y, int width, int height,
                                           MarketplacePreset preset, boolean interactive, boolean usePopupViewportState, float panX, float panY) {
        boolean popupInteractive = interactive && usePopupViewportState;
        PathmindMarketplaceScreen.PreviewGraphModel previewModel = screen.getCachedPreviewGraph(preset);
        if (previewModel == null || previewModel.nodes().isEmpty()) {
            screen.requestPreviewGraph(preset);
            drawGraphPreview(context, x, y, width, height, popupInteractive);
            return;
        }

        PathmindMarketplaceScreen.GraphBounds bounds = previewModel.bounds();
        float horizontalPadding = popupInteractive ? 22f : 16f;
        float verticalPadding = popupInteractive ? 20f : 14f;
        float scaleX = bounds.width() <= 0 ? 1f : Math.max(0.01f, (width - horizontalPadding) / bounds.width());
        float scaleY = bounds.height() <= 0 ? 1f : Math.max(0.01f, (height - verticalPadding) / bounds.height());
        float fitScale = Math.min(scaleX, scaleY);
        if (popupInteractive) {
            float viewScale = Math.max(0.18f, Math.min(1f, fitScale)) * screen.popupPreviewZoom;
            float offsetX = x + width / 2f - (bounds.minX() + bounds.width() / 2f) * viewScale + panX;
            float offsetY = y + height / 2f - (bounds.minY() + bounds.height() / 2f) * viewScale + panY;
            float visibleLeft = (x - offsetX) / viewScale;
            float visibleTop = (y - offsetY) / viewScale;
            float visibleRight = (x + width - offsetX) / viewScale;
            float visibleBottom = (y + height - offsetY) / viewScale;
            context.enableScissor(x, y, x + width, y + height);
            Object matrices = context.getMatrices();
            MatrixStackBridge.push(matrices);
            MatrixStackBridge.translate(matrices, offsetX, offsetY);
            MatrixStackBridge.scale(matrices, viewScale, viewScale);
            for (NodeGraphData.ConnectionData connection : previewModel.connections()) {
                Node from = previewModel.nodeLookup().get(connection.getOutputNodeId());
                Node to = previewModel.nodeLookup().get(connection.getInputNodeId());
                if (from == null || to == null) {
                    continue;
                }
                int outputSocket = Math.max(0, connection.getOutputSocket());
                int inputSocket = Math.max(0, connection.getInputSocket());
                int safeOutputSocket = Math.min(outputSocket, Math.max(0, from.getOutputSocketCount() - 1));
                int safeInputSocket = Math.min(inputSocket, Math.max(0, to.getInputSocketCount() - 1));
                int startX = from.getSocketX(false);
                int startY = from.getSocketY(safeOutputSocket, false);
                int endX = to.getSocketX(true);
                int endY = to.getSocketY(safeInputSocket, true);
                if (!isPreviewConnectionVisible(startX, startY, endX, endY, visibleLeft, visibleTop, visibleRight, visibleBottom)) {
                    continue;
                }
                drawPreviewConnection(
                    context,
                    startX,
                    startY,
                    endX,
                    endY,
                    from.getOutputSocketColor(safeOutputSocket),
                    true
                );
            }
            for (Node node : previewModel.nodes()) {
                if (!isPreviewNodeVisible(node, visibleLeft, visibleTop, visibleRight, visibleBottom)) {
                    continue;
                }
                renderPreviewNode(context, node, 0f, 0f, 1f, true, true);
            }
            MatrixStackBridge.pop(matrices);
            context.disableScissor();
            return;
        }
        float viewScale = Math.max(0.08f, Math.min(0.6f, fitScale));
        float offsetX = x + width / 2f - (bounds.minX() + bounds.width() / 2f) * viewScale;
        float offsetY = y + height / 2f - (bounds.minY() + bounds.height() / 2f) * viewScale;
        float visibleLeft = (x + 1 - offsetX) / viewScale;
        float visibleTop = (y + 1 - offsetY) / viewScale;
        float visibleRight = (x + width - 1 - offsetX) / viewScale;
        float visibleBottom = (y + height - 1 - offsetY) / viewScale;

        context.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        Object matrices = context.getMatrices();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.translate(matrices, offsetX, offsetY);
        MatrixStackBridge.scale(matrices, viewScale, viewScale);
        for (NodeGraphData.ConnectionData connection : previewModel.connections()) {
            Node from = previewModel.nodeLookup().get(connection.getOutputNodeId());
            Node to = previewModel.nodeLookup().get(connection.getInputNodeId());
            if (from == null || to == null) {
                continue;
            }
            int outputSocket = Math.max(0, connection.getOutputSocket());
            int inputSocket = Math.max(0, connection.getInputSocket());
            int safeOutputSocket = Math.min(outputSocket, Math.max(0, from.getOutputSocketCount() - 1));
            int safeInputSocket = Math.min(inputSocket, Math.max(0, to.getInputSocketCount() - 1));
            int startX = from.getSocketX(false);
            int startY = from.getSocketY(safeOutputSocket, false);
            int endX = to.getSocketX(true);
            int endY = to.getSocketY(safeInputSocket, true);
            if (!isPreviewConnectionVisible(startX, startY, endX, endY, visibleLeft, visibleTop, visibleRight, visibleBottom)) {
                continue;
            }
            drawPreviewConnection(
                context,
                startX,
                startY,
                endX,
                endY,
                from.getOutputSocketColor(safeOutputSocket),
                false
            );
        }

        for (Node node : previewModel.nodes()) {
            if (!isPreviewNodeVisible(node, visibleLeft, visibleTop, visibleRight, visibleBottom)) {
                continue;
            }
            renderPreviewNode(context, node, 0f, 0f, 1f, true, false);
        }
        MatrixStackBridge.pop(matrices);
        context.disableScissor();
    }

    private boolean isPreviewNodeVisible(Node node, float visibleLeft, float visibleTop, float visibleRight, float visibleBottom) {
        if (node == null) {
            return false;
        }
        float nodeLeft = node.getX();
        float nodeTop = node.getY();
        float nodeRight = nodeLeft + Math.max(18, node.getWidth());
        float nodeBottom = nodeTop + Math.max(14, node.getHeight());
        return nodeRight >= visibleLeft
            && nodeLeft <= visibleRight
            && nodeBottom >= visibleTop
            && nodeTop <= visibleBottom;
    }

    private boolean isPreviewConnectionVisible(int startX, int startY, int endX, int endY,
                                               float visibleLeft, float visibleTop, float visibleRight, float visibleBottom) {
        float padding = 24f;
        float minX = Math.min(startX, endX) - padding;
        float maxX = Math.max(startX, endX) + padding;
        float minY = Math.min(startY, endY) - padding;
        float maxY = Math.max(startY, endY) + padding;
        return maxX >= visibleLeft
            && minX <= visibleRight
            && maxY >= visibleTop
            && minY <= visibleBottom;
    }

    private void drawPreviewConnection(DrawContext context, int startX, int startY, int endX, int endY, int color, boolean popup) {
        int resolvedColor = popup ? screen.presetPopupAnimation.getAnimatedPopupColor(color) : color;
        int distance = Math.max(Math.abs(endX - startX), Math.abs(endY - startY));
        int steps = popup ? Math.max(8, distance / 8) : Math.min(14, Math.max(4, distance / 28));
        float controlOffset = Math.max(18f, Math.abs(endX - startX) * 0.33f);
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float invT = 1f - t;
            float p0x = startX;
            float p0y = startY;
            float p1x = startX + controlOffset;
            float p1y = startY;
            float p2x = endX - controlOffset;
            float p2y = endY;
            float p3x = endX;
            float p3y = endY;
            int x = Math.round(invT * invT * invT * p0x
                + 3f * invT * invT * t * p1x
                + 3f * invT * t * t * p2x
                + t * t * t * p3x);
            int y = Math.round(invT * invT * invT * p0y
                + 3f * invT * invT * t * p1y
                + 3f * invT * t * t * p2y
                + t * t * t * p3y);
            context.fill(x, y, x + 2, y + 2, resolvedColor);
        }
    }

    private void renderPreviewNode(DrawContext context, Node node, float offsetX, float offsetY, float scale, boolean interactive, boolean popup) {
        if (node == null) {
            return;
        }
        if (node.isStickyNote()) {
            renderPreviewStickyNote(context, node, offsetX, offsetY, scale, interactive, popup);
            return;
        }
        int nodeX = Math.round(offsetX + node.getX() * scale);
        int nodeY = Math.round(offsetY + node.getY() * scale);
        int nodeWidth = Math.max(18, Math.round(node.getWidth() * scale));
        int nodeHeight = Math.max(14, Math.round(node.getHeight() * scale));
        int color = node.getColor();
        int borderColor = node.isStopControlNode() ? UITheme.MARKETPLACE_STOP_NODE_BORDER : color;
        int backgroundColor = interactive ? UITheme.BACKGROUND_SECONDARY : AnimationHelper.darken(UITheme.BACKGROUND_SECONDARY, 0.94f);
        int resolvedBackground = popup ? screen.presetPopupAnimation.getAnimatedPopupColor(backgroundColor) : backgroundColor;
        int resolvedBorder = popup ? screen.presetPopupAnimation.getAnimatedPopupColor(borderColor) : borderColor;
        int resolvedInnerBorder = popup ? screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER) : UITheme.PANEL_INNER_BORDER;
        UIStyleHelper.drawBeveledPanel(
            context,
            nodeX,
            nodeY,
            nodeWidth,
            nodeHeight,
            resolvedBackground,
            resolvedBorder,
            resolvedInnerBorder
        );
        boolean compactNode = node.usesMinimalNodePresentation() || node.isSensorNode() || node.isParameterNode();
        int headerHeight = compactNode ? Math.max(10, Math.round(14f * scale)) : Math.max(12, Math.round(18f * scale));
        int headerColor = color & UITheme.NODE_HEADER_ALPHA_MASK;
        context.fill(nodeX + 1, nodeY + 1, nodeX + nodeWidth - 1, nodeY + headerHeight,
            popup ? screen.presetPopupAnimation.getAnimatedPopupColor(headerColor) : headerColor);

        if (scale > 0.12f) {
            String label = TextRenderUtil.trimWithEllipsis(screen.textRenderer(), node.getDisplayName().getString(), Math.max(20, nodeWidth - 8));
            context.drawTextWithShadow(screen.textRenderer(), Text.literal(label), nodeX + 4, nodeY + 3,
                popup ? screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_HEADER) : UITheme.TEXT_HEADER);
        }

        if (interactive && scale > 0.12f) {
            int textY = nodeY + headerHeight + 3;
            int lineHeight = Math.max(9, screen.textRenderer().fontHeight + 1);
            for (String line : buildNodeBodyLines(node)) {
                if (textY > nodeY + nodeHeight - lineHeight) {
                    break;
                }
                context.drawTextWithShadow(screen.textRenderer(),
                    Text.literal(TextRenderUtil.trimWithEllipsis(screen.textRenderer(), line, Math.max(22, nodeWidth - 8))),
                    nodeX + 4,
                    textY,
                    popup ? screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY) : UITheme.TEXT_SECONDARY);
                textY += lineHeight;
            }
        }
        renderNodeSockets(context, node, offsetX, offsetY, scale, popup);
    }

    private void renderPreviewStickyNote(DrawContext context, Node node, float offsetX, float offsetY, float scale,
                                         boolean interactive, boolean popup) {
        int nodeX = Math.round(offsetX + node.getX() * scale);
        int nodeY = Math.round(offsetY + node.getY() * scale);
        int nodeWidth = Math.max(18, Math.round(node.getWidth() * scale));
        int nodeHeight = Math.max(14, Math.round(node.getHeight() * scale));
        int paperColor = interactive ? 0xFFF3E28A : 0xFFE0CE70;
        int headerColor = interactive ? 0xFFE2C65A : 0xFFC9AE4C;
        int borderColor = 0xFFC2A748;
        int textColor = 0xFF3F3420;
        int resolvedPaper = popup ? screen.presetPopupAnimation.getAnimatedPopupColor(paperColor) : paperColor;
        int resolvedHeader = popup ? screen.presetPopupAnimation.getAnimatedPopupColor(headerColor) : headerColor;
        int resolvedBorder = popup ? screen.presetPopupAnimation.getAnimatedPopupColor(borderColor) : borderColor;
        int resolvedText = popup ? screen.presetPopupAnimation.getAnimatedPopupColor(textColor) : textColor;

        context.fill(nodeX, nodeY, nodeX + nodeWidth, nodeY + nodeHeight, resolvedPaper);
        int headerHeight = Math.max(3, Math.round(node.getStickyNoteHeaderHeight() * scale));
        context.fill(nodeX + 1, nodeY + 1, nodeX + nodeWidth - 1, nodeY + headerHeight, resolvedHeader);
        DrawContextBridge.drawBorderInLayer(context, nodeX, nodeY, nodeWidth, nodeHeight, resolvedBorder);

        if (!interactive || scale <= 0.12f) {
            return;
        }
        int margin = Math.max(2, Math.round(8f * scale));
        int bodyX = nodeX + margin;
        int bodyY = nodeY + headerHeight + margin;
        int bodyWidth = Math.max(8, nodeWidth - margin * 2);
        int bottom = nodeY + nodeHeight - margin;
        int lineHeight = Math.max(9, screen.textRenderer().fontHeight + 1);
        int textY = bodyY;
        for (String line : wrapStickyNotePreviewText(screen.fallback(node.getStickyNoteText(), ""), bodyWidth)) {
            if (textY + screen.textRenderer().fontHeight > bottom) {
                break;
            }
            context.drawText(screen.textRenderer(), Text.literal(line), bodyX, textY, resolvedText, false);
            textY += lineHeight;
        }
    }

    private List<String> wrapStickyNotePreviewText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] rawLines = screen.fallback(text, "").split("\\n", -1);
        for (String rawLine : rawLines) {
            appendWrappedStickyNoteLine(lines, rawLine == null ? "" : rawLine, Math.max(1, maxWidth));
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private void appendWrappedStickyNoteLine(List<String> lines, String rawLine, int maxWidth) {
        if (rawLine.isEmpty()) {
            lines.add("");
            return;
        }
        StringBuilder current = new StringBuilder();
        for (String word : rawLine.split(" ", -1)) {
            if (word.isEmpty()) {
                if (current.length() == 0 || screen.textRenderer().getWidth(current + " ") <= maxWidth) {
                    current.append(' ');
                }
                continue;
            }
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (screen.textRenderer().getWidth(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
            }
            appendWrappedStickyNoteWord(lines, current, word, maxWidth);
        }
        lines.add(current.toString());
    }

    private void appendWrappedStickyNoteWord(List<String> lines, StringBuilder current, String word, int maxWidth) {
        for (int index = 0; index < word.length(); index++) {
            String candidate = current.toString() + word.charAt(index);
            if (current.length() > 0 && screen.textRenderer().getWidth(candidate) > maxWidth) {
                lines.add(current.toString());
                current.setLength(0);
            }
            current.append(word.charAt(index));
        }
    }

    private void renderNodeSockets(DrawContext context, Node node, float offsetX, float offsetY, float scale, boolean popup) {
        int socketSize = Math.max(2, Math.round(4f * scale));
        int halfSocket = Math.max(1, socketSize / 2);
        for (int socketIndex = 0; socketIndex < node.getInputSocketCount(); socketIndex++) {
            int socketX = Math.round(offsetX + node.getSocketX(true) * scale);
            int socketY = Math.round(offsetY + node.getSocketY(socketIndex, true) * scale);
            int inputColor = popup ? screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY) : UITheme.TEXT_TERTIARY;
            context.fill(socketX - halfSocket, socketY - halfSocket, socketX + halfSocket + 1, socketY + halfSocket + 1, inputColor);
        }
        for (int socketIndex = 0; socketIndex < node.getOutputSocketCount(); socketIndex++) {
            int socketX = Math.round(offsetX + node.getSocketX(false) * scale);
            int socketY = Math.round(offsetY + node.getSocketY(socketIndex, false) * scale);
            int socketColor = node.getOutputSocketColor(socketIndex);
            int outputColor = popup ? screen.presetPopupAnimation.getAnimatedPopupColor(socketColor) : socketColor;
            context.fill(socketX - halfSocket, socketY - halfSocket, socketX + halfSocket + 1, socketY + halfSocket + 1, outputColor);
        }
    }

    private List<String> buildNodeBodyLines(Node node) {
        List<String> bodyLines = new ArrayList<>();
        if (node == null) {
            return bodyLines;
        }
        if (node.getMode() != null) {
            bodyLines.add(Text.translatable("pathmind.marketplace.graphMode", node.getMode().getDisplayName()).getString());
        }
        if (node.getType() == com.pathmind.nodes.NodeType.MESSAGE) {
            for (String line : node.getMessageLines()) {
                String value = screen.fallback(line, "").trim();
                if (!value.isEmpty()) {
                    bodyLines.add(Text.translatable("pathmind.marketplace.graphMessage", value).getString());
                }
            }
        }
        if (node.hasAttachedParameter()) {
            for (Map.Entry<Integer, Node> entry : node.getAttachedParameters().entrySet()) {
                if (entry == null || entry.getValue() == null) {
                    continue;
                }
                String slotLabel = node.getParameterSlotLabel(entry.getKey());
                String childLabel = entry.getValue().getDisplayName().getString();
                if (slotLabel == null || slotLabel.isBlank()) {
                    slotLabel = Text.translatable("pathmind.marketplace.graphParam", entry.getKey() + 1).getString();
                }
                bodyLines.add(slotLabel + ": " + childLabel);
                if (bodyLines.size() >= 6) {
                    return bodyLines;
                }
            }
        }
        for (NodeParameter parameter : node.getParameters()) {
            if (parameter == null) {
                continue;
            }
            String value = screen.fallback(parameter.getStringValue(), "").trim();
            if (value.isEmpty()) {
                if (node.isParameterNode()) {
                    bodyLines.add(parameter.getName() + ": empty");
                }
                if (bodyLines.size() >= 6) {
                    break;
                }
                continue;
            }
            bodyLines.add(parameter.getName() + ": " + value);
            if (bodyLines.size() >= 6) {
                break;
            }
        }
        return bodyLines;
    }


    void drawMinimalPreviewButton(DrawContext context, int x, int y, int width, int height, boolean hovered) {
        int background = screen.presetPopupAnimation.getAnimatedPopupColor(hovered ? UITheme.BACKGROUND_SECTION : UITheme.BACKGROUND_PRIMARY);
        int border = screen.presetPopupAnimation.getAnimatedPopupColor(hovered ? screen.getAccentColor() : UITheme.BORDER_SUBTLE);
        UIStyleHelper.drawBeveledPanel(
            context,
            x,
            y,
            width,
            height,
            background,
            border,
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );
    }

    void drawPreviewMinusIcon(DrawContext context, int x, int y, int color) {
        context.fill(x + 4, y + 6, x + 10, y + 8, color);
    }

    void drawPreviewPlusIcon(DrawContext context, int x, int y, int color) {
        context.fill(x + 4, y + 6, x + 10, y + 8, color);
        context.fill(x + 6, y + 4, x + 8, y + 10, color);
    }
}
