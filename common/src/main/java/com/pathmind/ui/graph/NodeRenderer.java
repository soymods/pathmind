package com.pathmind.ui.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Renders node hierarchies and the common chrome shared by every node type. */
final class NodeRenderer {

    interface Host {
        int cameraX();
        int cameraY();
        boolean compactViewportMode();
        boolean intersectsViewport(Node node);
        boolean isNodeOverSidebarForRender(Node node, int x, int width);
        void renderStickyNote(GuiGraphics context, Font textRenderer, Node node, int x, int y,
                              int width, int height, boolean isOverSidebar);
        int selectedNodeAccentColor();
        int toGrayscale(int color, float brightnessFactor);
        int adjustColorBrightness(int color, float factor);
        String trimTextToWidth(String text, Font renderer, int maxWidth);
        boolean isComparisonOperator(Node node);
        void drawNodeText(GuiGraphics context, Font renderer, Component text, int x, int y, int color);
        void drawNodeText(GuiGraphics context, Font renderer, String text, int x, int y, int color);
        void renderNodeSockets(GuiGraphics context, Node node, boolean isOverSidebar, boolean lowDetail);
        void renderNodeContent(GuiGraphics context, Font textRenderer, Node node, int mouseX, int mouseY,
                               int x, int y, int width, int height, boolean isOverSidebar,
                               boolean simpleStyle, boolean lowDetail);
    }

    private static final int MINIMAL_NODE_TAB_WIDTH = 6;
    private static final int NODE_HEADER_BUTTON_SIZE = 12;

    private final Host host;

    NodeRenderer(Host host) {
        this.host = host;
    }

