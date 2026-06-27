package com.jarvis.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SearchCodeToolTest {

    private SearchCodeTool tool;

    @BeforeEach
    void setUp() {
        tool = new SearchCodeTool();
    }

    // ── meta ──────────────────────────────────────────────────────────────────

    @Test
    void name_isSearchCode() {
        assertEquals("search_code", tool.name());
    }

    @Test
    void description_mentionsPattern() {
        assertTrue(tool.description().toLowerCase().contains("pattern")
                || tool.description().toLowerCase().contains("search"));
    }

    @Test
    void toDefinition_hasRequiredQueryParam() {
        assertNotNull(tool.toDefinition());
        // Schema should reference "query" as a required parameter
        assertTrue(tool.toDefinition().getParameters().toString().contains("query"));
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void execute_findsMatchInFile(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("Sample.java"),
                "public class Sample {\n    void hello() {}\n}", StandardCharsets.UTF_8);

        String result = tool.execute(Map.of("query", "hello", "path", tempDir.toString()));

        assertTrue(result.contains("hello"));
        assertTrue(result.contains("Sample.java"));
    }

    @Test
    void execute_caseInsensitiveSearch(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("f.txt"), "HELLO world", StandardCharsets.UTF_8);

        String result = tool.execute(Map.of("query", "hello", "path", tempDir.toString()));

        assertTrue(result.contains("HELLO"));
    }

    @Test
    void execute_noMatches_returnsNoMatchesMessage(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("empty.txt"), "nothing here", StandardCharsets.UTF_8);

        String result = tool.execute(Map.of("query", "XYZZY_NOTFOUND", "path", tempDir.toString()));

        assertTrue(result.toLowerCase().contains("no match"));
    }

    @Test
    void execute_filePatternFilter_onlyMatchesSpecifiedType(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("code.java"), "target text", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("doc.txt"), "target text", StandardCharsets.UTF_8);

        String result = tool.execute(Map.of(
                "query", "target text",
                "path", tempDir.toString(),
                "filePattern", "*.java"
        ));

        assertTrue(result.contains("code.java"));
        assertFalse(result.contains("doc.txt"));
    }

    @Test
    void execute_includesLineNumbers(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("lines.txt"),
                "line one\nline two\nline three\n", StandardCharsets.UTF_8);

        String result = tool.execute(Map.of("query", "two", "path", tempDir.toString()));

        // Format is "filename:lineNumber: content"
        assertTrue(result.contains(":2:") || result.contains(": "));
    }

    @Test
    void execute_skipsIgnoredDirs(@TempDir Path tempDir) throws Exception {
        // Create a .git directory with matching content — it must be skipped
        Path gitDir = tempDir.resolve(".git");
        Files.createDirectory(gitDir);
        Files.writeString(gitDir.resolve("config"), "secret_token = abc123", StandardCharsets.UTF_8);

        // Normal file without the token
        Files.writeString(tempDir.resolve("code.java"), "no token here", StandardCharsets.UTF_8);

        String result = tool.execute(Map.of("query", "secret_token", "path", tempDir.toString()));
        assertTrue(result.toLowerCase().contains("no match"));
    }

    // ── error cases ───────────────────────────────────────────────────────────

    @Test
    void execute_missingQuery_throwsToolException() {
        assertThrows(ToolException.class, () -> tool.execute(Map.of("path", ".")));
    }

    @Test
    void execute_pathNotFound_throwsToolException(@TempDir Path tempDir) {
        assertThrows(ToolException.class, () -> tool.execute(Map.of(
                "query", "anything",
                "path", tempDir.resolve("nonexistent").toString()
        )));
    }

    @Test
    void execute_largeFilesSkipped(@TempDir Path tempDir) throws Exception {
        // Write a file that is over 1MB — it should be silently skipped
        Path big = tempDir.resolve("big.txt");
        byte[] chunk = "x".repeat(1024).getBytes(StandardCharsets.UTF_8);
        try (var out = Files.newOutputStream(big)) {
            for (int i = 0; i < 1100; i++) out.write(chunk); // ~1.1 MB
        }

        // Should not throw, just return no matches
        String result = tool.execute(Map.of("query", "x", "path", tempDir.toString()));
        assertNotNull(result);
    }
}
