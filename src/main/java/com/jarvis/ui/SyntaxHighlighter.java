package com.jarvis.ui;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Basic syntax highlighter for terminal output.
 * Applies ANSI colors to common language constructs.
 */
public class SyntaxHighlighter {

    // ANSI codes
    private static final String RESET = "\u001B[0m";
    private static final String KEYWORD = "\u001B[38;5;198m";     // Pink
    private static final String STRING = "\u001B[38;5;113m";      // Green
    private static final String NUMBER = "\u001B[38;5;141m";      // Purple
    private static final String COMMENT = "\u001B[38;5;242m";     // Gray
    private static final String TYPE = "\u001B[38;5;81m";         // Cyan
    private static final String FUNC = "\u001B[38;5;220m";        // Yellow
    private static final String OPERATOR = "\u001B[38;5;203m";    // Red

    // Common keywords across languages
    private static final Set<String> JAVA_KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch",
        "char", "class", "const", "continue", "default", "do", "double",
        "else", "enum", "extends", "final", "finally", "float", "for",
        "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "var", "void", "volatile", "while", "yield",
        "record", "sealed", "permits", "when"
    );

    private static final Set<String> PYTHON_KEYWORDS = Set.of(
        "and", "as", "assert", "async", "await", "break", "class", "continue",
        "def", "del", "elif", "else", "except", "False", "finally", "for",
        "from", "global", "if", "import", "in", "is", "lambda", "None",
        "nonlocal", "not", "or", "pass", "raise", "return", "True", "try",
        "while", "with", "yield"
    );

    private static final Set<String> JS_KEYWORDS = Set.of(
        "async", "await", "break", "case", "catch", "class", "const",
        "continue", "debugger", "default", "delete", "do", "else",
        "export", "extends", "false", "finally", "for", "from", "function",
        "if", "import", "in", "instanceof", "let", "new", "null", "of",
        "return", "static", "super", "switch", "this", "throw", "true",
        "try", "typeof", "undefined", "var", "void", "while", "with", "yield"
    );

    /**
     * Apply syntax highlighting to a line of code.
     */
    public String highlight(String line, String language) {
        if (line == null || line.isEmpty()) return line;

        Set<String> keywords = getKeywordsForLanguage(language);

        // Handle line comments first
        String commentPrefix = getCommentPrefix(language);
        int commentIdx = findCommentStart(line, commentPrefix);
        String codePart = commentIdx >= 0 ? line.substring(0, commentIdx) : line;
        String commentPart = commentIdx >= 0 ? line.substring(commentIdx) : "";

        // Highlight the code part
        StringBuilder result = new StringBuilder();
        String[] tokens = codePart.split("(?<=[\\s{}()\\[\\];,.<>!=+\\-*/&|^~?:])|(?=[\\s{}()\\[\\];,.<>!=+\\-*/&|^~?:])");

        for (String token : tokens) {
            if (token.isEmpty()) continue;

            if (keywords.contains(token)) {
                result.append(KEYWORD).append(token).append(RESET);
            } else if (isString(token)) {
                result.append(STRING).append(token).append(RESET);
            } else if (isNumber(token)) {
                result.append(NUMBER).append(token).append(RESET);
            } else if (isType(token)) {
                result.append(TYPE).append(token).append(RESET);
            } else if (isAnnotation(token, language)) {
                result.append(FUNC).append(token).append(RESET);
            } else {
                result.append(token);
            }
        }

        // Add comment part in gray
        if (!commentPart.isEmpty()) {
            result.append(COMMENT).append(commentPart).append(RESET);
        }

        return result.toString();
    }

    private Set<String> getKeywordsForLanguage(String lang) {
        if (lang == null) return JAVA_KEYWORDS;
        return switch (lang.toLowerCase()) {
            case "java", "kotlin", "scala" -> JAVA_KEYWORDS;
            case "python", "py" -> PYTHON_KEYWORDS;
            case "javascript", "js", "typescript", "ts", "jsx", "tsx" -> JS_KEYWORDS;
            default -> JAVA_KEYWORDS; // Default to Java-like
        };
    }

    private String getCommentPrefix(String language) {
        if (language == null) return "//";
        return switch (language.toLowerCase()) {
            case "python", "py", "bash", "sh", "shell", "yaml", "yml" -> "#";
            default -> "//";
        };
    }

    private int findCommentStart(String line, String prefix) {
        // Simple: find comment prefix not inside a string
        boolean inString = false;
        char stringChar = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (!inString && (c == '"' || c == '\'')) {
                inString = true;
                stringChar = c;
            } else if (inString && c == stringChar && (i == 0 || line.charAt(i - 1) != '\\')) {
                inString = false;
            } else if (!inString && line.startsWith(prefix, i)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isString(String token) {
        return (token.startsWith("\"") && token.endsWith("\""))
                || (token.startsWith("'") && token.endsWith("'"));
    }

    private boolean isNumber(String token) {
        try {
            Double.parseDouble(token);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isType(String token) {
        // Heuristic: starts with uppercase, contains lowercase
        return token.length() > 1
                && Character.isUpperCase(token.charAt(0))
                && token.chars().anyMatch(Character::isLowerCase);
    }

    private boolean isAnnotation(String token, String lang) {
        return token.startsWith("@");
    }
}
