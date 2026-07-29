package com.pathmind.screen;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.control.PathmindWorkspaceChrome;
import com.pathmind.ui.control.UiHitTest;
import com.pathmind.ui.graph.NodeGraph;
import com.pathmind.ui.graph.StickyNoteResizeCorner;
import com.pathmind.ui.sidebar.Sidebar;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.InputCompatibilityBridge;
import com.pathmind.util.MatrixStackBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Owns the workspace viewport chrome: the custom cursor, the background grid,
 * the zoom controls, and the translucent preview drawn while a node is dragged
 * out of the sidebar.
 *
 * <p>The controller reads drag state through {@link Host} rather than owning it,
 * because the sidebar drag lifecycle belongs to the workspace interaction layer.
 */
final class PathmindWorkspaceViewportController {
    private static final int TITLE_BAR_HEIGHT = 20;
    private static final int ZOOM_BUTTON_SIZE = 14;
    private static final int ZOOM_BUTTON_MARGIN = 6;
    private static final int ZOOM_BUTTON_SPACING = 4;
    private static final int GRID_SIZE = 20;
    private static final int DRAG_PREVIEW_ALPHA = 0x80;

    interface Host {
        Font font();
        int screenWidth();
        int screenHeight();
        int accentColor();
        Minecraft client();
        NodeGraph nodeGraph();
        Sidebar sidebar();
        boolean showGrid();
        boolean isSidebarDragActive();
        NodeType draggingNodeType();
        Node draggingSidebarNode();
        boolean isNodeDragBlocked(NodeType nodeType);
        void closePresetDropdown();
    }

    private final Host host;
    private boolean systemCursorHidden = false;

    PathmindWorkspaceViewportController(Host host) {
        this.host = host;
    }

    // --- Custom cursor -----------------------------------------------------

    void ensureSystemCursorHidden() {
        if (systemCursorHidden) {
            return;
        }
        PathmindCursor.hideSystemCursor(resolveClient());
        systemCursorHidden = true;
    }

    void restoreSystemCursor() {
        if (!systemCursorHidden) {
            return;
        }
        PathmindCursor.showSystemCursor(resolveClient());
        systemCursorHidden = false;
    }

    void renderCursor(GuiGraphics context, int mouseX, int mouseY) {
        StickyNoteResizeCorner resizeCorner = hoveredStickyNoteResizeCorner(mouseX, mouseY);
        PathmindCursor.render(context, resolveCursorTexture(mouseX, mouseY, resizeCorner), mouseX, mouseY);
    }

    private Identifier resolveCursorTexture(int mouseX, int mouseY, StickyNoteResizeCorner resizeCorner) {
        NodeGraph nodeGraph = host.nodeGraph();
        Sidebar sidebar = host.sidebar();
        if (nodeGraph.isConnectionCutActive()) {
            return PathmindCursor.CUT_TEXTURE;
        }
        if (host.isSidebarDragActive()
            || nodeGraph.isAnyNodeBeingDragged()
            || nodeGraph.isPanning()) {
            return PathmindCursor.GRABBING_TEXTURE;
        }

        boolean overWorkspace = isOverWorkspace(mouseX, mouseY);
        if (overWorkspace && InputCompatibilityBridge.hasControlDown()) {
            return PathmindCursor.CUT_TEXTURE;
        }
        if (sidebar.isHoveringNode()) {
            if (host.isNodeDragBlocked(sidebar.getHoveredNodeType())) {
                return PathmindCursor.DISABLED_TEXTURE;
            }
            return PathmindCursor.GRAB_TEXTURE;
        }
        Node hoveredNode = overWorkspace ? nodeGraph.getNodeAt(mouseX, mouseY) : null;
        if (resizeCorner != null) {
            return switch (resizeCorner) {
                case TOP_LEFT -> PathmindCursor.SCALE_TOP_LEFT_TEXTURE;
                case TOP_RIGHT -> PathmindCursor.SCALE_TOP_RIGHT_TEXTURE;
                case BOTTOM_LEFT -> PathmindCursor.SCALE_BOTTOM_LEFT_TEXTURE;
                case BOTTOM_RIGHT -> PathmindCursor.SCALE_TEXTURE;
            };
        }
        if (hoveredNode != null && nodeGraph.isPointInsideInteractiveNodeControl(hoveredNode, mouseX, mouseY)) {
            return PathmindCursor.DEFAULT_TEXTURE;
        }
        if (overWorkspace && (hoveredNode != null
            || nodeGraph.getConnectionAt(mouseX, mouseY) != null)) {
            return PathmindCursor.GRAB_TEXTURE;
        }

        return PathmindCursor.DEFAULT_TEXTURE;
    }

