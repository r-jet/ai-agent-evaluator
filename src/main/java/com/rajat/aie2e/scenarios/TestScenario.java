package com.rajat.aie2e.scenarios;

import java.util.List;

/**
 * Defines a single test scenario: who the customer is, what their problem is,
 * how many turns to run, and what criteria the evaluator should judge against.
 */
public record TestScenario(

        /** Short unique name, used in log filenames. e.g. "wifi-not-working" */
        String name,

        /** Human-readable description shown in reports */
        String description,

        /**
         * System prompt injected into the customer LLM.
         * Defines the persona, the problem, and behavioural rules.
         */
        String customerPersona,

        /**
         * The hint passed to the customer LLM to generate the very first message.
         * e.g. "Start the conversation. Describe your WiFi problem."
         */
        String openingPrompt,

        /** How many customer→agent turns to run (not counting the opening message) */
        int maxTurns,

        /**
         * Plain-English criteria the evaluator LLM will score the agent against.
         * e.g. "Agent correctly identified the issue as a network congestion problem"
         */
        List<String> evaluationCriteria

) {}