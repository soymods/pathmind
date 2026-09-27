package com.pathmind.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pathmind.nodes.NodeMode;
import com.pathmind.nodes.NodeType;
import com.pathmind.routines.RoutineValueKind;

/** Strict envelope used for one model-selected action in the local graph agent loop. */
public final class AiAgentTurnSchema {
    private AiAgentTurnSchema() {
    }

    public static JsonObject create() {
        JsonObject parameterValue = object(
            property("parameterId", described(string(), "Exact parameter id from the node contract.")),
            property("value", described(string(), "Typed parameter value serialized as text."))
        );
        JsonObject planRequirement = object(
            property("graphRef", nullable(string())),
            property("ref", described(string(), "Node alias the plan will create or edit.")),
            property("nodeType", described(enumValues(NodeType.values()), "Expected node type for the alias.")),
            property("mode", described(nullable(enumValues(NodeMode.values())), "Expected mode for mode-specific parameter requirements, or null for the default.")),
            property("parameterId", described(string(), "Exact behavior-shaping parameter id.")),
            property("value", described(string(), "Required final value serialized according to its parameter type."))
        );
        JsonObject routineInput = object(
            property("label", described(string(), "User-facing routine argument label.")),
            property("valueKind", described(enumValues(RoutineValueKind.values()), "Typed value family accepted by the routine argument.")),
            property("required", described(bool(), "Whether callers must supply the argument when no default exists.")),
            property("defaultValue", described(nullable(string()), "Optional serialized default value.")),
            property("bindToRef", described(nullable(string()), "Optional body node whose parameter slot consumes this routine input.")),
            property("slotIndex", described(nullable(integer()), "Body parameter slot used with bindToRef."))
        );
        JsonObject command = object(
            property("graphRef", described(nullable(string()), "Routine graph ID/alias, or null for root. Command refs resolve within this graph.")),
            property("kind", described(enumString("add_node", "set_mode", "set_parameter", "set_parameters", "configure_node", "connect",
                "disconnect", "attach_action", "attach_sensor", "attach_parameter", "detach_action",
                "detach_sensor", "detach_parameter", "remove_node", "add_sequence", "insert_sequence_after",
                "wrap_in_repeat", "wrap_in_condition", "create_branch", "declare_variable", "declare_list",
                "clone_subgraph", "replace_subgraph", "create_routine", "add_routine_call", "auto_layout"),
                "The semantic graph mutation to perform.")),
            property("ref", described(nullable(string()), "A short node reference, or the existing anchor for insert_sequence_after.")),
            property("nodeType", described(nullable(enumValues(NodeType.values())), "The catalog node type for add_node.")),
            property("mode", described(nullable(enumValues(NodeMode.values())), "The exact catalog mode for set_mode.")),
            property("parameterId", described(nullable(string()), "The exact catalog parameter id for set_parameter.")),
            property("value", described(nullable(string()), "The user-facing parameter value for set_parameter.")),
            property("parameterValues", described(array(parameterValue), "Related typed values set atomically by set_parameters.")),
            property("messageClientSide", described(nullable(bool()), "MESSAGE only: true shows the text client-side, false sends it to the server. Not an instance parameter; set it with configure_node.")),
            property("from", described(nullable(string()), "Source node reference for connect.")),
            property("to", described(nullable(string()), "Destination node reference for connect.")),
            property("outputSocket", described(nullable(integer()), "Zero-based source socket for connect or insertion after an anchor.")),
            property("inputSocket", described(nullable(integer()), "Zero-based destination socket for connect.")),
            property("host", described(nullable(string()), "Host node reference for attachment commands.")),
            property("child", described(nullable(string()), "Child node reference for attachment commands.")),
            property("slotIndex", described(nullable(integer()), "Zero-based slot for attach_parameter.")),
            property("refs", described(array(string()), "Ordered node references for sequence and subgraph operations.")),
            property("newRefs", described(array(string()), "New aliases corresponding to refs for clone_subgraph.")),
            property("replacementRefs", described(array(string()), "Ordered existing replacement nodes for replace_subgraph.")),
            property("nodeTypes", described(array(enumValues(NodeType.values())), "Ordered catalog types for add_sequence or insert_sequence_after.")),
            property("count", described(nullable(integer()), "Positive repeat count for wrap_in_repeat.")),
            property("sensor", described(nullable(string()), "Existing boolean sensor reference for conditional composition.")),
            property("trueRefs", described(array(string()), "Ordered true-branch node references for create_branch.")),
            property("falseRefs", described(array(string()), "Ordered false-branch node references for create_branch.")),
            property("variableRef", described(nullable(string()), "Alias for the variable reporter created by declare_variable.")),
            property("name", described(nullable(string()), "Variable, list, or routine name.")),
            property("routineRef", described(nullable(string()), "Stable routine alias for routine commands.")),
            property("routineInputs", described(array(routineInput), "Typed routine arguments for create_routine."))
        );
        return object(
            property("tool", enumString("assess_request", "select_target", "inspect_preset", "list_node_types", "describe_node_types",
                "list_examples", "inspect_example", "find_nodes", "inspect_subgraph", "apply_graph_commands",
                "plan_graph", "validate_graph", "preview_execution", "finish", "bind_node_ref")),
            property("graphRef", nullable(string())), property("ref", nullable(string())), property("nodeId", nullable(string())),
            property("target", enumString("new", "current", "inspect", "undecided")),
            property("requestIntent", described(nullable(enumString("discuss", "diagnose", "build", "edit", "clarify")), "Classify the latest request separately from graph scope. Set on the first useful call; null afterwards.")),
            property("intentEvidence", described(nullable(string()), "Exact quote from the latest USER_REQUEST supporting intent, never historical context.")),
            property("planGoal", described(nullable(string()), "Concise desired graph behavior for plan_graph.")),
            property("planCorrectionReason", described(nullable(string()), "After failed validation only: explain the mistaken implementation refs, scopes or sockets being corrected. Preserve goal, structural kinds/node types and every typed outcome.")),
            property("planSteps", described(array(string()), "One to eight short structural steps; never hidden reasoning.")),
            property("planNodeTypes", described(array(enumValues(NodeType.values())), "Catalog types expected by the structural plan.")),
            property("planStructures", described(array(enumString("sequence", "repeat", "condition", "branch", "variable",
                "list", "routine", "subgraph_edit", "repeat_until", "fork_join", "parameter_attachment",
                "nested_control", "routine_arguments", "inventory", "navigation_collection")),
                "High-level structures the plan expects to build or edit.")),
            property("planAssumptions", described(array(string()), "At most four user-visible assumptions that affect graph structure.")),
            property("planRequirements", described(array(planRequirement), "Machine-readable final parameter values explicitly required by the user. Use one entry per required node parameter; do not encode multiple values in one string.")),
            property("structuralRequirements", array(object(
                property("kind", enumString("node", "flow", "action", "sensor", "parameter")),
                property("graphRef", nullable(string())), property("ref", string()), property("toRef", nullable(string())),
                property("nodeType", nullable(enumValues(NodeType.values()))), property("outputSocket", nullable(integer())),
                property("inputSocket", nullable(integer())), property("slotIndex", nullable(integer()))))),
            property("nodeTypes", array(enumValues(NodeType.values()))),
            property("exampleTraits", described(array(enumValues(AiExampleTrait.values())),
                "Structural traits used to retrieve only relevant golden examples.")),
            property("nodeRefs", array(string())),
            property("query", nullable(string())),
            property("radius", nullable(integer())),
            property("exampleId", nullable(string())),
            property("draftRevision", nullable(integer())),
            property("commands", array(command)),
            property("title", nullable(string())),
            property("response", described(nullable(string()), "User-visible reply: default 1-3 short plain-language sentences, usually under 60 words. State the answer/proposed change and essential caveat only. Do not duplicate tool logs or execution previews. Expand only when the user asks for detail or an important limitation requires it.")),
            property("completion", described(nullable(enumString("complete", "clarification", "blocked")), "finish: answer or validated proposal, clarification question, or specific blocker. Null means complete.")),
            property("completionReason", described(nullable(string()), "For clarification: identify essential missing information and why the latest reply, earlier user messages and workspace do not already resolve it. Do not re-ask answered questions. For blocked: explain the confirmed tool failure. Inspect is never itself a blocker.")),
            property("blockingToolTurn", described(nullable(integer()), "For blocked, cite a prior failed tool turn with context_unavailable or capability_unavailable. Validation, reference, command, and permission/scope errors are internal repair failures, not genuine blockers.")),
            property("workLog", array(string()))
        );
    }

