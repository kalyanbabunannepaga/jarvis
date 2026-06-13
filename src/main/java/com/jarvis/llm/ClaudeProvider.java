package com.jarvis.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Anthropic Claude API adapter.
 * Handles: Claude Opus, Sonnet, Haiku models.
 * API: POST https://api.anthropic.com/v1/messages
 *
 * Claude uses a different message format with content blocks (text, tool_use, tool_result).
 */
public class ClaudeProvider implements LLMProvider {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public ClaudeProvider(String apiKey, String modelName, String baseUrl) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.anthropic.com/v1";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public String name() { return "Claude"; }

    @Override
    public String model() { return modelName; }

    @Override
    public LLMResponse chat(List<Message> messages, List<ToolDefinition> tools, RequestConfig config) {
        try {
            ObjectNode requestBody = buildRequestBody(messages, tools, config);
            String jsonBody = mapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return new LLMResponse.Builder()
                        .content("API Error (" + response.statusCode() + "): " + response.body())
                        .finishReason(LLMResponse.FinishReason.ERROR)
                        .build();
            }

            return parseResponse(response.body());

        } catch (Exception e) {
            return new LLMResponse.Builder()
                    .content("Error calling Claude: " + e.getMessage())
                    .finishReason(LLMResponse.FinishReason.ERROR)
                    .build();
        }
    }

    // --- Request building ---

    private ObjectNode buildRequestBody(List<Message> messages, List<ToolDefinition> tools, RequestConfig config) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", modelName);
        body.put("max_tokens", config.maxTokens());
        body.put("temperature", config.temperature());

        // System prompt — Claude takes it as a top-level field, not a message
        String systemPrompt = null;
        List<Message> nonSystemMessages = new ArrayList<>();
        for (Message msg : messages) {
            if (msg.getRole() == Message.Role.SYSTEM) {
                systemPrompt = msg.getContent();
            } else {
                nonSystemMessages.add(msg);
            }
        }
        if (systemPrompt != null) {
            body.put("system", systemPrompt);
        }

        // Messages
        ArrayNode messagesArray = body.putArray("messages");
        for (Message msg : nonSystemMessages) {
            messagesArray.add(convertMessage(msg));
        }

        // Tools
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = body.putArray("tools");
            for (ToolDefinition tool : tools) {
                ObjectNode toolNode = mapper.createObjectNode();
                toolNode.put("name", tool.getName());
                toolNode.put("description", tool.getDescription());
                toolNode.set("input_schema", mapper.valueToTree(tool.getParameters()));
                toolsArray.add(toolNode);
            }
        }

        return body;
    }

    private ObjectNode convertMessage(Message msg) {
        ObjectNode node = mapper.createObjectNode();

        switch (msg.getRole()) {
            case USER -> {
                node.put("role", "user");
                // If this is a regular user message
                if (msg.getToolCallId() == null) {
                    node.put("content", msg.getContent());
                } else {
                    // This shouldn't happen for USER role
                    node.put("content", msg.getContent());
                }
            }
            case ASSISTANT -> {
                node.put("role", "assistant");
                ArrayNode contentArray = node.putArray("content");

                // Add text content if present
                if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                    ObjectNode textBlock = mapper.createObjectNode();
                    textBlock.put("type", "text");
                    textBlock.put("text", msg.getContent());
                    contentArray.add(textBlock);
                }

                // Add tool_use blocks
                if (msg.hasToolCalls()) {
                    for (ToolCall tc : msg.getToolCalls()) {
                        ObjectNode toolUseBlock = mapper.createObjectNode();
                        toolUseBlock.put("type", "tool_use");
                        toolUseBlock.put("id", tc.getId());
                        toolUseBlock.put("name", tc.getFunctionName());
                        toolUseBlock.set("input", mapper.valueToTree(tc.getArguments()));
                        contentArray.add(toolUseBlock);
                    }
                }
            }
            case TOOL -> {
                // Claude expects tool results as user messages with tool_result content blocks
                node.put("role", "user");
                ArrayNode contentArray = node.putArray("content");
                ObjectNode resultBlock = mapper.createObjectNode();
                resultBlock.put("type", "tool_result");
                resultBlock.put("tool_use_id", msg.getToolCallId());
                resultBlock.put("content", msg.getContent() != null ? msg.getContent() : "");
                contentArray.add(resultBlock);
            }
            default -> {
                node.put("role", "user");
                node.put("content", msg.getContent() != null ? msg.getContent() : "");
            }
        }

        return node;
    }

    // --- Response parsing ---

    private LLMResponse parseResponse(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            String stopReason = root.path("stop_reason").asText("end_turn");

            StringBuilder textContent = new StringBuilder();
            List<ToolCall> toolCalls = new ArrayList<>();

            // Parse content blocks
            JsonNode contentArray = root.path("content");
            if (contentArray.isArray()) {
                for (JsonNode block : contentArray) {
                    String type = block.path("type").asText();
                    switch (type) {
                        case "text" -> textContent.append(block.path("text").asText());
                        case "tool_use" -> {
                            String id = block.path("id").asText();
                            String toolName = block.path("name").asText();
                            JsonNode inputNode = block.path("input");
                            Map<String, Object> args = mapper.convertValue(inputNode, new TypeReference<>() {});
                            toolCalls.add(new ToolCall(id, toolName, args));
                        }
                    }
                }
            }

            // Extract usage
            int promptTokens = root.path("usage").path("input_tokens").asInt(0);
            int completionTokens = root.path("usage").path("output_tokens").asInt(0);

            String content = textContent.length() > 0 ? textContent.toString() : null;

            return new LLMResponse.Builder()
                    .content(content)
                    .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                    .finishReason(LLMResponse.FinishReason.fromClaude(stopReason))
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .build();

        } catch (Exception e) {
            return new LLMResponse.Builder()
                    .content("Error parsing Claude response: " + e.getMessage())
                    .finishReason(LLMResponse.FinishReason.ERROR)
                    .build();
        }
    }
}
