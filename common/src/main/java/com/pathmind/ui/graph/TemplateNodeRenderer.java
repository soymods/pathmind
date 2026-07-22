package com.pathmind.ui.graph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeCatalog;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.UiUtilsProxy;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Renders template-node chrome and its embedded preset graph preview. */
final class TemplateNodeRenderer {

    interface Host {
        int cameraX();
        int cameraY();
        int adjustColorBrightness(int color, float factor);
        void drawNodeText(GuiGraphics context, Font renderer, String text, int x, int y, int color);
        String trimTextToWidth(String text, Font renderer, int maxWidth);
        NodeGraphData.CustomNodeDefinition getTemplateDefinition(Node node);
        String getSelectedPresetName(Node node);
        NodeGraphData getPresetPreviewGraphData(Node node);
        void renderStopTargetInputField(GuiGraphics context, Font textRenderer, Node node,
                                        boolean isOverSidebar, int mouseX, int mouseY);
        void renderRunPresetDropdownList(GuiGraphics context, Font textRenderer, Node node,
                                         boolean isOverSidebar, int mouseX, int mouseY);
    }

    private static final int TEMPLATE_PREVIEW_MARGIN = 6;
    private static final int TEMPLATE_PREVIEW_BOTTOM_MARGIN = 6;

    private final Host host;

    TemplateNodeRenderer(Host host) {
        this.host = host;
    }

    void render(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        int x = node.getX() - host.cameraX();
        int y = node.getY() - host.cameraY();
        int width = node.getWidth();

        int headerColor = node.getColor() & UITheme.NODE_HEADER_ALPHA_MASK;
        if (isOverSidebar) {
            headerColor = UITheme.NODE_HEADER_DIMMED;
        }
        context.fill(x + 1, y + 1, x + width - 1, y + 14, headerColor);

        int bodyColor = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_TERTIARY;
        context.fill(x + 1, y + 15, x + width - 1, y + node.getHeight() - 1, bodyColor);

        NodeGraphData.CustomNodeDefinition definition = host.getTemplateDefinition(node);
        String headerName = definition != null && definition.getName() != null && !definition.getName().isBlank()
            ? definition.getName().trim()
            : "Preset";
        String versionLabel = definition != null && definition.getVersion() != null && definition.getVersion() > 0
            ? " v" + definition.getVersion()
            : "";
        String badge = "LINK";
        int badgeWidth = textRenderer.width(badge) + 8;
        int badgeLeft = x + width - badgeWidth - 6;
        int badgeTop = y + 2;
        int badgeFill = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : host.adjustColorBrightness(node.getColor(), 0.78f);
        int badgeBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : host.adjustColorBrightness(node.getColor(), 1.18f);
        context.fill(badgeLeft, badgeTop, badgeLeft + badgeWidth, badgeTop + 11, badgeFill);
        DrawContextBridge.drawBorderInLayer(context, badgeLeft, badgeTop, badgeWidth, 11, badgeBorder);
        host.drawNodeText(context, textRenderer, badge, badgeLeft + 4, badgeTop + 2, isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY);

        String name = host.trimTextToWidth(headerName + versionLabel, textRenderer, Math.max(0, width - badgeWidth - 20));
        host.drawNodeText(context, textRenderer, name, x + 6, y + 4, isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY);

        host.renderStopTargetInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);

        int previewLeft = getTemplatePreviewLeft(node) - host.cameraX();
        int previewTop = getTemplatePreviewTop(node) - host.cameraY();
        int previewWidth = getTemplatePreviewWidth(node);
        int previewHeight = getTemplatePreviewHeight(node);
        context.fill(previewLeft, previewTop, previewLeft + previewWidth, previewTop + previewHeight,
            isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR);
        DrawContextBridge.drawBorderInLayer(context, previewLeft, previewTop, previewWidth, previewHeight,
            isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT);

        renderTemplatePreviewGraph(context, textRenderer, node, previewLeft, previewTop, previewWidth, previewHeight, isOverSidebar);
        host.renderRunPresetDropdownList(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
    }

