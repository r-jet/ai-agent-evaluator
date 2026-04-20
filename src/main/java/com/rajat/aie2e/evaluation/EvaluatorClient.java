package com.rajat.aie2e.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rajat.aie2e.config.AppConfig;
import com.rajat.aie2e.scenarios.TestScenario;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Uses a separate OpenAI call (with a judge/evaluator system prompt) to score
 * the agent's performance on a completed conversation.
 *
 * Deliberately uses a stronger model (gpt-4o by default) than the customer LLM
 * to get more reliable evaluations.
 *
 * The evaluator receives:
 *   - The scenario description (what the customer's problem was)
 *   - The full conversation transcript
 *   - The evaluation criteria (from the TestScenario)
 *
 * It is asked to return structured JSON which we parse into EvaluationResult.
 */
public class EvaluatorClient {

    private static final String SYSTEM_PROMPT = """
            You are an expert QA evaluator assessing the performance of an AI customer support agent.

            You will be given:
            1. A scenario description explaining what the customer's problem was.
            2. A full conversation transcript between the customer and the agent.
            3. A list of evaluation criteria.

            Your job:
            - Evaluate each criterion independently. Be strict but fair.
            - Assign an overall score from 0 to 100.
            - Provide a short explanation for each criterion result.
            - Write a concise summary of the agent's overall performance.
            - Note any concerns: hallucinations, irrelevant responses, rudeness, unresolved issues.

            You MUST respond ONLY with a valid JSON object in exactly this format (no markdown, no preamble):
            {
              "overallScore": <integer 0-100>,
              "criterionResults": [
                {
                  "criterion": "<criterion text>",
                  "passed": <true|false>,
                  "explanation": "<1-2 sentence explanation>"
                }
              ],
              "summary": "<2-3 sentence summary of agent performance>",
              "concerns": "<any notable issues, or empty string if none>"
            }
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Evaluate a completed conversation against the scenario's criteria.
     *
     * @param scenario   The test scenario (provides description and criteria)
     * @param transcript The full conversation as a human-readable string
     * @return           Structured evaluation result
     */
    public EvaluationResult evaluate(TestScenario scenario, String transcript) throws Exception {
        String userPrompt = buildUserPrompt(scenario, transcript);

        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", AppConfig.OPENAI_EVALUATOR_MODEL);
        requestBody.put("temperature", 0.1); // Low temperature for consistent, deterministic scoring
        requestBody.put("max_tokens", 1500);

        var messages = requestBody.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
        messages.addObject().put("role", "user").put("content", userPrompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + AppConfig.OPENAI_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Evaluator API error " + response.statusCode()
                    + ": " + response.body());
        }

        JsonNode apiResponse = mapper.readTree(response.body());
        String rawJson = apiResponse.path("choices").get(0)
                .path("message").path("content").asText();

        return parseEvaluationResponse(scenario.name(), rawJson);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private String buildUserPrompt(TestScenario scenario, String transcript) {
        StringBuilder sb = new StringBuilder();
        sb.append("SCENARIO: ").append(scenario.description()).append("\n\n");
        sb.append("CONVERSATION TRANSCRIPT:\n");
        sb.append(transcript).append("\n\n");
        sb.append("EVALUATION CRITERIA:\n");
        for (int i = 0; i < scenario.evaluationCriteria().size(); i++) {
            sb.append((i + 1)).append(". ").append(scenario.evaluationCriteria().get(i)).append("\n");
        }
        return sb.toString();
    }

    private EvaluationResult parseEvaluationResponse(String scenarioName, String rawJson) {
        try {
            // Strip markdown fences if the model wrapped the JSON
            String clean = rawJson
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode json = mapper.readTree(clean);

            int overallScore = json.path("overallScore").asInt(0);
            String summary = json.path("summary").asText();
            String concerns = json.path("concerns").asText();

            List<EvaluationResult.CriterionResult> criterionResults = new ArrayList<>();
            for (JsonNode cr : json.path("criterionResults")) {
                criterionResults.add(new EvaluationResult.CriterionResult(
                        cr.path("criterion").asText(),
                        cr.path("passed").asBoolean(false),
                        cr.path("explanation").asText()
                ));
            }

            return new EvaluationResult(scenarioName, overallScore,
                    criterionResults, summary, concerns);

        } catch (Exception e) {
            // If JSON parsing fails, return a minimal result with the raw text as concerns
            return new EvaluationResult(
                    scenarioName, 0,
                    List.of(new EvaluationResult.CriterionResult(
                            "JSON parse failed", false,
                            "Evaluator returned unparseable response: " + rawJson.substring(0, Math.min(200, rawJson.length()))
                    )),
                    "Evaluation failed due to parse error.",
                    rawJson
            );
        }
    }
}