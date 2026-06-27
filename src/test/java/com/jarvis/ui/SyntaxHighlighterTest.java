package com.jarvis.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SyntaxHighlighterTest {

    private SyntaxHighlighter highlighter;

    @BeforeEach
    void setUp() {
        highlighter = new SyntaxHighlighter();
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    void highlight_null_returnsNull() {
        assertNull(highlighter.highlight(null, "java"));
    }

    @Test
    void highlight_emptyString_returnsEmpty() {
        assertEquals("", highlighter.highlight("", "java"));
    }

    // ── Java keywords ─────────────────────────────────────────────────────────

    @Test
    void highlight_javaKeyword_containsAnsiCode() {
        String result = highlighter.highlight("public class Foo {", "java");
        // ANSI codes are present → the raw string is longer than the input
        assertTrue(result.length() > "public class Foo {".length(),
                "Highlighted output should contain ANSI escape codes");
    }

    @Test
    void highlight_javaKeyword_preservesAllWords() {
        // Strip ANSI codes and verify tokens are preserved
        String result = highlighter.highlight("return value;", "java");
        String stripped = result.replaceAll("\\u001B\\[[;\\d]*m", "");
        assertTrue(stripped.contains("return"));
        assertTrue(stripped.contains("value"));
    }

    @Test
    void highlight_javaKotlinScala_useSameKeywords() {
        String java   = highlighter.highlight("class", "java");
        String kotlin = highlighter.highlight("class", "kotlin");
        String scala  = highlighter.highlight("class", "scala");
        // All three should produce identical output (same keyword set)
        assertEquals(java, kotlin);
        assertEquals(java, scala);
    }

    // ── Python keywords ───────────────────────────────────────────────────────

    @Test
    void highlight_pythonKeyword_isHighlighted() {
        String result = highlighter.highlight("def my_func(x):", "python");
        String stripped = result.replaceAll("\\u001B\\[[;\\d]*m", "");
        assertTrue(stripped.contains("def"));
    }

    @Test
    void highlight_python_pyAlias() {
        String a = highlighter.highlight("def f():", "python");
        String b = highlighter.highlight("def f():", "py");
        assertEquals(a, b);
    }

    // ── JavaScript keywords ───────────────────────────────────────────────────

    @Test
    void highlight_jsKeyword_isHighlighted() {
        String result = highlighter.highlight("const x = 42;", "javascript");
        String stripped = result.replaceAll("\\u001B\\[[;\\d]*m", "");
        assertTrue(stripped.contains("const"));
    }

    @Test
    void highlight_js_aliases() {
        String js  = highlighter.highlight("const x = 1;", "js");
        String ts  = highlighter.highlight("const x = 1;", "typescript");
        String tsx = highlighter.highlight("const x = 1;", "tsx");
        assertEquals(js, ts);
        assertEquals(js, tsx);
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    @Test
    void highlight_javaComment_coloredSeparately() {
        // Java-style // comment
        String line = "int x = 1; // set x";
        String result = highlighter.highlight(line, "java");
        // ANSI codes present (both for keywords and the comment)
        assertFalse(result.equals(line), "Comment should be colored");
    }

    @Test
    void highlight_pythonComment_startsWithHash() {
        String line = "x = 1  # assign";
        String result = highlighter.highlight(line, "python");
        assertFalse(result.equals(line));
    }

    @Test
    void highlight_commentInsideString_notTreatedAsComment() {
        // The // inside a string literal should NOT split the line
        String line = "String s = \"http://example.com\";";
        String result = highlighter.highlight(line, "java");
        String stripped = result.replaceAll("\\u001B\\[[;\\d]*m", "");
        // The part after // inside the string should still be present
        assertTrue(stripped.contains("example.com"),
                "Content inside string should not be treated as a comment");
    }

    // ── Numbers ───────────────────────────────────────────────────────────────

    @Test
    void highlight_integerLiteral_isHighlighted() {
        String result = highlighter.highlight("int x = 42;", "java");
        String stripped = result.replaceAll("\\u001B\\[[;\\d]*m", "");
        assertTrue(stripped.contains("42"));
        // Should be longer than raw (ANSI codes added)
        assertTrue(result.length() > stripped.length());
    }

    @Test
    void highlight_floatLiteral_isHighlighted() {
        String result = highlighter.highlight("double pi = 3.14;", "java");
        String stripped = result.replaceAll("\\u001B\\[[;\\d]*m", "");
        assertTrue(stripped.contains("3.14"));
    }

    // ── Type detection (starts uppercase) ─────────────────────────────────────

    @Test
    void highlight_uppercaseType_isColored() {
        String result = highlighter.highlight("ArrayList list = new ArrayList();", "java");
        // Should be longer than input due to ANSI codes
        assertTrue(result.length() > "ArrayList list = new ArrayList();".length());
    }

    // ── Annotations ──────────────────────────────────────────────────────────

    @Test
    void highlight_annotation_isColored() {
        String result = highlighter.highlight("@Override", "java");
        assertTrue(result.length() > "@Override".length(),
                "Annotation should be highlighted");
        String stripped = result.replaceAll("\\u001B\\[[;\\d]*m", "");
        assertTrue(stripped.contains("@Override"));
    }

    // ── Unknown / null language falls back to Java ────────────────────────────

    @Test
    void highlight_nullLanguage_usesJavaFallback() {
        // Should not throw
        assertDoesNotThrow(() -> highlighter.highlight("public void foo() {}", null));
    }

    @Test
    void highlight_unknownLanguage_usesJavaFallback() {
        String result = highlighter.highlight("public class X {}", "cobol");
        // "public" and "class" are Java keywords → should still be highlighted
        assertTrue(result.length() > "public class X {}".length());
    }
}
