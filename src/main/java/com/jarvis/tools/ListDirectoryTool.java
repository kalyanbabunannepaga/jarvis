package com.jarvis.tools;

import com.jarvis.llm.ToolDefinition;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Tool to list directory contents with file sizes and type indicators.
 * Supports recursive listing and respects common ignore patterns.
 */
public class ListDirectoryTool implements Tool {

    /** Maximum depth for recursive directory listings. */
    private static final int MAX_RECURSIVE_DEPTH = 5;

    private static final Set<String> IGNORED_DIRS = Set.of(
        ".git", ".svn", ".hg", "node_modules", "__pycache__",
        ".idea", ".vscode", ".gradle", "build", "target",
        "dist", ".next", "venv", ".venv", "env"
    );

    @Override
    public String name() {
        return "list_directory";
    }

    @Override
    public String description() {
        return "List the contents of a directory, showing files and subdirectories with their sizes. "
                + "By default lists only the immediate children. Set recursive=true for a tree view. "
                + "Common directories like .git, node_modules, target, etc. are automatically excluded.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", ToolDefinition.prop("string", "The directory path to list (relative or absolute). Defaults to current directory."));
        props.put("recursive", ToolDefinition.prop("boolean", "If true, list recursively as a tree. Default is false."));
        return ToolDefinition.buildParameterSchema(props, List.of("path"));
    }

    @Override
    public String execute(Map<String, Object> arguments) throws ToolException {
        String pathStr = arguments.getOrDefault("path", ".").toString();
        boolean recursive = Boolean.TRUE.equals(arguments.get("recursive"));
        Path path = Paths.get(pathStr).toAbsolutePath().normalize();

        if (!Files.exists(path)) {
            throw new ToolException("Directory not found: " + path);
        }
        if (!Files.isDirectory(path)) {
            throw new ToolException("Not a directory: " + path);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Directory: ").append(path).append("\n");
        sb.append("---\n");

        try {
            if (recursive) {
                listRecursive(path, path, sb, "", 0);
            } else {
                listFlat(path, sb);
            }
        } catch (IOException e) {
            throw new ToolException("Error listing directory: " + e.getMessage(), e);
        }

        return sb.toString();
    }

    private void listFlat(Path dir, StringBuilder sb) throws IOException {
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (!isIgnored(entry)) {
                    entries.add(entry);
                }
            }
        }

        // Sort: directories first, then files, alphabetically
        entries.sort(dirFirstAlpha());

        int dirCount = 0, fileCount = 0;
        for (Path entry : entries) {
            if (Files.isDirectory(entry)) {
                sb.append("  📁 ").append(entry.getFileName()).append("/\n");
                dirCount++;
            } else {
                long size = Files.size(entry);
                sb.append("  📄 ").append(entry.getFileName())
                  .append(" (").append(formatSize(size)).append(")\n");
                fileCount++;
            }
        }

        sb.append("---\n");
        sb.append(String.format("Total: %d directories, %d files\n", dirCount, fileCount));
    }

    private void listRecursive(Path root, Path current, StringBuilder sb, String indent, int depth) throws IOException {
        if (depth > MAX_RECURSIVE_DEPTH) {
            sb.append(indent).append("  ... (max depth reached)\n");
            return;
        }

        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            for (Path entry : stream) {
                if (!isIgnored(entry)) {
                    entries.add(entry);
                }
            }
        }

        entries.sort(dirFirstAlpha());

        for (Path entry : entries) {
            if (Files.isDirectory(entry)) {
                sb.append(indent).append("📁 ").append(entry.getFileName()).append("/\n");
                listRecursive(root, entry, sb, indent + "  ", depth + 1);
            } else {
                long size = Files.size(entry);
                sb.append(indent).append("📄 ").append(entry.getFileName())
                  .append(" (").append(formatSize(size)).append(")\n");
            }
        }
    }

    private boolean isIgnored(Path path) {
        String name = path.getFileName().toString();
        if (name.startsWith(".") && Files.isDirectory(path) && !name.equals(".")) {
            return IGNORED_DIRS.contains(name);
        }
        return IGNORED_DIRS.contains(name);
    }

    /**
     * Comparator that sorts directories before files, then alphabetically.
     * Extracted to avoid duplicate lambda CPD violations (squid:S4144).
     */
    private static Comparator<Path> dirFirstAlpha() {
        return (a, b) -> {
            boolean aIsDir = Files.isDirectory(a);
            boolean bIsDir = Files.isDirectory(b);
            if (aIsDir != bIsDir) return aIsDir ? -1 : 1;
            return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
        };
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
