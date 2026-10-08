package com.pathmind.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpenRouterProviderTest {
    private static final String RESPONSE = """
        {"id":"gen-1","choices":[{"finish_reason":"tool_calls","message":{"role":"assistant",
          "tool_calls":[{"id":"call-1","type":"function",
            "function":{"name":"inspect_preset","arguments":"{\\"target\\":\\"inspect\\"}"}}]}}]}
        """;

    @Test void completesPastedBaseUrlsWithoutManglingCustomProxyPaths() {
        assertEquals("https://openrouter.ai/api/v1/chat/completions", OpenRouterProvider.chatCompletionsEndpoint(""));
        assertEquals("https://openrouter.ai/api/v1/chat/completions",
            OpenRouterProvider.chatCompletionsEndpoint("https://openrouter.ai/api/v1"));
        assertEquals("https://openrouter.ai/api/v1/chat/completions",
            OpenRouterProvider.chatCompletionsEndpoint("  https://openrouter.ai/api/v1/  "));
        assertEquals("https://openrouter.ai/api/v1/chat/completions",
            OpenRouterProvider.chatCompletionsEndpoint("https://openrouter.ai/api/v1/chat/completions"));
        assertEquals("https://gateway.example/chat/completions",
            OpenRouterProvider.chatCompletionsEndpoint("https://gateway.example"));
        // A deliberate proxy route is never extended; only a bare origin or a /v1 root is.
        assertEquals("https://gateway.example/custom/route",
            OpenRouterProvider.chatCompletionsEndpoint("https://gateway.example/custom/route"));
    }

    @Test void routingPreferencesReachTheRequestBody() {
        List<JsonObject> bodies = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        var provider = provider("https://example.invalid/api/v1", "price", false, urls, bodies);
        provider.openSession().generate(new AiPresetRequest("instructions", "request", "model"), null).join();
        assertEquals("https://example.invalid/api/v1/chat/completions", urls.getFirst());
        var routing = bodies.getFirst().getAsJsonObject("provider");
        assertEquals("price", routing.get("sort").getAsString());
        assertFalse(routing.get("allow_fallbacks").getAsBoolean());
    }

    @Test void defaultRoutingLeavesUpstreamSelectionToTheGateway() {
        List<JsonObject> bodies = new ArrayList<>();
        var provider = provider("https://example.invalid/api/v1", "", true, new ArrayList<>(), bodies);
        provider.openSession().generate(new AiPresetRequest("instructions", "request", "model"), null).join();
        assertFalse(bodies.getFirst().has("provider"));
    }

    @Test void declaresNativeToolsSoTheAgentLoopUsesFunctionCalls() {
        var provider = new OpenRouterProvider("https://example.invalid/api/v1", "key");
        assertTrue(provider.capabilities().nativeFunctionTools());
        assertTrue(provider.capabilities().structuredOutput());
        assertEquals("openrouter", provider.providerId());
    }

    private OpenRouterProvider provider(String endpoint, String sort, boolean allowFallbacks,
                                        List<String> urls, List<JsonObject> bodies) {
        return new OpenRouterProvider(endpoint, "test-key", sort, allowFallbacks, (url, headers, body) -> {
            assertEquals("Bearer test-key", headers.get("Authorization"));
            urls.add(url);
            bodies.add(body.deepCopy());
            return CompletableFuture.completedFuture(JsonParser.parseString(RESPONSE).getAsJsonObject());
        });
    }
}
