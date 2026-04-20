package com.rajat.aie2e.runner;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.rajat.aie2e.config.AppConfig;
import com.rajat.aie2e.customer.CustomerLLMClient;
import com.rajat.aie2e.evaluation.EvaluationResult;
import com.rajat.aie2e.evaluation.EvaluatorClient;
import com.rajat.aie2e.livechat.LiveChatClient;
import com.rajat.aie2e.reporting.ConversationLogger;
import com.rajat.aie2e.scenarios.TestScenario;

/**
 * Drives a single test scenario end-to-end:
 *   1. Handshake + create conversation on Sprinklr
 *   2. Inject customer persona into the customer LLM
 *   3. Run N turns: customer sends → agent replies → customer responds
 *   4. Evaluate the transcript
 *   5. Log everything and close the conversation
 *
 * One ConversationRunner instance per scenario run — do not reuse.
 *
 * Role mapping:
 *   "system"    → customer persona (injected once at start)
 *   "user"      → the agent's replies (from the customer LLM's perspective,
 *                  the agent IS the "user" it's responding to)
 *   "assistant" → the customer LLM's own previous replies
 *
 * This is the correct OpenAI role mapping for a customer-side simulation:
 * the LLM IS the customer ("assistant"), and the things it's responding
 * to are the agent messages ("user").
 */
public class ConversationRunner {

    private final TestScenario scenario;
    private final LiveChatClient liveChat;
    private final CustomerLLMClient customerLLM;
    private final EvaluatorClient evaluator;
    private final ConversationLogger logger;

    // Full OpenAI message history (system + alternating user/assistant)
    private final ArrayNode llmHistory;

    // Human-readable transcript for logging and evaluation
    private final StringBuilder transcript = new StringBuilder();

    public ConversationRunner(TestScenario scenario) {
        this.scenario = scenario;
        this.liveChat = new LiveChatClient();
        this.customerLLM = new CustomerLLMClient();
        this.evaluator = new EvaluatorClient();
        this.logger = new ConversationLogger(scenario.name());
        this.llmHistory = customerLLM.createEmptyHistory();
    }

    /**
     * Execute the full scenario and return the evaluation result.
     * Evaluation runs on whatever transcript was collected, even if the
     * scenario ended early due to an agent timeout. The conversation is
     * always closed in the finally block regardless of outcome.
     */
    public EvaluationResult run() throws Exception {
        System.out.println("\n" + "━".repeat(60));
        System.out.println("▶ SCENARIO: " + scenario.name());
        System.out.println("  " + scenario.description());
        System.out.println("━".repeat(60));

        try {
            // ── Setup ──────────────────────────────────────────────────────
            liveChat.handshake();
            liveChat.createConversation();

            // Inject the customer persona as the system prompt
            llmHistory.add(customerLLM.buildMessage("system", scenario.customerPersona()));

            // ── Turn 1: Opening message ────────────────────────────────────
            printTurn(1);
            llmHistory.add(customerLLM.buildMessage("user", scenario.openingPrompt()));
            String openingMessage = customerLLM.generateCustomerMessage(llmHistory);
            llmHistory.add(customerLLM.buildMessage("assistant", openingMessage));

            logCustomer(openingMessage);
            liveChat.sendMessage(openingMessage);

            // ── Turns 2 → maxTurns: conversation loop ─────────────────────
            for (int turn = 2; turn <= scenario.maxTurns(); turn++) {
                printTurn(turn);

                String agentReply = waitForAgentReply(turn);
                if (agentReply == null) {
                    System.out.println("⚠️  Agent did not respond within timeout. Ending scenario early.");
                    // Don't break out of evaluation — whatever we have is worth scoring.
                    break;
                }

                logAgent(agentReply);

                // Don't send a customer reply on the final turn — there's nothing to reply to
                // from an evaluation standpoint, and it avoids a dangling send with no fetch.
                if (turn == scenario.maxTurns()) {
                    break;
                }

                // Agent reply becomes the "user" message from the customer LLM's perspective
                llmHistory.add(customerLLM.buildMessage("user", agentReply));
                String customerReply = customerLLM.generateCustomerMessage(llmHistory);
                llmHistory.add(customerLLM.buildMessage("assistant", customerReply));

                logCustomer(customerReply);
                liveChat.sendMessage(customerReply);
            }

            // ── Evaluation ────────────────────────────────────────────────
            return runEvaluation();

        } finally {
            liveChat.closeConversation();
        }
    }

    private EvaluationResult runEvaluation() throws Exception {
        System.out.println("\n⚖️  Running evaluation...");
        EvaluationResult result = evaluator.evaluate(scenario, transcript.toString());
        logger.setEvaluationResult(result);
        logger.save();
        System.out.println(result.toDisplayString());
        return result;
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private String waitForAgentReply(int turn) throws Exception {
        System.out.println("⏳ Waiting for agent reply (turn " + turn + ")...");
        Thread.sleep(AppConfig.POLL_INITIAL_DELAY_MS);

        for (int retry = 0; retry < AppConfig.POLL_MAX_RETRIES; retry++) {
            String reply = liveChat.fetchNewAgentReply();
            if (reply != null && !reply.isBlank()) {
                System.out.println("✅ Agent replied after "
                        + (retry == 0 ? "initial wait" : "retry " + retry));
                return reply;
            }
            System.out.println("   No reply yet, retrying... (" + (retry + 1)
                    + "/" + AppConfig.POLL_MAX_RETRIES + ")");
            Thread.sleep(AppConfig.POLL_RETRY_INTERVAL_MS);
        }

        return null;
    }

    private void logCustomer(String text) {
        String line = "CUSTOMER: " + text;
        transcript.append(line).append("\n\n");
        logger.logCustomer(text);
        System.out.println("👤 Customer: " + truncate(text, 120));
    }

    private void logAgent(String text) {
        String line = "AGENT: " + text;
        transcript.append(line).append("\n\n");
        logger.logAgent(text);
        System.out.println("🤖 Agent:    " + truncate(text, 120));
    }

    private void printTurn(int turn) {
        System.out.println("\n── Turn " + turn + "/" + scenario.maxTurns() + " ──");
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}