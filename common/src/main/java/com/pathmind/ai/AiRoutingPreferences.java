package com.pathmind.ai;

import com.google.gson.JsonObject;

/**
 * Aggregator routing hints for gateways that pick an upstream provider per request.
 *
 * <p>Only OpenRouter reads these today. They are kept separate from
 * {@link com.pathmind.data.SettingsManager.Settings.AiProviderSettings} so a direct
 * provider never has to carry fields it cannot honour.</p>
 *
 * @param sort           provider ranking: {@code throughput}, {@code price}, {@code latency}, or blank for the gateway default
 * @param allowFallbacks whether the gateway may retry another upstream when the first one fails
 */
record AiRoutingPreferences(String sort, boolean allowFallbacks) {
    static final AiRoutingPreferences DEFAULT = new AiRoutingPreferences("", true);

    AiRoutingPreferences {
        sort = sort == null ? "" : sort.trim();
    }

    /** Blank sort with fallbacks left on is exactly the gateway default, so the block is omitted entirely. */
    boolean isDefault() {
        return sort.isEmpty() && allowFallbacks;
    }

    JsonObject toJson() {
        JsonObject routing = new JsonObject();
        if (!sort.isEmpty()) routing.addProperty("sort", sort);
        if (!allowFallbacks) routing.addProperty("allow_fallbacks", false);
        return routing;
    }
}
