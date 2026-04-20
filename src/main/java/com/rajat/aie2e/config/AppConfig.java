package com.rajat.aie2e.config;

import java.util.Scanner;

/**
 * Central configuration.
 *
 * Most values are loaded from environment variables at class-load time.
 * SPRINKLR_APP_ID is the exception: if not set as an env var, the user
 * is prompted for it interactively at startup via promptForAppId().
 *
 * Call order in Main:
 *   1. AppConfig.promptForAppIdIfNeeded()   ← reads from console if not in env
 *   2. new ConversationRunner(scenario)      ← everything else reads AppConfig normally
 */
public class AppConfig {

    // ── Sprinklr ──────────────────────────────────────────────────────────────
    public static final String SPRINKLR_BASE_URL =
            getEnvOrDefault("SPRINKLR_BASE_URL",
                    "https://prod0-live-chat.sprinklr.com/api/livechat/v1");

    /**
     * Mutable — set either from the SPRINKLR_APP_ID env var or via
     * promptForAppIdIfNeeded() before any scenario runs.
     */
    private static String sprinklrAppId = System.getenv("SPRINKLR_APP_ID");

    /**
     * Derived from sprinklrAppId — computed once after the ID is known.
     * Access via getSprinklrPageUrl().
     */
    private static String sprinklrPageUrl;

    // ── OpenAI ────────────────────────────────────────────────────────────────
    public static final String OPENAI_API_KEY =
            getRequiredEnv("OPENAI_API_KEY");

    public static final String OPENAI_MODEL =
            getEnvOrDefault("OPENAI_MODEL", "gpt-4o-mini");

    public static final String OPENAI_EVALUATOR_MODEL =
            getEnvOrDefault("OPENAI_EVALUATOR_MODEL", "gpt-4o");

    // ── Polling ───────────────────────────────────────────────────────────────
    public static final int POLL_INITIAL_DELAY_MS =
            Integer.parseInt(getEnvOrDefault("POLL_INITIAL_DELAY_MS", "5000"));

    public static final int POLL_RETRY_INTERVAL_MS =
            Integer.parseInt(getEnvOrDefault("POLL_RETRY_INTERVAL_MS", "3000"));

    public static final int POLL_MAX_RETRIES =
            Integer.parseInt(getEnvOrDefault("POLL_MAX_RETRIES", "10"));

    // ── Reporting ─────────────────────────────────────────────────────────────
    public static final String REPORTS_DIR =
            getEnvOrDefault("REPORTS_DIR", "reports");

    // ── App ID prompt ─────────────────────────────────────────────────────────

    /**
     * If SPRINKLR_APP_ID is already set as an environment variable, this is a
     * no-op. Otherwise, prompts the user to enter it on the console.
     *
     * Must be called from Main before any scenario runs.
     */
    public static void promptForAppIdIfNeeded() {
        if (sprinklrAppId != null && !sprinklrAppId.isBlank()) {
            System.out.println("✅ Sprinklr App ID loaded from environment: " + sprinklrAppId);
        } else {
            System.out.print("Enter Sprinklr Live Chat Application ID: ");
            System.out.flush();
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine().trim();
            if (input.isBlank()) {
                throw new IllegalStateException(
                        "Sprinklr App ID cannot be empty. "
                                + "Either enter it at the prompt or set the SPRINKLR_APP_ID environment variable."
                );
            }
            sprinklrAppId = input;
        }

        // Build the derived page URL now that we have the ID
        sprinklrPageUrl = "https://live-chat-static.sprinklr.com/test-html/index.html"
                + "?appId=" + sprinklrAppId + "&env=prod0";
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public static String getSprinklrAppId() {
        assertAppIdReady();
        return sprinklrAppId;
    }

    public static String getSprinklrPageUrl() {
        assertAppIdReady();
        return sprinklrPageUrl;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static void assertAppIdReady() {
        if (sprinklrAppId == null || sprinklrAppId.isBlank()) {
            throw new IllegalStateException(
                    "SPRINKLR_APP_ID has not been set. "
                            + "Call AppConfig.promptForAppIdIfNeeded() at startup."
            );
        }
    }

    private static String getRequiredEnv(String name) {
        String val = System.getenv(name);
        if (val == null || val.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable not set: " + name
                            + "\nSet it in IntelliJ via Run > Edit Configurations > Environment Variables"
            );
        }
        return val;
    }

    private static String getEnvOrDefault(String name, String defaultValue) {
        String val = System.getenv(name);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}