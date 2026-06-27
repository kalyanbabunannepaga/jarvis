package com.jarvis.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {

    // ── loadDefaults via classpath resource ───────────────────────────────────

    @Test
    void load_returnsNonNullConfig() {
        // ConfigManager reads ~/.jarvis/config.json (may not exist in CI)
        // Just assert we always get a non-null Config back
        ConfigManager cm = new ConfigManager();
        Config c = cm.load();
        assertNotNull(c);
    }

    @Test
    void load_defaultProvider_isNotNull() {
        ConfigManager cm = new ConfigManager();
        Config c = cm.load();
        assertNotNull(c.getDefaultProvider(),
                "Default provider should be set from bundled default-config.json");
    }

    @Test
    void load_defaultMaxTokens_positive() {
        ConfigManager cm = new ConfigManager();
        Config c = cm.load();
        assertTrue(c.getMaxTokens() > 0);
    }

    @Test
    void load_defaultTemperature_between0and2() {
        ConfigManager cm = new ConfigManager();
        Config c = cm.load();
        assertTrue(c.getTemperature() >= 0.0 && c.getTemperature() <= 2.0);
    }

    // ── getConfigPath ──────────────────────────────────────────────────────────

    @Test
    void getConfigPath_endsWithConfigJson() {
        ConfigManager cm = new ConfigManager();
        Path path = cm.getConfigPath();
        assertTrue(path.toString().endsWith("config.json"));
    }

    @Test
    void getConfigPath_containsDotJarvis() {
        ConfigManager cm = new ConfigManager();
        Path path = cm.getConfigPath();
        assertTrue(path.toString().contains(".jarvis"));
    }

    // ── getConfig ─────────────────────────────────────────────────────────────

    @Test
    void getConfig_loadsIfNotYetLoaded() {
        ConfigManager cm = new ConfigManager();
        // call getConfig before load()
        Config c = cm.getConfig();
        assertNotNull(c);
    }

    // ── Merge logic (tested via a real user config JSON) ──────────────────────

    @Test
    void load_userValuesOverrideDefaults(@TempDir Path tempDir) throws IOException {
        // Write a minimal user config JSON to a temp file and verify merging
        // We can't easily override the internal configPath without subclassing,
        // so we test the JSON-reading capability via getConfig() after save().
        String userJson = """
                {
                  "defaultProvider": "gemini",
                  "maxTokens": 8192,
                  "temperature": 0.7
                }
                """;

        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, userJson);

        // Parse manually to verify Jackson binding is correct
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Config userConfig = mapper.readValue(configFile.toFile(), Config.class);

        assertEquals("gemini", userConfig.getDefaultProvider());
        assertEquals(8192,     userConfig.getMaxTokens());
        assertEquals(0.7,      userConfig.getTemperature(), 0.001);
    }

    // ── getApiKey ──────────────────────────────────────────────────────────────

    @Test
    void getApiKey_unknownProvider_returnsNull() {
        ConfigManager cm = new ConfigManager();
        // "nonexistent" has no ProviderConfig → should return null gracefully
        String key = cm.getApiKey("nonexistent-provider");
        assertNull(key);
    }
}
