package com.pathmind.ui.graph;

import com.pathmind.execution.ExecutionManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;

final class ConnectionRenderer {

    static final int VIEWPORT_CULL_MARGIN = 64;
    private static final int CONNECTION_DOT_SPACING = 12;
    private static final int CONNECTION_DOT_LENGTH = 4;
    private static final int CONNECTION_ANIMATION_STEP_MS = 50;

    interface Host {
        List<NodeConnection> getConnections();
        List<Node> getVisibleRootsForViewport();
        int getViewportWorldWidth();
        int getViewportWorldHeight();
        int getCameraX();
        int getCameraY();
        boolean isDenseViewportMode();
        boolean shouldRenderConnectionsOnTop();
        Node getParentForNode(Node node);
        boolean shouldConsiderConnectionForViewport(NodeConnection connection, Set<Node> visibleRoots,
                                                    int viewportWidth, int viewportHeight);
        boolean isNodeOverSidebarForRender(Node node, int screenX, int screenWidth);
        int toGrayscale(int color, float brightnessFactor);
        int getSelectedNodeAccentColor();
        void renderSocket(GuiGraphics context, int x, int y, boolean isInput, int color);
        void setProfilerConnectionMs(double profilerConnectionMs);
    }

    private final Host host;
    private final ConnectionController controller;

    ConnectionRenderer(Host host, ConnectionController controller) {
        this.host = host;
        this.controller = controller;
    }

    int renderConnections(GuiGraphics context, boolean onlyDragged, boolean trackProfiler) {
        ExecutionManager manager = ExecutionManager.getInstance();
        boolean denseViewportMode = host.isDenseViewportMode();
        boolean animateConnections = manager.isExecuting() && !denseViewportMode;
        long animationTimestamp = System.currentTimeMillis();
        int viewportWidth = host.getViewportWorldWidth();
        int viewportHeight = host.getViewportWorldHeight();
        Set<Node> visibleRoots = new HashSet<>(host.getVisibleRootsForViewport());
        long startNanos = trackProfiler ? System.nanoTime() : 0L;
        int drawnConnections = 0;

        if (!onlyDragged) {
            for (NodeConnection connection : host.getConnections()) {
                if (!host.shouldConsiderConnectionForViewport(connection, visibleRoots, viewportWidth, viewportHeight)) {
                    continue;
                }
                if (renderConnection(context, connection, animateConnections, animationTimestamp, viewportWidth, viewportHeight, manager)) {
                    drawnConnections++;
                }
            }
        } else if (host.shouldRenderConnectionsOnTop()) {
            for (NodeConnection connection : host.getConnections()) {
                if (shouldRenderConnectionInDraggedPass(connection)) {
                    if (renderConnection(context, connection, animateConnections, animationTimestamp, viewportWidth, viewportHeight, manager)) {
                        drawnConnections++;
                    }
                }
            }
        }

        // Render dragging connection if active
        Node connectionSourceNode = controller.getConnectionSourceNode();
        boolean isOutputSocket = controller.isOutputSocket();
        int cameraX = host.getCameraX();
        int cameraY = host.getCameraY();
        if (controller.isDraggingConnection() && connectionSourceNode != null) {
            int sourceX = connectionSourceNode.getSocketX(!isOutputSocket) - cameraX;
            int sourceY = connectionSourceNode.getSocketY(controller.getConnectionSourceSocket(), !isOutputSocket) - cameraY;
            int targetX = controller.getConnectionDragX() - cameraX;
            int targetY = controller.getConnectionDragY() - cameraY;

            // Snap to hovered socket if available
            Node hoveredNode = controller.getHoveredNode();
            int hoveredSocket = controller.getHoveredSocket();
            boolean hoveredSocketIsInput = controller.isHoveredSocketInput();
            if (hoveredNode != null && hoveredSocket != -1) {
                targetX = hoveredNode.getSocketX(hoveredSocketIsInput) - cameraX;
                targetY = hoveredNode.getSocketY(hoveredSocket, hoveredSocketIsInput) - cameraY;

                if (onlyDragged) {
                    // Highlight the target socket above nodes while dragging.
                    host.renderSocket(context, targetX, targetY, hoveredSocketIsInput, host.getSelectedNodeAccentColor());
                }
            }

            if (!onlyDragged) {
                // Render the dragging connection below sockets in the main layer.
                int color = connectionSourceNode.getOutputSocketColor(controller.getConnectionSourceSocket());
                int sourceScreenX = connectionSourceNode.getX() - cameraX;
                if (host.isNodeOverSidebarForRender(connectionSourceNode, sourceScreenX, connectionSourceNode.getWidth())) {
                    color = host.toGrayscale(color, 0.65f);
                }

                if (animateConnections) {
                    renderAnimatedConnectionCurve(context, sourceX, sourceY, targetX, targetY,
                            color, animationTimestamp);
                } else if (denseViewportMode) {
                    renderDenseConnectionCurve(context, sourceX, sourceY, targetX, targetY, color);
                } else {
                    renderConnectionCurve(context, sourceX, sourceY, targetX, targetY,
                            color);
                }
            }
        }

        if (!onlyDragged && controller.isConnectionCutActive()) {
            controller.renderConnectionCutPreview(context, cameraX, cameraY);
        }
        if (trackProfiler) {
            host.setProfilerConnectionMs((System.nanoTime() - startNanos) / 1_000_000.0);
        }
        return drawnConnections;
    }

