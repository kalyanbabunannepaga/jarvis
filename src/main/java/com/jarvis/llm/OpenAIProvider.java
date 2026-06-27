package com.jarvis.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI API adapter.
 * Handles: GPT-4o, GPT-4, GPT-3.5-turbo, o1, o3, etc.
 * API: POST {@value #DEFAULT_BASE_URL}/chat/completions
 */
public class OpenAIProvider extends AbstractHttpProvider {

    /** Default OpenAI API base URL. Override via constructor for compatible endpoints (e.g., Groq). */
    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private final String apiKey;
    private final String modelName;
    private final String baseUrl;

    public OpenAIProvider(String apiKey, String modelName, String baseUrl) {
        super();
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
    }

    @Override
    public String name() { return "OpenAI"; }

    @Override
    public String model() { return modelName; }

    @Override
    public LLMResponse chat(List<Message> messages, List<ToolDefinition> tools, RequestConfig config) {
        try {
            ObjectNode requestBody = buildRequestBody(messages, tools, config);
            String jsonBody = mapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return errorResponse("API Error (" + response.statusCode() + "): " + response.body());
            }

            return parseResponse(response.body());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return errorResponse("Request to OpenAI was interrupted: " + e.getMessage());
        } catch (IOException e) {
            return errorResponse("Error calling OpenAI: " + e.getMessage());
        }
    }

    // --- Request building ---

    private ObjectNode buildRequestBody(List<Message> messages, List<ToolDefinition> tools, RequestConfig config) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", modelName);
        body.put("max_tokens", config.maxTokens());
        body.put("temperature", config.temperature());

        // Messages
        ArrayNode messagesArray = body.putArray("messages");
        for (Message msg : messages) {
            messagesArray.add(convertMessage(msg));
        }

        // Tools
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = body.putArray("tools");
            for (ToolDefinition tool : tools) {
                ObjectNode toolNode = mapper.createObjectNode();
                toolNode.put("type", "function");
                ObjectNode funcNode = toolNode.putObject("function");
                funcNode.put("name", tool.getName());
                funcNode.put("description", tool.getDescription());
                funcNode.put("strict", true);
                funcNode.set("parameters", mapper.valueToTree(tool.getParameters()));
                toolsArray.add(toolNode);
            }
        }

        return body;
    }

    private ObjectNode convertMessage(Message msg) {
        ObjectNode node = mapper.createObjectNode();

        switch (msg.getRole()) {
            case SYSTEM -> {
                node.put("role", "system");
                node.put("content", msg.getContent() != null ? msg.getContent() : "");
            }
            case USER -> {
                node.put("role", "user");
                node.put("content", msg.getContent());
            }
            case ASSISTANT -> {
                node.put("role", "assistant");
                if (msg.getContent() != null) {
                    node.put("content", msg.getContent());
                }
                if (msg.hasToolCalls()) {
                    ArrayNode tcArray = node.putArray("tool_calls");
                    for (ToolCall tc : msg.getToolCalls()) {
                        ObjectNode tcNode = mapper.createObjectNode();
                        tcNode.put("id", tc.getId());
                        tcNode.put("type", "function");
                        ObjectNode funcNode = tcNode.putObject("function");
                        funcNode.put("name", tc.getFunctionName());
                        try {
                            funcNode.put("arguments", mapper.writeValueAsString(tc.getArguments()));
                        } catch (IOException e) {
                            funcNode.put("arguments", "{}");
                        }
                        tcArray.add(tcNode);
                    }
                }
            }
            case TOOL -> {
                node.put("role", "tool");
                node.put("tool_call_id", msg.getToolCallId());
                node.put("content", msg.getContent() != null ? msg.getContent() : "");
            }
        }

        return node;
    }

    // --- Response parsing ---

    private LLMResponse parseResponse(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");
            String finishReason = choice.path("finish_reason").asText("stop");

            // Extract content
            String content = message.has("content") && !message.get("content").isNull()
                    ? message.get("content").asText() : null;

            // Extract tool calls
            List<ToolCall> toolCalls = new ArrayList<>();
            if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
                for (JsonNode tc : message.get("tool_calls")) {
                    String id = tc.path("id").asText();
                    String funcName = tc.path("function").path("name").asText();
                    String argsJson = tc.path("function").path("arguments").asText("{}");
                    Map<String, Object> args = mapper.readValue(argsJson, new TypeReference<>() {});
                    toolCalls.add(new ToolCall(id, funcName, args));
                }
            }

            // Extract usage
            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = root.path("usage").path("completion_tokens").asInt(0);

            return new LLMResponse.Builder()
                    .content(content)
                    .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                    .finishReason(LLMResponse.FinishReason.fromOpenAI(finishReason))
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .build();

        } catch (IOException e) {
            return errorResponse("Error parsing OpenAI response: " + e.getMessage());
        }
    }
}
