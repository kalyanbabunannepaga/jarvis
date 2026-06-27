package com.jarvis.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    // ── Defaults ──────────────────────────────────────────────────────────────

    @Test
    void defaultProvider_isOpenai() {
        assertEquals("openai", new Config().getDefaultProvider());
    }

    @Test
    void defaultModel_isGpt4o() {
        assertEquals("gpt-4o", new Config().getDefaultModel());
    }

    @Test
    void defaultMaxTokens_is4096() {
        assertEquals(4096, new Config().getMaxTokens());
    }

    @Test
    void defaultTemperature_is01() {
        assertEquals(0.1, new Config().getTemperature(), 0.001);
    }

    @Test
    void defaultMaxToolIterations_is15() {
        assertEquals(15, new Config().getMaxToolIterations());
    }

    @Test
    void defaultCommandTimeout_is30() {
        assertEquals(30, new Config().getCommandTimeoutSeconds());
    }

    // ── Setters / Getters ─────────────────────────────────────────────────────

    @Test
    void setAndGetDefaultProvider() {
        Config c = new Config();
        c.setDefaultProvider("claude");
        assertEquals("claude", c.getDefaultProvider());
    }

    @Test
    void setAndGetDefaultModel() {
        Config c = new Config();
        c.setDefaultModel("claude-sonnet-4");
        assertEquals("claude-sonnet-4", c.getDefaultModel());
    }

    @Test
    void setAndGetMaxTokens() {
        Config c = new Config();
        c.setMaxTokens(8192);
        assertEquals(8192, c.getMaxTokens());
    }

    @Test
    void setAndGetTemperature() {
        Config c = new Config();
        c.setTemperature(0.7);
        assertEquals(0.7, c.getTemperature(), 0.001);
    }

    @Test
    void setAndGetMaxToolIterations() {
        Config c = new Config();
        c.setMaxToolIterations(20);
        assertEquals(20, c.getMaxToolIterations());
    }

    @Test
    void setAndGetCommandTimeoutSeconds() {
        Config c = new Config();
        c.setCommandTimeoutSeconds(60);
        assertEquals(60, c.getCommandTimeoutSeconds());
    }

    @Test
    void setAndGetSystemPrompt() {
        Config c = new Config();
        c.setSystemPrompt("You are Jarvis.");
        assertEquals("You are Jarvis.", c.getSystemPrompt());
    }

    // ── getProviderConfig ─────────────────────────────────────────────────────

    @Test
    void getProviderConfig_returnsCorrectConfig() {
        Config.ProviderConfig pc = new Config.ProviderConfig();
        pc.setModel("gpt-4o");
        pc.setEnvKey("OPENAI_API_KEY");

        Config c = new Config();
        c.setProviders(Map.of("openai", pc));

        Config.ProviderConfig result = c.getProviderConfig("openai");
        assertNotNull(result);
        assertEquals("gpt-4o", result.getModel());
        assertEquals("OPENAI_API_KEY", result.getEnvKey());
    }

    @Test
    void getProviderConfig_caseInsensitive() {
        Config.ProviderConfig pc = new Config.ProviderConfig();
        Config c = new Config();
        c.setProviders(Map.of("openai", pc));

        // Provider name is lowercased inside getProviderConfig
        assertNotNull(c.getProviderConfig("OPENAI"));
    }

    @Test
    void getProviderConfig_unknownProvider_returnsNull() {
        Config c = new Config();
        c.setProviders(Map.of("openai", new Config.ProviderConfig()));
        assertNull(c.getProviderConfig("unknown-provider"));
    }

    @Test
    void getProviderConfig_nullProviders_returnsNull() {
        Config c = new Config();
        // providers field is null by default
        assertNull(c.getProviderConfig("openai"));
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void toString_containsKeyFields() {
        Config c = new Config();
        c.setDefaultProvider("gemini");
        String s = c.toString();
        assertTrue(s.contains("gemini"));
        assertTrue(s.contains("4096")); // maxTokens
    }

    // ── ProviderConfig ────────────────────────────────────────────────────────

    @Test
    void providerConfig_settersAndGetters() {
        Config.ProviderConfig pc = new Config.ProviderConfig();
        pc.setModel("claude-sonnet");
        pc.setBaseUrl("https://api.anthropic.com");
        pc.setEnvKey("ANTHROPIC_API_KEY");

        assertEquals("claude-sonnet",             pc.getModel());
        assertEquals("https://api.anthropic.com", pc.getBaseUrl());
        assertEquals("ANTHROPIC_API_KEY",          pc.getEnvKey());
    }

    @Test
    void providerConfig_toString_containsFields() {
        Config.ProviderConfig pc = new Config.ProviderConfig();
        pc.setModel("gpt-4o");
        pc.setEnvKey("OPENAI_API_KEY");
        String s = pc.toString();
        assertTrue(s.contains("gpt-4o"));
        assertTrue(s.contains("OPENAI_API_KEY"));
    }
}
