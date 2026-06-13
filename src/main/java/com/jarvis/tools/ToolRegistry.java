package com.jarvis.tools;

import com.jarvis.llm.ToolDefinition;

import java.util.*;

/**
 * Registry of all available tools.
 * Provides lookup by name and generates tool definitions for LLM requests.
 */
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    /**
     * Register a tool.
     */
    public void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    /**
     * Get a tool by name.
     */
    public Tool getTool(String name) {
        return tools.get(name);
    }

    /**
     * Execute a tool by name with the given arguments.
     *
     * @return The tool's result string
     * @throws ToolException if the tool is not found or execution fails
     */
    public String executeTool(String name, Map<String, Object> arguments) throws ToolException {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new ToolException("Unknown tool: " + name + ". Available tools: " + tools.keySet());
        }
        return tool.execute(arguments);
    }

    /**
     * Get tool definitions for all registered tools (for LLM requests).
     */
    public List<ToolDefinition> getToolDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (Tool tool : tools.values()) {
            definitions.add(tool.toDefinition());
        }
        return definitions;
    }

    /**
     * Get all registered tool names.
     */
    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    /**
     * Create a registry with all default tools pre-registered.
     */
    public static ToolRegistry createDefault(String workingDirectory, int commandTimeoutSeconds) {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool());
        registry.register(new WriteFileTool());
        registry.register(new EditFileTool());
        registry.register(new ListDirectoryTool());
        registry.register(new SearchCodeTool());
        registry.register(new RunCommandTool(commandTimeoutSeconds));
        return registry;
    }
}
