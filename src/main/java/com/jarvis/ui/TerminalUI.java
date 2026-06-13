package com.jarvis.ui;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rich terminal UI for Jarvis using ANSI escape codes.
 * Provides colored output, tool call formatting, and markdown rendering.
 */
public class TerminalUI {

    // ANSI color codes
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String ITALIC = "\u001B[3m";
    private static final String UNDERLINE = "\u001B[4m";

    // Foreground colors
    private static final String BLACK = "\u001B[30m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";

    // Bright colors
    private static final String BRIGHT_GREEN = "\u001B[92m";
    private static final String BRIGHT_YELLOW = "\u001B[93m";
    private static final String BRIGHT_BLUE = "\u001B[94m";
    private static final String BRIGHT_MAGENTA = "\u001B[95m";
    private static final String BRIGHT_CYAN = "\u001B[96m";

    // Background colors
    private static final String BG_DARK = "\u001B[48;5;236m";
    private static final String BG_CODE = "\u001B[48;5;235m";

    private final SyntaxHighlighter highlighter;
    private volatile boolean isThinking = false;

    public TerminalUI() {
        this.highlighter = new SyntaxHighlighter();
    }

    /**
     * Display the welcome banner.
     */
    public void showWelcomeBanner() {
        System.out.println();
        System.out.println(BRIGHT_CYAN + BOLD +
                "     ╦╔═╗╦═╗╦  ╦╦╔═╗" + RESET);
        System.out.println(BRIGHT_CYAN + BOLD +
                "     ║╠═╣╠╦╝╚╗╔╝║╚═╗" + RESET);
        System.out.println(BRIGHT_CYAN + BOLD +
                "    ╚╝╩ ╩╩╚═ ╚╝ ╩╚═╝" + RESET);
        System.out.println();
        System.out.println(DIM + "  AI Coding Assistant Agent v1.0.0" + RESET);
        System.out.println(DIM + "  ─────────────────────────────────" + RESET);
        System.out.println();
    }

    /**
     * Show connection info after successful provider setup.
     */
    public void showConnectionInfo(String providerName, String model) {
        System.out.println(GREEN + "  ✓ Connected to " + BOLD + providerName + RESET
                + GREEN + " using " + BOLD + model + RESET);
        System.out.println(DIM + "  Type /help for commands, or start chatting!" + RESET);
        System.out.println();
    }

    /**
     * Show the thinking/loading indicator.
     */
    public void showThinking(String provider, String model) {
        isThinking = true;
        System.out.print(DIM + "  ⏳ Thinking" + RESET);
        System.out.print(DIM + " (" + provider + "/" + model + ")..." + RESET);
    }

    /**
     * Clear the thinking indicator.
     */
    public void clearThinking() {
        if (isThinking) {
            // Move cursor to start of line and clear
            System.out.print("\r\u001B[K");
            isThinking = false;
        }
    }

    /**
     * Display the assistant's final response.
     */
    public void showAssistantMessage(String content) {
        System.out.println();
        System.out.println(GREEN + BOLD + "  🤖 Jarvis:" + RESET);
        System.out.println();
        printFormattedContent(content, "     ");
        System.out.println();
    }

    /**
     * Display the assistant's intermediate thinking text (before tool calls).
     */
    public void showAssistantThinking(String content) {
        System.out.println(DIM + "  💭 " + truncate(content, 100) + RESET);
    }

