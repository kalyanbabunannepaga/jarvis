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

/**
 * Tool to create or overwrite files.
 * Creates parent directories as needed.
 */
public class WriteFileTool implements Tool {

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String description() {
        return "Create a new file or overwrite an existing file with the given content. "
                + "Parent directories are created automatically if they don't exist. "
                + "Use this for creating new files. For modifying existing files, prefer edit_file instead.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", ToolDefinition.prop("string", "The path for the file to create/overwrite (relative or absolute)"));
        props.put("content", ToolDefinition.prop("string", "The complete content to write to the file"));
        return ToolDefinition.buildParameterSchema(props, List.of("path", "content"));
    }

    @Override
    public String execute(Map<String, Object> arguments) throws ToolException {
        String pathStr = getRequiredString(arguments, "path");
        String content = getRequiredString(arguments, "content");
        Path path = Paths.get(pathStr).toAbsolutePath().normalize();

        try {
            // Create parent directories
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            boolean existed = Files.exists(path);
            Files.writeString(path, content, StandardCharsets.UTF_8);

            long bytes = Files.size(path);
            long lines = content.lines().count();

            return String.format("✓ %s file: %s (%d lines, %d bytes)",
                    existed ? "Updated" : "Created", path, lines, bytes);

        } catch (IOException e) {
            throw new ToolException("Error writing file: " + e.getMessage(), e);
        }
    }

    private String getRequiredString(Map<String, Object> args, String key) throws ToolException {
        Object val = args.get(key);
        if (val == null) throw new ToolException("Missing required parameter: " + key);
        return val.toString();
    }
}
