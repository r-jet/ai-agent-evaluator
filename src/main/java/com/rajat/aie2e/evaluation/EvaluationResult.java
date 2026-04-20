package com.rajat.aie2e.evaluation;

import java.util.List;

/**
 * Structured result produced by EvaluatorClient for one conversation.
 */
public record EvaluationResult(

        /** Name of the scenario that was evaluated */
        String scenarioName,

        /** Overall score from 0–100 */
        int overallScore,

        /** One result per evaluation criterion */
        List<CriterionResult> criterionResults,

        /** Evaluator's free-text summary of the agent's performance */
        String summary,

        /** Any notable failure modes or concerns identified */
        String concerns

) {

    /** Result for a single evaluation criterion */
    public record CriterionResult(
            String criterion,
            boolean passed,
            String explanation
    ) {}

    /** Convenience: count how many criteria passed */
    public long passCount() {
        return criterionResults.stream().filter(CriterionResult::passed).count();
    }

    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("═".repeat(60)).append("\n");
        sb.append("EVALUATION: ").append(scenarioName).append("\n");
        sb.append("Overall Score: ").append(overallScore).append("/100\n");
        sb.append("Criteria: ").append(passCount()).append("/")
                .append(criterionResults.size()).append(" passed\n");
        sb.append("─".repeat(60)).append("\n");

        for (CriterionResult cr : criterionResults) {
            sb.append(cr.passed() ? "  ✅ " : "  ❌ ")
                    .append(cr.criterion()).append("\n");
            sb.append("     → ").append(cr.explanation()).append("\n");
        }

        sb.append("─".repeat(60)).append("\n");
        sb.append("Summary: ").append(summary).append("\n");

        if (concerns != null && !concerns.isBlank()) {
            sb.append("Concerns: ").append(concerns).append("\n");
        }

        sb.append("═".repeat(60)).append("\n");
        return sb.toString();
    }
}