package dev.moneet.contextos.eval;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * OpenAI-compatible endpoints and the environment variable each reads its key
 * from. Model names are not preset: they change often, so they are always
 * passed explicitly. {@code CUSTOM} takes {@code --base-url} and {@code --api-key-env}.
 */
public enum Provider {
    GROQ("https://api.groq.com/openai/v1", "GROQ_API_KEY"),
    OPENROUTER("https://openrouter.ai/api/v1", "OPENROUTER_API_KEY"),
    GEMINI("https://generativelanguage.googleapis.com/v1beta/openai", "GEMINI_API_KEY"),
    MISTRAL("https://api.mistral.ai/v1", "MISTRAL_API_KEY"),
    GITHUB("https://models.github.ai/inference", "GITHUB_TOKEN"),
    CEREBRAS("https://api.cerebras.ai/v1", "CEREBRAS_API_KEY"),
    OLLAMA("http://localhost:11434/v1", null),
    CUSTOM(null, null);

    private final String baseUrl;
    private final String apiKeyEnv;

    Provider(String baseUrl, String apiKeyEnv) {
        this.baseUrl = baseUrl;
        this.apiKeyEnv = apiKeyEnv;
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** Environment variable holding the API key; null when no key is needed. */
    public String apiKeyEnv() {
        return apiKeyEnv;
    }

    public static Provider parse(String name) {
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown provider '" + name + "'. Use one of: " + names());
        }
    }

    public static String names() {
        return Arrays.stream(values()).map(p -> p.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "));
    }
}
