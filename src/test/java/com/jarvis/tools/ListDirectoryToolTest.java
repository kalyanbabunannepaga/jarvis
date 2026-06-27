package com.jarvis.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ListDirectoryToolTest {

    private ListDirectoryTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListDirectoryTool();
    }

    // ── meta ──────────────────────────────────────────────────────────────────

    @Test
    void name_isListDirectory() {
        assertEquals("list_directory", tool.name());
    }

    @Test
    void description_isNonEmpty() {
        assertFalse(tool.description().isBlank());
    }

    @Test
    void toDefinition_hasExpectedName() {
        assertEquals("list_directory", tool.toDefinition().getName());
    }

    // ── flat listing ──────────────────────────────────────────────────────────

    @Test
    void execute_flatListing_showsFiles(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "aaa", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("b.txt"), "bbb", StandardCharsets.UTF_8);

        String result = tool.execute(Map.of("path", tempDir.toString()));

        assertTrue(result.contains("a.txt"));
        assertTrue(result.contains("b.txt"));
        assertTrue(result.contains("Total:"));
    }

    @Test
    void execute_flatListing_showsSubdirs(@TempDir Path tempDir) throws Exception {
        Path sub = tempDir.resolve("mydir");
        Files.createDirectory(sub);

        String result = tool.execute(Map.of("path", tempDir.toString()));

        assertTrue(result.contains("mydir"));
    }

    @Test
    void execute_flatListing_countsCorrectly(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("file1.txt"), "x", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("file2.txt"), "y", StandardCharsets.UTF_8);
        Files.createDirectory(tempDir.resolve("subdir"));

        String result = tool.execute(Map.of("path", tempDir.toString()));

        assertTrue(result.contains("1 director"));
        assertTrue(result.contains("2 file"));
    }

    @Test
    void execute_defaultsToCurrentDirectory_doesNotThrow() {
        // Uses "." as default — should succeed if current directory is readable
        assertDoesNotThrow(() -> tool.execute(Map.of()));
    }

    // ── ignored directories ───────────────────────────────────────────────────

    @Test
    void execute_ignoresDotGitDir(@TempDir Path tempDir) throws Exception {
        Path gitDir = tempDir.resolve(".git");
        Files.createDirectory(gitDir);
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main", StandardCharsets.UTF_8);

        String result = tool.execute(Map.of("path", tempDir.toString()));
        // .git should not appear in flat listing output
        assertFalse(result.contains(".git"), ".git dir should be ignored");
    }

    // ── recursive listing ─────────────────────────────────────────────────────

    @Test
    void execute_recursive_listsNestedFiles(@TempDir Path tempDir) throws Exception {
        Path nested = tempDir.resolve("src").resolve("main");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("Main.java"), "class Main {}", StandardCharsets.UTF_8);

        String result = tool.execute(Map.of("path", tempDir.toString(), "recursive", true));

        assertTrue(result.contains("Main.java"));
        assertTrue(result.contains("src"));
    }

    // ── error cases ───────────────────────────────────────────────────────────

    @Test
    void execute_directoryNotFound_throwsToolException(@TempDir Path tempDir) {
        assertThrows(ToolException.class, () -> tool.execute(
                Map.of("path", tempDir.resolve("nonexistent").toString())
        ));
    }

    @Test
    void execute_pathIsFile_throwsToolException(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("f.txt");
        Files.writeString(file, "content", StandardCharsets.UTF_8);

        assertThrows(ToolException.class, () -> tool.execute(Map.of("path", file.toString())));
    }
}