    private void renderTemplatePreviewGraph(GuiGraphics context, Font textRenderer, Node node,
                                            int previewLeft, int previewTop, int previewWidth, int previewHeight,
                                            boolean isOverSidebar) {
        NodeGraphData templateData = host.getPresetPreviewGraphData(node);
        NodeGraphData.CustomNodeDefinition definition = templateData != null ? NodeGraphPersistence.resolveCustomNodeDefinition(host.getSelectedPresetName(node), templateData) : null;
        if (definition != null) {
            renderTemplateDefinitionSummary(context, textRenderer, node, definition, previewLeft, previewTop, previewWidth, previewHeight, isOverSidebar);
            return;
        }
        if (templateData == null || templateData.getNodes() == null || templateData.getNodes().isEmpty()) {
            return;
        }
        List<NodeGraphData.NodeData> previewNodes = templateData.getNodes();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (NodeGraphData.NodeData nd : previewNodes) {
            if (nd == null) {
                continue;
            }
            minX = Math.min(minX, nd.getX());
            minY = Math.min(minY, nd.getY());
            maxX = Math.max(maxX, nd.getX());
            maxY = Math.max(maxY, nd.getY());
        }
        if (minX == Integer.MAX_VALUE) {
            return;
        }

        float padding = 6f;
        float spanX = Math.max(1f, (maxX - minX) + 20f);
        float spanY = Math.max(1f, (maxY - minY) + 20f);
        float sx = Math.max(0.1f, (previewWidth - padding * 2f) / spanX);
        float sy = Math.max(0.1f, (previewHeight - padding * 2f) / spanY);
        float scale = Math.min(sx, sy);
        float offsetX = previewLeft + (previewWidth - spanX * scale) / 2f + 10f * scale;
        float offsetY = previewTop + (previewHeight - spanY * scale) / 2f + 10f * scale;

        Map<String, float[]> centers = new HashMap<>();
        for (int i = 0; i < previewNodes.size(); i++) {
            NodeGraphData.NodeData nd = previewNodes.get(i);
            if (nd == null) {
                continue;
            }
            float cx = offsetX + (nd.getX() - minX) * scale;
            float cy = offsetY + (nd.getY() - minY) * scale;
            String nodeId = nd.getId();
            if (nodeId == null || nodeId.isBlank()) {
                nodeId = "__preview_node_" + i;
            }
            centers.put(nodeId, new float[]{cx, cy});
        }

        int lineColor = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_HIGHLIGHT;
        if (templateData.getConnections() != null) {
            for (NodeGraphData.ConnectionData cd : templateData.getConnections()) {
                if (cd == null) {
                    continue;
                }
                float[] a = centers.get(cd.getOutputNodeId());
                float[] b = centers.get(cd.getInputNodeId());
                if (a == null || b == null) {
                    continue;
                }
                int x1 = Math.round(a[0]);
                int y1 = Math.round(a[1]);
                int x2 = Math.round(b[0]);
                int y2 = Math.round(b[1]);
                int mx = (x1 + x2) / 2;
                context.hLine(Math.min(x1, mx), Math.max(x1, mx), y1, lineColor);
                context.vLine(mx, Math.min(y1, y2), Math.max(y1, y2), lineColor);
                context.hLine(Math.min(mx, x2), Math.max(mx, x2), y2, lineColor);
            }
        }

        for (int i = 0; i < previewNodes.size(); i++) {
            NodeGraphData.NodeData nd = previewNodes.get(i);
            if (nd == null) {
                continue;
            }
            String nodeId = nd.getId();
            if (nodeId == null || nodeId.isBlank()) {
                nodeId = "__preview_node_" + i;
            }
            float[] c = centers.get(nodeId);
            if (c == null) {
                continue;
            }
            int cx = Math.round(c[0]);
            int cy = Math.round(c[1]);
            int color = nd.getType() == null
                ? UITheme.TEXT_TERTIARY
                : NodeCatalog.graphColor(
                    nd.getType(),
                    BaritoneDependencyChecker.isBaritoneApiPresent(),
                    UiUtilsProxy.isAvailable());
            if (isOverSidebar) {
                color = UITheme.BORDER_SUBTLE;
            }
            context.fill(cx - 2, cy - 2, cx + 3, cy + 3, color);
        }
    }