    private boolean renderConnection(GuiGraphics context, NodeConnection connection, boolean animateConnections,
                                     long animationTimestamp, int viewportWidth, int viewportHeight,
                                     ExecutionManager manager) {
        if (connection == null) {
            return false;
        }

        Node outputNode = connection.getOutputNode();
        Node inputNode = connection.getInputNode();

        if (outputNode == null || inputNode == null
            || !outputNode.shouldRenderSockets() || !inputNode.shouldRenderSockets()) {
            return false;
        }

        int cameraX = host.getCameraX();
        int cameraY = host.getCameraY();
        int outputX = outputNode.getSocketX(false) - cameraX;
        int outputY = outputNode.getSocketY(connection.getOutputSocket(), false) - cameraY;
        int inputX = inputNode.getSocketX(true) - cameraX;
        int inputY = inputNode.getSocketY(connection.getInputSocket(), true) - cameraY;
        int minX = Math.min(outputX, inputX) - VIEWPORT_CULL_MARGIN;
        int maxX = Math.max(outputX, inputX) + VIEWPORT_CULL_MARGIN;
        int minY = Math.min(outputY, inputY) - VIEWPORT_CULL_MARGIN;
        int maxY = Math.max(outputY, inputY) + VIEWPORT_CULL_MARGIN;
        if (viewportWidth > 0 && viewportHeight > 0
            && (maxX < 0 || minX > viewportWidth || maxY < 0 || minY > viewportHeight)) {
            return false;
        }

        int color = outputNode.getOutputSocketColor(connection.getOutputSocket());
        boolean denseViewportMode = host.isDenseViewportMode();
        if (!denseViewportMode && shouldGrayOutConnection(outputNode, inputNode)) {
            color = host.toGrayscale(color, 0.65f);
        }
        if (connection == controller.getInsertionPreviewConnection()) {
            color = host.getSelectedNodeAccentColor();
        }

        if (animateConnections && manager.shouldAnimateConnection(connection)) {
            renderAnimatedConnectionCurve(context, outputX, outputY, inputX, inputY, color, animationTimestamp);
        } else if (denseViewportMode) {
            renderDenseConnectionCurve(context, outputX, outputY, inputX, inputY, color);
        } else {
            renderConnectionCurve(context, outputX, outputY, inputX, inputY, color);
        }
        return true;
    }

    private boolean shouldGrayOutConnection(Node outputNode, Node inputNode) {
        if (outputNode == null || inputNode == null) {
            return false;
        }
        int cameraX = host.getCameraX();
        int outputScreenX = outputNode.getX() - cameraX;
        int inputScreenX = inputNode.getX() - cameraX;
        return host.isNodeOverSidebarForRender(outputNode, outputScreenX, outputNode.getWidth())
            || host.isNodeOverSidebarForRender(inputNode, inputScreenX, inputNode.getWidth());
    }

    boolean shouldRenderConnectionInDraggedPass(NodeConnection connection) {
        if (connection == null) {
            return false;
        }
        return isNodeInDraggedHierarchy(connection.getOutputNode())
            || isNodeInDraggedHierarchy(connection.getInputNode());
    }

    private boolean isNodeInDraggedHierarchy(Node node) {
        if (node == null) {
            return false;
        }
        if (node.isDragging()) {
            return true;
        }
        Node parent = host.getParentForNode(node);
        while (parent != null) {
            if (parent.isDragging()) {
                return true;
            }
            parent = host.getParentForNode(parent);
        }
        return false;
    }

