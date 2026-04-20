package com.rajat.aie2e.livechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rajat.aie2e.config.AppConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Low-level HTTP client for the Sprinklr Live Chat API.
 *
 * Every outbound request and its response is logged to reports/requests.log
 * as a curl-equivalent command, so you can paste it into Postman or a
 * terminal and compare it directly with a working call.
 */
public class LiveChatClient {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final RequestLogger requestLogger;

    private String chatToken;
    private String conversationId;
    private String senderId;

    /**
     * The creationTime (epoch ms) of the most recent customer message we sent.
     * fetchNewAgentReply() only returns agent messages AFTER this timestamp.
     */
    private long lastCustomerMessageTime = 0;

    public LiveChatClient() {
        try {
            this.requestLogger = new RequestLogger();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise RequestLogger", e);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void handshake() throws Exception {
        String body = """
                {
                    "appId": "%s",
                    "page": "%s",
                    "pageTitle": "Sprinklr Live Chat",
                    "timezone": "Asia/Calcutta",
                    "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
                    "fallbackLocales": ["en-GB"]
                }
                """.formatted(AppConfig.getSprinklrAppId(), AppConfig.getSprinklrPageUrl());

        log("Handshake — appId   : " + AppConfig.getSprinklrAppId());
        log("Handshake — page URL: " + AppConfig.getSprinklrPageUrl());

        JsonNode json = post("/handshake/appHandshake", body, null);

        chatToken = json.path("chatSessionToken").asText();
        if (chatToken.isBlank()) {
            chatToken = null;
            throw new LiveChatException("chatSessionToken missing in handshake response");
        }
        log("Handshake complete. Token obtained.");
    }

    public void createConversation() throws Exception {
        String body = """
                {
                    "pageTitle": "Sprinklr Live Chat",
                    "pageUrl": "%s",
                    "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
                    "timeZone": "Asia/Calcutta",
                    "locale": "en"
                }
                """.formatted(AppConfig.getSprinklrPageUrl());

        JsonNode json = post("/conversation/new", body, chatToken);

        conversationId = json.path("id").asText();
        if (conversationId.isBlank()) conversationId = null;

        JsonNode participants = json.path("participants");
        if (participants.isArray() && !participants.isEmpty()) {
            senderId = participants.get(0).asText();
        }

        if (conversationId == null || senderId == null) {
            throw new LiveChatException("Missing conversationId or senderId in createConversation response");
        }

        log("Conversation created: " + conversationId + " | Sender: " + senderId);
    }

    public void sendMessage(String text) throws Exception {
        var messagePayload = Map.of(
                "text", text,
                "textEntities", java.util.List.of(),
                "messageType", "MESSAGE"
        );
        var requestBody = Map.of(
                "conversationId", conversationId,
                "messagePayload", messagePayload,
                "sender", senderId
        );

        String body = mapper.writeValueAsString(requestBody);
        JsonNode json = post("/conversation/send", body, chatToken);

        if (!json.has("id")) {
            throw new LiveChatException("Send message failed — no 'id' in response: " + json);
        }

        long serverTime = json.path("creationTime").asLong(0);
        lastCustomerMessageTime = serverTime > 0 ? serverTime : System.currentTimeMillis();

        log("Message sent (creationTime=" + lastCustomerMessageTime + "): "
                + text.substring(0, Math.min(80, text.length())));
    }

    public String fetchNewAgentReply() throws Exception {
        String url = AppConfig.SPRINKLR_BASE_URL
                + "/conversation/fetchMessages?conversationId=" + conversationId
                + "&size=20";

        List<String> headers = List.of("x-chat-token: " + chatToken);
        requestLogger.logRequest("GET", url, headers, null);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("x-chat-token", chatToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        requestLogger.logResponse(response.statusCode(), response.body());

        if (response.statusCode() >= 500) {
            log("Transient " + response.statusCode() + " on fetchMessages — will retry");
            return null;
        }
        if (response.statusCode() >= 400) {
            throw new LiveChatException("HTTP " + response.statusCode()
                    + " on fetchMessages: " + response.body());
        }

        String responseBody = response.body();
        if (!responseBody.trim().startsWith("{")) {
            log("Non-JSON fetchMessages response — will retry: "
                    + responseBody.substring(0, Math.min(80, responseBody.length())));
            return null;
        }

        JsonNode root = mapper.readTree(responseBody);
        JsonNode messages = root.path("results");

        if (!messages.isArray() || messages.isEmpty()) {
            return null;
        }

        String latestAgentReply = null;
        long latestTimestamp = 0;

        for (JsonNode msg : messages) {
            String msgSender = msg.path("sender").asText();
            boolean isAgentMessage = !msgSender.equals(senderId);
            long ts = msg.path("creationTime").asLong(0);

            if (isAgentMessage && ts > lastCustomerMessageTime && ts > latestTimestamp) {
                String text = msg.path("messagePayload").path("text").asText();
                if (!text.isBlank()) {
                    latestAgentReply = text;
                    latestTimestamp = ts;
                }
            }
        }

        return latestAgentReply;
    }

    public void closeConversation() {
        if (conversationId == null) return;
        try {
            String url = AppConfig.SPRINKLR_BASE_URL
                    + "/conversation/closeConversation?conversationId=" + conversationId;

            List<String> headers = new ArrayList<>();
            headers.add("x-chat-token: " + chatToken);
            headers.add("Content-Type: application/json");
            requestLogger.logRequest("POST", url, headers, null);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("x-chat-token", chatToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            requestLogger.logResponse(response.statusCode(), response.body());

            log("Conversation closed: " + conversationId);
        } catch (Exception e) {
            log("Warning: failed to close conversation — " + e.getMessage());
        } finally {
            requestLogger.close();
        }
    }

    public String getConversationId() { return conversationId; }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Sends a POST, logs the full request + response to requests.log,
     * and returns the parsed JSON body.
     */
    private JsonNode post(String path, String body, String token) throws Exception {
        String url = AppConfig.SPRINKLR_BASE_URL + path;

        List<String> logHeaders = new ArrayList<>();
        logHeaders.add("Content-Type: application/json");
        if (token != null) {
            logHeaders.add("x-chat-token: " + token);
        }

        requestLogger.logRequest("POST", url, logHeaders, body);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (token != null) {
            builder.header("x-chat-token", token);
        }

        HttpResponse<String> response = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());

        requestLogger.logResponse(response.statusCode(), response.body());

        int status = response.statusCode();
        String responseBody = response.body();

        if (status >= 400) {
            throw new LiveChatException("HTTP " + status + " on " + path + ": " + responseBody);
        }
        if (!responseBody.trim().startsWith("{") && !responseBody.trim().startsWith("[")) {
            throw new LiveChatException("Non-JSON response on " + path + ": " + responseBody);
        }

        return mapper.readTree(responseBody);
    }

    private void log(String msg) {
        System.out.println("[LiveChatClient] " + msg);
    }
}