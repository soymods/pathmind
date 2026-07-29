package com.pathmind.screen;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.graph.NodeGraph;
import com.pathmind.ui.sidebar.Sidebar;
import com.pathmind.util.InputCompatibilityBridge;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Owns the workspace pointer-gesture lifecycle: dragging nodes out of the
 * sidebar, panning, connection cutting, the selection box, and the right-click
 * click-versus-drag discrimination that decides whether a context menu opens.
 *
 * <p>The screen keeps the top-level input ordering and calls into this
 * controller at the two points where workspace gestures are handled, which is
 * why drag and release each arrive as a pair of calls: preset-tab dragging is
 * interleaved between them and must keep its existing precedence.
 */
final class PathmindWorkspaceDragController {
    private static final int TITLE_BAR_HEIGHT = 20;
    private static final int CLICK_THRESHOLD = 5;  // pixels
    private static final long CLICK_TIME_THRESHOLD = 250;  // milliseconds

    interface Host {
        int screenWidth();
        int screenHeight();
        Minecraft client();
        NodeGraph nodeGraph();
        Sidebar sidebar();
        boolean isNodeDragBlocked(NodeType nodeType);
        boolean saveDraggedRoutineToLibrary(double mouseX, double mouseY, boolean fromLibrary, Node draggedNode);
        boolean importDraggedLibraryRoutineToList(double mouseX, double mouseY, boolean fromLibrary, Node draggedNode);
        Node dropDraggedSidebarNodeIntoWorkspace(int mouseX, int mouseY, boolean fromLibrary,
                                                 Node draggedNode, NodeType draggedType);
        void openRoutineWorkspaceTab(String routineId);
        void openLibraryRoutineWorkspaceTab(String routineId);
    }

    private final Host host;

    // Drag and drop state
    private boolean draggingFromSidebar = false;
    private boolean sidebarDragActivated = false;
    private int sidebarDragStartX = -1;
    private int sidebarDragStartY = -1;
    private NodeType draggingNodeType = null;
    private Node draggingSidebarNode = null;
    private boolean draggingFromRoutineLibrary = false;

    // Right-click context menu state
    private int rightClickStartX = -1;
    private int rightClickStartY = -1;
    private long rightClickStartTime = 0;
    private boolean cuttingConnections = false;

    PathmindWorkspaceDragController(Host host) {
        this.host = host;
    }

    // --- Drag state queried by rendering ------------------------------------

    boolean isSidebarDragActive() {
        return draggingFromSidebar && sidebarDragActivated;
    }

    NodeType draggingNodeType() {
        return draggingNodeType;
    }

    Node draggingSidebarNode() {
        return draggingSidebarNode;
    }

    /** Tells the sidebar whether to show its routine drop targets this frame. */
    void updateSidebarRoutineDragState() {
        boolean draggingRoutineCall = isSidebarDragActive()
            && draggingSidebarNode != null
            && draggingSidebarNode.getType() == NodeType.ROUTINE_CALL;
        host.sidebar().setRoutineDragState(draggingRoutineCall, draggingFromRoutineLibrary);
    }

    /**
     * Abandons any in-flight sidebar drag. Used when something else takes over
     * the screen, such as starting an execution or switching preset.
     */
    void clearSidebarDrag() {
        draggingFromSidebar = false;
        sidebarDragActivated = false;
        draggingNodeType = null;
        draggingSidebarNode = null;
        draggingFromRoutineLibrary = false;
    }

    // --- Gesture start ------------------------------------------------------

    /**
     * Arms a sidebar drag when the sidebar click landed on a draggable node.
     * Nodes whose backing mod is missing are ignored so they cannot be dragged.
     */
    void beginSidebarDragIfHovering(int mouseX, int mouseY) {
        Sidebar sidebar = host.sidebar();
        if (!sidebar.isHoveringNode()) {
            return;
        }
        NodeType hoveredType = sidebar.getHoveredNodeType();
        if (host.isNodeDragBlocked(hoveredType)) {
            return;
        }
        draggingFromSidebar = true;
        sidebarDragActivated = false;
        sidebarDragStartX = mouseX;
        sidebarDragStartY = mouseY;
        draggingNodeType = hoveredType;
        draggingSidebarNode = sidebar.createNodeFromSidebar(0, 0);
        draggingFromRoutineLibrary = sidebar.isHoveringLibraryRoutine();
        NodeGraph nodeGraph = host.nodeGraph();
        nodeGraph.resetDropTargets();
        nodeGraph.closeContextMenu();
    }

