package com.pathmind.data;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeGraphPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadRoundTripPreservesAttachmentsAndTemplateMetadata() {
        Node start = new Node(NodeType.START, 10, 20);
        start.setStartNodeNumber(7);

        Node wait = new Node(NodeType.WAIT, 40, 30);
        wait.getParameter("Duration").setStringValue("2.5");

        Node controlWaitUntil = new Node(NodeType.CONTROL_WAIT_UNTIL, 80, 40);
        Node sensor = new Node(NodeType.SENSOR_IS_DAYTIME, 0, 0);
        assertTrue(controlWaitUntil.attachSensor(sensor));

        Node repeat = new Node(NodeType.CONTROL_REPEAT, 120, 50);
        Node message = new Node(NodeType.MESSAGE, 0, 0);
        message.setMessageLines(List.of("Hello", "Release"));
        message.setMessageClientSide(true);
        assertTrue(repeat.attachActionNode(message));

        Node waitWithParameter = new Node(NodeType.WAIT, 160, 60);
        Node durationParameter = new Node(NodeType.PARAM_DURATION, 0, 0);
        durationParameter.getParameter("Duration").setStringValue("5");
        assertTrue(waitWithParameter.attachParameter(durationParameter, 0));

        Node template = new Node(NodeType.TEMPLATE, 200, 70);
        template.setTemplateName("Reusable");
        template.setTemplateVersion(3);
        template.setCustomNodeInstance(true);
        template.setTemplateGraphData(new NodeGraphData(
            List.of(new NodeGraphData.NodeData("inner-start", NodeType.START, null, 0, 0, List.of())),
            List.of()
        ));

        List<Node> nodes = List.of(
            start, wait, controlWaitUntil, sensor, repeat, message, waitWithParameter, durationParameter, template
        );
        List<NodeConnection> connections = List.of(
            new NodeConnection(start, wait, 0, 0),
            new NodeConnection(wait, controlWaitUntil, 0, 0),
            new NodeConnection(controlWaitUntil, repeat, 0, 0),
            new NodeConnection(repeat, waitWithParameter, 0, 0),
            new NodeConnection(waitWithParameter, template, 0, 0)
        );

        Path savePath = tempDir.resolve("graph.json");
        assertTrue(NodeGraphPersistence.saveNodeGraphToPath(nodes, connections, savePath));

        NodeGraphData loaded = NodeGraphPersistence.loadNodeGraphFromPath(savePath);
        assertNotNull(loaded);

        List<Node> restoredNodes = NodeGraphPersistence.convertToNodes(loaded);
        Map<String, Node> byId = restoredNodes.stream().collect(Collectors.toMap(Node::getId, Function.identity()));
        List<NodeConnection> restoredConnections = NodeGraphPersistence.convertToConnections(loaded, byId);

        Node restoredStart = byId.get(start.getId());
        Node restoredControlWaitUntil = byId.get(controlWaitUntil.getId());
        Node restoredRepeat = byId.get(repeat.getId());
        Node restoredWaitWithParameter = byId.get(waitWithParameter.getId());
        Node restoredTemplate = byId.get(template.getId());

        assertEquals(7, restoredStart.getStartNodeNumber());
        assertEquals(NodeType.SENSOR_IS_DAYTIME, restoredControlWaitUntil.getAttachedSensor().getType());
        assertEquals(restoredControlWaitUntil, restoredControlWaitUntil.getAttachedSensor().getParentControl());
        assertEquals(NodeType.MESSAGE, restoredRepeat.getAttachedActionNode().getType());
        assertEquals(restoredRepeat, restoredRepeat.getAttachedActionNode().getParentActionControl());
        assertEquals(List.of("Hello", "Release"), restoredRepeat.getAttachedActionNode().getMessageLines());
        assertTrue(restoredRepeat.getAttachedActionNode().isMessageClientSide());
        assertEquals(NodeType.PARAM_DURATION, restoredWaitWithParameter.getAttachedParameter(0).getType());
        assertEquals("5", restoredWaitWithParameter.getAttachedParameter(0).getParameter("Duration").getStringValue());
        assertEquals("Reusable", restoredTemplate.getTemplateName());
        assertEquals(3, restoredTemplate.getTemplateVersion());
        assertTrue(restoredTemplate.isCustomNodeInstance());
        assertNotNull(restoredTemplate.getTemplateGraphData());
        assertEquals(1, restoredTemplate.getTemplateGraphData().getNodes().size());
        assertEquals(connections.size(), restoredConnections.size());
    }
}
