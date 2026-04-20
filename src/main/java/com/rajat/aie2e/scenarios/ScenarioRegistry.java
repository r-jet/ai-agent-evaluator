package com.rajat.aie2e.scenarios;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Central registry of all test scenarios.
 *
 * TWO WAYS TO PROVIDE SCENARIOS:
 *
 *   1. Hardcoded (default)
 *      Edit ALL_SCENARIOS below. Used when no Excel file is specified.
 *
 *   2. Excel file
 *      Pass a path to a .xlsx file via fromExcel(path).
 *      Each row = one scenario. See ScenarioLoader for column spec.
 *      Run via: java -jar ... --scenarios path/to/scenarios.xlsx
 *      Or enter the path when prompted at startup.
 *
 * Main.java resolves which source to use at startup.
 */
public class ScenarioRegistry {

    // ── Hardcoded scenario definitions ────────────────────────────────────────
    // These are used when no Excel file is provided.

    public static final TestScenario WIFI_NOT_WORKING = new TestScenario(
            "wifi-not-working",
            "Customer reports complete WiFi outage at home",
            """
            You are a telecom customer chatting with a customer support agent via live chat.

            Your situation:
            - Your home WiFi stopped working completely about an hour ago.
            - All devices in your home cannot connect.
            - You have already tried restarting your router once.
            - You are mildly frustrated but cooperative.

            Rules:
            - You are the CUSTOMER. Never act like a support agent.
            - Speak naturally, like a real person texting support. Short messages.
            - Respond only to what the agent just said. Don't jump ahead.
            - If the agent asks you to try something, pretend you tried it and report back a realistic result.
            - Do NOT use phrases like "How can I assist you today?"
            - Do NOT solve your own problem — let the agent lead.
            """,
            "Start the conversation by telling the support agent your WiFi has stopped working completely.",
            5,
            List.of(
                    "Agent acknowledged the WiFi outage and expressed empathy",
                    "Agent asked at least one diagnostic question (e.g. router lights, devices affected)",
                    "Agent provided at least one actionable troubleshooting step",
                    "Agent did not ask the customer to repeat information already provided",
                    "Agent maintained a professional and friendly tone throughout"
            )
    );

    public static final TestScenario SLOW_INTERNET_PEAK_HOURS = new TestScenario(
            "slow-internet-peak-hours",
            "Customer reports slow internet specifically during evenings",
            """
            You are a telecom customer chatting with a customer support agent via live chat.

            Your situation:
            - Your internet is slow every evening between 7pm and 10pm.
            - During the day it works fine.
            - You are on a standard home broadband plan.
            - You are wondering if this is throttling or just congestion.
            - You want to know if there is an add-on or upgrade to fix it.

            Rules:
            - You are the CUSTOMER. Never act like a support agent.
            - Keep messages short (1-2 sentences). Be conversational.
            - Ask follow-up questions if the agent's response is vague.
            - Do NOT accept a non-answer politely — push back once if the agent deflects.
            - Do NOT use phrases like "How can I assist you today?"
            """,
            "Tell the support agent that your internet has been very slow every evening and ask if it could be throttling.",
            5,
            List.of(
                    "Agent correctly explained the difference between throttling and network congestion",
                    "Agent confirmed whether the customer's plan includes any throttling policy",
                    "Agent offered at least one concrete next step or upgrade option",
                    "Agent did not provide a link to a page unrelated to the customer's question",
                    "Agent's response was factually accurate regarding the service plan"
            )
    );

    public static final TestScenario BILLING_UNEXPECTED_CHARGE = new TestScenario(
            "billing-unexpected-charge",
            "Customer sees an unexpected charge on their bill",
            """
            You are a telecom customer chatting with a customer support agent via live chat.

            Your situation:
            - You received your monthly bill and it is €12 higher than usual.
            - You did not sign up for any new service or add-on.
            - You are confused and want an explanation.
            - If the agent can't explain it, you want it reversed.

            Rules:
            - You are the CUSTOMER. Never act like a support agent.
            - Be politely assertive. You want a clear answer, not generic apologies.
            - Keep messages to 1-2 sentences.
            - If the agent gives you a vague answer, push back and ask to escalate.
            - Do NOT accept "I'll look into it" without a follow-up.
            """,
            "Tell the support agent you noticed an unexpected charge on your bill this month and you want to understand what it is.",
            5,
            List.of(
                    "Agent asked for account identification or verification before discussing billing",
                    "Agent offered a specific explanation for the charge (or confirmed they could not identify it)",
                    "Agent explained the process for disputing or reversing a charge",
                    "Agent did not dismiss or minimise the customer's concern",
                    "Agent provided a clear next step or resolution path"
            )
    );

    public static final TestScenario CANCEL_SERVICE = new TestScenario(
            "cancel-service",
            "Customer wants to cancel their subscription",
            """
            You are a telecom customer chatting with a customer support agent via live chat.

            Your situation:
            - You want to cancel your mobile subscription.
            - Your reason is that you found a cheaper plan with a competitor.
            - You are open to a retention offer IF it is genuinely better.
            - You are firm but not rude.

            Rules:
            - You are the CUSTOMER. Never act like a support agent.
            - State clearly you want to cancel. Don't be talked out of it easily.
            - If the agent makes a retention offer, ask for specific details before considering it.
            - Keep messages to 1-3 sentences.
            - Do NOT agree to stay without knowing the exact offer details.
            """,
            "Tell the support agent you want to cancel your mobile subscription.",
            6,
            List.of(
                    "Agent did not immediately process the cancellation without attempting retention",
                    "Agent asked for the reason for cancellation",
                    "Agent made a concrete retention offer with specific pricing or benefit details",
                    "Agent respected the customer's decision if they insisted on cancelling",
                    "Agent explained the cancellation process and any notice period required",
                    "Agent maintained a respectful tone and did not guilt-trip the customer"
            )
    );

    // ── Default list (used when no Excel file is given) ───────────────────────

    public static final List<TestScenario> ALL_SCENARIOS = List.of(
            WIFI_NOT_WORKING,
            SLOW_INTERNET_PEAK_HOURS,
            BILLING_UNEXPECTED_CHARGE,
            CANCEL_SERVICE
    );

    // ── Excel source ──────────────────────────────────────────────────────────

    /**
     * Load scenarios from an Excel file.
     * Validates the file exists and is readable before delegating to ScenarioLoader.
     *
     * @param excelPath path to the .xlsx file
     * @return list of TestScenario parsed from the file
     */
    public static List<TestScenario> fromExcel(Path excelPath) throws Exception {
        if (!Files.exists(excelPath)) {
            throw new IllegalArgumentException(
                    "Excel file not found: " + excelPath.toAbsolutePath()
                            + "\nCheck the path and try again.");
        }
        if (!excelPath.toString().toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException(
                    "Only .xlsx files are supported. Got: " + excelPath.getFileName()
                            + "\nSave your file as Excel Workbook (.xlsx) and try again.");
        }
        return ScenarioLoader.load(excelPath);
    }
}