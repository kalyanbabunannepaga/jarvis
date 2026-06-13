package com.jarvis.tools;

/**
 * Exception thrown when a tool execution fails.
 * The error message is sent back to the LLM so it can recover.
 */
public class ToolException extends Exception {

    public ToolException(String message) {
        super(message);
    }

    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
