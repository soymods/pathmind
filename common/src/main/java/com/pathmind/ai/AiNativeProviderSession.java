package com.pathmind.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Native function-call history, including opaque reasoning/signature items, owned by one request. */
final class AiNativeProviderSession implements AiProviderSession {
    enum Dialect { OPENAI, OPENAI_CHAT, ANTHROPIC, GEMINI }
    private static final int MAX_HISTORY_CHARS = 500_000;
    private final Dialect dialect;
    private final String endpoint, apiKey;
    private final AiJsonTransport transport;
    private final boolean storeOpenAiResponses;
    private final AiRoutingPreferences routing;
    private final JsonArray history = new JsonArray();
    private final List<Call> pendingCalls = new ArrayList<>();
    private String previousResponseId;
    private String target;
    private boolean started;
    private volatile boolean closed;
    private CompletableFuture<JsonObject> pendingResponse;

    AiNativeProviderSession(Dialect dialect, String endpoint, String apiKey, AiJsonTransport transport,
                            boolean storeOpenAiResponses) {
        this(dialect, endpoint, apiKey, transport, storeOpenAiResponses, AiRoutingPreferences.DEFAULT);
    }

    AiNativeProviderSession(Dialect dialect, String endpoint, String apiKey, AiJsonTransport transport,
                            boolean storeOpenAiResponses, AiRoutingPreferences routing) {
        this.dialect = dialect;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.transport = transport;
        this.storeOpenAiResponses = storeOpenAiResponses;
        this.routing = routing == null ? AiRoutingPreferences.DEFAULT : routing;
    }

