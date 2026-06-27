package com.jarvis.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Shared infrastructure for HTTP-based LLM providers.
 *
 * <p>Centralises the {@link HttpClient} construction and the timeout constants
 * that were previously duplicated across every provider class, eliminating the
 * Copy-Paste-Detector (CPD) violations reported by SonarQube (squid:S4144).</p>
 */
public abstract class AbstractHttpProvider implements LLMProvider {

    /** Connection-establishment timeout shared by all providers. */
    protected static final int CONNECT_TIMEOUT_SECONDS = 10;

    /** Per-request read timeout shared by all providers. */
    protected static final int REQUEST_TIMEOUT_SECONDS = 120;

    /** Pre-built HTTP client reused across requests. */
    protected final HttpClient httpClient;

    /** Shared Jackson mapper instance. */
    protected final ObjectMapper mapper;

    protected AbstractHttpProvider() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Convenience factory that builds an {@link LLMResponse} representing an
     * error state.  Centralises the repeated builder pattern used in every
     * provider's catch block.
     *
     * @param message Human-readable description of the error.
     * @return A terminal {@link LLMResponse} with {@code FinishReason.ERROR}.
     */
    protected static LLMResponse errorResponse(String message) {
        return new LLMResponse.Builder()
                .content(message)
                .finishReason(LLMResponse.FinishReason.ERROR)
                .build();
    }
}