    private StickyNoteResizeCorner hoveredStickyNoteResizeCorner(int mouseX, int mouseY) {
        if (!isOverWorkspace(mouseX, mouseY)) {
            return null;
        }
        NodeGraph nodeGraph = host.nodeGraph();
        Node hoveredNode = nodeGraph.getNodeAt(mouseX, mouseY);
        return hoveredNode == null ? null : nodeGraph.getStickyNoteResizeCornerAt(hoveredNode, mouseX, mouseY);
    }

    // --- Background grid ---------------------------------------------------

    void renderGrid(GuiGraphics context) {
        if (!host.showGrid()) {
            return;
        }
        NodeGraph nodeGraph = host.nodeGraph();
        int startX = Sidebar.getCollapsedWidth();
        int startY = TITLE_BAR_HEIGHT;
        int endX = host.screenWidth();
        int endY = host.screenHeight();

        int leftWorld = nodeGraph.screenToWorldX(startX);
        int rightWorld = nodeGraph.screenToWorldX(endX);
        if (rightWorld < leftWorld) {
            int swap = leftWorld;
            leftWorld = rightWorld;
            rightWorld = swap;
        }

        int topWorld = nodeGraph.screenToWorldY(startY);
        int bottomWorld = nodeGraph.screenToWorldY(endY);
        if (bottomWorld < topWorld) {
            int swap = topWorld;
            topWorld = bottomWorld;
            bottomWorld = swap;
        }

        int firstVertical = leftWorld - Math.floorMod(leftWorld, GRID_SIZE);
        for (int worldX = firstVertical; worldX <= rightWorld + GRID_SIZE; worldX += GRID_SIZE) {
            int screenX = nodeGraph.worldToScreenX(worldX);
            if (screenX < startX || screenX > endX) {
                continue;
            }
            context.vLine(screenX, startY, endY, UITheme.GRID_LINE);
        }

        int firstHorizontal = topWorld - Math.floorMod(topWorld, GRID_SIZE);
        for (int worldY = firstHorizontal; worldY <= bottomWorld + GRID_SIZE; worldY += GRID_SIZE) {
            int screenY = nodeGraph.worldToScreenY(worldY);
            if (screenY < startY || screenY > endY) {
                continue;
            }
            context.hLine(startX, endX, screenY, UITheme.GRID_LINE);
        }
    }

    // --- Sidebar drag preview ----------------------------------------------

    void renderDragPreview(GuiGraphics context, int mouseX, int mouseY) {
        if (!host.isSidebarDragActive()) {
            return;
        }
        NodeType draggingNodeType = host.draggingNodeType();
        Node draggingSidebarNode = host.draggingSidebarNode();
        if (draggingNodeType == null && draggingSidebarNode == null) {
            return;
        }

        NodeGraph nodeGraph = host.nodeGraph();
        float scale = nodeGraph.getZoomScale();
        if (scale <= 0.0f) {
            scale = 1.0f;
        }

        // Create a temporary node for rendering
        Node tempNode = draggingSidebarNode != null ? draggingSidebarNode : new Node(draggingNodeType, 0, 0);
        tempNode.setDragging(true);

        int width = tempNode.getWidth();
        int height = tempNode.getHeight();
        int worldMouseX = nodeGraph.screenToWorldX(mouseX);
        int worldMouseY = nodeGraph.screenToWorldY(mouseY);
        int[] previewPosition = nodeGraph.getSidebarDragPreviewPosition(tempNode, worldMouseX, worldMouseY);
        int screenNodeX = nodeGraph.worldToScreenX(previewPosition[0]);
        int screenNodeY = nodeGraph.worldToScreenY(previewPosition[1]);

        var matrices = context.pose();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.scale(matrices, scale, scale);

        int x = Math.round(screenNodeX / scale);
        int y = Math.round(screenNodeY / scale);

        // Update temp node position for rendering
        tempNode.setPosition(x, y);

        // Render the node with a slight transparency
        NodeType renderType = tempNode.getType();
        int nodeColor = (tempNode.getColor() & 0x00FFFFFF) | DRAG_PREVIEW_ALPHA;

        // Node background with transparency
        context.fill(x, y, x + width, y + height, UITheme.DRAG_PREVIEW_BG);
        // Draw grey outline for dragging state
        DrawContextBridge.drawBorderInLayer(context, x, y, width, height, UITheme.DRAG_PREVIEW_BORDER);

        // Node header
        if (renderType != NodeType.START && renderType != NodeType.EVENT_FUNCTION) {
            context.fill(x + 1, y + 1, x + width - 1, y + 14, nodeColor);
            context.drawString(
                host.font(),
                Component.literal(renderType == NodeType.TEMPLATE ? tempNode.getTemplateName() : renderType.getDisplayName()),
                x + 4,
                y + 4,
                UITheme.TEXT_HEADER
            );
        }

        MatrixStackBridge.pop(matrices);
    }

