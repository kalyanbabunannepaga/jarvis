package com.jarvis.tools;

import com.jarvis.llm.ToolDefinition;
import java.util.Map;

/**
 * Interface for all tools that the AI agent can invoke.
 * Each tool provides its metadata (name, description, schema)
 * and an execute method that performs the actual work.
 */
public interface Tool {

    /**
     * Unique name for this tool (used in LLM function calling).
     */
    String name();

    /**
     * Human-readable description of what this tool does.
     * The LLM uses this to decide when to call the tool.
     */
    String description();

    /**
     * JSON Schema describing the tool's parameters.
     */
    Map<String, Object> parameterSchema();

    /**
     * Execute the tool with the given arguments.
     *
     * @param arguments Parsed arguments from the LLM's function call
     * @return The result as a string (returned to the LLM)
     * @throws ToolException if execution fails
     */
    String execute(Map<String, Object> arguments) throws ToolException;

    /**
     * Convert this tool to a ToolDefinition for LLM requests.
     */
    default ToolDefinition toDefinition() {
        return new ToolDefinition(name(), description(), parameterSchema());
    }
}
