package com.jarvis.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    // ── Role enum ─────────────────────────────────────────────────────────────

    @Test
    void role_value_returnsLowercase() {
        assertEquals("system",    Message.Role.SYSTEM.value());
        assertEquals("user",      Message.Role.USER.value());
        assertEquals("assistant", Message.Role.ASSISTANT.value());
        assertEquals("tool",      Message.Role.TOOL.value());
    }

    @Test
    void role_fromString_caseInsensitive() {
        assertEquals(Message.Role.USER,      Message.Role.fromString("USER"));
        assertEquals(Message.Role.USER,      Message.Role.fromString("user"));
        assertEquals(Message.Role.ASSISTANT, Message.Role.fromString("Assistant"));
        assertEquals(Message.Role.SYSTEM,    Message.Role.fromString("SYSTEM"));
        assertEquals(Message.Role.TOOL,      Message.Role.fromString("tool"));
    }

    @Test
    void role_fromString_invalid_throws() {
        assertThrows(IllegalArgumentException.class, () -> Message.Role.fromString("unknown"));
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    @Test
    void system_factory_setsRoleAndContent() {
        Message msg = Message.system("You are a helpful assistant.");
        assertEquals(Message.Role.SYSTEM, msg.getRole());
        assertEquals("You are a helpful assistant.", msg.getContent());
        assertNull(msg.getToolCalls());
        assertNull(msg.getToolCallId());
        assertNull(msg.getToolName());
    }

    @Test
    void user_factory_setsRoleAndContent() {
        Message msg = Message.user("Hello!");
        assertEquals(Message.Role.USER, msg.getRole());
        assertEquals("Hello!", msg.getContent());
    }

    @Test
    void assistant_factory_setsRoleAndContent() {
        Message msg = Message.assistant("Hi there!");
        assertEquals(Message.Role.ASSISTANT, msg.getRole());
        assertEquals("Hi there!", msg.getContent());
    }

    @Test
    void assistantWithToolCalls_setsToolCalls() {
        ToolCall tc = new ToolCall("id1", "read_file", Map.of("path", "foo.txt"));
        Message msg = Message.assistantWithToolCalls("Let me read it.", List.of(tc));

        assertEquals(Message.Role.ASSISTANT, msg.getRole());
        assertEquals("Let me read it.", msg.getContent());
        assertTrue(msg.hasToolCalls());
        assertEquals(1, msg.getToolCalls().size());
        assertEquals("read_file", msg.getToolCalls().get(0).getFunctionName());
    }

    @Test
    void assistantWithToolCalls_nullContent_isAllowed() {
        ToolCall tc = new ToolCall("id2", "write_file", Map.of());
        Message msg = Message.assistantWithToolCalls(null, List.of(tc));
        assertNull(msg.getContent());
        assertTrue(msg.hasToolCalls());
    }

    @Test
    void toolResult_factory_setsAllFields() {
        Message msg = Message.toolResult("call-99", "read_file", "line1\nline2");
        assertEquals(Message.Role.TOOL, msg.getRole());
        assertEquals("call-99",   msg.getToolCallId());
        assertEquals("read_file", msg.getToolName());
        assertEquals("line1\nline2", msg.getContent());
    }

    // ── hasToolCalls ──────────────────────────────────────────────────────────

    @Test
    void hasToolCalls_false_whenNull() {
        Message msg = Message.user("hi");
        assertFalse(msg.hasToolCalls());
    }

    @Test
    void hasToolCalls_false_whenEmptyList() {
        Message msg = Message.assistantWithToolCalls("text", List.of());
        assertFalse(msg.hasToolCalls());
    }

    @Test
    void hasToolCalls_true_whenNonEmpty() {
        ToolCall tc = new ToolCall("id", "fn", Map.of());
        Message msg = Message.assistantWithToolCalls(null, List.of(tc));
        assertTrue(msg.hasToolCalls());
    }

    // ── Defensive copy on toolCalls ────────────────────────────────────────

    @Test
    void assistantWithToolCalls_storesDefensiveCopy() {
        ToolCall tc1 = new ToolCall("id1", "fn1", Map.of());
        ToolCall tc2 = new ToolCall("id2", "fn2", Map.of());
        var original = new java.util.ArrayList<>(List.of(tc1));
        Message msg = Message.assistantWithToolCalls(null, original);
        original.add(tc2); // mutate original
        assertEquals(1, msg.getToolCalls().size(), "Message must not reflect external list mutations");
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void toString_containsRole() {
        String s = Message.user("test").toString();
        assertTrue(s.contains("USER"), "toString should include role");
    }

    @Test
    void toString_truncatesLongContent() {
        String longContent = "a".repeat(200);
        String s = Message.user(longContent).toString();
        assertTrue(s.contains("..."), "Long content should be truncated in toString");
    }

    @Test
    void toString_includesToolCallCount() {
        ToolCall tc = new ToolCall("id", "fn", Map.of());
        String s = Message.assistantWithToolCalls(null, List.of(tc)).toString();
        assertTrue(s.contains("toolCalls=1"));
    }
}
