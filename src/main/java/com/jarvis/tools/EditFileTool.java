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
 * Tool to perform search-and-replace edits on existing files.
 * Finds exact matches of old content and replaces with new content.
 */
public class EditFileTool implements Tool {

    @Override
    public String name() {
        return "edit_file";
    }

    @Override
    public String description() {
        return "Edit an existing file by replacing a specific piece of text with new text. "
                + "The oldContent must be an EXACT match of text currently in the file (including whitespace and indentation). "
                + "Always use read_file first to see the current file content before editing. "
                + "This performs a single, precise find-and-replace operation.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", ToolDefinition.prop("string", "The path to the file to edit"));
        props.put("oldContent", ToolDefinition.prop("string", "The exact text to find and replace (must match exactly)"));
        props.put("newContent", ToolDefinition.prop("string", "The replacement text"));
        return ToolDefinition.buildParameterSchema(props, List.of("path", "oldContent", "newContent"));
    }

    @Override
    public String execute(Map<String, Object> arguments) throws ToolException {
        String pathStr = getRequiredString(arguments, "path");
        String oldContent = getRequiredString(arguments, "oldContent");
        String newContent = getRequiredString(arguments, "newContent");
        Path path = Paths.get(pathStr).toAbsolutePath().normalize();

        if (!Files.exists(path)) {
            throw new ToolException("File not found: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new ToolException("Not a regular file: " + path);
        }

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);

            // Count occurrences
            int count = countOccurrences(content, oldContent);

            if (count == 0) {
                // Provide helpful error
                throw new ToolException(
                    "Could not find the specified oldContent in " + path + ". "
                    + "Make sure the text matches exactly, including whitespace and indentation. "
                    + "Use read_file to see the current file content."
                );
            }

            if (count > 1) {
                throw new ToolException(
                    "Found " + count + " occurrences of oldContent in " + path + ". "
                    + "Please provide a more specific/unique text snippet to replace."
                );
            }

            // Perform the replacement
            String updatedContent = content.replace(oldContent, newContent);
            Files.writeString(path, updatedContent, StandardCharsets.UTF_8);

            // Calculate what changed
            int oldLines = oldContent.split("\n", -1).length;
            int newLines = newContent.split("\n", -1).length;
            int lineDiff = newLines - oldLines;
            String diffStr = lineDiff == 0 ? "same line count"
                    : (lineDiff > 0 ? "+" + lineDiff + " lines" : lineDiff + " lines");

            return String.format("✓ Edited %s: replaced %d chars → %d chars (%s)",
                    path.getFileName(), oldContent.length(), newContent.length(), diffStr);

        } catch (IOException e) {
            throw new ToolException("Error editing file: " + e.getMessage(), e);
        }
    }

    private int countOccurrences(String content, String search) {
        int count = 0;
        int idx = 0;
        while ((idx = content.indexOf(search, idx)) != -1) {
            count++;
            idx += search.length();
        }
        return count;
    }

    private String getRequiredString(Map<String, Object> args, String key) throws ToolException {
        Object val = args.get(key);
        if (val == null) throw new ToolException("Missing required parameter: " + key);
        return val.toString();
    }
}