    private void renderTemplateDefinitionSummary(GuiGraphics context, Font textRenderer, Node node,
                                                 NodeGraphData.CustomNodeDefinition definition, int previewLeft,
                                                 int previewTop, int previewWidth, int previewHeight, boolean isOverSidebar) {
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int mutedColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.TEXT_SECONDARY;
        int warningColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.ACCENT_AMBER;
        int textX = previewLeft + 5;
        int lineY = previewTop + 4;
        int lineStep = textRenderer.lineHeight + 2;

        String presetLine = "Preset: " + host.trimTextToWidth(host.getSelectedPresetName(node), textRenderer, Math.max(0, previewWidth - 10 - textRenderer.width("Preset: ")));
        host.drawNodeText(context, textRenderer, presetLine, textX, lineY, mutedColor);
        lineY += lineStep;

        int instanceVersion = node.getTemplateVersion();
        int definitionVersion = definition.getVersion() != null ? definition.getVersion() : 0;
        String versionLine = "Version: " + (definitionVersion > 0 ? ("v" + definitionVersion) : "unversioned");
        if (instanceVersion > 0 && definitionVersion > instanceVersion) {
            versionLine += " (instance v" + instanceVersion + ")";
            host.drawNodeText(context, textRenderer, host.trimTextToWidth(versionLine, textRenderer, previewWidth - 10), textX, lineY, warningColor);
        } else {
            host.drawNodeText(context, textRenderer, host.trimTextToWidth(versionLine, textRenderer, previewWidth - 10), textX, lineY, textColor);
        }
        lineY += lineStep;

        host.drawNodeText(context, textRenderer, Component.translatable("pathmind.field.inputs").getString(), textX, lineY, mutedColor);
        lineY += lineStep;
        lineY = renderPortList(context, textRenderer, definition.getInputs(), textX, lineY, previewWidth, previewTop + previewHeight, textColor, mutedColor);
        if (lineY + lineStep < previewTop + previewHeight - 2) {
            host.drawNodeText(context, textRenderer, Component.translatable("pathmind.field.outputs").getString(), textX, lineY, mutedColor);
            lineY += lineStep;
            renderPortList(context, textRenderer, definition.getOutputs(), textX, lineY, previewWidth, previewTop + previewHeight, textColor, mutedColor);
        }
    }

    private int renderPortList(GuiGraphics context, Font textRenderer, List<NodeGraphData.CustomNodePort> ports,
                               int textX, int lineY, int previewWidth, int previewBottom, int textColor, int mutedColor) {
        if (ports == null || ports.isEmpty()) {
            host.drawNodeText(context, textRenderer, Component.translatable("pathmind.option.none").getString(), textX, lineY, mutedColor);
            return lineY + textRenderer.lineHeight + 2;
        }
        for (NodeGraphData.CustomNodePort port : ports) {
            if (lineY + textRenderer.lineHeight > previewBottom - 2) {
                break;
            }
            String label = buildPortSummary(port);
            host.drawNodeText(context, textRenderer, host.trimTextToWidth(label, textRenderer, previewWidth - 10), textX, lineY, textColor);
            lineY += textRenderer.lineHeight + 2;
        }
        return lineY;
    }

    private String buildPortSummary(NodeGraphData.CustomNodePort port) {
        if (port == null) {
            return "";
        }
        String name = port.getName() == null || port.getName().isBlank() ? "unnamed" : port.getName().trim();
        String type = port.getType() == null || port.getType().isBlank() ? "" : " [" + port.getType().trim() + "]";
        String defaultValue = port.getDefaultValue() == null || port.getDefaultValue().isBlank() ? "" : " = " + port.getDefaultValue().trim();
        return name + type + defaultValue;
    }

    private int getTemplatePreviewLeft(Node node) {
        return node.getX() + TEMPLATE_PREVIEW_MARGIN;
    }

    private int getTemplatePreviewTop(Node node) {
        return node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight() + 4;
    }

    private int getTemplatePreviewWidth(Node node) {
        return Math.max(20, node.getWidth() - TEMPLATE_PREVIEW_MARGIN * 2);
    }

    private int getTemplatePreviewHeight(Node node) {
        return Math.max(18, node.getY() + node.getHeight() - TEMPLATE_PREVIEW_BOTTOM_MARGIN - getTemplatePreviewTop(node));
    }
}
