package com.pathmind.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.nodes.NodeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Runs model-selected tools against an isolated graph draft, never the live editor graph. */
public final class AiPresetAgent {
    static final int MAX_TURNS = 20;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final int MAX_TRANSCRIPT_CHARS = 140_000;
    private static final Gson GSON = new Gson();

    private AiPresetAgent() {
    }

    public static CompletableFuture<AiPresetService.Proposal> run(AiProvider provider, String model, String userPrompt,
                                                                   String conversation, NodeGraphData activeGraph,
                                                                   String activePresetName, boolean baritoneAvailable,
                                                                   boolean uiUtilsAvailable) {
        State state = new State(provider, model, userPrompt, conversation, activeGraph, activePresetName,
            baritoneAvailable, uiUtilsAvailable);
        return runMeasured(state).thenCompose(report -> report.succeeded()
            ? CompletableFuture.completedFuture(report.proposal())
            : CompletableFuture.failedFuture(new IllegalStateException(report.error())));
    }

    public static CompletableFuture<AiRunReport> runMeasured(AiProvider provider, String model, String userPrompt,
                                                             String conversation, NodeGraphData activeGraph,
                                                             String activePresetName, boolean baritoneAvailable,
                                                             boolean uiUtilsAvailable) {
        return runMeasured(new State(provider, model, userPrompt, conversation, activeGraph, activePresetName,
            baritoneAvailable, uiUtilsAvailable));
    }

    public static CompletableFuture<AiPresetService.Proposal> run(AiProvider provider, String model, String userPrompt,
                                                                 String conversation, NodeGraphData activeGraph,
                                                                 String activePresetName, boolean baritoneAvailable,
                                                                 boolean uiUtilsAvailable, AiRequestControl control) {
        return runMeasured(provider, model, userPrompt, conversation, activeGraph, activePresetName,
            baritoneAvailable, uiUtilsAvailable, control).thenCompose(report -> report.succeeded()
            ? CompletableFuture.completedFuture(report.proposal()) : CompletableFuture.failedFuture(new IllegalStateException(report.error())));
    }

    public static CompletableFuture<AiRunReport> runMeasured(AiProvider provider, String model, String userPrompt,
                                                           String conversation, NodeGraphData activeGraph,
                                                           String activePresetName, boolean baritoneAvailable,
                                                           boolean uiUtilsAvailable, AiRequestControl control) {
        State state = new State(provider, model, userPrompt, conversation, activeGraph, activePresetName, baritoneAvailable, uiUtilsAvailable);
        state.control = control;
        return runMeasured(state);
    }

    private static CompletableFuture<AiRunReport> runMeasured(State state) {
        CompletableFuture<AiPresetService.Proposal> work;
        try { work = next(state); }
        catch (RuntimeException failure) { work = CompletableFuture.failedFuture(failure); }
        CompletableFuture<AiPresetService.Proposal> cancellableWork = work;
        state.control.onCancel(() -> {
            state.closed = true;
            state.session.close();
            cancellableWork.completeExceptionally(new java.util.concurrent.CancellationException("AI request cancelled."));
        });
        return work.orTimeout(4, java.util.concurrent.TimeUnit.MINUTES).handle((proposal, failure) -> {
            state.closed = true;
            state.session.close();
            state.control.release();
            AiRunMetrics metrics = new AiRunMetrics(state.provider.providerId(), state.model, state.turn,
                state.repairAttempts, state.validationFailures, state.toolErrors,
                (System.nanoTime() - state.startedNanos) / 1_000_000, AiTokenUsage.total(state.usages), state.toolCalls);
            AiUsageTelemetry.record(metrics);
            String message = null;
            if (failure != null) {
                Throwable cause = failure;
                while (cause.getCause() != null && cause instanceof java.util.concurrent.CompletionException) cause = cause.getCause();
                message = state.control.isCancelled() ? "AI request cancelled. No preset changes were applied."
                    : cause instanceof java.util.concurrent.TimeoutException ? "AI exceeded its four-minute request budget."
                    : AiDisplayText.diagnostic(cause.getMessage());
                if (message.isBlank()) message = "AI request failed.";
            }
            AiRequestDiagnostics.record(new AiRequestDiagnostics.Run(metrics, state.intent,
                proposal == null ? null : proposal.outcome(), state.trace));
            AiRequestProgress.Stage stage = failure == null ? switch (proposal.outcome()) {
                case PROPOSAL -> AiRequestProgress.Stage.AWAITING_REVIEW; case ANSWER -> AiRequestProgress.Stage.ANSWERED;
                case CLARIFICATION -> AiRequestProgress.Stage.CLARIFICATION; case BLOCKED -> AiRequestProgress.Stage.BLOCKED;
            } : state.control.isCancelled() ? AiRequestProgress.Stage.CANCELLED : AiRequestProgress.Stage.FAILED;
            state.progress(stage, failure == null ? stage == AiRequestProgress.Stage.AWAITING_REVIEW ? "Awaiting your review" : "Response ready" : message, null);
            return new AiRunReport(proposal, metrics, message, state.trace);
        });
    }

