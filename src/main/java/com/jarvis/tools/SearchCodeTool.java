package com.jarvis.tools;

import com.jarvis.llm.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Tool to search for text patterns across files in a directory.
 * Performs grep-style recursive search with line numbers and context.
 */
public class SearchCodeTool implements Tool {

    private static final Logger logger = LoggerFactory.getLogger(SearchCodeTool.class);

    /** Files larger than this are skipped to avoid memory pressure. */
    private static final long MAX_FILE_SIZE_BYTES = 1_000_000L;

    private static final int MAX_RESULTS = 50;
    private static final Set<String> IGNORED_DIRS = Set.of(
        ".git", ".svn", "node_modules", "__pycache__",
        ".idea", ".vscode", ".gradle", "build", "target",
        "dist", ".next", "venv", ".venv"
    );
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
        ".class", ".jar", ".war", ".zip", ".gz", ".tar",
        ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
        ".pdf", ".doc", ".docx", ".xls", ".exe", ".dll",
        ".so", ".dylib", ".o", ".pyc", ".woff", ".woff2", ".ttf"
    );

    @Override
    public String name() {
        return "search_code";
    }

    @Override
    public String description() {
        return "Search for a text pattern across files in a directory. "
                + "Returns matching lines with file paths and line numbers. "
                + "The query is treated as a case-insensitive literal string by default. "
                + "Use filePattern to filter by file type (e.g., '*.java', '*.py'). "
                + "Results are capped at " + MAX_RESULTS + " matches.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("query", ToolDefinition.prop("string", "The text or pattern to search for"));
        props.put("path", ToolDefinition.prop("string", "The directory to search in. Defaults to current directory."));
        props.put("filePattern", ToolDefinition.prop("string", "Optional glob pattern to filter files (e.g., '*.java', '*.py'). Searches all text files if not specified."));
        return ToolDefinition.buildParameterSchema(props, List.of("query"));
    }

    @Override
    public String execute(Map<String, Object> arguments) throws ToolException {
        String query = getRequiredString(arguments, "query");
        String pathStr = arguments.getOrDefault("path", ".").toString();
        String filePattern = arguments.containsKey("filePattern") ? arguments.get("filePattern").toString() : null;

        Path searchPath = Paths.get(pathStr).toAbsolutePath().normalize();

        if (!Files.exists(searchPath)) {
            throw new ToolException("Directory not found: " + searchPath);
        }

        // Compile case-insensitive pattern
        Pattern pattern;
        try {
            pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            throw new ToolException("Invalid search pattern: " + e.getMessage());
        }

        List<SearchResult> results = new ArrayList<>();

        try {
            Files.walkFileTree(searchPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (IGNORED_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return results.size() >= MAX_RESULTS
                            ? FileVisitResult.TERMINATE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (results.size() >= MAX_RESULTS) {
                        return FileVisitResult.TERMINATE;
                    }

                    // Skip binary files
                    String fileName = file.getFileName().toString();
                    if (isBinary(fileName)) return FileVisitResult.CONTINUE;

                    // Check file pattern
                    if (filePattern != null && !matchesGlob(fileName, filePattern)) {
                        return FileVisitResult.CONTINUE;
                    }

                    // Skip large files (> MAX_FILE_SIZE_BYTES)
                    if (attrs.size() > MAX_FILE_SIZE_BYTES) return FileVisitResult.CONTINUE;

                    try {
                        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                        for (int i = 0; i < lines.size() && results.size() < MAX_RESULTS; i++) {
                            if (pattern.matcher(lines.get(i)).find()) {
                                String relativePath = searchPath.relativize(file).toString();
                                results.add(new SearchResult(relativePath, i + 1, lines.get(i).trim()));
                            }
                        }
                    } catch (IOException e) {
                        // Non-UTF-8 or unreadable files are intentionally skipped during search
                        logger.debug("Skipping unreadable file '{}': {}", file, e.getMessage());
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new ToolException("Error searching: " + e.getMessage(), e);
        }

        if (results.isEmpty()) {
            return "No matches found for '" + query + "' in " + searchPath;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d match%s for '%s':\n",
                results.size(), results.size() == 1 ? "" : "es", query));
        sb.append("---\n");

        for (SearchResult r : results) {
            sb.append(String.format("%s:%d: %s\n", r.file, r.line, r.content));
        }

        if (results.size() >= MAX_RESULTS) {
            sb.append(String.format("\n... (capped at %d results, narrow your search)\n", MAX_RESULTS));
        }

        return sb.toString();
    }

    private boolean isBinary(String fileName) {
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx < 0) return false;
        return BINARY_EXTENSIONS.contains(fileName.substring(dotIdx).toLowerCase());
    }

    private boolean matchesGlob(String fileName, String glob) {
        // Simple glob matching: *.ext
        if (glob.startsWith("*.")) {
            String ext = glob.substring(1);
            return fileName.toLowerCase().endsWith(ext.toLowerCase());
        }
        return fileName.equalsIgnoreCase(glob);
    }

    private String getRequiredString(Map<String, Object> args, String key) throws ToolException {
        Object val = args.get(key);
        if (val == null) throw new ToolException("Missing required parameter: " + key);
        return val.toString();
    }

    private record SearchResult(String file, int line, String content) {}
}
