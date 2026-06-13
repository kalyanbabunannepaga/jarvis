package com.jarvis.llm;

import java.util.Map;

/**
 * Represents a tool/function call requested by the LLM.
 * Contains the tool name, arguments, and a unique ID for tracking.
 */
public class ToolCall {

    private final String id;
    private final String functionName;
    private final Map<String, Object> arguments;

    public ToolCall(String id, String functionName, Map<String, Object> arguments) {
        this.id = id;
        this.functionName = functionName;
        this.arguments = arguments;
    }

    public String getId() { return id; }
    public String getFunctionName() { return functionName; }
    public Map<String, Object> getArguments() { return arguments; }

    @Override
    public String toString() {
        return String.format("ToolCall{id='%s', function='%s', args=%s}", id, functionName, arguments);
    }
}
