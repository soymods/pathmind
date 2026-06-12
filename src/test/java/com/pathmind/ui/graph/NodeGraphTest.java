package com.pathmind.ui.graph;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeGraphTest {

    @Test
    void sidebarDropPrefersDeepestHoveredParameterHost() {
        NodeGraph graph = new NodeGraph();

        Node parent = new Node(NodeType.OPERATOR_EQUALS, 100, 100);
        Node child = new Node(NodeType.OPERATOR_LESS, 0, 0);
        Node existing = new Node(NodeType.PARAM_AMOUNT, 0, 0);

        assertTrue(parent.attachParameter(child, 0));
        assertTrue(child.attachParameter(existing, 0));

        graph.addNode(parent);
        graph.addNode(child);
        graph.addNode(existing);

        Node dropped = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        int hoverX = child.getX() + 8;
        int hoverY = child.getY() + 8;

        graph.handleSidebarDrop(dropped, hoverX, hoverY);

        assertSame(child, dropped.getParentParameterHost());
        assertSame(existing, child.getAttachedParameter(0));
        assertSame(dropped, child.getAttachedParameter(1));
        assertSame(child, parent.getAttachedParameter(0));
        assertSame(child, existing.getParentParameterHost());
    }

    @Test
    void initializeClearsStaleSelectionButKeepsClipboardPasteUsable() {
        NodeGraph graph = new NodeGraph();

        Node copied = new Node(NodeType.PARAM_AMOUNT, 100, 100);
        graph.addNode(copied);
        graph.selectNode(copied);

        assertTrue(graph.copySelectedNodeToClipboard());

        graph.initializeWithScreenDimensions(800, 600, 180, 40);

        assertFalse(copied.isSelected());
        assertTrue(graph.getSelectedNodes().isEmpty());

        Node pasted = graph.pasteClipboardNode();

        assertNotNull(pasted);
        assertNotSame(copied, pasted);
        assertEquals(NodeType.PARAM_AMOUNT, pasted.getType());
        assertEquals(1, graph.getSelectedNodes().size());
        assertSame(pasted, graph.getSelectedNode());
    }

    @Test
    void cutSelectedNodeCopiesThenDeletesAndCanPaste() {
        NodeGraph graph = new NodeGraph();

        Node cut = new Node(NodeType.PARAM_AMOUNT, 100, 100);
        graph.addNode(cut);
        graph.selectNode(cut);

        assertTrue(graph.cutSelectedNodeToClipboard());
        assertFalse(graph.getNodes().contains(cut));
        assertTrue(graph.getSelectedNodes().isEmpty());

        Node pasted = graph.pasteClipboardNode();

        assertNotNull(pasted);
        assertNotSame(cut, pasted);
        assertEquals(NodeType.PARAM_AMOUNT, pasted.getType());
        assertEquals(1, graph.getNodes().size());
        assertSame(pasted, graph.getSelectedNode());
    }
}
