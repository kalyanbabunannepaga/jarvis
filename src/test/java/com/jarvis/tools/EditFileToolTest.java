package com.jarvis.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EditFileToolTest {

    private EditFileTool tool;

    @BeforeEach
    void setUp() {
        tool = new EditFileTool();
    }

    // ── meta ──────────────────────────────────────────────────────────────────

    @Test
    void name_isEditFile() {
        assertEquals("edit_file", tool.name());
    }

    @Test
    void description_mentionsExact() {
        assertTrue(tool.description().toLowerCase().contains("exact"));
    }

    @Test
    void toDefinition_hasRequiredParams() {
        var def = tool.toDefinition();
        assertNotNull(def);
        assertEquals("edit_file", def.getName());
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void execute_replacesContentSuccessfully(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "Hello World\nSecond line\n", StandardCharsets.UTF_8);

        String result = tool.execute(Map.of(
                "path", file.toString(),
                "oldContent", "Hello World",
                "newContent", "Hi Universe"
        ));

        assertTrue(result.contains("✓"));
        String updated = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(updated.contains("Hi Universe"));
        assertFalse(updated.contains("Hello World"));
    }

    @Test
    void execute_returnsCharCountInfo(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("f.txt");
        Files.writeString(file, "abc", StandardCharsets.UTF_8);

        String result = tool.execute(Map.of(
                "path", file.toString(),
                "oldContent", "abc",
                "newContent", "abcdef"
        ));

        // Result should report chars replaced and same-line-count or +N lines
        assertTrue(result.contains("chars"));
    }

    @Test
    void execute_multiLineReplacement(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("multi.java");
        Files.writeString(file, "line1\nline2\nline3\n", StandardCharsets.UTF_8);

        String result = tool.execute(Map.of(
                "path", file.toString(),
                "oldContent", "line1\nline2",
                "newContent", "replaced1\nreplaced2\nreplaced3"
        ));

        assertTrue(result.contains("✓"));
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("replaced1"));
        assertTrue(content.contains("replaced3"));
        assertFalse(content.contains("line1"));
    }

    // ── error cases ───────────────────────────────────────────────────────────

    @Test
    void execute_fileNotFound_throwsToolException(@TempDir Path tempDir) {
        assertThrows(ToolException.class, () -> tool.execute(Map.of(
                "path", tempDir.resolve("nonexistent.txt").toString(),
                "oldContent", "x",
                "newContent", "y"
        )));
    }

    @Test
    void execute_notARegularFile_throwsToolException(@TempDir Path tempDir) {
        // Pass a directory path instead of a file
        assertThrows(ToolException.class, () -> tool.execute(Map.of(
                "path", tempDir.toString(),
                "oldContent", "x",
                "newContent", "y"
        )));
    }

    @Test
    void execute_oldContentNotFound_throwsToolException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("f.txt");
        Files.writeString(file, "actual content", StandardCharsets.UTF_8);

        ToolException ex = assertThrows(ToolException.class, () -> tool.execute(Map.of(
                "path", file.toString(),
                "oldContent", "THIS DOES NOT EXIST",
                "newContent", "replacement"
        )));
        assertTrue(ex.getMessage().toLowerCase().contains("could not find"));
    }

    @Test
    void execute_multipleOccurrences_throwsToolException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("dup.txt");
        Files.writeString(file, "dup dup", StandardCharsets.UTF_8);

        ToolException ex = assertThrows(ToolException.class, () -> tool.execute(Map.of(
                "path", file.toString(),
                "oldContent", "dup",
                "newContent", "replaced"
        )));
        assertTrue(ex.getMessage().contains("2"));
    }

    @Test
    void execute_missingPath_throwsToolException() {
        assertThrows(ToolException.class, () -> tool.execute(Map.of(
                "oldContent", "x",
                "newContent", "y"
        )));
    }

    @Test
    void execute_missingOldContent_throwsToolException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("f.txt");
        Files.writeString(file, "content", StandardCharsets.UTF_8);
        assertThrows(ToolException.class, () -> tool.execute(Map.of(
                "path", file.toString(),
                "newContent", "y"
        )));
    }

    @Test
    void execute_missingNewContent_throwsToolException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("f.txt");
        Files.writeString(file, "content", StandardCharsets.UTF_8);
        assertThrows(ToolException.class, () -> tool.execute(Map.of(
                "path", file.toString(),
                "oldContent", "content"
        )));
    }
}
