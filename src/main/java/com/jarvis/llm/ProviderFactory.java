package com.jarvis.llm;

import com.jarvis.config.Config;
import com.jarvis.config.ConfigManager;

/**
 * Factory for creating LLM provider instances.
 * Resolves provider name → adapter class, injecting the correct API key and model.
 */
public class ProviderFactory {

    private final ConfigManager configManager;

    public ProviderFactory(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Create an LLMProvider for the given provider name, using the config's default model.
     */
    public LLMProvider create(String providerName) {
        Config config = configManager.getConfig();
        Config.ProviderConfig pc = config.getProviderConfig(providerName);

        if (pc == null) {
            throw new IllegalArgumentException("Unknown provider: " + providerName
                    + ". Available: openai, claude, gemini");
        }

        String apiKey = configManager.getApiKey(providerName);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "API key not found for provider '" + providerName + "'. "
                    + "Please set the " + pc.getEnvKey() + " environment variable."
            );
        }

        return create(providerName, apiKey, pc.getModel(), pc.getBaseUrl());
    }

    /**
     * Create an LLMProvider with explicit model override.
     */
    public LLMProvider create(String providerName, String modelOverride) {
        Config config = configManager.getConfig();
        Config.ProviderConfig pc = config.getProviderConfig(providerName);

        if (pc == null) {
            throw new IllegalArgumentException("Unknown provider: " + providerName);
        }

        String apiKey = configManager.getApiKey(providerName);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "API key not found. Set " + pc.getEnvKey() + " environment variable."
            );
        }

        String model = modelOverride != null ? modelOverride : pc.getModel();
        return create(providerName, apiKey, model, pc.getBaseUrl());
    }

    /**
     * Create an LLMProvider with all parameters explicit.
     */
    public LLMProvider create(String providerName, String apiKey, String model, String baseUrl) {
        return switch (providerName.toLowerCase()) {
            case "openai" -> new OpenAIProvider(apiKey, model, baseUrl);
            case "claude", "anthropic" -> new ClaudeProvider(apiKey, model, baseUrl);
            case "gemini", "google" -> new GeminiProvider(apiKey, model, baseUrl);
            case "groq" -> new OpenAIProvider(apiKey, model, baseUrl);
            default -> throw new IllegalArgumentException(
                    "Unknown provider: " + providerName + ". Available: openai, claude, gemini, groq"
            );
        };
    }
}
