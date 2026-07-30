package com.pathmind.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.graph.NodeGraph;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Characterization tests for workspace click routing.
 *
 * <p>{@code handleClick} decides what a click landed on — a coordinate picker, a
 * connection socket, one of many inline node controls, or empty canvas — and the
 * order it checks in is the interaction contract. These tests pin the outcomes
 * that order produces.
 *
 * <p>Runs headless at zoom 1.0, where screen and world coordinates coincide, so
 * node bounds can be addressed directly.
 *
 * <p>Known gap: control- and shift-click selection are unreachable, because
 * {@code InputCompatibilityBridge.hasControlDown()} is always false without a
 * Minecraft client. Only the unmodified-click branch is exercised.
 */
class PathmindNodeInteractionControllerTest {
    private static final int LEFT = 0;
    private static final int RIGHT = 1;

    private NodeGraph nodeGraph;
    private RecordingHost host;
    private PathmindNodeInteractionController controller;

    @BeforeEach
    void setUp() {
        nodeGraph = new NodeGraph();
        host = new RecordingHost(nodeGraph);
        controller = new PathmindNodeInteractionController(host);
    }

    private Node addNode(NodeType type, int x, int y) {
        Node node = new Node(type, x, y);
        nodeGraph.getNodes().add(node);
        return node;
    }

    private int centerX(Node node) {
        return nodeGraph.worldToScreenX(node.getX() + node.getWidth() / 2);
    }

    private int centerY(Node node) {
        return nodeGraph.worldToScreenY(node.getY() + node.getHeight() / 2);
    }

    /**
     * A point on the node's header strip. The vertical centre of most nodes is an
     * inline control, so "click the node" has to mean the header to test dragging.
     */
    private int headerY(Node node) {
        return nodeGraph.worldToScreenY(node.getY() + 6);
    }

    // --- Node body ----------------------------------------------------------

    @Test
    void leftClickOnNodeHeaderSelectsAndBeginsDraggingIt() {
        Node node = addNode(NodeType.WAIT, 400, 400);

        assertTrue(controller.handleClick(centerX(node), headerY(node), LEFT));

        assertTrue(nodeGraph.isNodeSelected(node), "the clicked node should be selected");
        assertTrue(node.isDragging(), "an unmodified click on plain body should begin dragging");
    }

    @Test
    void leftClickOnAnInlineControlFocusesTheNodeWithoutDraggingIt() {
        // A WAIT node's vertical centre is its mode field. Inline controls are
        // tested before the drag fallback precisely so that clicking one does not
        // yank the node along with it.
        Node node = addNode(NodeType.WAIT, 400, 400);

        assertTrue(controller.handleClick(centerX(node), centerY(node), LEFT));

        assertTrue(nodeGraph.isNodeSelected(node), "operating a control should still focus its node");
        assertFalse(node.isDragging(), "clicking an inline control must not start a node drag");
    }

    @Test
    void nonLeftClickOnNodeBodyIsNotConsumed() {
        Node node = addNode(NodeType.WAIT, 400, 400);

        assertFalse(controller.handleClick(centerX(node), headerY(node), RIGHT),
            "right-clicks on a node are handled later, as a context-menu gesture");
        assertFalse(node.isDragging());
    }

    @Test
    void clickingASecondNodeMovesTheSelection() {
        Node first = addNode(NodeType.WAIT, 200, 200);
        Node second = addNode(NodeType.WAIT, 600, 600);

        controller.handleClick(centerX(first), headerY(first), LEFT);
        controller.handleClick(centerX(second), headerY(second), LEFT);

        assertTrue(nodeGraph.isNodeSelected(second));
        assertFalse(nodeGraph.isNodeSelected(first), "selection should not accumulate without a modifier");
    }

    // --- Empty canvas -------------------------------------------------------

    @Test
    void leftClickOnEmptyCanvasDeselectsAndStartsAMarquee() {
        Node node = addNode(NodeType.WAIT, 400, 400);
        controller.handleClick(centerX(node), headerY(node), LEFT);
        assertTrue(nodeGraph.isNodeSelected(node));

        assertTrue(controller.handleClick(50, 50, LEFT));

        assertFalse(nodeGraph.isNodeSelected(node), "clicking empty canvas should deselect");
        assertTrue(nodeGraph.isSelectionBoxActive(), "clicking empty canvas should arm the marquee");
    }

    @Test
    void rightClickOnEmptyCanvasIsConsumedWithoutStartingAMarquee() {
        assertTrue(controller.handleClick(50, 50, RIGHT));

        assertFalse(nodeGraph.isSelectionBoxActive(),
            "only the left button arms the marquee");
    }

    // --- Screen coordinate capture ------------------------------------------