    /**
     * Handles the workspace press gestures that are not plain left clicks:
     * control-right-click starts a connection cut, right-click records a
     * candidate context-menu position, and middle-click starts panning.
     *
     * @return {@code true} when the press was consumed
     */
    boolean beginWorkspacePointerGesture(int mouseX, int mouseY, int button) {
        NodeGraph nodeGraph = host.nodeGraph();
        if (button == 1) {
            if (InputCompatibilityBridge.hasControlDown()) {
                cuttingConnections = true;
                nodeGraph.startConnectionCut(mouseX, mouseY);
                return true;
            }
            rightClickStartX = mouseX;
            rightClickStartY = mouseY;
            rightClickStartTime = System.currentTimeMillis();
            return true;
        }

        if (button == 2) {
            nodeGraph.startPanning(mouseX, mouseY);
            return true;
        }

        return false;
    }

    // --- Drag ---------------------------------------------------------------

    /**
     * Handles the drag gestures that outrank preset-tab dragging: sidebar
     * resizing and the marquee selection box.
     *
     * @return {@code true} when the drag was consumed
     */
    boolean handleSidebarAndSelectionBoxDrag(double mouseX, double mouseY, int button) {
        if (host.sidebar().mouseDragged(mouseX, mouseY, button)) {
            return true;
        }

        NodeGraph nodeGraph = host.nodeGraph();
        if (button == 0 && nodeGraph.isSelectionBoxActive()) {
            nodeGraph.updateSelectionBox((int) mouseX, (int) mouseY);
            return true;
        }

        return false;
    }

    /**
     * Handles the workspace drag gestures that rank below preset-tab dragging:
     * connection cutting, promoting a held right-click into a pan, previewing
     * the sidebar drop target, and moving nodes or the camera.
     *
     * @return {@code true} when the drag was consumed
     */
    boolean handleWorkspaceDrag(double mouseX, double mouseY, int button) {
        NodeGraph nodeGraph = host.nodeGraph();

        if (button == 1 && cuttingConnections) {
            nodeGraph.updateConnectionCut(nodeGraph.screenToWorldX((int) mouseX), nodeGraph.screenToWorldY((int) mouseY));
            return true;
        }

        if (button == 1 && rightClickStartX != -1 && !nodeGraph.isPanning()) {
            int dragDeltaX = Math.abs((int) mouseX - rightClickStartX);
            int dragDeltaY = Math.abs((int) mouseY - rightClickStartY);
            if (dragDeltaX > CLICK_THRESHOLD || dragDeltaY > CLICK_THRESHOLD) {
                nodeGraph.startPanning(rightClickStartX, rightClickStartY);
                rightClickStartX = -1;
                rightClickStartY = -1;
            }
        }

        // Handle dragging from sidebar
        if (draggingFromSidebar && button == 0) {
            sidebarDragActivated = sidebarDragActivated
                || Math.abs((int) mouseX - sidebarDragStartX) > CLICK_THRESHOLD
                || Math.abs((int) mouseY - sidebarDragStartY) > CLICK_THRESHOLD;
            if (!sidebarDragActivated) return true;
            if ((draggingNodeType != null || draggingSidebarNode != null) && isOverWorkspace(mouseX, mouseY)) {
                int worldMouseX = nodeGraph.screenToWorldX((int) mouseX);
                int worldMouseY = nodeGraph.screenToWorldY((int) mouseY);
                if (draggingSidebarNode != null) {
                    nodeGraph.previewSidebarDrag(draggingSidebarNode, worldMouseX, worldMouseY);
                } else {
                    nodeGraph.previewSidebarDrag(draggingNodeType, worldMouseX, worldMouseY);
                }
            } else {
                nodeGraph.resetDropTargets();
            }
            return true; // Continue dragging
        }

        // Handle node dragging and connection dragging
        if (button == 0) {
            nodeGraph.updateDrag((int) mouseX, (int) mouseY);
            updateSelectionDeletionPreviewState();
            return true;
        }

        // Handle panning with right-click or middle-click
        if ((button == 1 || button == 2) && nodeGraph.isPanning()) {
            nodeGraph.updatePanning((int) mouseX, (int) mouseY);
            return true;
        }

        return false;
    }

    // --- Release ------------------------------------------------------------

    /**
     * Handles the releases that outrank preset-tab dragging.
     *
     * @return {@code true} when the release was consumed
     */
    boolean handleSidebarAndSelectionBoxRelease(int button) {
        if (host.sidebar().mouseReleased(button)) {
            return true;
        }

        NodeGraph nodeGraph = host.nodeGraph();
        if (button == 0 && nodeGraph.isSelectionBoxActive()) {
            nodeGraph.completeSelectionBox();
            return true;
        }

        return false;
    }

