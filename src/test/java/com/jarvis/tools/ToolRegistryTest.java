package com.jarvis.tools;

import com.jarvis.llm.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    // ── register / getTool ────────────────────────────────────────────────────

    @Test
    void register_toolIsRetrievableByName() {
        Tool tool = fakeTool("my_tool", "does something");
        registry.register(tool);
        assertSame(tool, registry.getTool("my_tool"));
    }

    @Test
    void getTool_unknownName_returnsNull() {
        assertNull(registry.getTool("nonexistent"));
    }

    @Test
    void register_overwrites_existingToolWithSameName() {
        Tool first  = fakeTool("dup_tool", "v1");
        Tool second = fakeTool("dup_tool", "v2");
        registry.register(first);
        registry.register(second);
        assertSame(second, registry.getTool("dup_tool"));
    }

    // ── executeTool ───────────────────────────────────────────────────────────

    @Test
    void executeTool_success_returnsToolResult() throws ToolException {
        Tool tool = Mockito.mock(Tool.class);
        when(tool.name()).thenReturn("greeter");
        when(tool.execute(any())).thenReturn("Hello!");
        registry.register(tool);

        String result = registry.executeTool("greeter", Map.of());
        assertEquals("Hello!", result);
    }

    @Test
    void executeTool_unknownTool_throwsToolException() {
        ToolException ex = assertThrows(ToolException.class,
                () -> registry.executeTool("ghost", Map.of()));
        assertTrue(ex.getMessage().contains("ghost"));
    }

    @Test
    void executeTool_passesThroughToolException() throws ToolException {
        Tool tool = Mockito.mock(Tool.class);
        when(tool.name()).thenReturn("broken");
        when(tool.execute(any())).thenThrow(new ToolException("disk error"));
        registry.register(tool);

        ToolException ex = assertThrows(ToolException.class,
                () -> registry.executeTool("broken", Map.of()));
        assertEquals("disk error", ex.getMessage());
    }

    // ── getToolDefinitions ────────────────────────────────────────────────────

    @Test
    void getToolDefinitions_emptyRegistry() {
        assertTrue(registry.getToolDefinitions().isEmpty());
    }

    @Test
    void getToolDefinitions_returnsOnePerTool() {
        registry.register(fakeTool("tool_a", "does A"));
        registry.register(fakeTool("tool_b", "does B"));
        List<ToolDefinition> defs = registry.getToolDefinitions();
        assertEquals(2, defs.size());
    }

    @Test
    void getToolDefinitions_preservesInsertionOrder() {
        registry.register(fakeTool("first",  "first tool"));
        registry.register(fakeTool("second", "second tool"));
        registry.register(fakeTool("third",  "third tool"));

        List<ToolDefinition> defs = registry.getToolDefinitions();
        assertEquals("first",  defs.get(0).getName());
        assertEquals("second", defs.get(1).getName());
        assertEquals("third",  defs.get(2).getName());
    }

    // ── getToolNames ──────────────────────────────────────────────────────────

    @Test
    void getToolNames_emptyRegistry() {
        assertTrue(registry.getToolNames().isEmpty());
    }

    @Test
    void getToolNames_returnsAllRegisteredNames() {
        registry.register(fakeTool("alpha", "a"));
        registry.register(fakeTool("beta",  "b"));
        Set<String> names = registry.getToolNames();
        assertTrue(names.contains("alpha"));
        assertTrue(names.contains("beta"));
    }

    @Test
    void getToolNames_isUnmodifiable() {
        registry.register(fakeTool("x", "x"));
        assertThrows(UnsupportedOperationException.class,
                () -> registry.getToolNames().add("hack"));
    }

    // ── createDefault ──────────────────────────────────────────────────────────

    @Test
    void createDefault_registersAllExpectedTools() {
        ToolRegistry def = ToolRegistry.createDefault(".", 30);
        Set<String> names = def.getToolNames();

        assertTrue(names.contains("read_file"),       "should have read_file");
        assertTrue(names.contains("write_file"),      "should have write_file");
        assertTrue(names.contains("edit_file"),       "should have edit_file");
        assertTrue(names.contains("list_directory"),  "should have list_directory");
        assertTrue(names.contains("search_code"),     "should have search_code");
        assertTrue(names.contains("run_command"),     "should have run_command");
    }

    @Test
    void createDefault_toolCount_isSix() {
        ToolRegistry def = ToolRegistry.createDefault(".", 30);
        assertEquals(6, def.getToolNames().size());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Creates a simple stub Tool whose name() and toDefinition() are functional. */
    private Tool fakeTool(String name, String description) {
        Tool tool = mock(Tool.class);
        when(tool.name()).thenReturn(name);
        when(tool.description()).thenReturn(description);
        when(tool.parameterSchema()).thenReturn(Map.of());
        when(tool.toDefinition()).thenReturn(new ToolDefinition(name, description, Map.of()));
        return tool;
    }
}
