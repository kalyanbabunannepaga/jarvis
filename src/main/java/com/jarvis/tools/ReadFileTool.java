package com.jarvis.tools;

import com.jarvis.llm.ToolDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tool to read file contents with optional line range.
 * Returns file content with line numbers for easy reference.
 */
public class ReadFileTool implements Tool {

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "Read the contents of a file. Returns the file content with line numbers. "
                + "Use startLine and endLine to read a specific range of lines (1-indexed, inclusive). "
                + "If no range is specified, reads the entire file (up to 500 lines). "
                + "Always use this before editing a file to understand its current state.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", ToolDefinition.prop("string", "The path to the file to read (relative or absolute)"));
        props.put("startLine", ToolDefinition.prop("integer", "Optional start line (1-indexed, inclusive). Leave empty to start from beginning."));
        props.put("endLine", ToolDefinition.prop("integer", "Optional end line (1-indexed, inclusive). Leave empty to read to end."));
        return ToolDefinition.buildParameterSchema(props, List.of("path"));
    }

    @Override
    public String execute(Map<String, Object> arguments) throws ToolException {
        String pathStr = getRequiredString(arguments, "path");
        Path path = Paths.get(pathStr).toAbsolutePath().normalize();

        if (!Files.exists(path)) {
            throw new ToolException("File not found: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new ToolException("Not a regular file: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new ToolException("File is not readable: " + path);
        }

        try {
            List<String> allLines = Files.readAllLines(path, StandardCharsets.UTF_8);
            int totalLines = allLines.size();

            int startLine = getOptionalInt(arguments, "startLine", 1);
            int endLine = getOptionalInt(arguments, "endLine", Math.min(totalLines, 500));

            // Clamp values
            startLine = Math.max(1, startLine);
            endLine = Math.min(totalLines, endLine);

            if (startLine > totalLines) {
                throw new ToolException("startLine " + startLine + " is beyond file length (" + totalLines + " lines)");
            }

            // Build output with line numbers
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("File: %s (%d total lines)\n", path, totalLines));
            if (startLine > 1 || endLine < totalLines) {
                sb.append(String.format("Showing lines %d-%d:\n", startLine, endLine));
            }
            sb.append("---\n");

            List<String> selectedLines = allLines.subList(startLine - 1, endLine);
            int lineNum = startLine;
            for (String line : selectedLines) {
                sb.append(String.format("%4d | %s\n", lineNum, line));
                lineNum++;
            }

            if (endLine < totalLines) {
                sb.append(String.format("--- (%d more lines) ---\n", totalLines - endLine));
            }

            return sb.toString();

        } catch (IOException e) {
            throw new ToolException("Error reading file: " + e.getMessage(), e);
        }
    }

    private String getRequiredString(Map<String, Object> args, String key) throws ToolException {
        Object val = args.get(key);
        if (val == null) throw new ToolException("Missing required parameter: " + key);
        return val.toString();
    }

    private int getOptionalInt(Map<String, Object> args, String key, int defaultVal) {
        Object val = args.get(key);
        if (val == null) return defaultVal;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return defaultVal; }
    }
}
