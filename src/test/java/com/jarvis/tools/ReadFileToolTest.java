package com.jarvis.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReadFileToolTest {

    private ReadFileTool tool;

    @BeforeEach
    void setUp() {
        tool = new ReadFileTool();
    }

    // ── Metadata ──────────────────────────────────────────────────────────────

    @Test
    void name_isReadFile() {
        assertEquals("read_file", tool.name());
    }

    @Test
    void description_isNotBlank() {
        assertFalse(tool.description().isBlank());
    }

    @Test
    void parameterSchema_requiresPath() {
        Map<String, Object> schema = tool.parameterSchema();
        @SuppressWarnings("unchecked")
        java.util.List<String> required = (java.util.List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.contains("path"));
    }

    // ── execute — happy path ──────────────────────────────────────────────────

    @Test
    void execute_fullFile_returnsAllLines(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "line 1\nline 2\nline 3\n");

        String result = tool.execute(Map.of("path", file.toString()));
        assertTrue(result.contains("line 1"));
        assertTrue(result.contains("line 2"));
        assertTrue(result.contains("line 3"));
    }

    @Test
    void execute_includesLineNumbers(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("numbered.txt");
        Files.writeString(file, "alpha\nbeta\ngamma\n");

        String result = tool.execute(Map.of("path", file.toString()));
        // Format: "   1 | alpha"
        assertTrue(result.contains("1 | alpha") || result.contains("1  | alpha"),
                "Output should include line numbers");
    }

    @Test
    void execute_withLineRange_returnsOnlySelectedLines(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("range.txt");
        Files.writeString(file, "line1\nline2\nline3\nline4\nline5\n");

        String result = tool.execute(Map.of(
                "path", file.toString(),
                "startLine", 2,
                "endLine", 4
        ));

        assertTrue(result.contains("line2"));
        assertTrue(result.contains("line3"));
        assertTrue(result.contains("line4"));
        assertFalse(result.contains("line1"), "line1 should be excluded");
        assertFalse(result.contains("line5"), "line5 should be excluded");
    }

    @Test
    void execute_startBeyondEnd_clampsStartLine(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("small.txt");
        Files.writeString(file, "only\n");

        // startLine > total lines → ToolException
        assertThrows(ToolException.class, () -> tool.execute(Map.of(
                "path", file.toString(),
                "startLine", 999
        )));
    }

    @Test
    void execute_headerContainsTotalLines(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("meta.txt");
        Files.writeString(file, "a\nb\nc\n");

        String result = tool.execute(Map.of("path", file.toString()));
        assertTrue(result.contains("3 total lines") || result.contains("(3 total"),
                "Output should mention total line count");
    }

    @Test
    void execute_fileOver500Lines_truncatesTo500(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("big.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 600; i++) sb.append("line ").append(i).append("\n");
        Files.writeString(file, sb.toString());

        String result = tool.execute(Map.of("path", file.toString()));
        // Should show "more lines" indicator
        assertTrue(result.contains("more lines"));
    }

    // ── execute — error cases ─────────────────────────────────────────────────

    @Test
    void execute_fileNotFound_throwsToolException(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does_not_exist.txt");
        ToolException ex = assertThrows(ToolException.class,
                () -> tool.execute(Map.of("path", missing.toString())));
        assertTrue(ex.getMessage().contains("not found") || ex.getMessage().contains("not exist"),
                "Error should mention file not found");
    }

    @Test
    void execute_pathIsDirectory_throwsToolException(@TempDir Path tempDir) {
        ToolException ex = assertThrows(ToolException.class,
                () -> tool.execute(Map.of("path", tempDir.toString())));
        assertTrue(ex.getMessage().toLowerCase().contains("file"));
    }

    @Test
    void execute_missingPathParam_throwsToolException() {
        ToolException ex = assertThrows(ToolException.class,
                () -> tool.execute(Map.of()));
        assertTrue(ex.getMessage().contains("path"));
    }
}
