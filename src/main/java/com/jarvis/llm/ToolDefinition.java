package com.jarvis.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines a tool/function that the LLM can call.
 * Contains the name, description, and JSON Schema for parameters.
 */
public class ToolDefinition {

    private final String name;
    private final String description;
    private final Map<String, Object> parameters;

    public ToolDefinition(String name, String description, Map<String, Object> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Map<String, Object> getParameters() { return parameters; }

    /**
     * Build a JSON Schema for parameters with the given properties and required fields.
     */
    public static Map<String, Object> buildParameterSchema(
            Map<String, Object> properties,
            List<String> required) {

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        // Include ALL properties in required for strict-mode compatibility (Groq, OpenAI strict)
        List<String> allKeys = new java.util.ArrayList<>(properties.keySet());
        schema.put("required", allKeys);
        schema.put("additionalProperties", false);
        return schema;
    }

    /**
     * Helper to build a single property definition.
     */
    public static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    /**
     * Helper to build a property with an enum constraint.
     */
    public static Map<String, Object> propEnum(String type, String description, List<String> enumValues) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        p.put("enum", enumValues);
        return p;
    }

    @Override
    public String toString() {
        return String.format("ToolDefinition{name='%s'}", name);
    }
}
