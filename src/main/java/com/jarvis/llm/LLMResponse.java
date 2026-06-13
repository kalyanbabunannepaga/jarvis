package com.jarvis.llm;

import java.util.List;

/**
 * Unified response from an LLM provider.
 * Contains the text content and/or tool calls, plus metadata.
 */
public class LLMResponse {

    public enum FinishReason {
        STOP,           // Normal completion
        TOOL_CALLS,     // Model wants to call tools
        MAX_TOKENS,     // Hit token limit
        ERROR;          // Something went wrong

        public static FinishReason fromOpenAI(String reason) {
            if (reason == null) return STOP;
            return switch (reason) {
                case "stop" -> STOP;
                case "tool_calls" -> TOOL_CALLS;
                case "length" -> MAX_TOKENS;
                default -> STOP;
            };
        }

        public static FinishReason fromClaude(String reason) {
            if (reason == null) return STOP;
            return switch (reason) {
                case "end_turn" -> STOP;
                case "tool_use" -> TOOL_CALLS;
                case "max_tokens" -> MAX_TOKENS;
                default -> STOP;
            };
        }

        public static FinishReason fromGemini(String reason) {
            if (reason == null) return STOP;
            return switch (reason) {
                case "STOP" -> STOP;
                case "MAX_TOKENS" -> MAX_TOKENS;
                default -> STOP;
            };
        }
    }

    private final String content;
    private final List<ToolCall> toolCalls;
    private final FinishReason finishReason;
    private final int promptTokens;
    private final int completionTokens;

    private LLMResponse(Builder builder) {
        this.content = builder.content;
        this.toolCalls = builder.toolCalls;
        this.finishReason = builder.finishReason;
        this.promptTokens = builder.promptTokens;
        this.completionTokens = builder.completionTokens;
    }

    // --- Getters ---

    public String getContent() { return content; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public FinishReason getFinishReason() { return finishReason; }
    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public int getTotalTokens() {
        return promptTokens + completionTokens;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LLMResponse{");
        sb.append("finish=").append(finishReason);
        if (content != null) sb.append(", content='").append(content.length() > 60 ? content.substring(0, 60) + "..." : content).append("'");
        if (hasToolCalls()) sb.append(", toolCalls=").append(toolCalls.size());
        sb.append(", tokens=").append(getTotalTokens());
        sb.append("}");
        return sb.toString();
    }

    // --- Builder ---

    public static class Builder {
        private String content;
        private List<ToolCall> toolCalls;
        private FinishReason finishReason = FinishReason.STOP;
        private int promptTokens;
        private int completionTokens;

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public Builder finishReason(FinishReason finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public Builder promptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }

        public Builder completionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }

        public LLMResponse build() {
            return new LLMResponse(this);
        }
    }
}
