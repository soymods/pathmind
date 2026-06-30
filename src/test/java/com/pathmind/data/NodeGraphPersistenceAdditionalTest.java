package com.pathmind.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeMode;
import com.pathmind.nodes.NodeType;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NodeGraphPersistenceAdditionalTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsNodeGraphDataAtExplicitPath() {
        NodeGraphData data = new NodeGraphData();
        data.getNodes().add(nodeData("start-id", NodeType.START, 10, 20));
        data.getNodes().add(nodeData("wait-id", NodeType.WAIT, 140, 20));
        data.getConnections().add(connectionData("start-id", "wait-id", 0, 0));

        Path target = tempDir.resolve("graph.json");
        assertTrue(NodeGraphPersistence.saveNodeGraphDataToPath(data, target));

        NodeGraphData loaded = NodeGraphPersistence.loadNodeGraphFromPath(target);
        assertNotNull(loaded);
        assertEquals(2, loaded.getNodes().size());
        assertEquals(1, loaded.getConnections().size());
        assertEquals(NodeType.START, loaded.getNodes().get(0).getType());
        assertEquals("wait-id", loaded.getConnections().get(0).getInputNodeId());
    }

    @Test
    void prettyJsonRoundTripsCustomNodeDefinition() {
        NodeGraphData data = new NodeGraphData();
        NodeGraphData.CustomNodeDefinition definition = new NodeGraphData.CustomNodeDefinition();
        definition.setPresetName("Utility");
        definition.setName("Utility");
        definition.setVersion(3);
        definition.setSignature("abc123");
        definition.setInputs(List.of(new NodeGraphData.CustomNodePort("target", NodeType.PARAM_MESSAGE.name(), "home")));
        data.setCustomNodeDefinition(definition);

        NodeGraphData parsed = NodeGraphPersistence.parseNodeGraphData(NodeGraphPersistence.toPrettyJson(data));

        assertNotNull(parsed);
        assertNotNull(parsed.getCustomNodeDefinition());
        assertEquals("Utility", parsed.getCustomNodeDefinition().getName());
        assertEquals(3, parsed.getCustomNodeDefinition().getVersion());
        assertEquals(1, parsed.getCustomNodeDefinition().getInputs().size());
    }

    @Test
    void convertToConnectionsSkipsSensorConnectionsAndReplacesInputConflicts() {
        Node startA = new Node(NodeType.START, 0, 0);
        Node startB = new Node(NodeType.START, 0, 80);
        Node wait = new Node(NodeType.WAIT, 120, 0);
        Node sensor = new Node(NodeType.SENSOR_IS_DAYTIME, 120, 80);

        NodeGraphData data = new NodeGraphData();
        data.getConnections().add(connectionData(startA.getId(), wait.getId(), 0, 0));
        data.getConnections().add(connectionData(startB.getId(), wait.getId(), 0, 0));
        data.getConnections().add(connectionData(startA.getId(), sensor.getId(), 0, 0));

        Map<String, Node> nodesById = new HashMap<>();
        for (Node node : List.of(startA, startB, wait, sensor)) {
            nodesById.put(node.getId(), node);
        }

        List<NodeConnection> connections = NodeGraphPersistence.convertToConnections(data, nodesById);

        assertEquals(1, connections.size());
        assertEquals(startB, connections.get(0).getOutputNode());
        assertEquals(wait, connections.get(0).getInputNode());
    }

    private static NodeGraphData.NodeData nodeData(String id, NodeType type, int x, int y) {
        return new NodeGraphData.NodeData(id, type, NodeMode.getDefaultModeForNodeType(type), x, y, List.of());
    }

    private static NodeGraphData.ConnectionData connectionData(String outputNodeId, String inputNodeId, int outputSocket, int inputSocket) {
        NodeGraphData.ConnectionData data = new NodeGraphData.ConnectionData();
        data.setOutputNodeId(outputNodeId);
        data.setInputNodeId(inputNodeId);
        data.setOutputSocket(outputSocket);
        data.setInputSocket(inputSocket);
        return data;
    }
}
