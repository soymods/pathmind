package com.pathmind.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeCatalog;
import com.pathmind.nodes.NodeCompatibility;
import com.pathmind.nodes.NodeMode;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeSlotType;
import com.pathmind.nodes.NodeType;
import com.pathmind.routines.RoutineBuilderModel;
import com.pathmind.routines.RoutineValueKind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies model-facing graph commands while Pathmind owns serialized graph details. */
public final class AiGraphCommandEngine {
    private static final int MAX_COMMANDS = 48;
    private static final Gson GSON = new Gson();

    private AiGraphCommandEngine() {
    }

    public static Result apply(JsonObject source, JsonArray commands, Map<String, String> knownReferences,
                               boolean baritoneAvailable, boolean uiUtilsAvailable) {
        if (source == null) return Result.failure("context_unavailable", false, "No graph draft is available.");
        if (commands == null || commands.isEmpty()) return Result.failure("command_rejected", true, "No graph commands were supplied.");
        if (commands.size() > MAX_COMMANDS) return Result.failure("command_rejected", true,
            "A command batch may contain at most " + MAX_COMMANDS + " commands.");
        try {
            NodeGraphData draft = NodeGraphPersistence.parseNodeGraphData(source.deepCopy().toString());
            if (draft == null) return Result.failure("context_unavailable", false, "The graph draft could not be read.");
            if (draft.getNodes() == null) draft.setNodes(new ArrayList<>());
            if (draft.getConnections() == null) draft.setConnections(new ArrayList<>());
            Map<String, String> references = new LinkedHashMap<>();
            if (knownReferences != null) references.putAll(knownReferences);
            removeStaleReferences(draft, references);
            JsonArray effects = new JsonArray();
            for (JsonElement element : commands) {
                if (!element.isJsonObject()) throw new IllegalArgumentException("Every graph command must be an object.");
                JsonObject command = element.getAsJsonObject();
                NodeGraphData scope = AiGraphScope.resolve(draft, nullableString(command, "graphRef"), references);
                if (scope != draft && "create_routine".equals(nullableString(command, "kind")))
                    throw new IllegalArgumentException("Create routine definitions in the root graph; graphRef supports editing bodies and adding calls to existing root routines.");
                if ("add_routine_call".equals(nullableString(command, "kind"))) addRoutineCall(scope, draft, command, references, effects);
                else applyOne(scope, command, references, effects, baritoneAvailable, uiUtilsAvailable);
            }
            return new Result(true, GSON.toJsonTree(draft).getAsJsonObject(), Map.copyOf(references), effects,
                parameterReadback(draft, commands, references), "", false,
                "Applied " + commands.size() + " semantic graph command(s).");
        } catch (CommandFailure failure) {
            return Result.failure(failure.code(), failure.recoverable(), failure.getMessage());
        } catch (RuntimeException exception) {
            return Result.failure("command_rejected", true,
                exception.getMessage() == null ? "The graph command batch was invalid." : exception.getMessage());
        }
    }

    private static void applyOne(NodeGraphData graph, JsonObject command, Map<String, String> references,
                                 JsonArray effects, boolean baritoneAvailable, boolean uiUtilsAvailable) {
        String kind = requiredString(command, "kind");
        switch (kind) {
            case "add_node" -> addNode(graph, command, references, effects, baritoneAvailable, uiUtilsAvailable);
            case "set_mode" -> setMode(graph, command, references, effects);
            case "set_parameter" -> setParameter(graph, command, references, effects);
            case "set_parameters" -> setParameters(graph, command, references, effects);
            case "configure_node" -> configureNode(graph, command, references, effects);
            case "connect" -> connect(graph, command, references, effects);
            case "disconnect" -> disconnect(graph, command, references, effects);
            case "attach_action" -> attach(graph, command, references, effects, NodeSlotType.ACTION);
            case "attach_sensor" -> attach(graph, command, references, effects, NodeSlotType.SENSOR);
            case "attach_parameter" -> attach(graph, command, references, effects, NodeSlotType.PARAMETER);
            case "detach_action" -> detach(graph, command, references, effects, NodeSlotType.ACTION);
            case "detach_sensor" -> detach(graph, command, references, effects, NodeSlotType.SENSOR);
            case "detach_parameter" -> detach(graph, command, references, effects, NodeSlotType.PARAMETER);
            case "remove_node" -> removeNode(graph, command, references, effects);
            case "add_sequence" -> addSequence(graph, command, references, effects, baritoneAvailable, uiUtilsAvailable);
            case "insert_sequence_after" -> insertSequenceAfter(graph, command, references, effects,
                baritoneAvailable, uiUtilsAvailable);
            case "wrap_in_repeat" -> wrapInControl(graph, command, references, effects, NodeType.CONTROL_REPEAT,
                baritoneAvailable, uiUtilsAvailable);
            case "wrap_in_condition" -> wrapInControl(graph, command, references, effects, NodeType.CONTROL_IF_DO,
                baritoneAvailable, uiUtilsAvailable);
            case "create_branch" -> createBranch(graph, command, references, effects, baritoneAvailable, uiUtilsAvailable);
            case "declare_variable" -> declareVariable(graph, command, references, effects, baritoneAvailable, uiUtilsAvailable);
            case "declare_list" -> declareList(graph, command, references, effects, baritoneAvailable, uiUtilsAvailable);
            case "clone_subgraph" -> cloneSubgraph(graph, command, references, effects);
            case "replace_subgraph" -> replaceSubgraph(graph, command, references, effects);
            case "create_routine" -> createRoutine(graph, command, references, effects);
            case "add_routine_call" -> addRoutineCall(graph, command, references, effects);
            case "auto_layout" -> autoLayout(graph, effects);
            default -> throw new IllegalArgumentException("Unknown graph command '" + kind + "'.");
        }
    }

    private static void addNode(NodeGraphData graph, JsonObject command, Map<String, String> references,
                                JsonArray effects, boolean baritoneAvailable, boolean uiUtilsAvailable) {
        String reference = requiredReference(command, "ref");
        if (references.containsKey(reference) || findNode(graph, reference) != null) {
            throw new IllegalArgumentException("Node reference '" + reference + "' is already in use.");
        }
        NodeType type = enumValue(command, "nodeType", NodeType.class);
        if (!NodeCatalog.hasDefinition(type)
            || !NodeCatalog.shouldDisplayInSidebar(type, baritoneAvailable, uiUtilsAvailable)) {
            throw new IllegalArgumentException("Node type " + type + " is unavailable in this installation.");
        }
        int[] position = nextPosition(graph);
        Node runtimeNode = Node.createForEditor(type, position[0], position[1]);
        NodeGraphData canonical = NodeGraphPersistence.createGraphData(List.of(runtimeNode), List.of());
        NodeGraphData.NodeData node = canonical.getNodes().get(0);
        graph.getNodes().add(node);
        references.put(reference, node.getId());
        effects.add("Added " + type + " as '" + reference + "' (id " + node.getId() + ").");
    }

    private static void setMode(NodeGraphData graph, JsonObject command, Map<String, String> references, JsonArray effects) {
        NodeGraphData.NodeData node = resolveNode(graph, references, requiredReference(command, "ref"));
        NodeMode mode = enumValue(command, "mode", NodeMode.class);
        if (Arrays.stream(NodeMode.getModesForNodeType(node.getType())).noneMatch(candidate -> candidate == mode)) {
            throw new IllegalArgumentException("Mode " + mode + " is not valid for " + node.getType() + ".");
        }
        Node runtimeNode = Node.createForEditor(node.getType(), node.getX(), node.getY());
        runtimeNode.setMode(mode);
        NodeGraphData.NodeData canonical = NodeGraphPersistence.createGraphData(List.of(runtimeNode), List.of()).getNodes().get(0);
        node.setMode(mode);
        node.setParameters(new ArrayList<>(canonical.getParameters()));
        effects.add("Set " + displayRef(command, "ref", node) + " mode to " + mode + ".");
    }

