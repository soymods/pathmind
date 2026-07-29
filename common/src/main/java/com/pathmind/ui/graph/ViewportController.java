package com.pathmind.ui.graph;

import static com.pathmind.ui.graph.ConnectionRenderer.VIEWPORT_CULL_MARGIN;

import com.pathmind.nodes.Node;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ViewportController {
    interface Host {
        void rebuildHierarchyCacheIfNeeded();
        List<Node> cachedRootNodes();
        Map<Node, NodeGraph.SelectionBounds> cachedHierarchyBounds();
        Map<Node, Integer> cachedHierarchyNodeCounts();
        String activePreset();
    }

    private static final float ZOOM_SCROLL_STEP = 1.12f;
    private static final float ZOOM_EPSILON = 0.0001f;
    private static final Map<String, SessionViewportState> SESSION_VIEWPORT_STATES = new ConcurrentHashMap<>();

    private final Host host;
    private final List<Node> cachedVisibleRootNodes = new ArrayList<>();
    private int cameraX;
    private int cameraY;
    private boolean isPanning;
    private int panStartX;
    private int panStartY;
    private int panStartCameraX;
    private int panStartCameraY;
    private NodeGraph.ZoomLevel zoomLevel = NodeGraph.ZoomLevel.FOCUSED;
    private float zoomScale = NodeGraph.ZoomLevel.FOCUSED.getScale();
    private boolean visibleRootsDirty = true;
    private int cachedVisibleNodeCount;
    private int visibleRootsCameraX = Integer.MIN_VALUE;
    private int visibleRootsCameraY = Integer.MIN_VALUE;
    private int visibleRootsViewportWidth = Integer.MIN_VALUE;
    private int visibleRootsViewportHeight = Integer.MIN_VALUE;
    private boolean compactViewportMode;
    private boolean denseViewportMode;

    ViewportController(Host host) {
        this.host = host;
    }

    int getCameraX() {
        return cameraX;
    }

    int getCameraY() {
        return cameraY;
    }

    NodeGraph.ZoomLevel getZoomLevel() {
        return zoomLevel;
    }

    float getZoomScale() {
        return zoomScale;
    }

    boolean isZoomedOut() {
        return zoomScale < (NodeGraph.ZoomLevel.FOCUSED.getScale() - ZOOM_EPSILON);
    }

    void setZoomLevel(NodeGraph.ZoomLevel newLevel, int anchorScreenX, int anchorScreenY) {
        if (newLevel == null || newLevel == this.zoomLevel) {
            return;
        }
        int anchorWorldX = screenToWorldX(anchorScreenX);
        int anchorWorldY = screenToWorldY(anchorScreenY);
        this.zoomLevel = newLevel;
        this.zoomScale = newLevel.getScale();
        alignCameraToAnchor(anchorWorldX, anchorWorldY, anchorScreenX, anchorScreenY);
        cacheSessionViewportState();
    }

    private void alignCameraToAnchor(int anchorWorldX, int anchorWorldY, int anchorScreenX, int anchorScreenY) {
        float scale = getZoomScale();
        if (scale == 0.0f) {
            scale = 1.0f;
        }
        cameraX = Math.round(anchorWorldX - anchorScreenX / scale);
        cameraY = Math.round(anchorWorldY - anchorScreenY / scale);
    }

    boolean shouldRenderNodeText() {
        return zoomLevel.shouldShowText();
    }

    boolean canZoomIn() {
        return zoomScale < (NodeGraph.ZoomLevel.FOCUSED.getScale() - ZOOM_EPSILON);
    }

    boolean canZoomOut() {
        return zoomScale > (NodeGraph.ZoomLevel.DISTANT.getScale() + ZOOM_EPSILON);
    }

    void zoomIn(int anchorScreenX, int anchorScreenY) {
        NodeGraph.ZoomLevel target = getNextZoomInLevel();
        if (target != null) {
            setZoomLevel(target, anchorScreenX, anchorScreenY);
        }
    }

    void zoomOut(int anchorScreenX, int anchorScreenY) {
        NodeGraph.ZoomLevel target = getNextZoomOutLevel();
        if (target != null) {
            setZoomLevel(target, anchorScreenX, anchorScreenY);
        }
    }

    boolean isDefaultZoom() {
        return Math.abs(zoomScale - NodeGraph.ZoomLevel.FOCUSED.getScale()) <= ZOOM_EPSILON;
    }

    private NodeGraph.ZoomLevel getNextZoomInLevel() {
        NodeGraph.ZoomLevel target = null;
        for (NodeGraph.ZoomLevel level : NodeGraph.ZoomLevel.values()) {
            if (level.getScale() > zoomScale + ZOOM_EPSILON) {
                if (target == null || level.getScale() < target.getScale()) {
                    target = level;
                }
            }
        }
        return target;
    }

    private NodeGraph.ZoomLevel getNextZoomOutLevel() {
        NodeGraph.ZoomLevel target = null;
        for (NodeGraph.ZoomLevel level : NodeGraph.ZoomLevel.values()) {
            if (level.getScale() < zoomScale - ZOOM_EPSILON) {
                if (target == null || level.getScale() > target.getScale()) {
                    target = level;
                }
            }
        }
        return target;
    }

    void zoomByScroll(double scrollAmount, int anchorScreenX, int anchorScreenY) {
        if (scrollAmount == 0.0) {
            return;
        }
        float scaleFactor = (float) Math.pow(ZOOM_SCROLL_STEP, scrollAmount);
        setZoomScale(zoomScale * scaleFactor, anchorScreenX, anchorScreenY);
    }

    private void setZoomScale(float newScale, int anchorScreenX, int anchorScreenY) {
        float minScale = NodeGraph.ZoomLevel.DISTANT.getScale();
        float maxScale = NodeGraph.ZoomLevel.FOCUSED.getScale();
        float clampedScale = Mth.clamp(newScale, minScale, maxScale);
        if (Math.abs(clampedScale - zoomScale) <= ZOOM_EPSILON) {
            return;
        }
        int anchorWorldX = screenToWorldX(anchorScreenX);
        int anchorWorldY = screenToWorldY(anchorScreenY);
        zoomScale = clampedScale;
        updateZoomLevelFromScale();
        alignCameraToAnchor(anchorWorldX, anchorWorldY, anchorScreenX, anchorScreenY);
        cacheSessionViewportState();
    }

    private void updateZoomLevelFromScale() {
        float minScale = NodeGraph.ZoomLevel.DISTANT.getScale();
        float maxScale = NodeGraph.ZoomLevel.FOCUSED.getScale();
        if (zoomScale >= maxScale - ZOOM_EPSILON) {
            zoomLevel = NodeGraph.ZoomLevel.FOCUSED;
        } else if (zoomScale <= minScale + ZOOM_EPSILON) {
            zoomLevel = NodeGraph.ZoomLevel.DISTANT;
        } else {
            zoomLevel = NodeGraph.ZoomLevel.OVERVIEW;
        }
    }

    void startPanning(int mouseX, int mouseY) {
        isPanning = true;
        panStartX = mouseX;
        panStartY = mouseY;
        panStartCameraX = cameraX;
        panStartCameraY = cameraY;
    }

    void updatePanning(int mouseX, int mouseY) {
        if (isPanning) {
            int deltaX = mouseX - panStartX;
            int deltaY = mouseY - panStartY;
            float scale = getZoomScale();
            if (scale == 0.0f) {
                scale = 1.0f;
            }
            cameraX = panStartCameraX - Math.round(deltaX / scale); // Flip horizontal panning
            cameraY = panStartCameraY - Math.round(deltaY / scale); // Flip vertical panning
            cacheSessionViewportState();
        }
    }

    void stopPanning() {
        isPanning = false;
    }

    boolean isPanning() {
        return isPanning;
    }

    void resetCamera() {
        cameraX = 0;
        cameraY = 0;
        zoomLevel = NodeGraph.ZoomLevel.FOCUSED;
        zoomScale = NodeGraph.ZoomLevel.FOCUSED.getScale();
        cacheSessionViewportState();
    }

    void restoreSessionViewportState() {
        SessionViewportState state = SESSION_VIEWPORT_STATES.get(host.activePreset());
        if (state == null) {
            return;
        }
        cameraX = state.cameraX;
        cameraY = state.cameraY;
        zoomLevel = state.zoomLevel != null ? state.zoomLevel : NodeGraph.ZoomLevel.FOCUSED;
        zoomScale = state.zoomScale > 0.0f ? state.zoomScale : zoomLevel.getScale();
        updateZoomLevelFromScale();
    }

    void persistSessionViewportState() {
        cacheSessionViewportState();
    }

    private void cacheSessionViewportState() {
        String activePreset = host.activePreset();
        if (activePreset == null || activePreset.isEmpty()) {
            return;
        }
        SESSION_VIEWPORT_STATES.put(activePreset, new SessionViewportState(cameraX, cameraY, zoomLevel, zoomScale));
    }

    void focusNode(Node node, int screenWidth, int screenHeight, int sidebarWidth, int titleBarHeight) {
        int workspaceLeft = Math.max(0, sidebarWidth);
        int workspaceTop = Math.max(0, titleBarHeight);
        int workspaceWidth = Math.max(1, screenWidth - workspaceLeft);
        int workspaceHeight = Math.max(1, screenHeight - workspaceTop);
        float scale = getZoomScale();
        if (scale <= 0.0f) {
            scale = 1.0f;
        }

        int desiredScreenX = workspaceLeft + workspaceWidth / 2 - Math.round(node.getWidth() * scale / 2f);
        int desiredScreenY = workspaceTop + workspaceHeight / 2 - Math.round(node.getHeight() * scale / 2f);
        cameraX = node.getX() - Math.round((desiredScreenX - workspaceLeft) / scale);
        cameraY = node.getY() - Math.round((desiredScreenY - workspaceTop) / scale);
        cacheSessionViewportState();
    }

    int screenToWorldX(int screenX) {
        float scale = getZoomScale();
        if (scale == 0.0f) {
            scale = 1.0f;
        }
        return cameraX + Math.round(screenX / scale);
    }

    int screenToWorldY(int screenY) {
        float scale = getZoomScale();
        if (scale == 0.0f) {
            scale = 1.0f;
        }
        return cameraY + Math.round(screenY / scale);
    }

    int screenToUiX(int screenX) {
        float scale = getZoomScale();
        if (scale == 0.0f) {
            scale = 1.0f;
        }
        return Math.round(screenX / scale);
    }

    int screenToUiY(int screenY) {
        float scale = getZoomScale();
        if (scale == 0.0f) {
            scale = 1.0f;
        }
        return Math.round(screenY / scale);
    }

    int worldToScreenX(int worldX) {
        return Math.round((worldX - cameraX) * getZoomScale());
    }

    int worldToScreenY(int worldY) {
        return Math.round((worldY - cameraY) * getZoomScale());
    }

    void invalidateVisibleRoots() {
        visibleRootsDirty = true;
    }

    List<Node> getVisibleRootsForViewport() {
        host.rebuildHierarchyCacheIfNeeded();

        int viewportWidth = getViewportWorldWidth();
        int viewportHeight = getViewportWorldHeight();
        if (!visibleRootsDirty
            && visibleRootsCameraX == cameraX
            && visibleRootsCameraY == cameraY
            && visibleRootsViewportWidth == viewportWidth
            && visibleRootsViewportHeight == viewportHeight) {
            return cachedVisibleRootNodes;
        }

        cachedVisibleRootNodes.clear();
        cachedVisibleNodeCount = 0;
        for (Node root : host.cachedRootNodes()) {
            NodeGraph.SelectionBounds bounds = host.cachedHierarchyBounds().get(root);
            if (!intersectsViewport(bounds, viewportWidth, viewportHeight)) {
                continue;
            }
            cachedVisibleRootNodes.add(root);
            cachedVisibleNodeCount += host.cachedHierarchyNodeCounts().getOrDefault(root, 0);
        }

        visibleRootsDirty = false;
        visibleRootsCameraX = cameraX;
        visibleRootsCameraY = cameraY;
        visibleRootsViewportWidth = viewportWidth;
        visibleRootsViewportHeight = viewportHeight;
        return cachedVisibleRootNodes;
    }

    int getCachedVisibleNodeCount() {
        return cachedVisibleNodeCount;
    }

    int getViewportWorldWidth() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return 0;
        }
        return Math.round(client.getWindow().getGuiScaledWidth() / Math.max(0.0001f, getZoomScale()));
    }

    int getViewportWorldHeight() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return 0;
        }
        return Math.round(client.getWindow().getGuiScaledHeight() / Math.max(0.0001f, getZoomScale()));
    }

    boolean intersectsViewport(NodeGraph.SelectionBounds bounds) {
        return intersectsViewport(bounds, getViewportWorldWidth(), getViewportWorldHeight());
    }

    boolean intersectsViewport(NodeGraph.SelectionBounds bounds, int viewportWidth, int viewportHeight) {
        if (bounds == null) {
            return false;
        }
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return true;
        }
        int viewportLeft = cameraX - VIEWPORT_CULL_MARGIN;
        int viewportTop = cameraY - VIEWPORT_CULL_MARGIN;
        int viewportRight = cameraX + viewportWidth + VIEWPORT_CULL_MARGIN;
        int viewportBottom = cameraY + viewportHeight + VIEWPORT_CULL_MARGIN;
        return bounds.maxX >= viewportLeft
            && bounds.minX <= viewportRight
            && bounds.maxY >= viewportTop
            && bounds.minY <= viewportBottom;
    }

    boolean intersectsViewport(Node node) {
        if (node == null) {
            return false;
        }
        return intersectsViewport(new NodeGraph.SelectionBounds(
            node.getX(),
            node.getY(),
            node.getX() + node.getWidth(),
            node.getY() + node.getHeight()
        ));
    }

    void beginRenderFrame(boolean compactViewportMode) {
        this.compactViewportMode = compactViewportMode;
        this.denseViewportMode = false;
    }

    void endRenderFrame() {
        compactViewportMode = false;
        denseViewportMode = false;
    }

    boolean isCompactViewportMode() {
        return compactViewportMode;
    }

    boolean isDenseViewportMode() {
        return denseViewportMode;
    }

    private static final class SessionViewportState {
        private final int cameraX;
        private final int cameraY;
        private final NodeGraph.ZoomLevel zoomLevel;
        private final float zoomScale;

        private SessionViewportState(int cameraX, int cameraY, NodeGraph.ZoomLevel zoomLevel, float zoomScale) {
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            this.zoomLevel = zoomLevel;
            this.zoomScale = zoomScale;
        }
    }
}
