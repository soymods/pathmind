package com.pathmind.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pathmind.data.NodeGraphData;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Exact declared relationships, never natural-language intent inference. */
final class AiStructuralRequirements {
    private AiStructuralRequirements() { }
    static void checkShape(JsonArray requirements) {
        if (requirements.size() > 48) throw new IllegalArgumentException("At most 48 structural requirements.");
        for (var value : requirements) {
            var r = value.getAsJsonObject(); String kind = text(r, "kind");
            if (kind == null || !Set.of("node", "flow", "action", "sensor", "parameter").contains(kind)
                || text(r, "ref") == null) throw new IllegalArgumentException("Each requirement needs kind and ref.");
            if (kind.equals("node")) com.pathmind.nodes.NodeType.valueOf(text(r, "nodeType"));
            else if (text(r, "toRef") == null) throw new IllegalArgumentException("Relationships need toRef.");
            for (String field : kind.equals("flow") ? new String[]{"outputSocket", "inputSocket"}
                : kind.equals("parameter") ? new String[]{"slotIndex"} : new String[]{}) {
                if (text(r, field) == null || r.get(field).getAsInt() < 0) throw new IllegalArgumentException("Missing nonnegative " + field);
            }
            if (kind.equals("flow")) checkOutputSocketExists(r);
        }
    }
    /**
     * Rejects a flow the command engine could never build.
     *
     * <p>A recorded requirement is permanent: validate_graph reports it until it is satisfied, and a
     * plan correction may retune sockets but never drop an entry. Accepting a flow out of a node type
     * with too few output sockets therefore strands the whole run, so the impossible socket has to be
     * caught here rather than once per attempt in apply_graph_commands.</p>
     */
    private static void checkOutputSocketExists(JsonObject requirement) {
        String typeName = text(requirement, "nodeType");
        if (typeName == null) return; // Source type is optional; verify() still matches by ref.
        com.pathmind.nodes.NodeType type = com.pathmind.nodes.NodeType.valueOf(typeName);
        int available = com.pathmind.nodes.Node.createForEditor(type, 0, 0).getOutputSocketCount();
        int requested = requirement.get("outputSocket").getAsInt();
        if (requested < available) return;
        throw new IllegalArgumentException(available == 0
            ? type + " has no output sockets, so no flow can leave it. Record its body as an action requirement instead."
            : type + " exposes output sockets 0.." + (available - 1) + ", so outputSocket " + requested + " can never be connected.");
    }

    static JsonArray verify(NodeGraphData root, JsonArray requirements, Map<String, String> refs) {
        JsonArray issues = new JsonArray();
        for (var value : requirements) {
            var r = value.getAsJsonObject(); String ref = text(r, "ref"), kind = text(r, "kind");
            String id = refs.getOrDefault(ref, ref), to = text(r, "toRef");
            String target = to == null ? null : refs.getOrDefault(to, to); boolean matched = false;
            try {
                var graph = AiGraphScope.resolve(root, text(r, "graphRef"), refs);
                var node = graph.getNodes().stream().filter(n -> n != null && id.equals(n.getId())).findFirst().orElse(null);
                if (node != null) matched = switch (kind) {
                    case "node" -> node.getType().name().equals(text(r, "nodeType"));
                    case "flow" -> graph.getConnections().stream().anyMatch(e -> e != null && id.equals(e.getOutputNodeId())
                        && target.equals(e.getInputNodeId()) && e.getOutputSocket() == r.get("outputSocket").getAsInt()
                        && e.getInputSocket() == r.get("inputSocket").getAsInt());
                    case "action" -> Objects.equals(target, node.getAttachedActionId());
                    case "sensor" -> Objects.equals(target, node.getAttachedSensorId());
                    case "parameter" -> node.getParameterAttachments() != null && node.getParameterAttachments().stream()
                        .anyMatch(a -> a != null && target.equals(a.getParameterNodeId()) && a.getSlotIndex() == r.get("slotIndex").getAsInt());
                    default -> false;
                };
            } catch (IllegalArgumentException ignored) { }
            if (!matched) {
                JsonObject issue = new JsonObject(); issue.addProperty("code", "structural_requirement_mismatch");
                issue.addProperty("severity", "error"); issue.addProperty("message", "Missing recorded " + kind + " requirement: " + r);
                issue.add("expected", r.deepCopy()); JsonArray inspect = new JsonArray(); inspect.add(ref); if (to != null) inspect.add(to);
                issue.add("inspectRefs", inspect); JsonArray operations = new JsonArray();
                operations.add(kind.equals("node") ? "add_node" : kind.equals("flow") ? "connect" : "attach_" + kind);
                issue.add("suggestedOperations", operations); issues.add(issue);
            }
        }
        return issues;
    }
    private static String text(JsonObject r, String field) { return !r.has(field) || r.get(field).isJsonNull() ? null : r.get(field).getAsString(); }
}
