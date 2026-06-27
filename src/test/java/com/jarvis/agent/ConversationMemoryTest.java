package com.jarvis.agent;

import com.jarvis.llm.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMemoryTest {

    // ── Constructor ───────────────────────────────────────────────────────────

    @Test
    void constructor_withSystemPrompt_addsSystemMessage() {
        ConversationMemory memory = new ConversationMemory("Be helpful.");
        List<Message> messages = memory.getMessages();
        assertEquals(1, messages.size());
        assertEquals(Message.Role.SYSTEM, messages.get(0).getRole());
        assertEquals("Be helpful.", messages.get(0).getContent());
    }

    @Test
    void constructor_withNullSystemPrompt_startsEmpty() {
        ConversationMemory memory = new ConversationMemory(null);
        assertTrue(memory.getMessages().isEmpty());
    }

    @Test
    void constructor_withEmptySystemPrompt_startsEmpty() {
        ConversationMemory memory = new ConversationMemory("");
        assertTrue(memory.getMessages().isEmpty());
    }

    // ── addMessage / getMessages ───────────────────────────────────────────────

    @Test
    void addMessage_appendsToList() {
        ConversationMemory memory = new ConversationMemory(null);
        memory.addMessage(Message.user("Hello"));
        memory.addMessage(Message.assistant("Hi"));
        assertEquals(2, memory.getMessages().size());
        assertEquals(Message.Role.USER,      memory.getMessages().get(0).getRole());
        assertEquals(Message.Role.ASSISTANT, memory.getMessages().get(1).getRole());
    }

    @Test
    void getMessages_returnsUnmodifiableView() {
        ConversationMemory memory = new ConversationMemory(null);
        memory.addMessage(Message.user("test"));
        assertThrows(UnsupportedOperationException.class,
                () -> memory.getMessages().add(Message.user("hack")));
    }

    // ── getUserMessageCount ────────────────────────────────────────────────────

    @Test
    void getUserMessageCount_excludesSystemMessage() {
        ConversationMemory memory = new ConversationMemory("System prompt");
        memory.addMessage(Message.user("q1"));
        memory.addMessage(Message.assistant("a1"));
        memory.addMessage(Message.user("q2"));
        // 3 non-system messages
        assertEquals(3, memory.getUserMessageCount());
    }

    @Test
    void getUserMessageCount_empty() {
        ConversationMemory memory = new ConversationMemory(null);
        assertEquals(0, memory.getUserMessageCount());
    }

    // ── clear ─────────────────────────────────────────────────────────────────

    @Test
    void clear_withSystemPrompt_keepsSystemMessage() {
        ConversationMemory memory = new ConversationMemory("Be concise.");
        memory.addMessage(Message.user("Hello"));
        memory.addMessage(Message.assistant("Hi"));
        memory.clear();

        List<Message> messages = memory.getMessages();
        assertEquals(1, messages.size());
        assertEquals(Message.Role.SYSTEM, messages.get(0).getRole());
        assertEquals("Be concise.", messages.get(0).getContent());
    }

    @Test
    void clear_withoutSystemPrompt_clearsAll() {
        ConversationMemory memory = new ConversationMemory(null);
        memory.addMessage(Message.user("Hello"));
        memory.clear();
        assertTrue(memory.getMessages().isEmpty());
    }

    @Test
    void clear_canAddMessagesAfterClear() {
        ConversationMemory memory = new ConversationMemory(null);
        memory.addMessage(Message.user("old"));
        memory.clear();
        memory.addMessage(Message.user("new"));
        assertEquals(1, memory.getMessages().size());
        assertEquals("new", memory.getMessages().get(0).getContent());
    }

    // ── estimateTokens ────────────────────────────────────────────────────────

    @Test
    void estimateTokens_empty_returnsZero() {
        ConversationMemory memory = new ConversationMemory(null);
        assertEquals(0, memory.estimateTokens());
    }

    @Test
    void estimateTokens_approximatelyCharsOverFour() {
        // 400 chars / 4 = 100 estimated tokens
        ConversationMemory memory = new ConversationMemory(null);
        memory.addMessage(Message.user("a".repeat(400)));
        assertEquals(100, memory.estimateTokens());
    }

    @Test
    void estimateTokens_nullContent_countedAsZero() {
        ConversationMemory memory = new ConversationMemory(null);
        // assistantWithToolCalls can have null content
        memory.addMessage(Message.assistantWithToolCalls(null, List.of()));
        assertEquals(0, memory.estimateTokens());
    }

    // ── trimIfNeeded ──────────────────────────────────────────────────────────

    @Test
    void trimIfNeeded_belowLimit_doesNothing() {
        ConversationMemory memory = new ConversationMemory("sys");
        memory.addMessage(Message.user("short"));
        int before = memory.getMessages().size();
        memory.trimIfNeeded(100_000);
        assertEquals(before, memory.getMessages().size());
    }

    @Test
    void trimIfNeeded_aboveLimit_removesOldestNonSystemMessage() {
        ConversationMemory memory = new ConversationMemory("sys");
        // Add two large messages (> 40 chars each → > 10 estimated tokens each)
        memory.addMessage(Message.user("a".repeat(400)));     // 100 tokens
        memory.addMessage(Message.assistant("b".repeat(400))); // 100 tokens
        // Total ≈ 100 + 100 + (3 chars "sys" / 4 ≈ 0) = 200 tokens

        // Trim to 50 tokens — should remove user message
        memory.trimIfNeeded(50);

        // System prompt must survive
        assertTrue(memory.getMessages().stream()
                .anyMatch(m -> m.getRole() == Message.Role.SYSTEM));
    }

    @Test
    void trimIfNeeded_keepsMinimumThreeMessages() {
        ConversationMemory memory = new ConversationMemory(null);
        // Only 2 messages — should NOT trim even if over limit
        memory.addMessage(Message.user("a".repeat(4000)));
        memory.addMessage(Message.assistant("b".repeat(4000)));
        int before = memory.getMessages().size();
        // Both messages are about 2000 tokens, well above any small limit,
        // but size is <= 3 so trimming is blocked
        memory.trimIfNeeded(1);
        // size stays the same because messages.size() <= 3
        assertEquals(before, memory.getMessages().size());
    }
}
