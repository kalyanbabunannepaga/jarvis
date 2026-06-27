package com.jarvis.llm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallTest {

    @Test
    void constructor_andGetters() {
        Map<String, Object> args = Map.of("path", "src/Main.java", "line", 42);
        ToolCall tc = new ToolCall("call-abc123", "read_file", args);

        assertEquals("call-abc123",    tc.getId());
        assertEquals("read_file",      tc.getFunctionName());
        assertEquals("src/Main.java",  tc.getArguments().get("path"));
        assertEquals(42,               tc.getArguments().get("line"));
    }

    @Test
    void toString_containsAllFields() {
        ToolCall tc = new ToolCall("id-1", "write_file", Map.of("path", "out.txt"));
        String s = tc.toString();

        assertTrue(s.contains("id-1"),       "toString should include id");
        assertTrue(s.contains("write_file"), "toString should include function name");
        assertTrue(s.contains("out.txt"),    "toString should include arguments");
    }

    @Test
    void nullArguments_areAllowed() {
        ToolCall tc = new ToolCall("id", "fn", null);
        assertNull(tc.getArguments());
    }
}
