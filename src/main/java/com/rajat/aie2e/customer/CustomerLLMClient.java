package com.rajat.aie2e.customer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rajat.aie2e.config.AppConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Drives the synthetic customer persona using OpenAI's Chat Completions API.
 *
 * Design: STATELESS from the caller's perspective.
 * The caller (ConversationRunner) owns the conversation history as a list
 * of ChatMessage objects and passes the full history in each call.
 * This class never maintains its own history — it just takes what it's given,
 * wraps it in an API call, and returns the reply.
 *
 * This fixes the double-history bug in the original where ConversationManager
 * was building a history string AND OpenAIClient was maintaining an internal list,
 * causing the full conversation to be duplicated in every API call.
 */
public class CustomerLLMClient {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Generate the next customer message given the current conversation history.
     *
     * @param history  Full conversation history in OpenAI message format.
     *                 The system prompt (persona) should be the first message.
     *                 Caller is responsible for maintaining and appending to this.
     * @return The customer's next message text.
     */
    public String generateCustomerMessage(ArrayNode history) throws Exception {
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", AppConfig.OPENAI_MODEL);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 200);
        requestBody.set("messages", history);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + AppConfig.OPENAI_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("OpenAI API error " + response.statusCode()
                    + ": " + response.body());
        }

        JsonNode json = mapper.readTree(response.body());
        JsonNode choices = json.path("choices");

        if (!choices.isArray() || choices.isEmpty()) {
            throw new RuntimeException("OpenAI returned no choices: " + response.body());
        }

        return choices.get(0).path("message").path("content").asText();
    }

    /**
     * Build a single message node in the format OpenAI expects.
     * role: "system" | "user" | "assistant"
     */
    public ObjectNode buildMessage(String role, String content) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    public ArrayNode createEmptyHistory() {
        return mapper.createArrayNode();
    }
}