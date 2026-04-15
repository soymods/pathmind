package com.pathmind.data;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void saveAndLoadRoundTripPreservesCoordinateOrOperatorAttachments() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 10, 20);
        Node leftCoordinate = coordinateNode(10, 64, 10);
        Node or = new Node(NodeType.OPERATOR_BOOLEAN_OR, 60, 20);
        Node firstCoordinate = coordinateNode(0, 64, 0);
        Node secondCoordinate = coordinateNode(10, 64, 10);

        assertTrue(or.attachParameter(firstCoordinate, 0));
        assertTrue(or.attachParameter(secondCoordinate, 1));
        assertTrue(equals.attachParameter(leftCoordinate, 0));
        assertTrue(equals.attachParameter(or, 1));

        List<Node> nodes = List.of(equals, leftCoordinate, or, firstCoordinate, secondCoordinate);
        Path savePath = tempDir.resolve("coordinate-or-graph.json");
        assertTrue(NodeGraphPersistence.saveNodeGraphToPath(nodes, List.of(), savePath));

        NodeGraphData loaded = NodeGraphPersistence.loadNodeGraphFromPath(savePath);
        assertNotNull(loaded);

        List<Node> restoredNodes = NodeGraphPersistence.convertToNodes(loaded);
        Map<String, Node> byId = restoredNodes.stream().collect(Collectors.toMap(Node::getId, Function.identity()));

        Node restoredEquals = byId.get(equals.getId());
        Node restoredOr = byId.get(or.getId());
        assertNotNull(restoredEquals);
        assertNotNull(restoredOr);
        assertEquals(NodeType.PARAM_COORDINATE, restoredEquals.getAttachedParameter(0).getType());
        assertEquals(NodeType.OPERATOR_BOOLEAN_OR, restoredEquals.getAttachedParameter(1).getType());
        assertEquals(NodeType.PARAM_COORDINATE, restoredOr.getAttachedParameter(0).getType());
        assertEquals(NodeType.PARAM_COORDINATE, restoredOr.getAttachedParameter(1).getType());
    }

    @Test
    void convertToNodesRecoversLegacyNestedAttachmentsFromSavedLayout() {
        Node controlWaitUntil = new Node(NodeType.CONTROL_WAIT_UNTIL, 40, 30);
        Node sensor = new Node(NodeType.SENSOR_IS_DAYTIME, 0, 0);
        assertTrue(controlWaitUntil.attachSensor(sensor));

        Node repeat = new Node(NodeType.CONTROL_REPEAT, 120, 50);
        Node message = new Node(NodeType.MESSAGE, 0, 0);
        assertTrue(repeat.attachActionNode(message));

        Node wait = new Node(NodeType.WAIT, 200, 60);
        Node duration = new Node(NodeType.PARAM_DURATION, 0, 0);
        duration.getParameter("Duration").setStringValue("3");
        assertTrue(wait.attachParameter(duration, 0));

        Path savePath = tempDir.resolve("legacy-layout-graph.json");
        assertTrue(NodeGraphPersistence.saveNodeGraphToPath(
            List.of(controlWaitUntil, sensor, repeat, message, wait, duration),
            List.of(),
            savePath
        ));

        NodeGraphData loaded = NodeGraphPersistence.loadNodeGraphFromPath(savePath);
        assertNotNull(loaded);

        for (NodeGraphData.NodeData nodeData : loaded.getNodes()) {
            nodeData.setAttachedSensorId(null);
            nodeData.setParentControlId(null);
            nodeData.setAttachedActionId(null);
            nodeData.setParentActionControlId(null);
            nodeData.setAttachedParameterId(null);
            nodeData.setParentParameterHostId(null);
            nodeData.setParameterAttachments(List.of());
        }

        List<Node> restoredNodes = NodeGraphPersistence.convertToNodes(loaded);
        Map<String, Node> byId = restoredNodes.stream().collect(Collectors.toMap(Node::getId, Function.identity()));

        Node restoredControl = byId.get(controlWaitUntil.getId());
        Node restoredSensor = byId.get(sensor.getId());
        Node restoredRepeat = byId.get(repeat.getId());
        Node restoredMessage = byId.get(message.getId());
        Node restoredWait = byId.get(wait.getId());
        Node restoredDuration = byId.get(duration.getId());

        assertNotNull(restoredControl);
        assertNotNull(restoredSensor);
        assertNotNull(restoredRepeat);
        assertNotNull(restoredMessage);
        assertNotNull(restoredWait);
        assertNotNull(restoredDuration);

        assertEquals(restoredControl, restoredSensor.getParentControl());
        assertEquals(restoredSensor, restoredControl.getAttachedSensor());
        assertEquals(restoredRepeat, restoredMessage.getParentActionControl());
        assertEquals(restoredMessage, restoredRepeat.getAttachedActionNode());
        assertEquals(restoredWait, restoredDuration.getParentParameterHost());
        assertEquals(restoredDuration, restoredWait.getAttachedParameter(0));
    }

    @Test
    void normalizeNodeGraphToPathPreservesKnownNodesFromMixedVersionPreset() throws Exception {
        Path sourcePath = tempDir.resolve("mixed-version.json");
        Files.writeString(sourcePath, """
            {
              "nodes": [
                {
                  "id": "start",
                  "type": "START",
                  "x": 0,
                  "y": 0,
                  "parameters": [],
                  "parameterAttachments": [],
                  "startNodeNumber": 1
                },
                {
                  "id": "future",
                  "type": "FUTURE_NODE_TYPE",
                  "x": 40,
                  "y": 0,
                  "parameters": [],
                  "parameterAttachments": []
                },
                {
                  "id": "legacyBoolean",
                  "type": "PARAM_BOOLEAN",
                  "x": 80,
                  "y": 0,
                  "parameters": [
                    {
                      "name": "Toggle",
                      "value": "false",
                      "type": "BOOLEAN",
                      "userEdited": true
                    }
                  ],
                  "booleanToggleValue": false,
                  "parameterAttachments": []
                }
              ],
              "connections": [
                {
                  "outputNodeId": "start",
                  "inputNodeId": "future",
                  "outputSocket": 0,
                  "inputSocket": 0
                }
              ]
            }
            """);

        Path normalizedPath = tempDir.resolve("normalized.json");
        assertTrue(NodeGraphPersistence.normalizeNodeGraphToPath(sourcePath, normalizedPath));

        NodeGraphData normalized = NodeGraphPersistence.loadNodeGraphFromPath(normalizedPath);
        assertNotNull(normalized);
        assertEquals(2, normalized.getNodes().size());
        assertFalse(normalized.getNodes().stream().anyMatch(node -> node.getType() == null));
        assertEquals(0, normalized.getConnections().size());

        List<Node> restoredNodes = NodeGraphPersistence.convertToNodes(normalized);
        Map<String, Node> byId = restoredNodes.stream().collect(Collectors.toMap(Node::getId, Function.identity()));
        Node restoredBoolean = byId.get("legacyBoolean");
        assertNotNull(restoredBoolean);
        assertNotNull(restoredBoolean.getParameter("Mode"));
        assertNotNull(restoredBoolean.getParameter("Variable"));
        assertEquals("false", restoredBoolean.getParameter("Toggle").getStringValue());
        assertEquals(false, restoredBoolean.getBooleanToggleValue());
    }

    private Node coordinateNode(int x, int y, int z) {
        Node coordinate = new Node(NodeType.PARAM_COORDINATE, 0, 0);
        coordinate.getParameter("X").setStringValue(Integer.toString(x));
        coordinate.getParameter("Y").setStringValue(Integer.toString(y));
        coordinate.getParameter("Z").setStringValue(Integer.toString(z));
        return coordinate;
    }
}
