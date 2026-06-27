package com.jarvis.llm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolDefinitionTest {

    // ── prop() factory ────────────────────────────────────────────────────────

    @Test
    void prop_returnsMapWithTypeAndDescription() {
        Map<String, Object> p = ToolDefinition.prop("string", "A file path");
        assertEquals("string", p.get("type"));
        assertEquals("A file path", p.get("description"));
    }

    @Test
    void prop_integer_type() {
        Map<String, Object> p = ToolDefinition.prop("integer", "A count");
        assertEquals("integer", p.get("type"));
    }

    @Test
    void prop_boolean_type() {
        Map<String, Object> p = ToolDefinition.prop("boolean", "A flag");
        assertEquals("boolean", p.get("type"));
    }

    // ── buildParameterSchema() ────────────────────────────────────────────────

    @Test
    void buildParameterSchema_hasObjectType() {
        Map<String, Object> props = Map.of("path", ToolDefinition.prop("string", "path"));
        Map<String, Object> schema = ToolDefinition.buildParameterSchema(props, java.util.List.of("path"));
        assertEquals("object", schema.get("type"));
    }

    @Test
    void buildParameterSchema_containsProperties() {
        Map<String, Object> props = Map.of("query", ToolDefinition.prop("string", "query"));
        Map<String, Object> schema = ToolDefinition.buildParameterSchema(props, java.util.List.of("query"));
        assertNotNull(schema.get("properties"));
    }

    @Test
    void buildParameterSchema_containsRequired() {
        Map<String, Object> props = Map.of("x", ToolDefinition.prop("string", "x"));
        Map<String, Object> schema = ToolDefinition.buildParameterSchema(props, java.util.List.of("x"));
        assertNotNull(schema.get("required"));
        assertTrue(schema.get("required").toString().contains("x"));
    }

    @Test
    void buildParameterSchema_emptyRequired_isAllowed() {
        Map<String, Object> props = Map.of("opt", ToolDefinition.prop("string", "optional"));
        assertDoesNotThrow(() ->
                ToolDefinition.buildParameterSchema(props, java.util.List.of()));
    }

    // ── ToolDefinition constructor / getters ──────────────────────────────────

    @Test
    void constructor_andGetters() {
        Map<String, Object> params = Map.of("type", "object");
        ToolDefinition def = new ToolDefinition("my_tool", "Does something", params);

        assertEquals("my_tool", def.getName());
        assertEquals("Does something", def.getDescription());
        assertEquals(params, def.getParameters());
    }

    @Test
    void toString_containsName() {
        ToolDefinition def = new ToolDefinition("read_file", "Reads a file", Map.of());
        assertTrue(def.toString().contains("read_file"));
    }
}