    /**
     * Settles the workspace gesture that was in flight: drops a sidebar node,
     * deletes nodes dragged onto the sidebar, ends a connection cut, or turns a
     * short right-click into a context menu.
     *
     * @return {@code true} when the release was consumed outright; otherwise the
     *         gesture was settled but the screen should still fall through
     */
    boolean handleWorkspaceRelease(double mouseX, double mouseY, int button) {
        NodeGraph nodeGraph = host.nodeGraph();
        if (button == 0) {
            // Handle dropping node from sidebar
            if (draggingFromSidebar) {
                dropSidebarDrag(mouseX, mouseY);
            } else {
                deleteNodesDraggedOntoSidebar(mouseX);
                nodeGraph.stopDragging();
                nodeGraph.stopDraggingConnection();
            }
        } else if (button == 1) {
            if (cuttingConnections) {
                nodeGraph.stopConnectionCut();
                cuttingConnections = false;
                return true;
            }
            // Right-click released - check if it's a click or a drag
            if (rightClickStartX != -1) {
                openContextMenuIfClick(mouseX, mouseY);
                rightClickStartX = -1;
                rightClickStartY = -1;
            }

            // Stop panning
            nodeGraph.stopPanning();
        } else if (button == 2) {
            // Stop panning on middle-click release
            nodeGraph.stopPanning();
        }
        return false;
    }

    private void dropSidebarDrag(double mouseX, double mouseY) {
        if (sidebarDragActivated && host.saveDraggedRoutineToLibrary(
            mouseX, mouseY, draggingFromRoutineLibrary, draggingSidebarNode)) {
            // Saved to the reusable routine catalogue.
        } else if (sidebarDragActivated && host.importDraggedLibraryRoutineToList(
            mouseX, mouseY, draggingFromRoutineLibrary, draggingSidebarNode)) {
            // Imported into this preset's routine list.
        } else if (sidebarDragActivated && isOverWorkspace(mouseX, mouseY)) {
            Node newNode = host.dropDraggedSidebarNodeIntoWorkspace(
                (int) mouseX, (int) mouseY, draggingFromRoutineLibrary, draggingSidebarNode, draggingNodeType);
            if (newNode != null) {
                host.nodeGraph().selectNode(newNode);
            }
        } else if (!sidebarDragActivated && draggingSidebarNode != null
            && draggingSidebarNode.getType() == NodeType.ROUTINE_CALL
            && !draggingSidebarNode.getRoutineId().isBlank()) {
            // A click without movement opens the routine rather than placing it.
            if (draggingFromRoutineLibrary) {
                host.openLibraryRoutineWorkspaceTab(draggingSidebarNode.getRoutineId());
            } else {
                host.openRoutineWorkspaceTab(draggingSidebarNode.getRoutineId());
            }
        }
        clearSidebarDrag();
        host.nodeGraph().resetDropTargets();
    }

    private void deleteNodesDraggedOntoSidebar(double mouseX) {
        // Check if dragging node into sidebar for deletion (only if actually dragging)
        NodeGraph nodeGraph = host.nodeGraph();
        int sidebarWidth = host.sidebar().getWidth();
        Set<Node> selectedNodes = nodeGraph.getSelectedNodes();
        if (selectedNodes != null && !selectedNodes.isEmpty()) {
            List<Node> snapshot = new ArrayList<>(selectedNodes);
            boolean selectionDragged = false;
            Node draggedNode = null;
            boolean selectionOverSidebar = false;
            for (Node selected : snapshot) {
                if (selected == null) {
                    continue;
                }
                if (selected.isDragging()) {
                    selectionDragged = true;
                    if (draggedNode == null) {
                        draggedNode = selected;
                    }
                }
            }
            if (selectionDragged) {
                if (snapshot.size() > 1) {
                    selectionOverSidebar = nodeGraph.isSelectionOverSidebar(sidebarWidth);
                } else if (draggedNode != null) {
                    selectionOverSidebar = nodeGraph.isNodeOverSidebar(draggedNode, sidebarWidth);
                }
            }
            if (selectionDragged && selectionOverSidebar) {
                nodeGraph.deleteSelectedNode();
            }
        } else if (nodeGraph.getSelectedNode() != null && nodeGraph.getSelectedNode().isDragging()) {
            nodeGraph.deleteNodeIfInSidebar(nodeGraph.getSelectedNode(), (int) mouseX, sidebarWidth);
        }
    }

