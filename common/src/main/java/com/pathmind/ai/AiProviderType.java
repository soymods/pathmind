package com.pathmind.ai;

/** Provider identifiers that remain stable in user settings. */
public enum AiProviderType {
    OPENAI("OpenAI", "https://api.openai.com/v1/responses", "gpt-5.4-mini"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1/messages", "claude-sonnet-4-20250514"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent", "gemini-2.5-flash"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "anthropic/claude-sonnet-4"),
    OPENAI_COMPATIBLE("OpenAI compatible", "", "");

    private final String displayName;
    private final String defaultEndpoint;
    private final String defaultModel;

    AiProviderType(String displayName, String defaultEndpoint, String defaultModel) {
        this.displayName = displayName;
        this.defaultEndpoint = defaultEndpoint;
        this.defaultModel = defaultModel;
    }

    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public String displayName() { return displayName; }
    public String defaultEndpoint() { return defaultEndpoint; }
    public String defaultModel() { return defaultModel; }
}
