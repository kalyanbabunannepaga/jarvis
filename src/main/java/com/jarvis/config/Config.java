package com.jarvis.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Configuration POJO for Jarvis settings.
 * Loaded from ~/.jarvis/config.json with defaults from bundled default-config.json.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {

    @JsonProperty("defaultProvider")
    private String defaultProvider = "openai";

    @JsonProperty("defaultModel")
    private String defaultModel = "gpt-4o";

    @JsonProperty("maxTokens")
    private int maxTokens = 4096;

    @JsonProperty("temperature")
    private double temperature = 0.1;

    @JsonProperty("maxToolIterations")
    private int maxToolIterations = 15;

    @JsonProperty("commandTimeoutSeconds")
    private int commandTimeoutSeconds = 30;

    @JsonProperty("systemPrompt")
    private String systemPrompt;

    @JsonProperty("providers")
    private Map<String, ProviderConfig> providers;

    public Config() {}

    // --- Getters & Setters ---

    public String getDefaultProvider() { return defaultProvider; }
    public void setDefaultProvider(String defaultProvider) { this.defaultProvider = defaultProvider; }

    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getMaxToolIterations() { return maxToolIterations; }
    public void setMaxToolIterations(int maxToolIterations) { this.maxToolIterations = maxToolIterations; }

    public int getCommandTimeoutSeconds() { return commandTimeoutSeconds; }
    public void setCommandTimeoutSeconds(int commandTimeoutSeconds) { this.commandTimeoutSeconds = commandTimeoutSeconds; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public Map<String, ProviderConfig> getProviders() { return providers; }
    public void setProviders(Map<String, ProviderConfig> providers) { this.providers = providers; }

    /**
     * Get the provider config for a specific provider name.
     */
    public ProviderConfig getProviderConfig(String providerName) {
        if (providers == null) return null;
        return providers.get(providerName.toLowerCase());
    }

    @Override
    public String toString() {
        return String.format(
            "Config{provider=%s, model=%s, maxTokens=%d, temperature=%.1f, maxToolIterations=%d}",
            defaultProvider, defaultModel, maxTokens, temperature, maxToolIterations
        );
    }

    /**
     * Nested configuration for each LLM provider.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProviderConfig {

        @JsonProperty("model")
        private String model;

        @JsonProperty("baseUrl")
        private String baseUrl;

        @JsonProperty("envKey")
        private String envKey;

        public ProviderConfig() {}

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getEnvKey() { return envKey; }
        public void setEnvKey(String envKey) { this.envKey = envKey; }

        @Override
        public String toString() {
            return String.format("ProviderConfig{model=%s, baseUrl=%s, envKey=%s}", model, baseUrl, envKey);
        }
    }
}