    private static void setParameter(NodeGraphData graph, JsonObject command, Map<String, String> references, JsonArray effects) {
        NodeGraphData.NodeData node = resolveNode(graph, references, requiredReference(command, "ref"));
        String parameterId = requiredString(command, "parameterId");
        setValidatedParameter(graph, node, parameterId, parameterValue(command));
        effects.add("Set " + displayRef(command, "ref", node) + "." + NodeParameter.createDefaultId(parameterId) + ".");
    }

    private static void setParameters(NodeGraphData graph, JsonObject command, Map<String, String> references,
                                      JsonArray effects) {
        NodeGraphData.NodeData node = resolveNode(graph, references, requiredReference(command, "ref"));
        JsonArray values = requiredArray(command, "parameterValues");
        if (values.isEmpty() || values.size() > 16) {
            throw new IllegalArgumentException("set_parameters requires 1 to 16 parameterValues.");
        }
        Map<String, String> requested = new LinkedHashMap<>();
        for (JsonElement element : values) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Every parameterValues entry must be an object.");
            JsonObject entry = element.getAsJsonObject();
            String parameterId = requiredString(entry, "parameterId");
            String normalized = NodeParameter.createDefaultId(parameterId);
            if (requested.putIfAbsent(normalized, parameterValue(entry)) != null) {
                throw new IllegalArgumentException("Parameter '" + parameterId + "' was supplied more than once.");
            }
        }
        List<PendingParameter> checked = new ArrayList<>();
        requested.forEach((parameterId, value) -> checked.add(validateParameter(graph, node, parameterId, value)));
        for (PendingParameter pending : checked) {
            pending.parameter().setValue(pending.value());
            pending.parameter().setUserEdited(true);
        }
        effects.add("Set " + checked.size() + " validated parameter(s) on " + displayRef(command, "ref", node) + ".");
    }

    private static void configureNode(NodeGraphData graph, JsonObject command, Map<String, String> references,
                                      JsonArray effects) {
        if (nullableString(command, "mode") != null) {
            NodeGraphData.NodeData node = resolveNode(graph, references, requiredReference(command, "ref"));
            if (!requiredString(command, "mode").equals(node.getMode() == null ? "" : node.getMode().name())) {
                List<NodeGraphData.ParameterData> previous = new ArrayList<>(safeParameters(node));
                setMode(graph, command, references, effects);
                for (NodeGraphData.ParameterData parameter : safeParameters(node)) {
                    previous.stream().filter(old -> old != null && parameter.getId().equals(old.getId())
                        && parameter.getType().equals(old.getType())).findFirst().ifPresent(old -> {
                            parameter.setValue(old.getValue());
                            parameter.setUserEdited(old.getUserEdited());
                        });
                }
            }
        }
        boolean scoped = applyMessageScope(graph, command, references, effects);
        if (!optionalArray(command, "parameterValues").isEmpty()) setParameters(graph, command, references, effects);
        else if (nullableString(command, "mode") == null && !scoped) {
            throw new IllegalArgumentException("configure_node needs a mode, parameterValues, or messageClientSide.");
        }
    }

    /**
     * Applies the MESSAGE client/server scope. It rides in the serialized graph rather than in the
     * parameter list, so it has no instance parameter to set and needs its own command field.
     */
    private static boolean applyMessageScope(NodeGraphData graph, JsonObject command, Map<String, String> references,
                                             JsonArray effects) {
        if (!command.has("messageClientSide") || command.get("messageClientSide").isJsonNull()) return false;
        NodeGraphData.NodeData node = resolveNode(graph, references, requiredReference(command, "ref"));
        if (node.getType() != NodeType.MESSAGE) {
            throw new CommandFailure("unsupported_node_field", true,
                "messageClientSide applies to MESSAGE, not " + node.getType() + ".");
        }
        boolean clientSide = command.get("messageClientSide").getAsBoolean();
        node.setMessageClientSide(clientSide);
        effects.add("Set " + displayRef(command, "ref", node) + " to send " + (clientSide ? "client-side" : "to the server") + ".");
        return true;
    }

    private static void setValidatedParameter(NodeGraphData graph, NodeGraphData.NodeData node, String parameterId, String value) {
        PendingParameter checked = validateParameter(graph, node, parameterId, value);
        checked.parameter().setValue(checked.value());
        checked.parameter().setUserEdited(true);
    }

    private static PendingParameter validateParameter(NodeGraphData graph, NodeGraphData.NodeData node, String parameterId, String value) {
        String normalized = NodeParameter.createDefaultId(parameterId);
        NodeGraphData.ParameterData parameter = safeParameters(node).stream().filter(candidate -> candidate != null
            && normalized.equals(AiConfiguredValues.parameterId(candidate)))
            .findFirst().orElseThrow(() -> new CommandFailure("unknown_instance_parameter", true,
                "Node " + node.getId() + " (" + node.getType() + ") has no stored parameter '" + parameterId
                + "'. Inspect its instance parameters and parameterAttachments, not just its type contract. "
                + "Configure the attached value source by its own ref when it supplies this input."));
        AiConfiguredValues.Value effective = AiConfiguredValues.read(graph, node, normalized);
        if (!node.getId().equals(effective.sourceNodeId())) {
            throw new CommandFailure("parameter_overridden", true, "Parameter '" + normalized + "' on "
                + node.getId() + " is supplied by attached node " + effective.sourceNodeId()
                + ". Configure that node by its own ref; changing the host literal would not change execution.");
        }
        try {
            AiParameterValidator.CheckedValue checked = AiParameterValidator.validate(
                node.getType(), node.getMode(), normalized, value);
            return new PendingParameter(parameter, checked.value());
        } catch (IllegalArgumentException exception) {
            throw new CommandFailure("invalid_parameter_value", true, exception.getMessage());
        }
    }

    private static void connect(NodeGraphData graph, JsonObject command, Map<String, String> references, JsonArray effects) {
        NodeGraphData.NodeData from = resolveNode(graph, references, requiredReference(command, "from"));
        NodeGraphData.NodeData to = resolveNode(graph, references, requiredReference(command, "to"));
        int outputSocket = requiredInteger(command, "outputSocket");
        int inputSocket = requiredInteger(command, "inputSocket");
        if (from.getId().equals(to.getId())) throw new IllegalArgumentException("A node cannot connect to itself.");
        Map<String, Node> runtime = runtimeNodes(graph);
        Node output = runtime.get(from.getId());
        Node input = runtime.get(to.getId());
        if (output == null || input == null) throw new IllegalArgumentException("The connection endpoints could not be constructed.");
        if (output.isSensorNode() || input.isSensorNode() || output.isParameterNode() || input.isParameterNode()) {
            String guidance = output.isSensorNode() || input.isSensorNode()
                ? "Use attach_sensor with the control host and sensor child."
                : "Use attach_parameter with the non-parameter host, parameter child, and the host contract's slotIndex.";
            throw new CommandFailure("wrong_connection_kind", true, "Cannot connect " + from.getType() + " to "
                + to.getType() + " as control flow. " + guidance);
        }
        if (outputSocket < 0 || outputSocket >= output.getOutputSocketCount()) {
            throw new IllegalArgumentException("Output socket " + outputSocket + " is invalid for " + from.getType() + ".");
        }
        if (inputSocket < 0 || inputSocket >= input.getInputSocketCount()) {
            throw new IllegalArgumentException("Input socket " + inputSocket + " is invalid for " + to.getType() + ".");
        }
        for (NodeGraphData.ConnectionData existing : graph.getConnections()) {
            if (existing == null) continue;
            if (from.getId().equals(existing.getOutputNodeId()) && outputSocket == existing.getOutputSocket()) {
                NodeGraphData.NodeData current = findNode(graph, existing.getInputNodeId());
                String destination = current == null ? "node '" + existing.getInputNodeId() + "'"
                    : current.getType() + " '" + referenceFor(current, references) + "'";
                throw new CommandFailure("occupied_output", true, "Output socket " + outputSocket + " on "
                    + displayRef(command, "from", from) + " is already connected to " + destination + " input socket "
                    + existing.getInputSocket() + ". Use insert_sequence_after to preserve that downstream connection, "
                    + "or disconnect it explicitly before rewiring.");
            }
            if (to.getId().equals(existing.getInputNodeId()) && inputSocket == existing.getInputSocket()) {
                NodeGraphData.NodeData current = findNode(graph, existing.getOutputNodeId());
                String source = current == null ? "node '" + existing.getOutputNodeId() + "'"
                    : current.getType() + " '" + referenceFor(current, references) + "'";
                throw new CommandFailure("occupied_input", true, "Input socket " + inputSocket + " on "
                    + displayRef(command, "to", to) + " is already connected from " + source + " output socket "
                    + existing.getOutputSocket() + ". Disconnect the existing edge explicitly before rewiring.");
            }
        }
        graph.getConnections().add(new NodeGraphData.ConnectionData(from.getId(), to.getId(), outputSocket, inputSocket));
        effects.add("Connected " + displayRef(command, "from", from) + "[" + outputSocket + "] to " + displayRef(command, "to", to) + "[" + inputSocket + "].");
    }

    private static void disconnect(NodeGraphData graph, JsonObject command, Map<String, String> references, JsonArray effects) {
        NodeGraphData.NodeData from = resolveNode(graph, references, requiredReference(command, "from"));
        NodeGraphData.NodeData to = resolveNode(graph, references, requiredReference(command, "to"));
        int outputSocket = requiredInteger(command, "outputSocket");
        int inputSocket = requiredInteger(command, "inputSocket");
        boolean removed = graph.getConnections().removeIf(edge -> edge != null
            && from.getId().equals(edge.getOutputNodeId()) && to.getId().equals(edge.getInputNodeId())
            && outputSocket == edge.getOutputSocket() && inputSocket == edge.getInputSocket());
        if (!removed) throw new IllegalArgumentException("The requested connection does not exist.");
        effects.add("Disconnected " + displayRef(command, "from", from) + "[" + outputSocket + "] from "
            + displayRef(command, "to", to) + "[" + inputSocket + "].");
    }

    private static void attach(NodeGraphData graph, JsonObject command, Map<String, String> references,
                               JsonArray effects, NodeSlotType slotType) {
        NodeGraphData.NodeData host = resolveNode(graph, references, requiredReference(command, "host"));
        NodeGraphData.NodeData child = resolveNode(graph, references, requiredReference(command, "child"));
        int slot = slotType == NodeSlotType.PARAMETER ? requiredInteger(command, "slotIndex") : 0;
        Map<String, Node> runtime = runtimeNodes(graph);
        Node runtimeHost = runtime.get(host.getId());
        Node runtimeChild = runtime.get(child.getId());
        if (!NodeCompatibility.canAttachToSlot(runtimeHost, runtimeChild, slotType, slot)) {
            throw new IllegalArgumentException(child.getType() + " cannot attach to " + host.getType() + " as " + slotType.name().toLowerCase() + (slotType == NodeSlotType.PARAMETER ? " slot " + slot : "") + ".");
        }
        if (slotType == NodeSlotType.SENSOR) {
            requireEmpty(host.getAttachedSensorId(), child.getId(), "sensor slot");
            requireEmpty(child.getParentControlId(), host.getId(), "sensor parent");
            host.setAttachedSensorId(child.getId());
            child.setParentControlId(host.getId());
        } else if (slotType == NodeSlotType.ACTION) {
            requireEmpty(host.getAttachedActionId(), child.getId(), "action slot");
            requireEmpty(child.getParentActionControlId(), host.getId(), "action parent");
            host.setAttachedActionId(child.getId());
            child.setParentActionControlId(host.getId());
        } else {
            requireEmpty(child.getParentParameterHostId(), host.getId(), "parameter parent");
            List<NodeGraphData.ParameterAttachmentData> attachments = safeAttachments(host);
            for (NodeGraphData.ParameterAttachmentData attachment : attachments) {
                if (attachment != null && attachment.getSlotIndex() == slot
                    && !child.getId().equals(attachment.getParameterNodeId())) {
                    throw new IllegalArgumentException("Parameter slot " + slot + " on " + displayRef(command, "host", host) + " is already occupied.");
                }
            }
            boolean exists = attachments.stream().anyMatch(attachment -> attachment != null
                && attachment.getSlotIndex() == slot && child.getId().equals(attachment.getParameterNodeId()));
            if (!exists) attachments.add(new NodeGraphData.ParameterAttachmentData(slot, child.getId()));
            host.setAttachedParameterId(attachments.isEmpty() ? null : attachments.get(0).getParameterNodeId());
            child.setParentParameterHostId(host.getId());
        }
        effects.add("Attached " + displayRef(command, "child", child) + " to " + displayRef(command, "host", host)
            + " as " + slotType.name().toLowerCase() + (slotType == NodeSlotType.PARAMETER ? "[" + slot + "]" : "") + ".");
    }

    private static void detach(NodeGraphData graph, JsonObject command, Map<String, String> references,
                               JsonArray effects, NodeSlotType slotType) {
        NodeGraphData.NodeData host = resolveNode(graph, references, requiredReference(command, "host"));
        String childId;
        if (slotType == NodeSlotType.SENSOR) {
            childId = host.getAttachedSensorId();
            if (childId == null || childId.isBlank()) throw new IllegalArgumentException("The sensor slot is already empty.");
            NodeGraphData.NodeData child = findNode(graph, childId);
            host.setAttachedSensorId(null);
            if (child != null && host.getId().equals(child.getParentControlId())) child.setParentControlId(null);
        } else if (slotType == NodeSlotType.ACTION) {
            childId = host.getAttachedActionId();
            if (childId == null || childId.isBlank()) throw new IllegalArgumentException("The action slot is already empty.");
            NodeGraphData.NodeData child = findNode(graph, childId);
            host.setAttachedActionId(null);
            if (child != null && host.getId().equals(child.getParentActionControlId())) child.setParentActionControlId(null);
        } else {
            int slot = requiredInteger(command, "slotIndex");
            NodeGraphData.ParameterAttachmentData attachment = safeAttachments(host).stream()
                .filter(item -> item != null && item.getSlotIndex() == slot).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Parameter slot " + slot + " is already empty."));
            childId = attachment.getParameterNodeId();
            safeAttachments(host).remove(attachment);
            host.setAttachedParameterId(safeAttachments(host).isEmpty() ? null
                : safeAttachments(host).get(0).getParameterNodeId());
            NodeGraphData.NodeData child = findNode(graph, childId);
            if (child != null && host.getId().equals(child.getParentParameterHostId())) child.setParentParameterHostId(null);
        }
        effects.add("Detached " + slotType.name().toLowerCase() + " node '" + childId + "' from "
            + displayRef(command, "host", host) + ".");
    }

    private static void removeNode(NodeGraphData graph, JsonObject command, Map<String, String> references, JsonArray effects) {
        NodeGraphData.NodeData removed = resolveNode(graph, references, requiredReference(command, "ref"));
        String id = removed.getId();
        graph.getNodes().removeIf(node -> node != null && id.equals(node.getId()));
        graph.getConnections().removeIf(connection -> connection != null
            && (id.equals(connection.getOutputNodeId()) || id.equals(connection.getInputNodeId())));
        for (NodeGraphData.NodeData node : graph.getNodes()) {
            if (node == null) continue;
            if (id.equals(node.getAttachedSensorId())) node.setAttachedSensorId(null);
            if (id.equals(node.getParentControlId())) node.setParentControlId(null);
            if (id.equals(node.getAttachedActionId())) node.setAttachedActionId(null);
            if (id.equals(node.getParentActionControlId())) node.setParentActionControlId(null);
            if (id.equals(node.getParentParameterHostId())) node.setParentParameterHostId(null);
            List<NodeGraphData.ParameterAttachmentData> attachments = safeAttachments(node);
            attachments.removeIf(attachment -> attachment != null && id.equals(attachment.getParameterNodeId()));
            node.setAttachedParameterId(attachments.isEmpty() ? null : attachments.get(0).getParameterNodeId());
        }
        references.entrySet().removeIf(entry -> id.equals(entry.getValue()));
        effects.add("Removed " + removed.getType() + " (id " + id + ") and its relationships.");
    }

    private static void addSequence(NodeGraphData graph, JsonObject command, Map<String, String> references,
                                    JsonArray effects, boolean baritoneAvailable, boolean uiUtilsAvailable) {
        List<String> refs = requiredReferences(command, "refs");
        JsonArray types = requiredArray(command, "nodeTypes");
        if (refs.size() != types.size()) throw new IllegalArgumentException("add_sequence requires one ref per nodeType.");
        if (refs.isEmpty()) throw new IllegalArgumentException("add_sequence requires at least one node.");
        for (int index = 0; index < refs.size(); index++) {
            JsonObject add = command("add_node", "ref", refs.get(index), "nodeType", types.get(index).getAsString());
            addNode(graph, add, references, effects, baritoneAvailable, uiUtilsAvailable);
            if (index > 0) connect(graph, command("connect", "from", refs.get(index - 1), "to", refs.get(index),
                "outputSocket", 0, "inputSocket", 0), references, effects);
        }
        effects.add("Built a connected sequence of " + refs.size() + " nodes.");
    }

    private static void insertSequenceAfter(NodeGraphData graph, JsonObject command, Map<String, String> references,
                                            JsonArray effects, boolean baritoneAvailable, boolean uiUtilsAvailable) {
        String anchorRef = requiredReference(command, "ref");
        NodeGraphData.NodeData anchor = resolveNode(graph, references, anchorRef);
        int outputSocket = requiredInteger(command, "outputSocket");
        NodeGraphData.ConnectionData downstream = null;
        for (NodeGraphData.ConnectionData edge : graph.getConnections()) {
            if (edge == null || !anchor.getId().equals(edge.getOutputNodeId()) || outputSocket != edge.getOutputSocket()) continue;
            if (downstream != null) {
                throw new CommandFailure("ambiguous_splice", true, "The selected output has multiple downstream edges. "
                    + "Inspect the subgraph and rewire those edges explicitly.");
            }
            downstream = edge;
        }

        if (downstream != null) graph.getConnections().remove(downstream);
        addSequence(graph, command, references, effects, baritoneAvailable, uiUtilsAvailable);
        List<String> refs = requiredReferences(command, "refs");
        connect(graph, command("connect", "from", anchorRef, "to", refs.get(0),
            "outputSocket", outputSocket, "inputSocket", 0), references, effects);
        if (downstream != null) {
            NodeGraphData.NodeData successor = findNode(graph, downstream.getInputNodeId());
            if (successor == null) throw new IllegalArgumentException("The downstream splice target no longer exists.");
            connect(graph, command("connect", "from", refs.get(refs.size() - 1),
                "to", referenceFor(successor, references), "outputSocket", 0,
                "inputSocket", downstream.getInputSocket()), references, effects);
        }
        effects.add("Inserted " + refs.size() + " node(s) after '" + anchorRef
            + "' while preserving its previous downstream connection.");
    }

    private static void wrapInControl(NodeGraphData graph, JsonObject command, Map<String, String> references,
                                      JsonArray effects, NodeType controlType,
                                      boolean baritoneAvailable, boolean uiUtilsAvailable) {
        List<NodeGraphData.NodeData> selected = resolveNodes(graph, references, requiredReferences(command, "refs"));
        Boundary boundary = boundary(graph, selected);
        String wrapperRef = requiredReference(command, "ref");
        JsonObject add = command("add_node", "ref", wrapperRef, "nodeType", controlType.name());
        addNode(graph, add, references, effects, baritoneAvailable, uiUtilsAvailable);
        NodeGraphData.NodeData wrapper = resolveNode(graph, references, wrapperRef);
        wrapper.setX(boundary.first().getX());
        wrapper.setY(boundary.first().getY());
        if (controlType == NodeType.CONTROL_REPEAT) {
            int count = requiredInteger(command, "count");
            if (count < 1) throw new IllegalArgumentException("Repeat count must be at least 1.");
            setParameter(graph, command("set_parameter", "ref", wrapperRef, "parameterId", "count",
                "value", Integer.toString(count)), references, effects);
        } else {
            attach(graph, command("attach_sensor", "host", wrapperRef, "child",
                requiredReference(command, "sensor")), references, effects, NodeSlotType.SENSOR);
        }
        detachBoundary(graph, boundary);
        attach(graph, command("attach_action", "host", wrapperRef, "child", referenceFor(boundary.first(), references)),
            references, effects, NodeSlotType.ACTION);
        if (boundary.incoming() != null) graph.getConnections().add(new NodeGraphData.ConnectionData(
            boundary.incoming().getOutputNodeId(), wrapper.getId(), boundary.incoming().getOutputSocket(), 0));
        if (boundary.outgoing() != null) graph.getConnections().add(new NodeGraphData.ConnectionData(
            wrapper.getId(), boundary.outgoing().getInputNodeId(), 0, boundary.outgoing().getInputSocket()));
        effects.add("Wrapped " + selected.size() + " node(s) in " + controlType + ".");
    }

    private static void createBranch(NodeGraphData graph, JsonObject command, Map<String, String> references,
                                     JsonArray effects, boolean baritoneAvailable, boolean uiUtilsAvailable) {
        String branchRef = requiredReference(command, "ref");
        List<NodeGraphData.NodeData> trueNodes = resolveNodes(graph, references, requiredReferences(command, "trueRefs"));
        List<NodeGraphData.NodeData> falseNodes = resolveNodes(graph, references, requiredReferences(command, "falseRefs"));
        requireSequence(graph, trueNodes, "trueRefs");
        requireSequence(graph, falseNodes, "falseRefs");
        addNode(graph, command("add_node", "ref", branchRef, "nodeType", "CONTROL_IF_ELSE"), references, effects,
            baritoneAvailable, uiUtilsAvailable);
        attach(graph, command("attach_sensor", "host", branchRef, "child", requiredReference(command, "sensor")),
            references, effects, NodeSlotType.SENSOR);
        if (!trueNodes.isEmpty()) connect(graph, command("connect", "from", branchRef, "to",
            referenceFor(trueNodes.get(0), references), "outputSocket", 0, "inputSocket", 0), references, effects);
        if (!falseNodes.isEmpty()) connect(graph, command("connect", "from", branchRef, "to",
            referenceFor(falseNodes.get(0), references), "outputSocket", 1, "inputSocket", 0), references, effects);
        effects.add("Created a true/false branch with " + trueNodes.size() + " and " + falseNodes.size() + " node(s).");
    }

    private static void declareVariable(NodeGraphData graph, JsonObject command, Map<String, String> references,
                                        JsonArray effects, boolean baritoneAvailable, boolean uiUtilsAvailable) {
        String setterRef = requiredReference(command, "ref");
        String variableRef = requiredReference(command, "variableRef");
        addNode(graph, command("add_node", "ref", setterRef, "nodeType", "SET_VARIABLE"), references, effects,
            baritoneAvailable, uiUtilsAvailable);
        addNode(graph, command("add_node", "ref", variableRef, "nodeType", "VARIABLE"), references, effects,
            baritoneAvailable, uiUtilsAvailable);
        setParameter(graph, command("set_parameter", "ref", variableRef, "parameterId", "variable",
            "value", requiredString(command, "name")), references, effects);
        attach(graph, command("attach_parameter", "host", setterRef, "child", variableRef, "slotIndex", 0),
            references, effects, NodeSlotType.PARAMETER);
        String valueRef = nullableString(command, "child");
        if (valueRef != null && !valueRef.isBlank()) attach(graph, command("attach_parameter", "host", setterRef,
            "child", valueRef, "slotIndex", 1), references, effects, NodeSlotType.PARAMETER);
        effects.add("Declared variable '" + requiredString(command, "name") + "'.");
    }

    private static void declareList(NodeGraphData graph, JsonObject command, Map<String, String> references,
                                    JsonArray effects, boolean baritoneAvailable, boolean uiUtilsAvailable) {
        String ref = requiredReference(command, "ref");
        addNode(graph, command("add_node", "ref", ref, "nodeType", "CREATE_LIST"), references, effects,
            baritoneAvailable, uiUtilsAvailable);
        setParameter(graph, command("set_parameter", "ref", ref, "parameterId", "list",
            "value", requiredString(command, "name")), references, effects);
        String initialValue = nullableString(command, "child");
        if (initialValue != null && !initialValue.isBlank()) attach(graph, command("attach_parameter", "host", ref,
            "child", initialValue, "slotIndex", 0), references, effects, NodeSlotType.PARAMETER);
        effects.add("Declared list '" + requiredString(command, "name") + "'.");
    }

    private static void cloneSubgraph(NodeGraphData graph, JsonObject command, Map<String, String> references,
                                      JsonArray effects) {
        List<String> sourceRefs = requiredReferences(command, "refs");
        List<String> targetRefs = requiredReferences(command, "newRefs");
        if (sourceRefs.size() != targetRefs.size() || sourceRefs.isEmpty()) {
            throw new IllegalArgumentException("clone_subgraph requires equally sized, non-empty refs and newRefs.");
        }
        List<NodeGraphData.NodeData> requestedSources = resolveNodes(graph, references, sourceRefs);
        Set<String> sourceIds = attachmentClosure(graph, requestedSources.stream().map(NodeGraphData.NodeData::getId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        List<NodeGraphData.NodeData> sources = graph.getNodes().stream().filter(java.util.Objects::nonNull)
            .filter(node -> sourceIds.contains(node.getId())).toList();
        Map<String, String> replacements = new LinkedHashMap<>();
        List<NodeGraphData.NodeData> clones = new ArrayList<>();
        for (NodeGraphData.NodeData source : sources) replacements.put(source.getId(), java.util.UUID.randomUUID().toString());
        for (NodeGraphData.NodeData source : sources) {
            NodeGraphData.NodeData clone = GSON.fromJson(GSON.toJsonTree(source), NodeGraphData.NodeData.class);
            String newId = replacements.get(source.getId());
            clone.setId(newId);
            clone.setX(clone.getX() + 80);
            clone.setY(clone.getY() + 120);
            clones.add(clone);
        }
        for (int index = 0; index < requestedSources.size(); index++) {
            String targetRef = requiredReferenceValue(targetRefs.get(index), "newRefs");
            if (references.containsKey(targetRef)) throw new IllegalArgumentException("Node reference '" + targetRef + "' is already in use.");
            references.put(targetRef, replacements.get(requestedSources.get(index).getId()));
        }
        for (NodeGraphData.NodeData clone : clones) remapRelationships(clone, replacements);
        graph.getNodes().addAll(clones);
        List<NodeGraphData.ConnectionData> clonedEdges = new ArrayList<>();
        for (NodeGraphData.ConnectionData edge : graph.getConnections()) {
            if (edge != null && sourceIds.contains(edge.getOutputNodeId()) && sourceIds.contains(edge.getInputNodeId())) {
                clonedEdges.add(new NodeGraphData.ConnectionData(replacements.get(edge.getOutputNodeId()),
                    replacements.get(edge.getInputNodeId()), edge.getOutputSocket(), edge.getInputSocket()));
            }
        }
        graph.getConnections().addAll(clonedEdges);
        effects.add("Cloned a " + clones.size() + "-node subgraph with new identities.");
    }

    private static void replaceSubgraph(NodeGraphData graph, JsonObject command, Map<String, String> references,
                                        JsonArray effects) {
        List<NodeGraphData.NodeData> oldNodes = resolveNodes(graph, references, requiredReferences(command, "refs"));
        List<NodeGraphData.NodeData> replacements = resolveNodes(graph, references, requiredReferences(command, "replacementRefs"));
        if (replacements.isEmpty()) throw new IllegalArgumentException("replace_subgraph requires replacementRefs.");
        Set<String> oldFlowIds = oldNodes.stream().map(NodeGraphData.NodeData::getId).collect(java.util.stream.Collectors.toSet());
        Set<String> oldIds = attachmentClosure(graph, oldFlowIds);
        if (replacements.stream().anyMatch(node -> oldIds.contains(node.getId()))) {
            throw new IllegalArgumentException("Replacement nodes must be outside the subgraph being replaced.");
        }
        Boundary oldBoundary = boundary(graph, oldNodes);
        Boundary replacementBoundary = boundary(graph, replacements);
        if (replacementBoundary.incoming() != null || replacementBoundary.outgoing() != null) {
            throw new IllegalArgumentException("replacementRefs must form a disconnected replacement sequence.");
        }
        graph.getConnections().removeIf(edge -> edge != null
            && (oldIds.contains(edge.getOutputNodeId()) || oldIds.contains(edge.getInputNodeId())));
        graph.getNodes().removeIf(node -> node != null && oldIds.contains(node.getId()));
        cleanupRelationships(graph, oldIds);
        references.entrySet().removeIf(entry -> oldIds.contains(entry.getValue()));
        if (oldBoundary.incoming() != null) graph.getConnections().add(new NodeGraphData.ConnectionData(
            oldBoundary.incoming().getOutputNodeId(), replacements.get(0).getId(), oldBoundary.incoming().getOutputSocket(), 0));
        if (oldBoundary.outgoing() != null) graph.getConnections().add(new NodeGraphData.ConnectionData(
            replacements.get(replacements.size() - 1).getId(), oldBoundary.outgoing().getInputNodeId(), 0,
            oldBoundary.outgoing().getInputSocket()));
        effects.add("Replaced " + oldNodes.size() + " node(s) with a " + replacements.size() + "-node subgraph.");
    }

    private static void createRoutine(NodeGraphData graph, JsonObject command, Map<String, String> references,
                                      JsonArray effects) {
        String routineRef = requiredReference(command, "routineRef");
        if (references.containsKey(routineRef)) throw new IllegalArgumentException("Reference '" + routineRef + "' is already in use.");
        List<NodeGraphData.NodeData> selected = resolveNodes(graph, references, requiredReferences(command, "refs"));
        Boundary boundary = boundary(graph, selected);
        NodeGraphData.RoutineDefinitionData routine = RoutineBuilderModel.createRoutine(requiredString(command, "name"));
        RoutineBuilderModel builder = new RoutineBuilderModel(routine);
        JsonArray inputs = optionalArray(command, "routineInputs");
        List<RoutineInputBinding> inputBindings = new ArrayList<>();
        for (JsonElement element : inputs) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Every routine input must be an object.");
            JsonObject input = element.getAsJsonObject();
            var created = builder.addInput(requiredString(input, "label"),
                enumValue(input, "valueKind", RoutineValueKind.class));
            builder.updateInput(created.getId(), requiredString(input, "label"),
                enumValue(input, "valueKind", RoutineValueKind.class), optionalBoolean(input, "required", false),
                nullableString(input, "defaultValue"));
            inputBindings.add(new RoutineInputBinding(created.getId(), nullableString(input, "bindToRef"),
                nullableInteger(input, "slotIndex")));
        }
        builder.ensureDefinitionGraph();
        NodeGraphData routineGraph = routine.getGraph();
        Set<String> selectedIds = attachmentClosure(graph,
            selected.stream().map(NodeGraphData.NodeData::getId).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        for (NodeGraphData.NodeData node : graph.getNodes()) {
            if (node == null || !selectedIds.contains(node.getId())) continue;
            NodeGraphData.NodeData copy = GSON.fromJson(GSON.toJsonTree(node), NodeGraphData.NodeData.class);
            stripExternalRelationships(copy, selectedIds);
            routineGraph.getNodes().add(copy);
        }
        for (NodeGraphData.ConnectionData edge : graph.getConnections()) {
            if (edge != null && selectedIds.contains(edge.getOutputNodeId()) && selectedIds.contains(edge.getInputNodeId())) {
                routineGraph.getConnections().add(new NodeGraphData.ConnectionData(edge.getOutputNodeId(), edge.getInputNodeId(),
                    edge.getOutputSocket(), edge.getInputSocket()));
            }
        }
        for (RoutineInputBinding binding : inputBindings) {
            if (binding.bindToRef() == null || binding.bindToRef().isBlank()) continue;
            if (binding.slotIndex() == null) throw new IllegalArgumentException("A bound routine input requires slotIndex.");
            NodeGraphData.NodeData bodyHost = resolveNode(routineGraph, references, binding.bindToRef());
            if (!selectedIds.contains(bodyHost.getId())) {
                throw new IllegalArgumentException("Routine input bindToRef must name a node in the extracted refs.");
            }
            Node reporter = builder.createInputReporter(binding.inputId(), bodyHost.getX() + 30, bodyHost.getY() + 100);
            NodeGraphData.NodeData reporterData = NodeGraphPersistence.createGraphData(List.of(reporter), List.of()).getNodes().get(0);
            routineGraph.getNodes().add(reporterData);
            attach(routineGraph, command("attach_parameter", "host", binding.bindToRef(), "child", reporterData.getId(),
                "slotIndex", binding.slotIndex()), references, effects, NodeSlotType.PARAMETER);
        }
        NodeGraphData.NodeData entry = routineGraph.getNodes().stream()
            .filter(node -> node != null && node.getType() == NodeType.ROUTINE_ENTRY).findFirst().orElseThrow();
        routineGraph.getConnections().add(new NodeGraphData.ConnectionData(entry.getId(), boundary.first().getId(), 0, 0));
        graph.getRoutines().add(routine);
        NodeGraphPersistence.sanitizeRoutineDefinitions(graph);
        references.put(routineRef, routine.getId());

        String callRef = requiredReference(command, "ref");
        Node call = Node.createRoutineCall(routine, boundary.first().getX(), boundary.first().getY());
        NodeGraphData.NodeData callData = NodeGraphPersistence.createGraphData(List.of(call), List.of()).getNodes().get(0);
        graph.getNodes().add(callData);
        references.put(callRef, callData.getId());
        graph.getConnections().removeIf(edge -> edge != null
            && (selectedIds.contains(edge.getOutputNodeId()) || selectedIds.contains(edge.getInputNodeId())));
        graph.getNodes().removeIf(node -> node != null && selectedIds.contains(node.getId()));
        cleanupRelationships(graph, selectedIds);
        // Body IDs are retained: aliases remain usable with the new routine graphRef.
        if (boundary.incoming() != null) graph.getConnections().add(new NodeGraphData.ConnectionData(
            boundary.incoming().getOutputNodeId(), callData.getId(), boundary.incoming().getOutputSocket(), 0));
        if (boundary.outgoing() != null) graph.getConnections().add(new NodeGraphData.ConnectionData(
            callData.getId(), boundary.outgoing().getInputNodeId(), 0, boundary.outgoing().getInputSocket()));
        effects.add("Extracted " + selected.size() + " node(s) into routine '" + routine.getName() + "' with "
            + routine.getInputs().size() + " typed input(s).");
    }

    private static void addRoutineCall(NodeGraphData graph, JsonObject command, Map<String, String> references,
                                       JsonArray effects) {
        addRoutineCall(graph, graph, command, references, effects);
    }

    private static void addRoutineCall(NodeGraphData graph, NodeGraphData registry, JsonObject command, Map<String, String> references,
                                       JsonArray effects) {
        String routineRef = requiredReference(command, "routineRef");
        String routineId = references.getOrDefault(routineRef, routineRef);
        NodeGraphData.RoutineDefinitionData routine = registry.getRoutines().stream()
            .filter(item -> item != null && routineId.equals(item.getId())).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown routine reference '" + routineRef + "'."));
        String callRef = requiredReference(command, "ref");
        if (references.containsKey(callRef)) throw new IllegalArgumentException("Reference '" + callRef + "' is already in use.");
        int[] position = nextPosition(graph);
        Node call = Node.createRoutineCall(routine, position[0], position[1]);
        NodeGraphData.NodeData data = NodeGraphPersistence.createGraphData(List.of(call), List.of()).getNodes().get(0);
        graph.getNodes().add(data);
        references.put(callRef, data.getId());
        effects.add("Added a call to routine '" + routine.getName() + "' as '" + callRef + "'.");
    }

    private static void autoLayout(NodeGraphData graph, JsonArray effects) {
        Map<String, NodeGraphData.NodeData> byId = new HashMap<>();
        List<NodeGraphData.NodeData> flow = new ArrayList<>();
        Map<String, Node> runtime = runtimeNodes(graph);
        for (NodeGraphData.NodeData node : graph.getNodes()) {
            if (node == null) continue;
            byId.put(node.getId(), node);
            Node specimen = runtime.get(node.getId());
            if (specimen != null && !specimen.isSensorNode() && !specimen.isParameterNode() && !specimen.isStickyNote()) {
                flow.add(node);
            }
        }
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<NodeGraphData.ConnectionData>> outgoing = new HashMap<>();
        flow.forEach(node -> indegree.put(node.getId(), 0));
        for (NodeGraphData.ConnectionData edge : graph.getConnections()) {
            if (edge == null || !indegree.containsKey(edge.getOutputNodeId()) || !indegree.containsKey(edge.getInputNodeId())) continue;
            indegree.merge(edge.getInputNodeId(), 1, Integer::sum);
            outgoing.computeIfAbsent(edge.getOutputNodeId(), ignored -> new ArrayList<>()).add(edge);
        }
        for (NodeGraphData.NodeData host : flow) {
            if (host.getAttachedActionId() == null || !indegree.containsKey(host.getAttachedActionId())) continue;
            NodeGraphData.ConnectionData virtual = new NodeGraphData.ConnectionData(host.getId(), host.getAttachedActionId(), 0, 0);
            indegree.merge(host.getAttachedActionId(), 1, Integer::sum);
            outgoing.computeIfAbsent(host.getId(), ignored -> new ArrayList<>()).add(virtual);
        }
        Comparator<NodeGraphData.NodeData> order = Comparator
            .comparingInt((NodeGraphData.NodeData node) -> node.getType() == NodeType.START || node.getType() == NodeType.ROUTINE_ENTRY ? 0 : 1)
            .thenComparing(node -> node.getType().name()).thenComparing(NodeGraphData.NodeData::getId);
        java.util.PriorityQueue<NodeGraphData.NodeData> queue = new java.util.PriorityQueue<>(order);
        flow.stream().filter(node -> indegree.get(node.getId()) == 0).forEach(queue::add);
        Map<String, Integer> rank = new HashMap<>();
        List<NodeGraphData.NodeData> ordered = new ArrayList<>();
        while (!queue.isEmpty()) {
            NodeGraphData.NodeData node = queue.remove();
            ordered.add(node);
            for (NodeGraphData.ConnectionData edge : outgoing.getOrDefault(node.getId(), List.of())) {
                rank.merge(edge.getInputNodeId(), rank.getOrDefault(node.getId(), 0) + 1, Math::max);
                if (indegree.merge(edge.getInputNodeId(), -1, Integer::sum) == 0) queue.add(byId.get(edge.getInputNodeId()));
            }
        }
        flow.stream().filter(node -> !ordered.contains(node)).sorted(order).forEach(ordered::add);
        Map<Integer, Integer> lanes = new HashMap<>();
        for (NodeGraphData.NodeData node : ordered) {
            int column = rank.getOrDefault(node.getId(), 0);
            int lane = lanes.merge(column, 1, Integer::sum) - 1;
            node.setX(60 + column * 230);
            node.setY(70 + lane * 170);
            layoutAttachments(node, byId);
        }
        for (NodeGraphData.RoutineDefinitionData routine : graph.getRoutines()) {
            if (routine != null && routine.getGraph() != null) autoLayout(routine.getGraph(), new JsonArray());
        }
        effects.add("Applied deterministic topology-aware layout to " + graph.getNodes().size() + " node(s).");
    }

    private static Map<String, Node> runtimeNodes(NodeGraphData graph) {
        Map<String, Node> result = new LinkedHashMap<>();
        for (Node node : NodeGraphPersistence.convertToNodes(graph)) if (node != null) result.put(node.getId(), node);
        return result;
    }

    private static NodeGraphData.NodeData resolveNode(NodeGraphData graph, Map<String, String> references, String reference) {
        String id = references.getOrDefault(reference, reference);
        NodeGraphData.NodeData node = findNode(graph, id);
        if (node == null) throw new IllegalArgumentException("Unknown node reference '" + reference + "'. Use a created ref or an id returned by inspect_preset.");
        return node;
    }

    private static NodeGraphData.NodeData findNode(NodeGraphData graph, String id) {
        if (graph == null || graph.getNodes() == null || id == null) return null;
        for (NodeGraphData.NodeData node : graph.getNodes()) if (node != null && id.equals(node.getId())) return node;
        return null;
    }

    private static void removeStaleReferences(NodeGraphData graph, Map<String, String> references) {
        Set<String> routineIds = new HashSet<>();
        for (NodeGraphData.RoutineDefinitionData routine : graph.getRoutines()) {
            if (routine != null && routine.getId() != null) routineIds.add(routine.getId());
        }
        references.entrySet().removeIf(entry -> AiGraphScope.containing(graph, entry.getValue()) == null
            && !routineIds.contains(entry.getValue()));
    }

    private static List<NodeGraphData.NodeData> resolveNodes(NodeGraphData graph, Map<String, String> references,
                                                             List<String> requested) {
        List<NodeGraphData.NodeData> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String reference : requested) {
            NodeGraphData.NodeData node = resolveNode(graph, references, reference);
            if (!seen.add(node.getId())) throw new IllegalArgumentException("Node references must not contain duplicates.");
            result.add(node);
        }
        return result;
    }

    private static Boundary boundary(NodeGraphData graph, List<NodeGraphData.NodeData> selected) {
        if (selected.isEmpty()) throw new IllegalArgumentException("The subgraph selection cannot be empty.");
        requireSequence(graph, selected, "refs");
        Set<String> ids = selected.stream().map(NodeGraphData.NodeData::getId).collect(java.util.stream.Collectors.toSet());
        List<NodeGraphData.ConnectionData> incoming = new ArrayList<>();
        List<NodeGraphData.ConnectionData> outgoing = new ArrayList<>();
        for (NodeGraphData.ConnectionData edge : graph.getConnections()) {
            if (edge == null) continue;
            if (!ids.contains(edge.getOutputNodeId()) && ids.contains(edge.getInputNodeId())) incoming.add(edge);
            if (ids.contains(edge.getOutputNodeId()) && !ids.contains(edge.getInputNodeId())) outgoing.add(edge);
        }
        if (incoming.size() > 1 || outgoing.size() > 1) {
            throw new IllegalArgumentException("Composition requires a subgraph with at most one external entry and exit.");
        }
        return new Boundary(selected.get(0), selected.get(selected.size() - 1),
            incoming.isEmpty() ? null : incoming.get(0), outgoing.isEmpty() ? null : outgoing.get(0));
    }

    private static void requireSequence(NodeGraphData graph, List<NodeGraphData.NodeData> nodes, String field) {
        if (nodes.isEmpty()) return;
        Map<String, Node> runtime = runtimeNodes(graph);
        for (NodeGraphData.NodeData node : nodes) {
            Node value = runtime.get(node.getId());
            if (value == null || value.isSensorNode() || value.isParameterNode() || value.isStickyNote()) {
                throw new IllegalArgumentException(field + " must contain control-flow nodes.");
            }
        }
        for (int index = 1; index < nodes.size(); index++) {
            String previous = nodes.get(index - 1).getId();
            String current = nodes.get(index).getId();
            boolean connected = graph.getConnections().stream().anyMatch(edge -> edge != null
                && previous.equals(edge.getOutputNodeId()) && current.equals(edge.getInputNodeId()));
            if (!connected) throw new IllegalArgumentException(field + " must be ordered as one connected sequence.");
        }
    }

    private static void detachBoundary(NodeGraphData graph, Boundary boundary) {
        graph.getConnections().removeIf(edge -> edge == boundary.incoming() || edge == boundary.outgoing());
    }

    private static void remapRelationships(NodeGraphData.NodeData node, Map<String, String> replacements) {
        node.setAttachedSensorId(replacements.get(node.getAttachedSensorId()));
        node.setParentControlId(replacements.get(node.getParentControlId()));
        node.setAttachedActionId(replacements.get(node.getAttachedActionId()));
        node.setParentActionControlId(replacements.get(node.getParentActionControlId()));
        node.setParentParameterHostId(replacements.get(node.getParentParameterHostId()));
        List<NodeGraphData.ParameterAttachmentData> attachments = safeAttachments(node);
        attachments.removeIf(attachment -> attachment == null || !replacements.containsKey(attachment.getParameterNodeId()));
        for (NodeGraphData.ParameterAttachmentData attachment : attachments) {
            attachment.setParameterNodeId(replacements.get(attachment.getParameterNodeId()));
        }
        node.setAttachedParameterId(attachments.isEmpty() ? null : attachments.get(0).getParameterNodeId());
    }

    private static void stripExternalRelationships(NodeGraphData.NodeData node, Set<String> selectedIds) {
        if (!selectedIds.contains(node.getAttachedSensorId())) node.setAttachedSensorId(null);
        if (!selectedIds.contains(node.getParentControlId())) node.setParentControlId(null);
        if (!selectedIds.contains(node.getAttachedActionId())) node.setAttachedActionId(null);
        if (!selectedIds.contains(node.getParentActionControlId())) node.setParentActionControlId(null);
        if (!selectedIds.contains(node.getParentParameterHostId())) node.setParentParameterHostId(null);
        List<NodeGraphData.ParameterAttachmentData> attachments = safeAttachments(node);
        attachments.removeIf(attachment -> attachment == null || !selectedIds.contains(attachment.getParameterNodeId()));
        node.setAttachedParameterId(attachments.isEmpty() ? null : attachments.get(0).getParameterNodeId());
    }

    private static void cleanupRelationships(NodeGraphData graph, Set<String> removedIds) {
        for (NodeGraphData.NodeData node : graph.getNodes()) {
            if (node == null) continue;
            if (removedIds.contains(node.getAttachedSensorId())) node.setAttachedSensorId(null);
            if (removedIds.contains(node.getParentControlId())) node.setParentControlId(null);
            if (removedIds.contains(node.getAttachedActionId())) node.setAttachedActionId(null);
            if (removedIds.contains(node.getParentActionControlId())) node.setParentActionControlId(null);
            if (removedIds.contains(node.getParentParameterHostId())) node.setParentParameterHostId(null);
            List<NodeGraphData.ParameterAttachmentData> attachments = safeAttachments(node);
            attachments.removeIf(attachment -> attachment != null && removedIds.contains(attachment.getParameterNodeId()));
            node.setAttachedParameterId(attachments.isEmpty() ? null : attachments.get(0).getParameterNodeId());
        }
    }

    private static Set<String> attachmentClosure(NodeGraphData graph, Set<String> initial) {
        LinkedHashSet<String> result = new LinkedHashSet<>(initial);
        ArrayDeque<String> pending = new ArrayDeque<>(initial);
        while (!pending.isEmpty()) {
            NodeGraphData.NodeData node = findNode(graph, pending.removeFirst());
            if (node == null) continue;
            addAttached(result, pending, node.getAttachedSensorId());
            addAttached(result, pending, node.getAttachedActionId());
            if (node.getParameterAttachments() != null) for (NodeGraphData.ParameterAttachmentData attachment : node.getParameterAttachments()) {
                if (attachment != null) addAttached(result, pending, attachment.getParameterNodeId());
            }
        }
        return result;
    }

    private static void addAttached(Set<String> found, ArrayDeque<String> pending, String id) {
        if (id != null && !id.isBlank() && found.add(id)) pending.addLast(id);
    }

    private static void layoutAttachments(NodeGraphData.NodeData host, Map<String, NodeGraphData.NodeData> byId) {
        int row = 0;
        row = placeAttachment(host, byId.get(host.getAttachedSensorId()), row);
        List<NodeGraphData.ParameterAttachmentData> attachments = safeAttachments(host);
        attachments.sort(Comparator.comparingInt(NodeGraphData.ParameterAttachmentData::getSlotIndex));
        for (NodeGraphData.ParameterAttachmentData attachment : attachments) {
            if (attachment != null) row = placeAttachment(host, byId.get(attachment.getParameterNodeId()), row);
        }
    }

    private static int placeAttachment(NodeGraphData.NodeData host, NodeGraphData.NodeData child, int row) {
        if (child == null) return row;
        child.setX(host.getX() + 30);
        child.setY(host.getY() + 95 + row * 85);
        return row + 1;
    }

    private static String referenceFor(NodeGraphData.NodeData node, Map<String, String> references) {
        return references.entrySet().stream().filter(entry -> node.getId().equals(entry.getValue()))
            .map(Map.Entry::getKey).findFirst().orElse(node.getId());
    }

    private static List<String> requiredReferences(JsonObject object, String key) {
        JsonArray values = requiredArray(object, key);
        List<String> result = new ArrayList<>();
        for (JsonElement element : values) result.add(requiredReferenceValue(element.getAsString(), key));
        return result;
    }

    private static String requiredReferenceValue(String value, String key) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}")) {
            throw new IllegalArgumentException("'" + key + "' entries must be short references without spaces.");
        }
        return value;
    }

    private static JsonArray requiredArray(JsonObject object, String key) {
        JsonArray values = optionalArray(object, key);
        if (values.isEmpty()) throw new IllegalArgumentException("Graph command is missing non-empty '" + key + "'.");
        return values;
    }

    private static JsonArray optionalArray(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static boolean optionalBoolean(JsonObject object, String key, boolean fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsBoolean() : fallback;
    }

    private static JsonObject command(String kind, Object... fields) {
        JsonObject result = new JsonObject();
        result.addProperty("kind", kind);
        for (int index = 0; index < fields.length; index += 2) {
            String key = (String) fields[index];
            Object value = fields[index + 1];
            if (value instanceof Number number) result.addProperty(key, number);
            else if (value instanceof Boolean bool) result.addProperty(key, bool);
            else result.addProperty(key, String.valueOf(value));
        }
        return result;
    }

    private static JsonArray parameterReadback(NodeGraphData graph, JsonArray commands,
                                               Map<String, String> references) {
        Set<String> requestedRefs = new LinkedHashSet<>();
        for (JsonElement element : commands) {
            if (!element.isJsonObject()) continue;
            JsonObject command = element.getAsJsonObject();
            String ref = nullableString(command, "ref");
            if (ref != null && !ref.isBlank()) requestedRefs.add(ref);
            String host = nullableString(command, "host");
            if (host != null && !host.isBlank()) requestedRefs.add(host);
            if (command.has("refs") && command.get("refs").isJsonArray()) {
                for (JsonElement value : command.getAsJsonArray("refs")) {
                    if (value.isJsonPrimitive()) requestedRefs.add(value.getAsString());
                }
            }
        }
        JsonArray readback = new JsonArray();
        for (String ref : requestedRefs) {
            if (readback.size() >= 24) break;
            NodeGraphData scope = AiGraphScope.containing(graph, references.getOrDefault(ref, ref));
            NodeGraphData.NodeData node = scope == null ? null : findNode(scope, references.getOrDefault(ref, ref));
            if (node == null) continue;
            if (safeParameters(node).isEmpty()) continue;
            JsonObject item = new JsonObject();
            item.addProperty("ref", ref);
            item.addProperty("nodeId", node.getId());
            item.addProperty("nodeType", node.getType().name());
            JsonArray parameters = new JsonArray();
            for (NodeGraphData.ParameterData parameter : safeParameters(node)) {
                if (parameter == null) continue;
                JsonObject actual = new JsonObject();
                actual.addProperty("parameterId", AiConfiguredValues.parameterId(parameter));
                actual.addProperty("type", parameter.getType());
                actual.addProperty("value", parameter.getValue());
                AiConfiguredValues.Value effective = AiConfiguredValues.read(scope, node, AiConfiguredValues.parameterId(parameter));
                actual.addProperty("staticallyKnown", effective.staticallyKnown());
                actual.addProperty("effectiveValue", effective.effective());
                actual.addProperty("sourceNodeId", effective.sourceNodeId());
                parameters.add(actual);
            }
            item.add("parameters", parameters);
            readback.add(item);
        }
        return readback;
    }

    private static List<NodeGraphData.ParameterData> safeParameters(NodeGraphData.NodeData node) {
        if (node.getParameters() == null) node.setParameters(new ArrayList<>());
        return node.getParameters();
    }

    private static List<NodeGraphData.ParameterAttachmentData> safeAttachments(NodeGraphData.NodeData node) {
        if (node.getParameterAttachments() == null) node.setParameterAttachments(new ArrayList<>());
        return node.getParameterAttachments();
    }

    private static int[] nextPosition(NodeGraphData graph) {
        for (int index = 0; index < 10_000; index++) {
            int candidateX = 40 + (index % 5) * 190;
            int candidateY = 40 + (index / 5) * 110;
            boolean occupied = graph.getNodes().stream().filter(java.util.Objects::nonNull)
                .anyMatch(node -> Math.abs(node.getX() - candidateX) < 120 && Math.abs(node.getY() - candidateY) < 70);
            if (!occupied) return new int[]{candidateX, candidateY};
        }
        throw new IllegalArgumentException("No automatic node position is available.");
    }

    private static void requireEmpty(String current, String requested, String relationship) {
        if (current != null && !current.isBlank() && !current.equals(requested)) {
            throw new IllegalArgumentException("The " + relationship + " is already occupied.");
        }
    }

    private static String displayRef(JsonObject command, String key, NodeGraphData.NodeData node) {
        String value = nullableString(command, key);
        if (value != null && !value.isBlank()) return "'" + value + "'";
        return "'" + node.getId() + "'";
    }

    private static String requiredReference(JsonObject object, String key) {
        String value = requiredString(object, key);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}")) {
            throw new IllegalArgumentException("'" + key + "' must be a short node reference without spaces.");
        }
        return value;
    }

    private static String requiredString(JsonObject object, String key) {
        String value = nullableString(object, key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Graph command is missing '" + key + "'.");
        return value;
    }

    private static String parameterValue(JsonObject object) {
        String value = nullableString(object, "value");
        if (value == null) throw new IllegalArgumentException("Parameter value is missing.");
        return value;
    }

    private static int requiredInteger(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) throw new IllegalArgumentException("Graph command is missing '" + key + "'.");
        return object.get(key).getAsInt();
    }

    private static Integer nullableInteger(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : null;
    }

    private static String nullableString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    private static <T extends Enum<T>> T enumValue(JsonObject object, String key, Class<T> type) {
        String value = requiredString(object, key);
        try {
            return Enum.valueOf(type, value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + key + " '" + value + "'.");
        }
    }

    public record Result(boolean success, JsonObject graph, Map<String, String> references,
                         JsonArray effects, JsonArray actualValues, String errorCode,
                         boolean recoverable, String message) {
        static Result failure(String errorCode, boolean recoverable, String message) {
            return new Result(false, null, Map.of(), new JsonArray(), new JsonArray(),
                errorCode, recoverable, message);
        }
    }

    private static final class CommandFailure extends IllegalArgumentException {
        private final String code;
        private final boolean recoverable;

        private CommandFailure(String code, boolean recoverable, String message) {
            super(message);
            this.code = code;
            this.recoverable = recoverable;
        }

        private String code() { return code; }
        private boolean recoverable() { return recoverable; }
    }

    private record Boundary(NodeGraphData.NodeData first, NodeGraphData.NodeData last,
                            NodeGraphData.ConnectionData incoming, NodeGraphData.ConnectionData outgoing) { }

    private record PendingParameter(NodeGraphData.ParameterData parameter, String value) { }

    private record RoutineInputBinding(String inputId, String bindToRef, Integer slotIndex) { }
}
