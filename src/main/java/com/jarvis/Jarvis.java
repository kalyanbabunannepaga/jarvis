package com.jarvis;

import com.jarvis.agent.Agent;
import com.jarvis.agent.ConversationMemory;
import com.jarvis.config.Config;
import com.jarvis.config.ConfigManager;
import com.jarvis.llm.LLMProvider;
import com.jarvis.llm.ProviderFactory;
import com.jarvis.tools.ToolRegistry;
import com.jarvis.ui.TerminalUI;

import org.jline.reader.*;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;

/**
 * Jarvis — AI Coding Assistant Agent
 *
 * A CLI tool that uses LLMs (OpenAI, Claude, Gemini) with tool calling
 * to help you read, write, debug, and understand code.
 */
@Command(
    name = "jarvis",
    mixinStandardHelpOptions = true,
    version = "Jarvis 1.0.0",
    description = "AI Coding Assistant Agent — Chat with an AI that can read, write, and edit your code."
)
public class Jarvis implements Runnable {

    @Option(names = {"-p", "--provider"}, description = "LLM provider (openai, claude, gemini)")
    private String providerOverride;

    @Option(names = {"-m", "--model"}, description = "Model name (e.g., gpt-4o, claude-sonnet-4-20250514, gemini-2.5-pro)")
    private String modelOverride;

    private ConfigManager configManager;
    private TerminalUI ui;
    private Agent agent;
    private ProviderFactory providerFactory;
    private ToolRegistry toolRegistry;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Jarvis()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        ui = new TerminalUI();
        ui.showWelcomeBanner();

        // Load configuration
        configManager = new ConfigManager();
        Config config = configManager.load();

        // Resolve provider and model
        String providerName = providerOverride != null ? providerOverride : config.getDefaultProvider();
        String modelName = modelOverride;

        // Create provider factory
        providerFactory = new ProviderFactory(configManager);

        // Create LLM provider
        LLMProvider provider;
        try {
            if (modelName != null) {
                provider = providerFactory.create(providerName, modelName);
            } else {
                provider = providerFactory.create(providerName);
            }
        } catch (Exception e) {
            ui.showError(e.getMessage());
            ui.showInfo("Set the API key and try again:");
            ui.showInfo("  export " + getEnvKeyHint(providerName) + "=your-api-key");
            return;
        }

        // Create tool registry
        toolRegistry = ToolRegistry.createDefault(".", config.getCommandTimeoutSeconds());

        // Create conversation memory
        ConversationMemory memory = new ConversationMemory(config.getSystemPrompt());

        // Create agent
        agent = new Agent(provider, toolRegistry, memory, ui, config);

        // Show connection info
        ui.showConnectionInfo(provider.name(), provider.model());

        // Start interactive shell
        startInteractiveShell(config);
    }

    private void startInteractiveShell(Config config) {
        try {
            Terminal terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();

            // Set up tab completion for slash commands
            Completer completer = new StringsCompleter(
                    "/help", "/clear", "/model", "/provider",
                    "/config", "/tools", "/exit", "/quit"
            );

            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(completer)
                    .option(LineReader.Option.HISTORY_BEEP, false)
                    .option(LineReader.Option.AUTO_FRESH_LINE, true)
                    .build();

            String prompt = ui.getPrompt();

            while (true) {
                String input;
                try {
                    input = reader.readLine(prompt);
                } catch (UserInterruptException e) {
                    // Ctrl+C
                    System.out.println();
                    ui.showInfo("Interrupted. Type /exit to quit.");
                    continue;
                } catch (EndOfFileException e) {
                    // Ctrl+D
                    break;
                }

                if (input == null || input.isBlank()) {
                    continue;
                }

                String trimmed = input.trim();

                // Handle slash commands
                if (trimmed.startsWith("/")) {
                    if (handleSlashCommand(trimmed, config)) {
                        continue; // Command handled, prompt again
                    } else {
                        break; // /exit was called
                    }
                }

                // Process the message through the agent
                try {
                    agent.processMessage(trimmed);
                } catch (Exception e) {
                    ui.showError("Unexpected error: " + e.getMessage());
                }
            }

            terminal.close();

        } catch (IOException e) {
            ui.showError("Failed to initialize terminal: " + e.getMessage());
        }

        System.out.println();
        ui.showInfo("Goodbye! 👋");
    }

    /**
     * Handle slash commands. Returns true to continue, false to exit.
     */
    private boolean handleSlashCommand(String input, Config config) {
        String[] parts = input.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String argument = parts.length > 1 ? parts[1].trim() : null;

        switch (command) {
            case "/help" -> ui.showHelp();

            case "/clear" -> {
                agent.clearHistory();
                ui.showSuccess("Conversation history cleared.");
            }

            case "/model" -> {
                if (argument == null || argument.isEmpty()) {
                    ui.showInfo("Current model: " + agent.getProvider().model());
                    ui.showInfo("Usage: /model <model-name>");
                } else {
                    switchModel(argument, config);
                }
            }

            case "/provider" -> {
                if (argument == null || argument.isEmpty()) {
                    ui.showInfo("Current provider: " + agent.getProvider().name());
                    ui.showInfo("Usage: /provider <openai|claude|gemini>");
                } else {
                    switchProvider(argument, config);
                }
            }

            case "/config" -> {
                ui.showConfig(
                    agent.getProvider().name(),
                    agent.getProvider().model(),
                    configManager.getConfigPath().toString()
                );
            }

            case "/tools" -> ui.showTools(toolRegistry.getToolNames());

            case "/exit", "/quit" -> {
                return false;
            }

            default -> ui.showWarning("Unknown command: " + command + ". Type /help for available commands.");
        }

        return true;
    }

    private void switchModel(String newModel, Config config) {
        try {
            String providerName = agent.getProvider().name().toLowerCase();
            LLMProvider newProvider = providerFactory.create(providerName, newModel);

            // Recreate agent with new provider but keep memory
            agent = new Agent(newProvider, toolRegistry, agent.getMemory(), ui, config);
            ui.showSuccess("Switched to model: " + newModel);
        } catch (Exception e) {
            ui.showError("Failed to switch model: " + e.getMessage());
        }
    }

    private void switchProvider(String newProviderName, Config config) {
        try {
            LLMProvider newProvider = providerFactory.create(newProviderName);

            // Recreate agent with new provider but keep memory
            agent = new Agent(newProvider, toolRegistry, agent.getMemory(), ui, config);
            ui.showSuccess("Switched to " + newProvider.name() + " (" + newProvider.model() + ")");
        } catch (Exception e) {
            ui.showError("Failed to switch provider: " + e.getMessage());
        }
    }

    private String getEnvKeyHint(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> "OPENAI_API_KEY";
            case "claude", "anthropic" -> "ANTHROPIC_API_KEY";
            case "gemini", "google" -> "GOOGLE_API_KEY";
            case "groq" -> "GROQ_API_KEY";
            default -> "<PROVIDER>_API_KEY";
        };
    }
}