    private void openContextMenuIfClick(double mouseX, double mouseY) {
        int deltaX = Math.abs((int) mouseX - rightClickStartX);
        int deltaY = Math.abs((int) mouseY - rightClickStartY);
        long deltaTime = System.currentTimeMillis() - rightClickStartTime;

        boolean isClick = deltaX <= CLICK_THRESHOLD
            && deltaY <= CLICK_THRESHOLD
            && deltaTime <= CLICK_TIME_THRESHOLD;
        if (!isClick || !isOverWorkspace(mouseX, mouseY)) {
            return;
        }

        NodeGraph nodeGraph = host.nodeGraph();
        Node clickedNode = nodeGraph.getNodeAt(rightClickStartX, rightClickStartY);
        if (clickedNode != null) {
            nodeGraph.focusSelectedNode(clickedNode);
            nodeGraph.showNodeContextMenu(
                rightClickStartX, rightClickStartY, clickedNode, host.screenWidth(), host.screenHeight());
        } else {
            // Show context menu at the right-click position
            nodeGraph.showContextMenu(
                rightClickStartX, rightClickStartY, host.sidebar(), host.screenWidth(), host.screenHeight());
        }
    }

    // --- Recovery -----------------------------------------------------------

    /**
     * Settles drag state that outlived its mouse button. The screen can miss a
     * release when it loses focus mid-drag, which would otherwise leave a node
     * stuck to the cursor.
     */
    void recoverStaleLeftMouseDrag(int mouseX, int mouseY) {
        Minecraft client = host.client();
        if (InputCompatibilityBridge.isMouseButtonPressed(
            client != null ? client : Minecraft.getInstance(), GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            return;
        }

        NodeGraph nodeGraph = host.nodeGraph();
        boolean recoveringWorkspaceDrag = false;
        Set<Node> selectedNodes = nodeGraph.getSelectedNodes();
        if (selectedNodes != null) {
            for (Node selected : selectedNodes) {
                if (selected != null && selected.isDragging()) {
                    recoveringWorkspaceDrag = true;
                    break;
                }
            }
        }

        boolean staleState = draggingFromSidebar
            || nodeGraph.isSelectionBoxActive()
            || nodeGraph.isAnyNodeBeingDragged()
            || recoveringWorkspaceDrag;
        if (!staleState) {
            return;
        }

        if (nodeGraph.isSelectionBoxActive()) {
            nodeGraph.completeSelectionBox();
        }

        if (draggingFromSidebar) {
            if (sidebarDragActivated && host.saveDraggedRoutineToLibrary(
                mouseX, mouseY, draggingFromRoutineLibrary, draggingSidebarNode)) {
                // Saved to the reusable routine catalogue.
            } else if (sidebarDragActivated && host.importDraggedLibraryRoutineToList(
                mouseX, mouseY, draggingFromRoutineLibrary, draggingSidebarNode)) {
                // Imported into this preset's routine list.
            } else if (sidebarDragActivated && isOverWorkspace(mouseX, mouseY)) {
                Node newNode = host.dropDraggedSidebarNodeIntoWorkspace(
                    mouseX, mouseY, draggingFromRoutineLibrary, draggingSidebarNode, draggingNodeType);
                if (newNode != null) {
                    nodeGraph.selectNode(newNode);
                }
            }
            clearSidebarDrag();
            nodeGraph.resetDropTargets();
            return;
        }
        nodeGraph.forceClearTransientDragState();
    }

    // --- Shared helpers -----------------------------------------------------

    private void updateSelectionDeletionPreviewState() {
        NodeGraph nodeGraph = host.nodeGraph();
        int sidebarWidth = host.sidebar().getWidth();
        Set<Node> selectedNodes = nodeGraph.getSelectedNodes();
        boolean preview = false;
        if (selectedNodes != null && !selectedNodes.isEmpty()) {
            boolean hasDragging = false;
            for (Node node : selectedNodes) {
                if (node != null && node.isDragging()) {
                    hasDragging = true;
                    break;
                }
            }
            if (hasDragging) {
                if (selectedNodes.size() > 1) {
                    preview = nodeGraph.isSelectionOverSidebar(sidebarWidth);
                } else {
                    for (Node node : selectedNodes) {
                        if (node != null && node.isDragging() && nodeGraph.isNodeOverSidebar(node, sidebarWidth)) {
                            preview = true;
                            break;
                        }
                    }
                }
            }
        }
        nodeGraph.setSelectionDeletionPreviewActive(preview);
    }

    private boolean isOverWorkspace(double mouseX, double mouseY) {
        return mouseX >= host.sidebar().getWidth() && mouseY > TITLE_BAR_HEIGHT;
    }
}
