package com.jarvis.agent;

import com.jarvis.config.Config;
import com.jarvis.llm.*;
import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolException;
import com.jarvis.tools.ToolRegistry;
import com.jarvis.ui.TerminalUI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentTest {

    @Mock LLMProvider provider;
    @Mock TerminalUI ui;

    private Config config;
    private ConversationMemory memory;
    private ToolRegistry toolRegistry;
    private Agent agent;

    @BeforeEach
    void setUp() {
        // Stub provider identity (called during most interactions)
        lenient().when(provider.name()).thenReturn("TestProvider");
        lenient().when(provider.model()).thenReturn("test-model");

        config = new Config();
        config.setMaxTokens(4096);
        config.setTemperature(0.1);
        config.setMaxToolIterations(5);

        memory = new ConversationMemory("You are a test assistant.");
        toolRegistry = new ToolRegistry();
        agent = new Agent(provider, toolRegistry, memory, ui, config);
    }

    // ── processMessage — happy path ───────────────────────────────────────────

    @Test
    void processMessage_finalTextResponse_returnsContent() {
        LLMResponse response = new LLMResponse.Builder()
                .content("Here is the answer.")
                .finishReason(LLMResponse.FinishReason.STOP)
                .promptTokens(50).completionTokens(10)
                .build();
        when(provider.chat(any(), any(), any())).thenReturn(response);

        String result = agent.processMessage("What is 2+2?");

        assertEquals("Here is the answer.", result);
        verify(ui).showAssistantMessage("Here is the answer.");
        verify(ui).showTokenUsage(50, 10);
    }

    @Test
    void processMessage_addsUserMessageToMemory() {
        LLMResponse response = new LLMResponse.Builder()
                .content("OK").finishReason(LLMResponse.FinishReason.STOP).build();
        when(provider.chat(any(), any(), any())).thenReturn(response);

        agent.processMessage("my question");

        long userMessages = memory.getMessages().stream()
                .filter(m -> m.getRole() == Message.Role.USER)
                .count();
        assertEquals(1, userMessages);
    }

    @Test
    void processMessage_nullLLMContent_returnsNoResponsePlaceholder() {
        LLMResponse response = new LLMResponse.Builder()
                .content(null).finishReason(LLMResponse.FinishReason.STOP).build();
        when(provider.chat(any(), any(), any())).thenReturn(response);

        String result = agent.processMessage("hello");
        assertEquals("(No response)", result);
    }

    // ── processMessage — error handling ───────────────────────────────────────

    @Test
    void processMessage_errorFinishReason_showsError() {
        LLMResponse response = new LLMResponse.Builder()
                .content("Rate limit exceeded")
                .finishReason(LLMResponse.FinishReason.ERROR)
                .build();
        when(provider.chat(any(), any(), any())).thenReturn(response);

        String result = agent.processMessage("anything");
        assertEquals("Rate limit exceeded", result);
        verify(ui).showError("Rate limit exceeded");
    }

    @Test
    void processMessage_errorFinishReason_nullContent_usesDefaultMessage() {
        LLMResponse response = new LLMResponse.Builder()
                .content(null)
                .finishReason(LLMResponse.FinishReason.ERROR)
                .build();
        when(provider.chat(any(), any(), any())).thenReturn(response);

        String result = agent.processMessage("anything");
        assertTrue(result.contains("TestProvider"));
    }

    // ── processMessage — max tokens ───────────────────────────────────────────

    @Test
    void processMessage_maxTokensResponse_appendsWarning() {
        LLMResponse response = new LLMResponse.Builder()
                .content("Partial answer")
                .finishReason(LLMResponse.FinishReason.MAX_TOKENS)
                .build();
        when(provider.chat(any(), any(), any())).thenReturn(response);

        String result = agent.processMessage("long question");
        assertTrue(result.contains("Partial answer"));
        assertTrue(result.contains("truncated"));
    }

    // ── processMessage — tool calls ───────────────────────────────────────────

    @Test
    void processMessage_singleToolCall_executesToolAndContinues() throws ToolException {
        // Register a fake tool
        Tool fakeTool = makeTool("echo_tool", "result from echo");
        toolRegistry.register(fakeTool);

        ToolCall toolCall = new ToolCall("call-1", "echo_tool", Map.of("input", "hi"));

        // First LLM call: returns tool call
        LLMResponse withTool = new LLMResponse.Builder()
                .finishReason(LLMResponse.FinishReason.TOOL_CALLS)
                .toolCalls(List.of(toolCall))
                .build();

        // Second LLM call: returns final text
        LLMResponse finalResp = new LLMResponse.Builder()
                .content("Done!")
                .finishReason(LLMResponse.FinishReason.STOP)
                .build();

        when(provider.chat(any(), any(), any()))
                .thenReturn(withTool)
                .thenReturn(finalResp);

        String result = agent.processMessage("do something");

        assertEquals("Done!", result);
        verify(ui).showToolCall(eq("echo_tool"), any());
        verify(ui).showToolResult(eq("echo_tool"), eq("result from echo"));
    }

    @Test
    void processMessage_toolException_showsToolError() throws ToolException {
        Tool failingTool = mock(Tool.class);
        when(failingTool.name()).thenReturn("bad_tool");
        when(failingTool.execute(any())).thenThrow(new ToolException("disk full"));
        when(failingTool.toDefinition()).thenReturn(new ToolDefinition("bad_tool", "desc", Map.of()));
        toolRegistry.register(failingTool);

        ToolCall toolCall = new ToolCall("call-2", "bad_tool", Map.of());

        LLMResponse withTool = new LLMResponse.Builder()
                .finishReason(LLMResponse.FinishReason.TOOL_CALLS)
                .toolCalls(List.of(toolCall))
                .build();

        LLMResponse finalResp = new LLMResponse.Builder()
                .content("Handled error.")
                .finishReason(LLMResponse.FinishReason.STOP)
                .build();

        when(provider.chat(any(), any(), any()))
                .thenReturn(withTool)
                .thenReturn(finalResp);

        agent.processMessage("trigger bad tool");
        verify(ui).showToolError(eq("bad_tool"), eq("disk full"));
    }

    // ── processMessage — max iterations ───────────────────────────────────────

    @Test
    void processMessage_maxIterations_returnsWarning() {
        // LLM always returns a tool call — agent should stop after maxToolIterations
        Tool infiniteTool = makeTool("loop_tool", "ok");
        toolRegistry.register(infiniteTool);
        ToolCall tc = new ToolCall("id", "loop_tool", Map.of());

        LLMResponse alwaysCallTool = new LLMResponse.Builder()
                .finishReason(LLMResponse.FinishReason.TOOL_CALLS)
                .toolCalls(List.of(tc))
                .build();
        when(provider.chat(any(), any(), any())).thenReturn(alwaysCallTool);

        String result = agent.processMessage("loop forever");
        assertTrue(result.contains("maximum tool iterations"));
        verify(ui).showWarning(contains("maximum tool iterations"));
    }

    // ── clearHistory ──────────────────────────────────────────────────────────

    @Test
    void clearHistory_resetsMemoryToSystemPromptOnly() {
        LLMResponse response = new LLMResponse.Builder()
                .content("hi").finishReason(LLMResponse.FinishReason.STOP).build();
        when(provider.chat(any(), any(), any())).thenReturn(response);

        agent.processMessage("some question");
        agent.clearHistory();

        // Only the system prompt remains
        assertEquals(1, memory.getMessages().size());
        assertEquals(Message.Role.SYSTEM, memory.getMessages().get(0).getRole());
    }

    // ── getProvider / getMemory ───────────────────────────────────────────────

    @Test
    void getProvider_returnsInjectedProvider() {
        assertSame(provider, agent.getProvider());
    }

    @Test
    void getMemory_returnsInjectedMemory() {
        assertSame(memory, agent.getMemory());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Tool makeTool(String name, String result) {
        Tool tool = mock(Tool.class);
        when(tool.name()).thenReturn(name);
        try {
            when(tool.execute(any())).thenReturn(result);
        } catch (ToolException ignored) {}
        when(tool.toDefinition()).thenReturn(new ToolDefinition(name, "desc", Map.of()));
        return tool;
    }
}
