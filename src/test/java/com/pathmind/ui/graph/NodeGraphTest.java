package com.pathmind.ui.graph;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import org.junit.jupiter.api.Test;

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
}
