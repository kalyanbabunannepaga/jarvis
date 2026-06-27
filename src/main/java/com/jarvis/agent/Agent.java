package com.jarvis.agent;

import com.jarvis.config.Config;
import com.jarvis.llm.*;
import com.jarvis.tools.ToolException;
import com.jarvis.tools.ToolRegistry;
import com.jarvis.ui.TerminalUI;

import java.util.List;

/**
 * The core AI agent implementing the ReAct (Reasoning + Acting) loop.
 *
 * Flow:
 * 1. User sends a message
 * 2. Message is added to conversation memory
 * 3. Memory + tool definitions are sent to the LLM
 * 4. If the LLM responds with tool calls:
 *    a. Execute each tool
 *    b. Add tool results to memory
 *    c. Send updated memory back to LLM (loop)
 * 5. If the LLM responds with text (no tool calls):
 *    a. Display the response
 *    b. Wait for next user input
 */
public class Agent {

    /** Rough character budget before trimming conversation memory (~100k token limit). */
    private static final int MAX_MEMORY_CHARS = 80_000;

    private final LLMProvider provider;
    private final ToolRegistry toolRegistry;
    private final ConversationMemory memory;
    private final TerminalUI ui;
    private final Config config;
    private final int maxIterations;

    public Agent(LLMProvider provider, ToolRegistry toolRegistry,
                 ConversationMemory memory, TerminalUI ui, Config config) {
        this.provider = provider;
        this.toolRegistry = toolRegistry;
        this.memory = memory;
        this.ui = ui;
        this.config = config;
        this.maxIterations = config.getMaxToolIterations();
    }

    /**
     * Process a user message through the agent loop.
     * Returns when the LLM produces a final text response (no more tool calls).
     */
    public String processMessage(String userMessage) {
        // Add user message to memory
        memory.addMessage(Message.user(userMessage));

        // Trim memory if needed (rough estimate of 100k token limit)
        memory.trimIfNeeded(MAX_MEMORY_CHARS);

        // Get tool definitions
        List<ToolDefinition> tools = toolRegistry.getToolDefinitions();

        // Request config
        LLMProvider.RequestConfig reqConfig = new LLMProvider.RequestConfig(
                config.getMaxTokens(),
                config.getTemperature()
        );

        int iteration = 0;

        while (iteration < maxIterations) {
            iteration++;

            // Show thinking indicator
            ui.showThinking(provider.name(), provider.model());

            // Call the LLM
            LLMResponse response = provider.chat(memory.getMessages(), tools, reqConfig);

            // Clear thinking indicator
            ui.clearThinking();

            // Handle errors
            if (response.getFinishReason() == LLMResponse.FinishReason.ERROR) {
                String errorMsg = response.getContent() != null
                        ? response.getContent() : "Unknown error from " + provider.name();
                ui.showError(errorMsg);
                return errorMsg;
            }

            // Handle max tokens
            if (response.getFinishReason() == LLMResponse.FinishReason.MAX_TOKENS) {
                String content = response.getContent() != null ? response.getContent() : "";
                content += "\n\n⚠ Response was truncated (max tokens reached).";
                memory.addMessage(Message.assistant(content));
                ui.showAssistantMessage(content);
                ui.showTokenUsage(response.getPromptTokens(), response.getCompletionTokens());
                return content;
            }

            // Handle tool calls
            if (response.hasToolCalls()) {
                // Add assistant message with tool calls to memory
                memory.addMessage(Message.assistantWithToolCalls(
                        response.getContent(),
                        response.getToolCalls()
                ));

                // Show any text content before tool calls
                if (response.getContent() != null && !response.getContent().isEmpty()) {
                    ui.showAssistantThinking(response.getContent());
                }

                // Execute each tool call
                for (ToolCall toolCall : response.getToolCalls()) {
                    ui.showToolCall(toolCall.getFunctionName(), toolCall.getArguments());

                    String result;
                    try {
                        result = toolRegistry.executeTool(
                                toolCall.getFunctionName(),
                                toolCall.getArguments()
                        );
                    } catch (ToolException e) {
                        result = "Error: " + e.getMessage();
                        ui.showToolError(toolCall.getFunctionName(), e.getMessage());
                    }

                    // Show tool result
                    ui.showToolResult(toolCall.getFunctionName(), result);

                    // Add tool result to memory
                    memory.addMessage(Message.toolResult(
                            toolCall.getId(),
                            toolCall.getFunctionName(),
                            result
                    ));
                }

                // Continue the loop — send results back to LLM
                continue;
            }

            // No tool calls — this is the final response
            String finalContent = response.getContent() != null
                    ? response.getContent() : "(No response)";

            memory.addMessage(Message.assistant(finalContent));
            ui.showAssistantMessage(finalContent);
            ui.showTokenUsage(response.getPromptTokens(), response.getCompletionTokens());

            return finalContent;
        }

        // Max iterations reached
        String warning = "⚠ Reached maximum tool iterations (" + maxIterations + "). "
                + "The agent may not have completed its task. Please try again with a more specific request.";
        ui.showWarning(warning);
        return warning;
    }

    /**
     * Clear conversation history.
     */
    public void clearHistory() {
        memory.clear();
    }

    /**
     * Get the current LLM provider.
     */
    public LLMProvider getProvider() {
        return provider;
    }

    /**
     * Get the conversation memory.
     */
    public ConversationMemory getMemory() {
        return memory;
    }
}
