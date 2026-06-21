package com.rajat.aie2e.config;

import java.util.Scanner;

public class AppConfig {

    // ── Sprinklr ──────────────────────────────────────────────────────────────

    public static final String SPRINKLR_BASE_URL =
            getEnvOrDefault(
                    "SPRINKLR_BASE_URL",
                    "https://prod-live-chat.sprinklr.com/api/livechat/v1"
            );

    private static String sprinklrAppId =
            System.getenv("SPRINKLR_APP_ID");

    private static String sprinklrPageUrl;

    // ── OpenAI ────────────────────────────────────────────────────────────────

    public static final String OPENAI_API_KEY =
            getRequiredEnv("OPENAI_API_KEY");

    public static final String OPENAI_MODEL =
            getEnvOrDefault(
                    "OPENAI_MODEL",
                    "gpt-4o-mini"
            );

    public static final String OPENAI_EVALUATOR_MODEL =
            getEnvOrDefault(
                    "OPENAI_EVALUATOR_MODEL",
                    "gpt-4o"
            );

    // ── Polling ───────────────────────────────────────────────────────────────

    public static final int POLL_INITIAL_DELAY_MS =
            Integer.parseInt(
                    getEnvOrDefault(
                            "POLL_INITIAL_DELAY_MS",
                            "5000"
                    )
            );

    public static final int POLL_RETRY_INTERVAL_MS =
            Integer.parseInt(
                    getEnvOrDefault(
                            "POLL_RETRY_INTERVAL_MS",
                            "3000"
                    )
            );

    public static final int POLL_MAX_RETRIES =
            Integer.parseInt(
                    getEnvOrDefault(
                            "POLL_MAX_RETRIES",
                            "10"
                    )
            );

    // ── Reporting ─────────────────────────────────────────────────────────────

    public static final String REPORTS_DIR =
            getEnvOrDefault(
                    "REPORTS_DIR",
                    "reports"
            );

    // ── Startup Prompt ───────────────────────────────────────────────────────

    public static void promptForAppIdIfNeeded() {

        if (sprinklrAppId != null && !sprinklrAppId.isBlank()) {

            System.out.println(
                    "✅ Sprinklr App ID loaded from environment: "
                            + sprinklrAppId
            );

        } else {

            System.out.print(
                    "Enter Sprinklr Live Chat Application ID: "
            );

            Scanner scanner = new Scanner(System.in);

            String input = scanner.nextLine().trim();

            if (input.isBlank()) {
                throw new IllegalStateException(
                        "Sprinklr App ID cannot be empty."
                );
            }

            sprinklrAppId = input;
        }

        System.out.println(
                "DEBUG BASE URL = "
                        + SPRINKLR_BASE_URL
        );

        rebuildPageUrl();
    }

    // ── Used by REST API ──────────────────────────────────────────────────────

    public static void setSprinklrAppId(String appId) {

        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException(
                    "Sprinklr App ID cannot be empty"
            );
        }

        sprinklrAppId = appId;

        System.out.println(
                "DEBUG BASE URL = "
                        + SPRINKLR_BASE_URL
        );

        rebuildPageUrl();
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

    // ── Internal Helpers ──────────────────────────────────────────────────────

    private static void rebuildPageUrl() {

        sprinklrPageUrl =
                "https://live-chat-static.sprinklr.com/test-html/index.html"
                        + "?appId=" + sprinklrAppId
                        + "&env=prod";

        System.out.println(
                "DEBUG PAGE URL = "
                        + sprinklrPageUrl
        );
    }

    private static void assertAppIdReady() {

        if (sprinklrAppId == null || sprinklrAppId.isBlank()) {

            throw new IllegalStateException(
                    "SPRINKLR_APP_ID has not been set."
            );
        }
    }

    private static String getRequiredEnv(String name) {

        String val = System.getenv(name);

        if (val == null || val.isBlank()) {

            throw new IllegalStateException(
                    "Required environment variable not set: "
                            + name
            );
        }

        return val;
    }

    private static String getEnvOrDefault(
            String name,
            String defaultValue
    ) {

        String val = System.getenv(name);

        return (val != null && !val.isBlank())
                ? val
                : defaultValue;
    }
}