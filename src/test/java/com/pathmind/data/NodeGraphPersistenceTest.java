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
import static org.junit.jupiter.api.Assertions.assertNull;
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
        waitWithParameter.setMode(com.pathmind.nodes.NodeMode.WAIT_MINUTES);
        waitWithParameter.getParameter("Duration").setStringValue("5");

        Node template = new Node(NodeType.TEMPLATE, 200, 70);
        template.setTemplateName("Reusable");
        template.setTemplateVersion(3);
        template.setCustomNodeInstance(true);
        template.setTemplateGraphData(new NodeGraphData(
            List.of(new NodeGraphData.NodeData("inner-start", NodeType.START, null, 0, 0, List.of())),
            List.of()
        ));

        List<Node> nodes = List.of(
            start, wait, controlWaitUntil, sensor, repeat, message, waitWithParameter, template
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
        assertNull(restoredWaitWithParameter.getAttachedParameter(0));
        assertEquals("5", restoredWaitWithParameter.getParameter("Duration").getStringValue());
        assertEquals(com.pathmind.nodes.NodeMode.WAIT_MINUTES, restoredWaitWithParameter.getMode());
        assertEquals("Reusable", restoredTemplate.getTemplateName());
        assertEquals(3, restoredTemplate.getTemplateVersion());
        assertTrue(restoredTemplate.isCustomNodeInstance());
        assertNotNull(restoredTemplate.getTemplateGraphData());
        assertEquals(1, restoredTemplate.getTemplateGraphData().getNodes().size());
        assertEquals(connections.size(), restoredConnections.size());
    }

    @Test
    void waitNodeSupportsSameModeSelectionAsDurationParameter() {
        Node wait = new Node(NodeType.WAIT, 10, 20);

        assertTrue(wait.supportsModeSelection());
        assertEquals(com.pathmind.nodes.NodeMode.WAIT_SECONDS, com.pathmind.nodes.NodeMode.getDefaultModeForNodeType(NodeType.WAIT));
        assertEquals(
            List.of(com.pathmind.nodes.NodeMode.WAIT_SECONDS, com.pathmind.nodes.NodeMode.WAIT_TICKS,
                com.pathmind.nodes.NodeMode.WAIT_MINUTES, com.pathmind.nodes.NodeMode.WAIT_HOURS),
            List.of(com.pathmind.nodes.NodeMode.getModesForNodeType(NodeType.WAIT))
        );
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
        wait.getParameter("Duration").setStringValue("3");

        Path savePath = tempDir.resolve("legacy-layout-graph.json");
        assertTrue(NodeGraphPersistence.saveNodeGraphToPath(
            List.of(controlWaitUntil, sensor, repeat, message, wait),
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
        assertNotNull(restoredControl);
        assertNotNull(restoredSensor);
        assertNotNull(restoredRepeat);
        assertNotNull(restoredMessage);
        assertNotNull(restoredWait);

        assertEquals(restoredControl, restoredSensor.getParentControl());
        assertEquals(restoredSensor, restoredControl.getAttachedSensor());
        assertEquals(restoredRepeat, restoredMessage.getParentActionControl());
        assertEquals(restoredMessage, restoredRepeat.getAttachedActionNode());
        assertEquals("3", restoredWait.getParameter("Duration").getStringValue());
        assertNull(restoredWait.getAttachedParameter(0));
    }

    @Test
    void convertToNodesAbsorbsLegacyWaitDurationAttachments() {
        NodeGraphData.NodeData waitNode = new NodeGraphData.NodeData(
            "wait-1",
            NodeType.WAIT,
            null,
            200,
            60,
            List.of(new NodeGraphData.ParameterData("Duration", "0.0", "DOUBLE"))
        );
        waitNode.setParameterAttachments(new java.util.ArrayList<>(List.of(new NodeGraphData.ParameterAttachmentData(0, "duration-1"))));
        waitNode.setAttachedParameterId("duration-1");

        NodeGraphData.NodeData durationNode = new NodeGraphData.NodeData(
            "duration-1",
            NodeType.PARAM_DURATION,
            com.pathmind.nodes.NodeMode.WAIT_MINUTES,
            0,
            0,
            List.of(new NodeGraphData.ParameterData("Duration", "3", "DOUBLE"))
        );
        durationNode.setParentParameterHostId("wait-1");

        List<Node> restoredNodes = NodeGraphPersistence.convertToNodes(
            new NodeGraphData(List.of(waitNode, durationNode), List.of())
        );
        Map<String, Node> byId = restoredNodes.stream().collect(Collectors.toMap(Node::getId, Function.identity()));

        Node restoredWait = byId.get("wait-1");
        assertNotNull(restoredWait);
        assertEquals("3", restoredWait.getParameter("Duration").getStringValue());
        assertEquals(com.pathmind.nodes.NodeMode.WAIT_MINUTES, restoredWait.getMode());
        assertNull(restoredWait.getAttachedParameter(0));
        assertFalse(byId.containsKey("duration-1"));
    }

    @Test
    void convertToNodesRepairsMissingCompatibilityParameters() {
        NodeGraphData.NodeData booleanNode = new NodeGraphData.NodeData(
            "bool-1",
            NodeType.PARAM_BOOLEAN,
            null,
            10,
            20,
            List.of(new NodeGraphData.ParameterData("Toggle", "false", "BOOLEAN"))
        );
        NodeGraphData.NodeData createListNode = new NodeGraphData.NodeData(
            "list-1",
            NodeType.CREATE_LIST,
            null,
            30,
            40,
            List.of()
        );

        List<Node> restoredNodes = NodeGraphPersistence.convertToNodes(
            new NodeGraphData(List.of(booleanNode, createListNode), List.of())
        );
        Map<String, Node> byId = restoredNodes.stream().collect(Collectors.toMap(Node::getId, Function.identity()));

        Node restoredBoolean = byId.get("bool-1");
        Node restoredCreateList = byId.get("list-1");
        assertNotNull(restoredBoolean);
        assertNotNull(restoredCreateList);
        assertNotNull(restoredBoolean.getParameter("Mode"));
        assertNotNull(restoredBoolean.getParameter("Variable"));
        assertEquals("false", restoredBoolean.getParameter("Toggle").getStringValue());
        assertNotNull(restoredCreateList.getParameter("UseRadius"));
        assertNotNull(restoredCreateList.getParameter("Radius"));
        assertNotNull(restoredCreateList.getParameter("UseBlockCap"));
        assertNotNull(restoredCreateList.getParameter("MaxBlocks"));
    }

    @Test
    void convertToNodesRestoresParametersByStableIdBeforeDisplayName() {
        NodeGraphData.ParameterData renamedDuration = new NodeGraphData.ParameterData();
        renamedDuration.setId("duration");
        renamedDuration.setName("Seconds");
        renamedDuration.setType("DOUBLE");
        renamedDuration.setValue("7.5");

        NodeGraphData.NodeData waitNode = new NodeGraphData.NodeData(
            "wait-1",
            NodeType.WAIT,
            null,
            10,
            20,
            List.of(renamedDuration)
        );

        List<Node> restoredNodes = NodeGraphPersistence.convertToNodes(
            new NodeGraphData(List.of(waitNode), List.of())
        );
        Map<String, Node> byId = restoredNodes.stream().collect(Collectors.toMap(Node::getId, Function.identity()));

        Node restoredWait = byId.get("wait-1");
        assertNotNull(restoredWait);
        assertNotNull(restoredWait.getParameter("Duration"));
        assertEquals("7.5", restoredWait.getParameter("Duration").getStringValue());
        assertNull(restoredWait.getParameter("Seconds"));
    }

    @Test
    void convertToNodesRestoresExplicitTradeParameterIdsAcrossDisplayNameChanges() {
        NodeGraphData.ParameterData legacyTradeNumber = new NodeGraphData.ParameterData();
        legacyTradeNumber.setId("trade_number");
        legacyTradeNumber.setName("Amount");
        legacyTradeNumber.setType("INTEGER");
        legacyTradeNumber.setValue("4");

        NodeGraphData.NodeData tradeNode = new NodeGraphData.NodeData(
            "trade-1",
            NodeType.TRADE,
            null,
            10,
            20,
            List.of(legacyTradeNumber)
        );

        List<Node> restoredNodes = NodeGraphPersistence.convertToNodes(
            new NodeGraphData(List.of(tradeNode), List.of())
        );
        Map<String, Node> byId = restoredNodes.stream().collect(Collectors.toMap(Node::getId, Function.identity()));

        Node restoredTrade = byId.get("trade-1");
        assertNotNull(restoredTrade);
        assertNotNull(restoredTrade.getParameter("Number"));
        assertEquals("4", restoredTrade.getParameter("Number").getStringValue());
        assertNull(restoredTrade.getParameter("Amount"));
    }

    @Test
    void convertToNodesRestoresDirectionAndRotationParameterIdsAcrossDisplayNameChanges() {
        NodeGraphData.ParameterData renamedDirectionYaw = new NodeGraphData.ParameterData();
        renamedDirectionYaw.setId("direction_yaw");
        renamedDirectionYaw.setName("Horizontal");
        renamedDirectionYaw.setType("DOUBLE");
        renamedDirectionYaw.setValue("90");

        NodeGraphData.ParameterData renamedDirectionPitch = new NodeGraphData.ParameterData();
        renamedDirectionPitch.setId("direction_pitch");
        renamedDirectionPitch.setName("Vertical");
        renamedDirectionPitch.setType("DOUBLE");
        renamedDirectionPitch.setValue("-30");

        NodeGraphData.ParameterData renamedRotationDistance = new NodeGraphData.ParameterData();
        renamedRotationDistance.setId("rotation_distance");
        renamedRotationDistance.setName("Reach");
        renamedRotationDistance.setType("DOUBLE");
        renamedRotationDistance.setValue("24");

        NodeGraphData.NodeData directionNode = new NodeGraphData.NodeData(
            "direction-1",
            NodeType.PARAM_DIRECTION,
            null,
            10,
            20,
            List.of(renamedDirectionYaw, renamedDirectionPitch)
        );
        NodeGraphData.NodeData rotationNode = new NodeGraphData.NodeData(
            "rotation-1",
            NodeType.PARAM_ROTATION,
            null,
            30,
            40,
            List.of(renamedRotationDistance)
        );

        List<Node> restoredNodes = NodeGraphPersistence.convertToNodes(
            new NodeGraphData(List.of(directionNode, rotationNode), List.of())
        );
        Map<String, Node> byId = restoredNodes.stream().collect(Collectors.toMap(Node::getId, Function.identity()));

        Node restoredDirection = byId.get("direction-1");
        Node restoredRotation = byId.get("rotation-1");
        assertNotNull(restoredDirection);
        assertNotNull(restoredRotation);
        assertEquals("90", restoredDirection.getParameter("Yaw").getStringValue());
        assertEquals("-30", restoredDirection.getParameter("Pitch").getStringValue());
        assertNull(restoredDirection.getParameter("Horizontal"));
        assertNull(restoredDirection.getParameter("Vertical"));
        assertEquals("24", restoredRotation.getParameter("Distance").getStringValue());
        assertNull(restoredRotation.getParameter("Reach"));
    }

    @Test
    void convertToNodesRestoresSlotParameterIdsAcrossDisplayNameChanges() {
        NodeGraphData.ParameterData renamedInventorySlot = new NodeGraphData.ParameterData();
        renamedInventorySlot.setId("inventory_slot_index");
        renamedInventorySlot.setName("Index");
        renamedInventorySlot.setType("INTEGER");
        renamedInventorySlot.setValue("12");

        NodeGraphData.ParameterData renamedInventoryMode = new NodeGraphData.ParameterData();
        renamedInventoryMode.setId("inventory_slot_mode");
        renamedInventoryMode.setName("Selection");
        renamedInventoryMode.setType("STRING");
        renamedInventoryMode.setValue("gui_container");

        NodeGraphData.ParameterData renamedMoveSource = new NodeGraphData.ParameterData();
        renamedMoveSource.setId("move_item_source_slot");
        renamedMoveSource.setName("From");
        renamedMoveSource.setType("INTEGER");
        renamedMoveSource.setValue("5");

        NodeGraphData.ParameterData renamedMoveTarget = new NodeGraphData.ParameterData();
        renamedMoveTarget.setId("move_item_target_slot");
        renamedMoveTarget.setName("To");
        renamedMoveTarget.setType("INTEGER");
        renamedMoveTarget.setValue("17");

        NodeGraphData.NodeData inventorySlotNode = new NodeGraphData.NodeData(
            "slot-1",
            NodeType.PARAM_INVENTORY_SLOT,
            null,
            10,
            20,
            List.of(renamedInventorySlot, renamedInventoryMode)
        );
        NodeGraphData.NodeData moveItemNode = new NodeGraphData.NodeData(
            "move-1",
            NodeType.MOVE_ITEM,
            null,
            30,
            40,
            List.of(renamedMoveSource, renamedMoveTarget)
        );

        List<Node> restoredNodes = NodeGraphPersistence.convertToNodes(
            new NodeGraphData(List.of(inventorySlotNode, moveItemNode), List.of())
        );
        Map<String, Node> byId = restoredNodes.stream().collect(Collectors.toMap(Node::getId, Function.identity()));

        Node restoredInventorySlot = byId.get("slot-1");
        Node restoredMoveItem = byId.get("move-1");
        assertNotNull(restoredInventorySlot);
        assertNotNull(restoredMoveItem);
        assertEquals("12", restoredInventorySlot.getParameter("Slot").getStringValue());
        assertEquals("gui_container", restoredInventorySlot.getParameter("Mode").getStringValue());
        assertNull(restoredInventorySlot.getParameter("Index"));
        assertNull(restoredInventorySlot.getParameter("Selection"));
        assertEquals("5", restoredMoveItem.getParameter("SourceSlot").getStringValue());
        assertEquals("17", restoredMoveItem.getParameter("TargetSlot").getStringValue());
        assertNull(restoredMoveItem.getParameter("From"));
        assertNull(restoredMoveItem.getParameter("To"));
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