    static void renderAnimatedConnectionCurve(GuiGraphics context, int x1, int y1, int x2, int y2, int color, long timestamp) {
        int midX = x1 + (x2 - x1) / 2;

        int firstSegmentLength = Math.abs(midX - x1);
        int secondSegmentLength = Math.abs(y2 - y1);

        int animationOffset = (int) ((timestamp / CONNECTION_ANIMATION_STEP_MS) % CONNECTION_DOT_SPACING);

        drawAnimatedSegment(context, x1, y1, midX, y1, true, color, animationOffset, 0);
        drawAnimatedSegment(context, midX, y1, midX, y2, false, color, animationOffset, firstSegmentLength);
        drawAnimatedSegment(context, midX, y2, x2, y2, true, color, animationOffset,
                firstSegmentLength + secondSegmentLength);
    }

    private static void drawAnimatedSegment(GuiGraphics context, int x1, int y1, int x2, int y2, boolean horizontal,
                                            int color, int animationOffset, int distanceOffset) {
        int length = horizontal ? Math.abs(x2 - x1) : Math.abs(y2 - y1);
        if (length == 0) {
            return;
        }

        int direction = horizontal ? Integer.compare(x2, x1) : Integer.compare(y2, y1);
        int start = horizontal ? x1 : y1;
        int staticCoord = horizontal ? y1 : x1;

        int initialOffset = mod(distanceOffset - animationOffset, CONNECTION_DOT_SPACING);
        int stepStart = (CONNECTION_DOT_SPACING - initialOffset) % CONNECTION_DOT_SPACING;

        int position = stepStart;
        while (position > 0) {
            position -= CONNECTION_DOT_SPACING;
        }

        boolean drewSegment = false;

        for (; position <= length; position += CONNECTION_DOT_SPACING) {
            int minDistance = Math.max(position, 0);
            int maxDistance = Math.min(position + CONNECTION_DOT_LENGTH - 1, length);
            if (maxDistance < 0 || minDistance > length || minDistance > maxDistance) {
                continue;
            }

            drewSegment = true;

            int startPos = start + minDistance * direction;
            int endPos = start + maxDistance * direction;

            if (horizontal) {
                int minX = Math.min(startPos, endPos);
                int maxX = Math.max(startPos, endPos);
                context.hLine(minX, maxX, staticCoord, color);
            } else {
                int minY = Math.min(startPos, endPos);
                int maxY = Math.max(startPos, endPos);
                context.vLine(staticCoord, minY, maxY, color);
            }
        }

        if (!drewSegment) {
            int fallbackLength = Math.min(CONNECTION_DOT_LENGTH, length);
            int minDistance = Math.max(0, length - fallbackLength);
            int maxDistance = length;
            int startPos = start + minDistance * direction;
            int endPos = start + maxDistance * direction;

            if (horizontal) {
                int minX = Math.min(startPos, endPos);
                int maxX = Math.max(startPos, endPos);
                context.hLine(minX, maxX, staticCoord, color);
            } else {
                int minY = Math.min(startPos, endPos);
                int maxY = Math.max(startPos, endPos);
                context.vLine(staticCoord, minY, maxY, color);
            }
        }
    }

    private static int mod(int value, int mod) {
        int result = value % mod;
        return result < 0 ? result + mod : result;
    }

    static void renderConnectionCurve(GuiGraphics context, int x1, int y1, int x2, int y2, int color) {
        // Draw a simple L-shaped connection line
        int midX = x1 + (x2 - x1) / 2;

        // Horizontal line from source to middle
        context.hLine(Math.min(x1, midX), Math.max(x1, midX), y1, color);

        // Vertical line from middle to target
        context.vLine(midX, Math.min(y1, y2), Math.max(y1, y2), color);

        // Horizontal line from middle to target
        context.hLine(Math.min(midX, x2), Math.max(midX, x2), y2, color);
    }

    static void drawSegmentWithThickness(GuiGraphics context, int x1, int y1, int x2, int y2, int color, int thickness) {
        if (x1 == x2 && y1 == y2) {
            context.fill(x1 - thickness, y1 - thickness, x1 + thickness + 1, y1 + thickness + 1, color);
            return;
        }

        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            float t = steps == 0 ? 0.0f : (float) i / (float) steps;
            int x = Math.round(x1 + (x2 - x1) * t);
            int y = Math.round(y1 + (y2 - y1) * t);
            context.fill(x - thickness, y - thickness, x + thickness + 1, y + thickness + 1, color);
        }
    }

    static void renderDenseConnectionCurve(GuiGraphics context, int x1, int y1, int x2, int y2, int color) {
        context.hLine(Math.min(x1, x2), Math.max(x1, x2), y1, color);
        context.vLine(x2, Math.min(y1, y2), Math.max(y1, y2), color);
    }
}
