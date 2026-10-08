package com.pathmind.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiNativeProviderSessionTest {
    @Test void closingSessionCancelsPendingTransportForEveryNativeProvider() {
        for (var dialect : AiNativeProviderSession.Dialect.values()) {
            var pending = new CompletableFuture<JsonObject>();
            var session = new AiNativeProviderSession(dialect, "https://example.invalid", "test-key",
                (endpoint, headers, body) -> pending, false);
            var turn = session.generate(REQUEST, null);
            session.close();
            assertTrue(pending.isCancelled(), dialect.toString());
            assertTrue(turn.isCompletedExceptionally());
            assertThrows(java.util.concurrent.CompletionException.class, () -> session.generate(REQUEST, null).join());
        }
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static final AiPresetRequest REQUEST = new AiPresetRequest("instructions", "request", "model");
    private static final JsonObject RESULT = json("{\"ok\":true}");
    private static final String OPENAI = """
        {"id":"response-1","status":"completed","output":[
          {"type":"reasoning","encrypted_content":"opaque","summary":[]},
          {"type":"function_call","call_id":"call-1","name":"inspect_preset","arguments":"{\\"target\\":\\"inspect\\"}"}],
          "usage":{"input_tokens":100,"output_tokens":20,"input_tokens_details":{"cached_tokens":40}}}
        """;

    @Test void openAiReplaysOpaqueOutputAndPairsCallIdWithoutStorage() {
        List<JsonObject> bodies = new ArrayList<>();
        var session = session(AiNativeProviderSession.Dialect.OPENAI, bodies, OPENAI, false);
        var first = session.generate(REQUEST, null).join();
        assertEquals("inspect_preset", json(first.action()).get("tool").getAsString());
        assertEquals(40L, first.usage().cachedInputTokens());
        session.generate(REQUEST, RESULT).join();
        var body = bodies.get(1);
        assertFalse(body.get("store").getAsBoolean());
        assertFalse(body.has("previous_response_id"));
        var input = body.getAsJsonArray("input");
        assertEquals("opaque", input.get(1).getAsJsonObject().get("encrypted_content").getAsString());
        assertEquals("call-1", input.get(3).getAsJsonObject().get("call_id").getAsString());
        assertFalse(body.get("parallel_tool_calls").getAsBoolean());
        assertTrue(body.getAsJsonArray("tools").get(0).getAsJsonObject().get("strict").getAsBoolean());
        assertFalse(body.has("text"));
        session.close();
        assertThrows(java.util.concurrent.CompletionException.class, () -> session.generate(REQUEST, RESULT).join());
        assertEquals(2, bodies.size());
    }

    @Test void currentOpenAiModelsRequestLowVerbosity() {
        List<JsonObject> bodies = new ArrayList<>();
        var session = session(AiNativeProviderSession.Dialect.OPENAI, bodies, OPENAI, false);
        session.generate(new AiPresetRequest("instructions", "request", "gpt-5.4-mini"), null).join();
        assertEquals("low", bodies.getFirst().getAsJsonObject("text").get("verbosity").getAsString());
    }

    @Test void explicitlyStoredOpenAiSessionUsesOnlyNewToolResult() {
        List<JsonObject> bodies = new ArrayList<>();
        var session = session(AiNativeProviderSession.Dialect.OPENAI, bodies, OPENAI, true);
        session.generate(REQUEST, null).join(); session.generate(REQUEST, RESULT).join();
        assertEquals("response-1", bodies.get(1).get("previous_response_id").getAsString());
        assertEquals(1, bodies.get(1).getAsJsonArray("input").size());
    }

    @Test void anthropicPreservesThinkingAndUsesNativeToolResultsAndCacheBreakpoints() {
        List<JsonObject> bodies = new ArrayList<>();
        var session = session(AiNativeProviderSession.Dialect.ANTHROPIC, bodies, """
            {"content":[{"type":"thinking","thinking":"opaque","signature":"sig"},
            {"type":"tool_use","id":"tool-1","name":"inspect_preset","input":{"target":"inspect"}}],
            "usage":{"input_tokens":10,"output_tokens":20,"cache_read_input_tokens":30,"cache_creation_input_tokens":40}}
            """, false);
        var turn = session.generate(REQUEST, null).join(); session.generate(REQUEST, RESULT).join();
        assertEquals(80L, turn.usage().inputTokens());
        var messages = bodies.get(1).getAsJsonArray("messages");
        assertEquals("sig", messages.get(1).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject().get("signature").getAsString());
        assertEquals("tool-1", messages.get(2).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject().get("tool_use_id").getAsString());
        assertTrue(bodies.get(0).getAsJsonArray("system").get(0).getAsJsonObject().has("cache_control"));
    }

    @Test void geminiPreservesThoughtSignatureAndPairsNativeFunctionResponse() {
        List<JsonObject> bodies = new ArrayList<>();
        var session = session(AiNativeProviderSession.Dialect.GEMINI, bodies, """
            {"candidates":[{"content":{"role":"model","parts":[{"thoughtSignature":"opaque",
            "functionCall":{"id":"g-1","name":"inspect_preset","args":{"target":"inspect"}}}]}}],
            "usageMetadata":{"promptTokenCount":100,"candidatesTokenCount":20,"thoughtsTokenCount":30,"cachedContentTokenCount":40}}
            """, false);
        var turn = session.generate(REQUEST, null).join(); session.generate(REQUEST, RESULT).join();
        assertEquals(50L, turn.usage().outputTokens());
        var contents = bodies.get(1).getAsJsonArray("contents");
        assertEquals("opaque", contents.get(1).getAsJsonObject().getAsJsonArray("parts").get(0).getAsJsonObject().get("thoughtSignature").getAsString());
        assertEquals("g-1", contents.get(2).getAsJsonObject().getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonObject("functionResponse").get("id").getAsString());
    }

    private AiNativeProviderSession session(AiNativeProviderSession.Dialect dialect, List<JsonObject> bodies, String response, boolean store) {
        return new AiNativeProviderSession(dialect, "https://example.invalid/{model}", "test-key",
            transport(bodies, response), store);
    }

    private AiJsonTransport transport(List<JsonObject> bodies, String response) {
        return (url, headers, body) -> {
            assertFalse(url.contains("test-key"));
            bodies.add(body.deepCopy());
            return CompletableFuture.completedFuture(json(response));
        };
    }

    private static final String OPENAI_CHAT = """
        {"id":"gen-1","choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
          "reasoning_details":[{"type":"reasoning.encrypted","data":"opaque"}],
          "tool_calls":[{"id":"call-1","type":"function",
            "function":{"name":"inspect_preset","arguments":"{\\"target\\":\\"inspect\\"}"}}]}}],
          "usage":{"prompt_tokens":100,"completion_tokens":20,"prompt_tokens_details":{"cached_tokens":40}}}
        """;

    @Test void openAiChatDropsReasoningFromReplayedHistoryButKeepsToolCalls() {
        List<JsonObject> bodies = new ArrayList<>();
        var session = session(AiNativeProviderSession.Dialect.OPENAI_CHAT, bodies, OPENAI_CHAT, false);
        session.generate(REQUEST, null).join();
        session.generate(REQUEST, RESULT).join();
        var assistant = bodies.get(1).getAsJsonArray("messages").get(2).getAsJsonObject();
        // Unbounded per-turn reasoning would otherwise exhaust the session history budget.
        assertFalse(assistant.has("reasoning_details"));
        assertFalse(assistant.has("reasoning"));
        assertEquals("call-1", assistant.getAsJsonArray("tool_calls").get(0).getAsJsonObject().get("id").getAsString());
    }

    @Test void openAiChatPairsToolCallIds() {
        List<JsonObject> bodies = new ArrayList<>();
        var session = session(AiNativeProviderSession.Dialect.OPENAI_CHAT, bodies, OPENAI_CHAT, false);
        var first = session.generate(REQUEST, null).join();
        assertEquals("inspect_preset", json(first.action()).get("tool").getAsString());
        assertEquals(100L, first.usage().inputTokens());
        assertEquals(40L, first.usage().cachedInputTokens());
        session.generate(REQUEST, RESULT).join();
        var messages = bodies.get(1).getAsJsonArray("messages");
        assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("request", messages.get(1).getAsJsonObject().get("content").getAsString());
        var toolMessage = messages.get(3).getAsJsonObject();
        assertEquals("tool", toolMessage.get("role").getAsString());
        assertEquals("call-1", toolMessage.get("tool_call_id").getAsString());
        assertEquals(RESULT.toString(), toolMessage.get("content").getAsString());
        var tool = bodies.getFirst().getAsJsonArray("tools").get(0).getAsJsonObject();
        assertEquals("function", tool.get("type").getAsString());
        assertTrue(tool.getAsJsonObject("function").get("strict").getAsBoolean());
        assertEquals("required", bodies.getFirst().get("tool_choice").getAsString());
        assertFalse(bodies.getFirst().get("parallel_tool_calls").getAsBoolean());
        assertFalse(bodies.getFirst().has("provider"));
    }

    @Test void openRouterRoutingPreferencesTravelInTheProviderBlock() {
        List<JsonObject> bodies = new ArrayList<>();
        var session = new AiNativeProviderSession(AiNativeProviderSession.Dialect.OPENAI_CHAT,
            "https://example.invalid", "test-key", transport(bodies, OPENAI_CHAT), false,
            new AiRoutingPreferences("throughput", false));
        session.generate(REQUEST, null).join();
        var routing = bodies.getFirst().getAsJsonObject("provider");
        assertEquals("throughput", routing.get("sort").getAsString());
        assertFalse(routing.get("allow_fallbacks").getAsBoolean());
    }

    @Test void openAiChatTruncationAsksForASmallerCall() {
        var response = json(OPENAI_CHAT);
        response.getAsJsonArray("choices").get(0).getAsJsonObject().addProperty("finish_reason", "length");
        var session = session(AiNativeProviderSession.Dialect.OPENAI_CHAT, new ArrayList<>(), response.toString(), false);
        var action = json(session.generate(REQUEST, null).join().action());
        assertEquals("invalid_provider_response", action.get("tool").getAsString());
        assertTrue(action.get("response").getAsString().contains("token limit"));
    }

    @Test void openAiChatAcknowledgesEveryParallelToolCallBeforeRecovery() {
        var response = json(OPENAI_CHAT);
        var calls = response.getAsJsonArray("choices").get(0).getAsJsonObject()
            .getAsJsonObject("message").getAsJsonArray("tool_calls");
        var second = calls.get(0).deepCopy().getAsJsonObject();
        second.addProperty("id", "call-2");
        calls.add(second);
        List<JsonObject> bodies = new ArrayList<>();
        var session = session(AiNativeProviderSession.Dialect.OPENAI_CHAT, bodies, response.toString(), false);
        assertEquals("invalid_provider_response", json(session.generate(REQUEST, null).join().action()).get("tool").getAsString());
        session.generate(REQUEST, json("{\"ok\":false}")).join();
        var messages = bodies.get(1).getAsJsonArray("messages");
        assertEquals("call-1", messages.get(3).getAsJsonObject().get("tool_call_id").getAsString());
        assertEquals("call-2", messages.get(4).getAsJsonObject().get("tool_call_id").getAsString());
    }

    @Test void perToolAndCommandSchemasExcludeUnrelatedFields() {
        for (var element : AiAgentToolDefinitions.create()) {
            var tool = element.getAsJsonObject();
            var schema = tool.getAsJsonObject("parameters");
            assertFalse(schema.get("additionalProperties").getAsBoolean());
            assertEquals(schema.getAsJsonObject("properties").size(), schema.getAsJsonArray("required").size());
            if (tool.get("name").getAsString().equals("inspect_preset"))
                assertEquals(3, schema.getAsJsonObject("properties").size());
            if (tool.get("name").getAsString().equals("apply_graph_commands")) {
                var alternatives = schema.getAsJsonObject("properties").getAsJsonObject("commands")
                    .getAsJsonObject("items").getAsJsonArray("anyOf");
                assertTrue(alternatives.size() > 20);
                var addNode = alternatives.get(0).getAsJsonObject().getAsJsonObject("properties");
                assertEquals(4, addNode.size());
                assertTrue(addNode.has("graphRef"));
                assertFalse(addNode.has("routineInputs"));
                assertFalse(addNode.has("x"));
            }
        }
    }

    @Test void rejectsParallelCallsAndAcknowledgesBothBeforeRecovery() {
        var response = json(OPENAI);
        var calls = response.getAsJsonArray("output");
        var second = calls.get(1).deepCopy().getAsJsonObject();
        second.addProperty("call_id", "call-2");
        calls.add(second);
        List<JsonObject> bodies = new ArrayList<>();
        var session = session(AiNativeProviderSession.Dialect.OPENAI, bodies, response.toString(), false);
        assertEquals("invalid_provider_response", json(session.generate(REQUEST, null).join().action()).get("tool").getAsString());
        session.generate(REQUEST, json("{\"ok\":false}")).join();
        var input = bodies.get(1).getAsJsonArray("input");
        assertEquals("call-1", input.get(4).getAsJsonObject().get("call_id").getAsString());
        assertEquals("call-2", input.get(5).getAsJsonObject().get("call_id").getAsString());
    }

    @Test void absentUsageDoesNotDiscardAValidFunctionCall() {
        var response = json(OPENAI);
        response.add("usage", com.google.gson.JsonNull.INSTANCE);
        var session = session(AiNativeProviderSession.Dialect.OPENAI, new ArrayList<>(), response.toString(), false);
        var turn = session.generate(REQUEST, null).join();
        assertEquals("inspect_preset", json(turn.action()).get("tool").getAsString());
        assertNull(turn.usage().inputTokens());
    }
}