    @Override
    public synchronized CompletableFuture<AiModelTurn> generate(AiPresetRequest request, JsonObject previousToolResult) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("AI session is closed."));
        JsonArray newInput = new JsonArray();
        if (!started) {
            newInput.add(userMessage(request.userPrompt()));
            started = true;
        } else {
            if (previousToolResult == null) return CompletableFuture.failedFuture(new IllegalStateException("Native continuation needs the previous tool result."));
            if (pendingCalls.isEmpty()) newInput.add(userMessage(previousToolResult.toString()));
            else appendToolResults(newInput, previousToolResult);
        }
        for (JsonElement element : newInput) history.add(element);
        pendingCalls.clear();
        if (history.toString().length() > MAX_HISTORY_CHARS) {
            return CompletableFuture.failedFuture(new IllegalStateException("AI native conversation exceeded its bounded context budget."));
        }
        JsonObject body = requestBody(request, newInput);
        String url = dialect == Dialect.GEMINI
            ? endpoint.replace("{model}", URLEncoder.encode(request.model(), StandardCharsets.UTF_8)) : endpoint;
        Map<String, String> headers = switch (dialect) {
            case OPENAI -> Map.of("Authorization", "Bearer " + apiKey);
            // X-Title is how an aggregator attributes usage; it carries no account data.
            case OPENAI_CHAT -> Map.of("Authorization", "Bearer " + apiKey, "X-Title", "Pathmind");
            case ANTHROPIC -> Map.of("x-api-key", apiKey, "anthropic-version", "2023-06-01");
            case GEMINI -> Map.of("x-goog-api-key", apiKey);
        };
        pendingResponse = transport.post(url, headers, body);
        return pendingResponse.thenApply(response -> {
            if (closed) throw new IllegalStateException("AI session is closed.");
            return accept(response);
        });
    }

    private JsonObject requestBody(AiPresetRequest request, JsonArray newInput) {
        JsonObject body = new JsonObject();
        if (dialect == Dialect.OPENAI) {
            body.addProperty("model", request.model());
            body.addProperty("instructions", request.systemPrompt());
            body.addProperty("store", storeOpenAiResponses);
            body.addProperty("max_output_tokens", 6000);
            body.addProperty("parallel_tool_calls", false);
            body.addProperty("tool_choice", "required");
            body.addProperty("prompt_cache_key", "pathmind-graph-tools-v5");
            if (request.model().startsWith("gpt-5.4") || request.model().startsWith("gpt-5.5")) {
                JsonObject text = new JsonObject();
                text.addProperty("verbosity", "low");
                body.add("text", text);
            }
            JsonArray include = new JsonArray();
            include.add("reasoning.encrypted_content");
            body.add("include", include);
            body.add("tools", nativeTools());
            if (storeOpenAiResponses && previousResponseId != null) {
                body.addProperty("previous_response_id", previousResponseId);
                body.add("input", newInput.deepCopy());
            } else body.add("input", history.deepCopy());
        } else if (dialect == Dialect.OPENAI_CHAT) {
            body.addProperty("model", request.model());
            body.addProperty("max_tokens", 6000);
            body.addProperty("parallel_tool_calls", false);
            body.addProperty("tool_choice", "required");
            // Chat Completions has no separate instructions field, so the system prompt leads every
            // request instead of living in history; that keeps it editable between turns.
            JsonArray messages = new JsonArray();
            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", request.systemPrompt());
            messages.add(system);
            messages.addAll(history.deepCopy());
            body.add("messages", messages);
            body.add("tools", nativeTools());
            if (!routing.isDefault()) body.add("provider", routing.toJson());
        } else if (dialect == Dialect.ANTHROPIC) {
            body.addProperty("model", request.model());
            body.addProperty("max_tokens", 6000);
            JsonObject system = textPart(request.systemPrompt());
            system.add("cache_control", ephemeralCache());
            JsonArray systemBlocks = new JsonArray();
            systemBlocks.add(system);
            body.add("system", systemBlocks);
            body.add("tools", nativeTools());
            JsonObject choice = new JsonObject();
            choice.addProperty("type", "any");
            choice.addProperty("disable_parallel_tool_use", true);
            body.add("tool_choice", choice);
            body.add("messages", history.deepCopy());
        } else {
            JsonObject system = new JsonObject();
            system.add("parts", parts(request.systemPrompt()));
            body.add("systemInstruction", system);
            body.add("contents", history.deepCopy());
            JsonObject tool = new JsonObject();
            tool.add("functionDeclarations", nativeTools());
            JsonArray tools = new JsonArray();
            tools.add(tool);
            body.add("tools", tools);
            JsonObject config = new JsonObject();
            config.addProperty("mode", "ANY");
            JsonObject toolConfig = new JsonObject();
            toolConfig.add("functionCallingConfig", config);
            body.add("toolConfig", toolConfig);
            JsonObject generation = new JsonObject();
            generation.addProperty("maxOutputTokens", 6000);
            body.add("generationConfig", generation);
        }
        return body;
    }

    private JsonArray nativeTools() {
        JsonArray tools = new JsonArray();
        for (JsonElement element : AiAgentToolDefinitions.create()) {
            JsonObject definition = element.getAsJsonObject();
            JsonObject tool = new JsonObject();
            tool.add("name", definition.get("name"));
            tool.add("description", definition.get("description"));
            if (dialect == Dialect.OPENAI) {
                tool.addProperty("type", "function");
                tool.addProperty("strict", true);
                tool.add("parameters", definition.get("parameters"));
            } else if (dialect == Dialect.OPENAI_CHAT) {
                tool.addProperty("strict", true);
                tool.add("parameters", definition.get("parameters"));
                JsonObject wrapped = new JsonObject();
                wrapped.addProperty("type", "function");
                wrapped.add("function", tool);
                tool = wrapped;
            } else if (dialect == Dialect.ANTHROPIC) {
                tool.addProperty("strict", true);
                tool.add("input_schema", definition.get("parameters"));
            } else tool.add("parametersJsonSchema", definition.get("parameters"));
            tools.add(tool);
        }
        if (dialect == Dialect.ANTHROPIC && !tools.isEmpty()) {
            tools.get(tools.size() - 1).getAsJsonObject().add("cache_control", ephemeralCache());
        }
        return tools;
    }

    private synchronized AiModelTurn accept(JsonObject response) {
        if (closed) throw new IllegalStateException("AI session is closed.");
        AiTokenUsage usage;
        try { usage = usage(response); }
        catch (RuntimeException malformedUsage) { usage = AiTokenUsage.UNKNOWN; }
        String invalidReason = null;
        try {
            if (dialect == Dialect.OPENAI) {
                previousResponseId = string(response, "id", null);
                JsonArray output = response.getAsJsonArray("output");
                for (JsonElement item : output) {
                    JsonObject object = item.getAsJsonObject();
                    history.add(object.deepCopy());
                    if ("function_call".equals(string(object, "type", ""))) {
                        pendingCalls.add(new Call(string(object, "call_id", ""), string(object, "name", ""),
                            string(object, "arguments", "{}")));
                    }
                }
                if (!"completed".equals(string(response, "status", "completed"))) invalidReason = "Provider response was incomplete; make a smaller function call.";
            } else if (dialect == Dialect.OPENAI_CHAT) {
                JsonObject choice = response.getAsJsonArray("choices").get(0).getAsJsonObject();
                JsonObject message = choice.getAsJsonObject("message");
                history.add(replayableAssistant(message));
                if (message.has("tool_calls") && !message.get("tool_calls").isJsonNull()) {
                    for (JsonElement item : message.getAsJsonArray("tool_calls")) {
                        JsonObject call = item.getAsJsonObject();
                        JsonObject function = call.getAsJsonObject("function");
                        pendingCalls.add(new Call(string(call, "id", ""), string(function, "name", ""),
                            string(function, "arguments", "{}")));
                    }
                }
                if ("length".equals(string(choice, "finish_reason", ""))) invalidReason = "Provider response reached its token limit; make a smaller function call.";
            } else if (dialect == Dialect.ANTHROPIC) {
                JsonArray content = response.getAsJsonArray("content");
                JsonObject assistant = new JsonObject();
                assistant.addProperty("role", "assistant");
                assistant.add("content", content.deepCopy());
                history.add(assistant);
                for (JsonElement item : content) {
                    JsonObject object = item.getAsJsonObject();
                    if ("tool_use".equals(string(object, "type", ""))) pendingCalls.add(new Call(
                        string(object, "id", ""), string(object, "name", ""), object.get("input").toString()));
                }
                if ("max_tokens".equals(string(response, "stop_reason", ""))) invalidReason = "Provider response reached its token limit; make a smaller function call.";
            } else {
                JsonObject candidate = response.getAsJsonArray("candidates").get(0).getAsJsonObject();
                JsonObject content = candidate.getAsJsonObject("content");
                history.add(content.deepCopy()); // Preserve Gemini thoughtSignature on every part unchanged.
                for (JsonElement item : content.getAsJsonArray("parts")) {
                    JsonObject part = item.getAsJsonObject();
                    if (part.has("functionCall")) {
                        JsonObject call = part.getAsJsonObject("functionCall");
                        pendingCalls.add(new Call(string(call, "id", ""), string(call, "name", ""), call.get("args").toString()));
                    }
                }
                if ("MAX_TOKENS".equals(string(candidate, "finishReason", ""))) invalidReason = "Provider response reached its token limit; make a smaller function call.";
            }
            if (invalidReason == null && pendingCalls.size() == 1) {
                Call call = pendingCalls.get(0);
                JsonObject action = AiAgentToolDefinitions.normalize(call.name(), JsonParser.parseString(call.arguments()).getAsJsonObject());
                if (action.has("target") && !action.get("target").isJsonNull()) target = action.get("target").getAsString();
                return new AiModelTurn(action.toString(), usage);
            }
            if (invalidReason == null) invalidReason = "Call exactly one native function per turn; no function from this response was executed.";
        } catch (RuntimeException exception) {
            invalidReason = "Provider returned an invalid native function call; retry with the declared schema.";
        }
        JsonObject action = new JsonObject();
        action.addProperty("tool", "invalid_provider_response");
        if (target != null) action.addProperty("target", target);
        action.addProperty("response", invalidReason);
        return new AiModelTurn(action.toString(), usage);
    }

    private AiTokenUsage usage(JsonObject response) {
        if (dialect == Dialect.OPENAI) {
            JsonObject usage = response.getAsJsonObject("usage");
            JsonObject details = usage == null ? null : usage.getAsJsonObject("input_tokens_details");
            return new AiTokenUsage(AiTokenUsage.number(usage, "input_tokens"), AiTokenUsage.number(usage, "output_tokens"),
                AiTokenUsage.number(details, "cached_tokens"), 0L);
        }
        if (dialect == Dialect.OPENAI_CHAT) {
            JsonObject usage = response.getAsJsonObject("usage");
            JsonObject details = usage == null ? null : usage.getAsJsonObject("prompt_tokens_details");
            return new AiTokenUsage(AiTokenUsage.number(usage, "prompt_tokens"), AiTokenUsage.number(usage, "completion_tokens"),
                AiTokenUsage.number(details, "cached_tokens"), 0L);
        }
        if (dialect == Dialect.ANTHROPIC) {
            JsonObject usage = response.getAsJsonObject("usage");
            Long uncached = AiTokenUsage.number(usage, "input_tokens");
            Long read = AiTokenUsage.number(usage, "cache_read_input_tokens");
            Long write = AiTokenUsage.number(usage, "cache_creation_input_tokens");
            // Anthropic input_tokens excludes cache reads/writes. Missing cache fields mean unavailable.
            Long input = uncached == null || read == null || write == null ? null : uncached + read + write;
            return new AiTokenUsage(input, AiTokenUsage.number(usage, "output_tokens"), read, write);
        }
        JsonObject usage = response.getAsJsonObject("usageMetadata");
        Long output = AiTokenUsage.number(usage, "candidatesTokenCount");
        Long thinking = AiTokenUsage.number(usage, "thoughtsTokenCount");
        if (output != null && thinking != null) output += thinking;
        return new AiTokenUsage(AiTokenUsage.number(usage, "promptTokenCount"), output,
            AiTokenUsage.number(usage, "cachedContentTokenCount"), 0L);
    }

    private void appendToolResults(JsonArray input, JsonObject result) {
        if (dialect == Dialect.OPENAI) {
            for (Call call : pendingCalls) {
                JsonObject output = new JsonObject();
                output.addProperty("type", "function_call_output");
                output.addProperty("call_id", call.id());
                output.addProperty("output", result.toString());
                input.add(output);
            }
        } else if (dialect == Dialect.OPENAI_CHAT) {
            // Chat Completions wants one top-level tool message per call, not a batched user turn.
            for (Call call : pendingCalls) {
                JsonObject message = new JsonObject();
                message.addProperty("role", "tool");
                message.addProperty("tool_call_id", call.id());
                message.addProperty("content", result.toString());
                input.add(message);
            }
        } else {
            JsonArray blocks = new JsonArray();
            for (Call call : pendingCalls) {
                JsonObject block = new JsonObject();
                if (dialect == Dialect.ANTHROPIC) {
                    block.addProperty("type", "tool_result");
                    block.addProperty("tool_use_id", call.id());
                    block.addProperty("content", result.toString());
                    block.addProperty("is_error", result.has("ok") && !result.get("ok").getAsBoolean());
                } else {
                    JsonObject function = new JsonObject();
                    function.addProperty("name", call.name());
                    if (!call.id().isBlank()) function.addProperty("id", call.id());
                    function.add("response", result.deepCopy());
                    block.add("functionResponse", function);
                }
                blocks.add(block);
            }
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.add(dialect == Dialect.ANTHROPIC ? "content" : "parts", blocks);
            input.add(message);
        }
    }

    /**
     * The assistant turn as it goes back on the wire.
     *
     * <p>Reasoning payloads are dropped. Chat Completions does not need them to continue a tool
     * conversation, unlike the Responses API, and a reasoning model emits an unbounded amount of
     * them; replaying every turn's thinking exhausted the session history budget within a dozen
     * turns. {@code tool_calls} must survive so the following tool results still pair by call id.</p>
     */
    private static JsonObject replayableAssistant(JsonObject message) {
        JsonObject replay = message.deepCopy();
        replay.remove("reasoning");
        replay.remove("reasoning_details");
        return replay;
    }

    private JsonObject userMessage(String text) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        if (dialect == Dialect.GEMINI) message.add("parts", parts(text));
        else message.addProperty("content", text);
        return message;
    }

    private static JsonObject textPart(String text) {
        JsonObject part = new JsonObject();
        part.addProperty("type", "text");
        part.addProperty("text", text);
        return part;
    }

    private static JsonArray parts(String text) {
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        parts.add(part);
        return parts;
    }

    private static JsonObject ephemeralCache() {
        JsonObject cache = new JsonObject();
        cache.addProperty("type", "ephemeral");
        return cache;
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    @Override public synchronized void close() {
        closed = true;
        if (pendingResponse != null && !pendingResponse.isDone()) pendingResponse.cancel(true);
        pendingResponse = null;
        while (!history.isEmpty()) history.remove(history.size() - 1);
        pendingCalls.clear();
        previousResponseId = null;
    }

    private record Call(String id, String name, String arguments) { }
}