    /**
     * Display a tool call being made.
     */
    public void showToolCall(String toolName, Map<String, Object> arguments) {
        System.out.println();
        System.out.println(YELLOW + "  🔧 " + BOLD + toolName + RESET + YELLOW + " ──────" + RESET);

        // Show key arguments (truncated for readability)
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue().toString() : "null";
            if (value.length() > 80) {
                value = value.substring(0, 77) + "...";
            }
            // Don't show very long content params
            if (entry.getKey().equals("content") && value.length() > 40) {
                value = value.substring(0, 37) + "... (" + entry.getValue().toString().length() + " chars)";
            }
            System.out.println(DIM + "     " + entry.getKey() + ": " + RESET + value);
        }
    }

    /**
     * Display a tool result.
     */
    public void showToolResult(String toolName, String result) {
        // Truncate long results for display
        String display = result;
        if (display.length() > 500) {
            display = display.substring(0, 497) + "...";
        }

        String[] lines = display.split("\n");
        System.out.println(DIM + YELLOW + "     ↳ Result:" + RESET);
        for (String line : lines) {
            System.out.println(DIM + "       " + line + RESET);
        }
    }

    /**
     * Display a tool error.
     */
    public void showToolError(String toolName, String error) {
        System.out.println(RED + "     ✗ Error in " + toolName + ": " + error + RESET);
    }

    /**
     * Display token usage info.
     */
    public void showTokenUsage(int promptTokens, int completionTokens) {
        if (promptTokens > 0 || completionTokens > 0) {
            int total = promptTokens + completionTokens;
            System.out.println(DIM + "     📊 Tokens: " + promptTokens + " in → "
                    + completionTokens + " out (total: " + total + ")" + RESET);
        }
    }

    /**
     * Display an error message.
     */
    public void showError(String message) {
        System.out.println(RED + BOLD + "  ✗ Error: " + RESET + RED + message + RESET);
    }

    /**
     * Display a warning message.
     */
    public void showWarning(String message) {
        System.out.println(YELLOW + "  ⚠ " + message + RESET);
    }

    /**
     * Display an info message.
     */
    public void showInfo(String message) {
        System.out.println(CYAN + "  ℹ " + message + RESET);
    }

    /**
     * Display a success message.
     */
    public void showSuccess(String message) {
        System.out.println(GREEN + "  ✓ " + message + RESET);
    }

    /**
     * Display the help text.
     */
    public void showHelp() {
        System.out.println();
        System.out.println(BOLD + "  Available Commands:" + RESET);
        System.out.println(CYAN + "    /help" + RESET + "          Show this help message");
        System.out.println(CYAN + "    /clear" + RESET + "         Clear conversation history");
        System.out.println(CYAN + "    /model" + RESET + " <name>  Switch to a different model");
        System.out.println(CYAN + "    /provider" + RESET + " <n>  Switch LLM provider (openai/claude/gemini)");
        System.out.println(CYAN + "    /config" + RESET + "        Show current configuration");
        System.out.println(CYAN + "    /tools" + RESET + "         List available tools");
        System.out.println(CYAN + "    /exit" + RESET + "          Exit Jarvis");
        System.out.println();
        System.out.println(DIM + "  Or just type your message to chat with the AI." + RESET);
        System.out.println();
    }

    /**
     * Display configuration info.
     */
    public void showConfig(String provider, String model, String configPath) {
        System.out.println();
        System.out.println(BOLD + "  Current Configuration:" + RESET);
        System.out.println("    Provider: " + BRIGHT_CYAN + provider + RESET);
        System.out.println("    Model:    " + BRIGHT_CYAN + model + RESET);
        System.out.println("    Config:   " + DIM + configPath + RESET);
        System.out.println();
    }

    /**
     * Display available tools.
     */
    public void showTools(java.util.Set<String> toolNames) {
        System.out.println();
        System.out.println(BOLD + "  Available Tools:" + RESET);
        for (String name : toolNames) {
            System.out.println(YELLOW + "    🔧 " + name + RESET);
        }
        System.out.println();
    }

    /**
     * Get the prompt string for the input line.
     */
    public String getPrompt() {
        return CYAN + BOLD + "  🧑 You: " + RESET;
    }

    // --- Private helpers ---

    /**
     * Print content with basic markdown rendering (code blocks, bold, etc.).
     */
    private void printFormattedContent(String content, String indent) {
        String[] lines = content.split("\n");
        boolean inCodeBlock = false;
        String codeLanguage = "";

        for (String line : lines) {
            // Code block delimiters
            if (line.trim().startsWith("```")) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    codeLanguage = line.trim().substring(3).trim();
                    System.out.println(indent + BG_CODE + DIM + " ─── "
                            + (codeLanguage.isEmpty() ? "code" : codeLanguage) + " ───" + RESET);
                } else {
                    inCodeBlock = false;
                    System.out.println(indent + BG_CODE + DIM + " ───────────" + RESET);
                }
                continue;
            }

            if (inCodeBlock) {
                // Syntax highlight code
                String highlighted = highlighter.highlight(line, codeLanguage);
                System.out.println(indent + BG_CODE + " " + highlighted + RESET);
            } else {
                // Basic inline formatting
                String formatted = formatInlineMarkdown(line);
                System.out.println(indent + formatted);
            }
        }
    }

    /**
     * Basic inline markdown formatting: **bold**, `code`, *italic*.
     */
    private String formatInlineMarkdown(String line) {
        // Headers
        if (line.startsWith("### ")) {
            return BOLD + BRIGHT_CYAN + line.substring(4) + RESET;
        }
        if (line.startsWith("## ")) {
            return BOLD + BRIGHT_CYAN + line.substring(3) + RESET;
        }
        if (line.startsWith("# ")) {
            return BOLD + BRIGHT_CYAN + UNDERLINE + line.substring(2) + RESET;
        }

        // Bullet points
        if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
            int indent = line.indexOf(line.trim().charAt(0));
            String spaces = " ".repeat(indent);
            return spaces + BRIGHT_CYAN + "•" + RESET + line.trim().substring(1);
        }

        // Inline code: `text`
        line = replacePattern(line, "`([^`]+)`", DIM + BG_CODE + " $1 " + RESET);

        // Bold: **text**
        line = replacePattern(line, "\\*\\*([^*]+)\\*\\*", BOLD + "$1" + RESET);

        // Italic: *text*
        line = replacePattern(line, "\\*([^*]+)\\*", ITALIC + "$1" + RESET);

        return line;
    }

    private String replacePattern(String input, String regex, String replacement) {
        try {
            return input.replaceAll(regex, replacement);
        } catch (Exception e) {
            return input;
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}
