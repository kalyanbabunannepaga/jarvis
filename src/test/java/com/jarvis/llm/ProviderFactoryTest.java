package com.jarvis.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProviderFactoryTest {

    /**
     * Tests the fully-explicit create() overload, which just instantiates the
     * correct provider class without touching config files or env vars.
     */
    private static final String FAKE_KEY = "test-api-key-1234";
    private static final String FAKE_MODEL = "test-model";

    @Test
    void create_openai_returnsOpenAIProvider() {
        LLMProvider p = makeFactory().create("openai", FAKE_KEY, FAKE_MODEL, null);
        assertEquals("OpenAI", p.name());
        assertEquals(FAKE_MODEL, p.model());
    }

    @Test
    void create_claude_returnsClaudeProvider() {
        LLMProvider p = makeFactory().create("claude", FAKE_KEY, FAKE_MODEL, null);
        assertEquals("Claude", p.name());
    }

    @Test
    void create_anthropic_aliasReturnsClaudeProvider() {
        LLMProvider p = makeFactory().create("anthropic", FAKE_KEY, FAKE_MODEL, null);
        assertEquals("Claude", p.name());
    }

    @Test
    void create_gemini_returnsGeminiProvider() {
        LLMProvider p = makeFactory().create("gemini", FAKE_KEY, FAKE_MODEL, null);
        assertEquals("Gemini", p.name());
    }

    @Test
    void create_google_aliasReturnsGeminiProvider() {
        LLMProvider p = makeFactory().create("google", FAKE_KEY, FAKE_MODEL, null);
        assertEquals("Gemini", p.name());
    }

    @Test
    void create_groq_usesOpenAIProvider() {
        // Groq is served via OpenAI-compatible API
        LLMProvider p = makeFactory().create("groq", FAKE_KEY, FAKE_MODEL, "https://api.groq.com/openai/v1");
        assertEquals("OpenAI", p.name());
    }

    @Test
    void create_unknownProvider_throwsIllegalArgumentException() {
        ProviderFactory factory = makeFactory();
        assertThrows(IllegalArgumentException.class,
                () -> factory.create("unknown-llm", FAKE_KEY, FAKE_MODEL, null));
    }

    @Test
    void create_caseInsensitive() {
        LLMProvider p = makeFactory().create("OPENAI", FAKE_KEY, FAKE_MODEL, null);
        assertEquals("OpenAI", p.name());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ProviderFactory makeFactory() {
        // ConfigManager not needed for the explicit-param overload
        return new ProviderFactory(null);
    }
}
