package com.jarvis.agent;

import com.jarvis.llm.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages conversation history (messages) for the agent.
 * Stores messages in memory and provides methods to manage context size.
 */
public class ConversationMemory {

    private final List<Message> messages = new ArrayList<>();
    private final String systemPrompt;

    public ConversationMemory(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Message.system(systemPrompt));
        }
    }

    /**
     * Add a message to the conversation.
     */
    public void addMessage(Message message) {
        messages.add(message);
    }

    /**
     * Get all messages in the conversation.
     */
    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * Get the number of messages (excluding system prompt).
     */
    public int getUserMessageCount() {
        return (int) messages.stream()
                .filter(m -> m.getRole() != Message.Role.SYSTEM)
                .count();
    }

    /**
     * Clear conversation history but keep the system prompt.
     */
    public void clear() {
        messages.clear();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Message.system(systemPrompt));
        }
    }

    /**
     * Estimate total character count for rough token estimation.
     * (Approximate: 1 token ≈ 4 chars)
     */
    public int estimateTokens() {
        int chars = 0;
        for (Message msg : messages) {
            if (msg.getContent() != null) {
                chars += msg.getContent().length();
            }
        }
        return chars / 4; // Rough estimate
    }

    /**
     * Trim old messages if we're approaching token limits.
     * Keeps system prompt and the most recent N user/assistant pairs.
     */
    public void trimIfNeeded(int maxEstimatedTokens) {
        while (estimateTokens() > maxEstimatedTokens && messages.size() > 3) {
            // Remove the oldest non-system message
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i).getRole() != Message.Role.SYSTEM) {
                    messages.remove(i);
                    break;
                }
            }
        }
    }
}
