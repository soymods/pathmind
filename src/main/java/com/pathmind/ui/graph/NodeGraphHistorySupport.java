package com.pathmind.ui.graph;

import com.pathmind.data.NodeGraphData;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class NodeGraphHistorySupport {
    private NodeGraphHistorySupport() {
    }

    static void pushUndoState(
        NodeGraph graph,
        List<Node> nodes,
        List<NodeConnection> connections,
        Deque<NodeGraphData> undoStack,
        Deque<NodeGraphData> redoStack,
        boolean suppressUndoCapture,
        int maxHistory
    ) {
        if (suppressUndoCapture) {
            return;
        }
        NodeGraphData snapshot = graph.buildGraphData(new ArrayList<>(nodes), new ArrayList<>(connections), null);
        pushUndoSnapshot(snapshot, undoStack, redoStack, suppressUndoCapture, maxHistory);
    }

    static void pushUndoSnapshot(
        NodeGraphData snapshot,
        Deque<NodeGraphData> undoStack,
        Deque<NodeGraphData> redoStack,
        boolean suppressUndoCapture,
        int maxHistory
    ) {
        if (snapshot == null || suppressUndoCapture) {
            return;
        }
        undoStack.push(snapshot);
        while (undoStack.size() > maxHistory) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    static boolean undo(
        NodeGraph graph,
        List<Node> nodes,
        List<NodeConnection> connections,
        Deque<NodeGraphData> undoStack,
        Deque<NodeGraphData> redoStack
    ) {
        if (undoStack.isEmpty()) {
            return false;
        }
        NodeGraphData currentState = graph.buildGraphData(new ArrayList<>(nodes), new ArrayList<>(connections), null);
        NodeGraphData previousState = undoStack.pop();
        if (currentState != null) {
            redoStack.push(currentState);
        }
        graph.restoreFromSnapshot(previousState);
        return true;
    }

    static boolean redo(
        NodeGraph graph,
        List<Node> nodes,
        List<NodeConnection> connections,
        Deque<NodeGraphData> undoStack,
        Deque<NodeGraphData> redoStack
    ) {
        if (redoStack.isEmpty()) {
            return false;
        }
        NodeGraphData currentState = graph.buildGraphData(new ArrayList<>(nodes), new ArrayList<>(connections), null);
        NodeGraphData nextState = redoStack.pop();
        if (currentState != null) {
            undoStack.push(currentState);
        }
        graph.restoreFromSnapshot(nextState);
        return true;
    }
}
