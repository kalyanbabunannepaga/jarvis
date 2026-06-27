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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Google Gemini API adapter.
 * Handles: gemini-2.5-pro, gemini-2.0-flash, gemini-1.5-pro, etc.
 * API: POST {@value #DEFAULT_BASE_URL}/models/{model}:generateContent
 */
public class GeminiProvider extends AbstractHttpProvider {

    /** Default Google Generative Language API base URL. */
    public static final String DEFAULT_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta";
    private final String apiKey;
    private final String modelName;
    private final String baseUrl;

    public GeminiProvider(String apiKey, String modelName, String baseUrl) {
        super();
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
    }

    @Override
    public String name() { return "Gemini"; }

    @Override
    public String model() { return modelName; }

    @Override
    public LLMResponse chat(List<Message> messages, List<ToolDefinition> tools, RequestConfig config) {
        try {
            ObjectNode requestBody = buildRequestBody(messages, tools, config);
            String jsonBody = mapper.writeValueAsString(requestBody);

            String url = baseUrl + "/models/" + modelName + ":generateContent?key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
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
            return errorResponse("Request to Gemini was interrupted: " + e.getMessage());
        } catch (IOException e) {
            return errorResponse("Error calling Gemini: " + e.getMessage());
        }
    }

    // --- Request building ---

    private ObjectNode buildRequestBody(List<Message> messages, List<ToolDefinition> tools, RequestConfig config) {
        ObjectNode body = mapper.createObjectNode();

        // System instruction (separate from contents in Gemini)
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
            ObjectNode systemInstruction = body.putObject("system_instruction");
            ArrayNode parts = systemInstruction.putArray("parts");
            parts.addObject().put("text", systemPrompt);
        }

        // Contents (conversation messages)
        ArrayNode contentsArray = body.putArray("contents");
        for (Message msg : nonSystemMessages) {
            ObjectNode contentNode = convertMessage(msg);
            if (contentNode != null) {
                contentsArray.add(contentNode);
            }
        }

        // Tools (function declarations)
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = body.putArray("tools");
            ObjectNode toolContainer = mapper.createObjectNode();
            ArrayNode funcDeclarations = toolContainer.putArray("function_declarations");
            for (ToolDefinition tool : tools) {
                ObjectNode funcNode = mapper.createObjectNode();
                funcNode.put("name", tool.getName());
                funcNode.put("description", tool.getDescription());
                // Gemini doesn't support "additionalProperties" in parameter schemas
                JsonNode params = mapper.valueToTree(tool.getParameters());
                if (params.isObject()) {
                    ((ObjectNode) params).remove("additionalProperties");
                }
                funcNode.set("parameters", params);
                funcDeclarations.add(funcNode);
            }
            toolsArray.add(toolContainer);
        }

        // Generation config
        ObjectNode genConfig = body.putObject("generationConfig");
        genConfig.put("maxOutputTokens", config.maxTokens());
        genConfig.put("temperature", config.temperature());

        return body;
    }

    private ObjectNode convertMessage(Message msg) {
        ObjectNode node = mapper.createObjectNode();

        switch (msg.getRole()) {
            case USER -> {
                node.put("role", "user");
                ArrayNode parts = node.putArray("parts");
                parts.addObject().put("text", msg.getContent());
            }
            case ASSISTANT -> {
                node.put("role", "model");
                ArrayNode parts = node.putArray("parts");

                if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                    parts.addObject().put("text", msg.getContent());
                }

                if (msg.hasToolCalls()) {
                    for (ToolCall tc : msg.getToolCalls()) {
                        ObjectNode funcCallPart = mapper.createObjectNode();
                        ObjectNode functionCall = funcCallPart.putObject("functionCall");
                        functionCall.put("name", tc.getFunctionName());
                        functionCall.set("args", mapper.valueToTree(tc.getArguments()));
                        parts.add(funcCallPart);
                    }
                }
            }
            case TOOL -> {
                // Gemini expects function responses as a special "function" role
                node.put("role", "function");
                ArrayNode parts = node.putArray("parts");
                ObjectNode funcResponsePart = mapper.createObjectNode();
                ObjectNode functionResponse = funcResponsePart.putObject("functionResponse");
                functionResponse.put("name", msg.getToolName() != null ? msg.getToolName() : "tool");
                ObjectNode response = functionResponse.putObject("response");
                response.put("result", msg.getContent() != null ? msg.getContent() : "");
                parts.add(funcResponsePart);
            }
            default -> {
                return null;
            }
        }

        return node;
    }

    // --- Response parsing ---

    private LLMResponse parseResponse(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode candidate = root.path("candidates").path(0);
            JsonNode content = candidate.path("content");
            String finishReason = candidate.path("finishReason").asText("STOP");

            StringBuilder textContent = new StringBuilder();
            List<ToolCall> toolCalls = new ArrayList<>();

            JsonNode parts = content.path("parts");
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    if (part.has("text")) {
                        textContent.append(part.get("text").asText());
                    }
                    if (part.has("functionCall")) {
                        JsonNode funcCall = part.get("functionCall");
                        String funcName = funcCall.path("name").asText();
                        Map<String, Object> args = new HashMap<>();
                        if (funcCall.has("args")) {
                            args = mapper.convertValue(funcCall.get("args"), new TypeReference<>() {});
                        }
                        // Gemini doesn't provide a tool call ID, so we generate one
                        String id = "gemini_" + UUID.randomUUID().toString().substring(0, 8);
                        toolCalls.add(new ToolCall(id, funcName, args));
                    }
                }
            }

            // Check if there are tool calls — if so, set the finish reason
            LLMResponse.FinishReason reason = toolCalls.isEmpty()
                    ? LLMResponse.FinishReason.fromGemini(finishReason)
                    : LLMResponse.FinishReason.TOOL_CALLS;

            // Extract usage
            int promptTokens = root.path("usageMetadata").path("promptTokenCount").asInt(0);
            int completionTokens = root.path("usageMetadata").path("candidatesTokenCount").asInt(0);

            String text = textContent.length() > 0 ? textContent.toString() : null;

            return new LLMResponse.Builder()
                    .content(text)
                    .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                    .finishReason(reason)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .build();

        } catch (IOException e) {
            return errorResponse("Error parsing Gemini response: " + e.getMessage());
        }
    }
}
