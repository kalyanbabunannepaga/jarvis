package com.jarvis.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * Common interface for all LLM providers.
 * Each provider (OpenAI, Claude, Gemini) implements this to translate
 * between the unified Jarvis message format and the provider-specific API.
 */
public interface LLMProvider {

    /**
     * The display name of this provider (e.g., "OpenAI", "Claude", "Gemini").
     */
    String name();

    /**
     * The model name being used (e.g., "gpt-4o", "claude-sonnet-4-20250514").
     */
    String model();

    /**
     * Send a chat request with tool definitions and get a response.
     *
     * @param messages The conversation history
     * @param tools    Available tool definitions (can be empty)
     * @param config   Request configuration (maxTokens, temperature, etc.)
     * @return The LLM's response
     */
    LLMResponse chat(List<Message> messages, List<ToolDefinition> tools, RequestConfig config);

    /**
     * Configuration for a single LLM request.
     */
    record RequestConfig(int maxTokens, double temperature) {
        public static RequestConfig defaults() {
            return new RequestConfig(4096, 0.1);
        }
    }
}