    private static Property property(String name, JsonObject schema) { return new Property(name, schema); }

    private static JsonObject object(Property... properties) {
        JsonObject schema = typed("object");
        JsonObject values = new JsonObject();
        JsonArray required = new JsonArray();
        for (Property property : properties) {
            values.add(property.name(), property.schema());
            required.add(property.name());
        }
        schema.add("properties", values);
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private static JsonObject array(JsonObject item) {
        JsonObject schema = typed("array");
        schema.add("items", item);
        return schema;
    }

    private static JsonObject nullable(JsonObject value) {
        JsonArray options = new JsonArray();
        options.add(value);
        options.add(typed("null"));
        JsonObject schema = new JsonObject();
        schema.add("anyOf", options);
        return schema;
    }

    private static JsonObject described(JsonObject schema, String description) {
        schema.addProperty("description", description);
        return schema;
    }

    private static JsonObject enumString(String... values) {
        JsonObject schema = typed("string");
        JsonArray allowed = new JsonArray();
        for (String value : values) allowed.add(value);
        schema.add("enum", allowed);
        return schema;
    }

    private static JsonObject enumValues(Enum<?>[] values) {
        JsonObject schema = typed("string");
        JsonArray allowed = new JsonArray();
        for (Enum<?> value : values) allowed.add(value.name());
        schema.add("enum", allowed);
        return schema;
    }

    private static JsonObject string() { return typed("string"); }
    private static JsonObject integer() { return typed("integer"); }
    private static JsonObject bool() { return typed("boolean"); }

    private static JsonObject typed(String type) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", type);
        return schema;
    }

    private record Property(String name, JsonObject schema) { }
}
