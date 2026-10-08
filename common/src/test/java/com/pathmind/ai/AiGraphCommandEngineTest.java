package com.pathmind.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pathmind.data.NodeGraphData;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiGraphCommandEngineTest {
    @Test
    void connectingParameterAsFlowReturnsExactAttachmentRecovery() {
        JsonArray commands = new JsonArray();
        commands.add(command("add_node", "ref", "walk", "nodeType", "WALK"));
        commands.add(command("add_node", "ref", "direction", "nodeType", "PARAM_DIRECTION"));
        commands.add(command("connect", "from", "direction", "to", "walk", "outputSocket", 0, "inputSocket", 0));
        var result = AiGraphCommandEngine.apply(emptyGraph(), commands, Map.of(), true, true);
        assertFalse(result.success());
        assertEquals("wrong_connection_kind", result.errorCode());
        assertTrue(result.message().contains("attach_parameter"));
        assertTrue(result.message().contains("slotIndex"));
        assertEquals(0, emptyGraph().getAsJsonArray("nodes").size());
    }

    @Test
    void buildsCanonicalNodesAndConnectionsFromSemanticCommands() {
        JsonArray commands = new JsonArray();
        commands.add(command("add_node", "ref", "start", "nodeType", "START"));
        commands.add(command("add_node", "ref", "jump", "nodeType", "JUMP"));
        commands.add(command("connect", "from", "start", "to", "jump", "outputSocket", 0, "inputSocket", 0));

        AiGraphCommandEngine.Result result = AiGraphCommandEngine.apply(emptyGraph(), commands, Map.of(), true, true);

        assertTrue(result.success(), result.message());
        assertEquals(2, result.graph().getAsJsonArray("nodes").size());
        assertEquals(1, result.graph().getAsJsonArray("connections").size());
        assertNotEquals("start", result.references().get("start"));
        assertNotEquals("jump", result.references().get("jump"));
        assertEquals(result.references().get("start"), result.graph().getAsJsonArray("connections").get(0)
            .getAsJsonObject().get("outputNodeId").getAsString());
        NodeGraphData graph = parse(result.graph());
        assertTrue(AiGraphIntegrityValidator.validate(graph, true, true).isEmpty());
    }

    @Test
    void modeAndParameterCommandsUseCatalogOwnedDefaults() {
        JsonArray commands = new JsonArray();
        commands.add(command("add_node", "ref", "start", "nodeType", "START"));
        commands.add(command("add_node", "ref", "wait", "nodeType", "WAIT"));
        commands.add(command("set_mode", "ref", "wait", "mode", "WAIT_SECONDS"));
        commands.add(command("set_parameter", "ref", "wait", "parameterId", "duration", "value", "2.5"));
        commands.add(command("connect", "from", "start", "to", "wait", "outputSocket", 0, "inputSocket", 0));

        AiGraphCommandEngine.Result result = AiGraphCommandEngine.apply(emptyGraph(), commands, Map.of(), true, true);

        assertTrue(result.success(), result.message());
        JsonObject wait = findNode(result.graph(), result.references().get("wait"));
        assertEquals("WAIT_SECONDS", wait.get("mode").getAsString());
        JsonObject duration = wait.getAsJsonArray("parameters").get(0).getAsJsonObject();
        assertEquals("duration", duration.get("id").getAsString());
        assertEquals("2.5", duration.get("value").getAsString());
        assertTrue(duration.get("userEdited").getAsBoolean());
    }

    @Test
    void craftParametersAreTypedAtomicAndReadBackFromTheActualDraft() {
        JsonArray commands = new JsonArray();
        commands.add(command("add_node", "ref", "craft", "nodeType", "CRAFT"));
        JsonObject values = command("set_parameters", "ref", "craft");
        values.add("parameterValues", parameterValues("Item", "minecraft:oak_planks", "Amount", "4"));
        commands.add(values);

        AiGraphCommandEngine.Result result = AiGraphCommandEngine.apply(emptyGraph(), commands, Map.of(), true, true);

        assertTrue(result.success(), result.message());
        JsonObject craft = findNode(result.graph(), result.references().get("craft"));
        assertEquals("minecraft:oak_planks", parameter(craft, "item").get("value").getAsString());
        assertEquals("4", parameter(craft, "amount").get("value").getAsString());
        JsonObject readback = result.actualValues().get(0).getAsJsonObject();
        assertEquals("CRAFT", readback.get("nodeType").getAsString());
        assertTrue(readback.toString().contains("minecraft:oak_planks"));
        assertTrue(readback.toString().contains("\"value\":\"4\""));
    }

    @Test
    void malformedIdentifiersAreRejectedButPartialUpdatesRemainSupported() {
        JsonArray malformed = new JsonArray();
        malformed.add(command("add_node", "ref", "craft", "nodeType", "CRAFT"));
        JsonObject badValues = command("set_parameters", "ref", "craft");
        badValues.add("parameterValues", parameterValues("Item", "minecraft:oak_planks,4", "Amount", "4"));
        malformed.add(badValues);

        AiGraphCommandEngine.Result malformedResult = AiGraphCommandEngine.apply(
            emptyGraph(), malformed, Map.of(), true, true);

        assertFalse(malformedResult.success());
        assertEquals("invalid_parameter_value", malformedResult.errorCode());
        assertTrue(malformedResult.message().contains("one resource identifier"));

        JsonArray partial = new JsonArray();
        partial.add(command("add_node", "ref", "craft", "nodeType", "CRAFT"));
        JsonObject partialValues = command("set_parameters", "ref", "craft");
        partialValues.add("parameterValues", parameterValues("Item", "minecraft:oak_planks"));
        partial.add(partialValues);
        AiGraphCommandEngine.Result partialResult = AiGraphCommandEngine.apply(
            emptyGraph(), partial, Map.of(), true, true);
        assertTrue(partialResult.success(), partialResult.message());
        assertEquals("1", parameter(findNode(partialResult.graph(), partialResult.references().get("craft")), "amount").get("value").getAsString());
    }

    @Test
    void attachmentCommandsCreateBothSidesOfTheRelationship() {
        JsonArray commands = new JsonArray();
        commands.add(command("add_node", "ref", "start", "nodeType", "START"));
        commands.add(command("add_node", "ref", "repeat", "nodeType", "CONTROL_REPEAT"));
        commands.add(command("add_node", "ref", "jump", "nodeType", "JUMP"));
        commands.add(command("connect", "from", "start", "to", "repeat", "outputSocket", 0, "inputSocket", 0));
        commands.add(command("attach_action", "host", "repeat", "child", "jump"));

        AiGraphCommandEngine.Result result = AiGraphCommandEngine.apply(emptyGraph(), commands, Map.of(), true, true);

        assertTrue(result.success(), result.message());
        JsonObject repeat = findNode(result.graph(), result.references().get("repeat"));
        JsonObject jump = findNode(result.graph(), result.references().get("jump"));
        assertEquals(result.references().get("jump"), repeat.get("attachedActionId").getAsString());
        assertEquals(result.references().get("repeat"), jump.get("parentActionControlId").getAsString());
        assertTrue(AiGraphIntegrityValidator.validate(parse(result.graph()), true, true).isEmpty());
    }

    @Test
    void sensorAndParameterAttachmentsUseCatalogCompatibilityAndPairBothSides() {
        JsonArray commands = new JsonArray();
        commands.add(command("add_node", "ref", "condition", "nodeType", "CONTROL_IF"));
        commands.add(command("add_node", "ref", "daytime", "nodeType", "SENSOR_IS_DAYTIME"));
        commands.add(command("add_node", "ref", "goto", "nodeType", "GOTO"));
        commands.add(command("add_node", "ref", "target", "nodeType", "PARAM_COORDINATE"));
        commands.add(command("attach_sensor", "host", "condition", "child", "daytime"));
        commands.add(command("attach_parameter", "host", "goto", "child", "target", "slotIndex", 0));

        AiGraphCommandEngine.Result result = AiGraphCommandEngine.apply(emptyGraph(), commands, Map.of(), true, true);

        assertTrue(result.success(), result.message());
        JsonObject condition = findNode(result.graph(), result.references().get("condition"));
        JsonObject daytime = findNode(result.graph(), result.references().get("daytime"));
        assertEquals(result.references().get("daytime"), condition.get("attachedSensorId").getAsString());
        assertEquals(result.references().get("condition"), daytime.get("parentControlId").getAsString());

        JsonObject gotoNode = findNode(result.graph(), result.references().get("goto"));
        JsonObject target = findNode(result.graph(), result.references().get("target"));
        JsonObject attachment = gotoNode.getAsJsonArray("parameterAttachments").get(0).getAsJsonObject();
        assertEquals(0, attachment.get("slotIndex").getAsInt());
        assertEquals(result.references().get("target"), attachment.get("parameterNodeId").getAsString());
        assertEquals(result.references().get("goto"), target.get("parentParameterHostId").getAsString());
    }

    @Test
    void targetedRepairCommandsDisconnectAndDetachRelationships() {
        JsonArray build = new JsonArray();
        build.add(command("add_node", "ref", "start", "nodeType", "START"));
        build.add(command("add_node", "ref", "condition", "nodeType", "CONTROL_IF"));
        build.add(command("add_node", "ref", "sensor", "nodeType", "SENSOR_IS_DAYTIME"));
        build.add(command("connect", "from", "start", "to", "condition", "outputSocket", 0, "inputSocket", 0));
        build.add(command("attach_sensor", "host", "condition", "child", "sensor"));
        AiGraphCommandEngine.Result initial = AiGraphCommandEngine.apply(emptyGraph(), build, Map.of(), true, true);
        JsonArray repair = new JsonArray();
        repair.add(command("disconnect", "from", "start", "to", "condition", "outputSocket", 0, "inputSocket", 0));
        repair.add(command("detach_sensor", "host", "condition"));

        AiGraphCommandEngine.Result result = AiGraphCommandEngine.apply(initial.graph(), repair,
            initial.references(), true, true);

        assertTrue(result.success(), result.message());
        assertEquals(0, result.graph().getAsJsonArray("connections").size());
        assertFalse(findNode(result.graph(), result.references().get("condition")).has("attachedSensorId"));
        assertFalse(findNode(result.graph(), result.references().get("sensor")).has("parentControlId"));
    }

    @Test
    void failedCommandBatchIsAtomic() {
        JsonObject source = emptyGraph();
        JsonArray commands = new JsonArray();
        commands.add(command("add_node", "ref", "start", "nodeType", "START"));
        commands.add(command("connect", "from", "start", "to", "missing", "outputSocket", 0, "inputSocket", 0));

        AiGraphCommandEngine.Result result = AiGraphCommandEngine.apply(source, commands, Map.of(), true, true);

        assertFalse(result.success());
        assertEquals(0, source.getAsJsonArray("nodes").size());
        assertTrue(result.references().isEmpty());
    }

    @Test
    void removeNodeCleansConnectionsAttachmentsAndReferences() {
        JsonArray build = new JsonArray();
        build.add(command("add_node", "ref", "start", "nodeType", "START"));
        build.add(command("add_node", "ref", "repeat", "nodeType", "CONTROL_REPEAT"));
        build.add(command("add_node", "ref", "jump", "nodeType", "JUMP"));
        build.add(command("connect", "from", "start", "to", "repeat", "outputSocket", 0, "inputSocket", 0));
        build.add(command("attach_action", "host", "repeat", "child", "jump"));
        AiGraphCommandEngine.Result initial = AiGraphCommandEngine.apply(emptyGraph(), build, Map.of(), true, true);
        JsonArray remove = new JsonArray();
        remove.add(command("remove_node", "ref", "jump"));

        AiGraphCommandEngine.Result result = AiGraphCommandEngine.apply(initial.graph(), remove, initial.references(), true, true);

        assertTrue(result.success(), result.message());
        assertEquals(2, result.graph().getAsJsonArray("nodes").size());
        assertFalse(result.references().containsKey("jump"));
        JsonObject repeat = findNode(result.graph(), result.references().get("repeat"));
        assertFalse(repeat.has("attachedActionId"));
    }

    @Test
    void buildsAndWrapsASequenceInOneCompositionCommand() {
        JsonArray commands = new JsonArray();
        commands.add(arrayCommand("add_sequence", "refs", "start", "wait", "jump",
            "nodeTypes", "START", "WAIT", "JUMP"));
        JsonObject wrap = command("wrap_in_repeat", "ref", "repeat", "count", 25);
        wrap.add("refs", strings("wait", "jump"));
        commands.add(wrap);
        commands.add(command("auto_layout"));

        AiGraphCommandEngine.Result result = AiGraphCommandEngine.apply(emptyGraph(), commands, Map.of(), true, true);

        assertTrue(result.success(), result.message());
        JsonObject repeat = findNode(result.graph(), result.references().get("repeat"));
        assertEquals(result.references().get("wait"), repeat.get("attachedActionId").getAsString());
        assertEquals("25", repeat.getAsJsonArray("parameters").get(0).getAsJsonObject().get("value").getAsString());
        assertTrue(hasConnection(result.graph(), result.references().get("start"), result.references().get("repeat"), 0));
        assertTrue(hasConnection(result.graph(), result.references().get("wait"), result.references().get("jump"), 0));
        assertFalse(hasConnection(result.graph(), result.references().get("start"), result.references().get("wait"), 0));
        assertTrue(AiGraphIntegrityValidator.validate(parse(result.graph()), true, true).isEmpty());
    }

    @Test
    void insertsASequenceAfterAConnectedNodeAndPreservesItsSuccessor() {
        JsonArray build = new JsonArray();
        build.add(arrayCommand("add_sequence", "refs", "start", "jump", "after",
            "nodeTypes", "START", "JUMP", "MESSAGE"));
        AiGraphCommandEngine.Result initial = AiGraphCommandEngine.apply(emptyGraph(), build, Map.of(), true, true);
        JsonObject splice = arrayCommand("insert_sequence_after", "refs", "inventory", "craft",
            "nodeTypes", "OPEN_INVENTORY", "CRAFT");
        splice.addProperty("ref", "jump");
        splice.addProperty("outputSocket", 0);
        JsonArray commands = new JsonArray();
        commands.add(splice);

        AiGraphCommandEngine.Result result = AiGraphCommandEngine.apply(initial.graph(), commands,
            initial.references(), true, true);

        assertTrue(result.success(), result.message());
        String jump = result.references().get("jump");
        String inventory = result.references().get("inventory");
        String craft = result.references().get("craft");
        String after = result.references().get("after");
        assertFalse(hasConnection(result.graph(), jump, after, 0));
        assertTrue(hasConnection(result.graph(), jump, inventory, 0));
        assertTrue(hasConnection(result.graph(), inventory, craft, 0));
        assertTrue(hasConnection(result.graph(), craft, after, 0));
        assertTrue(AiGraphIntegrityValidator.validate(parse(result.graph()), true, true).isEmpty());
    }

    @Test
    void occupiedOutputReportsTheCurrentDestinationAndIsRecoverable() {
        JsonArray build = new JsonArray();
        build.add(arrayCommand("add_sequence", "refs", "start", "jump",
            "nodeTypes", "START", "JUMP"));
        AiGraphCommandEngine.Result initial = AiGraphCommandEngine.apply(emptyGraph(), build, Map.of(), true, true);
        JsonArray commands = new JsonArray();
        commands.add(command("add_node", "ref", "wait", "nodeType", "WAIT"));
        commands.add(command("connect", "from", "start", "to", "wait", "outputSocket", 0, "inputSocket", 0));

        AiGraphCommandEngine.Result result = AiGraphCommandEngine.apply(initial.graph(), commands,
            initial.references(), true, true);

        assertFalse(result.success());
        assertEquals("occupied_output", result.errorCode());
        assertTrue(result.recoverable());
        assertTrue(result.message().contains("JUMP 'jump' input socket 0"), result.message());
        assertTrue(result.message().contains("insert_sequence_after"), result.message());
        assertEquals(2, initial.graph().getAsJsonArray("nodes").size());
    }

    @Test
    void createsNativeTrueFalseBranch() {
        JsonArray commands = new JsonArray();
        commands.add(command("add_node", "ref", "sensor", "nodeType", "SENSOR_IS_DAYTIME"));
        commands.add(command("add_node", "ref", "yes", "nodeType", "JUMP"));
        commands.add(command("add_node", "ref", "no", "nodeType", "WAIT"));
        JsonObject branch = command("create_branch", "ref", "branch", "sensor", "sensor");
        branch.add("trueRefs", strings("yes"));
        branch.add("falseRefs", strings("no"));
        commands.add(branch);

        AiGraphCommandEngine.Result result = AiGraphCommandEngine.apply(emptyGraph(), commands, Map.of(), true, true);

        assertTrue(result.success(), result.message());
        JsonObject control = findNode(result.graph(), result.references().get("branch"));
        assertEquals("CONTROL_IF_ELSE", control.get("type").getAsString());
        assertEquals(result.references().get("sensor"), control.get("attachedSensorId").getAsString());
        assertTrue(hasConnection(result.graph(), result.references().get("branch"), result.references().get("yes"), 0));
        assertTrue(hasConnection(result.graph(), result.references().get("branch"), result.references().get("no"), 1));
    }

    @Test
    void declaresVariableAndListThroughMeaningfulOperations() {
        JsonArray commands = new JsonArray();
        commands.add(command("add_node", "ref", "amount", "nodeType", "PARAM_AMOUNT"));
        commands.add(command("add_node", "ref", "listValue", "nodeType", "PARAM_AMOUNT"));
        commands.add(command("declare_variable", "ref", "setScore", "variableRef", "score",
            "name", "score", "child", "amount"));
        commands.add(command("declare_list", "ref", "nearby", "name", "nearby_blocks", "child", "listValue"));

        AiGraphCommandEngine.Result result = AiGraphCommandEngine.apply(emptyGraph(), commands, Map.of(), true, true);

        assertTrue(result.success(), result.message());
        JsonObject score = findNode(result.graph(), result.references().get("score"));
        assertEquals("score", score.getAsJsonArray("parameters").get(0).getAsJsonObject().get("value").getAsString());
        JsonObject setter = findNode(result.graph(), result.references().get("setScore"));
        assertEquals(2, setter.getAsJsonArray("parameterAttachments").size());
        JsonObject list = findNode(result.graph(), result.references().get("nearby"));
        assertEquals("nearby_blocks", list.getAsJsonArray("parameters").get(0).getAsJsonObject().get("value").getAsString());
    }

    @Test
    void clonesAndReplacesSubgraphsWithoutReusingIds() {
        JsonArray build = new JsonArray();
        build.add(arrayCommand("add_sequence", "refs", "start", "wait", "jump", "stop",
            "nodeTypes", "START", "WAIT", "JUMP", "MESSAGE"));
        JsonObject clone = command("clone_subgraph");
        clone.add("refs", strings("wait", "jump"));
        clone.add("newRefs", strings("waitCopy", "jumpCopy"));
        build.add(clone);
        JsonObject replace = command("replace_subgraph");
        replace.add("refs", strings("wait", "jump"));
        replace.add("replacementRefs", strings("waitCopy", "jumpCopy"));
        build.add(replace);

        AiGraphCommandEngine.Result result = AiGraphCommandEngine.apply(emptyGraph(), build, Map.of(), true, true);

        assertTrue(result.success(), result.message());
        assertFalse(result.references().containsKey("wait"));
        assertFalse(result.references().containsKey("jump"));
        assertTrue(hasConnection(result.graph(), result.references().get("start"), result.references().get("waitCopy"), 0));
        assertTrue(hasConnection(result.graph(), result.references().get("jumpCopy"), result.references().get("stop"), 0));
    }

    @Test
    void cloningCarriesAttachedBehaviorAsOwnedSubgraph() {
        JsonArray commands = new JsonArray();
        commands.add(command("add_node", "ref", "condition", "nodeType", "CONTROL_IF"));
        commands.add(command("add_node", "ref", "sensor", "nodeType", "SENSOR_IS_DAYTIME"));
        commands.add(command("attach_sensor", "host", "condition", "child", "sensor"));
        JsonObject clone = command("clone_subgraph");
        clone.add("refs", strings("condition"));
        clone.add("newRefs", strings("conditionCopy"));
        commands.add(clone);

        AiGraphCommandEngine.Result result = AiGraphCommandEngine.apply(emptyGraph(), commands, Map.of(), true, true);

        assertTrue(result.success(), result.message());
        assertEquals(4, result.graph().getAsJsonArray("nodes").size());
        JsonObject original = findNode(result.graph(), result.references().get("condition"));
        JsonObject copied = findNode(result.graph(), result.references().get("conditionCopy"));
        assertNotEquals(original.get("attachedSensorId").getAsString(), copied.get("attachedSensorId").getAsString());
        JsonObject copiedSensor = findNode(result.graph(), copied.get("attachedSensorId").getAsString());
        assertEquals(copied.get("id").getAsString(), copiedSensor.get("parentControlId").getAsString());
    }

    @Test
    void extractsASequenceIntoAReusableTypedRoutine() {
        JsonArray commands = new JsonArray();
        commands.add(arrayCommand("add_sequence", "refs", "start", "look", "after",
            "nodeTypes", "START", "LOOK", "MESSAGE"));
        JsonObject routine = command("create_routine", "ref", "call", "routineRef", "movement", "name", "Movement");
        routine.add("refs", strings("look"));
        JsonObject input = new JsonObject();
        input.addProperty("label", "times");
        input.addProperty("valueKind", "NUMBER");
        input.addProperty("required", false);
        input.addProperty("defaultValue", "1");
        input.addProperty("bindToRef", "look");
        input.addProperty("slotIndex", 0);
        JsonArray inputs = new JsonArray();
        inputs.add(input);
        routine.add("routineInputs", inputs);
        commands.add(routine);
        commands.add(command("add_routine_call", "ref", "secondCall", "routineRef", "movement"));

        AiGraphCommandEngine.Result result = AiGraphCommandEngine.apply(emptyGraph(), commands, Map.of(), true, true);

        assertTrue(result.success(), result.message());
        assertEquals(1, result.graph().getAsJsonArray("routines").size());
        JsonObject definition = result.graph().getAsJsonArray("routines").get(0).getAsJsonObject();
        assertEquals("Movement", definition.get("name").getAsString());
        assertEquals(1, definition.getAsJsonArray("inputs").size());
        assertEquals(3, definition.getAsJsonObject("graph").getAsJsonArray("nodes").size());
        assertEquals("ROUTINE_CALL", findNode(result.graph(), result.references().get("call")).get("type").getAsString());
        assertEquals("ROUTINE_CALL", findNode(result.graph(), result.references().get("secondCall")).get("type").getAsString());
        assertTrue(hasConnection(result.graph(), result.references().get("start"), result.references().get("call"), 0));
        assertTrue(hasConnection(result.graph(), result.references().get("call"), result.references().get("after"), 0));
        assertTrue(AiGraphIntegrityValidator.validate(parse(result.graph()), true, true).isEmpty());
    }

    // messageClientSide ships in the serialized graph format and in the bundled example presets the
    // model is invited to read, but had no command to set it. Models copied the name out of those
    // examples and burned turns discovering it was not an instance parameter.
    @Test
    void configureNodeSetsTheMessageScopeCarriedBySerializedGraphs() {
        JsonArray commands = new JsonArray();
        commands.add(command("add_node", "ref", "notify", "nodeType", "MESSAGE"));
        commands.add(command("configure_node", "ref", "notify", "messageClientSide", true));

        var result = AiGraphCommandEngine.apply(emptyGraph(), commands, Map.of(), true, true);

        assertTrue(result.success(), result.message());
        var node = result.graph().getAsJsonArray("nodes").get(0).getAsJsonObject();
        assertTrue(node.get("messageClientSide").getAsBoolean());
    }

    @Test
    void configureNodeStillRejectsABatchThatChangesNothing() {
        JsonArray commands = new JsonArray();
        commands.add(command("add_node", "ref", "notify", "nodeType", "MESSAGE"));
        commands.add(command("configure_node", "ref", "notify"));

        var result = AiGraphCommandEngine.apply(emptyGraph(), commands, Map.of(), true, true);

        assertFalse(result.success());
        assertTrue(result.message().contains("messageClientSide"), result.message());
    }

    @Test
    void messageScopeIsDiscoverableInTheNativeConfigureNodeSchema() {
        var commands = AiAgentToolDefinitions.create().asList().stream()
            .map(com.google.gson.JsonElement::getAsJsonObject)
            .filter(tool -> tool.get("name").getAsString().equals("apply_graph_commands"))
            .findFirst().orElseThrow();
        var alternatives = commands.getAsJsonObject("parameters").getAsJsonObject("properties")
            .getAsJsonObject("commands").getAsJsonObject("items").getAsJsonArray("anyOf");
        boolean exposed = alternatives.asList().stream().map(com.google.gson.JsonElement::getAsJsonObject)
            .filter(alternative -> alternative.getAsJsonObject("properties").getAsJsonObject("kind")
                .getAsJsonArray("enum").get(0).getAsString().equals("configure_node"))
            .anyMatch(alternative -> alternative.getAsJsonObject("properties").has("messageClientSide"));
        assertTrue(exposed, "configure_node must advertise messageClientSide");
    }

    private static JsonObject command(String kind, Object... fields) {
        JsonObject command = new JsonObject();
        command.addProperty("kind", kind);
        for (int index = 0; index < fields.length; index += 2) {
            String key = (String) fields[index];
            Object value = fields[index + 1];
            if (value instanceof Number number) command.addProperty(key, number);
            else if (value instanceof Boolean flag) command.addProperty(key, flag);
            else command.addProperty(key, String.valueOf(value));
        }
        return command;
    }

    private static JsonObject arrayCommand(String kind, String firstKey, String... values) {
        JsonObject command = command(kind);
        JsonArray first = new JsonArray();
        int divider = -1;
        for (int index = 0; index < values.length; index++) {
            if ("nodeTypes".equals(values[index])) { divider = index; break; }
            first.add(values[index]);
        }
        command.add(firstKey, first);
        JsonArray types = new JsonArray();
        for (int index = divider + 1; divider >= 0 && index < values.length; index++) types.add(values[index]);
        command.add("nodeTypes", types);
        return command;
    }

    private static JsonArray strings(String... values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(value);
        return result;
    }

    private static JsonArray parameterValues(String... fields) {
        JsonArray values = new JsonArray();
        for (int index = 0; index < fields.length; index += 2) {
            values.add(command("value", "parameterId", fields[index], "value", fields[index + 1]));
        }
        return values;
    }

    private static JsonObject parameter(JsonObject node, String id) {
        for (var element : node.getAsJsonArray("parameters")) {
            JsonObject parameter = element.getAsJsonObject();
            if (id.equals(parameter.get("id").getAsString())) return parameter;
        }
        throw new AssertionError("Missing parameter " + id);
    }

    private static boolean hasConnection(JsonObject graph, String from, String to, int outputSocket) {
        for (var element : graph.getAsJsonArray("connections")) {
            JsonObject edge = element.getAsJsonObject();
            if (from.equals(edge.get("outputNodeId").getAsString())
                && to.equals(edge.get("inputNodeId").getAsString())
                && outputSocket == edge.get("outputSocket").getAsInt()) return true;
        }
        return false;
    }

    private static JsonObject emptyGraph() {
        JsonObject graph = new JsonObject();
        graph.add("nodes", new JsonArray());
        graph.add("connections", new JsonArray());
        graph.add("customNodeDefinition", null);
        graph.add("routines", new JsonArray());
        return graph;
    }

    private static NodeGraphData parse(JsonObject graph) {
        NodeGraphData parsed = com.pathmind.data.NodeGraphPersistence.parseNodeGraphData(graph.toString());
        assertNotNull(parsed);
        return parsed;
    }

    private static JsonObject findNode(JsonObject graph, String id) {
        for (var element : graph.getAsJsonArray("nodes")) {
            JsonObject node = element.getAsJsonObject();
            if (id.equals(node.get("id").getAsString())) return node;
        }
        throw new AssertionError("Missing node " + id);
    }
}