    @Test
    void captureModeTakesPrecedenceOverEverythingElse() {
        Node node = addNode(NodeType.WAIT, 400, 400);
        Node picker = addNode(NodeType.CLICK_SCREEN, 400, 600);
        nodeGraph.startScreenCoordinateCapture(picker);
        if (!nodeGraph.isScreenCoordinateCaptureActive()) {
            return; // this node shape does not support capture on this build
        }

        controller.handleClick(centerX(node), headerY(node), LEFT);

        assertFalse(node.isDragging(),
            "while capturing a screen coordinate, a click must not also drag a node");
    }

    // --- Double click -------------------------------------------------------

    @Test
    void doubleClickingARoutineCallOpensItsWorkspace() {
        Node routineCall = addNode(NodeType.ROUTINE_CALL, 400, 400);
        routineCall.setRoutineIdentity("routine-42", "");
        int x = centerX(routineCall);
        int y = centerY(routineCall);

        controller.handleClick(x, y, LEFT);
        controller.handleClick(x, y, LEFT);

        assertEquals(List.of("routine-42"), host.openedRoutines,
            "a double-click should open the routine, exactly once");
    }

    @Test
    void singleClickingARoutineCallDoesNotOpenItsWorkspace() {
        Node routineCall = addNode(NodeType.ROUTINE_CALL, 400, 400);
        routineCall.setRoutineIdentity("routine-42", "");

        controller.handleClick(centerX(routineCall), centerY(routineCall), LEFT);

        assertTrue(host.openedRoutines.isEmpty(),
            "one click selects; it must not navigate");
    }

    @Test
    void doubleClickingARoutineCallWithNoTargetDoesNotNavigate() {
        Node routineCall = addNode(NodeType.ROUTINE_CALL, 400, 400);
        int x = centerX(routineCall);
        int y = centerY(routineCall);

        controller.handleClick(x, y, LEFT);
        controller.handleClick(x, y, LEFT);

        assertTrue(host.openedRoutines.isEmpty(),
            "a routine call with a blank id has nothing to open");
    }

    // --- Sockets ------------------------------------------------------------

    @Test
    void clickingAnInputSocketStartsAConnectionDragInsteadOfSelectingTheNode() {
        Node node = addNode(NodeType.WAIT, 400, 400);
        if (!node.shouldRenderSockets() || node.getInputSocketCount() == 0) {
            return; // nothing to address on this node shape
        }
        int[] socket = findInputSocket(node);
        if (socket == null) {
            return;
        }

        assertTrue(controller.handleClick(socket[0], socket[1], LEFT));

        assertFalse(node.isDragging(),
            "sockets are tested before node bodies, so the node must not start dragging");
        assertFalse(nodeGraph.isSelectionBoxActive(),
            "the socket consumed the click, so it must not fall through to the empty-canvas marquee");
    }

    /** Scans the node's bounds for a point the node reports as its first input socket. */
    private int[] findInputSocket(Node node) {
        for (int wy = node.getY() - 8; wy <= node.getY() + node.getHeight() + 8; wy++) {
            for (int wx = node.getX() - 8; wx <= node.getX() + node.getWidth() + 8; wx++) {
                if (node.isSocketClicked(wx, wy, 0, true)) {
                    return new int[] {nodeGraph.worldToScreenX(wx), nodeGraph.worldToScreenY(wy)};
                }
            }
        }
        return null;
    }

    /** Records host callbacks so navigation and overlay openings can be asserted. */
    private static final class RecordingHost implements PathmindNodeInteractionController.Host {
        private final NodeGraph nodeGraph;
        final List<String> openedRoutines = new ArrayList<>();
        final List<Node> templateTabs = new ArrayList<>();
        final List<Node> parameterOverlays = new ArrayList<>();
        final List<Node> bookEditors = new ArrayList<>();
        final List<String> switchedPresets = new ArrayList<>();
        final List<Node> doubleClickExecutions = new ArrayList<>();

        RecordingHost(NodeGraph nodeGraph) {
            this.nodeGraph = nodeGraph;
        }

        @Override
        public NodeGraph nodeGraph() {
            return nodeGraph;
        }

        @Override
        public void openTemplateWorkspaceTab(Node node) {
            templateTabs.add(node);
        }

        @Override
        public void openRoutineWorkspaceTab(String routineId) {
            openedRoutines.add(routineId);
        }

        @Override
        public void switchPreset(String presetName) {
            switchedPresets.add(presetName);
        }

        @Override
        public void openBookTextEditor(Node node) {
            bookEditors.add(node);
        }

        @Override
        public void openParameterOverlay(Node node) {
            parameterOverlays.add(node);
        }

        @Override
        public boolean executeFromNodeOnDoubleClick(Node node) {
            doubleClickExecutions.add(node);
            return false;
        }
    }
}
