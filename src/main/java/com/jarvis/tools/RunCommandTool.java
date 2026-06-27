package com.jarvis.tools;

import com.jarvis.llm.ToolDefinition;

import java.io.*;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Tool to execute shell commands on the user's system.
 * Requires user confirmation before executing each command.
 * Enforces a timeout to prevent runaway processes.
 */
public class RunCommandTool implements Tool {

    /** Maximum lines of command output captured before truncation. */
    private static final int MAX_OUTPUT_LINES = 200;

    private final int timeoutSeconds;

    public RunCommandTool(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
    }

    @Override
    public String name() {
        return "run_command";
    }

    @Override
    public String description() {
        return "Execute a shell command on the user's system and return its output. "
                + "The user will be prompted to confirm before execution. "
                + "Use this for running tests, builds, installing dependencies, git commands, etc. "
                + "Commands time out after " + timeoutSeconds + " seconds.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("command", ToolDefinition.prop("string", "The shell command to execute"));
        props.put("workingDirectory", ToolDefinition.prop("string", "The working directory for the command. Defaults to current directory."));
        return ToolDefinition.buildParameterSchema(props, List.of("command"));
    }

    @Override
    public String execute(Map<String, Object> arguments) throws ToolException {
        String command = getRequiredString(arguments, "command");
        String workDir = arguments.containsKey("workingDirectory")
                ? arguments.get("workingDirectory").toString() : ".";

        // Prompt user for confirmation — intentional terminal UI output, not logging (NOSONAR)
        System.out.println(); //NOSONAR
        System.out.println("\u001B[33m┌─ 🔧 Command Execution Request ─────────────────────────\u001B[0m"); //NOSONAR
        System.out.println("\u001B[33m│\u001B[0m Command: \u001B[1m" + command + "\u001B[0m"); //NOSONAR
        System.out.println("\u001B[33m│\u001B[0m Directory: " + Paths.get(workDir).toAbsolutePath().normalize()); //NOSONAR
        System.out.println("\u001B[33m└─────────────────────────────────────────────────────────\u001B[0m"); //NOSONAR
        System.out.print("\u001B[33mExecute this command? [y/N]: \u001B[0m"); //NOSONAR

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String response = reader.readLine();
            if (response == null || !response.trim().toLowerCase().startsWith("y")) {
                return "Command execution cancelled by user.";
            }
        } catch (IOException e) {
            throw new ToolException("Error reading user input: " + e.getMessage());
        }

        // Execute the command
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.directory(Paths.get(workDir).toAbsolutePath().normalize().toFile());
            pb.redirectErrorStream(true);

            // Use shell to interpret the command
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("/bin/sh", "-c", command);
            }

            Process process = pb.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    lineCount++;
                    System.out.println("  " + line); //NOSONAR intentional real-time output
                    // Cap output at MAX_OUTPUT_LINES lines
                    if (lineCount >= MAX_OUTPUT_LINES) {
                        output.append("... (output truncated at ").append(MAX_OUTPUT_LINES).append(" lines)\n");
                        break;
                    }
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Command timed out after " + timeoutSeconds + " seconds.\nPartial output:\n" + output;
            }

            int exitCode = process.exitValue();
            String result = output.toString();

            if (exitCode == 0) {
                return String.format("✓ Command completed (exit code: 0)\n%s",
                        result.isEmpty() ? "(no output)" : result);
            } else {
                return String.format("✗ Command failed (exit code: %d)\n%s", exitCode, result);
            }

        } catch (IOException e) {
            throw new ToolException("Error executing command: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolException("Command interrupted: " + e.getMessage(), e);
        }
    }

    private String getRequiredString(Map<String, Object> args, String key) throws ToolException {
        Object val = args.get(key);
        if (val == null) throw new ToolException("Missing required parameter: " + key);
        return val.toString();
    }
}