    // --- Zoom controls -----------------------------------------------------

    void renderZoomControls(GuiGraphics context, int mouseX, int mouseY, boolean disabled) {
        int buttonY = zoomButtonY();
        NodeGraph.ZoomLevel level = host.nodeGraph().getZoomLevel();
        boolean minusActive = level != NodeGraph.ZoomLevel.FOCUSED;
        boolean plusActive = level == NodeGraph.ZoomLevel.FOCUSED;
        drawZoomButton(context, zoomMinusButtonX(), buttonY, mouseX, mouseY, disabled, true, minusActive);
        drawZoomButton(context, zoomPlusButtonX(), buttonY, mouseX, mouseY, disabled, false, plusActive);
    }

    private void drawZoomButton(GuiGraphics context, int x, int y, int mouseX, int mouseY,
                                boolean disabled, boolean isMinus, boolean active) {
        boolean hovered = !disabled && UiHitTest.contains(mouseX, mouseY, x, y, ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE);
        String hoverKey = isMinus ? "zoom-minus-button" : "zoom-plus-button";
        float hoverProgress = HoverAnimator.getProgress(hoverKey, hovered || active);
        int accentColor = host.accentColor();
        PathmindWorkspaceChrome.drawToolbarButtonFrame(
            context, x, y, ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE, hovered, active, disabled, hoverProgress, accentColor);

        int iconColor = UITheme.TEXT_PRIMARY;
        if (disabled) {
            iconColor = UITheme.DROPDOWN_ACTION_DISABLED;
        } else if (hovered) {
            iconColor = accentColor;
        }

        Font font = host.font();
        Component iconText = Component.literal(isMinus ? "-" : "+");
        int iconWidth = font.width(iconText);
        int iconX = x + (ZOOM_BUTTON_SIZE - iconWidth) / 2 + 1;
        int iconY = y + (ZOOM_BUTTON_SIZE - font.lineHeight) / 2 + 2;
        context.drawString(font, iconText, iconX, iconY, iconColor);
    }

    /**
     * Applies a zoom step when either zoom button was clicked.
     *
     * @return {@code true} when the click landed on a zoom button and was consumed
     */
    boolean handleZoomButtonClick(int mouseX, int mouseY) {
        if (isPointInZoomMinus(mouseX, mouseY)) {
            host.closePresetDropdown();
            host.nodeGraph().zoomOut(workspaceCenterX(), workspaceCenterY());
            return true;
        }
        if (isPointInZoomPlus(mouseX, mouseY)) {
            host.closePresetDropdown();
            host.nodeGraph().zoomIn(workspaceCenterX(), workspaceCenterY());
            return true;
        }
        return false;
    }

    void zoomByScroll(double verticalAmount) {
        host.nodeGraph().zoomByScroll(verticalAmount, workspaceCenterX(), workspaceCenterY());
    }

    private boolean isPointInZoomMinus(int mouseX, int mouseY) {
        return UiHitTest.contains(mouseX, mouseY, zoomMinusButtonX(), zoomButtonY(), ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE);
    }

    private boolean isPointInZoomPlus(int mouseX, int mouseY) {
        return UiHitTest.contains(mouseX, mouseY, zoomPlusButtonX(), zoomButtonY(), ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE);
    }

    private int zoomPlusButtonX() {
        return host.screenWidth() - ZOOM_BUTTON_MARGIN - ZOOM_BUTTON_SIZE;
    }

    private int zoomMinusButtonX() {
        return zoomPlusButtonX() - ZOOM_BUTTON_SIZE - ZOOM_BUTTON_SPACING;
    }

    private int zoomButtonY() {
        return host.screenHeight() - ZOOM_BUTTON_MARGIN - ZOOM_BUTTON_SIZE;
    }

    private int workspaceCenterX() {
        int workspaceLeft = Sidebar.getCollapsedWidth();
        return workspaceLeft + (host.screenWidth() - workspaceLeft) / 2;
    }

    private int workspaceCenterY() {
        return TITLE_BAR_HEIGHT + (host.screenHeight() - TITLE_BAR_HEIGHT) / 2;
    }

    // --- Shared helpers ----------------------------------------------------

    private boolean isOverWorkspace(int mouseX, int mouseY) {
        return mouseX >= host.sidebar().getWidth() && mouseY > TITLE_BAR_HEIGHT;
    }

    private Minecraft resolveClient() {
        Minecraft client = host.client();
        return client != null ? client : Minecraft.getInstance();
    }
}
