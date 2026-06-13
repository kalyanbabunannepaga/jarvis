package com.jarvis.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified message model used across all LLM providers.
 * Each provider adapter translates to/from this format.
 */
public class Message {

    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL;

        public String value() {
            return name().toLowerCase();
        }

        public static Role fromString(String s) {
            return valueOf(s.toUpperCase());
        }
    }

    private final Role role;
    private final String content;
    private final List<ToolCall> toolCalls;
    private final String toolCallId;   // For TOOL role: which tool call this responds to
    private final String toolName;     // For TOOL role: name of the tool

    private Message(Builder builder) {
        this.role = builder.role;
        this.content = builder.content;
        this.toolCalls = builder.toolCalls;
        this.toolCallId = builder.toolCallId;
        this.toolName = builder.toolName;
    }

    // --- Factory methods ---

    public static Message system(String content) {
        return new Builder(Role.SYSTEM).content(content).build();
    }

    public static Message user(String content) {
        return new Builder(Role.USER).content(content).build();
    }

    public static Message assistant(String content) {
        return new Builder(Role.ASSISTANT).content(content).build();
    }

    public static Message assistantWithToolCalls(String content, List<ToolCall> toolCalls) {
        return new Builder(Role.ASSISTANT).content(content).toolCalls(toolCalls).build();
    }

    public static Message toolResult(String toolCallId, String toolName, String content) {
        return new Builder(Role.TOOL)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .content(content)
                .build();
    }

    // --- Getters ---

    public Role getRole() { return role; }
    public String getContent() { return content; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public String getToolCallId() { return toolCallId; }
    public String getToolName() { return toolName; }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Message{role=").append(role);
        if (content != null) sb.append(", content='").append(content.length() > 80 ? content.substring(0, 80) + "..." : content).append("'");
        if (hasToolCalls()) sb.append(", toolCalls=").append(toolCalls.size());
        if (toolCallId != null) sb.append(", toolCallId='").append(toolCallId).append("'");
        sb.append("}");
        return sb.toString();
    }

    // --- Builder ---

    public static class Builder {
        private final Role role;
        private String content;
        private List<ToolCall> toolCalls;
        private String toolCallId;
        private String toolName;

        public Builder(Role role) {
            this.role = role;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls != null ? new ArrayList<>(toolCalls) : null;
            return this;
        }

        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Message build() {
            return new Message(this);
        }
    }
}