    private static CompletableFuture<AiPresetService.Proposal> next(State state) {
        if (state.closed || state.control.isCancelled()) return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("AI request is closed."));
        if (state.turn >= MAX_TURNS) {
            return CompletableFuture.failedFuture(new IllegalStateException("AI stopped after " + MAX_TURNS + " tool turns without producing a valid proposal."));
        }
        state.turn++;
        if (state.turn == 1) state.progress(AiRequestProgress.Stage.THINKING, "Understanding your request", null);
        AiPresetRequest request = new AiPresetRequest(systemPrompt(state.provider.capabilities()), state.prompt(), state.model,
            "pathmind_agent_action", AiAgentTurnSchema.create());
        return state.session.generate(request, state.pendingResult).thenCompose(turn -> {
            if (state.closed) return CompletableFuture.failedFuture(new IllegalStateException("AI request is closed."));
            state.usages.add(turn.usage());
            state.currentTool = "invalid_response";
            state.toolRevisionBefore = state.draftRevision;
            try {
                JsonObject action = parseObject(turn.action());
                String toolName = string(action, "tool", "");
                state.currentTool = toolName;
                state.toolCalls.merge(toolName, 1, Integer::sum);
                String actionKey = actionKey(action, state.draftRevision);
                if (actionKey.equals(state.lastActionKey)) state.repeatedActionCount++;
                else {
                    state.lastActionKey = actionKey;
                    state.repeatedActionCount = 1;
                }
                if (state.repeatedActionCount >= 5) {
                    state.record("TOOL_ERROR", codedError("no_progress", "AI repeated the same action despite recovery guidance."));
                    return CompletableFuture.failedFuture(new IllegalStateException(
                        "AI could not recover from a repeated " + toolName + " action. No preset changes were applied."));
                }
                if (state.repeatedActionCount >= 3) {
                    state.record("TOOL_ERROR", codedError("recover_repeated_action",
                        "Do not repeat this identical " + toolName + " call: the draft has not changed. Use previous results. "
                        + "If validation succeeded, finish now. Otherwise inspect the specific failed relationship, change the command arguments, "
                        + "or satisfy the prerequisite named in the previous error. Do not ask the user to resolve internal tool errors."));
                    return next(state);
                }
                String intentError = assessIntent(state, action);
                if (intentError != null) {
                    state.record("TOOL_ERROR", codedError("intent_required", intentError));
                    return continueOrFail(state, intentError);
                }
                String scope = nullableString(action, "target");
                if (scope != null && (scope.equals("new") || scope.equals("current"))) {
                    String scopeError = initializeTarget(state, scope);
                    if (scopeError != null) {
                        state.record("TOOL_ERROR", codedError(scope.equals("current") && state.activeGraph == null ? "context_unavailable" : "scope_conflict", scopeError));
                        return continueOrFail(state, scopeError);
                    }
                }
                state.record("ASSISTANT_ACTION", action);
                if ("apply_graph_commands".equals(toolName) && state.repairRound > 0) state.repairAttempts++;
                state.publishToolProgress(toolName);
                ToolResult result = execute(state, action);
                if (state.closed || state.control.isCancelled()) return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("AI request cancelled."));
                if (result.proposal != null) {
                    state.record("TOOL_RESULT", ok("Finished with outcome " + result.proposal.outcome() + ". No preset was committed."));
                    return CompletableFuture.completedFuture(result.proposal);
                }
                if (result.payload != null) {
                    result.payload.addProperty("target", state.target == null ? "undecided" : state.target);
                    result.payload.addProperty("requestIntent", state.intent == null ? "unassessed" : state.intent.name().toLowerCase(Locale.ROOT));
                    result.payload.addProperty("draftEditsAuthorized", state.intent != null && state.intent.permitsDraftEdits());
                    result.payload.addProperty("draftRevision", state.draftRevision);
                }
                state.record("TOOL_RESULT", result.payload);
                if ("apply_graph_commands".equals(toolName) && result.payload != null
                    && result.payload.has("ok") && !result.payload.get("ok").getAsBoolean()) state.graphRecoveryFailures++;
                int stagnant = state.progressTracker.record(toolName, result.payload, state.draftRevision);
                int repeatedValidation = state.progressTracker.repeatedValidationCount();
                if (repeatedValidation >= 3) return CompletableFuture.failedFuture(new IllegalStateException(
                    "AI could not resolve the same validation issues after multiple draft edits. No preset changes were applied."));
                if (repeatedValidation == 2 && "validate_graph".equals(toolName) && state.pendingResult != null)
                    state.pendingResult.addProperty("recoveryGuidance",
                        "The same validation issues survived two repair rounds. Do not try another cosmetic edit or revalidation. Inspect the cited contracts and make a materially different correction.");
                if (stagnant >= 9) return CompletableFuture.failedFuture(new IllegalStateException(
                    "AI stopped because repeated tool cycles added no new information or graph changes. No preset changes were applied."));
                if (stagnant >= 6) state.record("TOOL_ERROR", codedError("stalled_cycle",
                    "Recent tools add no new information. Use existing results to make a different repair, or finish a validated proposal. Do not repeat inspection/edit cycles."));
                if (result.payload != null && result.payload.has("ok") && !result.payload.get("ok").getAsBoolean()) {
                    return continueOrFail(state, string(result.payload, "message", "Tool call failed."));
                }
                state.clearFailureStreak();
                return next(state);
            } catch (RuntimeException exception) {
                state.record("TOOL_ERROR", error(exception.getMessage()));
                return continueOrFail(state, exception.getMessage());
            }
        });
    }

    private static CompletableFuture<AiPresetService.Proposal> continueOrFail(State state, String message) {
        String normalizedMessage = message == null || message.isBlank() ? "unknown tool error" : message;
        String failureKey = state.draftRevision + "|" + state.target + "|" + normalizedMessage;
        if (failureKey.equals(state.lastFailureKey)) state.consecutiveFailures++;
        else {
            state.lastFailureKey = failureKey;
            state.consecutiveFailures = 1;
        }
        if (state.consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                "AI repeated the same unsuccessful action " + MAX_CONSECUTIVE_FAILURES + " times: "
                    + normalizedMessage));
        }
        if (state.consecutiveFailures >= 3) {
            state.record("TOOL_ERROR", codedError("recover_tool_error",
                "Repeated tool failure: " + normalizedMessage + ". Change strategy or arguments, inspect the relevant node contract, "
                + "and satisfy the reported prerequisite. Do not repeat the failing call or treat an internal tool error as missing user information."));
        }
        return next(state);
    }

    private static ToolResult execute(State state, JsonObject action) {
        String tool = string(action, "tool", "");
        return switch (tool) {
            case "assess_request" -> ToolResult.more(state.intent == null
                ? codedError("intent_required", "Set requestIntent and exact intentEvidence from the latest request.")
                : ok("Request intent assessed: " + state.intent + "."));
            case "select_target" -> selectTarget(state, action);
            case "inspect_preset" -> inspectPreset(state);
            case "list_node_types" -> listNodeTypes(state);
            case "describe_node_types" -> describeNodes(state, action);
            case "list_examples" -> listExamples(action);
            case "inspect_example" -> inspectExample(action);
            case "find_nodes" -> findNodes(state, action);
            case "inspect_subgraph" -> inspectSubgraph(state, action);
            case "bind_node_ref" -> bindNodeRef(state, action);
            case "plan_graph" -> planGraph(state, action);
            case "apply_graph_commands" -> applyCommands(state, action);
            case "validate_graph" -> validateGraph(state);
            case "preview_execution" -> previewExecution(state);
            case "finish" -> finish(state, action);
            case "invalid_provider_response" -> ToolResult.more(error(string(action, "response", "Call exactly one native function.")));
            default -> ToolResult.more(error("Unknown tool '" + tool + "'."));
        };
    }

    private static ToolResult selectTarget(State state, JsonObject action) {
        String target = nullableString(action, "target");
        String targetError = initializeTarget(state, target);
        if (targetError != null) return ToolResult.more(error(targetError));
        JsonObject result = ok("Target selected: " + target + ".");
        result.addProperty("draftRevision", state.draftRevision);
        result.addProperty("next", "Read tools are always available. Assess request intent before planning, editing, or finishing. Scope can change until the first successful draft edit.");
        return ToolResult.more(result);
    }

    private static String initializeTarget(State state, String target) {
        if ("inspect".equals(target) || "undecided".equals(target)) return null;
        if (!"new".equals(target) && !"current".equals(target)) return "Choose graph scope new or current, or leave it undecided while reading.";
        if (state.intent != null && !state.intent.permitsDraftEdits()) return null;
        if ("current".equals(target) && state.activeGraph == null) return "There is no open graph to edit.";
        if (target.equals(state.target)) return null;
        if (state.draftRevision > 0) return "Graph scope is frozen after a successful draft edit. Continue repairing this draft or ask the user to start another request.";
        state.target = target;
        state.planned = state.validated = state.previewed = false;
        state.plan = null; state.nodeReferences.clear();
        if ("current".equals(target)) state.workingGraph = graphJson(state.activeGraph);
        else if ("new".equals(target)) state.workingGraph = emptyGraph();
        return null;
    }

    private static String assessIntent(State state, JsonObject action) {
        String raw = nullableString(action, "requestIntent");
        if (raw == null) return null; // Read operations do not require premature intent selection.
        AiRequestIntent intent;
        try { intent = AiRequestIntent.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException failure) { return "Choose requestIntent discuss, diagnose, build, edit, or clarify."; }
        if (!quotesRequest(state.userPrompt, nullableString(action, "intentEvidence")))
            return "intentEvidence must quote this request's USER_REQUEST, not older chat or tool data.";
        if (state.intent != null && state.intent != intent) {
            if (state.draftRevision > 0 || state.intent == AiRequestIntent.DISCUSS || state.intent == AiRequestIntent.DIAGNOSE)
                return "Do not escalate read-only permissions or change intent after edits. Ask the user for clarification in a new request.";
            state.planned = false; state.plan = null;
        }
        if (intent == AiRequestIntent.CLARIFY && state.answeringClarification)
            return "This request answers the previous clarification. Classify the inherited task as build, edit, discuss, or diagnose and use the user's reply to resolve the choice; do not ask again.";
        state.intent = intent;
        if (!intent.permitsDraftEdits()) { state.target = null; state.workingGraph = null; state.nodeReferences.clear(); }
        return null;
    }

    /**
     * Whether the evidence is drawn from the request currently being answered.
     *
     * <p>The point of the check is to stop a model justifying intent with older chat or tool output,
     * so it compares on content rather than byte equality. Case, whitespace runs and typographic
     * quotes are normalised first: a model that reproduces the sense of the line exactly but retypes
     * a curly apostrophe was otherwise spending turns on a transcription technicality.</p>
     */
    static boolean quotesRequest(String userPrompt, String evidence) {
        if (userPrompt == null || evidence == null || evidence.isBlank()) return false;
        return normalizeQuote(userPrompt).contains(normalizeQuote(evidence));
    }

    private static String normalizeQuote(String value) {
        return value.replace('‘', '\'').replace('’', '\'')
            .replace('“', '"').replace('”', '"')
            .replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String draftPermissionError(State state) {
        if (state.intent == null) return "Assess requestIntent using evidence from USER_REQUEST before editing; read tools remain available.";
        if (!state.intent.permitsDraftEdits()) return "This request is discussion/diagnosis/clarification, so draft editing is not authorized. Answer or ask the user, without editing.";
        String expected = state.intent == AiRequestIntent.EDIT ? "current" : "new";
        if (!expected.equals(state.target)) return "For intent " + state.intent + ", select graph scope '" + expected + "' before planning or editing. Inspecting does not restrict that selection.";
        return null;
    }

    private static ToolResult inspectPreset(State state) {
        state.inspected = true;
        JsonObject result = ok("Returned the open preset and current draft.");
        result.addProperty("draftRevision", state.draftRevision);
        result.add("activePreset", AiExecutionPreview.inspect(state.activePresetName, state.activeGraph));
        if (state.workingGraph != null) result.add("workingDraft", AiExecutionPreview.inspect(
            "current".equals(state.target) ? state.activePresetName : "New preset", parseGraph(state.workingGraph)));
        return ToolResult.more(result);
    }

    private static ToolResult listNodeTypes(State state) {
        JsonArray contracts = AiPresetContextBuilder.availableNodeContracts(state.baritoneAvailable, state.uiUtilsAvailable);
        JsonArray resultNodes = new JsonArray();
        for (JsonElement element : contracts) {
            JsonObject contract = element.getAsJsonObject();
            JsonObject summary = new JsonObject();
            summary.add("type", contract.get("type"));
            summary.add("name", contract.get("name"));
            summary.add("description", contract.get("description"));
            summary.add("category", contract.get("category"));
            resultNodes.add(summary);
        }
        JsonObject result = ok("Returned " + resultNodes.size() + " available node types.");
        result.add("nodes", resultNodes);
        return ToolResult.more(result);
    }

    private static ToolResult describeNodes(State state, JsonObject action) {
        if (!action.has("nodeTypes") || !action.get("nodeTypes").isJsonArray() || action.getAsJsonArray("nodeTypes").isEmpty()) {
            return ToolResult.more(error("describe_node_types requires one or more nodeTypes."));
        }
        if (action.getAsJsonArray("nodeTypes").size() > 12) return ToolResult.more(error("Describe at most 12 node types per call."));
        JsonArray contracts = new JsonArray();
        java.util.Set<NodeType> requested = new java.util.LinkedHashSet<>();
        for (JsonElement element : action.getAsJsonArray("nodeTypes")) {
            try {
                NodeType type = NodeType.valueOf(element.getAsString().toUpperCase(Locale.ROOT));
                JsonObject contract = AiPresetContextBuilder.nodeContract(type);
                if (contract == null || !containsAvailableType(state, type)) {
                    return ToolResult.more(codedError("capability_unavailable", "Node type " + type + " is unavailable in this installation."));
                }
                requested.add(type);
                contracts.add(contract);
            } catch (IllegalArgumentException exception) {
                return ToolResult.more(codedError("capability_unavailable", "Unknown node type '" + element + "'. Use list_node_types."));
            }
        }
        JsonObject result = ok("Returned " + contracts.size() + " exact node contract(s).");
        result.add("contracts", contracts);
        result.add("relevantExamples", AiGoldenGraphLibrary.detailsMatching(requested, 2));
        return ToolResult.more(result);
    }

    private static ToolResult listExamples(JsonObject action) {
        java.util.Set<NodeType> types = parseExampleNodeTypes(action);
        java.util.Set<AiExampleTrait> traits = parseExampleTraits(action);
        JsonArray examples = types.isEmpty() && traits.isEmpty()
            ? AiGoldenGraphLibrary.list()
            : AiGoldenGraphLibrary.listMatching(types, traits, 4);
        JsonObject result = ok(types.isEmpty() && traits.isEmpty()
            ? "Returned the compact curated example index. Query by nodeTypes or exampleTraits before inspecting one."
            : "Returned " + examples.size() + " closest curated graph example(s).");
        result.add("examples", examples);
        return ToolResult.more(result);
    }

    private static java.util.Set<NodeType> parseExampleNodeTypes(JsonObject action) {
        java.util.Set<NodeType> types = new java.util.LinkedHashSet<>();
        if (!action.has("nodeTypes") || !action.get("nodeTypes").isJsonArray()) return types;
        for (JsonElement element : action.getAsJsonArray("nodeTypes")) {
            try {
                types.add(NodeType.valueOf(element.getAsString().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown example node type '" + element.getAsString() + "'.");
            }
        }
        return types;
    }

    private static java.util.Set<AiExampleTrait> parseExampleTraits(JsonObject action) {
        java.util.Set<AiExampleTrait> traits = new java.util.LinkedHashSet<>();
        if (!action.has("exampleTraits") || !action.get("exampleTraits").isJsonArray()) return traits;
        for (JsonElement element : action.getAsJsonArray("exampleTraits")) {
            try {
                traits.add(AiExampleTrait.valueOf(element.getAsString().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown example trait '" + element.getAsString() + "'.");
            }
        }
        return traits;
    }

    private static ToolResult inspectExample(JsonObject action) {
        String exampleId = nullableString(action, "exampleId");
        return AiGoldenGraphLibrary.find(exampleId).map(entry -> {
            JsonObject result = ok("Returned a bundled serialized graph example.");
            result.add("metadata", AiGoldenGraphLibrary.summary(entry));
            result.add("graph", graphJson(entry.graph()));
            return ToolResult.more(result);
        }).orElseGet(() -> ToolResult.more(error("Unknown example id. Use list_examples.")));
    }

    private static ToolResult findNodes(State state, JsonObject action) {
        JsonObject graph = scopedQuery(state, action);
        if (graph == null) return ToolResult.more(error("There is no graph available to search."));
        state.inspected = true;
        return ToolResult.more(AiGraphQueryEngine.find(graph,
            action.has("nodeTypes") && action.get("nodeTypes").isJsonArray()
                ? action.getAsJsonArray("nodeTypes") : new JsonArray(),
            nullableString(action, "query"), state.nodeReferences));
    }

    private static ToolResult inspectSubgraph(State state, JsonObject action) {
        JsonObject graph = scopedQuery(state, action);
        if (graph == null) return ToolResult.more(error("There is no graph available to inspect."));
        JsonArray refs = action.has("nodeRefs") && action.get("nodeRefs").isJsonArray()
            ? action.getAsJsonArray("nodeRefs") : new JsonArray();
        int radius = action.has("radius") && !action.get("radius").isJsonNull() ? action.get("radius").getAsInt() : 1;
        state.inspected = true;
        return ToolResult.more(AiGraphQueryEngine.inspectSubgraph(graph, refs, radius, state.nodeReferences));
    }

    private static JsonObject graphForQuery(State state) {
        if (state.workingGraph != null) return state.workingGraph;
        return state.activeGraph == null ? null : graphJson(state.activeGraph);
    }

    private static JsonObject scopedQuery(State state, JsonObject action) {
        JsonObject root = graphForQuery(state);
        return root == null ? null : graphJson(AiGraphScope.resolve(parseGraph(root), nullableString(action, "graphRef"), state.nodeReferences));
    }

    private static ToolResult bindNodeRef(State state, JsonObject action) {
        if (!state.inspected) return ToolResult.more(codedError("inspection_required", "Inspect the existing graph before binding references."));
        String ref = nullableString(action, "ref"), id = nullableString(action, "nodeId");
        if (ref == null || ref.isBlank() || id == null) return ToolResult.more(error("bind_node_ref needs ref and nodeId."));
        var graph = scopedQuery(state, action);
        if (graph == null || parseGraph(graph).getNodes().stream().noneMatch(n -> n != null && id.equals(n.getId())))
            return ToolResult.more(error("nodeId must exist in the selected graphRef."));
        if (state.nodeReferences.containsKey(ref) && !id.equals(state.nodeReferences.get(ref)))
            return ToolResult.more(error("That alias is already bound to another node."));
        state.nodeReferences.put(ref, id);
        return ToolResult.more(ok("Bound " + ref + " to existing node " + id + ". No graph changes."));
    }

    private static ToolResult planGraph(State state, JsonObject action) {
        boolean correcting = state.draftRevision > 0;
        String correctionReason = nullableString(action, "planCorrectionReason");
        if (correcting && (state.repairRound == 0 || correctionReason == null || correctionReason.isBlank() || state.planCorrections >= 2))
            return ToolResult.more(codedError("plan_frozen",
                "After failed validation, plan_graph may correct the structural implementation plan with planCorrectionReason, at most twice. Preserve every typed outcome requirement."));
        String permissionError = draftPermissionError(state);
        if (permissionError != null) return ToolResult.more(codedError("draft_permission", permissionError));
        String goal = shortText(nullableString(action, "planGoal"), 240);
        if (goal.isBlank()) return ToolResult.more(error("plan_graph requires a concise planGoal."));
        JsonArray rawSteps = action.has("planSteps") && action.get("planSteps").isJsonArray()
            ? action.getAsJsonArray("planSteps") : new JsonArray();
        if (rawSteps.isEmpty() || rawSteps.size() > 8) {
            return ToolResult.more(error("plan_graph requires 1 to 8 structural planSteps."));
        }
        JsonArray steps = new JsonArray();
        for (JsonElement step : rawSteps) {
            if (!step.isJsonPrimitive()) return ToolResult.more(error("Every plan step must be short text."));
            String value = shortText(step.getAsString(), 140);
            if (!value.isBlank()) steps.add(value);
        }
        JsonArray nodeTypes = action.has("planNodeTypes") && action.get("planNodeTypes").isJsonArray()
            ? action.getAsJsonArray("planNodeTypes") : new JsonArray();
        if (nodeTypes.size() > 16) return ToolResult.more(error("A plan may name at most 16 node types."));
        for (JsonElement element : nodeTypes) {
            try {
                NodeType type = NodeType.valueOf(element.getAsString().toUpperCase(Locale.ROOT));
                if (!containsAvailableType(state, type)) return ToolResult.more(error("Planned node type " + type + " is unavailable."));
            } catch (IllegalArgumentException exception) {
                return ToolResult.more(error("Unknown planned node type '" + element + "'."));
            }
        }
        JsonObject plan = new JsonObject();
        plan.addProperty("goal", goal);
        plan.add("steps", steps);
        plan.add("nodeTypes", nodeTypes.deepCopy());
        plan.add("structures", action.has("planStructures") && action.get("planStructures").isJsonArray()
            ? action.getAsJsonArray("planStructures").deepCopy() : new JsonArray());
        JsonArray assumptions = new JsonArray();
        if (action.has("planAssumptions") && action.get("planAssumptions").isJsonArray()) {
            for (JsonElement assumption : action.getAsJsonArray("planAssumptions")) {
                if (assumptions.size() >= 4) break;
                if (assumption.isJsonPrimitive()) assumptions.add(shortText(assumption.getAsString(), 120));
            }
        }
        plan.add("assumptions", assumptions);
        JsonArray requirements = new JsonArray();
        java.util.Set<String> requirementKeys = new java.util.LinkedHashSet<>();
        JsonArray rawRequirements = action.has("planRequirements") && action.get("planRequirements").isJsonArray()
            ? action.getAsJsonArray("planRequirements") : new JsonArray();
        if (rawRequirements.size() > 32) return ToolResult.more(error("A plan may contain at most 32 parameter requirements."));
        for (JsonElement element : rawRequirements) {
            if (!element.isJsonObject()) return ToolResult.more(error("Every plan requirement must be an object."));
            JsonObject raw = element.getAsJsonObject();
            String ref = nullableString(raw, "ref");
            String parameterId = nullableString(raw, "parameterId");
            String expected = nullableString(raw, "value");
            if (ref == null || ref.isBlank() || parameterId == null || parameterId.isBlank() || expected == null) {
                return ToolResult.more(error("Each plan requirement needs ref, nodeType, parameterId, and value."));
            }
            try {
                NodeType nodeType = NodeType.valueOf(string(raw, "nodeType", "").toUpperCase(Locale.ROOT));
                if (!containsAvailableType(state, nodeType)) return ToolResult.more(error("Required node type " + nodeType + " is unavailable."));
                String requiredMode = nullableString(raw, "mode");
                com.pathmind.nodes.NodeMode mode = requiredMode == null ? null
                    : com.pathmind.nodes.NodeMode.valueOf(requiredMode);
                if (mode != null && java.util.Arrays.stream(com.pathmind.nodes.NodeMode.getModesForNodeType(nodeType))
                    .noneMatch(candidate -> candidate == mode)) throw new IllegalArgumentException("Invalid requirement mode for " + nodeType);
                AiParameterValidator.CheckedValue checked = AiParameterValidator.validate(nodeType, mode, parameterId, expected);
                String key = nullableString(raw, "graphRef") + "|" + ref + "|" + checked.parameterId();
                if (!requirementKeys.add(key)) return ToolResult.more(error("Duplicate plan requirement for " + key + "."));
                JsonObject requirement = new JsonObject();
                requirement.addProperty("ref", ref);
                if (nullableString(raw, "graphRef") != null) requirement.addProperty("graphRef", nullableString(raw, "graphRef"));
                requirement.addProperty("nodeType", nodeType.name());
                requirement.addProperty("parameterId", checked.parameterId());
                requirement.addProperty("value", checked.value());
                if (mode != null) requirement.addProperty("mode", mode.name());
                requirements.add(requirement);
            } catch (IllegalArgumentException exception) {
                return ToolResult.more(codedError("invalid_plan_requirement", exception.getMessage()));
            }
        }
        plan.add("requirements", requirements);
        JsonArray structural = action.has("structuralRequirements") && action.get("structuralRequirements").isJsonArray()
            ? action.getAsJsonArray("structuralRequirements") : new JsonArray();
        try { AiStructuralRequirements.checkShape(structural); }
        catch (RuntimeException failure) { return ToolResult.more(codedError("invalid_structural_requirement", failure.getMessage())); }
        plan.add("structuralRequirements", structural.deepCopy());
        if (correcting && !AiPlanCorrection.preservesBehavior(state.plan, plan))
            return ToolResult.more(codedError("plan_correction_weakens_behavior", "Correction cannot change the goal, typed outcomes, structural kinds or required node types. Correct only implementation refs, scopes and sockets."));
        if (correcting) {
            state.planCorrections++;
            plan.addProperty("correctionReason", AiDisplayText.diagnostic(correctionReason));
            state.validated = false;
            state.previewed = false;
        }
        state.plan = plan;
        state.planned = true;
        state.planRevision++;
        JsonObject result = ok("Accepted structural plan " + state.planRevision + ". Apply semantic operations that implement it.");
        result.addProperty("planRevision", state.planRevision);
        result.add("plan", plan.deepCopy());
        return ToolResult.more(result);
    }

    private static ToolResult applyCommands(State state, JsonObject action) {
        String permissionError = draftPermissionError(state);
        if (permissionError != null) return ToolResult.more(codedError("draft_permission", permissionError));
        if (state.intent == AiRequestIntent.EDIT && !state.inspected)
            return ToolResult.more(codedError("inspection_required", "Inspect the open preset or relevant subgraph before editing it. Inspection is a read action and does not change permissions."));
        if (!state.planned) return ToolResult.more(error("Call plan_graph with a short structural plan before editing."));
        Integer requestedRevision = nullableInteger(action, "draftRevision");
        if (requestedRevision == null || requestedRevision != state.draftRevision) {
            JsonObject result = error("Draft revision mismatch; inspect the draft and retry against the current revision.");
            result.addProperty("draftRevision", state.draftRevision);
            return ToolResult.more(result);
        }
        JsonArray commands = action.has("commands") && action.get("commands").isJsonArray()
            ? action.getAsJsonArray("commands") : new JsonArray();
        AiGraphCommandEngine.Result mutation = AiGraphCommandEngine.apply(state.workingGraph, commands,
            state.nodeReferences, state.baritoneAvailable, state.uiUtilsAvailable);
        if (!mutation.success()) {
            JsonObject result = codedError(mutation.errorCode(), mutation.message());
            result.addProperty("recoverable", mutation.recoverable());
            JsonArray affectedRefs = new JsonArray();
            for (JsonElement element : commands) {
                if (!element.isJsonObject()) continue;
                for (String key : List.of("ref", "host", "child")) {
                    String ref = nullableString(element.getAsJsonObject(), key);
                    if (ref != null) affectedRefs.add(ref);
                }
            }
            if (!affectedRefs.isEmpty()) {
                JsonArray contexts = new JsonArray();
                for (var command : commands) {
                    try {
                        var scope = AiGraphScope.resolve(parseGraph(state.workingGraph), nullableString(command.getAsJsonObject(), "graphRef"), state.nodeReferences);
                        contexts.add(AiGraphQueryEngine.inspectSubgraph(graphJson(scope), affectedRefs, 1, state.nodeReferences));
                    } catch (RuntimeException ignored) { }
                    if (contexts.size() >= 4) break;
                }
                result.add("instanceContext", contexts);
            }
            if (mutation.recoverable()) result.addProperty("guidance",
                "Repair the command or inspect the relevant subgraph, then retry. This rejection does not prove the task is blocked.");
            return ToolResult.more(result);
        }
        try {
            parseGraph(mutation.graph());
        } catch (RuntimeException exception) {
            return ToolResult.more(error("Commands did not produce a readable graph: " + exception.getMessage()));
        }
        if (state.workingGraph.equals(mutation.graph())) return ToolResult.more(codedError("no_graph_change",
            "The batch changed no graph values or relationships. Use readback to choose a meaningful edit or finish an already validated draft."));
        state.workingGraph = mutation.graph();
        state.nodeReferences.clear();
        state.nodeReferences.putAll(mutation.references());
        state.validated = false;
        state.previewed = false;
        state.draftRevision++;
        JsonObject result = ok(mutation.message());
        result.addProperty("draftRevision", state.draftRevision);
        result.addProperty("nodeCount", state.workingGraph.getAsJsonArray("nodes").size());
        result.addProperty("connectionCount", state.workingGraph.getAsJsonArray("connections").size());
        result.add("resolvedRefs", GSON.toJsonTree(state.nodeReferences));
        result.add("effects", mutation.effects());
        result.add("actualValues", mutation.actualValues());
        return ToolResult.more(result);
    }

    private static ToolResult validateGraph(State state) {
        if (!state.changesGraph()) return ToolResult.more(error("Select target new or current before validation."));
        NodeGraphData graph = parseGraph(state.workingGraph);
        AiPresetService.Proposal temporary = new AiPresetService.Proposal(
            "Draft", "", List.of(), graph, state.target);
        AiPresetService.Validation validation = AiPresetService.validateProposal(temporary, state.activePresetName,
            state.baritoneAvailable, state.uiUtilsAvailable);
        JsonArray semanticIssues = requirementIssues(state, graph);
        if (state.plan != null) AiStructuralRequirements.verify(graph, state.plan.getAsJsonArray("structuralRequirements"), state.nodeReferences).forEach(semanticIssues::add);
        state.validated = validation.valid() && semanticIssues.asList().stream()
            .noneMatch(issue -> !"warning".equals(string(issue.getAsJsonObject(), "severity", "error")));
        state.previewed = state.validated;
        state.repairRound = state.validated ? 0 : state.repairRound + 1;
        if (!state.validated) state.validationFailures++;
        JsonObject result = new JsonObject();
        result.addProperty("ok", state.validated);
        result.addProperty("message", state.validated
            ? "The draft passes structural validation. Recorded parameter requirements were checked; runtime-dependent values remain unverified. This does not prove request completeness or world execution."
            : "Repair the listed structural or requirement issues, then validate again.");
        JsonArray issues = new JsonArray();
        java.util.Set<String> inspectRefs = new java.util.LinkedHashSet<>();
        java.util.Set<String> operations = new java.util.LinkedHashSet<>();
        validation.issues().forEach(issue -> {
            JsonObject described = AiGraphRepairAdvisor.describe(issue, graph, state.nodeReferences);
            issues.add(described);
            described.getAsJsonArray("inspectRefs").forEach(value -> inspectRefs.add(value.getAsString()));
            described.getAsJsonArray("suggestedOperations").forEach(value -> operations.add(value.getAsString()));
        });
        for (JsonElement element : semanticIssues) {
            JsonObject described = element.getAsJsonObject();
            issues.add(described);
            described.getAsJsonArray("inspectRefs").forEach(value -> inspectRefs.add(value.getAsString()));
            described.getAsJsonArray("suggestedOperations").forEach(value -> operations.add(value.getAsString()));
        }
        result.add("issues", issues);
        result.add("inspectRefs", GSON.toJsonTree(inspectRefs));
        result.add("suggestedRepairOperations", GSON.toJsonTree(operations));
        result.addProperty("repairRound", state.repairRound);
        if (!state.validated) result.addProperty("next", "Inspect only inspectRefs if more context is needed, apply the smallest suggested repair, then validate again.");
        result.addProperty("draftRevision", state.draftRevision);
        if (state.validated) result.add("preview", AiExecutionPreview.preview(graph));
        return ToolResult.more(result);
    }

    private static JsonArray requirementIssues(State state, NodeGraphData graph) {
        JsonArray issues = new JsonArray();
        if (state.plan == null || !state.plan.has("requirements") || !state.plan.get("requirements").isJsonArray()) return issues;
        for (JsonElement element : state.plan.getAsJsonArray("requirements")) {
            JsonObject requirement = element.getAsJsonObject();
            String ref = requirement.get("ref").getAsString();
            String expectedType = requirement.get("nodeType").getAsString();
            String parameterId = requirement.get("parameterId").getAsString();
            String expected = requirement.get("value").getAsString();
            String nodeId = state.nodeReferences.getOrDefault(ref, ref);
            NodeGraphData requirementScope;
            try { requirementScope = AiGraphScope.resolve(graph, nullableString(requirement, "graphRef"), state.nodeReferences); }
            catch (IllegalArgumentException failure) { requirementScope = new NodeGraphData(); requirementScope.setNodes(List.of()); }
            NodeGraphData.NodeData node = requirementScope.getNodes().stream().filter(candidate -> candidate != null
                && nodeId.equals(candidate.getId())).findFirst().orElse(null);
            String actual = null;
            String code = "requirement_mismatch";
            String message;
            JsonArray suggested = new JsonArray();
            if (node == null) {
                code = "required_node_missing";
                message = "Required " + expectedType + " node '" + ref + "' is missing.";
                suggested.add("add_node");
            } else if (!expectedType.equals(node.getType().name())) {
                code = "required_node_type_mismatch";
                message = "Node '" + ref + "' must be " + expectedType + " but is " + node.getType() + ".";
                suggested.add("replace_subgraph");
            } else {
                String normalized = com.pathmind.nodes.NodeParameter.createDefaultId(parameterId);
                NodeGraphData.ParameterData parameter = node.getParameters() == null ? null : node.getParameters().stream()
                    .filter(candidate -> candidate != null && normalized.equals(
                        com.pathmind.nodes.NodeParameter.createDefaultId(candidate.getId())))
                    .findFirst().orElse(null);
                AiConfiguredValues.Value configured = AiConfiguredValues.read(requirementScope, node, parameterId);
                actual = configured.effective();
                boolean matches = expected.equals(actual);
                if (configured.staticallyKnown() && actual != null) {
                    try {
                        matches = AiParameterValidator.validate(node.getType(), node.getMode(), parameterId, expected).value()
                            .equals(AiParameterValidator.validate(node.getType(), node.getMode(), parameterId, actual).value());
                    } catch (IllegalArgumentException ignored) { matches = false; }
                }
                boolean modeMatches = !requirement.has("mode") || requirement.get("mode").getAsString().equals(
                    node.getMode() == null ? "" : node.getMode().name());
                if (!modeMatches) matches = false;
                if (matches && configured.staticallyKnown()) continue;
                if (!modeMatches) code = "required_node_mode_mismatch";
                else if (!configured.staticallyKnown()) code = "requirement_runtime_dependent";
                message = "Required " + ref + "." + parameterId + " = '" + expected
                    + "', but the draft contains '" + (actual == null ? "<missing>" : actual) + "'.";
                if (!modeMatches) {
                    message = "Required " + ref + " mode = '" + requirement.get("mode").getAsString() + "', but the draft uses " + node.getMode() + ".";
                    suggested.add("configure_node");
                } else if (!configured.staticallyKnown()) message = "Required " + ref + "." + parameterId + " = '" + expected
                    + "' cannot be verified statically because its input is runtime-dependent. Review or test it in game; this is not a proven mismatch.";
                else suggested.add("set_parameters");
            }
            JsonObject issue = new JsonObject();
            issue.addProperty("code", code);
            issue.addProperty("severity", "requirement_runtime_dependent".equals(code) ? "warning" : "error");
            issue.addProperty("message", message);
            issue.addProperty("nodeRef", ref);
            issue.addProperty("nodeId", nodeId);
            issue.addProperty("expectedNodeType", expectedType);
            issue.addProperty("parameterId", parameterId);
            issue.addProperty("expected", expected);
            if (actual == null) issue.add("actual", null); else issue.addProperty("actual", actual);
            issue.add("suggestedOperations", suggested);
            JsonArray inspect = new JsonArray();
            inspect.add(ref);
            issue.add("inspectRefs", inspect);
            issues.add(issue);
        }
        return issues;
    }

    private static ToolResult previewExecution(State state) {
        if (!state.changesGraph()) return ToolResult.more(error("Select target new or current before previewing."));
        if (!state.validated) return ToolResult.more(error("Validate the current draft before previewing it."));
        state.previewed = true;
        JsonObject result = ok("Generated a structural preview without executing world actions.");
        result.add("preview", AiExecutionPreview.preview(parseGraph(state.workingGraph)));
        return ToolResult.more(result);
    }

    private static ToolResult finish(State state, JsonObject action) {
        if (state.intent == null) return ToolResult.more(codedError("intent_required", "Assess requestIntent from the latest request before finishing. Reading a preset does not choose permissions."));
        String responseError = responseLengthError(state.userPrompt, nullableString(action, "response"));
        if (responseError != null) return ToolResult.more(codedError("response_too_long", responseError));
        String completion = string(action, "completion", "complete");
        if ("clarification".equals(completion) && (state.draftRevision > 0 || state.graphRecoveryFailures > 0 || state.validationFailures > 0))
            return ToolResult.more(codedError("clarification_after_edit",
                "Clarification cannot be used to escape draft edits, validation failures, or internal tool errors. Repair the draft or use safe reversible defaults recorded as assumptions."));
        if ("clarification".equals(completion) && state.answeringClarification)
            return ToolResult.more(codedError("clarification_already_answered",
                "The latest user message answers or delegates the previous clarification. Continue the inherited task using that answer; do not ask another question."));
        if ("clarification".equals(completion) || "blocked".equals(completion) || state.intent == AiRequestIntent.CLARIFY) {
            if (nullableString(action, "completionReason") == null || nullableString(action, "completionReason").isBlank()
                || nullableString(action, "response") == null || nullableString(action, "response").isBlank())
                return ToolResult.more(codedError("missing_completion_reason", "A clarification/blocker needs a specific completionReason and user-visible response. Inspect mode is not a blocker."));
            if (!"blocked".equals(completion) && !nullableString(action, "response").contains("?"))
                return ToolResult.more(codedError("clarification_question_required", "Clarification must ask one actual unresolved question, not describe an edit you can already perform. Otherwise continue the draft."));
            if ("blocked".equals(completion)) {
                Integer blockingTurn = nullableInteger(action, "blockingToolTurn");
                boolean confirmed = blockingTurn != null && state.trace.stream().anyMatch(step -> step.turn() == blockingTurn && !step.success()
                    && List.of("context_unavailable", "capability_unavailable").contains(step.code()));
                if (!confirmed) return ToolResult.more(codedError("unconfirmed_blocker", "A blocker must cite blockingToolTurn from a real failed context/capability/validation result. A rejected graph command is recoverable and does not establish inability; inspect or repair and retry."));
            }
            return ToolResult.done(proposal(state, action, null, "inspect"));
        }
        if (!"complete".equals(completion)) return ToolResult.more(error("Unknown completion outcome."));
        if (!state.intent.permitsDraftEdits()) {
            if (state.intent == AiRequestIntent.DIAGNOSE && state.activeGraph != null && !state.inspected)
                return ToolResult.more(error("Inspect the current preset before giving a preset diagnosis. General discussion does not require inspection."));
            return ToolResult.done(proposal(state, action, null, "inspect"));
        }
        String permissionError = draftPermissionError(state);
        if (permissionError != null) return ToolResult.more(codedError("proposal_required", permissionError));
        if (state.draftRevision == 0) return ToolResult.more(codedError("proposal_required", "An edit/build request needs actual draft edits and a validated proposal, not read-only advice. Build the draft, ask clarification, or return a specific blocker."));
        if ("current".equals(state.target) && graphJson(state.activeGraph).equals(state.workingGraph))
            return ToolResult.more(codedError("proposal_required", "The current draft is unchanged. Do not present it as a completed edit; implement the requested change or ask clarification."));
        if (!state.validated) return ToolResult.more(error("The current draft must pass validate_graph after its last patch."));
        if (!state.previewed) return ToolResult.more(error("Call preview_execution after validation before finishing."));
        return ToolResult.done(proposal(state, action, parseGraph(state.workingGraph), state.target));
    }

    private static AiPresetService.Proposal proposal(State state, JsonObject action, NodeGraphData graph, String target) {
        String title = shortText(nullableString(action, "title"), 80);
        if (title.isBlank()) title = "Untitled AI preset";
        String response = AiDisplayText.message(nullableString(action, "response"));
        if ("blocked".equals(string(action, "completion", "complete"))) {
            int blockingTurn = nullableInteger(action, "blockingToolTurn");
            String reason = state.trace.stream().filter(step -> step.turn() == blockingTurn && !step.success())
                .map(AiToolTrace::message).findFirst().orElse("Required context or capability is unavailable.");
            response = "I couldn't prepare a proposal: " + reason + " No reviewable proposal or preset changes were produced.";
        }
        List<String> workLog = state.trace.stream().filter(entry -> entry.success()
            && !entry.tool().equals("finish") && !entry.tool().equals("select_target") && !entry.tool().equals("assess_request"))
            .map(entry -> entry.message()).distinct().toList();
        String sourceFingerprint = "current".equals(target)
            ? AiPresetService.graphFingerprint(state.activePresetName, state.activeGraph) : "";
        AiProposalReview review = graph == null ? null : AiProposalReview.create(
            "current".equals(target) ? state.activeGraph : null, graph);
        String completion = string(action, "completion", "complete");
        AiCompletionOutcome outcome = graph != null ? AiCompletionOutcome.PROPOSAL
            : state.intent == AiRequestIntent.CLARIFY || completion.equals("clarification") ? AiCompletionOutcome.CLARIFICATION
            : completion.equals("blocked") ? AiCompletionOutcome.BLOCKED : AiCompletionOutcome.ANSWER;
        return new AiPresetService.Proposal(title, response, List.copyOf(workLog), graph, target,
            sourceFingerprint, review, outcome);
    }

    private static boolean containsAvailableType(State state, NodeType type) {
        for (JsonElement element : AiPresetContextBuilder.availableNodeContracts(state.baritoneAvailable, state.uiUtilsAvailable)) {
            if (type.name().equals(element.getAsJsonObject().get("type").getAsString())) return true;
        }
        return false;
    }

    private static JsonArray compactNodeIndex(boolean baritoneAvailable, boolean uiUtilsAvailable) {
        JsonArray index = new JsonArray();
        for (JsonElement element : AiPresetContextBuilder.availableNodeContracts(baritoneAvailable, uiUtilsAvailable)) {
            JsonObject contract = element.getAsJsonObject();
            JsonObject summary = new JsonObject();
            summary.add("type", contract.get("type"));
            summary.add("name", contract.get("name"));
            summary.add("description", contract.get("description"));
            summary.add("category", contract.get("category"));
            index.add(summary);
        }
        return index;
    }

    private static NodeGraphData parseGraph(JsonObject json) {
        NodeGraphData graph = NodeGraphPersistence.parseNodeGraphData(json.toString());
        if (graph == null) throw new IllegalArgumentException("Graph JSON could not be parsed.");
        return graph;
    }

    private static JsonObject graphJson(NodeGraphData graph) {
        return GSON.toJsonTree(graph).getAsJsonObject();
    }

    private static JsonObject emptyGraph() {
        JsonObject graph = new JsonObject();
        graph.add("nodes", new JsonArray());
        graph.add("connections", new JsonArray());
        graph.add("customNodeDefinition", null);
        graph.add("routines", new JsonArray());
        return graph;
    }

    private static JsonObject parseObject(String content) {
        String value = content == null ? "" : content.trim();
        if (value.startsWith("```")) {
            int newline = value.indexOf('\n');
            int closing = value.lastIndexOf("```");
            if (newline >= 0 && closing > newline) value = value.substring(newline + 1, closing).trim();
        }
        JsonElement parsed = JsonParser.parseString(value);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Agent action must be a JSON object.");
        return parsed.getAsJsonObject();
    }

    private static String systemPrompt(AiProviderCapabilities capabilities) {
        String prompt = "You are Pathmind's graph agent. Work through one tool action per response. Never emit a complete graph directly. "
            + intentInstructions()
            + "For existing nodes, find_nodes/inspect_subgraph return actual instance parameters and parameterAttachments with sourceNodeId and sourceParameters. Type contracts describe canonical nodes, not necessarily every saved instance. Edit only the requested fields; configure an attached source by its own ref when it supplies the value. On rejection, use instanceContext to correct the target or parameter rather than repeating the same command. "
            + "Available tools: inspect_preset returns the exact open graph and draft; list_node_types lists creatable types; describe_node_types accepts nodeTypes and returns exact sockets, modes, parameters, attachment contracts, and relevant examples; "
            + "list_examples returns a compact index or ranks at most four examples by nodeTypes and exampleTraits; inspect_example returns one exact serialized example. Retrieve examples only for unfamiliar or structurally relevant concepts; do not inspect unrelated examples. "
            + "find_nodes searches the draft by type or text and inspect_subgraph returns only a bounded neighborhood; use these instead of repeatedly requesting the complete preset. plan_graph records a short structural plan and is required before the first edit to a new or current graph. "
            + "apply_graph_commands mutates the isolated draft. Primitive commands are add_node, set_mode, set_parameter, set_parameters, configure_node, connect, disconnect, attach_action, attach_sensor, attach_parameter, detach_action, detach_sensor, detach_parameter, and remove_node. Composition commands are add_sequence, insert_sequence_after, wrap_in_repeat, wrap_in_condition, create_branch, declare_variable, declare_list, clone_subgraph, replace_subgraph, create_routine, add_routine_call, and auto_layout. Pathmind owns real node IDs, typed values, defaults, serialization, rewiring, routine identities, and bidirectional attachment fields. validate_graph runs Pathmind's real validators and checks the finished graph against planRequirements; preview_execution returns bounded structural paths; finish returns the reviewed result. "
            + "For add_node choose a short ref and nodeType. Later commands use that ref; Pathmind returns resolvedRefs with generated IDs. Prefer configure_node to set mode and related parameterValues atomically. Partial updates preserve unrelated values. Follow each parameter's valueContract, including its format, minimum, and unit. Never combine a quantity with an identifier. Read actualValues after every patch instead of assuming values were accepted. connect uses from, to, outputSocket, and inputSocket. Attachments use host and child; attach_parameter also uses slotIndex. Set fields unused by a command to null. Every command batch must include the latest draftRevision from a tool result. "
            + "Before editing, call plan_graph once with an outcome-focused goal, 1-8 structural steps, relevant node types and structures, only necessary assumptions, and one planRequirements entry for every explicit behavior-shaping value in the user's request. Requirements name the intended ref, nodeType, exact parameterId, and one typed value; capture separate fields separately, preserving explicit units and quantities. Requirements check declared intent, not proof that the intent was extracted correctly. This is a concise architecture plan, not hidden reasoning. Use add_sequence for a new chain. Use insert_sequence_after with an anchor ref, outputSocket, ordered nodeTypes, and matching refs when adding behavior after an existing node; it atomically preserves the old successor. An occupied socket is a recoverable editing conflict: inspect or use the splice command, never finish blocked because of it. Wrappers accept one ordered connected refs selection; wrap_in_repeat also needs count and wrap_in_condition needs an existing sensor ref. create_branch accepts a sensor plus ordered trueRefs and falseRefs. clone_subgraph maps refs to newRefs; replace_subgraph rewires one-entry/one-exit refs to replacementRefs. create_routine extracts ordered refs into a definition, creates a call at their old location, and accepts typed routineInputs; an input can set bindToRef plus slotIndex to create and attach a typed reporter inside the routine body. add_routine_call reuses its routineRef, and attach_parameter supplies call arguments. End substantial construction with auto_layout. "
            + "Validation issues include expected structure, actual state, related nodes, suggested semantic operations, and focused inspectRefs. Repair only the reported relationship, then validate again; use disconnect or detach commands before replacing occupied relationships. "
            + "Use the supplied node index instead of calling list_node_types unless the index is insufficient. Inspect all relevant contracts in one batched call. Prefer one coherent command batch when possible. After commands, validate and repair every error. Successful validation includes preview_execution output, so finish immediately unless repair is needed. "
            + "Never claim a tool succeeded until its result says ok. Do not reveal hidden reasoning; workLog contains only concise user-visible actions. "
            + "Use graphRef=null for the root graph, or a routine ID/alias on commands, focused queries and requirements to edit a routine body. Never connect nodes across graph scopes. bind_node_ref binds an inspected existing nodeId to an alias without editing; bind existing aliases before planning, rather than inventing unbound symbolic helper nodes. "
            + "Record structuralRequirements for explicit nodes, ordered flow edges (ref,toRef,outputSocket,inputSocket) and action/sensor/parameter attachments (ref host,toRef child,slotIndex for parameter). Capture actual required relationships, not merely parameter values. After validation fails, plan_graph may correct mistaken implementation refs, scopes, slots and sockets with planCorrectionReason, at most twice; the goal, structural kinds/node types, and every typed outcome must remain unchanged. Prefer repairing the draft or reference binding when the plan is sound. Declared checks still cannot prove complete intent extraction. "
            + "Runtime-dependent requirements are warnings, not evidence of incorrect behavior. Never describe unverified values as verified. Internal validation or reference errors are repairable and cannot establish a user-facing blocker. ";
        if (capabilities != null && capabilities.nativeFunctionTools())
            return prompt + "Use exactly one native function per turn with only the fields in its function schema. Never emit a JSON action envelope as plain text.";
        prompt += "Every response must match the action schema; target is never null, unused nullable fields are null, and unused arrays are [].";
        if (capabilities == null || !capabilities.structuredOutput()) {
            prompt += " This transport cannot enforce the schema, so follow this exact schema:\n" + AiAgentTurnSchema.create();
        }
        return prompt;
    }

    private static JsonObject ok(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.addProperty("message", message);
        return result;
    }

    private static String diagnosticResult(JsonObject result) {
        String message = AiDisplayText.diagnostic(string(result, "message", "Tool result returned."));
        if (!result.has("issues") || !result.get("issues").isJsonArray() || result.getAsJsonArray("issues").isEmpty()) return message;
        StringBuilder details = new StringBuilder(message);
        int shown = 0;
        for (JsonElement value : result.getAsJsonArray("issues")) {
            if (!value.isJsonObject() || shown++ >= 4) break;
            JsonObject issue = value.getAsJsonObject();
            details.append(" ").append(shown).append(") ")
                .append(AiDisplayText.diagnostic(string(issue, "message", string(issue, "code", "Validation issue."))));
        }
        int remaining = result.getAsJsonArray("issues").size() - shown;
        if (remaining > 0) details.append(" (+").append(remaining).append(" more)");
        return details.toString();
    }

    /** True only when the latest application-authored request outcome is a clarification awaiting this reply. */
    static boolean pendingClarification(String context) {
        if (context == null || context.isBlank()) return false;
        try {
            JsonObject root = JsonParser.parseString(context).getAsJsonObject();
            JsonObject history = root.has("conversation") && root.get("conversation").isJsonObject()
                ? root.getAsJsonObject("conversation") : root;
            if (!history.has("messages") || !history.get("messages").isJsonArray()) return false;
            JsonArray messages = history.getAsJsonArray("messages");
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (!messages.get(i).isJsonObject()) continue;
                JsonObject entry = messages.get(i).getAsJsonObject();
                if (!"event".equalsIgnoreCase(string(entry, "role", ""))) continue;
                return string(entry, "text", "").startsWith("Request outcome: CLARIFICATION.");
            }
        } catch (RuntimeException ignored) { /* Unstructured legacy context cannot establish pending state. */ }
        return false;
    }

    private static String intentInstructions() {
        return "Separate latest-request intent, graph scope, and permissions. On the first useful call, set requestIntent to discuss (ideas/questions), diagnose (investigate a preset), build (create standalone preset), edit (explicit change to open preset), or clarify (genuinely unresolved intent/scope). "
            + "Provide intentEvidence as an exact quote from USER_REQUEST, not old conversation. Null intent fields on later calls preserve the decision. assess_request is optional if another useful call supplies the assessment. "
            + "Interpret the latest message in conversation context, not in isolation. When answeringPreviousClarification=true, the latest message resolves or delegates the prior choice: inherit the earlier task, classify it as build/edit/discuss/diagnose rather than clarify, and continue without asking them to restate it. Phrases that delegate choice authorize safe reversible defaults, not another question. intentEvidence still quotes the latest reply. Do not carry forward an old permission mode or treat an old proposal as applied. "
            + "Inspecting is a read action, not a permission or persistent mode. Read tools can run before intent/scope selection. target inspect or undecided is neutral. Select current for edit or new for build before planning; scope can change before the first successful draft edit, then freezes. "
            + "Do not escalate discussion/diagnosis into edits. 'What do you think?' calls for an answer; 'after it jumps, create a variable...' calls for an edit even if inspecting is your first action. Judge intent semantically, never use prompt-to-graph templates. "
            + "Discussion may finish without preset inspection. Diagnose an open preset only after inspection. Build/edit must return actual draft edits validated for review, not instructions for the user to implement. "
            + "Reuse tool results while the draft revision is unchanged. A repeated read, assessment, or failed finish is not progress. When a tool fails, use its error to change the arguments or satisfy the missing prerequisite; do not retry the identical call. A valid unchanged draft needs finish, not another validation or preview. "
            + "Prefer progress over clarification when the requested outcome is clear. Use fresh workspace selection, relevant node contracts, existing preset settings, earlier user messages, and safe node defaults to resolve implementation details. Record reasonable assumptions in planAssumptions and mention important ones in the review. "
            + "Ask one narrow question only when missing information materially changes the requested behavior or graph target and cannot be resolved from available context. Do not ask about node wiring, layout, variable names, routine names, or other reversible implementation choices. Do not silently change requested units or invent unsupported behavior. A request such as 'extend this preset' needs clarification only if neither the latest message nor earlier user context specifies the intended outcome. "
            + "Ask before editing or attempting repairs. Once draft editing, validation, or internal recovery starts, clarification is not an escape hatch: repair the isolated draft and apply safe reversible defaults, or report a confirmed capability/context blocker. "
            + "Before asking, check USER_REQUEST, conversation messages, olderUserMessages, and workspace facts for an existing answer. User statements resolve requirements; assistant questions or suggestions do not establish user decisions. Newer user corrections supersede older answers. Do not re-ask a resolved question or request confirmation of a choice the user already made. "
            + "For genuinely essential missing information finish with completion clarification, a specific completionReason explaining why no safe assumption works, and one concise question. For an actual blocker finish blocked with a specific reason and blockingToolTurn citing a confirmed failed tool result. Inspect/permission selection is not a blocker; recover instead. "
            + "finish completion complete (or null) returns an answer or validated proposal. workLog is ignored and generated from actual tool results. Never claim the live preset changed; confirmation is required. "
            + "Keep user-visible replies short and simple. Default to 1-3 short sentences, usually under 60 words, in plain language. Lead with the answer or proposed change and include only an essential caveat or next step. Do not repeat the user's request, narrate tool calls, explain node wiring, or duplicate the execution preview and collapsible Details. Avoid headings, numbered walkthroughs, jargon, and unsolicited offers to continue. Clarifications are one concise question; blockers are a brief specific reason and actionable next step. Give longer explanations only when the user explicitly asks for detail or brevity would hide an important limitation. This length guidance applies to response text, not graph commands, validation, or the correctness of the draft. ";
    }

    private static JsonObject error(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("ok", false);
        result.addProperty("message", message == null || message.isBlank() ? "Tool call failed." : message);
        return result;
    }

    private static JsonObject codedError(String code, String message) {
        JsonObject result = error(message); result.addProperty("code", code); return result;
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    private static String nullableString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    private static Integer nullableInteger(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : null;
    }

    private static String actionKey(JsonObject action, int revision) {
        JsonObject semantic = action.deepCopy();
        for (String field : List.of("response", "workLog", "intentEvidence", "requestIntent", "completionReason", "title")) semantic.remove(field);
        return revision + ":" + semantic;
    }

    private static String shortText(String value, int limit) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= limit ? normalized : normalized.substring(0, Math.max(1, limit - 1)) + "…";
    }

    private static String responseLengthError(String request, String response) {
        if (response == null || response.isBlank()) return null;
        String lower = request == null ? "" : request.toLowerCase(Locale.ROOT);
        boolean requestedDetail = List.of("detailed", "in detail", "thorough", "complete explanation", "at least ",
            "step by step", "walkthrough", "deep dive", "long answer").stream().anyMatch(lower::contains);
        int maxChars = requestedDetail ? 4_000 : 600;
        int maxWords = requestedDetail ? 650 : 90;
        int words = response.strip().split("\\s+").length;
        if (response.length() <= maxChars && words <= maxWords) return null;
        return requestedDetail
            ? "The user-visible reply is too long. Retry finish under 4,000 characters and 650 words while preserving the requested detail."
            : "The user-visible reply is too long. Retry finish with 1-3 short sentences under 600 characters and 90 words. Put graph details in the review.";
    }

    private static final class State {
        private final AiProvider provider;
        private final AiProviderSession session;
        private final long startedNanos = System.nanoTime();
        private final List<AiTokenUsage> usages = new ArrayList<>();
        private final java.util.Map<String, Integer> toolCalls = new java.util.LinkedHashMap<>();
        private JsonObject pendingResult;
        private volatile boolean closed;
        private int repairAttempts, validationFailures, toolErrors;
        private String nativeInitialPrompt;
        private final String model;
        private final String userPrompt;
        private final String conversation;
        private final boolean answeringClarification;
        private final NodeGraphData activeGraph;
        private final String activePresetName;
        private final boolean baritoneAvailable;
        private final boolean uiUtilsAvailable;
        private final StringBuilder transcript = new StringBuilder();
        private final java.util.Map<String, String> nodeReferences = new java.util.LinkedHashMap<>();
        private String target;
        private AiRequestIntent intent;
        private AiRequestControl control = new AiRequestControl();
        private AiRequestProgress.Stage progressStage = AiRequestProgress.Stage.THINKING;
        private String progressMessage = "Understanding your request";
        private final List<AiToolTrace> trace = new ArrayList<>();
        private int planCorrections;
        private String currentTool = "unknown";
        private int toolRevisionBefore;
        private JsonObject workingGraph;
        private boolean inspected;
        private boolean planned;
        private boolean validated;
        private boolean previewed;
        private int draftRevision;
        private int planRevision;
        private int repairRound;
        private int graphRecoveryFailures;
        private JsonObject plan;
        private int turn;
        private int consecutiveFailures;
        private int repeatedActionCount;
        private final AiProgressTracker progressTracker = new AiProgressTracker();
        private String lastActionKey = "";
        private String lastFailureKey = "";

        private State(AiProvider provider, String model, String userPrompt, String conversation,
                      NodeGraphData activeGraph, String activePresetName,
                      boolean baritoneAvailable, boolean uiUtilsAvailable) {
            this.provider = provider;
            this.session = provider.openSession();
            this.model = model;
            this.userPrompt = userPrompt == null ? "" : userPrompt;
            this.conversation = conversation == null ? "" : conversation;
            this.answeringClarification = pendingClarification(this.conversation);
            this.activeGraph = activeGraph;
            this.activePresetName = activePresetName == null ? "" : activePresetName;
            this.baritoneAvailable = baritoneAvailable;
            this.uiUtilsAvailable = uiUtilsAvailable;
        }

        private boolean changesGraph() { return "new".equals(target) || "current".equals(target); }

        private void progress(AiRequestProgress.Stage stage, String message, AiToolTrace detail) {
            progressStage = stage; progressMessage = message;
            control.publish(new AiRequestProgress(stage, message, turn, detail));
        }

        private void publishToolProgress(String tool) {
            switch (tool) {
                case "inspect_preset", "find_nodes", "inspect_subgraph", "describe_node_types", "list_node_types", "list_examples", "inspect_example" -> progress(AiRequestProgress.Stage.INSPECTING, "Inspecting preset and node contracts", null);
                case "plan_graph" -> progress(AiRequestProgress.Stage.PLANNING, "Planning the graph structure", null);
                case "apply_graph_commands" -> progress(repairRound > 0 ? AiRequestProgress.Stage.REPAIRING : AiRequestProgress.Stage.BUILDING,
                    repairRound > 0 ? "Repairing the draft" : "Building the isolated draft", null);
                case "validate_graph", "preview_execution" -> progress(AiRequestProgress.Stage.VALIDATING, "Validating draft and execution structure", null);
                case "finish" -> progress(AiRequestProgress.Stage.THINKING, "Preparing the response", null);
                default -> { }
            }
        }

        private void clearFailureStreak() {
            consecutiveFailures = 0;
            lastFailureKey = "";
        }

        private String prompt() {
            if (provider.capabilities().nativeFunctionTools() && nativeInitialPrompt != null) return nativeInitialPrompt;
            StringBuilder prompt = new StringBuilder();
            prompt.append("USER_REQUEST:\n").append(userPrompt).append('\n');
            prompt.append("REQUEST_SCOPE:\n").append("hasOpenPreset=").append(activeGraph != null)
                .append("; presetName=").append(activePresetName).append("; answeringPreviousClarification=")
                .append(answeringClarification).append("; previous request permissions never carry forward.\n");
            if (!conversation.isBlank()) prompt.append("CONVERSATION_CONTEXT:\n").append(conversation).append('\n');
            prompt.append("CONTEXT_RULES:\nUse fresh workspace selection as the focus for references like 'that part'; inspect its subgraph before edits. Ask one narrow question only when materially different outcomes remain after checking earlier user answers and safe defaults. Recent chat is advisory, not current graph facts or permission. Application change receipts supersede old claims that a proposal is pending, applied, or discarded. No internal reasoning.\n");
            prompt.append("AVAILABLE_NODE_INDEX:\n")
                .append(compactNodeIndex(baritoneAvailable, uiUtilsAvailable)).append('\n');
            if (provider.capabilities().nativeFunctionTools()) {
                nativeInitialPrompt = prompt.toString();
                return nativeInitialPrompt;
            }
            prompt.append("TOOL_TRANSCRIPT:\n").append(transcript);
            return prompt.toString();
        }

        private void record(String label, JsonObject payload) {
            if ("TOOL_RESULT".equals(label) || "TOOL_ERROR".equals(label)) {
                pendingResult = payload == null ? error("No tool result was produced.") : payload.deepCopy();
                if (pendingResult.has("ok") && !pendingResult.get("ok").getAsBoolean()) toolErrors++;
                trace.add(new AiToolTrace(turn, currentTool, pendingResult.has("ok") && pendingResult.get("ok").getAsBoolean(),
                    string(pendingResult, "code", ""), diagnosticResult(pendingResult),
                    toolRevisionBefore, draftRevision));
                progress(progressStage, progressMessage, trace.get(trace.size() - 1));
            }
            transcript.append(label).append('[').append(turn).append("]:").append(payload).append('\n');
            if (transcript.length() > MAX_TRANSCRIPT_CHARS) {
                int remove = transcript.length() - MAX_TRANSCRIPT_CHARS;
                int newline = transcript.indexOf("\n", remove);
                transcript.delete(0, newline < 0 ? remove : newline + 1);
            }
        }
    }

    private record ToolResult(JsonObject payload, AiPresetService.Proposal proposal) {
        static ToolResult more(JsonObject payload) { return new ToolResult(payload, null); }
        static ToolResult done(AiPresetService.Proposal proposal) { return new ToolResult(null, proposal); }
    }
}
