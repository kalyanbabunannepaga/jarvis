package com.jarvis.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LLMResponseTest {

    // ── Builder defaults ──────────────────────────────────────────────────────

    @Test
    void builder_defaultFinishReason_isStop() {
        LLMResponse r = new LLMResponse.Builder().content("hello").build();
        assertEquals(LLMResponse.FinishReason.STOP, r.getFinishReason());
    }

    @Test
    void builder_allFields() {
        ToolCall tc = new ToolCall("id1", "fn", Map.of());
        LLMResponse r = new LLMResponse.Builder()
                .content("some text")
                .toolCalls(List.of(tc))
                .finishReason(LLMResponse.FinishReason.TOOL_CALLS)
                .promptTokens(100)
                .completionTokens(50)
                .build();

        assertEquals("some text",                       r.getContent());
        assertEquals(LLMResponse.FinishReason.TOOL_CALLS, r.getFinishReason());
        assertEquals(100, r.getPromptTokens());
        assertEquals(50,  r.getCompletionTokens());
        assertEquals(150, r.getTotalTokens());
        assertTrue(r.hasToolCalls());
    }

    // ── hasToolCalls ──────────────────────────────────────────────────────────

    @Test
    void hasToolCalls_false_whenNull() {
        LLMResponse r = new LLMResponse.Builder().content("text").build();
        assertFalse(r.hasToolCalls());
    }

    @Test
    void hasToolCalls_false_whenEmptyList() {
        LLMResponse r = new LLMResponse.Builder().toolCalls(List.of()).build();
        assertFalse(r.hasToolCalls());
    }

    @Test
    void hasToolCalls_true_whenNonEmpty() {
        ToolCall tc = new ToolCall("id", "fn", Map.of());
        LLMResponse r = new LLMResponse.Builder().toolCalls(List.of(tc)).build();
        assertTrue(r.hasToolCalls());
    }

    // ── getTotalTokens ────────────────────────────────────────────────────────

    @Test
    void getTotalTokens_sumsBoth() {
        LLMResponse r = new LLMResponse.Builder()
                .promptTokens(200).completionTokens(75).build();
        assertEquals(275, r.getTotalTokens());
    }

    @Test
    void getTotalTokens_defaults_zeroWhenNotSet() {
        LLMResponse r = new LLMResponse.Builder().build();
        assertEquals(0, r.getTotalTokens());
    }

    // ── FinishReason.fromOpenAI ───────────────────────────────────────────────

    @Test
    void finishReason_fromOpenAI_null_returnsStop() {
        assertEquals(LLMResponse.FinishReason.STOP, LLMResponse.FinishReason.fromOpenAI(null));
    }

    @Test
    void finishReason_fromOpenAI_stop() {
        assertEquals(LLMResponse.FinishReason.STOP, LLMResponse.FinishReason.fromOpenAI("stop"));
    }

    @Test
    void finishReason_fromOpenAI_toolCalls() {
        assertEquals(LLMResponse.FinishReason.TOOL_CALLS, LLMResponse.FinishReason.fromOpenAI("tool_calls"));
    }

    @Test
    void finishReason_fromOpenAI_length() {
        assertEquals(LLMResponse.FinishReason.MAX_TOKENS, LLMResponse.FinishReason.fromOpenAI("length"));
    }

    @Test
    void finishReason_fromOpenAI_unknown_fallsToStop() {
        assertEquals(LLMResponse.FinishReason.STOP, LLMResponse.FinishReason.fromOpenAI("content_filter"));
    }

    // ── FinishReason.fromClaude ───────────────────────────────────────────────

    @Test
    void finishReason_fromClaude_null_returnsStop() {
        assertEquals(LLMResponse.FinishReason.STOP, LLMResponse.FinishReason.fromClaude(null));
    }

    @Test
    void finishReason_fromClaude_endTurn() {
        assertEquals(LLMResponse.FinishReason.STOP, LLMResponse.FinishReason.fromClaude("end_turn"));
    }

    @Test
    void finishReason_fromClaude_toolUse() {
        assertEquals(LLMResponse.FinishReason.TOOL_CALLS, LLMResponse.FinishReason.fromClaude("tool_use"));
    }

    @Test
    void finishReason_fromClaude_maxTokens() {
        assertEquals(LLMResponse.FinishReason.MAX_TOKENS, LLMResponse.FinishReason.fromClaude("max_tokens"));
    }

    // ── FinishReason.fromGemini ───────────────────────────────────────────────

    @Test
    void finishReason_fromGemini_null_returnsStop() {
        assertEquals(LLMResponse.FinishReason.STOP, LLMResponse.FinishReason.fromGemini(null));
    }

    @Test
    void finishReason_fromGemini_stop() {
        assertEquals(LLMResponse.FinishReason.STOP, LLMResponse.FinishReason.fromGemini("STOP"));
    }

    @Test
    void finishReason_fromGemini_maxTokens() {
        assertEquals(LLMResponse.FinishReason.MAX_TOKENS, LLMResponse.FinishReason.fromGemini("MAX_TOKENS"));
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void toString_containsFinishReason() {
        String s = new LLMResponse.Builder()
                .finishReason(LLMResponse.FinishReason.ERROR).build().toString();
        assertTrue(s.contains("ERROR"));
    }

    @Test
    void toString_includesToolCallCount() {
        ToolCall tc = new ToolCall("id", "fn", Map.of());
        String s = new LLMResponse.Builder()
                .toolCalls(List.of(tc, tc)).build().toString();
        assertTrue(s.contains("toolCalls=2"));
    }

    @Test
    void toString_truncatesLongContent() {
        String longContent = "x".repeat(200);
        String s = new LLMResponse.Builder().content(longContent).build().toString();
        assertTrue(s.contains("..."));
    }
}