    void renderHierarchy(Node node, GuiGraphics context, Font textRenderer, int mouseX, int mouseY,
                         float delta, boolean onlyDragged, boolean ancestorActive, Set<Node> renderedNodes) {
        if (node == null || renderedNodes.contains(node)) {
            return;
        }

        boolean ownActive = isHierarchyDragging(node);
        boolean hierarchyActive = ancestorActive || ownActive;
        if ((onlyDragged && !hierarchyActive) || (!onlyDragged && hierarchyActive)) {
            markHierarchyRendered(node, renderedNodes);
            return;
        }

        if (host.intersectsViewport(node) || hierarchyActive) {
            renderNode(context, textRenderer, node, mouseX, mouseY, delta);
        }
        renderedNodes.add(node);

        Node actionChild = node.getAttachedActionNode();
        renderHierarchy(actionChild, context, textRenderer, mouseX, mouseY, delta, onlyDragged, hierarchyActive, renderedNodes);

        Node sensorChild = node.getAttachedSensor();
        renderHierarchy(sensorChild, context, textRenderer, mouseX, mouseY, delta, onlyDragged, hierarchyActive, renderedNodes);

        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            List<Integer> keys = new ArrayList<>(parameterMap.keySet());
            Collections.sort(keys);
            for (Integer key : keys) {
                renderHierarchy(parameterMap.get(key), context, textRenderer, mouseX, mouseY, delta, onlyDragged, hierarchyActive, renderedNodes);
            }
        }
    }

    private void markHierarchyRendered(Node node, Set<Node> renderedNodes) {
        if (node == null || renderedNodes.contains(node)) {
            return;
        }
        renderedNodes.add(node);
        markHierarchyRendered(node.getAttachedActionNode(), renderedNodes);
        markHierarchyRendered(node.getAttachedSensor(), renderedNodes);
        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            for (Node parameter : parameterMap.values()) {
                markHierarchyRendered(parameter, renderedNodes);
            }
        }
    }

    private boolean isHierarchyDragging(Node node) {
        return isHierarchyDragging(node, new HashSet<>());
    }

    private boolean isHierarchyDragging(Node node, Set<Node> visited) {
        if (node == null || visited.contains(node)) {
            return false;
        }
        visited.add(node);
        if (node.isDragging()) {
            return true;
        }
        if (isHierarchyDragging(node.getAttachedActionNode(), visited)) {
            return true;
        }
        if (isHierarchyDragging(node.getAttachedSensor(), visited)) {
            return true;
        }
        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            for (Node parameter : parameterMap.values()) {
                if (isHierarchyDragging(parameter, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void renderNode(GuiGraphics context, Font textRenderer, Node node, int mouseX, int mouseY, float delta) {
        int x = node.getX() - host.cameraX();
        int y = node.getY() - host.cameraY();
        int width = node.getWidth();
        int height = node.getHeight();

        // Check if node is being dragged over sidebar (grey-out effect)
        // Use screen coordinates (with camera offset) for this check
        boolean isOverSidebar = host.isNodeOverSidebarForRender(node, x, width);

        if (node.isStickyNote()) {
            host.renderStickyNote(context, textRenderer, node, x, y, width, height, isOverSidebar);
            return;
        }

        boolean simpleStyle = node.usesMinimalNodePresentation();
        boolean isStopControl = node.isStopControlNode();
        boolean lowDetail = host.compactViewportMode();

        // Node background
        int bgColor = node.isSelected() ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_SECONDARY;
        if (isOverSidebar) {
            bgColor = UITheme.NODE_DIMMED_BG; // Grey when over sidebar for deletion
        }
        context.fill(x, y, x + width, y + height, bgColor);
        if (simpleStyle) {
            int tabColor;
            if (lowDetail) {
                tabColor = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_SECTION;
            } else if (isStopControl) {
                tabColor = isOverSidebar ? host.toGrayscale(UITheme.NODE_STOP_BG, 0.7f) : UITheme.NODE_STOP_BG;
            } else {
                int baseColor = node.getColor();
                tabColor = isOverSidebar ? host.toGrayscale(baseColor, 0.7f) : baseColor;
            }
            int tabRight = Math.min(x + MINIMAL_NODE_TAB_WIDTH, x + width - 1);
            if (tabRight > x + 1) {
                context.fill(x + 1, y + 1, tabRight, y + height - 1, tabColor);
            }
        }

        // Node border - use light blue for selection, grey for dragging, darker node type color for START/events, node type color otherwise
        int borderColor;
        if (node.isDragging()) {
            borderColor = UITheme.BORDER_DRAGGING; // Medium grey outline when dragging
        } else if (node.isSelected()) {
            borderColor = host.selectedNodeAccentColor();
        } else if (lowDetail) {
            borderColor = UITheme.BORDER_SUBTLE;
        } else if (node.getType() == NodeType.START) {
            borderColor = isOverSidebar ? host.toGrayscale(UITheme.NODE_START_BORDER, 0.75f) : UITheme.NODE_START_BORDER; // Darker green for START
        } else if (!simpleStyle
            && (node.getType() == NodeType.EVENT_FUNCTION || node.getType() == NodeType.ROUTINE_ENTRY)) {
            borderColor = isOverSidebar ? host.toGrayscale(UITheme.NODE_EVENT_BORDER, 0.75f) : UITheme.NODE_EVENT_BORDER; // Darker pink for event functions
        } else if (node.getType() == NodeType.EVENT_CALL) {
            borderColor = isOverSidebar ? host.toGrayscale(UITheme.NODE_EVENT_CALL_BG, 0.75f) : UITheme.NODE_EVENT_CALL_BG;
        } else if (node.getType() == NodeType.VARIABLE) {
            borderColor = isOverSidebar ? host.toGrayscale(UITheme.NODE_VARIABLE_BORDER, 0.75f) : UITheme.NODE_VARIABLE_BORDER; // Darker orange for variables
        } else if (isStopControl) {
            borderColor = isOverSidebar ? host.toGrayscale(UITheme.NODE_STOP_BORDER, 0.75f) : UITheme.NODE_STOP_BORDER;
        } else if (simpleStyle) {
            int baseColor = node.getColor();
            borderColor = isOverSidebar ? host.toGrayscale(baseColor, 0.6f) : host.adjustColorBrightness(baseColor, 1.1f);
        } else {
            borderColor = node.getColor();
        }
        if (isOverSidebar && node.getType() != NodeType.START && !node.isDragging()) {
            borderColor = UITheme.BORDER_SUBTLE; // Darker grey border when over sidebar (for regular nodes)
        }
        DrawContextBridge.drawBorderInLayer(context, x, y, width, height, borderColor);

        // Node header (only for non-START/event function nodes)
        if (simpleStyle) {
            boolean isOperator = node.getType() == NodeType.OPERATOR_EQUALS
                || node.getType() == NodeType.OPERATOR_NOT
                || node.getType() == NodeType.OPERATOR_BOOLEAN_OR
                || node.getType() == NodeType.OPERATOR_BOOLEAN_AND
                || node.getType() == NodeType.OPERATOR_BOOLEAN_XOR;
            boolean isRoutineNode = node.getType() == NodeType.ROUTINE_ENTRY
                || node.getType() == NodeType.ROUTINE_CALL
                || node.getType() == NodeType.ROUTINE_INPUT;
            String label = isRoutineNode
                ? node.getDisplayName().getString()
                : node.getType().getDisplayName().toUpperCase(Locale.ROOT);
            boolean isActivateNode = node.getType() == NodeType.START_CHAIN;
            int titleColor = (isStopControl || isActivateNode)
                ? UITheme.TEXT_PRIMARY
                : (isOverSidebar ? UITheme.TEXT_LABEL : UITheme.TEXT_PRIMARY);
            int textX;
            int textY;
            if (node.hasStopTargetInputField()) {
                int contentLeft = x + MINIMAL_NODE_TAB_WIDTH;
                textX = contentLeft + 4;
                textY = y + 4;
            } else if (!isOperator) {
                int contentLeft = x + MINIMAL_NODE_TAB_WIDTH;
                int contentWidth = Math.max(0, width - MINIMAL_NODE_TAB_WIDTH);
                int reservedWidth = node.supportsRuntimeValueScope() ? NODE_HEADER_BUTTON_SIZE + 4 : 0;
                String displayLabel = host.trimTextToWidth(label, textRenderer,
                    Math.max(0, contentWidth - 8 - reservedWidth));
                int textWidth = textRenderer.width(displayLabel);
                textX = contentLeft + Math.max(4, (contentWidth - textWidth) / 2);
                textY = y + (height - textRenderer.lineHeight) / 2;
                label = displayLabel;
            } else {
                textX = 0;
                textY = 0;
            }
            if (!isOperator && !host.isComparisonOperator(node)) {
                host.drawNodeText(context, textRenderer, label, textX, textY, titleColor);
            }
        } else if (node.getType() != NodeType.START
            && node.getType() != NodeType.EVENT_FUNCTION
            && node.getType() != NodeType.ROUTINE_ENTRY
            && node.getType() != NodeType.VARIABLE
            && node.getType() != NodeType.ROUTINE_INPUT
            && node.getType() != NodeType.TEMPLATE
            && node.getType() != NodeType.OPERATOR_EQUALS
            && node.getType() != NodeType.OPERATOR_NOT
            && node.getType() != NodeType.OPERATOR_BOOLEAN_XOR) {
            if (!lowDetail) {
                int headerColor = node.getColor() & UITheme.NODE_HEADER_ALPHA_MASK;
                if (isOverSidebar) {
                    headerColor = UITheme.NODE_HEADER_DIMMED; // Grey header when over sidebar
                }
                context.fill(x + 1, y + 1, x + width - 1, y + 14, headerColor);
            }

            // Node title
            int titleColor = isOverSidebar ? UITheme.TEXT_TERTIARY : (lowDetail ? UITheme.TEXT_SECONDARY : UITheme.TEXT_PRIMARY);
            Component displayName = node.getDisplayName();
            if (node.supportsRuntimeValueScope()) {
                displayName = Component.literal(host.trimTextToWidth(displayName.getString(), textRenderer,
                    Math.max(0, width - NODE_HEADER_BUTTON_SIZE - 10)));
            }
            host.drawNodeText(
                context,
                textRenderer,
                displayName,
                x + 4,
                y + 4,
                titleColor
            );
        }

        host.renderNodeSockets(context, node, isOverSidebar, lowDetail);
        host.renderNodeContent(context, textRenderer, node, mouseX, mouseY, x, y, width, height,
            isOverSidebar, simpleStyle, lowDetail);
    }
}
