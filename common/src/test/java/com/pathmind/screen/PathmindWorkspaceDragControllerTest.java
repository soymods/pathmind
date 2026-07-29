package com.pathmind.screen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.graph.NodeGraph;
import com.pathmind.ui.sidebar.Sidebar;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Characterization tests for the workspace pointer-gesture lifecycle.
 *
 * <p>These pin down behaviour that only a human clicking around used to verify:
 * the click-versus-drag threshold that decides whether a right-click opens a
 * context menu or pans the camera, panning, and the recovery path for a drag
 * whose mouse release was never delivered.
 *
 * <p>Runs headless. {@code InputCompatibilityBridge} degrades to "no modifier
 * held" and "no button pressed" when there is no Minecraft client, which is what
 * makes these paths reachable without a game.
 *
 * <p>Known gaps, verified by mutation testing rather than assumed:
 * <ul>
 *   <li>The sidebar drag lifecycle is <em>not</em> covered. Arming it requires
 *       {@link Sidebar#isHoveringNode()}, which is only set during render-time
 *       layout and has no test seam, so removing {@code clearSidebarDrag()} from
 *       the release path is not caught here.
 *   <li>Stale-drag recovery is asserted by outcome (marquee cleared, enclosed node
 *       selected). Swapping {@code completeSelectionBox} for a plain discard is an
 *       equivalent mutant, because the marquee already selects as it updates.
 *   <li>Control-modified gestures (control-right-drag to cut connections,
 *       control- and shift-click selection) cannot be reached: {@code
 *       hasControlDown()} is always false without a client.
 * </ul>
 */
class PathmindWorkspaceDragControllerTest {
    private static final int TITLE_BAR_HEIGHT = 20;
    /** Matches the controller's own CLICK_THRESHOLD. */
    private static final int CLICK_THRESHOLD = 5;

    private NodeGraph nodeGraph;
    private Sidebar sidebar;
    private RecordingHost host;
    private PathmindWorkspaceDragController controller;

    /** A point comfortably inside the workspace, right of the sidebar and below the title bar. */
    private int workspaceX;
    private int workspaceY;

    @BeforeEach
    void setUp() {
        nodeGraph = new NodeGraph();
        sidebar = new Sidebar(false, false);
        host = new RecordingHost(nodeGraph, sidebar);
        controller = new PathmindWorkspaceDragController(host);
        workspaceX = sidebar.getWidth() + 120;
        workspaceY = TITLE_BAR_HEIGHT + 120;
    }

    // --- Right-click: click versus drag -------------------------------------

    @Test
    void quickRightClickInWorkspaceOpensContextMenu() {
        assertTrue(controller.beginWorkspacePointerGesture(workspaceX, workspaceY, 1),
            "right press inside the workspace should be consumed");
        assertFalse(nodeGraph.isContextMenuOpen(), "menu must not open until release");

        controller.handleWorkspaceRelease(workspaceX, workspaceY, 1);

        assertTrue(nodeGraph.isContextMenuOpen(),
            "a right-click that did not move should open the context menu");
    }

    @Test
    void rightClickDraggedBeyondThresholdPansInsteadOfOpeningMenu() {
        controller.beginWorkspacePointerGesture(workspaceX, workspaceY, 1);

        controller.handleWorkspaceDrag(workspaceX + CLICK_THRESHOLD + 1, workspaceY, 1);
        assertTrue(nodeGraph.isPanning(), "exceeding the threshold should start panning");

        controller.handleWorkspaceRelease(workspaceX + CLICK_THRESHOLD + 1, workspaceY, 1);

        assertFalse(nodeGraph.isContextMenuOpen(),
            "a right-drag is a pan, not a context-menu click");
        assertFalse(nodeGraph.isPanning(), "release should stop panning");
    }

    @Test
    void rightClickMovedWithinThresholdStillOpensContextMenu() {
        controller.beginWorkspacePointerGesture(workspaceX, workspaceY, 1);

        controller.handleWorkspaceDrag(workspaceX + CLICK_THRESHOLD, workspaceY, 1);
        assertFalse(nodeGraph.isPanning(), "movement up to the threshold is still a click");

        controller.handleWorkspaceRelease(workspaceX + CLICK_THRESHOLD, workspaceY, 1);

        assertTrue(nodeGraph.isContextMenuOpen(),
            "jitter within the threshold should not suppress the context menu");
    }

    @Test
    void rightClickReleasedOverSidebarDoesNotOpenContextMenu() {
        // Press just inside the workspace and release just inside the sidebar. The
        // travel stays under CLICK_THRESHOLD so this is still a click; only the
        // over-workspace check can reject it.
        int justInsideWorkspace = sidebar.getWidth() + 2;
        int justInsideSidebar = sidebar.getWidth() - 2;
        controller.beginWorkspacePointerGesture(justInsideWorkspace, workspaceY, 1);

        controller.handleWorkspaceRelease(justInsideSidebar, workspaceY, 1);

        assertFalse(nodeGraph.isContextMenuOpen(),
            "the workspace context menu should not open over the sidebar");
    }

    // --- Panning ------------------------------------------------------------

    @Test
    void middleClickStartsAndStopsPanning() {
        assertTrue(controller.beginWorkspacePointerGesture(workspaceX, workspaceY, 2));
        assertTrue(nodeGraph.isPanning());

        controller.handleWorkspaceRelease(workspaceX, workspaceY, 2);

        assertFalse(nodeGraph.isPanning());
    }

    @Test
    void leftPressIsNotAWorkspacePointerGesture() {
        assertFalse(controller.beginWorkspacePointerGesture(workspaceX, workspaceY, 0),
            "left clicks are routed by node interaction, not the gesture handler");
    }

    // --- Selection box ------------------------------------------------------

    @Test
    void selectionBoxDragIsConsumedAndCompletedOnRelease() {
        nodeGraph.beginSelectionBox(workspaceX, workspaceY);
        assertTrue(nodeGraph.isSelectionBoxActive());

        assertTrue(controller.handleSidebarAndSelectionBoxDrag(workspaceX + 40, workspaceY + 40, 0),
            "an active selection box should consume the drag");
        assertTrue(controller.handleSidebarAndSelectionBoxRelease(0),
            "release should be consumed while the selection box is active");
        assertFalse(nodeGraph.isSelectionBoxActive(), "release should complete the selection box");
    }

    @Test
    void selectionBoxIgnoresNonLeftButtons() {
        nodeGraph.beginSelectionBox(workspaceX, workspaceY);

        assertFalse(controller.handleSidebarAndSelectionBoxDrag(workspaceX + 40, workspaceY + 40, 1),
            "only the left button drives the marquee");
        assertTrue(nodeGraph.isSelectionBoxActive());
    }

    // --- Stale drag recovery ------------------------------------------------

    @Test
    void recoveryCompletesASelectionBoxLeftOpenByAMissedRelease() {
        Node enclosed = new Node(NodeType.WAIT, nodeGraph.screenToWorldX(workspaceX + 10),
            nodeGraph.screenToWorldY(workspaceY + 10));
        nodeGraph.getNodes().add(enclosed);

        nodeGraph.beginSelectionBox(workspaceX - 40, workspaceY - 40);
        nodeGraph.updateSelectionBox(workspaceX + 200, workspaceY + 200);

        controller.recoverStaleLeftMouseDrag(workspaceX + 200, workspaceY + 200);

        assertFalse(nodeGraph.isSelectionBoxActive(),
            "losing the release must not leave the marquee stuck to the cursor");
        assertTrue(nodeGraph.isNodeSelected(enclosed),
            "recovery should commit the marquee selection, not silently discard it");
    }

    @Test
    void recoveryIsANoOpWhenNothingIsInFlight() {
        controller.recoverStaleLeftMouseDrag(workspaceX, workspaceY);

        assertFalse(nodeGraph.isSelectionBoxActive());
        assertFalse(nodeGraph.isPanning());
        assertFalse(controller.isSidebarDragActive());
        assertTrue(host.droppedNodes.isEmpty(), "recovery must not invent a drop");
    }

    // --- Sidebar drag state -------------------------------------------------

    @Test
    void clearSidebarDragResetsEveryPieceOfDragState() {
        controller.clearSidebarDrag();

        assertFalse(controller.isSidebarDragActive());
        assertNull(controller.draggingNodeType());
        assertNull(controller.draggingSidebarNode());
    }

    @Test
    void noSidebarDragStartsWhenTheSidebarIsNotHoveringANode() {
        controller.beginSidebarDragIfHovering(1, workspaceY);

        assertFalse(controller.isSidebarDragActive());
        assertNull(controller.draggingSidebarNode());
    }

    @Test
    void sidebarRoutineDragStateIsClearedWhenNoDragIsActive() {
        controller.updateSidebarRoutineDragState();
        // The sidebar ignores the "from library" flag when the drag is inactive,
        // so the only observable requirement is that this stays side-effect free.
        assertFalse(controller.isSidebarDragActive());
    }

    // --- Node drag ----------------------------------------------------------

    @Test
    void leftDragWithoutASidebarDragMovesTheGraphSelection() {
        Node node = new Node(NodeType.WAIT, 40, 40);
        nodeGraph.getNodes().add(node);
        nodeGraph.startDragging(node, workspaceX, workspaceY);

        assertTrue(controller.handleWorkspaceDrag(workspaceX + 25, workspaceY + 25, 0),
            "a left drag in the workspace should be consumed as a node drag");

        controller.handleWorkspaceRelease(workspaceX + 25, workspaceY + 25, 0);
        assertFalse(node.isDragging(), "release should stop dragging the node");
    }

    /** Records host callbacks so tests can assert nothing was dropped or opened by accident. */
    private static final class RecordingHost implements PathmindWorkspaceDragController.Host {
        private final NodeGraph nodeGraph;
        private final Sidebar sidebar;
        final List<String> droppedNodes = new ArrayList<>();
        final List<String> openedRoutines = new ArrayList<>();

        RecordingHost(NodeGraph nodeGraph, Sidebar sidebar) {
            this.nodeGraph = nodeGraph;
            this.sidebar = sidebar;
        }

        @Override
        public int screenWidth() {
            return 1280;
        }

        @Override
        public int screenHeight() {
            return 720;
        }

        @Override
        public Minecraft client() {
            return null;
        }

        @Override
        public NodeGraph nodeGraph() {
            return nodeGraph;
        }

        @Override
        public Sidebar sidebar() {
            return sidebar;
        }

        @Override
        public boolean isNodeDragBlocked(NodeType nodeType) {
            return false;
        }

        @Override
        public boolean saveDraggedRoutineToLibrary(double mouseX, double mouseY, boolean fromLibrary, Node draggedNode) {
            return false;
        }

        @Override
        public boolean importDraggedLibraryRoutineToList(double mouseX, double mouseY, boolean fromLibrary, Node draggedNode) {
            return false;
        }

        @Override
        public Node dropDraggedSidebarNodeIntoWorkspace(int mouseX, int mouseY, boolean fromLibrary,
                                                        Node draggedNode, NodeType draggedType) {
            droppedNodes.add(String.valueOf(draggedType));
            return null;
        }

        @Override
        public void openRoutineWorkspaceTab(String routineId) {
            openedRoutines.add(routineId);
        }

        @Override
        public void openLibraryRoutineWorkspaceTab(String routineId) {
            openedRoutines.add("library:" + routineId);
        }
    }
}
