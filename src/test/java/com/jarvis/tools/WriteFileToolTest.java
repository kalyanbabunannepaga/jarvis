package com.jarvis.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WriteFileToolTest {

    private WriteFileTool tool;

    @BeforeEach
    void setUp() {
        tool = new WriteFileTool();
    }

    // ── Metadata ──────────────────────────────────────────────────────────────

    @Test
    void name_isWriteFile() {
        assertEquals("write_file", tool.name());
    }

    @Test
    void description_isNotBlank() {
        assertFalse(tool.description().isBlank());
    }

    @Test
    void parameterSchema_requiresPathAndContent() {
        Map<String, Object> schema = tool.parameterSchema();
        @SuppressWarnings("unchecked")
        java.util.List<String> required = (java.util.List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.contains("path"));
        assertTrue(required.contains("content"));
    }

    // ── execute — create new file ─────────────────────────────────────────────

    @Test
    void execute_createsNewFile(@TempDir Path tempDir) throws Exception {
        Path newFile = tempDir.resolve("output.txt");
        String content = "Hello, World!\n";

        String result = tool.execute(Map.of("path", newFile.toString(), "content", content));

        assertTrue(Files.exists(newFile), "File should have been created");
        assertEquals(content, Files.readString(newFile));
        assertTrue(result.contains("Created"), "Result should say 'Created'");
    }

    @Test
    void execute_returnsLineCount(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("lines.txt");
        String content = "one\ntwo\nthree\n";

        String result = tool.execute(Map.of("path", file.toString(), "content", content));
        assertTrue(result.contains("3 lines"), "Result should report line count");
    }

    @Test
    void execute_returnsByteCount(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("bytes.txt");
        String content = "hello"; // 5 bytes in UTF-8

        String result = tool.execute(Map.of("path", file.toString(), "content", content));
        assertTrue(result.contains("5 bytes"), "Result should report byte size");
    }

    // ── execute — overwrite existing file ─────────────────────────────────────

    @Test
    void execute_overwritesExistingFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("overwrite.txt");
        Files.writeString(file, "old content");

        String result = tool.execute(Map.of("path", file.toString(), "content", "new content"));

        assertEquals("new content", Files.readString(file));
        assertTrue(result.contains("Updated"), "Result should say 'Updated' for overwrite");
    }

    // ── execute — creates parent directories ──────────────────────────────────

    @Test
    void execute_createsParentDirectories(@TempDir Path tempDir) throws Exception {
        Path deepFile = tempDir.resolve("a/b/c/deep.txt");

        String result = tool.execute(Map.of("path", deepFile.toString(), "content", "data"));

        assertTrue(Files.exists(deepFile), "File in nested dirs should be created");
        assertTrue(result.contains("Created"));
    }

    // ── execute — error cases ─────────────────────────────────────────────────

    @Test
    void execute_missingPath_throwsToolException() {
        ToolException ex = assertThrows(ToolException.class,
                () -> tool.execute(Map.of("content", "some content")));
        assertTrue(ex.getMessage().contains("path"));
    }

    @Test
    void execute_missingContent_throwsToolException(@TempDir Path tempDir) {
        ToolException ex = assertThrows(ToolException.class,
                () -> tool.execute(Map.of("path", tempDir.resolve("x.txt").toString())));
        assertTrue(ex.getMessage().contains("content"));
    }
}
