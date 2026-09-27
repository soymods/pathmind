package com.pathmind.ai;

import java.util.concurrent.CompletableFuture;

/**
 * OpenRouter adapter over the Chat Completions API.
 *
 * <p>OpenRouter also exposes a Responses-shaped endpoint, but it is alpha, rejects
 * {@code store}/{@code previous_response_id}, and covers far fewer models. Chat Completions is the
 * surface every tool-calling model behind the gateway supports, so the agent loop targets it.</p>
 */
public final class OpenRouterProvider implements AiProvider {
    private final String endpoint, apiKey;
    private final AiRoutingPreferences routing;
    private final AiJsonTransport transport;

    public OpenRouterProvider(String endpoint, String apiKey) { this(endpoint, apiKey, "", true); }

    public OpenRouterProvider(String endpoint, String apiKey, String routingSort, boolean allowFallbacks) {
        this(endpoint, apiKey, routingSort, allowFallbacks, AiJsonTransport.http());
    }

    OpenRouterProvider(String endpoint, String apiKey, String routingSort, boolean allowFallbacks, AiJsonTransport transport) {
        this.endpoint = chatCompletionsEndpoint(endpoint);
        this.apiKey = apiKey;
        this.routing = new AiRoutingPreferences(routingSort, allowFallbacks);
        this.transport = transport;
    }

    @Override public AiProviderCapabilities capabilities() { return AiProviderCapabilities.NATIVE_TOOLS; }
    @Override public String providerId() { return "openrouter"; }
    @Override public AiProviderSession openSession() {
        return new AiNativeProviderSession(AiNativeProviderSession.Dialect.OPENAI_CHAT, endpoint, apiKey, transport, false, routing);
    }
    @Override public CompletableFuture<String> generate(AiPresetRequest request) {
        AiProviderSession session = openSession();
        return session.generate(request, null).thenApply(AiModelTurn::action).whenComplete((value, failure) -> session.close());
    }

    /**
     * Completes a pasted base URL, because the gateway advertises {@code /api/v1} and users paste that.
     * Only a bare origin or a {@code /v1}-style root is extended; any other path is a deliberate proxy
     * route and is left exactly as typed.
     */
    static String chatCompletionsEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return AiProviderType.OPENROUTER.defaultEndpoint();
        String normalized = endpoint.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.endsWith("/chat/completions")) return normalized;
        String path = normalized.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://[^/]*", "");
        return path.isEmpty() || path.endsWith("/v1") ? normalized + "/chat/completions" : normalized;
    }
}
