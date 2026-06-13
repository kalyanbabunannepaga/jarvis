package com.jarvis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages loading, saving, and merging Jarvis configuration.
 * Config file location: ~/.jarvis/config.json
 */
public class ConfigManager {

    private static final String CONFIG_DIR = ".jarvis";
    private static final String CONFIG_FILE = "config.json";
    private static final String DEFAULT_CONFIG_RESOURCE = "/default-config.json";

    private final ObjectMapper mapper;
    private final Path configPath;
    private Config config;

    public ConfigManager() {
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.configPath = Paths.get(System.getProperty("user.home"), CONFIG_DIR, CONFIG_FILE);
    }

    /**
     * Load configuration: user config file → defaults → fallback.
     */
    public Config load() {
        // 1. Load bundled defaults
        Config defaults = loadDefaults();

        // 2. Try loading user config
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                Config userConfig = mapper.readValue(json, Config.class);
                this.config = merge(defaults, userConfig);
            } catch (IOException e) {
                System.err.println("⚠ Warning: Failed to read config file, using defaults: " + e.getMessage());
                this.config = defaults;
            }
        } else {
            this.config = defaults;
            // Save defaults so user can edit them
            save(this.config);
        }

        return this.config;
    }

    /**
     * Save configuration to ~/.jarvis/config.json.
     */
    public void save(Config config) {
        try {
            Files.createDirectories(configPath.getParent());
            mapper.writeValue(configPath.toFile(), config);
        } catch (IOException e) {
            System.err.println("⚠ Warning: Failed to save config file: " + e.getMessage());
        }
    }

    /**
     * Get API key for a provider from environment variables.
     */
    public String getApiKey(String providerName) {
        if (config == null) {
            load();
        }
        Config.ProviderConfig pc = config.getProviderConfig(providerName);
        if (pc == null || pc.getEnvKey() == null) {
            return null;
        }
        return System.getenv(pc.getEnvKey());
    }

    /**
     * Get the currently loaded config (loads if not yet loaded).
     */
    public Config getConfig() {
        if (config == null) {
            load();
        }
        return config;
    }

    /**
     * Get the path to the config file.
     */
    public Path getConfigPath() {
        return configPath;
    }

    // --- Private helpers ---

    private Config loadDefaults() {
        try (InputStream is = getClass().getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
            if (is != null) {
                return mapper.readValue(is, Config.class);
            }
        } catch (IOException e) {
            // Fall through to hardcoded defaults
        }
        // Hardcoded fallback
        return new Config();
    }

    /**
     * Merge user config over defaults — user values take priority where set.
     */
    private Config merge(Config defaults, Config user) {
        Config merged = new Config();

        merged.setDefaultProvider(
            user.getDefaultProvider() != null ? user.getDefaultProvider() : defaults.getDefaultProvider()
        );
        merged.setDefaultModel(
            user.getDefaultModel() != null ? user.getDefaultModel() : defaults.getDefaultModel()
        );
        merged.setMaxTokens(user.getMaxTokens() > 0 ? user.getMaxTokens() : defaults.getMaxTokens());
        merged.setTemperature(user.getTemperature() >= 0 ? user.getTemperature() : defaults.getTemperature());
        merged.setMaxToolIterations(
            user.getMaxToolIterations() > 0 ? user.getMaxToolIterations() : defaults.getMaxToolIterations()
        );
        merged.setCommandTimeoutSeconds(
            user.getCommandTimeoutSeconds() > 0 ? user.getCommandTimeoutSeconds() : defaults.getCommandTimeoutSeconds()
        );
        merged.setSystemPrompt(
            user.getSystemPrompt() != null ? user.getSystemPrompt() : defaults.getSystemPrompt()
        );
        merged.setProviders(
            user.getProviders() != null ? user.getProviders() : defaults.getProviders()
        );

        return merged;
    }
}
